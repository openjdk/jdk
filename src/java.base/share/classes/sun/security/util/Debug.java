/*
 * Copyright (c) 1998, 2025, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.util;

import java.io.PrintStream;
import java.math.BigInteger;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Locale;

/**
 * A utility class for debugging.
 *
 * @author Roland Schemers
 */
public class Debug {

    private String prefix;
    private static String args;

    static {
        args = System.getProperty("java.security.debug");

        String args2 = System.getProperty("java.security.auth.debug");

        if (args == null) {
            args = args2;
        } else {
            if (args2 != null)
               args = args + "," + args2;
        }

        if (args != null) {
            args = args.toLowerCase(Locale.ENGLISH);
            if (args.equals("help")) {
                Help();
            }
        }
    }

    public static void Help() {
        System.err.println();
        System.err.println("all           turn on all debugging");
        System.err.println("certpath      PKIX CertPathBuilder and");
        System.err.println("              CertPathValidator debugging");
        System.err.println("combiner      SubjectDomainCombiner debugging");
        System.err.println("gssloginconfig");
        System.err.println("              GSS LoginConfigImpl debugging");
        System.err.println("configfile    JAAS ConfigFile loading");
        System.err.println("configparser  JAAS ConfigFile parsing");
        System.err.println("jar           jar verification");
        System.err.println("logincontext  login context results");
        System.err.println("jca           JCA engine class debugging");
        System.err.println("keystore      KeyStore debugging");
        System.err.println("pcsc          Smartcard library debugging");
        System.err.println("provider      security provider debugging");
        System.err.println("pkcs11        PKCS11 session manager debugging");
        System.err.println("pkcs11keystore");
        System.err.println("              PKCS11 KeyStore debugging");
        System.err.println("pkcs12        PKCS12 KeyStore debugging");
        System.err.println("properties    Security property and configuration file debugging");
        System.err.println("sunpkcs11     SunPKCS11 provider debugging");
        System.err.println("securerandom  SecureRandom");
        System.err.println("ts            timestamping");
        System.err.println("x509          X.509 certificate debugging");
        System.err.println();
        System.err.println("The following can be used with provider:");
        System.err.println();
        System.err.println("engine=<engines>");
        System.err.println("              only dump output for the specified list");
        System.err.println("              of JCA engines. Supported values:");
        System.err.println("              Cipher, KDF, KeyAgreement, KeyGenerator,");
        System.err.println("              KeyPairGenerator, KeyStore, Mac,");
        System.err.println("              MessageDigest, SecureRandom, Signature.");
        System.err.println();
        System.err.println("The following can be used with certpath:");
        System.err.println();
        System.err.println("ocsp          dump the OCSP protocol exchanges");
        System.err.println("verbose       verbose debugging");
        System.err.println();
        System.err.println("The following can be used with x509:");
        System.err.println();
        System.err.println("ava           embed non-printable/non-escaped characters in AVA components as hex strings");
        System.err.println();
        System.err.println("Note: Separate multiple options with a comma");
        System.exit(0);
    }


    /**
     * Get a Debug object corresponding to whether or not the given
     * option is set. Set the prefix to be the same as option.
     */

    public static Debug getInstance(String option) {
        return getInstance(option, option);
    }

    /**
     * Get a Debug object corresponding to whether or not the given
     * option is set. Set the prefix to prefix.
     */
    public static Debug getInstance(String option, String prefix) {
        if (isOn(option)) {
            Debug d = new Debug();
            d.prefix = prefix;
            return d;
        } else {
            return null;
        }
    }

    private static String formatCaller() {
        return StackWalker.getInstance().walk(s ->
                s.dropWhile(f ->
                    f.getClassName().startsWith("sun.security.util.Debug"))
                        .map(f -> f.getFileName() + ":" + f.getLineNumber())
                        .findFirst().orElse("unknown caller"));
    }


    /**
     * Get a Debug object corresponding to the given option on the given
     * property value.
     * <p>
     * Note: unlike other {@code getInstance} methods, this method does not
     * use the {@code java.security.debug} system property.
     * <p>
     * Usually, this method is used by other individual area-specific debug
     * settings. For example,
     * {@snippet lang=java:
     * Map<String, String> settings = loadLoginSettings();
     * String property = settings.get("login");
     * Debug debug = Debug.of("login", property);
     * }
     *
     * @param prefix the debug option name
     * @param property debug setting for this option
     * @return a new Debug object if the property is true
     */
    public static Debug of(String prefix, String property) {
        if (property != null && property.toLowerCase(Locale.ROOT).startsWith("true")) {
            Debug d = new Debug();
            d.prefix = prefix;
            return d;
        }
        return null;
    }

    /**
     * True if the system property "security.debug" contains the
     * string "option".
     */
    public static boolean isOn(String option) {
        if (args == null)
            return false;
        else {
            if (args.contains("all"))
                return true;
            else
                return (args.contains(option));
        }
    }

    /**
     * Check if verbose messages is enabled for extra debugging.
     */
    public static boolean isVerbose() {
        return isOn("verbose");
    }

    /**
     * print a message to stderr that is prefixed with the prefix
     * created from the call to getInstance.
     */

    public void println(String message) {
        System.err.println(prefix + extraInfo() + ": " + message);
    }

    /**
     * print a message to stderr that is prefixed with the prefix
     * created from the call to getInstance and obj.
     */
    public void println(Object obj, String message) {
        System.err.println(prefix + extraInfo() + " [" + obj.getClass().getSimpleName() +
                "@" + System.identityHashCode(obj) + "]: "+message);
    }

    /**
     * print a blank line to stderr that is prefixed with the prefix.
     */

    public void println() {
        System.err.println(prefix + extraInfo() + ":");
    }

    /**
     * print a message to stderr that is prefixed with the prefix.
     */

    public void println(String prefix, String message) {
        System.err.println(prefix + extraInfo() + ": " + message);
    }

    /**
     * Include information containing:
     * - hex value of threadId
     * - the current thread name
     * - timestamp string
     * @return String with above metadata
     */
    private String extraInfo() {
        return String.format("[0x%s|%s|%s|%s]",
                Long.toHexString(Thread.currentThread().threadId()).toUpperCase(Locale.ROOT),
                Thread.currentThread().getName(),
                formatCaller(),
                FormatHolder.DATE_TIME_FORMATTER.format(Instant.now()));
    }

    /**
     * PrintStream for debug methods. Currently, only System.err is supported.
     */
    public PrintStream getPrintStream() {
        return System.err;
    }

    /**
     * return a hexadecimal printed representation of the specified
     * BigInteger object. the value is formatted to fit on lines of
     * at least 75 characters, with embedded newlines. Words are
     * separated for readability, with eight words (32 bytes) per line.
     */
    public static String toHexString(BigInteger b) {
        String hexValue = b.toString(16);
        StringBuilder sb = new StringBuilder(hexValue.length()*2);

        if (hexValue.startsWith("-")) {
            sb.append("   -");
            hexValue = hexValue.substring(1);
        } else {
            sb.append("    ");     // four spaces
        }
        if ((hexValue.length()%2) != 0) {
            // add back the leading 0
            hexValue = "0" + hexValue;
        }
        int i=0;
        while (i < hexValue.length()) {
            // one byte at a time
            sb.append(hexValue.substring(i, i + 2));
            i+=2;
            if (i!= hexValue.length()) {
                if ((i%64) == 0) {
                    sb.append("\n    ");     // line after eight words
                } else if (i%8 == 0) {
                    sb.append(" ");     // space between words
                }
            }
        }
        return sb.toString();
    }

    public static String toString(byte[] b) {
        if (b == null) {
            return "(null)";
        }
        return HexFormat.ofDelimiter(":").formatHex(b);
    }

    public static String toString(BigInteger b) {
        return toString(b.toByteArray());
    }

    // Holder class to break cyclic dependency seen during build
    private static class FormatHolder {
        private static final String PATTERN = "yyyy-MM-dd kk:mm:ss.SSS";
        private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter
                .ofPattern(PATTERN, Locale.ENGLISH)
                .withZone(ZoneId.systemDefault());
    }
}
