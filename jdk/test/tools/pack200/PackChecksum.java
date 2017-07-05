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
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/*
 * @test
 * @bug 8000650
 * @summary unpack200.exe should check gzip crc
 * @compile -XDignore.symbol.file Utils.java PackChecksum.java
 * @run main PackChecksum
 * @author kizune
 */
public class PackChecksum {

    public static void main(String... args) throws Exception {
        testChecksum();
    }

    static void testChecksum() throws Exception {

        // Create a fresh .jar file
        File testFile = new File("src_tools.jar");
        File testPack = new File("src_tools.pack.gz");
        generateJar(testFile);
        List<String> cmdsList = new ArrayList<>();

        // Create .pack file
        cmdsList.add(Utils.getPack200Cmd());
        cmdsList.add(testPack.getName());
        cmdsList.add(testFile.getName());
        Utils.runExec(cmdsList);

        // Mess up with the checksum of the packed file
        RandomAccessFile raf = new RandomAccessFile(testPack, "rw");
        raf.seek(raf.length() - 8);
        int val = raf.readInt();
        val = Integer.MAX_VALUE - val;
        raf.seek(raf.length() - 8);
        raf.writeInt(val);
        raf.close();

        File dstFile = new File("dst_tools.jar");
        cmdsList.clear();
        cmdsList.add(Utils.getUnpack200Cmd());
        cmdsList.add(testPack.getName());
        cmdsList.add(dstFile.getName());

        boolean passed = false;
        try {
            Utils.runExec(cmdsList);
        } catch (RuntimeException re) {
            // unpack200 should exit with non-zero exit code
            passed = true;
        }

        // tidy up
        if (testFile.exists()) testFile.delete();
        if (testPack.exists()) testPack.delete();
        if (dstFile.exists()) dstFile.delete();
        if (!passed) {
            throw new Exception("File with incorrect CRC unpacked without the error.");
        }
    }

    static void generateJar(File result) throws IOException {
        if (result.exists()) {
            result.delete();
        }

        try (JarOutputStream output = new JarOutputStream(new FileOutputStream(result)); ) {
            for (int i = 0 ; i < 100 ; i++) {
                JarEntry e = new JarEntry("F-" + i + ".txt");
                output.putNextEntry(e);
            }
            output.flush();
            output.close();
        }
    }

}
