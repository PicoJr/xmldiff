FROM gradle:6.5.0-jdk11 AS build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
RUN gradle installDist --no-daemon
RUN ls -la

FROM openjdk:11-jre-slim

RUN mkdir /app

COPY --from=build /home/gradle/src/build/install/xmldiff /app/xmldiff
RUN ls -Rla /app/
RUN ls -la /app/xmldiff/bin/xmldiff

ENTRYPOINT ["/app/xmldiff/bin/xmldiff"]
