const TOOLCALL_TAG_PATTERN =
  /\[toolcall\s+([^\]]*?)\]([\s\S]*?)\[\/toolcall\]/gi;
const ATTRIBUTE_PATTERN = /(\w+)=(?:"([^"]*)"|'([^']*)'|([^\s\]]+))/g;

function parseAttributes(rawAttributes: string): Record<string, string> {
  const attributes: Record<string, string> = {};
  let match: RegExpExecArray | null;

  while ((match = ATTRIBUTE_PATTERN.exec(rawAttributes)) !== null) {
    const [, key, doubleQuoted, singleQuoted, bareValue] = match;
    attributes[key] = doubleQuoted || singleQuoted || bareValue || "";
  }

  ATTRIBUTE_PATTERN.lastIndex = 0;
  return attributes;
}

export function normalizeToolCallMarkup(content: string): string {
  if (!content || !content.includes("[toolcall")) {
    return content;
  }

  return content.replace(
    TOOLCALL_TAG_PATTERN,
    (_fullMatch, rawAttributes, rawLabel) => {
      const attributes = parseAttributes(String(rawAttributes || ""));
      const prompt = (attributes.prompt || "").trim();
      const label = String(rawLabel || "").trim();

      if (!prompt || !label) {
        return label || "";
      }

      return (
        "```assistant-action\n" + JSON.stringify({ label, prompt }) + "\n```"
      );
    },
  );
}
