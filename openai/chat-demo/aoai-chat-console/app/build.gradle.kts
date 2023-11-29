plugins {
    application
}

repositories {
    mavenCentral()
}
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(19))
    }
}
application {
    // Define the main class for the application.
    mainClass.set("aoai.demos.chat.Main")
}
tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}

dependencies {
    implementation(platform("com.azure:azure-sdk-bom:1.2.18"))
    implementation("com.azure:azure-ai-openai:1.0.0-beta.5")
    implementation("com.azure:azure-search-documents:11.6.0") {
        exclude("com.azure","azure-core-serializer-json-jackson")
    }
    implementation("org.slf4j:slf4j-simple:2.0.7")
    implementation("io.github.cdimascio:dotenv-java:3.0.0")
    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}
