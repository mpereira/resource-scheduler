FROM java:8-jre
MAINTAINER Murilo Pereira <murilo@murilopereira.com>

ADD target/uberjar/resource-scheduler-0.1.0-SNAPSHOT-standalone.jar /srv

ENTRYPOINT ["java", "-jar", "/srv/resource-scheduler-0.1.0-SNAPSHOT-standalone.jar"]
