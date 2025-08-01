#!/bin/bash

# Welcome
echo "Welcome to MongoDB Schemas in Java!"
echo "To get started, please ensure you have the following prerequisites installed:"
echo "- Java Development Kit (JDK) 24"
echo "- Apache Maven"
echo "- MongoDB Atlas account (optional, for remote connection)"
echo "- MongoDB installed locally (optional, for local connection)"
echo "The MonogoDB sample database 'sample-weather' loaded in MongoDB (see https://www.mongodb.com/docs/guides/atlas/sample-data/)."
# Prompt the user to enter the MongoDB connection string
read -p "Enter your MongoDB Atlas connection string (skip to run local): " MONGODB_CONN_STRING

# Export the connection string as an environment variable
if [ -z "$MONGODB_CONN_STRING" ]; then
  echo "No connection string provided. Running with default local MongoDB settings."
  MONGODB_CONN_STRING="mongodb://localhost:27017"
else
  echo "Using provided MongoDB connection string."
fi
export MONGODB_CONNECTION_STRING="$MONGODB_CONN_STRING"

# Run the Spring Boot application using Maven
echo "Running Spring Boot application with MONGODB_CONNECTION_STRING..."
mvn -q spring-boot:run