/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8273236
 * @summary Test SHA1 usage SignedJAR
 * @library /test/lib
 */

import jdk.test.lib.SecurityTools;
import jdk.test.lib.process.OutputAnalyzer;

public class TestSha1Usage {

    static OutputAnalyzer kt(String cmd, String ks) throws Exception {
        return SecurityTools.keytool("-storepass changeit " + cmd +
                " -keystore " + ks);
    }

    public static void main(String[] args) throws Exception {

        SecurityTools.keytool("-keystore ks -storepass changeit " +
                "-genkeypair -keyalg rsa -alias ca -dname CN=CA " +
                "-ext eku=codeSigning -sigalg SHA1withRSA")
                .shouldContain("Warning:")
                .shouldMatch("The generated certificate.*SHA1withRSA.*considered a security risk")
                .shouldMatch("cannot be used to sign JARs")
                .shouldHaveExitValue(0);

        kt("-genkeypair -keyalg rsa -alias e1 -dname CN=E1", "ks");
        kt("-certreq -alias e1 -file tmp.req", "ks");
        SecurityTools.keytool("-keystore ks -storepass changeit " +
                "-gencert -alias ca -infile tmp.req -outfile tmp.cert")
                .shouldContain("Warning:")
                .shouldMatch("The issuer.*SHA1withRSA.*considered a security risk")
                .shouldMatch("cannot be used to sign JARs")
                .shouldHaveExitValue(0);
    }
}
