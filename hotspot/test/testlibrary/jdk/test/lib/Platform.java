/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.util.regex.Pattern;

/**
 * @deprecated This class is deprecated. Use the one from
 *             {@code <root>/test/lib/share/classes/jdk/test/lib}
 */
@Deprecated
public class Platform {
    private static final String osName      = System.getProperty("os.name");
    private static final String dataModel   = System.getProperty("sun.arch.data.model");
    private static final String vmVersion   = System.getProperty("java.vm.version");
    private static final String jdkDebug    = System.getProperty("jdk.debug");
    private static final String osArch      = System.getProperty("os.arch");
    private static final String vmName      = System.getProperty("java.vm.name");
    private static final String userName    = System.getProperty("user.name");
    private static final String compiler    = System.getProperty("sun.management.compiler");

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

    public static boolean isTieredSupported() {
        return compiler.contains("Tiered Compilers");
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

    public static boolean isDebugBuild() {
        return (jdkDebug.toLowerCase().contains("debug"));
    }

    public static String getVMVersion() {
        return vmVersion;
    }

    // Returns true for sparc and sparcv9.
    public static boolean isSparc() {
        return isArch("sparc.*");
    }

    public static boolean isARM() {
        return isArch("arm.*");
    }

    public static boolean isPPC() {
        return isArch("ppc.*");
    }

    public static boolean isX86() {
        // On Linux it's 'i386', Windows 'x86' without '_64' suffix.
        return isArch("(i386)|(x86(?!_64))");
    }

    public static boolean isX64() {
        // On OSX it's 'x86_64' and on other (Linux, Windows and Solaris) platforms it's 'amd64'
        return isArch("(amd64)|(x86_64)");
    }

    public static boolean isAArch64() {
        return isArch("aarch64");
    }

    private static boolean isArch(String archnameRE) {
        return Pattern.compile(archnameRE, Pattern.CASE_INSENSITIVE)
                .matcher(osArch)
                .matches();
    }

    public static String getOsArch() {
        return osArch;
    }

    /**
     * Return a boolean for whether we expect to be able to attach
     * the SA to our own processes on this system.
     */
    public static boolean shouldSAAttach() throws Exception {

        if (isAix()) {
            return false;   // SA not implemented.
        } else if (isLinux()) {
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
    public static boolean canPtraceAttachLinux() throws Exception {

        // SELinux deny_ptrace:
        String deny_ptrace = Utils.fileAsString("/sys/fs/selinux/booleans/deny_ptrace");
        if (deny_ptrace != null && deny_ptrace.contains("1")) {
            // ptrace will be denied:
            return false;
        }

        if (userName.equals("root")) {
            return true;
        }

        // ptrace_scope:
        String ptrace_scope = Utils.fileAsString("/proc/sys/kernel/yama/ptrace_scope");
        if (ptrace_scope != null && ptrace_scope.contains("1")) {
            // ptrace will be denied:
            return false;
        }

        // Otherwise expect to be permitted:
        return true;
    }

    /**
     * On OSX, expect permission to attach only if we are root.
     */
    public static boolean canAttachOSX() throws Exception {
        return userName.equals("root");
    }
}
