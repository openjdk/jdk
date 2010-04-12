/*
 * Copyright 1999-2003 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/* @test
   @bug 4241361 4842702 4985614 6646605 5032358 6923692
   @summary Make sure we can read a zip file.
 */

import java.io.*;
import java.util.zip.*;

public class ReadZip {
    private static void unreached (Object o)
        throws Exception
    {
        // Should never get here
        throw new Exception ("Expected exception was not thrown");
    }

    public static void main(String args[]) throws Exception {
        ZipFile zf = new ZipFile(new File(System.getProperty("test.src", "."),
                                          "input.zip"));

        // Make sure we throw NPE on null objects
        try { unreached (zf.getEntry(null)); }
        catch (NullPointerException e) {}

        try { unreached (zf.getInputStream(null)); }
        catch (NullPointerException e) {}

        ZipEntry ze = zf.getEntry("ReadZip.java");
        if (ze == null) {
            throw new Exception("cannot read from zip file");
        }
        zf.close();

        // Make sure we can read the zip file that has some garbage
        // bytes padded at the end.
        FileInputStream fis = new FileInputStream(
                                   new File(System.getProperty("test.src", "."),
                                            "input.zip"));
        File newZip = new File(System.getProperty("test.dir", "."),
                               "input2.zip");
        FileOutputStream fos = new FileOutputStream(newZip);

        byte[] buf = new byte[1024];
        int n = 0;
        while ((n = fis.read(buf)) != -1) {
            fos.write(buf, 0, n);
        }
        fis.close();
        // pad some bytes
        fos.write(1); fos.write(3); fos.write(5); fos.write(7);
        fos.close();
        try {
            zf = new ZipFile(newZip);
            ze = zf.getEntry("ReadZip.java");
            if (ze == null) {
                throw new Exception("cannot read from zip file");
            }
        } finally {
            zf.close();
            newZip.delete();
        }

        // Read zip file comment
        try {

            ZipOutputStream zos = new ZipOutputStream(
                                      new FileOutputStream(newZip));
            ze = new ZipEntry("ZipEntry");
            zos.putNextEntry(ze);
            zos.write(1); zos.write(2); zos.write(3); zos.write(4);
            zos.closeEntry();
            zos.setComment("This is the comment for testing");
            zos.close();

            zf = new ZipFile(newZip);
            ze = zf.getEntry("ZipEntry");
            if (ze == null)
                throw new Exception("cannot read entry from zip file");
            if (!"This is the comment for testing".equals(zf.getComment()))
                throw new Exception("cannot read comment from zip file");
        } finally {
            zf.close();
            newZip.delete();
        }

        // Throw a FNF exception when read a non-existing zip file
        try { unreached (new ZipFile(
                             new File(System.getProperty("test.src", "."),
                                     "input"
                                      + String.valueOf(new java.util.Random().nextInt())
                                      + ".zip")));
        } catch (FileNotFoundException fnfe) {}
    }
}
