/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package sun.launcher;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.io.PrintStream;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Security;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import jdk.internal.access.SharedSecrets;

/**
 * A utility class for security libs functionality
 * in the -XshowSettings:security output
 */
public final class SecuritySettings {

    private static final String INDENT = "    ";
    private static final String TWOINDENT = INDENT + INDENT;
    private static final String THREEINDENT = TWOINDENT + INDENT;
    private static final String PROV_INFO_STRING = "Provider information: ";
    private static PrintStream ostream = null;

    static void printSecuritySettings(String arg, PrintStream stream) {
        ostream = stream;
        switch (arg) {
            case "properties" -> printSecurityProperties();
            case "providers"  -> printSecurityProviderConfig(true);
            case "tls"        -> printSecurityTLSConfig(true);
            case "all"        -> printAllSecurityConfig();
            default           -> ostream.println(
                    "\nUnrecognized security subcommand. Valid values are " +
                    "\"all\", \"properties\", \"providers\", \"tls\". See \"java -X\"\n");
        }
    }

    // A non-verbose description of some core security configuration settings
    static void printSecuritySummarySettings(PrintStream stream) {
        ostream = stream;
        ostream.println("Security settings summary: " + "\n" +
                INDENT + "See \"java -X\" for verbose security settings options");
        printSecurityProviderConfig(false);
        printSecurityTLSConfig(false);
    }

    static void printAllSecurityConfig() {
        ostream.println("Security settings:");
        printSecurityProperties();
        printSecurityProviderConfig(true);
        printSecurityTLSConfig(true);
    }

    private static void printSecurityProperties() {
        ostream.println(INDENT + "Security properties:");
        Properties p = SharedSecrets.getJavaSecurityPropertiesAccess().getInitialProperties();
        for (String key : p.stringPropertyNames().stream().sorted().toList()) {
            String val = p.getProperty(key);
            if (val.length() > 60) {
                splitLongPropertyLines(key, val);
            } else {
                ostream.println(TWOINDENT + key + "=" + val);
            }
        }
        ostream.println();
    }

    private static void splitLongPropertyLines(String key, String val) {
        // split long property values which use well known separator
        if (val.contains(",") || val.contains(";")) {
            String separator = (val.contains(",")) ? "," : ";";
            ostream.println(TWOINDENT + key + "=");
            String[] values = val.split(separator);
            String lastValue = values[values.length -1].trim();
            List.of(values).forEach(
                    s -> ostream.println(THREEINDENT + s.trim() +
                            (s.trim().equals(lastValue) ? "" : separator)));
        } else {
            ostream.println(TWOINDENT + key + "=" + val);
        }
    }

    private static void printSecurityTLSConfig(boolean verbose) {
        SSLSocket ssls;
        SSLContext sslContext;
        try {
            sslContext = SSLContext.getDefault();
            ssls = (SSLSocket)sslContext.getSocketFactory().createSocket();
        } catch (IOException | NoSuchAlgorithmException e) {
            ostream.println(INDENT + "Failed to create SSL socket");
            ostream.println(INDENT + e + "\n");
            return;
        }

        ostream.println(INDENT + "Security TLS configuration (" +
                sslContext.getProvider().getName() + " provider):");
        ostream.println(TWOINDENT + "Enabled Protocols:");
        for (String s : ssls.getEnabledProtocols()) {
            ostream.println(THREEINDENT + s);
        }

        if (verbose) {
            ostream.println("\n" + TWOINDENT + "Enabled Cipher Suites:");
            for (String s : ssls.getEnabledCipherSuites()) {
                ostream.println(THREEINDENT + s);
            }
        }
        ostream.println();
    }

    private static void printSecurityProviderConfig(boolean verbose) {
        ostream.println(INDENT + "Security provider static configuration: (in order of preference)");
        for (Provider p : Security.getProviders()) {
            if (verbose) {
                // separate the views out
                ostream.println(TWOINDENT + "-".repeat(40));
            }
            ostream.println(TWOINDENT + "Provider name: " + p.getName());
            if (verbose) {
                ostream.println(wrappedString(PROV_INFO_STRING + p.getInfo(), 80,
                        TWOINDENT, THREEINDENT));
                ostream.println(TWOINDENT + "Provider services: (type : algorithm)");
                Set<Provider.Service> services = p.getServices();
                Set<String> keys = Collections.list(p.keys())
                        .stream()
                        .map(String.class::cast)
                        .filter(s -> s.startsWith("Alg.Alias."))
                        .collect(Collectors.toSet());
                if (!services.isEmpty()) {
                    services.stream()
                            .sorted(Comparator.comparing(Provider.Service::getType)
                                    .thenComparing(Provider.Service::getAlgorithm))
                            .forEach(ps -> {
                                ostream.println(THREEINDENT +
                                        ps.getType() + "." + ps.getAlgorithm());
                                List<String> aliases = keys
                                        .stream()
                                        .filter(s -> s.startsWith("Alg.Alias." + ps.getType()))
                                        .filter(s -> p.getProperty(s).equals(ps.getAlgorithm()))
                                        .map(s -> s.substring(("Alg.Alias." + ps.getType() + ".").length()))
                                        .toList();

                                if (!aliases.isEmpty()) {
                                    ostream.println(wrappedString(
                                            aliases.stream()
                                                    .collect(Collectors.joining(", ", INDENT + " aliases: [", "]")),
                                            80, " " + TWOINDENT, INDENT + THREEINDENT));
                                }
                            });
                } else {
                    ostream.println(THREEINDENT + "<none>");
                }
            }
        }
        if (verbose) {
            ostream.println();
        }
    }

    // return a string split across multiple lines which aims to limit max length
    private static String wrappedString(String orig, int limit,
                                        String initIndent, String successiveIndent) {
        if (orig == null || orig.isEmpty() || limit <= 0) {
            // bad input
            return orig;
        }
        StringBuilder sb = new StringBuilder();
        int widthCount = 0;
        for (String s : orig.split(" ")) {
            if (widthCount == 0) {
                // first iteration only
                sb.append(initIndent + s);
                widthCount = s.length() + initIndent.length();
            } else {
                if (widthCount + s.length() > limit) {
                    sb.append("\n" + successiveIndent + s);
                    widthCount = s.length() + successiveIndent.length();
                } else {
                    sb.append(" " + s);
                    widthCount += s.length() + 1;
                }
            }
        }
        return sb.toString();
    }
}
