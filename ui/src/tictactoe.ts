import {
  Application,
  Assets,
  Container,
  Graphics,
  Sprite,
  Text,
  TextStyle,
} from "pixi.js";
import { z } from "zod";
import { Client, Frame, StompSubscription } from "@stomp/stompjs";

const wsUrl = import.meta.env.VITE_WS_URL ||
  `${window.location.protocol === 'https:' ? 'wss:' : 'ws:'}//${window.location.host}/games/connect`;
const loginUrl = import.meta.env.VITE_LOGIN_URL || `${window.location.origin}/login`;

export enum GamePiece {
  X = "X",
  O = "O",
}

export function opposite(piece: GamePiece): GamePiece {
  switch (piece) {
    case GamePiece.X:
      return GamePiece.O;
    case GamePiece.O:
      return GamePiece.X;
  }
}

export class Position {
  row: number;
  column: number;

  constructor(data: PositionData) {
    this.row = data.row;
    this.column = data.column;
  }

  public equals(other: Position): boolean {
    return this.row === other.row && this.column === other.column;
  }

  static with(row: number, column: number): Position {
    return new Position({ row: row, column: column } as PositionData);
  }
}

export class Line {
  piece: GamePiece;
  positions: Position[];

  constructor(data: LineData) {
    this.piece = data.piece;
    this.positions = data.positions;
  }

  public contains(position: Position): boolean {
    return GameState.isIn(position, this.positions);
  }
}

export class GameMessage {
  context: GameContext;
  state: GameState;

  constructor(data: GameMessageData) {
    this.context = data.context;
    this.state = data.state;
  }

}

export class GameContext {
  gameId: string;
  player: string;
  nextGameId: string | undefined;

  constructor(data: GameContextData) {
    this.gameId = data.gameId;
    this.nextGameId = data.nextGameId;
    this.player = data.player;
  }

}

export class GameState {
  sequence: number;
  players: Map<GamePiece, string>;
  board: Map<GamePiece, Position[]>;
  moves: Map<GamePiece, Position[]>;
  over: boolean;
  quitter: GamePiece | undefined;
  leader: GamePiece;
  winner: GamePiece | undefined;
  line: Line | undefined;

  constructor(data: GameStateData) {
    this.sequence = data.sequence;
    this.players = data.players;
    this.board = data.board;
    this.moves = data.moves;
    this.leader = data.leader;
    this.over = data.over;
    this.quitter = data.quitter;
    this.winner = data.winner;
    this.line = data.line;
  }

  static isIn(position: Position, positions: Position[]): boolean {
    for (const pos of positions) {
      if (pos.equals(position)) {
        return true;
      }
    }
    return false;
  }

  public getPiece(position: Position): GamePiece | undefined {
    for (const [key, value] of this.board.entries()) {
      if (GameState.isIn(position, value)) {
        return key;
      }
    }
    return undefined;
  }

  public isMove(position: Position, piece: GamePiece): boolean {
    const positions = this.moves.get(piece);
    if (positions !== undefined) {
      return GameState.isIn(position, positions);
    }
    return false;
  }

  public isWinner(position: Position): boolean {
    if (this.line !== undefined) {
      return GameState.isIn(position, this.line.positions);
    }
    return false;
  }

  public getMyPiece(context: GameContext): GamePiece | undefined {
    const myName = context.player;
    for (const [key, value] of this.players.entries()) {
      if (myName == value) {
        return key;
      }
    }
    return undefined;
  }

  public getOpponentPiece(context: GameContext): GamePiece | undefined {
    const myPiece = this.getMyPiece(context);
    if (myPiece !== undefined) {
      return opposite(myPiece);
    }
    return undefined;
  }

  public getOpponentName(context: GameContext): string | undefined {
    const opponentPiece = this.getOpponentPiece(context);
    if (opponentPiece !== undefined) {
      return this.players.get(opponentPiece);
    }
    return undefined;
  }

  public isPieceMove(piece: GamePiece | undefined): boolean {
    if (piece !== undefined) {
      const possible = this.moves.get(piece);
      return possible !== undefined && possible.length > 0;
    }
    return false;
  }
}

export const PositionSchema = z
  .object({
    row: z.number(),
    column: z.number(),
  })
  .transform((data) => new Position(data as PositionData));

type PositionData = z.infer<typeof PositionSchema>;

export const LineSchema = z.object({
  piece: z.enum(GamePiece),
  positions: z.array(PositionSchema),
}).transform((data) => new Line(data as LineData));

type LineData = z.infer<typeof LineSchema>;

export const GameContextSchema = z.object({
  gameId: z.string(),
  player: z.string(),
  nextGameId: z.string().optional(),
}).transform((data) => new GameContext(data as GameContext));

type GameContextData = z.infer<typeof GameContextSchema>;
type GameMessageData = z.infer<typeof GameMessageSchema>;

const PieceToPlayerSchema = z.preprocess(
  (val) => new Map(Object.entries(val as object)),
  z.map(z.enum(GamePiece), z.string()),
);
const PieceToPositionSchema = z.preprocess(
  (val) => new Map(Object.entries(val as object)),
  z.map(z.enum(GamePiece), z.array(PositionSchema)),
);

export const GameStateSchema = z.object({
  sequence: z.number(),
  players: PieceToPlayerSchema,
  board: PieceToPositionSchema,
  moves: PieceToPositionSchema,
  leader: z.enum(GamePiece),
  over: z.boolean(),
  quitter: z.enum(GamePiece).optional(),
  winner: z.enum(GamePiece).optional(),
  line: LineSchema.optional(),
}).transform((data) => new GameState(data as GameStateData));

type GameStateData = z.infer<typeof GameStateSchema>;

export const GameMessageSchema = z.object({
  context: GameContextSchema,
  state: GameStateSchema,
}).transform((data) => new GameMessage(data as GameMessageData));

class GameSettings {
  constructor(
    public player: GamePiece,
    public leader: GamePiece,
    public opponent: string | undefined,
  ) {}
}

class BoardSquare extends Container {
  cell: Position;
  size: number;
  token: GamePiece | undefined = undefined;
  square: Graphics;
  sprite: Sprite | undefined;

  constructor(
    size: number,
    cell: Position,
    app: Application,
    handler: GameEventHandler,
  ) {
    super();
    this.size = size;
    this.cell = cell;
    this.init(size, handler);
  }

  init(size: number, handler: GameEventHandler) {
    const square = new Graphics();
    square.rect(0, 0, size, size).fill({
      color: 0x1099bb,
      alpha: 0.001,
    });
    this.square = square;
    this.addChild(square);
    this.eventMode = "none";
    this.cursor = "pointer";
    this.on("pointerdown", (_event: PointerEvent) => {
      handler.play(this);
    });
  }

  /**
   * Sets the game piece in this square and whether the square is a valid move option.
   *
   * @param token the game piece to render
   * @param isMove whether this square is a valid move (enable pointer)
   * @param pool a pool to get an image from
   */
  update(token: GamePiece | undefined, isMove: boolean, pool: SpritePool) {
    if (token !== undefined && this.token !== token) {
      this.token = token;
      const sprite: Sprite = pool.getSprite(token);
      sprite.anchor.set(0.5);
      const scale = (0.4 * this.size) / sprite.texture.width;
      sprite.scale.set(scale);
      sprite.position.set(this.size / 2, this.size / 2);
      this.addChild(sprite);
      this.sprite = sprite;
    }
    if (isMove) {
      this.eventMode = "static";
      this.cursor = "pointer";
    } else {
      this.eventMode = "none";
      this.cursor = "default";
    }
  }

  shrink() {
    if (this.sprite !== undefined) {
      const scale = (0.2 * this.size) / this.sprite.texture.width;
      this.sprite.scale.set(scale);
      this.sprite.alpha = 0.5;
    }
  }

}

export class Board extends Container {
  grid: Graphics;
  squares: BoardSquare[] = [];

  constructor() {
    super();
  }

  public static build(app: Application, handler: GameEventHandler): Board {
    // Use 80% of the screen to draw the grid (to closest even number)
    let size = Math.round(
      (0.8 * Math.min(app.screen.width, app.screen.height)) / 3,
    );
    size = size - (size % 2);
    console.log(`Cell size: ${size}`);

    const board = new Board();
    board.init(app, size, handler);
    board.eventMode = "passive";
    board.position.set(app.screen.width / 2, app.screen.height / 2);
    board.pivot.set(board.width / 2, board.height / 2);
    return board;
  }

  private init(
    app: Application,
    size: number = 100,
    handler: GameEventHandler,
  ) {
    const grid = new Graphics();
    grid.setStrokeStyle({ width: 5, color: "black" });

    // Vertical lines
    grid.moveTo(size, 0);
    grid.lineTo(size, size * 3);
    grid.moveTo(size * 2, 0);
    grid.lineTo(size * 2, size * 3);

    // Horizontal lines
    grid.moveTo(0, size);
    grid.lineTo(size * 3, size);
    grid.moveTo(0, size * 2);
    grid.lineTo(size * 3, size * 2);
    grid.stroke();

    this.grid = grid;
    this.addChild(grid);
    for (let row = 1; row <= 3; row++) {
      for (let col = 1; col <= 3; col++) {
        const square = new BoardSquare(
          size,
          Position.with(row, col),
          app,
          handler,
        );
        square.position.set((col - 1) * size, (row - 1) * size);
        this.addChild(square);
        this.squares.push(square);
      }
    }
  }

  getSquareAt(position: Position): BoardSquare | undefined {
    for (const square of this.squares) {
      if (square.cell.equals(position)) {
        return square;
      }
    }
    return undefined;
  }

  updateSquares(message: GameMessage, pool: SpritePool) {
    const state = message.state;
    const myPiece = state.getMyPiece(message.context);
    for (let row = 1; row <= 3; row++) {
      for (let col = 1; col <= 3; col++) {
        const position = Position.with(row, col);
        const square = this.getSquareAt(position);
        const piece = state.getPiece(position);
        const isMove = state.isMove(position, myPiece);
        if (square !== undefined) {
          square.update(piece, isMove, pool);
          if (state.over) {
            if (!state.isWinner(position)) {
              square.shrink();
            }
          }
        }
      }
    }
  }
}

class SpritePool {
  pool: Map<GamePiece, Sprite[]> = new Map();
  numCopies: number; // the number of sprites per image

  constructor(numCopies: number) {
    this.numCopies = numCopies;
  }

  async init() {
    await this.initTexture("/assets/suits/diamond.svg", GamePiece.X);
    await this.initTexture("/assets/suits/club.svg", GamePiece.O);
  }

  async initTexture(url: string, piece: GamePiece) {
    Assets.load(url).then((texture) => {
      const copies = [];
      for (let copy = 1; copy <= this.numCopies; copy++) {
        const sprite = new Sprite(texture);
        sprite.anchor.set(0.5);
        copies.push(sprite);
      }
      this.pool.set(piece, copies);
    });
  }

  public getSprite(piece: GamePiece): Sprite {
    return this.pool.get(piece).pop();
  }

  public returnSprite(piece: GamePiece, sprite: Sprite) {
    this.pool.get(piece).push(sprite);
  }
}

export class GameEventHandler {
  app: Application;
  sequence: number = -1;
  messageQueue: GameMessage[] = [];
  isProcessing: boolean = false;
  stompClient: Client;
  subscription: StompSubscription;
  gameId: string;
  board: Board;
  spritePool: SpritePool = new SpritePool(5);
  myMove: Text | undefined = undefined;
  yourMove: Text | undefined = undefined;
  quitButton: Text | undefined = undefined;

  constructor(app: Application) {
    this.app = app;
    this.board = Board.build(app, this);
  }

  public getBoard(): Board {
    return this.board;
  }

  public async initImages() {
    await this.spritePool.init();
  }

  public play(square: BoardSquare) {
    this.stompClient.publish({
      destination: `/games/xo/${this.gameId}/play`,
      body: JSON.stringify(square.cell),
    });
  }

  public playAgain() {
    this.stompClient.publish({
      destination: `/games/xo/${this.gameId}/again`,
    });
  }

  public quit() {
    this.stompClient.publish({
      destination: `/games/xo/${this.gameId}/quit`,
    });
  }

  async processMessage(message: GameMessage) {
    this.messageQueue.push(message);

    // lazy init of titles for "my move" and "your move"
    if (this.myMove === undefined) {
      this.createTitles(this.app, message);
    }

    if (this.isProcessing || this.messageQueue.length === 0) return;
    this.isProcessing = true;

    while (this.messageQueue.length > 0) {
      const nextMessage = this.messageQueue.shift();
      const nextSequence = nextMessage.state.sequence;
      if (nextSequence >= this.sequence) {
        if (nextSequence > this.sequence) {
          // new state - update the board
          this.sequence = nextSequence;
          this.board.updateSquares(nextMessage, this.spritePool);
          this.displayGameOver(nextMessage, this.app);
        } else {
          // same sequence - game was quit (no board move) or contains a "play again" link
          if (nextMessage.state.over) {
            this.board.updateSquares(nextMessage, this.spritePool);
            this.displayGameOver(nextMessage, this.app);
          }
          if (nextMessage.context.nextGameId !== undefined && nextMessage.state.over) {
            // clean up and go to next game
            this.subscription.unsubscribe();
            this.app.destroy(true, true);
            window.location.href = nextMessage.context.nextGameId;
          }
        }
      } else {
        console.log(
          `Skipping message with older sequence ${nextSequence} preceding ${this.sequence}`,
        );
      }
    }

    this.isProcessing = false;
  }

  displayGameOver(message: GameMessage, app: Application) {
    const state = message.state;
    const myPiece = message.state.getMyPiece(message.context);
    if (state.over) {
      this.myMove.visible = false;
      this.yourMove.visible = false;
      this.quitButton.visible = false;
      if (state.winner !== undefined) {
        if (state.winner == myPiece) {
          this.displayWin(app, "You Win!", state);
        } else {
          this.displayWin(app, "You Loose!", state);
        }
      } else if (state.quitter !== undefined) {
        const myPiece = state.getMyPiece(message.context);
        const quitMessage = state.quitter === myPiece ? "You quit" : "Opponent quit";
        this.displayWin(app, quitMessage, state);
      } else {
        this.displayWin(app, "Draw!", state);
      }
    } else {
      const myMove = state.isPieceMove(myPiece);
      this.myMove!.visible = myMove;
      this.yourMove!.visible = !myMove;
    }
  }

  private getTextStyle(): TextStyle {
    return new TextStyle({
      fontFamily: "Arial",
      fontSize: 60,
      fontWeight: "bold",
      fill: "white",
      stroke: {
        color: "#4a1850",
        width: 5,
      },
      dropShadow: {
        color: "#000000",
        blur: 4,
        angle: Math.PI / 6,
        distance: 6,
      },
    });
  }

  createTitles(app: Application, message: GameMessage) {
    const style = this.getTextStyle();
    this.myMove = new Text({ text: "Your move", style });
    this.yourMove = new Text({
      text: `Waiting for ${message.state.getOpponentName(message.context)}`,
      style,
    });
    this.quitButton = new Text({ text: "Quit", style });
    this.quitButton.cursor = "pointer";
    this.quitButton.eventMode = "static";
    this.quitButton.on("pointerdown", () => this.quit());
    this.positionTitle(app, this.myMove);
    this.positionTitle(app, this.yourMove);
    this.quitButton.anchor.set(1, 0);
    this.quitButton.x = app.screen.width;
    this.quitButton.y = 0;
    this.quitButton.visible = true;
    app.stage.addChild(this.quitButton);
  }

  positionTitle(app: Application, text: Text) {
    text.anchor.set(0.5); // Center the anchor point
    text.x = app.screen.width / 2;
    text.y = text.height / 2;
    text.visible = false;
    app.stage.addChild(text);
  }

  displayWin(app: Application, message: string, _state: GameState) {
    const style = this.getTextStyle();
    const winText = new Text({ text: message, style });
    const playAgain = new Text({ text: "Play Again", style });
    const exit = new Text({ text: "Exit", style });

    winText.anchor.set(0.5);
    winText.x = app.screen.width / 2;
    winText.y = winText.height / 2;

    playAgain.anchor.set(0.5);
    playAgain.x = app.screen.width / 2;
    playAgain.y = app.screen.height - winText.height / 2;
    playAgain.cursor = "pointer";
    playAgain.eventMode = "static";
    playAgain.on("pointerdown", (_event: PointerEvent) => {
      this.playAgain();
    });

    exit.anchor.set(0.5);
    exit.x = app.screen.width / 2;
    exit.y = app.screen.height - winText.height / 2 - playAgain.height;
    exit.cursor = "pointer";
    exit.eventMode = "static";
    exit.on("pointerdown", () => {
      window.location.href = "/";
    });

    app.stage.addChild(winText);
    app.stage.addChild(exit);
    app.stage.addChild(playAgain);
  }

  onConnect(frame: Frame) {
    console.log("Connected: " + frame);
    this.subscription = this.stompClient.subscribe(
      `/user/queue/games/${this.gameId}`,
      (stompMessage) => {
        const body = JSON.parse(stompMessage.body);
        const message = GameMessageSchema.parse(body);
        console.log(message);
        this.processMessage(message);
      },
    );
    // request state on initial connect
    this.stompClient.publish({
      destination: `/games/xo/${this.gameId}/load`,
      body: "",
    });
  }

  onWebSocketError(error: unknown) {
    console.error("Error with websocket", error);
    // redirect to login page to ensure user is authenticated
    window.location.href = loginUrl;
  }

  onStompError(frame: Frame) {
    console.error("Broker reported error: " + frame.headers["message"]);
    console.error("Additional details: " + frame.body);
  }

  public connect(gameId: string) {
    this.gameId = gameId;
    const client = new Client({
      brokerURL: wsUrl,
    });
    client.onConnect = (frame) => this.onConnect(frame);
    client.onWebSocketError = (error) => this.onWebSocketError(error);
    client.onStompError = (frame) => this.onStompError(frame);
    this.stompClient = client;
    this.stompClient.activate();
  }

}
