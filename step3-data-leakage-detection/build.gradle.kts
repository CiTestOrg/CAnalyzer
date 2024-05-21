plugins {
    application
    kotlin("jvm") version "1.9.0"
    kotlin("plugin.serialization") version "1.4.20"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.slf4j:slf4j-api:1.7.36")
    implementation("net.lingala.zip4j:zip4j:2.11.2")
    implementation("com.squareup.okhttp3:okhttp:4.4.0")
    implementation("com.squareup.okhttp3:okhttp-urlconnection:4.4.0")
    implementation ("com.google.code.gson:gson:2.8.6")
    implementation("com.opencsv:opencsv:5.7.1")
    implementation("org.isomorphism:token-bucket:1.6")
    implementation("org.jetbrains.kotlinx:kotlinx-cli:0.3")
    implementation("me.tongfei:progressbar:0.9.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.9")
    implementation("com.alibaba:easyexcel:3.2.1")
    implementation("com.opencsv:opencsv:5.7.1")
    implementation("org.apache.commons:commons-text:1.10.0")
    implementation("com.charleskorn.kaml:kaml:0.55.0")
    runtimeOnly("org.apache.logging.log4j:log4j-api:2.17.1")
    runtimeOnly("org.apache.logging.log4j:log4j-core:2.17.1")
    runtimeOnly("org.apache.logging.log4j:log4j-slf4j-impl:2.17.1")
    api("com.fasterxml.jackson.module:jackson-module-kotlin:2.14.1")
    api("com.fasterxml.jackson.core:jackson-databind:2.14.1")
    api("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.14.1")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.14.1")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.14.1")
    implementation("org.litote.kmongo:kmongo:4.8.0")
}

application {
    mainClass.set("com.cicache.CacheDataLeakageDetect")
}
