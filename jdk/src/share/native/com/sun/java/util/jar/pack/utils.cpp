/*
 * Copyright 2001-2008 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 */

#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <limits.h>

#include <sys/stat.h>

#ifdef _MSC_VER
#include <direct.h>
#include <io.h>
#include <process.h>
#else
#include <unistd.h>
#endif

#include "constants.h"
#include "defines.h"
#include "bytes.h"
#include "utils.h"

#include "unpack.h"

void* must_malloc(int size) {
  int msize = size;
  assert(size >= 0);
  #ifdef USE_MTRACE
  if (msize < sizeof(int))
    msize = sizeof(int);  // see 0xbaadf00d below
  #endif
  void* ptr = malloc(msize);
  if (ptr != null) {
    memset(ptr, 0, size);
  } else {
    unpack_abort(ERROR_ENOMEM);
  }
  mtrace('m', ptr, size);
  return ptr;
}

void mkdirs(int oklen, char* path) {

  if (strlen(path) <= (size_t)oklen)  return;
  char dir[PATH_MAX];

  strcpy(dir, path);
  char* slash = strrchr(dir, '/');
  if (slash == 0)  return;
  *slash = 0;
  mkdirs(oklen, dir);
  MKDIR(dir);
}


#ifndef PRODUCT
void breakpoint() { }  // hook for debugger
int assert_failed(const char* p) {
  char message[1<<12];
  sprintf(message, "@assert failed: %s\n", p);
  fprintf(stdout, 1+message);
  breakpoint();
  unpack_abort(message);
  return 0;
}
#endif

void unpack_abort(const char* msg, unpacker* u) {
  if (msg == null)  msg = "corrupt pack file or internal error";
  if (u == null)
    u = unpacker::current();
  if (u == null) {
    fprintf(stderr, "Error: unpacker: %s\n", msg);
    ::abort();
    return;
  }
  u->abort(msg);
}

bool unpack_aborting(unpacker* u) {
  if (u == null)
    u = unpacker::current();
  if (u == null) {
    fprintf(stderr, "Error: unpacker: no current instance\n");
    ::abort();
    return true;
  }
  return u->aborting();
}

#ifdef USE_MTRACE
// Use this occasionally for detecting storage leaks in unpack.
void mtrace(char c, void* ptr, size_t size) {
  if (c == 'f')  *(int*)ptr = 0xbaadf00d;
  static FILE* mtfp;
  if (mtfp == (FILE*)-1)  return;
  if (mtfp == null) {
    if (getenv("USE_MTRACE") == null) {
      mtfp = (FILE*)-1;
      return;
    }
    char fname[1024];
    sprintf(fname, "mtr%d.txt", getpid());
    mtfp = fopen(fname, "w");
    if (mtfp == null)
      mtfp = stdout;
  }
  fprintf(mtfp, "%c %p %p\n", c, ptr, (void*)size);
}

/* # Script for processing memory traces.
   # It should report only a limited number (2) of "suspended" blocks,
   # even if a large number of archive segments are processed.
   # It should report no "leaked" blocks at all.
   nawk < mtr*.txt '
   function checkleaks(what) {
     nd = 0
     for (ptr in allocated) {
       if (allocated[ptr] == 1) {
         print NR ": " what " " ptr
         #allocated[ptr] = 0  # stop the dangle
         nd++
       }
     }
     if (nd > 0)  print NR ": count " what " " nd
   }

   /^[mfr]/ {
       ptr = $2
       a1 = ($1 == "m")? 1: 0
       a0 = 0+allocated[ptr]
       allocated[ptr] = a1
       if (a0 + a1 != 1) {
         if (a0 == 0 && a1 == 0)
           print NR ": double free " ptr
         else if (a0 == 1 && a1 == 1)
           print NR ": double malloc " ptr
         else
           print NR ": oddity " $0
       }
       next
     }

   /^s/ {
     checkleaks("suspended")
     next
   }

   {
     print NR ": unrecognized " $0
   }
   END {
     checkleaks("leaked")
   }
'
*/
#endif // USE_MTRACE
