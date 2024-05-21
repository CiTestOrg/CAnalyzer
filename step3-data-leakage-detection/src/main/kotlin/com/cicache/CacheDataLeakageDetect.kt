package com.cicache

import com.cicache.cmdtools.CmdTools
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import me.tongfei.progressbar.ProgressBar
import java.io.File
import java.io.FileWriter
import java.nio.file.Paths


class CacheDataLeakageDetect {
    companion object {
        private val gson = Gson()

        private fun refineSecretPath(path: String, repoName: String): String {
            val rName = repoName.split('/')[1]
            var fixedPath = path.replace("\"", "")
            if (fixedPath.startsWith("~/")) {
                fixedPath = fixedPath.replace("~/", "/home/runner/")
            }
            fixedPath = fixedPath.replace("PA__TH", "PATH")
            fixedPath = fixedPath.replace("'", "")
            fixedPath = fixedPath.replace("&", "")
            fixedPath = fixedPath.replace(")", "")
            fixedPath = fixedPath.replace(";", "")
            fixedPath = fixedPath.replace("\$HOME/", "/home/runner/")
            fixedPath = fixedPath.replace("\${HOME}", "/home/runner/")
            fixedPath = fixedPath.replace("\${ANDROID_HOME}", "/home/runner/")
            fixedPath = fixedPath.replace("\$Home/", "/home/runner/")
            fixedPath = fixedPath.replace("\${HOME}/", "/home/runner/")
            fixedPath = fixedPath.replace("\${SSH_DIR}/", "/home/runner/.ssh/")
            fixedPath = fixedPath.replace("\$GITHUB_ENV", "/home/runner/work/_temp/_runner_file_commands/a_gh_env_file")
            fixedPath = fixedPath.replace("\$GITHUB_WORKSPACE/", "/home/runner/work/$rName/$rName/")
            fixedPath = fixedPath.replace("\$TRAVIS_BUILD_DIR/", "/home/runner/work/$rName/$rName/")
            fixedPath = fixedPath.replace("\$BITBUCKET_CLONE_DIR/", "/home/runner/work/$rName/$rName/")
            fixedPath = fixedPath.replace("\${GITHUB_WORKSPACE}/", "/home/runner/work/$rName/$rName/")
            fixedPath = fixedPath.replace("\${{github.workspace}}", "/home/runner/work/$rName/$rName")

            return fixedPath
        }

        private fun getAllCacheRuleFromRepo(repoName: String, ruleMap: Map<String, ArrayList<CacheRule>>): Set<String> {
            val result = HashSet<String>()
            ruleMap[repoName]?.forEach { rules ->
                result.addAll(rules.cacheRule)
            }
            return result
        }

        private fun getAllSensitiveFileFromRepo(repoName: String, ruleMap: Map<String, ArrayList<DataLeak>>): Map<String, HashSet<String>> {
            val result = HashMap<String, HashSet<String>>()
            ruleMap[repoName]?.forEach { dataLeak ->
                val leakFilePaths = dataLeak.filePath
                leakFilePaths.forEach { leakFilePath ->
                    if (leakFilePath != "\$GITHUB_ENV") {
                        if (result.containsKey(leakFilePath)) {
                            result[leakFilePath]!!.addAll(dataLeak.vars)
                        } else {
                            result[leakFilePath] = dataLeak.vars.toHashSet()
                        }
                    }
                }
            }
            return result
        }

        @JvmStatic
        fun main(args: Array<String>) {
            val cacheRuleFile = File(args[0]) // example: "step3-data-leakage-detection/test/cache_rule_output.txt"
            val sensitiveFileIn = File(args[1]) // example: "step3-data-leakage-detection/test/cache_leak_output.txt"
            val outDir = File(args[2]) // output dir

            val resultFile = File(outDir, "secret_leak_result.txt")
            if (resultFile.exists()) {
                resultFile.delete()
            }

            val noResultFile = File(outDir, "secret_leak_not_matched.txt")
            if (noResultFile.exists()) {
                noResultFile.delete()
            }

            var successCount = 0
            var failCount = 0
            var foundCount = 0
            val repoCacheRuleMap = HashMap<String, ArrayList<CacheRule>>()
            val repoSensitiveFileMap = HashMap<String, ArrayList<DataLeak>>()
            cacheRuleFile.forEachLine { line ->
                val rule = gson.fromJson(line, CacheRule::class.java)
                val repoName = rule.repoName
                if (repoCacheRuleMap.containsKey(repoName)) {
                    repoCacheRuleMap[repoName]!!.add(rule)
                } else {
                    val list = ArrayList<CacheRule>()
                    list.add(rule)
                    repoCacheRuleMap[repoName] = list
                }
            }

            sensitiveFileIn.forEachLine { line ->
                val item = gson.fromJson(line, SensitiveFile::class.java)
                val repoName = item.repoName
                if (repoSensitiveFileMap.containsKey(repoName)) {
                    repoSensitiveFileMap[repoName]!!.addAll(item.dataLeak)
                } else {
                    val list = ArrayList<DataLeak>()
                    list.addAll(item.dataLeak)
                    repoSensitiveFileMap[repoName] = list
                }
            }


            val hasSensitiveFileAndCacheRulesRepos = repoCacheRuleMap.keys intersect repoSensitiveFileMap.keys

            val todoCount = hasSensitiveFileAndCacheRulesRepos.size
            println(todoCount)
            val progressBar = ProgressBar("CacheDataLeakage", todoCount.toLong())
            val fileWriter = FileWriter(resultFile)
            val noFileWriter = FileWriter(noResultFile)

            hasSensitiveFileAndCacheRulesRepos.forEach { repoName ->
                progressBar.step()
                try {
                    // step 1 extract secret files and cache list
                    val cacheFiles = HashSet<String>()
                    val rName = repoName.split('/')[1]
                    val cacheRules = getAllCacheRuleFromRepo(repoName, repoCacheRuleMap)
                    cacheRules.forEach { rawCachePath ->
                        val fixedPath = refineSecretPath(rawCachePath.filter { !it.isWhitespace() }, repoName)
                        cacheFiles.add(fixedPath)
                    }
                    val rawSensitiveFileVarMap = getAllSensitiveFileFromRepo(repoName, repoSensitiveFileMap)
                    val fixedSensitiveFileVarMap = HashMap<String, HashSet<String>>()
                    val rawSensitiveFilePaths = rawSensitiveFileVarMap.keys

                    if (cacheFiles.isNotEmpty() && rawSensitiveFilePaths.isNotEmpty()) {
                        val workingDir = File("/home/runner/work/${rName}/${rName}")
                        workingDir.mkdirs()
                        val fixedSecretFilePaths = HashSet<String>()
                        val fixedPathForLog = HashSet<String>()
                        rawSensitiveFilePaths.forEach { rawSensitiveFilePath ->
                            val fixedPath = refineSecretPath(rawSensitiveFilePath, repoName)
                            fixedPathForLog.add(fixedPath)
                            val p = Paths.get(fixedPath)
                            if (p.isAbsolute) {
                                fixedSecretFilePaths.add(fixedPath)
                                fixedSensitiveFileVarMap[fixedPath] = rawSensitiveFileVarMap[rawSensitiveFilePath]!!
                            } else {
                                val fixedFile = File(workingDir, fixedPath)
                                fixedSecretFilePaths.add(fixedFile.absolutePath)
                                fixedSensitiveFileVarMap[fixedFile.absolutePath] = rawSensitiveFileVarMap[rawSensitiveFilePath]!!
                            }
                        }
                        // step 2 create sensitive files
                        fixedSecretFilePaths.forEach { secFilePath ->
                            val secFile = File(secFilePath)
                            if (secFile.exists() && secFile.isDirectory) {
                                // if file exists but is a directory, delete it
                                secFile.delete()
                            }
                            secFile.parentFile.mkdirs()
                            secFile.writeText("a_secret_string")
                        }

                        // step 3 calculate all files in the cache with a node.js script
                        // patterns.txt
                        val patternsFile = File(workingDir, "patterns.txt")
                        val jsResultFile = File(workingDir, "result.txt")
                        if (jsResultFile.exists()) {
                            jsResultFile.delete()
                        }
                        if (patternsFile.exists()) {
                            patternsFile.delete()
                        }
                        cacheFiles.forEach {
                            patternsFile.appendText("$it\n")
                        }

                        val startCmdList = listOf("node", "/root/pathmatch/matchpath.js")
                        val result = runBlocking {
                            CmdTools.execCommand(cmd = startCmdList, omitRedirect = false, workingDir = workingDir)
                        }

                        // step 4 detecting data leakage
                        val matchedFileList = HashSet<String>()
                        jsResultFile.forEachLine {
                            matchedFileList.add(it)
                        }
                        val leakFiles = ArrayList<File>()
                        fixedSecretFilePaths.forEach { secFilePath ->
                            val secFile = File(secFilePath)
                            if (matchedFileList.contains(secFile.absolutePath)) {
                                leakFiles.add(secFile)
                            }
                        }
                        if (leakFiles.isNotEmpty()) {
                            val ghaSecretLeakResult = SecretLeakResult(repoName = repoName,
                                    filePath = leakFiles.map { it.absolutePath }.toMutableSet(),
                            vars = fixedSensitiveFileVarMap.keys.toMutableSet(),
                            types = HashSet<String>())
                            ghaSecretLeakResult.types.addAll(repoSensitiveFileMap[repoName]!!.toSet().map { it.type })

                            fileWriter.appendLine(">>>>>> $repoName")
                            fileWriter.appendLine("Sensitive Files: ")
                            leakFiles.forEach { leakFile ->
                                fileWriter.appendLine("\t\t${leakFile.absolutePath}")
                                if (fixedSensitiveFileVarMap.containsKey(leakFile.absolutePath)) {
                                    val psSb = StringBuilder()
                                    fixedSensitiveFileVarMap[leakFile.absolutePath]!!.forEach {
                                        psSb.append("$it ")
                                    }
                                    fileWriter.appendLine("\t\t\t\tSecret Vars: $psSb")
                                }
                            }

                            val psSb = StringBuilder()
                            cacheRules.forEach {
                                psSb.append("\t\t$it\n")
                            }
                            fileWriter.appendLine("cachePaths :\n$psSb")
                            fileWriter.flush()
                        } else {
                            noFileWriter.appendLine(repoName)
                            noFileWriter.appendLine("\tFixedPath :")
                            fixedPathForLog.forEach {
                                noFileWriter.appendLine("\t\t$it")
                            }
                            noFileWriter.appendLine("\tCreatePath:")
                            fixedSecretFilePaths.forEach {
                                noFileWriter.appendLine("\t\t$it")
                            }
                            noFileWriter.appendLine("\tFixedCache:")
                            cacheFiles.forEach {
                                noFileWriter.appendLine("\t\t$it")
                            }
                            noFileWriter.appendLine("\tCacheRules :")
                            cacheRules.forEach {
                                noFileWriter.appendLine("\t\t$it")
                            }
                            noFileWriter.flush()
                        }

                        // step 5 clearing created files
                        fixedSecretFilePaths.forEach { secFilePath ->
                            File(secFilePath).delete()
                        }
                        workingDir.deleteRecursively()
                        workingDir.parentFile.deleteRecursively()

                    }

                    successCount++
                } catch (e: Exception) {
                    failCount++
                }
            }
            fileWriter.close()
            noFileWriter.close()
            println("$foundCount\t$successCount\t\t$failCount")
        }

    }

}

