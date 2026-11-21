# Code Review

## 1. Change History

| **Change Date**   | **Modified Sections** | **Rationale** |
| ----------------- | --------------------- | ------------- |
| 11-03-2026        | 2                     | Added the Codacy results for `main` at `e9776ef` |
| 11-03-2026        | 2                     | Updated to `cd0a483`, where Codacy reports 0 unfixed issues |
| 11-20-2026        | 3.2, 3.3              | Recorded the fault the other group reported (fabricated scores moving ratings) and how score confirmation, the set score rule and the started-game check fix it |

---

## 2. Automated Code Review Results

### 2.1. Commit Hash Where Codacy Ran

[`6c68f9a392be5417f0e9752761d51fca83be3d1e`](https://github.com/donut-cpen321/donut/commit/6c68f9a392be5417f0e9752761d51fca83be3d1e)

### 2.2. Unfixed Issues per Codacy Category

Codacy reports 0 unfixed issues in every category.

| **Category**  | **Unfixed issues** |
| ------------- | ------------------ |
| Error Prone   | 0                  |
| Security      | 0                  |
| Code Style    | 0                  |
| Complexity    | 0                  |
| Best Practice | 0                  |
| Performance   | 0                  |
| Unused Code   | 0                  |
| Compatibility | 0                  |
| Documentation | 0                  |
| **Total**     | **0**              |

### 2.3. Unfixed Issues per Codacy Code Pattern

None.

### 2.4. Justifications for Unfixed Issues

None: there are no unfixed issues.

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

- **Reviewing group:** Fabricate
- **Commit SHA that was reviewed:** `[Insert Commit SHA here]`
- **Review report received:** [`<CP#>_<YourGroupName>_Review.pdf`](#)

| **Section**          | **Item**                                                                                                                                             | **Score** |
| -------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------- | --------- |
| Manual code review   | Code is maintainable, efficient, handles error cases well                                                                                               | 8/10      |
| Manual test review   | Tests are complete, errors and edge cases are thoroughly tested, correct assertions are used                                                            | 9/10      |
| Manual test review   | Test code is maintainable and well-structured                                                                                                          | 8/10      |
| Manual test review   | Test implementation matches the requirements and design                                                                                                | 8/10      |
| Manual test review   | Non-functional requirements are tested well                                                                                                            | 3/10      |
| Manual test review   | Tests achieve high coverage and cases of < 100% coverage are well-justified                                                                            | 7/10      |
| Manual test review   | All back-end tests can be run automatically                                                                                                            | 10/10      |
| Automated code review| Codacy runs with the required setup                                                                                                                    | 10/10      |
| Automated code review| All remaining Codacy issues are well-justified                                                                                                          | 10/10      |

**Major fault reported against your project**

- **Description:** Fabricated scores: one player could set any opponent's rating. Any player of a game could report its final score alone, and it was final at once. Set scores only had to be non-negative whole numbers (the app allowed up to 99), and the rating's margin-of-victory term grows with the games difference, so a fabricated 99–0, 99–0, 99–0 moved each player's rating by about ±475 points. The reviewers also asked us to confirm that a game nobody accepted, or that hasn't started, can't be scored, since no test enforced Use Case 3's "the game has started" precondition.
- **Severity:** High

### 3.3. Actions Taken in Response to the Review

| **Finding** | **Action Taken** | **Commit / Location in Git** |
| ----------- | ---------------- | ---------------------------- |
| One player can set the final score alone | Score confirmation: a reported score waits until every player of the game confirms it (`POST /games/score/confirm`), and only then is the game completed and rated. A player who disagrees suggests a correction, which everyone confirms again | `81ba1c5`, `2f1a0f9` (backend), `68277a7`, `22e442a` (app); [`backend/src/games/games.repository.ts`](https://github.com/donut-cpen321/donut/blob/8e04061/backend/src/games/games.repository.ts) (`recordScore`) |
| Set scores aren't validated, so 99–0 is accepted | Past 6 games a set must be won by exactly 2 games, or be 7–6 after a tiebreak, checked by the backend and the app's score form. Each set's games difference is now at most 6, so the rating's margin term is bounded: 99–0 can't be entered, and a long set like 99–97 counts like 8–6 | `86dc2e5`; [`backend/src/games/games.schemas.ts`](https://github.com/donut-cpen321/donut/blob/8e04061/backend/src/games/games.schemas.ts) (`isLegalSet`), [`frontend/.../gamedetail/ScoreInput.kt`](https://github.com/donut-cpen321/donut/blob/8e04061/frontend/app/src/main/java/com/example/cpen321application/feature/gamedetail/ScoreInput.kt) |
| A game nobody accepted, or that hasn't started, can be scored | A game is scored only once a player started it (`IN_PROGRESS`), and only a booked game, every player having accepted, can be started (`NOT_BOOKED` otherwise). Tests now cover scoring an open, a booked and an unaccepted matched game (`GAME_NOT_STARTED`) | `d28ff7e`, `38cd2d4`; tests in `86dc2e5` |
| No tests for the above | Tests for confirming, correcting and refusing a stale confirmation (mocked and against Postgres), for the set rule on both score routes and in the app, for 99–97 rating like 8–6, and for the unaccepted matched game | [`backend/tests/mock/games.score.test.ts`](https://github.com/donut-cpen321/donut/blob/8e04061/backend/tests/mock/games.score.test.ts), [`backend/tests/no-mock/playerStats.test.ts`](https://github.com/donut-cpen321/donut/blob/8e04061/backend/tests/no-mock/playerStats.test.ts), [`backend/tests/unit/elo.test.ts`](https://github.com/donut-cpen321/donut/blob/8e04061/backend/tests/unit/elo.test.ts), [`frontend/.../ScoreInputTest.kt`](https://github.com/donut-cpen321/donut/blob/8e04061/frontend/app/src/test/java/com/example/cpen321application/feature/gamedetail/ScoreInputTest.kt) |

- **Findings not acted on.** For each finding you decided not to address, give the reason:
  1. **Finding:** Cap the margin-of-victory term in `elo.ts`.
     - **Justification:** The set rule already bounds the margin: no set's games difference can exceed 6, so a score can't move ratings further than a real lopsided match (6–0, 6–0). A cap on top would also shrink the effect of real lopsided wins, so we kept the rating formula as designed.
