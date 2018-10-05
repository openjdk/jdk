/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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

package jdk.test.lib;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.regex.Pattern;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;

public class Platform {
    public  static final String vmName      = privilegedGetProperty("java.vm.name");
    public  static final String vmInfo      = privilegedGetProperty("java.vm.info");
    private static final String osVersion   = privilegedGetProperty("os.version");
    private static       int osVersionMajor = -1;
    private static       int osVersionMinor = -1;
    private static final String osName      = privilegedGetProperty("os.name");
    private static final String dataModel   = privilegedGetProperty("sun.arch.data.model");
    private static final String vmVersion   = privilegedGetProperty("java.vm.version");
    private static final String jdkDebug    = privilegedGetProperty("jdk.debug");
    private static final String osArch      = privilegedGetProperty("os.arch");
    private static final String userName    = privilegedGetProperty("user.name");
    private static final String compiler    = privilegedGetProperty("sun.management.compiler");

    private static String privilegedGetProperty(String key) {
        return AccessController.doPrivileged((
                PrivilegedAction<String>) () -> System.getProperty(key));
    }

    public static boolean isClient() {
        return vmName.endsWith(" Client VM");
    }

    public static boolean isServer() {
        return vmName.endsWith(" Server VM");
    }

    public static boolean isGraal() {
        return vmName.endsWith(" Graal VM");
    }

    public static boolean isZero() {
        return vmName.endsWith(" Zero VM");
    }

    public static boolean isMinimal() {
        return vmName.endsWith(" Minimal VM");
    }

    public static boolean isEmbedded() {
        return vmName.contains("Embedded");
    }

    public static boolean isEmulatedClient() {
        return vmInfo.contains(" emulated-client");
    }

    public static boolean isTieredSupported() {
        return compiler.contains("Tiered Compilers");
    }

    public static boolean isInt() {
        return vmInfo.contains("interpreted");
    }

    public static boolean isMixed() {
        return vmInfo.contains("mixed");
    }

    public static boolean isComp() {
        return vmInfo.contains("compiled");
    }

    public static boolean is32bit() {
        return dataModel.equals("32");
    }

    public static boolean is64bit() {
        return dataModel.equals("64");
    }

    public static boolean isAix() {
        return isOs("aix");
    }

    public static boolean isLinux() {
        return isOs("linux");
    }

    public static boolean isOSX() {
        return isOs("mac");
    }

    public static boolean isSolaris() {
        return isOs("sunos");
    }

    public static boolean isWindows() {
        return isOs("win");
    }

    private static boolean isOs(String osname) {
        return osName.toLowerCase().startsWith(osname.toLowerCase());
    }

    public static String getOsName() {
        return osName;
    }

    // Os version support.
    private static void init_version() {
        String[] osVersionTokens = osVersion.split("\\.");
        try {
            if (osVersionTokens.length > 0) {
                osVersionMajor = Integer.parseInt(osVersionTokens[0]);
                if (osVersionTokens.length > 1) {
                    osVersionMinor = Integer.parseInt(osVersionTokens[1]);
                }
            }
        } catch (NumberFormatException e) {
            osVersionMajor = osVersionMinor = 0;
        }
    }

    public static String getOsVersion() {
        return osVersion;
    }

    // Returns major version number from os.version system property.
    // E.g. 5 on Solaris 10 and 3 on SLES 11.3 (for the linux kernel version).
    public static int getOsVersionMajor() {
        if (osVersionMajor == -1) init_version();
        return osVersionMajor;
    }

    // Returns minor version number from os.version system property.
    // E.g. 10 on Solaris 10 and 0 on SLES 11.3 (for the linux kernel version).
    public static int getOsVersionMinor() {
        if (osVersionMinor == -1) init_version();
        return osVersionMinor;
    }

    public static boolean isDebugBuild() {
        return (jdkDebug.toLowerCase().contains("debug"));
    }

    public static boolean isSlowDebugBuild() {
        return (jdkDebug.toLowerCase().equals("slowdebug"));
    }

    public static boolean isFastDebugBuild() {
        return (jdkDebug.toLowerCase().equals("fastdebug"));
    }

    public static String getVMVersion() {
        return vmVersion;
    }

    public static boolean isAArch64() {
        return isArch("aarch64");
    }

    public static boolean isARM() {
        return isArch("arm.*");
    }

    public static boolean isPPC() {
        return isArch("ppc.*");
    }

    // Returns true for IBM z System running linux.
    public static boolean isS390x() {
        return isArch("s390.*") || isArch("s/390.*") || isArch("zArch_64");
    }

    // Returns true for sparc and sparcv9.
    public static boolean isSparc() {
        return isArch("sparc.*");
    }

    public static boolean isX64() {
        // On OSX it's 'x86_64' and on other (Linux, Windows and Solaris) platforms it's 'amd64'
        return isArch("(amd64)|(x86_64)");
    }

    public static boolean isX86() {
        // On Linux it's 'i386', Windows 'x86' without '_64' suffix.
        return isArch("(i386)|(x86(?!_64))");
    }

    public static String getOsArch() {
        return osArch;
    }

    /**
     * Return a boolean for whether SA and jhsdb are ported/available
     * on this platform.
     */
    public static boolean hasSA() {
        if (isAix()) {
            return false; // SA not implemented.
        } else if (isLinux()) {
            if (isS390x()) {
                return false; // SA not implemented.
            }
        }
        // Other platforms expected to work:
        return true;
    }

    /**
     * Return a boolean for whether we expect to be able to attach
     * the SA to our own processes on this system.  This requires
     * that SA is ported/available on this platform.
     */
    public static boolean shouldSAAttach() throws IOException {
        if (!hasSA()) return false;
        if (isLinux()) {
            return canPtraceAttachLinux();
        } else if (isOSX()) {
            return canAttachOSX();
        } else {
            // Other platforms expected to work:
            return true;
        }
    }

    /**
     * On Linux, first check the SELinux boolean "deny_ptrace" and return false
     * as we expect to be denied if that is "1".  Then expect permission to attach
     * if we are root, so return true.  Then return false for an expected denial
     * if "ptrace_scope" is 1, and true otherwise.
     */
    private static boolean canPtraceAttachLinux() throws IOException {
        // SELinux deny_ptrace:
        File deny_ptrace = new File("/sys/fs/selinux/booleans/deny_ptrace");
        if (deny_ptrace.exists()) {
            try (RandomAccessFile file = AccessController.doPrivileged(
                    (PrivilegedExceptionAction<RandomAccessFile>) () -> new RandomAccessFile(deny_ptrace, "r"))) {
                if (file.readByte() != '0') {
                    return false;
                }
            } catch (PrivilegedActionException e) {
                @SuppressWarnings("unchecked")
                IOException t = (IOException) e.getException();
                throw t;
            }
        }

        // YAMA enhanced security ptrace_scope:
        // 0 - a process can PTRACE_ATTACH to any other process running under the same uid
        // 1 - restricted ptrace: a process must be a children of the inferior or user is root
        // 2 - only processes with CAP_SYS_PTRACE may use ptrace or user is root
        // 3 - no attach: no processes may use ptrace with PTRACE_ATTACH
        File ptrace_scope = new File("/proc/sys/kernel/yama/ptrace_scope");
        if (ptrace_scope.exists()) {
            try (RandomAccessFile file = AccessController.doPrivileged(
                    (PrivilegedExceptionAction<RandomAccessFile>) () -> new RandomAccessFile(ptrace_scope, "r"))) {
                byte yama_scope = file.readByte();
                if (yama_scope == '3') {
                    return false;
                }

                if (!userName.equals("root") && yama_scope != '0') {
                    return false;
                }
            } catch (PrivilegedActionException e) {
                @SuppressWarnings("unchecked")
                IOException t = (IOException) e.getException();
                throw t;
            }
        }
        // Otherwise expect to be permitted:
        return true;
    }

    /**
     * On OSX, expect permission to attach only if we are root.
     */
    private static boolean canAttachOSX() {
        return userName.equals("root");
    }

    private static boolean isArch(String archnameRE) {
        return Pattern.compile(archnameRE, Pattern.CASE_INSENSITIVE)
                      .matcher(osArch)
                      .matches();
    }

    /**
     * Returns file extension of shared library, e.g. "so" on linux, "dll" on windows.
     * @return file extension
     */
    public static String sharedLibraryExt() {
        if (isWindows()) {
            return "dll";
        } else if (isOSX()) {
            return "dylib";
        } else {
            return "so";
        }
    }

    public static boolean isDefaultCDSArchiveSupported() {
        return (is64bit()  &&
                isServer() &&
                (isLinux()   ||
                 isOSX()     ||
                 isSolaris() ||
                 isWindows()) &&
                !isZero()    &&
                !isMinimal() &&
                !isAArch64() &&
                !isARM());
    }

    /*
     * This should match the #if condition in ClassListParser::load_class_from_source().
     */
    public static boolean areCustomLoadersSupportedForCDS() {
        return (is64bit() && (isLinux() || isSolaris() || isOSX()));
    }
}
