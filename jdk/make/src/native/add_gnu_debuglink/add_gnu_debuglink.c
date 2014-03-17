/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

/*
 * Name:        add_gnu_debuglink.c
 *
 * Description: Add a ".gnu_debuglink" section that refers to the specified
 *     debug_info_path to the specified ELF object.
 *
 *     This program is adapted from the example program shown on the
 *     elf(3elf) man page and from code from the Solaris compiler
 *     driver.
 */

/*
 * needed to define SHF_EXCLUDE
 */
#define ELF_TARGET_ALL

#include <fcntl.h>
#include <stdio.h>
#include <libelf.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

static void failure(void);
static unsigned int gnu_debuglink_crc32(unsigned int crc, unsigned char *buf,
                                        size_t len);

void
main(int argc, char ** argv) {
                                 /* new ELF section name */
    static char SEC_NAME[] = ".gnu_debuglink";

    unsigned char buffer[8 * 1024];  /* I/O buffer */
    int           buffer_len;        /* buffer length */
    char *        debug_info_path;   /* debug info path */
    void *        ehdr;              /* ELF header */
    Elf *         elf;               /* ELF descriptor */
    char *        elf_ident;         /* ELF identity string */
    char *        elf_obj;           /* elf_obj file */
    int           fd;                /* descriptor for files */
    unsigned int  file_crc = 0;      /* CRC for debug info file */
    int           is_elfclass64;     /* is an ELFCLASS64 file? */
    Elf_Data *    link_dat;          /* ELF data for new debug info link */
    Elf_Data *    name_dat;          /* ELF data for new section name */
    Elf_Scn *     new_scn;           /* new ELF section descriptor */
    void *        new_shdr;          /* new ELF section header */
    Elf_Scn *     scn;               /* ELF section descriptor */
    void *        shdr;              /* ELF section header */

    if (argc != 3) {
        (void) fprintf(stderr, "Usage: %s debug_info_path elf_obj\n", argv[0]);
        exit(2);
    }

    debug_info_path = argv[1];  /* save for later */
    if ((fd = open(debug_info_path, O_RDONLY)) == -1) {
        (void) fprintf(stderr, "%s: cannot open file.\n", debug_info_path);
        exit(3);
    }

    (void) printf("Computing CRC for '%s'\n", debug_info_path);
    (void) fflush(stdout);
    /* compute CRC for the debug info file */
    for (;;) {
        int len = read(fd, buffer, sizeof buffer);
        if (len <= 0) {
            break;
        }
        file_crc = gnu_debuglink_crc32(file_crc, buffer, len);
    }
    (void) close(fd);

    /* open the elf_obj */
    elf_obj = argv[2];
    if ((fd = open(elf_obj, O_RDWR)) == -1) {
        (void) fprintf(stderr, "%s: cannot open file.\n", elf_obj);
        exit(4);
    }

    (void) printf("Opening '%s' for update\n", elf_obj);
    (void) fflush(stdout);
    (void) elf_version(EV_CURRENT);  /* coordinate ELF versions */

    /* obtain the ELF descriptors from the input file */
    if ((elf = elf_begin(fd, ELF_C_RDWR, NULL)) == NULL) {
        failure();
    }

    /* determine if ELFCLASS64 or not? */
    elf_ident = elf_getident(elf, NULL);
    is_elfclass64 = (elf_ident[EI_CLASS] == ELFCLASS64);

    /* get the ELF header */
    if (is_elfclass64) {
        ehdr = elf64_getehdr(elf);
    } else {
        ehdr = elf32_getehdr(elf);
    }
    if (ehdr == NULL) {
        failure();
    }

    /* get the ELF section descriptor */
    if (is_elfclass64) {
        scn = elf_getscn(elf, ((Elf64_Ehdr *) ehdr)->e_shstrndx);
    } else {
        scn = elf_getscn(elf, ((Elf32_Ehdr *) ehdr)->e_shstrndx);
    }
    if (scn == NULL) {
        failure();
    }

    /* get the section header */
    if (is_elfclass64) {
        shdr = elf64_getshdr(scn);
    } else {
        shdr = elf32_getshdr(scn);
    }
    if (shdr == NULL) {
        failure();
    }

    (void) printf("Adding ELF data for new section name\n");
    (void) fflush(stdout);
    name_dat = elf_newdata(scn);
    name_dat->d_buf = (void *) SEC_NAME;
    if (is_elfclass64) {
        name_dat->d_off = ((Elf64_Shdr *) shdr)->sh_size + 1;
    } else {
        name_dat->d_off = ((Elf32_Shdr *) shdr)->sh_size + 1;
    }
    name_dat->d_align = 1;
    name_dat->d_size = strlen(SEC_NAME) + 1;

    new_scn = elf_newscn(elf);

    if (is_elfclass64) {
        new_shdr = elf64_getshdr(new_scn);
        ((Elf64_Shdr *) new_shdr)->sh_flags = SHF_EXCLUDE;
        ((Elf64_Shdr *) new_shdr)->sh_type = SHT_PROGBITS;
        ((Elf64_Shdr *) new_shdr)->sh_name = ((Elf64_Shdr *) shdr)->sh_size;
        ((Elf64_Shdr *) new_shdr)->sh_addralign = 1;
        ((Elf64_Shdr *) shdr)->sh_size += (strlen(SEC_NAME) + 1);
    } else {
        new_shdr = elf32_getshdr(new_scn);
        ((Elf32_Shdr *) new_shdr)->sh_flags = SHF_EXCLUDE;
        ((Elf32_Shdr *) new_shdr)->sh_type = SHT_PROGBITS;
        ((Elf32_Shdr *) new_shdr)->sh_name = ((Elf32_Shdr *) shdr)->sh_size;
        ((Elf32_Shdr *) new_shdr)->sh_addralign = 1;
        ((Elf32_Shdr *) shdr)->sh_size += (strlen(SEC_NAME) + 1);
    }

    (void) printf("Adding ELF data for debug_info_path value\n");
    (void) fflush(stdout);
    (void) memset(buffer, 0, sizeof buffer);
    buffer_len = strlen(debug_info_path) + 1;  /* +1 for NUL */
    (void) strncpy((char *) buffer, debug_info_path, buffer_len);
    if (buffer_len % 4 != 0) {
        /* not on a 4 byte boundary so pad to the next one */
        buffer_len += (4 - buffer_len % 4);
    }
    /* save the CRC */
    (void) memcpy(&buffer[buffer_len], &file_crc, sizeof file_crc);
    buffer_len += sizeof file_crc;

    link_dat = elf_newdata(new_scn);
    link_dat->d_type = ELF_T_BYTE;
    link_dat->d_size = buffer_len;
    link_dat->d_buf = buffer;
    link_dat->d_align = 1;

    (void) printf("Saving updates to '%s'\n", elf_obj);
    (void) fflush(stdout);
    (void) elf_update(elf, ELF_C_NULL);   /* recalc ELF memory structures */
    (void) elf_update(elf, ELF_C_WRITE);  /* write out changes to ELF obj */
    (void) elf_end(elf);                  /* done with ELF obj */
    (void) close(fd);

    (void) printf("Done updating '%s'\n", elf_obj);
    (void) fflush(stdout);
    exit(0);
}  /* end main */


static void
failure() {
    (void) fprintf(stderr, "%s\n", elf_errmsg(elf_errno()));
    exit(5);
}


/*
 * The CRC used in gnu_debuglink, retrieved from
 * http://sourceware.org/gdb/current/onlinedocs/gdb/Separate-Debug-Files.html#Separate-Debug-Files.
 */

static unsigned int
gnu_debuglink_crc32(unsigned int crc, unsigned char *buf, size_t len) {
    static const unsigned int crc32_table[256] = {
        0x00000000, 0x77073096, 0xee0e612c, 0x990951ba, 0x076dc419,
        0x706af48f, 0xe963a535, 0x9e6495a3, 0x0edb8832, 0x79dcb8a4,
        0xe0d5e91e, 0x97d2d988, 0x09b64c2b, 0x7eb17cbd, 0xe7b82d07,
        0x90bf1d91, 0x1db71064, 0x6ab020f2, 0xf3b97148, 0x84be41de,
        0x1adad47d, 0x6ddde4eb, 0xf4d4b551, 0x83d385c7, 0x136c9856,
        0x646ba8c0, 0xfd62f97a, 0x8a65c9ec, 0x14015c4f, 0x63066cd9,
        0xfa0f3d63, 0x8d080df5, 0x3b6e20c8, 0x4c69105e, 0xd56041e4,
        0xa2677172, 0x3c03e4d1, 0x4b04d447, 0xd20d85fd, 0xa50ab56b,
        0x35b5a8fa, 0x42b2986c, 0xdbbbc9d6, 0xacbcf940, 0x32d86ce3,
        0x45df5c75, 0xdcd60dcf, 0xabd13d59, 0x26d930ac, 0x51de003a,
        0xc8d75180, 0xbfd06116, 0x21b4f4b5, 0x56b3c423, 0xcfba9599,
        0xb8bda50f, 0x2802b89e, 0x5f058808, 0xc60cd9b2, 0xb10be924,
        0x2f6f7c87, 0x58684c11, 0xc1611dab, 0xb6662d3d, 0x76dc4190,
        0x01db7106, 0x98d220bc, 0xefd5102a, 0x71b18589, 0x06b6b51f,
        0x9fbfe4a5, 0xe8b8d433, 0x7807c9a2, 0x0f00f934, 0x9609a88e,
        0xe10e9818, 0x7f6a0dbb, 0x086d3d2d, 0x91646c97, 0xe6635c01,
        0x6b6b51f4, 0x1c6c6162, 0x856530d8, 0xf262004e, 0x6c0695ed,
        0x1b01a57b, 0x8208f4c1, 0xf50fc457, 0x65b0d9c6, 0x12b7e950,
        0x8bbeb8ea, 0xfcb9887c, 0x62dd1ddf, 0x15da2d49, 0x8cd37cf3,
        0xfbd44c65, 0x4db26158, 0x3ab551ce, 0xa3bc0074, 0xd4bb30e2,
        0x4adfa541, 0x3dd895d7, 0xa4d1c46d, 0xd3d6f4fb, 0x4369e96a,
        0x346ed9fc, 0xad678846, 0xda60b8d0, 0x44042d73, 0x33031de5,
        0xaa0a4c5f, 0xdd0d7cc9, 0x5005713c, 0x270241aa, 0xbe0b1010,
        0xc90c2086, 0x5768b525, 0x206f85b3, 0xb966d409, 0xce61e49f,
        0x5edef90e, 0x29d9c998, 0xb0d09822, 0xc7d7a8b4, 0x59b33d17,
        0x2eb40d81, 0xb7bd5c3b, 0xc0ba6cad, 0xedb88320, 0x9abfb3b6,
        0x03b6e20c, 0x74b1d29a, 0xead54739, 0x9dd277af, 0x04db2615,
        0x73dc1683, 0xe3630b12, 0x94643b84, 0x0d6d6a3e, 0x7a6a5aa8,
        0xe40ecf0b, 0x9309ff9d, 0x0a00ae27, 0x7d079eb1, 0xf00f9344,
        0x8708a3d2, 0x1e01f268, 0x6906c2fe, 0xf762575d, 0x806567cb,
        0x196c3671, 0x6e6b06e7, 0xfed41b76, 0x89d32be0, 0x10da7a5a,
        0x67dd4acc, 0xf9b9df6f, 0x8ebeeff9, 0x17b7be43, 0x60b08ed5,
        0xd6d6a3e8, 0xa1d1937e, 0x38d8c2c4, 0x4fdff252, 0xd1bb67f1,
        0xa6bc5767, 0x3fb506dd, 0x48b2364b, 0xd80d2bda, 0xaf0a1b4c,
        0x36034af6, 0x41047a60, 0xdf60efc3, 0xa867df55, 0x316e8eef,
        0x4669be79, 0xcb61b38c, 0xbc66831a, 0x256fd2a0, 0x5268e236,
        0xcc0c7795, 0xbb0b4703, 0x220216b9, 0x5505262f, 0xc5ba3bbe,
        0xb2bd0b28, 0x2bb45a92, 0x5cb36a04, 0xc2d7ffa7, 0xb5d0cf31,
        0x2cd99e8b, 0x5bdeae1d, 0x9b64c2b0, 0xec63f226, 0x756aa39c,
        0x026d930a, 0x9c0906a9, 0xeb0e363f, 0x72076785, 0x05005713,
        0x95bf4a82, 0xe2b87a14, 0x7bb12bae, 0x0cb61b38, 0x92d28e9b,
        0xe5d5be0d, 0x7cdcefb7, 0x0bdbdf21, 0x86d3d2d4, 0xf1d4e242,
        0x68ddb3f8, 0x1fda836e, 0x81be16cd, 0xf6b9265b, 0x6fb077e1,
        0x18b74777, 0x88085ae6, 0xff0f6a70, 0x66063bca, 0x11010b5c,
        0x8f659eff, 0xf862ae69, 0x616bffd3, 0x166ccf45, 0xa00ae278,
        0xd70dd2ee, 0x4e048354, 0x3903b3c2, 0xa7672661, 0xd06016f7,
        0x4969474d, 0x3e6e77db, 0xaed16a4a, 0xd9d65adc, 0x40df0b66,
        0x37d83bf0, 0xa9bcae53, 0xdebb9ec5, 0x47b2cf7f, 0x30b5ffe9,
        0xbdbdf21c, 0xcabac28a, 0x53b39330, 0x24b4a3a6, 0xbad03605,
        0xcdd70693, 0x54de5729, 0x23d967bf, 0xb3667a2e, 0xc4614ab8,
        0x5d681b02, 0x2a6f2b94, 0xb40bbe37, 0xc30c8ea1, 0x5a05df1b,
        0x2d02ef8d
    };

    unsigned char *end;

    crc = ~crc & 0xffffffff;
    for (end = buf + len; buf < end; ++buf) {
        crc = crc32_table[(crc ^ *buf) & 0xff] ^ (crc >> 8);
    }
    return ~crc & 0xffffffff;
}
