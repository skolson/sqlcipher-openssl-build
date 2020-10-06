package com.oldguy.gradle

import org.eclipse.jgit.api.Git
import java.io.File
import java.net.URI

/**
 * Clones a repository single tag, then checks it out.  In OpenSSL case this is not fast, takes a few minutes to
 * do the clone operation.  HTTPS download and unzip of an archive is much quicker, but does not leave a
 * local git repository to work from after build.
 */
class GitHub(private val uri: URI,
             private val tagName: String,
             private val targetDir: File)
{

    fun cloneCheckout():Git {
        val spec = "refs/tags/$tagName"
        val git = Git.cloneRepository()
            .setURI(uri.toASCIIString())
            .setDirectory(targetDir)
            .setCloneAllBranches(false)
            .setBranchesToClone(listOf(spec))
            .setBranch(spec)
            .call()
        git.checkout().setName(tagName).call()
        return git
    }
}