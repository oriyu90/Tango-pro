// Karma's generated test webpack config intentionally has no output block and
// serves from /. Keep the deployment subpath out of that test-only config.
if (config.output) {
  config.output.publicPath = "/projects/tango-pro/web/";
  config.devServer = config.devServer || {};
  config.devServer.historyApiFallback = {
    index: "/projects/tango-pro/web/index.html"
  };
  config.devServer.headers = {
    "Cross-Origin-Opener-Policy": "same-origin",
    "Cross-Origin-Embedder-Policy": "require-corp",
    "Cross-Origin-Resource-Policy": "same-origin"
  };
}
