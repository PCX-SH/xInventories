package sh.pcx.xinventories.loader

/**
 * Defines all runtime dependencies required by xInventories.
 *
 * IMPORTANT: Keep these versions in sync with build.gradle runtimeDeps!
 * The Java loader (XInventoriesLoader.java) uses RuntimeDependencies.java
 * which is generated from build.gradle. This Kotlin enum is used by tests.
 *
 * @property groupId Maven group ID
 * @property artifactId Maven artifact ID
 * @property version Artifact version
 * @property sha256 Base64-encoded SHA-256 checksum for verification
 */
enum class Dependency(
    val groupId: String,
    val artifactId: String,
    val version: String,
    val sha256: String
) {
    // Keep versions in sync with build.gradle ext.runtimeDeps!

    KOTLIN_STDLIB(
        "org.jetbrains.kotlin",
        "kotlin-stdlib",
        "2.3.0",
        "iHWHyRcTJQrVL+FK2RZtBCwzg1BJiQ6UN/NV/8WhlbE="
    ),

    KOTLINX_COROUTINES_CORE(
        "org.jetbrains.kotlinx",
        "kotlinx-coroutines-core-jvm",
        "1.10.2",
        "XKF1s43zMf1kFVs1zYyuElH6nuNpcJs21C4KKIzM4/0="
    ),

    KOTLINX_SERIALIZATION_CORE(
        "org.jetbrains.kotlinx",
        "kotlinx-serialization-core-jvm",
        "1.10.0",
        "FNbyfOKPYevEpRbVYvkRt7wBz75Tl/uITEXqDbBExjU="
    ),

    KOTLINX_SERIALIZATION_JSON(
        "org.jetbrains.kotlinx",
        "kotlinx-serialization-json-jvm",
        "1.10.0",
        "rx4+Ho7jF2Ro4exynfhTsgZgcd6UqExBKtn6E1yzfzo="
    ),

    CAFFEINE(
        "com.github.ben-manes.caffeine",
        "caffeine",
        "3.2.3",
        "ynDJCl0c4VEYgM6ck9StIhCPYREdPa+R61J2K1cb0Xk="
    ),

    HIKARICP(
        "com.zaxxer",
        "HikariCP",
        "7.0.2",
        "8eYS+ic0W+MQeoVDHoqK6yBcFTZKsvLUEeQKnXuwgJU="
    ),

    SLF4J_API(
        "org.slf4j",
        "slf4j-api",
        "2.0.16",
        "oSV43eG6AL2bgW04iguHmSjQC6s8g8JA9wE79BlsV5o="
    ),

    JEDIS(
        "redis.clients",
        "jedis",
        "7.2.1",
        "2gXptRjTbyZfUms55SF1bnYz+cRHKRaaMjXNvxxyF5A="
    ),

    COMMONS_POOL2(
        "org.apache.commons",
        "commons-pool2",
        "2.12.0",
        "bTvRjfhBDz4xsDGspYLMEJNCNYpionWevQxM3zDQb4s="
    ),

    GSON(
        "com.google.code.gson",
        "gson",
        "2.12.1",
        "6+4T1ft0d81/HMAQ4MNW34yoBwlxUkjal/eeNcy0++w="
    );

    val mavenPath: String
        get() = "${groupId.replace('.', '/')}/$artifactId/$version/$artifactId-$version.jar"

    val fileName: String
        get() = "$artifactId-$version.jar"

    val mavenCentralUrl: String
        get() = "https://repo1.maven.org/maven2/$mavenPath"

    companion object {
        val CORE_DEPENDENCIES = listOf(
            KOTLIN_STDLIB,
            KOTLINX_COROUTINES_CORE,
            KOTLINX_SERIALIZATION_CORE,
            KOTLINX_SERIALIZATION_JSON,
            CAFFEINE,
            HIKARICP,
            SLF4J_API
        )

        val REDIS_DEPENDENCIES = listOf(
            JEDIS,
            COMMONS_POOL2,
            GSON
        )

        val ALL_DEPENDENCIES = CORE_DEPENDENCIES + REDIS_DEPENDENCIES
    }
}
