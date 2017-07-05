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
 * @test
 * @bug 6449504
 * @run shell copyin.sh bar.jar
 * @run main B6449504 caching
 * @run main B6449504 no_caching
 * @summary REGRESSION: ZipException throws when try to read a XML file
 */

import java.io.*;
import java.net.*;

public class B6449504 {

    public static void main (String[] args) throws Exception {

        boolean caching = args[0].equals ("caching");

        String dirname = System.getProperty ("test.classes");
        File f = new File (dirname);
        dirname = f.toURI().toString();

        String u = "jar:"+ dirname + "/bar.jar";
        URL url = new URL (u+"!/DoesNotExist.txt");
        System.out.println ("url = " + url);
        JarURLConnection j1 = (JarURLConnection)url.openConnection();

        URL url2 = new URL (u+"!/test.txt");
        System.out.println ("url2 = " + url2);
        JarURLConnection j2 = (JarURLConnection)url2.openConnection();

        j1.setUseCaches (caching);
        j2.setUseCaches (caching);

        /* connecting to j2 opens the jar file but does not read it */

        j2.connect ();

        try {
            /* attempt to read a non-existing entry in the jar file
             * shows the bug, where the jar file is closed after the
             * attempt fails.
             */
            InputStream is = j1.getInputStream ();
        } catch (IOException e) {
            System.out.println ("Got expected exception from j1 ");
        }

        /* If bug present, this will fail because we think the jar
         * is ready to be read, after the connect() above, but we
         * get a ZipException because it has been closed
         */
        InputStream is = j2.getInputStream ();
        readAndClose (is);
        System.out.println ("OK");
    }

    static void readAndClose (InputStream is) throws IOException {
        while (is.read() != -1) ;
        is.close();
    }

}
