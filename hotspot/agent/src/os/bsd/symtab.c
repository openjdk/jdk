/*
 * Copyright (c) 2003, 2013, Oracle and/or its affiliates. All rights reserved.
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

#include <unistd.h>
#include <search.h>
#include <stdlib.h>
#include <string.h>
#include <db.h>
#include <fcntl.h>

#include "libproc_impl.h"
#include "symtab.h"
#ifndef __APPLE__
#include "salibelf.h"
#endif // __APPLE__


// ----------------------------------------------------
// functions for symbol lookups
// ----------------------------------------------------

typedef struct symtab_symbol {
  char *name;                // name like __ZThread_...
  uintptr_t offset;          // to loaded address
  uintptr_t size;            // size strlen
} symtab_symbol;

typedef struct symtab {
  char *strs;                // all symbols "__symbol1__'\0'__symbol2__...."
  size_t num_symbols;
  DB* hash_table;
  symtab_symbol* symbols;
} symtab_t;

#ifdef __APPLE__

void build_search_table(symtab_t *symtab) {
  int i;
  for (i = 0; i < symtab->num_symbols; i++) {
    DBT key, value;
    key.data = symtab->symbols[i].name;
    key.size = strlen(key.data) + 1;
    value.data = &(symtab->symbols[i]);
    value.size = sizeof(symtab_symbol);
    (*symtab->hash_table->put)(symtab->hash_table, &key, &value, 0);

    // check result
    if (is_debug()) {
      DBT rkey, rvalue;
      char* tmp = (char *)malloc(strlen(symtab->symbols[i].name) + 1);
      strcpy(tmp, symtab->symbols[i].name);
      rkey.data = tmp;
      rkey.size = strlen(tmp) + 1;
      (*symtab->hash_table->get)(symtab->hash_table, &rkey, &rvalue, 0);
      // we may get a copy back so compare contents
      symtab_symbol *res = (symtab_symbol *)rvalue.data;
      if (strcmp(res->name, symtab->symbols[i].name)  ||
          res->offset != symtab->symbols[i].offset    ||
          res->size != symtab->symbols[i].size) {
        print_debug("error to get hash_table value!\n");
      }
      free(tmp);
    }
  }
}

// read symbol table from given fd.
struct symtab* build_symtab(int fd) {
  symtab_t* symtab = NULL;
  int i;
  mach_header_64 header;
  off_t image_start;

  if (!get_arch_off(fd, CPU_TYPE_X86_64, &image_start)) {
    print_debug("failed in get fat header\n");
    return NULL;
  }
  lseek(fd, image_start, SEEK_SET);
  if (read(fd, (void *)&header, sizeof(mach_header_64)) != sizeof(mach_header_64)) {
    print_debug("reading header failed!\n");
    return NULL;
  }
  // header
  if (header.magic != MH_MAGIC_64) {
    print_debug("not a valid .dylib file\n");
    return NULL;
  }

  load_command lcmd;
  symtab_command symtabcmd;
  nlist_64 lentry;

  bool lcsymtab_exist = false;

  long filepos = ltell(fd);
  for (i = 0; i < header.ncmds; i++) {
    lseek(fd, filepos, SEEK_SET);
    if (read(fd, (void *)&lcmd, sizeof(load_command)) != sizeof(load_command)) {
      print_debug("read load_command failed for file\n");
      return NULL;
    }
    filepos += lcmd.cmdsize;  // next command position
    if (lcmd.cmd == LC_SYMTAB) {
      lseek(fd, -sizeof(load_command), SEEK_CUR);
      lcsymtab_exist = true;
      break;
    }
  }
  if (!lcsymtab_exist) {
    print_debug("No symtab command found!\n");
    return NULL;
  }
  if (read(fd, (void *)&symtabcmd, sizeof(symtab_command)) != sizeof(symtab_command)) {
    print_debug("read symtab_command failed for file");
    return NULL;
  }
  symtab = (symtab_t *)malloc(sizeof(symtab_t));
  if (symtab == NULL) {
    print_debug("out of memory: allocating symtab\n");
    return NULL;
  }

  // create hash table, we use berkeley db to
  // manipulate the hash table.
  symtab->hash_table = dbopen(NULL, O_CREAT | O_RDWR, 0600, DB_HASH, NULL);
  if (symtab->hash_table == NULL)
    goto quit;

  symtab->num_symbols = symtabcmd.nsyms;
  symtab->symbols = (symtab_symbol *)malloc(sizeof(symtab_symbol) * symtab->num_symbols);
  symtab->strs    = (char *)malloc(sizeof(char) * symtabcmd.strsize);
  if (symtab->symbols == NULL || symtab->strs == NULL) {
     print_debug("out of memory: allocating symtab.symbol or symtab.strs\n");
     goto quit;
  }
  lseek(fd, image_start + symtabcmd.symoff, SEEK_SET);
  for (i = 0; i < symtab->num_symbols; i++) {
    if (read(fd, (void *)&lentry, sizeof(nlist_64)) != sizeof(nlist_64)) {
      print_debug("read nlist_64 failed at %i\n", i);
      goto quit;
    }
    symtab->symbols[i].offset = lentry.n_value;
    symtab->symbols[i].size  = lentry.n_un.n_strx;        // index
  }

  // string table
  lseek(fd, image_start + symtabcmd.stroff, SEEK_SET);
  int size = read(fd, (void *)(symtab->strs), symtabcmd.strsize * sizeof(char));
  if (size != symtabcmd.strsize * sizeof(char)) {
     print_debug("reading string table failed\n");
     goto quit;
  }

  for (i = 0; i < symtab->num_symbols; i++) {
    symtab->symbols[i].name = symtab->strs + symtab->symbols[i].size;
    if (i > 0) {
      // fix size
      symtab->symbols[i - 1].size = symtab->symbols[i].size - symtab->symbols[i - 1].size;
      print_debug("%s size = %d\n", symtab->symbols[i - 1].name, symtab->symbols[i - 1].size);

    }

    if (i == symtab->num_symbols - 1) {
      // last index
      symtab->symbols[i].size =
            symtabcmd.strsize - symtab->symbols[i].size;
      print_debug("%s size = %d\n", symtab->symbols[i].name, symtab->symbols[i].size);
    }
  }

  // build a hashtable for fast query
  build_search_table(symtab);
  return symtab;
quit:
  if (symtab) destroy_symtab(symtab);
  return NULL;
}

#else // __APPLE__

struct elf_section {
  ELF_SHDR   *c_shdr;
  void       *c_data;
};

// read symbol table from given fd.
struct symtab* build_symtab(int fd) {
  ELF_EHDR ehdr;
  struct symtab* symtab = NULL;

  // Reading of elf header
  struct elf_section *scn_cache = NULL;
  int cnt = 0;
  ELF_SHDR* shbuf = NULL;
  ELF_SHDR* cursct = NULL;
  ELF_PHDR* phbuf = NULL;
  int symtab_found = 0;
  int dynsym_found = 0;
  uint32_t symsection = SHT_SYMTAB;

  uintptr_t baseaddr = (uintptr_t)-1;

  lseek(fd, (off_t)0L, SEEK_SET);
  if (! read_elf_header(fd, &ehdr)) {
    // not an elf
    return NULL;
  }

  // read ELF header
  if ((shbuf = read_section_header_table(fd, &ehdr)) == NULL) {
    goto quit;
  }

  baseaddr = find_base_address(fd, &ehdr);

  scn_cache = calloc(ehdr.e_shnum, sizeof(*scn_cache));
  if (scn_cache == NULL) {
    goto quit;
  }

  for (cursct = shbuf, cnt = 0; cnt < ehdr.e_shnum; cnt++) {
    scn_cache[cnt].c_shdr = cursct;
    if (cursct->sh_type == SHT_SYMTAB ||
        cursct->sh_type == SHT_STRTAB ||
        cursct->sh_type == SHT_DYNSYM) {
      if ( (scn_cache[cnt].c_data = read_section_data(fd, &ehdr, cursct)) == NULL) {
         goto quit;
      }
    }

    if (cursct->sh_type == SHT_SYMTAB)
       symtab_found++;

    if (cursct->sh_type == SHT_DYNSYM)
       dynsym_found++;

    cursct++;
  }

  if (!symtab_found && dynsym_found)
     symsection = SHT_DYNSYM;

  for (cnt = 1; cnt < ehdr.e_shnum; cnt++) {
    ELF_SHDR *shdr = scn_cache[cnt].c_shdr;

    if (shdr->sh_type == symsection) {
      ELF_SYM  *syms;
      int j, n;
      size_t size;

      // FIXME: there could be multiple data buffers associated with the
      // same ELF section. Here we can handle only one buffer. See man page
      // for elf_getdata on Solaris.

      // guarantee(symtab == NULL, "multiple symtab");
      symtab = calloc(1, sizeof(*symtab));
      if (symtab == NULL) {
         goto quit;
      }
      // the symbol table
      syms = (ELF_SYM *)scn_cache[cnt].c_data;

      // number of symbols
      n = shdr->sh_size / shdr->sh_entsize;

      // create hash table, we use berkeley db to
      // manipulate the hash table.
      symtab->hash_table = dbopen(NULL, O_CREAT | O_RDWR, 0600, DB_HASH, NULL);
      // guarantee(symtab->hash_table, "unexpected failure: dbopen");
      if (symtab->hash_table == NULL)
        goto bad;

      // shdr->sh_link points to the section that contains the actual strings
      // for symbol names. the st_name field in ELF_SYM is just the
      // string table index. we make a copy of the string table so the
      // strings will not be destroyed by elf_end.
      size = scn_cache[shdr->sh_link].c_shdr->sh_size;
      symtab->strs = malloc(size);
      if (symtab->strs == NULL)
        goto bad;
      memcpy(symtab->strs, scn_cache[shdr->sh_link].c_data, size);

      // allocate memory for storing symbol offset and size;
      symtab->num_symbols = n;
      symtab->symbols = calloc(n , sizeof(*symtab->symbols));
      if (symtab->symbols == NULL)
        goto bad;

      // copy symbols info our symtab and enter them info the hash table
      for (j = 0; j < n; j++, syms++) {
        DBT key, value;
        char *sym_name = symtab->strs + syms->st_name;

        // skip non-object and non-function symbols
        int st_type = ELF_ST_TYPE(syms->st_info);
        if ( st_type != STT_FUNC && st_type != STT_OBJECT)
           continue;
        // skip empty strings and undefined symbols
        if (*sym_name == '\0' || syms->st_shndx == SHN_UNDEF) continue;

        symtab->symbols[j].name   = sym_name;
        symtab->symbols[j].offset = syms->st_value - baseaddr;
        symtab->symbols[j].size   = syms->st_size;

        key.data = sym_name;
        key.size = strlen(sym_name) + 1;
        value.data = &(symtab->symbols[j]);
        value.size = sizeof(symtab_symbol);
        (*symtab->hash_table->put)(symtab->hash_table, &key, &value, 0);
      }
    }
  }
  goto quit;

bad:
  destroy_symtab(symtab);
  symtab = NULL;

quit:
  if (shbuf) free(shbuf);
  if (phbuf) free(phbuf);
  if (scn_cache) {
    for (cnt = 0; cnt < ehdr.e_shnum; cnt++) {
      if (scn_cache[cnt].c_data != NULL) {
        free(scn_cache[cnt].c_data);
      }
    }
    free(scn_cache);
  }
  return symtab;
}

#endif // __APPLE__

void destroy_symtab(symtab_t* symtab) {
  if (!symtab) return;
  free(symtab->strs);
  free(symtab->symbols);
  free(symtab);
}

uintptr_t search_symbol(struct symtab* symtab, uintptr_t base, const char *sym_name, int *sym_size) {
  DBT key, value;
  int ret;

  // library does not have symbol table
  if (!symtab || !symtab->hash_table) {
     return 0;
  }

  key.data = (char*)(uintptr_t)sym_name;
  key.size = strlen(sym_name) + 1;
  ret = (*symtab->hash_table->get)(symtab->hash_table, &key, &value, 0);
  if (ret == 0) {
    symtab_symbol *sym = value.data;
    uintptr_t rslt = (uintptr_t) ((char*)base + sym->offset);
    if (sym_size) *sym_size = sym->size;
    return rslt;
  }

  return 0;
}

const char* nearest_symbol(struct symtab* symtab, uintptr_t offset,
                           uintptr_t* poffset) {
  int n = 0;
  if (!symtab) return NULL;
  for (; n < symtab->num_symbols; n++) {
    symtab_symbol* sym = &(symtab->symbols[n]);
    if (sym->name != NULL &&
      offset >= sym->offset && offset < sym->offset + sym->size) {
      if (poffset) *poffset = (offset - sym->offset);
      return sym->name;
    }
  }
  return NULL;
}
