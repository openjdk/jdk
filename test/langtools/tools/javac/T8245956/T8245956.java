/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8245956
 * @summary JavaCompiler still uses File API instead of Path API in a specific case
 * @run main T8245956
 */

import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

public class T8245956 {
    public static void main(String[] args) throws Exception {
        Path zipFilePath = Path.of("T8245956.zip");
        URI zipFileUri = zipFilePath.toUri();
        URI jarZipFileUri = URI.create("jar:" + zipFileUri.toString());
        Map<String, String> env = new LinkedHashMap<>();
        env.put("create", "true");
        try (FileSystem fs = FileSystems.newFileSystem(jarZipFileUri, env)) {
            Path fsPath = fs.getPath("");
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            DiagnosticCollector<JavaFileObject> diagnosticCollector = new DiagnosticCollector<>();
            try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnosticCollector, null, null)) {
                List<Path> classPath = new ArrayList<>();
                classPath.add(fsPath);
                fileManager.setLocationFromPaths(StandardLocation.CLASS_PATH, classPath);
                fileManager.getClassLoader(StandardLocation.CLASS_PATH);  // Should not generate any exceptions.
                System.out.println("The method getClassLoader terminated normally.\n");
            }
        }
    }
}
