/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.java.testlibrary;

public class Platform {
    private static final String osName      = System.getProperty("os.name");
    private static final String dataModel   = System.getProperty("sun.arch.data.model");
    private static final String vmVersion   = System.getProperty("java.vm.version");
    private static final String osArch      = System.getProperty("os.arch");

    public static boolean is32bit() {
        return dataModel.equals("32");
    }

    public static boolean is64bit() {
        return dataModel.equals("64");
    }

    public static boolean isSolaris() {
        return isOs("sunos");
    }

    public static boolean isWindows() {
        return isOs("win");
    }

    public static boolean isOSX() {
        return isOs("mac");
    }

    public static boolean isLinux() {
        return isOs("linux");
    }

    private static boolean isOs(String osname) {
        return osName.toLowerCase().startsWith(osname.toLowerCase());
    }

    public static String getOsName() {
        return osName;
    }

    public static boolean isDebugBuild() {
        return vmVersion.toLowerCase().contains("debug");
    }

    public static String getVMVersion() {
        return vmVersion;
    }

    // Returns true for sparc and sparcv9.
    public static boolean isSparc() {
        return isArch("sparc");
    }

    public static boolean isARM() {
        return isArch("arm");
    }

    public static boolean isPPC() {
        return isArch("ppc");
    }

    public static boolean isX86() {
        // On Linux it's 'i386', Windows 'x86'
        return (isArch("i386") || isArch("x86"));
    }

    public static boolean isX64() {
        // On OSX it's 'x86_64' and on other (Linux, Windows and Solaris) platforms it's 'amd64'
        return (isArch("amd64") || isArch("x86_64"));
    }

    private static boolean isArch(String archname) {
        return osArch.toLowerCase().startsWith(archname.toLowerCase());
    }

    public static String getOsArch() {
        return osArch;
    }

}
