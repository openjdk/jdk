/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jdk.test.lib.Asserts;
import sun.security.krb5.Config;

/*
 * @test
 * @bug 8333772
 * @summary check krb5.conf reading on default and realm-specific values
 * @library /test/lib
 * @run main/othervm RealmSpecificValues
 */
public class RealmSpecificValues {

    static DebugMatcher cm = new DebugMatcher();

    public static void main(String[] args) throws Exception {
        System.setProperty("sun.security.krb5.debug", "true");
        System.setProperty("java.security.krb5.conf", "alternative-krb5.conf");

        // Defaults
        writeConf(-1, -1, -1, -1, -1, -1);
        test(true, 3, 30000);

        // Below has settings. For each setting we provide 3 cases:
        // 1. Set in defaults, 2, set in realms, 3, both

        // udp = 0 is useful
        writeConf(0, -1, -1, -1, -1, -1);
        test(false, 3, 30000);
        writeConf(-1, -1, -1, 0, -1, -1);
        test(false, 3, 30000);
        writeConf(1, -1, -1, 0, -1, -1);
        test(false, 3, 30000);

        // max_retries = 0 is ignored
        writeConf(-1, 0, -1, -1, -1, -1);
        test(true, 3, 30000);
        writeConf(-1, -1, -1, -1, 0, -1);
        test(true, 3, 30000);
        writeConf(-1, 6, -1, -1, 0, -1); // Note: 0 is ignored, it does not reset to default
        test(true, 6, 30000);

        // max_retries = 1 is useful
        writeConf(-1, 1, -1, -1, -1, -1);
        test(true, 1, 30000);
        writeConf(-1, -1, -1, -1, 1, -1);
        test(true, 1, 30000);
        writeConf(-1, 3, -1, -1, 1, -1);
        test(true, 1, 30000);

        // timeout = 0 is ignored
        writeConf(-1, -1, 0, -1, -1, -1);
        test(true, 3, 30000);
        writeConf(-1, -1, -1, -1, -1, 0);
        test(true, 3, 30000);
        writeConf(-1, -1, 10000, -1, -1, 0);
        test(true, 3, 10000);

        // timeout > 0 is useful
        writeConf(-1, -1, 10000, -1, -1, -1);
        test(true, 3, 10000);
        writeConf(-1, -1, -1, -1, -1, 10000);
        test(true, 3, 10000);
        writeConf(-1, -1, 20000, -1, -1, 10000);
        test(true, 3, 10000);
    }

    static void writeConf(int limit, int retries, int timeout,
            int limitR, int retriesR, int timeoutR) throws Exception {

        String inDefaults = "";
        if (limit >= 0) inDefaults += "udp_preference_limit = " + limit + "\n";
        if (retries >= 0) inDefaults += "max_retries = " + retries + "\n";
        if (timeout >= 0) inDefaults += "kdc_timeout = " + timeout + "\n";

        String inRealm = "";
        if (limitR >= 0) inRealm += "udp_preference_limit = " + limitR + "\n";
        if (retriesR >= 0) inRealm += "max_retries = " + retriesR + "\n";
        if (timeoutR >= 0) inRealm += "kdc_timeout = " + timeoutR + "\n";

        String conf = "[libdefaults]\n" +
                "default_realm = " + OneKDC.REALM + "\n" +
                inDefaults +
                "\n" +
                "[realms]\n" +
                OneKDC.REALM + " = {\n" +
                "kdc = " + OneKDC.KDCHOST + ":12345\n" +
                inRealm +
                "}\n";

        Files.writeString(Paths.get("alternative-krb5.conf"), conf);
    }

    static void test(boolean isUDP, int retries, int timeout) throws Exception {

        PrintStream oldErr = System.err;
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        System.setErr(new PrintStream(bo));
        try {
            Config.refresh();
            Context.fromUserPass(OneKDC.USER, OneKDC.PASS, false);
        } catch (Exception e) {
            // will happen
        } finally {
            System.setErr(oldErr);
        }

        String[] lines = new String(bo.toByteArray()).split("\n");
        for (String line: lines) {
            if (cm.match(line)) {
                System.out.println(line);
                Asserts.assertEQ(cm.isUDP(), isUDP);
                Asserts.assertEQ(cm.timeout(), timeout);
                Asserts.assertEQ(cm.retries(), retries);
                return;
            }
        }
        Asserts.fail("Should not reach here");
    }

    /**
     * A helper class to match the krb5 debug output:
     * >>> KrbKdcReq send: kdc=kdc.rabbit.hole TCP:12345, timeout=30000,
     *     number of retries =3, #bytes=141
     */
    static class DebugMatcher {

        static final Pattern re = Pattern.compile(
                ">>> KrbKdcReq send: kdc=\\S+ (TCP|UDP):\\d+, " +
                        "timeout=(\\d+), number of retries\\s*=(\\d+)");

        Matcher matcher;

        boolean match(String line) {
            matcher = re.matcher(line);
            return matcher.find();
        }

        boolean isUDP() {
            return matcher.group(1).equals("UDP");
        }

        int timeout() {
            return Integer.parseInt(matcher.group(2));
        }

        int retries() {
            return Integer.parseInt(matcher.group(3));
        }
    }
}
