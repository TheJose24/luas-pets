# Etapa 1: compilación
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# Copiar primero el pom para aprovechar la caché de capas de Docker:
# las dependencias solo se vuelven a descargar si cambia el pom
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -B

# Etapa 2: ejecución
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
# -Xmx350m: el plan gratuito de Render da 512 MB de RAM.
# Limitar el heap deja margen para la memoria no-heap de la JVM
# y evita que el sistema operativo termine el proceso.
ENTRYPOINT ["sh", "-c", "java -Dspring.profiles.active=prod -Xmx350m -jar app.jar"]
