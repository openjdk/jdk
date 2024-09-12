
/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023 SAP SE. All rights reserved.
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

import java.security.KeyStore;
import java.util.HashSet;
import java.util.Set;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

/*
 * @test
 * @bug 8303465 8320362
 * @library /test/lib
 * @requires os.family == "mac"
 * @summary Check whether loading of certificates from MacOS Keychain correctly
 *          honors trust settings
 * @run main CheckMacOSKeyChainTrust KEYCHAINSTORE
 * @run main CheckMacOSKeyChainTrust KEYCHAINSTORE-ROOT
 */
public class CheckMacOSKeyChainTrust {
    private static Set<String> trusted = new HashSet<>();
    private static Set<String> distrusted = new HashSet<>();

    public static void main(String[] args) throws Throwable {
        String keystore = args[0];
        if (keystore.equals("KEYCHAINSTORE")) {
            loadUser(true);
            loadAdmin(true);
        } else {
            // check user and admin trustsettings to find distrusted certs
            loadUser(false);
            loadAdmin(false);
            loadSystem(true);
        }
        System.out.println("Trusted Certs: " + trusted);
        System.out.println("Distrusted Certs: " + distrusted);
        KeyStore ks = KeyStore.getInstance(keystore);
        ks.load(null, null);
        for (String alias : trusted) {
            if (!ks.containsAlias(alias)) {
                throw new RuntimeException("Not found: " + alias);
            }
        }
        for (String alias : distrusted) {
            if (ks.containsAlias(alias)) {
                throw new RuntimeException("Found: " + alias);
            }
        }
    }

    private static void loadUser(boolean addTrusted) throws Throwable {
        populate(ProcessTools.executeProcess("security", "dump-trust-settings"), addTrusted);
    }

    private static void loadAdmin(boolean addTrusted) throws Throwable {
        populate(ProcessTools.executeProcess("security", "dump-trust-settings", "-d"), addTrusted);
    }

    private static void loadSystem(boolean addTrusted) throws Throwable {
        populate(ProcessTools.executeProcess("security", "dump-trust-settings", "-s"), addTrusted);
    }

    private static void populate(OutputAnalyzer output, boolean addTrusted) throws Throwable {
        if (output.getExitValue() != 0) {
            return; // No Trust Settings were found
        }
        String certName = null;
        boolean trustRootFound = false;
        boolean trustAsRootFound = false;
        boolean denyFound = false;
        boolean unspecifiedFound = false;
        for (String line : output.asLines()) {
            if (line.startsWith("Cert ")) {
                if (certName != null) {
                    if (!denyFound &&
                        !(unspecifiedFound && !(trustRootFound || trustAsRootFound)) &&
                        !distrusted.contains(certName)) {
                        if (addTrusted) {
                            trusted.add(certName);
                        }
                    } else {
                        distrusted.add(certName);
                        trusted.remove(certName);
                    }
                }
                certName = line.split(":", 2)[1].trim().toLowerCase();
                trustRootFound = false;
                trustAsRootFound = false;
                denyFound = false;
                unspecifiedFound = false;
            } else if (line.contains("kSecTrustSettingsResultTrustRoot")) {
                trustRootFound = true;
            } else if (line.contains("kSecTrustSettingsResultTrustAsRoot")) {
                trustAsRootFound = true;
            } else if (line.contains("kSecTrustSettingsResultDeny")) {
                denyFound = true;
            } else if (line.contains("kSecTrustSettingsResultUnspecified")) {
                unspecifiedFound = true;
            }
        }
        if (certName != null) {
            if (!denyFound &&
                !(unspecifiedFound && !(trustRootFound || trustAsRootFound)) &&
                !distrusted.contains(certName)) {
                if (addTrusted) {
                    trusted.add(certName);
                }
            } else {
                distrusted.add(certName);
                trusted.remove(certName);
            }
        }
    }
}
