/*
 * Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
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

/**
 * @test
 * @bug 6198547
 * @summary Test to ensure that File.createNewFile() consistently
 *      returns the same (false) value when the specified path
 *      is already present as a directory.
 */

import java.io.*;

public class AccessDenied {
    public static void main(String[] args)
                throws Exception {
        File dir = new File(System.getProperty("test.dir", "."),
                         "hugo");
        dir.deleteOnExit();
        if (!dir.mkdir()) {
            throw new Exception("Could not create directory:" + dir);
        }
        System.out.println("Created directory:" + dir);

        File file = new File(System.getProperty("test.dir", "."), "hugo");
        boolean result = file.createNewFile();
        System.out.println("CreateNewFile() for:" + file + " returned:" +
                        result);
        if (result) {
            throw new Exception(
                "Expected createNewFile() to return false but it returned true");
        }
    }
}
