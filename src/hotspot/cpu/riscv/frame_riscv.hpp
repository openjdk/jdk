/*
 * Copyright (c) 1997, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2022, Huawei Technologies Co., Ltd. All rights reserved.
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

#ifndef CPU_RISCV_FRAME_RISCV_HPP
#define CPU_RISCV_FRAME_RISCV_HPP

#include "runtime/synchronizer.hpp"

// A frame represents a physical stack frame (an activation).  Frames can be
// C or Java frames, and the Java frames can be interpreted or compiled.
// In contrast, vframes represent source-level activations, so that one physical frame
// can correspond to multiple source level frames because of inlining.
// A frame is comprised of {pc, fp, sp}
// ------------------------------ Asm interpreter ----------------------------------------
// Layout of asm interpreter frame:
//    [expression stack      ] * <- sp

//    [monitors[0]           ]   \
//     ...                        | monitor block size = k
//    [monitors[k-1]         ]   /
//    [frame initial esp     ] ( == &monitors[0], initially here)       initial_sp_offset
//    [byte code index/pointr]                   = bcx()                bcx_offset

//    [pointer to locals     ]                   = locals()             locals_offset
//    [constant pool cache   ]                   = cache()              cache_offset

//    [klass of method       ]                   = mirror()             mirror_offset
//    [padding               ]

//    [methodData            ]                   = mdp()                mdx_offset
//    [Method                ]                   = method()             method_offset

//    [last esp              ]                   = last_sp()            last_sp_offset
//    [old stack pointer     ]                     (sender_sp)          sender_sp_offset

//    [old frame pointer     ]
//    [return pc             ]

//    [last sp               ]   <- fp           = link()
//    [oop temp              ]                     (only for native calls)

//    [padding               ]                     (to preserve machine SP alignment)
//    [locals and parameters ]
//                               <- sender sp
// ------------------------------ Asm interpreter ----------------------------------------

// ------------------------------ C Frame ------------------------------------------------
// Stack: gcc with -fno-omit-frame-pointer
//                    .
//                    .
//       +->          .
//       |   +-----------------+   |
//       |   | return address  |   |
//       |   |   previous fp ------+
//       |   | saved registers |
//       |   | local variables |
//       |   |       ...       | <-+
//       |   +-----------------+   |
//       |   | return address  |   |
//       +------ previous fp   |   |
//           | saved registers |   |
//           | local variables |   |
//       +-> |       ...       |   |
//       |   +-----------------+   |
//       |   | return address  |   |
//       |   |   previous fp ------+
//       |   | saved registers |
//       |   | local variables |
//       |   |       ...       | <-+
//       |   +-----------------+   |
//       |   | return address  |   |
//       +------ previous fp   |   |
//           | saved registers |   |
//           | local variables |   |
//   $fp --> |       ...       |   |
//           +-----------------+   |
//           | return address  |   |
//           |   previous fp ------+
//           | saved registers |
//   $sp --> | local variables |
//           +-----------------+
// ------------------------------ C Frame ------------------------------------------------

 public:
  enum {
    pc_return_offset                                 =  0,
    // All frames
    link_offset                                      = -2,
    return_addr_offset                               = -1,
    sender_sp_offset                                 =  0,
    // Interpreter frames
    interpreter_frame_oop_temp_offset                =  1, // for native calls only

    interpreter_frame_sender_sp_offset               = -3,
    // outgoing sp before a call to an invoked method
    interpreter_frame_last_sp_offset                 = interpreter_frame_sender_sp_offset - 1,
    interpreter_frame_method_offset                  = interpreter_frame_last_sp_offset - 1,
    interpreter_frame_mdp_offset                     = interpreter_frame_method_offset - 1,
    interpreter_frame_padding_offset                 = interpreter_frame_mdp_offset - 1,
    interpreter_frame_mirror_offset                  = interpreter_frame_padding_offset - 1,
    interpreter_frame_cache_offset                   = interpreter_frame_mirror_offset - 1,
    interpreter_frame_locals_offset                  = interpreter_frame_cache_offset - 1,
    interpreter_frame_bcp_offset                     = interpreter_frame_locals_offset - 1,
    interpreter_frame_initial_sp_offset              = interpreter_frame_bcp_offset - 1,

    interpreter_frame_monitor_block_top_offset       = interpreter_frame_initial_sp_offset,
    interpreter_frame_monitor_block_bottom_offset    = interpreter_frame_initial_sp_offset,

    // Entry frames
    // n.b. these values are determined by the layout defined in
    // stubGenerator for the Java call stub
    entry_frame_after_call_words                     =  22,
    entry_frame_call_wrapper_offset                  = -10,

    // we don't need a save area
    arg_reg_save_area_bytes                          =  0
  };

  intptr_t ptr_at(int offset) const {
    return *ptr_at_addr(offset);
  }

  void ptr_at_put(int offset, intptr_t value) {
    *ptr_at_addr(offset) = value;
  }

 private:
  // an additional field beyond _sp and _pc:
  intptr_t*   _fp; // frame pointer
  // The interpreter and adapters will extend the frame of the caller.
  // Since oopMaps are based on the sp of the caller before extension
  // we need to know that value. However in order to compute the address
  // of the return address we need the real "raw" sp. Since sparc already
  // uses sp() to mean "raw" sp and unextended_sp() to mean the caller's
  // original sp we use that convention.

  intptr_t*     _unextended_sp;
  void adjust_unextended_sp();

  intptr_t* ptr_at_addr(int offset) const {
    return (intptr_t*) addr_at(offset);
  }

#ifdef ASSERT
  // Used in frame::sender_for_{interpreter,compiled}_frame
  static void verify_deopt_original_pc(   CompiledMethod* nm, intptr_t* unextended_sp);
#endif

 public:
  // Constructors

  frame(intptr_t* ptr_sp, intptr_t* ptr_fp, address pc);

  frame(intptr_t* ptr_sp, intptr_t* unextended_sp, intptr_t* ptr_fp, address pc);

  frame(intptr_t* ptr_sp, intptr_t* ptr_fp);

  void init(intptr_t* ptr_sp, intptr_t* ptr_fp, address pc);

  // accessors for the instance variables
  // Note: not necessarily the real 'frame pointer' (see real_fp)
  intptr_t*   fp() const { return _fp; }

  inline address* sender_pc_addr() const;

  // expression stack tos if we are nested in a java call
  intptr_t* interpreter_frame_last_sp() const;

  // helper to update a map with callee-saved RBP
  static void update_map_with_saved_link(RegisterMap* map, intptr_t** link_addr);

  // deoptimization support
  void interpreter_frame_set_last_sp(intptr_t* last_sp);

  static jint interpreter_frame_expression_stack_direction() { return -1; }

  // returns the sending frame, without applying any barriers
  frame sender_raw(RegisterMap* map) const;

#endif // CPU_RISCV_FRAME_RISCV_HPP
