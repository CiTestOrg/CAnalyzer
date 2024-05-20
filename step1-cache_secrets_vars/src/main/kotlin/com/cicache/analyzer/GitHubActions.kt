package com.cicache.analyzer

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.contains
import com.google.gson.Gson
import me.tongfei.progressbar.ProgressBar
import java.io.*
import java.util.*
import java.util.regex.Matcher
import java.util.regex.Pattern

data class GhaJobsAndSecrets(val repoName: String, val path: String, val envSecretsAndVars: Set<String>, val jobs: List<GhaJobAndSecret>)
data class GhaJobAndSecret(val job: String, val secrets: Set<String>)

/**
 * After variable analysis, CAnalyzer extracted all the variables related to secrets.
 *
 * GitHub
 */
class GitHubActions {

    companion object {

        @Throws(IOException::class)
        fun countLines(aFile: File?): Int {
            var reader: LineNumberReader? = null
            return try {
                reader = LineNumberReader(FileReader(aFile))
                while (reader.readLine() != null);
                reader.lineNumber
            } catch (ex: java.lang.Exception) {
                -1
            } finally {
                reader?.close()
            }
        }

        private fun extractSecretsInContent(content: String): Set<String> {
            val pattern = "\\$\\{\\{(secrets\\.\\w*)\\}\\}"
            val r: Pattern = Pattern.compile(pattern)
            val lines = content.split("[\r\n]+".toRegex()).toTypedArray()
            val set = HashSet<String>()
            lines.forEach { line ->
                val noSpaceLine = line.filter { !it.isWhitespace() }
                if (noSpaceLine.contains("secrets")) {
                    val m: Matcher = r.matcher(noSpaceLine)
                    if (m.find()) {
                        if (!m.group(1).contains(" secrets.GITHUB_TOKEN")) {
                            set.add(m.group(1))
                        }
                    }
                }
            }
            return set
        }

        fun findAllSecretsVarsNoStepInfo(vars: HashMap<String, String>, secrets: Set<String>): Pair<Set<String>, Set<String>> {
            val originSecrets = HashSet<String>()
            val secVars = HashSet<String>()

            val secretsFiltered = secrets.filter { !it.equals("secrets.GITHUB_TOKEN", ignoreCase = true) }
                    .toList().sortedBy { it.length }.reversed() // secret is sorted by length to make it easier to deal with cases like SECRET_A SECRET_AB
            if (secretsFiltered.isEmpty()) {
                return Pair(originSecrets, secVars)
            }
            originSecrets.addAll(secretsFiltered)

            val secVarAndLinks = HashMap<String, List<String>>()
            vars.forEach lft@{ (key, value) ->
                val usedKeys = HashSet<String>()
                val link = ArrayList<String>()
                var k = key
                while (vars[k] != null) {
                    if (usedKeys.contains(k)) {
                        return@lft
                    }
                    val v = vars[k]!!
                    if (!link.contains(k)) {
                        link.add(k)
                    }
                    for (secret in secretsFiltered) {
                        if (v.contains(secret)) {
                            link.add(secret)
                            secVarAndLinks[key] = link
                            return@lft
                        }
                    }
                    usedKeys.add(k)
                    k = v
                }
            }
            secVars.addAll(secVarAndLinks.keys)
            secVarAndLinks.values.forEach {
                secVars.addAll(it)
            }


            return Pair(originSecrets, secVars)

        }


        private fun process(prefix: String, currentNode: JsonNode, stepCollector: ArrayList<JsonNode>,
                            varsCollector: HashMap<String, String>, secretsCollector: HashSet<String>, thirdPartyActions: HashSet<String>) {
            if (currentNode.isArray) {
                val arrayNode = currentNode as ArrayNode
                val node = arrayNode.elements()
                var index = 1
                while (node.hasNext()) {
                    process(if (prefix.isNotEmpty()) "$prefix-$index" else index.toString(), node.next(), stepCollector, varsCollector, secretsCollector, thirdPartyActions)
                    index += 1
                }
            } else if (currentNode.isObject) {
                if (currentNode.contains("steps") && currentNode.get("steps").isArray) {
                    // steps
                    val steps = (currentNode.get("steps") as ArrayNode).elements()
                    steps.forEach { stepNode ->
                        stepCollector.add(stepNode)
                    }
                }

                val useNode = currentNode.get("uses")
                if (useNode != null && useNode is TextNode) {
                    var actionName = useNode.textValue()
                    if (actionName.contains('@')) {
                        actionName = actionName.substring(0, actionName.indexOf('@'))
                    }
                    thirdPartyActions.add(actionName.lowercase())
                }

                currentNode.fields().forEachRemaining { (key, value): Map.Entry<String, JsonNode> ->
                    if (value is TextNode) {
                        val valueFixed = value.textValue().filter { !it.isWhitespace() }
                        if (valueFixed.startsWith('$')) {
                            varsCollector[key] = valueFixed.substring(1)
                        }
                    }

                    process(
                        if (prefix.isNotEmpty()) "$prefix-$key" else key,
                        value, stepCollector, varsCollector, secretsCollector, thirdPartyActions
                    )
                }
            } else {
//                println("$prefix: $currentNode")
                secretsCollector.addAll(extractSecretsInContent(currentNode.toString()))
            }
        }


        @JvmStatic
        fun main(args: Array<String>) {
            val inFile = File(args[0])
            val resultFile = File(args[1])
            if (resultFile.exists()) {
                resultFile.delete()
            }


            val cacheActionsFile = File("raw/possible_cache_actions_result_1.txt")
            val cacheActionsSet = HashSet<String>()
            cacheActionsFile.forEachLine {
                if (it.isNotBlank()) {
                    cacheActionsSet.add(it.lowercase())
                }
            }

            val gson = Gson()
            val mapper = YAMLMapper()
            val stepNodes = ArrayList<JsonNode>()
            val secrets = HashSet<String>()
            val vars = HashMap<String, String>()
            val thirdPartyActions = HashSet<String>()

            val todoCount = countLines(inFile)
            val progressBar = ProgressBar("Actions", todoCount.toLong())
            val fileWriter = FileWriter(resultFile)

            inFile.forEachLine { line ->
                progressBar.step()
                try {
                    val envSecretsAndVars = HashSet<String>()
                    val fileInfo = gson.fromJson(line, FileBase64Info::class.java)

                    val content =
                        Base64.getDecoder().decode(fileInfo.content.replace("\n", ""))
                            .toString(Charsets.UTF_8)
                    val jobResult = ArrayList<GhaJobAndSecret>()
                    val root = mapper.readTree(content)
                    if (root.get("env") != null) {
                        val envContent = mapper.writeValueAsString(root.get("env"))
                        val envSecrets = extractSecretsInContent(envContent)
                        val envVars = HashMap<String, String>()
                        if (envSecrets.isNotEmpty()) {
                            root.get("env").fields().forEachRemaining { (key, value): Map.Entry<String, JsonNode> ->
                                if (value is TextNode) {
                                    val valueFixed = value.textValue().filter { !it.isWhitespace() }
                                    if (valueFixed.startsWith('$')) {
                                        envVars[key] = valueFixed.substring(1)
                                    }
                                }
                            }
                        }
                        val envSecretsAndVarsResult = findAllSecretsVarsNoStepInfo(envVars, envSecrets)
                        envSecretsAndVars.addAll(envSecretsAndVarsResult.first union envSecretsAndVarsResult.second)
                    }

                    val jobsNode = root.get("jobs") as ObjectNode
                    jobsNode.fieldNames().forEach { jobName ->
                        stepNodes.clear()
                        secrets.clear()
                        vars.clear()
                        thirdPartyActions.clear()

                        val jobRootNode = jobsNode.get(jobName)
                        process("", jobRootNode, stepNodes, vars, secrets, thirdPartyActions)
                        val result = findAllSecretsVarsNoStepInfo(vars, secrets)
                        jobResult.add(GhaJobAndSecret(jobName, result.first union  result.second))
                    }
                    val result = GhaJobsAndSecrets(repoName = fileInfo.repoName, path = fileInfo.path, envSecretsAndVars = envSecretsAndVars, jobs = jobResult)
                    val resultStr = gson.toJson(result, GhaJobsAndSecrets::class.java)
                    fileWriter.appendLine(resultStr)

                } catch (e: Exception) {
                }
            }
            fileWriter.close()
        }

    }

}

