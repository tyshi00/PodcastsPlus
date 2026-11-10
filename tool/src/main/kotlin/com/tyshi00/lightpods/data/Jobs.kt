package com.tyshi00.lightpods.data

import com.thelightphone.sdk.LightJob
import com.thelightphone.sdk.LightJobHandler
import com.thelightphone.sdk.LightJobResult

@LightJob("download-episode")
val downloadEpisodeJob: LightJobHandler = { ctx, input ->
    Pods.init(ctx.filesDir)
    val key = input["key"]?.removePrefix("key=")
    when {
        key == null -> LightJobResult.Error()
        Pods.downloads.value[key] == null -> LightJobResult.Success()
        Pods.downloadAsync(key).await() -> LightJobResult.Success()
        else -> LightJobResult.Error()
    }
}

@LightJob("sync-feeds")
val syncFeedsJob: LightJobHandler = { ctx, _ ->
    Pods.init(ctx.filesDir)
    Pods.refreshAll()
    LightJobResult.Success()
}
