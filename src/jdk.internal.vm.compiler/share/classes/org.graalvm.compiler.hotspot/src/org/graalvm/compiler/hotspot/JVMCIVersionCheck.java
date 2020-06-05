/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
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
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
public final class JVMCIVersionCheck {

    private static final Version JVMCI_MIN_VERSION = new Version3(20, 1, 2);

    public interface Version {
        boolean isLessThan(Version other);

        default boolean isGreaterThan(Version other) {
            if (!isLessThan(other)) {
                return !equals(other);
            }
            return false;
        }

        static Version parse(String vmVersion) {
            Matcher m = Pattern.compile(".*-jvmci-(\\d+)\\.(\\d+)-b(\\d+).*").matcher(vmVersion);
            if (m.matches()) {
                try {
                    int major = Integer.parseInt(m.group(1));
                    int minor = Integer.parseInt(m.group(2));
                    int build = Integer.parseInt(m.group(3));
                    return new Version3(major, minor, build);
                } catch (NumberFormatException e) {
                    // ignore
                }
            }
            m = Pattern.compile(".*-jvmci-(\\d+)(?:\\.|-b)(\\d+).*").matcher(vmVersion);
            if (m.matches()) {
                try {
                    int major = Integer.parseInt(m.group(1));
                    int minor = Integer.parseInt(m.group(2));
                    return new Version2(major, minor);
                } catch (NumberFormatException e) {
                    // ignore
                }
            }
            return null;
        }
    }

    public static class Version2 implements Version {
        private final int major;
        private final int minor;

        public Version2(int major, int minor) {
            this.major = major;
            this.minor = minor;
        }

        @Override
        public boolean isLessThan(Version other) {
            if (other.getClass() == Version3.class) {
                return true;
            }
            Version2 o = (Version2) other;
            if (this.major < o.major) {
                return true;
            }
            if (this.major == o.major && this.minor < o.minor) {
                return true;
            }
            return false;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Version2) {
                Version2 that = (Version2) obj;
                return this.major == that.major && this.minor == that.minor;
            }
            return false;
        }

        @Override
        public int hashCode() {
            return this.major ^ this.minor;
        }

        @Override
        public String toString() {
            if (major >= 19) {
                return String.format("%d-b%02d", major, minor);
            } else {
                return String.format("%d.%d", major, minor);
            }
        }
    }

    public static class Version3 implements Version {
        private final int major;
        private final int minor;
        private final int build;

        public Version3(int major, int minor, int build) {
            this.major = major;
            this.minor = minor;
            this.build = build;
        }

        @Override
        public boolean isLessThan(Version other) {
            if (other.getClass() == Version2.class) {
                return false;
            }
            Version3 o = (Version3) other;
            if (this.major < o.major) {
                return true;
            }
            if (this.major == o.major) {
                if (this.minor < o.minor) {
                    return true;
                }
                if (this.minor == o.minor && this.build < o.build) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Version3) {
                Version3 that = (Version3) obj;
                return this.major == that.major && this.minor == that.minor && this.build == that.build;
            }
            return false;
        }

        @Override
        public int hashCode() {
            return this.major ^ this.minor ^ this.build;
        }

        @Override
        public String toString() {
            return String.format("%d.%d-b%02d", major, minor, build);
        }
    }

    private void failVersionCheck(boolean exit, String reason, Object... args) {
        Formatter errorMessage = new Formatter().format(reason, args);
        String javaHome = props.get("java.home");
        String vmName = props.get("java.vm.name");
        errorMessage.format("Set the JVMCI_VERSION_CHECK environment variable to \"ignore\" to suppress ");
        errorMessage.format("this error or to \"warn\" to emit a warning and continue execution.%n");
        errorMessage.format("Currently used Java home directory is %s.%n", javaHome);
        errorMessage.format("Currently used VM configuration is: %s%n", vmName);
        if (javaSpecVersion.compareTo("1.9") < 0) {
            errorMessage.format("Download the latest JVMCI JDK 8 from https://github.com/graalvm/openjdk8-jvmci-builder/releases");
        } else {
            if (javaSpecVersion.compareTo("11") == 0 && vmVersion.contains("-jvmci-")) {
                errorMessage.format("Download the latest Labs OpenJDK 11 from https://github.com/graalvm/labs-openjdk-11/releases");
            } else {
                errorMessage.format("Download JDK 11 or later.");
            }
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

    private final String javaSpecVersion;
    private final String vmVersion;
    private final Map<String, String> props;

    private JVMCIVersionCheck(Map<String, String> props, String javaSpecVersion, String vmVersion) {
        this.props = props;
        this.javaSpecVersion = javaSpecVersion;
        this.vmVersion = vmVersion;
    }

    static void check(Map<String, String> props, boolean exitOnFailure) {
        JVMCIVersionCheck checker = new JVMCIVersionCheck(props, props.get("java.specification.version"), props.get("java.vm.version"));
        checker.run(exitOnFailure, JVMCI_MIN_VERSION);
    }

    /**
     * Entry point for testing.
     */
    public static void check(Map<String, String> props,
                    Version minVersion,
                    String javaSpecVersion,
                    String javaVmVersion, boolean exitOnFailure) {
        JVMCIVersionCheck checker = new JVMCIVersionCheck(props, javaSpecVersion, javaVmVersion);
        checker.run(exitOnFailure, minVersion);
    }

    private void run(boolean exitOnFailure, Version minVersion) {
        if (javaSpecVersion.compareTo("1.9") < 0) {
            Version v = Version.parse(vmVersion);
            if (v != null) {
                if (v.isLessThan(minVersion)) {
                    failVersionCheck(exitOnFailure, "The VM does not support the minimum JVMCI API version required by Graal: %s < %s.%n", v, minVersion);
                }
                return;
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
            if (vmVersion.contains("-jvmci-")) {
                // A "labsjdk"
                Version v = Version.parse(vmVersion);
                if (v != null) {
                    if (v.isLessThan(minVersion)) {
                        failVersionCheck(exitOnFailure, "The VM does not support the minimum JVMCI API version required by Graal: %s < %s.%n", v, minVersion);
                    }
                    return;
                }
                failVersionCheck(exitOnFailure, "The VM does not support the minimum JVMCI API version required by Graal.%n" +
                                "Cannot read JVMCI version from java.vm.version property: %s.%n", vmVersion);
            } else {
                // Graal is compatible with all JDK versions as of 11 GA.
            }
        }
    }

    /**
     * Command line interface for performing the check.
     */
    public static void main(String[] args) {
        Properties sprops = System.getProperties();
        Map<String, String> props = new HashMap<>(sprops.size());
        for (String name : sprops.stringPropertyNames()) {
            props.put(name, sprops.getProperty(name));
        }
        check(props, true);
    }
}
