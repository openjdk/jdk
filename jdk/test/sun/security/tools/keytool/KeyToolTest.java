/*
 * Copyright 2005-2007 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 *
 *
 * @summary Testing keytool
 * @author weijun.wang
 *
 * Run through autotest.sh and manualtest.sh
 *
 * Testing non-PKCS11 keystores:
 *       echo | java -Dfile KeyToolTest
 *
 * Testing NSS PKCS11 keystores:
 *       # testing NSS
 *       # make sure the NSS db files are in current directory and writable
 *       echo | java -Dnss -Dnss.lib=/path/to/libsoftokn3.so KeyToolTest
 *
 * Testing Solaris Cryptography Framework PKCS11 keystores:
 *       # make sure you've already run pktool and set test12 as pin
 *       echo | java -Dsolaris KeyToolTest
 *
 * ATTENTION:
 * Exception in thread "main" java.security.ProviderException: sun.security.pkcs11.wrapper.PKCS11Exception: CKR_KEY_SIZE_RANGE
 *       at sun.security.pkcs11.P11Signature.engineSign(P11Signature.java:420)
 *       ...
 * Caused by: sun.security.pkcs11.wrapper.PKCS11Exception: CKR_KEY_SIZE_RANGE
 *       at sun.security.pkcs11.wrapper.PKCS11.C_SignFinal(Native Method)
 *       at sun.security.pkcs11.P11Signature.engineSign(P11Signature.java:391)
 *       ...
 * been observed. Possibly a Solaris bug
 *
 * ATTENTION:
 * NSS PKCS11 config file are changed, DSA not supported now.
 */

import java.security.KeyStore;
import java.util.Locale;
import java.util.MissingResourceException;
import sun.security.tools.KeyTool;
import sun.security.x509.*;
import java.io.*;

public class KeyToolTest {

    // The stdout and stderr outputs after a keytool run
    String out;
    String err;

    // the output of println() in KeyTool.run
    String ex;

    String lastInput = "", lastCommand = "";
    private static final boolean debug =
        System.getProperty("debug") != null;

    static final String NSS_P11_ARG =
            "-keystore NONE -storetype PKCS11 -providerName SunPKCS11-nss -providerClass sun.security.pkcs11.SunPKCS11 -providerArg p11-nss.txt ";
    static final String NSS_SRC_P11_ARG =
            "-srckeystore NONE -srcstoretype PKCS11 -srcproviderName SunPKCS11-nss -providerClass sun.security.pkcs11.SunPKCS11 -providerArg p11-nss.txt ";
    static final String NZZ_P11_ARG =
            "-keystore NONE -storetype PKCS11 -providerName SunPKCS11-nzz -providerClass sun.security.pkcs11.SunPKCS11 -providerArg p11-nzz.txt ";
    static final String NZZ_SRC_P11_ARG =
            "-srckeystore NONE -srcstoretype PKCS11 -srcproviderName SunPKCS11-nzz -providerClass sun.security.pkcs11.SunPKCS11 -providerArg p11-nzz.txt ";
    static final String SUN_P11_ARG = "-keystore NONE -storetype PKCS11 ";
    static final String SUN_SRC_P11_ARG = "-srckeystore NONE -srcstoretype PKCS11 ";

    String p11Arg, srcP11Arg;

    /** Creates a new instance of KeyToolTest */
    KeyToolTest() {
        // so that there is "Warning" and not translated into other language
        Locale.setDefault(Locale.US);
    }

    /**
     * Helper, removes a file
     */
    void remove(String filename) {
        if (debug) {
            System.err.println("Removing " + filename);
        }
        new File(filename).delete();
        if (new File(filename).exists()) {
            throw new RuntimeException("Error deleting " + filename);
        }
    }

    /**
     * Run a set of keytool command with given terminal input.
     * @param input the terminal inputs, the characters typed by human
     *        if <code>cmd</code> is running on a terminal
     * @param cmd the argument of a keytool command line
     * @throws if keytool goes wrong in some place
     */
    void test(String input, String cmd) throws Exception {
        lastInput = input;
        lastCommand = cmd;

        // "X" is appened so that we can precisely test how input is consumed
        HumanInputStream in = new HumanInputStream(input+"X");
        test(in, cmd);
        // make sure the input string is no more no less
        if(in.read() != 'X' || in.read() != -1)
            throw new Exception("Input not consumed exactly");
    }

    void test(InputStream in, String cmd) throws Exception {

        // save the original 3 streams
        if (debug) {
            System.err.println(cmd);
        } else {
            System.err.print(".");
        }
        PrintStream p1 = System.out;
        PrintStream p2 = System.err;
        InputStream i1 = System.in;

        ByteArrayOutputStream b1 = new ByteArrayOutputStream();
        ByteArrayOutputStream b2 = new ByteArrayOutputStream();

        try {
            System.setIn(in);
            System.setOut(new PrintStream(b1));
            System.setErr(new PrintStream(b2));

            // since System.in is overrided, the KeyTool.main() method will
            // never block at user input

            // use -debug so that KeyTool.main() will throw an Exception
            // instead of calling System.exit()
            KeyTool.main(("-debug "+cmd).split("\\s+"));
        } finally {
            out = b1.toString();
            err = b2.toString();
            ex = out;   // now it goes to System.out
            System.setIn(i1);
            System.setOut(p1);
            System.setErr(p2);
        }
    }

    /**
     * Call this method if you expect test(input, cmd) should go OK
     */
    void testOK(String input, String cmd) throws Exception {
        try {
            test(input, cmd);
        } catch(Exception e) {
            afterFail(input, cmd, "OK");
            throw e;
        }
    }

    /**
     * Call this method if you expect test(input, cmd) should fail and throw
     * an exception
     */
    void testFail(String input, String cmd) throws Exception {
        boolean ok;
        try {
            test(input, cmd);
            ok = true;
        } catch(Exception e) {
            if (e instanceof MissingResourceException) {
                ok = true;
            } else {
                ok = false;
            }
        }
        if(ok) {
            afterFail(input, cmd, "FAIL");
            throw new RuntimeException();
        }
    }

    /**
     * Call this method if you expect test(input, cmd) should go OK
     */
    void testOK(InputStream is, String cmd) throws Exception {
        try {
            test(is, cmd);
        } catch(Exception e) {
            afterFail("", cmd, "OK");
            throw e;
        }
    }

    /**
     * Call this method if you expect test(input, cmd) should fail and throw
     * an exception
     */
    void testFail(InputStream is, String cmd) throws Exception {
        boolean ok;
        try {
            test(is, cmd);
            ok = true;
        } catch(Exception e) {
            ok = false;
        }
        if(ok) {
            afterFail("", cmd, "FAIL");
            throw new RuntimeException();
        }
    }

    /**
     * Call this method if you just want to run the command and does
     * not care if it succeeds or fails.
     */
    void testAnyway(String input, String cmd) {
        try {
            test(input, cmd);
        } catch(Exception e) {
            ;
        }
    }

    /**
     * Helper method, print some output after a test does not do as expected
     */
    void afterFail(String input, String cmd, String should) {
        System.err.println("\nTest fails for the command ---\n" +
                "keytool " + cmd + "\nOr its debug version ---\n" +
                "keytool -debug " + cmd);

        System.err.println("The command result should be " + should +
                ", but it's not. Try run the command manually and type" +
                " these input into it: ");
        char[] inputChars = input.toCharArray();

        for (int i=0; i<inputChars.length; i++) {
            char ch = inputChars[i];
            if (ch == '\n') System.err.print("ENTER ");
            else if (ch == ' ') System.err.print("SPACE ");
            else System.err.print(ch + " ");
        }
        System.err.println("");

        System.err.println("ERR is:\n"+err);
        System.err.println("OUT is:\n"+out);
    }

    void assertTrue(boolean bool, String msg) {
        if(!bool) {
            afterFail(lastInput, lastCommand, "TRUE");
            throw new RuntimeException(msg);
        }
    }

    /**
     * Helper method, load a keystore
     * @param file file for keystore, null or "NONE" for PKCS11
     * @pass password for the keystore
     * @type keystore type
     * @returns the KeyStore object
     * @exception Exception if anything goes wrong
     */
    KeyStore loadStore(String file, String pass, String type) throws Exception {
        KeyStore ks = KeyStore.getInstance(type);
        FileInputStream is = null;
        if (file != null && !file.equals("NONE")) {
            is = new FileInputStream(file);
        }
        ks.load(is, pass.toCharArray());
        is.close();
        return ks;
    }

    /**
     * The test suite.
     * Maybe it's better to put this outside the KeyToolTest class
     */
    void testAll() throws Exception {
        KeyStore ks;

        remove("x.jks");
        remove("x.jceks");
        remove("x.p12");
        remove("x2.jceks");
        remove("x2.jks");
        remove("x.jks.p1.cert");

        // name changes: genkeypair, importcert, exportcert
        remove("x.jks");
        remove("x.jks.p1.cert");
        testOK("", "-keystore x.jks -storepass changeit -keypass changeit -genkeypair -alias p1 -dname CN=olala");
        testOK("", "-keystore x.jks -storepass changeit -exportcert -alias p1 -file x.jks.p1.cert");
        ks = loadStore("x.jks", "changeit", "JKS");
        assertTrue(ks.getKey("p1", "changeit".toCharArray()) != null,
            "key not DSA");
        assertTrue(new File("x.jks.p1.cert").exists(), "p1 export err");
        testOK("", "-keystore x.jks -storepass changeit -delete -alias p1");
        testOK("y\n", "-keystore x.jks -storepass changeit -importcert -alias c1 -file x.jks.p1.cert");  // importcert, prompt for Yes/No
        testOK("", "-keystore x.jks -storepass changeit -importcert -alias c2 -file x.jks.p1.cert -noprompt"); // importcert, -noprompt
        ks = loadStore("x.jks", "changeit", "JKS");
        assertTrue(ks.getCertificate("c1") != null, "import c1 err");

        // v3
        byte[] encoded = ks.getCertificate("c1").getEncoded();
        X509CertImpl certImpl = new X509CertImpl(encoded);
        assertTrue(certImpl.getVersion() == 3, "Version is not 3");

        // changealias and keyclone
        testOK("", "-keystore x.jks -storepass changeit -keypass changeit -genkeypair -alias p1 -dname CN=olala");
        testOK("changeit\n", "-keystore x.jks -changealias -alias p1 -destalias p11");
        testOK("changeit\n", "-keystore x.jks -changealias -alias c1 -destalias c11");
        testOK("changeit\n\n", "-keystore x.jks -keyclone -alias p11 -destalias p111"); // press ENTER when prompt for p111's keypass
        ks = loadStore("x.jks", "changeit", "JKS");
        assertTrue(!ks.containsAlias("p1"), "there is no p1");
        assertTrue(!ks.containsAlias("c1"), "there is no c1");
        assertTrue(ks.containsAlias("p11"), "there is p11");
        assertTrue(ks.containsAlias("c11"), "there is c11");
        assertTrue(ks.containsAlias("p111"), "there is p111");

        // genSecKey
        remove("x.jceks");
        testOK("changeit\nchangeit\n\n", "-keystore x.jceks -storetype JCEKS -genseckey -alias s1"); // DES, no need keysize
        testFail("changeit\n\n", "-keystore x.jceks -storetype JCEKS -genseckey -alias s11 -keysize 128"); // DES, keysize cannot be 128
        testOK("changeit\n\n", "-keystore x.jceks -storetype JCEKS -genseckey -keyalg DESede -alias s2"); // DESede. no need keysize
        testFail("changeit\n\n", "-keystore x.jceks -storetype AES -genseckey -keyalg Rijndael -alias s3"); // AES, need keysize
        testOK("changeit\n\n", "-keystore x.jceks -storetype JCEKS -genseckey -keyalg AES -alias s3 -keysize 128");
                // about keypass
        testOK("\n", "-keystore x.jceks -storetype JCEKS -storepass changeit -genseckey -alias s4"); // can accept storepass
        testOK("keypass\nkeypass\n", "-keystore x.jceks -storetype JCEKS -storepass changeit -genseckey -alias s5"); // or a new one
        testOK("bad\n\bad\nkeypass\nkeypass\n", "-keystore x.jceks -storetype JCEKS -storepass changeit -genseckey -alias s6"); // keypass must be valid (prompt 3 times)
        testFail("bad\n\bad\nbad\n", "-keystore x.jceks -storetype JCEKS -storepass changeit -genseckey -alias s7"); // keypass must be valid (prompt 3 times)
        testFail("bad\n\bad\nbad\nkeypass\n", "-keystore x.jceks -storetype JCEKS -storepass changeit -genseckey -alias s7"); // keypass must be valid (prompt 3 times)
        ks = loadStore("x.jceks", "changeit", "JCEKS");
        assertTrue(ks.getKey("s1", "changeit".toCharArray()).getAlgorithm().equalsIgnoreCase("DES"), "s1 is DES");
        assertTrue(ks.getKey("s1", "changeit".toCharArray()).getEncoded().length == 8,  "DES is 56");
        assertTrue(ks.getKey("s2", "changeit".toCharArray()).getEncoded().length == 24,  "DESede is 168");
        assertTrue(ks.getKey("s2", "changeit".toCharArray()).getAlgorithm().equalsIgnoreCase("DESede"), "s2 is DESede");
        assertTrue(ks.getKey("s3", "changeit".toCharArray()).getAlgorithm().equalsIgnoreCase("AES"), "s3 is AES");
        assertTrue(ks.getKey("s4", "changeit".toCharArray()).getAlgorithm().equalsIgnoreCase("DES"), "s4 is DES");
        assertTrue(ks.getKey("s5", "keypass".toCharArray()).getAlgorithm().equalsIgnoreCase("DES"), "s5 is DES");
        assertTrue(ks.getKey("s6", "keypass".toCharArray()).getAlgorithm().equalsIgnoreCase("DES"), "s6 is DES");
        assertTrue(!ks.containsAlias("s7"), "s7 not created");

        // maybe we needn't test this, one day JKS will support SecretKey
        //testFail("changeit\nchangeit\n", "-keystore x.jks -genseckey -keyalg AES -alias s3 -keysize 128");

        // importKeyStore
        remove("x.jks");
        remove("x.jceks");
        testOK("changeit\nchangeit\n\n", "-keystore x.jceks -storetype JCEKS -genkeypair -alias p1 -dname CN=Olala"); // create 2 entries...
        testOK("", "-keystore x.jceks -storetype JCEKS -storepass changeit -importcert -alias c1 -file x.jks.p1.cert -noprompt"); // ...
        ks = loadStore("x.jceks", "changeit", "JCEKS");
        assertTrue(ks.size() == 2, "2 entries in JCEKS");
        // import, shouldn't mention destalias/srckeypass/destkeypass if srcalias is no given
        testFail("changeit\nchangeit\n", "-importkeystore -srckeystore x.jceks -srcstoretype JCEKS -destkeystore x.jks -deststoretype JKS -destalias pp");
        testFail("changeit\nchangeit\n", "-importkeystore -srckeystore x.jceks -srcstoretype JCEKS -destkeystore x.jks -deststoretype JKS -srckeypass changeit");
        testFail("changeit\nchangeit\n", "-importkeystore -srckeystore x.jceks -srcstoretype JCEKS -destkeystore x.jks -deststoretype JKS -destkeypass changeit");
        // normal import
        testOK("changeit\nchangeit\nchangeit\n", "-importkeystore -srckeystore x.jceks -srcstoretype JCEKS -destkeystore x.jks -deststoretype JKS");
        ks = loadStore("x.jks", "changeit", "JKS");
        assertTrue(ks.size() == 2, "2 entries in JKS");
        // import again, type yes to overwrite old entries
        testOK("changeit\nchangeit\ny\ny\n", "-importkeystore -srckeystore x.jceks -srcstoretype JCEKS -destkeystore x.jks -deststoretype JKS");
        ks = loadStore("x.jks", "changeit", "JKS");
        // import again, specify -nopromt
        testOK("changeit\nchangeit\n", "-importkeystore -srckeystore x.jceks -srcstoretype JCEKS -destkeystore x.jks -deststoretype JKS -noprompt");
        assertTrue(err.indexOf("Warning") != -1, "noprompt will warn");
        ks = loadStore("x.jks", "changeit", "JKS");
        assertTrue(ks.size() == 2, "2 entries in JKS");
        // import again, type into new aliases when prompted
        testOK("changeit\nchangeit\n\ns1\n\ns2\n", "-importkeystore -srckeystore x.jceks -srcstoretype JCEKS -destkeystore x.jks -deststoretype JKS");
        ks = loadStore("x.jks", "changeit", "JKS");
        assertTrue(ks.size() == 4, "4 entries in JKS");

        // importkeystore single
        remove("x.jks");
        testOK("changeit\nchangeit\nchangeit\n", "-importkeystore -srckeystore x.jceks -srcstoretype JCEKS -destkeystore x.jks -deststoretype JKS -srcalias p1"); // normal
        ks = loadStore("x.jks", "changeit", "JKS");
        assertTrue(ks.size() == 1, "1 entries in JKS");
        testOK("changeit\nchangeit\ny\n", "-importkeystore -srckeystore x.jceks -srcstoretype JCEKS -destkeystore x.jks -deststoretype JKS -srcalias p1"); // overwrite
        ks = loadStore("x.jks", "changeit", "JKS");
        assertTrue(ks.size() == 1, "1 entries in JKS");
        testOK("changeit\nchangeit\n", "-importkeystore -srckeystore x.jceks -srcstoretype JCEKS -destkeystore x.jks -deststoretype JKS -srcalias p1 -noprompt"); // noprompt
        ks = loadStore("x.jks", "changeit", "JKS");
        assertTrue(ks.size() == 1, "1 entries in JKS");
        testOK("changeit\nchangeit\n", "-importkeystore -srckeystore x.jceks -srcstoretype JCEKS -destkeystore x.jks -deststoretype JKS -srcalias p1 -destalias p2"); // rename
        ks = loadStore("x.jks", "changeit", "JKS");
        assertTrue(ks.size() == 2, "2 entries in JKS");
        testOK("changeit\nchangeit\n\nnewalias\n", "-importkeystore -srckeystore x.jceks -srcstoretype JCEKS -destkeystore x.jks -deststoretype JKS -srcalias p1"); // another rename
        ks = loadStore("x.jks", "changeit", "JKS");
        assertTrue(ks.size() == 3, "3 entries in JKS");

        // importkeystore single, different keypass
        remove("x.jks");
        testOK("changeit\nkeypass\nkeypass\n", "-keystore x.jceks -storetype JCEKS -genkeypair -alias p2 -dname CN=Olala"); // generate entry with different keypass
        testOK("changeit\nchangeit\nchangeit\nkeypass\n", "-importkeystore -srckeystore x.jceks -srcstoretype JCEKS -destkeystore x.jks -deststoretype JKS -srcalias p2"); // prompt
        ks = loadStore("x.jks", "changeit", "JKS");
        assertTrue(ks.size() == 1, "1 entries in JKS");
        testOK("changeit\nchangeit\nkeypass\n", "-importkeystore -srckeystore x.jceks -srcstoretype JCEKS -destkeystore x.jks -deststoretype JKS -srcalias p2 -destalias p3 -destkeypass keypass2"); // diff destkeypass
        ks = loadStore("x.jks", "changeit", "JKS");
        assertTrue(ks.size() == 2, "2 entries in JKS");
        assertTrue(ks.getKey("p2", "keypass".toCharArray()) != null, "p2 has old password");
        assertTrue(ks.getKey("p3", "keypass2".toCharArray()) != null, "p3 has new password");

        // importkeystore single, cert
        remove("x.jks");
        testOK("changeit\nchangeit\nchangeit\n", "-importkeystore -srckeystore x.jceks -srcstoretype JCEKS -destkeystore x.jks -deststoretype JKS -srcalias c1"); // normal
        testOK("changeit\n\n", "-importkeystore -srckeystore x.jceks -srcstoretype JCEKS -destkeystore x.jks -deststoretype JKS -srcalias c1 -destalias c2");   // in fact srcstorepass can be ignored
        assertTrue(err.indexOf("WARNING") != -1, "But will warn");
        testOK("changeit\n\ny\n", "-importkeystore -srckeystore x.jceks -srcstoretype JCEKS -destkeystore x.jks -deststoretype JKS -srcalias c1 -destalias c2");   // 2nd import, press y to overwrite ...
        testOK("changeit\n\n\nc3\n", "-importkeystore -srckeystore x.jceks -srcstoretype JCEKS -destkeystore x.jks -deststoretype JKS -srcalias c1 -destalias c2");   // ... or rename
        ks = loadStore("x.jks", "changeit", "JKS");
        assertTrue(ks.size() == 3, "3 entries in JKS"); // c1, c2, c3

        // importkeystore, secretkey
        remove("x.jks");
        testOK("changeit\n\n", "-keystore x.jceks -storetype JCEKS -genseckey -alias s1"); // create SecretKeyEntry
        testOK("changeit\n\n", "-keystore x.jceks -storetype JCEKS -genseckey -alias s2"); // create SecretKeyEntry
        testOK("changeit\n", "-keystore x.jceks -storetype JCEKS -delete -alias p2"); // remove the keypass!=storepass one
        ks = loadStore("x.jceks", "changeit", "JCEKS");
        assertTrue(ks.size() == 4, "4 entries in JCEKS");       // p1, c1, s1, s2
        testOK("changeit\nchangeit\nchangeit\n", "-importkeystore -srckeystore x.jceks -srcstoretype JCEKS -destkeystore x.jks -deststoretype JKS -srcalias s1"); // normal
        assertTrue(err.indexOf("not imported") != -1, "Not imported");
        assertTrue(err.indexOf("Cannot store non-PrivateKeys") != -1, "Not imported");

        remove("x.jks");
        testOK("\n\n", "-srcstorepass changeit -deststorepass changeit -importkeystore -srckeystore x.jceks -srcstoretype JCEKS -destkeystore x.jks -deststoretype JKS"); // normal
        assertTrue(err.indexOf("s1 not") != -1, "s1 not");
        assertTrue(err.indexOf("s2 not") != -1, "s2 not");
        assertTrue(err.indexOf("c1 success") != -1, "c1 success");
        assertTrue(err.indexOf("p1 success") != -1, "p1 success");
        testOK("yes\n", "-srcstorepass changeit -deststorepass changeit -importkeystore -srckeystore x.jceks -srcstoretype JCEKS -destkeystore x.jks -deststoretype JKS"); // normal
        // maybe c1 or p1 has been imported before s1 or s2 is touched, anyway we know yesNo is only asked once.

        // pkcs12
        remove("x.jks");
        testFail("changeit\nchangeit\n", "-keystore x.jks -genkeypair -alias p1 -dname CN=olala"); // JKS prompt for keypass
        remove("x.jks");
        testOK("changeit\nchangeit\n\n", "-keystore x.jks -genkeypair -alias p1 -dname CN=olala"); // just type ENTER means keypass=storepass
        remove("x.p12");
        testOK("", "-keystore x.p12 -storetype PKCS12 -storepass changeit -genkeypair -alias p0 -dname CN=olala"); // PKCS12 only need storepass
        testOK("changeit\n", "-keystore x.p12 -storetype PKCS12 -genkeypair -alias p1 -dname CN=olala");
        testOK("changeit\n", "-keystore x.p12 -keypass changeit -storetype PKCS12 -genkeypair -alias p3 -dname CN=olala"); // when specify keypass, make sure keypass==storepass...
        assertTrue(err.indexOf("Warning") == -1, "PKCS12 silent when keypass == storepass");
        testOK("changeit\n", "-keystore x.p12 -keypass another -storetype PKCS12 -genkeypair -alias p2 -dname CN=olala"); // otherwise, print a warning
        assertTrue(err.indexOf("Warning") != -1, "PKCS12 warning when keypass != storepass");
        testFail("", "-keystore x.p12 -storepass changeit -storetype PKCS12 -keypasswd -new changeit -alias p3"); // no -keypasswd for PKCS12
        testOK("", "-keystore x.p12 -storepass changeit -storetype PKCS12 -changealias -alias p3 -destalias p33");
        testOK("", "-keystore x.p12 -storepass changeit -storetype PKCS12 -keyclone -alias p33 -destalias p3");

        // pkcs12
        remove("x.p12");
        testOK("", "-keystore x.p12 -storetype PKCS12 -storepass changeit -genkeypair -alias p0 -dname CN=olala"); // PKCS12 only need storepass
        testOK("", "-storepass changeit -keystore x.p12 -storetype PKCS12 -genkeypair -alias p1 -dname CN=olala");
        testOK("", "-storepass changeit -keystore x.p12 -keypass changeit -storetype PKCS12 -genkeypair -alias p3 -dname CN=olala"); // when specify keypass, make sure keypass==storepass...
        assertTrue(err.indexOf("Warning") == -1, "PKCS12 silent when keypass == storepass");
        testOK("", "-storepass changeit -keystore x.p12 -keypass another -storetype PKCS12 -genkeypair -alias p2 -dname CN=olala"); // otherwise, print a warning
        assertTrue(err.indexOf("Warning") != -1, "PKCS12 warning when keypass != storepass");

        remove("x.jks");
        remove("x.jceks");
        remove("x.p12");
        remove("x2.jceks");
        remove("x2.jks");
        remove("x.jks.p1.cert");
    }

    void testPKCS11() throws Exception {
        KeyStore ks;
        // pkcs11, the password maybe different and maybe PKCS11 is not supported

        // in case last test is not executed successfully
        testAnyway("", p11Arg + "-storepass test12 -delete -alias p1");
        testAnyway("", p11Arg + "-storepass test12 -delete -alias p2");
        testAnyway("", p11Arg + "-storepass test12 -delete -alias p3");
        testAnyway("", p11Arg + "-storepass test12 -delete -alias nss");

        testOK("", p11Arg + "-storepass test12 -list");
        assertTrue(out.indexOf("Your keystore contains 0 entries") != -1, "*** MAKE SURE YOU HAVE NO ENTRIES IN YOUR PKCS11 KEYSTORE BEFORE THIS TEST ***");

        testOK("", p11Arg + "-storepass test12 -genkeypair -alias p1 -dname CN=olala");
        testOK("test12\n", p11Arg + "-genkeypair -alias p2 -dname CN=olala2");
        testFail("test12\n", p11Arg + "-keypass test12 -genkeypair -alias p3 -dname CN=olala3"); // cannot provide keypass for PKCS11
        testFail("test12\n", p11Arg + "-keypass nonsense -genkeypair -alias p3 -dname CN=olala3"); // cannot provide keypass for PKCS11

        testOK("", p11Arg + "-storepass test12 -list");
        assertTrue(out.indexOf("Your keystore contains 2 entries") != -1, "2 entries in p11");

        testOK("test12\n", p11Arg + "-alias p1 -changealias -destalias p3");
        testOK("", p11Arg + "-storepass test12 -list -alias p3");
        testFail("", p11Arg + "-storepass test12 -list -alias p1");

        testOK("test12\n", p11Arg + "-alias p3 -keyclone -destalias p1");
        testFail("", p11Arg + "-storepass test12 -list -alias p3");   // in PKCS11, keyclone will delete old
        testOK("", p11Arg + "-storepass test12 -list -alias p1");

        testFail("test12\n", p11Arg + "-alias p1 -keypasswd -new another"); // cannot change password for PKCS11

        testOK("", p11Arg + "-storepass test12 -list");
        assertTrue(out.indexOf("Your keystore contains 2 entries") != -1, "2 entries in p11");

        testOK("", p11Arg + "-storepass test12 -delete -alias p1");
        testOK("", p11Arg + "-storepass test12 -delete -alias p2");

        testOK("", p11Arg + "-storepass test12 -list");
        assertTrue(out.indexOf("Your keystore contains 0 entries") != -1, "*** MAKE SURE YOU HAVE NO ENTRIES IN YOUR PKCS11 KEYSTORE BEFORE THIS TEST ***");
    }

    void testPKCS11ImportKeyStore() throws Exception {

        KeyStore ks;
        testOK("", p11Arg + "-storepass test12 -genkeypair -alias p1 -dname CN=olala");
        testOK("test12\n", p11Arg + "-genkeypair -alias p2 -dname CN=olala2");
        // test importkeystore for pkcs11

        remove("x.jks");
        // pkcs11 -> jks
        testOK("changeit\nchangeit\ntest12\n", srcP11Arg + "-importkeystore -destkeystore x.jks -deststoretype JKS -srcalias p1");
        assertTrue(err.indexOf("not imported") != -1, "cannot import key without destkeypass");
        ks = loadStore("x.jks", "changeit", "JKS");
        assertTrue(!ks.containsAlias("p1"), "p1 is not imported");

        testOK("changeit\ntest12\n", srcP11Arg + "-importkeystore -destkeystore x.jks -deststoretype JKS -srcalias p1 -destkeypass changeit");
        testOK("changeit\ntest12\n", srcP11Arg + "-importkeystore -destkeystore x.jks -deststoretype JKS -srcalias p2 -destkeypass changeit");
        ks = loadStore("x.jks", "changeit", "JKS");
        assertTrue(ks.containsAlias("p1"), "p1 is imported");
        assertTrue(ks.containsAlias("p2"), "p2 is imported");
        // jks -> pkcs11
        testOK("", p11Arg + "-storepass test12 -delete -alias p1");
        testOK("", p11Arg + "-storepass test12 -delete -alias p2");
        testOK("test12\nchangeit\n", p11Arg + "-importkeystore -srckeystore x.jks -srcstoretype JKS");
        testOK("", p11Arg + "-storepass test12 -list -alias p1");
        testOK("", p11Arg + "-storepass test12 -list -alias p2");
        testOK("", p11Arg + "-storepass test12 -list");
        assertTrue(out.indexOf("Your keystore contains 2 entries") != -1, "2 entries in p11");
        // clean up
        testOK("", p11Arg + "-storepass test12 -delete -alias p1");
        testOK("", p11Arg + "-storepass test12 -delete -alias p2");
        testOK("", p11Arg + "-storepass test12 -list");
        assertTrue(out.indexOf("Your keystore contains 0 entries") != -1, "empty p11");

        remove("x.jks");
    }

    // The sqeTest reflects the test suggested by judy.gao and bill.situ at
    // /net/sqesvr-nfs/global/nfs/sec/ws_6.0_int/security/src/SecurityTools/Keytool
    //
    void sqeTest() throws Exception {
        FileOutputStream fos = new FileOutputStream("badkeystore");
        for (int i=0; i<100; i++) {
            fos.write(i);
        }
        fos.close();

        sqeCsrTest();
        sqePrintcertTest();
        sqeDeleteTest();
        sqeExportTest();
        sqeGenkeyTest();
        sqeImportTest();
        sqeKeyclonetest();
        sqeKeypasswdTest();
        sqeListTest();
        sqeSelfCertTest();
        sqeStorepassTest();

        remove("badkeystore");
    }

    // Import: cacert, prompt, trusted, non-trusted, bad chain, not match
    void sqeImportTest() throws Exception {
        KeyStore ks;
        remove("x.jks");
        testOK("", "-keystore x.jks -storepass changeit -keypass changeit -genkeypair -dname CN=olala");
        testOK("", "-keystore x.jks -storepass changeit -exportcert -file x.jks.p1.cert");
        /* deleted */ testOK("", "-keystore x.jks -storepass changeit -delete -alias mykey");
        testOK("", "-keystore x.jks -storepass changeit -importcert -file x.jks.p1.cert -noprompt");
        /* deleted */ testOK("", "-keystore x.jks -storepass changeit -delete -alias mykey");
        testOK("yes\n", "-keystore x.jks -storepass changeit -importcert -file x.jks.p1.cert");
        ks = loadStore("x.jks", "changeit", "JKS");
        assertTrue(ks.containsAlias("mykey"), "imported");
        /* deleted */ testOK("", "-keystore x.jks -storepass changeit -delete -alias mykey");
        testOK("\n", "-keystore x.jks -storepass changeit -importcert -file x.jks.p1.cert");
        ks = loadStore("x.jks", "changeit", "JKS");
        assertTrue(!ks.containsAlias("mykey"), "imported");
        testOK("no\n", "-keystore x.jks -storepass changeit -importcert -file x.jks.p1.cert");
        ks = loadStore("x.jks", "changeit", "JKS");
        assertTrue(!ks.containsAlias("mykey"), "imported");
        testFail("no\n", "-keystore x.jks -storepass changeit -importcert -file nonexist");
        testFail("no\n", "-keystore x.jks -storepass changeit -importcert -file x.jks");
        remove("x.jks");
    }
    // keyclone: exist. nonexist err, cert err, dest exist, misc
    void sqeKeyclonetest() throws Exception {
        remove("x.jks");
        testOK("", "-keystore x.jks -storepass changeit -keypass changeit -genkeypair -dname CN=olala");
        testOK("", "-keystore x.jks -storepass changeit -keypass changeit -new newpass -keyclone -dest p0"); // new pass
        testOK("\n", "-keystore x.jks -storepass changeit -keypass changeit -keyclone -dest p1"); // new pass
        testOK("\n", "-keystore x.jks -storepass changeit -keyclone -dest p2");
        testFail("\n", "-keystore x.jks -storepass changeit -keyclone -dest p2");
        testFail("\n", "-keystore x.jks -storepass changeit -keyclone -dest p3 -alias noexist");
        // no cert
        testOK("", "-keystore x.jks -storepass changeit -exportcert -file x.jks.p1.cert");
        testOK("", "-keystore x.jks -storepass changeit -delete -alias mykey");
        testOK("", "-keystore x.jks -storepass changeit -importcert -file x.jks.p1.cert -noprompt");
        testFail("", "-keystore x.jks -storepass changeit -keypass changeit -new newpass -keyclone -dest p0"); // new pass
        remove("x.jks");
    }
    // keypasswd: exist, short, nonexist err, cert err, misc
    void sqeKeypasswdTest() throws Exception {
        remove("x.jks");
        testOK("", "-keystore x.jks -storepass changeit -keypass changeit -genkeypair -dname CN=olala");
        testOK("", "-keystore x.jks -storepass changeit -keypass changeit -keypasswd -new newpass");
        /*change back*/ testOK("", "-keystore x.jks -storepass changeit -keypass newpass -keypasswd -new changeit");
        testOK("newpass\nnewpass\n", "-keystore x.jks -storepass changeit -keypass changeit -keypasswd");
        /*change back*/ testOK("", "-keystore x.jks -storepass changeit -keypass newpass -keypasswd -new changeit");
        testOK("new\nnew\nnewpass\nnewpass\n", "-keystore x.jks -storepass changeit -keypass changeit -keypasswd");
        /*change back*/ testOK("", "-keystore x.jks -storepass changeit -keypass newpass -keypasswd -new changeit");
        testOK("", "-keystore x.jks -storepass changeit -keypasswd -new newpass");
        /*change back*/ testOK("", "-keystore x.jks -storepass changeit -keypass newpass -keypasswd -new changeit");
        testOK("changeit\n", "-keystore x.jks -keypasswd -new newpass");
        /*change back*/ testOK("", "-keystore x.jks -storepass changeit -keypass newpass -keypasswd -new changeit");
        testFail("", "-keystore x.jks -storepass badpass -keypass changeit -keypasswd -new newpass");
        testFail("", "-keystore x.jks -storepass changeit -keypass bad -keypasswd -new newpass");
        // no cert
        testOK("", "-keystore x.jks -storepass changeit -exportcert -file x.jks.p1.cert");
        testOK("", "-keystore x.jks -storepass changeit -delete -alias mykey");
        testOK("", "-keystore x.jks -storepass changeit -importcert -file x.jks.p1.cert -noprompt");
        testFail("", "-keystore x.jks -storepass changeit -keypass changeit -keypasswd -new newpass");
        // diff pass
        testOK("", "-keystore x.jks -storepass changeit -delete -alias mykey");
        testOK("", "-keystore x.jks -storepass changeit -keypass keypass -genkeypair -dname CN=olala");
        testFail("", "-keystore x.jks -storepass changeit -keypasswd -new newpass");
        testOK("keypass\n", "-keystore x.jks -storepass changeit -keypasswd -new newpass");
        // i hate those misc test
        remove("x.jks");
    }
    // list: -f -alias, exist, nonexist err; otherwise, check all shows, -rfc shows more, and misc
    void sqeListTest() throws Exception {
        remove("x.jks");
        testOK("", "-keystore x.jks -storepass changeit -keypass changeit -genkeypair -dname CN=olala");
        testOK("", "-keystore x.jks -storepass changeit -list");
        testOK("", "-keystore x.jks -storepass changeit -list -alias mykey");
        testFail("", "-keystore x.jks -storepass changeit -list -alias notexist");
        testFail("", "-keystore x.jks -storepass badpass -list -alias mykey");
        testOK("", "-keystore x.jks -storepass changeit -keypass badpass -list -alias mykey");  // keypass ignore
        testOK("\n", "-keystore x.jks -list");
        assertTrue(err.indexOf("WARNING") != -1, "no storepass");
        testOK("changeit\n", "-keystore x.jks -list");
        assertTrue(err.indexOf("WARNING") == -1, "has storepass");
        testFail("badpass\n", "-keystore x.jks -list");
        // misc
        testFail("", "-keystore aa\\bb//cc -storepass changeit -list");
        testFail("", "-keystore nonexisting -storepass changeit -list");
        testFail("", "-keystore badkeystore -storepass changeit -list");
        remove("x.jks");
    }
    // selfcert: exist, non-exist err, cert err, sig..., dname, wrong keypass, misc
    void sqeSelfCertTest() throws Exception {
        remove("x.jks");
        testOK("", "-keystore x.jks -storepass changeit -keypass changeit -genkeypair -dname CN=olala");
        testOK("", "-keystore x.jks -storepass changeit -selfcert");
        testOK("", "-keystore x.jks -storepass changeit -keypass changeit -selfcert");
        testFail("", "-keystore x.jks -storepass changeit -keypass changeit -selfcert -alias nonexisting"); // not exist
        testOK("", "-keystore x.jks -storepass changeit -keypass changeit -selfcert -dname CN=NewName");
        testFail("", "-keystore x.jks -storepass changeit -keypass changeit -selfcert -sigalg MD5withRSA"); // sig not compatible
        testFail("", "-keystore x.jks -storepass wrong -keypass changeit -selfcert"); // bad pass
        testFail("", "-keystore x.jks -storepass changeit -keypass wrong -selfcert"); // bad pass
        //misc
        testFail("", "-keystore nonexist -storepass changeit -keypass changeit -selfcert");
        testFail("", "-keystore aa//dd\\gg -storepass changeit -keypass changeit -selfcert");
        // diff pass
        remove("x.jks");
        testOK("", "-keystore x.jks -storepass changeit -keypass keypass -genkeypair -dname CN=olala");
        testFail("", "-keystore x.jks -storepass changeit -selfcert");
        testOK("keypass\n", "-keystore x.jks -storepass changeit -selfcert");

        testOK("", "-keystore x.jks -storepass changeit -exportcert -file x.jks.p1.cert");
        testOK("", "-keystore x.jks -storepass changeit -delete -alias mykey");
        testOK("", "-keystore x.jks -storepass changeit -importcert -file x.jks.p1.cert -noprompt");
        testFail("", "-keystore x.jks -storepass changeit -selfcert");  // certentry cannot do selfcert
        remove("x.jks");
    }
    // storepass: bad old, short new, misc
    void sqeStorepassTest() throws Exception {
        remove("x.jks");
        testOK("", "-keystore x.jks -storepass changeit -keypass changeit -genkeypair -dname CN=olala");
        testOK("", "-storepasswd -keystore x.jks -storepass changeit -new newstore"); // all in arg
        /* Change back */ testOK("", "-storepasswd -keystore x.jks -storepass newstore -new changeit");
        testOK("changeit\nnewstore\nnewstore\n", "-storepasswd -keystore x.jks"); // all not in arg, new twice
        /* Change back */ testOK("", "-storepasswd -keystore x.jks -storepass newstore -new changeit");
        testOK("changeit\n", "-storepasswd -keystore x.jks -new newstore"); // new in arg
        /* Change back */ testOK("", "-storepasswd -keystore x.jks -storepass newstore -new changeit");
        testOK("newstore\nnewstore\n", "-storepasswd -keystore x.jks -storepass changeit"); // old in arg
        /* Change back */ testOK("", "-storepasswd -keystore x.jks -storepass newstore -new changeit");
        testOK("new\nnew\nnewstore\nnewstore\n", "-storepasswd -keystore x.jks -storepass changeit"); // old in arg
        /* Change back */ testOK("", "-storepasswd -keystore x.jks -storepass newstore -new changeit");
        testFail("", "-storepasswd -keystore x.jks -storepass badold -new newstore"); // bad old
        testFail("", "-storepasswd -keystore x.jks -storepass changeit -new new"); // short new
        // misc
        testFail("", "-storepasswd -keystore nonexist -storepass changeit -new newstore"); // non exist
        testFail("", "-storepasswd -keystore badkeystore -storepass changeit -new newstore"); // bad file
        testFail("", "-storepasswd -keystore aa\\bb//cc//dd -storepass changeit -new newstore"); // bad file
        remove("x.jks");
    }

    void sqeGenkeyTest() throws Exception {

        remove("x.jks");
        testOK("", "-keystore x.jks -storepass changeit -keypass changeit -genkeypair -dname CN=olala");
        testFail("", "-keystore x.jks -storepass changeit -keypass changeit -genkeypair -dname CN=olala");
        testOK("", "-keystore x.jks -storepass changeit -keypass changeit -genkeypair -dname CN=olala -alias newentry");
        testFail("", "-keystore x.jks -storepass changeit -keypass changeit -genkeypair -dname CN=olala -alias newentry");
        testOK("", "-keystore x.jks -storepass changeit -keypass changeit -genkeypair -dname CN=olala -keyalg DSA -alias n1");
        testOK("", "-keystore x.jks -storepass changeit -keypass changeit -genkeypair -dname CN=olala -keyalg RSA -alias n2");
        testFail("", "-keystore x.jks -storepass changeit -keypass changeit -genkeypair -dname CN=olala -keyalg NoSuchAlg -alias n3");
        testFail("", "-keystore x.jks -storepass changeit -keypass changeit -genkeypair -dname CN=olala -keysize 56 -alias n4");
        testFail("", "-keystore x.jks -storepass changeit -keypass changeit -genkeypair -dname CN=olala -keysize 999 -alias n5");
        testOK("", "-keystore x.jks -storepass changeit -keypass changeit -genkeypair -dname CN=olala -keysize 512 -alias n6");
        testOK("", "-keystore x.jks -storepass changeit -keypass changeit -genkeypair -dname CN=olala -keysize 1024 -alias n7");
        testFail("", "-keystore x.jks -storepass changeit -keypass changeit -genkeypair -dname CN=olala -sigalg NoSuchAlg -alias n8");
        testOK("", "-keystore x.jks -storepass changeit -keypass changeit -genkeypair -dname CN=olala -keyalg RSA -sigalg MD2withRSA -alias n9");
        testOK("", "-keystore x.jks -storepass changeit -keypass changeit -genkeypair -dname CN=olala -keyalg RSA -sigalg MD5withRSA -alias n10");
        testOK("", "-keystore x.jks -storepass changeit -keypass changeit -genkeypair -dname CN=olala -keyalg RSA -sigalg SHA1withRSA -alias n11");
        testFail("", "-keystore aa\\bb//cc\\dd -storepass changeit -keypass changeit -genkeypair -dname CN=olala -keyalg RSA -sigalg NoSuchAlg -alias n12");
        testFail("", "-keystore badkeystore -storepass changeit -keypass changeit -genkeypair -dname CN=olala -alias n14");
        testFail("", "-keystore x.jks -storepass badpass -keypass changeit -genkeypair -dname CN=olala -alias n16");
        testFail("", "-keystore x.jks -storepass changeit -keypass changeit -genkeypair -dname CNN=olala -alias n17");
        remove("x.jks");
    }

    void sqeExportTest() throws Exception {
        remove("x.jks");
        testFail("", "-keystore x.jks -storepass changeit -export -file mykey.cert -alias mykey"); // nonexist
        testOK("", "-keystore x.jks -storepass changeit -keypass changeit -genkeypair -dname CN=olala");
        testOK("", "-keystore x.jks -storepass changeit -export -file mykey.cert -alias mykey");
        testOK("", "-keystore x.jks -storepass changeit -delete -alias mykey");
        testOK("", "-keystore x.jks -storepass changeit -import -file mykey.cert -noprompt -alias c1");
        testOK("", "-keystore x.jks -storepass changeit -export -file mykey.cert2 -alias c1");
        testFail("", "-keystore aa\\bb//cc\\dd -storepass changeit -export -file mykey.cert2 -alias c1");
        testFail("", "-keystore nonexistkeystore -storepass changeit -export -file mykey.cert2 -alias c1");
        testFail("", "-keystore badkeystore -storepass changeit -export -file mykey.cert2 -alias c1");
        testFail("", "-keystore x.jks -storepass badpass -export -file mykey.cert2 -alias c1");
        remove("mykey.cert");
        remove("mykey.cert2");
        remove("x.jks");
    }

    void sqeDeleteTest() throws Exception {
        remove("x.jks");
        testFail("", "-keystore x.jks -storepass changeit -delete -alias mykey"); // nonexist
        testOK("", "-keystore x.jks -storepass changeit -keypass changeit -genkeypair -dname CN=olala");
        testOK("", "-keystore x.jks -storepass changeit -delete -alias mykey");
        testOK("", "-keystore x.jks -storepass changeit -keypass changeit -genkeypair -dname CN=olala");
        testFail("", "-keystore aa\\bb//cc\\dd -storepass changeit -delete -alias mykey"); // keystore name illegal
        testFail("", "-keystore nonexistkeystore -storepass changeit -delete -alias mykey"); // keystore not exist
        testFail("", "-keystore badkeystore -storepass changeit -delete -alias mykey"); // keystore invalid
        testFail("", "-keystore x.jks -storepass xxxxxxxx -delete -alias mykey"); // wrong pass
        remove("x.jks");
    }

    void sqeCsrTest() throws Exception {
        remove("x.jks");
        remove("x.jks.p1.cert");
        remove("csr1");
        // PrivateKeyEntry can do certreq
        testOK("", "-keystore x.jks -storepass changeit -keypass changeit -genkeypair -dname CN=olala");
        testOK("", "-keystore x.jks -storepass changeit -certreq -file csr1 -alias mykey");
        testOK("", "-keystore x.jks -storepass changeit -certreq -file csr1");
        testOK("", "-keystore x.jks -storepass changeit -certreq -file csr1 -sigalg SHA1withDSA");
        testFail("", "-keystore x.jks -storepass changeit -certreq -file csr1 -sigalg MD5withRSA"); // unmatched sigalg
        // misc test
        testFail("", "-keystore x.jks -storepass badstorepass -certreq -file csr1"); // bad storepass
        testOK("changeit\n", "-keystore x.jks -certreq -file csr1"); // storepass from terminal
        testFail("\n", "-keystore x.jks -certreq -file csr1"); // must provide storepass
        testFail("", "-keystore x.jks -storepass changeit -keypass badkeypass -certreq -file csr1"); // bad keypass
        testFail("", "-keystore x.jks -storepass changeit -certreq -file aa\\bb//cc\\dd");  // bad filepath
        testFail("", "-keystore noexistks -storepass changeit -certreq -file csr1"); // non-existing keystore
        // Try the RSA private key
        testOK("", "-keystore x.jks -storepass changeit -delete -alias mykey");
        testOK("", "-keystore x.jks -storepass changeit -keypass changeit -genkeypair -dname CN=olala -keyalg RSA");
        testOK("", "-keystore x.jks -storepass changeit -certreq -file csr1 -alias mykey");
        testOK("", "-keystore x.jks -storepass changeit -certreq -file csr1");
        testFail("", "-keystore x.jks -storepass changeit -certreq -file csr1 -sigalg SHA1withDSA"); // unmatched sigalg
        testOK("", "-keystore x.jks -storepass changeit -certreq -file csr1 -sigalg MD5withRSA");
        // TrustedCertificateEntry cannot do certreq
        testOK("", "-keystore x.jks -storepass changeit -exportcert -file x.jks.p1.cert");
        testOK("", "-keystore x.jks -storepass changeit -delete -alias mykey");
        testOK("", "-keystore x.jks -storepass changeit -importcert -file x.jks.p1.cert -noprompt");
        testFail("", "-keystore x.jks -storepass changeit -certreq -file csr1 -alias mykey");
        testFail("", "-keystore x.jks -storepass changeit -certreq -file csr1");
        remove("x.jks");
        remove("x.jks.p1.cert");
        remove("csr1");
    }

    void sqePrintcertTest() throws Exception {
        remove("x.jks");
        remove("mykey.cert");
        testOK("", "-keystore x.jks -storepass changeit -keypass changeit -genkeypair -dname CN=olala");
        testOK("", "-keystore x.jks -storepass changeit -export -file mykey.cert -alias mykey");
        testFail("", "-printcert -file badkeystore");
        testFail("", "-printcert -file a/b/c/d");
        testOK("", "-printcert -file mykey.cert");
        FileInputStream fin = new FileInputStream("mykey.cert");
        testOK(fin, "-printcert");
        fin.close();
        remove("x.jks");
        remove("mykey.cert");
    }

    void i18nTest() throws Exception {
        //   1.  keytool -help
        remove("x.jks");
        try {
            test("", "-help");
            assertTrue(false, "Cannot come here");
        } catch(RuntimeException e) {
            assertTrue(e.getMessage().indexOf("NO ERROR, SORRY") != -1, "No error");
        }
        //   2. keytool -genkey -v -keysize 512 Enter "a" for the keystore password. Check error (password too short). Enter "password" for the keystore password. Hit 'return' for "first and last name", "organizational unit", "City", "State", and "Country Code". Type "yes" when they ask you if everything is correct. Type 'return' for new key password.
        testOK("a\npassword\npassword\nMe\nHere\nNow\nPlace\nPlace\nUS\nyes\n\n", "-genkey -v -keysize 512 -keystore x.jks");
        //   3. keytool -list -v -storepass password
        testOK("", "-list -v -storepass password -keystore x.jks");
        //   4. keytool -list -v Type "a" for the keystore password. Check error (wrong keystore password).
        testFail("a\n", "-list -v -keystore x.jks");
        assertTrue(ex.indexOf("password was incorrect") != -1, "");
        //   5. keytool -genkey -v -keysize 512 Enter "password" as the password. Check error (alias 'mykey' already exists).
        testFail("password\n", "-genkey -v -keysize 512 -keystore x.jks");
        assertTrue(ex.indexOf("alias <mykey> already exists") != -1, "");
        //   6. keytool -genkey -v -keysize 512 -alias mykey2 -storepass password Hit 'return' for "first and last name", "organizational unit", "City", "State", and "Country Code". Type "yes" when they ask you if everything is correct. Type 'return' for new key password.
        testOK("\n\n\n\n\n\nyes\n\n", "-genkey -v -keysize 512 -alias mykey2 -storepass password -keystore x.jks");
        //   7. keytool -list -v Type 'password' for the store password.
        testOK("password\n", "-list -v -keystore x.jks");
        //   8. keytool -keypasswd -v -alias mykey2 -storepass password Type "a" for the new key password. Type "aaaaaa" for the new key password. Type "bbbbbb" when re-entering the new key password. Type "a" for the new key password. Check Error (too many failures).
        testFail("a\naaaaaa\nbbbbbb\na\n", "-keypasswd -v -alias mykey2 -storepass password -keystore x.jks");
        assertTrue(ex.indexOf("Too many failures - try later") != -1, "");
        //   9. keytool -keypasswd -v -alias mykey2 -storepass password Type "aaaaaa" for the new key password. Type "aaaaaa" when re-entering the new key password.
        testOK("aaaaaa\naaaaaa\n", "-keypasswd -v -alias mykey2 -storepass password -keystore x.jks");
        //  10. keytool -selfcert -v -alias mykey -storepass password
        testOK("", "-selfcert -v -alias mykey -storepass password -keystore x.jks");
        //  11. keytool -list -v -storepass password
        testOK("", "-list -v -storepass password -keystore x.jks");
        //  12. keytool -export -v -alias mykey -file cert -storepass password
        remove("cert");
        testOK("", "-export -v -alias mykey -file cert -storepass password -keystore x.jks");
        //  13. keytool -import -v -file cert -storepass password Check error (Certificate reply and cert are the same)
        testFail("", "-import -v -file cert -storepass password -keystore x.jks");
        assertTrue(ex.indexOf("Certificate reply and certificate in keystore are identical") != -1, "");
        //  14. keytool -printcert -file cert
        testOK("", "-printcert -file cert -keystore x.jks");
        remove("cert");
        //  15. keytool -list -storepass password -provider sun.security.provider.Sun
        testOK("", "-list -storepass password -provider sun.security.provider.Sun -keystore x.jks");

        //Error tests

        //   1. keytool -storepasswd -storepass password -new abc Check error (password too short)
        testFail("", "-storepasswd -storepass password -new abc");
        assertTrue(ex.indexOf("New password must be at least 6 characters") != -1, "");
        // Changed, no NONE needed now
        //   2. keytool -list -storetype PKCS11 Check error (-keystore must be NONE)
        //testFail("", "-list -storetype PKCS11");
        //assertTrue(err.indexOf("keystore must be NONE") != -1, "");
        //   3. keytool -storepasswd -storetype PKCS11 -keystore NONE Check error (unsupported operation)
        testFail("", "-storepasswd -storetype PKCS11 -keystore NONE");
        assertTrue(ex.indexOf("UnsupportedOperationException") != -1, "");
        //   4. keytool -keypasswd -storetype PKCS11 -keystore NONE Check error (unsupported operation)
        testFail("", "-keypasswd -storetype PKCS11 -keystore NONE");
        assertTrue(ex.indexOf("UnsupportedOperationException") != -1, "");
        //   5. keytool -list -protected -storepass password Check error (password can not be specified with -protected)
        testFail("", "-list -protected -storepass password -keystore x.jks");
        assertTrue(ex.indexOf("if -protected is specified, then") != -1, "");
        //   6. keytool -keypasswd -protected -keypass password Check error (password can not be specified with -protected)
        testFail("", "-keypasswd -protected -keypass password -keystore x.jks");
        assertTrue(ex.indexOf("if -protected is specified, then") != -1, "");
        //   7. keytool -keypasswd -protected -new password Check error (password can not be specified with -protected)
        testFail("", "-keypasswd -protected -new password -keystore x.jks");
        assertTrue(ex.indexOf("if -protected is specified, then") != -1, "");
        remove("x.jks");
    }

    void i18nPKCS11Test() throws Exception {
        //PKCS#11 tests

        //   1. sccs edit cert8.db key3.db
        //Runtime.getRuntime().exec("/usr/ccs/bin/sccs edit cert8.db key3.db");
        testOK("", p11Arg + "-storepass test12 -genkey -alias genkey -dname cn=genkey -keysize 512 -keyalg rsa");
        testOK("", p11Arg + "-storepass test12 -list");
        testOK("", p11Arg + "-storepass test12 -list -alias genkey");
        testOK("", p11Arg + "-storepass test12 -certreq -alias genkey -file genkey.certreq");
        testOK("", p11Arg + "-storepass test12 -export -alias genkey -file genkey.cert");
        testOK("", "-printcert -file genkey.cert");
        testOK("", p11Arg + "-storepass test12 -selfcert -alias genkey -dname cn=selfCert");
        testOK("", p11Arg + "-storepass test12 -list -alias genkey -v");
        assertTrue(out.indexOf("Owner: CN=selfCert") != -1, "");
        //(check that cert subject DN is [cn=selfCert])
        testOK("", p11Arg + "-storepass test12 -delete -alias genkey");
        testOK("", p11Arg + "-storepass test12 -list");
        assertTrue(out.indexOf("Your keystore contains 0 entries") != -1, "");
        //(check for empty database listing)
        //Runtime.getRuntime().exec("/usr/ccs/bin/sccs unedit cert8.db key3.db");
        remove("genkey.cert");
        remove("genkey.certreq");
        //  12. sccs unedit cert8.db key3.db
    }

    // tesing new option -srcProviderName
    void sszzTest() throws Exception {
        testAnyway("", NSS_P11_ARG+"-delete -alias nss -storepass test12");
        testAnyway("", NZZ_P11_ARG+"-delete -alias nss -storepass test12");
        testOK("", NSS_P11_ARG+"-genkeypair -dname CN=NSS -alias nss -storepass test12");
        testOK("", NSS_SRC_P11_ARG + NZZ_P11_ARG +
                "-importkeystore -srcstorepass test12 -deststorepass test12");
        testAnyway("", NSS_P11_ARG+"-delete -alias nss -storepass test12");
        testAnyway("", NZZ_P11_ARG+"-delete -alias nss -storepass test12");
    }

    public static void main(String[] args) throws Exception {
        // first test if HumanInputStream really acts like a human being
        HumanInputStream.test();
        KeyToolTest t = new KeyToolTest();

        if (System.getProperty("file") != null) {
            t.sqeTest();
            t.testAll();
            t.i18nTest();
        }

        if (System.getProperty("nss") != null) {
            t.srcP11Arg = NSS_SRC_P11_ARG;
            t.p11Arg = NSS_P11_ARG;

            t.testPKCS11();

            // FAIL:
            // 1. we still don't have srcprovidername yet
            // 2. cannot store privatekey into NSS keystore
            //    java.security.KeyStoreException: sun.security.pkcs11.wrapper.PKCS11Exception: CKR_TEMPLATE_INCOMPLETE.
            //t.testPKCS11ImportKeyStore();

            t.i18nPKCS11Test();
            //FAIL: currently PKCS11-NSS does not support 2 NSS KeyStores to be loaded at the same time
            //t.sszzTest();
        }

        if (System.getProperty("solaris") != null) {
            // For Solaris Cryptography Framework
            t.srcP11Arg = SUN_SRC_P11_ARG;
            t.p11Arg = SUN_P11_ARG;
            t.testPKCS11();
            t.testPKCS11ImportKeyStore();
            t.i18nPKCS11Test();
        }

        System.out.println("Test pass!!!");
    }
}

class TestException extends Exception {
    public TestException(String e) {
        super(e);
    }
}

/**
 * HumanInputStream tries to act like a human sitting in front of a computer
 * terminal typing on the keyboard while the keytool program is running.
 *
 * keytool has called InputStream.read() and BufferedReader.readLine() in
 * various places. a call to B.readLine() will try to buffer as much input as
 * possible. Thus, a trivial InputStream will find it impossible to feed
 * anything to I.read() after a B.readLine() call.
 *
 * This is why i create HumanInputStream, which will only send a single line
 * to B.readLine(), no more, no less, and the next I.read() can have a chance
 * to read the exact character right after "\n".
 *
 * I don't know why HumanInputStream works.
 */
class HumanInputStream extends InputStream {
    byte[] src;
    int pos;
    int length;
    boolean inLine;
    int stopIt;

    public HumanInputStream(String input) {
        src = input.getBytes();
        pos = 0;
        length = src.length;
        stopIt = 0;
        inLine = false;
    }

    // the trick: when called through read(byte[], int, int),
    // return -1 twice after "\n"

    @Override public int read() throws IOException {
        int re;
        if(pos < length) {
            re = src[pos];
            if(inLine) {
                if(stopIt > 0) {
                    stopIt--;
                    re = -1;
                } else {
                    if(re == '\n') {
                        stopIt = 2;
                    }
                    pos++;
                }
            } else {
                pos++;
            }
        } else {
            re = -1;//throw new IOException("NO MORE TO READ");
        }
        //if (re < 32) System.err.printf("[%02d]", re);
        //else System.err.printf("[%c]", (char)re);
        return re;
    }
    @Override public int read(byte[] buffer, int offset, int len) {
        inLine = true;
        try {
            int re = super.read(buffer, offset, len);
            return re;
        } catch(Exception e) {
            throw new RuntimeException("HumanInputStream error");
        } finally {
            inLine = false;
        }
    }
    @Override public int available() {
        if(pos < length) return 1;
        return 0;
    }

    // test part
    static void assertTrue(boolean bool) {
        if(!bool)
            throw new RuntimeException();
    }

    public static void test() throws Exception {

        class Tester {
            HumanInputStream is;
            BufferedReader reader;
            Tester(String s) {
                is = new HumanInputStream(s);
                reader = new BufferedReader(new InputStreamReader(is));
            }

            // three kinds of test method
            // 1. read byte by byte from InputStream
            void testStreamReadOnce(int expection) throws Exception {
                assertTrue(is.read() == expection);
            }
            void testStreamReadMany(String expection) throws Exception {
                char[] keys = expection.toCharArray();
                for(int i=0; i<keys.length; i++) {
                    assertTrue(is.read() == keys[i]);
                }
            }
            // 2. read a line with a newly created Reader
            void testReaderReadline(String expection) throws Exception {
                String s = new BufferedReader(new InputStreamReader(is)).readLine();
                if(s == null) assertTrue(expection == null);
                else assertTrue(s.equals(expection));
            }
            // 3. read a line with the old Reader
            void testReaderReadline2(String expection) throws Exception  {
                String s = reader.readLine();
                if(s == null) assertTrue(expection == null);
                else assertTrue(s.equals(expection));
            }
        }

        Tester test;

        test = new Tester("111\n222\n\n444\n\n");
        test.testReaderReadline("111");
        test.testReaderReadline("222");
        test.testReaderReadline("");
        test.testReaderReadline("444");
        test.testReaderReadline("");
        test.testReaderReadline(null);

        test = new Tester("111\n222\n\n444\n\n");
        test.testReaderReadline2("111");
        test.testReaderReadline2("222");
        test.testReaderReadline2("");
        test.testReaderReadline2("444");
        test.testReaderReadline2("");
        test.testReaderReadline2(null);

        test = new Tester("111\n222\n\n444\n\n");
        test.testReaderReadline2("111");
        test.testReaderReadline("222");
        test.testReaderReadline2("");
        test.testReaderReadline2("444");
        test.testReaderReadline("");
        test.testReaderReadline2(null);

        test = new Tester("1\n2");
        test.testStreamReadMany("1\n2");
        test.testStreamReadOnce(-1);

        test = new Tester("12\n234");
        test.testStreamReadOnce('1');
        test.testReaderReadline("2");
        test.testStreamReadOnce('2');
        test.testReaderReadline2("34");
        test.testReaderReadline2(null);

        test = new Tester("changeit\n");
        test.testStreamReadMany("changeit\n");
        test.testReaderReadline(null);

        test = new Tester("changeit\nName\nCountry\nYes\n");
        test.testStreamReadMany("changeit\n");
        test.testReaderReadline("Name");
        test.testReaderReadline("Country");
        test.testReaderReadline("Yes");
        test.testReaderReadline(null);

        test = new Tester("Me\nHere\n");
        test.testReaderReadline2("Me");
        test.testReaderReadline2("Here");
    }
}
