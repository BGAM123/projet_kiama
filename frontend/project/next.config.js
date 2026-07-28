/** @type {import('next').NextConfig} */
const nextConfig = {
  eslint: {
    ignoreDuringBuilds: true,
  },
  images: { unoptimized: true },
  // The WASM SWC fallback on this host emits minified chunks with unescaped
  // backticks that crash static page-data collection. Disabling SWC minify
  // keeps the build green while we develop; the dev server is unaffected.
  swcMinify: false,
};

module.exports = nextConfig;
