# Multi-stage Dockerfile
FROM node:18 AS build

# Download Java 17 (LTS) and Clojure for compilation phase
ENV CLOJURE_VER=1.10.3.814
WORKDIR /tmp

RUN apt-get update && apt-get upgrade \
  && apt-get install --yes openjdk-17-jdk-headless \
  && npm install -g shadow-cljs \
  && curl -s https://download.clojure.org/install/linux-install-$CLOJURE_VER.sh | bash

WORKDIR /usr/src/app

# Copying dependency first prevents re-running npm install on every code change.
COPY package*.json ./

# Install dependencies.
RUN npm ci

# Copy local code to the container image.
COPY . ./

# Transpile the Clojurescript into Javascript using Shadow-CLJS
RUN npx shadow-cljs release hubspot

#################################################################

# Use the official lightweight node image v18 (LTS), for release.
# https://hub.docker.com/_/node
FROM node:18-alpine

# Use TCP port 8080, as default. Container reads ENV "PORT".
# If PORT not set, the container defaults to TCP port 3000.
ENV PORT=8080
EXPOSE 8080

WORKDIR /usr/src/app

# Run the web server on container startup
CMD ["node", "hubspot-webhooks.js"]

# Make dependencies available
COPY ./package*.json ./
RUN npm ci --only=production;rm package*.json

# Copy over build artifact
COPY --from=build /usr/src/app/target/main.js /usr/src/app/hubspot-webhooks.js
