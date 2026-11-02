-- Waiting requests have no location to fill in; they only last until the next match, so drop them
DELETE FROM "matchmaking_requests";

-- AlterTable
ALTER TABLE "matchmaking_requests" ADD COLUMN     "latitude" DOUBLE PRECISION NOT NULL,
ADD COLUMN     "longitude" DOUBLE PRECISION NOT NULL;
