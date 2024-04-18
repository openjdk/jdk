/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8189778
 * @summary Test JavadocHelper
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.compiler/jdk.internal.shellsupport.doc
 * @build toolbox.ToolBox toolbox.JarTask toolbox.JavacTask
 * @run testng/timeout=900/othervm -Xmx1024m FullJavadocHelperTest
 */

import java.io.IOException;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


import javax.lang.model.element.Element;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.ModuleElement.ExportsDirective;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic.Kind;
import javax.tools.DiagnosticListener;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import com.sun.source.util.JavacTask;
import jdk.internal.shellsupport.doc.JavadocHelper;
import org.testng.annotations.Test;

@Test
public class FullJavadocHelperTest {

    /*
     * Long-running test to retrieve doc comments for enclosed elements of all JDK classes.
     */
    public void testAllDocs() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticListener<? super JavaFileObject> noErrors = d -> {
            if (d.getKind() == Kind.ERROR) {
                throw new AssertionError(d.getMessage(null));
            }
        };

        List<Path> sources = new ArrayList<>();
        Path home = Paths.get(System.getProperty("java.home"));
        Path srcZip = home.resolve("lib").resolve("src.zip");
        if (Files.isReadable(srcZip)) {
            URI uri = URI.create("jar:" + srcZip.toUri());
            try (FileSystem zipFO = FileSystems.newFileSystem(uri, Collections.emptyMap())) {
                Path root = zipFO.getRootDirectories().iterator().next();

                //modular format:
                try (DirectoryStream<Path> ds = Files.newDirectoryStream(root)) {
                    for (Path p : ds) {
                        if (Files.isDirectory(p)) {
                            sources.add(p);
                        }
                    }
                }
                try (StandardJavaFileManager fm =
                             compiler.getStandardFileManager(null, null, null)) {
                    JavacTask task =
                            (JavacTask) compiler.getTask(null, fm, noErrors, null, null, null);
                    task.getElements().getTypeElement("java.lang.Object");
                    for (ModuleElement me : task.getElements().getAllModuleElements()) {
                        List<ExportsDirective> exports =
                                ElementFilter.exportsIn(me.getDirectives());
                        for (ExportsDirective ed : exports) {
                            try (JavadocHelper helper = JavadocHelper.create(task, sources)) {
                                List<? extends Element> content =
                                        ed.getPackage().getEnclosedElements();
                                for (TypeElement clazz : ElementFilter.typesIn(content)) {
                                    for (Element el : clazz.getEnclosedElements()) {
                                        helper.getResolvedDocComment(el);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
