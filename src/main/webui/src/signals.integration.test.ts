import { beforeAll, beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("./api", () => {
  class HttpResponseError extends Error {
    statusCode: number;
    contentType: string | null;
    responseText: string;

    constructor(
      statusCode: number,
      message: string,
      contentType: string | null,
      responseText: string,
    ) {
      super(message);
      this.name = "HttpResponseError";
      this.statusCode = statusCode;
      this.contentType = contentType;
      this.responseText = responseText;
    }
  }

  return {
    HttpResponseError,
    apiDetailed: vi.fn(async (endpoint: string) => ({
      data: { endpoint, method: "GET" },
      contentType: "application/json",
      status: 200,
    })),
    apiWithTimeoutDetailed: vi.fn(async (endpoint: string, timeoutMs: number) => ({
      data: { endpoint, method: "GET_TIMEOUT", timeoutMs },
      contentType: "application/json",
      status: 200,
    })),
    apiDeleteDetailed: vi.fn(async (endpoint: string) => ({
      data: { endpoint, method: "DELETE" },
      contentType: "application/json",
      status: 200,
    })),
    apiPostDetailed: vi.fn(async (endpoint: string, body: unknown) => ({
      data: { endpoint, method: "POST", body },
      contentType: "application/json",
      status: 200,
    })),
    apiPutDetailed: vi.fn(async (endpoint: string, body: unknown) => ({
      data: { endpoint, method: "PUT", body },
      contentType: "application/json",
      status: 200,
    })),
  };
});

import {
  apiDeleteDetailed,
  apiDetailed,
  apiPostDetailed,
  apiPutDetailed,
  apiWithTimeoutDetailed,
} from "./api";
import { SIGNALS, getApiSignalDefinitions } from "./signalDefinitions";
import { emitApiSignal, registerApiSignals } from "./signals";

type Transport = "GET" | "GET_TIMEOUT" | "POST" | "PUT" | "DELETE";

type SignalCase = {
  key: string;
  payload?: unknown;
  endpoint: string;
  transport: Transport;
};

const CASES: SignalCase[] = [
  {
    key: SIGNALS.TELEMETRY,
    endpoint: "/api/telemetry",
    transport: "GET",
  },
  {
    key: SIGNALS.WORKSPACE,
    payload: { path: "/tmp/work" },
    endpoint: "/api/workspace?path=%2Ftmp%2Fwork",
    transport: "GET_TIMEOUT",
  },
  {
    key: SIGNALS.WORKSPACE_LIST,
    endpoint: "/api/workspace/list",
    transport: "GET",
  },
  {
    key: SIGNALS.PROJECT_OPEN,
    payload: { directory: "/repo/demo" },
    endpoint: "/api/project/open",
    transport: "POST",
  },
  {
    key: SIGNALS.PROJECT_CLOSE,
    payload: { projectId: 11 },
    endpoint: "/api/project/close",
    transport: "DELETE",
  },
  {
    key: SIGNALS.PROJECT_REMOTE,
    payload: { projectId: 11 },
    endpoint: "/api/project/remote",
    transport: "POST",
  },
  {
    key: SIGNALS.PROJECT_BROWSE,
    payload: { projectId: 11, parentUuid: "abc-123" },
    endpoint: "/api/project/browse",
    transport: "POST",
  },
  {
    key: SIGNALS.PROJECT_FILE,
    payload: { projectId: 11, entryUuid: "uuid with space" },
    endpoint: "/api/project/file",
    transport: "POST",
  },
  {
    key: SIGNALS.SUMMARY_FOLDER,
    payload: { projectId: 11, entryUuid: "folder uuid" },
    endpoint: "/api/summary/folder",
    transport: "PUT",
  },
  {
    key: SIGNALS.SUMMARY_FILE,
    payload: { projectId: 11, entryUuid: "file uuid" },
    endpoint: "/api/summary/file",
    transport: "PUT",
  },
  {
    key: SIGNALS.SUMMARY_STATUS,
    payload: { projectId: 11, entryUuid: "status uuid" },
    endpoint: "/api/summary/status",
    transport: "PUT",
  },
  {
    key: SIGNALS.CHAT_STREAM,
    payload: { message: "test" },
    endpoint: "/api/chat",
    transport: "POST",
  },
  {
    key: SIGNALS.CHAT_RESET,
    payload: {},
    endpoint: "/api/chat",
    transport: "DELETE",
  },
];

describe("signals integration coverage", () => {
  beforeAll(() => {
    registerApiSignals(getApiSignalDefinitions());
  });

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("registers one definition per signal key", () => {
    const registered = getApiSignalDefinitions();
    const signalKeyCount = Object.keys(SIGNALS).length;
    expect(registered).toHaveLength(signalKeyCount);
  });

  for (const testCase of CASES) {
    it(`routes ${testCase.key} via ${testCase.transport}`, async () => {
      const result = await emitApiSignal<any, { endpoint: string; method: string }>(
        testCase.key as any,
        testCase.payload as any,
      );

      expect(result.endpoint).toBe(testCase.endpoint);
      expect(result.method).toBe(testCase.transport);

      if (testCase.transport === "GET") {
        expect(apiDetailed).toHaveBeenCalledWith(testCase.endpoint);
      }
      if (testCase.transport === "GET_TIMEOUT") {
        expect(apiWithTimeoutDetailed).toHaveBeenCalledWith(
          testCase.endpoint,
          5000,
        );
      }
      if (testCase.transport === "POST") {
        expect(apiPostDetailed).toHaveBeenCalled();
      }
      if (testCase.transport === "PUT") {
        expect(apiPutDetailed).toHaveBeenCalled();
      }
      if (testCase.transport === "DELETE") {
        expect(apiDeleteDetailed).toHaveBeenCalled();
      }
    });
  }
});
