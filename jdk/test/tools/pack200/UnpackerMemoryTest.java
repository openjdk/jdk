/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6531345
 * @summary check for unpacker memory leaks
 * @compile -XDignore.symbol.file Utils.java UnpackerMemoryTest.java
 * @run main/othervm/timeout=1200 -Xmx32m UnpackerMemoryTest
 * @author ksrini
 */

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.IOException;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

public class UnpackerMemoryTest {

    private static void createPackFile(File packFile) throws IOException {
        File tFile = new File("test.dat");
        FileOutputStream fos = null;
        PrintStream ps = null;
        String jarFileName = Utils.baseName(packFile, Utils.PACK_FILE_EXT)
                + Utils.JAR_FILE_EXT;
        JarFile jarFile = null;
        try {
            fos = new FileOutputStream(tFile);
            ps = new PrintStream(fos);
            ps.println("A quick brown fox");
            Utils.jar("cvf", jarFileName, tFile.getName());
            jarFile = new JarFile(jarFileName);
            Utils.pack(jarFile, packFile);
        } finally {
            Utils.close(ps);
            tFile.delete();
            Utils.close(jarFile);
        }
    }

    public static void main(String[] args) throws Exception {
        String name = "foo";
        File packFile = new File(name + Utils.PACK_FILE_EXT);
        createPackFile(packFile);
        if (!packFile.exists()) {
           throw new RuntimeException(packFile + " not found");
        }
        File jarOut = new File(name + ".out");
        for (int i = 0; i < 2000; i++) {
            JarOutputStream jarOS = null;
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(jarOut);
                jarOS = new JarOutputStream(fos);
                System.out.println("Unpacking[" + i + "]" + packFile);
                Utils.unpackn(packFile, jarOS);
            }  finally {
                Utils.close(jarOS);
                Utils.close(fos);
            }
        }
        Utils.cleanup();
    }
}

