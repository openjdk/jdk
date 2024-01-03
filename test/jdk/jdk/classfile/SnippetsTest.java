/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Compile ClassFile API snippets
 * @run junit SnippetsTest
 */

import java.nio.file.Paths;
import java.util.List;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class SnippetsTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "src/java.base/share/classes/java/lang/classfile/snippet-files/PackageSnippets.java",
        "src/java.base/share/classes/java/lang/classfile/components/snippet-files/PackageSnippets.java"})
    void testSnippet(String source) throws Exception {
        var p = Paths.get(System.getProperty("test.src", ".")).toAbsolutePath();
        while ((p = p.getParent()) != null) {
            var src = p.resolve(source).toFile();
            if (src.isFile()) {
                var compiler = ToolProvider.getSystemJavaCompiler();
                try (var fileManager = compiler.getStandardFileManager(null, null, null)) {
                    var compilationUnits = fileManager.getJavaFileObjectsFromFiles(List.of(src));
                    fileManager.setLocation(StandardLocation.CLASS_OUTPUT,
                            List.of(Paths.get(System.getProperty("test.classes", ".")).toFile()));
                    var task = compiler.getTask(null, fileManager, null, List.of(
                            "--enable-preview",
                            "--source", String.valueOf(Runtime.version().feature())),
                            null, compilationUnits);
                    if (task.call()) return;
                    throw new RuntimeException("Error compiling " + source);
                }
            }
        }
        Assumptions.abort("Source file not found: " + source); //do not fail in source-less test environment
    }
}
