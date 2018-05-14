/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2018, Red Hat Inc. All rights reserved.
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

/**
 *
 * Support for the creation of Elf Object files. Current support is limited to 64 bit x86_64.
 *
 */

final class Elf {
    //@formatter:off
    /**
     * Elf64_Ehdr structure defines
     */
    enum Elf64_Ehdr {
               e_ident( 0,16),
                e_type(16, 2),
             e_machine(18, 2),
             e_version(20, 4),
               e_entry(24, 8),
               e_phoff(32, 8),
               e_shoff(40, 8),
               e_flags(48, 4),
              e_ehsize(52, 2),
           e_phentsize(54, 2),
               e_phnum(56, 2),
           e_shentsize(58, 2),
               e_shnum(60, 2),
            e_shstrndx(62, 2);

        final int off;
        final int sz;

        Elf64_Ehdr(int offset, int size) {
            this.off = offset;
            this.sz = size;
        }

        static int totalsize = 64;

        /**
         * Elf64_Ehdr defines
         */

        /**
         * e_ident
         */
        static final int  EI_MAG0        = 0;
        static final byte ELFMAG0        = 0x7f;
        static final int  EI_MAG1        = 1;
        static final byte ELFMAG1        = 0x45;
        static final int  EI_MAG2        = 2;
        static final byte ELFMAG2        = 0x4c;
        static final int  EI_MAG3        = 3;
        static final byte ELFMAG3        = 0x46;
        static final int  EI_CLASS       = 4;
        static final byte ELFCLASS64     = 0x2;

        static final int  EI_DATA        = 5;
        static final byte ELFDATA2LSB    = 0x1;

        static final int  EI_VERSION     = 6;
        static final byte EV_CURRENT     = 0x1;

        static final int  EI_OSABI       = 7;
        static final byte ELFOSABI_NONE  = 0x0;

        /**
         * e_type
         */
        static final char ET_REL         = 0x1;

        /**
         * e_machine
         */
        static final char EM_NONE        = 0;
        static final char EM_X86_64      = 62;
        static final char EM_AARCH64     = 183;

    }

    /**
     * Elf64_Shdr structure defines
     */
    enum Elf64_Shdr {
               sh_name( 0, 4),
               sh_type( 4, 4),
              sh_flags( 8, 8),
               sh_addr(16, 8),
             sh_offset(24, 8),
               sh_size(32, 8),
               sh_link(40, 4),
               sh_info(44, 4),
          sh_addralign(48, 8),
            sh_entsize(56, 8);

        final int off;
        final int sz;

        Elf64_Shdr(int offset, int size) {
            this.off = offset;
            this.sz = size;
        }

        static int totalsize = 64;

        /**
         * Elf64_Shdr defines
         */

        /**
         * sh_type
         */
        static final int SHT_PROGBITS   = 0x1;
        static final int SHT_SYMTAB     = 0x2;
        static final int SHT_STRTAB     = 0x3;
        static final int SHT_RELA       = 0x4;
        static final int SHT_NOBITS     = 0x8;
        static final int SHT_REL        = 0x9;

        static final byte SHN_UNDEF     = 0x0;

        /**
         * sh_flag
         */
        static final int SHF_WRITE      = 0x1;
        static final int SHF_ALLOC      = 0x2;
        static final int SHF_EXECINSTR  = 0x4;

    }

    /**
     * Symbol table entry definitions
     *
     * Elf64_Sym structure defines
     */
    enum Elf64_Sym {
               st_name( 0, 4),
               st_info( 4, 1),
              st_other( 5, 1),
              st_shndx( 6, 2),
              st_value( 8, 8),
               st_size(16, 8);

        final int off;
        final int sz;

        Elf64_Sym(int offset, int size) {
            this.off = offset;
            this.sz = size;
        }

        static int totalsize = 24;

        /* ST_BIND is in bits 4-7 of st_info.  ST_TYPE is in low 4 bits */
        static final byte STB_LOCAL   = 0x0;
        static final byte STB_GLOBAL  = 0x1;

        static final byte STT_NOTYPE  = 0x0;
        static final byte STT_OBJECT  = 0x1;
        static final byte STT_FUNC    = 0x2;

        static byte ELF64_ST_INFO(byte bind, byte type) {
            return (byte)(((bind) << 4) + ((type) & 0xf));
        }

    }

    /**
     * Elf64_Rel structure defines
     */
    enum Elf64_Rel {
              r_offset( 0, 8),
                r_info( 8, 8);

        final int off;
        final int sz;

        Elf64_Rel(int offset, int size) {
            this.off = offset;
            this.sz = size;
        }

        static int totalsize = 16;

        /**
         * Relocation types
         */

        static final int R_X86_64_NONE     = 0x0;
        static final int R_X86_64_64       = 0x1;
        static final int R_X86_64_PC32     = 0x2;
        static final int R_X86_64_PLT32    = 0x4;
        static final int R_X86_64_GOTPCREL = 0x9;

        static final int R_AARCH64_NONE     = 256;
        static final int R_AARCH64_ABS64    = 257;
        static final int R_AARCH64_CALL26   = 283;
        static final int R_AARCH64_ADR_GOT_PAGE = 311;
        static final int R_AARCH64_LD64_GOT_LO12_NC = 312;

        static final int R_AARCH64_MOVW_UABS_G0_NC = 264;
        static final int R_AARCH64_MOVW_UABS_G1_NC = 266;
        static final int R_AARCH64_MOVW_UABS_G2_NC = 268;

        static final int R_AARCH64_ADR_PREL_PG_HI21 = 275;
        static final int R_AARCH64_ADD_ABS_LO12_NC = 277;
        static final int R_AARCH64_LDST64_ABS_LO12_NC = 286;
    }

    /**
     * Elf64_Rela structure defines
     */
    enum Elf64_Rela {
              r_offset( 0, 8),
                r_info( 8, 8),
              r_addend(16, 8);

        final int off;
        final int sz;

        Elf64_Rela(int offset, int size) {
            this.off = offset;
            this.sz = size;
        }

        static int totalsize = 24;

        static final int R_X86_64_NONE     = 0x0;
        static final int R_X86_64_64       = 0x1;
        static final int R_X86_64_PC32     = 0x2;
        static final int R_X86_64_PLT32    = 0x4;
        static final int R_X86_64_GOTPCREL = 0x9;

        static final int R_AARCH64_NONE     = 256;
        static final int R_AARCH64_ABS64    = 257;
        static final int R_AARCH64_CALL26   = 283;
        static final int R_AARCH64_ADR_GOT_PAGE = 311;
        static final int R_AARCH64_LD64_GOT_LO12_NC = 312;

        static final int R_AARCH64_MOVW_UABS_G0_NC = 264;
        static final int R_AARCH64_MOVW_UABS_G1_NC = 266;
        static final int R_AARCH64_MOVW_UABS_G2_NC = 268;

        static final int R_AARCH64_ADR_PREL_PG_HI21 = 275;
        static final int R_AARCH64_ADD_ABS_LO12_NC = 277;
        static final int R_AARCH64_LDST64_ABS_LO12_NC = 286;

        static long ELF64_R_INFO(int symidx, int type) {
            return (((long)symidx << 32) + type);
        }

    }
    //@formatter:on
}
