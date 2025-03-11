/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8338675
 * @summary javac shouldn't silently change .jar files on the classpath
 * @library /tools/lib /tools/javac/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.ToolBox toolbox.JarTask toolbox.JavacTask
 * @run junit AnnotationFilerTest
 */

import org.junit.jupiter.api.Test;
import toolbox.JarTask;
import toolbox.JavacTask;
import toolbox.ToolBox;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.TypeElement;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Set;

import static javax.tools.StandardLocation.CLASS_OUTPUT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AnnotationFilerTest {
    private static final String LIB_SOURCE =
            """
            package lib;
            public class LibClass {
                public static final String FIELD = "";
            }
            """;

    // Target source references the field to force library compilation.
    private static final String TARGET_SOURCE =
            """
            class TargetClass {
                static final String VALUE = lib.LibClass.FIELD;
            }
            """;

    private static final String LIB_CLASS_TYPE_NAME = "lib.LibClass";
    private static final Path LIB_JAR = Path.of("lib.jar");

    private static final String LIB_SOURCE_FILE_NAME = "lib/LibClass.java";
    private static final String LIB_CLASS_FILE_NAME = "lib/LibClass.class";

    // These differ only in the filename suffix part and represent the trailing
    // parts of a Path URI for source/class files in the JAR. The source file
    // will exist, but the class file must not be created by annotation processing.
    private static final String JAR_SOURCE_URI_SUFFIX = "lib.jar!/" + LIB_SOURCE_FILE_NAME;
    private static final String JAR_CLASS_URI_SUFFIX = "lib.jar!/" + LIB_CLASS_FILE_NAME;

    @Test
    public void classpathJarsCannotBeWrittenDuringProcessing() throws IOException {
        ToolBox tb = new ToolBox();
        tb.createDirectories("lib");
        tb.writeFile(LIB_SOURCE_FILE_NAME, LIB_SOURCE);
        new JarTask(tb, LIB_JAR).files(LIB_SOURCE_FILE_NAME).run();

        // These are assertions about the test environment, not the code-under-test.
        try (FileSystem zipFs = FileSystems.newFileSystem(LIB_JAR)) {
            // The bug would have only manifested with writable JAR files, so assert that here.
            assertFalse(zipFs.isReadOnly());
            // This is the JAR file URI for the *source* code. Later we attempt to create the
            // sibling *class* file from within the annotation processor (which MUST FAIL).
            // We get the source URI here to verify the naming convention of the JAR file
            // URIs to prevent the _negative_ test we do later being fragile.
            URI libUri = zipFs.getPath(LIB_SOURCE_FILE_NAME).toUri();
            assertEquals("jar", libUri.getScheme());
            assertTrue(libUri.getSchemeSpecificPart().endsWith(JAR_SOURCE_URI_SUFFIX));
        }

        // Code under test:
        // Compile the target class with library source only available in the JAR. This should
        // succeed, but MUST NOT be able to create files in the JAR during annotation processing.
        new JavacTask(tb)
                .processors(new TestAnnotationProcessor())
                .options("-implicit:none", "-g:source,lines,vars")
                .sources(TARGET_SOURCE)
                .classpath(LIB_JAR)
                .run()
                .writeAll();
    }

    static class TestAnnotationProcessor extends JavacTestingAbstractProcessor {
        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment env) {
            // Only run this once (during the final pass), or else we get a spurious failure
            // about trying to recreate the class file (not allowed during annotation
            // processing, but not what we are testing here).
            if (!env.processingOver()) {
                return false;
            }

            TypeElement libType = elements.getTypeElement(LIB_CLASS_TYPE_NAME);
            JavaFileObject libClass;
            // This is the primary code-under-test. The Filer must not return a file object
            // that's a sibling to the source file of the given type (that's in the JAR,
            // which MUST NOT be modified). Before bug 8338675 was fixed, this would fail.
            try {
                libClass = filer.createClassFile("LibClass", libType);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            // Double check that this is the expected file kind.
            assertEquals(JavaFileObject.Kind.CLASS, libClass.getKind());

            // Primary assertions: Check the file object's URI is not from the JAR file.
            URI libUri = libClass.toUri();
            // If this were from the JAR file, the URI scheme would be "jar" (as tested
            // for earlier during setup).
            assertEquals("file", libUri.getScheme());

            // Check that the URI is for the right class, but not in the JAR.
            assertTrue(libUri.getSchemeSpecificPart().endsWith("/LibClass.class"));
            // Testing a negative is fragile, but the earlier checks show this would be
            // the expected path if the URI was referencing an entry in the JAR.
            assertFalse(libUri.getSchemeSpecificPart().endsWith(JAR_CLASS_URI_SUFFIX));

            // Additional regression testing for other file objects the Filer can create
            // (all specified as originating from the LibClass type in the JAR). These
            // should all create file objects, just not in the JAR.
            try {
                assertNonJar(
                        filer.createClassFile("FooClass", libType),
                        "/FooClass.class");
                assertNonJar(
                        filer.createSourceFile("BarClass", libType),
                        "/BarClass.java");
                assertNonJar(
                        filer.createResource(CLASS_OUTPUT, "lib", "data.txt", libType),
                        "/data.txt");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return false;
        }
    }

    static void assertNonJar(FileObject file, String uriSuffix) {
        URI uri = file.toUri();
        assertEquals("file", uri.getScheme());
        assertTrue(uri.getSchemeSpecificPart().endsWith(uriSuffix));
    }
}
