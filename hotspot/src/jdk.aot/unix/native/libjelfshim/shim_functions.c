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

#include <libelf.h>
#include <stdio.h>
#include <stdlib.h>

/**
 * TODO: This is an intial and crude attempt to access structure
 * fields of some ELF structrures. Need to figure out a way to access the
 * given field of a given structure instead of writing one shim function
 * per access of each of the structure field.
 **/

#define STRINGIFYHELPER(x) #x
#define STRINGIFY(x) STRINGIFYHELPER(x)
#define FUNC_NAME(S, F) set_ ## S ## _ ## F
#define CAST_STRUCT(S, F) ((Elf_ ## S *) structPtr)
#define CAST_STRUCT32(S, F) ((Elf32_ ## S *) structPtr)
#define CAST_STRUCT64(S, F) ((Elf64_ ## S *) structPtr)
#define ACCESS_FIELD(S, F) CAST_STRUCT(S, F)-> F
#define ACCESS_FIELD32(S, F) CAST_STRUCT32(S, F)-> F
#define ACCESS_FIELD64(S, F) CAST_STRUCT64(S, F)-> F

/*
   Example:
   SET_TYPE_BASED_FIELD(Ehdr, e_machine, int)
   expands to
   set_Ehdr_e_machine(int elfclass, void * strPtr, int val) {
}
*/

#define SET_TYPE_BASED_FIELD(S, F, T)                               \
    void FUNC_NAME(S, F)(int elfclass, void * structPtr, T val) {   \
       if (elfclass == ELFCLASS32)  {                               \
           ACCESS_FIELD32(S, F) = val;                    \
       } else if (elfclass == ELFCLASS64) {               \
           ACCESS_FIELD64(S, F) = val;                    \
       } else {                                           \
           printf("%s: Unknown ELF Class %d provided\n", STRINGIFY(FUNC_NAME(S, F)), elfclass); \
    }  \
    return; \
}

/*
   Example:
   SET_FIELD(Ehdr, e_machine, int)
   expands to
   set_Ehdr_e_machine(void * strPtr, int val) {
}
*/

#define SET_FIELD(S, F, T)                               \
    void FUNC_NAME(S, F)(void * structPtr, T val) {      \
       ACCESS_FIELD(S, F) = val;                         \
       return; \
}

int size_of_Sym(int elfclass) {
    if (elfclass == ELFCLASS32)  {
        return sizeof(Elf32_Sym);
    } else if (elfclass == ELFCLASS64) {
        return sizeof(Elf64_Sym);
    } else {
        printf("Unknown ELF Class %d provided\n", elfclass);
    }
    return -1;
}

int size_of_Rela(int elfclass) {
    if (elfclass == ELFCLASS32)  {
        return sizeof(Elf32_Rela);
    } else if (elfclass == ELFCLASS64) {
        return sizeof(Elf64_Rela);
    } else {
        printf("Unknown ELF Class %d provided\n", elfclass);
    }
    return -1;
}

int size_of_Rel(int elfclass) {
    if (elfclass == ELFCLASS32)  {
        return sizeof(Elf32_Rel);
    } else if (elfclass == ELFCLASS64) {
        return sizeof(Elf64_Rel);
    } else {
        printf("Unknown ELF Class %d provided\n", elfclass);
    }
    return -1;
}

/* ELF Header field access */

void ehdr_set_data_encoding(void * ehdr, int val)  {
    ((Elf32_Ehdr *) ehdr)->e_ident[EI_DATA] = val;
    return;
}

SET_TYPE_BASED_FIELD(Ehdr, e_machine, int)
SET_TYPE_BASED_FIELD(Ehdr, e_type, int)
SET_TYPE_BASED_FIELD(Ehdr, e_version, int)
SET_TYPE_BASED_FIELD(Ehdr, e_shstrndx, int)

/* Data descriptor field access */
SET_FIELD(Data, d_align, int)
SET_FIELD(Data, d_off, int)
SET_FIELD(Data, d_buf, void*)
SET_FIELD(Data, d_type, int)
SET_FIELD(Data, d_size, int)
SET_FIELD(Data, d_version, int)

/* Section Header Access functions */
SET_TYPE_BASED_FIELD(Shdr, sh_name, int)
SET_TYPE_BASED_FIELD(Shdr, sh_type, int)
SET_TYPE_BASED_FIELD(Shdr, sh_flags, int)
SET_TYPE_BASED_FIELD(Shdr, sh_entsize, int)
SET_TYPE_BASED_FIELD(Shdr, sh_link, int)
SET_TYPE_BASED_FIELD(Shdr, sh_info, int)

/* Set the Program Header to be of PH_PHDR type and initialize other
   related fields of the program header.
*/
void phdr_set_type_self(int elfclass, void * ehdr, void * phdr)  {
    if (elfclass == ELFCLASS32) {
        Elf32_Ehdr * ehdr32 = (Elf32_Ehdr *) ehdr;
        Elf32_Phdr * phdr32 = (Elf32_Phdr *) phdr;
        phdr32->p_type = PT_PHDR;
        phdr32->p_offset = ehdr32->e_phoff;
        phdr32->p_filesz = elf32_fsize(ELF_T_PHDR, 1, EV_CURRENT);
    } else if (elfclass == ELFCLASS64) {
        Elf64_Ehdr * ehdr64 = (Elf64_Ehdr *) ehdr;
        Elf64_Phdr * phdr64 = (Elf64_Phdr *) phdr;
        phdr64->p_type = PT_PHDR;
        phdr64->p_offset = ehdr64->e_phoff;
        phdr64->p_filesz = elf64_fsize(ELF_T_PHDR, 1, EV_CURRENT);
    } else {
        printf("phdr_set_type_self: Unknown ELF Class %d provided\n", elfclass);
    }
    return;
}

/*
  Create symbol table entry with given type and binding
*/
void * create_sym_entry(int elfclass, int index, int type, int bind,
                        int shndx, int size, int value) {
  void * symentry = NULL;
    if (elfclass == ELFCLASS32) {
      Elf32_Sym * sym32 = (Elf32_Sym *) malloc(sizeof(Elf32_Sym));
      sym32->st_name = index;
      sym32->st_value = value;
      sym32->st_size = size;
      sym32->st_info = ELF32_ST_INFO(bind, type);
      sym32->st_other = 0;  // TODO: Add an argument to get this value ??
      sym32->st_shndx = shndx;
      symentry = sym32;
    } else if (elfclass == ELFCLASS64) {
      Elf64_Sym * sym64 = (Elf64_Sym *) malloc(sizeof(Elf64_Sym));
      sym64->st_name = index;
      sym64->st_value = value;
      sym64->st_size = size;
      sym64->st_info = ELF64_ST_INFO(bind, type);
      sym64->st_other = 0;  // TODO: Add an argument to get this value ??
      sym64->st_shndx = shndx;
      symentry = sym64;
    } else {
        printf("create_sym_entry: Unknown ELF Class %d provided\n", elfclass);
    }
    return (void *) symentry;
}

// Create a reloc (or reloca entry if argument reloca is non-zero)
void * create_reloc_entry(int elfclass, int roffset, int symtabIdx,
                          int relocType, int raddend, int reloca) {
  void * relocentry = NULL;
  if (elfclass == ELFCLASS32) {
    if (reloca) {
      Elf32_Rela * rela32 = (Elf32_Rela *) malloc(sizeof(Elf32_Rela));
      rela32->r_offset = roffset;
      rela32->r_info = ELF32_R_INFO(symtabIdx, relocType);
      rela32->r_addend = raddend;
      relocentry = rela32;
    } else {
      Elf32_Rel * rel32 = (Elf32_Rel *) malloc(sizeof(Elf32_Rel));
      rel32->r_offset = roffset;
      rel32->r_info = ELF32_R_INFO(symtabIdx, relocType);
      relocentry = rel32;
    }
  } else if (elfclass == ELFCLASS64) {
    if (reloca) {
      Elf64_Rela * rela64 = (Elf64_Rela *) malloc(sizeof(Elf64_Rela));
      rela64->r_offset = roffset;
      rela64->r_info = ELF64_R_INFO(symtabIdx, relocType);
      rela64->r_addend = raddend;
      relocentry = rela64;
    } else {
      Elf64_Rel * rel64 = (Elf64_Rel *) malloc(sizeof(Elf64_Rel));
      rel64->r_offset = roffset;
      rel64->r_info = ELF64_R_INFO(symtabIdx, relocType);
      relocentry = rel64;
    }
  } else {
    printf("create_reloc_entry: Unknown ELF Class %d provided\n", elfclass);
  }
  return (void *) relocentry;
}
