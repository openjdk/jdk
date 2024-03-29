/*
 * Copyright (c) 1998, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Locale;
import sun.security.action.GetPropertyAction;

/**
 * A utility class for debugging.
 *
 * @author Roland Schemers
 */
public class Debug {

    private String prefix;
    private boolean printDateTime;
    private boolean printThreadDetails;

    private static String args;
    private static boolean threadInfoAll;
    private static boolean timeStampInfoAll;
    private static final String TIMESTAMP_OPTION = "+timestamp";
    private static final String THREAD_OPTION = "+thread";

    static {
        args = GetPropertyAction.privilegedGetProperty("java.security.debug");

        String args2 = GetPropertyAction
                .privilegedGetProperty("java.security.auth.debug");

        if (args == null) {
            args = args2;
        } else {
            if (args2 != null)
               args = args + "," + args2;
        }

        if (args != null) {
            args = marshal(args);
            if (args.equals("help")) {
                Help();
            } else if (args.contains("all")) {
                // "all" option has special handling for decorator options
                // If the thread or timestamp decorator option is detected
                // with the "all" option, then it impacts decorator options
                // for other categories
                int beginIndex = args.lastIndexOf("all") + "all".length();
                int commaIndex = args.indexOf(',', beginIndex);
                if (commaIndex == -1) commaIndex = args.length();
                threadInfoAll = args.substring(beginIndex, commaIndex).contains(THREAD_OPTION);
                timeStampInfoAll = args.substring(beginIndex, commaIndex).contains(TIMESTAMP_OPTION);
            }
        }
    }

    public static void Help() {
        System.err.println();
        System.err.println("all           turn on all debugging");
        System.err.println("access        print all checkPermission results");
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
        System.err.println("policy        loading and granting");
        System.err.println("provider      security provider debugging");
        System.err.println("pkcs11        PKCS11 session manager debugging");
        System.err.println("pkcs11keystore");
        System.err.println("              PKCS11 KeyStore debugging");
        System.err.println("pkcs12        PKCS12 KeyStore debugging");
        System.err.println("properties    Security property and configuration file debugging");
        System.err.println("sunpkcs11     SunPKCS11 provider debugging");
        System.err.println("scl           permissions SecureClassLoader assigns");
        System.err.println("securerandom  SecureRandom");
        System.err.println("ts            timestamping");
        System.err.println("x509          X.509 certificate debugging");
        System.err.println();
        System.err.println("+timestamp can be appended to any of above options to print");
        System.err.println("              a timestamp for that debug option");
        System.err.println("+thread can be appended to any of above options to print");
        System.err.println("              thread and caller information for that debug option");
        System.err.println();
        System.err.println("The following can be used with access:");
        System.err.println();
        System.err.println("stack         include stack trace");
        System.err.println("domain        dump all domains in context");
        System.err.println("failure       before throwing exception, dump stack");
        System.err.println("              and domain that didn't have permission");
        System.err.println();
        System.err.println("The following can be used with stack and domain:");
        System.err.println();
        System.err.println("permission=<classname>");
        System.err.println("              only dump output if specified permission");
        System.err.println("              is being checked");
        System.err.println("codebase=<URL>");
        System.err.println("              only dump output if specified codebase");
        System.err.println("              is being checked");
        System.err.println();
        System.err.println("The following can be used with provider:");
        System.err.println();
        System.err.println("engine=<engines>");
        System.err.println("              only dump output for the specified list");
        System.err.println("              of JCA engines. Supported values:");
        System.err.println("              Cipher, KeyAgreement, KeyGenerator,");
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
            d.configureExtras(option);
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

    // parse an option string to determine if extra details,
    // like thread and timestamp, should be printed
    private void configureExtras(String option) {
        // treat "all" as special case, only used for java.security.debug property
        this.printDateTime = timeStampInfoAll;
        this.printThreadDetails = threadInfoAll;

        if (printDateTime && printThreadDetails) {
            // nothing left to configure
            return;
        }

        // args is converted to lower case for the most part via marshal method
        int optionIndex = args.lastIndexOf(option);
        if (optionIndex == -1) {
            // option not in args list. Only here since "all" was present
            // in debug property argument. "all" option already parsed
            return;
        }
        int beginIndex = optionIndex + option.length();
        int commaIndex = args.indexOf(',', beginIndex);
        if (commaIndex == -1) commaIndex = args.length();
        String subOpt = args.substring(beginIndex, commaIndex);
        printDateTime = printDateTime || subOpt.contains(TIMESTAMP_OPTION);
        printThreadDetails = printThreadDetails || subOpt.contains(THREAD_OPTION);
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
     * +timestamp string can be appended to property value
     * to print timestamp information. (e.g. true+timestamp)
     * +thread string can be appended to property value
     * to print thread and caller information. (e.g. true+thread)
     *
     * @param prefix the debug option name
     * @param property debug setting for this option
     * @return a new Debug object if the property is true
     */
    public static Debug of(String prefix, String property) {
        if (property != null && property.toLowerCase(Locale.ROOT).startsWith("true")) {
            Debug d = new Debug();
            d.prefix = prefix;
            d.printThreadDetails = property.contains(THREAD_OPTION);
            d.printDateTime = property.contains(TIMESTAMP_OPTION);
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
     * If thread debug option enabled, include information containing
     * hex value of threadId and the current thread name
     * If timestamp debug option enabled, include timestamp string
     * @return extra info if debug option enabled.
     */
    private String extraInfo() {
        String retString = "";
        if (printThreadDetails) {
            retString = "0x" + Long.toHexString(
                    Thread.currentThread().threadId()).toUpperCase(Locale.ROOT) +
                    "|" + Thread.currentThread().getName() + "|" + formatCaller();
        }
        if (printDateTime) {
            retString += (retString.isEmpty() ? "" : "|")
                    + FormatHolder.DATE_TIME_FORMATTER.format(Instant.now());
        }
        return retString.isEmpty() ? "" : "[" + retString + "]";
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

    /**
     * change a string into lower case except permission classes and URLs.
     */
    private static String marshal(String args) {
        if (args != null) {
            StringBuilder target = new StringBuilder();
            StringBuilder source = new StringBuilder(args);

            // obtain the "permission=<classname>" options
            // the syntax of classname: IDENTIFIER.IDENTIFIER
            // the regular express to match a class name:
            // "[a-zA-Z_$][a-zA-Z0-9_$]*([.][a-zA-Z_$][a-zA-Z0-9_$]*)*"
            String keyReg = "[Pp][Ee][Rr][Mm][Ii][Ss][Ss][Ii][Oo][Nn]=";
            String keyStr = "permission=";
            String reg = keyReg +
                "[a-zA-Z_$][a-zA-Z0-9_$]*([.][a-zA-Z_$][a-zA-Z0-9_$]*)*";
            Pattern pattern = Pattern.compile(reg);
            Matcher matcher = pattern.matcher(source);
            StringBuilder left = new StringBuilder();
            while (matcher.find()) {
                String matched = matcher.group();
                target.append(matched.replaceFirst(keyReg, keyStr));
                target.append("  ");

                // delete the matched sequence
                matcher.appendReplacement(left, "");
            }
            matcher.appendTail(left);
            source = left;

            // obtain the "codebase=<URL>" options
            // the syntax of URL is too flexible, and here assumes that the
            // URL contains no space, comma(','), and semicolon(';'). That
            // also means those characters also could be used as separator
            // after codebase option.
            // However, the assumption is incorrect in some special situation
            // when the URL contains comma or semicolon
            keyReg = "[Cc][Oo][Dd][Ee][Bb][Aa][Ss][Ee]=";
            keyStr = "codebase=";
            reg = keyReg + "[^, ;]*";
            pattern = Pattern.compile(reg);
            matcher = pattern.matcher(source);
            left = new StringBuilder();
            while (matcher.find()) {
                String matched = matcher.group();
                target.append(matched.replaceFirst(keyReg, keyStr));
                target.append("  ");

                // delete the matched sequence
                matcher.appendReplacement(left, "");
            }
            matcher.appendTail(left);
            source = left;

            // convert the rest to lower-case characters
            target.append(source.toString().toLowerCase(Locale.ENGLISH));

            return target.toString();
        }

        return null;
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
