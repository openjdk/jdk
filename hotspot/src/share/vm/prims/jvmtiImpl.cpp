/*
 * Copyright (c) 2003, 2007, Oracle and/or its affiliates. All rights reserved.
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
# include "incls/_jvmtiImpl.cpp.incl"

GrowableArray<JvmtiRawMonitor*> *JvmtiPendingMonitors::_monitors = new (ResourceObj::C_HEAP) GrowableArray<JvmtiRawMonitor*>(1,true);

void JvmtiPendingMonitors::transition_raw_monitors() {
  assert((Threads::number_of_threads()==1),
         "Java thread has not created yet or more than one java thread \
is running. Raw monitor transition will not work");
  JavaThread *current_java_thread = JavaThread::current();
  assert(current_java_thread->thread_state() == _thread_in_vm, "Must be in vm");
  {
    ThreadBlockInVM __tbivm(current_java_thread);
    for(int i=0; i< count(); i++) {
      JvmtiRawMonitor *rmonitor = monitors()->at(i);
      int r = rmonitor->raw_enter(current_java_thread);
      assert(r == ObjectMonitor::OM_OK, "raw_enter should have worked");
    }
  }
  // pending monitors are converted to real monitor so delete them all.
  dispose();
}

//
// class JvmtiAgentThread
//
// JavaThread used to wrap a thread started by an agent
// using the JVMTI method RunAgentThread.
//

JvmtiAgentThread::JvmtiAgentThread(JvmtiEnv* env, jvmtiStartFunction start_fn, const void *start_arg)
    : JavaThread(start_function_wrapper) {
    _env = env;
    _start_fn = start_fn;
    _start_arg = start_arg;
}

void
JvmtiAgentThread::start_function_wrapper(JavaThread *thread, TRAPS) {
    // It is expected that any Agent threads will be created as
    // Java Threads.  If this is the case, notification of the creation
    // of the thread is given in JavaThread::thread_main().
    assert(thread->is_Java_thread(), "debugger thread should be a Java Thread");
    assert(thread == JavaThread::current(), "sanity check");

    JvmtiAgentThread *dthread = (JvmtiAgentThread *)thread;
    dthread->call_start_function();
}

void
JvmtiAgentThread::call_start_function() {
    ThreadToNativeFromVM transition(this);
    _start_fn(_env->jvmti_external(), jni_environment(), (void*)_start_arg);
}


//
// class GrowableCache - private methods
//

void GrowableCache::recache() {
  int len = _elements->length();

  FREE_C_HEAP_ARRAY(address, _cache);
  _cache = NEW_C_HEAP_ARRAY(address,len+1);

  for (int i=0; i<len; i++) {
    _cache[i] = _elements->at(i)->getCacheValue();
    //
    // The cache entry has gone bad. Without a valid frame pointer
    // value, the entry is useless so we simply delete it in product
    // mode. The call to remove() will rebuild the cache again
    // without the bad entry.
    //
    if (_cache[i] == NULL) {
      assert(false, "cannot recache NULL elements");
      remove(i);
      return;
    }
  }
  _cache[len] = NULL;

  _listener_fun(_this_obj,_cache);
}

bool GrowableCache::equals(void* v, GrowableElement *e2) {
  GrowableElement *e1 = (GrowableElement *) v;
  assert(e1 != NULL, "e1 != NULL");
  assert(e2 != NULL, "e2 != NULL");

  return e1->equals(e2);
}

//
// class GrowableCache - public methods
//

GrowableCache::GrowableCache() {
  _this_obj       = NULL;
  _listener_fun   = NULL;
  _elements       = NULL;
  _cache          = NULL;
}

GrowableCache::~GrowableCache() {
  clear();
  delete _elements;
  FREE_C_HEAP_ARRAY(address, _cache);
}

void GrowableCache::initialize(void *this_obj, void listener_fun(void *, address*) ) {
  _this_obj       = this_obj;
  _listener_fun   = listener_fun;
  _elements       = new (ResourceObj::C_HEAP) GrowableArray<GrowableElement*>(5,true);
  recache();
}

// number of elements in the collection
int GrowableCache::length() {
  return _elements->length();
}

// get the value of the index element in the collection
GrowableElement* GrowableCache::at(int index) {
  GrowableElement *e = (GrowableElement *) _elements->at(index);
  assert(e != NULL, "e != NULL");
  return e;
}

int GrowableCache::find(GrowableElement* e) {
  return _elements->find(e, GrowableCache::equals);
}

// append a copy of the element to the end of the collection
void GrowableCache::append(GrowableElement* e) {
  GrowableElement *new_e = e->clone();
  _elements->append(new_e);
  recache();
}

// insert a copy of the element using lessthan()
void GrowableCache::insert(GrowableElement* e) {
  GrowableElement *new_e = e->clone();
  _elements->append(new_e);

  int n = length()-2;
  for (int i=n; i>=0; i--) {
    GrowableElement *e1 = _elements->at(i);
    GrowableElement *e2 = _elements->at(i+1);
    if (e2->lessThan(e1)) {
      _elements->at_put(i+1, e1);
      _elements->at_put(i,   e2);
    }
  }

  recache();
}

// remove the element at index
void GrowableCache::remove (int index) {
  GrowableElement *e = _elements->at(index);
  assert(e != NULL, "e != NULL");
  _elements->remove(e);
  delete e;
  recache();
}

// clear out all elements, release all heap space and
// let our listener know that things have changed.
void GrowableCache::clear() {
  int len = _elements->length();
  for (int i=0; i<len; i++) {
    delete _elements->at(i);
  }
  _elements->clear();
  recache();
}

void GrowableCache::oops_do(OopClosure* f) {
  int len = _elements->length();
  for (int i=0; i<len; i++) {
    GrowableElement *e = _elements->at(i);
    e->oops_do(f);
  }
}

void GrowableCache::gc_epilogue() {
  int len = _elements->length();
  // recompute the new cache value after GC
  for (int i=0; i<len; i++) {
    _cache[i] = _elements->at(i)->getCacheValue();
  }
}


//
// class JvmtiRawMonitor
//

JvmtiRawMonitor::JvmtiRawMonitor(const char *name) {
#ifdef ASSERT
  _name = strcpy(NEW_C_HEAP_ARRAY(char, strlen(name) + 1), name);
#else
  _name = NULL;
#endif
  _magic = JVMTI_RM_MAGIC;
}

JvmtiRawMonitor::~JvmtiRawMonitor() {
#ifdef ASSERT
  FreeHeap(_name);
#endif
  _magic = 0;
}


bool
JvmtiRawMonitor::is_valid() {
  int value = 0;

  // This object might not be a JvmtiRawMonitor so we can't assume
  // the _magic field is properly aligned. Get the value in a safe
  // way and then check against JVMTI_RM_MAGIC.

  switch (sizeof(_magic)) {
  case 2:
    value = Bytes::get_native_u2((address)&_magic);
    break;

  case 4:
    value = Bytes::get_native_u4((address)&_magic);
    break;

  case 8:
    value = Bytes::get_native_u8((address)&_magic);
    break;

  default:
    guarantee(false, "_magic field is an unexpected size");
  }

  return value == JVMTI_RM_MAGIC;
}


//
// class JvmtiBreakpoint
//

JvmtiBreakpoint::JvmtiBreakpoint() {
  _method = NULL;
  _bci    = 0;
#ifdef CHECK_UNHANDLED_OOPS
  // This one is always allocated with new, but check it just in case.
  Thread *thread = Thread::current();
  if (thread->is_in_stack((address)&_method)) {
    thread->allow_unhandled_oop((oop*)&_method);
  }
#endif // CHECK_UNHANDLED_OOPS
}

JvmtiBreakpoint::JvmtiBreakpoint(methodOop m_method, jlocation location) {
  _method        = m_method;
  assert(_method != NULL, "_method != NULL");
  _bci           = (int) location;
#ifdef CHECK_UNHANDLED_OOPS
  // Could be allocated with new and wouldn't be on the unhandled oop list.
  Thread *thread = Thread::current();
  if (thread->is_in_stack((address)&_method)) {
    thread->allow_unhandled_oop(&_method);
  }
#endif // CHECK_UNHANDLED_OOPS

  assert(_bci >= 0, "_bci >= 0");
}

void JvmtiBreakpoint::copy(JvmtiBreakpoint& bp) {
  _method   = bp._method;
  _bci      = bp._bci;
}

bool JvmtiBreakpoint::lessThan(JvmtiBreakpoint& bp) {
  Unimplemented();
  return false;
}

bool JvmtiBreakpoint::equals(JvmtiBreakpoint& bp) {
  return _method   == bp._method
    &&   _bci      == bp._bci;
}

bool JvmtiBreakpoint::is_valid() {
  return _method != NULL &&
         _bci >= 0;
}

address JvmtiBreakpoint::getBcp() {
  return _method->bcp_from(_bci);
}

void JvmtiBreakpoint::each_method_version_do(method_action meth_act) {
  ((methodOopDesc*)_method->*meth_act)(_bci);

  // add/remove breakpoint to/from versions of the method that
  // are EMCP. Directly or transitively obsolete methods are
  // not saved in the PreviousVersionInfo.
  Thread *thread = Thread::current();
  instanceKlassHandle ikh = instanceKlassHandle(thread, _method->method_holder());
  symbolOop m_name = _method->name();
  symbolOop m_signature = _method->signature();

  {
    ResourceMark rm(thread);
    // PreviousVersionInfo objects returned via PreviousVersionWalker
    // contain a GrowableArray of handles. We have to clean up the
    // GrowableArray _after_ the PreviousVersionWalker destructor
    // has destroyed the handles.
    {
      // search previous versions if they exist
      PreviousVersionWalker pvw((instanceKlass *)ikh()->klass_part());
      for (PreviousVersionInfo * pv_info = pvw.next_previous_version();
           pv_info != NULL; pv_info = pvw.next_previous_version()) {
        GrowableArray<methodHandle>* methods =
          pv_info->prev_EMCP_method_handles();

        if (methods == NULL) {
          // We have run into a PreviousVersion generation where
          // all methods were made obsolete during that generation's
          // RedefineClasses() operation. At the time of that
          // operation, all EMCP methods were flushed so we don't
          // have to go back any further.
          //
          // A NULL methods array is different than an empty methods
          // array. We cannot infer any optimizations about older
          // generations from an empty methods array for the current
          // generation.
          break;
        }

        for (int i = methods->length() - 1; i >= 0; i--) {
          methodHandle method = methods->at(i);
          if (method->name() == m_name && method->signature() == m_signature) {
            RC_TRACE(0x00000800, ("%sing breakpoint in %s(%s)",
              meth_act == &methodOopDesc::set_breakpoint ? "sett" : "clear",
              method->name()->as_C_string(),
              method->signature()->as_C_string()));
            assert(!method->is_obsolete(), "only EMCP methods here");

            ((methodOopDesc*)method()->*meth_act)(_bci);
            break;
          }
        }
      }
    } // pvw is cleaned up
  } // rm is cleaned up
}

void JvmtiBreakpoint::set() {
  each_method_version_do(&methodOopDesc::set_breakpoint);
}

void JvmtiBreakpoint::clear() {
  each_method_version_do(&methodOopDesc::clear_breakpoint);
}

void JvmtiBreakpoint::print() {
#ifndef PRODUCT
  const char *class_name  = (_method == NULL) ? "NULL" : _method->klass_name()->as_C_string();
  const char *method_name = (_method == NULL) ? "NULL" : _method->name()->as_C_string();

  tty->print("Breakpoint(%s,%s,%d,%p)",class_name, method_name, _bci, getBcp());
#endif
}


//
// class VM_ChangeBreakpoints
//
// Modify the Breakpoints data structure at a safepoint
//

void VM_ChangeBreakpoints::doit() {
  switch (_operation) {
  case SET_BREAKPOINT:
    _breakpoints->set_at_safepoint(*_bp);
    break;
  case CLEAR_BREAKPOINT:
    _breakpoints->clear_at_safepoint(*_bp);
    break;
  case CLEAR_ALL_BREAKPOINT:
    _breakpoints->clearall_at_safepoint();
    break;
  default:
    assert(false, "Unknown operation");
  }
}

void VM_ChangeBreakpoints::oops_do(OopClosure* f) {
  // This operation keeps breakpoints alive
  if (_breakpoints != NULL) {
    _breakpoints->oops_do(f);
  }
  if (_bp != NULL) {
    _bp->oops_do(f);
  }
}

//
// class JvmtiBreakpoints
//
// a JVMTI internal collection of JvmtiBreakpoint
//

JvmtiBreakpoints::JvmtiBreakpoints(void listener_fun(void *,address *)) {
  _bps.initialize(this,listener_fun);
}

JvmtiBreakpoints:: ~JvmtiBreakpoints() {}

void  JvmtiBreakpoints::oops_do(OopClosure* f) {
  _bps.oops_do(f);
}

void  JvmtiBreakpoints::gc_epilogue() {
  _bps.gc_epilogue();
}

void  JvmtiBreakpoints::print() {
#ifndef PRODUCT
  ResourceMark rm;

  int n = _bps.length();
  for (int i=0; i<n; i++) {
    JvmtiBreakpoint& bp = _bps.at(i);
    tty->print("%d: ", i);
    bp.print();
    tty->print_cr("");
  }
#endif
}


void JvmtiBreakpoints::set_at_safepoint(JvmtiBreakpoint& bp) {
  assert(SafepointSynchronize::is_at_safepoint(), "must be at safepoint");

  int i = _bps.find(bp);
  if (i == -1) {
    _bps.append(bp);
    bp.set();
  }
}

void JvmtiBreakpoints::clear_at_safepoint(JvmtiBreakpoint& bp) {
  assert(SafepointSynchronize::is_at_safepoint(), "must be at safepoint");

  int i = _bps.find(bp);
  if (i != -1) {
    _bps.remove(i);
    bp.clear();
  }
}

void JvmtiBreakpoints::clearall_at_safepoint() {
  assert(SafepointSynchronize::is_at_safepoint(), "must be at safepoint");

  int len = _bps.length();
  for (int i=0; i<len; i++) {
    _bps.at(i).clear();
  }
  _bps.clear();
}

int JvmtiBreakpoints::length() { return _bps.length(); }

int JvmtiBreakpoints::set(JvmtiBreakpoint& bp) {
  if ( _bps.find(bp) != -1) {
     return JVMTI_ERROR_DUPLICATE;
  }
  VM_ChangeBreakpoints set_breakpoint(this,VM_ChangeBreakpoints::SET_BREAKPOINT, &bp);
  VMThread::execute(&set_breakpoint);
  return JVMTI_ERROR_NONE;
}

int JvmtiBreakpoints::clear(JvmtiBreakpoint& bp) {
  if ( _bps.find(bp) == -1) {
     return JVMTI_ERROR_NOT_FOUND;
  }

  VM_ChangeBreakpoints clear_breakpoint(this,VM_ChangeBreakpoints::CLEAR_BREAKPOINT, &bp);
  VMThread::execute(&clear_breakpoint);
  return JVMTI_ERROR_NONE;
}

void JvmtiBreakpoints::clearall_in_class_at_safepoint(klassOop klass) {
  bool changed = true;
  // We are going to run thru the list of bkpts
  // and delete some.  This deletion probably alters
  // the list in some implementation defined way such
  // that when we delete entry i, the next entry might
  // no longer be at i+1.  To be safe, each time we delete
  // an entry, we'll just start again from the beginning.
  // We'll stop when we make a pass thru the whole list without
  // deleting anything.
  while (changed) {
    int len = _bps.length();
    changed = false;
    for (int i = 0; i < len; i++) {
      JvmtiBreakpoint& bp = _bps.at(i);
      if (bp.method()->method_holder() == klass) {
        bp.clear();
        _bps.remove(i);
        // This changed 'i' so we have to start over.
        changed = true;
        break;
      }
    }
  }
}

void JvmtiBreakpoints::clearall() {
  VM_ChangeBreakpoints clearall_breakpoint(this,VM_ChangeBreakpoints::CLEAR_ALL_BREAKPOINT);
  VMThread::execute(&clearall_breakpoint);
}

//
// class JvmtiCurrentBreakpoints
//

JvmtiBreakpoints *JvmtiCurrentBreakpoints::_jvmti_breakpoints  = NULL;
address *         JvmtiCurrentBreakpoints::_breakpoint_list    = NULL;


JvmtiBreakpoints& JvmtiCurrentBreakpoints::get_jvmti_breakpoints() {
  if (_jvmti_breakpoints != NULL) return (*_jvmti_breakpoints);
  _jvmti_breakpoints = new JvmtiBreakpoints(listener_fun);
  assert(_jvmti_breakpoints != NULL, "_jvmti_breakpoints != NULL");
  return (*_jvmti_breakpoints);
}

void  JvmtiCurrentBreakpoints::listener_fun(void *this_obj, address *cache) {
  JvmtiBreakpoints *this_jvmti = (JvmtiBreakpoints *) this_obj;
  assert(this_jvmti != NULL, "this_jvmti != NULL");

  debug_only(int n = this_jvmti->length(););
  assert(cache[n] == NULL, "cache must be NULL terminated");

  set_breakpoint_list(cache);
}


void JvmtiCurrentBreakpoints::oops_do(OopClosure* f) {
  if (_jvmti_breakpoints != NULL) {
    _jvmti_breakpoints->oops_do(f);
  }
}

void JvmtiCurrentBreakpoints::gc_epilogue() {
  if (_jvmti_breakpoints != NULL) {
    _jvmti_breakpoints->gc_epilogue();
  }
}


///////////////////////////////////////////////////////////////
//
// class VM_GetOrSetLocal
//

// Constructor for non-object getter
VM_GetOrSetLocal::VM_GetOrSetLocal(JavaThread* thread, jint depth, int index, BasicType type)
  : _thread(thread)
  , _calling_thread(NULL)
  , _depth(depth)
  , _index(index)
  , _type(type)
  , _set(false)
  , _jvf(NULL)
  , _result(JVMTI_ERROR_NONE)
{
}

// Constructor for object or non-object setter
VM_GetOrSetLocal::VM_GetOrSetLocal(JavaThread* thread, jint depth, int index, BasicType type, jvalue value)
  : _thread(thread)
  , _calling_thread(NULL)
  , _depth(depth)
  , _index(index)
  , _type(type)
  , _value(value)
  , _set(true)
  , _jvf(NULL)
  , _result(JVMTI_ERROR_NONE)
{
}

// Constructor for object getter
VM_GetOrSetLocal::VM_GetOrSetLocal(JavaThread* thread, JavaThread* calling_thread, jint depth, int index)
  : _thread(thread)
  , _calling_thread(calling_thread)
  , _depth(depth)
  , _index(index)
  , _type(T_OBJECT)
  , _set(false)
  , _jvf(NULL)
  , _result(JVMTI_ERROR_NONE)
{
}


vframe *VM_GetOrSetLocal::get_vframe() {
  if (!_thread->has_last_Java_frame()) {
    return NULL;
  }
  RegisterMap reg_map(_thread);
  vframe *vf = _thread->last_java_vframe(&reg_map);
  int d = 0;
  while ((vf != NULL) && (d < _depth)) {
    vf = vf->java_sender();
    d++;
  }
  return vf;
}

javaVFrame *VM_GetOrSetLocal::get_java_vframe() {
  vframe* vf = get_vframe();
  if (vf == NULL) {
    _result = JVMTI_ERROR_NO_MORE_FRAMES;
    return NULL;
  }
  javaVFrame *jvf = (javaVFrame*)vf;

  if (!vf->is_java_frame() || jvf->method()->is_native()) {
    _result = JVMTI_ERROR_OPAQUE_FRAME;
    return NULL;
  }
  return jvf;
}

// Check that the klass is assignable to a type with the given signature.
// Another solution could be to use the function Klass::is_subtype_of(type).
// But the type class can be forced to load/initialize eagerly in such a case.
// This may cause unexpected consequences like CFLH or class-init JVMTI events.
// It is better to avoid such a behavior.
bool VM_GetOrSetLocal::is_assignable(const char* ty_sign, Klass* klass, Thread* thread) {
  assert(ty_sign != NULL, "type signature must not be NULL");
  assert(thread != NULL, "thread must not be NULL");
  assert(klass != NULL, "klass must not be NULL");

  int len = (int) strlen(ty_sign);
  if (ty_sign[0] == 'L' && ty_sign[len-1] == ';') { // Need pure class/interface name
    ty_sign++;
    len -= 2;
  }
  symbolHandle ty_sym = oopFactory::new_symbol_handle(ty_sign, len, thread);
  if (klass->name() == ty_sym()) {
    return true;
  }
  // Compare primary supers
  int super_depth = klass->super_depth();
  int idx;
  for (idx = 0; idx < super_depth; idx++) {
    if (Klass::cast(klass->primary_super_of_depth(idx))->name() == ty_sym()) {
      return true;
    }
  }
  // Compare secondary supers
  objArrayOop sec_supers = klass->secondary_supers();
  for (idx = 0; idx < sec_supers->length(); idx++) {
    if (Klass::cast((klassOop) sec_supers->obj_at(idx))->name() == ty_sym()) {
      return true;
    }
  }
  return false;
}

// Checks error conditions:
//   JVMTI_ERROR_INVALID_SLOT
//   JVMTI_ERROR_TYPE_MISMATCH
// Returns: 'true' - everything is Ok, 'false' - error code

bool VM_GetOrSetLocal::check_slot_type(javaVFrame* jvf) {
  methodOop method_oop = jvf->method();
  if (!method_oop->has_localvariable_table()) {
    // Just to check index boundaries
    jint extra_slot = (_type == T_LONG || _type == T_DOUBLE) ? 1 : 0;
    if (_index < 0 || _index + extra_slot >= method_oop->max_locals()) {
      _result = JVMTI_ERROR_INVALID_SLOT;
      return false;
    }
    return true;
  }

  jint num_entries = method_oop->localvariable_table_length();
  if (num_entries == 0) {
    _result = JVMTI_ERROR_INVALID_SLOT;
    return false;       // There are no slots
  }
  int signature_idx = -1;
  int vf_bci = jvf->bci();
  LocalVariableTableElement* table = method_oop->localvariable_table_start();
  for (int i = 0; i < num_entries; i++) {
    int start_bci = table[i].start_bci;
    int end_bci = start_bci + table[i].length;

    // Here we assume that locations of LVT entries
    // with the same slot number cannot be overlapped
    if (_index == (jint) table[i].slot && start_bci <= vf_bci && vf_bci <= end_bci) {
      signature_idx = (int) table[i].descriptor_cp_index;
      break;
    }
  }
  if (signature_idx == -1) {
    _result = JVMTI_ERROR_INVALID_SLOT;
    return false;       // Incorrect slot index
  }
  symbolOop   sign_sym  = method_oop->constants()->symbol_at(signature_idx);
  const char* signature = (const char *) sign_sym->as_utf8();
  BasicType slot_type = char2type(signature[0]);

  switch (slot_type) {
  case T_BYTE:
  case T_SHORT:
  case T_CHAR:
  case T_BOOLEAN:
    slot_type = T_INT;
    break;
  case T_ARRAY:
    slot_type = T_OBJECT;
    break;
  };
  if (_type != slot_type) {
    _result = JVMTI_ERROR_TYPE_MISMATCH;
    return false;
  }

  jobject jobj = _value.l;
  if (_set && slot_type == T_OBJECT && jobj != NULL) { // NULL reference is allowed
    // Check that the jobject class matches the return type signature.
    JavaThread* cur_thread = JavaThread::current();
    HandleMark hm(cur_thread);

    Handle obj = Handle(cur_thread, JNIHandles::resolve_external_guard(jobj));
    NULL_CHECK(obj, (_result = JVMTI_ERROR_INVALID_OBJECT, false));
    KlassHandle ob_kh = KlassHandle(cur_thread, obj->klass());
    NULL_CHECK(ob_kh, (_result = JVMTI_ERROR_INVALID_OBJECT, false));

    if (!is_assignable(signature, Klass::cast(ob_kh()), cur_thread)) {
      _result = JVMTI_ERROR_TYPE_MISMATCH;
      return false;
    }
  }
  return true;
}

static bool can_be_deoptimized(vframe* vf) {
  return (vf->is_compiled_frame() && vf->fr().can_be_deoptimized());
}

bool VM_GetOrSetLocal::doit_prologue() {
  _jvf = get_java_vframe();
  NULL_CHECK(_jvf, false);

  if (!check_slot_type(_jvf)) {
    return false;
  }
  return true;
}

void VM_GetOrSetLocal::doit() {
  if (_set) {
    // Force deoptimization of frame if compiled because it's
    // possible the compiler emitted some locals as constant values,
    // meaning they are not mutable.
    if (can_be_deoptimized(_jvf)) {

      // Schedule deoptimization so that eventually the local
      // update will be written to an interpreter frame.
      Deoptimization::deoptimize_frame(_jvf->thread(), _jvf->fr().id());

      // Now store a new value for the local which will be applied
      // once deoptimization occurs. Note however that while this
      // write is deferred until deoptimization actually happens
      // can vframe created after this point will have its locals
      // reflecting this update so as far as anyone can see the
      // write has already taken place.

      // If we are updating an oop then get the oop from the handle
      // since the handle will be long gone by the time the deopt
      // happens. The oop stored in the deferred local will be
      // gc'd on its own.
      if (_type == T_OBJECT) {
        _value.l = (jobject) (JNIHandles::resolve_external_guard(_value.l));
      }
      // Re-read the vframe so we can see that it is deoptimized
      // [ Only need because of assert in update_local() ]
      _jvf = get_java_vframe();
      ((compiledVFrame*)_jvf)->update_local(_type, _index, _value);
      return;
    }
    StackValueCollection *locals = _jvf->locals();
    HandleMark hm;

    switch (_type) {
    case T_INT:    locals->set_int_at   (_index, _value.i); break;
    case T_LONG:   locals->set_long_at  (_index, _value.j); break;
    case T_FLOAT:  locals->set_float_at (_index, _value.f); break;
    case T_DOUBLE: locals->set_double_at(_index, _value.d); break;
    case T_OBJECT: {
      Handle ob_h(JNIHandles::resolve_external_guard(_value.l));
      locals->set_obj_at (_index, ob_h);
      break;
    }
    default: ShouldNotReachHere();
    }
    _jvf->set_locals(locals);
  } else {
    StackValueCollection *locals = _jvf->locals();

    if (locals->at(_index)->type() == T_CONFLICT) {
      memset(&_value, 0, sizeof(_value));
      _value.l = NULL;
      return;
    }

    switch (_type) {
    case T_INT:    _value.i = locals->int_at   (_index);   break;
    case T_LONG:   _value.j = locals->long_at  (_index);   break;
    case T_FLOAT:  _value.f = locals->float_at (_index);   break;
    case T_DOUBLE: _value.d = locals->double_at(_index);   break;
    case T_OBJECT: {
      // Wrap the oop to be returned in a local JNI handle since
      // oops_do() no longer applies after doit() is finished.
      oop obj = locals->obj_at(_index)();
      _value.l = JNIHandles::make_local(_calling_thread, obj);
      break;
    }
    default: ShouldNotReachHere();
    }
  }
}


bool VM_GetOrSetLocal::allow_nested_vm_operations() const {
  return true; // May need to deoptimize
}


/////////////////////////////////////////////////////////////////////////////////////////

//
// class JvmtiSuspendControl - see comments in jvmtiImpl.hpp
//

bool JvmtiSuspendControl::suspend(JavaThread *java_thread) {
  // external suspend should have caught suspending a thread twice

  // Immediate suspension required for JPDA back-end so JVMTI agent threads do
  // not deadlock due to later suspension on transitions while holding
  // raw monitors.  Passing true causes the immediate suspension.
  // java_suspend() will catch threads in the process of exiting
  // and will ignore them.
  java_thread->java_suspend();

  // It would be nice to have the following assertion in all the time,
  // but it is possible for a racing resume request to have resumed
  // this thread right after we suspended it. Temporarily enable this
  // assertion if you are chasing a different kind of bug.
  //
  // assert(java_lang_Thread::thread(java_thread->threadObj()) == NULL ||
  //   java_thread->is_being_ext_suspended(), "thread is not suspended");

  if (java_lang_Thread::thread(java_thread->threadObj()) == NULL) {
    // check again because we can get delayed in java_suspend():
    // the thread is in process of exiting.
    return false;
  }

  return true;
}

bool JvmtiSuspendControl::resume(JavaThread *java_thread) {
  // external suspend should have caught resuming a thread twice
  assert(java_thread->is_being_ext_suspended(), "thread should be suspended");

  // resume thread
  {
    // must always grab Threads_lock, see JVM_SuspendThread
    MutexLocker ml(Threads_lock);
    java_thread->java_resume();
  }

  return true;
}


void JvmtiSuspendControl::print() {
#ifndef PRODUCT
  MutexLocker mu(Threads_lock);
  ResourceMark rm;

  tty->print("Suspended Threads: [");
  for (JavaThread *thread = Threads::first(); thread != NULL; thread = thread->next()) {
#if JVMTI_TRACE
    const char *name   = JvmtiTrace::safe_get_thread_name(thread);
#else
    const char *name   = "";
#endif /*JVMTI_TRACE */
    tty->print("%s(%c ", name, thread->is_being_ext_suspended() ? 'S' : '_');
    if (!thread->has_last_Java_frame()) {
      tty->print("no stack");
    }
    tty->print(") ");
  }
  tty->print_cr("]");
#endif
}
