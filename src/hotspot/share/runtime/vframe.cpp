/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/javaClasses.inline.hpp"
#include "classfile/javaThreadStatus.hpp"
#include "classfile/vmClasses.hpp"
#include "classfile/vmSymbols.hpp"
#include "code/codeCache.hpp"
#include "code/debugInfoRec.hpp"
#include "code/nmethod.hpp"
#include "code/pcDesc.hpp"
#include "code/scopeDesc.hpp"
#include "interpreter/interpreter.hpp"
#include "interpreter/oopMapCache.hpp"
#include "memory/resourceArea.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/method.inline.hpp"
#include "oops/oop.inline.hpp"
#include "oops/stackChunkOop.hpp"
#include "prims/jvmtiExport.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/globals.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/javaThread.inline.hpp"
#include "runtime/objectMonitor.hpp"
#include "runtime/objectMonitor.inline.hpp"
#include "runtime/osThread.hpp"
#include "runtime/signature.hpp"
#include "runtime/stackFrameStream.inline.hpp"
#include "runtime/stubRoutines.hpp"
#include "runtime/synchronizer.hpp"
#include "runtime/vframe.inline.hpp"
#include "runtime/vframeArray.hpp"
#include "runtime/vframe_hp.hpp"

vframe::vframe(const frame* fr, const RegisterMap* reg_map, JavaThread* thread)
: _reg_map(reg_map), _thread(thread),
  _chunk(Thread::current(), reg_map->stack_chunk()()) {
  assert(fr != nullptr, "must have frame");
  _fr = *fr;
}

vframe* vframe::new_vframe(const frame* f, const RegisterMap* reg_map, JavaThread* thread) {
  // Interpreter frame
  if (f->is_interpreted_frame()) {
    return new interpretedVFrame(f, reg_map, thread);
  }

  // Compiled frame
  CodeBlob* cb = f->cb();
  if (cb != nullptr) {
    if (cb->is_nmethod()) {
      nmethod* nm = cb->as_nmethod();
      return new compiledVFrame(f, reg_map, thread, nm);
    }

    if (f->is_runtime_frame()) {
      // Skip this frame and try again.
      RegisterMap temp_map = *reg_map;
      frame s = f->sender(&temp_map);
      return new_vframe(&s, &temp_map, thread);
    }
  }

  // Entry frame
  if (f->is_entry_frame()) {
    return new entryVFrame(f, reg_map, thread);
  }

  // External frame
  return new externalVFrame(f, reg_map, thread);
}

vframe* vframe::sender() const {
  RegisterMap temp_map = *register_map();
  assert(is_top(), "just checking");
  if (_fr.is_empty()) return nullptr;
  if (_fr.is_entry_frame() && _fr.is_first_frame()) return nullptr;
  frame s = _fr.real_sender(&temp_map);
  if (s.is_first_frame()) return nullptr;
  return vframe::new_vframe(&s, &temp_map, thread());
}

bool vframe::is_vthread_entry() const {
  return _fr.is_first_vthread_frame(register_map()->thread());
}

javaVFrame* vframe::java_sender() const {
  vframe* f = sender();
  while (f != nullptr) {
    if (f->is_vthread_entry()) break;
    if (f->is_java_frame() && !javaVFrame::cast(f)->method()->is_continuation_enter_intrinsic())
      return javaVFrame::cast(f);
    f = f->sender();
  }
  return nullptr;
}

// ------------- javaVFrame --------------

GrowableArray<MonitorInfo*>* javaVFrame::locked_monitors() {
  assert(SafepointSynchronize::is_at_safepoint() || JavaThread::current() == thread(),
         "must be at safepoint or it's a java frame of the current thread");

  GrowableArray<MonitorInfo*>* mons = monitors();
  GrowableArray<MonitorInfo*>* result = new GrowableArray<MonitorInfo*>(mons->length());
  if (mons->is_empty()) return result;

  bool found_first_monitor = false;
  // The ObjectMonitor* can't be async deflated since we are either
  // at a safepoint or the calling thread is operating on itself so
  // it cannot exit the ObjectMonitor so it remains busy.
  ObjectMonitor *waiting_monitor = thread()->current_waiting_monitor();
  ObjectMonitor *pending_monitor = nullptr;
  if (waiting_monitor == nullptr) {
    pending_monitor = thread()->current_pending_monitor();
  }
  oop pending_obj = (pending_monitor != nullptr ? pending_monitor->object() : (oop) nullptr);
  oop waiting_obj = (waiting_monitor != nullptr ? waiting_monitor->object() : (oop) nullptr);

  for (int index = (mons->length()-1); index >= 0; index--) {
    MonitorInfo* monitor = mons->at(index);
    if (monitor->eliminated() && is_compiled_frame()) continue; // skip eliminated monitor
    oop obj = monitor->owner();
    if (obj == nullptr) continue; // skip unowned monitor
    //
    // Skip the monitor that the thread is blocked to enter or waiting on
    //
    if (!found_first_monitor && (obj == pending_obj || obj == waiting_obj)) {
      continue;
    }
    found_first_monitor = true;
    result->append(monitor);
  }
  return result;
}

void javaVFrame::print_locked_object_class_name(outputStream* st, Handle obj, const char* lock_state) {
  if (obj.not_null()) {
    st->print("\t- %s <" INTPTR_FORMAT "> ", lock_state, p2i(obj()));
    if (obj->klass() == vmClasses::Class_klass()) {
      st->print_cr("(a java.lang.Class for %s)", java_lang_Class::as_external_name(obj()));
    } else {
      Klass* k = obj->klass();
      st->print_cr("(a %s)", k->external_name());
    }
  }
}

void javaVFrame::print_lock_info_on(outputStream* st, int frame_count) {
  Thread* current = Thread::current();
  ResourceMark rm(current);
  HandleMark hm(current);

  // If this is the first frame and it is java.lang.Object.wait(...)
  // then print out the receiver. Locals are not always available,
  // e.g., compiled native frames have no scope so there are no locals.
  if (frame_count == 0) {
    if (method()->name() == vmSymbols::wait_name() &&
        method()->method_holder()->name() == vmSymbols::java_lang_Object()) {
      const char *wait_state = "waiting on"; // assume we are waiting
      // If earlier in the output we reported java.lang.Thread.State ==
      // "WAITING (on object monitor)" and now we report "waiting on", then
      // we are still waiting for notification or timeout. Otherwise if
      // we earlier reported java.lang.Thread.State == "BLOCKED (on object
      // monitor)", then we are actually waiting to re-lock the monitor.
      StackValueCollection* locs = locals();
      if (!locs->is_empty()) {
        StackValue* sv = locs->at(0);
        if (sv->type() == T_OBJECT) {
          Handle o = locs->at(0)->get_obj();
          if (java_lang_Thread::get_thread_status(thread()->threadObj()) ==
                                JavaThreadStatus::BLOCKED_ON_MONITOR_ENTER) {
            wait_state = "waiting to re-lock in wait()";
          }
          print_locked_object_class_name(st, o, wait_state);
        }
      } else {
        st->print_cr("\t- %s <no object reference available>", wait_state);
      }
    } else if (thread()->current_park_blocker() != nullptr) {
      oop obj = thread()->current_park_blocker();
      Klass* k = obj->klass();
      st->print_cr("\t- %s <" INTPTR_FORMAT "> (a %s)", "parking to wait for ", p2i(obj), k->external_name());
    }
    else if (thread()->osthread()->get_state() == CONDVAR_WAIT) {
      // We are waiting on the native class initialization monitor.
      InstanceKlass* k = thread()->class_to_be_initialized();
      if (k != nullptr) {
        st->print_cr("\t- waiting on the Class initialization monitor for %s", k->external_name());
      }
    }
  }

  // Print out all monitors that we have locked, or are trying to lock,
  // including re-locking after being notified or timing out in a wait().
  GrowableArray<MonitorInfo*>* mons = monitors();
  if (!mons->is_empty()) {
    bool found_first_monitor = false;
    for (int index = (mons->length()-1); index >= 0; index--) {
      MonitorInfo* monitor = mons->at(index);
      if (monitor->eliminated() && is_compiled_frame()) { // Eliminated in compiled code
        if (monitor->owner_is_scalar_replaced()) {
          Klass* k = java_lang_Class::as_Klass(monitor->owner_klass());
          st->print_cr("\t- eliminated <owner is scalar replaced> (a %s)", k->external_name());
        } else {
          Handle obj(current, monitor->owner());
          if (obj() != nullptr) {
            print_locked_object_class_name(st, obj, "eliminated");
          }
        }
        continue;
      }
      if (monitor->owner() != nullptr) {
        // the monitor is associated with an object, i.e., it is locked

        const char *lock_state = "locked"; // assume we have the monitor locked
        if (!found_first_monitor && frame_count == 0) {
          // If this is the first frame and we haven't found an owned
          // monitor before, then we need to see if we have completed
          // the lock or if we are blocked trying to acquire it. Only
          // an inflated monitor that is first on the monitor list in
          // the first frame can block us on a monitor enter.
          markWord mark = monitor->owner()->mark();
          // The first stage of async deflation does not affect any field
          // used by this comparison so the ObjectMonitor* is usable here.
          if (mark.has_monitor() &&
              ( // we have marked ourself as pending on this monitor
                mark.monitor() == thread()->current_pending_monitor() ||
                // we are not the owner of this monitor
                !mark.monitor()->is_entered(thread())
              )) {
            lock_state = "waiting to lock";
          }
        }
        print_locked_object_class_name(st, Handle(current, monitor->owner()), lock_state);

        found_first_monitor = true;
      }
    }
  }
}

// ------------- interpretedVFrame --------------

u_char* interpretedVFrame::bcp() const {
  return stack_chunk() == nullptr ? fr().interpreter_frame_bcp() : stack_chunk()->interpreter_frame_bcp(fr());
}

intptr_t* interpretedVFrame::locals_addr_at(int offset) const {
  assert(stack_chunk() == nullptr, "Not supported for heap frames"); // unsupported for now because seems to be unused
  assert(fr().is_interpreted_frame(), "frame should be an interpreted frame");
  return fr().interpreter_frame_local_at(offset);
}

GrowableArray<MonitorInfo*>* interpretedVFrame::monitors() const {
  GrowableArray<MonitorInfo*>* result = new GrowableArray<MonitorInfo*>(5);
  if (stack_chunk() == nullptr) { // no monitors in continuations
    for (BasicObjectLock* current = (fr().previous_monitor_in_interpreter_frame(fr().interpreter_frame_monitor_begin()));
        current >= fr().interpreter_frame_monitor_end();
        current = fr().previous_monitor_in_interpreter_frame(current)) {
      result->push(new MonitorInfo(current->obj(), current->lock(), false, false));
    }
  }
  return result;
}

int interpretedVFrame::bci() const {
  return method()->bci_from(bcp());
}

Method* interpretedVFrame::method() const {
  return stack_chunk() == nullptr ? fr().interpreter_frame_method() : stack_chunk()->interpreter_frame_method(fr());
}

static StackValue* create_stack_value_from_oop_map(const InterpreterOopMap& oop_mask,
                                                   int index,
                                                   const intptr_t* const addr,
                                                   stackChunkOop chunk) {

  assert(index >= 0 && index < oop_mask.number_of_entries(), "invariant");

  // categorize using oop_mask
  if (oop_mask.is_oop(index)) {
    return StackValue::create_stack_value_from_oop_location(chunk, (void*)addr);
  }
  // value (integer) "v"
  return new StackValue(addr != nullptr ? *addr : 0);
}

static bool is_in_expression_stack(const frame& fr, const intptr_t* const addr) {
  assert(addr != nullptr, "invariant");

  // Ensure to be 'inside' the expression stack (i.e., addr >= sp for Intel).
  // In case of exceptions, the expression stack is invalid and the sp
  // will be reset to express this condition.
  if (frame::interpreter_frame_expression_stack_direction() > 0) {
    return addr <= fr.interpreter_frame_tos_address();
  }

  return addr >= fr.interpreter_frame_tos_address();
}

static void stack_locals(StackValueCollection* result,
                         int length,
                         const InterpreterOopMap& oop_mask,
                         const frame& fr,
                         const stackChunkOop chunk) {

  assert(result != nullptr, "invariant");

  for (int i = 0; i < length; ++i) {
    const intptr_t* addr;
    if (chunk == nullptr) {
      addr = fr.interpreter_frame_local_at(i);
      assert(addr >= fr.sp(), "must be inside the frame");
    } else {
      addr = chunk->interpreter_frame_local_at(fr, i);
    }
    assert(addr != nullptr, "invariant");

    StackValue* const sv = create_stack_value_from_oop_map(oop_mask, i, addr, chunk);
    assert(sv != nullptr, "sanity check");

    result->add(sv);
  }
}

static void stack_expressions(StackValueCollection* result,
                              int length,
                              int max_locals,
                              const InterpreterOopMap& oop_mask,
                              const frame& fr,
                              const stackChunkOop chunk) {

  assert(result != nullptr, "invariant");

  for (int i = 0; i < length; ++i) {
    const intptr_t* addr;
    if (chunk == nullptr) {
      addr = fr.interpreter_frame_expression_stack_at(i);
      assert(addr != nullptr, "invariant");
      if (!is_in_expression_stack(fr, addr)) {
        // Need to ensure no bogus escapes.
        addr = nullptr;
      }
    } else {
      addr = chunk->interpreter_frame_expression_stack_at(fr, i);
    }

    StackValue* const sv = create_stack_value_from_oop_map(oop_mask,
                                                           i + max_locals,
                                                           addr,
                                                           chunk);
    assert(sv != nullptr, "sanity check");

    result->add(sv);
  }
}

StackValueCollection* interpretedVFrame::locals() const {
  return stack_data(false);
}

StackValueCollection* interpretedVFrame::expressions() const {
  return stack_data(true);
}

/*
 * Worker routine for fetching references and/or values
 * for a particular bci in the interpretedVFrame.
 *
 * Returns data for either "locals" or "expressions",
 * using bci relative oop_map (oop_mask) information.
 *
 * @param expressions  bool switch controlling what data to return
                       (false == locals / true == expression)
 *
 */
StackValueCollection* interpretedVFrame::stack_data(bool expressions) const {

  InterpreterOopMap oop_mask;
  method()->mask_for(bci(), &oop_mask);
  const int mask_len = oop_mask.number_of_entries();

  // If the method is native, method()->max_locals() is not telling the truth.
  // For our purposes, max locals instead equals the size of parameters.
  const int max_locals = method()->is_native() ?
    method()->size_of_parameters() : method()->max_locals();

  assert(mask_len >= max_locals, "invariant");

  const int length = expressions ? mask_len - max_locals : max_locals;
  assert(length >= 0, "invariant");

  StackValueCollection* const result = new StackValueCollection(length);

  if (0 == length) {
    return result;
  }

  if (expressions) {
    stack_expressions(result, length, max_locals, oop_mask, fr(), stack_chunk());
  } else {
    stack_locals(result, length, oop_mask, fr(), stack_chunk());
  }

  assert(length == result->size(), "invariant");

  return result;
}

void interpretedVFrame::set_locals(StackValueCollection* values) const {
  if (values == nullptr || values->size() == 0) return;

  // If the method is native, max_locals is not telling the truth.
  // maxlocals then equals the size of parameters
  const int max_locals = method()->is_native() ?
    method()->size_of_parameters() : method()->max_locals();

  assert(max_locals == values->size(), "Mismatch between actual stack format and supplied data");

  // handle locals
  for (int i = 0; i < max_locals; i++) {
    // Find stack location
    intptr_t *addr = locals_addr_at(i);

    // Depending on oop/int put it in the right package
    const StackValue* const sv = values->at(i);
    assert(sv != nullptr, "sanity check");
    if (sv->type() == T_OBJECT) {
      *(oop *) addr = (sv->get_obj())();
    } else {                   // integer
      *addr = sv->get_intptr();
    }
  }
}

// ------------- cChunk --------------

entryVFrame::entryVFrame(const frame* fr, const RegisterMap* reg_map, JavaThread* thread)
: externalVFrame(fr, reg_map, thread) {}

MonitorInfo::MonitorInfo(oop owner, BasicLock* lock, bool eliminated, bool owner_is_scalar_replaced) {
  Thread* thread = Thread::current();
  if (!owner_is_scalar_replaced) {
    _owner = Handle(thread, owner);
    _owner_klass = Handle();
  } else {
    assert(eliminated, "monitor should be eliminated for scalar replaced object");
    _owner = Handle();
    _owner_klass = Handle(thread, owner);
  }
  _lock = lock;
  _eliminated = eliminated;
  _owner_is_scalar_replaced = owner_is_scalar_replaced;
}

#ifdef ASSERT
void vframeStreamCommon::found_bad_method_frame() const {
  // 6379830 Cut point for an assertion that occasionally fires when
  // we are using the performance analyzer.
  // Disable this when testing the analyzer with fastdebug.
  fatal("invalid bci or invalid scope desc");
}
#endif

vframeStream::vframeStream(JavaThread* thread, Handle continuation_scope, bool stop_at_java_call_stub)
 : vframeStreamCommon(RegisterMap(thread,
                                  RegisterMap::UpdateMap::include,
                                  RegisterMap::ProcessFrames::include,
                                  RegisterMap::WalkContinuation::include)) {

  _stop_at_java_call_stub = stop_at_java_call_stub;
  _continuation_scope = continuation_scope;

  if (!thread->has_last_Java_frame()) {
    _mode = at_end_mode;
    return;
  }

  _frame = _thread->last_frame();
  _cont_entry = _thread->last_continuation();
  while (!fill_from_frame()) {
    _frame = _frame.sender(&_reg_map);
  }
}

vframeStream::vframeStream(oop continuation, Handle continuation_scope)
 : vframeStreamCommon(RegisterMap(continuation, RegisterMap::UpdateMap::include)) {

  _stop_at_java_call_stub = false;
  _continuation_scope = continuation_scope;

  if (!Continuation::has_last_Java_frame(continuation, &_frame, &_reg_map)) {
    _mode = at_end_mode;
    return;
  }

  // _chunk = _reg_map.stack_chunk();
  while (!fill_from_frame()) {
    _frame = _frame.sender(&_reg_map);
  }
}


// Step back n frames, skip any pseudo frames in between.
// This function is used in Class.forName, Class.newInstance, Method.Invoke,
// AccessController.doPrivileged.
void vframeStreamCommon::security_get_caller_frame(int depth) {
  assert(depth >= 0, "invalid depth: %d", depth);
  for (int n = 0; !at_end(); security_next()) {
    if (!method()->is_ignored_by_security_stack_walk()) {
      if (n == depth) {
        // We have reached the desired depth; return.
        return;
      }
      n++;  // this is a non-skipped frame; count it against the depth
    }
  }
  // NOTE: At this point there were not enough frames on the stack
  // to walk to depth.  Callers of this method have to check for at_end.
}


void vframeStreamCommon::security_next() {
  if (method()->is_prefixed_native()) {
    skip_prefixed_method_and_wrappers();  // calls next()
  } else {
    next();
  }
}


void vframeStreamCommon::skip_prefixed_method_and_wrappers() {
  ResourceMark rm;

  int    method_prefix_count = 0;
  char** method_prefixes = JvmtiExport::get_all_native_method_prefixes(&method_prefix_count);
  Klass* prefixed_klass = method()->method_holder();
  const char* prefixed_name = method()->name()->as_C_string();
  size_t prefixed_name_len = strlen(prefixed_name);
  int prefix_index = method_prefix_count-1;

  while (!at_end()) {
    next();
    if (method()->method_holder() != prefixed_klass) {
      break; // classes don't match, can't be a wrapper
    }
    const char* name = method()->name()->as_C_string();
    size_t name_len = strlen(name);
    size_t prefix_len = prefixed_name_len - name_len;
    if (prefix_len <= 0 || strcmp(name, prefixed_name + prefix_len) != 0) {
      break; // prefixed name isn't prefixed version of method name, can't be a wrapper
    }
    for (; prefix_index >= 0; --prefix_index) {
      const char* possible_prefix = method_prefixes[prefix_index];
      size_t possible_prefix_len = strlen(possible_prefix);
      if (possible_prefix_len == prefix_len &&
          strncmp(possible_prefix, prefixed_name, prefix_len) == 0) {
        break; // matching prefix found
      }
    }
    if (prefix_index < 0) {
      break; // didn't find the prefix, can't be a wrapper
    }
    prefixed_name = name;
    prefixed_name_len = name_len;
  }
}

javaVFrame* vframeStreamCommon::asJavaVFrame() {
  javaVFrame* result = nullptr;
  // FIXME, need to re-do JDK-8271140 and check is_native_frame?
  if (_mode == compiled_mode && _frame.is_compiled_frame()) {
    assert(_frame.is_compiled_frame() || _frame.is_native_frame(), "expected compiled Java frame");
    guarantee(_reg_map.update_map(), "");

    compiledVFrame* cvf = compiledVFrame::cast(vframe::new_vframe(&_frame, &_reg_map, _thread));

    guarantee(cvf->cb() == cb(), "wrong code blob");

    cvf = cvf->at_scope(_decode_offset, _vframe_id); // get the same scope as this stream

    guarantee(cvf->scope()->decode_offset() == _decode_offset, "wrong scope");
    guarantee(cvf->scope()->sender_decode_offset() == _sender_decode_offset, "wrong scope");
    guarantee(cvf->vframe_id() == _vframe_id, "wrong vframe");

    result = cvf;
  } else {
    result = javaVFrame::cast(vframe::new_vframe(&_frame, &_reg_map, _thread));
  }
  assert(result->method() == method(), "wrong method");
  return result;
}

#ifndef PRODUCT
void vframe::print() {
  if (WizardMode) _fr.print_value_on(tty,nullptr);
}

void vframe::print_value() const {
  ((vframe*)this)->print();
}


void entryVFrame::print_value() const {
  ((entryVFrame*)this)->print();
}

void entryVFrame::print() {
  vframe::print();
  tty->print_cr("C Chunk in between Java");
  tty->print_cr("C     link " INTPTR_FORMAT, p2i(_fr.link()));
}


// ------------- javaVFrame --------------

static void print_stack_values(const char* title, StackValueCollection* values) {
  if (values->is_empty()) return;
  tty->print_cr("\t%s:", title);
  values->print();
}


void javaVFrame::print() {
  Thread* current_thread = Thread::current();
  ResourceMark rm(current_thread);
  HandleMark hm(current_thread);

  vframe::print();
  tty->print("\t");
  method()->print_value();
  tty->cr();
  tty->print_cr("\tbci:    %d", bci());

  print_stack_values("locals",      locals());
  print_stack_values("expressions", expressions());

  GrowableArray<MonitorInfo*>* list = monitors();
  if (list->is_empty()) return;
  tty->print_cr("\tmonitor list:");
  for (int index = (list->length()-1); index >= 0; index--) {
    MonitorInfo* monitor = list->at(index);
    tty->print("\t  obj\t");
    if (monitor->owner_is_scalar_replaced()) {
      Klass* k = java_lang_Class::as_Klass(monitor->owner_klass());
      tty->print("( is scalar replaced %s)", k->external_name());
    } else if (monitor->owner() == nullptr) {
      tty->print("( null )");
    } else {
      monitor->owner()->print_value();
      tty->print("(owner=" INTPTR_FORMAT ")", p2i(monitor->owner()));
    }
    if (monitor->eliminated()) {
      if(is_compiled_frame()) {
        tty->print(" ( lock is eliminated in compiled frame )");
      } else {
        tty->print(" ( lock is eliminated, frame not compiled )");
      }
    }
    tty->cr();
    tty->print("\t  ");
    monitor->lock()->print_on(tty, monitor->owner());
    tty->cr();
  }
}


void javaVFrame::print_value() const {
  Method*    m = method();
  InstanceKlass*     k = m->method_holder();
  tty->print_cr("frame( sp=" INTPTR_FORMAT ", unextended_sp=" INTPTR_FORMAT ", fp=" INTPTR_FORMAT ", pc=" INTPTR_FORMAT ")",
                p2i(_fr.sp()),  p2i(_fr.unextended_sp()), p2i(_fr.fp()), p2i(_fr.pc()));
  tty->print("%s.%s", k->internal_name(), m->name()->as_C_string());

  if (!m->is_native()) {
    Symbol*  source_name = k->source_file_name();
    int        line_number = m->line_number_from_bci(bci());
    if (source_name != nullptr && (line_number != -1)) {
      tty->print("(%s:%d)", source_name->as_C_string(), line_number);
    }
  } else {
    tty->print("(Native Method)");
  }
  // Check frame size and print warning if it looks suspiciously large
  if (fr().sp() != nullptr) {
    RegisterMap map = *register_map();
    uint size = fr().frame_size();
#ifdef _LP64
    if (size > 8*K) warning("SUSPICIOUSLY LARGE FRAME (%d)", size);
#else
    if (size > 4*K) warning("SUSPICIOUSLY LARGE FRAME (%d)", size);
#endif
  }
}

void javaVFrame::print_activation(int index) const {
  // frame number and method
  tty->print("%2d - ", index);
  ((vframe*)this)->print_value();
  tty->cr();

  if (WizardMode) {
    ((vframe*)this)->print();
    tty->cr();
  }
}

// ------------- externalVFrame --------------

void externalVFrame::print() {
  _fr.print_value_on(tty,nullptr);
}

void externalVFrame::print_value() const {
  ((vframe*)this)->print();
}
#endif // PRODUCT
