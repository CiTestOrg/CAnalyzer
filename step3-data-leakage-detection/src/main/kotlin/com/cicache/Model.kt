package com.cicache

import com.google.gson.annotations.SerializedName

data class CacheRule (
    @SerializedName("repoName"   ) var repoName  : String,
    @SerializedName("path"       ) var path      : String,
    @SerializedName("cache_rule" ) var cacheRule : List<String>
)


data class SensitiveFile (
    @SerializedName("chp"      ) var chp      : String,
    @SerializedName("cp"       ) var cp       : String,
    @SerializedName("repoName" ) var repoName : String,
    @SerializedName("dataLeak" ) var dataLeak : List<DataLeak>
)

data class DataLeak (
    @SerializedName("filePath"    ) var filePath    : List<String>,
    @SerializedName("vars"        ) var vars        : List<String>,
    @SerializedName("type"        ) var type        : String,
)

data class SecretLeakResult(val repoName: String, val filePath : MutableSet<String>,
                            val vars: MutableSet<String>, val types: MutableSet<String>)