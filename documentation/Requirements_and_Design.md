# Requirements and Design

## 1. Change History

| **Change Date** | **Modified Sections** | **Rationale** |
| --------------- | --------------------- | ------------- |
| Oct 23, 2026 | 2, 3.1, 3.3–3.5, 4.1, 4.2, 4.7 | Narrowed the first version to tennis only; players no longer pick a sport. |
| Oct 28, 2026 | 4.1 | Added the Games interfaces that change a game's time and court, and `POST /games/score`. |
| Nov 1, 2026 | 4.1, 4.3 | Implemented `GET /courts`, `GET /games/feed` (with forecasts), `GET /games/me` and `GET /games`, and documented the join and leave rules. |
| Nov 1, 2026 | 4.1, 4.2 | `POST /games/score` updates ratings through `applyGameResult`. Removed the leftover `sport` parameters. |
| Nov 1, 2026 | 4.1, 4.2 | Added `GET /leaderboard` and `GET /user/me`. |
| Nov 2, 2026 | 3.1, 3.2, 3.4, 4.1–4.3, 4.5 | Added tournaments: the feature, its use cases, the Tournaments component and its tables. |
| Nov 3, 2026 | 4.1, 4.2 | Added the Friends component and its `friendships` table. |
| Nov 3, 2026 | 4.1 | Added `GET /games/history` and each player's `ratingBefore` and `ratingAfter`. |
| Nov 3, 2026 | 4.1 | Added `POST /games/invite`, and updated `DELETE /games` for matched games. |
| Nov 3, 2026 | All | Shortened the document and matched it to the code: token auth (`/user/refresh`, `/user/logout`), equipment, location-based matchmaking (`/matchmaking`, `POST /games/accept`, the deadline job), no game owner, and the libraries in use. Unbuilt parts are marked *(planned)*. |
| Nov 3, 2026 | 3.1, 3.3, 3.5 (Use Case 4), 4.3 | A scheduled game's court is picked from a list; the map shows a matched game's court. |

---

## 2. Project Description

Donut is a matchmaking and ranking app for recreational tennis players (singles and doubles) who can't commit to a weekly league. A player asks for a game now and the system finds nearby players of similar skill, picks a court and notifies everyone. Players can also schedule games or tournaments for later and let others join from a feed. After each game, every player's ELO rating changes based on the result and the margin of victory.

---

## 3. Requirements Specification

### **3.1. List of Features**

#### **Authentication**
Users sign in with Google. The backend then issues its own access and refresh tokens. Picking preferred courts on a map after the first sign-in is *(planned)*.

#### **Live matchmaking**
Users pick a format (singles, doubles or either) and where they want to play. The system matches nearby players with similar ratings, books a game one hour later at a nearby court and notifies everyone. The game is booked once every player accepts.

#### **Schedule match**
Users schedule a game for a future time at a court they pick from a list. It shows in the feed with the weather forecast, and others can join it or be invited by a friend. Adding it to Google Calendar is *(planned)*.

#### **Live game info update** *(planned)*
During a game, a player updates the score, and it syncs live to every other player's device.

#### **ELO ranking**
After a game's final score is reported, every player's ELO rating changes, and a leaderboard ranks players by rating.

#### **Tournaments**
Users create an 8-team single-elimination tournament (singles or doubles) that players register for from the feed. When it fills, the system forms and seeds the teams, draws the bracket and books the quarterfinals. Each reported score moves the winner on, up to the champion.

Supporting features: **friends** (requests, friends list, adding players from a match summary), **match history** with a summary per game, and **equipment** tracking (rackets, balls, shoes, with maintenance dates).

**Project scope requirements:**
- **External API**: Google Maps (picking courts), Google Places (picking where to play), OpenWeather (feed forecasts), Google Calendar *(planned)*.
- **External event**: match notifications and game invites through Firebase Cloud Messaging; live score updates *(planned)*.
- **Our own computation**: the matching algorithm, ELO with margin of victory, and the tournament draw (rating-based seeding, balanced doubles teams, moving winners through the bracket).

### **3.2. Use Case Diagram**
![Use case diagram](images/use_case_diagram.png)

External services are drawn with plain lines because they support use cases but never start one. Steps done by the system alone (matchmaking, syncing, the ELO calculation, updating the leaderboard) aren't drawn. The tournament use cases (17–20) aren't in the diagram yet.

### **3.3. Actors Description**
1. **User**: a tennis player who signs in with Google, joins or is matched into games, and reports scores. Every player in a game has the same permissions on it.
2. **Google Sign-In**: authenticates players.
3. **Google Maps Platform**: shows a matched game's court on a map, and Places looks up where a player wants to play.
4. **Firebase Cloud Messaging**: delivers match notifications and game invites.
5. **OpenWeather**: supplies forecasts for the feed.
6. **Google Calendar** *(planned)*: stores booked games in the player's calendar.

### **3.4. Use Case Description**
- Feature 1: Authentication
1. **Sign in with Google**: The user signs in with Google; the server creates their account the first time and returns tokens.
2. **Set up profile** *(planned)*: The user picks preferred courts on a map.

- Feature 2: Live matchmaking
3. **Initiate live match**: The user picks a format and a location (their current location or an address) and joins the queue. They can leave the queue at any time.
4. **Matchmaking**: Every 10 seconds, the system groups queued players who want a compatible format, are within 10 km of each other and have close ratings, then books a game one hour later at a court near them and notifies everyone.
5. **Accept or decline a match**: Each player accepts or declines. When all accept, the game is booked. If anyone declines, or not everyone has accepted 30 minutes before the start, the match is cancelled for everyone.

- Feature 3: Schedule match
6. **Create a scheduled match**: The user creates a game with a format, start time and court, and is its first player.
7. **Browse scheduled matches**: The feed lists the upcoming games and tournaments that can be joined, with each game's players and ratings, format, court and forecast.
8. **Manage scheduled match**: The user joins a game from the feed, or is invited by a friend. A player can leave unless it starts in less than 30 minutes, move it to another time or court, or cancel it.
9. **Add game to Google Calendar** *(planned)*: The user adds a booked game to their calendar.
10. **Start a scheduled match** *(planned)*: Players confirm they're at the court within 30 minutes of the start, or the game is cancelled.

- Feature 4: Live game info update *(planned)*
11. **Update game information**: A player updates the score during the game.
12. **Live game data syncing**: The score updates on every player's device.

- Feature 5: ELO ranking
13. **Submit final score**: A player reports the final score, and every player sees the match summary with their rating change.
14. **ELO change calculation**: The system calculates each player's rating change.
15. **Update leaderboard**: The system applies the new ratings to the leaderboard.
16. **View leaderboard**: The user views players ranked by rating, with their own rank.

- Feature 6: Tournaments
17. **Create a tournament**: The user creates a tournament with a name, format, court and start time, and is registered as its first player.
18. **Register for or leave a tournament**: The user registers from the feed until it is full (8 players in singles, 16 in doubles) or starts. Players can leave during registration; the creator can't leave but can cancel it.
19. **Bracket draw**: When it fills, the system forms the teams (in doubles, strongest with weakest), seeds them by rating, draws the bracket (1 v 8, 4 v 5, 3 v 6, 2 v 7) and books the quarterfinals.
20. **View the bracket and play tournament matches**: The user views the bracket. Each match is played like any game; reporting its score moves the winner on, books the next match once both teams are known (semifinals one hour after the start, the final two hours after), and the final completes the tournament.

### **3.5. Formal Use Case Specifications (5 Most Major Use Cases)**
<a name="uc1"></a>

#### Use Case 1: Initiate live match

**Description**: The user joins the matchmaking queue. The system matches them with nearby players of a close rating, books a court and notifies everyone.

**Primary actor(s)**: User

**Preconditions**: The user is signed in and has no matched game waiting for answers or booked.

**Postconditions**: A proposed game exists one hour from when the match was found, at a court near the players, with every matched player in it and out of the queue.

**Main success scenario**:
1. The user opens the Matchmaking tab and picks a format (singles, doubles or either).
2. The user picks where to play: their current location, or an address.
3. The user taps "Find a match". The system adds them to the queue and shows that it is searching.
4. Every 10 seconds, the system takes the longest-waiting players first and groups each with the closest-rated players within 10 km (2 players for singles, 4 for doubles). Ratings in a group must be within 200 points for singles and 400 for doubles.
5. The system creates a proposed game one hour from now, at the most-preferred court within 10 km of every player, or else the court nearest the middle of them. In doubles, the highest- and lowest-rated players play the middle two.
6. The system notifies every player through Firebase Cloud Messaging.
7. The user sees the match with its court, time and players, and can accept or decline.

**Failure scenario(s)**:
- 3a. The user is already in the queue: the system rejects the request (`409`) and the app shows the existing search.
- 3b. The user already has a matched game waiting for answers, or booked: the system rejects the request (`403`).
- 3c. The server can't be reached: the app shows an error and the user can retry.
- 4a. No compatible players are queued: the user stays in the queue and the system tries again every 10 seconds.
- 4b. The user leaves the queue first: they are removed and no game is proposed.
- 6a. A player's device can't receive the notification: the game stays on the server, and the app shows it on the Matchmaking tab and in "My games".

<a name="uc2"></a>

#### Use Case 2: Starting a scheduled match *(planned)*
**Description**: At a booked game's start time, each player confirms they are at the court. The game starts once all confirm within 30 minutes, otherwise it is cancelled.

**Primary actor(s)**: User

**Preconditions**: The user is signed in and is a player in a booked game that hasn't started.

**Postconditions**: The game has started, or it is cancelled for everyone with no rating changes.

**Main success scenario**:
1. At the start time, the system notifies every player to confirm they are present.
2. The user opens the game and taps "I'm here". The system records it and shows who has confirmed.
3. The other players confirm the same way.
4. When the last player confirms, the system starts the game and notifies everyone.

**Failure scenario(s)**:
- 1a. A scheduled game still has open spots at its start time: the system cancels it and notifies its players.
- 2a. The user confirms before the start time or after the 30-minute window: the confirmation is not recorded, and the app explains why.
- 3a. A player hasn't confirmed 30 minutes after the start: the system cancels the game and notifies everyone.

<a name="uc3"></a>

#### Use Case 3: Submit final score
**Description**: A player reports the final score. The system decides the winner, updates every player's rating and shows the match summary.

**Primary actor(s)**: User

**Preconditions**: The user is signed in and is a player in a game that has started and has no final score.

**Postconditions**: The game is completed, its score can't change, every player's rating is updated, and the match summary shows each rating before and after.

**Main success scenario**:
1. The user opens the game and taps "Report final score".
2. The user enters each set's score and submits.
3. The system validates the score and decides the winner: the side that won more sets.
4. The system calculates each player's rating change with ELO and margin of victory (§7 of the Product Design). In doubles, a team is rated by its average, and each player gets half the team's change.
5. In one transaction, the system saves the score and the new ratings, records each player's rating before and after, and marks the game completed. For a tournament game, it also moves the winner on through the bracket.
6. The game shows its final score, and each player can open the match summary from their match history.

**Failure scenario(s)**:
- 1a. The final score was already reported: the system rejects it (`409`) and the game shows its final score.
- 3a. There are no sets, a set score isn't a non-negative whole number, a set is tied, or both sides won the same number of sets: the system rejects it (`400`) and the user corrects the score.
- 3b. A team isn't full: the system rejects it (`409`).
- 5a. Saving fails, for example on a deadlock when two games sharing a player finish at once: nothing is saved (`409`), and the user can retry.

<a name="uc4"></a>

#### Use Case 4: Create a scheduled match
**Description**: The user creates a game for a future time at a court they pick from a list. It appears in the feed for others to join.

**Primary actor(s)**: User

**Preconditions**: The user is signed in.

**Postconditions**: An open scheduled game exists with the user as its first player, and it is in the feed until it is full, cancelled or started.

**Main success scenario**:
1. The user opens the Games tab, taps "Schedule" and picks "Game".
2. The user picks the format and the start date and time.
3. The user opens the court picker and taps a court in the list.
4. The user submits the form.
5. The system validates the game and creates it with the user on team 1.
6. The app shows the new game, and it appears in the feed with its forecast.

**Failure scenario(s)**:
- 2a. The start time isn't in the future: the app asks for a start time in the future, and the server rejects it too (`400`).
- 3a. The courts can't be loaded: the court picker shows an error with a retry button.
- 5a. The court doesn't exist: the server rejects the game.
- 5b. The server can't be reached: the app shows an error and keeps the form filled in.
- 6a. The forecast can't be loaded: the feed shows the game without one.

<a name="uc5"></a>

#### Use Case 5: Update game information *(planned)*
**Description**: During a started game, a player enters each set's score, and the system pushes it to every other player in real time.

**Primary actor(s)**: User

**Preconditions**: The user is signed in and is a player in a started game with no final score.

**Postconditions**: The new score is saved, and every player with the game open sees it.

**Main success scenario**:
1. The user opens the current game, which connects to its live updates.
2. The user changes a set's score, or starts a new set, and saves it.
3. The system validates and saves it, then sends it to every connected player.
4. Each other player's app shows the new score without refreshing.

**Failure scenario(s)**:
- 3a. The score is invalid, or the game already has a final score: the system rejects it.
- 3b. The server can't be reached: the app keeps the unsaved score and the user can retry.
- 4a. A player's app is offline: it loads the latest score when it reconnects.

### **3.6. Screen Mock-ups**
Not included. The formal use cases describe each screen.

### **3.7. Non-Functional Requirements**
<a name="nfr1"></a>

1. **Live score sync latency** *(planned with the live score)*
    - **Description**: 95% of score updates reach every other connected player within 1 second.
    - **Justification**: 1 second is the limit for keeping a user's flow uninterrupted (Nielsen Norman Group); players check the score between points.

<a name="nfr2"></a>

2. **API response time**
    - **Description**: 95% of REST requests (feed, create, join, accept, score) respond within 500 ms with 50 concurrent users.
    - **Justification**: Players open the app right before playing, often on mobile data, so 500 ms leaves room for the network within the 1-second limit. 50 concurrent users is above the expected load in one city.

<a name="nfr3"></a>

3. **Match found latency**
    - **Description**: When compatible players are queued, 95% are matched and notified within 60 seconds of the last one joining.
    - **Justification**: Matched players play within the hour and must accept 30 minutes before the start. The job runs every 10 seconds, so 60 seconds allows for missed runs and notification delivery.

---

## 4. Designs Specification
### **4.1. Main Components**

Every route except `POST /user/login`, `POST /user/refresh` and `POST /user/logout` needs `Authorization: Bearer <access token>` (`401` otherwise). An invalid body or query is `400` with `details`.

1. **Users**
    - **Purpose**: Accounts, sign-in and tokens, device notification tokens, equipment and the courts list.
    - **Rationale**: Everything about who a player is lives in one place, and other components only read it. Sign-in is delegated to Google, so auth only adds our own short-lived tokens on top.
    - **Interfaces**:
        1. `POST /user/login` with `{ idToken }` → `201` (new account) or `200` `{ message, username, accessToken, refreshToken }` - E
            - **Purpose**: Verifies the Google ID token and creates the account on the first sign-in. `401` for an invalid token, `400` if the Google profile has no name or email. Access tokens last 1 hour, refresh tokens 30 days.
        2. `POST /user/refresh` with `{ refreshToken }` → `{ accessToken, refreshToken }`, `POST /user/logout` with `{ refreshToken }` → `204` - E
            - **Purpose**: Exchanges a refresh token for new tokens (each refresh token works once; `401` if invalid or expired), or revokes it on sign-out.
        3. `GET /user/me` → `{ user }` - E
            - **Purpose**: The caller's profile with their rating. `404` if the account no longer exists.
        4. `PUT /user/me/fcm-token` with `{ token }` → `204` - E
            - **Purpose**: Registers the device that receives the caller's notifications.
        5. `GET /user/equipment` → `{ equipment }`, `POST /user/equipment` with `{ type, name, isDefault?, nextMaintenanceAt? }` → `201 { equipment }`, `PATCH /user/equipment/:equipmentId` → `{ equipment }`, `DELETE /user/equipment/:equipmentId` → `204` - E
            - **Purpose**: The caller's equipment (`RACKET`, `BALLS`, `SHOES` or `OTHER`), default items first. Deleting retires the item. `404` "Equipment not found".
        6. `GET /courts` → `{ courts: { id, name, latitude, longitude }[] }` - E
            - **Purpose**: Every court, by name, for the court picker.
        7. `PUT /user/me/courts` with `{ courtIds }` → `Court[]` *(planned)* - E
            - **Purpose**: Replaces the caller's preferred courts.
        8. `string[] getFcmTokens(userIds)`, `void clearFcmTokens(tokens)` - In
            - **Purpose**: Used by Matchmaking and Games to send notifications and drop dead tokens.
2. **Games**
    - **Purpose**: Games, their players and rounds, and their lifecycle: create, feed, join, leave, invite, accept, move, cancel and final score.
    - **Rationale**: A game's players, status and rounds change together, so they stay in one component. Scheduled, matched and tournament games share it, told apart by `gameSource`.
    - **Interfaces**:
        1. `POST /games` with `{ format, startsAt, courtId, gameSource }` → `201 { game }` - E
            - **Purpose**: Creates a game with the caller on team 1. `gameSource` is `SCHEDULED` or `MATCHED`; tournament games are only created by their bracket.
        2. `GET /games/feed` → `{ games: FeedGame[] }` - E
            - **Purpose**: Scheduled games that are `OPEN` and haven't started, soonest first. A `FeedGame` is a `GameDetails` with a `forecast` (`{ at, temperature, description, icon, precipitationChance }`), or `null` if it is over 5 days away or OpenWeather fails.
        3. `GET /games` with `gameId` → `{ game: GameDetails }` - E
            - **Purpose**: One game with its court, players (`{ id, username, name, rating, team, acceptedAt, ratingBefore, ratingAfter }`), rounds and `tournament` (`{ id, name, round }`, or `null`). `404` if not found.
        4. `GET /games/me` → `{ games }` - E
            - **Purpose**: The caller's `BOOKED` games and `OPEN` games that haven't started, soonest first.
        5. `GET /games/history` → `{ games }` - E
            - **Purpose**: The caller's `COMPLETED` games, most recent first, for the match history and summary.
        6. `DELETE /games` with `gameId` → `204` - E
            - **Purpose**: Cancels a game for everyone. A scheduled game is deleted with its players, rounds and equipment in one transaction, under the game's row lock; a matched game is marked `CANCELLED` (this is how a match is declined). `403` if the caller isn't in it, `404` if not found, `409` for a tournament game or one already completed or cancelled (a completed game keeps its score and rating changes).
        7. `POST /games/join` with `{ gameId, team }` → `204`, `POST /games/leave` with `{ gameId }` → `204` - E
            - **Purpose**: Join or leave a scheduled game. Joining needs an `OPEN` scheduled game that hasn't started and a free spot on the team (`409` otherwise); filling the last spot books it. Leaving is `409` for tournament and matched games, closed games, or within 30 minutes of the start; a booked game reopens. Both lock the game's row, so two players can't take the last spot.
        8. `POST /games/accept` with `{ gameId }` → `204` - E
            - **Purpose**: Accepts a matched game for the caller; it is booked once every player has accepted. `409` if it is no longer waiting for answers, including within 30 minutes of the start.
        9. `POST /games/invite` with `{ gameId, userId }` → `{ notified }` - E
            - **Purpose**: Sends a friend a push notification that opens the game, where they join it. The caller must be in the game (`403`), it must be an open scheduled game that hasn't started (`409`), and the user must be a friend (`403`) not already in it (`409`). `notified` is `false` without Firebase credentials or a working device token.
        10. `PATCH /games/time` with `{ gameId, startsAt }`, `PATCH /games/location` with `{ gameId, courtId }` → `{ game }` - E
            - **Purpose**: Move a game to a future time or another court. The caller must be in it (`403`), and it must be open or booked and not a matched game (`409`).
        11. `POST /games/score` with `{ gameId, rounds: [team1Score, team2Score][] }` → `{ game, rounds, ratingChanges }` - E
            - **Purpose**: Reports the final score. In one transaction it saves the rounds, completes the game, updates ratings (`applyGameResult`) and, for a tournament game, advances the bracket (`advanceBracket`). `400` for an invalid score, `409` if already reported, a team isn't full, or Postgres aborts the transaction on a deadlock.
        12. `POST /games/check-in` *(planned)*, live round updates and a Socket.IO `game-update` event *(planned)* - E
            - **Purpose**: Check-in at the court, and the live score.
3. **Matchmaking**
    - **Purpose**: The matchmaking queue, the matching algorithm, and the jobs that run it every 10 seconds and cancel unanswered matches every minute.
    - **Rationale**: Matching creates games without a request, so it runs on a timer outside the request path, where join requests can't race each other.
    - **Interfaces**:
        1. `POST /matchmaking` with `{ format, latitude, longitude }` → `201 { request }`, `GET /matchmaking` → `{ request }`, `DELETE /matchmaking` → `204` - E
            - **Purpose**: Join, check or leave the queue. Joining is `409` if already queued and `403` if the caller has a matched game waiting or booked. `GET` is `404` once the caller is matched or left.
        2. `Match[] runMatchmaking()` - In
            - **Purpose**: Called by the matchmaking job: finds matches (`findMatches`), picks a court (`chooseCourt`, else `nearestCourt`) and teams (`assignTeams`), creates each game in its own transaction and notifies its players.
        3. `number cancelUnansweredMatches(now)` - In
            - **Purpose**: Called every minute: cancels matched games not accepted by everyone 30 minutes before the start.
4. **Ranking**
    - **Purpose**: Ratings, the ELO calculation and the leaderboard.
    - **Rationale**: Our own computation, kept as a pure, unit-tested function, and the only code that writes ratings.
    - **Interfaces**:
        1. `RatingChange[] applyGameResult(tx, gameId, format, rounds)` - In
            - **Purpose**: Called by Games in the final-score transaction: locks the players' ratings, calculates and saves the changes, and records each rating before and after. Throws if a team isn't full, which rolls everything back.
        2. `RatingChange[] calculateELOChange(players, game)` - In
            - **Purpose**: The pure ELO calculation.
        3. `GET /leaderboard` with optional `limit` (1–100, default 50) → `{ leaderboard, me }` - E
            - **Purpose**: The top players and the caller's entry, each `{ rank, id, username, name, rating }`. Ranked by full rating, ties by username.
5. **Tournaments**
    - **Purpose**: Registration, the bracket draw, the bracket and moving winners through it. Each match is an ordinary game with `gameSource` `TOURNAMENT`.
    - **Rationale**: A tournament has its own lifecycle and rules spanning several games, so it has its own tables (`tournaments`, `tournament_players`, `tournament_matches`), while each match reuses the game's players, score and ratings. Bracket writes live in `bracket.repository.ts`, which only takes the caller's transaction, so Games can call it without an import cycle. Limitations: 8-team brackets only, no forfeits, no tournament notifications.
    - **Interfaces**:
        1. `POST /tournaments` with `{ name, format, startsAt, courtId }` → `201 { tournament }` - E
            - **Purpose**: Creates a tournament in `REGISTRATION` with the caller registered. `name` is 1–50 characters; `404` if the court doesn't exist.
        2. `GET /tournaments/feed` → `{ tournaments }`, `GET /tournaments/me` → `{ tournaments }` - E
            - **Purpose**: Tournaments open for registration, with forecasts; or the caller's tournaments in registration, in progress, or completed in the last 24 hours. Each includes its `capacity`, court, creator and players with their seeds.
        3. `GET /tournaments` with `tournamentId` → `{ tournament }` - E
            - **Purpose**: One tournament with its bracket: `matches` of `{ round, position, team1, team2, winner, game }`. `404` if not found.
        4. `POST /tournaments/join`, `POST /tournaments/leave` with `{ tournamentId }` → `204` - E
            - **Purpose**: Register or leave during registration (`409` if not open, full or already registered; the creator can't leave). The registration that fills it draws the bracket in the same transaction, under the tournament's row lock.
        5. `DELETE /tournaments` with `tournamentId` → `204` - E
            - **Purpose**: The creator cancels it while it is still in registration, including one that didn't fill by its start time. It is kept as `CANCELLED`. `403` for anyone else, `409` once the bracket is drawn or it is closed.
        6. `void drawBracket(tx, tournament, now)` - In
            - **Purpose**: Forms and seeds the teams (`formTeams`), saves the 7 matches, books the 4 quarterfinals and marks it `IN_PROGRESS`.
        7. `void advanceBracket(tx, gameId, rounds, now)` - In
            - **Purpose**: Called by Games when a tournament game's score is reported: records the winner, books the next match once both its teams are known, and after the final marks the tournament `COMPLETED` with its champion.
        8. `string[][] formTeams(entrants, format)` - In
            - **Purpose**: The pure team draw: ranks by rating, pairs strongest with weakest in doubles, seeds by average rating.
6. **Friends**
    - **Purpose**: Friend requests and friends lists.
    - **Rationale**: Friendships touch no game or rating data. One `friendships` row per pair, holding who asked and whether it was accepted, covers requests and friends without a second table.
    - **Interfaces**:
        1. `GET /friends` → `{ friends }`, `GET /friends/requests` → `{ incoming, outgoing }` - E
            - **Purpose**: The caller's friends (`{ id, username, name, rating, friendsSince }`) and pending requests, most recent first.
        2. `POST /friends/requests` with `{ userId }` → `201 { status }` - E
            - **Purpose**: Sends a request, or accepts theirs if they already sent one. `400` for yourself, `409` if already friends or sent, `404` if the user doesn't exist.
        3. `POST /friends/requests/accept` with `{ userId }` → `204`, `DELETE /friends` with `userId` → `204` - E
            - **Purpose**: Accept a request, or remove a friend or request in either direction. `404` if there is none.

### **4.2. Databases**
1. **PostgreSQL (`donut`)**
    - **Purpose**: Stores users, refresh tokens, courts, the matchmaking queue, games, their players (with ratings before and after), rounds, equipment, friendships and tournaments (`backend/prisma/schema.prisma`).
    - **Rationale**: The data is relational, and a match or a final score (with ratings and the bracket) must be saved in one transaction. MongoDB was rejected because nothing is document-shaped and it would lose those transactions.

### **4.3. External Modules**
1. **Google Sign-In**
    - **Purpose**: Signs players in on Android; the backend verifies the ID token.
    - **Rationale**: External authentication is required, and storing passwords ourselves would be less secure.
    - **Interfaces we call**:
        1. `verifyIdToken({ idToken, audience })` (google-auth-library) - called by Users
        2. `CredentialManager.getCredential(GetGoogleIdOption)` - called by the front-end
2. **Google Maps Platform (Maps SDK and Places)**
    - **Purpose**: Shows a matched game's court on a map, and looks up the address a player wants to be matched near.
    - **Rationale**: Players recognise courts by location. The courts themselves are stored in our database.
    - **Interfaces we call**:
        1. `GoogleMap(...)` with `Marker`s (Maps Compose) - called by the front-end
        2. `PlacesClient.fetchPlace(...)` (Places SDK) - called by the front-end
3. **Firebase Cloud Messaging**
    - **Purpose**: Delivers match notifications and game invites, even when the app is closed.
    - **Rationale**: The standard push service on Android; no polling.
    - **Interfaces we call**:
        1. `sendEach(messages)` (firebase-admin) - called by Matchmaking and Games
4. **OpenWeather**
    - **Purpose**: Forecasts for games and tournaments in the feed.
    - **Rationale**: Most courts are outdoors; the free tier covers our load.
    - **Interfaces we call**:
        1. `GET /data/2.5/forecast?lat&lon&units=metric&appid` - called by Games and Tournaments
            - **Purpose**: Picks the 3-hour step nearest the start time (within 1.5 hours). Cached per court for 10 minutes, with a 2-second timeout; without `OPENWEATHER_API_KEY` or on failure, the feed has no forecast.
5. **Google Calendar API** *(planned)*
    - **Purpose**: Adds a booked game to the player's calendar.
    - **Interfaces we call**:
        1. `POST /calendars/primary/events` - called by the front-end

### **4.4. Frameworks and Libraries**
1. **Express 5**: HTTP server and routing, with routes → controllers → services → repositories.
2. **Prisma 7 and PostgreSQL 17**: schema, migrations and typed queries.
3. **TypeScript 5.9 and tsx**: backend language and dev runner; catches interface mismatches at compile time.
4. **zod**: validates request bodies and gives their TypeScript types.
5. **jsonwebtoken**: signs and verifies our access tokens.
6. **node-cron**: runs the matchmaking and deadline jobs, with no overlapping runs.
7. **google-auth-library**: verifies Google ID tokens.
8. **firebase-admin**: sends push notifications.
9. **Jest 30, ts-jest and Supertest**: backend tests; Supertest calls the Express app without starting a server.
10. **Kotlin and Jetpack Compose (Material 3)**: the Android app and its UI.
11. **Retrofit with Gson**: typed HTTP client for the backend.
12. **Credential Manager**: Google sign-in on Android.
13. **Maps Compose, Places and Play Services Location**: the court map, address lookup and current location.
14. **Firebase Messaging (Android)**: receives push notifications.
15. **Socket.IO** *(planned)*: live score updates.

### **4.5. Dependencies Diagram**

![Dependencies diagram](images/dependencies_diagram.png)

An arrow from A to B means A calls an interface of B. Arrows touching the Backend box apply to every backend component: the front-end calls each, and each reads and writes PostgreSQL. The Tournaments and Friends components aren't drawn yet: the front-end calls both, both use PostgreSQL, Tournaments calls OpenWeather, and Games calls Tournaments (`advanceBracket`) and Friends (to check an invite).

### **4.6. Use Case Sequence Diagram (5 Most Major Use Cases)**
1. [**Initiate live match**](#uc1)

![Initiate live match sequence diagram](images/seq_initiate_live_match.png)

2. [**Starting a scheduled match**](#uc2)

![Starting a scheduled match sequence diagram](images/seq_start_scheduled_match.png)

3. [**Submit final score**](#uc3)

![Submit final score sequence diagram](images/seq_submit_final_score.png)

4. [**Create a scheduled match**](#uc4)

![Create a scheduled match sequence diagram](images/seq_create_scheduled_match.png)

5. [**Update game information**](#uc5)

![Update game information sequence diagram](images/seq_update_game_info.png)

### **4.7. Design of Non-Functional Requirements**
1. [**Live score sync latency**](#nfr1) *(planned)*
    - **Implementation**: Each player with a game open joins its Socket.IO room, and a saved score is emitted to the room right after it is written, over connections already open.
2. [**API response time**](#nfr2)
    - **Implementation**: The columns we filter on most are indexed, the feed loads games, players and ratings in one query, forecasts are cached per court for 10 minutes with a 2-second timeout, and matchmaking runs outside the request path.
3. [**Match found latency**](#nfr3)
    - **Implementation**: The matchmaking job runs every 10 seconds without overlapping runs. The greedy matcher sorts each format's queue once, so a run takes milliseconds, and notifications are sent right after each match commits.
