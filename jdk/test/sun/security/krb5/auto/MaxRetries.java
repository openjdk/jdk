/*
 * Copyright 2010 Sun Microsystems, Inc.  All Rights Reserved.
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

/*
 * @test
 * @bug 6844193
 * @run main/timeout=300 MaxRetries
 * @summary support max_retries in krb5.conf
 */

import java.io.*;
import java.security.Security;

public class MaxRetries {
    public static void main(String[] args)
            throws Exception {

        System.setProperty("sun.security.krb5.debug", "true");
        new OneKDC(null).writeJAASConf();
        System.setProperty("java.security.krb5.conf", "alternative-krb5.conf");

        // For tryLast
        Security.setProperty("krb5.kdc.bad.policy", "trylast");
        rewriteMaxRetries(4);
        test1(4000, 6);         // 1 1 1 1 2 2
        test1(4000, 2);         // 2 2

        rewriteMaxRetries(1);
        test1(1000, 3);         // 1 2 2
        test1(1000, 2);         // 2 2

        rewriteMaxRetries(-1);
        test1(5000, 4);         // 1 1 2 2
        test1(5000, 2);         // 2 2

        // For tryLess
        Security.setProperty("krb5.kdc.bad.policy", "tryless");
        rewriteMaxRetries(4);
        test1(4000, 7);         // 1 1 1 1 2 1 2
        test1(4000, 4);         // 1 2 1 2

        rewriteMaxRetries(1);
        test1(1000, 4);         // 1 2 1 2
        test1(1000, 4);         // 1 2 1 2

        rewriteMaxRetries(-1);
        test1(5000, 5);         // 1 1 2 1 2
        test1(5000, 4);         // 1 2 1 2

        rewriteUdpPrefLimit(-1, -1);    // default, no limit
        test2("UDP");

        rewriteUdpPrefLimit(10, -1);    // global rules
        test2("TCP");

        rewriteUdpPrefLimit(10, 10000); // realm rules
        test2("UDP");

        rewriteUdpPrefLimit(10000, 10); // realm rules
        test2("TCP");
    }

    /**
     * One round of test for max_retries and timeout.
     * @param timeout the expected timeout
     * @param count the expected total try
     */
    private static void test1(int timeout, int count) throws Exception {
        String timeoutTag = "timeout=" + timeout;
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        PrintStream oldout = System.out;
        System.setOut(new PrintStream(bo));
        Context c = Context.fromJAAS("client");
        System.setOut(oldout);

        String[] lines = new String(bo.toByteArray()).split("\n");
        System.out.println("----------------- TEST (" + timeout + "," +
                count + ") -----------------");
        for (String line: lines) {
            if (line.startsWith(">>> KDCCommunication")) {
                System.out.println(line);
                if (line.indexOf(timeoutTag) < 0) {
                    throw new Exception("Wrong timeout value");
                }
                count--;
            }
        }
        if (count != 0) {
            throw new Exception("Retry count is " + count + " less");
        }
    }

    /**
     * One round of test for udp_preference_limit.
     * @param proto the expected protocol used
     */
    private static void test2(String proto) throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        PrintStream oldout = System.out;
        System.setOut(new PrintStream(bo));
        Context c = Context.fromJAAS("client");
        System.setOut(oldout);

        int count = 2;
        String[] lines = new String(bo.toByteArray()).split("\n");
        System.out.println("----------------- TEST -----------------");
        for (String line: lines) {
            if (line.startsWith(">>> KDCCommunication")) {
                System.out.println(line);
                count--;
                if (line.indexOf(proto) < 0) {
                    throw new Exception("Wrong timeout value");
                }
            }
        }
        if (count != 0) {
            throw new Exception("Retry count is " + count + " less");
        }
    }

    /**
     * Set udp_preference_limit for global and realm
     */
    private static void rewriteUdpPrefLimit(int global, int realm)
            throws Exception {
        BufferedReader fr = new BufferedReader(new FileReader(OneKDC.KRB5_CONF));
        FileWriter fw = new FileWriter("alternative-krb5.conf");
        while (true) {
            String s = fr.readLine();
            if (s == null) {
                break;
            }
            if (s.startsWith("[realms]")) {
                // Reconfig global setting
                if (global != -1) {
                    fw.write("udp_preference_limit = " + global + "\n");
                }
            } else if (s.trim().startsWith("kdc = ")) {
                if (realm != -1) {
                    // Reconfig for realm
                    fw.write("    udp_preference_limit = " + realm + "\n");
                }
            }
            fw.write(s + "\n");
        }
        fr.close();
        fw.close();
        sun.security.krb5.Config.refresh();
    }

    /**
     * Set max_retries and timeout value for realm. The global value is always
     * 2 and 5000.
     * @param value max_retries and timeout/1000 for a realm, -1 means none.
     */
    private static void rewriteMaxRetries(int value) throws Exception {
        BufferedReader fr = new BufferedReader(new FileReader(OneKDC.KRB5_CONF));
        FileWriter fw = new FileWriter("alternative-krb5.conf");
        while (true) {
            String s = fr.readLine();
            if (s == null) {
                break;
            }
            if (s.startsWith("[realms]")) {
                // Reconfig global setting
                fw.write("max_retries = 2\n");
                fw.write("kdc_timeout = 5000\n");
            } else if (s.trim().startsWith("kdc = ")) {
                if (value != -1) {
                    // Reconfig for realm
                    fw.write("    max_retries = " + value + "\n");
                    fw.write("    kdc_timeout = " + (value*1000) + "\n");
                }
                // Add a bad KDC as the first candidate
                fw.write("    kdc = localhost:33333\n");
            }
            fw.write(s + "\n");
        }
        fr.close();
        fw.close();
        sun.security.krb5.Config.refresh();
    }
}
