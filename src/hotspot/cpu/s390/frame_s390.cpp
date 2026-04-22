/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2016, 2023 SAP SE. All rights reserved.
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

#include "compiler/oopMap.hpp"
#include "interpreter/interpreter.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "oops/markWord.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/monitorChunk.hpp"
#include "runtime/os.inline.hpp"
#include "runtime/signature.hpp"
#include "runtime/stubCodeGenerator.hpp"
#include "runtime/stubRoutines.hpp"
#include "vmreg_s390.inline.hpp"
#ifdef COMPILER1
#include "c1/c1_Runtime1.hpp"
#include "runtime/vframeArray.hpp"
#endif

// Major contributions by Aha, AS.

#ifdef ASSERT
void RegisterMap::check_location_valid() {
}
#endif // ASSERT


// Profiling/safepoint support

bool frame::safe_for_sender(JavaThread *thread) {
  address sp = (address)_sp;
  address fp = (address)_fp;
  address unextended_sp = (address)_unextended_sp;

  // consider stack guards when trying to determine "safe" stack pointers
  // sp must be within the usable part of the stack (not in guards)
  if (!thread->is_in_usable_stack(sp)) {
    return false;
  }

  // Unextended sp must be within the stack
  if (!thread->is_in_full_stack_checked(unextended_sp)) {
    return false;
  }

  // An fp must be within the stack and above (but not equal) sp.
  bool fp_safe = thread->is_in_stack_range_excl(fp, sp);
  // An interpreter fp must be fp_safe.
  // Moreover, it must be at a distance at least the size of the z_ijava_state structure.
  bool fp_interp_safe = fp_safe && ((fp - sp) >= z_ijava_state_size);

  // We know sp/unextended_sp are safe, only fp is questionable here

  // If the current frame is known to the code cache then we can attempt to
  // construct the sender and do some validation of it. This goes a long way
  // toward eliminating issues when we get in frame construction code

  if (_cb != nullptr ) {

    // First check if the frame is complete and the test is reliable.
    // Unfortunately we can only check frame completeness for runtime stubs.
    // Other generic buffer blobs are more problematic so we just assume they are OK.
    // Adapter blobs never have a complete frame and are never OK.
    // nmethods should be OK on s390.
    if (!_cb->is_frame_complete_at(_pc)) {
      if (_cb->is_adapter_blob() || _cb->is_runtime_stub()) {
        return false;
      }
    }

    // Could just be some random pointer within the codeBlob.
    if (!_cb->code_contains(_pc)) {
      return false;
    }

    // Entry frame checks
    if (is_entry_frame()) {
      // An entry frame must have a valid fp.
      return fp_safe && is_entry_frame_valid(thread);
    }

    if (is_interpreted_frame() && !fp_interp_safe) {
      return false;
    }

    // At this point, there still is a chance that fp_safe is false.
    // In particular, fp might be null. So let's check and
    // bail out before we actually dereference from fp.
    if (!fp_safe) {
      return false;
    }

    z_common_abi* sender_abi = (z_common_abi*)fp;
    intptr_t* sender_sp = (intptr_t*) fp;
    address   sender_pc = (address)   sender_abi->return_pc;

    // We must always be able to find a recognizable pc.
    CodeBlob* sender_blob = CodeCache::find_blob(sender_pc);
    if (sender_blob == nullptr) {
      return false;
    }

    // It should be safe to construct the sender though it might not be valid.

    frame sender(sender_sp, sender_pc);

    // Do we have a valid fp?
    address sender_fp = (address) sender.fp();

    // sender_fp must be within the stack and above (but not
    // equal) current frame's fp.
    if (!thread->is_in_stack_range_excl(sender_fp, fp)) {
      return false;
    }

    // If the potential sender is the interpreter then we can do some more checking.
    if (Interpreter::contains(sender_pc)) {
      return sender.is_interpreted_frame_valid(thread);
    }

    // Could just be some random pointer within the codeBlob.
    if (!sender.cb()->code_contains(sender_pc)) {
      return false;
    }

    // We should never be able to see an adapter if the current frame is something from code cache.
    if (sender_blob->is_adapter_blob()) {
      return false;
    }

    if (sender.is_entry_frame()) {
      return sender.is_entry_frame_valid(thread);
    }

    // Frame size is always greater than zero. If the sender frame size is zero or less,
    // something is really weird and we better give up.
    if (sender_blob->frame_size() <= 0) {
      return false;
    }

    return true;
  }

  // Must be native-compiled frame. Since sender will try and use fp to find
  // linkages it must be safe

  if (!fp_safe) {
    return false;
  }

  return true;
}

bool frame::is_interpreted_frame() const {
  return Interpreter::contains(pc());
}

// locals

void frame::interpreter_frame_set_locals(intptr_t* locs)  {
  assert(is_interpreted_frame(), "interpreted frame expected");
  // set relativized locals
  *addr_at(_z_ijava_idx(locals)) = (intptr_t) (locs - fp());
}

// sender_sp

intptr_t* frame::interpreter_frame_sender_sp() const {
  return sender_sp();
}

frame frame::sender_for_entry_frame(RegisterMap *map) const {
  assert(map != nullptr, "map must be set");
  // Java frame called from C. Skip all C frames and return top C
  // frame of that chunk as the sender.
  JavaFrameAnchor* jfa = entry_frame_call_wrapper()->anchor();

  assert(!entry_frame_is_first(), "next Java sp must be non zero");
  assert(jfa->last_Java_sp() > _sp, "must be above this frame on stack");

  map->clear();

  assert(map->include_argument_oops(), "should be set by clear");

  if (jfa->last_Java_pc() != nullptr) {
    frame fr(jfa->last_Java_sp(), jfa->last_Java_pc());
    return fr;
  }
  // Last_java_pc is not set if we come here from compiled code.
  frame fr(jfa->last_Java_sp());
  return fr;
}

UpcallStub::FrameData* UpcallStub::frame_data_for_frame(const frame& frame) const {
  assert(frame.is_upcall_stub_frame(), "wrong frame");
  // need unextended_sp here, since normal sp is wrong for interpreter callees
  return reinterpret_cast<UpcallStub::FrameData*>(
    reinterpret_cast<address>(frame.unextended_sp()) + in_bytes(_frame_data_offset));
}

bool frame::upcall_stub_frame_is_first() const {
  assert(is_upcall_stub_frame(), "must be optimized entry frame");
  UpcallStub* blob = _cb->as_upcall_stub();
  JavaFrameAnchor* jfa = blob->jfa_for_frame(*this);
  return jfa->last_Java_sp() == nullptr;
}

frame frame::sender_for_upcall_stub_frame(RegisterMap* map) const {
  assert(map != nullptr, "map must be set");
  UpcallStub* blob = _cb->as_upcall_stub();
  // Java frame called from C; skip all C frames and return top C
  // frame of that chunk as the sender
  JavaFrameAnchor* jfa = blob->jfa_for_frame(*this);
  assert(!upcall_stub_frame_is_first(), "must have a frame anchor to go back to");
  assert(jfa->last_Java_sp() > sp(), "must be above this frame on stack");
  map->clear();
  assert(map->include_argument_oops(), "should be set by clear");
  frame fr(jfa->last_Java_sp(), jfa->last_Java_pc());

  return fr;
}

JavaThread** frame::saved_thread_address(const frame& f) {
  Unimplemented();
  return nullptr;
}

frame frame::sender_for_interpreter_frame(RegisterMap *map) const {
  // Pass callers sender_sp as unextended_sp.
  return frame(sender_sp(), sender_pc(), (intptr_t*)(ijava_state()->sender_sp));
}

void frame::patch_pc(Thread* thread, address pc) {
  assert(_cb == CodeCache::find_blob(pc), "unexpected pc");
  address* pc_addr = (address*)&(own_abi()->return_pc);

  if (TracePcPatching) {
    tty->print_cr("patch_pc at address  " PTR_FORMAT " [" PTR_FORMAT " -> " PTR_FORMAT "] ",
                  p2i(&((address*) _sp)[-1]), p2i(((address*) _sp)[-1]), p2i(pc));
  }
  assert(!Continuation::is_return_barrier_entry(*pc_addr), "return barrier");
  assert(_pc == *pc_addr || pc == *pc_addr || nullptr == *pc_addr,
         "must be (pc: " INTPTR_FORMAT " _pc: " INTPTR_FORMAT " pc_addr: " INTPTR_FORMAT
         " *pc_addr: " INTPTR_FORMAT  " sp: " INTPTR_FORMAT ")",
         p2i(pc), p2i(_pc), p2i(pc_addr), p2i(*pc_addr), p2i(sp()));
  DEBUG_ONLY(address old_pc = _pc;)
  own_abi()->return_pc = (uint64_t)pc;
  _pc = pc; // must be set before call to get_deopt_original_pc
  address original_pc = get_deopt_original_pc();
  if (original_pc != nullptr) {
    // assert(original_pc == _pc, "expected original to be stored before patching");
    _deopt_state = is_deoptimized;
    _pc = original_pc;
  } else {
    _deopt_state = not_deoptimized;
  }
  assert(!is_compiled_frame() || !_cb->as_nmethod()->is_deopt_entry(_pc), "must be");

  #ifdef ASSERT
  {
    frame f(this->sp(), pc, this->unextended_sp());
    assert(f.is_deoptimized_frame() == this->is_deoptimized_frame() && f.pc() == this->pc() && f.raw_pc() == this->raw_pc(),
           "must be (f.is_deoptimized_frame(): %d this->is_deoptimized_frame(): %d "
           "f.pc(): " INTPTR_FORMAT " this->pc(): " INTPTR_FORMAT " f.raw_pc(): " INTPTR_FORMAT " this->raw_pc(): " INTPTR_FORMAT ")",
           f.is_deoptimized_frame(), this->is_deoptimized_frame(), p2i(f.pc()), p2i(this->pc()), p2i(f.raw_pc()), p2i(this->raw_pc()));
  }
  #endif
}

bool frame::is_interpreted_frame_valid(JavaThread* thread) const {
  assert(is_interpreted_frame(), "Not an interpreted frame");
  // These are reasonable sanity checks
  if (fp() == nullptr || (intptr_t(fp()) & (wordSize-1)) != 0) {
    return false;
  }
  if (sp() == nullptr || (intptr_t(sp()) & (wordSize-1)) != 0) {
    return false;
  }
  int min_frame_slots = (z_common_abi_size + z_ijava_state_size) / sizeof(intptr_t);
  if (fp() - min_frame_slots < sp()) {
    return false;
  }
  // These are hacks to keep us out of trouble.
  // The problem with these is that they mask other problems
  if (fp() <= sp()) {        // this attempts to deal with unsigned comparison above
    return false;
  }

  // do some validation of frame elements

  // first the method
  // Need to use "unchecked" versions to avoid "z_istate_magic_number" assertion.
  Method* m = (Method*)(ijava_state_unchecked()->method);

  // validate the method we'd find in this potential sender
  if (!Method::is_valid_method(m)) return false;

  // stack frames shouldn't be much larger than max_stack elements
  // this test requires the use of unextended_sp which is the sp as seen by
  // the current frame, and not sp which is the "raw" pc which could point
  // further because of local variables of the callee method inserted after
  // method arguments
  if (fp() - unextended_sp() > 1024 + m->max_stack()*Interpreter::stackElementSize) {
    return false;
  }

  // validate bci/bcx
  address bcp = (address)(ijava_state_unchecked()->bcp);
  if (m->validate_bci_from_bcp(bcp) < 0) {
    return false;
  }

  // validate constantPoolCache*
  ConstantPoolCache* cp = (ConstantPoolCache*)(ijava_state_unchecked()->cpoolCache);
  if (MetaspaceObj::is_valid(cp) == false) return false;

  // validate locals
  address locals = (address)interpreter_frame_locals();
  return thread->is_in_stack_range_incl(locals, (address)fp());
}

BasicType frame::interpreter_frame_result(oop* oop_result, jvalue* value_result) {
  assert(is_interpreted_frame(), "interpreted frame expected");
  Method* method = interpreter_frame_method();
  BasicType type = method->result_type();

  if (method->is_native()) {
    address lresult = (address)&(ijava_state()->lresult);
    address fresult = (address)&(ijava_state()->fresult);

    switch (type) {
      case T_OBJECT:
      case T_ARRAY: {
        *oop_result = cast_to_oop((void*) ijava_state()->oop_tmp);
        break;
      }
      // We use std/stfd to store the values.
      case T_BOOLEAN : value_result->z = (jboolean) *(unsigned long*)lresult; break;
      case T_INT     : value_result->i = (jint)     *(long*)lresult;          break;
      case T_CHAR    : value_result->c = (jchar)    *(unsigned long*)lresult; break;
      case T_SHORT   : value_result->s = (jshort)   *(long*)lresult;          break;
      case T_BYTE    : value_result->z = (jbyte)    *(long*)lresult;          break;
      case T_LONG    : value_result->j = (jlong)    *(long*)lresult;          break;
      case T_FLOAT   : value_result->f = (jfloat)   *(float*)fresult;        break;
      case T_DOUBLE  : value_result->d = (jdouble)  *(double*)fresult;        break;
      case T_VOID    : break; // Nothing to do.
      default        : ShouldNotReachHere();
    }
  } else {
    intptr_t* tos_addr = interpreter_frame_tos_address();
    switch (type) {
      case T_OBJECT:
      case T_ARRAY: {
       oop obj = *(oop*)tos_addr;
       assert(Universe::is_in_heap_or_null(obj), "sanity check");
       *oop_result = obj;
       break;
      }
      case T_BOOLEAN : value_result->z = (jboolean) *(jint*)tos_addr; break;
      case T_BYTE    : value_result->b = (jbyte) *(jint*)tos_addr; break;
      case T_CHAR    : value_result->c = (jchar) *(jint*)tos_addr; break;
      case T_SHORT   : value_result->s = (jshort) *(jint*)tos_addr; break;
      case T_INT     : value_result->i = *(jint*)tos_addr; break;
      case T_LONG    : value_result->j = *(jlong*)tos_addr; break;
      case T_FLOAT   : value_result->f = *(jfloat*)tos_addr; break;
      case T_DOUBLE  : value_result->d = *(jdouble*)tos_addr; break;
      case T_VOID    : break; // Nothing to do.
      default        : ShouldNotReachHere();
    }
  }

  return type;
}


// Dump all frames starting a given C stack-pointer.
// Use max_frames to limit the number of traced frames.
void frame::back_trace(outputStream* st, intptr_t* start_sp, intptr_t* top_pc, unsigned long flags, int max_frames) {

  static char buf[ 150 ];

  bool print_outgoing_arguments = flags & 0x1;
  bool print_istate_pointers    = flags & 0x2;
  int num = 0;

  intptr_t* current_sp = (intptr_t*) start_sp;
  int last_num_jargs = 0;
  int frame_type = 0;
  int last_frame_type = 0;

  while (current_sp) {
    intptr_t* current_fp = (intptr_t*) *current_sp;
    address   current_pc = (num == 0)
                           ? (address) top_pc
                           : (address) *((intptr_t*)(((address) current_sp) + _z_abi(return_pc)));

    if ((intptr_t*) current_fp != nullptr && (intptr_t*) current_fp <= current_sp) {
      st->print_cr("ERROR: corrupt stack");
      return;
    }

    st->print("#%-3d ", num);
    const char* type_name = "    ";
    const char* function_name = nullptr;

    // Detect current frame's frame_type, default to 'C frame'.
    frame_type = 0;

    CodeBlob* blob = nullptr;

    if (Interpreter::contains(current_pc)) {
      frame_type = 1;
    } else if (StubRoutines::contains(current_pc)) {
      if (StubRoutines::returns_to_call_stub(current_pc)) {
        frame_type = 2;
      } else {
        frame_type = 4;
        type_name = "stu";
        StubCodeDesc* desc = StubCodeDesc::desc_for (current_pc);
        if (desc) {
          function_name = desc->name();
        } else {
          function_name = "unknown stub";
        }
      }
    } else if (CodeCache::contains(current_pc)) {
      blob = CodeCache::find_blob(current_pc);
      if (blob) {
        if (blob->is_nmethod()) {
          frame_type = 3;
        } else if (blob->is_deoptimization_stub()) {
          frame_type = 4;
          type_name = "deo";
          function_name = "deoptimization blob";
        } else if (blob->is_uncommon_trap_stub()) {
          frame_type = 4;
          type_name = "uct";
          function_name = "uncommon trap blob";
        } else if (blob->is_exception_stub()) {
          frame_type = 4;
          type_name = "exc";
          function_name = "exception blob";
        } else if (blob->is_safepoint_stub()) {
          frame_type = 4;
          type_name = "saf";
          function_name = "safepoint blob";
        } else if (blob->is_runtime_stub()) {
          frame_type = 4;
          type_name = "run";
          function_name = ((RuntimeStub *)blob)->name();
        } else if (blob->is_method_handles_adapter_blob()) {
          frame_type = 4;
          type_name = "mha";
          function_name = "method handles adapter blob";
        } else {
          frame_type = 4;
          type_name = "blo";
          function_name = "unknown code blob";
        }
      } else {
        frame_type = 4;
        type_name = "blo";
        function_name = "unknown code blob";
      }
    }

    st->print("sp=" PTR_FORMAT " ", p2i(current_sp));

    if (frame_type == 0) {
      current_pc = (address) *((intptr_t*)(((address) current_sp) + _z_abi(gpr14)));
    }

    st->print("pc=" PTR_FORMAT " ", p2i(current_pc));
    st->print(" ");

    switch (frame_type) {
      case 0: // C frame:
        {
          st->print("    ");
          if (current_pc == nullptr) {
            st->print("? ");
          } else {
             // name
            int func_offset;
            char demangled_name[256];
            int demangled_name_len = 256;
            if (os::dll_address_to_function_name(current_pc, demangled_name, demangled_name_len, &func_offset)) {
              demangled_name[demangled_name_len-1] = '\0';
              st->print(func_offset == -1 ? "%s " : "%s+0x%x", demangled_name, func_offset);
            } else {
              st->print("? ");
            }
          }
        }
        break;

      case 1: // interpreter frame:
        {
          st->print(" i  ");

          if (last_frame_type != 1) last_num_jargs = 8;

          // name
          Method* method = *(Method**)((address)current_fp + _z_ijava_state_neg(method));
          if (method) {
            ResourceMark rm;
            if (method->is_synchronized()) st->print("synchronized ");
            if (method->is_static()) st->print("static ");
            if (method->is_native()) st->print("native ");
            method->name_and_sig_as_C_string(buf, sizeof(buf));
            st->print("%s ", buf);
          }
          else
            st->print("? ");

          intptr_t* tos = (intptr_t*) *(intptr_t*)((address)current_fp + _z_ijava_state_neg(esp));
          if (print_istate_pointers) {
            st->cr();
            st->print("     ");
            st->print("ts=" PTR_FORMAT " ", p2i(tos));
          }

          // Dump some Java stack slots.
          if (print_outgoing_arguments) {
            if (method->is_native()) {
#ifdef ASSERT
              intptr_t* cargs = (intptr_t*) (((address)current_sp) + _z_abi(carg_1));
              for (int i = 0; i < last_num_jargs; i++) {
                // Cargs is not prepushed.
                st->cr();
                st->print("        ");
                st->print(PTR_FORMAT, *(cargs));
                cargs++;
              }
#endif /* ASSERT */
            }
            else {
              if (tos) {
                for (int i = 0; i < last_num_jargs; i++) {
                  // tos+0 is prepushed, ignore.
                  tos++;
                  if (tos >= (intptr_t *)((address)current_fp + _z_ijava_state_neg(monitors)))
                    break;
                  st->cr();
                  st->print("        ");
                  st->print(PTR_FORMAT " %+.3e %+.3le", *(tos), *(float*)(tos), *(double*)(tos));
                }
              }
            }
            last_num_jargs = method->size_of_parameters();
          }
        }
        break;

      case 2: // entry frame:
        {
          st->print("v2i ");

          // name
          st->print("call stub");
        }
        break;

      case 3: // compiled frame:
        {
          st->print(" c  ");

          // name
          Method* method = ((nmethod *)blob)->method();
          if (method) {
            ResourceMark rm;
            method->name_and_sig_as_C_string(buf, sizeof(buf));
            st->print("%s ", buf);
          }
          else
            st->print("? ");
        }
        break;

      case 4: // named frames
        {
          st->print("%s ", type_name);

          // name
          if (function_name)
            st->print("%s", function_name);
        }
        break;

      default:
        break;
    }

    st->cr();
    st->flush();

    current_sp = current_fp;
    last_frame_type = frame_type;
    num++;
    // Check for maximum # of frames, and stop when reached.
    if (max_frames > 0 && --max_frames == 0)
      break;
  }

}

// Convenience function for calls from the debugger.

extern "C" void bt(intptr_t* start_sp,intptr_t* top_pc) {
  frame::back_trace(tty,start_sp, top_pc, 0);
}

extern "C" void bt_full(intptr_t* start_sp,intptr_t* top_pc) {
  frame::back_trace(tty,start_sp, top_pc, (unsigned long)(long)-1);
}


// Function for tracing a limited number of frames.
// Use this one if you only need to see the "top of stack" frames.
extern "C" void bt_max(intptr_t *start_sp, intptr_t *top_pc, int max_frames) {
  frame::back_trace(tty, start_sp, top_pc, 0, max_frames);
}

#if !defined(PRODUCT)

#define DESCRIBE_ADDRESS(name) \
  values.describe(frame_no, (intptr_t*)&ijava_state()->name, #name);

void frame::describe_pd(FrameValues& values, int frame_no) {
  if (is_interpreted_frame()) {
    // Describe z_ijava_state elements.
    DESCRIBE_ADDRESS(method);
    DESCRIBE_ADDRESS(locals);
    DESCRIBE_ADDRESS(monitors);
    DESCRIBE_ADDRESS(cpoolCache);
    DESCRIBE_ADDRESS(bcp);
    DESCRIBE_ADDRESS(mdx);
    DESCRIBE_ADDRESS(esp);
    DESCRIBE_ADDRESS(sender_sp);
    DESCRIBE_ADDRESS(top_frame_sp);
    DESCRIBE_ADDRESS(oop_tmp);
    DESCRIBE_ADDRESS(lresult);
    DESCRIBE_ADDRESS(fresult);
  }
}

#endif // !PRODUCT

intptr_t *frame::initial_deoptimization_info() {
  // Used to reset the saved FP.
  return fp();
}

BasicObjectLock* frame::interpreter_frame_monitor_end() const {
  return interpreter_frame_monitors();
}

intptr_t* frame::interpreter_frame_tos_at(jint offset) const {
  return &interpreter_frame_tos_address()[offset];
}

