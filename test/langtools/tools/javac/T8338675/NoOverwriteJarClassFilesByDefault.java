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
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.ToolBox toolbox.JarTask toolbox.JavacTask
 * @run main NoOverwriteJarClassFilesByDefault
 */

import toolbox.JavacTask;
import toolbox.ToolBox;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.ZoneId;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class NoOverwriteJarClassFilesByDefault {

    private static final String OLD_LIB_SOURCE = """
            class LibClass {
                static final String OLD_FIELD = "This will not compile with Target";
            }
            """;

    private static final String NEW_LIB_SOURCE = """
            class LibClass {
                static final String NEW_FIELD = "Only this will compile with Target";
            }
            """;

    // Target source references the field only available in the new source.
    private static final String TARGET_SOURCE = """
            class TargetClass {
                static final String VALUE = LibClass.NEW_FIELD;
            }
            """;

    private static final String LIB_SOURCE_NAME = "LibClass.java";
    private static final String LIB_CLASS_NAME = "LibClass.class";

    public static void main(String[] args) throws IOException {
        ToolBox tb = new ToolBox();

        // Compile the old (broken) source and then store the class file in the JAR.
        new JavacTask(tb)
                .sources(OLD_LIB_SOURCE)
                .run();

        // The new (fixed) source is never written to disk, so if compilation works
        // it proves it's getting it from the source file in the JAR.
        Instant olderTime = Instant.now();
        Instant newerTime = olderTime.plusSeconds(1);
        try (OutputStream jos = Files.newOutputStream(Path.of("lib.jar"))) {
            JarOutputStream jar = new JarOutputStream(jos);
            // Important: The JAR file entry order *matters* if the timestamps are
            // the same (the latter entry is used for compilation, regardless of
            // whether it's a source file or a class file). So in this test, we
            // put the one we want to use *first* with a newer timestamp, to show
            // that it's definitely the timestamp being used to select the source.
            // TODO: Should this be UTF-8, or platform default for byte encoding?
            writeEntry(jar, LIB_SOURCE_NAME, NEW_LIB_SOURCE.getBytes(), newerTime);
            // Source is newer than the (broken) compiled class, so should compile
            // from the source file in the JAR. If timestamps were not set, or set
            // equal, the test would use this class file, and fail.
            writeEntry(jar, LIB_CLASS_NAME, Files.readAllBytes(Path.of(LIB_CLASS_NAME)), olderTime);
            jar.close();
        }
        long originalLibCrc = getLibCrc();

        // This compilation only succeeds if 'NEW_FIELD' exists, which is only in
        // the source file written to the JAR, and nowhere on disk.
        // Before the fix, adding '.options("-implicit:none")' makes the test pass.
        new JavacTask(tb)
                .sources(TARGET_SOURCE)
                .classpath("lib.jar")
                .run();

        // Since compilation succeeded, we know it used NEW_LIB_SOURCE, and if it
        // wrote the class file back to the JAR (bad) then that should now have
        // different contents. Note that the modification time of the class file
        // is NOT modified, even if the JAR is updated, so we cannot test that.
        long actualLibCrc = getLibCrc();
        if (actualLibCrc != originalLibCrc) {
            throw new AssertionError("Class library contents were modified in the JAR file.");
        }
    }

    // Note: JarOutputStream only writes modification time, not creation time, but
    // that's what Javac uses to determine "newness" so it's fine.
    private static void writeEntry(JarOutputStream jar, String name, byte[] bytes, Instant timestamp) throws IOException {
        ZipEntry e = new ZipEntry(name);
        e.setLastModifiedTime(FileTime.from(timestamp));
        jar.putNextEntry(e);
        jar.write(bytes);
        jar.closeEntry();
    }

    private static long getLibCrc() throws IOException {
        try (ZipFile zipFile = new ZipFile("lib.jar")) {
            // zipFile.stream().map(NoOverwriteJarClassFilesByDefault::format).forEach(System.err::println);
            return zipFile.getEntry(LIB_CLASS_NAME).getCrc();
        }
    }

    // ---- Debug helper methods ----
    private static String format(ZipEntry e) {
        return String.format("name: %s, size: %s, modified: %s\n",
                e.getName(),
                e.getSize(),
                toLocalTime(e.getLastModifiedTime()));
    }

    private static String toLocalTime(FileTime t) {
        return t != null ? t.toInstant().atZone(ZoneId.systemDefault()).toString() : "<null>";
    }
}
