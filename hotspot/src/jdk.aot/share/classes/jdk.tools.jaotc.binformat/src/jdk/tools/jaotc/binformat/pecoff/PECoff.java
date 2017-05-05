/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

package jdk.tools.jaotc.binformat.pecoff;

/**
 *
 * Support for the creation of Coff files.
 * Current support is limited to 64 bit x86_64.
 *
 */

public class PECoff {

    /**
     * IMAGE_FILE_HEADER structure defines
     */
    public enum IMAGE_FILE_HEADER {
                     Machine( 0, 2),
            NumberOfSections( 2, 2),
               TimeDateStamp( 4, 4),
        PointerToSymbolTable( 8, 4),
             NumberOfSymbols(12, 4),
        SizeOfOptionalHeader(16, 2),
             Characteristics(18, 2);

        public final int off;
        public final int sz;

        IMAGE_FILE_HEADER(int offset, int size) {
            this.off = offset;
            this.sz = size;
        }

        public static int totalsize = 20;

        /**
         * IMAGE_FILE_HEADER defines
         */

        /**
         * Machine
         */
        public static final char IMAGE_FILE_MACHINE_UNKNOWN = 0x0;
        public static final char IMAGE_FILE_MACHINE_AMD64   = 0x8664;

    }

    /**
     * IMAGE_SECTION_HEADER structure defines
     */
    public enum IMAGE_SECTION_HEADER {
                        Name( 0, 8),
             PhysicalAddress( 8, 4),
                 VirtualSize( 8, 4),
              VirtualAddress(12, 4),
               SizeOfRawData(16, 4),
            PointerToRawData(20, 4),
        PointerToRelocations(24, 4),
        PointerToLinenumbers(28, 4),
         NumberOfRelocations(32, 2),
         NumberOfLinenumbers(34, 2),
             Characteristics(36, 4);

        public final int off;
        public final int sz;

        IMAGE_SECTION_HEADER(int offset, int size) {
            this.off = offset;
            this.sz = size;
        }

        public static int totalsize = 40;

        /**
         * IMAGE_SECTION_HEADER defines
         */

        /**
         * Characteristics
         */
        public static final int IMAGE_SCN_CNT_CODE               = 0x20;
        public static final int IMAGE_SCN_CNT_INITIALIZED_DATA   = 0x40;
        public static final int IMAGE_SCN_CNT_UNINITIALIZED_DATA = 0x80;
        public static final int IMAGE_SCN_LNK_COMDAT             = 0x1000;
        public static final int IMAGE_SCN_LNK_INFO               = 0x200;
        public static final int IMAGE_SCN_LNK_REMOVE             = 0x800;

        public static final int IMAGE_SCN_ALIGN_1BYTES           = 0x100000;
        public static final int IMAGE_SCN_ALIGN_2BYTES           = 0x200000;
        public static final int IMAGE_SCN_ALIGN_4BYTES           = 0x300000;
        public static final int IMAGE_SCN_ALIGN_8BYTES           = 0x400000;
        public static final int IMAGE_SCN_ALIGN_16BYTES          = 0x500000;
        public static final int IMAGE_SCN_ALIGN_MASK             = 0xf00000;
        public static final int IMAGE_SCN_ALIGN_SHIFT            = 20;

        public static final int IMAGE_SCN_LNK_NRELOC_OVFL        = 0x01000000;

        public static final int IMAGE_SCN_MEM_SHARED             = 0x10000000;
        public static final int IMAGE_SCN_MEM_EXECUTE            = 0x20000000;
        public static final int IMAGE_SCN_MEM_READ               = 0x40000000;
        public static final int IMAGE_SCN_MEM_WRITE              = 0x80000000;

    }

    /**
     * Symbol table entry definitions
     *
     * IMAGE_SYMBOL structure defines
     */
    public enum IMAGE_SYMBOL {
                   ShortName( 0, 8),
                       Short( 0, 4),
                        Long( 4, 4),
                       Value( 8, 4),
               SectionNumber(12, 2),
                        Type(14, 2),
                StorageClass(16, 1),
          NumberOfAuxSymbols(17, 1);

        public final int off;
        public final int sz;

        IMAGE_SYMBOL(int offset, int size) {
            this.off = offset;
            this.sz = size;
        }

        public static int totalsize = 18;

        /**
         * Type
         */
        public static final int IMAGE_SYM_DTYPE_NONE     = 0x0;
        public static final int IMAGE_SYM_DTYPE_FUNCTION = 0x20;

        /**
         * StorageClass
         */
        public static final int IMAGE_SYM_CLASS_NULL     = 0x0;
        public static final int IMAGE_SYM_CLASS_EXTERNAL = 0x2;
        public static final int IMAGE_SYM_CLASS_STATIC   = 0x3;
        public static final int IMAGE_SYM_CLASS_LABEL    = 0x6;

    }

    /**
     * IMAGE_RELOCATION structure defines
     */
    public enum IMAGE_RELOCATION {
              VirtualAddress( 0, 4),
            SymbolTableIndex( 4, 4),
                        Type( 8, 2);

        public final int off;
        public final int sz;

        IMAGE_RELOCATION(int offset, int size) {
            this.off = offset;
            this.sz = size;
        }

        public static int totalsize = 10;

        /**
         * Relocation types
         */
        public static final int IMAGE_REL_AMD64_ABSOLUTE = 0x0;
        public static final int IMAGE_REL_AMD64_ADDR32   = 0x2;
        public static final int IMAGE_REL_AMD64_ADDR64   = 0x1;
        public static final int IMAGE_REL_AMD64_REL32    = 0x4;
        public static final int IMAGE_REL_AMD64_REL32_1  = 0x5;
        public static final int IMAGE_REL_AMD64_REL32_2  = 0x6;
        public static final int IMAGE_REL_AMD64_REL32_3  = 0x7;
        public static final int IMAGE_REL_AMD64_REL32_4  = 0x8;
        public static final int IMAGE_REL_AMD64_REL32_5  = 0x9;

    }

}
