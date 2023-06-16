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

import jdk.internal.access.SharedSecrets;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Security;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import static sun.launcher.LauncherHelper.INDENT;
import static sun.launcher.LauncherHelper.TWOINDENT;
import static sun.launcher.LauncherHelper.ostream;


/**
 * A utility class for security libs functionality
 * in the -XshowSettings:security output
 */
public final class SecuritySettings {

    private static final String THREEINDENT = TWOINDENT + INDENT;
    private static final String PROV_INFO_STRING = "Provider information: ";

    static void printSecuritySettings(String arg) {
        switch (arg) {
            case "properties" -> printSecurityProperties();
            case "providers"  -> printSecurityProviderConfig(true);
            case "tls"        -> printSecurityTLSConfig(true);
            case "all"        -> printAllSecurityConfig();
            default           -> {
                ostream.println("Unrecognized security subcommand. See \"java -X\" for help");
                ostream.println("Printing all security settings");
                printAllSecurityConfig();
            }

        }
    }

    // A non-verbose description of some core security configuration settings
    static void printSecuritySummarySettings() {
        ostream.println("Security settings summary: " + "\n" +
                INDENT + "See \"java -X\" for verbose security settings options");
        printSecurityProviderConfig(false);
        printSecurityTLSConfig(false);
    }

    static void printAllSecurityConfig() {
        ostream.println("Security settings:\n");
        printSecurityProperties();
        printSecurityProviderConfig(true);
        printSecurityTLSConfig(true);
    }

    private static void printSecurityProperties() {
        ostream.println(INDENT + "Security properties:");
        Properties p = SharedSecrets.getJavaSecurityPropertiesAccess().getInitialProperties();
        for (String key : p.stringPropertyNames().stream().sorted().toList()) {
            String val = p.getProperty(key);
            if (val.contains(",") && val.length() > 60) {
                // split lines longer than 60 chars which have multiple values
                ostream.println(TWOINDENT + key + "=");
                String[] values = val.split(",");
                String lastValue = values[values.length -1].trim();
                List.of(values).forEach(
                        s -> ostream.println(THREEINDENT + s.trim() +
                                (s.trim().equals(lastValue) ? "" : ",")));
            } else {
                ostream.println(TWOINDENT + key + "=" + val);
            }
        }
        ostream.println();
    }

    private static void printSecurityTLSConfig(boolean verbose) {
        SSLSocket ssls;
        try {
            ssls = (SSLSocket)
                    SSLContext.getDefault().getSocketFactory().createSocket();
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new InternalError("Failed to create SSL socket");
        }

        ostream.println(INDENT + "Security TLS configuration:");
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
