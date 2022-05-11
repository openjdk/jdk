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

import jdk.test.lib.Asserts;
import jdk.test.lib.SecurityTools;

import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/*
 * @test
 * @bug 6782021
 * @requires os.family == "windows"
 * @library /test/lib
 * @summary More keystore types
 */
public class AllTypes {

    public static void main(String[] args) throws Exception {
        var nm = test("windows-my");
        var nr = test("windows-root");
        var nmu = test("windows-my-currentuser");
        var nru = test("windows-root-currentuser");
        var hasAdminPrivileges = detectIfRunningWithAdminPrivileges();
        var nmm = adminTest("windows-my-localmachine", hasAdminPrivileges);
        var nrm = adminTest("windows-root-localmachine", hasAdminPrivileges);
        Asserts.assertEQ(nm, nmu);
        Asserts.assertEQ(nr, nru);
    }

    private static boolean detectIfRunningWithAdminPrivileges() {
        try {
            Process p = Runtime.getRuntime().exec("reg query \"HKU\\S-1-5-19\"");
            p.waitFor();
            return (p.exitValue() == 0);
        }
        catch (Exception ex) {
            System.out.println("Warning: unable to detect admin privileges, assuming none");
            return false;
        }
    }

    private static List<String> adminTest(String type, boolean hasAdminPrivileges) throws Exception {
        if (hasAdminPrivileges) {
            return test(type);
        }
        System.out.println("Ignoring: " + type + " as it requires admin privileges");
        return null;
    }

    private static List<String> test(String type) throws Exception {
        var stdType = "Windows-" + type.substring(8).toUpperCase(Locale.ROOT);
        SecurityTools.keytool("-storetype " + type + " -list")
        .shouldHaveExitValue(0)
        .shouldContain("Keystore provider: SunMSCAPI")
        .shouldContain("Keystore type: " + stdType);
        KeyStore ks = KeyStore.getInstance(type);
        ks.load(null, null);
        var content = Collections.list(ks.aliases());
        Collections.sort(content);
        return content;
    }
}