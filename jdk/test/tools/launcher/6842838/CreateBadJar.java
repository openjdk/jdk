/*
 * Copyright (c) 2009, Oracle and/or its affiliates. All rights reserved.
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
 * Borrowing significantly from Martin Buchholz's CorruptedZipFiles.java
 *
 * Needed a way of testing the checks for corrupt zip/jar entry in
 * inflate_file from file j2se/src/share/bin/parse_manifest.c
 * and running them with the 64-bit launcher. e.g.
 * sparcv9/bin/java -jar badjar.jar
 *
 * Run from a script driver Test6842838.sh as we want to specifically run
 * bin/sparcv9/java, the 64-bit launcher.
 *
 * So this program will create a zip file and damage it in the way
 * required to tickle this bug.
 *
 * It will cause a buffer overrun: but that will not always crash.
 * Use libumem preloaded by the script driver in order to
 * abort quickly when the overrun happens.  That makes the test
 * Solaris-specific.
 */

import java.util.*;
import java.util.zip.*;
import java.io.*;
import static java.lang.System.*;
import static java.util.zip.ZipFile.*;

public class CreateBadJar {

public static void main(String [] arguments) {

        if (arguments.length != 2) {
            throw new RuntimeException("Arguments: jarfilename entryname");
        }
        String outFile = arguments[0];
        String entryName = arguments[1];

        try {
        // If the named file doesn't exist, create it.
        // If it does, we are expecting it to contain the named entry, for
        // alteration.
        if (!new File(outFile).exists()) {
          System.out.println("Creating file " + outFile);

          // Create the requested zip/jar file.
          ZipOutputStream zos = null;
          zos = new ZipOutputStream(
            new FileOutputStream(outFile));

          ZipEntry e = new ZipEntry(entryName);
          zos.putNextEntry(e);
          for (int j=0; j<50000; j++) {
            zos.write((int)'a');
          }
          zos.closeEntry();
          zos.close();
          zos = null;
        }

        // Read it.
        int len = (int)(new File(outFile).length());
        byte[] good = new byte[len];
        FileInputStream fis = new FileInputStream(outFile);
        fis.read(good);
        fis.close();
        fis = null;

        int endpos = len - ENDHDR;
        int cenpos = u16(good, endpos+ENDOFF);
        if (u32(good, cenpos) != CENSIG) throw new RuntimeException("Where's CENSIG?");

        byte[] bad;
        bad = good.clone();

        // Corrupt it...
        int pos = findInCEN(bad, cenpos, entryName);

        // What bad stuff are we doing to it?
        // Store a 32-bit -1 in uncomp size.
        bad[pos+0x18]=(byte)0xff;
        bad[pos+0x19]=(byte)0xff;
        bad[pos+0x1a]=(byte)0xff;
        bad[pos+0x1b]=(byte)0xff;

        // Bad work complete, delete the original.
        new File(outFile).delete();

        // Write it.
        FileOutputStream fos = new FileOutputStream(outFile);
        fos.write(bad);
        fos.close();
        fos = null;

        } catch (Exception e) {
            e.printStackTrace();
        }

}

        /*
         * Scan Central Directory File Headers looking for the named entry.
         */

    static int findInCEN(byte[] bytes, int cenpos, String entryName) {
        int pos = cenpos;
        int nextPos = 0;
        String filename = null;
        do {
            if (nextPos != 0) {
                pos = nextPos;
            }
            System.out.println("entry at pos = " + pos);
            if (u32(bytes, pos) != CENSIG) throw new RuntimeException ("entry not found in CEN or premature end...");

            int csize = u32(bytes, pos+0x14);          // +0x14 1 dword csize
            int uncompsize = u32(bytes, pos+0x18);     // +0x18 1 dword uncomp size
            int filenameLength = u16(bytes, pos+0x1c); // +0x1c 1 word length of filename
            int extraLength = u16(bytes, pos+0x1e);    // +0x1e 1 world length of extra field
            int commentLength = u16(bytes, pos+0x20);  // +0x20 1 world length of file comment
            filename = new String(bytes, pos+0x2e, filenameLength); // +0x2e chars of filename
            int offset = u32(bytes, pos+0x2a);         // +0x2a chars of filename

            System.out.println("filename = " + filename + "\ncsize = " + csize +
                               " uncomp.size = " + uncompsize +" file offset = " + offset);
            nextPos =  pos + 0x2e + filenameLength + extraLength + commentLength;

        } while (!filename.equals(entryName));

        System.out.println("entry found at pos = " + pos);
        return pos;
    }

    static int u8(byte[] data, int offset) {
        return data[offset]&0xff;
    }

    static int u16(byte[] data, int offset) {
        return u8(data,offset) + (u8(data,offset+1)<<8);
    }

    static int u32(byte[] data, int offset) {
        return u16(data,offset) + (u16(data,offset+2)<<16);
    }

}

