/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8331027 8356645
 * @summary Verify classfile inside ct.sym
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.file
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.compiler/com.sun.tools.javac.platform
 *          jdk.compiler/com.sun.tools.javac.util:+open
 * @build toolbox.ToolBox VerifyCTSymClassFiles
 * @run main VerifyCTSymClassFiles
 */

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.attribute.ModuleMainClassAttribute;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

public class VerifyCTSymClassFiles {

    public static void main(String... args) throws IOException, URISyntaxException {
        VerifyCTSymClassFiles t = new VerifyCTSymClassFiles();

        t.checkClassFiles();
    }

    void checkClassFiles() throws IOException {
        Path ctSym = Paths.get(System.getProperty("java.home"), "lib", "ct.sym");

        if (!Files.exists(ctSym)) {
            //no ct.sym, nothing to check:
            return ;
        }
        // Expected to always be a ZIP filesystem.
        Map<String, ?> env = Map.of("accessMode", "readOnly");
        try (FileSystem fs = FileSystems.newFileSystem(ctSym, env)) {
            // Check that the file system is read only (not true if not a zip file system).
            if (!fs.isReadOnly()) {
                throw new AssertionError("Expected read-only file system");
            }
            Files.walk(fs.getRootDirectories().iterator().next())
                 .filter(p -> Files.isRegularFile(p))
                 .forEach(p -> checkClassFile(p));
        }
    }

    void checkClassFile(Path p) {
        if (!"module-info.sig".equals(p.getFileName().toString())) {
            return ;
        }
        try {
            ClassFile.of().parse(p).attributes().forEach(attr -> {
                if (attr instanceof ModuleMainClassAttribute mmca) {
                    mmca.mainClass();
                }
            });
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

}
