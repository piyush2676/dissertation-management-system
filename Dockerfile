# Build stage: full JDK + Maven. Tests run in CI/locally, not on every deploy.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package \
    && cp target/*.jar app.jar

# Run stage: JRE only.
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/app.jar app.jar
RUN mkdir -p /app/uploads

# Hosting platforms inject PORT and the database parts; map them onto the
# properties application.properties already defines. dms.base-url must be the
# public URL or emailed links point at localhost.
ENV JAVA_OPTS="-Xmx380m -XX:+UseSerialGC"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS \
  -Dserver.port=${PORT:-8080} \
  -Dserver.forward-headers-strategy=framework \
  -Dserver.servlet.session.cookie.secure=true \
  -Dspring.datasource.url=jdbc:postgresql://${DB_HOST}:${DB_PORT:-5432}/${DB_NAME}?sslmode=${DB_SSLMODE:-prefer} \
  -Dspring.datasource.username=${DB_USER} \
  -Dspring.datasource.password=${DB_PASSWORD} \
  -Ddms.base-url=${DMS_BASE_URL:-${RENDER_EXTERNAL_URL:-http://localhost:8080}} \
  -jar app.jar"]
