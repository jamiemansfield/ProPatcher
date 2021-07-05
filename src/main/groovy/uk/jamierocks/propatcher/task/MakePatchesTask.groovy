/*
 * This file is part of ProPatcher, licensed under the MIT License (MIT).
 *
 * Copyright (c) Jamie Mansfield <https://www.jamierocks.uk/>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package uk.jamierocks.propatcher.task

import com.cloudbees.diff.Diff
import groovy.io.FileType
import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserDataException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction

import java.nio.charset.StandardCharsets
import java.util.regex.Matcher
import java.util.zip.ZipFile

class MakePatchesTask extends DefaultTask {

    @InputFile @Optional File rootZip = null
    @InputDirectory @Optional File rootDir = null
    @InputDirectory File target
    @InputDirectory File patches
    @Input @Optional String originalPrefix = 'a/'
    @Input @Optional String modifiedPrefix = 'b/'
    private boolean ignoreWhitespace = true

    static def relative(base, file) {
        return file.path.substring(base.path.length() + 1).replaceAll(Matcher.quoteReplacement(File.separator), '/') //Replace is to normalize windows to linux/zip format
    }

    static def deleteEmpty(base) {
        def dirs = []
        base.eachFileRecurse(FileType.DIRECTORIES){ file -> if (file.list().length == 0) dirs.add(file) }
        dirs.reverse().each{ it.delete() } //Do it in reverse order do we delete deepest first
    }

    @Input
    boolean getIgnoreWhitespace() {
        return ignoreWhitespace
    }

    void setIgnoreWhitespace(boolean ignoreWhitespace) {
        this.ignoreWhitespace = ignoreWhitespace
    }

    @TaskAction
    void doTask() {
        if (!patches.exists())
            patches.mkdirs()

        def root = rootZip == null ? rootDir : rootZip
        if (root == null)
            throw new InvalidUserDataException("At least one of rootZip and rootDir has to be specified!")
        process(root, target) // Make the patches
    }

    void process(File root, File target) {
        def paths = []
        target.eachFileRecurse(FileType.FILES){ file -> if (!file.path.endsWith('~')) paths.add relative(target, file) }
        if (root.isDirectory()) {
            root.eachFileRecurse(FileType.FILES) { file ->
                def relative = relative(root, file)
                file.withInputStream{stream -> makePatch(relative, stream, new File(target, relative))}
                paths.remove(relative)
            }
        } else {
            def zip = new ZipFile(root)
            zip.entries().each { ent ->
                if (!ent.isDirectory()) {
                    makePatch(ent.name, zip.getInputStream(ent), new File(target, ent.name))
                    paths.remove(ent.name)
                }
            }
        }
        paths.each{ makePatch(it, null, new File(target, it)) } //Added files!
    }

    def makePatch(relative, original, modified) {
        String originalRelative = original == null ? '/dev/null' : originalPrefix + relative
        String modifiedRelative = !modified.exists() ? '/dev/null' : modifiedPrefix + relative

        def originalData = original == null ? "" : original.getText("UTF-8")
        def modifiedData = !modified.exists() ? "" : modified.getText("UTF-8")

        final Diff diff = Diff.diff(new StringReader(originalData), new StringReader(modifiedData), ignoreWhitespace)

        if (!diff.isEmpty()) {
            final File patchFile = new File(patches, "${relative}.patch")
            patchFile.parentFile.mkdirs()
            patchFile.createNewFile()

            final String unifiedDiff = diff.toUnifiedDiff(originalRelative, modifiedRelative,
                    new StringReader(originalData), new StringReader(modifiedData), 3)
                    .replaceAll('\r?\n', '\n') // Normalise to linux line endings

            patchFile.newOutputStream().withStream {
                s -> s.write(unifiedDiff.getBytes(StandardCharsets.UTF_8))
            }
        }
    }

}
