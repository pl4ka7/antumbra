"use strict";

// Reader extraction on request from the app. Nothing runs until the app asks.
(function () {
  let port;
  try {
    port = browser.runtime.connectNative("antumbra");
  } catch (e) {
    return;
  }

  const SKIP = "nav, footer, aside, header, form, menu, [role=navigation], [role=complementary], [aria-hidden=true]";
  const MAX_CHARS = 120000;

  function visible(el) {
    const style = window.getComputedStyle(el);
    return style.display !== "none" && style.visibility !== "hidden";
  }

  function textOf(el) {
    return (el.innerText || "").replace(/\s+/g, " ").trim();
  }

  function pickRoot() {
    const explicit = document.querySelector("article, main, [role=main]");
    if (explicit && textOf(explicit).length > 400) return explicit;
    let best = document.body;
    let bestScore = 0;
    for (const el of document.querySelectorAll("div, section")) {
      let score = 0;
      for (const p of el.querySelectorAll(":scope > p")) score += textOf(p).length;
      if (score > bestScore) {
        bestScore = score;
        best = el;
      }
    }
    return best;
  }

  function extract() {
    const root = pickRoot();
    const blocks = [];
    let total = 0;
    for (const el of root.querySelectorAll("h1, h2, h3, p, li, blockquote, pre")) {
      if (el.closest(SKIP) || !visible(el)) continue;
      if (el.tagName !== "PRE" && el.querySelector("p, li, blockquote, pre")) continue;
      const text = el.tagName === "PRE" ? (el.innerText || "").trim() : textOf(el);
      if (!text) continue;
      if (el.tagName === "P" && text.length < 25) continue;
      blocks.push({ t: el.tagName.toLowerCase(), x: text });
      total += text.length;
      if (total > MAX_CHARS) break;
    }
    const byline = document.querySelector("[rel=author], .author, [itemprop=author]");
    return {
      title: document.title || "",
      site: location.hostname,
      url: location.href,
      byline: byline ? textOf(byline).slice(0, 120) : "",
      blocks: blocks,
    };
  }

  port.onMessage.addListener((message) => {
    if (message.type === "extract") {
      let article;
      try {
        article = extract();
      } catch (e) {
        article = { title: document.title || "", site: location.hostname, url: location.href, byline: "", blocks: [] };
      }
      article.type = "article";
      article.id = message.id;
      port.postMessage(article);
    }
  });
})();
