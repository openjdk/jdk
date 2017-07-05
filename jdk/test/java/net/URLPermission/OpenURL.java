/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @bug 8029354
 * @run main/othervm OpenURL
 */

import java.net.*;
import java.io.*;

public class OpenURL {

    public static void main (String[] args) throws Exception {

        System.setSecurityManager(new SecurityManager());

        try {
            URL url = new URL ("http://joe@127.0.0.1/a/b");
            HttpURLConnection urlc = (HttpURLConnection)url.openConnection();
            InputStream is = urlc.getInputStream();
            // error will throw exception other than SecurityException
        } catch (SecurityException e) {
            System.out.println("OK");
        }
    }
}
