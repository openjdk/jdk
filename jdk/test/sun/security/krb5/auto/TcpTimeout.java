/*
 * Copyright (c) 2010, 2012, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6952519
 * @compile -XDignore.symbol.file TcpTimeout.java
 * @run main/othervm TcpTimeout
 * @summary kdc_timeout is not being honoured when using TCP
 */

import java.io.*;
import java.net.ServerSocket;
import sun.security.krb5.Config;

public class TcpTimeout {
    public static void main(String[] args)
            throws Exception {

        // Set debug to grab debug output like ">>> KDCCommunication"
        System.setProperty("sun.security.krb5.debug", "true");

        // Called before new ServerSocket on p1 and p2 to make sure
        // customized nameservice is used
        KDC k = new KDC(OneKDC.REALM, OneKDC.KDCHOST, 0, true);
        int p3 = k.getPort();
        k.addPrincipal(OneKDC.USER, OneKDC.PASS);
        k.addPrincipalRandKey("krbtgt/" + OneKDC.REALM);

        // Start two listener that does not communicate, simulate timeout
        ServerSocket ss1 = null;
        ServerSocket ss2 = null;

        try {
            ss1 = new ServerSocket(0);
            ss2 = new ServerSocket(0);
            int p1 = ss1.getLocalPort();
            int p2 = ss2.getLocalPort();

            FileWriter fw = new FileWriter("alternative-krb5.conf");

            fw.write("[libdefaults]\n" +
                    "udp_preference_limit = 1\n" +
                    "max_retries = 2\n" +
                    "default_realm = " + OneKDC.REALM + "\n" +
                    "kdc_timeout = 5000\n");
            fw.write("[realms]\n" + OneKDC.REALM + " = {\n" +
                    "kdc = " + OneKDC.KDCHOST + ":" + p1 + "\n" +
                    "kdc = " + OneKDC.KDCHOST + ":" + p2 + "\n" +
                    "kdc = " + OneKDC.KDCHOST + ":" + p3 + "\n" +
                    "}\n");

            fw.close();
            System.setProperty("java.security.krb5.conf",
                    "alternative-krb5.conf");
            Config.refresh();

            System.out.println("Ports opened on " + p1 + ", " + p2 + ", " + p3);

            // The correct behavior should be:
            // 5 sec on p1, 5 sec on p1, fail
            // 5 sec on p2, 5 sec on p2, fail
            // p3 ok, p3 ok again for preauth.
            int count = 6;

            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            PrintStream oldout = System.out;
            System.setOut(new PrintStream(bo));
            Context c = Context.fromUserPass(OneKDC.USER, OneKDC.PASS, false);
            System.setOut(oldout);

            String[] lines = new String(bo.toByteArray()).split("\n");
            for (String line: lines) {
                if (line.startsWith(">>> KDCCommunication")) {
                    System.out.println(line);
                    count--;
                }
            }
            if (count != 0) {
                throw new Exception("Retry count is " + count + " less");
            }
        } finally {
            if (ss1 != null) ss1.close();
            if (ss2 != null) ss2.close();
        }
    }
}
