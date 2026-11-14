/**
 * Allowlist HTML sanitizer for model-authored `<html>` blocks.
 *
 * Chat content is attacker-influenced: a prompt, a tool result, or a scraped page can end up in
 * an `<html>` block. Rendering that with `dangerouslySetInnerHTML` without filtering is a direct
 * XSS path, so every string that reaches the DOM here is rebuilt from an allowlist of tags and
 * attributes. Scripts, event handlers, `javascript:` URLs, `<iframe>`, `<object>`, and `<style>`
 * are dropped.
 *
 * Parsing happens in an inert document (`DOMParser` with `text/html`), whose nodes never load
 * resources or run handlers, so this is safe to call on untrusted input.
 */

const ALLOWED_TAGS = new Set([
  'a', 'abbr', 'b', 'blockquote', 'br', 'caption', 'code', 'col', 'colgroup', 'dd', 'del',
  'div', 'dl', 'dt', 'em', 'figcaption', 'figure', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'hr',
  'i', 'img', 'ins', 'kbd', 'li', 'mark', 'ol', 'p', 'pre', 'q', 's', 'samp', 'small', 'span',
  'strong', 'sub', 'sup', 'table', 'tbody', 'td', 'tfoot', 'th', 'thead', 'tr', 'u', 'ul', 'var'
]);

const ALLOWED_ATTRS = new Set([
  'alt', 'class', 'colspan', 'datetime', 'height', 'href', 'rowspan', 'src', 'style', 'title',
  'width'
]);

const SAFE_URL = /^(?:https?:|mailto:|tel:|#|\/|\.\/|\.\.\/)/i;

/** Style declarations are dropped unless both the property and the value look inert. */
const UNSAFE_STYLE = /(?:expression|javascript:|url\s*\(|@import|behavior\s*:|-moz-binding)/i;

function cleanAttributes(el: Element): void {
  for (const attr of Array.from(el.attributes)) {
    const name = attr.name.toLowerCase();
    const value = attr.value;

    if (name.startsWith('on') || !ALLOWED_ATTRS.has(name)) {
      el.removeAttribute(attr.name);
      continue;
    }

    if (name === 'href' || name === 'src') {
      const trimmed = value.trim().replace(/[\u0000-\u001f\u007f]/g, '');
      if (!SAFE_URL.test(trimmed)) {
        el.removeAttribute(attr.name);
      }
      continue;
    }

    if (name === 'style' && UNSAFE_STYLE.test(value)) {
      el.removeAttribute(attr.name);
    }
  }
}

function cleanNode(node: Node, out: Node[]): void {
  if (node.nodeType === Node.TEXT_NODE) {
    out.push(node.cloneNode(true));
    return;
  }

  if (node.nodeType !== Node.ELEMENT_NODE) {
    return;
  }

  const el = node as Element;
  const tag = el.tagName.toLowerCase();

  if (!ALLOWED_TAGS.has(tag)) {
    // Keep the text of a dropped element so content is not silently lost, but never its tags.
    for (const child of Array.from(el.childNodes)) {
      cleanNode(child, out);
    }
    return;
  }

  const clean = document.createElement(tag);
  for (const attr of Array.from(el.attributes)) {
    clean.setAttribute(attr.name, attr.value);
  }
  cleanAttributes(clean);

  // Rebuild children into the clean element.
  const childOut: Node[] = [];
  for (const child of Array.from(el.childNodes)) {
    cleanNode(child, childOut);
  }
  clean.replaceChildren(...childOut);
  out.push(clean);
}

/**
 * Returns HTML that is safe to place in `dangerouslySetInnerHTML`.
 * Unknown markup is unwrapped to its text, disallowed attributes are stripped.
 */
export function sanitizeChatHtml(html: string): string {
  if (!html) {
    return '';
  }

  const doc = new DOMParser().parseFromString(html, 'text/html');
  const container = doc.createElement('div');

  for (const child of Array.from(doc.body.childNodes)) {
    const out: Node[] = [];
    cleanNode(child, out);
    for (const n of out) {
      container.append(n);
    }
  }

  return container.innerHTML;
}
