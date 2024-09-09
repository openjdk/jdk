/*
 * Copyright (c) 1998, 2023, Oracle and/or its affiliates. All rights reserved.
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
#ifdef COMPILER2
#include "asm/macroAssembler.hpp"
#include "asm/macroAssembler.inline.hpp"
#include "code/vmreg.hpp"
#include "compiler/oopMap.hpp"
#include "interpreter/interpreter.hpp"
#include "memory/resourceArea.hpp"
#include "opto/runtime.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/stubRoutines.hpp"
#include "runtime/vframeArray.hpp"
#include "utilities/globalDefinitions.hpp"
#include "vmreg_x86.inline.hpp"
#endif


#define __ masm->

//------------------------------generate_uncommon_trap_blob--------------------
void OptoRuntime::generate_uncommon_trap_blob() {
  // allocate space for the code
  ResourceMark rm;
  // setup code generation tools
  CodeBuffer   buffer("uncommon_trap_blob", 512, 512);
  MacroAssembler* masm = new MacroAssembler(&buffer);

  enum frame_layout {
    arg0_off,      // thread                     sp + 0 // Arg location for
    arg1_off,      // unloaded_class_index       sp + 1 // calling C
    arg2_off,      // exec_mode                  sp + 2
    // The frame sender code expects that rbp will be in the "natural" place and
    // will override any oopMap setting for it. We must therefore force the layout
    // so that it agrees with the frame sender code.
    rbp_off,       // callee saved register      sp + 3
    return_off,    // slot for return address    sp + 4
    framesize
  };

  address start = __ pc();

  // Push self-frame.
  __ subptr(rsp, return_off*wordSize);     // Epilog!

  // rbp, is an implicitly saved callee saved register (i.e. the calling
  // convention will save restore it in prolog/epilog) Other than that
  // there are no callee save registers no that adapter frames are gone.
  __ movptr(Address(rsp, rbp_off*wordSize), rbp);

  // Clear the floating point exception stack
  __ empty_FPU_stack();

  // set last_Java_sp
  __ get_thread(rdx);
  __ set_last_Java_frame(rdx, noreg, noreg, nullptr, noreg);

  // Call C code.  Need thread but NOT official VM entry
  // crud.  We cannot block on this call, no GC can happen.  Call should
  // capture callee-saved registers as well as return values.
  __ movptr(Address(rsp, arg0_off*wordSize), rdx);
  // argument already in ECX
  __ movl(Address(rsp, arg1_off*wordSize),rcx);
  __ movl(Address(rsp, arg2_off*wordSize), Deoptimization::Unpack_uncommon_trap);
  __ call(RuntimeAddress(CAST_FROM_FN_PTR(address, Deoptimization::uncommon_trap)));

  // Set an oopmap for the call site
  OopMapSet *oop_maps = new OopMapSet();
  OopMap* map =  new OopMap( framesize, 0 );
  // No oopMap for rbp, it is known implicitly

  oop_maps->add_gc_map( __ pc()-start, map);

  __ get_thread(rcx);

  __ reset_last_Java_frame(rcx, false);

  // Load UnrollBlock into EDI
  __ movptr(rdi, rax);

#ifdef ASSERT
  { Label L;
    __ cmpptr(Address(rdi, Deoptimization::UnrollBlock::unpack_kind_offset()),
            (int32_t)Deoptimization::Unpack_uncommon_trap);
    __ jcc(Assembler::equal, L);
    __ stop("OptoRuntime::generate_uncommon_trap_blob: expected Unpack_uncommon_trap");
    __ bind(L);
  }
#endif

  // Pop all the frames we must move/replace.
  //
  // Frame picture (youngest to oldest)
  // 1: self-frame (no frame link)
  // 2: deopting frame  (no frame link)
  // 3: caller of deopting frame (could be compiled/interpreted).

  // Pop self-frame.  We have no frame, and must rely only on EAX and ESP.
  __ addptr(rsp,(framesize-1)*wordSize);     // Epilog!

  // Pop deoptimized frame
  __ movl2ptr(rcx, Address(rdi,Deoptimization::UnrollBlock::size_of_deoptimized_frame_offset()));
  __ addptr(rsp, rcx);

  // sp should be pointing at the return address to the caller (3)

  // Pick up the initial fp we should save
  // restore rbp before stack bang because if stack overflow is thrown it needs to be pushed (and preserved)
  __ movptr(rbp, Address(rdi, Deoptimization::UnrollBlock::initial_info_offset()));

#ifdef ASSERT
  // Compilers generate code that bang the stack by as much as the
  // interpreter would need. So this stack banging should never
  // trigger a fault. Verify that it does not on non product builds.
  __ movl(rbx, Address(rdi ,Deoptimization::UnrollBlock::total_frame_sizes_offset()));
  __ bang_stack_size(rbx, rcx);
#endif

  // Load array of frame pcs into ECX
  __ movl(rcx,Address(rdi,Deoptimization::UnrollBlock::frame_pcs_offset()));

  __ pop(rsi); // trash the pc

  // Load array of frame sizes into ESI
  __ movptr(rsi,Address(rdi,Deoptimization::UnrollBlock::frame_sizes_offset()));

  Address counter(rdi, Deoptimization::UnrollBlock::counter_temp_offset());

  __ movl(rbx, Address(rdi, Deoptimization::UnrollBlock::number_of_frames_offset()));
  __ movl(counter, rbx);

  // Now adjust the caller's stack to make up for the extra locals
  // but record the original sp so that we can save it in the skeletal interpreter
  // frame and the stack walking of interpreter_sender will get the unextended sp
  // value and not the "real" sp value.

  Address sp_temp(rdi, Deoptimization::UnrollBlock::sender_sp_temp_offset());
  __ movptr(sp_temp, rsp);
  __ movl(rbx, Address(rdi, Deoptimization::UnrollBlock::caller_adjustment_offset()));
  __ subptr(rsp, rbx);

  // Push interpreter frames in a loop
  Label loop;
  __ bind(loop);
  __ movptr(rbx, Address(rsi, 0));      // Load frame size
  __ subptr(rbx, 2*wordSize);           // we'll push pc and rbp, by hand
  __ pushptr(Address(rcx, 0));          // save return address
  __ enter();                           // save old & set new rbp,
  __ subptr(rsp, rbx);                  // Prolog!
  __ movptr(rbx, sp_temp);              // sender's sp
  // This value is corrected by layout_activation_impl
  __ movptr(Address(rbp, frame::interpreter_frame_last_sp_offset * wordSize), NULL_WORD );
  __ movptr(Address(rbp, frame::interpreter_frame_sender_sp_offset * wordSize), rbx); // Make it walkable
  __ movptr(sp_temp, rsp);              // pass to next frame
  __ addptr(rsi, wordSize);             // Bump array pointer (sizes)
  __ addptr(rcx, wordSize);             // Bump array pointer (pcs)
  __ decrementl(counter);             // decrement counter
  __ jcc(Assembler::notZero, loop);
  __ pushptr(Address(rcx, 0));            // save final return address

  // Re-push self-frame
  __ enter();                           // save old & set new rbp,
  __ subptr(rsp, (framesize-2) * wordSize);   // Prolog!


  // set last_Java_sp, last_Java_fp
  __ get_thread(rdi);
  __ set_last_Java_frame(rdi, noreg, rbp, nullptr, noreg);

  // Call C code.  Need thread but NOT official VM entry
  // crud.  We cannot block on this call, no GC can happen.  Call should
  // restore return values to their stack-slots with the new SP.
  __ movptr(Address(rsp,arg0_off*wordSize),rdi);
  __ movl(Address(rsp,arg1_off*wordSize), Deoptimization::Unpack_uncommon_trap);
  __ call(RuntimeAddress(CAST_FROM_FN_PTR(address, Deoptimization::unpack_frames)));
  // Set an oopmap for the call site
  oop_maps->add_gc_map( __ pc()-start, new OopMap( framesize, 0 ) );

  __ get_thread(rdi);
  __ reset_last_Java_frame(rdi, true);

  // Pop self-frame.
  __ leave();     // Epilog!

  // Jump to interpreter
  __ ret(0);

  // -------------
  // make sure all code is generated
  masm->flush();

   _uncommon_trap_blob = UncommonTrapBlob::create(&buffer, oop_maps, framesize);
}

//------------------------------generate_exception_blob---------------------------
// creates exception blob at the end
// Using exception blob, this code is jumped from a compiled method.
//
// Given an exception pc at a call we call into the runtime for the
// handler in this method. This handler might merely restore state
// (i.e. callee save registers) unwind the frame and jump to the
// exception handler for the nmethod if there is no Java level handler
// for the nmethod.
//
// This code is entered with a jmp.
//
// Arguments:
//   rax: exception oop
//   rdx: exception pc
//
// Results:
//   rax: exception oop
//   rdx: exception pc in caller or ???
//   destination: exception handler of caller
//
// Note: the exception pc MUST be at a call (precise debug information)
//       Only register rax, rdx, rcx are not callee saved.
//

void OptoRuntime::generate_exception_blob() {

  // Capture info about frame layout
  enum layout {
    thread_off,                 // last_java_sp
    // The frame sender code expects that rbp will be in the "natural" place and
    // will override any oopMap setting for it. We must therefore force the layout
    // so that it agrees with the frame sender code.
    rbp_off,
    return_off,                 // slot for return address
    framesize
  };

  // allocate space for the code
  ResourceMark rm;
  // setup code generation tools
  CodeBuffer   buffer("exception_blob", 512, 512);
  MacroAssembler* masm = new MacroAssembler(&buffer);

  OopMapSet *oop_maps = new OopMapSet();

  address start = __ pc();

  __ push(rdx);
  __ subptr(rsp, return_off * wordSize);   // Prolog!

  // rbp, location is implicitly known
  __ movptr(Address(rsp,rbp_off  *wordSize), rbp);

  // Store exception in Thread object. We cannot pass any arguments to the
  // handle_exception call, since we do not want to make any assumption
  // about the size of the frame where the exception happened in.
  __ get_thread(rcx);
  __ movptr(Address(rcx, JavaThread::exception_oop_offset()), rax);
  __ movptr(Address(rcx, JavaThread::exception_pc_offset()),  rdx);

  // This call does all the hard work.  It checks if an exception handler
  // exists in the method.
  // If so, it returns the handler address.
  // If not, it prepares for stack-unwinding, restoring the callee-save
  // registers of the frame being removed.
  //
  __ movptr(Address(rsp, thread_off * wordSize), rcx); // Thread is first argument
  __ set_last_Java_frame(rcx, noreg, noreg, nullptr, noreg);

  __ call(RuntimeAddress(CAST_FROM_FN_PTR(address, OptoRuntime::handle_exception_C)));

  // No registers to map, rbp is known implicitly
  oop_maps->add_gc_map( __ pc() - start,  new OopMap( framesize, 0 ));
  __ get_thread(rcx);
  __ reset_last_Java_frame(rcx, false);

  // Restore callee-saved registers
  __ movptr(rbp, Address(rsp, rbp_off * wordSize));

  __ addptr(rsp, return_off * wordSize);   // Epilog!
  __ pop(rdx); // Exception pc

  // rax: exception handler for given <exception oop/exception pc>

  // We have a handler in rax, (could be deopt blob)
  // rdx - throwing pc, deopt blob will need it.

  __ push(rax);

  // Get the exception
  __ movptr(rax, Address(rcx, JavaThread::exception_oop_offset()));
  // Get the exception pc in case we are deoptimized
  __ movptr(rdx, Address(rcx, JavaThread::exception_pc_offset()));
#ifdef ASSERT
  __ movptr(Address(rcx, JavaThread::exception_handler_pc_offset()), NULL_WORD);
  __ movptr(Address(rcx, JavaThread::exception_pc_offset()), NULL_WORD);
#endif
  // Clear the exception oop so GC no longer processes it as a root.
  __ movptr(Address(rcx, JavaThread::exception_oop_offset()), NULL_WORD);

  __ pop(rcx);

  // rax: exception oop
  // rcx: exception handler
  // rdx: exception pc
  __ jmp (rcx);

  // -------------
  // make sure all code is generated
  masm->flush();

  _exception_blob = ExceptionBlob::create(&buffer, oop_maps, framesize);
}
