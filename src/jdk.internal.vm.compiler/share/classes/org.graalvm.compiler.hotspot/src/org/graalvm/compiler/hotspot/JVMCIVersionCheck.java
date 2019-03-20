/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.hotspot;

import java.util.Formatter;

/**
 * Mechanism for checking that the current Java runtime environment supports the minimum JVMCI API
 * required by Graal. The {@code JVMCI_VERSION_CHECK} environment variable can be used to ignore a
 * failed check ({@code JVMCI_VERSION_CHECK=ignore}) or print a warning (
 * {@code JVMCI_VERSION_CHECK=warn}) and continue. Otherwise, a failed check results in an
 * {@link InternalError} being raised or, if called from {@link #main(String[])}, the VM exiting
 * with a result code of {@code -1}
 *
 * This class only depends on the JDK so that it can be used without building Graal.
 */
class JVMCIVersionCheck {

    // 0.55 introduces new HotSpotSpeculationLog API
    private static final int JVMCI8_MIN_MAJOR_VERSION = 0;
    private static final int JVMCI8_MIN_MINOR_VERSION = 55;

    private static void failVersionCheck(boolean exit, String reason, Object... args) {
        Formatter errorMessage = new Formatter().format(reason, args);
        String javaHome = System.getProperty("java.home");
        String vmName = System.getProperty("java.vm.name");
        errorMessage.format("Set the JVMCI_VERSION_CHECK environment variable to \"ignore\" to suppress ");
        errorMessage.format("this error or to \"warn\" to emit a warning and continue execution.%n");
        errorMessage.format("Currently used Java home directory is %s.%n", javaHome);
        errorMessage.format("Currently used VM configuration is: %s%n", vmName);
        if (System.getProperty("java.specification.version").compareTo("1.9") < 0) {
            errorMessage.format("Download the latest JVMCI JDK 8 from http://www.oracle.com/technetwork/oracle-labs/program-languages/downloads/index.html");
        } else {
            errorMessage.format("Download JDK 11 or later.");
        }
        String value = System.getenv("JVMCI_VERSION_CHECK");
        if ("warn".equals(value)) {
            System.err.println(errorMessage.toString());
        } else if ("ignore".equals(value)) {
            return;
        } else if (exit) {
            System.err.println(errorMessage.toString());
            System.exit(-1);
        } else {
            throw new InternalError(errorMessage.toString());
        }
    }

    static void check(boolean exitOnFailure) {
        // Don't use regular expressions to minimize Graal startup time
        String javaSpecVersion = System.getProperty("java.specification.version");
        String vmVersion = System.getProperty("java.vm.version");
        if (javaSpecVersion.compareTo("1.9") < 0) {
            int start = vmVersion.indexOf("-jvmci-");
            if (start >= 0) {
                start += "-jvmci-".length();
                int end = vmVersion.indexOf('.', start);
                if (end > 0) {
                    int major;
                    try {
                        major = Integer.parseInt(vmVersion.substring(start, end));
                    } catch (NumberFormatException e) {
                        failVersionCheck(exitOnFailure, "The VM does not support the minimum JVMCI API version required by Graal.%n" +
                                        "Cannot read JVMCI major version from java.vm.version property: %s.%n", vmVersion);
                        return;
                    }
                    start = end + 1;
                    end = start;
                    while (end < vmVersion.length() && Character.isDigit(vmVersion.charAt(end))) {
                        end++;
                    }
                    int minor;
                    try {
                        minor = Integer.parseInt(vmVersion.substring(start, end));
                    } catch (NumberFormatException e) {
                        failVersionCheck(exitOnFailure, "The VM does not support the minimum JVMCI API version required by Graal.%n" +
                                        "Cannot read JVMCI minor version from java.vm.version property: %s.%n", vmVersion);
                        return;
                    }
                    if (major >= JVMCI8_MIN_MAJOR_VERSION && minor >= JVMCI8_MIN_MINOR_VERSION) {
                        return;
                    }
                    failVersionCheck(exitOnFailure, "The VM does not support the minimum JVMCI API version required by Graal: %d.%d < %d.%d.%n",
                                    major, minor, JVMCI8_MIN_MAJOR_VERSION, JVMCI8_MIN_MINOR_VERSION);
                    return;
                }
            }
            failVersionCheck(exitOnFailure, "The VM does not support the minimum JVMCI API version required by Graal.%n" +
                            "Cannot read JVMCI version from java.vm.version property: %s.%n", vmVersion);
        } else if (javaSpecVersion.compareTo("11") < 0) {
            failVersionCheck(exitOnFailure, "Graal is not compatible with the JVMCI API in JDK 9 and 10.%n");
        } else {
            if (vmVersion.contains("SNAPSHOT")) {
                return;
            }
            if (vmVersion.contains("internal")) {
                // Allow local builds
                return;
            }
            if (vmVersion.startsWith("11-ea+")) {
                String buildString = vmVersion.substring("11-ea+".length());
                try {
                    int build = Integer.parseInt(buildString);
                    if (build < 20) {
                        failVersionCheck(exitOnFailure, "Graal requires build 20 or later of JDK 11 early access binary, got build %d.%n", build);
                        return;
                    }
                } catch (NumberFormatException e) {
                    failVersionCheck(exitOnFailure, "Could not parse the JDK 11 early access build number from java.vm.version property: %s.%n", vmVersion);
                    return;
                }
            } else {
                // Graal is compatible with all JDK versions as of 11 GA.
            }
        }
    }

    /**
     * Command line interface for performing the check.
     */
    public static void main(String[] args) {
        check(true);
    }
}
