/*
 * Copyright (c) 2004, Oracle and/or its affiliates. All rights reserved.
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


/* Testcase that does a defineClass with a NULL name on HelloWorld.class */

import java.io.*;

public class DefineClass extends ClassLoader {
    public static void main(String args[]) {
        DefineClass t = new DefineClass();
        t.run(args);
    }
    public void run(String args[]) {
        Class n;
        byte b[] = new byte[10000];
        int len = 0;
        String cdir;
        String cfile;

        /* Class is found here: */
        cdir = System.getProperty("test.classes", ".");
        cfile = cdir + java.io.File.separator + "HelloWorld.class";

        try {
            /* Construct byte array with complete class image in it. */
            FileInputStream fis = new FileInputStream(cfile);
            int nbytes;
            do {
                nbytes = fis.read(b, len, b.length-len);
                if ( nbytes > 0 ) {
                    len += nbytes;
                }
            } while ( nbytes > 0 );
        } catch ( Throwable x ) {
            System.err.println("Cannot find " + cfile);
            x.printStackTrace();
        }

        /* Define the class with null for the name */
        n = defineClass(null, b, 0, len);

        /* Try to create an instance of it */
        try {
            n.newInstance();
        } catch ( Throwable x ) {
            x.printStackTrace();
        }
    }
}
