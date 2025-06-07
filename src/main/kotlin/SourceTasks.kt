package com.oldguy.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.nio.channels.Channels
import javax.inject.Inject

/**
 * Using a Github URI and tagName, clone the single tag only, then do checkout to end up with a local copy of the
 * source for that tag, as a local git repo.
 */
abstract class GitCheckoutTask @Inject constructor(): DefaultTask() {

    init {
        description = "Clone tag from Github repo and check out local"
        group = "build"
    }

    @get:Input
    abstract val uri: Property<String>
    @get:Input
    abstract val tagName: Property<String>
    @get:OutputDirectory
    abstract val sourceDirectory: DirectoryProperty

    fun setup(gitUri: String, gitTagName: String, srcDir: File) {
        uri.set(gitUri)
        tagName.set(gitTagName)
        sourceDirectory.set(srcDir)
    }

    @TaskAction
    fun gitCloneCheckout() {
        val git = GitHub(
                URI.create(uri.get()),
                tagName.get(),
                sourceDirectory.get().asFile)
        git.cloneCheckout()
    }
}

/**
 * Download an archive file, typically a zip or a tar, usually from a Github URL
 */
abstract class DownloadArchiveTask @Inject constructor(): DefaultTask() {

    init {
        description = "HTTP download of an extractable archive from a specified URL"
        group = "build"
    }

    @get:Input
    abstract val url: Property<String>
    @get:OutputFile
    abstract val downloadFile: RegularFileProperty

    fun setup(downloadUrl: String, outputFile: File) {
        url.set(downloadUrl)
        downloadFile.set(outputFile)
        outputs.file(outputFile)
    }

    @TaskAction
    fun download() {
        val readChannel = Channels.newChannel(URI(url.get()).toURL().openStream())
        val fileOS = FileOutputStream(downloadFile.get().asFile)
        val writeChannel = fileOS.channel
        writeChannel.transferFrom(readChannel, 0, Long.MAX_VALUE)
        writeChannel.close()
        readChannel.close()
    }
}

/**
 * Perform an extract all operation from the input archive file
 */
abstract class ExtractArchiveTask @Inject constructor(): DefaultTask() {

    init {
        description = "Extractable all of an archive to a specified source directory"
        group = "build"
    }

    @get:OutputFile
    abstract val archiveFile: RegularFileProperty
    @get:OutputDirectory
    abstract val sourceDirectory: DirectoryProperty

    /**
     * Archives usually contain their parent directory at top, which is redundant with the value of srcDir and results
     * in an extra undesired subdirectory. So this function assumes that use case. Note that in a case where the archive
     * file does not contain a parent directory, the caller must make one and pass it to srcDir for the extract to end
     * up in the correct location.
     * @param archiveFile specifies the file (zip or tar.gz) to be extracted
     * @param srcDir is the directory the extract should go into, WITHOUT the owning parent directory.  So the actual
     * extract will resolve up one subdirectory from [srcDir] and extract all into that.
     */
    fun setup(archiveFile: File, srcDir: File) {
        this.archiveFile.set(archiveFile)
        inputs.file(this.archiveFile)
        if (!srcDir.exists()) srcDir.mkdir()
        sourceDirectory.set(srcDir.resolve(".."))
        outputs.dir(sourceDirectory)
    }

    @TaskAction
    fun download() {
        project.copy { spec ->
            if (Builder.isWindowsArchive(archiveFile.get().asFile))
                spec.from(project.zipTree(archiveFile.get().asFile))
                        .into(sourceDirectory.get())
            else
                spec.from(project.tarTree(archiveFile.get().asFile))
                        .into(sourceDirectory.get())
        }
    }
}