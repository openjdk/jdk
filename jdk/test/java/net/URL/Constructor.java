/*
 * Copyright (c) 1998, 2002, Oracle and/or its affiliates. All rights reserved.
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

/* This is no longer run directly. See runconstructor.sh
 *
 *
 *
 * This program tests the URL parser in the URL constructor. It
 * tries to construct a variety of valid URLs with a given context
 * (which may be null) and a variety of specs. It then compares the
 * result with an expected value.
 *
 * It expects that a data file named "urls" be available in the
 * current directory, from which it will get its testing data. The
 * format of the file is:
 *
 * URL: null
 * spec: jar:http://www.foo.com/dir1/jar.jar!/
 * expected: jar:http://www.foo.com/dir1/jar.jar!/
 *
 * where URL is the context, spec is the spec and expected is the
 * expected result. The first : must be followed by a space. Each test
 * entry should be followed by a blank line.
 */

import java.io.*;
import java.net.URL;

public class Constructor {

    public static void main(String[] args) throws Exception {
        URL url = null;
        String urls = "jar_urls";
        if (args.length > 0 && args[0] != null) {
            urls = args[0];
        }

        File f = new File(urls);
        InputStream file = new FileInputStream(f);
        BufferedReader in = new BufferedReader(new InputStreamReader(file));
        while(true) {
            String context = in.readLine();
            if (context == null) {
                break;
            }
            context = getValue(context);
            String spec = getValue(in.readLine());
            String expected = getValue(in.readLine());

            if (context.equals("null")) {
                url = new URL(spec);
            } else {
                url = new URL(new URL(context), spec);
            }
            if (!(url.toString().equals(expected))) {
                throw new RuntimeException("error for: \n\tURL:" + context +
                                           "\n\tspec: " + spec +
                                           "\n\texpected: " + expected +
                                           "\n\tactual: " + url.toString());
            } else {
                System.out.println("success for: " + url + "\n");
            }
            in.readLine();
        }
        in.close();
    }

    private static String getValue(String value) {
        return value.substring(value.indexOf(':') + 2);
    }
}
