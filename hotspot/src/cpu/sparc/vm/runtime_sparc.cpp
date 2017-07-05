/*
 * Copyright 1998-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

#include "incls/_precompiled.incl"
#include "incls/_runtime_sparc.cpp.incl"


#define __ masm->

ExceptionBlob      *OptoRuntime::_exception_blob;

//------------------------------ generate_exception_blob ---------------------------
// creates exception blob at the end
// Using exception blob, this code is jumped from a compiled method.
// (see emit_exception_handler in sparc.ad file)
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
//   O0: exception oop
//   O1: exception pc
//
// Results:
//   O0: exception oop
//   O1: exception pc in caller or ???
//   destination: exception handler of caller
//
// Note: the exception pc MUST be at a call (precise debug information)
//
void OptoRuntime::generate_exception_blob() {
  // allocate space for code
  ResourceMark rm;
  int pad = VerifyThread ? 256 : 0;// Extra slop space for more verify code

  // setup code generation tools
  // Measured 8/7/03 at 256 in 32bit debug build (no VerifyThread)
  // Measured 8/7/03 at 528 in 32bit debug build (VerifyThread)
  CodeBuffer buffer("exception_blob", 600+pad, 512);
  MacroAssembler* masm     = new MacroAssembler(&buffer);

  int framesize_in_bytes = __ total_frame_size_in_bytes(0);
  int framesize_in_words = framesize_in_bytes / wordSize;
  int framesize_in_slots = framesize_in_bytes / sizeof(jint);

  Label L;

  int start = __ offset();

  __ verify_thread();
  __ st_ptr(Oexception,  G2_thread, JavaThread::exception_oop_offset());
  __ st_ptr(Oissuing_pc, G2_thread, JavaThread::exception_pc_offset());

  // This call does all the hard work. It checks if an exception catch
  // exists in the method.
  // If so, it returns the handler address.
  // If the nmethod has been deoptimized and it had a handler the handler
  // address is the deopt blob unpack_with_exception entry.
  //
  // If no handler exists it prepares for stack-unwinding, restoring the callee-save
  // registers of the frame being removed.
  //
  __ save_frame(0);

  __ mov(G2_thread, O0);
  __ set_last_Java_frame(SP, noreg);
  __ save_thread(L7_thread_cache);

  // This call can block at exit and nmethod can be deoptimized at that
  // point. If the nmethod had a catch point we would jump to the
  // now deoptimized catch point and fall thru the vanilla deopt
  // path and lose the exception
  // Sure would be simpler if this call didn't block!
  __ call(CAST_FROM_FN_PTR(address, OptoRuntime::handle_exception_C), relocInfo::runtime_call_type);
  __ delayed()->mov(L7_thread_cache, O0);

  // Set an oopmap for the call site.  This oopmap will only be used if we
  // are unwinding the stack.  Hence, all locations will be dead.
  // Callee-saved registers will be the same as the frame above (i.e.,
  // handle_exception_stub), since they were restored when we got the
  // exception.

  OopMapSet *oop_maps = new OopMapSet();
  oop_maps->add_gc_map( __ offset()-start, new OopMap(framesize_in_slots, 0));

  __ bind(L);
  __ restore_thread(L7_thread_cache);
  __ reset_last_Java_frame();

  __ mov(O0, G3_scratch);             // Move handler address to temp
  __ restore();

  // G3_scratch contains handler address
  // Since this may be the deopt blob we must set O7 to look like we returned
  // from the original pc that threw the exception

  __ ld_ptr(G2_thread, JavaThread::exception_pc_offset(), O7);
  __ sub(O7, frame::pc_return_offset, O7);


  assert(Assembler::is_simm13(in_bytes(JavaThread::exception_oop_offset())), "exception offset overflows simm13, following ld instruction cannot be in delay slot");
  __ ld_ptr(G2_thread, JavaThread::exception_oop_offset(), Oexception); // O0
#ifdef ASSERT
  __ st_ptr(G0, G2_thread, JavaThread::exception_handler_pc_offset());
  __ st_ptr(G0, G2_thread, JavaThread::exception_pc_offset());
#endif
  __ JMP(G3_scratch, 0);
  // Clear the exception oop so GC no longer processes it as a root.
  __ delayed()->st_ptr(G0, G2_thread, JavaThread::exception_oop_offset());

  // -------------
  // make sure all code is generated
  masm->flush();

  _exception_blob = ExceptionBlob::create(&buffer, oop_maps, framesize_in_words);
}
