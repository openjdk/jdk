/*
 * Copyright 2002 Sun Microsystems, Inc.  All Rights Reserved.
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
   @bug 4528128 6846616
   @summary Test if reading InputStream of a closed ZipFile crashes VM
   @author kladko
   */


import java.util.zip.*;
import java.io.*;
import java.util.*;

public class ReadAfterClose {
    public static void main(String[] argv) throws Exception {
        ZipFile zf = new ZipFile(new File(System.getProperty("test.src","."),"crash.jar"));
        ZipEntry zent = zf.getEntry("Test.java");
        InputStream in = zf.getInputStream(zent);
        zf.close();
        try {
            in.read();
        } catch (IOException e) {
            return;
        }
        throw new Exception("Test failed.");
    }
}
