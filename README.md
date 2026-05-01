# game-server
The back-end game server - a Spring Boot application.

## Development
Dependencies are configured with Gradle, and the project uses the [spotless](https://github.com/diffplug/spotless) Gradle plugin to format source code.
Formatting is configured to run as part of the build. The server connects to the [game-database](https://github.com/codenzasoft/game-database) and
requires it to be available on startup - see its README for details. 

To start the server locally in development mode:
```shell
./gradlew bootRun
```

To compile/build the server:
```shell
./gradlew build
```

To format source code:
```shell
./gradlew spotlessApply
```
The server is configured to start on port 8082. Point your browser to [http://localhost:8082](http://localhost:8082)
to login.