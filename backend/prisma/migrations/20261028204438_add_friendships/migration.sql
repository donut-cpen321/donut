-- CreateEnum
CREATE TYPE "FriendshipStatus" AS ENUM ('PENDING', 'ACCEPTED');

-- CreateTable
CREATE TABLE "friendships" (
    "requesterId" UUID NOT NULL,
    "addresseeId" UUID NOT NULL,
    "status" "FriendshipStatus" NOT NULL DEFAULT 'PENDING',
    "createdAt" TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "acceptedAt" TIMESTAMPTZ(3),

    CONSTRAINT "friendships_pkey" PRIMARY KEY ("requesterId","addresseeId")
);

-- CreateIndex
CREATE INDEX "friendships_addresseeId_idx" ON "friendships"("addresseeId");

-- AddForeignKey
ALTER TABLE "friendships" ADD CONSTRAINT "friendships_requesterId_fkey" FOREIGN KEY ("requesterId") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "friendships" ADD CONSTRAINT "friendships_addresseeId_fkey" FOREIGN KEY ("addresseeId") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- Not expressible in schema.prisma: one row per user pair regardless of direction
CREATE UNIQUE INDEX "friendships_pair_key" ON "friendships"(LEAST("requesterId", "addresseeId"), GREATEST("requesterId", "addresseeId"));

-- Not expressible in schema.prisma: users can't friend themselves
ALTER TABLE "friendships" ADD CONSTRAINT "friendships_no_self" CHECK ("requesterId" <> "addresseeId");
