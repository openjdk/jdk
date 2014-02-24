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
 * Name:        fix_empty_sec_hdr_flags.c
 *
 * Description: Remove the SHF_ALLOC flag from "empty" section headers.
 *     An "empty" section header has sh_addr == 0 and sh_size == 0.
 *
 *     This program is adapted from the example program shown on the
 *     elf(3elf) man page and from code from the Solaris compiler
 *     driver.
 */

#include <fcntl.h>
#include <stdio.h>
#include <libelf.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

static void failure(void);

void
main(int argc, char ** argv) {
    void *        ehdr;           /* ELF header */
    unsigned int  i;              /* section counter */
    int           fd;             /* descriptor for file */
    Elf *         elf;            /* ELF descriptor */
    char *        elf_ident;      /* ELF identity string */
    char *        elf_obj;        /* elf_obj file */
    int           fix_count;      /* number of flags fixed */
    int           is_elfclass64;  /* is an ELFCLASS64 file? */
    Elf_Scn *     scn;            /* ELF section descriptor */
    void *        shdr;           /* ELF section header */
    Elf_Data *    shstrtab;       /* ELF section header string table */

    if (argc != 2) {
        (void) fprintf(stderr, "Usage: %s elf_obj\n", argv[0]);
        exit(2);
    }

    /* open the elf_obj */
    elf_obj = argv[1];
    if ((fd = open(elf_obj, O_RDWR)) == -1) {
        (void) fprintf(stderr, "%s: cannot open file.\n", elf_obj);
        exit(3);
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

    /* get the section header string table */
    shstrtab = elf_getdata(scn, NULL);
    if (shstrtab == NULL) {
        failure();
    }

    fix_count = 0;

    /* traverse the sections of the input file */
    for (i = 1, scn = NULL; scn = elf_nextscn(elf, scn); i++) {
        int    has_flag_set;  /* is SHF_ALLOC flag set? */
        int    is_empty;      /* is section empty? */
        char * name;          /* short hand pointer */

        /* get the section header */
        if (is_elfclass64) {
            shdr = elf64_getshdr(scn);
        } else {
            shdr = elf32_getshdr(scn);
        }
        if (shdr == NULL) {
            failure();
        }

        if (is_elfclass64) {
            name = (char *)shstrtab->d_buf + ((Elf64_Shdr *) shdr)->sh_name;
        } else {
            name = (char *)shstrtab->d_buf + ((Elf32_Shdr *) shdr)->sh_name;
        }

        if (is_elfclass64) {
            has_flag_set = ((Elf64_Shdr *) shdr)->sh_flags & SHF_ALLOC;
            is_empty = ((Elf64_Shdr *) shdr)->sh_addr == 0 &&
                ((Elf64_Shdr *) shdr)->sh_size == 0;
        } else {
            has_flag_set = ((Elf32_Shdr *) shdr)->sh_flags & SHF_ALLOC;
            is_empty = ((Elf32_Shdr *) shdr)->sh_addr == 0 &&
                ((Elf32_Shdr *) shdr)->sh_size == 0;
        }

        if (is_empty && has_flag_set) {
            (void) printf("section[%u] '%s' is empty, "
                "but SHF_ALLOC flag is set.\n", i, name);
            (void) printf("Clearing the SHF_ALLOC flag.\n");

            if (is_elfclass64) {
                ((Elf64_Shdr *) shdr)->sh_flags &= ~SHF_ALLOC;
            } else {
                ((Elf32_Shdr *) shdr)->sh_flags &= ~SHF_ALLOC;
            }
            fix_count++;
        }
    }  /* end for each ELF section */

    if (fix_count > 0) {
        (void) printf("Saving %d updates to '%s'\n", fix_count, elf_obj);
        (void) fflush(stdout);
        (void) elf_update(elf, ELF_C_NULL);   /* recalc ELF memory structures */
        (void) elf_update(elf, ELF_C_WRITE);  /* write out changes to ELF obj */
    } else {
        (void) printf("No SHF_ALLOC flags needed to be cleared.\n");
    }

    (void) elf_end(elf);                  /* done with ELF obj */
    (void) close(fd);

    (void) printf("Done %s '%s'\n",
               (fix_count > 0) ? "updating" : "with", elf_obj);
    (void) fflush(stdout);
    exit(0);
}  /* end main */


static void
failure() {
    (void) fprintf(stderr, "%s\n", elf_errmsg(elf_errno()));
    exit(6);
}
