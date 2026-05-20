package io.github.ganyuke.bedFallback.compaction

enum class FallbackPolicy {
    LAST_N,
    LAST_N_VALID,
    LAST_N_IN_DIMENSION,
    LAST_N_VALID_IN_DIMENSION,
}