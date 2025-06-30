/*
 * Copyright (c) 1999, 2025, Oracle and/or its affiliates. All rights reserved.
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
#include "code/vtableStubs.hpp"
#include "interpreter/interpreter.hpp"
#include "jvm.h"
#include "memory/allocation.inline.hpp"
#include "memory/resourceArea.hpp"
#include "nativeInst_x86.hpp"
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
#include "runtime/os.inline.hpp"
#include "runtime/osThread.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/stubRoutines.hpp"
#include "runtime/timer.hpp"
#include "symbolengine.hpp"
#include "unwind_windows_x86.hpp"
#include "utilities/events.hpp"
#include "utilities/vmError.hpp"
#include "windbghelp.hpp"


#undef REG_SP
#undef REG_FP
#undef REG_PC
#define REG_SP Rsp
#define REG_FP Rbp
#define REG_PC Rip
#define REG_BCP R13

JNIEXPORT
extern LONG WINAPI topLevelExceptionFilter(_EXCEPTION_POINTERS* );

// Install a win32 structured exception handler around thread.
void os::os_exception_wrapper(java_call_t f, JavaValue* value, const methodHandle& method, JavaCallArguments* args, JavaThread* thread) {
  __try {
    f(value, method, args, thread);
  } __except(topLevelExceptionFilter((_EXCEPTION_POINTERS*)_exception_info())) {
      // Nothing to do.
  }
}

// This is the language specific handler for exceptions
// originating from dynamically generated code.
// We call the standard structured exception handler
// We only expect Continued Execution since we cannot unwind
// from generated code.
LONG HandleExceptionFromCodeCache(
  IN PEXCEPTION_RECORD ExceptionRecord,
  IN ULONG64 EstablisherFrame,
  IN OUT PCONTEXT ContextRecord,
  IN OUT PDISPATCHER_CONTEXT DispatcherContext) {
  EXCEPTION_POINTERS ep;
  LONG result;

  ep.ExceptionRecord = ExceptionRecord;
  ep.ContextRecord = ContextRecord;

  result = topLevelExceptionFilter(&ep);

  // We better only get a CONTINUE_EXECUTION from our handler
  // since we don't have unwind information registered.

  guarantee( result == EXCEPTION_CONTINUE_EXECUTION,
             "Unexpected result from topLevelExceptionFilter");

  return(ExceptionContinueExecution);
}


// Structure containing the Windows Data Structures required
// to register our Code Cache exception handler.
// We put these in the CodeCache since the API requires
// all addresses in these structures are relative to the Code
// area registered with RtlAddFunctionTable.
typedef struct {
  char ExceptionHandlerInstr[16];  // jmp HandleExceptionFromCodeCache
  RUNTIME_FUNCTION rt;
  UNWIND_INFO_EH_ONLY unw;
} DynamicCodeData, *pDynamicCodeData;

//
// Register our CodeCache area with the OS so it will dispatch exceptions
// to our topLevelExceptionFilter when we take an exception in our
// dynamically generated code.
//
// Arguments:  low and high are the address of the full reserved
// codeCache area
//
bool os::win32::register_code_area(char *low, char *high) {
  ResourceMark rm;

  pDynamicCodeData pDCD;
  PRUNTIME_FUNCTION prt;
  PUNWIND_INFO_EH_ONLY punwind;

  BufferBlob* blob = BufferBlob::create("CodeCache Exception Handler", sizeof(DynamicCodeData));
  CodeBuffer cb(blob);
  MacroAssembler* masm = new MacroAssembler(&cb);
  pDCD = (pDynamicCodeData) masm->pc();

  masm->jump(RuntimeAddress((address)&HandleExceptionFromCodeCache), rscratch1);
  masm->flush();

  // Create an Unwind Structure specifying no unwind info
  // other than an Exception Handler
  punwind = &pDCD->unw;
  punwind->Version = 1;
  punwind->Flags = UNW_FLAG_EHANDLER;
  punwind->SizeOfProlog = 0;
  punwind->CountOfCodes = 0;
  punwind->FrameRegister = 0;
  punwind->FrameOffset = 0;
  punwind->ExceptionHandler = (char *)(&(pDCD->ExceptionHandlerInstr[0])) -
                              (char*)low;
  punwind->ExceptionData[0] = 0;

  // This structure describes the covered dynamic code area.
  // Addresses are relative to the beginning on the code cache area
  prt = &pDCD->rt;
  prt->BeginAddress = 0;
  prt->EndAddress = (ULONG)(high - low);
  prt->UnwindData = ((char *)punwind - low);

  guarantee(RtlAddFunctionTable(prt, 1, (ULONGLONG)low),
            "Failed to register Dynamic Code Exception Handler with RtlAddFunctionTable");

  return true;
}

#if defined(_M_AMD64)
//-----------------------------------------------------------------------------
bool handle_FLT_exception(struct _EXCEPTION_POINTERS* exceptionInfo) {
  // handle exception caused by native method modifying control word
  DWORD exception_code = exceptionInfo->ExceptionRecord->ExceptionCode;

  switch (exception_code) {
  case EXCEPTION_FLT_DENORMAL_OPERAND:
  case EXCEPTION_FLT_DIVIDE_BY_ZERO:
  case EXCEPTION_FLT_INEXACT_RESULT:
  case EXCEPTION_FLT_INVALID_OPERATION:
  case EXCEPTION_FLT_OVERFLOW:
  case EXCEPTION_FLT_STACK_CHECK:
  case EXCEPTION_FLT_UNDERFLOW: {
    PCONTEXT ctx = exceptionInfo->ContextRecord;
    // On Windows, the mxcsr control bits are non-volatile across calls
    // See also CR 6192333
    //
    jint MxCsr = INITIAL_MXCSR; // set to 0x1f80` in winnt.h
    if (EnableX86ECoreOpts) {
      // On ECore restore with status bits enabled
      MxCsr |= 0x3F;
    }

    // we can't use StubRoutines::x86::addr_mxcsr_std()
    // because in Win64 mxcsr is not saved there
    if (MxCsr != ctx->MxCsr) {
      ctx->MxCsr = MxCsr;
      return true;
    }
  }
  }

  return false;
}
#endif

#ifdef HAVE_PLATFORM_PRINT_NATIVE_STACK
/*
 * Windows/x64 does not use stack frames the way expected by Java:
 * [1] in most cases, there is no frame pointer. All locals are addressed via RSP
 * [2] in rare cases, when alloca() is used, a frame pointer is used, but this may
 *     not be RBP.
 * See http://msdn.microsoft.com/en-us/library/ew5tede7.aspx
 *
 * So it's not possible to print the native stack using the
 *     while (...) {...  fr = os::get_sender_for_C_frame(&fr); }
 * loop in vmError.cpp. We need to roll our own loop.
 */
bool os::win32::platform_print_native_stack(outputStream* st, const void* context,
                                            char *buf, int buf_size, address& lastpc)
{
  CONTEXT ctx;
  if (context != nullptr) {
    memcpy(&ctx, context, sizeof(ctx));
  } else {
    RtlCaptureContext(&ctx);
  }

  st->print_cr("Native frames: (J=compiled Java code, j=interpreted, Vv=VM code, C=native code)");

  STACKFRAME stk;
  memset(&stk, 0, sizeof(stk));
  stk.AddrStack.Offset    = ctx.Rsp;
  stk.AddrStack.Mode      = AddrModeFlat;
  stk.AddrFrame.Offset    = ctx.Rbp;
  stk.AddrFrame.Mode      = AddrModeFlat;
  stk.AddrPC.Offset       = ctx.Rip;
  stk.AddrPC.Mode         = AddrModeFlat;

  // Ensure we consider dynamically loaded dll's
  SymbolEngine::refreshModuleList();

  int count = 0;
  address lastpc_internal = 0;
  while (count++ < StackPrintLimit) {
    intptr_t* sp = (intptr_t*)stk.AddrStack.Offset;
    intptr_t* fp = (intptr_t*)stk.AddrFrame.Offset; // NOT necessarily the same as ctx.Rbp!
    address pc = (address)stk.AddrPC.Offset;

    if (pc != nullptr) {
      if (count == 2 && lastpc_internal == pc) {
        // Skip it -- StackWalk64() may return the same PC
        // (but different SP) on the first try.
      } else {
        // Don't try to create a frame(sp, fp, pc) -- on WinX64, stk.AddrFrame
        // may not contain what Java expects, and may cause the frame() constructor
        // to crash. Let's just print out the symbolic address.
        frame::print_C_frame(st, buf, buf_size, pc);
        // print source file and line, if available
        char buf[128];
        int line_no;
        if (SymbolEngine::get_source_info(pc, buf, sizeof(buf), &line_no)) {
          st->print("  (%s:%d)", buf, line_no);
        } else {
          st->print("  (no source info available)");
        }
        st->cr();
      }
      lastpc_internal = pc;
    }

    PVOID p = WindowsDbgHelp::symFunctionTableAccess64(GetCurrentProcess(), stk.AddrPC.Offset);
    if (!p) {
      // StackWalk64() can't handle this PC. Calling StackWalk64 again may cause crash.
      lastpc = lastpc_internal;
      break;
    }

    BOOL result = WindowsDbgHelp::stackWalk64(
        IMAGE_FILE_MACHINE_AMD64,  // __in      DWORD MachineType,
        GetCurrentProcess(),       // __in      HANDLE hProcess,
        GetCurrentThread(),        // __in      HANDLE hThread,
        &stk,                      // __inout   LP STACKFRAME64 StackFrame,
        &ctx);                     // __inout   PVOID ContextRecord,

    if (!result) {
      break;
    }
  }
  if (count > StackPrintLimit) {
    st->print_cr("...<more frames>...");
  }
  st->cr();

  return true;
}
#endif // HAVE_PLATFORM_PRINT_NATIVE_STACK

address os::fetch_frame_from_context(const void* ucVoid,
                    intptr_t** ret_sp, intptr_t** ret_fp) {

  address  epc;
  CONTEXT* uc = (CONTEXT*)ucVoid;

  if (uc != nullptr) {
    epc = (address)uc->REG_PC;
    if (ret_sp) *ret_sp = (intptr_t*)uc->REG_SP;
    if (ret_fp) *ret_fp = (intptr_t*)uc->REG_FP;
  } else {
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
  if (!is_readable_pointer(epc)) {
    // Try to recover from calling into bad memory
    // Assume new frame has not been set up, the same as
    // compiled frame stack bang
    return frame(sp + 1, fp, (address)*sp);
  }
  return frame(sp, fp, epc);
}

#ifdef ASSERT
static bool is_interpreter(const CONTEXT* uc) {
  assert(uc != nullptr, "invariant");
  address pc = reinterpret_cast<address>(uc->REG_PC);
  assert(pc != nullptr, "invariant");
  return Interpreter::contains(pc);
}
#endif

intptr_t* os::fetch_bcp_from_context(const void* ucVoid) {
  assert(ucVoid != nullptr, "invariant");
  const CONTEXT* const uc = (CONTEXT*)ucVoid;
  assert(is_interpreter(uc), "invariant");
  return reinterpret_cast<intptr_t*>(uc->REG_BCP);
}

// Returns the current stack pointer. Accurate value needed for
// os::verify_stack_alignment().
address os::current_stack_pointer() {
  typedef address get_sp_func();
  get_sp_func* func = CAST_TO_FN_PTR(get_sp_func*,
                                     StubRoutines::x86::get_previous_sp_entry());
  return (*func)();
}

bool os::win32::get_frame_at_stack_banging_point(JavaThread* thread,
        struct _EXCEPTION_POINTERS* exceptionInfo, address pc, frame* fr) {
  PEXCEPTION_RECORD exceptionRecord = exceptionInfo->ExceptionRecord;
  address addr = (address) exceptionRecord->ExceptionInformation[1];
  if (Interpreter::contains(pc)) {
    *fr = os::fetch_frame_from_context((void*)exceptionInfo->ContextRecord);
    if (!fr->is_first_java_frame()) {
      // get_frame_at_stack_banging_point() is only called when we
      // have well defined stacks so java_sender() calls do not need
      // to assert safe_for_sender() first.
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
      // in compiled code, the stack banging is performed just after the return pc
      // has been pushed on the stack
      intptr_t* fp = (intptr_t*)exceptionInfo->ContextRecord->REG_FP;
      intptr_t* sp = (intptr_t*)exceptionInfo->ContextRecord->REG_SP;
      *fr = frame(sp + 1, fp, (address)*sp);
      if (!fr->is_java_frame()) {
        // See java_sender() comment above.
        *fr = fr->java_sender();
      }
    }
  }
  assert(fr->is_java_frame(), "Safety check");
  return true;
}


// VC++ does not save frame pointer on stack in optimized build. It
// can be turned off by /Oy-. If we really want to walk C frames,
// we can use the StackWalk() API.
frame os::get_sender_for_C_frame(frame* fr) {
  ShouldNotReachHere();
  return frame();
}

frame os::current_frame() {
  return frame();  // cannot walk Windows frames this way.  See os::get_native_stack
                   // and os::platform_print_native_stack
}

void os::print_context(outputStream *st, const void *context) {
  if (context == nullptr) return;

  const CONTEXT* uc = (const CONTEXT*)context;

  st->print_cr("Registers:");
  st->print(  "RAX=" INTPTR_FORMAT, uc->Rax);
  st->print(", RBX=" INTPTR_FORMAT, uc->Rbx);
  st->print(", RCX=" INTPTR_FORMAT, uc->Rcx);
  st->print(", RDX=" INTPTR_FORMAT, uc->Rdx);
  st->cr();
  st->print(  "RSP=" INTPTR_FORMAT, uc->Rsp);
  st->print(", RBP=" INTPTR_FORMAT, uc->Rbp);
  st->print(", RSI=" INTPTR_FORMAT, uc->Rsi);
  st->print(", RDI=" INTPTR_FORMAT, uc->Rdi);
  st->cr();
  st->print(  "R8 =" INTPTR_FORMAT, uc->R8);
  st->print(", R9 =" INTPTR_FORMAT, uc->R9);
  st->print(", R10=" INTPTR_FORMAT, uc->R10);
  st->print(", R11=" INTPTR_FORMAT, uc->R11);
  st->cr();
  st->print(  "R12=" INTPTR_FORMAT, uc->R12);
  st->print(", R13=" INTPTR_FORMAT, uc->R13);
  st->print(", R14=" INTPTR_FORMAT, uc->R14);
  st->print(", R15=" INTPTR_FORMAT, uc->R15);
  st->cr();
  st->print(  "RIP=" INTPTR_FORMAT, uc->Rip);
  st->print(", EFLAGS=" INTPTR_FORMAT, uc->EFlags);
  // Add XMM registers + MXCSR. Note that C2 uses XMM to spill GPR values including pointers.
  st->cr();
  st->cr();
  for (int i = 0; i < 16; ++i) {
    const uint64_t *xmm = ((const uint64_t*)&(uc->Xmm0)) + 2 * i;
    st->print_cr("XMM[%d]=" INTPTR_FORMAT " " INTPTR_FORMAT,
                 i, xmm[1], xmm[0]);
  }
  st->print("  MXCSR=" UINT32_FORMAT_X_0, uc->MxCsr);
  st->cr();
  st->cr();
}

void os::print_register_info(outputStream *st, const void *context, int& continuation) {
  const int register_count = 16;
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
    CASE_PRINT_REG( 0, "RAX=", Rax); break;
    CASE_PRINT_REG( 1, "RBX=", Rbx); break;
    CASE_PRINT_REG( 2, "RCX=", Rcx); break;
    CASE_PRINT_REG( 3, "RDX=", Rdx); break;
    CASE_PRINT_REG( 4, "RSP=", Rsp); break;
    CASE_PRINT_REG( 5, "RBP=", Rbp); break;
    CASE_PRINT_REG( 6, "RSI=", Rsi); break;
    CASE_PRINT_REG( 7, "RDI=", Rdi); break;
    CASE_PRINT_REG( 8, "R8 =", R8); break;
    CASE_PRINT_REG( 9, "R9 =", R9); break;
    CASE_PRINT_REG(10, "R10=", R10); break;
    CASE_PRINT_REG(11, "R11=", R11); break;
    CASE_PRINT_REG(12, "R12=", R12); break;
    CASE_PRINT_REG(13, "R13=", R13); break;
    CASE_PRINT_REG(14, "R14=", R14); break;
    CASE_PRINT_REG(15, "R15=", R15); break;
    }
# undef CASE_PRINT_REG
    ++n;
  }
}

extern "C" int SpinPause () {
   return 0 ;
}

juint os::cpu_microcode_revision() {
  juint result = 0;
  BYTE data[8] = {0};
  HKEY key;
  DWORD status = RegOpenKey(HKEY_LOCAL_MACHINE,
               "HARDWARE\\DESCRIPTION\\System\\CentralProcessor\\0", &key);
  if (status == ERROR_SUCCESS) {
    DWORD size = sizeof(data);
    status = RegQueryValueEx(key, "Update Revision", nullptr, nullptr, data, &size);
    if (status == ERROR_SUCCESS) {
      if (size == 4) result = *((juint*)data);
      if (size == 8) result = *((juint*)data + 1); // upper 32-bits
    }
    RegCloseKey(key);
  }
  return result;
}

void os::setup_fpu() {
}

#ifndef PRODUCT
void os::verify_stack_alignment() {
  // The current_stack_pointer() calls generated get_previous_sp stub routine.
  // Only enable the assert after the routine becomes available.
  if (StubRoutines::initial_stubs_code() != nullptr) {
    assert(((intptr_t)os::current_stack_pointer() & (StackAlignmentInBytes-1)) == 0, "incorrect stack alignment");
  }
}
#endif

int os::extra_bang_size_in_bytes() {
  // JDK-8050147 requires the full cache line bang for x86.
  return VM_Version::L1_line_size();
}
