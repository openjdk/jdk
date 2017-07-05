/*
 * Copyright (c) 1997, 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/classLoader.hpp"
#include "code/codeCache.hpp"
#include "code/vtableStubs.hpp"
#include "gc/shared/collectedHeap.inline.hpp"
#include "interpreter/interpreter.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/universe.inline.hpp"
#include "oops/oop.inline.hpp"
#include "oops/symbol.hpp"
#include "runtime/deoptimization.hpp"
#include "runtime/fprofiler.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/stubCodeGenerator.hpp"
#include "runtime/stubRoutines.hpp"
#include "runtime/task.hpp"
#include "runtime/thread.inline.hpp"
#include "runtime/vframe.hpp"
#include "utilities/macros.hpp"

// Static fields of FlatProfiler
int               FlatProfiler::received_gc_ticks   = 0;
int               FlatProfiler::vm_operation_ticks  = 0;
int               FlatProfiler::threads_lock_ticks  = 0;
int               FlatProfiler::class_loader_ticks  = 0;
int               FlatProfiler::extra_ticks         = 0;
int               FlatProfiler::blocked_ticks       = 0;
int               FlatProfiler::deopt_ticks         = 0;
int               FlatProfiler::unknown_ticks       = 0;
int               FlatProfiler::interpreter_ticks   = 0;
int               FlatProfiler::compiler_ticks      = 0;
int               FlatProfiler::received_ticks      = 0;
int               FlatProfiler::delivered_ticks     = 0;
int*              FlatProfiler::bytecode_ticks      = NULL;
int*              FlatProfiler::bytecode_ticks_stub = NULL;
int               FlatProfiler::all_int_ticks       = 0;
int               FlatProfiler::all_comp_ticks      = 0;
int               FlatProfiler::all_ticks           = 0;
bool              FlatProfiler::full_profile_flag   = false;
ThreadProfiler*   FlatProfiler::thread_profiler     = NULL;
ThreadProfiler*   FlatProfiler::vm_thread_profiler  = NULL;
FlatProfilerTask* FlatProfiler::task                = NULL;
elapsedTimer      FlatProfiler::timer;
int               FlatProfiler::interval_ticks_previous = 0;
IntervalData*     FlatProfiler::interval_data       = NULL;

ThreadProfiler::ThreadProfiler() {
  // Space for the ProfilerNodes
  const int area_size = 1 * ProfilerNodeSize * 1024;
  area_bottom = AllocateHeap(area_size, mtInternal);
  area_top    = area_bottom;
  area_limit  = area_bottom + area_size;

  // ProfilerNode pointer table
  table = NEW_C_HEAP_ARRAY(ProfilerNode*, table_size, mtInternal);
  initialize();
  engaged = false;
}

ThreadProfiler::~ThreadProfiler() {
  FreeHeap(area_bottom);
  area_bottom = NULL;
  area_top = NULL;
  area_limit = NULL;
  FreeHeap(table);
  table = NULL;
}

// Statics for ThreadProfiler
int ThreadProfiler::table_size = 1024;

int ThreadProfiler::entry(int  value) {
  value = (value > 0) ? value : -value;
  return value % table_size;
}

ThreadProfilerMark::ThreadProfilerMark(ThreadProfilerMark::Region r) {
  _r = r;
  _pp = NULL;
  assert(((r > ThreadProfilerMark::noRegion) && (r < ThreadProfilerMark::maxRegion)), "ThreadProfilerMark::Region out of bounds");
  Thread* tp = Thread::current();
  if (tp != NULL && tp->is_Java_thread()) {
    JavaThread* jtp = (JavaThread*) tp;
    ThreadProfiler* pp = jtp->get_thread_profiler();
    _pp = pp;
    if (pp != NULL) {
      pp->region_flag[r] = true;
    }
  }
}

ThreadProfilerMark::~ThreadProfilerMark() {
  if (_pp != NULL) {
    _pp->region_flag[_r] = false;
  }
  _pp = NULL;
}

// Random other statics
static const int col1 = 2;      // position of output column 1
static const int col2 = 11;     // position of output column 2
static const int col3 = 25;     // position of output column 3
static const int col4 = 55;     // position of output column 4


// Used for detailed profiling of nmethods.
class PCRecorder : AllStatic {
 private:
  static int*    counters;
  static address base;
  enum {
   bucket_size = 16
  };
  static int     index_for(address pc) { return (pc - base)/bucket_size;   }
  static address pc_for(int index)     { return base + (index * bucket_size); }
  static int     size() {
    return ((int)CodeCache::max_capacity())/bucket_size * BytesPerWord;
  }
 public:
  static address bucket_start_for(address pc) {
    if (counters == NULL) return NULL;
    return pc_for(index_for(pc));
  }
  static int bucket_count_for(address pc)  { return counters[index_for(pc)]; }
  static void init();
  static void record(address pc);
  static void print();
  static void print_blobs(CodeBlob* cb);
};

int*    PCRecorder::counters = NULL;
address PCRecorder::base     = NULL;

void PCRecorder::init() {
  MutexLockerEx lm(CodeCache_lock, Mutex::_no_safepoint_check_flag);
  int s = size();
  counters = NEW_C_HEAP_ARRAY(int, s, mtInternal);
  for (int index = 0; index < s; index++) {
    counters[index] = 0;
  }
  base = CodeCache::low_bound();
}

void PCRecorder::record(address pc) {
  if (counters == NULL) return;
  assert(CodeCache::contains(pc), "must be in CodeCache");
  counters[index_for(pc)]++;
}


address FlatProfiler::bucket_start_for(address pc) {
  return PCRecorder::bucket_start_for(pc);
}

int FlatProfiler::bucket_count_for(address pc) {
  return PCRecorder::bucket_count_for(pc);
}

void PCRecorder::print() {
  if (counters == NULL) return;

  tty->cr();
  tty->print_cr("Printing compiled methods with PC buckets having more than " INTX_FORMAT " ticks", ProfilerPCTickThreshold);
  tty->print_cr("===================================================================");
  tty->cr();

  GrowableArray<CodeBlob*>* candidates = new GrowableArray<CodeBlob*>(20);


  int s;
  {
    MutexLockerEx lm(CodeCache_lock, Mutex::_no_safepoint_check_flag);
    s = size();
  }

  for (int index = 0; index < s; index++) {
    int count = counters[index];
    if (count > ProfilerPCTickThreshold) {
      address pc = pc_for(index);
      CodeBlob* cb = CodeCache::find_blob_unsafe(pc);
      if (cb != NULL && candidates->find(cb) < 0) {
        candidates->push(cb);
      }
    }
  }
  for (int i = 0; i < candidates->length(); i++) {
    print_blobs(candidates->at(i));
  }
}

void PCRecorder::print_blobs(CodeBlob* cb) {
  if (cb != NULL) {
    cb->print();
    if (cb->is_nmethod()) {
      ((nmethod*)cb)->print_code();
    }
    tty->cr();
  } else {
    tty->print_cr("stub code");
  }
}

class tick_counter {            // holds tick info for one node
 public:
  int ticks_in_code;
  int ticks_in_native;

  tick_counter()                     {  ticks_in_code = ticks_in_native = 0; }
  tick_counter(int code, int native) {  ticks_in_code = code; ticks_in_native = native; }

  int total() const {
    return (ticks_in_code + ticks_in_native);
  }

  void add(tick_counter* a) {
    ticks_in_code += a->ticks_in_code;
    ticks_in_native += a->ticks_in_native;
  }

  void update(TickPosition where) {
    switch(where) {
      case tp_code:     ticks_in_code++;       break;
      case tp_native:   ticks_in_native++;      break;
    }
  }

  void print_code(outputStream* st, int total_ticks) {
    st->print("%5.1f%% %5d ", total() * 100.0 / total_ticks, ticks_in_code);
  }

  void print_native(outputStream* st) {
    st->print(" + %5d ", ticks_in_native);
  }
};

class ProfilerNode {
 private:
  ProfilerNode* _next;
 public:
  tick_counter ticks;

 public:

  void* operator new(size_t size, ThreadProfiler* tp) throw();
  void  operator delete(void* p);

  ProfilerNode() {
    _next = NULL;
  }

  virtual ~ProfilerNode() {
    if (_next)
      delete _next;
  }

  void set_next(ProfilerNode* n) { _next = n; }
  ProfilerNode* next()           { return _next; }

  void update(TickPosition where) { ticks.update(where);}
  int total_ticks() { return ticks.total(); }

  virtual bool is_interpreted() const { return false; }
  virtual bool is_compiled()    const { return false; }
  virtual bool is_stub()        const { return false; }
  virtual bool is_runtime_stub() const{ return false; }
  virtual void oops_do(OopClosure* f) = 0;

  virtual bool interpreted_match(Method* m) const { return false; }
  virtual bool compiled_match(Method* m ) const { return false; }
  virtual bool stub_match(Method* m, const char* name) const { return false; }
  virtual bool adapter_match() const { return false; }
  virtual bool runtimeStub_match(const CodeBlob* stub, const char* name) const { return false; }
  virtual bool unknown_compiled_match(const CodeBlob* cb) const { return false; }

  static void print_title(outputStream* st) {
    st->print(" + native");
    st->fill_to(col3);
    st->print("Method");
    st->fill_to(col4);
    st->cr();
  }

  static void print_total(outputStream* st, tick_counter* t, int total, const char* msg) {
    t->print_code(st, total);
    st->fill_to(col2);
    t->print_native(st);
    st->fill_to(col3);
    st->print("%s", msg);
    st->cr();
  }

  virtual Method* method()         = 0;

  virtual void print_method_on(outputStream* st) {
    int limit;
    int i;
    Method* m = method();
    Symbol* k = m->klass_name();
    // Print the class name with dots instead of slashes
    limit = k->utf8_length();
    for (i = 0 ; i < limit ; i += 1) {
      char c = (char) k->byte_at(i);
      if (c == '/') {
        c = '.';
      }
      st->print("%c", c);
    }
    if (limit > 0) {
      st->print(".");
    }
    Symbol* n = m->name();
    limit = n->utf8_length();
    for (i = 0 ; i < limit ; i += 1) {
      char c = (char) n->byte_at(i);
      st->print("%c", c);
    }
    if (Verbose || WizardMode) {
      // Disambiguate overloaded methods
      Symbol* sig = m->signature();
      sig->print_symbol_on(st);
    } else if (MethodHandles::is_signature_polymorphic(m->intrinsic_id()))
      // compare with Method::print_short_name
      MethodHandles::print_as_basic_type_signature_on(st, m->signature(), true);
  }

  virtual void print(outputStream* st, int total_ticks) {
    ticks.print_code(st, total_ticks);
    st->fill_to(col2);
    ticks.print_native(st);
    st->fill_to(col3);
    print_method_on(st);
    st->cr();
  }

  // for hashing into the table
  static int hash(Method* method) {
      // The point here is to try to make something fairly unique
      // out of the fields we can read without grabbing any locks
      // since the method may be locked when we need the hash.
      return (
          method->code_size() ^
          method->max_stack() ^
          method->max_locals() ^
          method->size_of_parameters());
  }

  // for sorting
  static int compare(ProfilerNode** a, ProfilerNode** b) {
    return (*b)->total_ticks() - (*a)->total_ticks();
  }
};

void* ProfilerNode::operator new(size_t size, ThreadProfiler* tp) throw() {
  void* result = (void*) tp->area_top;
  tp->area_top += size;

  if (tp->area_top > tp->area_limit) {
    fatal("flat profiler buffer overflow");
  }
  return result;
}

void ProfilerNode::operator delete(void* p){
}

class interpretedNode : public ProfilerNode {
 private:
   Method* _method;
   oop       _class_loader;  // needed to keep metadata for the method alive
 public:
   interpretedNode(Method* method, TickPosition where) : ProfilerNode() {
     _method = method;
     _class_loader = method->method_holder()->class_loader();
     update(where);
   }

   bool is_interpreted() const { return true; }

   bool interpreted_match(Method* m) const {
      return _method == m;
   }

   void oops_do(OopClosure* f) {
     f->do_oop(&_class_loader);
   }

   Method* method() { return _method; }

   static void print_title(outputStream* st) {
     st->fill_to(col1);
     st->print("%11s", "Interpreted");
     ProfilerNode::print_title(st);
   }

   void print(outputStream* st, int total_ticks) {
     ProfilerNode::print(st, total_ticks);
   }

   void print_method_on(outputStream* st) {
     ProfilerNode::print_method_on(st);
     MethodCounters* mcs = method()->method_counters();
     if (Verbose && mcs != NULL) mcs->invocation_counter()->print_short();
   }
};

class compiledNode : public ProfilerNode {
 private:
   Method* _method;
   oop       _class_loader;  // needed to keep metadata for the method alive
 public:
   compiledNode(Method* method, TickPosition where) : ProfilerNode() {
     _method = method;
     _class_loader = method->method_holder()->class_loader();
     update(where);
  }
  bool is_compiled()    const { return true; }

  bool compiled_match(Method* m) const {
    return _method == m;
  }

  Method* method()         { return _method; }

  void oops_do(OopClosure* f) {
    f->do_oop(&_class_loader);
  }

  static void print_title(outputStream* st) {
    st->fill_to(col1);
    st->print("%11s", "Compiled");
    ProfilerNode::print_title(st);
  }

  void print(outputStream* st, int total_ticks) {
    ProfilerNode::print(st, total_ticks);
  }

  void print_method_on(outputStream* st) {
    ProfilerNode::print_method_on(st);
  }
};

class stubNode : public ProfilerNode {
 private:
  Method* _method;
  oop       _class_loader;  // needed to keep metadata for the method alive
  const char* _symbol;   // The name of the nearest VM symbol (for +ProfileVM). Points to a unique string
 public:
   stubNode(Method* method, const char* name, TickPosition where) : ProfilerNode() {
     _method = method;
     _class_loader = method->method_holder()->class_loader();
     _symbol = name;
     update(where);
   }

   bool is_stub() const { return true; }

   void oops_do(OopClosure* f) {
     f->do_oop(&_class_loader);
   }

   bool stub_match(Method* m, const char* name) const {
     return (_method == m) && (_symbol == name);
   }

   Method* method() { return _method; }

   static void print_title(outputStream* st) {
     st->fill_to(col1);
     st->print("%11s", "Stub");
     ProfilerNode::print_title(st);
   }

   void print(outputStream* st, int total_ticks) {
     ProfilerNode::print(st, total_ticks);
   }

   void print_method_on(outputStream* st) {
     ProfilerNode::print_method_on(st);
     print_symbol_on(st);
   }

  void print_symbol_on(outputStream* st) {
    if(_symbol) {
      st->print("  (%s)", _symbol);
    }
  }
};

class adapterNode : public ProfilerNode {
 public:
   adapterNode(TickPosition where) : ProfilerNode() {
     update(where);
  }
  bool is_compiled()    const { return true; }

  bool adapter_match() const { return true; }

  Method* method()         { return NULL; }

  void oops_do(OopClosure* f) {
    ;
  }

  void print(outputStream* st, int total_ticks) {
    ProfilerNode::print(st, total_ticks);
  }

  void print_method_on(outputStream* st) {
    st->print("%s", "adapters");
  }
};

class runtimeStubNode : public ProfilerNode {
 private:
   const CodeBlob* _stub;
  const char* _symbol;     // The name of the nearest VM symbol when ProfileVM is on. Points to a unique string.
 public:
   runtimeStubNode(const CodeBlob* stub, const char* name, TickPosition where) : ProfilerNode(), _stub(stub),  _symbol(name) {
     assert(stub->is_runtime_stub(), "wrong code blob");
     update(where);
   }

  bool is_runtime_stub() const { return true; }

  bool runtimeStub_match(const CodeBlob* stub, const char* name) const {
    assert(stub->is_runtime_stub(), "wrong code blob");
    return ((RuntimeStub*)_stub)->entry_point() == ((RuntimeStub*)stub)->entry_point() &&
            (_symbol == name);
  }

  Method* method() { return NULL; }

  static void print_title(outputStream* st) {
    st->fill_to(col1);
    st->print("%11s", "Runtime stub");
    ProfilerNode::print_title(st);
  }

  void oops_do(OopClosure* f) {
    ;
  }

  void print(outputStream* st, int total_ticks) {
    ProfilerNode::print(st, total_ticks);
  }

  void print_method_on(outputStream* st) {
    st->print("%s", ((RuntimeStub*)_stub)->name());
    print_symbol_on(st);
  }

  void print_symbol_on(outputStream* st) {
    if(_symbol) {
      st->print("  (%s)", _symbol);
    }
  }
};


class unknown_compiledNode : public ProfilerNode {
 const char *_name;
 public:
   unknown_compiledNode(const CodeBlob* cb, TickPosition where) : ProfilerNode() {
     if ( cb->is_buffer_blob() )
       _name = ((BufferBlob*)cb)->name();
     else
       _name = ((SingletonBlob*)cb)->name();
     update(where);
  }
  bool is_compiled()    const { return true; }

  bool unknown_compiled_match(const CodeBlob* cb) const {
     if ( cb->is_buffer_blob() )
       return !strcmp(((BufferBlob*)cb)->name(), _name);
     else
       return !strcmp(((SingletonBlob*)cb)->name(), _name);
  }

  Method* method()         { return NULL; }

  void oops_do(OopClosure* f) {
    ;
  }

  void print(outputStream* st, int total_ticks) {
    ProfilerNode::print(st, total_ticks);
  }

  void print_method_on(outputStream* st) {
    st->print("%s", _name);
  }
};

class vmNode : public ProfilerNode {
 private:
  const char* _name; // "optional" name obtained by os means such as dll lookup
 public:
  vmNode(const TickPosition where) : ProfilerNode() {
    _name = NULL;
    update(where);
  }

  vmNode(const char* name, const TickPosition where) : ProfilerNode() {
    _name = os::strdup(name);
    update(where);
  }

  ~vmNode() {
    if (_name != NULL) {
      os::free((void*)_name);
    }
  }

  const char *name()    const { return _name; }
  bool is_compiled()    const { return true; }

  bool vm_match(const char* name) const { return strcmp(name, _name) == 0; }

  Method* method()          { return NULL; }

  static int hash(const char* name){
    // Compute a simple hash
    const char* cp = name;
    int h = 0;

    if(name != NULL){
      while(*cp != '\0'){
        h = (h << 1) ^ *cp;
        cp++;
      }
    }
    return h;
  }

  void oops_do(OopClosure* f) {
    ;
  }

  void print(outputStream* st, int total_ticks) {
    ProfilerNode::print(st, total_ticks);
  }

  void print_method_on(outputStream* st) {
    if(_name==NULL){
      st->print("%s", "unknown code");
    }
    else {
      st->print("%s", _name);
    }
  }
};

void ThreadProfiler::interpreted_update(Method* method, TickPosition where) {
  int index = entry(ProfilerNode::hash(method));
  if (!table[index]) {
    table[index] = new (this) interpretedNode(method, where);
  } else {
    ProfilerNode* prev = table[index];
    for(ProfilerNode* node = prev; node; node = node->next()) {
      if (node->interpreted_match(method)) {
        node->update(where);
        return;
      }
      prev = node;
    }
    prev->set_next(new (this) interpretedNode(method, where));
  }
}

void ThreadProfiler::compiled_update(Method* method, TickPosition where) {
  int index = entry(ProfilerNode::hash(method));
  if (!table[index]) {
    table[index] = new (this) compiledNode(method, where);
  } else {
    ProfilerNode* prev = table[index];
    for(ProfilerNode* node = prev; node; node = node->next()) {
      if (node->compiled_match(method)) {
        node->update(where);
        return;
      }
      prev = node;
    }
    prev->set_next(new (this) compiledNode(method, where));
  }
}

void ThreadProfiler::stub_update(Method* method, const char* name, TickPosition where) {
  int index = entry(ProfilerNode::hash(method));
  if (!table[index]) {
    table[index] = new (this) stubNode(method, name, where);
  } else {
    ProfilerNode* prev = table[index];
    for(ProfilerNode* node = prev; node; node = node->next()) {
      if (node->stub_match(method, name)) {
        node->update(where);
        return;
      }
      prev = node;
    }
    prev->set_next(new (this) stubNode(method, name, where));
  }
}

void ThreadProfiler::adapter_update(TickPosition where) {
  int index = 0;
  if (!table[index]) {
    table[index] = new (this) adapterNode(where);
  } else {
    ProfilerNode* prev = table[index];
    for(ProfilerNode* node = prev; node; node = node->next()) {
      if (node->adapter_match()) {
        node->update(where);
        return;
      }
      prev = node;
    }
    prev->set_next(new (this) adapterNode(where));
  }
}

void ThreadProfiler::runtime_stub_update(const CodeBlob* stub, const char* name, TickPosition where) {
  int index = 0;
  if (!table[index]) {
    table[index] = new (this) runtimeStubNode(stub, name, where);
  } else {
    ProfilerNode* prev = table[index];
    for(ProfilerNode* node = prev; node; node = node->next()) {
      if (node->runtimeStub_match(stub, name)) {
        node->update(where);
        return;
      }
      prev = node;
    }
    prev->set_next(new (this) runtimeStubNode(stub, name, where));
  }
}


void ThreadProfiler::unknown_compiled_update(const CodeBlob* cb, TickPosition where) {
  int index = 0;
  if (!table[index]) {
    table[index] = new (this) unknown_compiledNode(cb, where);
  } else {
    ProfilerNode* prev = table[index];
    for(ProfilerNode* node = prev; node; node = node->next()) {
      if (node->unknown_compiled_match(cb)) {
        node->update(where);
        return;
      }
      prev = node;
    }
    prev->set_next(new (this) unknown_compiledNode(cb, where));
  }
}

void ThreadProfiler::vm_update(TickPosition where) {
  vm_update(NULL, where);
}

void ThreadProfiler::vm_update(const char* name, TickPosition where) {
  int index = entry(vmNode::hash(name));
  assert(index >= 0, "Must be positive");
  // Note that we call strdup below since the symbol may be resource allocated
  if (!table[index]) {
    table[index] = new (this) vmNode(name, where);
  } else {
    ProfilerNode* prev = table[index];
    for(ProfilerNode* node = prev; node; node = node->next()) {
      if (((vmNode *)node)->vm_match(name)) {
        node->update(where);
        return;
      }
      prev = node;
    }
    prev->set_next(new (this) vmNode(name, where));
  }
}


class FlatProfilerTask : public PeriodicTask {
public:
  FlatProfilerTask(int interval_time) : PeriodicTask(interval_time) {}
  void task();
};

void FlatProfiler::record_vm_operation() {
  if (Universe::heap()->is_gc_active()) {
    FlatProfiler::received_gc_ticks += 1;
    return;
  }

  if (DeoptimizationMarker::is_active()) {
    FlatProfiler::deopt_ticks += 1;
    return;
  }

  FlatProfiler::vm_operation_ticks += 1;
}

void FlatProfiler::record_vm_tick() {
  // Profile the VM Thread itself if needed
  // This is done without getting the Threads_lock and we can go deep
  // inside Safepoint, etc.
  if( ProfileVM  ) {
    ResourceMark rm;
    ExtendedPC epc;
    const char *name = NULL;
    char buf[256];
    buf[0] = '\0';

    vm_thread_profiler->inc_thread_ticks();

    // Get a snapshot of a current VMThread pc (and leave it running!)
    // The call may fail if, for instance the VM thread is interrupted while
    // holding the Interrupt_lock or for other reasons.
    epc = os::get_thread_pc(VMThread::vm_thread());
    if(epc.pc() != NULL) {
      if (os::dll_address_to_function_name(epc.pc(), buf, sizeof(buf), NULL)) {
         name = buf;
      }
    }
    if (name != NULL) {
      vm_thread_profiler->vm_update(name, tp_native);
    }
  }
}

void FlatProfiler::record_thread_ticks() {

  int maxthreads, suspendedthreadcount;
  JavaThread** threadsList;
  bool interval_expired = false;

  if (ProfileIntervals &&
      (FlatProfiler::received_ticks >= interval_ticks_previous + ProfileIntervalsTicks)) {
    interval_expired = true;
    interval_ticks_previous = FlatProfiler::received_ticks;
  }

  // Try not to wait for the Threads_lock
  if (Threads_lock->try_lock()) {
    {  // Threads_lock scope
      maxthreads = Threads::number_of_threads();
      threadsList = NEW_C_HEAP_ARRAY(JavaThread *, maxthreads, mtInternal);
      suspendedthreadcount = 0;
      for (JavaThread* tp = Threads::first(); tp != NULL; tp = tp->next()) {
        if (tp->is_Compiler_thread()) {
          // Only record ticks for active compiler threads
          CompilerThread* cthread = (CompilerThread*)tp;
          if (cthread->task() != NULL) {
            // The compiler is active.  If we need to access any of the fields
            // of the compiler task we should suspend the CompilerThread first.
            FlatProfiler::compiler_ticks += 1;
            continue;
          }
        }

        // First externally suspend all threads by marking each for
        // external suspension - so it will stop at its next transition
        // Then do a safepoint
        ThreadProfiler* pp = tp->get_thread_profiler();
        if (pp != NULL && pp->engaged) {
          MutexLockerEx ml(tp->SR_lock(), Mutex::_no_safepoint_check_flag);
          if (!tp->is_external_suspend() && !tp->is_exiting()) {
            tp->set_external_suspend();
            threadsList[suspendedthreadcount++] = tp;
          }
        }
      }
      Threads_lock->unlock();
    }
    // Suspend each thread. This call should just return
    // for any threads that have already self-suspended
    // Net result should be one safepoint
    for (int j = 0; j < suspendedthreadcount; j++) {
      JavaThread *tp = threadsList[j];
      if (tp) {
        tp->java_suspend();
      }
    }

    // We are responsible for resuming any thread on this list
    for (int i = 0; i < suspendedthreadcount; i++) {
      JavaThread *tp = threadsList[i];
      if (tp) {
        ThreadProfiler* pp = tp->get_thread_profiler();
        if (pp != NULL && pp->engaged) {
          HandleMark hm;
          FlatProfiler::delivered_ticks += 1;
          if (interval_expired) {
          FlatProfiler::interval_record_thread(pp);
          }
          // This is the place where we check to see if a user thread is
          // blocked waiting for compilation.
          if (tp->blocked_on_compilation()) {
            pp->compiler_ticks += 1;
            pp->interval_data_ref()->inc_compiling();
          } else {
            pp->record_tick(tp);
          }
        }
        MutexLocker ml(Threads_lock);
        tp->java_resume();
      }
    }
    if (interval_expired) {
      FlatProfiler::interval_print();
      FlatProfiler::interval_reset();
    }

    FREE_C_HEAP_ARRAY(JavaThread *, threadsList);
  } else {
    // Couldn't get the threads lock, just record that rather than blocking
    FlatProfiler::threads_lock_ticks += 1;
  }

}

void FlatProfilerTask::task() {
  FlatProfiler::received_ticks += 1;

  if (ProfileVM) {
    FlatProfiler::record_vm_tick();
  }

  VM_Operation* op = VMThread::vm_operation();
  if (op != NULL) {
    FlatProfiler::record_vm_operation();
    if (SafepointSynchronize::is_at_safepoint()) {
      return;
    }
  }
  FlatProfiler::record_thread_ticks();
}

void ThreadProfiler::record_interpreted_tick(JavaThread* thread, frame fr, TickPosition where, int* ticks) {
  FlatProfiler::all_int_ticks++;
  if (!FlatProfiler::full_profile()) {
    return;
  }

  if (!fr.is_interpreted_frame_valid(thread)) {
    // tick came at a bad time
    interpreter_ticks += 1;
    FlatProfiler::interpreter_ticks += 1;
    return;
  }

  // The frame has been fully validated so we can trust the method and bci

  Method* method = *fr.interpreter_frame_method_addr();

  interpreted_update(method, where);

  // update byte code table
  InterpreterCodelet* desc = Interpreter::codelet_containing(fr.pc());
  if (desc != NULL && desc->bytecode() >= 0) {
    ticks[desc->bytecode()]++;
  }
}

void ThreadProfiler::record_compiled_tick(JavaThread* thread, frame fr, TickPosition where) {
  const char *name = NULL;
  TickPosition localwhere = where;

  FlatProfiler::all_comp_ticks++;
  if (!FlatProfiler::full_profile()) return;

  CodeBlob* cb = fr.cb();

// For runtime stubs, record as native rather than as compiled
   if (cb->is_runtime_stub()) {
        RegisterMap map(thread, false);
        fr = fr.sender(&map);
        cb = fr.cb();
        localwhere = tp_native;
  }
  Method* method = (cb->is_nmethod()) ? ((nmethod *)cb)->method() :
                                          (Method*)NULL;

  if (method == NULL) {
    if (cb->is_runtime_stub())
      runtime_stub_update(cb, name, localwhere);
    else
      unknown_compiled_update(cb, localwhere);
  }
  else {
    if (method->is_native()) {
      stub_update(method, name, localwhere);
    } else {
      compiled_update(method, localwhere);
    }
  }
}

extern "C" void find(int x);


void ThreadProfiler::record_tick_for_running_frame(JavaThread* thread, frame fr) {
  // The tick happened in real code -> non VM code
  if (fr.is_interpreted_frame()) {
    interval_data_ref()->inc_interpreted();
    record_interpreted_tick(thread, fr, tp_code, FlatProfiler::bytecode_ticks);
    return;
  }

  if (CodeCache::contains(fr.pc())) {
    interval_data_ref()->inc_compiled();
    PCRecorder::record(fr.pc());
    record_compiled_tick(thread, fr, tp_code);
    return;
  }

  if (VtableStubs::stub_containing(fr.pc()) != NULL) {
    unknown_ticks_array[ut_vtable_stubs] += 1;
    return;
  }

  frame caller = fr.profile_find_Java_sender_frame(thread);

  if (caller.sp() != NULL && caller.pc() != NULL) {
    record_tick_for_calling_frame(thread, caller);
    return;
  }

  unknown_ticks_array[ut_running_frame] += 1;
  FlatProfiler::unknown_ticks += 1;
}

void ThreadProfiler::record_tick_for_calling_frame(JavaThread* thread, frame fr) {
  // The tick happened in VM code
  interval_data_ref()->inc_native();
  if (fr.is_interpreted_frame()) {
    record_interpreted_tick(thread, fr, tp_native, FlatProfiler::bytecode_ticks_stub);
    return;
  }
  if (CodeCache::contains(fr.pc())) {
    record_compiled_tick(thread, fr, tp_native);
    return;
  }

  frame caller = fr.profile_find_Java_sender_frame(thread);

  if (caller.sp() != NULL && caller.pc() != NULL) {
    record_tick_for_calling_frame(thread, caller);
    return;
  }

  unknown_ticks_array[ut_calling_frame] += 1;
  FlatProfiler::unknown_ticks += 1;
}

void ThreadProfiler::record_tick(JavaThread* thread) {
  FlatProfiler::all_ticks++;
  thread_ticks += 1;

  // Here's another way to track global state changes.
  // When the class loader starts it marks the ThreadProfiler to tell it it is in the class loader
  // and we check that here.
  // This is more direct, and more than one thread can be in the class loader at a time,
  // but it does mean the class loader has to know about the profiler.
  if (region_flag[ThreadProfilerMark::classLoaderRegion]) {
    class_loader_ticks += 1;
    FlatProfiler::class_loader_ticks += 1;
    return;
  } else if (region_flag[ThreadProfilerMark::extraRegion]) {
    extra_ticks += 1;
    FlatProfiler::extra_ticks += 1;
    return;
  }
  // Note that the WatcherThread can now stop for safepoints
  uint32_t debug_bits = 0;
  if (!thread->wait_for_ext_suspend_completion(SuspendRetryCount,
      SuspendRetryDelay, &debug_bits)) {
    unknown_ticks_array[ut_unknown_thread_state] += 1;
    FlatProfiler::unknown_ticks += 1;
    return;
  }

  frame fr;

  switch (thread->thread_state()) {
  case _thread_in_native:
  case _thread_in_native_trans:
  case _thread_in_vm:
  case _thread_in_vm_trans:
    if (thread->profile_last_Java_frame(&fr)) {
      if (fr.is_runtime_frame()) {
        RegisterMap map(thread, false);
        fr = fr.sender(&map);
      }
      record_tick_for_calling_frame(thread, fr);
    } else {
      unknown_ticks_array[ut_no_last_Java_frame] += 1;
      FlatProfiler::unknown_ticks += 1;
    }
    break;
  // handle_special_runtime_exit_condition self-suspends threads in Java
  case _thread_in_Java:
  case _thread_in_Java_trans:
    if (thread->profile_last_Java_frame(&fr)) {
      if (fr.is_safepoint_blob_frame()) {
        RegisterMap map(thread, false);
        fr = fr.sender(&map);
      }
      record_tick_for_running_frame(thread, fr);
    } else {
      unknown_ticks_array[ut_no_last_Java_frame] += 1;
      FlatProfiler::unknown_ticks += 1;
    }
    break;
  case _thread_blocked:
  case _thread_blocked_trans:
    if (thread->osthread() && thread->osthread()->get_state() == RUNNABLE) {
        if (thread->profile_last_Java_frame(&fr)) {
          if (fr.is_safepoint_blob_frame()) {
            RegisterMap map(thread, false);
            fr = fr.sender(&map);
            record_tick_for_running_frame(thread, fr);
          } else {
            record_tick_for_calling_frame(thread, fr);
          }
        } else {
          unknown_ticks_array[ut_no_last_Java_frame] += 1;
          FlatProfiler::unknown_ticks += 1;
        }
    } else {
          blocked_ticks += 1;
          FlatProfiler::blocked_ticks += 1;
    }
    break;
  case _thread_uninitialized:
  case _thread_new:
  // not used, included for completeness
  case _thread_new_trans:
     unknown_ticks_array[ut_no_last_Java_frame] += 1;
     FlatProfiler::unknown_ticks += 1;
     break;
  default:
    unknown_ticks_array[ut_unknown_thread_state] += 1;
    FlatProfiler::unknown_ticks += 1;
    break;
  }
  return;
}

void ThreadProfiler::engage() {
  engaged = true;
  timer.start();
}

void ThreadProfiler::disengage() {
  engaged = false;
  timer.stop();
}

void ThreadProfiler::initialize() {
  for (int index = 0; index < table_size; index++) {
    table[index] = NULL;
  }
  thread_ticks = 0;
  blocked_ticks = 0;
  compiler_ticks = 0;
  interpreter_ticks = 0;
  for (int ut = 0; ut < ut_end; ut += 1) {
    unknown_ticks_array[ut] = 0;
  }
  region_flag[ThreadProfilerMark::classLoaderRegion] = false;
  class_loader_ticks = 0;
  region_flag[ThreadProfilerMark::extraRegion] = false;
  extra_ticks = 0;
  timer.start();
  interval_data_ref()->reset();
}

void ThreadProfiler::reset() {
  timer.stop();
  if (table != NULL) {
    for (int index = 0; index < table_size; index++) {
      ProfilerNode* n = table[index];
      if (n != NULL) {
        delete n;
      }
    }
  }
  initialize();
}

void FlatProfiler::allocate_table() {
  { // Bytecode table
    bytecode_ticks      = NEW_C_HEAP_ARRAY(int, Bytecodes::number_of_codes, mtInternal);
    bytecode_ticks_stub = NEW_C_HEAP_ARRAY(int, Bytecodes::number_of_codes, mtInternal);
    for(int index = 0; index < Bytecodes::number_of_codes; index++) {
      bytecode_ticks[index]      = 0;
      bytecode_ticks_stub[index] = 0;
    }
  }

  if (ProfilerRecordPC) PCRecorder::init();

  interval_data         = NEW_C_HEAP_ARRAY(IntervalData, interval_print_size, mtInternal);
  FlatProfiler::interval_reset();
}

void FlatProfiler::engage(JavaThread* mainThread, bool fullProfile) {
  full_profile_flag = fullProfile;
  if (bytecode_ticks == NULL) {
    allocate_table();
  }
  if(ProfileVM && (vm_thread_profiler == NULL)){
    vm_thread_profiler = new ThreadProfiler();
  }
  if (task == NULL) {
    task = new FlatProfilerTask(WatcherThread::delay_interval);
    task->enroll();
  }
  timer.start();
  if (mainThread != NULL) {
    // When mainThread was created, it might not have a ThreadProfiler
    ThreadProfiler* pp = mainThread->get_thread_profiler();
    if (pp == NULL) {
      mainThread->set_thread_profiler(new ThreadProfiler());
    } else {
      pp->reset();
    }
    mainThread->get_thread_profiler()->engage();
  }
  // This is where we would assign thread_profiler
  // if we wanted only one thread_profiler for all threads.
  thread_profiler = NULL;
}

void FlatProfiler::disengage() {
  if (!task) {
    return;
  }
  timer.stop();
  task->disenroll();
  delete task;
  task = NULL;
  if (thread_profiler != NULL) {
    thread_profiler->disengage();
  } else {
    MutexLocker tl(Threads_lock);
    for (JavaThread* tp = Threads::first(); tp != NULL; tp = tp->next()) {
      ThreadProfiler* pp = tp->get_thread_profiler();
      if (pp != NULL) {
        pp->disengage();
      }
    }
  }
}

void FlatProfiler::reset() {
  if (task) {
    disengage();
  }

  class_loader_ticks = 0;
  extra_ticks        = 0;
  received_gc_ticks  = 0;
  vm_operation_ticks = 0;
  compiler_ticks     = 0;
  deopt_ticks        = 0;
  interpreter_ticks  = 0;
  blocked_ticks      = 0;
  unknown_ticks      = 0;
  received_ticks     = 0;
  delivered_ticks    = 0;
  timer.stop();
}

bool FlatProfiler::is_active() {
  return task != NULL;
}

void FlatProfiler::print_byte_code_statistics() {
  GrowableArray <ProfilerNode*>* array = new GrowableArray<ProfilerNode*>(200);

  tty->print_cr(" Bytecode ticks:");
  for (int index = 0; index < Bytecodes::number_of_codes; index++) {
    if (FlatProfiler::bytecode_ticks[index] > 0 || FlatProfiler::bytecode_ticks_stub[index] > 0) {
      tty->print_cr("  %4d %4d = %s",
        FlatProfiler::bytecode_ticks[index],
        FlatProfiler::bytecode_ticks_stub[index],
        Bytecodes::name( (Bytecodes::Code) index));
    }
  }
  tty->cr();
}

void print_ticks(const char* title, int ticks, int total) {
  if (ticks > 0) {
    tty->print("%5.1f%% %5d", ticks * 100.0 / total, ticks);
    tty->fill_to(col3);
    tty->print("%s", title);
    tty->cr();
  }
}

void ThreadProfiler::print(const char* thread_name) {
  ResourceMark rm;
  MutexLocker ppl(ProfilePrint_lock);
  int index = 0; // Declared outside for loops for portability

  if (table == NULL) {
    return;
  }

  if (thread_ticks <= 0) {
    return;
  }

  const char* title = "too soon to tell";
  double secs = timer.seconds();

  GrowableArray <ProfilerNode*>* array = new GrowableArray<ProfilerNode*>(200);
  for(index = 0; index < table_size; index++) {
    for(ProfilerNode* node = table[index]; node; node = node->next())
      array->append(node);
  }

  array->sort(&ProfilerNode::compare);

  // compute total (sanity check)
  int active =
    class_loader_ticks +
    compiler_ticks +
    interpreter_ticks +
    unknown_ticks();
  for (index = 0; index < array->length(); index++) {
    active += array->at(index)->ticks.total();
  }
  int total = active + blocked_ticks;

  tty->cr();
  tty->print_cr("Flat profile of %3.2f secs (%d total ticks): %s", secs, total, thread_name);
  if (total != thread_ticks) {
    print_ticks("Lost ticks", thread_ticks-total, thread_ticks);
  }
  tty->cr();

  // print interpreted methods
  tick_counter interpreted_ticks;
  bool has_interpreted_ticks = false;
  int print_count = 0;
  for (index = 0; index < array->length(); index++) {
    ProfilerNode* n = array->at(index);
    if (n->is_interpreted()) {
      interpreted_ticks.add(&n->ticks);
      if (!has_interpreted_ticks) {
        interpretedNode::print_title(tty);
        has_interpreted_ticks = true;
      }
      if (print_count++ < ProfilerNumberOfInterpretedMethods) {
        n->print(tty, active);
      }
    }
  }
  if (has_interpreted_ticks) {
    if (print_count <= ProfilerNumberOfInterpretedMethods) {
      title = "Total interpreted";
    } else {
      title = "Total interpreted (including elided)";
    }
    interpretedNode::print_total(tty, &interpreted_ticks, active, title);
    tty->cr();
  }

  // print compiled methods
  tick_counter compiled_ticks;
  bool has_compiled_ticks = false;
  print_count = 0;
  for (index = 0; index < array->length(); index++) {
    ProfilerNode* n = array->at(index);
    if (n->is_compiled()) {
      compiled_ticks.add(&n->ticks);
      if (!has_compiled_ticks) {
        compiledNode::print_title(tty);
        has_compiled_ticks = true;
      }
      if (print_count++ < ProfilerNumberOfCompiledMethods) {
        n->print(tty, active);
      }
    }
  }
  if (has_compiled_ticks) {
    if (print_count <= ProfilerNumberOfCompiledMethods) {
      title = "Total compiled";
    } else {
      title = "Total compiled (including elided)";
    }
    compiledNode::print_total(tty, &compiled_ticks, active, title);
    tty->cr();
  }

  // print stub methods
  tick_counter stub_ticks;
  bool has_stub_ticks = false;
  print_count = 0;
  for (index = 0; index < array->length(); index++) {
    ProfilerNode* n = array->at(index);
    if (n->is_stub()) {
      stub_ticks.add(&n->ticks);
      if (!has_stub_ticks) {
        stubNode::print_title(tty);
        has_stub_ticks = true;
      }
      if (print_count++ < ProfilerNumberOfStubMethods) {
        n->print(tty, active);
      }
    }
  }
  if (has_stub_ticks) {
    if (print_count <= ProfilerNumberOfStubMethods) {
      title = "Total stub";
    } else {
      title = "Total stub (including elided)";
    }
    stubNode::print_total(tty, &stub_ticks, active, title);
    tty->cr();
  }

  // print runtime stubs
  tick_counter runtime_stub_ticks;
  bool has_runtime_stub_ticks = false;
  print_count = 0;
  for (index = 0; index < array->length(); index++) {
    ProfilerNode* n = array->at(index);
    if (n->is_runtime_stub()) {
      runtime_stub_ticks.add(&n->ticks);
      if (!has_runtime_stub_ticks) {
        runtimeStubNode::print_title(tty);
        has_runtime_stub_ticks = true;
      }
      if (print_count++ < ProfilerNumberOfRuntimeStubNodes) {
        n->print(tty, active);
      }
    }
  }
  if (has_runtime_stub_ticks) {
    if (print_count <= ProfilerNumberOfRuntimeStubNodes) {
      title = "Total runtime stubs";
    } else {
      title = "Total runtime stubs (including elided)";
    }
    runtimeStubNode::print_total(tty, &runtime_stub_ticks, active, title);
    tty->cr();
  }

  if (blocked_ticks + class_loader_ticks + interpreter_ticks + compiler_ticks + unknown_ticks() != 0) {
    tty->fill_to(col1);
    tty->print_cr("Thread-local ticks:");
    print_ticks("Blocked (of total)",  blocked_ticks,      total);
    print_ticks("Class loader",        class_loader_ticks, active);
    print_ticks("Extra",               extra_ticks,        active);
    print_ticks("Interpreter",         interpreter_ticks,  active);
    print_ticks("Compilation",         compiler_ticks,     active);
    print_ticks("Unknown: vtable stubs",  unknown_ticks_array[ut_vtable_stubs],         active);
    print_ticks("Unknown: null method",   unknown_ticks_array[ut_null_method],          active);
    print_ticks("Unknown: running frame", unknown_ticks_array[ut_running_frame],        active);
    print_ticks("Unknown: calling frame", unknown_ticks_array[ut_calling_frame],        active);
    print_ticks("Unknown: no pc",         unknown_ticks_array[ut_no_pc],                active);
    print_ticks("Unknown: no last frame", unknown_ticks_array[ut_no_last_Java_frame],   active);
    print_ticks("Unknown: thread_state",  unknown_ticks_array[ut_unknown_thread_state], active);
    tty->cr();
  }

  if (WizardMode) {
    tty->print_cr("Node area used: " INTX_FORMAT " Kb", (area_top - area_bottom) / 1024);
  }
  reset();
}

/*
ThreadProfiler::print_unknown(){
  if (table == NULL) {
    return;
  }

  if (thread_ticks <= 0) {
    return;
  }
} */

void FlatProfiler::print(int unused) {
  ResourceMark rm;
  if (thread_profiler != NULL) {
    thread_profiler->print("All threads");
  } else {
    MutexLocker tl(Threads_lock);
    for (JavaThread* tp = Threads::first(); tp != NULL; tp = tp->next()) {
      ThreadProfiler* pp = tp->get_thread_profiler();
      if (pp != NULL) {
        pp->print(tp->get_thread_name());
      }
    }
  }

  if (ProfilerPrintByteCodeStatistics) {
    print_byte_code_statistics();
  }

  if (non_method_ticks() > 0) {
    tty->cr();
    tty->print_cr("Global summary of %3.2f seconds:", timer.seconds());
    print_ticks("Received ticks",      received_ticks,     received_ticks);
    print_ticks("Received GC ticks",   received_gc_ticks,  received_ticks);
    print_ticks("Compilation",         compiler_ticks,     received_ticks);
    print_ticks("Deoptimization",      deopt_ticks,        received_ticks);
    print_ticks("Other VM operations", vm_operation_ticks, received_ticks);
#ifndef PRODUCT
    print_ticks("Blocked ticks",       blocked_ticks,      received_ticks);
    print_ticks("Threads_lock blocks", threads_lock_ticks, received_ticks);
    print_ticks("Delivered ticks",     delivered_ticks,    received_ticks);
    print_ticks("All ticks",           all_ticks,          received_ticks);
#endif
    print_ticks("Class loader",        class_loader_ticks, received_ticks);
    print_ticks("Extra       ",        extra_ticks,        received_ticks);
    print_ticks("Interpreter",         interpreter_ticks,  received_ticks);
    print_ticks("Unknown code",        unknown_ticks,      received_ticks);
  }

  PCRecorder::print();

  if(ProfileVM){
    tty->cr();
    vm_thread_profiler->print("VM Thread");
  }
}

void IntervalData::print_header(outputStream* st) {
  st->print("i/c/n/g");
}

void IntervalData::print_data(outputStream* st) {
  st->print("%d/%d/%d/%d", interpreted(), compiled(), native(), compiling());
}

void FlatProfiler::interval_record_thread(ThreadProfiler* tp) {
  IntervalData id = tp->interval_data();
  int total = id.total();
  tp->interval_data_ref()->reset();

  // Insertion sort the data, if it's relevant.
  for (int i = 0; i < interval_print_size; i += 1) {
    if (total > interval_data[i].total()) {
      for (int j = interval_print_size - 1; j > i; j -= 1) {
        interval_data[j] = interval_data[j-1];
      }
      interval_data[i] = id;
      break;
    }
  }
}

void FlatProfiler::interval_print() {
  if ((interval_data[0].total() > 0)) {
    tty->stamp();
    tty->print("\t");
    IntervalData::print_header(tty);
    for (int i = 0; i < interval_print_size; i += 1) {
      if (interval_data[i].total() > 0) {
        tty->print("\t");
        interval_data[i].print_data(tty);
      }
    }
    tty->cr();
  }
}

void FlatProfiler::interval_reset() {
  for (int i = 0; i < interval_print_size; i += 1) {
    interval_data[i].reset();
  }
}

void ThreadProfiler::oops_do(OopClosure* f) {
  if (table == NULL) return;

  for(int index = 0; index < table_size; index++) {
    for(ProfilerNode* node = table[index]; node; node = node->next())
      node->oops_do(f);
  }
}

void FlatProfiler::oops_do(OopClosure* f) {
  if (thread_profiler != NULL) {
    thread_profiler->oops_do(f);
  } else {
    for (JavaThread* tp = Threads::first(); tp != NULL; tp = tp->next()) {
      ThreadProfiler* pp = tp->get_thread_profiler();
      if (pp != NULL) {
        pp->oops_do(f);
      }
    }
  }
}
