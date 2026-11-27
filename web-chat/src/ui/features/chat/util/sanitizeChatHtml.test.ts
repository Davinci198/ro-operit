import { afterEach, describe, expect, it, vi } from 'vitest';

import { sanitizeChatHtml } from './sanitizeChatHtml';

/**
 * The sanitizer is the only thing between model-authored `<html>` blocks and
 * `dangerouslySetInnerHTML` (see `CustomXmlRenderer.tsx`), so these tests pin the allowlist
 * behaviour instead of trusting the comments at the top of the module.
 */

function textOf(html: string): string {
  return new DOMParser().parseFromString(html, 'text/html').body.textContent ?? '';
}

describe('sanitizeChatHtml', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('returns an empty string for empty input', () => {
    expect(sanitizeChatHtml('')).toBe('');
  });

  it('keeps allowlisted tags, attributes and text', () => {
    const result = sanitizeChatHtml('<p class="lead" title="t">hello <b>world</b></p>');

    expect(result).toContain('<p class="lead" title="t">');
    expect(result).toContain('<b>world</b>');
    expect(textOf(result)).toBe('hello world');
  });

  it('keeps table structure and cell attributes', () => {
    const result = sanitizeChatHtml(
      '<table><thead><tr><th scope="col">h</th></tr></thead><tbody><tr><td colspan="2" rowspan="1">c</td></tr></tbody></table>'
    );

    expect(result).toContain('<table>');
    expect(result).toContain('<td colspan="2" rowspan="1">c</td>');
    // `scope` is not on the attribute allowlist.
    expect(result).not.toContain('scope');
  });

  it('unwraps disallowed elements but keeps their text', () => {
    const result = sanitizeChatHtml('<section><marquee>deep text</marquee></section>');

    expect(result).not.toContain('<section');
    expect(result).not.toContain('<marquee');
    expect(result.trim()).toBe('deep text');
  });

  it('drops a top-level script element together with its source', () => {
    // The HTML parser routes a leading `<script>` into `<head>`, and only `document.body` is
    // walked, so nothing of it survives. Fail-closed is the behaviour we want here.
    expect(sanitizeChatHtml('<script>alert(1)</script>')).toBe('');
  });

  it('unwraps a nested script element to escaped text', () => {
    const result = sanitizeChatHtml('<div>before<script>alert(1)</script>after</div>');

    expect(result).not.toContain('<script');
    expect(result).not.toContain('</script>');
    expect(result).toContain('before');
    expect(result).toContain('after');
    expect(textOf(result)).toBe('beforealert(1)after');
    // The script source survived only as text: re-parsing the output yields the div and nothing else.
    const reparsed = new DOMParser().parseFromString(result, 'text/html').body;
    expect(reparsed.children).toHaveLength(1);
    expect(reparsed.children[0].tagName).toBe('DIV');
  });

  it('drops iframe, object, embed, style and form elements', () => {
    const result = sanitizeChatHtml(
      '<iframe src="https://evil.example"></iframe><object data="x"></object><embed src="y"><style>body{color:red}</style><form action="/steal"><input name="pw"></form>'
    );

    for (const tag of ['iframe', 'object', 'embed', 'style', 'form', 'input']) {
      expect(result).not.toContain(`<${tag}`);
    }
  });

  it('removes event handler attributes', () => {
    const result = sanitizeChatHtml('<div onclick="steal()" ONMOUSEOVER="steal()">hi</div>');

    expect(result).toBe('<div>hi</div>');
  });

  it('removes attributes outside the allowlist', () => {
    const result = sanitizeChatHtml('<p id="x" data-y="1" name="n" class="c">y</p>');

    expect(result).toBe('<p class="c">y</p>');
  });

  it.each([
    ['https://example.com/a', true],
    ['http://example.com/a', true],
    ['mailto:a@example.com', true],
    ['tel:+40700000000', true],
    ['#anchor', true],
    ['/relative/path', true],
    ['./relative', true],
    ['../relative', true],
    ['javascript:alert(1)', false],
    ['JaVaScRiPt:alert(1)', false],
    ['java\tscript:alert(1)', false],
    ['data:text/html;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg==', false],
    ['vbscript:msgbox(1)', false],
    // A protocol-relative URL is an ordinary external link, not a script scheme: the allowlist
    // blocks executable schemes, it does not block the open web.
    ['//example.com/protocol-relative', true],
  ])('href "%s" keeps its href: %s', (href, kept) => {
    const result = sanitizeChatHtml(`<a href="${href}">link</a>`);

    expect(result).toContain('<a');
    expect(result.includes('href=')).toBe(kept);
    expect(textOf(result)).toBe('link');
  });

  it('applies the same URL rules to img src and keeps alt text', () => {
    const safe = sanitizeChatHtml('<img src="https://example.com/a.png" alt="a" width="10">');
    expect(safe).toContain('src="https://example.com/a.png"');
    expect(safe).toContain('alt="a"');

    const unsafe = sanitizeChatHtml('<img src="javascript:alert(1)" alt="a">');
    expect(unsafe).not.toContain('src=');
    expect(unsafe).toContain('alt="a"');
  });

  it('keeps inert style declarations and drops dangerous ones', () => {
    expect(sanitizeChatHtml('<p style="color:red">x</p>')).toContain('style="color:red"');

    for (const style of [
      'background:url(javascript:alert(1))',
      'width:expression(alert(1))',
      'behavior:url(#default#time2)',
      '-moz-binding:url(evil.xml)',
      'background:@import "evil.css"',
    ]) {
      expect(sanitizeChatHtml(`<p style="${style}">x</p>`)).not.toContain('style=');
    }
  });

  it('escapes text so it cannot become markup again', () => {
    const result = sanitizeChatHtml('<p>1 < 2 & 3 > 2</p>');

    expect(result).toContain('&lt;');
    expect(result).toContain('&amp;');
    expect(textOf(result)).toBe('1 < 2 & 3 > 2');
  });

  it('never touches the live document while sanitizing', () => {
    // Elements created through the live `document` start loading resources as soon as an
    // allowlisted `img src` is set; the rebuild must stay inside the inert parsed document.
    const createElement = vi.spyOn(document, 'createElement');

    sanitizeChatHtml('<img src="https://example.com/a.png" alt="a"><p>text</p>');

    expect(createElement).not.toHaveBeenCalled();
  });

  it('is idempotent for already-clean markup', () => {
    const once = sanitizeChatHtml('<p class="c">hello <a href="https://example.com">world</a></p>');

    expect(sanitizeChatHtml(once)).toBe(once);
  });
});
