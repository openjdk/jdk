/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8242184
 * @summary CRL generation error with RSASSA-PSS
 * @library /test/lib
 */

import jdk.test.lib.SecurityTools;
import jdk.test.lib.process.OutputAnalyzer;

public class GenerateAll {

    public static void main(String[] args) throws Throwable {

        kt("-genkeypair -alias ca -dname CN=CA -keyalg ec");

        String[] aliases = {
                "rsa", "dsa", "rrr", "rsassa-pss", "ec"};

        for (String alias : aliases) {
            // "rrr": keyalg is rsa, sigalg is rsassa-pss
            // otherwise: keyalg is alias, sigalg auto derived
            String keyAlg = alias.equals("rrr") ? "rsa" : alias;
            String extra = alias.equals("rrr") ? " -sigalg rsassa-pss" : "";

            // gen
            kt("-genkeypair -alias " + alias + " -dname CN=" + alias
                    + " -keyalg " + keyAlg + extra);

            // req
            kt("-certreq -alias " + alias + " -file " + alias + ".req");
            kt("-printcertreq -file " + alias + ".req");

            // gencert
            kt("-gencert -alias ca -infile " + alias
                    + ".req -outfile " + alias + ".crt");
            kt("-printcert -file " + alias + ".crt");

            // crl
            kt("-gencrl -alias " + alias + " -id 0 -file " + alias + ".crl");
            kt("-printcrl -file " + alias + ".crl")
                    .shouldContain("Verified by " + alias);
        }
    }

    static OutputAnalyzer kt(String arg) throws Exception {
        return SecurityTools.keytool("-keystore ks -storepass changeit " + arg)
                .shouldHaveExitValue(0);
    }
}
