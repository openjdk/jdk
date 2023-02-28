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
 * @bug 8284194
 * @summary Allow empty subject fields in keytool
 * @library /test/lib
 */

import jdk.test.lib.Asserts;
import jdk.test.lib.SecurityTools;

import java.io.File;
import java.security.KeyStore;
import java.security.cert.X509Certificate;

public class EmptyField {

    public static void main(String[] args) throws Exception {
        // All "." in first round, "Me" as name in 2nd round.
        SecurityTools.setResponse(
                ".\n.\n.\n.\n.\n.\n"        // all empty, must retry
                + "Me\n\n\n\n\n\nno\n"      // one non-empty, ask yes/no
                + "\n\n\n\n\n\nyes\n");     // remember input
        SecurityTools.keytool("-genkeypair -keystore ks -storepass changeit -alias b -keyalg EC")
                .shouldContain("[Unknown]") // old default
                .shouldContain("At least one field must be provided. Enter again.")
                .shouldContain("[]") // new value in 2nd round
                .shouldContain("[Me]") // new value in 3nd round
                .shouldContain("Is CN=Me correct?")
                .shouldHaveExitValue(0);
        var ks = KeyStore.getInstance(new File("ks"), "changeit".toCharArray());
        var cert = (X509Certificate) ks.getCertificate("b");
        Asserts.assertEQ(cert.getSubjectX500Principal().toString(), "CN=Me");
    }
}
