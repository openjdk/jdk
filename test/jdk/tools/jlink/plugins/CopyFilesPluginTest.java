/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @summary Test copy files plugin
 * @library ../../lib
 * @library /test/lib
 * @modules java.base/jdk.internal.jimage
 *          jdk.jdeps/com.sun.tools.classfile
 *          jdk.jlink/jdk.tools.jlink.internal
 *          jdk.jlink/jdk.tools.jmod
 *          jdk.jlink/jdk.tools.jimage
 *          jdk.compiler
 * @build tests.*
 * @run main CopyFilesPluginTest
 */

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;

import jdk.test.lib.process.ProcessTools;

import tests.Helper;

public class CopyFilesPluginTest {

    public static void main(String[] args) throws Throwable {

        Helper helper = Helper.newHelper();
        if (helper == null) {
            System.err.println("Test not run");
            return;
        }

        var module = "copyfiles";
        helper.generateDefaultJModule(module);
        var image = helper.generateDefaultImage(new String[] {
                "--add-modules", "jdk.jlink,jdk.jdeps,jdk.internal.opt,jdk.compiler,jdk.zipfs,java.compiler",
                "--keep-packaged-modules", "images/copyfiles.image/jmods"
            }, module)
            .assertSuccess();
        helper.checkImage(image, module, null, null);

        String[] files = {
            "lib/foo",
            "bin/bar",
            "foreign/baz"
        };

        for (String file : files) {
            Path path = image.resolve(file);
            Files.createDirectories(path.getParent());
            Files.writeString(image.resolve(file), file);
        }

        var launcher = image.resolve("bin/jlink"
                                     + (System.getProperty("os.name").startsWith("Windows")
                                        ? ".exe" : ""));


        var oa = ProcessTools.executeProcess(launcher.toString(),
                                             "--add-modules", "ALL-MODULE-PATH",
                                             "--copy-files=" + String.join(File.pathSeparator, files),
                                             "--output", "image2");
        oa.shouldHaveExitValue(0);

        for (String file : files) {
            Path path = Paths.get("image2", file);
            String content = Files.readString(path);
            if (!file.equals(content)) {
                throw new AssertionError(path + ": expected \"" + file + "\", got \"" + content + "\"");
            }
        }
    }
}
