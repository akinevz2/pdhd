const path = require("path");
const CopyWebpackPlugin = require("copy-webpack-plugin");

module.exports = {
  entry: "./src/index.ts",
  module: {
    rules: [
      {
        test: /\.tsx?$/,
        use: "ts-loader",
        exclude: /node_modules/,
      },
    ],
  },
  resolve: {
    extensions: [".tsx", ".ts", ".js"],
  },
  output: {
    filename: "bundle.js",
    path: path.resolve(__dirname, "dist"),
    clean: true,
  },
  plugins: [
    new CopyWebpackPlugin({
      patterns: [
        {
          from: path.resolve(__dirname),
          to: path.resolve(__dirname, "dist"),
          globOptions: {
            ignore: [
              "**/dist/**",
              "**/src/**",
              "**/node_modules/**",
              "**/webpack.config.js",
              "**/tsconfig.json",
              "**/package.json",
              "**/package-lock.json",
            ],
          },
          noErrorOnMissing: true,
        },
      ],
    }),
  ],
  mode: "development",
  devtool: "source-map",
};
