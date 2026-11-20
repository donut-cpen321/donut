// The non-functional requirement tests (tests/nfr), one file at a time and without coverage, so nothing else
// competes with the requests they time
import base from "./jest.config.mjs";

export default {
  ...base,
  roots: ["<rootDir>/tests/nfr"],
  testPathIgnorePatterns: ["/node_modules/"],
  collectCoverage: false,
  maxWorkers: 1,
};
