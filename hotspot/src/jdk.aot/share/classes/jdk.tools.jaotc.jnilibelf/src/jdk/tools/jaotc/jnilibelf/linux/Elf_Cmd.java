/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

package jdk.tools.jaotc.jnilibelf.linux;

/**
 * Represent Elf_Cmd enums defined in libelf.h on Linux as they slightly different from libelf.h on
 * SunOS.
 */
public enum Elf_Cmd {
    /** Nothing, terminate, or compute only. */
    ELF_C_NULL,

    /** Read. */
    ELF_C_READ,

    /** Read and write. */
    ELF_C_RDWR,

    /** Write. */
    ELF_C_WRITE,

    /** Clear flag. */
    ELF_C_CLR,

    /** Set flag. */
    ELF_C_SET,

    /**
     * Signal that file descriptor will not be used anymore.
     */
    ELF_C_FDDONE,

    /**
     * Read rest of data so that file descriptor is not used anymore.
     */
    ELF_C_FDREAD,

    /* The following are extensions. */

    /** Read, but mmap the file if possible. */
    ELF_C_READ_MMAP,

    /** Read and write, with mmap. */
    ELF_C_RDWR_MMAP,

    /** Write, with mmap. */
    ELF_C_WRITE_MMAP,

    /**
     * Read, but memory is writable, results are not written to the file.
     */
    ELF_C_READ_MMAP_PRIVATE,

    /** Copy basic file data but not the content. */
    ELF_C_EMPTY,

    /** Keep this the last entry. */
    ELF_C_NUM;
}
