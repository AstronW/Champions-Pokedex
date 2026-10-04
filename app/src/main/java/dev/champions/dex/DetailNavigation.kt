package dev.champions.dex

/** Revisit an existing entry without duplicating its state or the path above it. */
internal fun openDetailEntry(stack: List<String>, entry: String): List<String> {
    val route = entry.substringAfter('#')
    val existing = stack.indexOfLast { it.substringAfter('#') == route }
    return if (existing >= 0) stack.take(existing + 1) else (stack + entry).takeLast(64)
}

/** Entry IDs increase on forward navigation; returning to an earlier ID moves back. */
internal fun isReturningToDetail(initialEntry: String?, targetEntry: String?): Boolean =
    (targetEntry?.substringBefore('#')?.toInt() ?: -1) <
        (initialEntry?.substringBefore('#')?.toInt() ?: -1)
