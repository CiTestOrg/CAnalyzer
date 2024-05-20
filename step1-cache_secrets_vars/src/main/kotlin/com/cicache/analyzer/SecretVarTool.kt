package com.cicache.analyzer

import java.io.File
import java.util.regex.Matcher
import java.util.regex.Pattern

enum class CP {
    GithubActions, GitlabCI, CircleCI, TravisCI, BitbucketPipeline, Jenkinis, TeamCity
}

data class FileBase64Info(var chp: String? = "github", val repoName: String, var star: Int = 0, var fork: Boolean = false, val path: String, val content: String) {
    val cp: CP?
        get() = if (path.lowercase().contains(".github")) {
            CP.GithubActions
        } else if (path.lowercase().contains(".gitlab-ci.yml")) {
            CP.GitlabCI
        } else if (path.lowercase().contains("bitbucket-pipelines.yml")) {
            CP.BitbucketPipeline
        } else if (path.lowercase().contains(".circleci")) {
            CP.CircleCI
        } else if (path.lowercase().contains("jenkinsfile")) {
            CP.Jenkinis
        } else if (path.lowercase().contains(".travis.yml")) {
            CP.TravisCI
        } else {
            null
        }
    val tag: String
        get() = "${chp}__$repoName"
}




class SecretVarTool {

    companion object {
        fun extractSecretsInContent(content: String): Set<String> {
            val patternWithBrace = "\\\$\\{([a-zA-Z_]+[a-zA-Z0-9_]*)\\}"
            val pattern = "\\\$([a-zA-Z_]+[a-zA-Z0-9_]*)"
            val r: Pattern = Pattern.compile(pattern)
            val r2: Pattern = Pattern.compile(patternWithBrace)
            val lines = content.split("[\r\n]+".toRegex()).toTypedArray()
            val set = HashSet<String>()
            lines.forEach { line ->
                val noSpaceLine = line.filter { !it.isWhitespace() }
                val m: Matcher = r.matcher(noSpaceLine)
                if (m.find()) {
                    if (!m.group(1).contains(" secrets.GITHUB_TOKEN")) {
                        set.add(m.group(1))
                    }
                }
                val m2: Matcher = r2.matcher(noSpaceLine)
                if (m2.find()) {
                    if (!m2.group(1).contains(" secrets.GITHUB_TOKEN")) {
                        set.add(m2.group(1))
                    }
                }
            }
            return set
        }

        fun getSecretVars (varsReferInContent: Set<String>, localVars: Set<String>, globalVars: Set<String> = emptySet()): Set<String> {
            val file = File("raw/gitlab_predefined_vars.txt")
            val predefineVars = HashSet<String>()
            file.forEachLine {
                predefineVars.add(it)
            }
            return varsReferInContent - localVars - globalVars - predefineVars
        }

    }

}

