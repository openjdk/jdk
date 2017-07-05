/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
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

import java.io.*;
import java.net.BindException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.security.auth.login.LoginException;
import sun.security.krb5.Asn1Exception;
import sun.security.krb5.Config;

public class BadKdc {

    // Matches the krb5 debug output:
    // >>> KDCCommunication: kdc=kdc.rabbit.hole UDP:14319, timeout=2000,...
    //                                               ^ kdc#         ^ timeout
    static final Pattern re = Pattern.compile(
            ">>> KDCCommunication: kdc=kdc.rabbit.hole UDP:(\\d)...., " +
            "timeout=(\\d+),");

    // Ratio for timeout values of all timeout tests. Not final so that
    // each test can choose their own.
    static float ratio = 2f;

    static void setRatio(float ratio) {
        BadKdc.ratio = ratio;
    }

    static float getRatio() {
        return ratio;
    }

    // Gets real timeout value. This method is called when writing krb5.conf
    static int toReal(int from) {
        return (int)(from * ratio + .5);
    }

    // De-ratio a millisecond value to second
    static int toSymbolicSec(int from) {
        return (int)(from / ratio / 1000f + 0.5);
    }

    /*
     * There are several cases this test fails:
     *
     * 1. The random selected port is used by another process. No good way to
     * prevent this happening, coz krb5.conf must be written before KDC starts.
     * There are two different outcomes:
     *
     *  a. Cannot start the KDC. A BindException thrown.
     *  b. When trying to access a non-existing KDC, a response is received!
     *     Most likely a Asn1Exception thrown
     *
     * 2. Even if a KDC is started, and more than 20 seconds pass by, a timeout
     * can still happens for the first UDP request. In fact, the KDC did not
     * received it at all. This happens on almost all platforms, especially
     * solaris-i586 and solaris-x64.
     *
     * To avoid them:
     *
     * 1. Catch those exceptions and ignore
     *
     * 2. a. Make the timeout longer? useless
     *    b. Read the output carefully, if there is a timeout, it's OK.
     *       Just make sure the retries times and KDCs are correct.
     *       This is tough.
     *    c. Feed the KDC a UDP packet first. The current "solution".
     */
    public static void go(String... expected)
            throws Exception {
        try {
            go0(expected);
        } catch (BindException be) {
            System.out.println("The random port is used by another process");
        } catch (LoginException le) {
            Throwable cause = le.getCause();
            if (cause instanceof Asn1Exception) {
                System.out.println("Bad packet possibly from another process");
                return;
            }
            throw le;
        }
    }

    public static void go0(String... expected)
            throws Exception {
        System.setProperty("sun.security.krb5.debug", "true");

        // Idle UDP sockets will trigger a SocketTimeoutException, without it,
        // a PortUnreachableException will be thrown.
        DatagramSocket d1 = null, d2 = null, d3 = null;

        // Make sure KDCs' ports starts with 1 and 2 and 3,
        // useful for checking debug output.
        int p1 = 10000 + new java.util.Random().nextInt(10000);
        int p2 = 20000 + new java.util.Random().nextInt(10000);
        int p3 = 30000 + new java.util.Random().nextInt(10000);

        FileWriter fw = new FileWriter("alternative-krb5.conf");

        fw.write("[libdefaults]\n" +
                "default_realm = " + OneKDC.REALM + "\n" +
                "kdc_timeout = " + toReal(2000) + "\n");
        fw.write("[realms]\n" + OneKDC.REALM + " = {\n" +
                "kdc = " + OneKDC.KDCHOST + ":" + p1 + "\n" +
                "kdc = " + OneKDC.KDCHOST + ":" + p2 + "\n" +
                "kdc = " + OneKDC.KDCHOST + ":" + p3 + "\n" +
                "}\n");

        fw.close();
        System.setProperty("java.security.krb5.conf", "alternative-krb5.conf");
        Config.refresh();

        // Turn on k3 only
        d1 = new DatagramSocket(p1);
        d2 = new DatagramSocket(p2);
        KDC k3 = on(p3);

        test(expected[0]);
        test(expected[1]);
        Config.refresh();
        test(expected[2]);

        k3.terminate(); // shutdown k3
        d3 = new DatagramSocket(p3);

        d2.close();
        on(p2);         // k2 is on

        test(expected[3]);
        d1.close();
        on(p1);         // k1 and k2 is on
        test(expected[4]);

        d3.close();
    }

    private static KDC on(int p) throws Exception {
        KDC k = new KDC(OneKDC.REALM, OneKDC.KDCHOST, p, true);
        k.addPrincipal(OneKDC.USER, OneKDC.PASS);
        k.addPrincipalRandKey("krbtgt/" + OneKDC.REALM);
        // Feed a packet to newly started KDC to warm it up
        System.err.println("-------- IGNORE THIS ERROR MESSAGE --------");
        new DatagramSocket().send(
                new DatagramPacket("Hello".getBytes(), 5,
                        InetAddress.getByName(OneKDC.KDCHOST), p));
        return k;
    }

    private static void test(String expected) throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        System.out.println("----------------- TEST -----------------");
        try {
            test0(bo, expected);
        } catch (Exception e) {
            System.out.println("----------------- ERROR -----------------");
            System.out.println(new String(bo.toByteArray()));
            System.out.println("--------------- ERROR END ---------------");
            throw e;
        }
    }

    /**
     * One round of test for max_retries and timeout.
     * @param expected the expected kdc# timeout kdc# timeout...
     */
    private static void test0(ByteArrayOutputStream bo, String expected)
            throws Exception {
        PrintStream oldout = System.out;
        boolean failed = false;
        System.setOut(new PrintStream(bo));
        try {
            Context.fromUserPass(OneKDC.USER, OneKDC.PASS, false);
        } catch (Exception e) {
            failed = true;
        } finally {
            System.setOut(oldout);
        }

        String[] lines = new String(bo.toByteArray()).split("\n");
        StringBuilder sb = new StringBuilder();
        for (String line: lines) {
            Matcher m = re.matcher(line);
            if (m.find()) {
                System.out.println(line);
                sb.append(m.group(1))
                        .append(toSymbolicSec(Integer.parseInt(m.group(2))));
            }
        }
        if (failed) sb.append('-');

        String output = sb.toString();
        System.out.println("Expected: " + expected + ", actual " + output);
        if (!output.matches(expected)) {
            throw new Exception("Does not match");
        }
    }
}
