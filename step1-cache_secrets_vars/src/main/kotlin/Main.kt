package com.cicache

import com.cicache.analyzer.*
import com.google.gson.Gson
import java.io.File
import java.lang.RuntimeException
import java.nio.file.Paths
import java.util.*
import java.util.regex.Pattern

class CacheSecretsVarsAnalysis {

    companion object {
        private const val pattern = "\\\$([a-zA-Z_]+[a-zA-Z0-9_]*)"
        private val r: Pattern = Pattern.compile(pattern)
        private val gson = Gson()

        private fun writeConfigToFile(repoDir: File, configDir: File, outFile: File) {
            configDir.listFiles().filter { it.isFile && (it.name.endsWith(".yml")||it.name.endsWith(".yaml")) }
                .forEach { file ->
                    val base64Content = Base64.getEncoder().encodeToString(file.readBytes())
                    val filePath = Paths.get(repoDir.absolutePath).relativize(Paths.get(file.absolutePath))
                    val base64File = FileBase64Info(repoName = repoDir.name, path = filePath.toString(), content = base64Content)
                    val resultStr = gson.toJson(base64File, FileBase64Info::class.java)
                    outFile.appendText("$resultStr\n")
                }
        }

        private fun writeSingleConfigToFile(repoDir: File, configFile: File, outFile: File) {
            val base64Content = Base64.getEncoder().encodeToString(configFile.readBytes())
            val filePath = configFile.name
            val base64File = FileBase64Info(repoName = repoDir.name, path = filePath.toString(), content = base64Content)
            val resultStr = gson.toJson(base64File, FileBase64Info::class.java)
            outFile.appendText("$resultStr\n")
        }

        @JvmStatic
        fun main(args: Array<String>) {
            val repoDir = File(args[0])
            val outDir = File(args[1])


            val ciConfigFileOutput = File(outDir, "ci-config-files.txt")
            val ciSecretVarsOutput = File(outDir, "ci-secret-vars.txt")

            if (ciConfigFileOutput.exists()) {
                ciConfigFileOutput.delete()
            }
            if (ciSecretVarsOutput.exists()) {
                ciSecretVarsOutput.delete()
            }

            // parse ci config files
            val ghaConfigDir = File(repoDir, ".github/workflows")
            val circleCIConfigDir = File(repoDir, ".circleci")
            val gitlabConfigFile = File(repoDir, ".gitlab-ci.yml")
            val bitbucketConfigFile = File(repoDir, "bitbucket-pipelines.yml")
            val travisConfigFile = File(repoDir, ".travis.yml")

            if (ghaConfigDir.exists()) {
                // github actions
                writeConfigToFile(repoDir, ghaConfigDir, ciConfigFileOutput)
                // parse secret vars
                GitHubActions.main(arrayOf(ciConfigFileOutput.absolutePath, ciSecretVarsOutput.absolutePath))
            } else if (circleCIConfigDir.exists()) {
                // circle ci
                writeConfigToFile(repoDir, circleCIConfigDir, ciConfigFileOutput)
                CircleCI.main(arrayOf(ciConfigFileOutput.absolutePath, ciSecretVarsOutput.absolutePath))

            } else if (gitlabConfigFile.exists()) {
                // gitlab
                writeSingleConfigToFile(repoDir, gitlabConfigFile, ciConfigFileOutput)
                GitLab.main(arrayOf(ciConfigFileOutput.absolutePath, ciSecretVarsOutput.absolutePath))
            } else if (bitbucketConfigFile.exists()) {
                // bitbucket
                writeSingleConfigToFile(repoDir, bitbucketConfigFile, ciConfigFileOutput)
                Bitbucket.main(arrayOf(ciConfigFileOutput.absolutePath, ciSecretVarsOutput.absolutePath))
            } else if (travisConfigFile.exists()) {
                // travis
                writeSingleConfigToFile(repoDir, bitbucketConfigFile, ciConfigFileOutput)
                TravisCI.main(arrayOf(ciConfigFileOutput.absolutePath, ciSecretVarsOutput.absolutePath))
            } else {
                throw RuntimeException("CI config file not found!")
            }
        }

    }

}