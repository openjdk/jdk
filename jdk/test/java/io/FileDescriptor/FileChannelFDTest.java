/*
 * Copyright (c) 2006, Oracle and/or its affiliates. All rights reserved.
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

/**
 *
 * @test
 * @bug 6322678
 * @summary Test for making sure that fd is closed during
 *          finalization of a stream, when an associated
 *          file channel is not available
 */

import java.io.*;
import java.nio.*;
import java.nio.channels.*;

public class FileChannelFDTest {

    static byte data[] = new byte[] {48, 49, 50, 51, 52, 53, 54, 55, 56, 57,};
    static String inFileName = "fd-in-test.txt";
    static String outFileName = "fd-out-test.txt";
    static File inFile;
    static File outFile;

    private static void writeToInFile() throws IOException {
        FileOutputStream out = new FileOutputStream(inFile);
        out.write(data);
        out.close();
    }

    public static void main(String[] args)
                throws Exception {

        inFile= new File(System.getProperty("test.dir", "."),
                        inFileName);
        inFile.createNewFile();
        inFile.deleteOnExit();
        writeToInFile();

        outFile  = new File(System.getProperty("test.dir", "."),
                        outFileName);
        outFile.createNewFile();
        outFile.deleteOnExit();

        doFileChannel();
    }

     private static void doFileChannel() throws Exception {

        FileInputStream fis = new FileInputStream(inFile);
        FileDescriptor fd = fis.getFD();
        FileChannel fc = fis.getChannel();
        System.out.println("Created fis:" + fis);

        /**
         * Encourage the GC
         */
        fis = null;
        fc = null;
        System.gc();
        Thread.sleep(500);

        if (fd.valid()) {
            throw new Exception("Finalizer either didn't run --" +
                "try increasing the Thread's sleep time after System.gc();" +
                "or the finalizer didn't close the file");
        }

        System.out.println("File Closed successfully");
        System.out.println();
  }
}
