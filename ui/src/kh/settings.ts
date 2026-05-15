import { Application, Container, Graphics, Text, TextStyle } from "pixi.js";

const PANEL_WIDTH = 300;
const PANEL_PADDING = 20;
const MARGIN = 12;

/**
 * A PixiJS settings menu that renders as a "⚙ Settings" button anchored to
 * the top-right corner. Clicking it toggles a dropdown panel containing
 * "Save and Exit" and "Quit Game" actions. Clicking outside the open panel
 * closes it via a transparent full-screen overlay.
 */
export class SettingsMenu extends Container {
  private readonly panel: Container;
  private readonly overlay: Graphics;
  private readonly settingsButton: Text;
  private isOpen: boolean = false;

  constructor(
    app: Application,
    onQuit: () => void,
    onSaveAndExit: () => void,
  ) {
    super();

    this.settingsButton = this.buildSettingsButton(app);
    this.overlay = this.buildOverlay(app);
    this.panel = this.buildPanel(app, onQuit, onSaveAndExit);
  }

  // -------------------------------------------------------------------------
  // Public API
  // -------------------------------------------------------------------------

  toggle(): void {
    this.isOpen ? this.close() : this.open();
  }

  open(): void {
    this.isOpen = true;
    this.overlay.visible = true;
    this.panel.visible = true;
    // Ensure the panel renders on top of the overlay.
    this.removeChild(this.panel);
    this.addChild(this.panel);
  }

  close(): void {
    this.isOpen = false;
    this.overlay.visible = false;
    this.panel.visible = false;
  }

  // -------------------------------------------------------------------------
  // Private builders
  // -------------------------------------------------------------------------

  private buildSettingsButton(app: Application): Text {
    const style = new TextStyle({
      fontFamily: "Arial",
      fontSize: 32,
      fontWeight: "bold",
      fill: "white",
      stroke: { color: "#4a1850", width: 3 },
      dropShadow: { color: "#000000", blur: 4, angle: Math.PI / 6, distance: 4 },
    });

    const btn = new Text({ text: "⚙ Settings", style });
    btn.anchor.set(1, 0);
    btn.x = app.screen.width - MARGIN;
    btn.y = MARGIN;
    btn.eventMode = "static";
    btn.cursor = "pointer";
    btn.on("pointerdown", () => this.toggle());
    this.addChild(btn);
    return btn;
  }

  /**
   * Full-screen transparent surface that sits beneath the open panel.
   * Any click on it closes the menu.
   */
  private buildOverlay(app: Application): Graphics {
    const overlay = new Graphics();
    overlay.rect(0, 0, app.screen.width, app.screen.height).fill({
      color: 0x000000,
      alpha: 0.001,
    });
    overlay.eventMode = "static";
    overlay.on("pointerdown", () => this.close());
    overlay.visible = false;
    this.addChild(overlay);
    return overlay;
  }

  private buildPanel(
    app: Application,
    onQuit: () => void,
    onSaveAndExit: () => void,
  ): Container {
    const panel = new Container();
    const W = PANEL_WIDTH;
    const P = PANEL_PADDING;

    // --- Text styles ---
    const labelStyle = new TextStyle({
      fontFamily: "Arial",
      fontSize: 15,
      fontWeight: "bold",
      fill: "#888888",
      letterSpacing: 3,
    });

    const itemStyle = new TextStyle({
      fontFamily: "Arial",
      fontSize: 30,
      fontWeight: "bold",
      fill: "white",
      stroke: { color: "#4a1850", width: 4 },
      dropShadow: { color: "#000000", blur: 4, angle: Math.PI / 6, distance: 4 },
    });

    const quitItemStyle = new TextStyle({
      fontFamily: "Arial",
      fontSize: 30,
      fontWeight: "bold",
      fill: "#ff6b6b",
      stroke: { color: "#4a0000", width: 4 },
      dropShadow: { color: "#000000", blur: 4, angle: Math.PI / 6, distance: 4 },
    });

    // --- Build text nodes (measure before drawing background) ---
    const sectionLabel = new Text({ text: "SETTINGS", style: labelStyle });
    sectionLabel.x = P;
    sectionLabel.y = P;

    const dividerY = sectionLabel.y + sectionLabel.height + 8;

    const saveExit = new Text({ text: "Save and Exit", style: itemStyle });
    saveExit.x = P;
    saveExit.y = dividerY + 12;
    saveExit.eventMode = "static";
    saveExit.cursor = "pointer";
    saveExit.on("pointerover", () => { saveExit.alpha = 0.7; });
    saveExit.on("pointerout", () => { saveExit.alpha = 1.0; });
    saveExit.on("pointerdown", () => onSaveAndExit());

    const quitGame = new Text({ text: "Quit Game", style: quitItemStyle });
    quitGame.x = P;
    quitGame.y = saveExit.y + saveExit.height + 10;
    quitGame.eventMode = "static";
    quitGame.cursor = "pointer";
    quitGame.on("pointerover", () => { quitGame.alpha = 0.7; });
    quitGame.on("pointerout", () => { quitGame.alpha = 1.0; });
    quitGame.on("pointerdown", () => onQuit());

    const totalH = quitGame.y + quitGame.height + P;

    // --- Background (sized to content) ---
    const bg = new Graphics();
    bg.roundRect(0, 0, W, totalH, 12).fill({ color: 0x1a1a2e, alpha: 0.95 });
    bg.setStrokeStyle({ color: 0x4a1850, width: 2 });
    bg.roundRect(0, 0, W, totalH, 12).stroke();

    // --- Divider ---
    const divider = new Graphics();
    divider.setStrokeStyle({ color: 0x4a1850, width: 1 });
    divider.moveTo(P, dividerY).lineTo(W - P, dividerY).stroke();

    // --- Assemble ---
    panel.addChild(bg);
    panel.addChild(divider);
    panel.addChild(sectionLabel);
    panel.addChild(saveExit);
    panel.addChild(quitGame);

    // Position: top-right, just below the settings button.
    const buttonBottomY =
      this.settingsButton.y + this.settingsButton.height + 6;
    panel.x = app.screen.width - W - MARGIN;
    panel.y = buttonBottomY;

    panel.visible = false;
    this.addChild(panel);
    return panel;
  }
}
