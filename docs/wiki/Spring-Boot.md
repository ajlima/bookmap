# Spring Boot

`bookmap-spring-boot` supports Spring Boot 4.0.x. The auto-configuration creates a `BookmapClient` bean
when a `Transport` bean exists.

```kotlin
dependencies {
    implementation("pt.dazz.bookmap:bookmap-spring-boot:<version>")
    annotationProcessor("pt.dazz.bookmap:bookmap-processor:<version>")
}
```

## Properties

| Property | Default | Meaning |
|---|---|---|
| `bookmap.charset` | `IBM037` | The charset of the client. |
| `bookmap.max-calls` | `1000` | The maximum number of calls for one execution. |

## Transport auto-configuration

A transport auto-configuration must run before the BookMap auto-configuration.
Declare it in this way:

```java
@AutoConfiguration(before = BookmapAutoConfiguration.class)
```

Then the `Transport` bean exists when the client condition runs. The core module has no Spring dependency.
