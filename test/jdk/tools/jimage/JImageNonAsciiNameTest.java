/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.file.Path;
import jdk.internal.jimage.BasicImageReader;
import jdk.internal.jimage.ImageLocation;
import jdk.test.lib.compiler.InMemoryJavaCompiler;
import jdk.test.lib.util.JarBuilder;

import tests.Helper;
import tests.JImageGenerator;
import tests.Result;

/*
 * @test
 * @bug 8278185
 * @summary Test non-ASCII path in custom JRE
 * @library ../lib
 *          /test/lib
 * @modules java.base/jdk.internal.jimage
 *          jdk.jdeps/com.sun.tools.classfile
 *          jdk.jlink/jdk.tools.jimage
 * @build tests.*
 * @run main/othervm JImageNonAsciiNameTest
 */

public class JImageNonAsciiNameTest {
    private final static String moduleName = "A_module";
    private final static String packageName = "test.\u3042"; //non-ASCII
    private final static String className = "A";
    private final static String fullName = packageName + "." + className;
    private static Helper helper;

    public static void main(String[] args) throws Exception {
        helper = Helper.newHelper();
        if (helper == null) {
            System.err.println("Test not run");
            return;
        }

        String source =
            "package "+packageName+";" +
            "public class "+className+" {" +
            "    public static void main(String[] args) {}" +
            "}";
        String moduleInfo = "module " + moduleName + " {}";

        // Using InMemory features to avoid generating non-ASCII name file
        byte[] byteA = InMemoryJavaCompiler.compile(fullName, source);
        byte[] byteModule = InMemoryJavaCompiler.compile(
                "module-info", moduleInfo);

        Path jarDir = helper.getJarDir();
        JarBuilder jb = new JarBuilder(
                jarDir.resolve(moduleName + ".jar").toString());
        jb.addEntry(fullName.replace(".","/") + ".class", byteA);
        jb.addEntry("module-info.class", byteModule);
        jb.build();

        Path outDir = helper.createNewImageDir(moduleName);

        Result result = JImageGenerator.getJLinkTask()
                .modulePath(helper.defaultModulePath())
                .output(outDir)
                .addMods(moduleName)
                .call();
        Path testImage = result.assertSuccess();

        BasicImageReader bir = BasicImageReader.open(
                testImage.resolve("lib").resolve("modules"));
        ImageLocation loc = bir.findLocation(moduleName,
                fullName.replace(".","/") + ".class");
        if (loc == null) {
            throw new RuntimeException("Failed to find " +
                    fullName + " in module " +moduleName);
        }
    }
}
