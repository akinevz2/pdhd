import React from "react";
import { createRoot } from "react-dom/client";
import { App } from "./App";
import "./theme.css";

const mount = document.getElementById("app");
if (!mount) {
  throw new Error("Missing #app mount element");
}

createRoot(mount).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
