const HRS_HOSTNAME_RE = /^(?=.{1,253}$)([a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)(?:\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+$/i;

function hrsIsValidHost(host) {
  if (typeof host !== "string") return false;
  if (host.length === 0 || host.length > 253) return false;
  if (host.includes("/") || host.includes(":") || host.includes("*") || host.includes("?")) return false;
  return HRS_HOSTNAME_RE.test(host);
}

function hrsOriginPatterns(host) {
  return [`https://${host}/*`];
}

if (typeof self !== "undefined") {
  self.hrsIsValidHost = hrsIsValidHost;
  self.hrsOriginPatterns = hrsOriginPatterns;
}
