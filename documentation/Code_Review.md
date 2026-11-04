# Code Review

## 1. Change History

| **Change Date**   | **Modified Sections** | **Rationale** |
| ----------------- | --------------------- | ------------- |
| 11-03-2026        | 2                     | Added the Codacy results for `main` at `e9776ef` |

---

## 2. Automated Code Review Results

### 2.1. Commit Hash Where Codacy Ran

[`e9776ef4e0094294ac146ad60a029ef802cf1bb7`](https://github.com/donut-cpen321/donut/commit/e9776ef4e0094294ac146ad60a029ef802cf1bb7) (`main`, merge of PR #84). Codacy reported 0 new issues and 6 fixed for this commit, and it passes the repository's quality gate.

### 2.2. Unfixed Issues per Codacy Category

| **Category**  | **Unfixed issues** |
| ------------- | ------------------ |
| Error Prone   | 1                  |
| Security      | 0                  |
| Code Style    | 0                  |
| Complexity    | 0                  |
| Best Practice | 0                  |
| Performance   | 0                  |
| Unused Code   | 0                  |
| Compatibility | 0                  |
| Documentation | 0                  |
| **Total**     | **1**              |

### 2.3. Unfixed Issues per Codacy Code Pattern

| **Code pattern**                   | **Tool** | **Severity** | **Unfixed issues** |
| ---------------------------------- | -------- | ------------ | ------------------ |
| `no-undef` (Disallow undeclared variables) | ESLint   | High         | 1                  |

### 2.4. Justifications for Unfixed Issues

- **Code Pattern: [`no-undef`](https://eslint.org/docs/latest/rules/no-undef)**

  1. **`'process' is not defined.`**

     - **Location in Git:** [`backend/prisma.config.ts#L8`](https://github.com/donut-cpen321/donut/blob/e9776ef4e0094294ac146ad60a029ef802cf1bb7/backend/prisma.config.ts#L8)
     - **Justification:** False positive. `prisma.config.ts` is only loaded by the Prisma CLI, which runs in Node, where `process` is always a global. The repository's `.eslintrc.json` enables the `node` environment, and the file declares the global with `/* global process */`, which is the fix the rule's documentation gives. With either of these, ESLint's `no-undef` passes on the file (checked locally with the rule alone, while the version without the comment fails), but Codacy's ESLint run still reports it, and the issue still points at line 7 of the file before the comment was added. The only other way to avoid the identifier is Prisma's `env()` helper, but it throws (`PrismaConfigEnvError`) when `DATABASE_URL` is unset, which breaks `npx prisma generate` in the backend's Docker build, where no database URL is set.

---

## 3. Manual Code Review Results

### 3.1. Review You Performed

- **Peer group reviewed:** [WRITE_HERE]
- **Commit SHA reviewed:** `[Insert Commit SHA here]`
- **Date the artifacts were exchanged:** [WRITE_HERE]
- **Review report submitted:** [`<CP#>_<YourPeerGroupName>_Review.pdf`](#)

| **Section**          | **Item**                                                                                                                                             | **Score** |
| -------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------- | --------- |
| Manual code review   | Code is maintainable (well-documented, uses self-explanatory variable names, does not have complicated and long methods, magic numbers, etc.), efficient, handles error cases well | x/10      |
| Manual test review   | Tests are complete (all APIs exposed to the front-end are tested, three main features are tested), errors and edge cases are thoroughly tested, correct assertions are used | x/10      |
| Manual test review   | Test code is maintainable and well-structured                                                                                                          | x/10      |
| Manual test review   | Test implementation matches the requirements and design                                                                                                | x/10      |
| Manual test review   | Non-functional requirements are tested well                                                                                                            | x/10      |
| Manual test review   | Tests achieve high coverage and cases of < 100% coverage are well-justified                                                                            | x/10      |
| Manual test review   | All back-end tests can be run automatically                                                                                                            | x/10      |
| Automated code review| Codacy runs with the required setup                                                                                                                    | x/10      |
| Automated code review| All remaining Codacy issues are well-justified                                                                                                          | x/10      |

**Major fault reported**

- **Description:** ...
- **Steps to reproduce:** ...
- **Screenshots:** _(Placeholder for screenshots of the reported fault)_
- **Severity:** ...

### 3.2. Review You Received

- **Reviewing group:** [WRITE_HERE]
- **Commit SHA that was reviewed:** `[Insert Commit SHA here]`
- **Review report received:** [`<CP#>_<YourGroupName>_Review.pdf`](#)

| **Section**          | **Item**                                                                                                                                             | **Score** |
| -------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------- | --------- |
| Manual code review   | Code is maintainable, efficient, handles error cases well                                                                                               | x/10      |
| Manual test review   | Tests are complete, errors and edge cases are thoroughly tested, correct assertions are used                                                            | x/10      |
| Manual test review   | Test code is maintainable and well-structured                                                                                                          | x/10      |
| Manual test review   | Test implementation matches the requirements and design                                                                                                | x/10      |
| Manual test review   | Non-functional requirements are tested well                                                                                                            | x/10      |
| Manual test review   | Tests achieve high coverage and cases of < 100% coverage are well-justified                                                                            | x/10      |
| Manual test review   | All back-end tests can be run automatically                                                                                                            | x/10      |
| Automated code review| Codacy runs with the required setup                                                                                                                    | x/10      |
| Automated code review| All remaining Codacy issues are well-justified                                                                                                          | x/10      |

**Major fault reported against your project**

- **Description:** ...
- **Severity:** ...

### 3.3. Actions Taken in Response to the Review

| **Finding** | **Action Taken** | **Commit / Location in Git** |
| ----------- | ---------------- | ---------------------------- |
| ...         | ...              | [`src/...`](#)               |
| ...         | ...              | ...                          |

- **Findings not acted on.** For each finding you decided not to address, give the reason:
  1. **Finding:** ...
     - **Justification:** ...
