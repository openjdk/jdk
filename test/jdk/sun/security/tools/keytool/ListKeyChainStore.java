/*
 * Copyright (c) 2012, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7133495 8062264 8046777 8153005
 * @summary KeyChain KeyStore implementation retrieves only one private key entry
 * @requires (os.family == "mac")
 * @library /test/lib
 * @run main/othervm/manual ListKeyChainStore
 */

import jdk.test.lib.SecurityTools;
import jdk.test.lib.process.ProcessTools;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.Key;
import java.security.KeyStore;
import java.security.PrivateKey;

public class ListKeyChainStore {
    private static final String PWD = "xxxxxx";
    private static final String DEFAULT_KEYTOOL = "-list -storetype KeychainStore " +
            "-keystore NONE -storepass " + PWD;
    private static final String USER_DIR = System.getProperty("user.dir", ".");
    private static final String FS = System.getProperty("file.separator");
    private static final String PKCS12_KEYSTORE = USER_DIR + FS + "7133495.p12";
    private static final String KEYCHAIN_FILE = USER_DIR + FS + "7133495.keychain";
    private static final String TEMPORARY_FILE = USER_DIR + FS + "7133495.tmp";
    private static final String USER_KEYCHAIN_LIST = USER_DIR + FS + "user.keychain.list";
    private static final String PRIVATE_KEY_ENTRY = "PrivateKeyEntry";

    public static void main(String[] args) throws Throwable {
        LOG_MSG("WARNING: This test doesn't work on macOS virtualized environment. " +
                "`security list-keychains -s` doesn't update the search order.");

        deleteTestTempFilesIfExists();

        // Get the old security keychain list to restore later
        try (PrintStream printStream = new PrintStream(USER_KEYCHAIN_LIST)) {
            ProcessTools.executeCommand("sh", "-c", "security list-keychains")
                    .shouldHaveExitValue(0).outputTo(printStream);
        }

        try {
            try (PrintStream printStream = new PrintStream(TEMPORARY_FILE)) {
                SecurityTools.keytool(DEFAULT_KEYTOOL).shouldHaveExitValue(0)
                        .outputTo(printStream);
            }
            int oldPrivateKeyCount = countOccurrences(TEMPORARY_FILE, PRIVATE_KEY_ENTRY);
            LOG_MSG("Found " + oldPrivateKeyCount + " private key entries in the " +
                    "Keychain keystore");

            // Create the PKCS12 keystore containing 3 public/private key pairs
            LOG_MSG("Creating PKCS12 keystore: " + PKCS12_KEYSTORE);
            for (int i = 0; i < 3; i++) {
                // Use legacy encryption and MAC algorithms, refer macOS open radar FB8988319
                // macOS security framework doesn't work with the latest algorithms
                SecurityTools.keytool(String.format("-J-Dkeystore.pkcs12.legacy -genkeypair" +
                                " -storetype PKCS12 -keystore %s -storepass %s -keyalg rsa  -dname " +
                                "CN=CN%d,OU=OU%d,O=O%d,ST=ST%d,C=US -alias 7133495-%d",
                        PKCS12_KEYSTORE, PWD, i, i, i, i, i)).shouldHaveExitValue(0);
            }

            // Create the keychain
            LOG_MSG("Creating keychain: " + KEYCHAIN_FILE);
            ProcessTools.executeCommand("sh", "-c", String.format("security create-keychain" +
                    " -p %s %s", PWD, KEYCHAIN_FILE)).shouldHaveExitValue(0);

            // Unlock the keychain
            LOG_MSG("Unlock keychain: " + KEYCHAIN_FILE);
            ProcessTools.executeCommand("sh", "-c", String.format("security unlock-keychain" +
                    " -p %s %s", PWD, KEYCHAIN_FILE)).shouldHaveExitValue(0);

            // Import the key pairs from the PKCS12 keystore into the keychain
            // The '-A' option is used to lower the keychain's access controls
            LOG_MSG("Importing the key pairs from " + PKCS12_KEYSTORE
                    + " to " + KEYCHAIN_FILE);
            ProcessTools.executeCommand("sh", "-c", String.format("security import %s -k %s" +
                    " -f pkcs12 -P %s -A", PKCS12_KEYSTORE, KEYCHAIN_FILE, PWD)).shouldHaveExitValue(0);

            // Generate a 2048-bit RSA keypair and import into the keychain
            // Its private key is configured with non-default key usage settings
            ProcessTools.executeCommand("sh", "-c", String.format("certtool ca k=%s " +
                    "<<EOF\n" +
                    "test\n" +
                    "r\n" +
                    "2048\n" +
                    "y\n" +
                    "b\n" +
                    "s\n" +
                    "y\n" +
                    "A\n" +
                    "US\n" +
                    "A\n" +
                    "A\n" +
                    "\n" +
                    "\n" +
                    "y\n" +
                    "EOF", KEYCHAIN_FILE)).shouldHaveExitValue(0);

            // Adjust the keychain search order to add KEYCHAIN_FILE to top
            try (PrintStream printStream = new PrintStream(TEMPORARY_FILE)) {
                printStream.println("\"" + KEYCHAIN_FILE + "\"");
                printStream.println(ProcessTools.executeCommand("sh", "-c", "security list-keychains")
                        .shouldHaveExitValue(0).getOutput());
            }
            ProcessTools.executeCommand("sh", "-c", String.format("security list-keychains -s %s",
                    ProcessTools.executeCommand("sh", "-c", String.format("xargs < %s",
                            TEMPORARY_FILE)).getOutput()));
            LOG_MSG("Keychain search order:");
            ProcessTools.executeCommand("sh", "-c", "security list-keychains");

            // Recount the number of private key entries in the Keychain keystore
            // 3 private keys imported from PKCS12, 1 private key generated by 'certtool'
            Files.deleteIfExists(Paths.get(TEMPORARY_FILE));
            try (PrintStream printStream = new PrintStream(TEMPORARY_FILE)) {
                SecurityTools.keytool(DEFAULT_KEYTOOL).shouldHaveExitValue(0)
                        .outputTo(printStream);
            }
            int newPrivateKeyCount = countOccurrences(TEMPORARY_FILE, PRIVATE_KEY_ENTRY);
            LOG_MSG("Found " + newPrivateKeyCount + " private key entries in " +
                    "the Keychain keystore");
            if (newPrivateKeyCount < (oldPrivateKeyCount + 4)) {
                throw new RuntimeException("Error: expected more private key entries in the " +
                        "Keychain keystore");
            }

            // Export a private key from the keychain (without supplying a password)
            // Access controls have already been lowered (see 'security import ... -A' above)
            LOG_MSG("Exporting a private key from the keychain");
            KeyStore ks = KeyStore.getInstance("KeychainStore");
            ks.load(null, null);
            Key key = ks.getKey("CN0", null);
            if (key instanceof PrivateKey) {
                LOG_MSG("Exported " + key.getAlgorithm() + " private key from CN0");
            } else {
                throw new RuntimeException("Error exporting private key from keychain");
            }
        } finally {
            // Reset earlier keychain list
            ProcessTools.executeCommand("sh", "-c", String.format("security list-keychains -s %s",
                    ProcessTools.executeCommand("sh", "-c", String.format("xargs < %s",
                            USER_KEYCHAIN_LIST)).getOutput()));

            deleteTestTempFilesIfExists();
        }
    }

    private static void deleteTestTempFilesIfExists() throws Throwable {
        Files.deleteIfExists(Paths.get(USER_KEYCHAIN_LIST));
        Files.deleteIfExists(Paths.get(PKCS12_KEYSTORE));
        if (Files.exists(Paths.get(KEYCHAIN_FILE))) {
            ProcessTools.executeCommand("sh", "-c", String.format("security delete-keychain" +
                    " %s", KEYCHAIN_FILE)).shouldHaveExitValue(0);
        }
        Files.deleteIfExists(Paths.get(TEMPORARY_FILE));
    }

    private static int countOccurrences(String filePath, String word) throws IOException {
        int count = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains(word)) {
                    count++;
                }
            }
        }

        return count;
    }

    private static void LOG_MSG(String msg) {
        // ProcessTools and SecurityTools log a lot of messages so pretty format
        // messages logged from this test
        System.out.println();
        System.out.println("==> " + msg);
    }
}
