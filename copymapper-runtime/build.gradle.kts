plugins {
    `java-library`
}

dependencies {
    api(project(":copymapper-core"))
    api(project(":copymapper-copybook"))
    api(libs.jackson3.databind)
}
