/*
 * Copyright (c) 1999, 2010, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_PRIMS_JVMTIIMPL_HPP
#define SHARE_VM_PRIMS_JVMTIIMPL_HPP

#ifndef JVMTI_KERNEL

#include "classfile/systemDictionary.hpp"
#include "jvmtifiles/jvmti.h"
#include "oops/objArrayOop.hpp"
#include "prims/jvmtiEnvThreadState.hpp"
#include "prims/jvmtiEventController.hpp"
#include "prims/jvmtiTrace.hpp"
#include "prims/jvmtiUtil.hpp"
#include "runtime/stackValueCollection.hpp"
#include "runtime/vm_operations.hpp"

//
// Forward Declarations
//

class JvmtiBreakpoint;
class JvmtiBreakpoints;


///////////////////////////////////////////////////////////////
//
// class GrowableCache, GrowableElement
// Used by              : JvmtiBreakpointCache
// Used by JVMTI methods: none directly.
//
// GrowableCache is a permanent CHeap growable array of <GrowableElement *>
//
// In addition, the GrowableCache maintains a NULL terminated cache array of type address
// that's created from the element array using the function:
//     address GrowableElement::getCacheValue().
//
// Whenever the GrowableArray changes size, the cache array gets recomputed into a new C_HEAP allocated
// block of memory. Additionally, every time the cache changes its position in memory, the
//    void (*_listener_fun)(void *this_obj, address* cache)
// gets called with the cache's new address. This gives the user of the GrowableCache a callback
// to update its pointer to the address cache.
//

class GrowableElement : public CHeapObj {
public:
  virtual address getCacheValue()          =0;
  virtual bool equals(GrowableElement* e)  =0;
  virtual bool lessThan(GrowableElement *e)=0;
  virtual GrowableElement *clone()         =0;
  virtual void oops_do(OopClosure* f)      =0;
};

class GrowableCache VALUE_OBJ_CLASS_SPEC {

private:
  // Object pointer passed into cache & listener functions.
  void *_this_obj;

  // Array of elements in the collection
  GrowableArray<GrowableElement *> *_elements;

  // Parallel array of cached values
  address *_cache;

  // Listener for changes to the _cache field.
  // Called whenever the _cache field has it's value changed
  // (but NOT when cached elements are recomputed).
  void (*_listener_fun)(void *, address*);

  static bool equals(void *, GrowableElement *);

  // recache all elements after size change, notify listener
  void recache();

public:
   GrowableCache();
   ~GrowableCache();

  void initialize(void *this_obj, void listener_fun(void *, address*) );

  // number of elements in the collection
  int length();
  // get the value of the index element in the collection
  GrowableElement* at(int index);
  // find the index of the element, -1 if it doesn't exist
  int find(GrowableElement* e);
  // append a copy of the element to the end of the collection, notify listener
  void append(GrowableElement* e);
  // insert a copy of the element using lessthan(), notify listener
  void insert(GrowableElement* e);
  // remove the element at index, notify listener
  void remove (int index);
  // clear out all elements and release all heap space, notify listener
  void clear();
  // apply f to every element and update the cache
  void oops_do(OopClosure* f);
};


///////////////////////////////////////////////////////////////
//
// class JvmtiBreakpointCache
// Used by              : JvmtiBreakpoints
// Used by JVMTI methods: none directly.
// Note   : typesafe wrapper for GrowableCache of JvmtiBreakpoint
//

class JvmtiBreakpointCache : public CHeapObj {

private:
  GrowableCache _cache;

public:
  JvmtiBreakpointCache()  {}
  ~JvmtiBreakpointCache() {}

  void initialize(void *this_obj, void listener_fun(void *, address*) ) {
    _cache.initialize(this_obj,listener_fun);
  }

  int length()                          { return _cache.length(); }
  JvmtiBreakpoint& at(int index)        { return (JvmtiBreakpoint&) *(_cache.at(index)); }
  int find(JvmtiBreakpoint& e)          { return _cache.find((GrowableElement *) &e); }
  void append(JvmtiBreakpoint& e)       { _cache.append((GrowableElement *) &e); }
  void remove (int index)               { _cache.remove(index); }
  void clear()                          { _cache.clear(); }
  void oops_do(OopClosure* f)           { _cache.oops_do(f); }
};


///////////////////////////////////////////////////////////////
//
// class JvmtiBreakpoint
// Used by              : JvmtiBreakpoints
// Used by JVMTI methods: SetBreakpoint, ClearBreakpoint, ClearAllBreakpoints
// Note: Extends GrowableElement for use in a GrowableCache
//
// A JvmtiBreakpoint describes a location (class, method, bci) to break at.
//

typedef void (methodOopDesc::*method_action)(int _bci);

class JvmtiBreakpoint : public GrowableElement {
private:
  methodOop             _method;
  int                   _bci;
  Bytecodes::Code       _orig_bytecode;

public:
  JvmtiBreakpoint();
  JvmtiBreakpoint(methodOop m_method, jlocation location);
  bool equals(JvmtiBreakpoint& bp);
  bool lessThan(JvmtiBreakpoint &bp);
  void copy(JvmtiBreakpoint& bp);
  bool is_valid();
  address getBcp();
  void each_method_version_do(method_action meth_act);
  void set();
  void clear();
  void print();

  methodOop method() { return _method; }

  // GrowableElement implementation
  address getCacheValue()         { return getBcp(); }
  bool lessThan(GrowableElement* e) { Unimplemented(); return false; }
  bool equals(GrowableElement* e) { return equals((JvmtiBreakpoint&) *e); }
  void oops_do(OopClosure* f)     { f->do_oop((oop *) &_method); }
  GrowableElement *clone()        {
    JvmtiBreakpoint *bp = new JvmtiBreakpoint();
    bp->copy(*this);
    return bp;
  }
};


///////////////////////////////////////////////////////////////
//
// class VM_ChangeBreakpoints
// Used by              : JvmtiBreakpoints
// Used by JVMTI methods: none directly.
// Note: A Helper class.
//
// VM_ChangeBreakpoints implements a VM_Operation for ALL modifications to the JvmtiBreakpoints class.
//

class VM_ChangeBreakpoints : public VM_Operation {
private:
  JvmtiBreakpoints* _breakpoints;
  int               _operation;
  JvmtiBreakpoint*  _bp;

public:
  enum { SET_BREAKPOINT=0, CLEAR_BREAKPOINT=1, CLEAR_ALL_BREAKPOINT=2 };

  VM_ChangeBreakpoints(JvmtiBreakpoints* breakpoints, int operation) {
    _breakpoints = breakpoints;
    _bp = NULL;
    _operation = operation;
    assert(breakpoints != NULL, "breakpoints != NULL");
    assert(operation == CLEAR_ALL_BREAKPOINT, "unknown breakpoint operation");
  }
  VM_ChangeBreakpoints(JvmtiBreakpoints* breakpoints, int operation, JvmtiBreakpoint *bp) {
    _breakpoints = breakpoints;
    _bp = bp;
    _operation = operation;
    assert(breakpoints != NULL, "breakpoints != NULL");
    assert(bp != NULL, "bp != NULL");
    assert(operation == SET_BREAKPOINT || operation == CLEAR_BREAKPOINT , "unknown breakpoint operation");
  }

  VMOp_Type type() const { return VMOp_ChangeBreakpoints; }
  void doit();
  void oops_do(OopClosure* f);
};


///////////////////////////////////////////////////////////////
//
// class JvmtiBreakpoints
// Used by              : JvmtiCurrentBreakpoints
// Used by JVMTI methods: none directly
// Note: A Helper class
//
// JvmtiBreakpoints is a GrowableCache of JvmtiBreakpoint.
// All changes to the GrowableCache occur at a safepoint using VM_ChangeBreakpoints.
//
// Because _bps is only modified at safepoints, its possible to always use the
// cached byte code pointers from _bps without doing any synchronization (see JvmtiCurrentBreakpoints).
//
// It would be possible to make JvmtiBreakpoints a static class, but I've made it
// CHeap allocated to emphasize its similarity to JvmtiFramePops.
//

class JvmtiBreakpoints : public CHeapObj {
private:

  JvmtiBreakpointCache _bps;

  // These should only be used by VM_ChangeBreakpoints
  // to insure they only occur at safepoints.
  // Todo: add checks for safepoint
  friend class VM_ChangeBreakpoints;
  void set_at_safepoint(JvmtiBreakpoint& bp);
  void clear_at_safepoint(JvmtiBreakpoint& bp);
  void clearall_at_safepoint();

  static void do_element(GrowableElement *e);

public:
  JvmtiBreakpoints(void listener_fun(void *, address *));
  ~JvmtiBreakpoints();

  int length();
  void oops_do(OopClosure* f);
  void print();

  int  set(JvmtiBreakpoint& bp);
  int  clear(JvmtiBreakpoint& bp);
  void clearall_in_class_at_safepoint(klassOop klass);
  void clearall();
};


///////////////////////////////////////////////////////////////
//
// class JvmtiCurrentBreakpoints
//
// A static wrapper class for the JvmtiBreakpoints that provides:
// 1. a fast inlined function to check if a byte code pointer is a breakpoint (is_breakpoint).
// 2. a function for lazily creating the JvmtiBreakpoints class (this is not strictly necessary,
//    but I'm copying the code from JvmtiThreadState which needs to lazily initialize
//    JvmtiFramePops).
// 3. An oops_do entry point for GC'ing the breakpoint array.
//

class JvmtiCurrentBreakpoints : public AllStatic {

private:

  // Current breakpoints, lazily initialized by get_jvmti_breakpoints();
  static JvmtiBreakpoints *_jvmti_breakpoints;

  // NULL terminated cache of byte-code pointers corresponding to current breakpoints.
  // Updated only at safepoints (with listener_fun) when the cache is moved.
  // It exists only to make is_breakpoint fast.
  static address          *_breakpoint_list;
  static inline void set_breakpoint_list(address *breakpoint_list) { _breakpoint_list = breakpoint_list; }
  static inline address *get_breakpoint_list()                     { return _breakpoint_list; }

  // Listener for the GrowableCache in _jvmti_breakpoints, updates _breakpoint_list.
  static void listener_fun(void *this_obj, address *cache);

public:
  static void initialize();
  static void destroy();

  // lazily create _jvmti_breakpoints and _breakpoint_list
  static JvmtiBreakpoints& get_jvmti_breakpoints();

  // quickly test whether the bcp matches a cached breakpoint in the list
  static inline bool is_breakpoint(address bcp);

  static void oops_do(OopClosure* f);
};

// quickly test whether the bcp matches a cached breakpoint in the list
bool JvmtiCurrentBreakpoints::is_breakpoint(address bcp) {
    address *bps = get_breakpoint_list();
    if (bps == NULL) return false;
    for ( ; (*bps) != NULL; bps++) {
      if ((*bps) == bcp) return true;
    }
    return false;
}

///////////////////////////////////////////////////////////////
// The get/set local operations must only be done by the VM thread
// because the interpreter version needs to access oop maps, which can
// only safely be done by the VM thread
//
// I'm told that in 1.5 oop maps are now protected by a lock and
// we could get rid of the VM op
// However if the VM op is removed then the target thread must
// be suspended AND a lock will be needed to prevent concurrent
// setting of locals to the same java thread. This lock is needed
// to prevent compiledVFrames from trying to add deferred updates
// to the thread simultaneously.
//
class VM_GetOrSetLocal : public VM_Operation {
 protected:
  JavaThread* _thread;
  JavaThread* _calling_thread;
  jint        _depth;
  jint        _index;
  BasicType   _type;
  jvalue      _value;
  javaVFrame* _jvf;
  bool        _set;

  // It is possible to get the receiver out of a non-static native wrapper
  // frame.  Use VM_GetReceiver to do this.
  virtual bool getting_receiver() const { return false; }

  jvmtiError  _result;

  vframe* get_vframe();
  javaVFrame* get_java_vframe();
  bool check_slot_type(javaVFrame* vf);

public:
  // Constructor for non-object getter
  VM_GetOrSetLocal(JavaThread* thread, jint depth, jint index, BasicType type);

  // Constructor for object or non-object setter
  VM_GetOrSetLocal(JavaThread* thread, jint depth, jint index, BasicType type, jvalue value);

  // Constructor for object getter
  VM_GetOrSetLocal(JavaThread* thread, JavaThread* calling_thread, jint depth,
                   int index);

  VMOp_Type type() const { return VMOp_GetOrSetLocal; }
  jvalue value()         { return _value; }
  jvmtiError result()    { return _result; }

  bool doit_prologue();
  void doit();
  bool allow_nested_vm_operations() const;
  const char* name() const                       { return "get/set locals"; }

  // Check that the klass is assignable to a type with the given signature.
  static bool is_assignable(const char* ty_sign, Klass* klass, Thread* thread);
};

class VM_GetReceiver : public VM_GetOrSetLocal {
 protected:
  virtual bool getting_receiver() const { return true; }

 public:
  VM_GetReceiver(JavaThread* thread, JavaThread* calling_thread, jint depth);
  const char* name() const                       { return "get receiver"; }
};


///////////////////////////////////////////////////////////////
//
// class JvmtiSuspendControl
//
// Convenience routines for suspending and resuming threads.
//
// All attempts by JVMTI to suspend and resume threads must go through the
// JvmtiSuspendControl interface.
//
// methods return true if successful
//
class JvmtiSuspendControl : public AllStatic {
public:
  // suspend the thread, taking it to a safepoint
  static bool suspend(JavaThread *java_thread);
  // resume the thread
  static bool resume(JavaThread *java_thread);

  static void print();
};

#endif // !JVMTI_KERNEL

// Utility macro that checks for NULL pointers:
#define NULL_CHECK(X, Y) if ((X) == NULL) { return (Y); }

#endif // SHARE_VM_PRIMS_JVMTIIMPL_HPP
