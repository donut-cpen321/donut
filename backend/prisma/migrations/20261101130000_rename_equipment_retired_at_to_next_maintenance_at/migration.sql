ALTER TABLE "equipment" RENAME COLUMN "retiredAt" TO "nextMaintenanceAt";
ALTER TABLE "equipment" ADD COLUMN "retiredAt" TIMESTAMPTZ(3);