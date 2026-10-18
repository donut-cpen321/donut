# Donut — Product Design

## 1. Scope

Donut is an instant matchmaking and ranking app for people who play competitive sports but can't commit to a weekly league.

The first version supports **racket sports only**:

- Tennis
- Pickleball
- Badminton

Every sport supports **singles (1v1)** and **doubles (2v2)** from the start. Other sports (e.g. basketball) may be added later; nothing in the design should assume racket sports beyond the scoring rules in [§6](#6-scoring).

## 2. Navigation

The app has three tabs.

| Tab | Purpose |
| --- | --- |
| **Discover** | Browse games: a feed of upcoming games and a calendar of all games. |
| **Play** | Create a game, post availability, see your current game and enter its score. |

## 3. Discover tab

### 3.1. Feed

- Shows games happening **now or in the next 24 hours**.
- Shows games in **every sport**, not only the sports the user plays. A tennis player still sees pickleball and badminton games.
- Users can join a game from the feed if it has open spots.

The feed exists for discovery and joining games by hand. Automatic matchmaking ([§5](#5-matchmaking)) is the other way to get a game.

### 3.2. Calendar

- Week-by-week view of all games, including games created further in the future than the feed shows.
- No recurring games.

## 4. Play tab

### 4.1. Create a game

The creator picks:

- Sport
- Format: **singles or doubles**
- Date and time
- Court

When a game is created, players whose availability fits it ([§5.1](#51-availability)) get a live notification and can join.

### 4.2. Current game

While a player is in a game, the Play tab shows that game, its live score, and lets them enter the score ([§6](#6-scoring)).

## 5. Matchmaking

Players can be matched without anyone creating a game, by posting when they are free. The system tries to match players as soon as possible.

### 5.1. Availability

A player posts availability with:

- Sport
- Format: **singles, doubles, or either**
- Time, as one of:
  - a **weekly pattern** (e.g. every Tuesday 6–8pm), or
  - a **specific date** (e.g. Tue Oct 7, 6–8pm).
- Preferred location

Weekly patterns are expanded into specific dates before matching, so the matcher only ever deals with concrete date ranges.

### 5.2. Preferred courts

Each player saves a few preferred courts, picked on Google Maps. This is how the system decides where a matched game is played.

### 5.3. Match criteria

Players are matched when they share all of:

- Sport
- Format (a player who chose "either" fits singles and doubles)
- An overlapping time slot
- A preferred court
- A similar rating ([§7](#7-rating))

Open decision: How close the ratings between two players must be to match? 

Singles needs 2 players who fit; doubles needs 4. Doubles matches will therefore be found less often. That is accepted for now.

### 5.4. Accepting a match

1. The system finds a match and notifies every player in it.
2. Each player accepts or declines.
3. The game is booked once **all** players have accepted.

Open decision: when 4 players are matched, how they are split into teams (just based on rating or they should be able to choose?)

**Deadline:** players can accept until **30 minutes before the game starts**.

- If any player has not accepted by then, the match is cancelled for everyone. That player can no longer join it.
- The players who did accept go back into the matching pool.
- The system does not create matches for games starting in less than 30 minutes, since there would be no time left to accept.

## 6. Scoring

### 6.1. Score structure

Every match is a list of **rounds**, each with a score per side. The winner is the side that wins more rounds.

| Sport | A round is a… | Round score counts… | Example |
| --- | --- | --- | --- |
| Tennis | set | games | 6–4 |
| Pickleball | game | points | 11–9 |
| Badminton | game | points | 21–15 |

The same data structure serves all three sports.

### 6.2. Who enters the score

- Any player in the match can enter the score.
- The **first score entered is final**. Everyone in the match sees it.
- Scores are trust-based: there is no confirmation or dispute step.

## 7. Rating

### 7.1. Model

- Every player has a **separate rating per sport**, starting at **1500**.
- Ratings use **Elo with margin of victory**: the rating change depends on both the win/loss result and on whether the side won by more or less than expected.
- **Doubles:** a team's rating is the average of its two players' ratings. Each player on a team receives **half of the team's rating change**, so both teammates move by the same amount.

### 7.2. Formula

For side A (team 0) with rating `R_A` against side B (team 1) with rating `R_B`:

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
- `400` is the standard Elo scale: a 400-point gap gives the stronger side 10-to-1 odds of winning. It is the same for every sport, since changing it would only rescale stored ratings.
- Because of the margin term, a heavy favorite who wins by less than expected can lose a small amount of rating.

The parameters are set per sport:

| Sport | `K` (kFactor) | `α` (maxExpectedMargin) | `β` (marginGrowthRate) | `λ` (marginWeight) |
| --- | --- | --- | --- | --- |
| Tennis | 50 | 6 | 0.0025 | 1.5 |
| Pickleball | 60 | 8 | 0.0025 | 1.0 |
| Badminton | 32 | 15 | 0.005 | 0.5 |

Players can be listed in any order; each player's `team` decides which side they are on.

A game is rejected, and no rating changes, if:

- the player count doesn't match the format (2 for singles, 4 for doubles),
- the players aren't split evenly between the two teams,
- the same player appears more than once,
- any player's rating is not a finite number,
- it has no rounds,
- any round score is not a non-negative whole number,
- any round is tied, or
- both sides won the same number of rounds, so the match has no winner.

### 7.3. Future: Glicko

We will likely move to **Glicko** (the system chess.com uses). It adds a rating deviation per player that grows when they haven't played for a while, which suits players with irregular schedules.

## 8. External services

| Service | Use |
| --- | --- |
| Google Maps Platform | Picking courts, showing game locations |
| OpenWeather | Weather for outdoor games |
| Google Calendar API (Optional) | Adding games to the user's calendar |
| Firebase Cloud Messaging | Push notifications for matches and new games|

## 9. Out of scope for now

- Recurring games
- Score confirmation or disputes
- Glicko ([§7.3](#73-future-glicko))
- Sports other than tennis, pickleball and badminton
