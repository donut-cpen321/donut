/*
  Warnings:

  - The values [PICKLEBALL,BADMINTON] on the enum `Sport` will be removed. If these variants are still used in the database, this will fail.

*/
-- AlterEnum
BEGIN;
-- matchmaking_requests (userId, sport) references user_ratings, so both sport columns
-- must change type together; drop the key during the swap and restore it below.
ALTER TABLE "matchmaking_requests" DROP CONSTRAINT "matchmaking_requests_userId_sport_fkey";
CREATE TYPE "Sport_new" AS ENUM ('TENNIS');
ALTER TABLE "user_ratings" ALTER COLUMN "sport" TYPE "Sport_new" USING ("sport"::text::"Sport_new");
ALTER TABLE "user_availabilities" ALTER COLUMN "sport" TYPE "Sport_new" USING ("sport"::text::"Sport_new");
ALTER TABLE "matchmaking_requests" ALTER COLUMN "sport" TYPE "Sport_new" USING ("sport"::text::"Sport_new");
ALTER TABLE "games" ALTER COLUMN "sport" TYPE "Sport_new" USING ("sport"::text::"Sport_new");
ALTER TYPE "Sport" RENAME TO "Sport_old";
ALTER TYPE "Sport_new" RENAME TO "Sport";
DROP TYPE "public"."Sport_old";
ALTER TABLE "matchmaking_requests" ADD CONSTRAINT "matchmaking_requests_userId_sport_fkey" FOREIGN KEY ("userId", "sport") REFERENCES "user_ratings"("userId", "sport") ON DELETE RESTRICT ON UPDATE CASCADE;
COMMIT;
