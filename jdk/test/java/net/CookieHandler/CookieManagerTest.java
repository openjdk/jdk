/*
 * Copyright (c) 2005, 2008, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Unit test for java.net.CookieManager
 * @bug 6244040
 * @library ../../../sun/net/www/httptest/
 * @build HttpCallback HttpServer ClosedChannelList HttpTransaction
 * @run main/othervm -ea CookieManagerTest
 * @author Edward Wang
 */

import java.net.*;
import java.util.*;
import java.io.*;
import sun.net.www.MessageHeader;

public class CookieManagerTest {
    static CookieHttpTransaction httpTrans;
    static HttpServer server;

    public static void main(String[] args) throws Exception {
        startHttpServer();
        makeHttpCall();

        if (httpTrans.badRequest) {
            throw new RuntimeException("Test failed : bad cookie header");
        }
    }

    public static void startHttpServer() {
        try {
            httpTrans = new CookieHttpTransaction();
            server = new HttpServer(httpTrans, 1, 1, 0);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void makeHttpCall() {
        try {
            System.out.println("http server listen on: " + server.getLocalPort());

            // install CookieManager to use
            CookieHandler.setDefault(new CookieManager());

            for (int i = 0; i < CookieHttpTransaction.testCount; i++) {
                System.out.println("====== CookieManager test " + (i+1) + " ======");
                ((CookieManager)CookieHandler.getDefault()).setCookiePolicy(CookieHttpTransaction.testPolicies[i]);
                ((CookieManager)CookieHandler.getDefault()).getCookieStore().removeAll();
                URL url = new URL("http" , InetAddress.getLocalHost().getHostAddress(),
                                    server.getLocalPort(), CookieHttpTransaction.testCases[i][0].serverPath);
                HttpURLConnection uc = (HttpURLConnection)url.openConnection();
                uc.getResponseCode();
                uc.disconnect();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            server.terminate();
        }
    }
}

class CookieHttpTransaction implements HttpCallback {
    public static boolean badRequest = false;
    // the main test control logic will also loop exactly this number
    // to send http request
    public static final int testCount = 6;

    private String localHostAddr = "127.0.0.1";

    // test cases
    public static class CookieTestCase {
        public String headerToken;
        public String cookieToSend;
        public String cookieToRecv;
        public String serverPath;

        public CookieTestCase(String h, String cts, String ctr, String sp) {
            headerToken = h;
            cookieToSend = cts;
            cookieToRecv = ctr;
            serverPath = sp;
        }
    };

    //
    // these two must match each other, i.e. testCases.length == testPolicies.length
    //
    public static CookieTestCase[][] testCases = null;  // the test cases to run; each test case may contain multiple roundtrips
    public static CookiePolicy[] testPolicies = null;   // indicates what CookiePolicy to use with each test cases

    CookieHttpTransaction() {
        testCases = new CookieTestCase[testCount][];
        testPolicies = new CookiePolicy[testCount];

        try {
            localHostAddr = InetAddress.getLocalHost().getHostAddress();
        } catch (Exception ignored) {
        };
        int count = 0;

        // an http session with Netscape cookies exchanged
        testPolicies[count] = CookiePolicy.ACCEPT_ORIGINAL_SERVER;
        testCases[count++] = new CookieTestCase[]{
                new CookieTestCase("Set-Cookie",
                "CUSTOMER=WILE:BOB; path=/; expires=Wednesday, 09-Nov-2030 23:12:40 GMT;" + "domain=." + localHostAddr,
                "CUSTOMER=WILE:BOB",
                "/"
                ),
                new CookieTestCase("Set-Cookie",
                "PART_NUMBER=ROCKET_LAUNCHER_0001; path=/;" + "domain=." + localHostAddr,
                "CUSTOMER=WILE:BOB; PART_NUMBER=ROCKET_LAUNCHER_0001",
                "/"
                ),
                new CookieTestCase("Set-Cookie",
                "SHIPPING=FEDEX; path=/foo;" + "domain=." + localHostAddr,
                "CUSTOMER=WILE:BOB; PART_NUMBER=ROCKET_LAUNCHER_0001",
                "/"
                ),
                new CookieTestCase("Set-Cookie",
                "SHIPPING=FEDEX; path=/foo;" + "domain=." + localHostAddr,
                "CUSTOMER=WILE:BOB; PART_NUMBER=ROCKET_LAUNCHER_0001; SHIPPING=FEDEX",
                "/foo"
                )
                };

        // check whether or not path rule is applied
        testPolicies[count] = CookiePolicy.ACCEPT_ORIGINAL_SERVER;
        testCases[count++] = new CookieTestCase[]{
                new CookieTestCase("Set-Cookie",
                "PART_NUMBER=ROCKET_LAUNCHER_0001; path=/;" + "domain=." + localHostAddr,
                "PART_NUMBER=ROCKET_LAUNCHER_0001",
                "/"
                ),
                new CookieTestCase("Set-Cookie",
                "PART_NUMBER=RIDING_ROCKET_0023; path=/ammo;" + "domain=." + localHostAddr,
                "PART_NUMBER=RIDING_ROCKET_0023; PART_NUMBER=ROCKET_LAUNCHER_0001",
                "/ammo"
                )
                };

        // an http session with rfc2965 cookies exchanged
        testPolicies[count] = CookiePolicy.ACCEPT_ORIGINAL_SERVER;
        testCases[count++] = new CookieTestCase[]{
                new CookieTestCase("Set-Cookie2",
                "Customer=\"WILE_E_COYOTE\"; Version=\"1\"; Path=\"/acme\";" + "domain=." + localHostAddr,
                "$Version=\"1\"; Customer=\"WILE_E_COYOTE\";$Path=\"/acme\";$Domain=\"." + localHostAddr + "\"",
                "/acme/login"
                ),
                new CookieTestCase("Set-Cookie2",
                "Part_Number=\"Rocket_Launcher_0001\"; Version=\"1\";Path=\"/acme\";" + "domain=." + localHostAddr,
                "$Version=\"1\"; Customer=\"WILE_E_COYOTE\";$Path=\"/acme\";" + "$Domain=\"." + localHostAddr  + "\"" + "; Part_Number=\"Rocket_Launcher_0001\";$Path=\"/acme\";" + "$Domain=\"." + localHostAddr +  "\"",
                "/acme/pickitem"
                ),
                new CookieTestCase("Set-Cookie2",
                "Shipping=\"FedEx\"; Version=\"1\"; Path=\"/acme\";" + "domain=." + localHostAddr,
                "$Version=\"1\"; Customer=\"WILE_E_COYOTE\";$Path=\"/acme\";" + "$Domain=\"." + localHostAddr  + "\"" + "; Part_Number=\"Rocket_Launcher_0001\";$Path=\"/acme\";" + "$Domain=\"." + localHostAddr  + "\"" + "; Shipping=\"FedEx\";$Path=\"/acme\";" + "$Domain=\"." + localHostAddr + "\"",
                "/acme/shipping"
                )
                };

        // check whether or not the path rule is applied
        testPolicies[count] = CookiePolicy.ACCEPT_ORIGINAL_SERVER;
        testCases[count++] = new CookieTestCase[]{
                new CookieTestCase("Set-Cookie2",
                "Part_Number=\"Rocket_Launcher_0001\"; Version=\"1\"; Path=\"/acme\";" + "domain=." + localHostAddr,
                "$Version=\"1\"; Part_Number=\"Rocket_Launcher_0001\";$Path=\"/acme\";$Domain=\"." + localHostAddr + "\"",
                "/acme/ammo"
                ),
                new CookieTestCase("Set-Cookie2",
                "Part_Number=\"Riding_Rocket_0023\"; Version=\"1\"; Path=\"/acme/ammo\";" + "domain=." + localHostAddr,
                "$Version=\"1\"; Part_Number=\"Riding_Rocket_0023\";$Path=\"/acme/ammo\";$Domain=\"." + localHostAddr  + "\"" + "; Part_Number=\"Rocket_Launcher_0001\";$Path=\"/acme\";" + "$Domain=\"." + localHostAddr + "\"",
                "/acme/ammo"
                ),
                new CookieTestCase("",
                "",
                "$Version=\"1\"; Part_Number=\"Rocket_Launcher_0001\";$Path=\"/acme\";" + "$Domain=\"." + localHostAddr + "\"",
                "/acme/parts"
                )
                };

        // new cookie should overwrite old cookie
        testPolicies[count] = CookiePolicy.ACCEPT_ORIGINAL_SERVER;
        testCases[count++] = new CookieTestCase[]{
                new CookieTestCase("Set-Cookie2",
                "Part_Number=\"Rocket_Launcher_0001\"; Version=\"1\"; Path=\"/acme\";" + "domain=." + localHostAddr,
                "$Version=\"1\"; Part_Number=\"Rocket_Launcher_0001\";$Path=\"/acme\";$Domain=\"." + localHostAddr + "\"",
                "/acme"
                ),
                new CookieTestCase("Set-Cookie2",
                "Part_Number=\"Rocket_Launcher_2000\"; Version=\"1\"; Path=\"/acme\";" + "domain=." + localHostAddr,
                "$Version=\"1\"; Part_Number=\"Rocket_Launcher_2000\";$Path=\"/acme\";$Domain=\"." + localHostAddr + "\"",
                "/acme"
                )
                };

        // cookies without domain attributes
        // RFC 2965 states that domain should default to host
        testPolicies[count] = CookiePolicy.ACCEPT_ALL;
        testCases[count++] = new CookieTestCase[]{
                new CookieTestCase("Set-Cookie2",
                "Customer=\"WILE_E_COYOTE\"; Version=\"1\"; Path=\"/acme\"",
                "$Version=\"1\"; Customer=\"WILE_E_COYOTE\";$Path=\"/acme\";$Domain=\""+localHostAddr+"\"",
                "/acme/login"
                ),
                new CookieTestCase("Set-Cookie2",
                "Part_Number=\"Rocket_Launcher_0001\"; Version=\"1\";Path=\"/acme\"",
                "$Version=\"1\"; Customer=\"WILE_E_COYOTE\";$Path=\"/acme\";$Domain=\""+localHostAddr+"\"" + "; Part_Number=\"Rocket_Launcher_0001\";$Path=\"/acme\";$Domain=\""+localHostAddr+"\"",
                "/acme/pickitem"
                ),
                new CookieTestCase("Set-Cookie2",
                "Shipping=\"FedEx\"; Version=\"1\"; Path=\"/acme\"",
                "$Version=\"1\"; Customer=\"WILE_E_COYOTE\";$Path=\"/acme\";$Domain=\""+localHostAddr+"\"" + "; Part_Number=\"Rocket_Launcher_0001\";$Path=\"/acme\";$Domain=\""+localHostAddr+"\"" + "; Shipping=\"FedEx\";$Path=\"/acme\";$Domain=\""+localHostAddr+"\"",
                "/acme/shipping"
                )
                };

        assert count == testCount;
    }

    private int testcaseDone = 0;
    private int testDone = 0;
    /*
     * Our http server which is conducted by testCases array
     */
    public void request(HttpTransaction trans) {
        try {
            if (testDone < testCases[testcaseDone].length) {
                // still have other tests to run,
                // check the Cookie header and then redirect it
                if (testDone > 0) checkResquest(trans);
                trans.addResponseHeader("Location", testCases[testcaseDone][testDone].serverPath);
                trans.addResponseHeader(testCases[testcaseDone][testDone].headerToken,
                                        testCases[testcaseDone][testDone].cookieToSend);
                testDone++;
                trans.sendResponse(302, "Moved Temporarily");
            } else {
                // the last test of this test case
                if (testDone > 0) checkResquest(trans);
                testcaseDone++;
                testDone = 0;
                trans.sendResponse(200, "OK");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void checkResquest(HttpTransaction trans) {
        String cookieHeader = null;

        assert testDone > 0;
        cookieHeader = trans.getRequestHeader("Cookie");
        if (cookieHeader != null &&
            cookieHeader.equalsIgnoreCase(testCases[testcaseDone][testDone-1].cookieToRecv))
        {
            System.out.printf("%15s %s\n", "PASSED:", cookieHeader);
        } else {
            System.out.printf("%15s %s\n", "FAILED:", cookieHeader);
            System.out.printf("%15s %s\n\n", "should be:", testCases[testcaseDone][testDone-1].cookieToRecv);
            badRequest = true;
        }
    }
}
