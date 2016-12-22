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

package jdk.tools.jaotc.jnilibelf;

/**
 * Class that abstracts ELF relocations.
 *
 */
public interface JNIELFRelocation {
    int R_UNDEF = -1;

    /**
     * x86-specific relocation types.
     *
     */
    public interface I386 {
        /* i386 relocs. */

        int R_386_NONE = 0; /* No reloc */
        int R_386_32 = 1; /* Direct 32 bit */
        int R_386_PC32 = 2; /* PC relative 32 bit */
        int R_386_GOT32 = 3; /* 32 bit GOT entry */
        int R_386_PLT32 = 4; /* 32 bit PLT address */
        int R_386_COPY = 5; /* Copy symbol at runtime */
        int R_386_GLOB_DAT = 6; /* Create GOT entry */
        int R_386_JMP_SLOT = 7; /* Create PLT entry */
        int R_386_RELATIVE = 8; /* Adjust by program base */
        int R_386_GOTOFF = 9; /* 32 bit offset to GOT */
        int R_386_GOTPC = 10; /* 32 bit PC relative offset to GOT */
        int R_386_32PLT = 11;
        int R_386_TLS_TPOFF = 14; /* Offset in static TLS block */
        int R_386_TLS_IE = 15; /* Address of GOT entry for static TLS block offset */
        int R_386_TLS_GOTIE = 16; /* GOT entry for static TLS block offset */
        int R_386_TLS_LE = 17; /* Offset relative to static TLS block */
        int R_386_TLS_GD = 18; /* Direct 32 bit for GNU version of general dynamic thread local data */
        int R_386_TLS_LDM = 19; /*
                                 * Direct 32 bit for GNU version of local dynamic thread local data
                                 * in LE code
                                 */
        int R_386_16 = 20;
        int R_386_PC16 = 21;
        int R_386_8 = 22;
        int R_386_PC8 = 23;
        int R_386_TLS_GD_32 = 24; /* Direct 32 bit for general dynamic thread local data */
        int R_386_TLS_GD_PUSH = 25; /* Tag for pushl in GD TLS code */
        int R_386_TLS_GD_CALL = 26; /* Relocation for call to __tls_get_addr() */
        int R_386_TLS_GD_POP = 27; /* Tag for popl in GD TLS code */
        int R_386_TLS_LDM_32 = 28; /* Direct 32 bit for local dynamic thread local data in LE code */
        int R_386_TLS_LDM_PUSH = 29; /* Tag for pushl in LDM TLS code */
        int R_386_TLS_LDM_CALL = 30; /* Relocation for call to __tls_get_addr() in LDM code */
        int R_386_TLS_LDM_POP = 31; /* Tag for popl in LDM TLS code */
        int R_386_TLS_LDO_32 = 32; /* Offset relative to TLS block */
        int R_386_TLS_IE_32 = 33; /* GOT entry for negated static TLS block offset */
        int R_386_TLS_LE_32 = 34; /* Negated offset relative to static TLS block */
        int R_386_TLS_DTPMOD32 = 35; /* ID of module containing symbol */
        int R_386_TLS_DTPOFF32 = 36; /* Offset in TLS block */
        int R_386_TLS_TPOFF32 = 37; /* Negated offset in static TLS block */
        int R_386_SIZE32 = 38; /* 32-bit symbol size */
        int R_386_TLS_GOTDESC = 39; /* GOT offset for TLS descriptor. */
        int R_386_TLS_DESC_CALL = 40; /* Marker of call through TLS descriptor for relaxation. */
        int R_386_TLS_DESC = 41; /*
                                  * TLS descriptor containing pointer to code and to argument,
                                  * returning the TLS offset for the symbol.
                                  */
        int R_386_IRELATIVE = 42; /* Adjust indirectly by program base */
        /* Keep this the last entry. */
        int R_386_NUM = 43;
    }

    /**
     * x86_64-specific relocation types.
     */
    public interface X86_64 {
        /* AMD x86-64 relocations. */
        int R_X86_64_NONE = 0; /* No reloc */
        int R_X86_64_64 = 1; /* Direct 64 bit */
        int R_X86_64_PC32 = 2; /* PC relative 32 bit signed */
        int R_X86_64_GOT32 = 3; /* 32 bit GOT entry */
        int R_X86_64_PLT32 = 4; /* 32 bit PLT address */
        int R_X86_64_COPY = 5; /* Copy symbol at runtime */
        int R_X86_64_GLOB_DAT = 6; /* Create GOT entry */
        int R_X86_64_JUMP_SLOT = 7; /* Create PLT entry */
        int R_X86_64_RELATIVE = 8; /* Adjust by program base */
        int R_X86_64_GOTPCREL = 9; /* 32 bit signed PC relative offset to GOT */
        int R_X86_64_32 = 10; /* Direct 32 bit zero extended */
        int R_X86_64_32S = 11; /* Direct 32 bit sign extended */
        int R_X86_64_16 = 12; /* Direct 16 bit zero extended */
        int R_X86_64_PC16 = 13; /* 16 bit sign extended pc relative */
        int R_X86_64_8 = 14; /* Direct 8 bit sign extended */
        int R_X86_64_PC8 = 15; /* 8 bit sign extended pc relative */
        int R_X86_64_DTPMOD64 = 16; /* ID of module containing symbol */
        int R_X86_64_DTPOFF64 = 17; /* Offset in module's TLS block */
        int R_X86_64_TPOFF64 = 18; /* Offset in initial TLS block */
        int R_X86_64_TLSGD = 19; /*
                                  * 32 bit signed PC relative offset to two GOT entries for GD
                                  * symbol
                                  */
        int R_X86_64_TLSLD = 20; /*
                                  * 32 bit signed PC relative offset to two GOT entries for LD
                                  * symbol
                                  */
        int R_X86_64_DTPOFF32 = 21; /* Offset in TLS block */
        int R_X86_64_GOTTPOFF = 22; /*
                                     * 32 bit signed PC relative offset to GOT entry for IE symbol
                                     */
        int R_X86_64_TPOFF32 = 23; /* Offset in initial TLS block */
        int R_X86_64_PC64 = 24; /* PC relative 64 bit */
        int R_X86_64_GOTOFF64 = 25; /* 64 bit offset to GOT */
        int R_X86_64_GOTPC32 = 26; /* 32 bit signed pc relative offset to GOT */
        int R_X86_64_GOT64 = 27; /* 64-bit GOT entry offset */
        int R_X86_64_GOTPCREL64 = 28; /* 64-bit PC relative offset to GOT entry */
        int R_X86_64_GOTPC64 = 29; /* 64-bit PC relative offset to GOT */
        int R_X86_64_GOTPLT64 = 30; /* like GOT64, says PLT entry needed */
        int R_X86_64_PLTOFF64 = 31; /* 64-bit GOT relative offset to PLT entry */
        int R_X86_64_SIZE32 = 32; /* Size of symbol plus 32-bit addend */
        int R_X86_64_SIZE64 = 33; /* Size of symbol plus 64-bit addend */
        int R_X86_64_GOTPC32_TLSDESC = 34; /* GOT offset for TLS descriptor. */
        int R_X86_64_TLSDESC_CALL = 35; /*
                                         * Marker for call through TLS descriptor.
                                         */
        int R_X86_64_TLSDESC = 36; /* TLS descriptor. */
        int R_X86_64_IRELATIVE = 37; /* Adjust indirectly by program base */
        int R_X86_64_RELATIVE64 = 38; /* 64-bit adjust by program base */

        int R_X86_64_NUM = 39;
    }
}
