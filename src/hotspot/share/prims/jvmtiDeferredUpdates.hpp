/*
 * Copyright (c) 1997, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020 SAP SE. All rights reserved.
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

#ifndef SHARE_PRIMS_JVMTIDEFERREDUPDATES_HPP
#define SHARE_PRIMS_JVMTIDEFERREDUPDATES_HPP

#include "runtime/javaThread.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/growableArray.hpp"

class MonitorInfo;
template <typename T> class GrowableArray;

class jvmtiDeferredLocalVariable : public CHeapObj<mtCompiler> {

  private:

    BasicType _type;
    jvalue    _value;
    int       _index;

  public:

    jvmtiDeferredLocalVariable(int index, BasicType type, jvalue value);

    BasicType type(void)         { return _type; }
    int index(void)              { return _index; }
    jvalue value(void)           { return _value; }

    // Only mutator is for value as only it can change
    void set_value(jvalue value) { _value = value; }

    // For gc
    oop* oop_addr(void)          { return (oop*) &_value.l; }
};

// In order to implement set_locals for compiled vframes we must
// store updated locals in a data structure that contains enough
// information to recognize equality with a vframe and to store
// any updated locals.

class StackValueCollection;

class jvmtiDeferredLocalVariableSet : public CHeapObj<mtCompiler> {
  friend class compiledVFrame;

private:

  Method*   _method;
  int       _bci;
  int       _vframe_id;
  GrowableArray<jvmtiDeferredLocalVariable*>* _locals;
  bool      _objects_are_deoptimized;

  void      update_value(StackValueCollection* locals, BasicType type, int index, jvalue value);

  void      set_value_at(int idx, BasicType typ, jvalue val);

 public:
  // JVM state
  Method*   method()                  const { return _method; }
  int       bci()                     const { return _bci; }
  int       vframe_id()               const { return _vframe_id; }
  bool      objects_are_deoptimized() const { return _objects_are_deoptimized; }

  void      update_locals(StackValueCollection* locals);
  void      update_stack(StackValueCollection* locals);
  void      update_monitors(GrowableArray<MonitorInfo*>* monitors);
  void      set_objs_are_deoptimized()      { _objects_are_deoptimized = true; }

  // Does the vframe match this jvmtiDeferredLocalVariableSet
  bool      matches(const vframe* vf);

  // GC
  void      oops_do(OopClosure* f);

  // constructor
  jvmtiDeferredLocalVariableSet(Method* method, int bci, int vframe_id);

  // destructor
  ~jvmtiDeferredLocalVariableSet();
};

// Holds updates for compiled frames by JVMTI agents that cannot be performed immediately.

class JvmtiDeferredUpdates : public CHeapObj<mtCompiler> {

  address _original_pc;

  // Deferred updates of locals, expressions, and monitors
  GrowableArray<jvmtiDeferredLocalVariableSet*> _deferred_locals_updates;

public:
  JvmtiDeferredUpdates(address original_pc) :
    _original_pc(original_pc),
    _deferred_locals_updates((AnyObj::set_allocation_type((address) &_deferred_locals_updates,
                                                          AnyObj::C_HEAP), 1), mtCompiler) { }

  ~JvmtiDeferredUpdates();

  address original_pc() const { return _original_pc; }
  GrowableArray<jvmtiDeferredLocalVariableSet*>* deferred_locals() { return &_deferred_locals_updates; }
};

#endif // SHARE_PRIMS_JVMTIDEFERREDUPDATES_HPP
