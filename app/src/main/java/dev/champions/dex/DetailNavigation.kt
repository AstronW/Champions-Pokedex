package dev.champions.dex

/** Navigation 3 back stack keys: the catalogue pages always sit at the bottom, details stack above them. */
internal sealed interface DexKey

internal data object HomeKey : DexKey

internal data class DetailKey(
    val route: String,
) : DexKey

/** Content key of the bottom entry; detail routes are prefixed ("pokemon:", "settings", …) and never equal it. */
internal const val HOME_CONTENT_KEY = "home"

/** Revisit an existing entry without duplicating its state or the path above it. */
internal fun openDetailEntry(
    stack: List<String>,
    route: String,
): List<String> {
    val existing = stack.lastIndexOf(route)
    return if (existing >= 0) stack.take(existing + 1) else (stack + route).takeLast(64)
}
