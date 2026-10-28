import type { Block, Content } from "./schema";

function blockText(block: Block): string {
  switch (block.t) {
    case "p":
      return block.c.map((inline) => inline.v).join("");
    case "math_block":
      return block.v;
    case "image":
      return block.alt;
  }
}

/**
 * Liste önizlemeleri için düz metin; LaTeX kaynak olarak kalır (render edilmez).
 * QuestionBody dışında LaTeX render edilmez (kurallar.md A12).
 */
export function toPlainText(content: Content): string {
  return content.blocks
    .map(blockText)
    .filter((text) => text.length > 0)
    .join("\n");
}
