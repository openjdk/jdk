/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
/*
 * @test
 * @bug 8029646
 * @summary tests that native unpacker produces the same result as Java one
 * @compile -XDignore.symbol.file Utils.java PackTestZip64.java
 * @run main PackTestZip64
 * @author kizune
 */

public class PackTestZip64 {
    public static void main(String... args) throws Exception {
        testPacking();
        Utils.cleanup();
    }

    // 1KB buffer is enough to copy jar content
    private static final byte[] BUFFER = new byte[1024];

    static void testPacking() throws IOException {
        // make a copy of the test specimen to local directory
        File testFile = new File("tools_java.jar");
        // Add a large number of small files to the golden jar
        generateLargeJar(testFile, Utils.locateJar("golden.jar"));

        List<String> cmdsList = new ArrayList<>();

        // Repack file to get the Java-based result
        cmdsList.add(Utils.getPack200Cmd());
        cmdsList.add("--repack");
        cmdsList.add(testFile.getName());
        Utils.runExec(cmdsList);
        cmdsList.clear();

        // Pack file with pack200 and unpack in with unpack200
        File packedFile = new File("tools.pack.gz");
        cmdsList.add(Utils.getPack200Cmd());
        cmdsList.add(packedFile.getName());
        cmdsList.add(testFile.getName());
        Utils.runExec(cmdsList);
        cmdsList.clear();

        File unpackedFile = new File("tools_native.jar");
        cmdsList.add(Utils.getUnpack200Cmd());
        cmdsList.add(packedFile.getName());
        cmdsList.add(unpackedFile.getName());
        Utils.runExec(cmdsList);

        // Compare files binary
        compareTwoFiles(testFile, unpackedFile);

        // Cleaning up generated files
        testFile.delete();
        packedFile.delete();
        unpackedFile.delete();
    }

    static void compareTwoFiles(File src, File dst) throws IOException {
        if (!src.exists()) {
            throw new IOException("File " + src.getName() + " does not exist!");
        }

        if(!dst.exists()) {
            throw new IOException("File " + dst.getName() + " does not exist!");
        }

        BufferedInputStream srcis, dstis;
        srcis = new BufferedInputStream(new FileInputStream(src));
        dstis = new BufferedInputStream(new FileInputStream(dst));

        int s = 0, d, pos = 0;
        while (s != -1) { // Checking of just one result for EOF is enough
            s = srcis.read();
            d = dstis.read();

            if (s != d) {
                throw new IOException("Files are differ starting at position: "
                + Integer.toHexString(pos));
            }

            pos++;
        }

        srcis.close();
        dstis.close();
    }

    static void generateLargeJar(File result, File source) throws IOException {
        if (result.exists()) {
            result.delete();
        }

        try (JarOutputStream copyTo = new JarOutputStream(new FileOutputStream(result));
             JarFile srcJar = new JarFile(source)) {

            for (JarEntry je : Collections.list(srcJar.entries())) {
                copyTo.putNextEntry(je);
                if (!je.isDirectory()) {
                    copyStream(srcJar.getInputStream(je), copyTo);
                }
                copyTo.closeEntry();
            }

            int many = Short.MAX_VALUE * 2 + 2;

            for (int i = 0 ; i < many ; i++) {
                JarEntry e = new JarEntry("F-" + i + ".txt");
                copyTo.putNextEntry(e);
            }
            copyTo.flush();
            copyTo.close();
        }
    }

    static void copyStream(InputStream in, OutputStream out) throws IOException {
        int bytesRead;
        while ((bytesRead = in.read(BUFFER))!= -1) {
            out.write(BUFFER, 0, bytesRead);
        }
    }
}
