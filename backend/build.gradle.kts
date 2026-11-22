plugins { id("org.springframework.boot") version "4.0.8" apply false }
subprojects {
    apply(plugin = "java-library")
    group = "cl.induztek"
    version = "0.1.0"
    configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    dependencies {
        "implementation"(platform("org.springframework.boot:spring-boot-dependencies:4.0.8"))
        "implementation"("org.springframework.boot:spring-boot-starter-webmvc")
        "implementation"("org.springframework.boot:spring-boot-starter-security")
        "implementation"("org.springframework.boot:spring-boot-starter-jdbc")
        "implementation"("com.google.code.gson:gson:2.13.2")
        if (name == "common") {
            "implementation"("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3")
        }
        "runtimeOnly"("com.h2database:h2")
        if (name != "report-service") {
            "runtimeOnly"("com.mysql:mysql-connector-j")
        }
        "testImplementation"("org.springframework.boot:spring-boot-starter-test")
        "testImplementation"("org.springframework.security:spring-security-test")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }
    tasks.withType<Test> { useJUnitPlatform() }
    tasks.withType<JavaCompile> { options.encoding = "UTF-8" }
}
