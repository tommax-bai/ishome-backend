plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-gradle-plugin:3.5.16")
    implementation("com.diffplug.spotless:spotless-plugin-gradle:8.9.0")
}
