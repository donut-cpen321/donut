-- CreateEnum
CREATE TYPE "EquipmentType" AS ENUM ('RACKET', 'BALLS', 'SHOES', 'OTHER');

-- CreateTable
CREATE TABLE "equipment" (
    "id" SERIAL NOT NULL,
    "userId" UUID NOT NULL,
    "type" "EquipmentType" NOT NULL,
    "name" TEXT NOT NULL,
    "isDefault" BOOLEAN NOT NULL DEFAULT false,
    "createdAt" TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "retiredAt" TIMESTAMPTZ(3),

    CONSTRAINT "equipment_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "game_equipment" (
    "gameId" UUID NOT NULL,
    "equipmentId" INTEGER NOT NULL,

    CONSTRAINT "game_equipment_pkey" PRIMARY KEY ("gameId", "equipmentId")
);

-- CreateIndex
CREATE INDEX "equipment_userId_idx" ON "equipment"("userId");

-- CreateIndex
CREATE INDEX "game_equipment_equipmentId_idx" ON "game_equipment"("equipmentId");

-- AddForeignKey
ALTER TABLE "equipment" ADD CONSTRAINT "equipment_userId_fkey" FOREIGN KEY ("userId") REFERENCES "users"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "game_equipment" ADD CONSTRAINT "game_equipment_gameId_fkey" FOREIGN KEY ("gameId") REFERENCES "games"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "game_equipment" ADD CONSTRAINT "game_equipment_equipmentId_fkey" FOREIGN KEY ("equipmentId") REFERENCES "equipment"("id") ON DELETE RESTRICT ON UPDATE CASCADE;