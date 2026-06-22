/*
 * Copyright (c) 2020, 2026, Microsoft Corporation. All rights reserved.
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "asm/macroAssembler.hpp"
#include "classfile/vmSymbols.hpp"
#include "code/codeCache.hpp"
#include "code/vtableStubs.hpp"
#include "code/nativeInst.hpp"
#include "cppstdlib/cstdlib.hpp"
#include "interpreter/interpreter.hpp"
#include "jvm.h"
#include "memory/allocation.inline.hpp"
#include "memory/resourceArea.hpp"
#include "os_windows.hpp"
#include "prims/jniFastGetField.hpp"
#include "prims/jvm_misc.hpp"
#include "runtime/arguments.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/java.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/osThread.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/stubRoutines.hpp"
#include "runtime/timer.hpp"
#include "unwind_windows_aarch64.hpp"
#include "utilities/debug.hpp"
#include "utilities/events.hpp"
#include "utilities/vmError.hpp"

// put OS-includes here
# include <sys/types.h>
# include <signal.h>
# include <errno.h>
# include <stdio.h>
# include <intrin.h>

#define REG_BCP X22

// Language-specific exception handler installed for the whole code cache.
LONG HandleException(IN PEXCEPTION_RECORD ExceptionRecord,
    IN ULONG64 EstablisherFrame, IN OUT PCONTEXT ContextRecord,
    IN OUT PDISPATCHER_CONTEXT DispatcherContext) {
  // Vectored exception handling resolves recoverable exceptions at code cache
  // addresses, so if anything reaches here, it is a genuine crash.
  Thread* thread = Thread::current_or_null_safe();
  VMError::report_and_die(thread, ExceptionRecord->ExceptionCode,
                          (address)ContextRecord->Pc, ExceptionRecord, ContextRecord);
  return ExceptionContinueSearch; // only reached under UseOSErrorReporting
}

typedef struct {
  unsigned char ExceptionHandlerInstr[32];
  UNWIND_INFO_EH_ONLY unw;
  UNWIND_INFO_EH_ONLY unw_tail;
} DynamicCodeData, *pDynamicCodeData;

// The .xdata (UNWIND_INFO) record on AArch64 can contain information of a
// function that is just under 1MB in size, since the function length, which
// tracks the size of the function in multiples of 4 bytes, is limited to 18
// bits.  More precisely, the limit is (2^18 - 1) * 4 bytes.
static const uint32_t kMaxFragmentSize = ((1u << 18) - 1) * 4;

static void initialize_unwind_info(PUNWIND_INFO_EH_ONLY unwind_info,
                                   size_t fragment_size,
                                   DWORD handler_rva) {
  guarantee(fragment_size <= kMaxFragmentSize, "invalid code cache fragment size");
  guarantee((fragment_size % 4) == 0, "code cache fragment size must be 4-byte aligned");
  unwind_info->FunctionLength = fragment_size / 4;
  unwind_info->Version        = 0;
  unwind_info->X              = 1;    // exception handler present
  unwind_info->E              = 1;    // single (empty) epilog described inline
  unwind_info->EpilogCount    = 0;
  unwind_info->CodeWords      = 1;
  unwind_info->UnwindCode0    = 0xE4; // "end"
  unwind_info->UnwindCode1    = 0;
  unwind_info->UnwindCode2    = 0;
  unwind_info->UnwindCode3    = 0;
  unwind_info->ExceptionHandler = handler_rva;
}

//
// Register our CodeCache area with the OS so it will dispatch exceptions that
// unwind into our dynamically generated code to HandleException.
//
// Arguments:  low and high are the addresses of the full reserved
// codeCache area.
//
bool os::win32::register_code_area(char *low, char *high) {
  ResourceMark rm;
  const size_t code_size = high - low;
  guarantee((code_size % 4) == 0, "CodeCache size must be 4-byte aligned");

  // Depending on the size of the code cache area, we need a variable number of
  // .pdata records. Instead of allocating and managing a separate area of
  // memory for the .pdata records, we simply allocate space for it after the
  // unwind record, take care that the addresses associated with the .pdata
  // records are 4-byte aligned.
  const uint32_t num_entries = (uint32_t) ((code_size + kMaxFragmentSize - 1) / kMaxFragmentSize);
  const size_t blob_size = align_up(sizeof(DynamicCodeData), sizeof(DWORD)) +
                           num_entries * sizeof(RUNTIME_FUNCTION);
  BufferBlob* blob = BufferBlob::create("CodeCache Exception Handler",
      (uint)blob_size);
  CodeBuffer cb(blob);
  MacroAssembler* masm = new MacroAssembler(&cb);
  pDynamicCodeData pDCD = (pDynamicCodeData) masm->pc();

  // The `HandleException()` function coukd be more than 4GB (32 bits) away from
  // `low`, so we cannot store the relative address of that function in the
  // `ExceptionHandler` field.  As a workaround, we emit a (local) trampoline
  // that tail-calls the `HandleException()` function, while using the relative
  // address of the trampoline to set the `ExceptionHandler` field.  We use
  // `rscratch1` so as to not perturb the callee's arguments in x0 through x3.
  masm->mov(rscratch1, (address)&HandleException);
  masm->br(rscratch1);
  masm->flush();

  // Generally, each .pdata record has an accompanying .xdata record, but if the
  // contents of the .xdata records are the same, then a single .xdata record
  // can be shared by multiple .pdata records.  Since the size of the code cache
  // area might not be a perfect multiple of 1MB and because the function size
  // is stored inside the .xdata record, just two .xdata records suffice: one
  // for the first N-1 records where the function size is 1MB and one for the
  // last record whose size is less than or equal to 1MB.
  PUNWIND_INFO_EH_ONLY full_info = &pDCD->unw;
  PUNWIND_INFO_EH_ONLY tail_info = &pDCD->unw_tail;
  const size_t tail_size = code_size - ((size_t)num_entries - 1) * kMaxFragmentSize;
  const DWORD handler_rva = (char*)&pDCD->ExceptionHandlerInstr[0] - low;
  initialize_unwind_info(full_info, kMaxFragmentSize, handler_rva);
  initialize_unwind_info(tail_info, tail_size, handler_rva);

  // We need one .pdata record for every fragment of the code cache, where each
  // fragment has the size (2^18 - 1) * 4 bytes, except for the final fragment
  // which can be shorter. The table is kept in the BufferBlob so it remains
  // live for the lifetime of the code cache registration.
  PRUNTIME_FUNCTION prt = (PRUNTIME_FUNCTION)align_up((char*)pDCD +
      sizeof(DynamicCodeData), sizeof(DWORD));

  for (uint32_t i = 0; i < num_entries; i++) {
    const bool last = (i == num_entries - 1) && (tail_size != kMaxFragmentSize);
    PUNWIND_INFO_EH_ONLY unwind_info =  last ? tail_info : full_info;
    DWORD unwind_rva = (DWORD)((char*)unwind_info - low);
    guarantee((unwind_rva & 0x3) == 0, ".xdata RVA must be 4-byte aligned");

    prt[i].BeginAddress = i * kMaxFragmentSize;
    prt[i].UnwindData = unwind_rva;
  }

  guarantee(RtlAddFunctionTable(prt, num_entries, (DWORD64)low),
            "Failed to register Dynamic Code Exception Handler with RtlAddFunctionTable");

  return true;
}

void os::os_exception_wrapper(java_call_t f, JavaValue* value, const methodHandle& method, JavaCallArguments* args, JavaThread* thread) {
  f(value, method, args, thread);
}

PRAGMA_DISABLE_MSVC_WARNING(4172)
// Returns an estimate of the current stack pointer. Result must be guaranteed
// to point into the calling threads stack, and be no lower than the current
// stack pointer.
address os::current_stack_pointer() {
  int dummy;
  address sp = (address)&dummy;
  return sp;
}

address os::fetch_frame_from_context(const void* ucVoid,
                    intptr_t** ret_sp, intptr_t** ret_fp) {
  address  epc;
  CONTEXT* uc = (CONTEXT*)ucVoid;

  if (uc != nullptr) {
    epc = (address)uc->Pc;
    if (ret_sp) *ret_sp = (intptr_t*)uc->Sp;
    if (ret_fp) *ret_fp = (intptr_t*)uc->Fp;
  } else {
    // construct empty ExtendedPC for return value checking
    epc = nullptr;
    if (ret_sp) *ret_sp = (intptr_t *)nullptr;
    if (ret_fp) *ret_fp = (intptr_t *)nullptr;
  }
  return epc;
}

frame os::fetch_frame_from_context(const void* ucVoid) {
  intptr_t* sp;
  intptr_t* fp;
  address epc = fetch_frame_from_context(ucVoid, &sp, &fp);
  return frame(sp, fp, epc);
}

#ifdef ASSERT
static bool is_interpreter(const CONTEXT* uc) {
  assert(uc != nullptr, "invariant");
  address pc = reinterpret_cast<address>(uc->Pc);
  assert(pc != nullptr, "invariant");
  return Interpreter::contains(pc);
}
#endif

intptr_t* os::fetch_bcp_from_context(const void* ucVoid) {
  assert(ucVoid != nullptr, "invariant");
  CONTEXT* uc = (CONTEXT*)ucVoid;
  assert(is_interpreter(uc), "invariant");
  return reinterpret_cast<intptr_t*>(uc->REG_BCP);
}

void os::win32::context_set_pc(CONTEXT* uc, address pc) {
  uc->Pc = (intptr_t)pc;
}

bool os::win32::get_frame_at_stack_banging_point(JavaThread* thread,
        struct _EXCEPTION_POINTERS* exceptionInfo, address pc, frame* fr) {
  PEXCEPTION_RECORD exceptionRecord = exceptionInfo->ExceptionRecord;
  address addr = (address) exceptionRecord->ExceptionInformation[1];
  if (Interpreter::contains(pc)) {
    // interpreter performs stack banging after the fixed frame header has
    // been generated while the compilers perform it before. To maintain
    // semantic consistency between interpreted and compiled frames, the
    // method returns the Java sender of the current frame.
    *fr = os::fetch_frame_from_context((void*)exceptionInfo->ContextRecord);
    if (!fr->is_first_java_frame()) {
      assert(fr->safe_for_sender(thread), "Safety check");
      *fr = fr->java_sender();
    }
  } else {
    // more complex code with compiled code
    assert(!Interpreter::contains(pc), "Interpreted methods should have been handled above");
    CodeBlob* cb = CodeCache::find_blob(pc);
    if (cb == nullptr || !cb->is_nmethod() || cb->is_frame_complete_at(pc)) {
      // Not sure where the pc points to, fallback to default
      // stack overflow handling
      return false;
    } else {
      // In compiled code, the stack banging is performed before LR
      // has been saved in the frame.  LR is live, and SP and FP
      // belong to the caller.
      intptr_t* fp = (intptr_t*)exceptionInfo->ContextRecord->Fp;
      intptr_t* sp = (intptr_t*)exceptionInfo->ContextRecord->Sp;
      address pc = (address)(exceptionInfo->ContextRecord->Lr
                         - NativeInstruction::instruction_size);
      *fr = frame(sp, fp, pc);
      if (!fr->is_java_frame()) {
        assert(fr->safe_for_sender(thread), "Safety check");
        assert(!fr->is_first_frame(), "Safety check");
        *fr = fr->java_sender();
      }
    }
  }
  assert(fr->is_java_frame(), "Safety check");
  return true;
}

frame os::get_sender_for_C_frame(frame* fr) {
  ShouldNotReachHere();
  return frame();
}

frame os::current_frame() {
  return frame();  // cannot walk Windows frames this way.  See os::get_native_stack
                   // and os::platform_print_native_stack
}

////////////////////////////////////////////////////////////////////////////////
// thread stack

// Minimum usable stack sizes required to get to user code. Space for
// HotSpot guard pages is added later.

/////////////////////////////////////////////////////////////////////////////
// helper functions for fatal error handler

void os::print_context(outputStream *st, const void *context) {
  if (context == nullptr) return;

  const CONTEXT* uc = (const CONTEXT*)context;

  st->print_cr("Registers:");

  st->print(  "X0 =" INTPTR_FORMAT, uc->X0);
  st->print(", X1 =" INTPTR_FORMAT, uc->X1);
  st->print(", X2 =" INTPTR_FORMAT, uc->X2);
  st->print(", X3 =" INTPTR_FORMAT, uc->X3);
  st->cr();
  st->print(  "X4 =" INTPTR_FORMAT, uc->X4);
  st->print(", X5 =" INTPTR_FORMAT, uc->X5);
  st->print(", X6 =" INTPTR_FORMAT, uc->X6);
  st->print(", X7 =" INTPTR_FORMAT, uc->X7);
  st->cr();
  st->print(  "X8 =" INTPTR_FORMAT, uc->X8);
  st->print(", X9 =" INTPTR_FORMAT, uc->X9);
  st->print(", X10=" INTPTR_FORMAT, uc->X10);
  st->print(", X11=" INTPTR_FORMAT, uc->X11);
  st->cr();
  st->print(  "X12=" INTPTR_FORMAT, uc->X12);
  st->print(", X13=" INTPTR_FORMAT, uc->X13);
  st->print(", X14=" INTPTR_FORMAT, uc->X14);
  st->print(", X15=" INTPTR_FORMAT, uc->X15);
  st->cr();
  st->print(  "X16=" INTPTR_FORMAT, uc->X16);
  st->print(", X17=" INTPTR_FORMAT, uc->X17);
  st->print(", X18=" INTPTR_FORMAT, uc->X18);
  st->print(", X19=" INTPTR_FORMAT, uc->X19);
  st->cr();
  st->print(", X20=" INTPTR_FORMAT, uc->X20);
  st->print(", X21=" INTPTR_FORMAT, uc->X21);
  st->print(", X22=" INTPTR_FORMAT, uc->X22);
  st->print(", X23=" INTPTR_FORMAT, uc->X23);
  st->cr();
  st->print(", X24=" INTPTR_FORMAT, uc->X24);
  st->print(", X25=" INTPTR_FORMAT, uc->X25);
  st->print(", X26=" INTPTR_FORMAT, uc->X26);
  st->print(", X27=" INTPTR_FORMAT, uc->X27);
  st->print(", X28=" INTPTR_FORMAT, uc->X28);
  st->cr();
  st->cr();
}

void os::print_register_info(outputStream *st, const void *context, int& continuation) {
  const int register_count = 29 /* X0-X28 */;
  int n = continuation;
  assert(n >= 0 && n <= register_count, "Invalid continuation value");
  if (context == nullptr || n == register_count) {
    return;
  }

  const CONTEXT* uc = (const CONTEXT*)context;
  while (n < register_count) {
    // Update continuation with next index before printing location
    continuation = n + 1;
# define CASE_PRINT_REG(n, str, id) case n: st->print(str); print_location(st, uc->id);
    switch (n) {
      CASE_PRINT_REG( 0, " X0=", X0); break;
      CASE_PRINT_REG( 1, " X1=", X1); break;
      CASE_PRINT_REG( 2, " X2=", X2); break;
      CASE_PRINT_REG( 3, " X3=", X3); break;
      CASE_PRINT_REG( 4, " X4=", X4); break;
      CASE_PRINT_REG( 5, " X5=", X5); break;
      CASE_PRINT_REG( 6, " X6=", X6); break;
      CASE_PRINT_REG( 7, " X7=", X7); break;
      CASE_PRINT_REG( 8, " X8=", X8); break;
      CASE_PRINT_REG( 9, " X9=", X9); break;
      CASE_PRINT_REG(10, "X10=", X10); break;
      CASE_PRINT_REG(11, "X11=", X11); break;
      CASE_PRINT_REG(12, "X12=", X12); break;
      CASE_PRINT_REG(13, "X13=", X13); break;
      CASE_PRINT_REG(14, "X14=", X14); break;
      CASE_PRINT_REG(15, "X15=", X15); break;
      CASE_PRINT_REG(16, "X16=", X16); break;
      CASE_PRINT_REG(17, "X17=", X17); break;
      CASE_PRINT_REG(18, "X18=", X18); break;
      CASE_PRINT_REG(19, "X19=", X19); break;
      CASE_PRINT_REG(20, "X20=", X20); break;
      CASE_PRINT_REG(21, "X21=", X21); break;
      CASE_PRINT_REG(22, "X22=", X22); break;
      CASE_PRINT_REG(23, "X23=", X23); break;
      CASE_PRINT_REG(24, "X24=", X24); break;
      CASE_PRINT_REG(25, "X25=", X25); break;
      CASE_PRINT_REG(26, "X26=", X26); break;
      CASE_PRINT_REG(27, "X27=", X27); break;
      CASE_PRINT_REG(28, "X28=", X28); break;
    }
# undef CASE_PRINT_REG
    ++n;
  }
}

void os::setup_fpu() {
}

#ifndef PRODUCT
void os::verify_stack_alignment() {
  assert(((intptr_t)os::current_stack_pointer() & (StackAlignmentInBytes-1)) == 0, "incorrect stack alignment");
}
#endif

int os::extra_bang_size_in_bytes() {
  // AArch64 does not require the additional stack bang.
  return 0;
}

extern "C" {
  int SpinPause() {
    return 0;
  }
};
