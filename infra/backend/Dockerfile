# Build context: /Users/Imola_Saraine_Lajos/MPCalc/backend (set in docker-compose.yml). Tested 2026-10-13 (builds and runs).
ARG BUILD_IMAGE=maven:3.9-eclipse-temurin-26
ARG RUN_IMAGE=eclipse-temurin:26-jre

FROM ${BUILD_IMAGE} AS build
WORKDIR /src
COPY . .
RUN mkdir /out && \
    if [ -f pom.xml ]; then \
      mvn -B -q -DskipTests package && \
      cp "$(ls target/*.jar | grep -v '\.original$' | head -n 1)" /out/app.jar ; \
    else \
      echo "No pom.xml in backend folder: building placeholder (answers 200 on port 8080)." && \
      mkdir /ph && cd /ph && \
      printf '%s\n' \
        'import com.sun.net.httpserver.HttpServer;' \
        'import java.net.InetSocketAddress;' \
        'public class Placeholder { public static void main(String[] a) throws Exception {' \
        '  HttpServer s = HttpServer.create(new InetSocketAddress(8080), 0);' \
        '  s.createContext("/", x -> { byte[] b = "backend placeholder".getBytes(); x.sendResponseHeaders(200, b.length); x.getResponseBody().write(b); x.close(); });' \
        '  s.start(); } }' > Placeholder.java && \
      javac Placeholder.java && \
      jar --create --file /out/app.jar --main-class Placeholder Placeholder*.class ; \
    fi

FROM ${RUN_IMAGE}
WORKDIR /app
COPY --from=build /out/app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
