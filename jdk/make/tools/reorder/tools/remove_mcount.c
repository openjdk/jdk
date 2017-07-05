/*
 * Copyright (c) 2000, 2001, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

#include <stdio.h>
#include <sys/mman.h>
#include <dlfcn.h>
#include <libelf.h>
#include <strings.h>
#include <fcntl.h>
#include <sys/param.h>
#include <stdlib.h>
#include <thread.h>
#include <synch.h>
#include <stdarg.h>

#define TRUE    1
#define FALSE   0


static void fail(const char *err, ...)
{
    va_list ap;
    va_start(ap, err);
    vfprintf(stderr, err, ap);
    fflush(stderr);
    va_end(ap);
    exit(2);
}


static Elf_Scn *find_section(Elf *elf, Elf_Data *sectionStringData,
                             const char *name)
{
    Elf_Scn *result = NULL;
    Elf32_Shdr *symHeader;
    const char *p;

    while ((result = elf_nextscn(elf, result)) != NULL) {
        symHeader = elf32_getshdr(result);
        p = (const char *)(sectionStringData->d_buf) + symHeader->sh_name;
        if (strcmp(p, name) == 0)
            break;
    }
    return result;
}


static void trash_mcount(int count, Elf_Data *data, Elf_Data *stringData)
{
    int i;
    for (i = 0; i < count; ++i) {
        Elf32_Sym *sym = ((Elf32_Sym *)data->d_buf) + i;
        char *name = (char *)stringData->d_buf + sym->st_name;

        if (strcmp(name, "_mcount") == 0) {
            name[6] = 'T';
            break;
        }
    }
    if (i < count)
        printf("Symbol _mcount found and changed.\n");
    else
        printf("Symbol _mcount not found.\n");
}


/*
 * In the executable program named as the sole command line argument, find
 * the symbol _mcount, if present, and change its name to something
 * different.  The symbol _mcount is included in Solaris/x86 programs by
 * the compilers, and its presence prevents preloaded modules from
 * supplying a custom implementation of that method.
 */

int main(int argc, char **argv)
{
    Elf32_Ehdr *ehdr;
    Elf_Scn    *sectionStringSection;
    Elf_Scn    *stringSection;
    Elf_Scn    *dynStringSection;
    Elf_Scn    *symSection;
    Elf_Scn    *dynSymSection;
    Elf32_Shdr *symHeader;
    Elf32_Shdr *dynSymHeader;
    Elf32_Shdr *dynStringHeader;
    Elf32_Shdr *stringHeader;
    Elf        *elf;
    const char *p;
    int        i;
    const char *fullName;
    int        fd;
    Elf_Data   *sectionStringData;
    Elf_Data   *symData;
    Elf_Data   *dynSymData;
    Elf_Data   *symStringData;
    Elf_Data   *dynSymStringData;
    int        symCount;
    int        dynSymCount;


    if (argc != 2) {
        fprintf(stderr, "Usage:\n"
                "\t%s  <file>\n", argv[0]);
        exit(1);
    }

    fullName = argv[1];

    /* Open the ELF file. Get section headers. */

    elf_version(EV_CURRENT);
    fd = open(fullName, O_RDWR);
    if (fd < 0)
        fail("Unable to open ELF file %s.\n", fullName);
    elf = elf_begin(fd, ELF_C_RDWR, (Elf *)0);
    if (elf == NULL)
        fail("elf_begin failed.\n");
    ehdr = elf32_getehdr(elf);
    sectionStringSection = elf_getscn(elf, ehdr->e_shstrndx);
    sectionStringData = elf_getdata(sectionStringSection, NULL);

    /* Find the symbol table section. */

    symSection = find_section(elf, sectionStringData, ".symtab");
    if (symSection != NULL) {
        symData = elf_getdata(symSection, NULL);
        symCount = symData->d_size / sizeof (Elf32_Sym);

        /* Find the string section, trash the _mcount symbol. */

        stringSection = find_section(elf, sectionStringData, ".strtab");
        if (stringSection == NULL)
            fail("Unable to find string table.\n");
        symStringData = elf_getdata(stringSection, NULL);
        trash_mcount(symCount, symData, symStringData);
    } else {
        fprintf(stderr, "Unable to find symbol table.\n");
    }

    /* Find the dynamic symbol table section. */

    dynSymSection = find_section(elf, sectionStringData, ".dynsym");
    if (dynSymSection != NULL) {
        dynSymData = elf_getdata(dynSymSection, NULL);
        dynSymCount = dynSymData->d_size / sizeof (Elf32_Sym);

        /* Find the dynamic string section, trash the _mcount symbol. */

        dynStringSection = find_section(elf, sectionStringData, ".dynstr");
        if (dynStringSection == NULL)
            fail("Unable to find dynamic string table.\n");
        dynSymStringData = elf_getdata(dynStringSection, NULL);
        trash_mcount(dynSymCount, dynSymData, dynSymStringData);
    } else {
        fail("Unable to find dynamic symbol table.\n");
    }

    elf_update(elf, ELF_C_WRITE);
    elf_end(elf);

    exit(0);
}
