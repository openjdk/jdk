/*
 * Copyright (c) 1997, 2015, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "asm/macroAssembler.hpp"
#include "interpreter/bytecodeHistogram.hpp"
#include "interpreter/interpreter.hpp"
#include "interpreter/interpreterRuntime.hpp"
#include "interpreter/interp_masm.hpp"
#include "interpreter/templateInterpreterGenerator.hpp"
#include "interpreter/templateTable.hpp"
#include "oops/arrayOop.hpp"
#include "oops/methodData.hpp"
#include "oops/method.hpp"
#include "oops/oop.inline.hpp"
#include "prims/jvmtiExport.hpp"
#include "prims/jvmtiThreadState.hpp"
#include "prims/methodHandles.hpp"
#include "runtime/arguments.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/stubRoutines.hpp"
#include "runtime/synchronizer.hpp"
#include "runtime/timer.hpp"
#include "runtime/vframeArray.hpp"
#include "utilities/debug.hpp"
#ifdef COMPILER1
#include "c1/c1_Runtime1.hpp"
#endif

#define __ _masm->

//------------------------------------------------------------------------------------------------------------------------

address AbstractInterpreterGenerator::generate_slow_signature_handler() {
  address entry = __ pc();
  // rbx,: method
  // rcx: temporary
  // rdi: pointer to locals
  // rsp: end of copied parameters area
  __ mov(rcx, rsp);
  __ call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::slow_signature_handler), rbx, rdi, rcx);
  __ ret(0);
  return entry;
}


address TemplateInterpreterGenerator::generate_math_entry(AbstractInterpreter::MethodKind kind) {

  // rbx,: Method*
  // rcx: scratrch
  // rsi: sender sp

  if (!InlineIntrinsics) return NULL; // Generate a vanilla entry

  address entry_point = __ pc();

  // These don't need a safepoint check because they aren't virtually
  // callable. We won't enter these intrinsics from compiled code.
  // If in the future we added an intrinsic which was virtually callable
  // we'd have to worry about how to safepoint so that this code is used.

  // mathematical functions inlined by compiler
  // (interpreter must provide identical implementation
  // in order to avoid monotonicity bugs when switching
  // from interpreter to compiler in the middle of some
  // computation)
  //
  // stack: [ ret adr ] <-- rsp
  //        [ lo(arg) ]
  //        [ hi(arg) ]
  //

  // Note: For JDK 1.2 StrictMath doesn't exist and Math.sin/cos/sqrt are
  //       native methods. Interpreter::method_kind(...) does a check for
  //       native methods first before checking for intrinsic methods and
  //       thus will never select this entry point. Make sure it is not
  //       called accidentally since the SharedRuntime entry points will
  //       not work for JDK 1.2.
  //
  // We no longer need to check for JDK 1.2 since it's EOL'ed.
  // The following check existed in pre 1.6 implementation,
  //    if (Universe::is_jdk12x_version()) {
  //      __ should_not_reach_here();
  //    }
  // Universe::is_jdk12x_version() always returns false since
  // the JDK version is not yet determined when this method is called.
  // This method is called during interpreter_init() whereas
  // JDK version is only determined when universe2_init() is called.

  // Note: For JDK 1.3 StrictMath exists and Math.sin/cos/sqrt are
  //       java methods.  Interpreter::method_kind(...) will select
  //       this entry point for the corresponding methods in JDK 1.3.
  // get argument
  __ fld_d(Address(rsp, 1*wordSize));
  switch (kind) {
    case Interpreter::java_lang_math_sin :
        __ trigfunc('s');
        break;
    case Interpreter::java_lang_math_cos :
        __ trigfunc('c');
        break;
    case Interpreter::java_lang_math_tan :
        __ trigfunc('t');
        break;
    case Interpreter::java_lang_math_sqrt:
        __ fsqrt();
        break;
    case Interpreter::java_lang_math_abs:
        __ fabs();
        break;
    case Interpreter::java_lang_math_log:
        __ subptr(rsp, 2 * wordSize);
        __ fstp_d(Address(rsp, 0));
        if (VM_Version::supports_sse2()) {
          __ call(RuntimeAddress(CAST_FROM_FN_PTR(address, StubRoutines::dlog())));
        }
        else {
          __ call(RuntimeAddress(CAST_FROM_FN_PTR(address, SharedRuntime::dlog)));
        }
        __ addptr(rsp, 2 * wordSize);
        break;
    case Interpreter::java_lang_math_log10:
        __ flog10();
        // Store to stack to convert 80bit precision back to 64bits
        __ push_fTOS();
        __ pop_fTOS();
        break;
    case Interpreter::java_lang_math_pow:
      __ fld_d(Address(rsp, 3*wordSize)); // second argument
      __ pow_with_fallback(0);
      // Store to stack to convert 80bit precision back to 64bits
      __ push_fTOS();
      __ pop_fTOS();
      break;
    case Interpreter::java_lang_math_exp:
      __ subptr(rsp, 2*wordSize);
      __ fstp_d(Address(rsp, 0));
      if (VM_Version::supports_sse2()) {
        __ call(RuntimeAddress(CAST_FROM_FN_PTR(address, StubRoutines::dexp())));
      } else {
        __ call(RuntimeAddress(CAST_FROM_FN_PTR(address, SharedRuntime::dexp)));
      }
      __ addptr(rsp, 2*wordSize);
    break;
    default                              :
        ShouldNotReachHere();
  }

  // return double result in xmm0 for interpreter and compilers.
  if (UseSSE >= 2) {
    __ subptr(rsp, 2*wordSize);
    __ fstp_d(Address(rsp, 0));
    __ movdbl(xmm0, Address(rsp, 0));
    __ addptr(rsp, 2*wordSize);
  }

  // done, result in FPU ST(0) or XMM0
  __ pop(rdi);                               // get return address
  __ mov(rsp, rsi);                          // set sp to sender sp
  __ jmp(rdi);

  return entry_point;
}
