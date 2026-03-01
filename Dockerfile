# ==============================================
# Stage 1: Build the project with Maven
# ==============================================
FROM maven:3.9-eclipse-temurin-11 AS builder
WORKDIR /build
COPY . .
RUN mvn clean install -DskipTests -Dmaven.javadoc.skip=true

# ==============================================
# Stage 2: Extend official HMDM image
# (keeps entrypoint, DB config, installer, etc.)
# ==============================================
FROM headwindmdm/hmdm:latest

# Remove old WAR and expanded directory
RUN rm -f /usr/local/tomcat/webapps/ROOT.war && \
    rm -rf /usr/local/tomcat/webapps/ROOT

# Copy our custom-built WAR

COPY --from=builder /build/server/target/launcher.war /usr/local/tomcat/webapps/ROOT.war
