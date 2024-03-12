/*
 * Copyright (c) 2012, 2023 SAP SE. All rights reserved.
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
// needs to be defined first, so that the implicit loaded xcoff.h header defines
// the right structures to analyze the loader header of 64 Bit executable files
// this is needed for rtv_linkedin_libpath() to get the linked (burned) in library
// search path of an XCOFF executable
#define __XCOFF64__
#include <xcoff.h>

#include "asm/assembler.hpp"
#include "compiler/disassembler.hpp"
#include "loadlib_aix.hpp"
#include "memory/allocation.hpp"
#include "memory/allocation.inline.hpp"
#include "misc_aix.hpp"
#include "porting_aix.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/os.hpp"
#include "utilities/align.hpp"
#include "utilities/debug.hpp"
#include <cxxabi.h>
#include <sys/debug.h>
#include <pthread.h>
#include <ucontext.h>

//////////////////////////////////
// Provide implementation for dladdr based on LoadedLibraries pool and
// traceback table scan

// Search traceback table in stack,
// return procedure name from trace back table.
#define MAX_FUNC_SEARCH_LEN 0x10000

#define PTRDIFF_BYTES(p1,p2) (((ptrdiff_t)p1) - ((ptrdiff_t)p2))

// Typedefs for stackslots, stack pointers, pointers to op codes.
typedef unsigned long stackslot_t;
typedef stackslot_t* stackptr_t;
typedef unsigned int* codeptr_t;

// Unfortunately, the interface of dladdr makes the implementer
// responsible for maintaining memory for function name/library
// name. I guess this is because most OS's keep those values as part
// of the mapped executable image ready to use. On AIX, this doesn't
// work, so I have to keep the returned strings. For now, I do this in
// a primitive string map. Should this turn out to be a performance
// problem, a better hashmap has to be used.
class fixed_strings {
  struct node : public CHeapObj<mtInternal> {
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
      os::free(p->v);
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
    p->v = os::strdup_check_oom(s);
    p->next = first;
    first = p;
    return p->v;
  }
};

static fixed_strings dladdr_fixed_strings;

bool AixSymbols::get_function_name (
    address pc0,                     // [in] program counter
    char* p_name, size_t namelen,    // [out] optional: function name ("" if not available)
    int* p_displacement,             // [out] optional: displacement (-1 if not available)
    const struct tbtable** p_tb,     // [out] optional: ptr to traceback table to get further
                                     //                 information (null if not available)
    bool demangle                    // [in] whether to demangle the name
  ) {
  struct tbtable* tb = 0;
  unsigned int searchcount = 0;

  // initialize output parameters
  if (p_name && namelen > 0) {
    *p_name = '\0';
  }
  if (p_displacement) {
    *p_displacement = -1;
  }
  if (p_tb) {
    *p_tb = nullptr;
  }

  codeptr_t pc = (codeptr_t)pc0;

  // weed out obvious bogus states
  if (pc < (codeptr_t)0x1000) {
    trcVerbose("invalid program counter");
    return false;
  }

  // We see random but frequent crashes in this function since some months mainly on shutdown
  // (-XX:+DumpInfoAtExit). It appears the page we are reading is randomly disappearing while
  // we read it (?).
  // As the pc cannot be trusted to be anything sensible lets make all reads via SafeFetch. Also
  // bail if this is not a text address right now.
  if (!LoadedLibraries::find_for_text_address(pc, nullptr)) {
    trcVerbose("not a text address");
    return false;
  }

  // .. (Note that is_readable_pointer returns true if safefetch stubs are not there yet;
  // in that case I try reading the traceback table unsafe - I rather risk secondary crashes in
  // error files than not having a callstack.)
#define CHECK_POINTER_READABLE(p) \
  if (!os::is_readable_pointer(p)) { \
    trcVerbose("pc not readable"); \
    return false; \
  }

  codeptr_t pc2 = (codeptr_t) pc;

  // Make sure the pointer is word aligned.
  pc2 = (codeptr_t) align_up((char*)pc2, 4);
  CHECK_POINTER_READABLE(pc2)

  // Find start of traceback table.
  // (starts after code, is marked by word-aligned (32bit) zeros)
  while ((*pc2 != 0) && (searchcount++ < MAX_FUNC_SEARCH_LEN)) {
    CHECK_POINTER_READABLE(pc2)
    pc2++;
  }
  if (*pc2 != 0) {
    trcVerbose("no traceback table found");
    return false;
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
    trcVerbose("no traceback table found");
    return false;
  }

  // Existence of fields in the tbtable extension are contingent upon
  // specific fields in the base table.  Check for their existence so
  // that we can address the function name if it exists.
  pc2 = (codeptr_t) tb +
    sizeof(struct tbtable_short)/sizeof(int);
  if (tb->tb.fixedparms != 0 || tb->tb.floatparms != 0)
    pc2++;

  CHECK_POINTER_READABLE(pc2)

  if (tb->tb.has_tboff == TRUE) {

    // I want to know the displacement
    const unsigned int tb_offset = *pc2;
    codeptr_t start_of_procedure =
    (codeptr_t)(((char*)tb) - 4 - tb_offset);  // (-4 to omit leading 0000)

    // Weed out the cases where we did find the wrong traceback table.
    if (pc < start_of_procedure) {
      trcVerbose("no traceback table found");
      return false;
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

  CHECK_POINTER_READABLE(pc2)

  //
  // return function name if it exists.
  //
  if (p_name && namelen > 0) {
    if (tb->tb.name_present) {
      // Copy name from text because it may not be zero terminated.
      const short l = MIN2<short>(*((short*)pc2), namelen - 1);
      // Be very careful.
      int i = 0; char* const p = (char*)pc2 + sizeof(short);
      while (i < l && os::is_readable_pointer(p + i)) {
        p_name[i] = p[i];
        i++;
      }
      p_name[i] = '\0';

      // If it is a C++ name, try and demangle it using the __cxa_demangle interface(see demangle.h).
      if (demangle) {
        int status;
        char *demangled_name = abi::__cxa_demangle(p_name, nullptr, nullptr, &status);
        if ((demangled_name != nullptr) && (status == 0)) {
          strncpy(p_name, demangled_name, namelen-1);
          p_name[namelen-1] = '\0';
        }
        if (demangled_name != nullptr) {
          ALLOW_C_FUNCTION(::free, ::free(demangled_name));
        }
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

  return true;

}

bool AixSymbols::get_module_name(address pc,
                         char* p_name, size_t namelen) {

  if (p_name && namelen > 0) {
    p_name[0] = '\0';
    loaded_module_t lm;
    if (LoadedLibraries::find_for_text_address(pc, &lm)) {
      strncpy(p_name, lm.shortname, namelen);
      p_name[namelen - 1] = '\0';
      return true;
    }
  }

  return false;
}

bool AixSymbols::get_module_name_and_base(address pc,
                         char* p_name, size_t namelen,
                         address* p_base) {

  if (p_base && p_name && namelen > 0) {
    p_name[0] = '\0';
    loaded_module_t lm;
    if (LoadedLibraries::find_for_text_address(pc, &lm)) {
      strncpy(p_name, lm.shortname, namelen);
      p_name[namelen - 1] = '\0';
      *p_base = (address) lm.text;
      return true;
    }
  }

  return false;
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
  // does not say anything about returning null
  info->dli_fname = ZEROSTRING;
  info->dli_sname = ZEROSTRING;
  info->dli_saddr = nullptr;

  address p = (address) addr;
  loaded_module_t lm;
  bool found = false;

  enum { noclue, code, data } type = noclue;

  trcVerbose("dladdr(%p)...", p);

  // Note: input address may be a function. I accept both a pointer to
  // the entry of a function and a pointer to the function descriptor.
  // (see ppc64 ABI)
  found = LoadedLibraries::find_for_text_address(p, &lm);
  if (found) {
    type = code;
  }

  if (!found) {
    // Not a pointer into any text segment. Is it a function descriptor?
    const FunctionDescriptor* const pfd = (const FunctionDescriptor*) p;
    p = pfd->entry();
    if (p) {
      found = LoadedLibraries::find_for_text_address(p, &lm);
      if (found) {
        type = code;
      }
    }
  }

  if (!found) {
    // Neither direct code pointer nor function descriptor. A data ptr?
    p = (address)addr;
    found = LoadedLibraries::find_for_data_address(p, &lm);
    if (found) {
      type = data;
    }
  }

  // If we did find the shared library this address belongs to (either
  // code or data segment) resolve library path and, if possible, the
  // symbol name.
  if (found) {

    // No need to intern the libpath, that one is already interned one layer below.
    info->dli_fname = lm.path;

    if (type == code) {

      // For code symbols resolve function name and displacement. Use
      // displacement to calc start of function.
      char funcname[256] = "";
      int displacement = 0;

      if (AixSymbols::get_function_name(p, funcname, sizeof(funcname),
                      &displacement, nullptr, true)) {
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

/////////////////////////////////////////////////////////////////////////////
// Native callstack dumping

// Print the traceback table for one stack frame.
static void print_tbtable (outputStream* st, const struct tbtable* p_tb) {

  if (p_tb == nullptr) {
    st->print("<null>");
    return;
  }

  switch(p_tb->tb.lang) {
    case TB_C: st->print("C"); break;
    case TB_FORTRAN: st->print("FORTRAN"); break;
    case TB_PASCAL: st->print("PASCAL"); break;
    case TB_ADA: st->print("ADA"); break;
    case TB_PL1: st->print("PL1"); break;
    case TB_BASIC: st->print("BASIC"); break;
    case TB_LISP: st->print("LISP"); break;
    case TB_COBOL: st->print("COBOL"); break;
    case TB_MODULA2: st->print("MODULA2"); break;
    case TB_CPLUSPLUS: st->print("C++"); break;
    case TB_RPG: st->print("RPG"); break;
    case TB_PL8: st->print("PL8"); break;
    case TB_ASM: st->print("ASM"); break;
    case TB_HPJ: st->print("HPJ"); break;
    default: st->print("unknown");
  }
  st->print(" ");

  if (p_tb->tb.globallink) {
    st->print("globallink ");
  }
  if (p_tb->tb.is_eprol) {
    st->print("eprol ");
  }
  if (p_tb->tb.int_proc) {
    st->print("int_proc ");
  }
  if (p_tb->tb.tocless) {
    st->print("tocless ");
  }
  if (p_tb->tb.fp_present) {
    st->print("fp_present ");
  }
  if (p_tb->tb.int_hndl) {
    st->print("interrupt_handler ");
  }
  if (p_tb->tb.uses_alloca) {
    st->print("uses_alloca ");
  }
  if (p_tb->tb.saves_cr) {
    st->print("saves_cr ");
  }
  if (p_tb->tb.saves_lr) {
    st->print("saves_lr ");
  }
  if (p_tb->tb.stores_bc) {
    st->print("stores_bc ");
  }
  if (p_tb->tb.fixup) {
    st->print("fixup ");
  }
  if (p_tb->tb.fpr_saved > 0) {
    st->print("fpr_saved:%d ", p_tb->tb.fpr_saved);
  }
  if (p_tb->tb.gpr_saved > 0) {
    st->print("gpr_saved:%d ", p_tb->tb.gpr_saved);
  }
  if (p_tb->tb.fixedparms > 0) {
    st->print("fixedparms:%d ", p_tb->tb.fixedparms);
  }
  if (p_tb->tb.floatparms > 0) {
    st->print("floatparms:%d ", p_tb->tb.floatparms);
  }
  if (p_tb->tb.parmsonstk > 0) {
    st->print("parmsonstk:%d", p_tb->tb.parmsonstk);
  }
}

// Print information for pc (module, function, displacement, traceback table)
// on one line.
static void print_info_for_pc (outputStream* st, codeptr_t pc, char* buf,
                               size_t buf_size, bool demangle) {
  const struct tbtable* tb = nullptr;
  int displacement = -1;

  if (!os::is_readable_pointer(pc)) {
    st->print("(invalid)");
    return;
  }

  if (AixSymbols::get_module_name((address)pc, buf, buf_size)) {
    st->print("%s", buf);
  } else {
    st->print("(unknown module)");
  }
  st->print("::");
  if (AixSymbols::get_function_name((address)pc, buf, buf_size,
                                     &displacement, &tb, demangle)) {
    st->print("%s", buf);
  } else {
    st->print("(unknown function)");
  }
  if (displacement == -1) {
    st->print("+?");
  } else {
    st->print("+0x%x", displacement);
  }
  if (tb) {
    st->fill_to(64);
    st->print("  (");
    print_tbtable(st, tb);
    st->print(")");
  }
}

static void print_stackframe(outputStream* st, stackptr_t sp, char* buf,
                             size_t buf_size, bool demangle) {

  stackptr_t sp2 = sp;

  // skip backchain

  sp2++;

  // skip crsave

  sp2++;

  // retrieve lrsave. That is the only info I need to get the function/displacement

  codeptr_t lrsave = (codeptr_t) *(sp2);
  st->print (PTR_FORMAT " - " PTR_FORMAT " ", p2i(sp2), p2i(lrsave));

  if (lrsave != nullptr) {
    print_info_for_pc(st, lrsave, buf, buf_size, demangle);
  }

}

// Function to check a given stack pointer against given stack limits.
static bool is_valid_stackpointer(stackptr_t sp, stackptr_t stack_base, size_t stack_size) {
  if (((uintptr_t)sp) & 0x7) {
    return false;
  }
  if (sp > stack_base) {
    return false;
  }
  if (sp < (stackptr_t) ((address)stack_base - stack_size)) {
    return false;
  }
  return true;
}

// Returns true if function is a valid codepointer.
static bool is_valid_codepointer(codeptr_t p) {
  if (!p) {
    return false;
  }
  if (((uintptr_t)p) & 0x3) {
    return false;
  }
  return LoadedLibraries::find_for_text_address(p, nullptr);
}

// Function tries to guess if the given combination of stack pointer, stack base
// and stack size is a valid stack frame.
static bool is_valid_frame (stackptr_t p, stackptr_t stack_base, size_t stack_size) {

  if (!is_valid_stackpointer(p, stack_base, stack_size)) {
    return false;
  }

  // First check - the occurrence of a valid backchain pointer up the stack, followed by a
  // valid codeptr, counts as a good candidate.
  stackptr_t sp2 = (stackptr_t) *p;
  if (is_valid_stackpointer(sp2, stack_base, stack_size) && // found a valid stack pointer in the stack...
     ((sp2 - p) > 6) &&  // ... pointing upwards and not into my frame...
     is_valid_codepointer((codeptr_t)(*(sp2 + 2)))) // ... followed by a code pointer after two slots...
  {
    return true;
  }

  return false;
}

// Try to relocate a stack back chain in a given stack.
// Used in callstack dumping, when the backchain is broken by an overwriter
static stackptr_t try_find_backchain (stackptr_t last_known_good_frame,
                                      stackptr_t stack_base, size_t stack_size)
{
  if (!is_valid_stackpointer(last_known_good_frame, stack_base, stack_size)) {
    return nullptr;
  }

  stackptr_t sp = last_known_good_frame;

  sp += 6; // Omit next fixed frame slots.
  while (sp < stack_base) {
    if (is_valid_frame(sp, stack_base, stack_size)) {
      return sp;
    }
    sp ++;
  }

  return nullptr;
}

static void decode_instructions_at_pc(const char* header,
                                      codeptr_t pc, int num_before,
                                      int num_after, outputStream* st) {
  // TODO: PPC port Disassembler::decode(pc, 16, 16, st);
}


void AixNativeCallstack::print_callstack_for_context(outputStream* st, const ucontext_t* context,
                                                     bool demangle, char* buf, size_t buf_size) {

#define MAX_CALLSTACK_DEPTH 50

  unsigned long* sp;
  unsigned long* sp_last;
  int frame;

  // To print the first frame, use the current value of iar:
  // current entry indicated by iar (the current pc)
  codeptr_t cur_iar = 0;
  stackptr_t cur_sp = 0;
  codeptr_t cur_rtoc = 0;
  codeptr_t cur_lr = 0;

  const ucontext_t* uc = (const ucontext_t*) context;

  // fallback: use the current context
  ucontext_t local_context;
  if (!uc) {
    st->print_cr("No context given, using current context.");
    if (getcontext(&local_context) == 0) {
      uc = &local_context;
    } else {
      st->print_cr("No context given and getcontext failed. ");
      return;
    }
  }

  cur_iar = (codeptr_t)uc->uc_mcontext.jmp_context.iar;
  cur_sp = (stackptr_t)uc->uc_mcontext.jmp_context.gpr[1];
  cur_rtoc = (codeptr_t)uc->uc_mcontext.jmp_context.gpr[2];
  cur_lr = (codeptr_t)uc->uc_mcontext.jmp_context.lr;

  // syntax used here:
  //  n   --------------   <-- stack_base,   stack_to
  //  n-1 |            |
  //  ... | older      |
  //  ... |   frames   | |
  //      |            | | stack grows downward
  //  ... | younger    | |
  //  ... |   frames   | V
  //      |            |
  //      |------------|   <-- cur_sp, current stack ptr
  //      |            |
  //      |  unused    |
  //      |    stack   |
  //      |            |
  //      .            .
  //      .            .
  //      .            .
  //      .            .
  //      |            |
  //   0  --------------   <-- stack_from
  //

  // Retrieve current stack base, size from the current thread. If there is none,
  // retrieve it from the OS.
  stackptr_t stack_base = nullptr;
  size_t stack_size = 0;
  {
    AixMisc::stackbounds_t stackbounds;
    if (!AixMisc::query_stack_bounds_for_current_thread(&stackbounds)) {
      st->print_cr("Cannot retrieve stack bounds.");
      return;
    }
    stack_base = (stackptr_t)stackbounds.base;
    stack_size = stackbounds.size;
  }

  st->print_cr("Native frame:");
  st->print("iar:  " PTR_FORMAT " ", p2i(cur_iar));
  print_info_for_pc(st, cur_iar, buf, buf_size, demangle);
  st->cr();

  if (cur_iar && os::is_readable_pointer(cur_iar)) {
    decode_instructions_at_pc(
      "Decoded instructions at iar:",
      cur_iar, 32, 16, st);
  }

  // Print out lr too, which may be interesting if we did jump to some bogus location;
  // in those cases the new frame is not built up yet and the caller location is only
  // preserved via lr register.
  st->print("lr:   " PTR_FORMAT " ", p2i(cur_lr));
  print_info_for_pc(st, cur_lr, buf, buf_size, demangle);
  st->cr();

  if (cur_lr && os::is_readable_pointer(cur_lr)) {
    decode_instructions_at_pc(
      "Decoded instructions at lr:",
      cur_lr, 32, 16, st);
  }

  // Check and print sp.
  st->print("sp:   " PTR_FORMAT " ", p2i(cur_sp));
  if (!is_valid_stackpointer(cur_sp, stack_base, stack_size)) {
    st->print("(invalid) ");
    goto cleanup;
  } else {
    st->print("(base - 0x%X) ", PTRDIFF_BYTES(stack_base, cur_sp));
  }
  st->cr();

  // Check and print rtoc.
  st->print("rtoc: "  PTR_FORMAT " ", p2i(cur_rtoc));
  if (cur_rtoc == nullptr || cur_rtoc == (codeptr_t)-1 ||
      !os::is_readable_pointer(cur_rtoc)) {
    st->print("(invalid)");
  } else if (((uintptr_t)cur_rtoc) & 0x7) {
    st->print("(unaligned)");
  }
  st->cr();

  st->print_cr("|---stackaddr----|   |----lrsave------|:   <function name>");

  ///
  // Walk callstack.
  //
  // (if no context was given, use the current stack)
  sp = (unsigned long*)(*(unsigned long*)cur_sp); // Stack pointer
  sp_last = cur_sp;

  frame = 0;

  while (frame < MAX_CALLSTACK_DEPTH) {

    // Check sp.
    bool retry = false;
    if (sp == nullptr) {
      // The backchain pointer was null. This normally means the end of the chain. But the
      // stack might be corrupted, and it may be worth looking for the stack chain.
      if (is_valid_stackpointer(sp_last, stack_base, stack_size) && (stack_base - 0x10) > sp_last) {
        // If we are not within <guess> 0x10 stackslots of the stack base, we assume that this
        // is indeed not the end of the chain but that the stack was corrupted. So lets try to
        // find the end of the chain.
        st->print_cr("*** back chain pointer is null - end of stack or broken backchain ? ***");
        retry = true;
      } else {
        st->print_cr("*** end of backchain ***");
        goto end_walk_callstack;
      }
    } else if (!is_valid_stackpointer(sp, stack_base, stack_size)) {
      st->print_cr("*** stack pointer invalid - backchain corrupted (" PTR_FORMAT ") ***", p2i(sp));
      retry = true;
    } else if (sp < sp_last) {
      st->print_cr("invalid stack pointer: " PTR_FORMAT " (not monotone raising)", p2i(sp));
      retry = true;
    }

    // If backchain is broken, try to recover, by manually scanning the stack for a pattern
    // which looks like a valid stack.
    if (retry) {
      st->print_cr("trying to recover and find backchain...");
      sp = try_find_backchain(sp_last, stack_base, stack_size);
      if (sp) {
        st->print_cr("found something which looks like a backchain at " PTR_FORMAT ", after 0x%x bytes... ",
            p2i(sp), PTRDIFF_BYTES(sp, sp_last));
      } else {
        st->print_cr("did not find a backchain, giving up.");
        goto end_walk_callstack;
      }
    }

    // Print stackframe.
    print_stackframe(st, sp, buf, buf_size, demangle);
    st->cr();
    frame ++;

    // Next stack frame and link area.
    sp_last = sp;
    sp = (unsigned long*)(*sp);
  }

  // Prevent endless loops in case of invalid callstacks.
  if (frame == MAX_CALLSTACK_DEPTH) {
    st->print_cr("...(stopping after %d frames.", MAX_CALLSTACK_DEPTH);
  }

end_walk_callstack:

  st->print_cr("-----------------------");

cleanup:

  return;

}


bool AixMisc::query_stack_bounds_for_current_thread(stackbounds_t* out) {

  // Information about this api can be found (a) in the pthread.h header and
  // (b) in http://publib.boulder.ibm.com/infocenter/pseries/v5r3/index.jsp?topic=/com.ibm.aix.basetechref/doc/basetrf1/pthread_getthrds_np.htm
  //
  // The use of this API to find out the current stack is kind of undefined.
  // But after a lot of tries and asking IBM about it, I concluded that it is safe
  // enough for cases where I let the pthread library create its stacks. For cases
  // where I create an own stack and pass this to pthread_create, it seems not to
  // work (the returned stack size in that case is 0).

  pthread_t tid = pthread_self();
  struct __pthrdsinfo pinfo;
  char dummy[1]; // Just needed to satisfy pthread_getthrds_np.
  int dummy_size = sizeof(dummy);

  memset(&pinfo, 0, sizeof(pinfo));

  const int rc = pthread_getthrds_np(&tid, PTHRDSINFO_QUERY_ALL, &pinfo,
                                     sizeof(pinfo), dummy, &dummy_size);

  if (rc != 0) {
    fprintf(stderr, "pthread_getthrds_np failed (%d)\n", rc);
    fflush(stdout);
    return false;
  }

  // The following may happen when invoking pthread_getthrds_np on a pthread
  // running on a user provided stack (when handing down a stack to pthread
  // create, see pthread_attr_setstackaddr).
  // Not sure what to do then.
  if (pinfo.__pi_stackend == nullptr || pinfo.__pi_stackaddr == nullptr) {
    fprintf(stderr, "pthread_getthrds_np - invalid values\n");
    fflush(stdout);
    return false;
  }

  // Note: we get three values from pthread_getthrds_np:
  //       __pi_stackaddr, __pi_stacksize, __pi_stackend
  //
  // high addr    ---------------------                                                           base, high
  //
  //    |         pthread internal data, like ~2K
  //    |
  //    |         ---------------------   __pi_stackend   (usually not page aligned, (xxxxF890))
  //    |
  //    |
  //    |
  //    |
  //    |
  //    |
  //    |          ---------------------   (__pi_stackend - __pi_stacksize)
  //    |
  //    |          padding to align the following AIX guard pages, if enabled.
  //    |
  //    V          ---------------------   __pi_stackaddr                                        low, base - size
  //
  // low addr      AIX guard pages, if enabled (AIXTHREAD_GUARDPAGES > 0)
  //

  out->base = (address)pinfo.__pi_stackend;
  address low = (address)pinfo.__pi_stackaddr;
  out->size = out->base - low;
  return true;

}

// variables needed to emulate linux behavior in os::dll_load() if library is loaded twice
static pthread_mutex_t g_handletable_mutex = PTHREAD_MUTEX_INITIALIZER;

struct TableLocker {
  TableLocker() { pthread_mutex_lock(&g_handletable_mutex); }
  ~TableLocker() { pthread_mutex_unlock(&g_handletable_mutex); }
};
struct handletableentry{
    void*   handle;
    ino64_t inode;
    dev64_t devid;
    uint    refcount;
};
constexpr unsigned init_num_handles = 128;
static unsigned max_handletable = 0;
static unsigned g_handletable_used = 0;
// We start with an empty array. At first use we will dynamically allocate memory for 128 entries.
// If this table is full we dynamically reallocate a memory reagion of double size, and so on.
static struct handletableentry* p_handletable = nullptr;

// get the library search path burned in to the executable file during linking
// If the libpath cannot be retrieved return an empty path
static const char* rtv_linkedin_libpath() {
  constexpr int bufsize = 4096;
  static char buffer[bufsize];
  static const char* libpath = 0;

  // we only try to retrieve the libpath once. After that try we
  // let libpath point to buffer, which then contains a valid libpath
  // or an empty string
  if (libpath != nullptr) {
    return libpath;
  }

  // retrieve the path to the currently running executable binary
  // to open it
  snprintf(buffer, 100, "/proc/%ld/object/a.out", (long)getpid());
  FILE* f = nullptr;
  struct xcoffhdr the_xcoff;
  struct scnhdr the_scn;
  struct ldhdr the_ldr;
  constexpr size_t xcoffsz = FILHSZ + _AOUTHSZ_EXEC;
  STATIC_ASSERT(sizeof(the_xcoff) == xcoffsz);
  STATIC_ASSERT(sizeof(the_scn) == SCNHSZ);
  STATIC_ASSERT(sizeof(the_ldr) == LDHDRSZ);
  // read the generic XCOFF header and analyze the substructures
  // to find the burned in libpath. In any case of error perform the assert
  if (nullptr == (f = fopen(buffer, "r")) ||
      xcoffsz != fread(&the_xcoff, 1, xcoffsz, f) ||
      the_xcoff.filehdr.f_magic != U64_TOCMAGIC ||
      0 != fseek(f, (FILHSZ + the_xcoff.filehdr.f_opthdr + (the_xcoff.aouthdr.o_snloader -1)*SCNHSZ), SEEK_SET) ||
      SCNHSZ != fread(&the_scn, 1, SCNHSZ, f) ||
      0 != strcmp(the_scn.s_name, ".loader") ||
      0 != fseek(f, the_scn.s_scnptr, SEEK_SET) ||
      LDHDRSZ != fread(&the_ldr, 1, LDHDRSZ, f) ||
      0 != fseek(f, the_scn.s_scnptr + the_ldr.l_impoff, SEEK_SET) ||
      0 == fread(buffer, 1, bufsize, f)) {
    buffer[0] = 0;
    assert(false, "could not retrieve burned in library path from executables loader section");
  }

  if (f) {
    fclose(f);
  }
  libpath = buffer;

  return libpath;
}

// Simulate the library search algorithm of dlopen() (in os::dll_load)
static bool search_file_in_LIBPATH(const char* path, struct stat64x* stat) {
  if (path == nullptr)
    return false;

  char* path2 = os::strdup(path);
  // if exist, strip off trailing (shr_64.o) or similar
  char* substr;
  if (path2[strlen(path2) - 1] == ')' && (substr = strrchr(path2, '('))) {
    *substr = 0;
  }

  bool ret = false;
  // If FilePath contains a slash character, FilePath is used directly,
  // and no directories are searched.
  // But if FilePath does not start with / or . we have to prepend it with ./
  if (strchr(path2, '/')) {
    stringStream combined;
    if (*path2 == '/' || *path2 == '.') {
      combined.print("%s", path2);
    } else {
      combined.print("./%s", path2);
    }
    ret = (0 == stat64x(combined.base(), stat));
    os::free(path2);
    return ret;
  }

  const char* env = getenv("LIBPATH");
  if (env == nullptr) {
    // no LIBPATH, try with LD_LIBRARY_PATH
    env = getenv("LD_LIBRARY_PATH");
  }

  stringStream Libpath;
  if (env == nullptr) {
    // no LIBPATH or LD_LIBRARY_PATH given -> try only with burned in libpath
    Libpath.print("%s", rtv_linkedin_libpath());
  } else if (*env == 0) {
    // LIBPATH or LD_LIBRARY_PATH given but empty -> try first with burned
    //  in libpath and with current working directory second
    Libpath.print("%s:.", rtv_linkedin_libpath());
  } else {
    // LIBPATH or LD_LIBRARY_PATH given with content -> try first with
    // LIBPATH or LD_LIBRARY_PATH and second with burned in libpath.
    // No check against current working directory
    Libpath.print("%s:%s", env, rtv_linkedin_libpath());
  }

  char* libpath = os::strdup(Libpath.base());

  char *saveptr, *token;
  for (token = strtok_r(libpath, ":", &saveptr); token != nullptr; token = strtok_r(nullptr, ":", &saveptr)) {
    stringStream combined;
    combined.print("%s/%s", token, path2);
    if ((ret = (0 == stat64x(combined.base(), stat))))
      break;
  }

  os::free(libpath);
  os::free(path2);
  return ret;
}

// specific AIX versions for ::dlopen() and ::dlclose(), which handles the struct g_handletable
// This way we mimic dl handle equality for a library
// opened a second time, as it is implemented on other platforms.
void* Aix_dlopen(const char* filename, int Flags, const char** error_report) {
  assert(error_report != nullptr, "error_report is nullptr");
  void* result;
  struct stat64x libstat;

  if (false == search_file_in_LIBPATH(filename, &libstat)) {
    // file with filename does not exist
  #ifdef ASSERT
    result = ::dlopen(filename, Flags);
    assert(result == nullptr, "dll_load: Could not stat() file %s, but dlopen() worked; Have to improve stat()", filename);
  #endif
    *error_report = "Could not load module .\nSystem error: No such file or directory";
    return nullptr;
  }
  else {
    unsigned i = 0;
    TableLocker lock;
    // check if library belonging to filename is already loaded.
    // If yes use stored handle from previous ::dlopen() and increase refcount
    for (i = 0; i < g_handletable_used; i++) {
      if ((p_handletable + i)->handle &&
          (p_handletable + i)->inode == libstat.st_ino &&
          (p_handletable + i)->devid == libstat.st_dev) {
        (p_handletable + i)->refcount++;
        result = (p_handletable + i)->handle;
        break;
      }
    }
    if (i == g_handletable_used) {
      // library not yet loaded. Check if there is space left in array
      // to store new ::dlopen() handle
      if (g_handletable_used == max_handletable) {
        // No place in array anymore; increase array.
        unsigned new_max = MAX2(max_handletable * 2, init_num_handles);
        struct handletableentry* new_tab = (struct handletableentry*)::realloc(p_handletable, new_max * sizeof(struct handletableentry));
        assert(new_tab != nullptr, "no more memory for handletable");
        if (new_tab == nullptr) {
          *error_report = "dlopen: no more memory for handletable";
          return nullptr;
        }
        max_handletable = new_max;
        p_handletable = new_tab;
      }
      // Library not yet loaded; load it, then store its handle in handle table
      result = ::dlopen(filename, Flags);
      if (result != nullptr) {
        g_handletable_used++;
        (p_handletable + i)->handle = result;
        (p_handletable + i)->inode = libstat.st_ino;
        (p_handletable + i)->devid = libstat.st_dev;
        (p_handletable + i)->refcount = 1;
      }
      else {
        // error analysis when dlopen fails
        *error_report = ::dlerror();
        if (*error_report == nullptr) {
          *error_report = "dlerror returned no error description";
        }
      }
    }
  }
  return result;
}

bool os::pd_dll_unload(void* libhandle, char* ebuf, int ebuflen) {
  unsigned i = 0;
  bool res = false;

  if (ebuf && ebuflen > 0) {
    ebuf[0] = '\0';
    ebuf[ebuflen - 1] = '\0';
  }

  {
    TableLocker lock;
    // try to find handle in array, which means library was loaded by os::dll_load() call
    for (i = 0; i < g_handletable_used; i++) {
      if ((p_handletable + i)->handle == libhandle) {
        // handle found, decrease refcount
        assert((p_handletable + i)->refcount > 0, "Sanity");
        (p_handletable + i)->refcount--;
        if ((p_handletable + i)->refcount > 0) {
          // if refcount is still >0 then we have to keep library and just return true
          return true;
        }
        // refcount == 0, so we have to ::dlclose() the lib
        // and delete the entry from the array.
        break;
      }
    }

    // If we reach this point either the libhandle was found with refcount == 0, or the libhandle
    // was not found in the array at all. In both cases we have to ::dlclose the lib and perform
    // the error handling. In the first case we then also have to delete the entry from the array
    // while in the second case we simply have to nag.
    res = (0 == ::dlclose(libhandle));
    if (!res) {
      // error analysis when dlopen fails
      const char* error_report = ::dlerror();
      if (error_report == nullptr) {
        error_report = "dlerror returned no error description";
      }
      if (ebuf != nullptr && ebuflen > 0) {
        snprintf(ebuf, ebuflen - 1, "%s", error_report);
      }
      assert(false, "os::pd_dll_unload() ::dlclose() failed");
    }

    if (i < g_handletable_used) {
      if (res) {
        // First case: libhandle was found (with refcount == 0) and ::dlclose successful,
        // so delete entry from array
        g_handletable_used--;
        // If the entry was the last one of the array, the previous g_handletable_used--
        // is sufficient to remove the entry from the array, otherwise we move the last
        // entry of the array to the place of the entry we want to remove and overwrite it
        if (i < g_handletable_used) {
          *(p_handletable + i) = *(p_handletable + g_handletable_used);
          (p_handletable + g_handletable_used)->handle = nullptr;
        }
      }
    }
    else {
      // Second case: libhandle was not found (library was not loaded by os::dll_load())
      // therefore nag
      assert(false, "os::pd_dll_unload() library was not loaded by os::dll_load()");
    }
  }

  // Update the dll cache
  LoadedLibraries::reload();

  return res;
} // end: os::pd_dll_unload()

