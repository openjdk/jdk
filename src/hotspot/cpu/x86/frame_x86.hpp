/*
 * Copyright (c) 1997, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef CPU_X86_FRAME_X86_HPP
#define CPU_X86_FRAME_X86_HPP

// A frame represents a physical stack frame (an activation).  Frames can be
// C or Java frames, and the Java frames can be interpreted or compiled.
// In contrast, vframes represent source-level activations, so that one physical frame
// can correspond to multiple source level frames because of inlining.
// A frame is comprised of {pc, fp, sp}
// ------------------------------ Asm interpreter ----------------------------------------
// Layout of asm interpreter frame:
//    [expression stack      ] * <- sp
//    [monitors              ]   \
//     ...                        | monitor block size
//    [monitors              ]   /
//    [monitor block size    ]
//    [byte code pointer     ]                   = bcp()                bcp_offset
//    [pointer to locals     ]                   = locals()             locals_offset
//    [constant pool cache   ]                   = cache()              cache_offset
//    [methodData            ]                   = mdp()                mdx_offset
//    [klass of method       ]                   = mirror()             mirror_offset
//    [Method*               ]                   = method()             method_offset
//    [last sp               ]                   = last_sp()            last_sp_offset
//    [old stack pointer     ]                     (sender_sp)          sender_sp_offset
//    [old frame pointer     ]   <- fp           = link()
//    [return pc             ]
//    [oop temp              ]                     (only for native calls)
//    [locals and parameters ]
//                               <- sender sp
// ------------------------------ Asm interpreter ----------------------------------------

 public:
  enum {
    pc_return_offset                                 =  0,
    // All frames
    link_offset                                      =  0,
    return_addr_offset                               =  1,
    // non-interpreter frames
    sender_sp_offset                                 =  2,

    // Interpreter frames
    interpreter_frame_result_handler_offset          =  3, // for native calls only
    interpreter_frame_oop_temp_offset                =  2, // for native calls only

    interpreter_frame_sender_sp_offset               = -1,
    // outgoing sp before a call to an invoked method
    interpreter_frame_last_sp_offset                 = interpreter_frame_sender_sp_offset - 1,
    interpreter_frame_method_offset                  = interpreter_frame_last_sp_offset - 1,
    interpreter_frame_mirror_offset                  = interpreter_frame_method_offset - 1,
    interpreter_frame_mdp_offset                     = interpreter_frame_mirror_offset - 1,
    interpreter_frame_cache_offset                   = interpreter_frame_mdp_offset - 1,
    interpreter_frame_locals_offset                  = interpreter_frame_cache_offset - 1,
    interpreter_frame_bcp_offset                     = interpreter_frame_locals_offset - 1,
    interpreter_frame_initial_sp_offset              = interpreter_frame_bcp_offset - 1,

    interpreter_frame_monitor_block_top_offset       = interpreter_frame_initial_sp_offset,
    interpreter_frame_monitor_block_bottom_offset    = interpreter_frame_initial_sp_offset,

    // Entry frames
#ifdef AMD64
#ifdef _WIN64
    entry_frame_after_call_words                     =  28,
    entry_frame_call_wrapper_offset                  =  2,

    arg_reg_save_area_bytes                          = 32, // Register argument save area
#else
    entry_frame_after_call_words                     = 13,
    entry_frame_call_wrapper_offset                  = -6,

    arg_reg_save_area_bytes                          =  0,
#endif // _WIN64
#else
    entry_frame_call_wrapper_offset                  =  2,
#endif // AMD64

    // size, in words, of frame metadata (e.g. pc and link)
    metadata_words                                   = sender_sp_offset,
    // size, in words, of metadata at frame bottom, i.e. it is not part of the
    // caller/callee overlap
    metadata_words_at_bottom                         = metadata_words,
    // size, in words, of frame metadata at the frame top, i.e. it is located
    // between a callee frame and its stack arguments, where it is part
    // of the caller/callee overlap
    metadata_words_at_top                            = 0,
    // size, in words, of frame metadata at the frame top that needs
    // to be reserved for callee functions in the runtime
    frame_alignment                                  = 16,
    // size, in words, of maximum shift in frame position due to alignment
    align_wiggle                                     =  1
  };

  intptr_t ptr_at(int offset) const {
    return *ptr_at_addr(offset);
  }

  void ptr_at_put(int offset, intptr_t value) {
    *ptr_at_addr(offset) = value;
  }

 private:
  // an additional field beyond _sp and _pc:
  union {
    intptr_t*  _fp; // frame pointer
    int _offset_fp; // relative frame pointer for use in stack-chunk frames
  };
  // The interpreter and adapters will extend the frame of the caller.
  // Since oopMaps are based on the sp of the caller before extension
  // we need to know that value. However in order to compute the address
  // of the return address we need the real "raw" sp. By convention we
  // use sp() to mean "raw" sp and unextended_sp() to mean the caller's
  // original sp.

  union {
    intptr_t* _unextended_sp;
    int _offset_unextended_sp; // for use in stack-chunk frames
  };

  void adjust_unextended_sp() NOT_DEBUG_RETURN;

  intptr_t* ptr_at_addr(int offset) const {
    return (intptr_t*) addr_at(offset);
  }

#ifdef ASSERT
  // Used in frame::sender_for_{interpreter,compiled}_frame
  static void verify_deopt_original_pc(CompiledMethod* nm, intptr_t* unextended_sp);
#endif

 public:
  // Constructors

  frame(intptr_t* sp, intptr_t* fp, address pc);

  frame(intptr_t* sp, intptr_t* unextended_sp, intptr_t* fp, address pc);

  frame(intptr_t* sp, intptr_t* unextended_sp, intptr_t* fp, address pc, CodeBlob* cb);
  // used for heap frame construction by continuations
  frame(intptr_t* sp, intptr_t* unextended_sp, intptr_t* fp, address pc, CodeBlob* cb, const ImmutableOopMap* oop_map, bool relative);

  frame(intptr_t* sp, intptr_t* fp);

  void init(intptr_t* sp, intptr_t* fp, address pc);
  void setup(address pc);

  // accessors for the instance variables
  // Note: not necessarily the real 'frame pointer' (see real_fp)
  intptr_t* fp() const          { assert_absolute(); return _fp; }
  void set_fp(intptr_t* newfp)  { _fp = newfp; }
  int offset_fp() const         { assert_offset();  return _offset_fp; }
  void set_offset_fp(int value) { assert_on_heap(); _offset_fp = value; }

  inline address* sender_pc_addr() const;

  // expression stack tos if we are nested in a java call
  intptr_t* interpreter_frame_last_sp() const;

  template <typename RegisterMapT>
  static void update_map_with_saved_link(RegisterMapT* map, intptr_t** link_addr);

  // deoptimization support
  void interpreter_frame_set_last_sp(intptr_t* sp);

  static jint interpreter_frame_expression_stack_direction() { return -1; }

  // returns the sending frame, without applying any barriers
  inline frame sender_raw(RegisterMap* map) const;

#endif // CPU_X86_FRAME_X86_HPP
