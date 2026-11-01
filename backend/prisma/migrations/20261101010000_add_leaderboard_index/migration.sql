-- CreateIndex
CREATE INDEX "users_rating_username_idx" ON "users"("rating" DESC, "username");
