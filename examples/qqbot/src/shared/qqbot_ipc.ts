import * as QQBotRuntime from "./qqbot_runtime";
import * as QQBotAutoReply from "./qqbot_auto_reply";

const QQBOT_CONTEXT_RUN_IPC_CHANNEL = "qqbot.context.run";

type QQBotContextRunner<TResult extends object> = () => TResult | Promise<TResult>;
type QQBotContextFunction = (...args: readonly object[]) => object | Promise<object>;
type QQBotContextModuleValue = QQBotContextFunction | object | string | number | boolean | null | undefined;
type QQBotContextModule = Record<string, QQBotContextModuleValue>;

type QQBotContextRunPayload = {
  /** Name of a function registered through the context module, e.g. "qqbot_service_start". */
  operation: string;
  /** Serialized arguments forwarded to that function. */
  args: readonly object[];
  envs: object;
};

const qqbotContextFunctions: Record<string, QQBotContextFunction> = {};

function previewJson(
  value: object | string | number | boolean | null | undefined,
  maxLength = 800
): string {
  try {
    const text = JSON.stringify(value);
    if (typeof text !== "string") {
      return "";
    }
    return text.length > maxLength ? `${text.slice(0, maxLength)}...` : text;
  } catch (error) {
    const errorText = error instanceof Error ? error.message : "preview failed";
    console.error(`[qqbot_ipc] preview json failed: ${errorText}`);
    return "[unserializable]";
  }
}

function validateContextEnvName(name: string): void {
  if (!/^[A-Za-z_$][A-Za-z0-9_$]*$/.test(name)) {
    throw new Error(`withContext env name is not a valid identifier: ${name}`);
  }
}

async function executeContextRunner(payload: QQBotContextRunPayload): Promise<object> {
  const envs = payload.envs;
  try {
    // Only names that were explicitly registered can be invoked. No source text is evaluated,
    // so a malicious IPC message can at worst call a known bot function with data it controls.
    const operation = payload.operation;
    if (typeof operation !== "string" || !Object.prototype.hasOwnProperty.call(qqbotContextFunctions, operation)) {
      throw new Error(`withContext unknown operation: ${String(operation)}`);
    }
    const runner = qqbotContextFunctions[operation];
    if (typeof runner !== "function") {
      throw new Error(`withContext operation is not callable: ${operation}`);
    }

    const args = Array.isArray(payload.args) ? payload.args : [];
    const runnerWithEnvs = Object.assign(runner, { __qqbotContextEnvs: envs });
    return await runnerWithEnvs(...args);
  } catch (error) {
    const errorText = error instanceof Error ? error.message : "withContext runner failed";
    console.error(
      `[qqbot_ipc] withContext target execution failed: error=${errorText}, envs=${previewJson(envs)}`
    );
    throw error;
  }
}

function registerQQBotContextModule(moduleExports: QQBotContextModule): void {
  Object.keys(moduleExports).forEach((name) => {
    validateContextEnvName(name);
    const value = moduleExports[name];
    const candidate = value as QQBotContextFunction;
    if (typeof candidate === "function") {
      qqbotContextFunctions[name] = candidate;
    }
  });
}

let qqbotContextRunnerRegistered = false;

function registerQQBotContextRunner(): void {
  if (qqbotContextRunnerRegistered) {
    return;
  }
  qqbotContextRunnerRegistered = true;
  ToolPkg.ipc.on<QQBotContextRunPayload, object>(
    QQBOT_CONTEXT_RUN_IPC_CHANNEL,
    async (payload) => await executeContextRunner(payload)
  );
}

type QQBotContextOperationSignatures = typeof QQBotRuntime & typeof QQBotAutoReply;

const qqbotContextOperationSignatures: QQBotContextOperationSignatures = {
  ...QQBotRuntime,
  ...QQBotAutoReply
};

registerQQBotContextRunner();
registerQQBotContextModule(QQBotRuntime as QQBotContextModule);
registerQQBotContextModule(QQBotAutoReply as QQBotContextModule);

async function runWithContext<TResult extends object>(
  kind: ToolPkg.RuntimeKind,
  envs: object,
  operation: string,
  args: readonly object[]
): Promise<TResult> {
  const payload: QQBotContextRunPayload = {
    operation,
    args,
    envs
  };
  try {
    return await ToolPkg.ipc.call<QQBotContextRunPayload, TResult>(
      QQBOT_CONTEXT_RUN_IPC_CHANNEL,
      payload,
      {
        targetRuntime: kind
      }
    );
  } catch (error) {
    const errorText = error instanceof Error ? error.message : "withContext call failed";
    console.error(
      `[qqbot_ipc] withContext call failed: kind=${kind}, error=${errorText}, envs=${previewJson(envs)}`
    );
    throw error;
  }
}

type QQBotOperationName = keyof typeof qqbotContextOperationSignatures;

/**
 * Return type of a registered context operation, so `withContext` keeps the concrete result type
 * of the named function instead of falling back to `object`.
 */
type QQBotOperationResult<TName extends string> = TName extends keyof typeof qqbotContextOperationSignatures
  ? Awaited<ReturnType<(typeof qqbotContextOperationSignatures)[TName]>>
  : object;

export function withContext<TName extends QQBotOperationName>(
  kind: ToolPkg.RuntimeKind,
  envs: object,
  operation: TName,
  args?: readonly object[]
): Promise<QQBotOperationResult<TName>>;

export function withContext<TResult extends object>(
  kind: ToolPkg.RuntimeKind,
  envs: object,
  operation: string,
  args?: readonly object[]
): Promise<TResult>;

export function withContext<TResult extends object>(
  kind: ToolPkg.RuntimeKind,
  envs: object,
  operationOrRunner?: string | QQBotContextRunner<TResult>,
  args?: readonly object[]
): Promise<TResult> {
  if (typeof operationOrRunner !== "string" || operationOrRunner.length === 0) {
    throw new Error(
      "withContext requires the name of a registered operation (executable source text is not accepted)"
    );
  }
  return runWithContext<TResult>(kind, envs, operationOrRunner, args ?? []);
}

export * from "./qqbot_runtime";
export * from "./qqbot_auto_reply";
