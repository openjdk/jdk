/*
 * Copyright (c) 2003, 2021, Oracle and/or its affiliates. All rights reserved.
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
#include "interpreter/interpreter.hpp"
#include "opto/runtime.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/stubRoutines.hpp"
#include "runtime/vframeArray.hpp"
#include "utilities/globalDefinitions.hpp"
#include "vmreg_x86.inline.hpp"

class SimpleRuntimeFrame {

  public:

  // Most of the runtime stubs have this simple frame layout.
  // This class exists to make the layout shared in one place.
  // Offsets are for compiler stack slots, which are jints.
  enum layout {
    // The frame sender code expects that rbp will be in the "natural" place and
    // will override any oopMap setting for it. We must therefore force the layout
    // so that it agrees with the frame sender code.
    rbp_off = frame::arg_reg_save_area_bytes/BytesPerInt,
    rbp_off2,
    return_off, return_off2,
    framesize
  };
};

#define __ masm->

//------------------------------generate_uncommon_trap_blob--------------------
void OptoRuntime::generate_uncommon_trap_blob() {
  // Allocate space for the code
  ResourceMark rm;
  // Setup code generation tools
  CodeBuffer buffer("uncommon_trap_blob", 2048, 1024);
  MacroAssembler* masm = new MacroAssembler(&buffer);

  assert(SimpleRuntimeFrame::framesize % 4 == 0, "sp not 16-byte aligned");

  address start = __ pc();

  // Push self-frame.  We get here with a return address on the
  // stack, so rsp is 8-byte aligned until we allocate our frame.
  __ subptr(rsp, SimpleRuntimeFrame::return_off << LogBytesPerInt); // Epilog!

  // No callee saved registers. rbp is assumed implicitly saved
  __ movptr(Address(rsp, SimpleRuntimeFrame::rbp_off << LogBytesPerInt), rbp);

  // compiler left unloaded_class_index in j_rarg0 move to where the
  // runtime expects it.
  __ movl(c_rarg1, j_rarg0);

  __ set_last_Java_frame(noreg, noreg, nullptr, rscratch1);

  // Call C code.  Need thread but NOT official VM entry
  // crud.  We cannot block on this call, no GC can happen.  Call should
  // capture callee-saved registers as well as return values.
  // Thread is in rdi already.
  //
  // UnrollBlock* uncommon_trap(JavaThread* thread, jint unloaded_class_index);

  __ mov(c_rarg0, r15_thread);
  __ movl(c_rarg2, Deoptimization::Unpack_uncommon_trap);
  __ call(RuntimeAddress(CAST_FROM_FN_PTR(address, Deoptimization::uncommon_trap)));

  // Set an oopmap for the call site
  OopMapSet* oop_maps = new OopMapSet();
  OopMap* map = new OopMap(SimpleRuntimeFrame::framesize, 0);

  // location of rbp is known implicitly by the frame sender code

  oop_maps->add_gc_map(__ pc() - start, map);

  __ reset_last_Java_frame(false);

  // Load UnrollBlock* into rdi
  __ mov(rdi, rax);

#ifdef ASSERT
  { Label L;
    __ cmpptr(Address(rdi, Deoptimization::UnrollBlock::unpack_kind_offset()),
              Deoptimization::Unpack_uncommon_trap);
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

  // Pop self-frame.  We have no frame, and must rely only on rax and rsp.
  __ addptr(rsp, (SimpleRuntimeFrame::framesize - 2) << LogBytesPerInt); // Epilog!

  // Pop deoptimized frame (int)
  __ movl(rcx, Address(rdi,
                       Deoptimization::UnrollBlock::
                       size_of_deoptimized_frame_offset()));
  __ addptr(rsp, rcx);

  // rsp should be pointing at the return address to the caller (3)

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

  // Load address of array of frame pcs into rcx (address*)
  __ movptr(rcx, Address(rdi, Deoptimization::UnrollBlock::frame_pcs_offset()));

  // Trash the return pc
  __ addptr(rsp, wordSize);

  // Load address of array of frame sizes into rsi (intptr_t*)
  __ movptr(rsi, Address(rdi, Deoptimization::UnrollBlock:: frame_sizes_offset()));

  // Counter
  __ movl(rdx, Address(rdi, Deoptimization::UnrollBlock:: number_of_frames_offset())); // (int)

  // Now adjust the caller's stack to make up for the extra locals but
  // record the original sp so that we can save it in the skeletal
  // interpreter frame and the stack walking of interpreter_sender
  // will get the unextended sp value and not the "real" sp value.

  const Register sender_sp = r8;

  __ mov(sender_sp, rsp);
  __ movl(rbx, Address(rdi, Deoptimization::UnrollBlock:: caller_adjustment_offset())); // (int)
  __ subptr(rsp, rbx);

  // Push interpreter frames in a loop
  Label loop;
  __ bind(loop);
  __ movptr(rbx, Address(rsi, 0)); // Load frame size
  __ subptr(rbx, 2 * wordSize);    // We'll push pc and rbp by hand
  __ pushptr(Address(rcx, 0));     // Save return address
  __ enter();                      // Save old & set new rbp
  __ subptr(rsp, rbx);             // Prolog
  __ movptr(Address(rbp, frame::interpreter_frame_sender_sp_offset * wordSize),
            sender_sp);            // Make it walkable
  // This value is corrected by layout_activation_impl
  __ movptr(Address(rbp, frame::interpreter_frame_last_sp_offset * wordSize), NULL_WORD);
  __ mov(sender_sp, rsp);          // Pass sender_sp to next frame
  __ addptr(rsi, wordSize);        // Bump array pointer (sizes)
  __ addptr(rcx, wordSize);        // Bump array pointer (pcs)
  __ decrementl(rdx);              // Decrement counter
  __ jcc(Assembler::notZero, loop);
  __ pushptr(Address(rcx, 0));     // Save final return address

  // Re-push self-frame
  __ enter();                 // Save old & set new rbp
  __ subptr(rsp, (SimpleRuntimeFrame::framesize - 4) << LogBytesPerInt);
                              // Prolog

  // Use rbp because the frames look interpreted now
  // Save "the_pc" since it cannot easily be retrieved using the last_java_SP after we aligned SP.
  // Don't need the precise return PC here, just precise enough to point into this code blob.
  address the_pc = __ pc();
  __ set_last_Java_frame(noreg, rbp, the_pc, rscratch1);

  // Call C code.  Need thread but NOT official VM entry
  // crud.  We cannot block on this call, no GC can happen.  Call should
  // restore return values to their stack-slots with the new SP.
  // Thread is in rdi already.
  //
  // BasicType unpack_frames(JavaThread* thread, int exec_mode);

  __ andptr(rsp, -(StackAlignmentInBytes)); // Align SP as required by ABI
  __ mov(c_rarg0, r15_thread);
  __ movl(c_rarg1, Deoptimization::Unpack_uncommon_trap);
  __ call(RuntimeAddress(CAST_FROM_FN_PTR(address, Deoptimization::unpack_frames)));

  // Set an oopmap for the call site
  // Use the same PC we used for the last java frame
  oop_maps->add_gc_map(the_pc - start, new OopMap(SimpleRuntimeFrame::framesize, 0));

  // Clear fp AND pc
  __ reset_last_Java_frame(true);

  // Pop self-frame.
  __ leave();                 // Epilog

  // Jump to interpreter
  __ ret(0);

  // Make sure all code is generated
  masm->flush();

  _uncommon_trap_blob =  UncommonTrapBlob::create(&buffer, oop_maps,
                                                 SimpleRuntimeFrame::framesize >> 1);
}

//------------------------------generate_exception_blob---------------------------
// creates exception blob at the end
// Using exception blob, this code is jumped from a compiled method.
// (see emit_exception_handler in x86_64.ad file)
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
//       Registers rax, rdx, rcx, rsi, rdi, r8-r11 are not callee saved.
//

void OptoRuntime::generate_exception_blob() {
  assert(!OptoRuntime::is_callee_saved_register(RDX_num), "");
  assert(!OptoRuntime::is_callee_saved_register(RAX_num), "");
  assert(!OptoRuntime::is_callee_saved_register(RCX_num), "");

  assert(SimpleRuntimeFrame::framesize % 4 == 0, "sp not 16-byte aligned");

  // Allocate space for the code
  ResourceMark rm;
  // Setup code generation tools
  CodeBuffer buffer("exception_blob", 2048, 1024);
  MacroAssembler* masm = new MacroAssembler(&buffer);


  address start = __ pc();

  // Exception pc is 'return address' for stack walker
  __ push(rdx);
  __ subptr(rsp, SimpleRuntimeFrame::return_off << LogBytesPerInt); // Prolog

  // Save callee-saved registers.  See x86_64.ad.

  // rbp is an implicitly saved callee saved register (i.e., the calling
  // convention will save/restore it in the prolog/epilog). Other than that
  // there are no callee save registers now that adapter frames are gone.

  __ movptr(Address(rsp, SimpleRuntimeFrame::rbp_off << LogBytesPerInt), rbp);

  // Store exception in Thread object. We cannot pass any arguments to the
  // handle_exception call, since we do not want to make any assumption
  // about the size of the frame where the exception happened in.
  // c_rarg0 is either rdi (Linux) or rcx (Windows).
  __ movptr(Address(r15_thread, JavaThread::exception_oop_offset()),rax);
  __ movptr(Address(r15_thread, JavaThread::exception_pc_offset()), rdx);

  // This call does all the hard work.  It checks if an exception handler
  // exists in the method.
  // If so, it returns the handler address.
  // If not, it prepares for stack-unwinding, restoring the callee-save
  // registers of the frame being removed.
  //
  // address OptoRuntime::handle_exception_C(JavaThread* thread)

  // At a method handle call, the stack may not be properly aligned
  // when returning with an exception.
  address the_pc = __ pc();
  __ set_last_Java_frame(noreg, noreg, the_pc, rscratch1);
  __ mov(c_rarg0, r15_thread);
  __ andptr(rsp, -(StackAlignmentInBytes));    // Align stack
  __ call(RuntimeAddress(CAST_FROM_FN_PTR(address, OptoRuntime::handle_exception_C)));

  // Set an oopmap for the call site.  This oopmap will only be used if we
  // are unwinding the stack.  Hence, all locations will be dead.
  // Callee-saved registers will be the same as the frame above (i.e.,
  // handle_exception_stub), since they were restored when we got the
  // exception.

  OopMapSet* oop_maps = new OopMapSet();

  oop_maps->add_gc_map(the_pc - start, new OopMap(SimpleRuntimeFrame::framesize, 0));

  __ reset_last_Java_frame(false);

  // Restore callee-saved registers

  // rbp is an implicitly saved callee-saved register (i.e., the calling
  // convention will save restore it in prolog/epilog) Other than that
  // there are no callee save registers now that adapter frames are gone.

  __ movptr(rbp, Address(rsp, SimpleRuntimeFrame::rbp_off << LogBytesPerInt));

  __ addptr(rsp, SimpleRuntimeFrame::return_off << LogBytesPerInt); // Epilog
  __ pop(rdx);                  // No need for exception pc anymore

  // rax: exception handler

  // We have a handler in rax (could be deopt blob).
  __ mov(r8, rax);

  // Get the exception oop
  __ movptr(rax, Address(r15_thread, JavaThread::exception_oop_offset()));
  // Get the exception pc in case we are deoptimized
  __ movptr(rdx, Address(r15_thread, JavaThread::exception_pc_offset()));
#ifdef ASSERT
  __ movptr(Address(r15_thread, JavaThread::exception_handler_pc_offset()), NULL_WORD);
  __ movptr(Address(r15_thread, JavaThread::exception_pc_offset()), NULL_WORD);
#endif
  // Clear the exception oop so GC no longer processes it as a root.
  __ movptr(Address(r15_thread, JavaThread::exception_oop_offset()), NULL_WORD);

  // rax: exception oop
  // r8:  exception handler
  // rdx: exception pc
  // Jump to handler

  __ jmp(r8);

  // Make sure all code is generated
  masm->flush();

  // Set exception blob
  _exception_blob =  ExceptionBlob::create(&buffer, oop_maps, SimpleRuntimeFrame::framesize >> 1);
}
#endif // COMPILER2
