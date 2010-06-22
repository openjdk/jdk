/*
 * Copyright (c) 2007, 2010 Oracle and/or its affiliates. All rights reserved.
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
import java.util.jar.*;
import java.util.zip.*;

/*
 * Pack200Test.java
 *
 * @author ksrini
 */

/**
 * These tests are very rudimentary smoke tests to ensure that the packing
 * unpacking process works on a select set of JARs.
 */
public class Pack200Test {

    private static ArrayList <File> jarList = new ArrayList<File>();
    static final String PACKEXT = ".pack";

    /** Creates a new instance of Pack200Test */
    private Pack200Test() {}

    private static void doPackUnpack() {
        for (File in : jarList) {
            Pack200.Packer packer = Pack200.newPacker();
            Map<String, String> p = packer.properties();
            // Take the time optimization vs. space
            p.put(packer.EFFORT, "1");  // CAUTION: do not use 0.
            // Make the memory consumption as effective as possible
            p.put(packer.SEGMENT_LIMIT,"10000");
            // throw an error if an attribute is unrecognized
            p.put(packer.UNKNOWN_ATTRIBUTE, packer.ERROR);
            // ignore all JAR deflation requests to save time
            p.put(packer.DEFLATE_HINT, packer.FALSE);
            // save the file ordering of the original JAR
            p.put(packer.KEEP_FILE_ORDER, packer.TRUE);

            try {
                JarFile jarFile = new JarFile(in);

                // Write out to a jtreg scratch area
                FileOutputStream fos = new FileOutputStream(in.getName() + PACKEXT);

                System.out.print("Packing [" + in.toString() + "]...");
                // Call the packer
                packer.pack(jarFile, fos);
                jarFile.close();
                fos.close();

                System.out.print("Unpacking...");
                File f = new File(in.getName() + PACKEXT);

                // Write out to current directory, jtreg will setup a scratch area
                JarOutputStream jostream = new JarOutputStream(new FileOutputStream(in.getName()));

                // Unpack the files
                Pack200.Unpacker unpacker = Pack200.newUnpacker();
                // Call the unpacker
                unpacker.unpack(f, jostream);
                // Must explicitly close the output.
                jostream.close();
                System.out.print("Testing...");
                // Ok we have unpacked the file, lets test it.
                doTest(in);
                System.out.println("Done.");
            } catch (Exception e) {
                System.out.println("ERROR: " + e.getMessage());
                System.exit(1);
            }
        }
    }

    private static ArrayList <String> getZipFileEntryNames(ZipFile z) {
        ArrayList <String> out = new ArrayList<String>();
        for (ZipEntry ze : Collections.list(z.entries())) {
            out.add(ze.getName());
        }
        return out;
    }

    private static void doTest(File in) throws Exception {
       // make sure all the files in the original jar exists in the other
       ArrayList <String> refList = getZipFileEntryNames(new ZipFile(in));
       ArrayList <String> cmpList = getZipFileEntryNames(new ZipFile(in.getName()));

       System.out.print(refList.size() + "/" + cmpList.size() + " entries...");

       if (refList.size() != cmpList.size()) {
           throw new Exception("Missing: files ?, entries don't match");
       }

       for (String ename: refList) {
          if (!cmpList.contains(ename)) {
              throw new Exception("Does not contain : " + ename);
          }
       }
    }

    private static void doSanity(String[] args) {
        for (String s: args) {
            File f = new File(s);
            if (f.exists()) {
                jarList.add(f);
            } else {
                System.out.println("Warning: The JAR file " + f.toString() + " does not exist,");
                System.out.println("         this test requires a JDK image, this file will be skipped.");
            }
        }
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: jar1 jar2 jar3 .....");
            System.exit(1);
        }
        doSanity(args);
        doPackUnpack();
    }
}
