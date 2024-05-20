package com.cicache.cmdtools

import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class CmdWorker(): Runnable {
    private val logger: Logger = LoggerFactory.getLogger(CmdWorker::class.java)

    override fun run() {
        val startCmdList = listOf("/bin/sh", "node", "matchpath.js")
        val result = runBlocking {
            CmdTools.execCommand(cmd = startCmdList, omitRedirect = true)
        }
        logger.info(result.toString())
    }

}