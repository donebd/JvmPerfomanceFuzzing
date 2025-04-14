FROM openjdk:17-jdk-slim as base

RUN apt-get update && apt-get install -y \
    wget curl unzip \
    && apt-get clean

# HotSpot уже включен в базовый образ

# GraalVM
RUN wget -q https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-17.0.9/graalvm-community-jdk-17.0.9_linux-x64_bin.tar.gz \
    && mkdir -p /opt/graalvm \
    && tar -xzf graalvm-community-jdk-17.0.9_linux-x64_bin.tar.gz -C /opt \
    && mv /opt/graalvm-community-openjdk-17.0.9+9.1/* /opt/graalvm/ \
    && rm -rf /opt/graalvm-community-openjdk-17.0.9+9.1 \
    && rm graalvm-community-jdk-17.0.9_linux-x64_bin.tar.gz \
    && /opt/graalvm/bin/gu install native-image

# OpenJ9 - раскомментируем и обновим до актуальной версии
RUN wget -q https://github.com/ibmruntimes/semeru17-binaries/releases/download/jdk-17.0.14%2B7_openj9-0.49.0/ibm-semeru-open-jdk_x64_linux_17.0.14_7_openj9-0.49.0.tar.gz \
    && mkdir -p /opt/openj9 \
    && tar -xzf ibm-semeru-open-jdk_x64_linux_17.0.14_7_openj9-0.49.0.tar.gz -C /opt \
    && mv /opt/jdk-17.0.14+7/* /opt/openj9/ \
    && rm -rf /opt/jdk-17.0.14+7 \
    && rm ibm-semeru-open-jdk_x64_linux_17.0.14_7_openj9-0.49.0.tar.gz

# Опционально: Добавляем Axiom JVM, если она вам нужна
# RUN wget -q https://url-to-axiom-jvm/axiom-jdk-17.tar.gz \
#     && mkdir -p /opt/axiom \
#     && tar -xzf axiom-jdk-17.tar.gz -C /opt/axiom --strip-components=1 \
#     && rm axiom-jdk-17.tar.gz

# Gradle
RUN wget -q https://services.gradle.org/distributions/gradle-7.6-bin.zip \
    && unzip gradle-7.6-bin.zip -d /opt \
    && ln -s /opt/gradle-7.6/bin/gradle /usr/bin/gradle \
    && rm gradle-7.6-bin.zip

# Настройка переменных окружения
ENV JAVA_HOTSPOT=/usr/local/openjdk-17
ENV JAVA_OPENJ9=/opt/openj9
ENV JAVA_GRAALVM=/opt/graalvm
# ENV JAVA_AXIOM=/opt/axiom

WORKDIR /app
COPY . /app

# Создаём конфигурационный файл для JVM путей
RUN mkdir -p /app/src/main/resources/
RUN echo '{ \
  "HotSpotJvm": "/usr/local/openjdk-17/bin/java", \
  "OpenJ9Jvm": "/opt/openj9/bin/java", \
  "GraalVmJvm": "/opt/graalvm/bin/java" \
}' > /app/src/main/resources/jvm_config.json

# Сборка проекта
RUN chmod +x ./gradlew && ./gradlew clean jar -x test

# Создаём папку для результатов
RUN mkdir -p /app/anomalies


# Обновите имя JAR-файла в соответствии с вашим проектом
ENTRYPOINT ["java", "-jar", "build/libs/JvmPerfomanceFuzzing-1.0.jar"]
