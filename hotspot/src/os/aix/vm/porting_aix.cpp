/*
 * Copyright 2012, 2013 SAP AG. All rights reserved.
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

#include "asm/assembler.hpp"
#include "loadlib_aix.hpp"
#include "porting_aix.hpp"
#include "utilities/debug.hpp"

#include <demangle.h>
#include <sys/debug.h>

//////////////////////////////////
// Provide implementation for dladdr based on LoadedLibraries pool and
// traceback table scan (see getFuncName).

// Search traceback table in stack,
// return procedure name from trace back table.
#define MAX_FUNC_SEARCH_LEN 0x10000
// Any PC below this value is considered toast.
#define MINIMUM_VALUE_FOR_PC ((unsigned int*)0x1024)

#define PTRDIFF_BYTES(p1,p2) (((ptrdiff_t)p1) - ((ptrdiff_t)p2))

// Align a pointer without having to cast.
inline char* align_ptr_up(char* ptr, intptr_t alignment) {
  return (char*) align_size_up((intptr_t)ptr, alignment);
}

// Trace if verbose to tty.
// I use these now instead of the Xtrace system because the latter is
// not available at init time, hence worthless. Until we fix this, all
// tracing here is done with -XX:+Verbose.
#define trcVerbose(fmt, ...) { \
  if (Verbose) { \
    fprintf(stderr, fmt, ##__VA_ARGS__); \
    fputc('\n', stderr); fflush(stderr); \
  } \
}
#define ERRBYE(s) { trcVerbose(s); return -1; }

// Unfortunately, the interface of dladdr makes the implementator
// responsible for maintaining memory for function name/library
// name. I guess this is because most OS's keep those values as part
// of the mapped executable image ready to use. On AIX, this doesn't
// work, so I have to keep the returned strings. For now, I do this in
// a primitive string map. Should this turn out to be a performance
// problem, a better hashmap has to be used.
class fixed_strings {
  struct node {
    char* v;
    node* next;
  };

  node* first;

  public:

  fixed_strings() : first(0) {}
  ~fixed_strings() {
    node* n = first;
    while (n) {
      node* p = n;
      n = n->next;
      free(p->v);
      delete p;
    }
  }

  char* intern(const char* s) {
    for (node* n = first; n; n = n->next) {
      if (strcmp(n->v, s) == 0) {
        return n->v;
      }
    }
    node* p = new node;
    p->v = strdup(s);
    p->next = first;
    first = p;
    return p->v;
  }
};

static fixed_strings dladdr_fixed_strings;

// Given a code pointer, returns the function name and the displacement.
// Function looks for the traceback table at the end of the function.
extern "C" int getFuncName(
    codeptr_t pc,                    // [in] program counter
    char* p_name, size_t namelen,    // [out] optional: function name ("" if not available)
    int* p_displacement,             // [out] optional: displacement (-1 if not available)
    const struct tbtable** p_tb,     // [out] optional: ptr to traceback table to get further
                                     //                 information (NULL if not available)
    char* p_errmsg, size_t errmsglen // [out] optional: user provided buffer for error messages
  ) {
  struct tbtable* tb = 0;
  unsigned int searchcount = 0;

  // initialize output parameters
  if (p_name && namelen > 0) {
    *p_name = '\0';
  }
  if (p_errmsg && errmsglen > 0) {
    *p_errmsg = '\0';
  }
  if (p_displacement) {
    *p_displacement = -1;
  }
  if (p_tb) {
    *p_tb = NULL;
  }

  // weed out obvious bogus states
  if (pc < MINIMUM_VALUE_FOR_PC) {
    ERRBYE("invalid program counter");
  }

  codeptr_t pc2 = pc;

  // make sure the pointer is word aligned.
  pc2 = (codeptr_t) align_ptr_up((char*)pc2, 4);

  // Find start of traceback table.
  // (starts after code, is marked by word-aligned (32bit) zeros)
  while ((*pc2 != NULL) && (searchcount++ < MAX_FUNC_SEARCH_LEN)) {
    pc2++;
  }
  if (*pc2 != 0) {
    ERRBYE("could not find traceback table within 5000 bytes of program counter");
  }
  //
  // Set up addressability to the traceback table
  //
  tb = (struct tbtable*) (pc2 + 1);

  // Is this really a traceback table? No way to be sure but
  // some indicators we can check.
  if (tb->tb.lang >= 0xf && tb->tb.lang <= 0xfb) {
    // Language specifiers, go from 0 (C) to 14 (Objective C).
    // According to spec, 0xf-0xfa reserved, 0xfb-0xff reserved for ibm.
    ERRBYE("not a traceback table");
  }

  // Existence of fields in the tbtable extension are contingent upon
  // specific fields in the base table.  Check for their existence so
  // that we can address the function name if it exists.
  pc2 = (codeptr_t) tb +
    sizeof(struct tbtable_short)/sizeof(int);
  if (tb->tb.fixedparms != 0 || tb->tb.floatparms != 0)
    pc2++;

  if (tb->tb.has_tboff == TRUE) {

    // I want to know the displacement
    const unsigned int tb_offset = *pc2;
    codeptr_t start_of_procedure =
    (codeptr_t)(((char*)tb) - 4 - tb_offset);  // (-4 to omit leading 0000)

    // Weed out the cases where we did find the wrong traceback table.
    if (pc < start_of_procedure) {
      ERRBYE("could not find (the real) traceback table within 5000 bytes of program counter");
    }

    // return the displacement
    if (p_displacement) {
      (*p_displacement) = (int) PTRDIFF_BYTES(pc, start_of_procedure);
    }

    pc2++;
  } else {
    // return -1 for displacement
    if (p_displacement) {
      (*p_displacement) = -1;
    }
  }

  if (tb->tb.int_hndl == TRUE)
    pc2++;

  if (tb->tb.has_ctl == TRUE)
    pc2 += (*pc2) + 1; // don't care

  //
  // return function name if it exists.
  //
  if (p_name && namelen > 0) {
    if (tb->tb.name_present) {
      char buf[256];
      const short l = MIN2<short>(*((short*)pc2), sizeof(buf) - 1);
      memcpy(buf, (char*)pc2 + sizeof(short), l);
      buf[l] = '\0';

      p_name[0] = '\0';

      // If it is a C++ name, try and demangle it using the Demangle interface (see demangle.h).
      char* rest;
      Name* const name = Demangle(buf, rest);
      if (name) {
        const char* const demangled_name = name->Text();
        if (demangled_name) {
          strncpy(p_name, demangled_name, namelen-1);
          p_name[namelen-1] = '\0';
        }
        delete name;
      }

      // Fallback: if demangling did not work, just provide the unmangled name.
      if (p_name[0] == '\0') {
        strncpy(p_name, buf, namelen-1);
        p_name[namelen-1] = '\0';
      }

    } else {
      strncpy(p_name, "<nameless function>", namelen-1);
      p_name[namelen-1] = '\0';
    }
  }
  // Return traceback table, if user wants it.
  if (p_tb) {
    (*p_tb) = tb;
  }

  return 0;
}

// Special implementation of dladdr for Aix based on LoadedLibraries
// Note: dladdr returns non-zero for ok, 0 for error!
// Note: dladdr is not posix, but a non-standard GNU extension. So this tries to
//   fulfill the contract of dladdr on Linux (see http://linux.die.net/man/3/dladdr)
// Note: addr may be both an AIX function descriptor or a real code pointer
//   to the entry of a function.
extern "C"
int dladdr(void* addr, Dl_info* info) {

  if (!addr) {
    return 0;
  }

  assert(info, "");

  int rc = 0;

  const char* const ZEROSTRING = "";

  // Always return a string, even if a "" one. Linux dladdr manpage
  // does not say anything about returning NULL
  info->dli_fname = ZEROSTRING;
  info->dli_sname = ZEROSTRING;
  info->dli_saddr = NULL;

  address p = (address) addr;
  const LoadedLibraryModule* lib = NULL;

  enum { noclue, code, data } type = noclue;

  trcVerbose("dladdr(%p)...", p);

  // Note: input address may be a function. I accept both a pointer to
  // the entry of a function and a pointer to the function decriptor.
  // (see ppc64 ABI)
  lib = LoadedLibraries::find_for_text_address(p);
  if (lib) {
    type = code;
  }

  if (!lib) {
    // Not a pointer into any text segment. Is it a function descriptor?
    const FunctionDescriptor* const pfd = (const FunctionDescriptor*) p;
    p = pfd->entry();
    if (p) {
      lib = LoadedLibraries::find_for_text_address(p);
      if (lib) {
        type = code;
      }
    }
  }

  if (!lib) {
    // Neither direct code pointer nor function descriptor. A data ptr?
    p = (address)addr;
    lib = LoadedLibraries::find_for_data_address(p);
    if (lib) {
      type = data;
    }
  }

  // If we did find the shared library this address belongs to (either
  // code or data segment) resolve library path and, if possible, the
  // symbol name.
  if (lib) {
    const char* const interned_libpath =
      dladdr_fixed_strings.intern(lib->get_fullpath());
    if (interned_libpath) {
      info->dli_fname = interned_libpath;
    }

    if (type == code) {

      // For code symbols resolve function name and displacement. Use
      // displacement to calc start of function.
      char funcname[256] = "";
      int displacement = 0;

      if (getFuncName((codeptr_t) p, funcname, sizeof(funcname), &displacement,
                      NULL, NULL, 0) == 0) {
        if (funcname[0] != '\0') {
          const char* const interned = dladdr_fixed_strings.intern(funcname);
          info->dli_sname = interned;
          trcVerbose("... function name: %s ...", interned);
        }

        // From the displacement calculate the start of the function.
        if (displacement != -1) {
          info->dli_saddr = p - displacement;
        } else {
          info->dli_saddr = p;
        }
      } else {

        // No traceback table found. Just assume the pointer is it.
        info->dli_saddr = p;

      }

    } else if (type == data) {

      // For data symbols.
      info->dli_saddr = p;

    } else {
      ShouldNotReachHere();
    }

    rc = 1; // success: return 1 [sic]

  }

  // sanity checks.
  if (rc) {
    assert(info->dli_fname, "");
    assert(info->dli_sname, "");
    assert(info->dli_saddr, "");
  }

  return rc; // error: return 0 [sic]

}
