 # ===== 阶段 1：用 Maven 把后端打包成 jar =====
  FROM maven:3.9-eclipse-temurin-21 AS build
  WORKDIR /app
  COPY pom.xml .
  RUN mvn dependency:go-offline -B
  COPY src ./src
  RUN mvn package -DskipTests -B

  # ===== 阶段 2：运行时（只装 JRE，镜像更小）=====
  FROM eclipse-temurin:21-jre
  WORKDIR /app
  COPY --from=build /app/target/*.jar app.jar
  EXPOSE 8081
  ENTRYPOINT ["java", "-jar", "app.jar"]