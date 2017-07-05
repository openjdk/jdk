/*
 * Copyright (c) 2000, 2004, Oracle and/or its affiliates. All rights reserved.
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
#include <unistd.h>

#define TRUE    1
#define FALSE   0

/* 32/64 bit build issues. */

#ifdef _LP64
 #define ElfXX_Sym      Elf64_Sym
 #define ElfXX_Ehdr     Elf64_Ehdr
 #define ElfXX_Shdr     Elf64_Shdr
 #define elfXX_getehdr  elf64_getehdr
 #define ElfXX_Addr     Elf64_Addr
 #define ELFXX_ST_TYPE  ELF64_ST_TYPE
 #define ELFXX_ST_BIND  ELF64_ST_BIND
 #define elfXX_getshdr  elf64_getshdr
#else
 #define ElfXX_Sym      Elf32_Sym
 #define ElfXX_Ehdr     Elf32_Ehdr
 #define ElfXX_Shdr     Elf32_Shdr
 #define elfXX_getehdr  elf32_getehdr
 #define ElfXX_Addr     Elf32_Addr
 #define ELFXX_ST_TYPE  ELF32_ST_TYPE
 #define ELFXX_ST_BIND  ELF32_ST_BIND
 #define elfXX_getshdr  elf32_getshdr
#endif

extern void *_getReturnAddr(void);



typedef struct StabEntry {
    unsigned      n_strx;
    unsigned char n_type;
    char          n_other;
    short         n_desc;
    unsigned      n_value;
} StabEntry;


typedef struct SymChain {
    struct SymChain *next;
    ElfXX_Sym *sym;
} SymChain;


typedef struct ObjFileList {
    struct ObjFileList *next;
    const char *objFileName;
    int    nameLen;
} ObjFileList;


typedef struct ElfInfo {
    const char *fullName;
    const char *baseName;
    FILE       *outFile;
    int        fd;
    Elf        *elf;
    Elf_Data   *sectionStringData;
    Elf_Data   *symData;
    Elf_Data   *symStringData;
    int        symCount;
    SymChain   *symChainHead;
    Elf_Data   *stabData;
    Elf_Data   *stabStringData;
    int        stabCount;
    ObjFileList *objFileList;
} ElfInfo;



#define COUNT_BUF_SIZE (16*1024*1024)

#define ENTRY_CHAIN_BUCKETS  4999

static int *countBuf;
static void *textOffset;
static const char *libFileName;



static void fail(const char *err, ...)
{
    va_list ap;
    va_start(ap, err);
    vfprintf(stderr, err, ap);
    fflush(stderr);
    va_end(ap);
}



static const char *getSymString(ElfInfo *elfInfo, int index)
{
    return (const char *)elfInfo->symStringData->d_buf + index;
}


static const char *getStabString(ElfInfo *elfInfo, int index)
{
    return (const char *)elfInfo->stabStringData->d_buf + index;
}


static const char *getSectionString(ElfInfo *elfInfo, int index)
{
    return (const char *)elfInfo->sectionStringData->d_buf + index;
}


static const char *makeObjFileList(ElfInfo *elfInfo)
{
    int i;
    const char *file;
    unsigned offset, lastOffset;
    ObjFileList *objFileList;

    file = NULL;
    offset = lastOffset = 0;
    for (i = 0; i < elfInfo->stabCount; ++i) {
        StabEntry *stab = ((StabEntry *)elfInfo->stabData->d_buf) + i;

        if (stab->n_type == 0 /* N_UNDEF */) {
            offset = lastOffset;
            lastOffset += stab-> n_value;
        }
        else if (stab->n_type == 0x38 /* N_OBJ */) {
            file = getStabString(elfInfo, stab->n_strx + offset);
            objFileList = (ObjFileList *)malloc(sizeof (ObjFileList));
            objFileList->objFileName = file;
            /*fprintf(stderr,"new obj file %s.\n", file);*/
            objFileList->nameLen = strlen(file);
            objFileList->next = elfInfo->objFileList;
            elfInfo->objFileList = objFileList;
        }
    }
    return NULL;
}


static ElfInfo *createElfInfo(const char *fullName)
{
    ElfInfo    *elfInfo;
    ElfXX_Ehdr *ehdr;
    Elf_Scn    *sectionStringSection;
    Elf_Scn    *stringSection;
    Elf_Scn    *symSection;
    ElfXX_Shdr *symHeader;
    Elf_Scn    *stabSection;
    ElfXX_Shdr *stabHeader;
    ElfXX_Shdr *stringHeader;
    Elf        *elf;
    const char *p;

    /*fprintf(stderr, "# mapfile info for %s.\n", fullName);*/
    elfInfo = (ElfInfo *)malloc(sizeof (ElfInfo));
    memset(elfInfo, 0, sizeof (ElfInfo));
    elfInfo->fullName = strdup(fullName);
    p = rindex(elfInfo->fullName, '/');
    elfInfo->baseName = (p == NULL) ? elfInfo->fullName : p + 1;

    /* Open the ELF file. Get section headers. */

    elf_version(EV_CURRENT);
    elfInfo->fd = open(fullName, O_RDONLY);
    if (elfInfo->fd < 0)
        fail("Unable to open ELF file %s.\n", fullName);
    elf = elf_begin(elfInfo->fd, ELF_C_READ, (Elf *)0);
    if (elf == NULL)
        fail("elf_begin failed.\n");
    ehdr = elfXX_getehdr(elf);
    sectionStringSection = elf_getscn(elf, ehdr->e_shstrndx);
    elfInfo->sectionStringData = elf_getdata(sectionStringSection, NULL);

    /* Find the symbol table section. */

    symSection = NULL;
    while ((symSection = elf_nextscn(elf, symSection)) != NULL) {
        symHeader = elfXX_getshdr(symSection);
        p = getSectionString(elfInfo, symHeader->sh_name);
        if (strcmp(p, ".symtab") == 0)
            break;
    }
    if (symSection == NULL)
        fail("Unable to find symbol table.\n");

    elfInfo->symData = elf_getdata(symSection, NULL);
    elfInfo->symCount = elfInfo->symData->d_size / sizeof (ElfXX_Sym);

    /* Find the string section. */

    stringSection = NULL;
    while ((stringSection = elf_nextscn(elf, stringSection)) != NULL) {
        stringHeader = elfXX_getshdr(stringSection);
        p = getSectionString(elfInfo, stringHeader->sh_name);
        if (strcmp(p, ".strtab") == 0)
            break;
    }
    if (stringSection == NULL)
        fail("Unable to find string table.\n");

    elfInfo->symStringData = elf_getdata(stringSection, NULL);
    elfInfo->symChainHead = NULL;

    /* Find the stab section. */

    stabSection = NULL;
    while ((stabSection = elf_nextscn(elf, stabSection)) != NULL) {
        stabHeader = elfXX_getshdr(stabSection);
        p = getSectionString(elfInfo, stabHeader->sh_name);
        if (strcmp(p, ".stab.index") == 0)
            break;
    }
    if (stabSection == NULL)
        fail("Unable to find .stab.index.\n");

    elfInfo->stabData = elf_getdata(stabSection, NULL);
    elfInfo->stabCount = elfInfo->stabData->d_size / sizeof (StabEntry);

    /* Find the string section. */

    stringSection = NULL;
    while ((stringSection = elf_nextscn(elf, stringSection)) != NULL) {
        stringHeader = elfXX_getshdr(stringSection);
        p = getSectionString(elfInfo, stringHeader->sh_name);
        if (strcmp(p, ".stab.indexstr") == 0)
            break;
    }
    if (stringSection == NULL)
        fail("Unable to find .stab.indexstr table.\n");

    elfInfo->stabStringData = elf_getdata(stringSection, NULL);
    makeObjFileList(elfInfo);

    return elfInfo;
}


static const char *identifyFile(ElfInfo *elfInfo, const char *name)
{
    int i;
    const char *file;
    const char *sourceFile;
    unsigned offset, lastOffset;
    const char *lastOptions;
    char *buf;

    file = NULL;
    lastOptions = NULL;
    offset = lastOffset = 0;
    for (i = 0; i < elfInfo->stabCount; ++i) {
        StabEntry *stab = ((StabEntry *)elfInfo->stabData->d_buf) + i;

        if (stab->n_type == 0 /* N_UNDEF */) {
            offset = lastOffset;
            lastOffset += stab-> n_value;
            file = NULL;   /* C++ output files seem not to have N_OBJ fields.*/
            lastOptions = NULL;
            sourceFile = getStabString(elfInfo, stab->n_strx + offset);
        }
        else if (stab->n_type == 0x24 /* N_FUN */) {
            const char *stabName;
            char *p1, *p2;

            stabName = getStabString(elfInfo, stab->n_strx + offset);
            if (strcmp (stabName, name) == 0) {

                if (file != NULL)
                    return file;

                if (lastOptions == NULL)
                    return NULL;

                p1 = strstr(lastOptions, ";ptr");
                if (p1 == NULL)
                    return NULL;
                p1 += 4;
                p2 = index(p1, ';');
                if (p2 == NULL)
                    return NULL;

                buf = (char *)malloc(p2 - p1 + strlen(sourceFile) + 10);
                strncpy(buf, p1, p2 - p1);
                buf[p2-p1] = '/';
                strcpy(buf + (p2 - p1) + 1, sourceFile);
                p1 = rindex(buf, '.');
                if (p1 == NULL)
                    return NULL;
                p1[1] = 'o';
                p1[2] = '\0';
                return buf;
            }
        }
        else if (stab->n_type == 0x3c /* N_OPT */) {
            lastOptions =  getStabString(elfInfo, stab->n_strx + offset);
        }
        else if (stab->n_type == 0x38 /* N_OBJ */) {
            file = getStabString(elfInfo, stab->n_strx + offset);
        }
    }
    return NULL;
}


static const char *checkObjFileList(ElfInfo *elfInfo, const char *file) {
    ObjFileList *objFileList;
    int len = strlen(file);
    int nameLen;
    const char *objFileName;

    /*fprintf(stderr, "checkObjFileList(%s).\n", file);*/
    for (objFileList = elfInfo->objFileList; objFileList != NULL;
         objFileList = objFileList->next) {

        objFileName = objFileList->objFileName;
        nameLen = objFileList->nameLen;
        if (strcmp(objFileName +nameLen - len, file) != 0)
            continue;

        if (len == nameLen)
            return file;

        if (len > nameLen)
            continue;

        if (*(objFileName + nameLen - len - 1) == '/')
            return objFileName;
    }
    return file;
}


static void identifySymbol(ElfInfo *elfInfo, ElfXX_Addr value, int count)
{
    int i;
    ElfXX_Sym *bestFunc = NULL;
    ElfXX_Sym *bestFile = NULL;
    ElfXX_Sym *lastFile = NULL;
    char fileName[MAXPATHLEN];
    char buf[4096];
    const char *file;
    SymChain *chain;
    const char *format;

    for (i = 0; i < elfInfo->symCount; ++i) {
        ElfXX_Sym *sym = ((ElfXX_Sym *)elfInfo->symData->d_buf) + i;
        if (ELFXX_ST_TYPE(sym->st_info) == STT_FUNC) {

            if (sym->st_shndx == SHN_UNDEF)
                continue;

            if (sym->st_value > value)
                continue;

            if (bestFunc != NULL) {

                if (sym->st_value < bestFunc->st_value)
                    continue;

                /*
                 * If we have two symbols of equal value, we have a problem -
                 * we must pick the "right" one, which is the one the compiler
                 * used to generate the section name with -xF.
                 *
                 * The compiler has the nasty habit of generating two
                 * mangled names for some C++ functions.
                 *
                 * Try - picking the shortest name.
                 */

                if (sym->st_value == bestFunc->st_value) {
                    if (strlen(getSymString(elfInfo, bestFunc->st_name)) <
                        strlen(getSymString(elfInfo, sym->st_name)))
                        continue;
                }

            }
            bestFunc = sym;
            bestFile = lastFile;
        }
        else if (ELFXX_ST_TYPE(sym->st_info) == STT_FILE) {
            lastFile = sym;
        }
    }

    if (bestFunc == NULL)
        fail("Unable to find symbol for address 0x%x.\n", value);

    for (chain = elfInfo->symChainHead; chain != NULL; chain = chain->next) {
        if (chain->sym == bestFunc)
            return;
    }
    chain = (SymChain *)malloc(sizeof (SymChain));
    chain->sym = bestFunc;
    chain->next = elfInfo->symChainHead;
    elfInfo->symChainHead = chain;


    if (ELFXX_ST_BIND(bestFunc->st_info) == STB_GLOBAL)
        file = "";
    else {
        const char *name = getSymString(elfInfo, bestFunc->st_name);
        file = identifyFile(elfInfo, name);
        if (file == NULL) {
            if (bestFile == NULL) {
                file = "notFound";
                fail("Failed to identify %s.\n", name);
            } else {
                char *suffix;
                fileName[0] = ':';
                fileName[1] = ' ';
                file = getSymString(elfInfo, bestFile->st_name);
                strncpy(fileName+2, file, MAXPATHLEN-3);
                suffix = rindex(fileName, '.');
                if (suffix == NULL)
                    fail("no file name suffix?");
                suffix[1] = 'o';
                suffix[2] = '\0';

                file = checkObjFileList(elfInfo, fileName+2);
                if (file != fileName+2)
                    strncpy(fileName+2, file, MAXPATHLEN-3);

                file = fileName;
            }
        } else {
            fileName[0] = ':';
            fileName[1] = ' ';
            strncpy(fileName + 2, file, MAXPATHLEN-3);
            file = fileName;
        }
    }
    format = "text: .text%%%s%s;\n";
    i = snprintf(buf, sizeof buf, format,
            bestFunc ? getSymString(elfInfo, bestFunc->st_name) : "notFound",
            file);
    write(2, buf, i);
}


static mutex_t mutex;
static int orderByCount = FALSE;


static void init_mcount(void)
{
    mutex_init(&mutex, USYNC_THREAD, NULL);
}

#pragma init(init_mcount)


typedef struct CountAddrPair {
    int          count;
    unsigned int addr;
} CountAddrPair;


static int compareCounts(const void *a, const void *b) {
    return ((CountAddrPair *)b)->count - ((CountAddrPair *)a)->count;
}

static int compareCountsReverse(const void *a, const void *b) {
    return ((CountAddrPair *)a)->count - ((CountAddrPair *)b)->count;
}


static void doCounts(void) {
    unsigned int i;
    int n;
    int nMethods;
    int nMethods2;
    CountAddrPair *pairs;
    ElfInfo *elfInfo;

    elfInfo = createElfInfo(libFileName);

    nMethods = 0;
    for (i = 0; i < COUNT_BUF_SIZE >> 2; ++i) {
        n = countBuf[i];
        if (n > 0)
            ++nMethods;
    }
    pairs = (CountAddrPair *)malloc(sizeof(CountAddrPair) * nMethods);
    nMethods2 = 0;
    for (i = 0; i < COUNT_BUF_SIZE >> 2; ++i) {
        n = countBuf[i];
        if (n > 0) {
            pairs[nMethods2].count = n;
            pairs[nMethods2].addr = i << 2;
            ++nMethods2;
            if (nMethods2 > nMethods) {
                fprintf(stderr, "Number of methods detected increased after"
                        " the atexit call.\n");
                break;
            }
        }
    }
    if (orderByCount) {
        qsort(pairs, nMethods, sizeof pairs[0], &compareCounts);
        for (i = 0; i < nMethods; ++i) {
            identifySymbol(elfInfo, pairs[i].addr, pairs[i].count);
        }
    }
    else {
        qsort(pairs, nMethods, sizeof pairs[0], &compareCountsReverse);
        for (i = 0; i < nMethods; ++i) {
            identifySymbol(elfInfo, pairs[i].addr, 0);
        }
    }
}


static void __mcount(void *i0)
{
    Dl_info info;
    unsigned int offset;
    int *p;
    static int callsCounted = 0;
    static int initialized = FALSE;

    if (!initialized) {
        dladdr(i0, &info);
        libFileName = info.dli_fname;
#if 0
        fprintf(stderr, "Profiling %s\n", libFileName);
#endif
        textOffset = info.dli_fbase;
        if (getenv("MCOUNT_ORDER_BY_COUNT") != NULL) {
            orderByCount = TRUE;
        }
        countBuf = (int *)malloc(COUNT_BUF_SIZE);
        memset(countBuf, 0, COUNT_BUF_SIZE);
        atexit(&doCounts);
        initialized = TRUE;
    }

    if ((uintptr_t)i0 < (uintptr_t)textOffset) {
        fprintf(stderr, "mcount: function being profiled out of range????\n");
        fprintf(stderr, "        profiling more than one library at once????\n");
#if 0
        dladdr(i0, &info);
        fprintf(stderr, "Problem with %s in %s ???\n",
                info.dli_sname, info.dli_fname);
#endif
        fflush(stderr);
        exit(666);
    }
    offset = ((uintptr_t)i0) - ((uintptr_t)textOffset);
    if (offset > COUNT_BUF_SIZE) {
        fprintf(stderr, "mcount: internal buffer too small for test.\n");
        fprintf(stderr, "     or function being profiled out of range????\n");
        fprintf(stderr, "     or profiling more than one library at once????\n");
#if 0
        dladdr(i0, &info);
        fprintf(stderr, "Problem with %s in %s ???\n",
                info.dli_sname, info.dli_fname);
#endif
        fflush(stderr);
        exit(666);
    }

    p = &countBuf[offset >>2];
    if (orderByCount) {
        ++*p;
    }
    else {
        if (*p == 0) {
            *p = ++callsCounted;
        }
    }
}


void _mcount(void)
{
    __mcount(_getReturnAddr());
}


void mcount(void)
{
    __mcount(_getReturnAddr());
}
