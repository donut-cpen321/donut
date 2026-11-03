import { Format } from "../../src/generated/prisma/enums";
import {
  BRACKET_TEAMS,
  bracketSlots,
  type Entrant,
  FINAL_ROUND,
  formTeams,
  nextSlot,
  QUARTERFINAL_SEEDS,
  ROUND_INTERVAL_MS,
  roundStartsAt,
  teamSizeOf,
  tournamentCapacity,
  winningSide,
} from "../../src/tournaments/bracket";

const registeredAt = new Date("2026-11-01T12:00:00Z");

// Entrants p1..pN, rated from `topRating` down in steps of 10, all registered at the same moment
const entrants = (count: number, topRating = 2000): Entrant[] =>
  Array.from({ length: count }, (_, index) => ({
    userId: `p${index + 1}`,
    rating: topRating - index * 10,
    joinedAt: registeredAt,
  }));

// Reverses the entrants, so tests can show the order they arrive in does not matter
const shuffled = (list: Entrant[]) => [...list].reverse();

// Interface teamSizeOf(format) and tournamentCapacity(format)
describe("Unit: tournament capacity", () => {
  // Input: SINGLES and DOUBLES
  // Expected output: 1 and 2 players per team; 8 and 16 players to fill the bracket
  test("Fills the bracket with 8 teams of the format's size", () => {
    expect(teamSizeOf(Format.SINGLES)).toBe(1);
    expect(teamSizeOf(Format.DOUBLES)).toBe(2);
    expect(tournamentCapacity(Format.SINGLES)).toBe(BRACKET_TEAMS);
    expect(tournamentCapacity(Format.DOUBLES)).toBe(BRACKET_TEAMS * 2);
  });
});

// Interface formTeams(entrants, format)
describe("Unit: formTeams", () => {
  // Input: 8 singles players with distinct ratings, in reverse order
  // Expected output: 8 one-player teams, seeded from the highest rating down
  test("Seeds singles players by rating", () => {
    const teams = formTeams(shuffled(entrants(8)), Format.SINGLES);

    expect(teams).toEqual([["p1"], ["p2"], ["p3"], ["p4"], ["p5"], ["p6"], ["p7"], ["p8"]]);
  });

  // Input: 8 singles players, two pairs with equal ratings registered at different times
  // Expected output: the earlier registration wins the tie, then the user ID
  test("Breaks rating ties by registration time, then by user ID", () => {
    const tied: Entrant[] = [
      { userId: "late", rating: 1500, joinedAt: new Date("2026-11-01T13:00:00Z") },
      { userId: "early", rating: 1500, joinedAt: new Date("2026-11-01T12:00:00Z") },
      { userId: "b", rating: 1400, joinedAt: registeredAt },
      { userId: "a", rating: 1400, joinedAt: registeredAt },
      ...entrants(4, 1300),
    ];

    const seeds = formTeams(tied, Format.SINGLES).map(([userId]) => userId);

    expect(seeds.slice(0, 4)).toEqual(["early", "late", "a", "b"]);
  });

  // Input: 16 doubles players rated 2000, 1990, ..., 1850
  // Expected output: 8 balanced pairs, strongest with weakest (p1+p16, p2+p15, ...)
  test("Pairs the strongest doubles player with the weakest", () => {
    const teams = formTeams(shuffled(entrants(16)), Format.DOUBLES);

    // Order-independent key for a pair, e.g. "p1+p16"
    const pairKey = (team: string[]) => [...team].sort().join("+");
    const expectedPairs = [
      ["p1", "p16"],
      ["p2", "p15"],
      ["p3", "p14"],
      ["p4", "p13"],
      ["p5", "p12"],
      ["p6", "p11"],
      ["p7", "p10"],
      ["p8", "p9"],
    ];
    expect(teams.map(pairKey).sort()).toEqual(expectedPairs.map(pairKey).sort());
  });

  // Input: 16 doubles players whose balanced pairs have different averages
  // Expected output: the teams are seeded by average rating, highest first
  test("Seeds doubles teams by average rating", () => {
    const ratings = [2400, 2000, 1900, 1850, 1800, 1750, 1700, 1650, 1600, 1550, 1500, 1450, 1400, 1350, 800, 700];
    const players: Entrant[] = ratings.map((rating, index) => ({
      userId: `p${index + 1}`,
      rating,
      joinedAt: registeredAt,
    }));
    const ratingOf = new Map(players.map(({ userId, rating }) => [userId, rating]));
    const average = (team: string[]) =>
      team.reduce((sum, userId) => sum + (ratingOf.get(userId) ?? 0), 0) / team.length;

    const averages = formTeams(players, Format.DOUBLES).map(average);

    expect(averages).toEqual([...averages].sort((a, b) => b - a));
    // p2 (2000) + p15 (800) averages 1400, the weakest pair, so it is seeded last
    expect(formTeams(players, Format.DOUBLES).at(-1)?.sort()).toEqual(["p15", "p2"]);
  });

  // Input: every player in a 16-player doubles bracket
  // Expected output: each player is on exactly one team
  test("Puts every player on exactly one team", () => {
    const userIds = formTeams(entrants(16), Format.DOUBLES).flat();

    expect(new Set(userIds).size).toBe(16);
  });

  // Input: 7 singles players, or 17 doubles players
  // Expected behavior: throws, since the bracket must be filled exactly
  test.each([
    [Format.SINGLES, 7],
    [Format.DOUBLES, 17],
  ])("Rejects a %s bracket with %i players", (format, count) => {
    expect(() => formTeams(entrants(count), format)).toThrow(/bracket needs/);
  });
});

// Interface bracketSlots()
describe("Unit: bracketSlots", () => {
  // Input: none
  // Expected output: 4 quarterfinals with seeds 1v8, 4v5, 3v6, 2v7, then 2 semifinals and a final with no teams yet
  test("Lists the seeded quarterfinals, then the empty semifinals and final", () => {
    expect(bracketSlots()).toEqual([
      { round: 1, position: 0, team1: 1, team2: 8 },
      { round: 1, position: 1, team1: 4, team2: 5 },
      { round: 1, position: 2, team1: 3, team2: 6 },
      { round: 1, position: 3, team1: 2, team2: 7 },
      { round: 2, position: 0, team1: null, team2: null },
      { round: 2, position: 1, team1: null, team2: null },
      { round: 3, position: 0, team1: null, team2: null },
    ]);
  });

  // Input: the quarterfinal seeds
  // Expected output: every seed from 1 to 8 plays exactly once
  test("Gives every seed exactly one quarterfinal", () => {
    const seeds = QUARTERFINAL_SEEDS.flat();

    expect([...seeds].sort((a, b) => a - b)).toEqual([1, 2, 3, 4, 5, 6, 7, 8]);
  });
});

// Interface nextSlot(round, position)
describe("Unit: nextSlot", () => {
  // Input: each quarterfinal and semifinal
  // Expected output: winners of positions 0 and 1 meet in position 0 of the next round, on sides 1 and 2
  test.each([
    [1, 0, { round: 2, position: 0, side: 1 }],
    [1, 1, { round: 2, position: 0, side: 2 }],
    [1, 2, { round: 2, position: 1, side: 1 }],
    [1, 3, { round: 2, position: 1, side: 2 }],
    [2, 0, { round: 3, position: 0, side: 1 }],
    [2, 1, { round: 3, position: 0, side: 2 }],
  ])("Sends the winner of round %i position %i on", (round, position, expected) => {
    expect(nextSlot(round, position)).toEqual(expected);
  });

  // Input: the final
  // Expected output: null, since the winner is the champion
  test("Has no next match after the final", () => {
    expect(nextSlot(FINAL_ROUND, 0)).toBeNull();
  });

  // Input: the seeds following the bracket with the higher seed winning every match
  // Expected output: seed 1 meets seed 2 in the final
  test("Keeps the top two seeds apart until the final", () => {
    const semifinalists = new Map<string, number>();
    QUARTERFINAL_SEEDS.forEach(([team1, team2], position) => {
      const next = nextSlot(1, position);
      semifinalists.set(`${next?.position}-${next?.side}`, Math.min(team1, team2));
    });

    expect([semifinalists.get("0-1"), semifinalists.get("0-2")]).toEqual([1, 4]);
    expect([semifinalists.get("1-1"), semifinalists.get("1-2")]).toEqual([3, 2]);
  });
});

// Interface winningSide(rounds)
describe("Unit: winningSide", () => {
  // Input: rounds that team 1 or team 2 won the majority of
  // Expected output: the side that won more rounds
  test.each([
    ["team 1 in straight sets", [[6, 4], [6, 3]], 1],
    ["team 2 in straight sets", [[4, 6], [3, 6]], 2],
    ["team 1 in three sets", [[6, 4], [3, 6], [7, 5]], 1],
    ["team 2 in three sets", [[6, 4], [3, 6], [5, 7]], 2],
    ["team 2 winning fewer games but more sets", [[6, 0], [6, 7], [6, 7]], 2],
  ] as [string, [number, number][], number][])("Picks %s", (_description, rounds, expected) => {
    expect(winningSide(rounds)).toBe(expected);
  });
});

// Interface roundStartsAt(startsAt, round, now)
describe("Unit: roundStartsAt", () => {
  const startsAt = new Date("2026-11-08T17:00:00Z");
  const beforeStart = new Date("2026-11-08T09:00:00Z");

  // Input: rounds 1, 2 and 3 before the tournament starts
  // Expected output: the start time, then one and two hours later
  test.each([
    [1, 0],
    [2, 1],
    [3, 2],
  ])("Schedules round %i %i hour(s) after the start", (round, hours) => {
    expect(roundStartsAt(startsAt, round, beforeStart)).toEqual(
      new Date(startsAt.getTime() + hours * ROUND_INTERVAL_MS),
    );
  });

  // Input: the semifinal, booked after its scheduled time because the quarterfinals ran late
  // Expected output: the current time, never a time in the past
  test("Starts a late round now", () => {
    const late = new Date(startsAt.getTime() + 90 * 60 * 1000);

    expect(roundStartsAt(startsAt, 2, late)).toEqual(late);
  });
});
