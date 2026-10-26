/*
  Warnings:

  - You are about to drop the column `sport` on the `games` table. All the data in the column will be lost.
  - You are about to drop the column `sport` on the `matchmaking_requests` table. All the data in the column will be lost.
  - You are about to drop the column `sport` on the `user_availabilities` table. All the data in the column will be lost.
  - You are about to drop the `user_ratings` table. If the table is not empty, all the data it contains will be lost.

*/
-- DropForeignKey
ALTER TABLE "matchmaking_requests" DROP CONSTRAINT "matchmaking_requests_userId_sport_fkey";

-- DropForeignKey
ALTER TABLE "user_ratings" DROP CONSTRAINT "user_ratings_userId_fkey";

-- AlterTable
ALTER TABLE "games" DROP COLUMN "sport";

-- AlterTable
ALTER TABLE "matchmaking_requests" DROP COLUMN "sport";

-- AlterTable
ALTER TABLE "user_availabilities" DROP COLUMN "sport";

-- AlterTable
ALTER TABLE "users" ADD COLUMN     "gamesPlayed" INTEGER NOT NULL DEFAULT 0,
ADD COLUMN     "rating" DOUBLE PRECISION NOT NULL DEFAULT 1500;

-- DropTable
DROP TABLE "user_ratings";

-- DropEnum
DROP TYPE "Sport";
