FROM openjdk:17-jdk
WORKDIR /app
COPY notification-service-0.0.1-SNAPSHOT.jar .

EXPOSE 9013

# Spring profiles
ENV SPRING_PROFILES_ACTIVE=jdbc,production

# Disable Eureka registration/fetch
ENV EUREKA_CLIENT_REGISTER_WITH_EUREKA=false
ENV EUREKA_CLIENT_FETCH_REGISTRY=false

# Datasource
ENV SPRING_DATASOURCE_URL=jdbc:mariadb://mariadb-db:3306/middleware
ENV SPRING_DATASOURCE_USERNAME=root
ENV SPRING_DATASOURCE_PASSWORD=BR-10h8352

# Disable all metrics to avoid cgroup crash
ENV MANAGEMENT_METRICS_ENABLE_ALL=false
ENV MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,info

# JVM options for low-memory
CMD ["java","-Xms128m","-Xmx256m","-Dspring.main.allow-bean-definition-overriding=true","-jar","notification-service-0.0.1-SNAPSHOT.jar"]
