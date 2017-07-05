/*
 * Copyright (c) 1997, 2008, Oracle and/or its affiliates. All rights reserved.
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
# include "incls/_oopMapCache.cpp.incl"

class OopMapCacheEntry: private InterpreterOopMap {
  friend class InterpreterOopMap;
  friend class OopMapForCacheEntry;
  friend class OopMapCache;
  friend class VerifyClosure;

 protected:
  // Initialization
  void fill(methodHandle method, int bci);
  // fills the bit mask for native calls
  void fill_for_native(methodHandle method);
  void set_mask(CellTypeState* vars, CellTypeState* stack, int stack_top);

  // Deallocate bit masks and initialize fields
  void flush();

 private:
  void allocate_bit_mask();   // allocates the bit mask on C heap f necessary
  void deallocate_bit_mask(); // allocates the bit mask on C heap f necessary
  bool verify_mask(CellTypeState *vars, CellTypeState *stack, int max_locals, int stack_top);

 public:
  OopMapCacheEntry() : InterpreterOopMap() {
#ifdef ASSERT
     _resource_allocate_bit_mask = false;
#endif
  }
};


// Implementation of OopMapForCacheEntry
// (subclass of GenerateOopMap, initializes an OopMapCacheEntry for a given method and bci)

class OopMapForCacheEntry: public GenerateOopMap {
  OopMapCacheEntry *_entry;
  int               _bci;
  int               _stack_top;

  virtual bool report_results() const     { return false; }
  virtual bool possible_gc_point          (BytecodeStream *bcs);
  virtual void fill_stackmap_prolog       (int nof_gc_points);
  virtual void fill_stackmap_epilog       ();
  virtual void fill_stackmap_for_opcodes  (BytecodeStream *bcs,
                                           CellTypeState* vars,
                                           CellTypeState* stack,
                                           int stack_top);
  virtual void fill_init_vars             (GrowableArray<intptr_t> *init_vars);

 public:
  OopMapForCacheEntry(methodHandle method, int bci, OopMapCacheEntry *entry);

  // Computes stack map for (method,bci) and initialize entry
  void compute_map(TRAPS);
  int  size();
};


OopMapForCacheEntry::OopMapForCacheEntry(methodHandle method, int bci, OopMapCacheEntry* entry) : GenerateOopMap(method) {
  _bci       = bci;
  _entry     = entry;
  _stack_top = -1;
}


void OopMapForCacheEntry::compute_map(TRAPS) {
  assert(!method()->is_native(), "cannot compute oop map for native methods");
  // First check if it is a method where the stackmap is always empty
  if (method()->code_size() == 0 || method()->max_locals() + method()->max_stack() == 0) {
    _entry->set_mask_size(0);
  } else {
    ResourceMark rm;
    GenerateOopMap::compute_map(CATCH);
    result_for_basicblock(_bci);
  }
}


bool OopMapForCacheEntry::possible_gc_point(BytecodeStream *bcs) {
  return false; // We are not reporting any result. We call result_for_basicblock directly
}


void OopMapForCacheEntry::fill_stackmap_prolog(int nof_gc_points) {
  // Do nothing
}


void OopMapForCacheEntry::fill_stackmap_epilog() {
  // Do nothing
}


void OopMapForCacheEntry::fill_init_vars(GrowableArray<intptr_t> *init_vars) {
  // Do nothing
}


void OopMapForCacheEntry::fill_stackmap_for_opcodes(BytecodeStream *bcs,
                                                    CellTypeState* vars,
                                                    CellTypeState* stack,
                                                    int stack_top) {
  // Only interested in one specific bci
  if (bcs->bci() == _bci) {
    _entry->set_mask(vars, stack, stack_top);
    _stack_top = stack_top;
  }
}


int OopMapForCacheEntry::size() {
  assert(_stack_top != -1, "compute_map must be called first");
  return ((method()->is_static()) ? 0 : 1) + method()->max_locals() + _stack_top;
}


// Implementation of InterpreterOopMap and OopMapCacheEntry

class VerifyClosure : public OffsetClosure {
 private:
  OopMapCacheEntry* _entry;
  bool              _failed;

 public:
  VerifyClosure(OopMapCacheEntry* entry)         { _entry = entry; _failed = false; }
  void offset_do(int offset)                     { if (!_entry->is_oop(offset)) _failed = true; }
  bool failed() const                            { return _failed; }
};

InterpreterOopMap::InterpreterOopMap() {
  initialize();
#ifdef ASSERT
  _resource_allocate_bit_mask = true;
#endif
}

InterpreterOopMap::~InterpreterOopMap() {
  // The expection is that the bit mask was allocated
  // last in this resource area.  That would make the free of the
  // bit_mask effective (see how FREE_RESOURCE_ARRAY does a free).
  // If it was not allocated last, there is not a correctness problem
  // but the space for the bit_mask is not freed.
  assert(_resource_allocate_bit_mask, "Trying to free C heap space");
  if (mask_size() > small_mask_limit) {
    FREE_RESOURCE_ARRAY(uintptr_t, _bit_mask[0], mask_word_size());
  }
}

bool InterpreterOopMap::is_empty() {
  bool result = _method == NULL;
  assert(_method != NULL || (_bci == 0 &&
    (_mask_size == 0 || _mask_size == USHRT_MAX) &&
    _bit_mask[0] == 0), "Should be completely empty");
  return result;
}

void InterpreterOopMap::initialize() {
  _method    = NULL;
  _mask_size = USHRT_MAX;  // This value should cause a failure quickly
  _bci       = 0;
  _expression_stack_size = 0;
  for (int i = 0; i < N; i++) _bit_mask[i] = 0;
}


void InterpreterOopMap::oop_iterate(OopClosure *blk) {
  if (method() != NULL) {
    blk->do_oop((oop*) &_method);
  }
}

void InterpreterOopMap::oop_iterate(OopClosure *blk, MemRegion mr) {
  if (method() != NULL && mr.contains(&_method)) {
    blk->do_oop((oop*) &_method);
  }
}



void InterpreterOopMap::iterate_oop(OffsetClosure* oop_closure) {
  int n = number_of_entries();
  int word_index = 0;
  uintptr_t value = 0;
  uintptr_t mask = 0;
  // iterate over entries
  for (int i = 0; i < n; i++, mask <<= bits_per_entry) {
    // get current word
    if (mask == 0) {
      value = bit_mask()[word_index++];
      mask = 1;
    }
    // test for oop
    if ((value & (mask << oop_bit_number)) != 0) oop_closure->offset_do(i);
  }
}

void InterpreterOopMap::verify() {
  // If we are doing mark sweep _method may not have a valid header
  // $$$ This used to happen only for m/s collections; we might want to
  // think of an appropriate generalization of this distinction.
  guarantee(Universe::heap()->is_gc_active() || _method->is_oop_or_null(),
            "invalid oop in oopMapCache");
}

#ifdef ENABLE_ZAP_DEAD_LOCALS

void InterpreterOopMap::iterate_all(OffsetClosure* oop_closure, OffsetClosure* value_closure, OffsetClosure* dead_closure) {
  int n = number_of_entries();
  int word_index = 0;
  uintptr_t value = 0;
  uintptr_t mask = 0;
  // iterate over entries
  for (int i = 0; i < n; i++, mask <<= bits_per_entry) {
    // get current word
    if (mask == 0) {
      value = bit_mask()[word_index++];
      mask = 1;
    }
    // test for dead values  & oops, and for live values
         if ((value & (mask << dead_bit_number)) != 0)  dead_closure->offset_do(i); // call this for all dead values or oops
    else if ((value & (mask <<  oop_bit_number)) != 0)   oop_closure->offset_do(i); // call this for all live oops
    else                                               value_closure->offset_do(i); // call this for all live values
  }
}

#endif


void InterpreterOopMap::print() {
  int n = number_of_entries();
  tty->print("oop map for ");
  method()->print_value();
  tty->print(" @ %d = [%d] { ", bci(), n);
  for (int i = 0; i < n; i++) {
#ifdef ENABLE_ZAP_DEAD_LOCALS
    if (is_dead(i)) tty->print("%d+ ", i);
    else
#endif
    if (is_oop(i)) tty->print("%d ", i);
  }
  tty->print_cr("}");
}

class MaskFillerForNative: public NativeSignatureIterator {
 private:
  uintptr_t * _mask;                             // the bit mask to be filled
  int         _size;                             // the mask size in bits

  void set_one(int i) {
    i *= InterpreterOopMap::bits_per_entry;
    assert(0 <= i && i < _size, "offset out of bounds");
    _mask[i / BitsPerWord] |= (((uintptr_t) 1 << InterpreterOopMap::oop_bit_number) << (i % BitsPerWord));
  }

 public:
  void pass_int()                                { /* ignore */ }
  void pass_long()                               { /* ignore */ }
#if defined(_LP64) || defined(ZERO)
  void pass_float()                              { /* ignore */ }
#endif
  void pass_double()                             { /* ignore */ }
  void pass_object()                             { set_one(offset()); }

  MaskFillerForNative(methodHandle method, uintptr_t* mask, int size) : NativeSignatureIterator(method) {
    _mask   = mask;
    _size   = size;
    // initialize with 0
    int i = (size + BitsPerWord - 1) / BitsPerWord;
    while (i-- > 0) _mask[i] = 0;
  }

  void generate() {
    NativeSignatureIterator::iterate();
  }
};

bool OopMapCacheEntry::verify_mask(CellTypeState* vars, CellTypeState* stack, int max_locals, int stack_top) {
  // Check mask includes map
  VerifyClosure blk(this);
  iterate_oop(&blk);
  if (blk.failed()) return false;

  // Check if map is generated correctly
  // (Use ?: operator to make sure all 'true' & 'false' are represented exactly the same so we can use == afterwards)
  if (TraceOopMapGeneration && Verbose) tty->print("Locals (%d): ", max_locals);

  for(int i = 0; i < max_locals; i++) {
    bool v1 = is_oop(i)               ? true : false;
    bool v2 = vars[i].is_reference()  ? true : false;
    assert(v1 == v2, "locals oop mask generation error");
    if (TraceOopMapGeneration && Verbose) tty->print("%d", v1 ? 1 : 0);
#ifdef ENABLE_ZAP_DEAD_LOCALS
    bool v3 = is_dead(i)              ? true : false;
    bool v4 = !vars[i].is_live()      ? true : false;
    assert(v3 == v4, "locals live mask generation error");
    assert(!(v1 && v3), "dead value marked as oop");
#endif
  }

  if (TraceOopMapGeneration && Verbose) { tty->cr(); tty->print("Stack (%d): ", stack_top); }
  for(int j = 0; j < stack_top; j++) {
    bool v1 = is_oop(max_locals + j)  ? true : false;
    bool v2 = stack[j].is_reference() ? true : false;
    assert(v1 == v2, "stack oop mask generation error");
    if (TraceOopMapGeneration && Verbose) tty->print("%d", v1 ? 1 : 0);
#ifdef ENABLE_ZAP_DEAD_LOCALS
    bool v3 = is_dead(max_locals + j) ? true : false;
    bool v4 = !stack[j].is_live()     ? true : false;
    assert(v3 == v4, "stack live mask generation error");
    assert(!(v1 && v3), "dead value marked as oop");
#endif
  }
  if (TraceOopMapGeneration && Verbose) tty->cr();
  return true;
}

void OopMapCacheEntry::allocate_bit_mask() {
  if (mask_size() > small_mask_limit) {
    assert(_bit_mask[0] == 0, "bit mask should be new or just flushed");
    _bit_mask[0] = (intptr_t)
      NEW_C_HEAP_ARRAY(uintptr_t, mask_word_size());
  }
}

void OopMapCacheEntry::deallocate_bit_mask() {
  if (mask_size() > small_mask_limit && _bit_mask[0] != 0) {
    assert(!Thread::current()->resource_area()->contains((void*)_bit_mask[0]),
      "This bit mask should not be in the resource area");
    FREE_C_HEAP_ARRAY(uintptr_t, _bit_mask[0]);
    debug_only(_bit_mask[0] = 0;)
  }
}


void OopMapCacheEntry::fill_for_native(methodHandle mh) {
  assert(mh->is_native(), "method must be native method");
  set_mask_size(mh->size_of_parameters() * bits_per_entry);
  allocate_bit_mask();
  // fill mask for parameters
  MaskFillerForNative mf(mh, bit_mask(), mask_size());
  mf.generate();
}


void OopMapCacheEntry::fill(methodHandle method, int bci) {
  HandleMark hm;
  // Flush entry to deallocate an existing entry
  flush();
  set_method(method());
  set_bci(bci);
  if (method->is_native()) {
    // Native method activations have oops only among the parameters and one
    // extra oop following the parameters (the mirror for static native methods).
    fill_for_native(method);
  } else {
    EXCEPTION_MARK;
    OopMapForCacheEntry gen(method, bci, this);
    gen.compute_map(CATCH);
  }
  #ifdef ASSERT
    verify();
  #endif
}


void OopMapCacheEntry::set_mask(CellTypeState *vars, CellTypeState *stack, int stack_top) {
  // compute bit mask size
  int max_locals = method()->max_locals();
  int n_entries = max_locals + stack_top;
  set_mask_size(n_entries * bits_per_entry);
  allocate_bit_mask();
  set_expression_stack_size(stack_top);

  // compute bits
  int word_index = 0;
  uintptr_t value = 0;
  uintptr_t mask = 1;

  CellTypeState* cell = vars;
  for (int entry_index = 0; entry_index < n_entries; entry_index++, mask <<= bits_per_entry, cell++) {
    // store last word
    if (mask == 0) {
      bit_mask()[word_index++] = value;
      value = 0;
      mask = 1;
    }

    // switch to stack when done with locals
    if (entry_index == max_locals) {
      cell = stack;
    }

    // set oop bit
    if ( cell->is_reference()) {
      value |= (mask << oop_bit_number );
    }

  #ifdef ENABLE_ZAP_DEAD_LOCALS
    // set dead bit
    if (!cell->is_live()) {
      value |= (mask << dead_bit_number);
      assert(!cell->is_reference(), "dead value marked as oop");
    }
  #endif
  }

  // make sure last word is stored
  bit_mask()[word_index] = value;

  // verify bit mask
  assert(verify_mask(vars, stack, max_locals, stack_top), "mask could not be verified");


}

void OopMapCacheEntry::flush() {
  deallocate_bit_mask();
  initialize();
}


// Implementation of OopMapCache

#ifndef PRODUCT

static long _total_memory_usage = 0;

long OopMapCache::memory_usage() {
  return _total_memory_usage;
}

#endif

void InterpreterOopMap::resource_copy(OopMapCacheEntry* from) {
  assert(_resource_allocate_bit_mask,
    "Should not resource allocate the _bit_mask");
  assert(from->method()->is_oop(), "MethodOop is bad");

  set_method(from->method());
  set_bci(from->bci());
  set_mask_size(from->mask_size());
  set_expression_stack_size(from->expression_stack_size());

  // Is the bit mask contained in the entry?
  if (from->mask_size() <= small_mask_limit) {
    memcpy((void *)_bit_mask, (void *)from->_bit_mask,
      mask_word_size() * BytesPerWord);
  } else {
    // The expectation is that this InterpreterOopMap is a recently created
    // and empty. It is used to get a copy of a cached entry.
    // If the bit mask has a value, it should be in the
    // resource area.
    assert(_bit_mask[0] == 0 ||
      Thread::current()->resource_area()->contains((void*)_bit_mask[0]),
      "The bit mask should have been allocated from a resource area");
    // Allocate the bit_mask from a Resource area for performance.  Allocating
    // from the C heap as is done for OopMapCache has a significant
    // performance impact.
    _bit_mask[0] = (uintptr_t) NEW_RESOURCE_ARRAY(uintptr_t, mask_word_size());
    assert(_bit_mask[0] != 0, "bit mask was not allocated");
    memcpy((void*) _bit_mask[0], (void*) from->_bit_mask[0],
      mask_word_size() * BytesPerWord);
  }
}

inline unsigned int OopMapCache::hash_value_for(methodHandle method, int bci) {
  // We use method->code_size() rather than method->identity_hash() below since
  // the mark may not be present if a pointer to the method is already reversed.
  return   ((unsigned int) bci)
         ^ ((unsigned int) method->max_locals()         << 2)
         ^ ((unsigned int) method->code_size()          << 4)
         ^ ((unsigned int) method->size_of_parameters() << 6);
}


OopMapCache::OopMapCache() :
  _mut(Mutex::leaf, "An OopMapCache lock", true)
{
  _array  = NEW_C_HEAP_ARRAY(OopMapCacheEntry, _size);
  // Cannot call flush for initialization, since flush
  // will check if memory should be deallocated
  for(int i = 0; i < _size; i++) _array[i].initialize();
  NOT_PRODUCT(_total_memory_usage += sizeof(OopMapCache) + (sizeof(OopMapCacheEntry) * _size);)
}


OopMapCache::~OopMapCache() {
  assert(_array != NULL, "sanity check");
  // Deallocate oop maps that are allocated out-of-line
  flush();
  // Deallocate array
  NOT_PRODUCT(_total_memory_usage -= sizeof(OopMapCache) + (sizeof(OopMapCacheEntry) * _size);)
  FREE_C_HEAP_ARRAY(OopMapCacheEntry, _array);
}

OopMapCacheEntry* OopMapCache::entry_at(int i) const {
  return &_array[i % _size];
}

void OopMapCache::flush() {
  for (int i = 0; i < _size; i++) _array[i].flush();
}

void OopMapCache::flush_obsolete_entries() {
  for (int i = 0; i < _size; i++)
    if (!_array[i].is_empty() && _array[i].method()->is_old()) {
      // Cache entry is occupied by an old redefined method and we don't want
      // to pin it down so flush the entry.
      RC_TRACE(0x08000000, ("flush: %s(%s): cached entry @%d",
        _array[i].method()->name()->as_C_string(),
        _array[i].method()->signature()->as_C_string(), i));

      _array[i].flush();
    }
}

void OopMapCache::oop_iterate(OopClosure *blk) {
  for (int i = 0; i < _size; i++) _array[i].oop_iterate(blk);
}

void OopMapCache::oop_iterate(OopClosure *blk, MemRegion mr) {
    for (int i = 0; i < _size; i++) _array[i].oop_iterate(blk, mr);
}

void OopMapCache::verify() {
  for (int i = 0; i < _size; i++) _array[i].verify();
}

void OopMapCache::lookup(methodHandle method,
                         int bci,
                         InterpreterOopMap* entry_for) {
  MutexLocker x(&_mut);

  OopMapCacheEntry* entry = NULL;
  int probe = hash_value_for(method, bci);

  // Search hashtable for match
  int i;
  for(i = 0; i < _probe_depth; i++) {
    entry = entry_at(probe + i);
    if (entry->match(method, bci)) {
      entry_for->resource_copy(entry);
      assert(!entry_for->is_empty(), "A non-empty oop map should be returned");
      return;
    }
  }

  if (TraceOopMapGeneration) {
    static int count = 0;
    ResourceMark rm;
    tty->print("%d - Computing oopmap at bci %d for ", ++count, bci);
    method->print_value(); tty->cr();
  }

  // Entry is not in hashtable.
  // Compute entry and return it

  if (method->should_not_be_cached()) {
    // It is either not safe or not a good idea to cache this methodOop
    // at this time. We give the caller of lookup() a copy of the
    // interesting info via parameter entry_for, but we don't add it to
    // the cache. See the gory details in methodOop.cpp.
    compute_one_oop_map(method, bci, entry_for);
    return;
  }

  // First search for an empty slot
  for(i = 0; i < _probe_depth; i++) {
    entry  = entry_at(probe + i);
    if (entry->is_empty()) {
      entry->fill(method, bci);
      entry_for->resource_copy(entry);
      assert(!entry_for->is_empty(), "A non-empty oop map should be returned");
      return;
    }
  }

  if (TraceOopMapGeneration) {
    ResourceMark rm;
    tty->print_cr("*** collision in oopmap cache - flushing item ***");
  }

  // No empty slot (uncommon case). Use (some approximation of a) LRU algorithm
  //entry_at(probe + _probe_depth - 1)->flush();
  //for(i = _probe_depth - 1; i > 0; i--) {
  //  // Coping entry[i] = entry[i-1];
  //  OopMapCacheEntry *to   = entry_at(probe + i);
  //  OopMapCacheEntry *from = entry_at(probe + i - 1);
  //  to->copy(from);
  // }

  assert(method->is_method(), "gaga");

  entry = entry_at(probe + 0);
  entry->fill(method, bci);

  // Copy the  newly cached entry to input parameter
  entry_for->resource_copy(entry);

  if (TraceOopMapGeneration) {
    ResourceMark rm;
    tty->print("Done with ");
    method->print_value(); tty->cr();
  }
  assert(!entry_for->is_empty(), "A non-empty oop map should be returned");

  return;
}

void OopMapCache::compute_one_oop_map(methodHandle method, int bci, InterpreterOopMap* entry) {
  // Due to the invariants above it's tricky to allocate a temporary OopMapCacheEntry on the stack
  OopMapCacheEntry* tmp = NEW_C_HEAP_ARRAY(OopMapCacheEntry, 1);
  tmp->initialize();
  tmp->fill(method, bci);
  entry->resource_copy(tmp);
  FREE_C_HEAP_ARRAY(OopMapCacheEntry, tmp);
}
