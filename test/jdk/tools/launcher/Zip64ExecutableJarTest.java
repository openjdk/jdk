/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.CRC32;

import jdk.test.lib.compiler.CompilerUtils;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import org.junit.jupiter.api.Test;
import static java.util.zip.ZipEntry.STORED;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * @test
 * @bug 8328995
 * @summary verifies that java -jar can launch a zip64 jar file
 * @library /test/lib
 * @build jdk.test.lib.process.ProcessTools jdk.test.lib.compiler.CompilerUtils
 * @run junit Zip64ExecutableJarTest
 */
public class Zip64ExecutableJarTest {
    // size in bytes to trigger representing ZIP entries as ZIP64 in the CEN
    private static final long ZIP64_SIZE_MAGICVAL = 0xFFFFFFFFL;
    private static final Path SCRATCH_DIR = Path.of(".");
    private static final String MAIN_CLASS = "foo.Bar";
    private static final String MAIN_CLASS_CONTENT = """
            package foo;
            public class Bar {
                public static void main(final String[] args) throws Exception {
                    System.out.println("hello world from " + Bar.class.getName());
                }
            }
            """;

    /*
     * Creates a ZIP64 executable JAR file and verifies that "java -jar" against
     * that file correctly launches the application class.
     */
    @Test
    public void testLaunchZip64() throws Exception {
        final Path mainClassFile = compileMainClass();
        final Path zip64JarFile = createZip64Jar(mainClassFile);
        System.out.println("created zip64 jar at " + zip64JarFile);
        // java -jar <jar>
        final OutputAnalyzer oa = ProcessTools.executeTestJava("-jar",
                zip64JarFile.toAbsolutePath().toString());
        oa.shouldHaveExitValue(0);
        oa.shouldContain("hello world from " + MAIN_CLASS);
    }

    /*
     * Compile the application class that will be packaged into the JAR file.
     */
    private static Path compileMainClass() throws Exception {
        // create foo/Bar.java
        final Path javaSrcDir = Files.createTempDirectory(SCRATCH_DIR, "8328995-java-src-");
        final Path javaSrcFile = javaSrcDir.resolve("foo").resolve("Bar.java");
        Files.createDirectories(javaSrcFile.getParent());
        Files.writeString(javaSrcFile, MAIN_CLASS_CONTENT);
        // compile foo/Bar.java into a classes dir
        final Path classesDir = Files.createTempDirectory(SCRATCH_DIR, "8328995-classes-");
        final boolean compiled = CompilerUtils.compile(javaSrcFile, classesDir);
        assertTrue(compiled, "failed to compile " + javaSrcFile);
        final Path mainClassFile = classesDir.resolve("foo").resolve("Bar.class");
        assertTrue(Files.isRegularFile(mainClassFile), "missing compiled class file at "
                + mainClassFile);
        return mainClassFile;
    }

    /*
     * Create the ZIP64 JAR file
     */
    private static Path createZip64Jar(final Path mainClassFile) throws Exception {
        final Path jarFile = Files.createTempFile(SCRATCH_DIR, "8328995-", ".jar");
        try (final SparseOutputStream sos =
                     new SparseOutputStream(new FileOutputStream(jarFile.toFile()));
             final JarOutputStream jaros = new JarOutputStream(sos)) {
            // aad an entry with a large size so that ZipOutputStream
            // creates ZIP64 data while writing the CEN
            forceCreateLargeZip64Entry(jaros);
            // now that the large entry has been written as sparse holes, we now
            // switch to writing the actual bytes for the rest of the entries
            sos.sparse = false;
            // add the main class
            final JarEntry mainClassEntry = new JarEntry(MAIN_CLASS.replace('.', '/') + ".class");
            jaros.putNextEntry(mainClassEntry);
            jaros.write(Files.readAllBytes(mainClassFile));
            jaros.closeEntry();
            // finally add the META-INF/MANIFEST.MF
            final Manifest manifest = new Manifest();
            final Attributes mainAttributes = manifest.getMainAttributes();
            mainAttributes.putValue("Manifest-Version", "1.0");
            mainAttributes.putValue("Main-Class", MAIN_CLASS);
            final JarEntry manifestEntry = new JarEntry("META-INF/MANIFEST.MF");
            jaros.putNextEntry(manifestEntry);
            manifest.write(jaros);
            jaros.closeEntry();
        }
        return jarFile;
    }

    private static void forceCreateLargeZip64Entry(final JarOutputStream jaros) throws IOException {
        final JarEntry entry = new JarEntry("entry-1");
        entry.setMethod(STORED); // no need to deflate, we want the entry to be large sized
        entry.setSize(ZIP64_SIZE_MAGICVAL);
        // start with a dummy value, we will update it once the entry content is written
        entry.setCrc(0);
        jaros.putNextEntry(entry);
        long numWritten = 0;
        final byte[] entryContent = new byte[102400];
        final CRC32 crc = new CRC32();
        // keep writing the entry content till we reach the
        // desired size that represents a zip64 entry
        while (numWritten < ZIP64_SIZE_MAGICVAL) {
            final long remaining = ZIP64_SIZE_MAGICVAL - numWritten;
            final int len = remaining < entryContent.length
                    ? (int) remaining
                    : entryContent.length;
            jaros.write(entryContent, 0, len);
            numWritten += len;
            crc.update(entryContent, 0, len);
        }
        entry.setCrc(crc.getValue()); // update the CRC in the entry
        jaros.closeEntry();
    }

    /*
     * An OutputStream which writes holes through its write() methods, until it is
     * instructed to write the actual bytes. This implementation allows us to create large
     * ZIP files without actually consuming large disk space.
     * Instances of this class should be passed directly to the ZipOutputStream/JarOutputStream
     * constructor, without any buffering, to allow for the implementation to correctly keep
     * track of when to and when not to write holes.
     */
    private static class SparseOutputStream extends FilterOutputStream {
        private final FileChannel channel;
        private boolean sparse = true; // if true then contents will be written as sparse holes
        private long position;

        public SparseOutputStream(FileOutputStream fos) {
            super(fos);
            this.channel = fos.getChannel();
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            position += len;
            if (sparse) {
                channel.position(position);
            } else {
                // regular write
                out.write(b, off, len);
            }
        }

        @Override
        public void write(int b) throws IOException {
            position++;
            if (sparse) {
                channel.position(position);
            } else {
                // regular write
                out.write(b);
            }
        }
    }
}
