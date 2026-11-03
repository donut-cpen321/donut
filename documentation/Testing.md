# Testing

## 1. Change History

| **Change Date**   | **Modified Sections** | **Rationale** |
| ----------------- | --------------------- | ------------- |
| 10-19-2026        | 2. Back-end Test      | Added tests   |
| 11-01-2026        | 2.1.1                 | Added tests for `GET /courts`, `GET /games`, `GET /games/feed`, `GET /games/me`, `POST /games/join` and `POST /games/leave`, the OpenWeather client and the games and courts repositories |
| 11-02-2026        | 2.1.1, 2.1.3, 2.2     | Added tests for the tournament routes, the tournament and bracket repositories and the bracket rules, updated the rows of the games routes that tournaments changed, and noted that the no-mock tests need a migrated Postgres database |
| 11-03-2026        | 2.1.1                 | Added tests for `GET /games/history`, and listed the existing tests of the friends routes |

---

## 2. Back-end Test Specification: APIs

### 2.1. Locations of Back-end Tests and Instructions to Run Them

#### 2.1.1. Tests

| **Interface**                 | **Describe Group Location, No Mocks**                | **Describe Group Location, With Mocks**            | **Mocked Components**              |
| ----------------------------- | ---------------------------------------------------- | -------------------------------------------------- | ---------------------------------- |
| **POST /user/login**          | ...                                                  | [`tests/mock/userLogin.test.ts`](#)                | Google Authentication API, User DB |
| **Matchmaking**               | ...                                                  | [`tests/mock/matchmaking.job.test.ts`](#)          | Task Scheduler                     |
| **GET /courts**               | ...                                                  | [`tests/mock/courts.test.ts`](#), [`tests/mock/repositories.test.ts`](#) | Court DB                 |
| **POST /games**               | ...                                                  | [`tests/mock/games.test.ts`](#), [`tests/mock/repositories.test.ts`](#) | Game DB                            |
| **GET /games**                | ...                                                  | [`tests/mock/games.test.ts`](#), [`tests/mock/repositories.test.ts`](#) | Game DB                   |
| **GET /games/feed**           | [`tests/no-mock/tournaments.test.ts`](#)             | [`tests/mock/games.test.ts`](#), [`tests/mock/repositories.test.ts`](#), [`tests/mock/weather.test.ts`](#) | Game DB, OpenWeather API           |
| **GET /games/me**             | [`tests/no-mock/tournaments.test.ts`](#)             | [`tests/mock/games.test.ts`](#), [`tests/mock/repositories.test.ts`](#) | Game DB                            |
| **GET /games/history**        | ...                                                  | [`tests/mock/games.test.ts`](#), [`tests/mock/repositories.test.ts`](#) | Game DB                            |
| **DELETE /games**             | [`tests/no-mock/tournaments.test.ts`](#)             | [`tests/mock/games.test.ts`](#)                    | Game DB                            |
| **POST /games/join**          | [`tests/no-mock/tournaments.test.ts`](#)             | [`tests/mock/games.test.ts`](#), [`tests/mock/repositories.test.ts`](#) | Game DB                            |
| **POST /games/leave**         | [`tests/no-mock/tournaments.test.ts`](#)             | [`tests/mock/games.test.ts`](#), [`tests/mock/repositories.test.ts`](#) | Game DB                            |
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

The rows that cite `tests/no-mock/tournaments.test.ts` run in its `Unmocked: tournament flow` describe group, against a real, migrated Postgres database (see 2.1.3). The pure bracket rules (`formTeams`, the bracket's slots, where a winner plays next and when each round starts) are unit tested in `tests/unit/bracket.test.ts`.

#### 2.1.2. Commit Hash Where Tests Run

`[Insert Commit SHA here]`

#### 2.1.3. Explanation on How to Run the Tests

1. **Clone the Repository**:

   - Open your terminal and run:
     ```
     git clone https://github.com/example/your-project.git
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

| **Non-Functional Requirement**  | **Location in Git**                              |
| ------------------------------- | ------------------------------------------------ |
| **Performance (Response Time)** | [`backend/tests/nfr/response_time.test.js`](#) |
| **Chat Data Security**          | [`backend/tests/nfr/chat_security.test.js`](#) |

### 3.2. Test Verification and Logs

- **Performance (Response Time)**

  - **Verification:** This test suite simulates multiple concurrent API calls using Jest along with a load-testing utility to mimic real-world user behavior. The focus is on key endpoints such as user login and study group search to ensure that each call completes within the target response time of 2 seconds under normal load. The test logs capture metrics such as average response time, maximum response time, and error rates. These logs are then analyzed to identify any performance bottlenecks, ensuring the system can handle expected traffic without degradation in user experience.
  - **Log Output**
    ```
    [Placeholder for response time test logs]
    ```

- **Chat Data Security**
  - **Verification:** ...
  - **Log Output**
    ```
    [Placeholder for chat security test logs]
    ```

---

## 4. Front-end Test Specification

### 4.1. Location in Git of Front-end Test Suite:

`frontend/src/androidTest/java/com/studygroupfinder/`

### 4.2. Tests

- **Use Case: Login**

  - **Expected Behaviors:**
    | **Scenario Steps** | **Test Case Steps** |
    | ------------------ | ------------------- |
    | 1. The user opens "Add Todo Items" screen. | Open "Add Todo Items" screen. |
    | 2. The app shows an input text field and an "Add" button. The add button is disabled. | Check that the text field is present on screen.<br>Check that the button labelled "Add" is present on screen.<br>Check that the "Add" button is disabled. |
    | 3a. The user inputs an ill-formatted string. | Input "_^_^^OQ#$" in the text field. |
    | 3a1. The app displays an error message prompting the user for the expected format. | Check that a dialog is opened with the text: "Please use only alphanumeric characters ". |
    | 3. The user inputs a new item for the list and the add button becomes enabled. | Input "buy milk" in the text field.<br>Check that the button labelled "add" is enabled. |
    | 4. The user presses the "Add" button. | Click the button labelled "add ". |
    | 5. The screen refreshes and the new item is at the bottom of the todo list. | Check that a text box with the text "buy milk" is present on screen.<br>Input "buy chocolate" in the text field.<br>Click the button labelled "add".<br>Check that two text boxes are present on the screen with "buy milk" on top and "buy chocolate" at the bottom. |
    | 5a. The list exceeds the maximum todo-list size. | Repeat steps 3 to 5 ten times.<br>Check that a dialog is opened with the text: "You have too many items, try completing one first". |

  - **Test Logs:**
    ```
    [Placeholder for Espresso test execution logs]
    ```

- **Use Case: ...**

  - **Expected Behaviors:**

    | **Scenario Steps** | **Test Case Steps** |
    | ------------------ | ------------------- |
    | ...                | ...                 |

  - **Test Logs:**
    ```
    [Placeholder for Espresso test execution logs]
    ```

- **...**
