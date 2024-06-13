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

/*
 * @test
 * @bug 8330217
 * @summary correct warnings on whether signer is in keystore and listed
 * @library /test/lib
 */

import jdk.test.lib.SecurityTools;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.util.JarUtils;

import java.nio.file.Files;
import java.nio.file.Path;

public class ByAlias {
    static OutputAnalyzer kt(String cmd) throws Exception {
        return SecurityTools.keytool("-storepass changeit "
                + "-keypass changeit -keystore ks " + cmd);
    }

    static void selfsign(String name) throws Exception {
        kt("-alias " + name + " -dname CN=" + name + " -keyalg ec -genkey");
    }

    static void gencert(String signer, String owner) throws Exception {
        selfsign(owner);
        kt("-certreq -alias " + owner + " -file tmp.req")
                .shouldHaveExitValue(0);
        kt("-gencert -infile tmp.req -outfile tmp.cert -alias " + signer)
                .shouldHaveExitValue(0);
        kt("-import -alias " + owner + " -file tmp.cert")
                .shouldHaveExitValue(0);
    }

    static OutputAnalyzer js(String cmd) throws Exception {
        return SecurityTools.jarsigner("-keystore ks -storepass changeit " + cmd);
    }

    public static void main(String[] args) throws Exception {
        JarUtils.createJarFile(Path.of("a.jar"), Path.of("."),
                Files.writeString(Path.of("a"), "a"));

        selfsign("ca");
        gencert("ca", "ca1");
        gencert("ca1", "ee");
        selfsign("n1");
        selfsign("n2");

        js("a.jar ee");

        // Everything is good at the beginning
        js("-verify a.jar ee")
                .shouldNotContain("not signed by the specified alias(es)")
                .shouldNotContain("not signed by alias in this keystore");

        js("-verify a.jar ca1") // ca1 is not the signer
                .shouldContain("not signed by the specified alias(es)")
                .shouldNotContain("not signed by alias in this keystore");

        // Remove intermediate cert from ks. Still good.
        kt("-delete -alias ca1");
        js("-verify a.jar ee")
                .shouldNotContain("not signed by alias in this keystore");

        // End-entity cert is removed. Warn now.
        kt("-delete -alias ee");
        js("-verify a.jar")
                .shouldContain("not signed by alias in this keystore");

        // Sign with different signer n1
        js("a.jar n1");

        // Add a new file and sign with different signer n2
        JarUtils.updateJarFile(Path.of("a.jar"), Path.of("."),
                Files.writeString(Path.of("b"), "b"));
        js("a.jar n2");

        // Now a signed with n1 and n2, b signed with n2
        js("-verify a.jar")
                .shouldNotContain("not signed by alias in this keystore");

        // If n2 is removed, then b has no signer
        kt("-delete -alias n2");
        js("-verify a.jar")
                .shouldContain("not signed by alias in this keystore");
    }
}
