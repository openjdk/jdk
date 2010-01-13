/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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

import java.io.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import sun.security.krb5.Config;

public class BadKdc {

    // Matches the krb5 debug output:
    // >>> KDCCommunication: kdc=kdc.rabbit.hole UDP:14319, timeout=2000,...
    //                                               ^ kdc#         ^ timeout
    static final Pattern re = Pattern.compile(
            ">>> KDCCommunication: kdc=kdc.rabbit.hole UDP:(\\d)...., " +
            "timeout=(\\d)000,");
    public static void go(int[]... expected)
            throws Exception {
        System.setProperty("sun.security.krb5.debug", "true");

        // Make sure KDCs' ports starts with 1 and 2 and 3,
        // useful for checking debug output.
        int p1 = 10000 + new java.util.Random().nextInt(10000);
        int p2 = 20000 + new java.util.Random().nextInt(10000);
        int p3 = 30000 + new java.util.Random().nextInt(10000);

        FileWriter fw = new FileWriter("alternative-krb5.conf");

        fw.write("[libdefaults]\n" +
                "default_realm = " + OneKDC.REALM + "\n" +
                "kdc_timeout = 2000\n");
        fw.write("[realms]\n" + OneKDC.REALM + " = {\n" +
                "kdc = " + OneKDC.KDCHOST + ":" + p1 + "\n" +
                "kdc = " + OneKDC.KDCHOST + ":" + p2 + "\n" +
                "kdc = " + OneKDC.KDCHOST + ":" + p3 + "\n" +
                "}\n");

        fw.close();
        System.setProperty("java.security.krb5.conf", "alternative-krb5.conf");
        Config.refresh();

        // Turn on k3 only
        KDC k3 = on(p3);

        test(expected[0]);
        test(expected[1]);
        Config.refresh();
        test(expected[2]);

        k3.terminate(); // shutdown k3
        on(p2);         // k2 is on
        test(expected[3]);
        on(p1);         // k1 and k2 is on
        test(expected[4]);
    }

    private static KDC on(int p) throws Exception {
        KDC k = new KDC(OneKDC.REALM, OneKDC.KDCHOST, p, true);
        k.addPrincipal(OneKDC.USER, OneKDC.PASS);
        k.addPrincipalRandKey("krbtgt/" + OneKDC.REALM);
        return k;
    }

    /**
     * One round of test for max_retries and timeout.
     * @param timeout the expected timeout
     * @param expected the expected kdc# timeout kdc# timeout...
     */
    private static void test(int... expected) throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        PrintStream oldout = System.out;
        System.setOut(new PrintStream(bo));
        Context c = Context.fromUserPass(OneKDC.USER, OneKDC.PASS, false);
        System.setOut(oldout);

        String[] lines = new String(bo.toByteArray()).split("\n");
        System.out.println("----------------- TEST -----------------");
        int count = 0;
        for (String line: lines) {
            Matcher m = re.matcher(line);
            if (m.find()) {
                System.out.println(line);
                if (Integer.parseInt(m.group(1)) != expected[count++] ||
                        Integer.parseInt(m.group(2)) != expected[count++]) {
                    throw new Exception("Fail here");
                }
            }
        }
        if (count != expected.length) {
            throw new Exception("Less rounds");
        }
    }
}
