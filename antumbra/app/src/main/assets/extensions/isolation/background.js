"use strict";

// Routes each request through tor with SOCKS credentials derived from the top-level site and
// the tab, so tor builds a separate circuit per site and per tab (IsolateSOCKSAuth).
// Tabs in different identities therefore never share a circuit, even on the same site.

const SOCKS_HOST = "127.0.0.1";
const SOCKS_PORT = 9152;

// Keep in sync with core/Domains.kt
const SECOND_LEVEL = new Set([
  "co.uk", "org.uk", "ac.uk", "gov.uk", "com.au", "net.au", "org.au", "co.nz", "co.jp",
  "co.in", "co.za", "com.br", "com.mx", "com.ar", "com.tr", "com.cn", "com.tw", "co.kr",
]);

let salt = token();
const nonces = new Map();
let port = null;

function token() {
  const bytes = new Uint8Array(12);
  crypto.getRandomValues(bytes);
  return Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");
}

function hostOf(url) {
  try {
    return new URL(url).hostname;
  } catch (e) {
    return "";
  }
}

function baseDomain(host) {
  const h = host.toLowerCase().replace(/\.$/, "");
  if (!h || h.includes(":") || /^[\d.]+$/.test(h)) return h;
  const labels = h.split(".");
  if (labels.length <= 2) return h;
  const lastTwo = labels.slice(-2).join(".");
  return SECOND_LEVEL.has(lastTwo) ? labels.slice(-3).join(".") : lastTwo;
}

function firstParty(details) {
  if (details.type === "main_frame") return hostOf(details.url);
  const ancestors = details.frameAncestors;
  if (ancestors && ancestors.length) return hostOf(ancestors[ancestors.length - 1].url);
  return hostOf(details.documentUrl || details.originUrl || details.url);
}

browser.proxy.onRequest.addListener(
  (details) => {
    const domain = baseDomain(firstParty(details)) || "--";
    return {
      type: "socks",
      host: SOCKS_HOST,
      port: SOCKS_PORT,
      proxyDNS: true,
      username: domain + "|" + details.tabId,
      password: salt + (nonces.get(domain) || ""),
    };
  },
  { urls: ["<all_urls>"] }
);

browser.webRequest.onHeadersReceived.addListener(
  (details) => {
    const header = (details.responseHeaders || []).find((h) => h.name.toLowerCase() === "onion-location");
    if (!header || !header.value) return;
    try {
      const onion = new URL(header.value);
      if (onion.hostname.endsWith(".onion") && (onion.protocol === "http:" || onion.protocol === "https:")) {
        send({ type: "onion", page: details.url, onion: onion.href });
      }
    } catch (e) {
      // Malformed header: ignore.
    }
  },
  { urls: ["https://*/*"], types: ["main_frame"] },
  ["responseHeaders"]
);

function send(message) {
  if (port) port.postMessage(message);
}

function connect() {
  port = browser.runtime.connectNative("antumbra");
  port.onMessage.addListener((message) => {
    if (message.type === "rotate") {
      nonces.set(message.domain, token());
    } else if (message.type === "reset") {
      nonces.clear();
      salt = token();
    }
  });
  port.onDisconnect.addListener(() => {
    port = null;
    setTimeout(connect, 1000);
  });
  send({ type: "ready" });
}

connect();
