/*
 * Copyright (c) 2003, 2010, Oracle and/or its affiliates. All rights reserved.
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
#include "symtab.h"
#include "salibelf.h"


// ----------------------------------------------------
// functions for symbol lookups
// ----------------------------------------------------

struct elf_section {
  ELF_SHDR   *c_shdr;
  void       *c_data;
};

struct elf_symbol {
  char *name;
  uintptr_t offset;
  uintptr_t size;
};

typedef struct symtab {
  char *strs;
  size_t num_symbols;
  struct elf_symbol *symbols;
  DB* hash_table;
} symtab_t;

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
        value.size = sizeof(void *);
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

void destroy_symtab(struct symtab* symtab) {
  if (!symtab) return;
  if (symtab->strs) free(symtab->strs);
  if (symtab->symbols) free(symtab->symbols);
  if (symtab->hash_table) {
    (*symtab->hash_table->close)(symtab->hash_table);
  }
  free(symtab);
}

uintptr_t search_symbol(struct symtab* symtab, uintptr_t base,
                      const char *sym_name, int *sym_size) {
  DBT key, value;
  int ret;

  // library does not have symbol table
  if (!symtab || !symtab->hash_table)
     return 0;

  key.data = (char*)(uintptr_t)sym_name;
  key.size = strlen(sym_name) + 1;
  ret = (*symtab->hash_table->get)(symtab->hash_table, &key, &value, 0);
  if (ret == 0) {
    struct elf_symbol *sym = value.data;
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
    struct elf_symbol* sym = &(symtab->symbols[n]);
    if (sym->name != NULL &&
      offset >= sym->offset && offset < sym->offset + sym->size) {
      if (poffset) *poffset = (offset - sym->offset);
      return sym->name;
    }
  }
  return NULL;
}
