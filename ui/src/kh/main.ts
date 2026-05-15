import { Application } from "pixi.js";
import { Client, Frame, StompSubscription } from "@stomp/stompjs";
import { OptionsMenu } from "./settings.ts";

const wsUrl =
  import.meta.env.VITE_WS_URL ||
  `${window.location.protocol === "https:" ? "wss:" : "ws:"}//${window.location.host}/games/connect`;
const loginUrl =
  import.meta.env.VITE_LOGIN_URL || `${window.location.origin}/login`;
const homeUrl = `${window.location.origin}/`;

const getLastPathSegment = (url) => {
  const pathname = new URL(url).pathname;
  const parts = pathname.split("/");
  // The pop() method removes the last element and returns it.
  // Calling it a second time handles cases where the URL ends in a trailing slash.
  return parts.pop() || parts.pop();
};

const getGameId = () => {
  return getLastPathSegment(window.location.href);
};

(async () => {
  // Create a new application
  const app = new Application();

  // Initialize the application
  await app.init({ background: "red", resizeTo: window });

  // Append the application canvas to the document body
  document.getElementById("pixi-container")!.appendChild(app.canvas);

  const gameId = getGameId();
  if (gameId.length === 0) {
    console.error(`Game ID not provided`);
    // redirect to login page to ensure user is authenticated
    window.location.href = loginUrl;
    return;
  }

  console.log(`Using game ID: ${gameId}`);

  // Connect to WebSocket via STOMP
  let subscription: StompSubscription;

  const client = new Client({ brokerURL: wsUrl });

  client.onConnect = (frame: Frame) => {
    console.log("Connected: " + frame);
    subscription = client.subscribe(
      `/user/queue/games/${gameId}`,
      (stompMessage) => {
        const body = JSON.parse(stompMessage.body);
        console.log("Received KH message:", body);
        if (body.quit) {
          subscription.unsubscribe();
          app.destroy(true, true);
          window.location.href = homeUrl;
        }
      },
    );
  };

  client.onWebSocketError = (error: unknown) => {
    console.error("Error with websocket", error);
    // redirect to login page to ensure user is authenticated
    window.location.href = loginUrl;
  };

  client.onStompError = (frame: Frame) => {
    console.error("Broker reported error: " + frame.headers["message"]);
    console.error("Additional details: " + frame.body);
  };

  client.activate();

  // Options menu — contains "Save and Exit" and "Quit Game"
  const optionsMenu = new OptionsMenu(
    app,
    () => {
      // Quit Game: notify the server, which will confirm via WebSocket and
      // trigger the redirect once the quit_at timestamp is persisted.
      optionsMenu.close();
      client.publish({ destination: `/games/kh/${gameId}/quit` });
    },
    () => {
      // Save and Exit: game state is already persisted server-side, so just
      // navigate home — the game remains active in the active games list.
      window.location.href = homeUrl;
    },
  );
  app.stage.addChild(optionsMenu);
})();
