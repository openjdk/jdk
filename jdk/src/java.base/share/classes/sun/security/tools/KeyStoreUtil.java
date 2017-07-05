/*
 * Copyright (c) 2005, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package sun.security.tools;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

import java.io.StreamTokenizer;
import java.io.StringReader;
import java.net.URL;

import java.security.KeyStore;

import java.security.cert.X509Certificate;
import java.text.Collator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import sun.security.util.PropertyExpander;

/**
 * <p> This class provides several utilities to <code>KeyStore</code>.
 *
 * @since 1.6.0
 */
public class KeyStoreUtil {

    private KeyStoreUtil() {
        // this class is not meant to be instantiated
    }

    private static final String JKS = "jks";

    private static final Collator collator = Collator.getInstance();
    static {
        // this is for case insensitive string comparisons
        collator.setStrength(Collator.PRIMARY);
    };

    /**
     * Returns true if the certificate is self-signed, false otherwise.
     */
    public static boolean isSelfSigned(X509Certificate cert) {
        return signedBy(cert, cert);
    }

    public static boolean signedBy(X509Certificate end, X509Certificate ca) {
        if (!ca.getSubjectX500Principal().equals(end.getIssuerX500Principal())) {
            return false;
        }
        try {
            end.verify(ca.getPublicKey());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Returns true if KeyStore has a password. This is true except for
     * MSCAPI KeyStores
     */
    public static boolean isWindowsKeyStore(String storetype) {
        return storetype.equalsIgnoreCase("Windows-MY")
                || storetype.equalsIgnoreCase("Windows-ROOT");
    }

    /**
     * Returns standard-looking names for storetype
     */
    public static String niceStoreTypeName(String storetype) {
        if (storetype.equalsIgnoreCase("Windows-MY")) {
            return "Windows-MY";
        } else if(storetype.equalsIgnoreCase("Windows-ROOT")) {
            return "Windows-ROOT";
        } else {
            return storetype.toUpperCase(Locale.ENGLISH);
        }
    }

    /**
     * Returns the keystore with the configured CA certificates.
     */
    public static KeyStore getCacertsKeyStore()
        throws Exception
    {
        String sep = File.separator;
        File file = new File(System.getProperty("java.home") + sep
                             + "lib" + sep + "security" + sep
                             + "cacerts");
        if (!file.exists()) {
            return null;
        }
        KeyStore caks = null;
        try (FileInputStream fis = new FileInputStream(file)) {
            caks = KeyStore.getInstance(JKS);
            caks.load(fis, null);
        }
        return caks;
    }

    public static char[] getPassWithModifier(String modifier, String arg,
                                             java.util.ResourceBundle rb) {
        if (modifier == null) {
            return arg.toCharArray();
        } else if (collator.compare(modifier, "env") == 0) {
            String value = System.getenv(arg);
            if (value == null) {
                System.err.println(rb.getString(
                        "Cannot.find.environment.variable.") + arg);
                return null;
            } else {
                return value.toCharArray();
            }
        } else if (collator.compare(modifier, "file") == 0) {
            try {
                URL url = null;
                try {
                    url = new URL(arg);
                } catch (java.net.MalformedURLException mue) {
                    File f = new File(arg);
                    if (f.exists()) {
                        url = f.toURI().toURL();
                    } else {
                        System.err.println(rb.getString(
                                "Cannot.find.file.") + arg);
                        return null;
                    }
                }

                try (BufferedReader br =
                     new BufferedReader(new InputStreamReader(
                         url.openStream()))) {
                    String value = br.readLine();

                    if (value == null) {
                        return new char[0];
                    }

                    return value.toCharArray();
                }
            } catch (IOException ioe) {
                System.err.println(ioe);
                return null;
            }
        } else {
            System.err.println(rb.getString("Unknown.password.type.") +
                    modifier);
            return null;
        }
    }

    /**
     * Parses a option line likes
     *    -genkaypair -dname "CN=Me"
     * and add the results into a list
     * @param list the list to fill into
     * @param s the line
     */
    private static void parseArgsLine(List<String> list, String s)
            throws IOException, PropertyExpander.ExpandException {
        StreamTokenizer st = new StreamTokenizer(new StringReader(s));

        st.resetSyntax();
        st.whitespaceChars(0x00, 0x20);
        st.wordChars(0x21, 0xFF);
        // Everything is a word char except for quotation and apostrophe
        st.quoteChar('"');
        st.quoteChar('\'');

        while (true) {
            if (st.nextToken() == StreamTokenizer.TT_EOF) {
                break;
            }
            list.add(PropertyExpander.expand(st.sval));
        }
    }

    /**
     * Prepends matched options from a pre-configured options file.
     * @param tool the name of the tool, can be "keytool" or "jarsigner"
     * @param file the pre-configured options file
     * @param c1 the name of the command, with the "-" prefix,
     *        must not be null
     * @param c2 the alternative command name, with the "-" prefix,
     *        null if none. For example, "genkey" is alt name for
     *        "genkeypair". A command can only have one alt name now.
     * @param args existing arguments
     * @return arguments combined
     * @throws IOException if there is a file I/O or format error
     * @throws PropertyExpander.ExpandException
     *         if there is a property expansion error
     */
    public static String[] expandArgs(String tool, String file,
                    String c1, String c2, String[] args)
            throws IOException, PropertyExpander.ExpandException {

        List<String> result = new ArrayList<>();
        Properties p = new Properties();
        p.load(new FileInputStream(file));

        String s = p.getProperty(tool + ".all");
        if (s != null) {
            parseArgsLine(result, s);
        }

        // Cannot provide both -genkey and -genkeypair
        String s1 = p.getProperty(tool + "." + c1.substring(1));
        String s2 = null;
        if (c2 != null) {
            s2 = p.getProperty(tool + "." + c2.substring(1));
        }
        if (s1 != null && s2 != null) {
            throw new IOException("Cannot have both " + c1 + " and "
                    + c2 + " as pre-configured options");
        }
        if (s1 == null) {
            s1 = s2;
        }
        if (s1 != null) {
            parseArgsLine(result, s1);
        }

        if (result.isEmpty()) {
            return args;
        } else {
            result.addAll(Arrays.asList(args));
            return result.toArray(new String[result.size()]);
        }
    }
}
