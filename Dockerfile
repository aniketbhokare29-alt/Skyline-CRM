FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY backend/pom.xml backend/pom.xml
RUN mvn -f backend/pom.xml dependency:go-offline
COPY backend backend
COPY frontend frontend
RUN mvn -f backend/pom.xml -DskipTests package dependency:copy-dependencies -DoutputDirectory=target/dependency

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/backend/target/classes /app/backend/target/classes
COPY --from=build /app/backend/target/dependency /app/backend/target/dependency
COPY --from=build /app/frontend /app/frontend
ENV CRM_FRONTEND_DIR=/app/frontend
ENV CRM_UPLOAD_DIR=/app/uploads
EXPOSE 8080
CMD ["sh", "-c", "exec java -cp '/app/backend/target/classes:/app/backend/target/dependency/*' com.saivandan.crm.App"]
