/**
 * WebSocket client for streaming assistant chat responses.
 *
 * Protocol (server → client, each frame is a JSON-encoded ChatStreamMessage):
 *   { "type": "token",  "content": "…" }  — incremental response fragment
 *   { "type": "done",   "content": null }  — stream complete
 *   { "type": "error",  "content": "…" }  — server-side error, followed by "done"
 *
 * Client → server: plain text string (the user's message).
 *
 * The connection is lazily opened on the first call to `stream()` and kept alive
 * across turns so that subsequent messages reuse the same socket.  The connection
 * is closed automatically when `close()` is called or the page is unloaded.
 */

const WS_PATH = "/ws/chat";

type ServerMessage =
  | { type: "token"; content: string }
  | { type: "done"; content: null }
  | { type: "error"; content: string };

export type ChatStreamCallbacks = {
  /** Called for each token fragment as it arrives. */
  onToken: (fragment: string) => void;
  /** Called once when the server signals the stream is complete. */
  onDone: () => void;
  /** Called if the server sends an error or the socket drops unexpectedly. */
  onError: (message: string) => void;
};

class ChatWebSocketClient {
  private ws: WebSocket | null = null;
  private pendingCallbacks: ChatStreamCallbacks | null = null;
  private connectPromise: Promise<WebSocket> | null = null;

  /** Opens (or reuses) the WebSocket connection. */
  private connect(): Promise<WebSocket> {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      return Promise.resolve(this.ws);
    }

    if (this.connectPromise) {
      return this.connectPromise;
    }

    this.connectPromise = new Promise<WebSocket>((resolve, reject) => {
      const protocol = window.location.protocol === "https:" ? "wss" : "ws";
      const url = `${protocol}://${window.location.host}${WS_PATH}`;
      const socket = new WebSocket(url);

      socket.onopen = () => {
        this.ws = socket;
        this.connectPromise = null;
        resolve(socket);
      };

      socket.onmessage = (event) => {
        this.handleMessage(event.data as string);
      };

      socket.onclose = (event) => {
        this.ws = null;
        this.connectPromise = null;
        if (this.pendingCallbacks) {
          if (!event.wasClean) {
            this.pendingCallbacks.onError(
              `Connection closed unexpectedly (code ${event.code})`,
            );
          }
          this.pendingCallbacks.onDone();
          this.pendingCallbacks = null;
        }
      };

      socket.onerror = () => {
        this.ws = null;
        this.connectPromise = null;
        if (this.pendingCallbacks) {
          this.pendingCallbacks.onError("WebSocket connection error");
          this.pendingCallbacks.onDone();
          this.pendingCallbacks = null;
        }
        reject(new Error("WebSocket connection failed"));
      };
    });

    return this.connectPromise;
  }

  private handleMessage(raw: string): void {
    const callbacks = this.pendingCallbacks;
    if (!callbacks) {
      return;
    }

    let msg: ServerMessage;
    try {
      msg = JSON.parse(raw) as ServerMessage;
    } catch {
      callbacks.onError(`Failed to parse server message: ${raw}`);
      return;
    }

    switch (msg.type) {
      case "token":
        callbacks.onToken(msg.content);
        break;
      case "done":
        this.pendingCallbacks = null;
        callbacks.onDone();
        break;
      case "error":
        callbacks.onError(msg.content ?? "Unknown error");
        break;
    }
  }

  /**
   * Sends a user message and streams the assistant reply via `callbacks`.
   *
   * Only one stream may be active at a time.  Calling `stream()` while a
   * previous one is in progress will invoke the previous callbacks' `onError`.
   *
   * @returns a cancel function that aborts the current stream by closing and
   *          reopening the underlying connection.
   */
  async stream(
    userMessage: string,
    callbacks: ChatStreamCallbacks,
  ): Promise<() => void> {
    if (this.pendingCallbacks) {
      this.pendingCallbacks.onError("Superseded by a new request");
      this.pendingCallbacks.onDone();
      this.pendingCallbacks = null;
    }

    let socket: WebSocket;
    try {
      socket = await this.connect();
    } catch {
      callbacks.onError("Unable to connect to the assistant");
      callbacks.onDone();
      return () => {};
    }

    this.pendingCallbacks = callbacks;
    socket.send(userMessage);

    const cancel = () => {
      if (this.pendingCallbacks === callbacks) {
        this.pendingCallbacks = null;
      }
      if (this.ws) {
        this.ws.close(1000, "Cancelled");
        this.ws = null;
      }
      callbacks.onDone();
    };

    return cancel;
  }

  /** Closes the underlying WebSocket connection. */
  close(): void {
    if (this.ws) {
      this.ws.close(1000, "Client closed");
      this.ws = null;
    }
  }
}

/** Singleton client — reuse one connection across all chat turns. */
export const chatWebSocket = new ChatWebSocketClient();
