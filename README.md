# Introduction
This is a simple service for managing "Todo" tasks. The goal of this project is to demonstrate the Vert.x stack.
  
# How to run
  - `docker-compose up` to run the whole project
  - `mvn test` to run tests using embedded MongoDB instance
  - `java -jar target/todo-service.jar -conf src/config/config.json` to run from the fat jar using local MongoDB instance

# How to test endpoints
  - get all  
    `curl http://localhost:8080/todo -i -H "Accept: application/stream+json"`
  - get by ID (please replace {id} with real identifier)  
    `curl http://localhost:8080/todo/{id} -i -H "Accept: application/json"`
  - create  
    `curl http://localhost:8080/todo -i -H "Content-Type: application/json" -H "Accept: application/json" -X POST -d '{"description":"Take a nap"}'`