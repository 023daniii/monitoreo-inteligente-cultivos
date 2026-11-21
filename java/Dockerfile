FROM eclipse-temurin:21-jdk AS compilador
WORKDIR /fuente
COPY src/main/java src/main/java
RUN javac --release 17 -d /clases -sourcepath src/main/java src/main/java/ec/edu/epn/invernaderos/servicio/MedidorHorario.java

FROM eclipse-temurin:21-jre
WORKDIR /aplicacion
ADD --checksum=sha256:cab1cd67cfa25c25de4348e532298028288a877ba01c77d1619fe45416193387 https://repo.maven.apache.org/maven2/org/postgresql/postgresql/42.7.10/postgresql-42.7.10.jar jdbc.jar
COPY --from=compilador /clases clases
ENTRYPOINT ["java", "-cp", "clases:jdbc.jar", "ec.edu.epn.invernaderos.servicio.MedidorHorario"]
