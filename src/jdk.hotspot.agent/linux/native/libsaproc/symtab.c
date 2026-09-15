/*
 * Copyright (c) 2003, 2026, Oracle and/or its affiliates. All rights reserved.
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
#include <sys/procfs.h>
#include <search.h>
#include <stdlib.h>
#include <string.h>
#include "symtab.h"
#include "salibelf.h"


// ----------------------------------------------------
// functions for symbol lookups
// ----------------------------------------------------

struct elf_symbol {
  char *name;
  uintptr_t offset;
  uintptr_t size;
};

typedef struct symtab {
  char *strs;
  size_t num_symbols;
  struct elf_symbol *symbols;
  struct hsearch_data *hash_table;
} symtab_t;


static struct symtab* build_symtab_internal(int fd, const char *filename, bool try_debuginfo);

/* Try to open a suitable debuginfo file and read a symbol table from it. */
static struct symtab *build_symtab_from_debuginfo(const char* filename, int fd) {
  int debug_fd = open_debuginfo(filename, fd);
  if (debug_fd >= 0) {
    struct symtab *symtab = build_symtab_internal(debug_fd, NULL, /* try_debuginfo */ false);
    close(debug_fd);
    return symtab;
  }

  return NULL;
}

// read symbol table from given fd.  If try_debuginfo) is true, also
// try to open an associated debuginfo file
static struct symtab* build_symtab_internal(int fd, const char *filename, bool try_debuginfo) {
  ELF_EHDR ehdr;
  struct symtab* symtab = NULL;

  // Reading of elf header
  struct elf_section *scn_cache = NULL;
#if defined(ppc64) && !defined(ABI_ELFv2)
  // Only big endian ppc64 (i.e. ABI_ELFv1) has 'official procedure descriptors' in ELF files
  // see: http://refspecs.linuxfoundation.org/LSB_3.1.1/LSB-Core-PPC64/LSB-Core-PPC64/specialsections.html
  struct elf_section *opd_sect = NULL;
  ELF_SHDR *opd = NULL;
#endif
  int cnt = 0;
  ELF_SHDR* shbuf = NULL;
  ELF_SHDR* cursct = NULL;
  int sym_section = SHT_DYNSYM;

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

  scn_cache = (struct elf_section *)
              calloc(ehdr.e_shnum * sizeof(struct elf_section), 1);
  if (scn_cache == NULL) {
    goto quit;
  }

  for (cursct = shbuf, cnt = 0; cnt < ehdr.e_shnum; cnt++) {
    scn_cache[cnt].c_shdr = cursct;
    if (cursct->sh_type == SHT_SYMTAB || cursct->sh_type == SHT_STRTAB
        || cursct->sh_type == SHT_NOTE || cursct->sh_type == SHT_DYNSYM) {
      if ( (scn_cache[cnt].c_data = read_section_data(fd, &ehdr, cursct)) == NULL) {
         goto quit;
      }
    }
    if (cursct->sh_type == SHT_SYMTAB) {
      // Full symbol table available so use that
      sym_section = cursct->sh_type;
    }
    cursct++;
  }

#if defined(ppc64) && !defined(ABI_ELFv2)
  opd_sect = find_section_by_name(".opd", fd, &ehdr, scn_cache);
  if (opd_sect != NULL && opd_sect->c_data != NULL && opd_sect->c_shdr != NULL) {
    // plausibility check
    opd = opd_sect->c_shdr;
  }
#endif

  for (cnt = 1; cnt < ehdr.e_shnum; cnt++) {
    ELF_SHDR *shdr = scn_cache[cnt].c_shdr;

    if (shdr->sh_type == sym_section) {
      ELF_SYM  *syms;
      size_t size, n, j, htab_sz;

      // FIXME: there could be multiple data buffers associated with the
      // same ELF section. Here we can handle only one buffer. See man page
      // for elf_getdata on Solaris.

      // guarantee(symtab == NULL, "multiple symtab");
      symtab = (struct symtab*)calloc(1, sizeof(struct symtab));
      if (symtab == NULL) {
         goto quit;
      }
      // the symbol table
      syms = (ELF_SYM *)scn_cache[cnt].c_data;

      // number of symbols
      n = shdr->sh_size / shdr->sh_entsize;

      // create hash table, we use hcreate_r, hsearch_r and hdestroy_r to
      // manipulate the hash table.

      // NOTES section in the man page of hcreate_r says
      // "Hash table implementations are usually more efficient when
      // the table contains enough free space to minimize collisions.
      // Typically, this means that nel should be at least 25% larger
      // than the maximum number of elements that the caller expects
      // to store in the table."
      htab_sz = n*1.25;

      symtab->hash_table = (struct hsearch_data*) calloc(1, sizeof(struct hsearch_data));
      if (symtab->hash_table == NULL) {
        goto bad;
      }

      if (hcreate_r(htab_sz, symtab->hash_table) == 0) {
        goto bad;
      }

      // shdr->sh_link points to the section that contains the actual strings
      // for symbol names. the st_name field in ELF_SYM is just the
      // string table index. we make a copy of the string table so the
      // strings will not be destroyed by elf_end.
      size = scn_cache[shdr->sh_link].c_shdr->sh_size;
      symtab->strs = (char *)malloc(size);
      if (symtab->strs == NULL) {
        goto bad;
      }
      memcpy(symtab->strs, scn_cache[shdr->sh_link].c_data, size);

      // allocate memory for storing symbol offset and size;
      symtab->num_symbols = n;
      symtab->symbols = (struct elf_symbol *)calloc(n , sizeof(struct elf_symbol));
      if (symtab->symbols == NULL) {
        goto bad;
      }

      // copy symbols info our symtab and enter them info the hash table
      for (j = 0; j < n; j++, syms++) {
        ENTRY item, *ret;
        uintptr_t sym_value;
        char *sym_name = symtab->strs + syms->st_name;

        // skip non-object and non-function symbols, but STT_NOTYPE is allowed for
        // signal trampoline.
        int st_type = ELF_ST_TYPE(syms->st_info);
        if (st_type != STT_FUNC &&
            st_type != STT_OBJECT &&
            st_type != STT_NOTYPE) {
           continue;
        }
        // skip empty strings and undefined symbols
        if (*sym_name == '\0' || syms->st_shndx == SHN_UNDEF) continue;

        symtab->symbols[j].name   = sym_name;
        symtab->symbols[j].size   = syms->st_size;
        sym_value = syms->st_value;

#if defined(ppc64) && !defined(ABI_ELFv2)
        // see hotspot/src/share/vm/utilities/elfFuncDescTable.hpp for a detailed description
        // of why we have to go this extra way via the '.opd' section on big endian ppc64
        if (opd != NULL && *sym_name != '.' &&
            (opd->sh_addr <= sym_value && sym_value <= opd->sh_addr + opd->sh_size)) {
          sym_value = ((ELF_ADDR*)opd_sect->c_data)[(sym_value - opd->sh_addr) / sizeof(ELF_ADDR*)];
        }
#endif

        symtab->symbols[j].offset = sym_value - baseaddr;
        item.key = sym_name;
        item.data = (void *)&(symtab->symbols[j]);
        hsearch_r(item, ENTER, &ret, symtab->hash_table);
      }
    }
  }

#if defined(ppc64) && !defined(ABI_ELFv2)
  // On Linux/PPC64 the debuginfo files contain an empty function descriptor
  // section (i.e. '.opd' section) which makes the resolution of symbols
  // with the above algorithm impossible (we would need the have both, the
  // .opd section from the library and the symbol table from the debuginfo
  // file which doesn't match with the current workflow.)
  goto quit;
#endif

  // Look for a separate debuginfo file.
  if (try_debuginfo) {
    // We prefer a debug symtab to an object's own symtab, so look in
    // the debuginfo file.  We stash a copy of the old symtab in case
    // there is no debuginfo.
    struct symtab* prev_symtab = symtab;
    symtab = build_symtab_from_debuginfo(filename, fd);

    // If we still haven't found a symtab, use the object's own symtab.
    if (symtab != NULL) {
      if (prev_symtab != NULL)
        destroy_symtab(prev_symtab);
    } else {
      symtab = prev_symtab;
    }
  }
  goto quit;

bad:
  destroy_symtab(symtab);
  symtab = NULL;

quit:
  if (shbuf) free(shbuf);
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

struct symtab* build_symtab(int fd, const char *filename) {
  return build_symtab_internal(fd, filename, /* try_debuginfo */ true);
}


void destroy_symtab(struct symtab* symtab) {
  if (!symtab) return;
  if (symtab->strs) free(symtab->strs);
  if (symtab->symbols) free(symtab->symbols);
  if (symtab->hash_table) {
     hdestroy_r(symtab->hash_table);
     free(symtab->hash_table);
  }
  free(symtab);
}

uintptr_t search_symbol(struct symtab* symtab, uintptr_t base,
                      const char *sym_name, int *sym_size) {
  ENTRY item;
  ENTRY* ret = NULL;

  // library does not have symbol table
  if (!symtab || !symtab->hash_table)
     return (uintptr_t)NULL;

  item.key = (char*) strdup(sym_name);
  item.data = NULL;
  hsearch_r(item, FIND, &ret, symtab->hash_table);
  if (ret) {
    struct elf_symbol * sym = (struct elf_symbol *)(ret->data);
    uintptr_t rslt = (uintptr_t) ((char*)base + sym->offset);
    if (sym_size) *sym_size = sym->size;
    free(item.key);
    return rslt;
  }

  free(item.key);
  return (uintptr_t) NULL;
}

static bool is_in(uintptr_t offset, struct elf_symbol* sym) {
  if (sym->size == 0 && offset == sym->offset) {
    // offset points to the top of the symbol.
    // Some functions have size 0. For example, __restore_rt() (signal trampoline
    // in glibc) would be detected as out of the function incorrectly, even if it
    // points to the top of the instruction address, because the size of
    // __restore_rt() is 0 (you can see this with "readelf -s libc.so.6" when
    // debug symbols are available).
    // Hence we need to treat this as a special case if the function size is 0,
    // only the exact symbol address should be treated as inside.
    return true;
  } else if (offset >= sym->offset && offset < sym->offset + sym->size) {
    // offset is in address range of the symbol
    return true;
  }

  // offset is out of address range of the symbol
  return false;
}

const char* nearest_symbol(struct symtab* symtab, uintptr_t offset,
                           uintptr_t* poffset) {
  int n = 0;
  if (!symtab) return NULL;
  for (; n < symtab->num_symbols; n++) {
     struct elf_symbol* sym = &(symtab->symbols[n]);
     if (sym->name != NULL && is_in(offset, sym)) {
        if (poffset) *poffset = (offset - sym->offset);
        return sym->name;
     }
  }
  return NULL;
}
