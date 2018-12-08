/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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



package jdk.tools.jaotc.binformat.macho;

//@formatter:off
//@Checkstyle: stop

/**
 *
 * Support for the creation of Mach-o Object files. Current support is limited to 64 bit x86_64.
 *
 * File Format Overview:
 *
 *   mach_header
 *   load_commands
 *      Typical Mac OSX 64-bit object files have these 4 load_commands
 *      (LC_SEGMENT_64, LC_SYMTAB, LC_VERSIN_MIN_MACOSX, LC_DYSYMTAB)
 *   Segments corresponding to load_commands
 *      (which each include multiple Sections)
 */

final class MachO {

    /**
     * mach_header_64 structure defines
     */
    enum mach_header_64 {
                 magic( 0, 4),
               cputype( 4, 4),
            cpusubtype( 8, 4),
              filetype(12, 4),
                 ncmds(16, 4),
            sizeofcmds(20, 4),
                 flags(24, 4),
              reserved(28, 4);

        final int off;
        final int sz;

        mach_header_64(int offset, int size) {
            this.off = offset;
            this.sz = size;
        }

        static int totalsize = 32;

        /**
         * mach_header_64 defines
         */
        static final int MH_MAGIC                   = 0xfeedface;
        static final int MH_MAGIC_64                = 0xfeedfacf;
        static final int MH_SUBSECTIONS_VIA_SYMBOLS = 0x2000;

        /**
         * filetype
         */
        static final int MH_OBJECT = 0x1;

        /**
         * cputype
         */
        static final int CPU_TYPE_ANY              = -1;
        static final int CPU_ARCH_ABI64            = 0x1000000;
        static final int CPU_TYPE_X86_64           = 0x1000007;
        static final int CPU_TYPE_ARM64            = 0x100000c;
        /**
         * cpusubtype
         */
        static final int CPU_SUBTYPE_I386_ALL      = 3;
        static final int CPU_SUBTYPE_ARM64_ALL     = 0;
        static final int CPU_SUBTYPE_LITTLE_ENDIAN = 0;
        static final int CPU_SUBTYPE_BIG_ENDIAN    = 1;

    }

    /**
     * segment_command_64 structure defines
     */
    enum segment_command_64 {
                   cmd( 0, 4),
               cmdsize( 4, 4),
               segname( 8,16),
                vmaddr(24, 8),
                vmsize(32, 8),
               fileoff(40, 8),
              filesize(48, 8),
               maxprot(56, 4),
              initprot(60, 4),
                nsects(64, 4),
                 flags(68, 4);

        final int off;
        final int sz;

        segment_command_64(int offset, int size) {
            this.off = offset;
            this.sz = size;
        }

        static int totalsize = 72;

        static final int LC_SEGMENT_64           = 0x19;
    }

    /**
     * section_64 structure defines
     */
    enum section_64 {
              sectname( 0,16),
               segname(16,16),
                  addr(32, 8),
                  size(40, 8),
                offset(48, 4),
                 align(52, 4),
                reloff(56, 4),
                nreloc(60, 4),
                 flags(64, 4),
             reserved1(68, 4),
             reserved2(72, 4),
             reserved3(76, 4);

        final int off;
        final int sz;

        section_64(int offset, int size) {
            this.off = offset;
            this.sz = size;
        }

        static int totalsize = 80;

        static int S_REGULAR                = 0x0;
        static int S_CSTRING_LITERALS       = 0x2;
        static int S_ATTR_PURE_INSTRUCTIONS = 0x80000000;
        static int S_ATTR_SOME_INSTRUCTIONS = 0x400;
    }

    /**
     * version_min_command structure defines
     */
    enum version_min_command {
                   cmd( 0, 4),
               cmdsize( 4, 4),
               version( 8, 4),
                   sdk(12, 4);

        final int off;
        final int sz;

        version_min_command(int offset, int size) {
            this.off = offset;
            this.sz = size;
        }

        static int totalsize = 16;

        static final int LC_VERSION_MIN_MACOSX   = 0x24;
        static final int LC_VERSION_MIN_IPHONEOS = 0x25;
    }

    /**
     * symtab_command structure defines
     */
    enum symtab_command {
                   cmd( 0, 4),
               cmdsize( 4, 4),
                symoff( 8, 4),
                 nsyms(12, 4),
                stroff(16, 4),
               strsize(20, 4);

        final int off;
        final int sz;

        symtab_command(int offset, int size) {
            this.off = offset;
            this.sz = size;
        }

        static int totalsize = 24;

        static final int LC_SYMTAB               = 0x2;
    }

    /**
     * Symbol table entry definitions
     *
     * nlist_64 structure defines
     */
    enum nlist_64 {
                n_strx( 0, 4),
                n_type( 4, 1),
                n_sect( 5, 1),
                n_desc( 6, 2),
               n_value( 8, 8);

        final int off;
        final int sz;

        nlist_64(int offset, int size) {
            this.off = offset;
            this.sz = size;
        }

        static int totalsize = 16;

        static final int N_EXT                = 0x1;
        static final int N_TYPE               = 0xe;
        static final int N_UNDF               = 0x0;
        static final int N_SECT               = 0xe;
    }

    /**
     * dysymtab_command structure defines
     */
    enum dysymtab_command {
                   cmd( 0, 4),
               cmdsize( 4, 4),
             ilocalsym( 8, 4),
             nlocalsym(12, 4),
            iextdefsym(16, 4),
            nextdefsym(20, 4),
             iundefsym(24, 4),
             nundefsym(28, 4),
                tocoff(32, 4),
                  ntoc(36, 4),
             modtaboff(40, 4),
               nmodtab(44, 4),
          extrefsymoff(48, 4),
           nextrefsyms(52, 4),
        indirectsymoff(56, 4),
         nindirectsyms(60, 4),
             extreloff(64, 4),
               nextrel(68, 4),
             locreloff(72, 4),
               nlocrel(76, 4);

        final int off;
        final int sz;

        dysymtab_command(int offset, int size) {
            this.off = offset;
            this.sz = size;
        }

        static int totalsize = 80;

        static final int LC_DYSYMTAB             = 0xb;
    }

    /**
     * relocation_info structure defines
     */
    enum reloc_info {
             r_address( 0, 4),
           r_relocinfo( 4, 4);

        final int off;
        final int sz;

        reloc_info(int offset, int size) {
            this.off = offset;
            this.sz = size;
        }

        static int totalsize = 8;

        static final int REL_SYMNUM_MASK         = 0xffffff;
        static final int REL_SYMNUM_SHIFT        = 0x0;
        static final int REL_PCREL_MASK          = 0x1;
        static final int REL_PCREL_SHIFT         = 0x18;
        static final int REL_LENGTH_MASK         = 0x3;
        static final int REL_LENGTH_SHIFT        = 0x19;
        static final int REL_EXTERN_MASK         = 0x1;
        static final int REL_EXTERN_SHIFT        = 0x1b;
        static final int REL_TYPE_MASK           = 0xf;
        static final int REL_TYPE_SHIFT          = 0x1c;

        /* reloc_type_x86_64 defines */

        static final int X86_64_RELOC_NONE      = 0x0;
        static final int X86_64_RELOC_BRANCH    = 0x2;
        static final int X86_64_RELOC_GOT       = 0x4;
        static final int X86_64_RELOC_GOT_LOAD  = 0x3;
        static final int X86_64_RELOC_SIGNED    = 0x1;
        static final int X86_64_RELOC_UNSIGNED  = 0x0;
    }
}
