package com.lizongying.mytv0

import android.content.Context
import android.os.Environment
import android.util.Log
import com.lizongying.mytv0.data.SourceType
import com.lizongying.mytv0.data.TV
import java.io.File
import java.util.regex.Pattern

object LocalMediaScanner {
    private const val TAG = "LocalMediaScanner"
    const val FOLDER_NAME = "我的电视本地资源库"

    private val VIDEO_EXTENSIONS = setOf(
        "mp4", "mkv", "avi", "flv", "ts", "mov", "rmvb", "wmv", "m4v", "webm", "iso"
    )

    fun scan(context: Context): List<TV> {
        val rootDirs = mutableListOf<File>()

        // 1. Primary external storage root (/storage/emulated/0/我的电视本地资源库)
        try {
            val primaryStorage = Environment.getExternalStorageDirectory()
            if (primaryStorage != null && primaryStorage.exists()) {
                val primaryDir = File(primaryStorage, FOLDER_NAME)
                if (primaryDir.exists() && primaryDir.isDirectory) {
                    rootDirs.add(primaryDir)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking primary storage", e)
        }

        // 2. Secondary external storage roots (TF Card, USB, /storage/XXXX-XXXX/我的电视本地资源库)
        try {
            val externalDirs = context.getExternalFilesDirs(null)
            for (dir in externalDirs) {
                if (dir != null) {
                    var parent = dir.parentFile
                    while (parent != null && parent.name != "Android" && parent.parentFile != null && parent.parentFile?.name != "storage") {
                        parent = parent.parentFile
                    }
                    if (parent != null && parent.name == "Android") {
                        val storageRoot = parent.parentFile
                        if (storageRoot != null && storageRoot.exists()) {
                            val candidate = File(storageRoot, FOLDER_NAME)
                            if (candidate.exists() && candidate.isDirectory && !rootDirs.contains(candidate)) {
                                rootDirs.add(candidate)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking secondary external storage", e)
        }

        val result = mutableListOf<TV>()

        for (rootDir in rootDirs) {
            Log.i(TAG, "Scanning local library at: ${rootDir.absolutePath}")
            // Level 1: Category folders (e.g. "经典影视", "少儿动画")
            val groupDirs = rootDir.listFiles { file -> file.isDirectory } ?: continue
            groupDirs.sortWith { a, b -> naturalCompare(a.name, b.name) }

            for (groupDir in groupDirs) {
                val groupName = groupDir.name
                // Level 2: Channel folders (e.g. "西游记", "三国演义")
                val channelDirs = groupDir.listFiles { file -> file.isDirectory } ?: continue
                channelDirs.sortWith { a, b -> naturalCompare(a.name, b.name) }

                for (channelDir in channelDirs) {
                    val channelTitle = channelDir.name
                    val videoFiles = channelDir.listFiles { file ->
                        file.isFile && VIDEO_EXTENSIONS.contains(file.extension.lowercase())
                    } ?: continue

                    if (videoFiles.isEmpty()) continue

                    // Natural order sort for video files
                    videoFiles.sortWith { a, b -> naturalCompare(a.name, b.name) }

                    val uris = videoFiles.map { "file://${it.absolutePath}" }

                    val tv = TV(
                        id = -1,
                        name = channelTitle,
                        title = channelTitle,
                        description = "共 ${uris.size} 集",
                        logo = "",
                        image = null,
                        uris = uris,
                        videoIndex = 0,
                        headers = null,
                        group = groupName,
                        sourceType = SourceType.PROGRESSIVE,
                        number = -1,
                        child = emptyList()
                    )
                    result.add(tv)
                    Log.i(TAG, "Found local channel: [$groupName] $channelTitle with ${uris.size} episodes")
                }
            }
        }

        return result
    }

    private val NUM_PATTERN = Pattern.compile("(\\d+)|(\\D+)")

    fun naturalCompare(a: String, b: String): Int {
        val matcherA = NUM_PATTERN.matcher(a)
        val matcherB = NUM_PATTERN.matcher(b)

        while (matcherA.find() && matcherB.find()) {
            val tokenA = matcherA.group()
            val tokenB = matcherB.group()

            val isDigitA = Character.isDigit(tokenA[0])
            val isDigitB = Character.isDigit(tokenB[0])

            if (isDigitA && isDigitB) {
                val numA = tokenA.toLongOrNull() ?: Long.MAX_VALUE
                val numB = tokenB.toLongOrNull() ?: Long.MAX_VALUE
                val cmp = numA.compareTo(numB)
                if (cmp != 0) return cmp
            } else {
                val cmp = tokenA.compareTo(tokenB, ignoreCase = true)
                if (cmp != 0) return cmp
            }
        }
        return a.length.compareTo(b.length)
    }
}
