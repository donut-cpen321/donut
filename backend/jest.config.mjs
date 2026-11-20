export default {
  preset: "ts-jest",
  testEnvironment: "node",
  moduleNameMapper: {
    "^(\\.{1,2}/.*)\\.js$": "$1",
  },
  roots: ["<rootDir>/src", "<rootDir>/tests"],
  // The NFR tests time the app, so they run on their own: npm run test:nfr (jest.nfr.config.mjs)
  testPathIgnorePatterns: ["/node_modules/", "/tests/nfr/"],
  collectCoverageFrom: ["src/**/*.ts"],
  collectCoverage: true,
  coverageDirectory: "coverage",
  coverageReporters: ["text", "lcov", "html", "text-summary"],
  coveragePathIgnorePatterns: [
    "/node_modules/",
    "/dist/",
    "/coverage/",
    "/src/generated/",
  ],
};
