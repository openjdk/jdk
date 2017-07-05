/*
 * Copyright 1997-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

# include "incls/_precompiled.incl"
# include "incls/_frame.cpp.incl"

RegisterMap::RegisterMap(JavaThread *thread, bool update_map) {
  _thread         = thread;
  _update_map     = update_map;
  clear();
  debug_only(_update_for_id = NULL;)
#ifndef PRODUCT
  for (int i = 0; i < reg_count ; i++ ) _location[i] = NULL;
#endif /* PRODUCT */
}

RegisterMap::RegisterMap(const RegisterMap* map) {
  assert(map != this, "bad initialization parameter");
  assert(map != NULL, "RegisterMap must be present");
  _thread                = map->thread();
  _update_map            = map->update_map();
  _include_argument_oops = map->include_argument_oops();
  debug_only(_update_for_id = map->_update_for_id;)
  pd_initialize_from(map);
  if (update_map()) {
    for(int i = 0; i < location_valid_size; i++) {
      LocationValidType bits = !update_map() ? 0 : map->_location_valid[i];
      _location_valid[i] = bits;
      // for whichever bits are set, pull in the corresponding map->_location
      int j = i*location_valid_type_size;
      while (bits != 0) {
        if ((bits & 1) != 0) {
          assert(0 <= j && j < reg_count, "range check");
          _location[j] = map->_location[j];
        }
        bits >>= 1;
        j += 1;
      }
    }
  }
}

void RegisterMap::clear() {
  set_include_argument_oops(true);
  if (_update_map) {
    for(int i = 0; i < location_valid_size; i++) {
      _location_valid[i] = 0;
    }
    pd_clear();
  } else {
    pd_initialize();
  }
}

#ifndef PRODUCT

void RegisterMap::print_on(outputStream* st) const {
  st->print_cr("Register map");
  for(int i = 0; i < reg_count; i++) {

    VMReg r = VMRegImpl::as_VMReg(i);
    intptr_t* src = (intptr_t*) location(r);
    if (src != NULL) {

      r->print_on(st);
      st->print(" [" INTPTR_FORMAT "] = ", src);
      if (((uintptr_t)src & (sizeof(*src)-1)) != 0) {
        st->print_cr("<misaligned>");
      } else {
        st->print_cr(INTPTR_FORMAT, *src);
      }
    }
  }
}

void RegisterMap::print() const {
  print_on(tty);
}

#endif
// This returns the pc that if you were in the debugger you'd see. Not
// the idealized value in the frame object. This undoes the magic conversion
// that happens for deoptimized frames. In addition it makes the value the
// hardware would want to see in the native frame. The only user (at this point)
// is deoptimization. It likely no one else should ever use it.

address frame::raw_pc() const {
  if (is_deoptimized_frame()) {
    return ((nmethod*) cb())->deopt_handler_begin() - pc_return_offset;
  } else {
    return (pc() - pc_return_offset);
  }
}

// Change the pc in a frame object. This does not change the actual pc in
// actual frame. To do that use patch_pc.
//
void frame::set_pc(address   newpc ) {
#ifdef ASSERT
  if (_cb != NULL && _cb->is_nmethod()) {
    assert(!((nmethod*)_cb)->is_deopt_pc(_pc), "invariant violation");
  }
#endif // ASSERT

  // Unsafe to use the is_deoptimzed tester after changing pc
  _deopt_state = unknown;
  _pc = newpc;
  _cb = CodeCache::find_blob_unsafe(_pc);

}

// type testers
bool frame::is_deoptimized_frame() const {
  assert(_deopt_state != unknown, "not answerable");
  return _deopt_state == is_deoptimized;
}

bool frame::is_native_frame() const {
  return (_cb != NULL &&
          _cb->is_nmethod() &&
          ((nmethod*)_cb)->is_native_method());
}

bool frame::is_java_frame() const {
  if (is_interpreted_frame()) return true;
  if (is_compiled_frame())    return true;
  return false;
}


bool frame::is_compiled_frame() const {
  if (_cb != NULL &&
      _cb->is_nmethod() &&
      ((nmethod*)_cb)->is_java_method()) {
    return true;
  }
  return false;
}


bool frame::is_runtime_frame() const {
  return (_cb != NULL && _cb->is_runtime_stub());
}

bool frame::is_safepoint_blob_frame() const {
  return (_cb != NULL && _cb->is_safepoint_stub());
}

// testers

bool frame::is_first_java_frame() const {
  RegisterMap map(JavaThread::current(), false); // No update
  frame s;
  for (s = sender(&map); !(s.is_java_frame() || s.is_first_frame()); s = s.sender(&map));
  return s.is_first_frame();
}


bool frame::entry_frame_is_first() const {
  return entry_frame_call_wrapper()->anchor()->last_Java_sp() == NULL;
}


bool frame::should_be_deoptimized() const {
  if (_deopt_state == is_deoptimized ||
      !is_compiled_frame() ) return false;
  assert(_cb != NULL && _cb->is_nmethod(), "must be an nmethod");
  nmethod* nm = (nmethod *)_cb;
  if (TraceDependencies) {
    tty->print("checking (%s) ", nm->is_marked_for_deoptimization() ? "true" : "false");
    nm->print_value_on(tty);
    tty->cr();
  }

  if( !nm->is_marked_for_deoptimization() )
    return false;

  // If at the return point, then the frame has already been popped, and
  // only the return needs to be executed. Don't deoptimize here.
  return !nm->is_at_poll_return(pc());
}

bool frame::can_be_deoptimized() const {
  if (!is_compiled_frame()) return false;
  nmethod* nm = (nmethod*)_cb;

  if( !nm->can_be_deoptimized() )
    return false;

  return !nm->is_at_poll_return(pc());
}

void frame::deoptimize(JavaThread* thread, bool thread_is_known_safe) {
// Schedule deoptimization of an nmethod activation with this frame.

  // Store the original pc before an patch (or request to self-deopt)
  // in the published location of the frame.

  assert(_cb != NULL && _cb->is_nmethod(), "must be");
  nmethod* nm = (nmethod*)_cb;

  // This is a fix for register window patching race
  if (NeedsDeoptSuspend && !thread_is_known_safe) {

    // It is possible especially with DeoptimizeALot/DeoptimizeRandom that
    // we could see the frame again and ask for it to be deoptimized since
    // it might move for a long time. That is harmless and we just ignore it.
    if (id() == thread->must_deopt_id()) {
      assert(thread->is_deopt_suspend(), "lost suspension");
      return;
    }

    // We are at a safepoint so the target thread can only be
    // in 4 states:
    //     blocked - no problem
    //     blocked_trans - no problem (i.e. could have woken up from blocked
    //                                 during a safepoint).
    //     native - register window pc patching race
    //     native_trans - momentary state
    //
    // We could just wait out a thread in native_trans to block.
    // Then we'd have all the issues that the safepoint code has as to
    // whether to spin or block. It isn't worth it. Just treat it like
    // native and be done with it.
    //
    JavaThreadState state = thread->thread_state();
    if (state == _thread_in_native || state == _thread_in_native_trans) {
      // Since we are at a safepoint the target thread will stop itself
      // before it can return to java as long as we remain at the safepoint.
      // Therefore we can put an additional request for the thread to stop
      // no matter what no (like a suspend). This will cause the thread
      // to notice it needs to do the deopt on its own once it leaves native.
      //
      // The only reason we must do this is because on machine with register
      // windows we have a race with patching the return address and the
      // window coming live as the thread returns to the Java code (but still
      // in native mode) and then blocks. It is only this top most frame
      // that is at risk. So in truth we could add an additional check to
      // see if this frame is one that is at risk.
      RegisterMap map(thread, false);
      frame at_risk =  thread->last_frame().sender(&map);
      if (id() == at_risk.id()) {
        thread->set_must_deopt_id(id());
        thread->set_deopt_suspend();
        return;
      }
    }
  } // NeedsDeoptSuspend


  address deopt = nm->deopt_handler_begin();
  // Save the original pc before we patch in the new one
  nm->set_original_pc(this, pc());
  patch_pc(thread, deopt);
#ifdef ASSERT
  {
    RegisterMap map(thread, false);
    frame check = thread->last_frame();
    while (id() != check.id()) {
      check = check.sender(&map);
    }
    assert(check.is_deoptimized_frame(), "missed deopt");
  }
#endif // ASSERT
}

frame frame::java_sender() const {
  RegisterMap map(JavaThread::current(), false);
  frame s;
  for (s = sender(&map); !(s.is_java_frame() || s.is_first_frame()); s = s.sender(&map)) ;
  guarantee(s.is_java_frame(), "tried to get caller of first java frame");
  return s;
}

frame frame::real_sender(RegisterMap* map) const {
  frame result = sender(map);
  while (result.is_runtime_frame()) {
    result = result.sender(map);
  }
  return result;
}

// Note: called by profiler - NOT for current thread
frame frame::profile_find_Java_sender_frame(JavaThread *thread) {
// If we don't recognize this frame, walk back up the stack until we do
  RegisterMap map(thread, false);
  frame first_java_frame = frame();

  // Find the first Java frame on the stack starting with input frame
  if (is_java_frame()) {
    // top frame is compiled frame or deoptimized frame
    first_java_frame = *this;
  } else if (safe_for_sender(thread)) {
    for (frame sender_frame = sender(&map);
      sender_frame.safe_for_sender(thread) && !sender_frame.is_first_frame();
      sender_frame = sender_frame.sender(&map)) {
      if (sender_frame.is_java_frame()) {
        first_java_frame = sender_frame;
        break;
      }
    }
  }
  return first_java_frame;
}

// Interpreter frames


void frame::interpreter_frame_set_locals(intptr_t* locs)  {
  assert(is_interpreted_frame(), "Not an interpreted frame");
  *interpreter_frame_locals_addr() = locs;
}

methodOop frame::interpreter_frame_method() const {
  assert(is_interpreted_frame(), "interpreted frame expected");
  methodOop m = *interpreter_frame_method_addr();
  assert(m->is_perm(), "bad methodOop in interpreter frame");
  assert(m->is_method(), "not a methodOop");
  return m;
}

void frame::interpreter_frame_set_method(methodOop method) {
  assert(is_interpreted_frame(), "interpreted frame expected");
  *interpreter_frame_method_addr() = method;
}

void frame::interpreter_frame_set_bcx(intptr_t bcx) {
  assert(is_interpreted_frame(), "Not an interpreted frame");
  if (ProfileInterpreter) {
    bool formerly_bci = is_bci(interpreter_frame_bcx());
    bool is_now_bci = is_bci(bcx);
    *interpreter_frame_bcx_addr() = bcx;

    intptr_t mdx = interpreter_frame_mdx();

    if (mdx != 0) {
      if (formerly_bci) {
        if (!is_now_bci) {
          // The bcx was just converted from bci to bcp.
          // Convert the mdx in parallel.
          methodDataOop mdo = interpreter_frame_method()->method_data();
          assert(mdo != NULL, "");
          int mdi = mdx - 1; // We distinguish valid mdi from zero by adding one.
          address mdp = mdo->di_to_dp(mdi);
          interpreter_frame_set_mdx((intptr_t)mdp);
        }
      } else {
        if (is_now_bci) {
          // The bcx was just converted from bcp to bci.
          // Convert the mdx in parallel.
          methodDataOop mdo = interpreter_frame_method()->method_data();
          assert(mdo != NULL, "");
          int mdi = mdo->dp_to_di((address)mdx);
          interpreter_frame_set_mdx((intptr_t)mdi + 1); // distinguish valid from 0.
        }
      }
    }
  } else {
    *interpreter_frame_bcx_addr() = bcx;
  }
}

jint frame::interpreter_frame_bci() const {
  assert(is_interpreted_frame(), "interpreted frame expected");
  intptr_t bcx = interpreter_frame_bcx();
  return is_bci(bcx) ? bcx : interpreter_frame_method()->bci_from((address)bcx);
}

void frame::interpreter_frame_set_bci(jint bci) {
  assert(is_interpreted_frame(), "interpreted frame expected");
  assert(!is_bci(interpreter_frame_bcx()), "should not set bci during GC");
  interpreter_frame_set_bcx((intptr_t)interpreter_frame_method()->bcp_from(bci));
}

address frame::interpreter_frame_bcp() const {
  assert(is_interpreted_frame(), "interpreted frame expected");
  intptr_t bcx = interpreter_frame_bcx();
  return is_bci(bcx) ? interpreter_frame_method()->bcp_from(bcx) : (address)bcx;
}

void frame::interpreter_frame_set_bcp(address bcp) {
  assert(is_interpreted_frame(), "interpreted frame expected");
  assert(!is_bci(interpreter_frame_bcx()), "should not set bcp during GC");
  interpreter_frame_set_bcx((intptr_t)bcp);
}

void frame::interpreter_frame_set_mdx(intptr_t mdx) {
  assert(is_interpreted_frame(), "Not an interpreted frame");
  assert(ProfileInterpreter, "must be profiling interpreter");
  *interpreter_frame_mdx_addr() = mdx;
}

address frame::interpreter_frame_mdp() const {
  assert(ProfileInterpreter, "must be profiling interpreter");
  assert(is_interpreted_frame(), "interpreted frame expected");
  intptr_t bcx = interpreter_frame_bcx();
  intptr_t mdx = interpreter_frame_mdx();

  assert(!is_bci(bcx), "should not access mdp during GC");
  return (address)mdx;
}

void frame::interpreter_frame_set_mdp(address mdp) {
  assert(is_interpreted_frame(), "interpreted frame expected");
  if (mdp == NULL) {
    // Always allow the mdp to be cleared.
    interpreter_frame_set_mdx((intptr_t)mdp);
  }
  intptr_t bcx = interpreter_frame_bcx();
  assert(!is_bci(bcx), "should not set mdp during GC");
  interpreter_frame_set_mdx((intptr_t)mdp);
}

BasicObjectLock* frame::next_monitor_in_interpreter_frame(BasicObjectLock* current) const {
  assert(is_interpreted_frame(), "Not an interpreted frame");
#ifdef ASSERT
  interpreter_frame_verify_monitor(current);
#endif
  BasicObjectLock* next = (BasicObjectLock*) (((intptr_t*) current) + interpreter_frame_monitor_size());
  return next;
}

BasicObjectLock* frame::previous_monitor_in_interpreter_frame(BasicObjectLock* current) const {
  assert(is_interpreted_frame(), "Not an interpreted frame");
#ifdef ASSERT
//   // This verification needs to be checked before being enabled
//   interpreter_frame_verify_monitor(current);
#endif
  BasicObjectLock* previous = (BasicObjectLock*) (((intptr_t*) current) - interpreter_frame_monitor_size());
  return previous;
}

// Interpreter locals and expression stack locations.

intptr_t* frame::interpreter_frame_local_at(int index) const {
  const int n = Interpreter::local_offset_in_bytes(index)/wordSize;
  return &((*interpreter_frame_locals_addr())[n]);
}

frame::Tag frame::interpreter_frame_local_tag(int index) const {
  const int n = Interpreter::local_tag_offset_in_bytes(index)/wordSize;
  return (Tag)(*interpreter_frame_locals_addr()) [n];
}

void frame::interpreter_frame_set_local_tag(int index, Tag tag) const {
  const int n = Interpreter::local_tag_offset_in_bytes(index)/wordSize;
  (*interpreter_frame_locals_addr())[n] = (intptr_t)tag;
}

intptr_t* frame::interpreter_frame_expression_stack_at(jint offset) const {
  const int i = offset * interpreter_frame_expression_stack_direction();
  const int n = ((i * Interpreter::stackElementSize()) +
                 Interpreter::value_offset_in_bytes())/wordSize;
  return &(interpreter_frame_expression_stack()[n]);
}

frame::Tag frame::interpreter_frame_expression_stack_tag(jint offset) const {
  const int i = offset * interpreter_frame_expression_stack_direction();
  const int n = ((i * Interpreter::stackElementSize()) +
                 Interpreter::tag_offset_in_bytes())/wordSize;
  return (Tag)(interpreter_frame_expression_stack()[n]);
}

void frame::interpreter_frame_set_expression_stack_tag(jint offset,
                                                       Tag tag) const {
  const int i = offset * interpreter_frame_expression_stack_direction();
  const int n = ((i * Interpreter::stackElementSize()) +
                 Interpreter::tag_offset_in_bytes())/wordSize;
  interpreter_frame_expression_stack()[n] = (intptr_t)tag;
}

jint frame::interpreter_frame_expression_stack_size() const {
  // Number of elements on the interpreter expression stack
  // Callers should span by stackElementWords
  int element_size = Interpreter::stackElementWords();
  if (frame::interpreter_frame_expression_stack_direction() < 0) {
    return (interpreter_frame_expression_stack() -
            interpreter_frame_tos_address() + 1)/element_size;
  } else {
    return (interpreter_frame_tos_address() -
            interpreter_frame_expression_stack() + 1)/element_size;
  }
}


// (frame::interpreter_frame_sender_sp accessor is in frame_<arch>.cpp)

const char* frame::print_name() const {
  if (is_native_frame())      return "Native";
  if (is_interpreted_frame()) return "Interpreted";
  if (is_compiled_frame()) {
    if (is_deoptimized_frame()) return "Deoptimized";
    return "Compiled";
  }
  if (sp() == NULL)            return "Empty";
  return "C";
}

void frame::print_value_on(outputStream* st, JavaThread *thread) const {
  NOT_PRODUCT(address begin = pc()-40;)
  NOT_PRODUCT(address end   = NULL;)

  st->print("%s frame (sp=" INTPTR_FORMAT " unextended sp=" INTPTR_FORMAT, print_name(), sp(), unextended_sp());
  if (sp() != NULL)
    st->print(", fp=" INTPTR_FORMAT ", pc=" INTPTR_FORMAT, fp(), pc());

  if (StubRoutines::contains(pc())) {
    st->print_cr(")");
    st->print("(");
    StubCodeDesc* desc = StubCodeDesc::desc_for(pc());
    st->print("~Stub::%s", desc->name());
    NOT_PRODUCT(begin = desc->begin(); end = desc->end();)
  } else if (Interpreter::contains(pc())) {
    st->print_cr(")");
    st->print("(");
    InterpreterCodelet* desc = Interpreter::codelet_containing(pc());
    if (desc != NULL) {
      st->print("~");
      desc->print();
      NOT_PRODUCT(begin = desc->code_begin(); end = desc->code_end();)
    } else {
      st->print("~interpreter");
    }
  }
  st->print_cr(")");

  if (_cb != NULL) {
    st->print("     ");
    _cb->print_value_on(st);
    st->cr();
#ifndef PRODUCT
    if (end == NULL) {
      begin = _cb->instructions_begin();
      end = _cb->instructions_end();
    }
#endif
  }
  NOT_PRODUCT(if (WizardMode && Verbose) Disassembler::decode(begin, end);)
}


void frame::print_on(outputStream* st) const {
  print_value_on(st,NULL);
  if (is_interpreted_frame()) {
    interpreter_frame_print_on(st);
  }
}


void frame::interpreter_frame_print_on(outputStream* st) const {
#ifndef PRODUCT
  assert(is_interpreted_frame(), "Not an interpreted frame");
  jint i;
  for (i = 0; i < interpreter_frame_method()->max_locals(); i++ ) {
    intptr_t x = *interpreter_frame_local_at(i);
    st->print(" - local  [" INTPTR_FORMAT "]", x);
    if (TaggedStackInterpreter) {
      Tag x = interpreter_frame_local_tag(i);
      st->print(" - local tag [" INTPTR_FORMAT "]", x);
    }
    st->fill_to(23);
    st->print_cr("; #%d", i);
  }
  for (i = interpreter_frame_expression_stack_size() - 1; i >= 0; --i ) {
    intptr_t x = *interpreter_frame_expression_stack_at(i);
    st->print(" - stack  [" INTPTR_FORMAT "]", x);
    if (TaggedStackInterpreter) {
      Tag x = interpreter_frame_expression_stack_tag(i);
      st->print(" - stack tag [" INTPTR_FORMAT "]", x);
    }
    st->fill_to(23);
    st->print_cr("; #%d", i);
  }
  // locks for synchronization
  for (BasicObjectLock* current = interpreter_frame_monitor_end();
       current < interpreter_frame_monitor_begin();
       current = next_monitor_in_interpreter_frame(current)) {
    st->print_cr(" [ - obj ");
    current->obj()->print_value_on(st);
    st->cr();
    st->print_cr(" - lock ");
    current->lock()->print_on(st);
    st->cr();
  }
  // monitor
  st->print_cr(" - monitor[" INTPTR_FORMAT "]", interpreter_frame_monitor_begin());
  // bcp
  st->print(" - bcp    [" INTPTR_FORMAT "]", interpreter_frame_bcp());
  st->fill_to(23);
  st->print_cr("; @%d", interpreter_frame_bci());
  // locals
  st->print_cr(" - locals [" INTPTR_FORMAT "]", interpreter_frame_local_at(0));
  // method
  st->print(" - method [" INTPTR_FORMAT "]", (address)interpreter_frame_method());
  st->fill_to(23);
  st->print("; ");
  interpreter_frame_method()->print_name(st);
  st->cr();
#endif
}

// Return whether the frame is in the VM or os indicating a Hotspot problem.
// Otherwise, it's likely a bug in the native library that the Java code calls,
// hopefully indicating where to submit bugs.
static void print_C_frame(outputStream* st, char* buf, int buflen, address pc) {
  // C/C++ frame
  bool in_vm = os::address_is_in_vm(pc);
  st->print(in_vm ? "V" : "C");

  int offset;
  bool found;

  // libname
  found = os::dll_address_to_library_name(pc, buf, buflen, &offset);
  if (found) {
    // skip directory names
    const char *p1, *p2;
    p1 = buf;
    int len = (int)strlen(os::file_separator());
    while ((p2 = strstr(p1, os::file_separator())) != NULL) p1 = p2 + len;
    st->print("  [%s+0x%x]", p1, offset);
  } else {
    st->print("  " PTR_FORMAT, pc);
  }

  // function name - os::dll_address_to_function_name() may return confusing
  // names if pc is within jvm.dll or libjvm.so, because JVM only has
  // JVM_xxxx and a few other symbols in the dynamic symbol table. Do this
  // only for native libraries.
  if (!in_vm) {
    found = os::dll_address_to_function_name(pc, buf, buflen, &offset);

    if (found) {
      st->print("  %s+0x%x", buf, offset);
    }
  }
}

// frame::print_on_error() is called by fatal error handler. Notice that we may
// crash inside this function if stack frame is corrupted. The fatal error
// handler can catch and handle the crash. Here we assume the frame is valid.
//
// First letter indicates type of the frame:
//    J: Java frame (compiled)
//    j: Java frame (interpreted)
//    V: VM frame (C/C++)
//    v: Other frames running VM generated code (e.g. stubs, adapters, etc.)
//    C: C/C++ frame
//
// We don't need detailed frame type as that in frame::print_name(). "C"
// suggests the problem is in user lib; everything else is likely a VM bug.

void frame::print_on_error(outputStream* st, char* buf, int buflen, bool verbose) const {
  if (_cb != NULL) {
    if (Interpreter::contains(pc())) {
      methodOop m = this->interpreter_frame_method();
      if (m != NULL) {
        m->name_and_sig_as_C_string(buf, buflen);
        st->print("j  %s", buf);
        st->print("+%d", this->interpreter_frame_bci());
      } else {
        st->print("j  " PTR_FORMAT, pc());
      }
    } else if (StubRoutines::contains(pc())) {
      StubCodeDesc* desc = StubCodeDesc::desc_for(pc());
      if (desc != NULL) {
        st->print("v  ~StubRoutines::%s", desc->name());
      } else {
        st->print("v  ~StubRoutines::" PTR_FORMAT, pc());
      }
    } else if (_cb->is_buffer_blob()) {
      st->print("v  ~BufferBlob::%s", ((BufferBlob *)_cb)->name());
    } else if (_cb->is_nmethod()) {
      methodOop m = ((nmethod *)_cb)->method();
      if (m != NULL) {
        m->name_and_sig_as_C_string(buf, buflen);
        st->print("J  %s", buf);
      } else {
        st->print("J  " PTR_FORMAT, pc());
      }
    } else if (_cb->is_runtime_stub()) {
      st->print("v  ~RuntimeStub::%s", ((RuntimeStub *)_cb)->name());
    } else if (_cb->is_deoptimization_stub()) {
      st->print("v  ~DeoptimizationBlob");
    } else if (_cb->is_exception_stub()) {
      st->print("v  ~ExceptionBlob");
    } else if (_cb->is_safepoint_stub()) {
      st->print("v  ~SafepointBlob");
    } else {
      st->print("v  blob " PTR_FORMAT, pc());
    }
  } else {
    print_C_frame(st, buf, buflen, pc());
  }
}


/*
  The interpreter_frame_expression_stack_at method in the case of SPARC needs the
  max_stack value of the method in order to compute the expression stack address.
  It uses the methodOop in order to get the max_stack value but during GC this
  methodOop value saved on the frame is changed by reverse_and_push and hence cannot
  be used. So we save the max_stack value in the FrameClosure object and pass it
  down to the interpreter_frame_expression_stack_at method
*/
class InterpreterFrameClosure : public OffsetClosure {
 private:
  frame* _fr;
  OopClosure* _f;
  int    _max_locals;
  int    _max_stack;

 public:
  InterpreterFrameClosure(frame* fr, int max_locals, int max_stack,
                          OopClosure* f) {
    _fr         = fr;
    _max_locals = max_locals;
    _max_stack  = max_stack;
    _f          = f;
  }

  void offset_do(int offset) {
    oop* addr;
    if (offset < _max_locals) {
      addr = (oop*) _fr->interpreter_frame_local_at(offset);
      assert((intptr_t*)addr >= _fr->sp(), "must be inside the frame");
      _f->do_oop(addr);
    } else {
      addr = (oop*) _fr->interpreter_frame_expression_stack_at((offset - _max_locals));
      // In case of exceptions, the expression stack is invalid and the esp will be reset to express
      // this condition. Therefore, we call f only if addr is 'inside' the stack (i.e., addr >= esp for Intel).
      bool in_stack;
      if (frame::interpreter_frame_expression_stack_direction() > 0) {
        in_stack = (intptr_t*)addr <= _fr->interpreter_frame_tos_address();
      } else {
        in_stack = (intptr_t*)addr >= _fr->interpreter_frame_tos_address();
      }
      if (in_stack) {
        _f->do_oop(addr);
      }
    }
  }

  int max_locals()  { return _max_locals; }
  frame* fr()       { return _fr; }
};


class InterpretedArgumentOopFinder: public SignatureInfo {
 private:
  OopClosure* _f;      // Closure to invoke
  int    _offset;      // TOS-relative offset, decremented with each argument
  bool   _is_static;   // true if the callee is a static method
  frame* _fr;

  void set(int size, BasicType type) {
    _offset -= size;
    if (type == T_OBJECT || type == T_ARRAY) oop_offset_do();
  }

  void oop_offset_do() {
    oop* addr;
    addr = (oop*)_fr->interpreter_frame_tos_at(_offset);
    _f->do_oop(addr);
  }

 public:
  InterpretedArgumentOopFinder(symbolHandle signature, bool is_static, frame* fr, OopClosure* f) : SignatureInfo(signature) {
    // compute size of arguments
    int args_size = ArgumentSizeComputer(signature).size() + (is_static ? 0 : 1);
    assert(!fr->is_interpreted_frame() ||
           args_size <= fr->interpreter_frame_expression_stack_size(),
            "args cannot be on stack anymore");
    // initialize InterpretedArgumentOopFinder
    _f         = f;
    _fr        = fr;
    _offset    = args_size;
    _is_static = is_static;
  }

  void oops_do() {
    if (!_is_static) {
      --_offset;
      oop_offset_do();
    }
    iterate_parameters();
  }
};


// Entry frame has following form (n arguments)
//         +-----------+
//   sp -> |  last arg |
//         +-----------+
//         :    :::    :
//         +-----------+
// (sp+n)->|  first arg|
//         +-----------+



// visits and GC's all the arguments in entry frame
class EntryFrameOopFinder: public SignatureInfo {
 private:
  bool   _is_static;
  int    _offset;
  frame* _fr;
  OopClosure* _f;

  void set(int size, BasicType type) {
    assert (_offset >= 0, "illegal offset");
    if (type == T_OBJECT || type == T_ARRAY) oop_at_offset_do(_offset);
    _offset -= size;
  }

  void oop_at_offset_do(int offset) {
    assert (offset >= 0, "illegal offset")
    oop* addr = (oop*) _fr->entry_frame_argument_at(offset);
    _f->do_oop(addr);
  }

 public:
   EntryFrameOopFinder(frame* frame, symbolHandle signature, bool is_static) : SignatureInfo(signature) {
     _f = NULL; // will be set later
     _fr = frame;
     _is_static = is_static;
     _offset = ArgumentSizeComputer(signature).size() - 1; // last parameter is at index 0
   }

  void arguments_do(OopClosure* f) {
    _f = f;
    if (!_is_static) oop_at_offset_do(_offset+1); // do the receiver
    iterate_parameters();
  }

};

oop* frame::interpreter_callee_receiver_addr(symbolHandle signature) {
  ArgumentSizeComputer asc(signature);
  int size = asc.size();
  return (oop *)interpreter_frame_tos_at(size);
}


void frame::oops_interpreted_do(OopClosure* f, const RegisterMap* map, bool query_oop_map_cache) {
  assert(is_interpreted_frame(), "Not an interpreted frame");
  assert(map != NULL, "map must be set");
  Thread *thread = Thread::current();
  methodHandle m (thread, interpreter_frame_method());
  jint      bci = interpreter_frame_bci();

  assert(Universe::heap()->is_in(m()), "must be valid oop");
  assert(m->is_method(), "checking frame value");
  assert((m->is_native() && bci == 0)  || (!m->is_native() && bci >= 0 && bci < m->code_size()), "invalid bci value");

  // Handle the monitor elements in the activation
  for (
    BasicObjectLock* current = interpreter_frame_monitor_end();
    current < interpreter_frame_monitor_begin();
    current = next_monitor_in_interpreter_frame(current)
  ) {
#ifdef ASSERT
    interpreter_frame_verify_monitor(current);
#endif
    current->oops_do(f);
  }

  // process fixed part
  f->do_oop((oop*)interpreter_frame_method_addr());
  f->do_oop((oop*)interpreter_frame_cache_addr());

  // Hmm what about the mdp?
#ifdef CC_INTERP
  // Interpreter frame in the midst of a call have a methodOop within the
  // object.
  interpreterState istate = get_interpreterState();
  if (istate->msg() == BytecodeInterpreter::call_method) {
    f->do_oop((oop*)&istate->_result._to_call._callee);
  }

#endif /* CC_INTERP */

  if (m->is_native()) {
#ifdef CC_INTERP
    f->do_oop((oop*)&istate->_oop_temp);
#else
    f->do_oop((oop*)( fp() + interpreter_frame_oop_temp_offset ));
#endif /* CC_INTERP */
  }

  int max_locals = m->is_native() ? m->size_of_parameters() : m->max_locals();

  symbolHandle signature;
  bool is_static = false;

  // Process a callee's arguments if we are at a call site
  // (i.e., if we are at an invoke bytecode)
  // This is used sometimes for calling into the VM, not for another
  // interpreted or compiled frame.
  if (!m->is_native()) {
    Bytecode_invoke *call = Bytecode_invoke_at_check(m, bci);
    if (call != NULL) {
      signature = symbolHandle(thread, call->signature());
      is_static = call->is_invokestatic();
      if (map->include_argument_oops() &&
          interpreter_frame_expression_stack_size() > 0) {
        ResourceMark rm(thread);  // is this right ???
        // we are at a call site & the expression stack is not empty
        // => process callee's arguments
        //
        // Note: The expression stack can be empty if an exception
        //       occurred during method resolution/execution. In all
        //       cases we empty the expression stack completely be-
        //       fore handling the exception (the exception handling
        //       code in the interpreter calls a blocking runtime
        //       routine which can cause this code to be executed).
        //       (was bug gri 7/27/98)
        oops_interpreted_arguments_do(signature, is_static, f);
      }
    }
  }

  if (TaggedStackInterpreter) {
    // process locals & expression stack
    InterpreterOopMap *mask = NULL;
#ifdef ASSERT
    InterpreterOopMap oopmap_mask;
    OopMapCache::compute_one_oop_map(m, bci, &oopmap_mask);
    mask = &oopmap_mask;
#endif // ASSERT
    oops_interpreted_locals_do(f, max_locals, mask);
    oops_interpreted_expressions_do(f, signature, is_static,
                                    m->max_stack(),
                                    max_locals, mask);
  } else {
    InterpreterFrameClosure blk(this, max_locals, m->max_stack(), f);

    // process locals & expression stack
    InterpreterOopMap mask;
    if (query_oop_map_cache) {
      m->mask_for(bci, &mask);
    } else {
      OopMapCache::compute_one_oop_map(m, bci, &mask);
    }
    mask.iterate_oop(&blk);
  }
}


void frame::oops_interpreted_locals_do(OopClosure *f,
                                      int max_locals,
                                      InterpreterOopMap *mask) {
  // Process locals then interpreter expression stack
  for (int i = 0; i < max_locals; i++ ) {
    Tag tag = interpreter_frame_local_tag(i);
    if (tag == TagReference) {
      oop* addr = (oop*) interpreter_frame_local_at(i);
      assert((intptr_t*)addr >= sp(), "must be inside the frame");
      f->do_oop(addr);
#ifdef ASSERT
    } else {
      assert(tag == TagValue, "bad tag value for locals");
      oop* p = (oop*) interpreter_frame_local_at(i);
      // Not always true - too bad.  May have dead oops without tags in locals.
      // assert(*p == NULL || !(*p)->is_oop(), "oop not tagged on interpreter locals");
      assert(*p == NULL || !mask->is_oop(i), "local oop map mismatch");
#endif // ASSERT
    }
  }
}

void frame::oops_interpreted_expressions_do(OopClosure *f,
                                      symbolHandle signature,
                                      bool is_static,
                                      int max_stack,
                                      int max_locals,
                                      InterpreterOopMap *mask) {
  // There is no stack no matter what the esp is pointing to (native methods
  // might look like expression stack is nonempty).
  if (max_stack == 0) return;

  // Point the top of the expression stack above arguments to a call so
  // arguments aren't gc'ed as both stack values for callee and callee
  // arguments in callee's locals.
  int args_size = 0;
  if (!signature.is_null()) {
    args_size = ArgumentSizeComputer(signature).size() + (is_static ? 0 : 1);
  }

  intptr_t *tos_addr = interpreter_frame_tos_at(args_size);
  assert(args_size != 0 || tos_addr == interpreter_frame_tos_address(), "these are same");
  intptr_t *frst_expr = interpreter_frame_expression_stack_at(0);
  // In case of exceptions, the expression stack is invalid and the esp
  // will be reset to express this condition. Therefore, we call f only
  // if addr is 'inside' the stack (i.e., addr >= esp for Intel).
  bool in_stack;
  if (interpreter_frame_expression_stack_direction() > 0) {
    in_stack = (intptr_t*)frst_expr <= tos_addr;
  } else {
    in_stack = (intptr_t*)frst_expr >= tos_addr;
  }
  if (!in_stack) return;

  jint stack_size = interpreter_frame_expression_stack_size() - args_size;
  for (int j = 0; j < stack_size; j++) {
    Tag tag = interpreter_frame_expression_stack_tag(j);
    if (tag == TagReference) {
      oop *addr = (oop*) interpreter_frame_expression_stack_at(j);
      f->do_oop(addr);
#ifdef ASSERT
    } else {
      assert(tag == TagValue, "bad tag value for stack element");
      oop *p = (oop*) interpreter_frame_expression_stack_at((j));
      assert(*p == NULL || !mask->is_oop(j+max_locals), "stack oop map mismatch");
#endif // ASSERT
    }
  }
}

void frame::oops_interpreted_arguments_do(symbolHandle signature, bool is_static, OopClosure* f) {
  InterpretedArgumentOopFinder finder(signature, is_static, this, f);
  finder.oops_do();
}

void frame::oops_code_blob_do(OopClosure* f, CodeBlobClosure* cf, const RegisterMap* reg_map) {
  assert(_cb != NULL, "sanity check");
  if (_cb->oop_maps() != NULL) {
    OopMapSet::oops_do(this, reg_map, f);

    // Preserve potential arguments for a callee. We handle this by dispatching
    // on the codeblob. For c2i, we do
    if (reg_map->include_argument_oops()) {
      _cb->preserve_callee_argument_oops(*this, reg_map, f);
    }
  }
  // In cases where perm gen is collected, GC will want to mark
  // oops referenced from nmethods active on thread stacks so as to
  // prevent them from being collected. However, this visit should be
  // restricted to certain phases of the collection only. The
  // closure decides how it wants nmethods to be traced.
  if (cf != NULL)
    cf->do_code_blob(_cb);
}

class CompiledArgumentOopFinder: public SignatureInfo {
 protected:
  OopClosure*     _f;
  int             _offset;      // the current offset, incremented with each argument
  bool            _is_static;   // true if the callee is a static method
  frame           _fr;
  RegisterMap*    _reg_map;
  int             _arg_size;
  VMRegPair*      _regs;        // VMReg list of arguments

  void set(int size, BasicType type) {
    if (type == T_OBJECT || type == T_ARRAY) handle_oop_offset();
    _offset += size;
  }

  virtual void handle_oop_offset() {
    // Extract low order register number from register array.
    // In LP64-land, the high-order bits are valid but unhelpful.
    VMReg reg = _regs[_offset].first();
    oop *loc = _fr.oopmapreg_to_location(reg, _reg_map);
    _f->do_oop(loc);
  }

 public:
  CompiledArgumentOopFinder(symbolHandle signature, bool is_static, OopClosure* f, frame fr,  const RegisterMap* reg_map)
    : SignatureInfo(signature) {

    // initialize CompiledArgumentOopFinder
    _f         = f;
    _offset    = 0;
    _is_static = is_static;
    _fr        = fr;
    _reg_map   = (RegisterMap*)reg_map;
    _arg_size  = ArgumentSizeComputer(signature).size() + (is_static ? 0 : 1);

    int arg_size;
    _regs = SharedRuntime::find_callee_arguments(signature(), is_static, &arg_size);
    assert(arg_size == _arg_size, "wrong arg size");
  }

  void oops_do() {
    if (!_is_static) {
      handle_oop_offset();
      _offset++;
    }
    iterate_parameters();
  }
};

void frame::oops_compiled_arguments_do(symbolHandle signature, bool is_static, const RegisterMap* reg_map, OopClosure* f) {
  ResourceMark rm;
  CompiledArgumentOopFinder finder(signature, is_static, f, *this, reg_map);
  finder.oops_do();
}


// Get receiver out of callers frame, i.e. find parameter 0 in callers
// frame.  Consult ADLC for where parameter 0 is to be found.  Then
// check local reg_map for it being a callee-save register or argument
// register, both of which are saved in the local frame.  If not found
// there, it must be an in-stack argument of the caller.
// Note: caller.sp() points to callee-arguments
oop frame::retrieve_receiver(RegisterMap* reg_map) {
  frame caller = *this;

  // First consult the ADLC on where it puts parameter 0 for this signature.
  VMReg reg = SharedRuntime::name_for_receiver();
  oop r = *caller.oopmapreg_to_location(reg, reg_map);
  assert( Universe::heap()->is_in_or_null(r), "bad receiver" );
  return r;
}


oop* frame::oopmapreg_to_location(VMReg reg, const RegisterMap* reg_map) const {
  if(reg->is_reg()) {
    // If it is passed in a register, it got spilled in the stub frame.
    return (oop *)reg_map->location(reg);
  } else {
    int sp_offset_in_bytes = reg->reg2stack() * VMRegImpl::stack_slot_size;
    return (oop*)(((address)unextended_sp()) + sp_offset_in_bytes);
  }
}

BasicLock* frame::compiled_synchronized_native_monitor(nmethod* nm) {
  if (nm == NULL) {
    assert(_cb != NULL && _cb->is_nmethod() &&
           nm->method()->is_native() &&
           nm->method()->is_synchronized(),
           "should not call this otherwise");
    nm = (nmethod*) _cb;
  }
  int byte_offset = in_bytes(nm->compiled_synchronized_native_basic_lock_sp_offset());
  assert(byte_offset >= 0, "should not see invalid offset");
  return (BasicLock*) &sp()[byte_offset / wordSize];
}

oop frame::compiled_synchronized_native_monitor_owner(nmethod* nm) {
  if (nm == NULL) {
    assert(_cb != NULL && _cb->is_nmethod() &&
           nm->method()->is_native() &&
           nm->method()->is_synchronized(),
           "should not call this otherwise");
    nm = (nmethod*) _cb;
  }
  int byte_offset = in_bytes(nm->compiled_synchronized_native_basic_lock_owner_sp_offset());
  assert(byte_offset >= 0, "should not see invalid offset");
  oop owner = ((oop*) sp())[byte_offset / wordSize];
  assert( Universe::heap()->is_in(owner), "bad receiver" );
  return owner;
}

void frame::oops_entry_do(OopClosure* f, const RegisterMap* map) {
  assert(map != NULL, "map must be set");
  if (map->include_argument_oops()) {
    // must collect argument oops, as nobody else is doing it
    Thread *thread = Thread::current();
    methodHandle m (thread, entry_frame_call_wrapper()->callee_method());
    symbolHandle signature (thread, m->signature());
    EntryFrameOopFinder finder(this, signature, m->is_static());
    finder.arguments_do(f);
  }
  // Traverse the Handle Block saved in the entry frame
  entry_frame_call_wrapper()->oops_do(f);
}


void frame::oops_do_internal(OopClosure* f, CodeBlobClosure* cf, RegisterMap* map, bool use_interpreter_oop_map_cache) {
         if (is_interpreted_frame())    { oops_interpreted_do(f, map, use_interpreter_oop_map_cache);
  } else if (is_entry_frame())          { oops_entry_do      (f, map);
  } else if (CodeCache::contains(pc())) { oops_code_blob_do  (f, cf, map);
  } else {
    ShouldNotReachHere();
  }
}

void frame::nmethods_do(CodeBlobClosure* cf) {
  if (_cb != NULL && _cb->is_nmethod()) {
    cf->do_code_blob(_cb);
  }
}


void frame::gc_prologue() {
  if (is_interpreted_frame()) {
    // set bcx to bci to become methodOop position independent during GC
    interpreter_frame_set_bcx(interpreter_frame_bci());
  }
}


void frame::gc_epilogue() {
  if (is_interpreted_frame()) {
    // set bcx back to bcp for interpreter
    interpreter_frame_set_bcx((intptr_t)interpreter_frame_bcp());
  }
  // call processor specific epilog function
  pd_gc_epilog();
}


# ifdef ENABLE_ZAP_DEAD_LOCALS

void frame::CheckValueClosure::do_oop(oop* p) {
  if (CheckOopishValues && Universe::heap()->is_in_reserved(*p)) {
    warning("value @ " INTPTR_FORMAT " looks oopish (" INTPTR_FORMAT ") (thread = " INTPTR_FORMAT ")", p, (address)*p, Thread::current());
  }
}
frame::CheckValueClosure frame::_check_value;


void frame::CheckOopClosure::do_oop(oop* p) {
  if (*p != NULL && !(*p)->is_oop()) {
    warning("value @ " INTPTR_FORMAT " should be an oop (" INTPTR_FORMAT ") (thread = " INTPTR_FORMAT ")", p, (address)*p, Thread::current());
 }
}
frame::CheckOopClosure frame::_check_oop;

void frame::check_derived_oop(oop* base, oop* derived) {
  _check_oop.do_oop(base);
}


void frame::ZapDeadClosure::do_oop(oop* p) {
  if (TraceZapDeadLocals) tty->print_cr("zapping @ " INTPTR_FORMAT " containing " INTPTR_FORMAT, p, (address)*p);
  // Need cast because on _LP64 the conversion to oop is ambiguous.  Constant
  // can be either long or int.
  *p = (oop)(int)0xbabebabe;
}
frame::ZapDeadClosure frame::_zap_dead;

void frame::zap_dead_locals(JavaThread* thread, const RegisterMap* map) {
  assert(thread == Thread::current(), "need to synchronize to do this to another thread");
  // Tracing - part 1
  if (TraceZapDeadLocals) {
    ResourceMark rm(thread);
    tty->print_cr("--------------------------------------------------------------------------------");
    tty->print("Zapping dead locals in ");
    print_on(tty);
    tty->cr();
  }
  // Zapping
       if (is_entry_frame      ()) zap_dead_entry_locals      (thread, map);
  else if (is_interpreted_frame()) zap_dead_interpreted_locals(thread, map);
  else if (is_compiled_frame()) zap_dead_compiled_locals   (thread, map);

  else
    // could be is_runtime_frame
    // so remove error: ShouldNotReachHere();
    ;
  // Tracing - part 2
  if (TraceZapDeadLocals) {
    tty->cr();
  }
}


void frame::zap_dead_interpreted_locals(JavaThread *thread, const RegisterMap* map) {
  // get current interpreter 'pc'
  assert(is_interpreted_frame(), "Not an interpreted frame");
  methodOop m   = interpreter_frame_method();
  int       bci = interpreter_frame_bci();

  int max_locals = m->is_native() ? m->size_of_parameters() : m->max_locals();

  if (TaggedStackInterpreter) {
    InterpreterOopMap *mask = NULL;
#ifdef ASSERT
    InterpreterOopMap oopmap_mask;
    methodHandle method(thread, m);
    OopMapCache::compute_one_oop_map(method, bci, &oopmap_mask);
    mask = &oopmap_mask;
#endif // ASSERT
    oops_interpreted_locals_do(&_check_oop, max_locals, mask);
  } else {
    // process dynamic part
    InterpreterFrameClosure value_blk(this, max_locals, m->max_stack(),
                                      &_check_value);
    InterpreterFrameClosure   oop_blk(this, max_locals, m->max_stack(),
                                      &_check_oop  );
    InterpreterFrameClosure  dead_blk(this, max_locals, m->max_stack(),
                                      &_zap_dead   );

    // get frame map
    InterpreterOopMap mask;
    m->mask_for(bci, &mask);
    mask.iterate_all( &oop_blk, &value_blk, &dead_blk);
  }
}


void frame::zap_dead_compiled_locals(JavaThread* thread, const RegisterMap* reg_map) {

  ResourceMark rm(thread);
  assert(_cb != NULL, "sanity check");
  if (_cb->oop_maps() != NULL) {
    OopMapSet::all_do(this, reg_map, &_check_oop, check_derived_oop, &_check_value);
  }
}


void frame::zap_dead_entry_locals(JavaThread*, const RegisterMap*) {
  if (TraceZapDeadLocals) warning("frame::zap_dead_entry_locals unimplemented");
}


void frame::zap_dead_deoptimized_locals(JavaThread*, const RegisterMap*) {
  if (TraceZapDeadLocals) warning("frame::zap_dead_deoptimized_locals unimplemented");
}

# endif // ENABLE_ZAP_DEAD_LOCALS

void frame::verify(const RegisterMap* map) {
  // for now make sure receiver type is correct
  if (is_interpreted_frame()) {
    methodOop method = interpreter_frame_method();
    guarantee(method->is_method(), "method is wrong in frame::verify");
    if (!method->is_static()) {
      // fetch the receiver
      oop* p = (oop*) interpreter_frame_local_at(0);
      // make sure we have the right receiver type
    }
  }
  COMPILER2_PRESENT(assert(DerivedPointerTable::is_empty(), "must be empty before verify");)
  oops_do_internal(&VerifyOopClosure::verify_oop, NULL, (RegisterMap*)map, false);
}


#ifdef ASSERT
bool frame::verify_return_pc(address x) {
  if (StubRoutines::returns_to_call_stub(x)) {
    return true;
  }
  if (CodeCache::contains(x)) {
    return true;
  }
  if (Interpreter::contains(x)) {
    return true;
  }
  return false;
}
#endif


#ifdef ASSERT
void frame::interpreter_frame_verify_monitor(BasicObjectLock* value) const {
  assert(is_interpreted_frame(), "Not an interpreted frame");
  // verify that the value is in the right part of the frame
  address low_mark  = (address) interpreter_frame_monitor_end();
  address high_mark = (address) interpreter_frame_monitor_begin();
  address current   = (address) value;

  const int monitor_size = frame::interpreter_frame_monitor_size();
  guarantee((high_mark - current) % monitor_size  ==  0         , "Misaligned top of BasicObjectLock*");
  guarantee( high_mark > current                                , "Current BasicObjectLock* higher than high_mark");

  guarantee((current - low_mark) % monitor_size  ==  0         , "Misaligned bottom of BasicObjectLock*");
  guarantee( current >= low_mark                               , "Current BasicObjectLock* below than low_mark");
}
#endif


//-----------------------------------------------------------------------------------
// StackFrameStream implementation

StackFrameStream::StackFrameStream(JavaThread *thread, bool update) : _reg_map(thread, update) {
  assert(thread->has_last_Java_frame(), "sanity check");
  _fr = thread->last_frame();
  _is_done = false;
}
