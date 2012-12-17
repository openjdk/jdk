/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

#ifndef CPU_SPARC_VM_FRAME_SPARC_HPP
#define CPU_SPARC_VM_FRAME_SPARC_HPP

#include "runtime/synchronizer.hpp"
#include "utilities/top.hpp"

// A frame represents a physical stack frame (an activation).  Frames can be
// C or Java frames, and the Java frames can be interpreted or compiled.
// In contrast, vframes represent source-level activations, so that one physical frame
// can correspond to multiple source level frames because of inlining.
// A frame is comprised of {pc, sp, younger_sp}


// Layout of asm interpreter frame:
//
//  0xfffffff
//  ......
// [last  extra incoming arg,  (local # Nargs > 6 ? Nargs-1 : undef)]
// .. Note: incoming args are copied to local frame area upon entry
// [first extra incoming arg,  (local # Nargs > 6 ? 6       : undef)]
// [6 words for C-arg storage (unused)] Are this and next one really needed?
// [C-aggregate-word (unused)] Yes, if want extra params to be  in same place as C convention
// [16 words for register saving]                                    <--- FP
// [interpreter_frame_vm_locals ] (see below)

//              Note: Llocals is always double-word aligned
// [first local i.e. local # 0]        <-- Llocals
// ...
// [last local, i.e. local # Nlocals-1]

// [monitors                 ]
// ....
// [monitors                 ]    <-- Lmonitors (same as Llocals + 6*4 if none)
//                                    (must be double-word aligned because
//                                     monitor element size is constrained to
//                                     doubleword)
//
//                                <-- Lesp (points 1 past TOS)
// [bottom word used for stack ]
// ...
// [top word used for stack]    (first word of stack is double-word aligned)

// [space for outgoing args (conservatively allocated as max_stack - 6 + interpreter_frame_extra_outgoing_argument_words)]
// [6 words for C-arg storage]
// [C-aggregate-word (unused)]
// [16 words for register saving]                                    <--- SP
// ...
// 0x0000000
//
// The in registers and local registers are preserved in a block at SP.
//
// The first six in registers (I0..I5) hold the first six locals.
// The locals are used as follows:
//    Lesp         first free element of expression stack
//                 (which grows towards __higher__ addresses)
//    Lbcp         is set to address of bytecode to execute
//                 It is accessed in the frame under the name "bcx".
//                 It may at times (during GC) be an index instead.
//    Lmethod      the method being interpreted
//    Llocals      the base pointer for accessing the locals array
//                 (lower-numbered locals have lower addresses)
//    Lmonitors    the base pointer for accessing active monitors
//    Lcache       a saved pointer to the method's constant pool cache
//
//
// When calling out to another method,
// G5_method is set to method to call, G5_inline_cache_klass may be set,
// parameters are put in O registers, and also extra parameters
// must be cleverly copied from the top of stack to the outgoing param area in the frame,
// ------------------------------ C++ interpreter ----------------------------------------
// Layout of C++ interpreter frame:
//



// All frames:

 public:

  enum {
    // normal return address is 2 words past PC
    pc_return_offset                             = 2 * BytesPerInstWord,

    // size of each block, in order of increasing address:
    register_save_words                          = 16,
#ifdef _LP64
    callee_aggregate_return_pointer_words        =  0,
#else
    callee_aggregate_return_pointer_words        =  1,
#endif
    callee_register_argument_save_area_words     =  6,
    // memory_parameter_words                    = <arbitrary>,

    // offset of each block, in order of increasing address:
    // (note: callee_register_argument_save_area_words == Assembler::n_register_parameters)
    register_save_words_sp_offset                = 0,
    callee_aggregate_return_pointer_sp_offset    = register_save_words_sp_offset + register_save_words,
    callee_register_argument_save_area_sp_offset = callee_aggregate_return_pointer_sp_offset + callee_aggregate_return_pointer_words,
    memory_parameter_word_sp_offset              = callee_register_argument_save_area_sp_offset + callee_register_argument_save_area_words,
    varargs_offset                               = memory_parameter_word_sp_offset
  };

 private:
  intptr_t*  _younger_sp;                 // optional SP of callee (used to locate O7)
  int        _sp_adjustment_by_callee;   // adjustment in words to SP by callee for making locals contiguous

  // Note:  On SPARC, unlike Intel, the saved PC for a stack frame
  // is stored at a __variable__ distance from that frame's SP.
  // (In fact, it may be in the register save area of the callee frame,
  // but that fact need not bother us.)  Thus, we must store the
  // address of that saved PC explicitly.  On the other hand, SPARC
  // stores the FP for a frame at a fixed offset from the frame's SP,
  // so there is no need for a separate "frame::_fp" field.

 public:
  // Accessors

  intptr_t* younger_sp() const {
    assert(_younger_sp != NULL, "frame must possess a younger_sp");
    return _younger_sp;
  }

  int callee_sp_adjustment() const { return _sp_adjustment_by_callee; }
  void set_sp_adjustment_by_callee(int number_of_words) { _sp_adjustment_by_callee = number_of_words; }

  // Constructors

  // This constructor relies on the fact that the creator of a frame
  // has flushed register windows which the frame will refer to, and
  // that those register windows will not be reloaded until the frame is
  // done reading and writing the stack.  Moreover, if the "younger_sp"
  // argument points into the register save area of the next younger
  // frame (though it need not), the register window for that next
  // younger frame must also stay flushed.  (The caller is responsible
  // for ensuring this.)

  frame(intptr_t* sp, intptr_t* younger_sp, bool younger_frame_adjusted_stack = false);

  // make a deficient frame which doesn't know where its PC is:
  enum unpatchable_t { unpatchable };
  frame(intptr_t* sp, unpatchable_t, address pc = NULL, CodeBlob* cb = NULL);

  // Walk from sp outward looking for old_sp, and return old_sp's predecessor
  // (i.e. return the sp from the frame where old_sp is the fp).
  // Register windows are assumed to be flushed for the stack in question.

  static intptr_t* next_younger_sp_or_null(intptr_t* old_sp, intptr_t* sp);

  // Return true if sp is a younger sp in the stack described by valid_sp.
  static bool is_valid_stack_pointer(intptr_t* valid_sp, intptr_t* sp);

 public:
  // accessors for the instance variables
  intptr_t*   fp() const { return (intptr_t*) ((intptr_t)(sp()[FP->sp_offset_in_saved_window()]) + STACK_BIAS ); }

  // All frames

  intptr_t*  fp_addr_at(int index) const   { return &fp()[index];    }
  intptr_t*  sp_addr_at(int index) const   { return &sp()[index];    }
  intptr_t   fp_at(     int index) const   { return *fp_addr_at(index); }
  intptr_t   sp_at(     int index) const   { return *sp_addr_at(index); }

 private:
  inline address* I7_addr() const;
  inline address* O7_addr() const;

  inline address* I0_addr() const;
  inline address* O0_addr() const;
  intptr_t*  younger_sp_addr_at(int index) const   { return &younger_sp()[index];    }

 public:
  // access to SPARC arguments and argument registers

  // Assumes reg is an in/local register
  intptr_t*     register_addr(Register reg) const {
    return sp_addr_at(reg->sp_offset_in_saved_window());
  }

  // Assumes reg is an out register
  intptr_t*     out_register_addr(Register reg) const {
    return younger_sp_addr_at(reg->after_save()->sp_offset_in_saved_window());
  }


  // Interpreter frames

 public:
  // Asm interpreter
#ifndef CC_INTERP
  enum interpreter_frame_vm_locals {
       // 2 words, also used to save float regs across  calls to C
       interpreter_frame_d_scratch_fp_offset          = -2,
       interpreter_frame_l_scratch_fp_offset          = -4,
       interpreter_frame_padding_offset               = -5, // for native calls only
       interpreter_frame_oop_temp_offset              = -6, // for native calls only
       interpreter_frame_vm_locals_fp_offset          = -6, // should be same as above, and should be zero mod 8

       interpreter_frame_vm_local_words = -interpreter_frame_vm_locals_fp_offset,


       // interpreter frame set-up needs to save 2 extra words in outgoing param area
       // for class and jnienv arguments for native stubs (see nativeStubGen_sparc.cpp_

       interpreter_frame_extra_outgoing_argument_words = 2
  };
#else
  enum interpreter_frame_vm_locals {
       // 2 words, also used to save float regs across  calls to C
       interpreter_state_ptr_offset                   = 0,  // Is in L0 (Lstate) in save area
       interpreter_frame_mirror_offset                = 1,  // Is in L1 (Lmirror) in save area (for native calls only)

       // interpreter frame set-up needs to save 2 extra words in outgoing param area
       // for class and jnienv arguments for native stubs (see nativeStubGen_sparc.cpp_

       interpreter_frame_extra_outgoing_argument_words = 2
  };
#endif /* CC_INTERP */

  enum compiler_frame_fixed_locals {
       compiler_frame_vm_locals_fp_offset          = -2
  };

 private:
  ConstantPoolCache** interpreter_frame_cpoolcache_addr() const;

#ifndef CC_INTERP

  // where Lmonitors is saved:
  inline BasicObjectLock** interpreter_frame_monitors_addr() const;
  inline intptr_t** interpreter_frame_esp_addr() const;

  inline void interpreter_frame_set_tos_address(intptr_t* x);

  // monitors:

  // next two fns read and write Lmonitors value,
 private:
  BasicObjectLock* interpreter_frame_monitors()           const  { return *interpreter_frame_monitors_addr(); }
  void interpreter_frame_set_monitors(BasicObjectLock* monitors) {        *interpreter_frame_monitors_addr() = monitors; }
#else
 public:
  inline interpreterState get_interpreterState() const {
    return ((interpreterState)sp_at(interpreter_state_ptr_offset));
  }

#endif /* CC_INTERP */

 public:

#endif // CPU_SPARC_VM_FRAME_SPARC_HPP
