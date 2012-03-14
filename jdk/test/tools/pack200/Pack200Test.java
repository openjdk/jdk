/*
 * Copyright (c) 2007, 2012, Oracle and/or its affiliates. All rights reserved.
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


import java.util.*;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.jar.*;

 /*
  * @test
  * @bug 6521334 6712743
  * @summary check for memory leaks, test general packer/unpacker functionality\
  *          using native and java unpackers
  * @compile -XDignore.symbol.file Utils.java Pack200Test.java
  * @run main/othervm/timeout=1200 -Xmx512m Pack200Test
  * @author ksrini
  */

/**
 * Tests the packing/unpacking via the APIs.
 */
public class Pack200Test {

    private static ArrayList <File> jarList = new ArrayList<File>();
    static final MemoryMXBean mmxbean = ManagementFactory.getMemoryMXBean();
    static final long m0 = getUsedMemory();
    static final int LEAK_TOLERANCE = 21000; // OS and GC related variations.

    /** Creates a new instance of Pack200Test */
    private Pack200Test() {}

    static long getUsedMemory() {
        mmxbean.gc();
        mmxbean.gc();
        mmxbean.gc();
        return mmxbean.getHeapMemoryUsage().getUsed()/1024;
    }

    private static void leakCheck() throws Exception {
        long diff = getUsedMemory() - m0;
        System.out.println("  Info: memory diff = " + diff + "K");
        if ( diff  > LEAK_TOLERANCE) {
            throw new Exception("memory leak detected " + diff);
        }
    }

    private static void doPackUnpack() {
        for (File in : jarList) {
            JarOutputStream javaUnpackerStream = null;
            JarOutputStream nativeUnpackerStream = null;
            JarFile jarFile = null;
            try {
                jarFile = new JarFile(in);

                // Write out to a jtreg scratch area
                File packFile = new File(in.getName() + Utils.PACK_FILE_EXT);

                System.out.println("Packing [" + in.toString() + "]");
                // Call the packer
                Utils.pack(jarFile, packFile);
                jarFile.close();
                leakCheck();

                System.out.println("  Unpacking using java unpacker");
                File javaUnpackedJar = new File("java-" + in.getName());
                // Write out to current directory, jtreg will setup a scratch area
                javaUnpackerStream = new JarOutputStream(
                        new FileOutputStream(javaUnpackedJar));
                Utils.unpackj(packFile, javaUnpackerStream);
                javaUnpackerStream.close();
                System.out.println("  Testing...java unpacker");
                leakCheck();
                // Ok we have unpacked the file, lets test it.
                Utils.doCompareVerify(in.getAbsoluteFile(), javaUnpackedJar);

                System.out.println("  Unpacking using native unpacker");
                // Write out to current directory
                File nativeUnpackedJar = new File("native-" + in.getName());
                nativeUnpackerStream = new JarOutputStream(
                        new FileOutputStream(nativeUnpackedJar));
                Utils.unpackn(packFile, nativeUnpackerStream);
                nativeUnpackerStream.close();
                System.out.println("  Testing...native unpacker");
                leakCheck();
                // the unpackers (native and java) should produce identical bits
                // so we use use bit wise compare, the verification compare is
                // very expensive wrt. time.
                Utils.doCompareBitWise(javaUnpackedJar, nativeUnpackedJar);
                System.out.println("Done.");
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                Utils.close(nativeUnpackerStream);
                Utils.close(javaUnpackerStream);
                Utils.close((Closeable) jarFile);
            }
        }
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        // select the jars carefully, adding more jars will increase the
        // testing time, especially for jprt.
        jarList.add(Utils.locateJar("tools.jar"));
        jarList.add(Utils.locateJar("rt.jar"));
        jarList.add(Utils.locateJar("golden.jar"));
        System.out.println(jarList);
        doPackUnpack();
    }
}
