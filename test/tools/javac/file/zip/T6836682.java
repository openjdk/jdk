/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6836682
 * @summary JavacFileManager handles zip64 archives (64K+ entries and large file support)
 * @compile  -XDignore.symbol.file T6836682.java Utils.java
 * @run main T6836682
 */
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

public class T6836682 {

    private static final long GIGA = 1024 * 1024 * 1024;

    static void createLargeFile(File outFile, long minlength) throws IOException {
        FileOutputStream fos = null;
        BufferedOutputStream bos = null;
        byte[] buffer = new byte[Short.MAX_VALUE * 2];
        try {
            fos = new FileOutputStream(outFile);
            bos = new BufferedOutputStream(fos);
            long count = minlength / ( Short.MAX_VALUE * 2)  + 1;
            for (long i = 0 ; i < count ; i++) {
                bos.write(buffer);
            }
        } finally {
            Utils.close(bos);
            Utils.close(fos);
        }
        if (outFile.length() < minlength) {
            throw new RuntimeException("could not create large file " + outFile.getAbsolutePath());
        }
    }

    static void createJarWithLargeFile(File jarFile, File javaFile,
            long minlength) throws IOException {
        Utils.createClassFile(javaFile, null, true);
        File largeFile = new File("large.data");
        createLargeFile(largeFile, minlength);
        String[] jarArgs = {
            "0cvf",
            jarFile.getAbsolutePath(),
            largeFile.getName(),
            Utils.getClassFileName(javaFile)
        };
        Utils.jarTool.run(jarArgs);
        // deleted to prevent accidental linkage
        new File(Utils.getClassFileName(javaFile)).delete();
    }

    static void createLargeJar(File jarFile, File javaFile) throws IOException {
        File classFile = new File(Utils.getClassFileName(javaFile));
        Utils.createClassFile(javaFile, null, true);
        JarOutputStream jos = null;
        FileInputStream fis = null;
        try {
            jos = new JarOutputStream(new FileOutputStream(jarFile));

            for (int i = 0; i < Short.MAX_VALUE * 2 + 10; i++) {
                jos.putNextEntry(new ZipEntry("X" + i + ".txt"));
            }
            jos.putNextEntry(new ZipEntry(classFile.getName()));
            fis = new FileInputStream(classFile);
            Utils.copyStream(fis, jos);
        } finally {
            Utils.close(jos);
            Utils.close(fis);
        }
        // deleted to prevent accidental linkage
        new File(Utils.getClassFileName(javaFile)).delete();
    }

    // a jar with entries exceeding 64k + a class file for the existential test
    public static void testLargeJar(String... args) throws IOException {
        File largeJar = new File("large.jar");
        File javaFile = new File("Foo.java");
        createLargeJar(largeJar, javaFile);

        File testFile = new File("Bar.java");
        try {
            Utils.createJavaFile(testFile, javaFile);
            if (!Utils.compile("-doe", "-verbose", "-cp",
                    largeJar.getAbsolutePath(), testFile.getAbsolutePath())) {
                throw new IOException("test failed");
            }
        } finally {
            Utils.deleteFile(largeJar);
        }
    }

    // a jar with an enormous file + a class file for the existential test
    public static void testHugeJar(String... args) throws IOException {
        final File largeJar = new File("huge.jar");
        final File javaFile = new File("Foo.java");

        final Path path = largeJar.getAbsoluteFile().getParentFile().toPath();
        final long available = Files.getFileStore(path).getUsableSpace();
        final long MAX_VALUE = 0xFFFF_FFFFL;

        final long absolute  = MAX_VALUE + 1L;
        final long required  = (long)(absolute * 1.1); // pad for sundries
        System.out.println("\tavailable: " + available / GIGA + " GB");
        System.out.println("\required: " + required / GIGA + " GB");

        if (available > required) {
            createJarWithLargeFile(largeJar, javaFile, absolute);
            File testFile = new File("Bar.java");
            Utils.createJavaFile(testFile, javaFile);
            try {
                if (!Utils.compile("-doe", "-verbose", "-cp",
                        largeJar.getAbsolutePath(), testFile.getAbsolutePath())) {
                    throw new IOException("test failed");
                }
            } finally {
                Utils.deleteFile(largeJar);
            }
        } else {
            System.out.println("Warning: test passes vacuously, requirements exceeds available space");
        }
    }

    public static void main(String... args) throws IOException {
        testLargeJar();
        testHugeJar();
    }
}
