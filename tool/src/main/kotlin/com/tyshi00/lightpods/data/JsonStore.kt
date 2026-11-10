package com.tyshi00.lightpods.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File

val PodJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

class JsonStore<T>(
    private val file: File,
    private val serializer: KSerializer<T>,
    default: T,
    private val scope: CoroutineScope,
) {
    private val writeLock = Mutex()
    private val _state = MutableStateFlow(read() ?: default)
    val state: StateFlow<T> = _state.asStateFlow()
    val value: T get() = _state.value

    private fun read(): T? = runCatching {
        if (file.exists()) PodJson.decodeFromString(serializer, file.readText()) else null
    }.getOrNull()

    fun update(transform: (T) -> T) {
        synchronized(this) {
            _state.value = transform(_state.value)
        }
        scope.launch {
            writeLock.withLock {
                val snapshot = _state.value
                runCatching {
                    file.parentFile?.mkdirs()
                    val tmp = File(file.parentFile, file.name + ".tmp")
                    tmp.writeText(PodJson.encodeToString(serializer, snapshot))
                    tmp.renameTo(file)
                }
            }
        }
    }
}
