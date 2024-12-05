FROM ubuntu:21.04

RUN apt-get update --yes && env DEBIAN_FRONTEND=noninteractive apt-get install openjdk-15-jdk maven --yes --no-install-recommends

# assumes that the project has already been built
COPY target/LRS-*.jar LRS.jar
COPY target/lib/*.jar /lib/

ENTRYPOINT ["java", "-jar", "LRS.jar"]
