-- CreateEnum
CREATE TYPE "Sport" AS ENUM ('TENNIS', 'PICKLEBALL', 'BADMINTON');

-- CreateEnum
CREATE TYPE "Format" AS ENUM ('SINGLES', 'DOUBLES');

-- CreateEnum
CREATE TYPE "FormatPreference" AS ENUM ('SINGLES', 'DOUBLES', 'EITHER');

-- CreateEnum
CREATE TYPE "GameStatus" AS ENUM ('OPEN', 'BOOKED', 'COMPLETED', 'CANCELLED');

-- CreateEnum
CREATE TYPE "GameSource" AS ENUM ('SCHEDULED', 'MATCHED');

-- CreateTable
CREATE TABLE "users" (
    "id" UUID NOT NULL,
    "googleSub" TEXT NOT NULL,
    "username" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "email" TEXT NOT NULL,
    "fcmToken" TEXT,
    "createdAt" TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "users_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "user_ratings" (
    "userId" UUID NOT NULL,
    "sport" "Sport" NOT NULL,
    "rating" DOUBLE PRECISION NOT NULL DEFAULT 1500,
    "gamesPlayed" INTEGER NOT NULL DEFAULT 0,

    CONSTRAINT "user_ratings_pkey" PRIMARY KEY ("userId","sport")
);

-- CreateTable
CREATE TABLE "courts" (
    "id" SERIAL NOT NULL,
    "googlePlaceId" TEXT,
    "name" TEXT NOT NULL,
    "latitude" DOUBLE PRECISION NOT NULL,
    "longitude" DOUBLE PRECISION NOT NULL,

    CONSTRAINT "courts_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "user_availabilities" (
    "id" SERIAL NOT NULL,
    "userId" UUID NOT NULL,
    "sport" "Sport" NOT NULL,
    "format" "FormatPreference" NOT NULL,
    "weekday" INTEGER,
    "date" DATE,
    "startTime" TIME(0) NOT NULL,
    "endTime" TIME(0) NOT NULL,

    CONSTRAINT "user_availabilities_pkey" PRIMARY KEY ("id"),
    CONSTRAINT "user_availabilities_exactly_one_kind_constraint"
        CHECK (("weekday" IS NOT NULL) <> ("date" IS NOT NULL))
);

-- CreateTable
CREATE TABLE "games" (
    "id" UUID NOT NULL,
    "sport" "Sport" NOT NULL,
    "format" "Format" NOT NULL,
    "status" "GameStatus" NOT NULL,
    "startsAt" TIMESTAMPTZ(3) NOT NULL,
    "courtId" INTEGER NOT NULL,
    "createdAt" TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "gameSource" "GameSource" NOT NULL,

    CONSTRAINT "games_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "game_users" (
    "gameId" UUID NOT NULL,
    "userId" UUID NOT NULL,
    "team" INTEGER NOT NULL,
    "acceptedAt" TIMESTAMPTZ(3),

    CONSTRAINT "game_users_pkey" PRIMARY KEY ("gameId","userId")
);

-- CreateTable
CREATE TABLE "game_rounds" (
    "gameId" UUID NOT NULL,
    "number" INTEGER NOT NULL,
    "team1Score" INTEGER NOT NULL,
    "team2Score" INTEGER NOT NULL,

    CONSTRAINT "game_rounds_pkey" PRIMARY KEY ("gameId","number")
);

-- CreateTable
CREATE TABLE "_CourtToUser" (
    "A" INTEGER NOT NULL,
    "B" UUID NOT NULL,

    CONSTRAINT "_CourtToUser_AB_pkey" PRIMARY KEY ("A","B")
);

-- CreateIndex
CREATE UNIQUE INDEX "users_googleSub_key" ON "users"("googleSub");

-- CreateIndex
CREATE UNIQUE INDEX "users_username_key" ON "users"("username");

-- CreateIndex
CREATE UNIQUE INDEX "courts_googlePlaceId_key" ON "courts"("googlePlaceId");

-- CreateIndex
CREATE INDEX "_CourtToUser_B_index" ON "_CourtToUser"("B");

-- AddForeignKey
ALTER TABLE "user_ratings" ADD CONSTRAINT "user_ratings_userId_fkey" FOREIGN KEY ("userId") REFERENCES "users"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "user_availabilities" ADD CONSTRAINT "user_availabilities_userId_fkey" FOREIGN KEY ("userId") REFERENCES "users"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "games" ADD CONSTRAINT "games_courtId_fkey" FOREIGN KEY ("courtId") REFERENCES "courts"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "game_users" ADD CONSTRAINT "game_users_gameId_fkey" FOREIGN KEY ("gameId") REFERENCES "games"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "game_users" ADD CONSTRAINT "game_users_userId_fkey" FOREIGN KEY ("userId") REFERENCES "users"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "game_rounds" ADD CONSTRAINT "game_rounds_gameId_fkey" FOREIGN KEY ("gameId") REFERENCES "games"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "_CourtToUser" ADD CONSTRAINT "_CourtToUser_A_fkey" FOREIGN KEY ("A") REFERENCES "courts"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "_CourtToUser" ADD CONSTRAINT "_CourtToUser_B_fkey" FOREIGN KEY ("B") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;
