-- CreateTable
CREATE TABLE "matchmaking_requests" (
    "userId" UUID NOT NULL,
    "sport" "Sport" NOT NULL,
    "format" "FormatPreference" NOT NULL,
    "createdAt" TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "matchmaking_requests_pkey" PRIMARY KEY ("userId")
);

-- AddForeignKey
ALTER TABLE "matchmaking_requests" ADD CONSTRAINT "matchmaking_requests_userId_fkey" FOREIGN KEY ("userId") REFERENCES "users"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "matchmaking_requests" ADD CONSTRAINT "matchmaking_requests_userId_sport_fkey" FOREIGN KEY ("userId", "sport") REFERENCES "user_ratings"("userId", "sport") ON DELETE RESTRICT ON UPDATE CASCADE;
