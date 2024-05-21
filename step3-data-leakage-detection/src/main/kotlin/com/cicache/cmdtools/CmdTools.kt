package com.cicache.cmdtools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.InputStream
import java.lang.RuntimeException
import java.util.concurrent.TimeUnit


data class ExecCmdResult(val exitCode: Int, val outMsg: String, val errorMsg: String, val cmd: String)

val NULL_FILE = File(
    if (System.getProperty("os.name")
            .startsWith("Windows")
    ) "NUL" else "/dev/null"
)

interface ProcessErrorHandler{
    fun handleError(p: Process?, e: Exception)

    companion object NOOP: ProcessErrorHandler {
        override fun handleError(p: Process?, e: Exception) {
            // do noting
        }
    }
}

interface ProcessHandler {
    fun onProcessCreated(process: Process)
}

interface ExecStreamHandler {
    fun onOutputLine(line: String)
}

object CmdTools {
    private val logger: Logger = LoggerFactory.getLogger(CmdTools::class.java)

    suspend fun execCommand(
        cmd: List<String>,
        workingDir: File = File("."),
        timeout: Long = 10,
        envVariables: Map<String, String> = emptyMap(),
        omitRedirect: Boolean = false,
        customRedirect: Boolean = false,
        outputFile: File = NULL_FILE,
        errorFile: File = NULL_FILE,
        errorHandler: ProcessErrorHandler = ProcessErrorHandler,
        processHandler: ProcessHandler? = null,
        execOutStreamHandler: ExecStreamHandler? = null
    ): ExecCmdResult {

        if (customRedirect && execOutStreamHandler != null) {
            val exp = RuntimeException("Cannot use customRedirect and execOutStreamHandler simultaneously!")
            logger.error("CmdTools error", exp)
            throw exp
        }


        val pb = ProcessBuilder(cmd)
            .directory(workingDir)

        if (envVariables.isNotEmpty()) {
            pb.environment().putAll(envVariables)
        }

        if (omitRedirect) {
            pb.redirectInput(NULL_FILE)
            pb.redirectError(NULL_FILE)
            pb.redirectOutput(NULL_FILE)
        }
        if (customRedirect) {
            pb.redirectOutput(outputFile)
            pb.redirectError(errorFile)
        }

        var process: Process? = null
        try {
            process = pb.start()

            if (process != null && processHandler != null) {
                processHandler.onProcessCreated(process)
            }


            val outputStream = if (!omitRedirect && execOutStreamHandler == null) GlobalScope.async(Dispatchers.IO) { readStream(process.inputStream) } else null
            val errorStream = if (!omitRedirect) GlobalScope.async(Dispatchers.IO) { readStream(process.errorStream) } else null

            if ( execOutStreamHandler != null) {
                readOutputStream(process.inputStream, execOutStreamHandler)
            }

            val exitCode = withContext(Dispatchers.IO) {
                process
                    .apply { waitFor(timeout, TimeUnit.SECONDS) }
                    .exitValue()
            }

            return ExecCmdResult(exitCode, outputStream?.await() ?: "omitRedirect", errorStream?.await() ?: "", cmd.joinToString(" "))
        } catch (e: Exception) {
            e.printStackTrace()
            errorHandler.handleError(process, e)
            return ExecCmdResult(-1, "", e.localizedMessage, cmd.joinToString(" "))
        }

    }

    fun execCommandSync(
        cmd: List<String>,
        workingDir: File = File("."),
        timeout: Long = 10,
        envVariables: Map<String, String> = emptyMap(),
        outputFile: File = NULL_FILE,
        errorFile: File = NULL_FILE
    ): Int = try {
        logger.info("execCommandSync ${cmd.joinToString(" ")}")

        ProcessBuilder(cmd)
            .directory(workingDir)
            .redirectOutput(outputFile)
            .redirectInput(NULL_FILE)
            .redirectError(errorFile)
            .start().apply { waitFor(timeout, TimeUnit.SECONDS) }
            .exitValue()
    } catch (e: java.io.IOException) {
        e.printStackTrace()
        -100
    } catch (e: IllegalThreadStateException) {
        // time out
        e.printStackTrace()
        -200
    }

    fun execCommandAsync(
        cmd: List<String>,
        workingDir: File = File("."),
        envVariables: Map<String, String> = emptyMap()
    ): Process {
        val pb = ProcessBuilder(cmd)
            .directory(workingDir)
        if (envVariables.isNotEmpty()) {
            pb.environment().putAll(envVariables)
        }
        return pb.start()
    }

    private suspend fun readStream(inputStream: InputStream): String {
        val readLines = mutableListOf<String>()

        withContext(Dispatchers.IO) {
            try {
                inputStream.bufferedReader().use { reader ->
                    var line: String?

                    do {
                        line = reader.readLine()

                        if (line != null) {
                            readLines.add(line)
                        }
                    } while (line != null)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return readLines.joinToString(System.lineSeparator())
    }

    private suspend fun readOutputStream(inputStream: InputStream, execStreamHandler: ExecStreamHandler) {
        withContext(Dispatchers.IO) {
            try {
                inputStream.bufferedReader().use { reader ->
                    var line: String?
                    do {
                        line = reader.readLine()
                        if (line != null) {
                            execStreamHandler.onOutputLine(line)
                        }
                    } while (line != null)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

}