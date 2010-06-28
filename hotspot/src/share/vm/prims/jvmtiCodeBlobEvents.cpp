/*
 * Copyright (c) 2003, 2009, Oracle and/or its affiliates. All rights reserved.
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
# include "incls/_jvmtiCodeBlobEvents.cpp.incl"

// Support class to collect a list of the non-nmethod CodeBlobs in
// the CodeCache.
//
// This class actually creates a list of JvmtiCodeBlobDesc - each JvmtiCodeBlobDesc
// describes a single CodeBlob in the CodeCache. Note that collection is
// done to a static list - this is because CodeCache::blobs_do is defined
// as void CodeCache::blobs_do(void f(CodeBlob* nm)) and hence requires
// a C or static method.
//
// Usage :-
//
// CodeBlobCollector collector;
//
// collector.collect();
// JvmtiCodeBlobDesc* blob = collector.first();
// while (blob != NULL) {
//   :
//   blob = collector.next();
// }
//

class CodeBlobCollector : StackObj {
 private:
  GrowableArray<JvmtiCodeBlobDesc*>* _code_blobs;   // collected blobs
  int _pos;                                         // iterator position

  // used during a collection
  static GrowableArray<JvmtiCodeBlobDesc*>* _global_code_blobs;
  static void do_blob(CodeBlob* cb);
 public:
  CodeBlobCollector() {
    _code_blobs = NULL;
    _pos = -1;
  }
  ~CodeBlobCollector() {
    if (_code_blobs != NULL) {
      for (int i=0; i<_code_blobs->length(); i++) {
        FreeHeap(_code_blobs->at(i));
      }
      delete _code_blobs;
    }
  }

  // collect list of code blobs in the cache
  void collect();

  // iteration support - return first code blob
  JvmtiCodeBlobDesc* first() {
    assert(_code_blobs != NULL, "not collected");
    if (_code_blobs->length() == 0) {
      return NULL;
    }
    _pos = 0;
    return _code_blobs->at(0);
  }

  // iteration support - return next code blob
  JvmtiCodeBlobDesc* next() {
    assert(_pos >= 0, "iteration not started");
    if (_pos+1 >= _code_blobs->length()) {
      return NULL;
    }
    return _code_blobs->at(++_pos);
  }

};

// used during collection
GrowableArray<JvmtiCodeBlobDesc*>* CodeBlobCollector::_global_code_blobs;


// called for each CodeBlob in the CodeCache
//
// This function filters out nmethods as it is only interested in
// other CodeBlobs. This function also filters out CodeBlobs that have
// a duplicate starting address as previous blobs. This is needed to
// handle the case where multiple stubs are generated into a single
// BufferBlob.

void CodeBlobCollector::do_blob(CodeBlob* cb) {

  // ignore nmethods
  if (cb->is_nmethod()) {
    return;
  }

  // check if this starting address has been seen already - the
  // assumption is that stubs are inserted into the list before the
  // enclosing BufferBlobs.
  address addr = cb->instructions_begin();
  for (int i=0; i<_global_code_blobs->length(); i++) {
    JvmtiCodeBlobDesc* scb = _global_code_blobs->at(i);
    if (addr == scb->code_begin()) {
      return;
    }
  }

  // we must name the CodeBlob - some CodeBlobs already have names :-
  // - stubs used by compiled code to call a (static) C++ runtime routine
  // - non-relocatable machine code such as the interpreter, stubroutines, etc.
  // - various singleton blobs
  //
  // others are unnamed so we create a name :-
  // - OSR adapter (interpreter frame that has been on-stack replaced)
  // - I2C and C2I adapters
  const char* name = NULL;
  if (cb->is_runtime_stub()) {
    name = ((RuntimeStub*)cb)->name();
  }
  if (cb->is_buffer_blob()) {
    name = ((BufferBlob*)cb)->name();
  }
  if (cb->is_deoptimization_stub() || cb->is_safepoint_stub()) {
    name = ((SingletonBlob*)cb)->name();
  }
  if (cb->is_uncommon_trap_stub() || cb->is_exception_stub()) {
    name = ((SingletonBlob*)cb)->name();
  }

  // record the CodeBlob details as a JvmtiCodeBlobDesc
  JvmtiCodeBlobDesc* scb = new JvmtiCodeBlobDesc(name, cb->instructions_begin(),
                                                 cb->instructions_end());
  _global_code_blobs->append(scb);
}


// collects a list of CodeBlobs in the CodeCache.
//
// The created list is growable array of JvmtiCodeBlobDesc - each one describes
// a CodeBlob. Note that the list is static - this is because CodeBlob::blobs_do
// requires a a C or static function so we can't use an instance function. This
// isn't a problem as the iteration is serial anyway as we need the CodeCache_lock
// to iterate over the code cache.
//
// Note that the CodeBlobs in the CodeCache will include BufferBlobs that may
// contain multiple stubs. As a profiler is interested in the stubs rather than
// the enclosing container we first iterate over the stub code descriptors so
// that the stubs go into the list first. do_blob will then filter out the
// enclosing blobs if the starting address of the enclosing blobs matches the
// starting address of first stub generated in the enclosing blob.

void CodeBlobCollector::collect() {
  assert_locked_or_safepoint(CodeCache_lock);
  assert(_global_code_blobs == NULL, "checking");

  // create the global list
  _global_code_blobs = new (ResourceObj::C_HEAP) GrowableArray<JvmtiCodeBlobDesc*>(50,true);

  // iterate over the stub code descriptors and put them in the list first.
  int index = 0;
  StubCodeDesc* desc;
  while ((desc = StubCodeDesc::desc_for_index(++index)) != NULL) {
    _global_code_blobs->append(new JvmtiCodeBlobDesc(desc->name(), desc->begin(), desc->end()));
  }

  // next iterate over all the non-nmethod code blobs and add them to
  // the list - as noted above this will filter out duplicates and
  // enclosing blobs.
  CodeCache::blobs_do(do_blob);

  // make the global list the instance list so that it can be used
  // for other iterations.
  _code_blobs = _global_code_blobs;
  _global_code_blobs = NULL;
}


// Generate a DYNAMIC_CODE_GENERATED event for each non-nmethod code blob.

jvmtiError JvmtiCodeBlobEvents::generate_dynamic_code_events(JvmtiEnv* env) {
  CodeBlobCollector collector;

  // first collect all the code blobs
  {
    MutexLockerEx mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
    collector.collect();
  }

  // iterate over the collected list and post an event for each blob
  JvmtiCodeBlobDesc* blob = collector.first();
  while (blob != NULL) {
    JvmtiExport::post_dynamic_code_generated(env, blob->name(), blob->code_begin(), blob->code_end());
    blob = collector.next();
  }
  return JVMTI_ERROR_NONE;
}


// Support class to describe a nmethod in the CodeCache

class nmethodDesc: public CHeapObj {
 private:
  jmethodID _jmethod_id;
  address _code_begin;
  address _code_end;
  jvmtiAddrLocationMap* _map;
  jint _map_length;
 public:
  nmethodDesc(jmethodID jmethod_id, address code_begin, address code_end,
              jvmtiAddrLocationMap* map, jint map_length) {
    _jmethod_id = jmethod_id;
    _code_begin = code_begin;
    _code_end = code_end;
    _map = map;
    _map_length = map_length;
  }
  jmethodID jmethod_id() const          { return _jmethod_id; }
  address code_begin() const            { return _code_begin; }
  address code_end() const              { return _code_end; }
  jvmtiAddrLocationMap* map() const     { return _map; }
  jint map_length() const               { return _map_length; }
};


// Support class to collect a list of the nmethod CodeBlobs in
// the CodeCache.
//
// Usage :-
//
// nmethodCollector collector;
//
// collector.collect();
// JvmtiCodeBlobDesc* blob = collector.first();
// while (blob != NULL) {
//   :
//   blob = collector.next();
// }
//
class nmethodCollector : StackObj {
 private:
  GrowableArray<nmethodDesc*>* _nmethods;           // collect nmethods
  int _pos;                                         // iteration support

  // used during a collection
  static GrowableArray<nmethodDesc*>* _global_nmethods;
  static void do_nmethod(nmethod* nm);
 public:
  nmethodCollector() {
    _nmethods = NULL;
    _pos = -1;
  }
  ~nmethodCollector() {
    if (_nmethods != NULL) {
      for (int i=0; i<_nmethods->length(); i++) {
        nmethodDesc* blob = _nmethods->at(i);
        if (blob->map()!= NULL) {
          FREE_C_HEAP_ARRAY(jvmtiAddrLocationMap, blob->map());
        }
      }
      delete _nmethods;
    }
  }

  // collect list of nmethods in the cache
  void collect();

  // iteration support - return first code blob
  nmethodDesc* first() {
    assert(_nmethods != NULL, "not collected");
    if (_nmethods->length() == 0) {
      return NULL;
    }
    _pos = 0;
    return _nmethods->at(0);
  }

  // iteration support - return next code blob
  nmethodDesc* next() {
    assert(_pos >= 0, "iteration not started");
    if (_pos+1 >= _nmethods->length()) {
      return NULL;
    }
    return _nmethods->at(++_pos);
  }
};

// used during collection
GrowableArray<nmethodDesc*>* nmethodCollector::_global_nmethods;


// called for each nmethod in the CodeCache
//
// This function simply adds a descriptor for each nmethod to the global list.

void nmethodCollector::do_nmethod(nmethod* nm) {
  // ignore zombies
  if (!nm->is_alive()) {
    return;
  }

  assert(nm->method() != NULL, "checking");

  // create the location map for the nmethod.
  jvmtiAddrLocationMap* map;
  jint map_length;
  JvmtiCodeBlobEvents::build_jvmti_addr_location_map(nm, &map, &map_length);

  // record the nmethod details
  nmethodDesc* snm = new nmethodDesc(nm->get_and_cache_jmethod_id(),
                                     nm->code_begin(),
                                     nm->code_end(),
                                     map,
                                     map_length);
  _global_nmethods->append(snm);
}

// collects a list of nmethod in the CodeCache.
//
// The created list is growable array of nmethodDesc - each one describes
// a nmethod and includs its JVMTI address location map.

void nmethodCollector::collect() {
  assert_locked_or_safepoint(CodeCache_lock);
  assert(_global_nmethods == NULL, "checking");

  // create the list
  _global_nmethods = new (ResourceObj::C_HEAP) GrowableArray<nmethodDesc*>(100,true);

  // any a descriptor for each nmethod to the list.
  CodeCache::nmethods_do(do_nmethod);

  // make the list the instance list
  _nmethods = _global_nmethods;
  _global_nmethods = NULL;
}

// Generate a COMPILED_METHOD_LOAD event for each nnmethod

jvmtiError JvmtiCodeBlobEvents::generate_compiled_method_load_events(JvmtiEnv* env) {
  HandleMark hm;
  nmethodCollector collector;

  // first collect all nmethods
  {
    MutexLockerEx mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
    collector.collect();
  }

  // iterate over the  list and post an event for each nmethod
  nmethodDesc* nm_desc = collector.first();
  while (nm_desc != NULL) {
    jmethodID mid = nm_desc->jmethod_id();
    assert(mid != NULL, "checking");
    JvmtiExport::post_compiled_method_load(env, mid,
                                           (jint)(nm_desc->code_end() - nm_desc->code_begin()),
                                           nm_desc->code_begin(), nm_desc->map_length(),
                                           nm_desc->map());
    nm_desc = collector.next();
  }
  return JVMTI_ERROR_NONE;
}


// create a C-heap allocated address location map for an nmethod
void JvmtiCodeBlobEvents::build_jvmti_addr_location_map(nmethod *nm,
                                                        jvmtiAddrLocationMap** map_ptr,
                                                        jint *map_length_ptr)
{
  ResourceMark rm;
  jvmtiAddrLocationMap* map = NULL;
  jint map_length = 0;


  // Generate line numbers using PcDesc and ScopeDesc info
  methodHandle mh(nm->method());

  if (!mh->is_native()) {
    PcDesc *pcd;
    int pcds_in_method;

    pcds_in_method = (nm->scopes_pcs_end() - nm->scopes_pcs_begin());
    map = NEW_C_HEAP_ARRAY(jvmtiAddrLocationMap, pcds_in_method);

    address scopes_data = nm->scopes_data_begin();
    for( pcd = nm->scopes_pcs_begin(); pcd < nm->scopes_pcs_end(); ++pcd ) {
      ScopeDesc sc0(nm, pcd->scope_decode_offset(), pcd->should_reexecute(), pcd->return_oop());
      ScopeDesc *sd  = &sc0;
      while( !sd->is_top() ) { sd = sd->sender(); }
      int bci = sd->bci();
      if (bci != InvocationEntryBci) {
        assert(map_length < pcds_in_method, "checking");
        map[map_length].start_address = (const void*)pcd->real_pc(nm);
        map[map_length].location = bci;
        ++map_length;
      }
    }
  }

  *map_ptr = map;
  *map_length_ptr = map_length;
}
