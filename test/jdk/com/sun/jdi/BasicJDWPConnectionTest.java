/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Smoke test for JDWP hardening
 * @library /test/lib
 * @run driver BasicJDWPConnectionTest
 */

import java.io.IOException;

import java.net.Socket;
import java.net.SocketException;

import jdk.test.lib.apps.LingeredApp;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class BasicJDWPConnectionTest {

    public static int handshake(int port) throws IOException {
        // Connect to the debuggee and handshake
        int res = -1;
        Socket s = null;
        try {
            s = new Socket("localhost", port);
            s.getOutputStream().write("JDWP-Handshake".getBytes("UTF-8"));
            byte[] buffer = new byte[24];
            res = s.getInputStream().read(buffer);
        }
        catch (SocketException ex) {
            // pass
        } finally {
            if (s != null) {
                s.close();
            }
        }
        return res;
    }

    public static ArrayList<String> prepareCmd(String allowOpt) {
         ArrayList<String> cmd = new ArrayList<>();

         String jdwpArgs = "-agentlib:jdwp=transport=dt_socket,server=y," +
                           "suspend=n,address=*:0" + allowOpt;
         cmd.add(jdwpArgs);
         return cmd;
    }

    private static Pattern listenRegexp = Pattern.compile("Listening for transport \\b(.+)\\b at address: \\b(\\d+)\\b");
    private static int detectPort(String s) {
        Matcher m = listenRegexp.matcher(s);
        if (!m.find()) {
            throw new RuntimeException("Could not detect port from '" + s + "'");
        }
        // m.group(1) is transport, m.group(2) is port
        return Integer.parseInt(m.group(2));
    }

    public static void positiveTest(String testName, String allowOpt)
        throws InterruptedException, IOException {
        System.err.println("\nStarting " + testName);
        ArrayList<String> cmd = prepareCmd(allowOpt);

        LingeredApp a = LingeredApp.startApp(cmd);
        int res;
        try {
            res = handshake(detectPort(a.getProcessStdout()));
        } finally {
            a.stopApp();
        }
        if (res < 0) {
            throw new RuntimeException(testName + " FAILED");
        }
        System.err.println(testName + " PASSED");
    }

    public static void negativeTest(String testName, String allowOpt)
        throws InterruptedException, IOException {
        System.err.println("\nStarting " + testName);
        ArrayList<String> cmd = prepareCmd(allowOpt);

        LingeredApp a = LingeredApp.startApp(cmd);
        int res;
        try {
            res = handshake(detectPort(a.getProcessStdout()));
        } finally {
            a.stopApp();
        }
        if (res > 0) {
            System.err.println(testName + ": res=" + res);
            throw new RuntimeException(testName + " FAILED");
        }
        System.err.println(testName + ": returned a negative code as expected: " + res);
        System.err.println(testName + " PASSED");
    }

    public static void badAllowOptionTest(String testName, String allowOpt)
        throws InterruptedException, IOException {
        System.err.println("\nStarting " + testName);
        ArrayList<String> cmd = prepareCmd(allowOpt);

        LingeredApp a;
        try {
            a = LingeredApp.startApp(cmd);
        } catch (IOException ex) {
            System.err.println(testName + ": caught expected IOException");
            System.err.println(testName + " PASSED");
            return;
        }
        // LingeredApp.startApp is expected to fail, but if not, terminate the app
        a.stopApp();
        throw new RuntimeException(testName + " FAILED");
    }

    public static void DefaultTest() throws InterruptedException, IOException {
        // No allow option is the same as the allow option ',allow=*' is passed
        String allowOpt = "";
        positiveTest("DefaultTest", allowOpt);
    }

    static void ExplicitDefaultTest() throws InterruptedException, IOException {
        // Explicit permission for connections from everywhere
        String allowOpt = ",allow=*";
        positiveTest("ExplicitDefaultTest" ,allowOpt);
    }

    public static void AllowTest() throws InterruptedException, IOException {
        String allowOpt = ",allow=127.0.0.1";
        positiveTest("AllowTest", allowOpt);
    }

    public static void MultiAllowTest() throws InterruptedException, IOException {
        String allowOpt = ",allow=127.0.0.1+10.0.0.0/8+172.16.0.0/12+192.168.0.0/24";
        positiveTest("MultiAllowTest", allowOpt);
    }

    public static void DenyTest() throws InterruptedException, IOException {
        // Bad allow address
        String allowOpt = ",allow=0.0.0.0";
        negativeTest("DenyTest", allowOpt);
    }

    public static void MultiDenyTest() throws InterruptedException, IOException {
        // Wrong separator ';' is used for allow option
        String allowOpt = ",allow=127.0.0.1;192.168.0.0/24";
        badAllowOptionTest("MultiDenyTest", allowOpt);
    }

    public static void EmptyAllowOptionTest() throws InterruptedException, IOException {
        // Empty allow option
        String allowOpt = ",allow=";
        badAllowOptionTest("EmptyAllowOptionTest", allowOpt);
    }

    public static void ExplicitMultiDefault1Test() throws InterruptedException, IOException {
        // Bad mix of allow option '*' with address value
        String allowOpt = ",allow=*+allow=127.0.0.1";
        badAllowOptionTest("ExplicitMultiDefault1Test", allowOpt);
    }

    public static void ExplicitMultiDefault2Test() throws InterruptedException, IOException {
        // Bad mix of allow address value with '*'
        String allowOpt = ",allow=allow=127.0.0.1+*";
        badAllowOptionTest("ExplicitMultiDefault2Test", allowOpt);
    }

    public static void main(String[] args) throws Exception {
        DefaultTest();
        ExplicitDefaultTest();
        AllowTest();
        MultiAllowTest();
        DenyTest();
        MultiDenyTest();
        EmptyAllowOptionTest();
        ExplicitMultiDefault1Test();
        ExplicitMultiDefault2Test();
        System.err.println("\nTest PASSED");
    }
}
