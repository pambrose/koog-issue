plugins {
  kotlin("jvm") version "2.3.0"
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.versions)
}

group = "com.github.pambrose"
version = "1.0-SNAPSHOT"

repositories {
  google()
  mavenCentral()
}

dependencies {
  implementation(libs.koog.agents)
  implementation(libs.oshai.kotlin.logging)

  testImplementation(kotlin("test"))
}

tasks.test {
  useJUnitPlatform()
}

kotlin {
  jvmToolchain(17)
}