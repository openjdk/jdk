/*
 * Copyright (c) 2010, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6844193
 * @compile -XDignore.symbol.file MaxRetries.java
 * @run main/othervm/timeout=300 MaxRetries
 * @summary support max_retries in krb5.conf
 */

import javax.security.auth.login.LoginException;
import java.io.*;
import java.net.DatagramSocket;
import java.security.Security;

public class MaxRetries {

    static int idlePort = -1;
    static CommMatcher cm = new CommMatcher();

    public static void main(String[] args)
            throws Exception {

        System.setProperty("sun.security.krb5.debug", "true");
        OneKDC kdc = new OneKDC(null).writeJAASConf();

        // An idle UDP socket to prevent PortUnreachableException
        DatagramSocket ds = new DatagramSocket();
        idlePort = ds.getLocalPort();

        cm.addPort(idlePort);
        cm.addPort(kdc.getPort());

        System.setProperty("java.security.krb5.conf", "alternative-krb5.conf");

        Security.setProperty("krb5.kdc.bad.policy", "trylast");

        // We always make the real timeout to be 1 second
        BadKdc.setRatio(0.25f);
        rewriteMaxRetries(4);

        // Explanation: In this case, max_retries=4 and timeout=4s.
        // For AS-REQ without preauth, we will see 4 4s timeout on kdc#1
        // ("a4" repeat 4 times), and one 4s timeout on kdc#2 ("b4"). For
        // AS-REQ with preauth, one 4s timeout on kdc#2 (second "b4").
        // we tolerate 4 real timeout on kdc#2, so make it "(b4){2,6}".
        test1("a4a4a4a4b4b4", "a4a4a4a4(b4){2,6}");
        test1("b4b4", "(b4){2,6}");

        BadKdc.setRatio(1f);
        rewriteMaxRetries(1);
        // Explanation: Since max_retries=1 only, we could fail in 1st or 2nd
        // AS-REQ to kdc#2.
        String actual = test1("a1b1b1", "(a1b1b1|a1b1x|a1b1b1x)");
        if (actual.endsWith("x")) {
            // If 1st attempt fails, all bads are back available
            test1("a1b1b1", "(a1b1b1|a1b1x|a1b1b1x)");
        } else {
            test1("b1b1", "(b1b1|b1x|b1b1x)");
        }

        BadKdc.setRatio(0.2f);
        rewriteMaxRetries(-1);
        test1("a5a5a5b5b5", "a5a5a5(b5){2,4}");
        test1("b5b5", "(b5){2,4}");

        BadKdc.setRatio(0.25f);
        Security.setProperty("krb5.kdc.bad.policy",
                "tryless:1,1000");
        rewriteMaxRetries(4);
        test1("a4a4a4a4b4a4b4", "a4a4a4a4(b4){1,3}a4(b4){1,3}");
        test1("a4b4a4b4", "a4(b4){1,3}a4(b4){1,3}");

        BadKdc.setRatio(1f);
        rewriteMaxRetries(1);
        actual = test1("a1b1a1b1", "(a1b1|a1b1x|a1b1a1b1|a1b1a1b1x)");
        if (actual.endsWith("x")) {
            test1("a1b1a1b1", "(a1b1|a1b1x|a1b1a1b1|a1b1a1b1x)");
        } else {
            test1("a1b1a1b1", "(a1b1|a1b1x|a1b1a1b1|a1b1a1b1x)");
        }

        BadKdc.setRatio(.2f);
        rewriteMaxRetries(-1);
        test1("a5a5a5b5a5b5", "a5a5a5(b5){1,2}a5(b5){1,2}");
        test1("a5b5a5b5", "a5(b5){1,2}a5(b5){1,2}");

        BadKdc.setRatio(1f);
        rewriteMaxRetries(2);
        if (BadKdc.toReal(2000) > 1000) {
            // Explanation: if timeout is longer than 1s in tryLess,
            // we will see "a1" at 2nd kdc#1 access
            test1("a2a2b2a1b2", "a2a2(b2){1,2}a1(b2){1,2}");
        } else {
            test1("a2a2b2a2b2", "a2a2(b2){1,2}a2(b2){1,2}");
        }

        BadKdc.setRatio(1f);

        rewriteUdpPrefLimit(-1, -1);    // default, no limit
        test2("UDP");

        rewriteUdpPrefLimit(10, -1);    // global rules
        test2("TCP");

        rewriteUdpPrefLimit(10, 10000); // realm rules
        test2("UDP");

        rewriteUdpPrefLimit(10000, 10); // realm rules
        test2("TCP");

        ds.close();
    }

    /**
     * One round of test for max_retries and timeout.
     *
     * @param exact the expected exact match, where no timeout
     *              happens for real KDCs
     * @param relaxed the expected relaxed match, where some timeout
     *                could happen for real KDCs
     * @return the actual result
     */
    private static String test1(String exact, String relaxed) throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        PrintStream oldout = System.out;
        System.setOut(new PrintStream(bo));
        boolean failed = false;
        long start = System.nanoTime();
        try {
            Context c = Context.fromJAAS("client");
        } catch (LoginException e) {
            failed = true;
        }
        System.setOut(oldout);

        String[] lines = new String(bo.toByteArray()).split("\n");
        System.out.println("----------------- TEST (" + exact
                + ") -----------------");

        // Result, a series of timeout + kdc#
        StringBuilder sb = new StringBuilder();
        for (String line: lines) {
            if (cm.match(line)) {
                System.out.println(line);
                sb.append(cm.kdc()).append(cm.timeout());
            }
        }
        if (failed) {
            sb.append("x");
        }
        System.out.println("Time: " + (System.nanoTime() - start) / 1000000000d);
        String actual = sb.toString();
        System.out.println("Actual: " + actual);
        if (actual.equals(exact)) {
            System.out.println("Exact match: " + exact);
        } else if (actual.matches(relaxed)) {
            System.out.println("!!!! Tolerant match: " + relaxed);
        } else {
            throw new Exception("Match neither " + exact + " nor " + relaxed);
        }
        return actual;
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
            if (cm.match(line)) {
                System.out.println(line);
                count--;
                if (!cm.protocol().equals(proto)) {
                    throw new Exception("Wrong protocol value");
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
                fw.write("kdc_timeout = 5000\n");
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
     * 3 and 5000.
     *
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
                fw.write("max_retries = 3\n");
                fw.write("kdc_timeout = " + BadKdc.toReal(5000) + "\n");
            } else if (s.trim().startsWith("kdc = ")) {
                if (value != -1) {
                    // Reconfig for realm
                    fw.write("    max_retries = " + value + "\n");
                    fw.write("    kdc_timeout = " + BadKdc.toReal(value*1000) + "\n");
                }
                // Add a bad KDC as the first candidate
                fw.write("    kdc = localhost:" + idlePort + "\n");
            }
            fw.write(s + "\n");
        }
        fr.close();
        fw.close();
        sun.security.krb5.Config.refresh();
    }
}
