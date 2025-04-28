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
 * @run junit TestNoOverwriteJarFiles
 */

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import toolbox.JavacTask;
import toolbox.ToolBox;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.TypeElement;
import javax.tools.FileObject;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Set;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import static javax.tools.StandardLocation.CLASS_OUTPUT;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

/**
 * Tests that javac cannot unexpectedly modify contents of JAR files on the
 * class path.
 * <p>
 * Consider the following javac behaviours:
 * <ol>
 * <li>If there is no source path, javac searches the classpath for sources,
 * including inside JAR files.
 * <li>If a Java source file was modified more recently that an existing class
 * file, or if no class file exists, javac will compile it from the source.
 * <li>If there is no output directory specified, javac will put compiled class
 * files next to their corresponding sources.
 * </ol>
 * Taken together, this suggests that a newly compiled class file should be
 * written back into the JAR in which its source was found, possibly overwriting
 * an existing class file entry. This would be very problematic.
 * <p>
 * This test ensures javac will not modify JAR files on the classpath, even if
 * it compiles sources contained within them. Instead, the class file will be
 * written into the current working directory, which mimics the JDK 8 behavior.
 *
 * <h2>Important</h2>
 *
 * This test creates files from Java compilation and annotation processing, and
 * relies on files being written to the current working directory. Since jtreg
 * currently offers no way to run each test case in its own directory, or clean
 * the test directory between test cases, we must be careful to:
 * <ul>
 *     <li>Use {@code @Execution(SAME_THREAD)} to run test cases sequentially.
 *     <li>Clean up the test directory ourselves between test cases (via
 *     {@code @BeforeEach}).
 * </ul>
 * The alternative approach would be to compile the test classes in a specified
 * working directory unique to each test case, but this is currently only
 * possible using a subprocess via {@code Task.Mode.EXEC} , and this has two
 * serious disadvantages:
 * <ul>
 *     <li>It significantly complicates compilation setup.
 *     <li>It prevents step-through debugging of the annotation processor.
 * </ul>
 */
@Execution(SAME_THREAD)
public class TestNoOverwriteJarFiles {
    private static final String LIB_SOURCE_FILE_NAME = "lib/LibClass.java";
    private static final String LIB_CLASS_FILE_NAME = "lib/LibClass.class";
    private static final String LIB_CLASS_TYPE_NAME = "lib.LibClass";

    private static final Path TEST_LIB_JAR = Path.of("lib.jar");
    private static final Path OUTPUT_CLASS_FILE = Path.of("LibClass.class");

    // Source which can only compile against the Java source in the test library.
    public static final String TARGET_SOURCE =
            """
            class TargetClass {
                static final String VALUE = lib.LibClass.NEW_FIELD;
            }
            """;

    // Not expensive to create, but conceptually a singleton.
    private static final ToolBox toolBox = new ToolBox();

    @BeforeAll
    public static void ensureEmptyTestDirectory() throws IOException {
        try (var files = Files.walk(Path.of("."), 1)) {
            // Always includes the given path as the first returned element, so skip it.
            if (files.skip(1).findFirst().isPresent()) {
                throw new IllegalStateException("Test working directory must be empty.");
            }
        }
    }

    @BeforeEach
    public void cleanUpTestDirectory() throws IOException {
        toolBox.cleanDirectory(Path.of("."));
    }

    @Test
    public void jarFileNotModifiedOrdinaryCompilation() throws IOException {
        byte[] originalJarBytes = compileTestLibJar();

        new JavacTask(toolBox)
                .sources(TARGET_SOURCE)
                .classpath(TEST_LIB_JAR)
                .run()
                .writeAll();

        // Assertion 1: The JAR is unchanged.
        assertArrayEquals(originalJarBytes, Files.readAllBytes(TEST_LIB_JAR), "Jar file was modified.");
        // Assertion 2: An output class file was written to the current directory.
        assertTrue(Files.exists(OUTPUT_CLASS_FILE), "Output class file missing.");
    }

    // As above, but the JAR is added to the source path instead (with same results).
    @Test
    public void jarFileNotModifiedForSourcePath() throws IOException {
        byte[] originalJarBytes = compileTestLibJar();

        new JavacTask(toolBox)
                .sources(TARGET_SOURCE)
                .sourcepath(TEST_LIB_JAR)
                .run()
                .writeAll();

        // Assertion 1: The JAR is unchanged.
        assertArrayEquals(originalJarBytes, Files.readAllBytes(TEST_LIB_JAR), "Jar file was modified.");
        // Assertion 2: An output class file was written to the current directory.
        assertTrue(Files.exists(OUTPUT_CLASS_FILE), "Output class file missing.");
    }

    @Test
    public void jarFileNotModifiedAnnotationProcessing() throws IOException {
        byte[] originalJarBytes = compileTestLibJar();

        new JavacTask(toolBox)
                .sources(TARGET_SOURCE)
                .classpath(TEST_LIB_JAR)
                .processors(new TestAnnotationProcessor())
                // Use "-implicit:none" to avoid writing the library class file.
                .options("-implicit:none", "-g:source,lines,vars")
                .run()
                .writeAll();

        // Assertion 1: The JAR is unchanged.
        assertArrayEquals(originalJarBytes, Files.readAllBytes(TEST_LIB_JAR), "Jar file was modified.");
        // Assertion 2: All expected output files were written to the current directory.
        assertDummyFile("DummySource.java");
        assertDummyFile("DummyClass.class");
        assertDummyFile("DummyResource.txt");
        // Assertion 3: The class file itself wasn't written (because we used "-implicit:none").
        assertFalse(Files.exists(OUTPUT_CLASS_FILE), "Unexpected class file in working directory.");
    }

    static class TestAnnotationProcessor extends JavacTestingAbstractProcessor {
        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment env) {
            // Only run this once (in the final pass), or else we get spurious failures about
            // trying to recreate file objects (not allowed during annotation processing).
            if (!env.processingOver()) {
                return false;
            }

            TypeElement libClass = elements.getTypeElement(LIB_CLASS_TYPE_NAME);
            try {
                // Note: A generated Java source file must be legal Java, but a generated class
                // file that's unreferenced will never be loaded, so can contain any bytes.
                writeFileObject(
                        filer.createSourceFile("DummySource", libClass),
                        "DummySource.java",
                        "class DummySource {}");
                writeFileObject(
                        filer.createClassFile("DummyClass", libClass),
                        "DummyClass.class",
                        "<<DummyClass Bytes>>");
                writeFileObject(
                        filer.createResource(CLASS_OUTPUT, "", "DummyResource.txt", libClass),
                        "DummyResource.txt",
                        "Dummy Resource Bytes");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return false;
        }
    }

    static void writeFileObject(FileObject file, String expectedName, String contents)
            throws IOException {
        URI fileUri = file.toUri();
        // Check that the file URI doesn't look like it is associated with a JAR.
        assertTrue(fileUri.getSchemeSpecificPart().endsWith("/" + expectedName));
        // The JAR file system would have a scheme of "jar", not "file".
        assertEquals("file", fileUri.getScheme());
        // Testing a negative is fragile, but a JAR URI would be expected to contain "jar!".
        assertFalse(fileUri.getSchemeSpecificPart().contains("jar!"));
        // Write dummy data (which should end up in the test working directory).
        try (OutputStream os = file.openOutputStream()) {
            os.write(contents.getBytes());
        }
    }

    static void assertDummyFile(String filename) throws IOException {
        Path path = Path.of(filename);
        assertTrue(Files.exists(path), "Output file missing: " + filename);
        assertTrue(Files.readString(path).contains("Dummy"), "Unexpected file contents: " + filename);
    }

    // Compiles and writes the test library JAR (LIB_JAR) into the current directory.
    static byte[] compileTestLibJar() throws IOException {
        Path libDir = Path.of("lib");
        toolBox.createDirectories(libDir);
        try {
            toolBox.writeFile(LIB_SOURCE_FILE_NAME,
                    """
                    package lib;
                    public class LibClass {
                        public static final String OLD_FIELD = "This will not compile with Target";
                    }
                    """);

            // Compile the old (broken) source and then store the class file in the JAR.
            // The class file is generated in the lib/ directory, which we delete after
            // making the JAR. This ensures that when compiling the target class, it's
            // the source file being read from the JAR,
            new JavacTask(toolBox)
                    .files(LIB_SOURCE_FILE_NAME)
                    .run()
                    .writeAll();

            // If timestamps are equal JAR file resolution of classes is ambiguous
            // (currently "last one wins"), so give the source we want to be used a
            // newer timestamp.
            Instant now = Instant.now();
            try (OutputStream jos = Files.newOutputStream(Path.of("lib.jar"))) {
                JarOutputStream jar = new JarOutputStream(jos);
                writeEntry(jar,
                        LIB_SOURCE_FILE_NAME,
                        """
                        package lib;
                        public class LibClass {
                            public static final String NEW_FIELD = "This will compile with Target";
                        }
                        """.getBytes(StandardCharsets.UTF_8),
                        now.plusSeconds(1));
                writeEntry(jar,
                        LIB_CLASS_FILE_NAME,
                        Files.readAllBytes(Path.of(LIB_CLASS_FILE_NAME)),
                        now);
                jar.close();
            }
            // Return the JAR file bytes for comparison later.
            return Files.readAllBytes(TEST_LIB_JAR);
        } finally {
            toolBox.cleanDirectory(libDir);
            toolBox.deleteFiles(libDir);
        }
    }

    // Note: JarOutputStream only writes modification time, not creation time, but
    // that's what Javac uses to determine "newness" so it's fine.
    private static void writeEntry(JarOutputStream jar, String name, byte[] bytes, Instant timestamp)
            throws IOException {
        ZipEntry e = new ZipEntry(name);
        e.setLastModifiedTime(FileTime.from(timestamp));
        jar.putNextEntry(e);
        jar.write(bytes);
        jar.closeEntry();
    }
}
