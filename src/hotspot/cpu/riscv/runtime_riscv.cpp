/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, Red Hat Inc. All rights reserved.
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

#ifdef COMPILER2
#include "asm/macroAssembler.hpp"
#include "asm/macroAssembler.inline.hpp"
#include "code/vmreg.hpp"
#include "interpreter/interpreter.hpp"
#include "opto/runtime.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/stubRoutines.hpp"
#include "runtime/vframeArray.hpp"
#include "utilities/globalDefinitions.hpp"
#include "vmreg_riscv.inline.hpp"

class SimpleRuntimeFrame {
public:

  // Most of the runtime stubs have this simple frame layout.
  // This class exists to make the layout shared in one place.
  // Offsets are for compiler stack slots, which are jints.
  enum layout {
    // The frame sender code expects that fp will be in the "natural" place and
    // will override any oopMap setting for it. We must therefore force the layout
    // so that it agrees with the frame sender code.
    // we don't expect any arg reg save area so riscv asserts that
    // frame::arg_reg_save_area_bytes == 0
    fp_off = 0, fp_off2,
    return_off, return_off2,
    framesize
  };
};

#define __ masm->

//------------------------------generate_uncommon_trap_blob--------------------
UncommonTrapBlob* OptoRuntime::generate_uncommon_trap_blob() {
  // Allocate space for the code
  ResourceMark rm;
  // Setup code generation tools
  const char* name = OptoRuntime::stub_name(StubId::c2_uncommon_trap_id);
  CodeBuffer buffer(name, 2048, 1024);
  if (buffer.blob() == nullptr) {
    return nullptr;
  }
  MacroAssembler* masm = new MacroAssembler(&buffer);
  assert_cond(masm != nullptr);

  assert(SimpleRuntimeFrame::framesize % 4 == 0, "sp not 16-byte aligned");

  address start = __ pc();

  // Push self-frame.  We get here with a return address in RA
  // and sp should be 16 byte aligned
  // push fp and retaddr by hand
  __ subi(sp, sp, 2 * wordSize);
  __ sd(ra, Address(sp, wordSize));
  __ sd(fp, Address(sp, 0));
  // we don't expect an arg reg save area
#ifndef PRODUCT
  assert(frame::arg_reg_save_area_bytes == 0, "not expecting frame reg save area");
#endif
  // compiler left unloaded_class_index in j_rarg0 move to where the
  // runtime expects it.
  __ sext(c_rarg1, j_rarg0, 32);

  // we need to set the past SP to the stack pointer of the stub frame
  // and the pc to the address where this runtime call will return
  // although actually any pc in this code blob will do).
  Label retaddr;
  __ set_last_Java_frame(sp, noreg, retaddr, t0);

  // Call C code.  Need thread but NOT official VM entry
  // crud.  We cannot block on this call, no GC can happen.  Call should
  // capture callee-saved registers as well as return values.
  //
  // UnrollBlock* uncommon_trap(JavaThread* thread, jint unloaded_class_index, jint exec_mode)
  //
  // n.b. 3 gp args, 0 fp args, integral return type

  __ mv(c_rarg0, xthread);
  __ mv(c_rarg2, Deoptimization::Unpack_uncommon_trap);
  __ rt_call(CAST_FROM_FN_PTR(address, Deoptimization::uncommon_trap));
  __ bind(retaddr);

  // Set an oopmap for the call site
  OopMapSet* oop_maps = new OopMapSet();
  OopMap* map = new OopMap(SimpleRuntimeFrame::framesize, 0);
  assert_cond(oop_maps != nullptr && map != nullptr);

  // location of fp is known implicitly by the frame sender code

  oop_maps->add_gc_map(__ pc() - start, map);

  __ reset_last_Java_frame(false);

  // move UnrollBlock* into x14
  __ mv(x14, x10);

#ifdef ASSERT
  { Label L;
    __ lwu(t0, Address(x14, Deoptimization::UnrollBlock::unpack_kind_offset()));
    __ mv(t1, Deoptimization::Unpack_uncommon_trap);
    __ beq(t0, t1, L);
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

  __ add(sp, sp, (SimpleRuntimeFrame::framesize) << LogBytesPerInt); // Epilog!

  // Pop deoptimized frame (int)
  __ lwu(x12, Address(x14,
                      Deoptimization::UnrollBlock::
                      size_of_deoptimized_frame_offset()));
  __ subi(x12, x12, 2 * wordSize);
  __ add(sp, sp, x12);
  __ ld(fp, Address(sp, 0));
  __ ld(ra, Address(sp, wordSize));
  __ addi(sp, sp, 2 * wordSize);
  // RA should now be the return address to the caller (3) frame

#ifdef ASSERT
  // Compilers generate code that bang the stack by as much as the
  // interpreter would need. So this stack banging should never
  // trigger a fault. Verify that it does not on non product builds.
  __ lwu(x11, Address(x14,
                      Deoptimization::UnrollBlock::
                      total_frame_sizes_offset()));
  __ bang_stack_size(x11, x12);
#endif

  // Load address of array of frame pcs into x12 (address*)
  __ ld(x12, Address(x14,
                     Deoptimization::UnrollBlock::frame_pcs_offset()));

  // Load address of array of frame sizes into x15 (intptr_t*)
  __ ld(x15, Address(x14,
                     Deoptimization::UnrollBlock::
                     frame_sizes_offset()));

  // Counter
  __ lwu(x13, Address(x14,
                      Deoptimization::UnrollBlock::
                      number_of_frames_offset())); // (int)

  // Now adjust the caller's stack to make up for the extra locals but
  // record the original sp so that we can save it in the skeletal
  // interpreter frame and the stack walking of interpreter_sender
  // will get the unextended sp value and not the "real" sp value.

  const Register sender_sp = t1; // temporary register

  __ lwu(x11, Address(x14,
                      Deoptimization::UnrollBlock::
                      caller_adjustment_offset())); // (int)
  __ mv(sender_sp, sp);
  __ sub(sp, sp, x11);

  // Push interpreter frames in a loop
  Label loop;
  __ bind(loop);
  __ ld(x11, Address(x15, 0));       // Load frame size
  __ subi(x11, x11, 2 * wordSize);   // We'll push pc and fp by hand
  __ ld(ra, Address(x12, 0));        // Save return address
  __ enter();                        // and old fp & set new fp
  __ sub(sp, sp, x11);               // Prolog
  __ sd(sender_sp, Address(fp, frame::interpreter_frame_sender_sp_offset * wordSize)); // Make it walkable
  // This value is corrected by layout_activation_impl
  __ sd(zr, Address(fp, frame::interpreter_frame_last_sp_offset * wordSize));
  __ mv(sender_sp, sp);              // Pass sender_sp to next frame
  __ addi(x15, x15, wordSize);       // Bump array pointer (sizes)
  __ addi(x12, x12, wordSize);       // Bump array pointer (pcs)
  __ subiw(x13, x13, 1);             // Decrement counter
  __ bgtz(x13, loop);
  __ ld(ra, Address(x12, 0));        // save final return address
  // Re-push self-frame
  __ enter();                        // & old fp & set new fp

  // Use fp because the frames look interpreted now
  // Save "the_pc" since it cannot easily be retrieved using the last_java_SP after we aligned SP.
  // Don't need the precise return PC here, just precise enough to point into this code blob.
  address the_pc = __ pc();
  __ set_last_Java_frame(sp, fp, the_pc, t0);

  // Call C code.  Need thread but NOT official VM entry
  // crud.  We cannot block on this call, no GC can happen.  Call should
  // restore return values to their stack-slots with the new SP.
  //
  // BasicType unpack_frames(JavaThread* thread, int exec_mode)
  //

  // n.b. 2 gp args, 0 fp args, integral return type

  // sp should already be aligned
  __ mv(c_rarg0, xthread);
  __ mv(c_rarg1, Deoptimization::Unpack_uncommon_trap);
  __ rt_call(CAST_FROM_FN_PTR(address, Deoptimization::unpack_frames));

  // Set an oopmap for the call site
  // Use the same PC we used for the last java frame
  oop_maps->add_gc_map(the_pc - start, new OopMap(SimpleRuntimeFrame::framesize, 0));

  // Clear fp AND pc
  __ reset_last_Java_frame(true);

  // Pop self-frame.
  __ leave();                 // Epilog

  // Jump to interpreter
  __ ret();

  // Make sure all code is generated
  masm->flush();

  return UncommonTrapBlob::create(&buffer, oop_maps,
                                                  SimpleRuntimeFrame::framesize >> 1);
}

//------------------------------generate_exception_blob---------------------------
// creates exception blob at the end
// Using exception blob, this code is jumped from a compiled method.
// (see emit_exception_handler in riscv.ad file)
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
//   x10: exception oop
//   x13: exception pc
//
// Results:
//   x10: exception oop
//   x13: exception pc in caller
//   destination: exception handler of caller
//
// Note: the exception pc MUST be at a call (precise debug information)
//       Registers x10, x13, x12, x14, x15, t0 are not callee saved.
//

ExceptionBlob* OptoRuntime::generate_exception_blob() {
  assert(!OptoRuntime::is_callee_saved_register(R13_num), "");
  assert(!OptoRuntime::is_callee_saved_register(R10_num), "");
  assert(!OptoRuntime::is_callee_saved_register(R12_num), "");

  assert(SimpleRuntimeFrame::framesize % 4 == 0, "sp not 16-byte aligned");

  // Allocate space for the code
  ResourceMark rm;
  // Setup code generation tools
  const char* name = OptoRuntime::stub_name(StubId::c2_exception_id);
  CodeBuffer buffer(name, 2048, 1024);
  if (buffer.blob() == nullptr) {
    return nullptr;
  }
  MacroAssembler* masm = new MacroAssembler(&buffer);
  assert_cond(masm != nullptr);

  // TODO check various assumptions made here
  //
  // make sure we do so before running this

  address start = __ pc();

  // push fp and retaddr by hand
  // Exception pc is 'return address' for stack walker
  __ subi(sp, sp, 2 * wordSize);
  __ sd(ra, Address(sp, wordSize));
  __ sd(fp, Address(sp));
  // there are no callee save registers and we don't expect an
  // arg reg save area
#ifndef PRODUCT
  assert(frame::arg_reg_save_area_bytes == 0, "not expecting frame reg save area");
#endif
  // Store exception in Thread object. We cannot pass any arguments to the
  // handle_exception call, since we do not want to make any assumption
  // about the size of the frame where the exception happened in.
  __ sd(x10, Address(xthread, JavaThread::exception_oop_offset()));
  __ sd(x13, Address(xthread, JavaThread::exception_pc_offset()));

  // This call does all the hard work.  It checks if an exception handler
  // exists in the method.
  // If so, it returns the handler address.
  // If not, it prepares for stack-unwinding, restoring the callee-save
  // registers of the frame being removed.
  //
  // address OptoRuntime::handle_exception_C(JavaThread* thread)
  //
  // n.b. 1 gp arg, 0 fp args, integral return type

  // the stack should always be aligned
  address the_pc = __ pc();
  __ set_last_Java_frame(sp, noreg, the_pc, t0);
  __ mv(c_rarg0, xthread);
  __ rt_call(CAST_FROM_FN_PTR(address, OptoRuntime::handle_exception_C));

  // handle_exception_C is a special VM call which does not require an explicit
  // instruction sync afterwards.

  // Set an oopmap for the call site.  This oopmap will only be used if we
  // are unwinding the stack.  Hence, all locations will be dead.
  // Callee-saved registers will be the same as the frame above (i.e.,
  // handle_exception_stub), since they were restored when we got the
  // exception.

  OopMapSet* oop_maps = new OopMapSet();
  assert_cond(oop_maps != nullptr);

  oop_maps->add_gc_map(the_pc - start, new OopMap(SimpleRuntimeFrame::framesize, 0));

  __ reset_last_Java_frame(false);

  // Restore callee-saved registers

  // fp is an implicitly saved callee saved register (i.e. the calling
  // convention will save restore it in prolog/epilog) Other than that
  // there are no callee save registers now that adapter frames are gone.
  // and we dont' expect an arg reg save area
  __ ld(fp, Address(sp));
  __ ld(x13, Address(sp, wordSize));
  __ addi(sp, sp, 2 * wordSize);

  // x10: exception handler

  // We have a handler in x10 (could be deopt blob).
  __ mv(t1, x10);

  // Get the exception oop
  __ ld(x10, Address(xthread, JavaThread::exception_oop_offset()));
  // Get the exception pc in case we are deoptimized
  __ ld(x14, Address(xthread, JavaThread::exception_pc_offset()));
#ifdef ASSERT
  __ sd(zr, Address(xthread, JavaThread::exception_handler_pc_offset()));
  __ sd(zr, Address(xthread, JavaThread::exception_pc_offset()));
#endif
  // Clear the exception oop so GC no longer processes it as a root.
  __ sd(zr, Address(xthread, JavaThread::exception_oop_offset()));

  // x10: exception oop
  // t1:  exception handler
  // x14: exception pc
  // Jump to handler

  __ jr(t1);

  // Make sure all code is generated
  masm->flush();

  // Set exception blob
  return ExceptionBlob::create(&buffer, oop_maps, SimpleRuntimeFrame::framesize >> 1);
}
#endif // COMPILER2
