/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8286032
 * @summary Validate the warnings of the keytool -list -alias command
 * @library /test/lib
 */

import jdk.test.lib.SecurityTools;

public class ListAlias {

    public static void main(String[] args) throws Exception {
        SecurityTools.keytool("-keystore ks -storepass changeit " +
                "-genseckey -keyalg DES -alias deskey")
                .shouldContain("Warning")
                .shouldMatch("The generated secret key uses the DES algorithm.*considered a security risk")
                .shouldHaveExitValue(0);

        SecurityTools.keytool("-keystore ks -storepass changeit " +
                        "-list -alias deskey -v")
                .shouldContain("Warning")
                .shouldMatch("<deskey> uses the DES algorithm.*considered a security risk")
                .shouldNotContain("The certificate")
                .shouldHaveExitValue(0);

        SecurityTools.keytool("-keystore ks -storepass changeit " +
                        "-genkeypair -keyalg RSA -alias ca -dname CN=CA -ext bc:c " +
                        "-sigalg SHA1withRSA")
                .shouldContain("Warning")
                .shouldMatch("The generated certificate uses the SHA1withRSA.*considered a security risk")
                .shouldHaveExitValue(0);

        SecurityTools.keytool("-keystore ks -storepass changeit " +
                        "-list -alias ca -v")
                .shouldContain("Warning")
                .shouldMatch("<ca> uses the SHA1withRSA.*considered a security risk")
                .shouldNotContain("The certificate")
                .shouldHaveExitValue(0);

        SecurityTools.keytool("-keystore ks -storepass changeit " +
                        "-list -v")
                .shouldContain("Warning")
                .shouldMatch("<deskey> uses the DES algorithm.*considered a security risk")
                .shouldMatch("<ca> uses the SHA1withRSA.*considered a security risk")
                .shouldNotContain("The certificate")
                .shouldHaveExitValue(0);
    }
}