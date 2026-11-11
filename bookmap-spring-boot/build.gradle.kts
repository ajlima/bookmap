plugins { `java-library` }

dependencies {
    api(project(":bookmap-core"))
    implementation(platform(libs.spring.boot.bom))
    api("org.springframework.boot:spring-boot-autoconfigure")

    testImplementation("org.springframework.boot:spring-boot-test")
}
