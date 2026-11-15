FROM node:22-slim
WORKDIR /usr/src/app
COPY package.json ./
RUN npm install --omit=dev
COPY crypto-utils.js provision-admin.js server.js ./
ENV NODE_ENV=production
EXPOSE 8080
CMD ["node", "server.js"]
