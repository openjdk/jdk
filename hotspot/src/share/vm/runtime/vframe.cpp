/*
 * Copyright (c) 1997, 2010, Oracle and/or its affiliates. All rights reserved.
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

# include "incls/_precompiled.incl"
# include "incls/_vframe.cpp.incl"

vframe::vframe(const frame* fr, const RegisterMap* reg_map, JavaThread* thread)
: _reg_map(reg_map), _thread(thread) {
  assert(fr != NULL, "must have frame");
  _fr = *fr;
}

vframe::vframe(const frame* fr, JavaThread* thread)
: _reg_map(thread), _thread(thread) {
  assert(fr != NULL, "must have frame");
  _fr = *fr;
}

vframe* vframe::new_vframe(const frame* f, const RegisterMap* reg_map, JavaThread* thread) {
  // Interpreter frame
  if (f->is_interpreted_frame()) {
    return new interpretedVFrame(f, reg_map, thread);
  }

  // Compiled frame
  CodeBlob* cb = f->cb();
  if (cb != NULL) {
    if (cb->is_nmethod()) {
      nmethod* nm = (nmethod*)cb;
      return new compiledVFrame(f, reg_map, thread, nm);
    }

    if (f->is_runtime_frame()) {
      // Skip this frame and try again.
      RegisterMap temp_map = *reg_map;
      frame s = f->sender(&temp_map);
      return new_vframe(&s, &temp_map, thread);
    }
  }

  // External frame
  return new externalVFrame(f, reg_map, thread);
}

vframe* vframe::sender() const {
  RegisterMap temp_map = *register_map();
  assert(is_top(), "just checking");
  if (_fr.is_entry_frame() && _fr.is_first_frame()) return NULL;
  frame s = _fr.real_sender(&temp_map);
  if (s.is_first_frame()) return NULL;
  return vframe::new_vframe(&s, &temp_map, thread());
}

vframe* vframe::top() const {
  vframe* vf = (vframe*) this;
  while (!vf->is_top()) vf = vf->sender();
  return vf;
}


javaVFrame* vframe::java_sender() const {
  vframe* f = sender();
  while (f != NULL) {
    if (f->is_java_frame()) return javaVFrame::cast(f);
    f = f->sender();
  }
  return NULL;
}

// ------------- javaVFrame --------------

GrowableArray<MonitorInfo*>* javaVFrame::locked_monitors() {
  assert(SafepointSynchronize::is_at_safepoint() || JavaThread::current() == thread(),
         "must be at safepoint or it's a java frame of the current thread");

  GrowableArray<MonitorInfo*>* mons = monitors();
  GrowableArray<MonitorInfo*>* result = new GrowableArray<MonitorInfo*>(mons->length());
  if (mons->is_empty()) return result;

  bool found_first_monitor = false;
  ObjectMonitor *pending_monitor = thread()->current_pending_monitor();
  ObjectMonitor *waiting_monitor = thread()->current_waiting_monitor();
  oop pending_obj = (pending_monitor != NULL ? (oop) pending_monitor->object() : (oop) NULL);
  oop waiting_obj = (waiting_monitor != NULL ? (oop) waiting_monitor->object() : (oop) NULL);

  for (int index = (mons->length()-1); index >= 0; index--) {
    MonitorInfo* monitor = mons->at(index);
    if (monitor->eliminated() && is_compiled_frame()) continue; // skip eliminated monitor
    oop obj = monitor->owner();
    if (obj == NULL) continue; // skip unowned monitor
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

static void print_locked_object_class_name(outputStream* st, Handle obj, const char* lock_state) {
  if (obj.not_null()) {
    st->print("\t- %s <" INTPTR_FORMAT "> ", lock_state, (address)obj());
    if (obj->klass() == SystemDictionary::Class_klass()) {
      klassOop target_klass = java_lang_Class::as_klassOop(obj());
      st->print_cr("(a java.lang.Class for %s)", instanceKlass::cast(target_klass)->external_name());
    } else {
      Klass* k = Klass::cast(obj->klass());
      st->print_cr("(a %s)", k->external_name());
    }
  }
}

void javaVFrame::print_lock_info_on(outputStream* st, int frame_count) {
  ResourceMark rm;

  // If this is the first frame, and java.lang.Object.wait(...) then print out the receiver.
  if (frame_count == 0) {
    if (method()->name() == vmSymbols::wait_name() &&
        instanceKlass::cast(method()->method_holder())->name() == vmSymbols::java_lang_Object()) {
      StackValueCollection* locs = locals();
      if (!locs->is_empty()) {
        StackValue* sv = locs->at(0);
        if (sv->type() == T_OBJECT) {
          Handle o = locs->at(0)->get_obj();
          print_locked_object_class_name(st, o, "waiting on");
        }
      }
    } else if (thread()->current_park_blocker() != NULL) {
      oop obj = thread()->current_park_blocker();
      Klass* k = Klass::cast(obj->klass());
      st->print_cr("\t- %s <" INTPTR_FORMAT "> (a %s)", "parking to wait for ", (address)obj, k->external_name());
    }
  }


  // Print out all monitors that we have locked or are trying to lock
  GrowableArray<MonitorInfo*>* mons = monitors();
  if (!mons->is_empty()) {
    bool found_first_monitor = false;
    for (int index = (mons->length()-1); index >= 0; index--) {
      MonitorInfo* monitor = mons->at(index);
      if (monitor->eliminated() && is_compiled_frame()) { // Eliminated in compiled code
        if (monitor->owner_is_scalar_replaced()) {
          Klass* k = Klass::cast(monitor->owner_klass());
          st->print("\t- eliminated <owner is scalar replaced> (a %s)", k->external_name());
        } else {
          oop obj = monitor->owner();
          if (obj != NULL) {
            print_locked_object_class_name(st, obj, "eliminated");
          }
        }
        continue;
      }
      if (monitor->owner() != NULL) {

        // First, assume we have the monitor locked. If we haven't found an
        // owned monitor before and this is the first frame, then we need to
        // see if we have completed the lock or we are blocked trying to
        // acquire it - we can only be blocked if the monitor is inflated

        const char *lock_state = "locked"; // assume we have the monitor locked
        if (!found_first_monitor && frame_count == 0) {
          markOop mark = monitor->owner()->mark();
          if (mark->has_monitor() &&
              mark->monitor() == thread()->current_pending_monitor()) {
            lock_state = "waiting to lock";
          }
        }

        found_first_monitor = true;
        print_locked_object_class_name(st, monitor->owner(), lock_state);
      }
    }
  }
}

// ------------- interpretedVFrame --------------

u_char* interpretedVFrame::bcp() const {
  return fr().interpreter_frame_bcp();
}

void interpretedVFrame::set_bcp(u_char* bcp) {
  fr().interpreter_frame_set_bcp(bcp);
}

intptr_t* interpretedVFrame::locals_addr_at(int offset) const {
  assert(fr().is_interpreted_frame(), "frame should be an interpreted frame");
  return fr().interpreter_frame_local_at(offset);
}


GrowableArray<MonitorInfo*>* interpretedVFrame::monitors() const {
  GrowableArray<MonitorInfo*>* result = new GrowableArray<MonitorInfo*>(5);
  for (BasicObjectLock* current = (fr().previous_monitor_in_interpreter_frame(fr().interpreter_frame_monitor_begin()));
       current >= fr().interpreter_frame_monitor_end();
       current = fr().previous_monitor_in_interpreter_frame(current)) {
    result->push(new MonitorInfo(current->obj(), current->lock(), false, false));
  }
  return result;
}

int interpretedVFrame::bci() const {
  return method()->bci_from(bcp());
}

methodOop interpretedVFrame::method() const {
  return fr().interpreter_frame_method();
}

StackValueCollection* interpretedVFrame::locals() const {
  int length = method()->max_locals();

  if (method()->is_native()) {
    // If the method is native, max_locals is not telling the truth.
    // maxlocals then equals the size of parameters
    length = method()->size_of_parameters();
  }

  StackValueCollection* result = new StackValueCollection(length);

  // Get oopmap describing oops and int for current bci
  InterpreterOopMap oop_mask;
  if (TraceDeoptimization && Verbose) {
    methodHandle m_h(thread(), method());
    OopMapCache::compute_one_oop_map(m_h, bci(), &oop_mask);
  } else {
    method()->mask_for(bci(), &oop_mask);
  }
  // handle locals
  for(int i=0; i < length; i++) {
    // Find stack location
    intptr_t *addr = locals_addr_at(i);

    // Depending on oop/int put it in the right package
    StackValue *sv;
    if (oop_mask.is_oop(i)) {
      // oop value
      Handle h(*(oop *)addr);
      sv = new StackValue(h);
    } else {
      // integer
      sv = new StackValue(*addr);
    }
    assert(sv != NULL, "sanity check");
    result->add(sv);
  }
  return result;
}

void interpretedVFrame::set_locals(StackValueCollection* values) const {
  if (values == NULL || values->size() == 0) return;

  int length = method()->max_locals();
  if (method()->is_native()) {
    // If the method is native, max_locals is not telling the truth.
    // maxlocals then equals the size of parameters
    length = method()->size_of_parameters();
  }

  assert(length == values->size(), "Mismatch between actual stack format and supplied data");

  // handle locals
  for (int i = 0; i < length; i++) {
    // Find stack location
    intptr_t *addr = locals_addr_at(i);

    // Depending on oop/int put it in the right package
    StackValue *sv = values->at(i);
    assert(sv != NULL, "sanity check");
    if (sv->type() == T_OBJECT) {
      *(oop *) addr = (sv->get_obj())();
    } else {                   // integer
      *addr = sv->get_int();
    }
  }
}

StackValueCollection*  interpretedVFrame::expressions() const {
  int length = fr().interpreter_frame_expression_stack_size();
  if (method()->is_native()) {
    // If the method is native, there is no expression stack
    length = 0;
  }

  int nof_locals = method()->max_locals();
  StackValueCollection* result = new StackValueCollection(length);

  InterpreterOopMap oop_mask;
  // Get oopmap describing oops and int for current bci
  if (TraceDeoptimization && Verbose) {
    methodHandle m_h(method());
    OopMapCache::compute_one_oop_map(m_h, bci(), &oop_mask);
  } else {
    method()->mask_for(bci(), &oop_mask);
  }
  // handle expressions
  for(int i=0; i < length; i++) {
    // Find stack location
    intptr_t *addr = fr().interpreter_frame_expression_stack_at(i);

    // Depending on oop/int put it in the right package
    StackValue *sv;
    if (oop_mask.is_oop(i + nof_locals)) {
      // oop value
      Handle h(*(oop *)addr);
      sv = new StackValue(h);
    } else {
      // integer
      sv = new StackValue(*addr);
    }
    assert(sv != NULL, "sanity check");
    result->add(sv);
  }
  return result;
}


// ------------- cChunk --------------

entryVFrame::entryVFrame(const frame* fr, const RegisterMap* reg_map, JavaThread* thread)
: externalVFrame(fr, reg_map, thread) {}


void vframeStreamCommon::found_bad_method_frame() {
  // 6379830 Cut point for an assertion that occasionally fires when
  // we are using the performance analyzer.
  // Disable this assert when testing the analyzer with fastdebug.
  // -XX:SuppressErrorAt=vframe.cpp:XXX (XXX=following line number)
  assert(false, "invalid bci or invalid scope desc");
}

// top-frame will be skipped
vframeStream::vframeStream(JavaThread* thread, frame top_frame,
  bool stop_at_java_call_stub) : vframeStreamCommon(thread) {
  _stop_at_java_call_stub = stop_at_java_call_stub;

  // skip top frame, as it may not be at safepoint
  _frame  = top_frame.sender(&_reg_map);
  while (!fill_from_frame()) {
    _frame = _frame.sender(&_reg_map);
  }
}


// Step back n frames, skip any pseudo frames in between.
// This function is used in Class.forName, Class.newInstance, Method.Invoke,
// AccessController.doPrivileged.
//
// NOTE that in JDK 1.4 this has been exposed to Java as
// sun.reflect.Reflection.getCallerClass(), which can be inlined.
// Inlined versions must match this routine's logic.
// Native method prefixing logic does not need to match since
// the method names don't match and inlining will not occur.
// See, for example,
// Parse::inline_native_Reflection_getCallerClass in
// opto/library_call.cpp.
void vframeStreamCommon::security_get_caller_frame(int depth) {
  bool use_new_reflection = JDK_Version::is_gte_jdk14x_version() && UseNewReflection;

  while (!at_end()) {
    if (Universe::reflect_invoke_cache()->is_same_method(method())) {
      // This is Method.invoke() -- skip it
    } else if (use_new_reflection &&
              Klass::cast(method()->method_holder())
                 ->is_subclass_of(SystemDictionary::reflect_MethodAccessorImpl_klass())) {
      // This is an auxilary frame -- skip it
    } else if (method()->is_method_handle_adapter()) {
      // This is an internal adapter frame from the MethodHandleCompiler -- skip it
    } else {
      // This is non-excluded frame, we need to count it against the depth
      if (depth-- <= 0) {
        // we have reached the desired depth, we are done
        break;
      }
    }
    if (method()->is_prefixed_native()) {
      skip_prefixed_method_and_wrappers();
    } else {
      next();
    }
  }
}


void vframeStreamCommon::skip_prefixed_method_and_wrappers() {
  ResourceMark rm;
  HandleMark hm;

  int    method_prefix_count = 0;
  char** method_prefixes = JvmtiExport::get_all_native_method_prefixes(&method_prefix_count);
  KlassHandle prefixed_klass(method()->method_holder());
  const char* prefixed_name = method()->name()->as_C_string();
  size_t prefixed_name_len = strlen(prefixed_name);
  int prefix_index = method_prefix_count-1;

  while (!at_end()) {
    next();
    if (method()->method_holder() != prefixed_klass()) {
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


void vframeStreamCommon::skip_reflection_related_frames() {
  while (!at_end() &&
         (JDK_Version::is_gte_jdk14x_version() && UseNewReflection &&
          (Klass::cast(method()->method_holder())->is_subclass_of(SystemDictionary::reflect_MethodAccessorImpl_klass()) ||
           Klass::cast(method()->method_holder())->is_subclass_of(SystemDictionary::reflect_ConstructorAccessorImpl_klass())))) {
    next();
  }
}


#ifndef PRODUCT
void vframe::print() {
  if (WizardMode) _fr.print_value_on(tty,NULL);
}


void vframe::print_value() const {
  ((vframe*)this)->print();
}


void entryVFrame::print_value() const {
  ((entryVFrame*)this)->print();
}

void entryVFrame::print() {
  vframe::print();
  tty->print_cr("C Chunk inbetween Java");
  tty->print_cr("C     link " INTPTR_FORMAT, _fr.link());
}


// ------------- javaVFrame --------------

static void print_stack_values(const char* title, StackValueCollection* values) {
  if (values->is_empty()) return;
  tty->print_cr("\t%s:", title);
  values->print();
}


void javaVFrame::print() {
  ResourceMark rm;
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
      Klass* k = Klass::cast(monitor->owner_klass());
      tty->print("( is scalar replaced %s)", k->external_name());
    } else if (monitor->owner() == NULL) {
      tty->print("( null )");
    } else {
      monitor->owner()->print_value();
      tty->print("(" INTPTR_FORMAT ")", (address)monitor->owner());
    }
    if (monitor->eliminated() && is_compiled_frame())
      tty->print(" ( lock is eliminated )");
    tty->cr();
    tty->print("\t  ");
    monitor->lock()->print_on(tty);
    tty->cr();
  }
}


void javaVFrame::print_value() const {
  methodOop  m = method();
  klassOop   k = m->method_holder();
  tty->print_cr("frame( sp=" INTPTR_FORMAT ", unextended_sp=" INTPTR_FORMAT ", fp=" INTPTR_FORMAT ", pc=" INTPTR_FORMAT ")",
                _fr.sp(),  _fr.unextended_sp(), _fr.fp(), _fr.pc());
  tty->print("%s.%s", Klass::cast(k)->internal_name(), m->name()->as_C_string());

  if (!m->is_native()) {
    symbolOop  source_name = instanceKlass::cast(k)->source_file_name();
    int        line_number = m->line_number_from_bci(bci());
    if (source_name != NULL && (line_number != -1)) {
      tty->print("(%s:%d)", source_name->as_C_string(), line_number);
    }
  } else {
    tty->print("(Native Method)");
  }
  // Check frame size and print warning if it looks suspiciously large
  if (fr().sp() != NULL) {
    RegisterMap map = *register_map();
    uint size = fr().frame_size(&map);
#ifdef _LP64
    if (size > 8*K) warning("SUSPICIOUSLY LARGE FRAME (%d)", size);
#else
    if (size > 4*K) warning("SUSPICIOUSLY LARGE FRAME (%d)", size);
#endif
  }
}


bool javaVFrame::structural_compare(javaVFrame* other) {
  // Check static part
  if (method() != other->method()) return false;
  if (bci()    != other->bci())    return false;

  // Check locals
  StackValueCollection *locs = locals();
  StackValueCollection *other_locs = other->locals();
  assert(locs->size() == other_locs->size(), "sanity check");
  int i;
  for(i = 0; i < locs->size(); i++) {
    // it might happen the compiler reports a conflict and
    // the interpreter reports a bogus int.
    if (       is_compiled_frame() &&       locs->at(i)->type() == T_CONFLICT) continue;
    if (other->is_compiled_frame() && other_locs->at(i)->type() == T_CONFLICT) continue;

    if (!locs->at(i)->equal(other_locs->at(i)))
      return false;
  }

  // Check expressions
  StackValueCollection* exprs = expressions();
  StackValueCollection* other_exprs = other->expressions();
  assert(exprs->size() == other_exprs->size(), "sanity check");
  for(i = 0; i < exprs->size(); i++) {
    if (!exprs->at(i)->equal(other_exprs->at(i)))
      return false;
  }

  return true;
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


void javaVFrame::verify() const {
}


void interpretedVFrame::verify() const {
}


// ------------- externalVFrame --------------

void externalVFrame::print() {
  _fr.print_value_on(tty,NULL);
}


void externalVFrame::print_value() const {
  ((vframe*)this)->print();
}
#endif // PRODUCT
