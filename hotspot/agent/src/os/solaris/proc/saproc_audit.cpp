/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

#include <link.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <limits.h>
#include <varargs.h>

// This class sets up an interposer on open calls from libproc.so to
// support a pathmap facility in the SA.

static uintptr_t* libproc_cookie;
static uintptr_t* libc_cookie;
static uintptr_t* libsaproc_cookie;


uint_t
la_version(uint_t version)
{
  return (LAV_CURRENT);
}


uint_t
la_objopen(Link_map * lmp, Lmid_t lmid, uintptr_t * cookie)
{
  if (strstr(lmp->l_name, "/libproc.so") != NULL) {
    libproc_cookie = cookie;
    return LA_FLG_BINDFROM;
  }
  if (strstr(lmp->l_name, "/libc.so") != NULL) {
    libc_cookie = cookie;
    return LA_FLG_BINDTO;
  }
  if (strstr(lmp->l_name, "/libsaproc.so") != NULL) {
    libsaproc_cookie = cookie;
    return LA_FLG_BINDTO | LA_FLG_BINDFROM;
  }
  return 0;
}


#if     defined(_LP64)
uintptr_t
la_symbind64(Elf64_Sym *symp, uint_t symndx, uintptr_t *refcook,
             uintptr_t *defcook, uint_t *sb_flags, const char *sym_name)
#else
uintptr_t
la_symbind32(Elf32_Sym *symp, uint_t symndx, uintptr_t *refcook,
             uintptr_t *defcook, uint_t *sb_flags)
#endif
{
#if     !defined(_LP64)
  const char      *sym_name = (const char *)symp->st_name;
#endif
  if (strcmp(sym_name, "open") == 0 && refcook == libproc_cookie) {
    // redirect all open calls from libproc.so through libsaproc_open which will
    // try the alternate library locations first.
    void* handle = dlmopen(LM_ID_BASE, "libsaproc.so", RTLD_NOLOAD);
    if (handle == NULL) {
      fprintf(stderr, "libsaproc_audit.so: didn't find libsaproc.so during linking\n");
    } else {
      uintptr_t libsaproc_open = (uintptr_t)dlsym(handle, "libsaproc_open");
      if (libsaproc_open == 0) {
        fprintf(stderr, "libsaproc_audit.so: didn't find libsaproc_open during linking\n");
      } else {
        return libsaproc_open;
      }
    }
  }
  return symp->st_value;
}
