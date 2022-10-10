/*
 * Copyright (c) 2000, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4348369 8076069 8294994
 * @summary keytool i18n compliant
 * @author charlie lai
 * @modules java.base/sun.security.tools.keytool
 * @library /test/lib
 * @run main/manual/othervm i18n en
 */

/*
 * @test
 * @bug 4348369 8076069 8294994
 * @summary keytool i18n compliant
 * @author charlie lai
 * @modules java.base/sun.security.tools.keytool
 * @library /test/lib
 * @run main/manual/othervm i18n de
 */

/*
 * @test
 * @bug 4348369 8076069 8294994
 * @summary keytool i18n compliant
 * @author charlie lai
 * @modules java.base/sun.security.tools.keytool
 * @library /test/lib
 * @run main/manual/othervm i18n ja
 */

/*
 * @test
 * @bug 4348369 8076069 8294994
 * @summary keytool i18n compliant
 * @author charlie lai
 * @modules java.base/sun.security.tools.keytool
 * @library /test/lib
 * @run main/manual/othervm i18n zh CN
 */

import javax.swing.*;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Locale;

public class i18n {
    private static final String[][] TABLE = new String[][]{
            {"-help", "All the output in this test should be in ${LANG}. "
                    + "Otherwise, the test failed."},

            {"-genkeypair -keyalg DSA -v -keysize 512 "
                    + "-dname cn=Name,ou=Java,o=Oracle,l=City,s=State,c=Country "
                    + "-storepass a "
                    + "-keypass a "
                    + "-keystore ./i18n.keystore",
                    "Output in ${LANG}. Check keytool error: java.lang.Exception: "
                            + "Keystore password must be at least 6 characters."},

            {"-genkeypair -keyalg DSA -v -keysize 512 "
                    + "-dname cn=Name,ou=Java,o=Oracle,l=City,s=State,c=Country "
                    + "-storepass password "
                    + "-keypass password "
                    + "-keystore ./i18n.keystore",
                    "Output in ${LANG}. Check: generated a 512 bit DSA key pair "
                            + "for CN=Name, OU=Java, O=Oracle, L=City, ST=State "
                            + "C=Country."},

            {"-list -v -storepass password -keystore ./i18n.keystore",
                    "Output in ${LANG}. Check: contains 1 keystore entry with "
                            + "512-bit DSA key algorithm for CN=Name, OU=Java, "
                            + "O=Oracle, L=City, ST=State C=Country."},

            {"-list -v -storepass a -keystore ./i18n.keystore",
                    "Output in ${LANG}. Check keytool error:java.io.IOException: "
                            + "keystore password was incorrect."},

            {"-genkey -keyalg DSA -v -keysize 512 "
                    + "-storepass password "
                    + "-keypass password "
                    + "-keystore ./i18n.keystore",
                    "Output in ${LANG}. Check keytool error: java.lang.Exception: "
                            + "alias 'mykey' already exists."},

            {"-genkeypair -keyalg DSA -v -keysize 512 "
                    + "-dname cn=Name,ou=Java,o=Oracle,l=City,s=State,c=Country "
                    + "-alias mykey2 "
                    + "-storepass password "
                    + "-keypass password "
                    + "-keystore ./i18n.keystore",
                    "Output in ${LANG}. Check: generated a 512 bit DSA key pair "
                            + "for CN=Name, OU=Java, O=Oracle, L=City, ST=State "
                            + "C=Country."},

            {"-list -v -storepass password -keystore ./i18n.keystore",
                    "Output in ${LANG}. Check: contains 2 keystore entries "
                            + "(alias name mykey & mykey2), both with 512-bit DSA"
                            + " key algorithm for CN=Name, OU=Java, O=Oracle, "
                            + "L=City, ST=State C=Country."},

            {"-keypasswd -v "
                    + "-alias mykey2 "
                    + "-storepass password "
                    + "-keypass password "
                    + "-new a "
                    + "-keystore ./i18n.keystore",
                    "Output in ${LANG}. Check keytool error: java.lang.Exception: "
                            + "New password must be at least 6 characters."},

            {"-keypasswd -v "
                    + "-alias mykey2 "
                    + "-storepass password "
                    + "-keypass password "
                    + "-new aaaaaa "
                    + "-keystore ./i18n.keystore",
                    "Output in ${LANG}. Check keytool error: -keypasswd "
                            + "commands not supported if -storetype is PKCS12."},

            {"-genkeypair -keyalg DSA -v -keysize 512 "
                    + "-dname cn=Name,ou=Java,o=Oracle,l=City,s=State,c=Country "
                    + "-storepass password "
                    + "-keypass password "
                    + "-keystore ./i18n.jks "
                    + "-storetype JKS",
                    "Output in ${LANG}. Check: generated a 512 bit DSA key pair "
                            + "with a JKS warning."},

            {"-keypasswd -v "
                    + "-storepass password "
                    + "-keypass password "
                    + "-new aaaaaa "
                    + "-keystore ./i18n.jks",
                    "Output in ${LANG}. Check: storing i18n.jks with a JKS warning."},

            {"-selfcert -v -alias mykey "
                    + "-storepass password "
                    + "-keypass password "
                    + "-keystore ./i18n.keystore",
                    "Output in ${LANG}. Check: generated a new certificate "
                            + "(self-signed)."},

            {"-list -v -storepass password -keystore ./i18n.keystore",
                    "Output in ${LANG}. Check: contains 2 keystore entries "
                            + "(alias name mykey & mykey2), both with 512-bit DSA"
                            + " key algorithm for CN=Name, OU=Java, O=Oracle, "
                            + "L=City, ST=State C=Country."},

            {"-export -v -alias mykey "
                    + "-file backup.keystore "
                    + "-storepass password "
                    + "-keystore ./i18n.keystore",
                    "Output in ${LANG}. Check: certificate stored in file <backup"
                            + ".keystore>."},

            {"-import -v "
                    + "-file backup.keystore "
                    + "-storepass password "
                    + "-keystore ./i18n.keystore",
                    "Output in ${LANG}. Check keytool error: reply and certificate "
                            + "in keystore are identical."},

            {"-printcert -file backup.keystore",
                    "Output in ${LANG}. Check: 512 bit DSA key pair for CN=Name,"
                            + " OU=Java, O=Oracle, L=City, ST=State C=Country."},

            {"-list -storepass password -keystore ./i18n.keystore "
                    + "-addprovider SUN",
                    "Output in ${LANG}. Check: contains 2 keystore entries "
                            + "(alias name mykey & mykey2)."},

            {"-storepasswd "
                    + "-storepass password "
                    + "-new a "
                    + "-keystore ./i18n.keystore",
                    "Output in ${LANG}. Check keytool error: java.lang.Exception: "
                            + "New password must be at least 6 characters."},

            {"-storepasswd "
                    + "-storetype PKCS11 "
                    + "-keystore NONE",
                    "Output in ${LANG}. Check keytool error: java.lang"
                            + ".UnsupportedOperationException: -storepasswd and "
                            + "-keypasswd commands not supported if -storetype is"
                            + " PKCS11."},

            {"-keypasswd "
                    + "-storetype PKCS11 "
                    + "-keystore NONE",
                    "Output in ${LANG}. Check keytool error: java.lang"
                            + ".UnsupportedOperationException: -storepasswd and "
                            + "-keypasswd commands not supported if -storetype is"
                            + " PKCS11."},

            {"-list -protected "
                    + "-storepass password "
                    + "-keystore ./i18n.keystore",
                    "Output in ${LANG}. Check keytool error: java.lang"
                            + ".IllegalArgumentException: if -protected is "
                            + "specified, then -storepass, -keypass, and -new "
                            + "must not be specified."},

            {"-keypasswd -protected "
                    + "-storepass password "
                    + "-keystore ./i18n.keystore",
                    "Output in ${LANG}. Check keytool error: java.lang"
                            + ".IllegalArgumentException: if -protected is "
                            + "specified, then -storepass, -keypass, and -new "
                            + "must not be specified."},

            {"-keypasswd -protected "
                    + "-storepass password "
                    + "-new aaaaaa "
                    + "-keystore ./i18n.keystore",
                    "Output in ${LANG}. Check keytool error: java.lang"
                            + ".IllegalArgumentException: if -protected is "
                            + "specified, then -storepass, -keypass, and -new "
                            + "must not be specified."},
    };
    private static String TEST_SRC = System.getProperty("test.src");
    private static int TIMEOUT_MS = 120000;
    private volatile boolean failed = false;
    private volatile boolean aborted = false;
    private Thread currentThread = null;

    public static class DialogBuilder {
        private JDialog dialog;
        private JTextArea instructionsText;
        private JTextArea messageText;
        private JButton pass;
        private JButton fail;

        public DialogBuilder() {
            dialog = new JDialog(new JFrame());
            dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
            instructionsText = new JTextArea("", 5, 100);

            dialog.add("North", new JScrollPane(instructionsText,
                    JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                    JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS));

            messageText = new JTextArea("", 20, 100);
            dialog.add("Center", new JScrollPane(messageText,
                    JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                    JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS));

            JPanel buttons = new JPanel();
            pass = new JButton("pass");
            pass.setActionCommand("pass");
            buttons.add("East", pass);

            fail = new JButton("fail");
            fail.setActionCommand("fail");
            buttons.add("West", fail);

            dialog.add("South", buttons);
        }

        public DialogBuilder setTitle(String title) {
            dialog.setTitle(title);
            return this;
        }

        public DialogBuilder setInstruction(String instruction) {
            instructionsText.setText("Test instructions:\n" + instruction);
            return this;
        }

        public DialogBuilder setMessage(String message) {
            messageText.setText(message);
            return this;
        }

        public DialogBuilder setPassAction(ActionListener action) {
            pass.addActionListener(action);
            return this;
        }

        public DialogBuilder setFailAction(ActionListener action) {
            fail.addActionListener(action);
            return this;
        }

        public DialogBuilder setAbortAction(Runnable action) {
            dialog.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    super.windowClosing(e);
                    action.run();
                }
            });
            return this;
        }

        public JDialog build() {
            dialog.pack();
            return dialog;
        }
    }

    public static void executeKeytool(String command) throws Exception {
        sun.security.tools.keytool.Main.main(command.split("\\s+"));
    }

    public static void main(String[] args) {
        if (args.length == 1) {
            Locale.setDefault(Locale.of(args[0]));
        } else if (args.length == 2) {
            Locale.setDefault(Locale.of(args[0], args[1]));
        }
        final String LANG = Locale.getDefault().getDisplayLanguage();

        boolean testFailed = false;
        i18n i18nTest = new i18n();

        for (String[] entry : TABLE) {
            String command = entry[0].replaceAll("\\$\\{TEST_SRC\\}", TEST_SRC);
            String instruction = entry[1].replaceAll("\\$\\{LANG\\}", LANG);

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();

            doKeytool(command, new PrintStream(buffer, true));

            testFailed |= i18nTest.validate(command, instruction, buffer.toString());
        }

        if (testFailed) {
            throw new RuntimeException("One or more tests failed.");
        }
    }

    public static void doKeytool(String command, PrintStream dest) {
        // Backups stdout and stderr.
        PrintStream origStdOut = System.out;
        PrintStream origErrOut = System.err;

        // Redirects the system output to a custom one.
        System.setOut(dest);
        System.setErr(dest);

        try {
            executeKeytool("-debug " + command);
        } catch (Exception e) {
            // Do nothing.
        } finally {
            System.setOut(origStdOut);
            System.setErr(origErrOut);
        }
    }

    public boolean validate(String command, String instruction, String message) {
        failed = false;
        currentThread = Thread.currentThread();
        JDialog dialog = new DialogBuilder()
                .setTitle("keytool " + command)
                .setInstruction(instruction)
                .setMessage(message)
                .setPassAction(e -> pass())
                .setFailAction(e -> fail())
                .setAbortAction(() -> abort())
                .build();

        SwingUtilities.invokeLater(() -> {
            try {
                dialog.setVisible(true);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        try {
            Thread.sleep(TIMEOUT_MS);
            //Timed out, so fail the test
            throw new RuntimeException(
                    "Timed out after " + TIMEOUT_MS / 1000 + " seconds");
        } catch (InterruptedException e) {
            if (aborted) {
                throw new RuntimeException("TEST ABORTED");
            }

            if (failed) {
                System.out.println(command + ": TEST FAILED");
                System.out.println(message);
            } else {
                System.out.println(command + ": TEST PASSED");
            }
        } finally {
            dialog.dispose();
        }

        return failed;
    }

    public void pass() {
        failed = false;
        currentThread.interrupt();
    }

    public void fail() {
        failed = true;
        currentThread.interrupt();
    }

    public void abort() {
        aborted = true;
        currentThread.interrupt();
    }
}
