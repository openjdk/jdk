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

public class JNILibELFAPI {

    static {
        System.loadLibrary("jelfshim");
    }

    /**
     * Definitions for file open.
     */
    public static enum OpenFlags {
        O_RDONLY(0x0),
        O_WRONLY(0x1),
        O_RDWR(0x2),
        O_CREAT(0x40);

        private final int intVal;

        private OpenFlags(int v) {
            intVal = v;
        }

        public int intValue() {
            return intVal;
        }
    }

    /**
     * Definitions reflecting those in elf.h.
     *
     */
    public interface ELF {
        int EI_NIDENT = 16;

        int EI_CLASS = 4; /* File class byte index */
        int ELFCLASSNONE = 0; /* Invalid class */
        int ELFCLASS32 = 1; /* 32-bit objects */
        int ELFCLASS64 = 2; /* 64-bit objects */
        int ELFCLASSNUM = 3;

        int EI_DATA = 5; /* Data encoding byte index */
        int ELFDATANONE = 0; /* Invalid data encoding */
        int ELFDATA2LSB = 1; /* 2's complement, little endian */
        int ELFDATA2MSB = 2; /* 2's complement, big endian */
        int ELFDATANUM = 3;

        // Legal architecture values for e_machine (add others as needed)
        int EM_NONE = 0; /* No machine */
        int EM_SPARC = 2; /* SUN SPARC */
        int EM_386 = 3; /* Intel 80386 */
        int EM_SPARCV9 = 43; /* SPARC v9 64-bit */
        int EM_X64_64 = 62; /* AMD x86-64 architecture */

        /* Legal values for e_type (object file type). */

        int ET_NONE = 0; /* No file type */
        int ET_REL = 1; /* Relocatable file */
        int ET_EXEC = 2; /* Executable file */
        int ET_DYN = 3; /* Shared object file */
        int ET_CORE = 4; /* Core file */
        int ET_NUM = 5; /* Number of defined types */
        int ET_LOOS = 0xfe00; /* OS-specific range start */
        int ET_HIOS = 0xfeff; /* OS-specific range end */
        int ET_LOPROC = 0xff00; /* Processor-specific range start */
        int ET_HIPROC = 0xffff; /* Processor-specific range end */

        /* Legal values for e_version (version). */

        int EV_NONE = 0; /* Invalid ELF version */
        int EV_CURRENT = 1; /* Current version */
        int EV_NUM = 2;

        /* Legal values for p_type (segment type). */

        int PT_NULL = 0; /* Program header table entry unused */
        int PT_LOAD = 1; /* Loadable program segment */
        int PT_DYNAMIC = 2; /* Dynamic linking information */
        int PT_INTERP = 3; /* Program interpreter */
        int PT_NOTE = 4; /* Auxiliary information */
        int PT_SHLIB = 5; /* Reserved */
        int PT_PHDR = 6; /* Entry for header table itself */
        int PT_TLS = 7; /* Thread-local storage segment */
        int PT_NUM = 8; /* Number of defined types */
        int PT_LOOS = 0x60000000; /* Start of OS-specific */
        int PT_GNU_EH_FRAME = 0x6474e550; /* GCC .eh_frame_hdr segment */
        int PT_GNU_STACK = 0x6474e551; /* Indicates stack executability */
        int PT_GNU_RELRO = 0x6474e552; /* Read-only after relocation */
        int PT_LOSUNW = 0x6ffffffa;
        int PT_SUNWBSS = 0x6ffffffa; /* Sun Specific segment */
        int PT_SUNWSTACK = 0x6ffffffb; /* Stack segment */
        int PT_HISUNW = 0x6fffffff;
        int PT_HIOS = 0x6fffffff; /* End of OS-specific */
        int PT_LOPROC = 0x70000000; /* Start of processor-specific */
        int PT_HIPROC = 0x7fffffff; /* End of processor-specific */

        /* Special section indices. */

        int SHN_UNDEF = 0; /* Undefined section */
        int SHN_LORESERVE = 0xff00; /* Start of reserved indices */
        int SHN_LOPROC = 0xff00; /* Start of processor-specific */
        int SHN_BEFORE = 0xff00; /* Order section before all others (Solaris). */
        int SHN_AFTER = 0xff01; /* Order section after all others (Solaris). */
        int SHN_HIPROC = 0xff1f; /* End of processor-specific */
        int SHN_LOOS = 0xff20; /* Start of OS-specific */
        int SHN_HIOS = 0xff3f; /* End of OS-specific */
        int SHN_ABS = 0xfff1; /* Associated symbol is absolute */
        int SHN_COMMON = 0xfff2; /* Associated symbol is common */
        int SHN_XINDEX = 0xffff; /* Index is in extra table. */
        int SHN_HIRESERVE = 0xffff; /* End of reserved indices */

        /* Legal values for sh_type (section type). */

        int SHT_NULL = 0; /* Section header table entry unused */
        int SHT_PROGBITS = 1; /* Program data */
        int SHT_SYMTAB = 2; /* Symbol table */
        int SHT_STRTAB = 3; /* String table */
        int SHT_RELA = 4; /* Relocation entries with addends */
        int SHT_HASH = 5; /* Symbol hash table */
        int SHT_DYNAMIC = 6; /* Dynamic linking information */
        int SHT_NOTE = 7; /* Notes */
        int SHT_NOBITS = 8; /* Program space with no data (bss) */
        int SHT_REL = 9; /* Relocation entries, no addends */
        int SHT_SHLIB = 10; /* Reserved */
        int SHT_DYNSYM = 11; /* Dynamic linker symbol table */
        int SHT_INIT_ARRAY = 14; /* Array of constructors */
        int SHT_FINI_ARRAY = 15; /* Array of destructors */
        int SHT_PREINIT_ARRAY = 16; /* Array of pre-constructors */
        int SHT_GROUP = 17; /* Section group */
        int SHT_SYMTAB_SHNDX = 18; /* Extended section indeces */
        int SHT_NUM = 19; /* Number of defined types. */
        int SHT_LOOS = 0x60000000; /* Start OS-specific. */
        int SHT_GNU_ATTRIBUTES = 0x6ffffff5; /* Object attributes. */
        int SHT_GNU_HASH = 0x6ffffff6; /* GNU-style hash table. */
        int SHT_GNU_LIBLIST = 0x6ffffff7; /* Prelink library list */
        int SHT_CHECKSUM = 0x6ffffff8; /* Checksum for DSO content. */
        int SHT_LOSUNW = 0x6ffffffa; /* Sun-specific low bound. */
        int SHT_SUNW_move = 0x6ffffffa;
        int SHT_SUNW_COMDAT = 0x6ffffffb;
        int SHT_SUNW_syminfo = 0x6ffffffc;
        int SHT_GNU_verdef = 0x6ffffffd; /* Version definition section. */
        int SHT_GNU_verneed = 0x6ffffffe; /* Version needs section. */
        int SHT_GNU_versym = 0x6fffffff; /* Version symbol table. */
        int SHT_HISUNW = 0x6fffffff; /* Sun-specific high bound. */
        int SHT_HIOS = 0x6fffffff; /* End OS-specific type */
        int SHT_LOPROC = 0x70000000; /* Start of processor-specific */
        int SHT_HIPROC = 0x7fffffff; /* End of processor-specific */
        int SHT_LOUSER = 0x80000000; /* Start of application-specific */
        int SHT_HIUSER = 0x8fffffff; /* End of application-specific */

        /* Legal values for sh_flags (section flags). */

        int SHF_WRITE = (1 << 0); /* Writable */
        int SHF_ALLOC = (1 << 1); /* Occupies memory during execution */
        int SHF_EXECINSTR = (1 << 2); /* Executable */
        int SHF_MERGE = (1 << 4); /* Might be merged */
        int SHF_STRINGS = (1 << 5); /* Contains nul-terminated strings */
        int SHF_INFO_LINK = (1 << 6); /* `sh_info' contains SHT index */
        int SHF_LINK_ORDER = (1 << 7); /* Preserve order after combining */
        int SHF_OS_NONCONFORMING = (1 << 8); /* Non-standard OS specific handling required */
        int SHF_GROUP = (1 << 9); /* Section is member of a group. */
        int SHF_TLS = (1 << 10); /* Section hold thread-local data. */
        int SHF_MASKOS = 0x0ff00000; /* OS-specific. */
        int SHF_MASKPROC = 0xf0000000; /* Processor-specific */
        int SHF_ORDERED = (1 << 30); /* Special ordering requirement (Solaris). */
        int SHF_EXCLUDE = (1 << 31); /*
                                      * Section is excluded unless referenced or allocated
                                      * (Solaris).
                                      */

        /* Legal values for ST_BIND subfield of st_info (symbol binding). */

        int STB_LOCAL = 0; /* Local symbol */
        int STB_GLOBAL = 1; /* Global symbol */
        int STB_WEAK = 2; /* Weak symbol */
        int STB_NUM = 3; /* Number of defined types. */
        int STB_LOOS = 10; /* Start of OS-specific */
        int STB_GNU_UNIQUE = 10; /* Unique symbol. */
        int STB_HIOS = 12; /* End of OS-specific */
        int STB_LOPROC = 13; /* Start of processor-specific */
        int STB_HIPROC = 15; /* End of processor-specific */

        /* Legal values for ST_TYPE subfield of st_info (symbol type). */

        int STT_NOTYPE = 0; /* Symbol type is unspecified */
        int STT_OBJECT = 1; /* Symbol is a data object */
        int STT_FUNC = 2; /* Symbol is a code object */
        int STT_SECTION = 3; /* Symbol associated with a section */
        int STT_FILE = 4; /* Symbol's name is file name */
        int STT_COMMON = 5; /* Symbol is a common data object */
        int STT_TLS = 6; /* Symbol is thread-local data object */
        int STT_NUM = 7; /* Number of defined types. */
        int STT_LOOS = 10; /* Start of OS-specific */
        int STT_GNU_IFUNC = 10; /* Symbol is indirect code object */
        int STT_HIOS = 12; /* End of OS-specific */
        int STT_LOPROC = 13; /* Start of processor-specific */
        int STT_HIPROC = 15; /* End of processor-specific */
    }

    /**
     * Definitions reflecting those in libelf.h.
     *
     */
    public interface LibELF {

        public static enum Elf_Cmd {
            ELF_C_NULL("NULL"), /* Nothing, terminate, or compute only. */
            ELF_C_READ("READ"), /* Read .. */
            ELF_C_RDWR("RDWR"), /* Read and write .. */
            ELF_C_WRITE("WRITE"), /* Write .. */
            ELF_C_CLR("CLR"), /* Clear flag. */
            ELF_C_SET("SET"), /* Set flag. */
            ELF_C_FDDONE("FDDONE"), /*
                                     * Signal that file descriptor will not be used anymore.
                                     */
            ELF_C_FDREAD("FDREAD"), /*
                                     * Read rest of data so that file descriptor is not used
                                     * anymore.
                                     */
            /* The following are Linux-only extensions. */
            ELF_C_READ_MMAP("READ_MMAP"), /* Read, but mmap the file if possible. */
            ELF_C_RDWR_MMAP("RDWR_MMAP"), /* Read and write, with mmap. */
            ELF_C_WRITE_MMAP("WRITE_MMAP"), /* Write, with mmap. */
            ELF_C_READ_MMAP_PRIVATE("READ_MMAP_PRIVATE"), /*
                                                           * Read, but memory is writable, results
                                                           * are not written to the file.
                                                           */
            ELF_C_EMPTY("EMPTY"), /* Copy basic file data but not the content. */
            /* The following are SunOS-only enums */
            ELF_C_WRIMAGE("WRIMAGE"),
            ELF_C_IMAGE("IMAGE"),
            /* Common last entry. */
            ELF_C_NUM("NUM");
            private final int intVal;
            private final String name;

            private Elf_Cmd(String cmd) {
                name = "ELF_C_" + cmd;
                switch (cmd) {
                    case "NULL":
                        // ELF_C_NULL has the same enum ordinal on both Linux and SunOS
                        intVal = jdk.tools.jaotc.jnilibelf.linux.Elf_Cmd.ELF_C_NULL.ordinal();
                        break;

                    case "READ":
                        // ELF_C_READ has the same enum ordinal on both Linux and SunOS
                        intVal = jdk.tools.jaotc.jnilibelf.linux.Elf_Cmd.ELF_C_READ.ordinal();
                        break;

                    // Enums defined in libelf.h of both Linux and SunOS
                    // but with different ordinals
                    case "RDWR":
                        if (JNIELFTargetInfo.getOsName().equals("linux")) {
                            intVal = jdk.tools.jaotc.jnilibelf.linux.Elf_Cmd.ELF_C_RDWR.ordinal();
                        } else if (JNIELFTargetInfo.getOsName().equals("sunos")) {
                            intVal = jdk.tools.jaotc.jnilibelf.sunos.Elf_Cmd.ELF_C_RDWR.ordinal();
                        } else {
                            // Unsupported platform
                            intVal = -1;
                        }
                        break;

                    case "WRITE":
                        if (JNIELFTargetInfo.getOsName().equals("linux")) {
                            intVal = jdk.tools.jaotc.jnilibelf.linux.Elf_Cmd.ELF_C_WRITE.ordinal();
                        } else if (JNIELFTargetInfo.getOsName().equals("sunos")) {
                            intVal = jdk.tools.jaotc.jnilibelf.sunos.Elf_Cmd.ELF_C_WRITE.ordinal();
                        } else {
                            // Unsupported platform
                            intVal = -1;
                        }
                        break;

                    case "CLR":
                        if (JNIELFTargetInfo.getOsName().equals("linux")) {
                            intVal = jdk.tools.jaotc.jnilibelf.linux.Elf_Cmd.ELF_C_CLR.ordinal();
                        } else if (JNIELFTargetInfo.getOsName().equals("sunos")) {
                            intVal = jdk.tools.jaotc.jnilibelf.sunos.Elf_Cmd.ELF_C_CLR.ordinal();
                        } else {
                            // Unsupported platform
                            intVal = -1;
                        }
                        break;

                    case "SET":
                        if (JNIELFTargetInfo.getOsName().equals("linux")) {
                            intVal = jdk.tools.jaotc.jnilibelf.linux.Elf_Cmd.ELF_C_SET.ordinal();
                        } else if (JNIELFTargetInfo.getOsName().equals("sunos")) {
                            intVal = jdk.tools.jaotc.jnilibelf.sunos.Elf_Cmd.ELF_C_SET.ordinal();
                        } else {
                            // Unsupported platform
                            intVal = -1;
                        }
                        break;

                    case "FDDONE":
                        if (JNIELFTargetInfo.getOsName().equals("linux")) {
                            intVal = jdk.tools.jaotc.jnilibelf.linux.Elf_Cmd.ELF_C_FDDONE.ordinal();
                        } else if (JNIELFTargetInfo.getOsName().equals("sunos")) {
                            intVal = jdk.tools.jaotc.jnilibelf.sunos.Elf_Cmd.ELF_C_FDDONE.ordinal();
                        } else {
                            // Unsupported platform
                            intVal = -1;
                        }
                        break;

                    case "FDREAD":
                        if (JNIELFTargetInfo.getOsName().equals("linux")) {
                            intVal = jdk.tools.jaotc.jnilibelf.linux.Elf_Cmd.ELF_C_FDREAD.ordinal();
                        } else if (JNIELFTargetInfo.getOsName().equals("sunos")) {
                            intVal = jdk.tools.jaotc.jnilibelf.sunos.Elf_Cmd.ELF_C_FDREAD.ordinal();
                        } else {
                            // Unsupported platform
                            intVal = -1;
                        }
                        break;

                    case "NUM":
                        if (JNIELFTargetInfo.getOsName().equals("linux")) {
                            intVal = jdk.tools.jaotc.jnilibelf.linux.Elf_Cmd.ELF_C_NUM.ordinal();
                        } else if (JNIELFTargetInfo.getOsName().equals("sunos")) {
                            intVal = jdk.tools.jaotc.jnilibelf.sunos.Elf_Cmd.ELF_C_NUM.ordinal();
                        } else {
                            // Unsupported platform
                            intVal = -1;
                        }
                        break;

                    // Linux-only Elf_Cmd enums
                    case "READ_MMAP":
                        if (JNIELFTargetInfo.getOsName().equals("linux")) {
                            intVal = jdk.tools.jaotc.jnilibelf.linux.Elf_Cmd.ELF_C_READ_MMAP.ordinal();
                        } else {
                            // Unsupported platform
                            intVal = -1;
                        }
                        break;

                    case "RDWR_MMAP":
                        if (JNIELFTargetInfo.getOsName().equals("linux")) {
                            intVal = jdk.tools.jaotc.jnilibelf.linux.Elf_Cmd.ELF_C_RDWR_MMAP.ordinal();
                        } else {
                            // Unsupported platform
                            intVal = -1;
                        }
                        break;

                    case "WRITE_MMAP":
                        if (JNIELFTargetInfo.getOsName().equals("linux")) {
                            intVal = jdk.tools.jaotc.jnilibelf.linux.Elf_Cmd.ELF_C_WRITE_MMAP.ordinal();
                        } else {
                            // Unsupported platform
                            intVal = -1;
                        }
                        break;

                    case "READ_MMAP_PRIVATE":
                        if (JNIELFTargetInfo.getOsName().equals("linux")) {
                            intVal = jdk.tools.jaotc.jnilibelf.linux.Elf_Cmd.ELF_C_READ_MMAP_PRIVATE.ordinal();
                        } else {
                            // Unsupported platform
                            intVal = -1;
                        }
                        break;

                    case "EMPTY":
                        if (JNIELFTargetInfo.getOsName().equals("linux")) {
                            intVal = jdk.tools.jaotc.jnilibelf.linux.Elf_Cmd.ELF_C_EMPTY.ordinal();
                        } else {
                            // Unsupported platform
                            intVal = -1;
                        }
                        break;
                    // SunOS-only Elf_Cmd enums
                    case "WRIMAGE":
                        if (JNIELFTargetInfo.getOsName().equals("linux")) {
                            intVal = jdk.tools.jaotc.jnilibelf.sunos.Elf_Cmd.ELF_C_WRIMAGE.ordinal();
                        } else {
                            // Unsupported platform
                            intVal = -1;
                        }
                        break;
                    case "IMAGE":
                        if (JNIELFTargetInfo.getOsName().equals("linux")) {
                            intVal = jdk.tools.jaotc.jnilibelf.sunos.Elf_Cmd.ELF_C_IMAGE.ordinal();
                        } else {
                            // Unsupported platform
                            intVal = -1;
                        }
                        break;
                    default:
                        intVal = -1;
                }
            }

            public int intValue() {
                assert intVal != -1 : "enum " + name + "not supported on " + JNIELFTargetInfo.getOsName();
                return intVal;
            }

            public String getName() {
                return name;
            }
        }

        public static enum Elf_Type {
            ELF_T_BYTE(0), /* unsigned char */
            ELF_T_ADDR(1), /* Elf32_Addr, Elf64_Addr, ... */
            ELF_T_DYN(2), /* Dynamic section record. */
            ELF_T_EHDR(3), /* ELF header. */
            ELF_T_HALF(4), /* Elf32_Half, Elf64_Half, ... */
            ELF_T_OFF(5), /* Elf32_Off, Elf64_Off, ... */
            ELF_T_PHDR(6), /* Program header. */
            ELF_T_RELA(7), /* Relocation entry with addend. */
            ELF_T_REL(8), /* Relocation entry. */
            ELF_T_SHDR(9), /* Section header. */
            ELF_T_SWORD(10), /* Elf32_Sword, Elf64_Sword, ... */
            ELF_T_SYM(11), /* Symbol record. */
            ELF_T_WORD(12), /* Elf32_Word, Elf64_Word, ... */
            ELF_T_XWORD(13), /* Elf32_Xword, Elf64_Xword, ... */
            ELF_T_SXWORD(14), /* Elf32_Sxword, Elf64_Sxword, ... */
            ELF_T_VDEF(15), /* Elf32_Verdef, Elf64_Verdef, ... */
            ELF_T_VDAUX(16), /* Elf32_Verdaux, Elf64_Verdaux, ... */
            ELF_T_VNEED(17), /* Elf32_Verneed, Elf64_Verneed, ... */
            ELF_T_VNAUX(18), /* Elf32_Vernaux, Elf64_Vernaux, ... */
            ELF_T_NHDR(19), /* Elf32_Nhdr, Elf64_Nhdr, ... */
            ELF_T_SYMINFO(20), /* Elf32_Syminfo, Elf64_Syminfo, ... */
            ELF_T_MOVE(21), /* Elf32_Move, Elf64_Move, ... */
            ELF_T_LIB(22), /* Elf32_Lib, Elf64_Lib, ... */
            ELF_T_GNUHASH(23), /* GNU-style hash section. */
            ELF_T_AUXV(24), /* Elf32_auxv_t, Elf64_auxv_t, ... */
            /* Keep this the last entry. */
            ELF_T_NUM(25);

            private final int intVal;

            private Elf_Type(int v) {
                intVal = v;
            }

            public int intValue() {
                return intVal;
            }
        }

        /* Flags for the ELF structures. */
        int ELF_F_DIRTY = 0x1;
        int ELF_F_LAYOUT = 0x4;
        int ELF_F_PERMISSIVE = 0x8;

        public static enum Elf_Kind {
            ELF_K_NONE(0), /* Unknown. */
            ELF_K_AR(1), /* Archive. */
            ELF_K_COFF(2), /* Stupid old COFF. */
            ELF_K_ELF(3), /* ELF file. */
            /* Keep this the last entry. */
            ELF_K_NUM(4);
            private final int intVal;

            private Elf_Kind(int v) {
                intVal = v;
            }

            public int intValue() {
                return intVal;
            }
        }
    }

    /**
     * Invoke native libelf function unsigned int elf_version (unsigned int v).
     *
     * @param v version
     * @return return value of native call
     */
    // Checkstyle: stop method name check
    static native int elf_version(int v);

    /**
     * Return version recorded in libelfshim.
     *
     * @return return version string
     */
    // Checkstyle: stop method name check
    static native String elfshim_version();

    /**
     * Invoke native libelf function Elf *elf_begin (int fildes, Elf_Cmd cmd, Elf *elfPtr).
     *
     * @param fildes open file descriptor
     * @param elfCRead command
     * @param elfHdrPtr pointer to ELF header
     * @return return value of native call
     */
    static native Pointer elf_begin(int fildes, int elfCRead, Pointer elfHdrPtr);

    /**
     * Invoke native libelf function elf_end (Elf *elfPtr).
     *
     * @param elfPtr pointer to ELF header
     * @return return value of native call
     */
    static native int elf_end(Pointer elfPtr);

    /**
     * Invoke native libelf function elf_end (Elf *elfPtr).
     *
     * @param elfPtr pointer to ELF header
     * @return return value of native call
     */
    static native int elf_kind(Pointer elfPtr);

    /**
     * Invoke native libelf function unsigned int elf_flagphdr (Elf *elf, Elf_Cmd cmd, unsigned int
     * flags).
     *
     * @param elfPtr Pointer to ELF descriptor
     * @param cmd command
     * @param flags flags
     * @return return value of native call
     */
    static native int elf_flagphdr(Pointer elfPtr, int cmd, int flags);

    /**
     * Invoke native libelf function Elf_Scn *elf_newscn (Elf *elfPtr).
     *
     * @param elfPtr Elf header pointer
     * @return return value of native call
     */
    static native Pointer elf_newscn(Pointer elfPtr);

    /**
     * Invoke native libelf function Elf_Data *elf_newdata (Elf_Scn *scn).
     *
     * @param scnPtr pointer to section for which the new data descriptor is to be created
     * @return return value of native call
     */
    static native Pointer elf_newdata(Pointer scnPtr);

    /**
     * Invoke native libelf function Elf64_Shdr *elf64_getshdr (Elf_Scn *scnPtr).
     *
     * @param scnPtr pointer to section whose header information is to be retrieved
     * @return return value of native call
     */
    static native Pointer elf64_getshdr(Pointer scnPtr);

    /**
     * Invoke native libelf function loff_t elf_update (Elf *elfPtr, Elf_Cmd cmd).
     *
     * @param elfPtr Pointer to ELF descriptor
     * @param cmd command
     * @return return value of native call
     */
    static native long elf_update(Pointer elfPtr, int cmd);

    /**
     * Invoke native libelf function char *elf_errmsg (int error).
     *
     * @param error error
     * @return return value of native call
     */
    static native String elf_errmsg(int error);

    /**
     * Invoke native libelf function size_t elf_ndxscn (Elf_Scn *scn).
     *
     * @param scn section pointer
     * @return return value of native call
     */
    static native int elf_ndxscn(Pointer scn);

    /**
     * GELF interfaces
     */
    /**
     * Invoke native libelf function unsigned long int gelf_newehdr (Elf *elf, int elfClass).
     *
     * @param elf ELF Header pointer
     * @param elfclass ELF class
     * @return return value of native call boxed as a pointer
     */
    static native Pointer gelf_newehdr(Pointer elf, int elfclass);

    /**
     * Invoke native libelf function unsigned long int gelf_newphdr (Elf *elf, size_t phnum).
     *
     * @param elf ELF header pointer
     * @param phnum number of program headers
     * @return return value of native call boxed as a pointer
     */
    static native Pointer gelf_newphdr(Pointer elf, int phnum);

    /**
     * Miscellaneous convenience native methods that help peek and poke ELF data structures.
     */
    static native int size_of_Sym(int elfClass);

    static native int size_of_Rela(int elfClass);

    static native int size_of_Rel(int elfClass);

    static native void ehdr_set_data_encoding(Pointer ehdr, int val);

    static native void set_Ehdr_e_machine(int elfclass, Pointer structPtr, int val);

    static native void set_Ehdr_e_type(int elfclass, Pointer structPtr, int val);

    static native void set_Ehdr_e_version(int elfclass, Pointer structPtr, int val);

    static native void set_Ehdr_e_shstrndx(int elfclass, Pointer structPtr, int val);

    static native void phdr_set_type_self(int elfclass, Pointer ehdr, Pointer phdr);

    static native void set_Shdr_sh_name(int elfclass, Pointer structPtr, int val);

    static native void set_Shdr_sh_type(int elfclass, Pointer structPtr, int val);

    static native void set_Shdr_sh_flags(int elfclass, Pointer structPtr, int val);

    static native void set_Shdr_sh_entsize(int elfclass, Pointer structPtr, int val);

    static native void set_Shdr_sh_link(int elfclass, Pointer structPtr, int val);

    static native void set_Shdr_sh_info(int elfclass, Pointer structPtr, int val);

    static native void set_Data_d_align(Pointer structPtr, int val);

    static native void set_Data_d_off(Pointer structPtr, int val);

    static native void set_Data_d_buf(Pointer structPtr, Pointer val);

    static native void set_Data_d_type(Pointer structPtr, int val);

    static native void set_Data_d_size(Pointer structPtr, int val);

    static native void set_Data_d_version(Pointer structPtr, int val);

    static native long create_sym_entry(int elfclass, int index, int type, int bind, int shndx, int size, int value);

    static native long create_reloc_entry(int elfclass, int roffset, int symtabIdx, int relocType, int raddend, int reloca);

    /**
     * File Operations.
     */
    static native int open_rw(String fileName);

    static native int open(String fileName, int flags);

    static native int open(String fileName, int flags, int mode);

    static native int close(int fd);
    // Checkstyle: resume method name check
}
