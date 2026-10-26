export interface Game {
  id: string;
  sport: Sport;
  format: Format;
  status: GameStatus;
  startsAt: Date;
  courtId: number;
  createdAt: Date;
  gameSource: GameSource;
}

export type Sport = "TENNIS" | "BADMINTON" | "PICKLEBALL";
export type Format = "SINGLES" | "DOUBLES";
export type GameStatus = "BOOKED" | "CANCELLED" | "COMPLETED" | "OPEN";
export type GameSource = "MATCHED" | "SCHEDULED";

export type GameInput = Omit<Game, "id" | "createdAt">;
