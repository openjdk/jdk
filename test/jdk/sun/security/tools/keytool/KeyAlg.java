/*
 * Copyright (c) 2014, 2019, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8029659
 * @summary Keytool, print key algorithm of certificate or key entry
 * @library /test/lib
 */

import jdk.test.lib.SecurityTools;
import jdk.test.lib.process.OutputAnalyzer;

public class KeyAlg {
    public static void main(String[] args) throws Exception {
        keytool("-genkeypair -alias ca -dname CN=CA -keyalg EC")
                .shouldHaveExitValue(0);
        keytool("-genkeypair -alias user -dname CN=User -keyalg RSA -keysize 1024")
                .shouldHaveExitValue(0);
        keytool("-certreq -alias user -file user.req").shouldHaveExitValue(0);
        keytool("-gencert -alias ca -rfc -sigalg SHA1withECDSA"
                + " -infile user.req -outfile user.crt")
                .shouldHaveExitValue(0);
        keytool("-printcert -file user.crt")
                .shouldHaveExitValue(0)
                .shouldMatch("Signature algorithm name:.*SHA1withECDSA")
                .shouldMatch("Subject Public Key Algorithm:.*1024.*RSA");
    }

    static OutputAnalyzer keytool(String s) throws Exception {
        return SecurityTools.keytool(
                "-keystore ks -storepass changeit -keypass changeit " + s);
    }
}
