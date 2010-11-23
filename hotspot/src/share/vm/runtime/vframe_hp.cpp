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

#include "precompiled.hpp"
#include "code/codeCache.hpp"
#include "code/debugInfoRec.hpp"
#include "code/nmethod.hpp"
#include "code/pcDesc.hpp"
#include "code/scopeDesc.hpp"
#include "interpreter/interpreter.hpp"
#include "interpreter/oopMapCache.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/basicLock.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/monitorChunk.hpp"
#include "runtime/signature.hpp"
#include "runtime/stubRoutines.hpp"
#include "runtime/vframeArray.hpp"
#include "runtime/vframe_hp.hpp"
#ifdef COMPILER2
#include "opto/matcher.hpp"
#endif


// ------------- compiledVFrame --------------

StackValueCollection* compiledVFrame::locals() const {
  // Natives has no scope
  if (scope() == NULL) return new StackValueCollection(0);
  GrowableArray<ScopeValue*>*  scv_list = scope()->locals();
  if (scv_list == NULL) return new StackValueCollection(0);

  // scv_list is the list of ScopeValues describing the JVM stack state.
  // There is one scv_list entry for every JVM stack state in use.
  int length = scv_list->length();
  StackValueCollection* result = new StackValueCollection(length);
  // In rare instances set_locals may have occurred in which case
  // there are local values that are not described by the ScopeValue anymore
  GrowableArray<jvmtiDeferredLocalVariable*>* deferred = NULL;
  GrowableArray<jvmtiDeferredLocalVariableSet*>* list = thread()->deferred_locals();
  if (list != NULL ) {
    // In real life this never happens or is typically a single element search
    for (int i = 0; i < list->length(); i++) {
      if (list->at(i)->matches((vframe*)this)) {
        deferred = list->at(i)->locals();
        break;
      }
    }
  }

  for( int i = 0; i < length; i++ ) {
    result->add( create_stack_value(scv_list->at(i)) );
  }

  // Replace specified locals with any deferred writes that are present
  if (deferred != NULL) {
    for ( int l = 0;  l < deferred->length() ; l ++) {
      jvmtiDeferredLocalVariable* val = deferred->at(l);
      switch (val->type()) {
      case T_BOOLEAN:
        result->set_int_at(val->index(), val->value().z);
        break;
      case T_CHAR:
        result->set_int_at(val->index(), val->value().c);
        break;
      case T_FLOAT:
        result->set_float_at(val->index(), val->value().f);
        break;
      case T_DOUBLE:
        result->set_double_at(val->index(), val->value().d);
        break;
      case T_BYTE:
        result->set_int_at(val->index(), val->value().b);
        break;
      case T_SHORT:
        result->set_int_at(val->index(), val->value().s);
        break;
      case T_INT:
        result->set_int_at(val->index(), val->value().i);
        break;
      case T_LONG:
        result->set_long_at(val->index(), val->value().j);
        break;
      case T_OBJECT:
        {
          Handle obj((oop)val->value().l);
          result->set_obj_at(val->index(), obj);
        }
        break;
      default:
        ShouldNotReachHere();
      }
    }
  }

  return result;
}


void compiledVFrame::set_locals(StackValueCollection* values) const {

  fatal("Should use update_local for each local update");
}

void compiledVFrame::update_local(BasicType type, int index, jvalue value) {

#ifdef ASSERT

  assert(fr().is_deoptimized_frame(), "frame must be scheduled for deoptimization");
#endif /* ASSERT */
  GrowableArray<jvmtiDeferredLocalVariableSet*>* deferred = thread()->deferred_locals();
  if (deferred != NULL ) {
    // See if this vframe has already had locals with deferred writes
    int f;
    for ( f = 0 ; f < deferred->length() ; f++ ) {
      if (deferred->at(f)->matches(this)) {
        // Matching, vframe now see if the local already had deferred write
        GrowableArray<jvmtiDeferredLocalVariable*>* locals = deferred->at(f)->locals();
        int l;
        for (l = 0 ; l < locals->length() ; l++ ) {
          if (locals->at(l)->index() == index) {
            locals->at(l)->set_value(value);
            return;
          }
        }
        // No matching local already present. Push a new value onto the deferred collection
        locals->push(new jvmtiDeferredLocalVariable(index, type, value));
        return;
      }
    }
    // No matching vframe must push a new vframe
  } else {
    // No deferred updates pending for this thread.
    // allocate in C heap
    deferred =  new(ResourceObj::C_HEAP) GrowableArray<jvmtiDeferredLocalVariableSet*> (1, true);
    thread()->set_deferred_locals(deferred);
  }
  deferred->push(new jvmtiDeferredLocalVariableSet(method(), bci(), fr().id()));
  assert(deferred->top()->id() == fr().id(), "Huh? Must match");
  deferred->top()->set_local_at(index, type, value);
}

StackValueCollection* compiledVFrame::expressions() const {
  // Natives has no scope
  if (scope() == NULL) return new StackValueCollection(0);
  GrowableArray<ScopeValue*>*  scv_list = scope()->expressions();
  if (scv_list == NULL) return new StackValueCollection(0);

  // scv_list is the list of ScopeValues describing the JVM stack state.
  // There is one scv_list entry for every JVM stack state in use.
  int length = scv_list->length();
  StackValueCollection* result = new StackValueCollection(length);
  for( int i = 0; i < length; i++ )
    result->add( create_stack_value(scv_list->at(i)) );

  return result;
}


// The implementation of the following two methods was factorized into the
// class StackValue because it is also used from within deoptimization.cpp for
// rematerialization and relocking of non-escaping objects.

StackValue *compiledVFrame::create_stack_value(ScopeValue *sv) const {
  return StackValue::create_stack_value(&_fr, register_map(), sv);
}

BasicLock* compiledVFrame::resolve_monitor_lock(Location location) const {
  return StackValue::resolve_monitor_lock(&_fr, location);
}


GrowableArray<MonitorInfo*>* compiledVFrame::monitors() const {
  // Natives has no scope
  if (scope() == NULL) {
    nmethod* nm = code();
    methodOop method = nm->method();
    assert(method->is_native(), "");
    if (!method->is_synchronized()) {
      return new GrowableArray<MonitorInfo*>(0);
    }
    // This monitor is really only needed for UseBiasedLocking, but
    // return it in all cases for now as it might be useful for stack
    // traces and tools as well
    GrowableArray<MonitorInfo*> *monitors = new GrowableArray<MonitorInfo*>(1);
    // Casting away const
    frame& fr = (frame&) _fr;
    MonitorInfo* info = new MonitorInfo(fr.compiled_synchronized_native_monitor_owner(nm),
                                        fr.compiled_synchronized_native_monitor(nm), false, false);
    monitors->push(info);
    return monitors;
  }
  GrowableArray<MonitorValue*>* monitors = scope()->monitors();
  if (monitors == NULL) {
    return new GrowableArray<MonitorInfo*>(0);
  }
  GrowableArray<MonitorInfo*>* result = new GrowableArray<MonitorInfo*>(monitors->length());
  for (int index = 0; index < monitors->length(); index++) {
    MonitorValue* mv = monitors->at(index);
    ScopeValue*   ov = mv->owner();
    StackValue *owner_sv = create_stack_value(ov); // it is an oop
    if (ov->is_object() && owner_sv->obj_is_scalar_replaced()) { // The owner object was scalar replaced
      assert(mv->eliminated(), "monitor should be eliminated for scalar replaced object");
      // Put klass for scalar replaced object.
      ScopeValue* kv = ((ObjectValue *)ov)->klass();
      assert(kv->is_constant_oop(), "klass should be oop constant for scalar replaced object");
      KlassHandle k(((ConstantOopReadValue*)kv)->value()());
      result->push(new MonitorInfo(k->as_klassOop(), resolve_monitor_lock(mv->basic_lock()),
                                   mv->eliminated(), true));
    } else {
      result->push(new MonitorInfo(owner_sv->get_obj()(), resolve_monitor_lock(mv->basic_lock()),
                                   mv->eliminated(), false));
    }
  }
  return result;
}


compiledVFrame::compiledVFrame(const frame* fr, const RegisterMap* reg_map, JavaThread* thread, nmethod* nm)
: javaVFrame(fr, reg_map, thread) {
  _scope  = NULL;
  // Compiled method (native stub or Java code)
  // native wrappers have no scope data, it is implied
  if (!nm->is_native_method()) {
    _scope  = nm->scope_desc_at(_fr.pc());
  }
}

compiledVFrame::compiledVFrame(const frame* fr, const RegisterMap* reg_map, JavaThread* thread, ScopeDesc* scope)
: javaVFrame(fr, reg_map, thread) {
  _scope  = scope;
  guarantee(_scope != NULL, "scope must be present");
}


bool compiledVFrame::is_top() const {
  // FIX IT: Remove this when new native stubs are in place
  if (scope() == NULL) return true;
  return scope()->is_top();
}


nmethod* compiledVFrame::code() const {
  return CodeCache::find_nmethod(_fr.pc());
}


methodOop compiledVFrame::method() const {
  if (scope() == NULL) {
    // native nmethods have no scope the method is implied
    nmethod* nm = code();
    assert(nm->is_native_method(), "must be native");
    return nm->method();
  }
  return scope()->method()();
}


int compiledVFrame::bci() const {
  int raw = raw_bci();
  return raw == SynchronizationEntryBCI ? 0 : raw;
}


int compiledVFrame::raw_bci() const {
  if (scope() == NULL) {
    // native nmethods have no scope the method/bci is implied
    nmethod* nm = code();
    assert(nm->is_native_method(), "must be native");
    return 0;
  }
  return scope()->bci();
}

bool compiledVFrame::should_reexecute() const {
  if (scope() == NULL) {
    // native nmethods have no scope the method/bci is implied
    nmethod* nm = code();
    assert(nm->is_native_method(), "must be native");
    return false;
  }
  return scope()->should_reexecute();
}

vframe* compiledVFrame::sender() const {
  const frame f = fr();
  if (scope() == NULL) {
    // native nmethods have no scope the method/bci is implied
    nmethod* nm = code();
    assert(nm->is_native_method(), "must be native");
    return vframe::sender();
  } else {
    return scope()->is_top()
      ? vframe::sender()
      : new compiledVFrame(&f, register_map(), thread(), scope()->sender());
  }
}

jvmtiDeferredLocalVariableSet::jvmtiDeferredLocalVariableSet(methodOop method, int bci, intptr_t* id) {
  _method = method;
  _bci = bci;
  _id = id;
  // Alway will need at least one, must be on C heap
  _locals = new(ResourceObj::C_HEAP) GrowableArray<jvmtiDeferredLocalVariable*> (1, true);
}

jvmtiDeferredLocalVariableSet::~jvmtiDeferredLocalVariableSet() {
  for (int i = 0; i < _locals->length() ; i++ ) {
    delete _locals->at(i);
  }
  // Free growableArray and c heap for elements
  delete _locals;
}

bool jvmtiDeferredLocalVariableSet::matches(vframe* vf) {
  if (!vf->is_compiled_frame()) return false;
  compiledVFrame* cvf = (compiledVFrame*)vf;
  return cvf->fr().id() == id() && cvf->method() == method() && cvf->bci() == bci();
}

void jvmtiDeferredLocalVariableSet::set_local_at(int idx, BasicType type, jvalue val) {
  int i;
  for ( i = 0 ; i < locals()->length() ; i++ ) {
    if ( locals()->at(i)->index() == idx) {
      assert(locals()->at(i)->type() == type, "Wrong type");
      locals()->at(i)->set_value(val);
      return;
    }
  }
  locals()->push(new jvmtiDeferredLocalVariable(idx, type, val));
}

void jvmtiDeferredLocalVariableSet::oops_do(OopClosure* f) {

  f->do_oop((oop*) &_method);
  for ( int i = 0; i < locals()->length(); i++ ) {
    if ( locals()->at(i)->type() == T_OBJECT) {
      f->do_oop(locals()->at(i)->oop_addr());
    }
  }
}

jvmtiDeferredLocalVariable::jvmtiDeferredLocalVariable(int index, BasicType type, jvalue value) {
  _index = index;
  _type = type;
  _value = value;
}


#ifndef PRODUCT
void compiledVFrame::verify() const {
  Unimplemented();
}
#endif // PRODUCT
