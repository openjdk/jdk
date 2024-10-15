/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8245432
 * @modules jdk.compiler
 * @library /test/lib
 * @build jdk.test.lib.Utils
 *        jdk.test.lib.compiler.CompilerUtils
 * @run testng PreviewHiddenClass
 * @summary verify UnsupportedClassVersionError thrown when defining a hidden class
 *         with preview minor version but --enable-preview is not set
 * @comment This test itself cannot enablePreview, or hidden class definition
 *         will pass
 */

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import jdk.test.lib.compiler.CompilerUtils;
import jdk.test.lib.Utils;

import org.testng.annotations.Test;
import static org.testng.Assert.*;

public class PreviewHiddenClass {

    private static final Path SRC_DIR = Paths.get(Utils.TEST_SRC, "src");
    private static final Path CLASSES_DIR = Paths.get("classes");

    @Test(expectedExceptions = { UnsupportedClassVersionError.class })
    public void previewNotEnabled() throws Exception {
        // compile a class with --enable-preview
        Path sourceFile = SRC_DIR.resolve("HiddenInterface.java");
        String[] options = new String[] {
                "--enable-preview", "-source", String.valueOf(Runtime.version().feature()), "-XDforcePreview" };
        if (!CompilerUtils.compile(sourceFile, CLASSES_DIR, options)) {
            throw new RuntimeException("Compilation of the test failed: " + sourceFile);
        }

        byte[] bytes = Files.readAllBytes(CLASSES_DIR.resolve("HiddenInterface.class"));
        var dis = new DataInputStream(new ByteArrayInputStream(bytes));
        dis.skipBytes(4); // 0xCAFEBABE
        assertEquals(dis.readUnsignedShort(), 65535); // Minor version
        MethodHandles.lookup().defineHiddenClass(bytes, false);
    }
}
