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



package jdk.tools.jaotc.binformat.elf;

import java.nio.ByteOrder;
import jdk.tools.jaotc.binformat.elf.Elf.Elf64_Ehdr;

/**
 * Class that abstracts MACH-O target details.
 *
 */
final class ElfTargetInfo {
    /**
     * Target architecture.
     */
    private static final char arch;

    /**
     * Architecture endian-ness.
     */
    private static final int endian = Elf64_Ehdr.ELFDATA2LSB;

    /**
     * Target OS string.
     */
    private static String osName;

    static {
        // Find the target arch details
        String archStr = System.getProperty("os.arch").toLowerCase();
        if (ByteOrder.nativeOrder() != ByteOrder.LITTLE_ENDIAN) {
            System.out.println("Only Little Endian byte order supported!");
        }

        if (archStr.equals("amd64") || archStr.equals("x86_64")) {
            arch = Elf64_Ehdr.EM_X86_64;
        } else if (archStr.equals("aarch64")) {
            arch = Elf64_Ehdr.EM_AARCH64;
        } else {
            System.out.println("Unsupported architecture " + archStr);
            arch = Elf64_Ehdr.EM_NONE;
        }

        osName = System.getProperty("os.name").toLowerCase();
        if (!osName.equals("linux") && !osName.equals("sunos")) {
            System.out.println("Unsupported Operating System " + osName);
            osName = "Unknown";
        }
    }

    static char getElfArch() {
        return arch;
    }

    static int getElfEndian() {
        return endian;
    }

    static String getOsName() {
        return osName;
    }
}
