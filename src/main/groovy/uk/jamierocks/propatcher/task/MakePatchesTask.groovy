/*
 * This file is part of ProPatcher, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2015, Jamie Mansfield <https://github.com/jamierocks>
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
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

import java.nio.file.Files
import java.nio.file.Paths

class MakePatchesTask extends DefaultTask {

    File root
    File target
    File patches

    @TaskAction
    void doTask() {
        if (patches.isDirectory()) {
            patches.deleteDir() // If exists, delete directory
        }

        patches.mkdirs() // Make sure patches directory exists.

        process(root, target) // Make the patches
    }

    void process(File root, File target) {
        Files.walk(Paths.get(root.canonicalPath)).each { filePath ->
            if (Files.isRegularFile(filePath)) {
                String relative = filePath.toString().replace(root.getCanonicalPath() + '/', '')
                println relative

                File originalFile = new File(root, relative)
                File modifiedFile = new File(target, relative)

                Diff diff = Diff.diff(originalFile, modifiedFile, true)

                if (!diff.isEmpty()) {
                    File patchFile = new File(patches, "${relative}.patch")
                    patchFile.parentFile.mkdirs()
                    patchFile.createNewFile()

                    String thediff = diff.toUnifiedDiff(relative, relative,
                            new FileReader(originalFile), new FileReader(modifiedFile), 3)

                    new FileOutputStream(patchFile).withStream {
                        write(thediff.getBytes())
                        close()
                    }
                }
            }
        }
    }
}
