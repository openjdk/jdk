/*
 * Copyright (c) 1998, 2001, Oracle and/or its affiliates. All rights reserved.
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

/* @test
   @bug 4113217
   @summary Test File.isDirectory on block device
 */

import java.io.*;
import java.util.*;

public class BlockIsDirectory {
    public static void main( String args[] ) throws Exception {
        String osname = System.getProperty("os.name");
        if (osname.equals("SunOS")) {
            File dir = new File("/dev/dsk");
            String dirList[] = dir.list();

            File aFile = new File( "/dev/dsk/" + dirList[0] );

            boolean result = aFile.isDirectory();
            if (result == true)
                throw new RuntimeException(
                    "IsDirectory returns true for block device.");
        }
        if (osname.equals("Linux")) {
            File dir = new File("/dev/ide0");
            if (dir.exists()) {
                boolean result = dir.isDirectory();
                if (result == true)
                    throw new RuntimeException(
                        "IsDirectory returns true for block device.");
            }
            dir = new File("/dev/scd0");
            if (dir.exists()) {
                boolean result = dir.isDirectory();
                if (result == true)
                    throw new RuntimeException(
                        "IsDirectory returns true for block device.");
            }
        }
    }
}
