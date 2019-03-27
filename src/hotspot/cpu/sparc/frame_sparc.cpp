/*
 * Copyright (c) 1997, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "code/codeCache.hpp"
#include "interpreter/interpreter.hpp"
#include "memory/resourceArea.hpp"
#include "oops/markOop.hpp"
#include "oops/method.hpp"
#include "oops/oop.inline.hpp"
#include "prims/methodHandles.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/monitorChunk.hpp"
#include "runtime/signature.hpp"
#include "runtime/stubCodeGenerator.hpp"
#include "runtime/stubRoutines.hpp"
#include "vmreg_sparc.inline.hpp"
#ifdef COMPILER1
#include "c1/c1_Runtime1.hpp"
#include "runtime/vframeArray.hpp"
#endif

void RegisterMap::pd_clear() {
  if (_thread->has_last_Java_frame()) {
    frame fr = _thread->last_frame();
    _window = fr.sp();
  } else {
    _window = NULL;
  }
  _younger_window = NULL;
}


// Unified register numbering scheme: each 32-bits counts as a register
// number, so all the V9 registers take 2 slots.
const static int R_L_nums[] = {0+040,2+040,4+040,6+040,8+040,10+040,12+040,14+040};
const static int R_I_nums[] = {0+060,2+060,4+060,6+060,8+060,10+060,12+060,14+060};
const static int R_O_nums[] = {0+020,2+020,4+020,6+020,8+020,10+020,12+020,14+020};
const static int R_G_nums[] = {0+000,2+000,4+000,6+000,8+000,10+000,12+000,14+000};
static RegisterMap::LocationValidType bad_mask = 0;
static RegisterMap::LocationValidType R_LIO_mask = 0;
static bool register_map_inited = false;

static void register_map_init() {
  if (!register_map_inited) {
    register_map_inited = true;
    int i;
    for (i = 0; i < 8; i++) {
      assert(R_L_nums[i] < RegisterMap::location_valid_type_size, "in first chunk");
      assert(R_I_nums[i] < RegisterMap::location_valid_type_size, "in first chunk");
      assert(R_O_nums[i] < RegisterMap::location_valid_type_size, "in first chunk");
      assert(R_G_nums[i] < RegisterMap::location_valid_type_size, "in first chunk");
    }

    bad_mask |= (1LL << R_O_nums[6]); // SP
    bad_mask |= (1LL << R_O_nums[7]); // cPC
    bad_mask |= (1LL << R_I_nums[6]); // FP
    bad_mask |= (1LL << R_I_nums[7]); // rPC
    bad_mask |= (1LL << R_G_nums[2]); // TLS
    bad_mask |= (1LL << R_G_nums[7]); // reserved by libthread

    for (i = 0; i < 8; i++) {
      R_LIO_mask |= (1LL << R_L_nums[i]);
      R_LIO_mask |= (1LL << R_I_nums[i]);
      R_LIO_mask |= (1LL << R_O_nums[i]);
    }
  }
}


address RegisterMap::pd_location(VMReg regname) const {
  register_map_init();

  assert(regname->is_reg(), "sanity check");
  // Only the GPRs get handled this way
  if( !regname->is_Register())
    return NULL;

  // don't talk about bad registers
  if ((bad_mask & ((LocationValidType)1 << regname->value())) != 0) {
    return NULL;
  }

  // Convert to a GPR
  Register reg;
  int second_word = 0;
  // 32-bit registers for in, out and local
  if (!regname->is_concrete()) {
    // HMM ought to return NULL for any non-concrete (odd) vmreg
    // this all tied up in the fact we put out double oopMaps for
    // register locations. When that is fixed we'd will return NULL
    // (or assert here).
    reg = regname->prev()->as_Register();
    second_word = sizeof(jint);
  } else {
    reg = regname->as_Register();
  }
  if (reg->is_out()) {
    return _younger_window == NULL ? NULL :
      second_word + (address)&_younger_window[reg->after_save()->sp_offset_in_saved_window()];
  }
  if (reg->is_local() || reg->is_in()) {
    assert(_window != NULL, "Window should be available");
    return second_word + (address)&_window[reg->sp_offset_in_saved_window()];
  }
  // Only the window'd GPRs get handled this way; not the globals.
  return NULL;
}


#ifdef ASSERT
void RegisterMap::check_location_valid() {
  register_map_init();
  assert((_location_valid[0] & bad_mask) == 0, "cannot have special locations for SP,FP,TLS,etc.");
}
#endif

// We are shifting windows.  That means we are moving all %i to %o,
// getting rid of all current %l, and keeping all %g.  This is only
// complicated if any of the location pointers for these are valid.
// The normal case is that everything is in its standard register window
// home, and _location_valid[0] is zero.  In that case, this routine
// does exactly nothing.
void RegisterMap::shift_individual_registers() {
  if (!update_map())  return;  // this only applies to maps with locations
  register_map_init();
  check_location_valid();

  LocationValidType lv = _location_valid[0];
  LocationValidType lv0 = lv;

  lv &= ~R_LIO_mask;  // clear %l, %o, %i regs

  // if we cleared some non-%g locations, we may have to do some shifting
  if (lv != lv0) {
    // copy %i0-%i5 to %o0-%o5, if they have special locations
    // This can happen in within stubs which spill argument registers
    // around a dynamic link operation, such as resolve_opt_virtual_call.
    for (int i = 0; i < 8; i++) {
      if (lv0 & (1LL << R_I_nums[i])) {
        _location[R_O_nums[i]] = _location[R_I_nums[i]];
        lv |=  (1LL << R_O_nums[i]);
      }
    }
  }

  _location_valid[0] = lv;
  check_location_valid();
}

bool frame::safe_for_sender(JavaThread *thread) {

  address _SP = (address) sp();
  address _FP = (address) fp();
  address _UNEXTENDED_SP = (address) unextended_sp();
  // sp must be within the stack
  bool sp_safe = (_SP <= thread->stack_base()) &&
                 (_SP >= thread->stack_base() - thread->stack_size());

  if (!sp_safe) {
    return false;
  }

  // unextended sp must be within the stack and above or equal sp
  bool unextended_sp_safe = (_UNEXTENDED_SP <= thread->stack_base()) &&
                            (_UNEXTENDED_SP >= _SP);

  if (!unextended_sp_safe) return false;

  // an fp must be within the stack and above (but not equal) sp
  bool fp_safe = (_FP <= thread->stack_base()) &&
                 (_FP > _SP);

  // We know sp/unextended_sp are safe only fp is questionable here

  // If the current frame is known to the code cache then we can attempt to
  // to construct the sender and do some validation of it. This goes a long way
  // toward eliminating issues when we get in frame construction code

  if (_cb != NULL ) {

    // First check if frame is complete and tester is reliable
    // Unfortunately we can only check frame complete for runtime stubs and nmethod
    // other generic buffer blobs are more problematic so we just assume they are
    // ok. adapter blobs never have a frame complete and are never ok.

    if (!_cb->is_frame_complete_at(_pc)) {
      if (_cb->is_compiled() || _cb->is_adapter_blob() || _cb->is_runtime_stub()) {
        return false;
      }
    }

    // Could just be some random pointer within the codeBlob
    if (!_cb->code_contains(_pc)) {
      return false;
    }

    // Entry frame checks
    if (is_entry_frame()) {
      // an entry frame must have a valid fp.
      return fp_safe && is_entry_frame_valid(thread);
    }

    intptr_t* younger_sp = sp();
    intptr_t* _SENDER_SP = sender_sp(); // sender is actually just _FP
    bool adjusted_stack = is_interpreted_frame();

    address   sender_pc = (address)younger_sp[I7->sp_offset_in_saved_window()] + pc_return_offset;


    // We must always be able to find a recognizable pc
    CodeBlob* sender_blob = CodeCache::find_blob_unsafe(sender_pc);
    if (sender_pc == NULL ||  sender_blob == NULL) {
      return false;
    }

    // Could be a zombie method
    if (sender_blob->is_zombie() || sender_blob->is_unloaded()) {
      return false;
    }

    // It should be safe to construct the sender though it might not be valid

    frame sender(_SENDER_SP, younger_sp, adjusted_stack);

    // Do we have a valid fp?
    address sender_fp = (address) sender.fp();

    // an fp must be within the stack and above (but not equal) current frame's _FP

    bool sender_fp_safe = (sender_fp <= thread->stack_base()) &&
                   (sender_fp > _FP);

    if (!sender_fp_safe) {
      return false;
    }


    // If the potential sender is the interpreter then we can do some more checking
    if (Interpreter::contains(sender_pc)) {
      return sender.is_interpreted_frame_valid(thread);
    }

    // Could just be some random pointer within the codeBlob
    if (!sender.cb()->code_contains(sender_pc)) {
      return false;
    }

    // We should never be able to see an adapter if the current frame is something from code cache
    if (sender_blob->is_adapter_blob()) {
      return false;
    }

    if (sender.is_entry_frame()) {
      // Validate the JavaCallWrapper an entry frame must have

      address jcw = (address)sender.entry_frame_call_wrapper();

      bool jcw_safe = (jcw <= thread->stack_base()) && (jcw > sender_fp);

      return jcw_safe;
    }

    // If the frame size is 0 something (or less) is bad because every nmethod has a non-zero frame size
    // because you must allocate window space

    if (sender_blob->frame_size() <= 0) {
      assert(!sender_blob->is_compiled(), "should count return address at least");
      return false;
    }

    // The sender should positively be an nmethod or call_stub. On sparc we might in fact see something else.
    // The cause of this is because at a save instruction the O7 we get is a leftover from an earlier
    // window use. So if a runtime stub creates two frames (common in fastdebug/debug) then we see the
    // stale pc. So if the sender blob is not something we'd expect we have little choice but to declare
    // the stack unwalkable. pd_get_top_frame_for_signal_handler tries to recover from this by unwinding
    // that initial frame and retrying.

    if (!sender_blob->is_compiled()) {
      return false;
    }

    // Could put some more validation for the potential non-interpreted sender
    // frame we'd create by calling sender if I could think of any. Wait for next crash in forte...

    // One idea is seeing if the sender_pc we have is one that we'd expect to call to current cb

    // We've validated the potential sender that would be created

    return true;

  }

  // Must be native-compiled frame. Since sender will try and use fp to find
  // linkages it must be safe

  if (!fp_safe) return false;

  // could try and do some more potential verification of native frame if we could think of some...

  return true;
}

// constructors

// Construct an unpatchable, deficient frame
void frame::init(intptr_t* sp, address pc, CodeBlob* cb) {
  assert( (((intptr_t)sp & (wordSize-1)) == 0), "frame constructor passed an invalid sp");
  _sp = sp;
  _younger_sp = NULL;
  _pc = pc;
  _cb = cb;
  _sp_adjustment_by_callee = 0;
  assert(pc == NULL && cb == NULL || pc != NULL, "can't have a cb and no pc!");
  if (_cb == NULL && _pc != NULL ) {
    _cb = CodeCache::find_blob(_pc);
  }
  _deopt_state = unknown;
}

frame::frame(intptr_t* sp, unpatchable_t, address pc, CodeBlob* cb) {
  init(sp, pc, cb);
}

frame::frame(intptr_t* sp, intptr_t* younger_sp, bool younger_frame_is_interpreted) :
  _sp(sp),
  _younger_sp(younger_sp),
  _deopt_state(unknown),
  _sp_adjustment_by_callee(0) {
  if (younger_sp == NULL) {
    // make a deficient frame which doesn't know where its PC is
    _pc = NULL;
    _cb = NULL;
  } else {
    _pc = (address)younger_sp[I7->sp_offset_in_saved_window()] + pc_return_offset;
    assert( (intptr_t*)younger_sp[FP->sp_offset_in_saved_window()] == (intptr_t*)((intptr_t)sp - STACK_BIAS), "younger_sp must be valid");
    // Any frame we ever build should always "safe" therefore we should not have to call
    // find_blob_unsafe
    // In case of native stubs, the pc retrieved here might be
    // wrong.  (the _last_native_pc will have the right value)
    // So do not put add any asserts on the _pc here.
  }

  if (_pc != NULL)
    _cb = CodeCache::find_blob(_pc);

  // Check for MethodHandle call sites.
  if (_cb != NULL) {
    CompiledMethod* nm = _cb->as_compiled_method_or_null();
    if (nm != NULL) {
      if (nm->is_deopt_mh_entry(_pc) || nm->is_method_handle_return(_pc)) {
        _sp_adjustment_by_callee = (intptr_t*) ((intptr_t) sp[L7_mh_SP_save->sp_offset_in_saved_window()] + STACK_BIAS) - sp;
        // The SP is already adjusted by this MH call site, don't
        // overwrite this value with the wrong interpreter value.
        younger_frame_is_interpreted = false;
      }
    }
  }

  if (younger_frame_is_interpreted) {
    // compute adjustment to this frame's SP made by its interpreted callee
    _sp_adjustment_by_callee = (intptr_t*) ((intptr_t) younger_sp[I5_savedSP->sp_offset_in_saved_window()] + STACK_BIAS) - sp;
  }

  // It is important that the frame is fully constructed when we do
  // this lookup as get_deopt_original_pc() needs a correct value for
  // unextended_sp() which uses _sp_adjustment_by_callee.
  if (_pc != NULL) {
    address original_pc = CompiledMethod::get_deopt_original_pc(this);
    if (original_pc != NULL) {
      _pc = original_pc;
      _deopt_state = is_deoptimized;
    } else {
      _deopt_state = not_deoptimized;
    }
  }
}

#ifndef PRODUCT
// This is a generic constructor which is only used by pns() in debug.cpp.
frame::frame(void* sp, void* fp, void* pc) {
  init((intptr_t*)sp, (address)pc, NULL);
}

extern "C" void findpc(intptr_t x);

void frame::pd_ps() {
  intptr_t* curr_sp = sp();
  intptr_t* prev_sp = curr_sp - 1;
  intptr_t *pc = NULL;
  intptr_t *next_pc = NULL;
  int count = 0;
  tty->print_cr("register window backtrace from " INTPTR_FORMAT ":", p2i(curr_sp));
  while (curr_sp != NULL && ((intptr_t)curr_sp & 7) == 0 && curr_sp > prev_sp && curr_sp < prev_sp+1000) {
    pc      = next_pc;
    next_pc = (intptr_t*) curr_sp[I7->sp_offset_in_saved_window()];
    tty->print("[%d] curr_sp=" INTPTR_FORMAT " pc=", count, p2i(curr_sp));
    findpc((intptr_t)pc);
    if (WizardMode && Verbose) {
      // print register window contents also
      tty->print_cr("    L0..L7: {"
                    INTPTR_FORMAT " " INTPTR_FORMAT " " INTPTR_FORMAT " " INTPTR_FORMAT " "
                    INTPTR_FORMAT " " INTPTR_FORMAT " " INTPTR_FORMAT " " INTPTR_FORMAT " ",
                    curr_sp[0+0], curr_sp[0+1], curr_sp[0+2], curr_sp[0+3],
                    curr_sp[0+4], curr_sp[0+5], curr_sp[0+6], curr_sp[0+7]);
      tty->print_cr("    I0..I7: {"
                    INTPTR_FORMAT " " INTPTR_FORMAT " " INTPTR_FORMAT " " INTPTR_FORMAT " "
                    INTPTR_FORMAT " " INTPTR_FORMAT " " INTPTR_FORMAT " " INTPTR_FORMAT " ",
                    curr_sp[8+0], curr_sp[8+1], curr_sp[8+2], curr_sp[8+3],
                    curr_sp[8+4], curr_sp[8+5], curr_sp[8+6], curr_sp[8+7]);
      // (and print stack frame contents too??)

      CodeBlob *b = CodeCache::find_blob((address) pc);
      if (b != NULL) {
        if (b->is_nmethod()) {
          Method* m = ((nmethod*)b)->method();
          int nlocals = m->max_locals();
          int nparams  = m->size_of_parameters();
          tty->print_cr("compiled java method (locals = %d, params = %d)", nlocals, nparams);
        }
      }
    }
    prev_sp = curr_sp;
    curr_sp = (intptr_t *)curr_sp[FP->sp_offset_in_saved_window()];
    curr_sp = (intptr_t *)((intptr_t)curr_sp + STACK_BIAS);
    count += 1;
  }
  if (curr_sp != NULL)
    tty->print("[%d] curr_sp=" INTPTR_FORMAT " [bogus sp!]", count, p2i(curr_sp));
}

#endif // PRODUCT

bool frame::is_interpreted_frame() const  {
  return Interpreter::contains(pc());
}

// sender_sp

intptr_t* frame::interpreter_frame_sender_sp() const {
  assert(is_interpreted_frame(), "interpreted frame expected");
  return fp();
}

void frame::set_interpreter_frame_sender_sp(intptr_t* sender_sp) {
  assert(is_interpreted_frame(), "interpreted frame expected");
  Unimplemented();
}

frame frame::sender_for_entry_frame(RegisterMap *map) const {
  assert(map != NULL, "map must be set");
  // Java frame called from C; skip all C frames and return top C
  // frame of that chunk as the sender
  JavaFrameAnchor* jfa = entry_frame_call_wrapper()->anchor();
  assert(!entry_frame_is_first(), "next Java fp must be non zero");
  assert(jfa->last_Java_sp() > _sp, "must be above this frame on stack");
  intptr_t* last_Java_sp = jfa->last_Java_sp();
  // Since we are walking the stack now this nested anchor is obviously walkable
  // even if it wasn't when it was stacked.
  if (!jfa->walkable()) {
    // Capture _last_Java_pc (if needed) and mark anchor walkable.
    jfa->capture_last_Java_pc(_sp);
  }
  assert(jfa->last_Java_pc() != NULL, "No captured pc!");
  map->clear();
  map->make_integer_regs_unsaved();
  map->shift_window(last_Java_sp, NULL);
  assert(map->include_argument_oops(), "should be set by clear");
  return frame(last_Java_sp, frame::unpatchable, jfa->last_Java_pc());
}

frame frame::sender_for_interpreter_frame(RegisterMap *map) const {
  ShouldNotCallThis();
  return sender(map);
}

frame frame::sender_for_compiled_frame(RegisterMap *map) const {
  ShouldNotCallThis();
  return sender(map);
}

frame frame::sender(RegisterMap* map) const {
  assert(map != NULL, "map must be set");

  assert(CodeCache::find_blob_unsafe(_pc) == _cb, "inconsistent");

  // Default is not to follow arguments; update it accordingly below
  map->set_include_argument_oops(false);

  if (is_entry_frame()) return sender_for_entry_frame(map);

  intptr_t* younger_sp = sp();
  intptr_t* sp         = sender_sp();

  // Note:  The version of this operation on any platform with callee-save
  //        registers must update the register map (if not null).
  //        In order to do this correctly, the various subtypes of
  //        of frame (interpreted, compiled, glue, native),
  //        must be distinguished.  There is no need on SPARC for
  //        such distinctions, because all callee-save registers are
  //        preserved for all frames via SPARC-specific mechanisms.
  //
  //        *** HOWEVER, *** if and when we make any floating-point
  //        registers callee-saved, then we will have to copy over
  //        the RegisterMap update logic from the Intel code.

  // The constructor of the sender must know whether this frame is interpreted so it can set the
  // sender's _sp_adjustment_by_callee field.  An osr adapter frame was originally
  // interpreted but its pc is in the code cache (for c1 -> osr_frame_return_id stub), so it must be
  // explicitly recognized.


  bool frame_is_interpreted = is_interpreted_frame();
  if (frame_is_interpreted) {
    map->make_integer_regs_unsaved();
    map->shift_window(sp, younger_sp);
  } else if (_cb != NULL) {
    // Update the locations of implicitly saved registers to be their
    // addresses in the register save area.
    // For %o registers, the addresses of %i registers in the next younger
    // frame are used.
    map->shift_window(sp, younger_sp);
    if (map->update_map()) {
      // Tell GC to use argument oopmaps for some runtime stubs that need it.
      // For C1, the runtime stub might not have oop maps, so set this flag
      // outside of update_register_map.
      map->set_include_argument_oops(_cb->caller_must_gc_arguments(map->thread()));
      if (_cb->oop_maps() != NULL) {
        OopMapSet::update_register_map(this, map);
      }
    }
  }
  return frame(sp, younger_sp, frame_is_interpreted);
}


void frame::patch_pc(Thread* thread, address pc) {
  vmassert(_deopt_state != unknown, "frame is unpatchable");
  if(thread == Thread::current()) {
   StubRoutines::Sparc::flush_callers_register_windows_func()();
  }
  if (TracePcPatching) {
    // QQQ this assert is invalid (or too strong anyway) sice _pc could
    // be original pc and frame could have the deopt pc.
    // assert(_pc == *O7_addr() + pc_return_offset, "frame has wrong pc");
    tty->print_cr("patch_pc at address " INTPTR_FORMAT " [" INTPTR_FORMAT " -> " INTPTR_FORMAT "]",
                  p2i(O7_addr()), p2i(_pc), p2i(pc));
  }
  _cb = CodeCache::find_blob(pc);
  *O7_addr() = pc - pc_return_offset;
  _cb = CodeCache::find_blob(_pc);
  address original_pc = CompiledMethod::get_deopt_original_pc(this);
  if (original_pc != NULL) {
    assert(original_pc == _pc, "expected original to be stored before patching");
    _deopt_state = is_deoptimized;
  } else {
    _deopt_state = not_deoptimized;
  }
}


static bool sp_is_valid(intptr_t* old_sp, intptr_t* young_sp, intptr_t* sp) {
  return (((intptr_t)sp & (2*wordSize-1)) == 0 &&
          sp <= old_sp &&
          sp >= young_sp);
}


/*
  Find the (biased) sp that is just younger than old_sp starting at sp.
  If not found return NULL. Register windows are assumed to be flushed.
*/
intptr_t* frame::next_younger_sp_or_null(intptr_t* old_sp, intptr_t* sp) {

  intptr_t* previous_sp = NULL;
  intptr_t* orig_sp = sp;

  int max_frames = (old_sp - sp) / 16; // Minimum frame size is 16
  int max_frame2 = max_frames;
  while(sp != old_sp && sp_is_valid(old_sp, orig_sp, sp)) {
    if (max_frames-- <= 0)
      // too many frames have gone by; invalid parameters given to this function
      break;
    previous_sp = sp;
    sp = (intptr_t*)sp[FP->sp_offset_in_saved_window()];
    sp = (intptr_t*)((intptr_t)sp + STACK_BIAS);
  }

  return (sp == old_sp ? previous_sp : NULL);
}

/*
  Determine if "sp" is a valid stack pointer. "sp" is assumed to be younger than
  "valid_sp". So if "sp" is valid itself then it should be possible to walk frames
  from "sp" to "valid_sp". The assumption is that the registers windows for the
  thread stack in question are flushed.
*/
bool frame::is_valid_stack_pointer(intptr_t* valid_sp, intptr_t* sp) {
  return next_younger_sp_or_null(valid_sp, sp) != NULL;
}

bool frame::is_interpreted_frame_valid(JavaThread* thread) const {
  assert(is_interpreted_frame(), "Not an interpreted frame");
  // These are reasonable sanity checks
  if (fp() == 0 || (intptr_t(fp()) & (2*wordSize-1)) != 0) {
    return false;
  }
  if (sp() == 0 || (intptr_t(sp()) & (2*wordSize-1)) != 0) {
    return false;
  }

  const intptr_t interpreter_frame_initial_sp_offset = interpreter_frame_vm_local_words;
  if (fp() + interpreter_frame_initial_sp_offset < sp()) {
    return false;
  }
  // These are hacks to keep us out of trouble.
  // The problem with these is that they mask other problems
  if (fp() <= sp()) {        // this attempts to deal with unsigned comparison above
    return false;
  }
  // do some validation of frame elements

  // first the method

  Method* m = *interpreter_frame_method_addr();

  // validate the method we'd find in this potential sender
  if (!Method::is_valid_method(m)) return false;

  // stack frames shouldn't be much larger than max_stack elements

  if (fp() - unextended_sp() > 1024 + m->max_stack()*Interpreter::stackElementSize) {
    return false;
  }

  // validate bci/bcp

  address bcp = interpreter_frame_bcp();
  if (m->validate_bci_from_bcp(bcp) < 0) {
    return false;
  }

  // validate ConstantPoolCache*
  ConstantPoolCache* cp = *interpreter_frame_cache_addr();
  if (MetaspaceObj::is_valid(cp) == false) return false;

  // validate locals

  address locals =  (address) *interpreter_frame_locals_addr();

  if (locals > thread->stack_base() || locals < (address) fp()) return false;

  // We'd have to be pretty unlucky to be mislead at this point
  return true;
}


// Windows have been flushed on entry (but not marked). Capture the pc that
// is the return address to the frame that contains "sp" as its stack pointer.
// This pc resides in the called of the frame corresponding to "sp".
// As a side effect we mark this JavaFrameAnchor as having flushed the windows.
// This side effect lets us mark stacked JavaFrameAnchors (stacked in the
// call_helper) as flushed when we have flushed the windows for the most
// recent (i.e. current) JavaFrameAnchor. This saves useless flushing calls
// and lets us find the pc just once rather than multiple times as it did
// in the bad old _post_Java_state days.
//
void JavaFrameAnchor::capture_last_Java_pc(intptr_t* sp) {
  if (last_Java_sp() != NULL && last_Java_pc() == NULL) {
    // try and find the sp just younger than _last_Java_sp
    intptr_t* _post_Java_sp = frame::next_younger_sp_or_null(last_Java_sp(), sp);
    // Really this should never fail otherwise VM call must have non-standard
    // frame linkage (bad) or stack is not properly flushed (worse).
    guarantee(_post_Java_sp != NULL, "bad stack!");
    _last_Java_pc = (address) _post_Java_sp[ I7->sp_offset_in_saved_window()] + frame::pc_return_offset;

  }
  set_window_flushed();
}

void JavaFrameAnchor::make_walkable(JavaThread* thread) {
  if (walkable()) return;
  // Eventually make an assert
  guarantee(Thread::current() == (Thread*)thread, "only current thread can flush its registers");
  // We always flush in case the profiler wants it but we won't mark
  // the windows as flushed unless we have a last_Java_frame
  intptr_t* sp = StubRoutines::Sparc::flush_callers_register_windows_func()();
  if (last_Java_sp() != NULL ) {
    capture_last_Java_pc(sp);
  }
}

intptr_t* frame::entry_frame_argument_at(int offset) const {
  // convert offset to index to deal with tsi
  int index = (Interpreter::expr_offset_in_bytes(offset)/wordSize);

  intptr_t* LSP = (intptr_t*) sp()[Lentry_args->sp_offset_in_saved_window()];
  return &LSP[index+1];
}


BasicType frame::interpreter_frame_result(oop* oop_result, jvalue* value_result) {
  assert(is_interpreted_frame(), "interpreted frame expected");
  Method* method = interpreter_frame_method();
  BasicType type = method->result_type();

  if (method->is_native()) {
    // Prior to notifying the runtime of the method_exit the possible result
    // value is saved to l_scratch and d_scratch.

    intptr_t* l_scratch = fp() + interpreter_frame_l_scratch_fp_offset;
    intptr_t* d_scratch = fp() + interpreter_frame_d_scratch_fp_offset;

    address l_addr = (address)l_scratch;
    // On 64-bit the result for 1/8/16/32-bit result types is in the other
    // word half
    l_addr += wordSize/2;

    switch (type) {
      case T_OBJECT:
      case T_ARRAY: {
        oop obj = cast_to_oop(at(interpreter_frame_oop_temp_offset));
        assert(obj == NULL || Universe::heap()->is_in(obj), "sanity check");
        *oop_result = obj;
        break;
      }

      case T_BOOLEAN : { jint* p = (jint*)l_addr; value_result->z = (jboolean)((*p) & 0x1); break; }
      case T_BYTE    : { jint* p = (jint*)l_addr; value_result->b = (jbyte)((*p) & 0xff); break; }
      case T_CHAR    : { jint* p = (jint*)l_addr; value_result->c = (jchar)((*p) & 0xffff); break; }
      case T_SHORT   : { jint* p = (jint*)l_addr; value_result->s = (jshort)((*p) & 0xffff); break; }
      case T_INT     : value_result->i = *(jint*)l_addr; break;
      case T_LONG    : value_result->j = *(jlong*)l_scratch; break;
      case T_FLOAT   : value_result->f = *(jfloat*)d_scratch; break;
      case T_DOUBLE  : value_result->d = *(jdouble*)d_scratch; break;
      case T_VOID    : /* Nothing to do */ break;
      default        : ShouldNotReachHere();
    }
  } else {
    intptr_t* tos_addr = interpreter_frame_tos_address();

    switch(type) {
      case T_OBJECT:
      case T_ARRAY: {
        oop obj = cast_to_oop(*tos_addr);
        assert(obj == NULL || Universe::heap()->is_in(obj), "sanity check");
        *oop_result = obj;
        break;
      }
      case T_BOOLEAN : { jint* p = (jint*)tos_addr; value_result->z = (jboolean)((*p) & 0x1); break; }
      case T_BYTE    : { jint* p = (jint*)tos_addr; value_result->b = (jbyte)((*p) & 0xff); break; }
      case T_CHAR    : { jint* p = (jint*)tos_addr; value_result->c = (jchar)((*p) & 0xffff); break; }
      case T_SHORT   : { jint* p = (jint*)tos_addr; value_result->s = (jshort)((*p) & 0xffff); break; }
      case T_INT     : value_result->i = *(jint*)tos_addr; break;
      case T_LONG    : value_result->j = *(jlong*)tos_addr; break;
      case T_FLOAT   : value_result->f = *(jfloat*)tos_addr; break;
      case T_DOUBLE  : value_result->d = *(jdouble*)tos_addr; break;
      case T_VOID    : /* Nothing to do */ break;
      default        : ShouldNotReachHere();
    }
  };

  return type;
}

// Lesp pointer is one word lower than the top item on the stack.
intptr_t* frame::interpreter_frame_tos_at(jint offset) const {
  int index = (Interpreter::expr_offset_in_bytes(offset)/wordSize) - 1;
  return &interpreter_frame_tos_address()[index];
}


#ifndef PRODUCT

#define DESCRIBE_FP_OFFSET(name) \
  values.describe(frame_no, fp() + frame::name##_offset, #name)

void frame::describe_pd(FrameValues& values, int frame_no) {
  for (int w = 0; w < frame::register_save_words; w++) {
    values.describe(frame_no, sp() + w, err_msg("register save area word %d", w), 1);
  }

  if (is_interpreted_frame()) {
    DESCRIBE_FP_OFFSET(interpreter_frame_d_scratch_fp);
    DESCRIBE_FP_OFFSET(interpreter_frame_l_scratch_fp);
    DESCRIBE_FP_OFFSET(interpreter_frame_mirror);
    DESCRIBE_FP_OFFSET(interpreter_frame_oop_temp);

    // esp, according to Lesp (e.g. not depending on bci), if seems valid
    intptr_t* esp = *interpreter_frame_esp_addr();
    if ((esp >= sp()) && (esp < fp())) {
      values.describe(-1, esp, "*Lesp");
    }
  }

  if (!is_compiled_frame()) {
    if (frame::callee_aggregate_return_pointer_words != 0) {
      values.describe(frame_no, sp() + frame::callee_aggregate_return_pointer_sp_offset, "callee_aggregate_return_pointer_word");
    }
    for (int w = 0; w < frame::callee_register_argument_save_area_words; w++) {
      values.describe(frame_no, sp() + frame::callee_register_argument_save_area_sp_offset + w,
                      err_msg("callee_register_argument_save_area_words %d", w));
    }
  }
}

#endif

intptr_t *frame::initial_deoptimization_info() {
  // unused... but returns fp() to minimize changes introduced by 7087445
  return fp();
}
