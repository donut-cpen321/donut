# syntax=docker/dockerfile:1

FROM node:22-alpine
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

# Copy the rest of the application
COPY . .

# Generate the Prisma migrations
RUN npx prisma generate

# Set the environment variables
ENV PORT=3000 
ENV NODE_ENV=development

EXPOSE 3000

# Apply pending migrations, then start the application
CMD ["sh", "-c", "npx prisma migrate deploy && npm run dev"]