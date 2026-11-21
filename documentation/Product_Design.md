# Donut — Product Design

## 1. Scope

Donut is an instant matchmaking and ranking app for people who play competitive tennis but can't commit to a weekly league.

The first version supports **tennis only**, in **singles (1v1)** and **doubles (2v2)**. Other sports may be added later, which means adding a sport to the database and its scoring rules ([§6](#6-scoring)) and rating parameters ([§7.2](#72-formula)).

## 2. Navigation

The app has four tabs.

| Tab | Purpose |
| --- | --- |
| **Profile** | Rating, match history (with a summary per game), friends and equipment. |
| **Matchmaking** | Find a match now ([§5](#5-matchmaking)). |
| **Leaderboard** | Players ranked by rating, with your own rank. |
| **Games** | The feed of games and tournaments to join, your upcoming games, and scheduling a game or tournament. |

## 3. Feed

- Lists the scheduled games with open spots and the tournaments open for registration that haven't started, soonest first, with the weather forecast.
- Users join a game or register for a tournament from the feed. A game's players can also invite friends to it.

The feed is for finding games by hand. Matchmaking ([§5](#5-matchmaking)) is the other way to get a game.

## 4. Scheduling a game

The creator picks:

- Format: **singles or doubles**
- Date and time
- Court, picked from a list

Any player in a game can move it to another time or court, leave it (until 30 minutes before the start) or cancel it, and report or confirm its final score ([§6](#6-scoring)). There is no owner role.

## 5. Matchmaking

Players ask for a game now instead of creating one. The system matches them as soon as it can.

### 5.1. Joining the queue

A player picks:

- Format: **singles, doubles, or either**
- Where they want to play: their current location, or an address

They stay in the queue until they are matched or leave it.

### 5.2. Match criteria

Every 10 seconds, players are matched when they share all of:

- Format (a player who chose "either" fits singles and doubles)
- Location: every player is within **10 km** of the others
- A similar rating ([§7](#7-rating)): within **200 points** in singles and **400** in doubles

The longest-waiting player is matched first, with the closest-rated players near them. Singles needs 2 players; doubles needs 4, so doubles matches are found less often. That is accepted for now.

### 5.3. The proposed game

- It starts **one hour** after the match is found.
- The court is the one most of the players prefer within 10 km of all of them, or else the court nearest the middle of them.
- In doubles, the highest- and lowest-rated players play against the middle two, so the teams are balanced.

### 5.4. Accepting a match

1. The system notifies every matched player.
2. Each player accepts or declines.
3. The game is booked once **all** players have accepted.

- If anyone declines, the match is cancelled for everyone.
- Players can accept until **30 minutes before the game starts**. A match not accepted by everyone by then is cancelled.
- After a cancelled match, players join the queue again themselves.

## 6. Scoring

### 6.1. Score structure

Every match is a list of **rounds**, each with a score per side. The winner is the side that wins more rounds.

In tennis, a round is a **set** and its score counts **games** (e.g. 6–4). A set is won at 6 games; past 6 it is won by exactly 2 games (e.g. 8–6), or 7–6 after a tiebreak. Any score up to 6 games is accepted, so 99–0 can't be entered, and a long set like 99–97 rates like 8–6.

### 6.2. Who enters the score

- Any player in the match can report the final score once the game has started.
- The score only counts once **every player confirms it**. Until then it waits on the game screen, which shows who reported it and who has confirmed so far. Everyone in the match sees each step live.
- The other players get a push notification asking them to confirm it.
- A player who disagrees **suggests a correction**: their score replaces the one waiting, and everyone has to confirm again. The reporter can change their score the same way.
- When the last player confirms, the game is completed and ratings update. A completed score can't change.

## 7. Rating

### 7.1. Model

- Every player has one rating, starting at **1500**.
- Ratings use **Elo with margin of victory**: the rating change depends on both the win/loss result and on whether the side won by more or less than expected.
- **Doubles:** a team's rating is the average of its two players' ratings. Each player on a team receives **half of the team's rating change**, calculated with their own `K`, so teammates with the same number of games move by the same amount.

### 7.2. Formula

For side A (team 1) with rating `R_A` against side B (team 2) with rating `R_B`:

```
ΔR    = R_A - R_B
E_A   = 1 / (1 + 10^(-ΔR / 400))          expected outcome (0 to 1)
E_MOV = α * tanh(β * ΔR)                  expected margin of victory
ΔMOV  = T_MOV - E_MOV                     margin surprise
Δ     = K * (S_A - E_A) + λ * ΔMOV        rating change for A

R_A'  = R_A + Δ
R_B'  = R_B - Δ
```

- `S_A` is 1 if A wins the match and 0 if A loses. The match winner is the side that wins more rounds (see 6.1).
- `T_MOV` is A's total points minus B's total points across all rounds. The result uses rounds, but the margin uses points, because points show how close the match really was.
- `E_MOV` grows with the rating gap but levels off at `α`, so a large gap never predicts an unrealistic blowout.
- `400` is the standard Elo scale: a 400-point gap gives the stronger side 10-to-1 odds of winning. Changing it would only rescale stored ratings.
- Because of the margin term, a heavy favorite who wins by less than expected can lose a small amount of rating.

`K` depends on how many rated (completed) games the player has played before this one, `N`, so new players reach their real rating faster and established ratings stay stable. Each player uses their own `K` (in doubles, each player gets half of `K_player * (S_A - E_A) + λ * ΔMOV`), so a game is only zero-sum when every player has the same `K`.

The tennis parameters are:

| `K` (kFactor) | `α` (maxExpectedMargin) | `β` (marginGrowthRate) | `λ` (marginWeight) |
| --- | --- | --- | --- |
| `250 / (N + 5)^0.4` (≈131 new, ≈85 after 10 games, ≈39 after 100) | 6 | 0.0025 | 1.5 |

The code also holds the parameters for when other sports are added: badminton `K = 32`, `α = 15`, `β = 0.005`, `λ = 0.5`; pickleball `K = 120` for a player's first 8 games and `60` after, `α = 8`, `β = 0.0025`, `λ = 1`.

Players can be listed in any order; each player's `team` decides which side they are on.

A game is rejected, and no rating changes, if:

- the player count doesn't match the format (2 for singles, 4 for doubles),
- the players aren't split evenly between the two teams,
- the same player appears more than once,
- any player's rating is not a finite number, or games-played count is not a non-negative whole number,
- it has no rounds,
- any round score is not a non-negative whole number,
- any round is tied, or
- both sides won the same number of rounds, so the match has no winner.

### 7.3. Future: Glicko

We will likely move to **Glicko** (the system chess.com uses). It adds a rating deviation per player that grows when they haven't played for a while, which suits players with irregular schedules.

## 8. External services

| Service | Use |
| --- | --- |
| Google Sign-In | Signing in |
| Google Maps Platform (Maps and Places) | Showing a matched game's court on a map, and the address to be matched near |
| OpenWeather | Forecasts in the feed |
| Firebase Cloud Messaging | Push notifications for matches, game invites and scores to confirm |

## 9. Out of scope for now

- Recurring games
- Settling a score the players never agree on (e.g. a deadline or a moderator): the game stays in progress until they do
- Glicko ([§7.3](#73-future-glicko))
- Sports other than tennis (e.g. pickleball and badminton)
