# Code Review

## 1. Change History

| **Change Date**   | **Modified Sections** | **Rationale** |
| ----------------- | --------------------- | ------------- |
| 11-03-2026        | 2                     | Added the Codacy results for `main` at `e9776ef` |
| 11-03-2026        | 2                     | Updated to `cd0a483`, where Codacy reports 0 unfixed issues |
| 11-20-2026        | 3.2, 3.3              | Recorded the fault the other group reported (fabricated scores moving ratings) and how score confirmation, the set score rule and the started-game check fix it |
| 11-20-2026        | 3.1, 3.2, 3.3         | Added our review of TimeTravel, Fabricate's scores and both review PDFs, and the actions taken on Fabricate's other findings |

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

- **Peer group reviewed:** TimeTravel ([abhiverma13/TimeTravel](https://github.com/abhiverma13/TimeTravel))
- **Commit SHA reviewed:** [`86b568c57cb1fac7c5393c09b821071e55f5483e`](https://github.com/abhiverma13/TimeTravel/commit/86b568c57cb1fac7c5393c09b821071e55f5483e)
- **Date the artifacts were exchanged:** Nov 11, 2026
- **Review report submitted:** [`CP3_TimeTravel_Review.pdf`](reviews/CP3_TimeTravel_Review.pdf)

| **Section**          | **Item**                                                                                                                                             | **Score** |
| -------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------- | --------- |
| Manual code review   | Code is maintainable (well-documented, uses self-explanatory variable names, does not have complicated and long methods, magic numbers, etc.), efficient, handles error cases well | 7.25/10      |
| Manual test review   | Tests are complete (all APIs exposed to the front-end are tested, three main features are tested), errors and edge cases are thoroughly tested, correct assertions are used | 8.5/10      |
| Manual test review   | Test code is maintainable and well-structured                                                                                                          | 10/10      |
| Manual test review   | Test implementation matches the requirements and design                                                                                                | 9.7/10      |
| Manual test review   | Non-functional requirements are tested well                                                                                                            | 8.7/10      |
| Manual test review   | Tests achieve high coverage and cases of < 100% coverage are well-justified                                                                            | 9.5/10      |
| Manual test review   | All back-end tests can be run automatically                                                                                                            | 10/10      |
| Automated code review| Codacy runs with the required setup                                                                                                                    | 5/10      |
| Automated code review| All remaining Codacy issues are well-justified                                                                                                          | 6/10      |

**Major fault reported**

- **Description:** A removed group member keeps getting the other members' live locations. The server only checks that a user is in the group when they subscribe to live updates, so a member removed while their map is open keeps receiving every location shared afterwards.
- **Steps to reproduce:**
  1. User A creates a group, and User B joins it.
  2. User B opens the map.
  3. User A removes User B from the group.
  4. User A opens the map and shares their location.
  5. User B still sees User A's live location, for as long as they keep the map open.
- **Screenshots:** User A's map (left) and User B's map after being removed (right), still showing User A's location: page 6 of [`CP3_TimeTravel_Review.pdf`](reviews/CP3_TimeTravel_Review.pdf).
- **Severity:** High: a privacy and security issue, since someone removed from a group can keep tracking its members' locations.

### 3.2. Review You Received

- **Reviewing group:** Fabricate
- **Commit SHA that was reviewed:** [`0ab1a7c34a4bd2c580b44b444c305310fdd5bb88`](https://github.com/donut-cpen321/donut/commit/0ab1a7c34a4bd2c580b44b444c305310fdd5bb88)
- **Review report received:** [`CP3_Donut_Review.pdf`](reviews/CP3_Donut_Review.pdf)

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
- **Severity:** High: it breaks the ELO ranking and the leaderboard, two core features, and matchmaking uses the wrong ratings to pick opponents.

### 3.3. Actions Taken in Response to the Review

| **Finding** | **Action Taken** | **Commit / Location in Git** |
| ----------- | ---------------- | ---------------------------- |
| One player can set the final score alone | Score confirmation: a reported score waits until every player of the game confirms it (`POST /games/score/confirm`), and only then is the game completed and rated. A player who disagrees suggests a correction, which everyone confirms again | `81ba1c5`, `2f1a0f9` (backend), `68277a7`, `22e442a` (app); [`backend/src/games/games.repository.ts`](https://github.com/donut-cpen321/donut/blob/8e04061/backend/src/games/games.repository.ts) (`recordScore`) |
| Set scores aren't validated, so 99–0 is accepted | Past 6 games a set must be won by exactly 2 games, or be 7–6 after a tiebreak, checked by the backend and the app's score form. Each set's games difference is now at most 6, so the rating's margin term is bounded: 99–0 can't be entered, and a long set like 99–97 counts like 8–6 | `86dc2e5`; [`backend/src/games/games.schemas.ts`](https://github.com/donut-cpen321/donut/blob/8e04061/backend/src/games/games.schemas.ts) (`isLegalSet`), [`frontend/.../gamedetail/ScoreInput.kt`](https://github.com/donut-cpen321/donut/blob/8e04061/frontend/app/src/main/java/com/example/cpen321application/feature/gamedetail/ScoreInput.kt) |
| A game nobody accepted, or that hasn't started, can be scored | A game is scored only once a player started it (`IN_PROGRESS`), and only a booked game, every player having accepted, can be started (`NOT_BOOKED` otherwise). Tests now cover scoring an open, a booked and an unaccepted matched game (`GAME_NOT_STARTED`) | `d28ff7e`, `38cd2d4`; tests in `86dc2e5` |
| No tests for the above | Tests for confirming, correcting and refusing a stale confirmation (mocked and against Postgres), for the set rule on both score routes and in the app, for 99–97 rating like 8–6, and for the unaccepted matched game | [`backend/tests/mock/games.score.test.ts`](https://github.com/donut-cpen321/donut/blob/8e04061/backend/tests/mock/games.score.test.ts), [`backend/tests/no-mock/playerStats.test.ts`](https://github.com/donut-cpen321/donut/blob/8e04061/backend/tests/no-mock/playerStats.test.ts), [`backend/tests/unit/elo.test.ts`](https://github.com/donut-cpen321/donut/blob/8e04061/backend/tests/unit/elo.test.ts), [`frontend/.../ScoreInputTest.kt`](https://github.com/donut-cpen321/donut/blob/8e04061/frontend/app/src/test/java/com/example/cpen321application/feature/gamedetail/ScoreInputTest.kt) |
| No NFR tests or logs (NFR item, 3/10) | Tests for every NFR (API response time with 50 concurrent users, match found latency, rating accuracy for new players), run by `npm run test:nfr` and in GitHub Actions, with their logs in Testing.md §3 | `bfb755e`, `1ea591d`; [`backend/tests/nfr/`](https://github.com/donut-cpen321/donut/tree/8e04061/backend/tests/nfr) |
| 23 of 37 interfaces have no no-mock test (tests item, 9/10) | No-mock tests for every interface, against a real Postgres database, including the live update streams | `bfb755e`, `7018638`; Testing.md §2.1.1 |
| Coverage is 47% without mocks, the screenshots are placeholders, and gaps aren't justified (coverage item, 7/10) | Real coverage screenshots with and without mocks, and every line still uncovered justified in Testing.md §2.3–2.4 | `bfb755e`, `1ea591d`; [`documentation/images/`](https://github.com/donut-cpen321/donut/tree/8e04061/documentation/images) |
| `games.test.ts` is 2,577 lines, and the no-mock tests depend on running in order (test code item, 8/10) | Split into five files by route group with shared fixtures. Every no-mock test creates its own users and data (`tests/realDb.ts`) and deletes them after, so the suites pass in any order (`--randomize`) | `bfb755e`; [`backend/tests/mock/`](https://github.com/donut-cpen321/donut/tree/8e04061/backend/tests/mock), [`backend/tests/realDb.ts`](https://github.com/donut-cpen321/donut/blob/8e04061/backend/tests/realDb.ts) |
| Testing.md's commit hash and links are stale (requirements item, 8/10) | Testing.md points at the commit where the tests run, and its links use that commit | `1ea591d`; Testing.md §2.1.2 |

- **Findings not acted on.** For each finding you decided not to address, give the reason:
  1. **Finding:** Cap the margin-of-victory term in `elo.ts`.
     - **Justification:** The set rule already bounds the margin: no set's games difference can exceed 6, so a score can't move ratings further than a real lopsided match (6–0, 6–0). A cap on top would also shrink the effect of real lopsided wins, so we kept the rating formula as designed.
