# Testing

## 1. Change History

| **Change Date**   | **Modified Sections** | **Rationale** |
| ----------------- | --------------------- | ------------- |
| 10-19-2026        | 2. Back-end Test      | Added tests   |
| 11-01-2026        | 2.1.1                 | Added tests for `GET /courts`, `GET /games`, `GET /games/feed`, `GET /games/me`, `POST /games/join` and `POST /games/leave`, the OpenWeather client and the games and courts repositories |
| 11-02-2026        | 2.1.1, 2.1.3, 2.2     | Added tests for the tournament routes, the tournament and bracket repositories and the bracket rules, updated the rows of the games routes that tournaments changed, and noted that the no-mock tests need a migrated Postgres database |
| 11-03-2026        | 2.1.1                 | Added tests for `GET /games/history`, and listed the existing tests of the friends routes |
| 11-03-2026        | 2.1.1                 | Added tests for `POST /games/invite`, and for `deleteGame` removing a game's players, rounds and equipment |
| 11-03-2026        | 2.1, 3, 4             | Listed the existing tests of the user, matchmaking, accept and leaderboard routes, the deadline job and `requireAuth`; set the commit and clone URL; replaced the template examples in 3 and 4 with what exists |
| 11-03-2026        | 4.2                   | Added the end-to-end test of Use Case 4 (create a scheduled match), with its steps and log |

---

## 2. Back-end Test Specification: APIs

### 2.1. Locations of Back-end Tests and Instructions to Run Them

#### 2.1.1. Tests

| **Interface**                 | **Describe Group Location, No Mocks**                | **Describe Group Location, With Mocks**            | **Mocked Components**              |
| ----------------------------- | ---------------------------------------------------- | -------------------------------------------------- | ---------------------------------- |
| **POST /user/login**          | ...                                                  | [`tests/mock/userLogin.test.ts`](#)                | Google Authentication API, User DB |
| **POST /user/refresh**        | ...                                                  | [`tests/mock/userRefresh.test.ts`](#)              | Refresh token DB                   |
| **POST /user/logout**         | ...                                                  | [`tests/mock/userLogout.test.ts`](#)               | Refresh token DB                   |
| **GET /user/me**              | ...                                                  | [`tests/mock/userMe.test.ts`](#)                   | User DB                            |
| **PUT /user/me/fcm-token**    | ...                                                  | [`tests/mock/userFcmToken.test.ts`](#)             | User DB                            |
| **GET, POST /user/equipment**, **PATCH, DELETE /user/equipment/:equipmentId** | ... | [`tests/mock/userEquipment.test.ts`](#)  | Equipment DB                       |
| **POST, GET, DELETE /matchmaking** | ...                                             | [`tests/mock/matchmaking.test.ts`](#)              | Matchmaking DB                     |
| **Matchmaking**               | ...                                                  | [`tests/mock/matchmaking.job.test.ts`](#)          | Task Scheduler                     |
| **Match deadline job**        | ...                                                  | [`tests/mock/matchDeadline.job.test.ts`](#)        | Task Scheduler                     |
| **GET /leaderboard**          | ...                                                  | [`tests/mock/leaderboard.test.ts`](#)              | User DB                            |
| **GET /courts**               | ...                                                  | [`tests/mock/courts.test.ts`](#), [`tests/mock/repositories.test.ts`](#) | Court DB                 |
| **POST /games**               | ...                                                  | [`tests/mock/games.test.ts`](#), [`tests/mock/repositories.test.ts`](#) | Game DB                            |
| **GET /games**                | ...                                                  | [`tests/mock/games.test.ts`](#), [`tests/mock/repositories.test.ts`](#) | Game DB                   |
| **GET /games/feed**           | [`tests/no-mock/tournaments.test.ts`](#)             | [`tests/mock/games.test.ts`](#), [`tests/mock/repositories.test.ts`](#), [`tests/mock/weather.test.ts`](#) | Game DB, OpenWeather API           |
| **GET /games/me**             | [`tests/no-mock/tournaments.test.ts`](#)             | [`tests/mock/games.test.ts`](#), [`tests/mock/repositories.test.ts`](#) | Game DB                            |
| **GET /games/history**        | ...                                                  | [`tests/mock/games.test.ts`](#), [`tests/mock/repositories.test.ts`](#) | Game DB                            |
| **DELETE /games**             | [`tests/no-mock/tournaments.test.ts`](#)             | [`tests/mock/games.test.ts`](#), [`tests/mock/repositories.test.ts`](#) | Game DB                            |
| **POST /games/join**          | [`tests/no-mock/tournaments.test.ts`](#)             | [`tests/mock/games.test.ts`](#), [`tests/mock/repositories.test.ts`](#) | Game DB                            |
| **POST /games/leave**         | [`tests/no-mock/tournaments.test.ts`](#)             | [`tests/mock/games.test.ts`](#), [`tests/mock/repositories.test.ts`](#) | Game DB                            |
| **POST /games/accept**        | ...                                                  | [`tests/mock/games.test.ts`](#), [`tests/mock/repositories.test.ts`](#) | Game DB                            |
| **POST /games/invite**        | ...                                                  | [`tests/mock/games.test.ts`](#)                    | Game DB, Friendship DB, User DB, Firebase Cloud Messaging |
| **PATCH /games/time**         | ...                                                  | [`tests/mock/games.test.ts`](#)                    | Game DB                            |
| **PATCH /games/location**     | ...                                                  | [`tests/mock/games.test.ts`](#)                    | Game DB                            |
| **POST /games/score**         | [`tests/no-mock/tournaments.test.ts`](#)             | [`tests/mock/games.test.ts`](#), [`tests/mock/repositories.test.ts`](#), [`tests/mock/bracket.repository.test.ts`](#) | Game DB, Tournament DB             |
| **POST /tournaments**         | [`tests/no-mock/tournaments.test.ts`](#)             | [`tests/mock/tournaments.test.ts`](#), [`tests/mock/tournaments.repository.test.ts`](#) | Tournament DB                      |
| **GET /tournaments**          | [`tests/no-mock/tournaments.test.ts`](#)             | [`tests/mock/tournaments.test.ts`](#), [`tests/mock/tournaments.repository.test.ts`](#) | Tournament DB                      |
| **GET /tournaments/feed**     | [`tests/no-mock/tournaments.test.ts`](#)             | [`tests/mock/tournaments.test.ts`](#), [`tests/mock/tournaments.repository.test.ts`](#), [`tests/mock/weather.test.ts`](#) | Tournament DB, OpenWeather API     |
| **GET /tournaments/me**       | [`tests/no-mock/tournaments.test.ts`](#)             | [`tests/mock/tournaments.test.ts`](#), [`tests/mock/tournaments.repository.test.ts`](#) | Tournament DB                      |
| **POST /tournaments/join**    | [`tests/no-mock/tournaments.test.ts`](#)             | [`tests/mock/tournaments.test.ts`](#), [`tests/mock/tournaments.repository.test.ts`](#), [`tests/mock/bracket.repository.test.ts`](#) | Tournament DB, Game DB             |
| **POST /tournaments/leave**   | [`tests/no-mock/tournaments.test.ts`](#)             | [`tests/mock/tournaments.test.ts`](#), [`tests/mock/tournaments.repository.test.ts`](#) | Tournament DB                      |
| **DELETE /tournaments**       | [`tests/no-mock/tournaments.test.ts`](#)             | [`tests/mock/tournaments.test.ts`](#), [`tests/mock/tournaments.repository.test.ts`](#) | Tournament DB                      |
| **GET /friends**              | ...                                                  | [`tests/mock/friends.test.ts`](#)                  | Friendship DB                      |
| **GET /friends/requests**     | ...                                                  | [`tests/mock/friends.test.ts`](#)                  | Friendship DB                      |
| **POST /friends/requests**    | ...                                                  | [`tests/mock/friends.test.ts`](#)                  | Friendship DB                      |
| **POST /friends/requests/accept** | ...                                              | [`tests/mock/friends.test.ts`](#)                  | Friendship DB                      |
| **DELETE /friends**           | ...                                                  | [`tests/mock/friends.test.ts`](#)                  | Friendship DB                      |

The rows that cite `tests/no-mock/tournaments.test.ts` run in its `Unmocked: tournament flow` describe group, against a real, migrated Postgres database (see 2.1.3). `tests/no-mock/requireAuth.test.ts` checks the `Authorization` header that every route except login, refresh and logout requires, and `tests/no-mock/health.test.ts` checks `GET /health` and unknown routes. Pure logic is unit tested in `tests/unit/`: the bracket rules (`bracket.test.ts`), ELO (`elo.test.ts`), distances (`geo.test.ts`), matching (`matchmaking.service.test.ts`) and tokens (`tokens.test.ts`).

#### 2.1.2. Commit Hash Where Tests Run

[`e9776ef4e0094294ac146ad60a029ef802cf1bb7`](https://github.com/donut-cpen321/donut/commit/e9776ef4e0094294ac146ad60a029ef802cf1bb7)

#### 2.1.3. Explanation on How to Run the Tests

1. **Clone the Repository**:

   - Open your terminal and run:
     ```
     git clone https://github.com/donut-cpen321/donut.git
     ```

2. **Start a database for the no-mock tests**:

   - The no-mock tests (`tests/no-mock/`) now read and write a real, migrated Postgres database at `DATABASE_URL` (set in `backend/.env`). From the repository root, start one with `docker compose up -d postgres` (it needs `POSTGRES_PASSWORD` in `./.env`), then run `npx prisma migrate deploy` in `backend/`. The GitHub Actions workflow (2.2) provides its own Postgres service and runs the migrations before the tests.

3. **Run the tests**:

   - In `backend/`, run `npm ci`, `npm run generate`, then `npm test` for every suite with coverage. `npx jest tests/unit tests/mock` runs only the unit and mocked tests, which need no database.

### 2.2. GitHub Actions Configuration Location

`~/.github/workflows/backend-test.yml`

### 2.3. Jest Coverage Report Screenshots With Mocks

_(Placeholder for Jest coverage screenshot with mocks enabled)_

### 2.4. Jest Coverage Report Screenshots Without Mocks

_(Placeholder for Jest coverage screenshot without mocks)_

---

## 3. Tests of Non-Functional Requirements

### 3.1. Test Locations in Git

Not written yet. `backend/tests/nfr/` is reserved for them.

| **Non-Functional Requirement** | **Location in Git** |
| ------------------------------ | ------------------- |
| **API response time** (95% of requests within 500 ms with 50 concurrent users) | `backend/tests/nfr/` *(planned)* |
| **Match found latency** (95% of compatible players matched within 60 seconds) | `backend/tests/nfr/` *(planned)* |
| **Live score sync latency** | Waits for the live score feature *(planned)* |

### 3.2. Test Verification and Logs

To be added with the tests.

---

## 4. Front-end Test Specification

### 4.1. Location in Git of Front-end Test Suite:

`frontend/app/src/androidTest/java/com/example/cpen321application/` (Compose UI tests), and `frontend/app/src/test/java/com/example/cpen321application/` for unit tests of view models, repositories and screen logic.

### 4.2. Tests

#### 4.2.1. Use Case 4: Create a scheduled match (end-to-end)

[`e2e/ScheduleGameE2ETest.kt`](#) runs the whole app against the real backend at `API_BASE_URL` (default `http://10.0.2.2:3000`, the host from the emulator), with no mocks. It creates a game, finds it under My games, then cancels it, so a passing run leaves nothing behind.

**Before running it:**
1. Start the backend and its database: `docker compose up -d` in the repository root.
2. Add a Google account to the device (Settings → Passwords & accounts). Running the tests reinstalls the app, which clears any saved session, so the test taps "Sign in with Google" itself. Google signs in on its own when the device has one account that used the app before; otherwise the test taps the first account in Google's account sheet with UiAutomator.
3. Run it from Android Studio, or with `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.cpen321application.e2e.ScheduleGameE2ETest` in `frontend/`.

| **Scenario Steps** | **Test Case Steps** |
| ------------------ | ------------------- |
| 1. The user opens the Games tab, taps "Schedule" and picks "Game". | Signs in if the login screen shows. Taps the "Games" tab, then "Schedule", then "Game". Checks the "Schedule a game" form opens. |
| 2. The user picks the format and the start date and time. | Taps "Singles". Taps "Pick a date and time", then "Next" and "OK" to keep the default, one hour from now. Reads the start time the button now shows. |
| 3. The user opens the court picker and taps a court. | Taps "Pick a court", waits for the courts to load from the backend, taps the first one and checks its name shows on the button. |
| 4. The user submits the form. | Taps "Schedule game". |
| 5. The system validates the game and creates it with the user on team 1. | The backend creates the game (`POST /games`); the test checks the result in the next step. |
| 6. The app shows the new game. | Checks the game screen opens with the start time, the court and "Singles". Goes back, opens "My games", refreshes and checks a game with that start time is listed. |
| *(cleanup, Manage scheduled match)* | Opens the game, taps "Cancel game" and confirms in the "Cancel this game?" dialog. Back on the list, refreshes "My games" and checks the game is gone (`DELETE /games`). |

- **Test Logs** (Pixel 9 emulator, Android 16):
  ```
  $ adb shell am instrument -w -e class com.example.cpen321application.e2e.ScheduleGameE2ETest com.example.cpen321application.test/androidx.test.runner.AndroidJUnitRunner
  com.example.cpen321application.e2e.ScheduleGameE2ETest:.

  Time: 7.066

  OK (1 test)
  ```

#### 4.2.2. Screen tests

These render one screen with injected data, with no backend or signed-in account:

- **Leaderboard screen** ([`feature/leaderboard/LeaderboardScreenTest.kt`](#)): shows each player's rank, username and rounded rating; marks the signed-in player once when they are in the top list, and pins them at the bottom when they aren't or are scrolled out of view; handles long names, an empty list, loading, and an error with a retry.
- **Tournament detail screen** ([`feature/tournamentdetail/TournamentDetailScreenTest.kt`](#)): during registration shows the registrants and a Join button, or Cancel for the creator; in progress shows the bracket columns with team names, and tapping a played match opens its game; when completed shows the champion banner.

