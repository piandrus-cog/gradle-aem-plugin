package com.cognifide.gradle.aem.instance.tasks

import com.cognifide.gradle.aem.AemDefaultTask
import com.cognifide.gradle.aem.AemException
import com.cognifide.gradle.aem.common.file.FileOperations
import com.cognifide.gradle.aem.common.instance.InstanceException
import com.cognifide.gradle.aem.common.instance.local.Status
import com.cognifide.gradle.aem.common.instance.names
import com.cognifide.gradle.common.utils.Formats
import java.io.File
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

open class InstanceBackup : AemDefaultTask() {

    private val resolver = aem.localInstanceManager.backup

    /**
     * Determines what need to be done (backup zipped and uploaded or something else).
     */
    @Internal
    var mode: Mode = Mode.of(aem.prop.string("instance.backup.mode")
            ?: Mode.ZIP_AND_UPLOAD.name)

    @TaskAction
    fun backup() {
        when (mode) {
            Mode.ZIP_ONLY -> zip()
            Mode.ZIP_AND_UPLOAD -> {
                val zip = zip()
                upload(zip, false)
            }
            Mode.UPLOAD_ONLY -> {
                val zip = resolver.local ?: throw InstanceException("No local instance backup to upload!")
                upload(zip, true)
            }
        }
    }

    private fun zip(): File {
        val uncreated = aem.localInstances.filter { !it.created }
        if (uncreated.isNotEmpty()) {
            throw InstanceException("Cannot create local instance backup, because there are instances not yet created: ${uncreated.names}")
        }

        val running = aem.localInstances.filter { it.status == Status.RUNNING }
        if (running.isNotEmpty()) {
            throw InstanceException("Cannot create local instance backup, because there are instances still running: ${running.names}")
        }

        val file = resolver.localDir.get().asFile.resolve(resolver.namer())

        common.progress {
            message = "Backing up instances: ${aem.localInstances.names}"
            FileOperations.zipPack(file, aem.localInstanceManager.rootDir.get().asFile)
        }

        common.notifier.lifecycle("Backed up local instances", "File: $file (${Formats.fileSize(file)})")

        return file
    }

    private fun upload(file: File, verbose: Boolean) {
        val dirUrl = resolver.uploadUrl.get()
        if (dirUrl.isNullOrBlank()) {
            val message = "Cannot upload local instance backup as of URL is not defined."
            if (verbose) {
                throw InstanceException(message)
            } else {
                aem.logger.info(message)
                return
            }
        }

        logger.info("Uploading local instance(s) backup file '$file' to URL '$dirUrl'")
        common.fileTransfer.uploadTo(dirUrl, file)

        common.notifier.lifecycle("Uploaded local instances backup", "File '$file' to URL '$dirUrl'")
    }

    init {
        description = "Turns off local instance(s), archives to ZIP file, then turns on again."
    }

    enum class Mode {
        ZIP_ONLY,
        ZIP_AND_UPLOAD,
        UPLOAD_ONLY;

        companion object {
            fun of(name: String) = values().find { it.name.equals(name, true) }
                    ?: throw AemException("Unsupported instance backup mode: $name")
        }
    }

    companion object {
        const val NAME = "instanceBackup"
    }
}
