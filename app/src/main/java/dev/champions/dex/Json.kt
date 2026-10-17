package dev.champions.dex

import org.json.JSONArray
import org.json.JSONObject

/** Readers for the bundled JSON assets and stored preferences. */
internal fun JSONArray.ints() = (0 until length()).map(::getInt)

internal fun JSONArray.strings() = (0 until length()).map(::getString)

internal fun <T> JSONArray.objects(read: (JSONObject) -> T) = (0 until length()).map { read(getJSONObject(it)) }
