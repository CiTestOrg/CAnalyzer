package com.cicache.analyzer

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.TextNode
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.google.gson.Gson
import me.tongfei.progressbar.ProgressBar
import java.io.*
import java.util.*
import java.util.regex.Matcher
import java.util.regex.Pattern


/**
 * TravisCI
 */
class TravisCI {

    companion object {
        private const val pattern = "\\\$([a-zA-Z_]+[a-zA-Z0-9_]*)"
        private val r: Pattern = Pattern.compile(pattern)

        private fun getSecretVars (varsReferInContent: Set<String>, localVars: Set<String>, globalVars: Set<String> = emptySet()): Set<String> {
            val file = File("raw/travis_predefined_vars.txt")
            val predefineVars = HashSet<String>()
            file.forEachLine {
                predefineVars.add(it)
            }
            return varsReferInContent - localVars - globalVars - predefineVars
        }


        private fun process(prefix: String, currentNode: JsonNode, localVarSet: HashSet<String>, varsCollector: HashMap<String, String>) {
            if (currentNode.isArray) {
                val arrayNode = currentNode as ArrayNode
                val node = arrayNode.elements()
                var index = 1
                while (node.hasNext()) {
                    val nextNode = node.next()
                    process(if (prefix.isNotEmpty()) "$prefix-$index" else index.toString(), nextNode, localVarSet, varsCollector)
                    index += 1
                }
            } else if (currentNode.isObject) {
                currentNode?.fields()?.forEachRemaining { (key, value): Map.Entry<String, JsonNode> ->
                    if (value is TextNode) {
                        localVarSet.add(key)
                    }
                    if (value is TextNode) {
                        val valueFixed = value.textValue().filter { !it.isWhitespace() && it != '{' && it != '}' }
                        val m: Matcher = r.matcher(valueFixed)
                        if (m.find()) {
                            varsCollector[key] = m.group(1)
                        }
                    }
                }

                currentNode.fields().forEachRemaining { (key, value): Map.Entry<String, JsonNode> ->
                    process(
                            if (prefix.isNotEmpty()) "$prefix-$key" else key, value, localVarSet, varsCollector
                    )
                }
            } else {
                if (currentNode is TextNode) {
                    val v = currentNode.textValue().filter { !it.isWhitespace() && it !in setOf<Char>('{', '}') }
                    if (v.contains("=$")) {
                        localVarSet.add(v.substring(0, v.indexOf("=$")))
                    }

                }
            }
        }


        @JvmStatic
        fun main(args: Array<String>) {
            val inFile = File(args[0])
            val resultFile = File(args[1])
            if (resultFile.exists()) {
                resultFile.delete()
            }

            val gson = Gson()
            val mapper = YAMLMapper()

            val todoCount = GitHubActions.countLines(inFile)
            val progressBar = ProgressBar("Actions", todoCount.toLong())
            val fileWriter = FileWriter(resultFile)

            inFile.forEachLine { line ->
                progressBar.step()
                try {

                    val fileInfo = gson.fromJson(line, FileBase64Info::class.java)
                    val content =
                        Base64.getDecoder().decode(fileInfo.content.replace("\n", ""))
                            .toString(Charsets.UTF_8)
                    val varLinks = HashMap<String, String>()
                    val varSet = HashSet<String>()
                    val root = mapper.readTree(content)
                    process("", root, varSet, varLinks)
                    val allUsedVars = SecretVarTool.extractSecretsInContent(mapper.writeValueAsString(root))
                    val seedSecrets = getSecretVars(allUsedVars, varSet)
                    val tempResult = GitHubActions.findAllSecretsVarsNoStepInfo(varLinks, seedSecrets)
                    val secretAndVars = tempResult.first union tempResult.second

                    val result = GhaJobsAndSecrets(repoName = fileInfo.repoName, path = fileInfo.path, envSecretsAndVars = secretAndVars, emptyList())
                    val resultStr = gson.toJson(result, GhaJobsAndSecrets::class.java)
                    fileWriter.appendLine(resultStr)

                } catch (e: Exception) {
                }
            }
            fileWriter.close()
        }

    }

}

