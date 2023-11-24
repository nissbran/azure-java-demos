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
    implementation("org.slf4j:slf4j-simple:2.0.7")
    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}
