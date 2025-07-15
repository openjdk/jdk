/*
 * Copyright (c) 1997, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "cds/archiveHeapLoader.hpp"
#include "cds/cdsConfig.hpp"
#include "cds/heapShared.hpp"
#include "classfile/classLoader.hpp"
#include "classfile/classLoaderData.inline.hpp"
#include "classfile/classLoaderDataGraph.inline.hpp"
#include "classfile/javaClasses.inline.hpp"
#include "classfile/moduleEntry.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/systemDictionaryShared.hpp"
#include "classfile/vmClasses.hpp"
#include "classfile/vmSymbols.hpp"
#include "gc/shared/collectedHeap.inline.hpp"
#include "jvm_io.h"
#include "logging/log.hpp"
#include "memory/metadataFactory.hpp"
#include "memory/metaspaceClosure.hpp"
#include "memory/oopFactory.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "oops/compressedKlass.inline.hpp"
#include "oops/compressedOops.inline.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/klass.inline.hpp"
#include "oops/objArrayKlass.hpp"
#include "oops/oop.inline.hpp"
#include "oops/oopHandle.inline.hpp"
#include "prims/jvmtiExport.hpp"
#include "runtime/atomic.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/perfData.hpp"
#include "utilities/macros.hpp"
#include "utilities/powerOfTwo.hpp"
#include "utilities/rotate_bits.hpp"
#include "utilities/stack.inline.hpp"

void Klass::set_java_mirror(Handle m) {
  assert(!m.is_null(), "New mirror should never be null.");
  assert(_java_mirror.is_empty(), "should only be used to initialize mirror");
  _java_mirror = class_loader_data()->add_handle(m);
}

bool Klass::is_cloneable() const {
  return _misc_flags.is_cloneable_fast() ||
         is_subtype_of(vmClasses::Cloneable_klass());
}

void Klass::set_is_cloneable() {
  if (name() == vmSymbols::java_lang_invoke_MemberName()) {
    assert(is_final(), "no subclasses allowed");
    // MemberName cloning should not be intrinsified and always happen in JVM_Clone.
  } else if (is_instance_klass() && InstanceKlass::cast(this)->reference_type() != REF_NONE) {
    // Reference cloning should not be intrinsified and always happen in JVM_Clone.
  } else {
    _misc_flags.set_is_cloneable_fast(true);
  }
}

uint8_t Klass::compute_hash_slot(Symbol* n) {
  uint hash_code;
  // Special cases for the two superclasses of all Array instances.
  // Code elsewhere assumes, for all instances of ArrayKlass, that
  // these two interfaces will be in this order.

  // We ensure there are some empty slots in the hash table between
  // these two very common interfaces because if they were adjacent
  // (e.g. Slots 0 and 1), then any other class which hashed to 0 or 1
  // would result in a probe length of 3.
  if (n == vmSymbols::java_lang_Cloneable()) {
    hash_code = 0;
  } else if (n == vmSymbols::java_io_Serializable()) {
    hash_code = SECONDARY_SUPERS_TABLE_SIZE / 2;
  } else {
    auto s = (const jbyte*) n->bytes();
    hash_code = java_lang_String::hash_code(s, n->utf8_length());
    // We use String::hash_code here (rather than e.g.
    // Symbol::identity_hash()) in order to have a hash code that
    // does not change from run to run. We want that because the
    // hash value for a secondary superclass appears in generated
    // code as a constant.

    // This constant is magic: see Knuth, "Fibonacci Hashing".
    constexpr uint multiplier
      = 2654435769; // (uint)(((u8)1 << 32) / ((1 + sqrt(5)) / 2 ))
    constexpr uint hash_shift = sizeof(hash_code) * 8 - 6;
    // The leading bits of the least significant half of the product.
    hash_code = (hash_code * multiplier) >> hash_shift;

    if (StressSecondarySupers) {
      // Generate many hash collisions in order to stress-test the
      // linear search fallback.
      hash_code = hash_code % 3;
      hash_code = hash_code * (SECONDARY_SUPERS_TABLE_SIZE / 3);
    }
  }

  return (hash_code & SECONDARY_SUPERS_TABLE_MASK);
}

void Klass::set_name(Symbol* n) {
  _name = n;

  if (_name != nullptr) {
    _name->increment_refcount();
  }

  {
    elapsedTimer selftime;
    selftime.start();

    _hash_slot = compute_hash_slot(n);
    assert(_hash_slot < SECONDARY_SUPERS_TABLE_SIZE, "required");

    selftime.stop();
    if (UsePerfData) {
      ClassLoader::perf_secondary_hash_time()->inc(selftime.ticks());
    }
  }

  if (CDSConfig::is_dumping_archive() && is_instance_klass()) {
    SystemDictionaryShared::init_dumptime_info(InstanceKlass::cast(this));
  }
}

bool Klass::is_subclass_of(const Klass* k) const {
  // Run up the super chain and check
  if (this == k) return true;

  Klass* t = const_cast<Klass*>(this)->super();

  while (t != nullptr) {
    if (t == k) return true;
    t = t->super();
  }
  return false;
}

void Klass::release_C_heap_structures(bool release_constant_pool) {
  if (_name != nullptr) _name->decrement_refcount();
}

bool Klass::linear_search_secondary_supers(const Klass* k) const {
  // Scan the array-of-objects for a match
  // FIXME: We could do something smarter here, maybe a vectorized
  // comparison or a binary search, but is that worth any added
  // complexity?
  int cnt = secondary_supers()->length();
  for (int i = 0; i < cnt; i++) {
    if (secondary_supers()->at(i) == k) {
      return true;
    }
  }
  return false;
}

// Given a secondary superklass k, an initial array index, and an
// occupancy bitmap rotated such that Bit 1 is the next bit to test,
// search for k.
bool Klass::fallback_search_secondary_supers(const Klass* k, int index, uintx rotated_bitmap) const {
  // Once the occupancy bitmap is almost full, it's faster to use a
  // linear search.
  if (secondary_supers()->length() > SECONDARY_SUPERS_TABLE_SIZE - 2) {
    return linear_search_secondary_supers(k);
  }

  // This is conventional linear probing, but instead of terminating
  // when a null entry is found in the table, we maintain a bitmap
  // in which a 0 indicates missing entries.

  precond((int)population_count(rotated_bitmap) == secondary_supers()->length());

  // The check for secondary_supers()->length() <= SECONDARY_SUPERS_TABLE_SIZE - 2
  // at the start of this function guarantees there are 0s in the
  // bitmap, so this loop eventually terminates.
  while ((rotated_bitmap & 2) != 0) {
    if (++index == secondary_supers()->length()) {
      index = 0;
    }
    if (secondary_supers()->at(index) == k) {
      return true;
    }
    rotated_bitmap = rotate_right(rotated_bitmap, 1);
  }
  return false;
}

// Return self, except for abstract classes with exactly 1
// implementor.  Then return the 1 concrete implementation.
Klass *Klass::up_cast_abstract() {
  Klass *r = this;
  while( r->is_abstract() ) {   // Receiver is abstract?
    Klass *s = r->subklass();   // Check for exactly 1 subklass
    if (s == nullptr || s->next_sibling() != nullptr) // Oops; wrong count; give up
      return this;              // Return 'this' as a no-progress flag
    r = s;                    // Loop till find concrete class
  }
  return r;                   // Return the 1 concrete class
}

// Find LCA in class hierarchy
Klass *Klass::LCA( Klass *k2 ) {
  Klass *k1 = this;
  while( 1 ) {
    if( k1->is_subtype_of(k2) ) return k2;
    if( k2->is_subtype_of(k1) ) return k1;
    k1 = k1->super();
    k2 = k2->super();
  }
}


void Klass::check_valid_for_instantiation(bool throwError, TRAPS) {
  ResourceMark rm(THREAD);
  THROW_MSG(throwError ? vmSymbols::java_lang_InstantiationError()
            : vmSymbols::java_lang_InstantiationException(), external_name());
}


void Klass::copy_array(arrayOop s, int src_pos, arrayOop d, int dst_pos, int length, TRAPS) {
  ResourceMark rm(THREAD);
  assert(s != nullptr, "Throw NPE!");
  THROW_MSG(vmSymbols::java_lang_ArrayStoreException(),
            err_msg("arraycopy: source type %s is not an array", s->klass()->external_name()));
}


void Klass::initialize(TRAPS) {
  ShouldNotReachHere();
}

Klass* Klass::find_field(Symbol* name, Symbol* sig, fieldDescriptor* fd) const {
#ifdef ASSERT
  tty->print_cr("Error: find_field called on a klass oop."
                " Likely error: reflection method does not correctly"
                " wrap return value in a mirror object.");
#endif
  ShouldNotReachHere();
  return nullptr;
}

Method* Klass::uncached_lookup_method(const Symbol* name, const Symbol* signature,
                                      OverpassLookupMode overpass_mode,
                                      PrivateLookupMode private_mode) const {
#ifdef ASSERT
  tty->print_cr("Error: uncached_lookup_method called on a klass oop."
                " Likely error: reflection method does not correctly"
                " wrap return value in a mirror object.");
#endif
  ShouldNotReachHere();
  return nullptr;
}

static markWord make_prototype(const Klass* kls) {
  markWord prototype = markWord::prototype();
#ifdef _LP64
  if (UseCompactObjectHeaders) {
    // With compact object headers, the narrow Klass ID is part of the mark word.
    // We therfore seed the mark word with the narrow Klass ID.
    // Note that only those Klass that can be instantiated have a narrow Klass ID.
    // For those who don't, we leave the klass bits empty and assert if someone
    // tries to use those.
    const narrowKlass nk = CompressedKlassPointers::is_encodable(kls) ?
        CompressedKlassPointers::encode(const_cast<Klass*>(kls)) : 0;
    prototype = prototype.set_narrow_klass(nk);
  }
#endif
  return prototype;
}

Klass::Klass() : _kind(UnknownKlassKind) {
  assert(CDSConfig::is_dumping_static_archive() || CDSConfig::is_using_archive(), "only for cds");
}

// "Normal" instantiation is preceded by a MetaspaceObj allocation
// which zeros out memory - calloc equivalent.
// The constructor is also used from CppVtableCloner,
// which doesn't zero out the memory before calling the constructor.
Klass::Klass(KlassKind kind) : _kind(kind),
                               _prototype_header(make_prototype(this)),
                               _shared_class_path_index(-1) {
  CDS_ONLY(_shared_class_flags = 0;)
  CDS_JAVA_HEAP_ONLY(_archived_mirror_index = -1;)
  _primary_supers[0] = this;
  set_super_check_offset(in_bytes(primary_supers_offset()));
}

jint Klass::array_layout_helper(BasicType etype) {
  assert(etype >= T_BOOLEAN && etype <= T_OBJECT, "valid etype");
  // Note that T_ARRAY is not allowed here.
  int  hsize = arrayOopDesc::base_offset_in_bytes(etype);
  int  esize = type2aelembytes(etype);
  bool isobj = (etype == T_OBJECT);
  int  tag   =  isobj ? _lh_array_tag_obj_value : _lh_array_tag_type_value;
  int lh = array_layout_helper(tag, hsize, etype, exact_log2(esize));

  assert(lh < (int)_lh_neutral_value, "must look like an array layout");
  assert(layout_helper_is_array(lh), "correct kind");
  assert(layout_helper_is_objArray(lh) == isobj, "correct kind");
  assert(layout_helper_is_typeArray(lh) == !isobj, "correct kind");
  assert(layout_helper_header_size(lh) == hsize, "correct decode");
  assert(layout_helper_element_type(lh) == etype, "correct decode");
  assert(1 << layout_helper_log2_element_size(lh) == esize, "correct decode");

  return lh;
}

int Klass::modifier_flags() const {
  int mods = java_lang_Class::modifiers(java_mirror());
  assert(mods == compute_modifier_flags(), "should be same");
  return mods;
}

bool Klass::can_be_primary_super_slow() const {
  if (super() == nullptr)
    return true;
  else if (super()->super_depth() >= primary_super_limit()-1)
    return false;
  else
    return true;
}

void Klass::set_secondary_supers(Array<Klass*>* secondaries, uintx bitmap) {
#ifdef ASSERT
  if (secondaries != nullptr) {
    uintx real_bitmap = compute_secondary_supers_bitmap(secondaries);
    assert(bitmap == real_bitmap, "must be");
    assert(secondaries->length() >= (int)population_count(bitmap), "must be");
  }
#endif
  _secondary_supers_bitmap = bitmap;
  _secondary_supers = secondaries;

  if (secondaries != nullptr) {
    LogMessage(class, load) msg;
    NonInterleavingLogStream log {LogLevel::Debug, msg};
    if (log.is_enabled()) {
      ResourceMark rm;
      log.print_cr("set_secondary_supers: hash_slot: %d; klass: %s", hash_slot(), external_name());
      print_secondary_supers_on(&log);
    }
  }
}

// Hashed secondary superclasses
//
// We use a compressed 64-entry hash table with linear probing. We
// start by creating a hash table in the usual way, followed by a pass
// that removes all the null entries. To indicate which entries would
// have been null we use a bitmap that contains a 1 in each position
// where an entry is present, 0 otherwise. This bitmap also serves as
// a kind of Bloom filter, which in many cases allows us quickly to
// eliminate the possibility that something is a member of a set of
// secondaries.
uintx Klass::hash_secondary_supers(Array<Klass*>* secondaries, bool rewrite) {
  const int length = secondaries->length();

  if (length == 0) {
    return SECONDARY_SUPERS_BITMAP_EMPTY;
  }

  if (length == 1) {
    int hash_slot = secondaries->at(0)->hash_slot();
    return uintx(1) << hash_slot;
  }

  // Invariant: _secondary_supers.length >= population_count(_secondary_supers_bitmap)

  // Don't attempt to hash a table that's completely full, because in
  // the case of an absent interface linear probing would not
  // terminate.
  if (length >= SECONDARY_SUPERS_TABLE_SIZE) {
    return SECONDARY_SUPERS_BITMAP_FULL;
  }

  {
    PerfTraceTime ptt(ClassLoader::perf_secondary_hash_time());

    ResourceMark rm;
    uintx bitmap = SECONDARY_SUPERS_BITMAP_EMPTY;
    auto hashed_secondaries = new GrowableArray<Klass*>(SECONDARY_SUPERS_TABLE_SIZE,
                                                        SECONDARY_SUPERS_TABLE_SIZE, nullptr);

    for (int j = 0; j < length; j++) {
      Klass* k = secondaries->at(j);
      hash_insert(k, hashed_secondaries, bitmap);
    }

    // Pack the hashed secondaries array by copying it into the
    // secondaries array, sans nulls, if modification is allowed.
    // Otherwise, validate the order.
    int i = 0;
    for (int slot = 0; slot < SECONDARY_SUPERS_TABLE_SIZE; slot++) {
      bool has_element = ((bitmap >> slot) & 1) != 0;
      assert(has_element == (hashed_secondaries->at(slot) != nullptr), "");
      if (has_element) {
        Klass* k = hashed_secondaries->at(slot);
        if (rewrite) {
          secondaries->at_put(i, k);
        } else if (secondaries->at(i) != k) {
          assert(false, "broken secondary supers hash table");
          return SECONDARY_SUPERS_BITMAP_FULL;
        }
        i++;
      }
    }
    assert(i == secondaries->length(), "mismatch");
    postcond((int)population_count(bitmap) == secondaries->length());

    return bitmap;
  }
}

void Klass::hash_insert(Klass* klass, GrowableArray<Klass*>* secondaries, uintx& bitmap) {
  assert(bitmap != SECONDARY_SUPERS_BITMAP_FULL, "");

  int dist = 0;
  for (int slot = klass->hash_slot(); true; slot = (slot + 1) & SECONDARY_SUPERS_TABLE_MASK) {
    Klass* existing = secondaries->at(slot);
    assert(((bitmap >> slot) & 1) == (existing != nullptr), "mismatch");
    if (existing == nullptr) { // no conflict
      secondaries->at_put(slot, klass);
      bitmap |= uintx(1) << slot;
      assert(bitmap != SECONDARY_SUPERS_BITMAP_FULL, "");
      return;
    } else {
      // Use Robin Hood hashing to minimize the worst case search.
      // Also, every permutation of the insertion sequence produces
      // the same final Robin Hood hash table, provided that a
      // consistent tie breaker is used.
      int existing_dist = (slot - existing->hash_slot()) & SECONDARY_SUPERS_TABLE_MASK;
      if (existing_dist < dist
          // This tie breaker ensures that the hash order is maintained.
          || ((existing_dist == dist)
              && (uintptr_t(existing) < uintptr_t(klass)))) {
        Klass* tmp = secondaries->at(slot);
        secondaries->at_put(slot, klass);
        klass = tmp;
        dist = existing_dist;
      }
      ++dist;
    }
  }
}

Array<Klass*>* Klass::pack_secondary_supers(ClassLoaderData* loader_data,
                                            GrowableArray<Klass*>* primaries,
                                            GrowableArray<Klass*>* secondaries,
                                            uintx& bitmap, TRAPS) {
  int new_length = primaries->length() + secondaries->length();
  Array<Klass*>* secondary_supers = MetadataFactory::new_array<Klass*>(loader_data, new_length, CHECK_NULL);

  // Combine the two arrays into a metadata object to pack the array.
  // The primaries are added in the reverse order, then the secondaries.
  int fill_p = primaries->length();
  for (int j = 0; j < fill_p; j++) {
    secondary_supers->at_put(j, primaries->pop());  // add primaries in reverse order.
  }
  for( int j = 0; j < secondaries->length(); j++ ) {
    secondary_supers->at_put(j+fill_p, secondaries->at(j));  // add secondaries on the end.
  }
#ifdef ASSERT
  // We must not copy any null placeholders left over from bootstrap.
  for (int j = 0; j < secondary_supers->length(); j++) {
    assert(secondary_supers->at(j) != nullptr, "correct bootstrapping order");
  }
#endif

  bitmap = hash_secondary_supers(secondary_supers, /*rewrite=*/true); // rewrites freshly allocated array
  return secondary_supers;
}

uintx Klass::compute_secondary_supers_bitmap(Array<Klass*>* secondary_supers) {
  return hash_secondary_supers(secondary_supers, /*rewrite=*/false); // no rewrites allowed
}

uint8_t Klass::compute_home_slot(Klass* k, uintx bitmap) {
  uint8_t hash = k->hash_slot();
  if (hash > 0) {
    return population_count(bitmap << (SECONDARY_SUPERS_TABLE_SIZE - hash));
  }
  return 0;
}


void Klass::initialize_supers(Klass* k, Array<InstanceKlass*>* transitive_interfaces, TRAPS) {
  if (k == nullptr) {
    set_super(nullptr);
    _primary_supers[0] = this;
    assert(super_depth() == 0, "Object must already be initialized properly");
  } else if (k != super() || k == vmClasses::Object_klass()) {
    assert(super() == nullptr || super() == vmClasses::Object_klass(),
           "initialize this only once to a non-trivial value");
    set_super(k);
    Klass* sup = k;
    int sup_depth = sup->super_depth();
    juint my_depth  = MIN2(sup_depth + 1, (int)primary_super_limit());
    if (!can_be_primary_super_slow())
      my_depth = primary_super_limit();
    for (juint i = 0; i < my_depth; i++) {
      _primary_supers[i] = sup->_primary_supers[i];
    }
    Klass* *super_check_cell;
    if (my_depth < primary_super_limit()) {
      _primary_supers[my_depth] = this;
      super_check_cell = &_primary_supers[my_depth];
    } else {
      // Overflow of the primary_supers array forces me to be secondary.
      super_check_cell = &_secondary_super_cache;
    }
    set_super_check_offset(u4((address)super_check_cell - (address) this));

#ifdef ASSERT
    {
      juint j = super_depth();
      assert(j == my_depth, "computed accessor gets right answer");
      Klass* t = this;
      while (!t->can_be_primary_super()) {
        t = t->super();
        j = t->super_depth();
      }
      for (juint j1 = j+1; j1 < primary_super_limit(); j1++) {
        assert(primary_super_of_depth(j1) == nullptr, "super list padding");
      }
      while (t != nullptr) {
        assert(primary_super_of_depth(j) == t, "super list initialization");
        t = t->super();
        --j;
      }
      assert(j == (juint)-1, "correct depth count");
    }
#endif
  }

  if (secondary_supers() == nullptr) {

    // Now compute the list of secondary supertypes.
    // Secondaries can occasionally be on the super chain,
    // if the inline "_primary_supers" array overflows.
    int extras = 0;
    Klass* p;
    for (p = super(); !(p == nullptr || p->can_be_primary_super()); p = p->super()) {
      ++extras;
    }

    ResourceMark rm(THREAD);  // need to reclaim GrowableArrays allocated below

    // Compute the "real" non-extra secondaries.
    GrowableArray<Klass*>* secondaries = compute_secondary_supers(extras, transitive_interfaces);
    if (secondaries == nullptr) {
      // secondary_supers set by compute_secondary_supers
      return;
    }

    GrowableArray<Klass*>* primaries = new GrowableArray<Klass*>(extras);

    for (p = super(); !(p == nullptr || p->can_be_primary_super()); p = p->super()) {
      int i;                    // Scan for overflow primaries being duplicates of 2nd'arys

      // This happens frequently for very deeply nested arrays: the
      // primary superclass chain overflows into the secondary.  The
      // secondary list contains the element_klass's secondaries with
      // an extra array dimension added.  If the element_klass's
      // secondary list already contains some primary overflows, they
      // (with the extra level of array-ness) will collide with the
      // normal primary superclass overflows.
      for( i = 0; i < secondaries->length(); i++ ) {
        if( secondaries->at(i) == p )
          break;
      }
      if( i < secondaries->length() )
        continue;               // It's a dup, don't put it in
      primaries->push(p);
    }
    // Combine the two arrays into a metadata object to pack the array.
    uintx bitmap = 0;
    Array<Klass*>* s2 = pack_secondary_supers(class_loader_data(), primaries, secondaries, bitmap, CHECK);
    set_secondary_supers(s2, bitmap);
  }
}

GrowableArray<Klass*>* Klass::compute_secondary_supers(int num_extra_slots,
                                                       Array<InstanceKlass*>* transitive_interfaces) {
  assert(num_extra_slots == 0, "override for complex klasses");
  assert(transitive_interfaces == nullptr, "sanity");
  set_secondary_supers(Universe::the_empty_klass_array(), Universe::the_empty_klass_bitmap());
  return nullptr;
}


// superklass links
InstanceKlass* Klass::superklass() const {
  assert(super() == nullptr || super()->is_instance_klass(), "must be instance klass");
  return _super == nullptr ? nullptr : InstanceKlass::cast(_super);
}

// subklass links.  Used by the compiler (and vtable initialization)
// May be cleaned concurrently, so must use the Compile_lock.
// The log parameter is for clean_weak_klass_links to report unlinked classes.
Klass* Klass::subklass(bool log) const {
  // Need load_acquire on the _subklass, because it races with inserts that
  // publishes freshly initialized data.
  for (Klass* chain = Atomic::load_acquire(&_subklass);
       chain != nullptr;
       // Do not need load_acquire on _next_sibling, because inserts never
       // create _next_sibling edges to dead data.
       chain = Atomic::load(&chain->_next_sibling))
  {
    if (chain->is_loader_alive()) {
      return chain;
    } else if (log) {
      if (log_is_enabled(Trace, class, unload)) {
        ResourceMark rm;
        log_trace(class, unload)("unlinking class (subclass): %s", chain->external_name());
      }
    }
  }
  return nullptr;
}

Klass* Klass::next_sibling(bool log) const {
  // Do not need load_acquire on _next_sibling, because inserts never
  // create _next_sibling edges to dead data.
  for (Klass* chain = Atomic::load(&_next_sibling);
       chain != nullptr;
       chain = Atomic::load(&chain->_next_sibling)) {
    // Only return alive klass, there may be stale klass
    // in this chain if cleaned concurrently.
    if (chain->is_loader_alive()) {
      return chain;
    } else if (log) {
      if (log_is_enabled(Trace, class, unload)) {
        ResourceMark rm;
        log_trace(class, unload)("unlinking class (sibling): %s", chain->external_name());
      }
    }
  }
  return nullptr;
}

void Klass::set_subklass(Klass* s) {
  assert(s != this, "sanity check");
  Atomic::release_store(&_subklass, s);
}

void Klass::set_next_sibling(Klass* s) {
  assert(s != this, "sanity check");
  // Does not need release semantics. If used by cleanup, it will link to
  // already safely published data, and if used by inserts, will be published
  // safely using cmpxchg.
  Atomic::store(&_next_sibling, s);
}

void Klass::append_to_sibling_list() {
  if (Universe::is_fully_initialized()) {
    assert_locked_or_safepoint(Compile_lock);
  }
  DEBUG_ONLY(verify();)
  // add ourselves to superklass' subklass list
  InstanceKlass* super = superklass();
  if (super == nullptr) return;     // special case: class Object
  assert((!super->is_interface()    // interfaces cannot be supers
          && (super->superklass() == nullptr || !is_interface())),
         "an interface can only be a subklass of Object");

  // Make sure there is no stale subklass head
  super->clean_subklass();

  for (;;) {
    Klass* prev_first_subklass = Atomic::load_acquire(&_super->_subklass);
    if (prev_first_subklass != nullptr) {
      // set our sibling to be the superklass' previous first subklass
      assert(prev_first_subklass->is_loader_alive(), "May not attach not alive klasses");
      set_next_sibling(prev_first_subklass);
    }
    // Note that the prev_first_subklass is always alive, meaning no sibling_next links
    // are ever created to not alive klasses. This is an important invariant of the lock-free
    // cleaning protocol, that allows us to safely unlink dead klasses from the sibling list.
    if (Atomic::cmpxchg(&super->_subklass, prev_first_subklass, this) == prev_first_subklass) {
      return;
    }
  }
  DEBUG_ONLY(verify();)
}

void Klass::clean_subklass() {
  for (;;) {
    // Need load_acquire, due to contending with concurrent inserts
    Klass* subklass = Atomic::load_acquire(&_subklass);
    if (subklass == nullptr || subklass->is_loader_alive()) {
      return;
    }
    // Try to fix _subklass until it points at something not dead.
    Atomic::cmpxchg(&_subklass, subklass, subklass->next_sibling());
  }
}

void Klass::clean_weak_klass_links(bool unloading_occurred, bool clean_alive_klasses) {
  if (!ClassUnloading || !unloading_occurred) {
    return;
  }

  Klass* root = vmClasses::Object_klass();
  Stack<Klass*, mtGC> stack;

  stack.push(root);
  while (!stack.is_empty()) {
    Klass* current = stack.pop();

    assert(current->is_loader_alive(), "just checking, this should be live");

    // Find and set the first alive subklass
    Klass* sub = current->subklass(true);
    current->clean_subklass();
    if (sub != nullptr) {
      stack.push(sub);
    }

    // Find and set the first alive sibling
    Klass* sibling = current->next_sibling(true);
    current->set_next_sibling(sibling);
    if (sibling != nullptr) {
      stack.push(sibling);
    }

    // Clean the implementors list and method data.
    if (clean_alive_klasses && current->is_instance_klass()) {
      InstanceKlass* ik = InstanceKlass::cast(current);
      clean_weak_instanceklass_links(ik);
    }
  }
}

void Klass::clean_weak_instanceklass_links(InstanceKlass* ik) {
  ik->clean_weak_instanceklass_links();
  // JVMTI RedefineClasses creates previous versions that are not in
  // the class hierarchy, so process them here.
  while ((ik = ik->previous_versions()) != nullptr) {
    ik->clean_weak_instanceklass_links();
  }
}

void Klass::metaspace_pointers_do(MetaspaceClosure* it) {
  if (log_is_enabled(Trace, aot)) {
    ResourceMark rm;
    log_trace(aot)("Iter(Klass): %p (%s)", this, external_name());
  }

  it->push(&_name);
  it->push(&_secondary_supers);
  for (int i = 0; i < _primary_super_limit; i++) {
    it->push(&_primary_supers[i]);
  }
  it->push(&_super);
  if (!CDSConfig::is_dumping_archive()) {
    // If dumping archive, these may point to excluded classes. There's no need
    // to follow these pointers anyway, as they will be set to null in
    // remove_unshareable_info().
    it->push((Klass**)&_subklass);
    it->push((Klass**)&_next_sibling);
    it->push(&_next_link);
  }

  vtableEntry* vt = start_of_vtable();
  for (int i=0; i<vtable_length(); i++) {
    it->push(vt[i].method_addr());
  }
}

#if INCLUDE_CDS
void Klass::remove_unshareable_info() {
  assert(CDSConfig::is_dumping_archive(),
          "only called during CDS dump time");
  JFR_ONLY(REMOVE_ID(this);)
  if (log_is_enabled(Trace, aot, unshareable)) {
    ResourceMark rm;
    log_trace(aot, unshareable)("remove: %s", external_name());
  }

  // _secondary_super_cache may be updated by an is_subtype_of() call
  // while ArchiveBuilder is copying metaspace objects. Let's reset it to
  // null and let it be repopulated at runtime.
  set_secondary_super_cache(nullptr);

  set_subklass(nullptr);
  set_next_sibling(nullptr);
  set_next_link(nullptr);

  // Null out class_loader_data because we don't share that yet.
  set_class_loader_data(nullptr);
  set_is_shared();

  if (CDSConfig::is_dumping_classic_static_archive()) {
    // "Classic" static archives are required to have deterministic contents.
    // The elements in _secondary_supers are addresses in the ArchiveBuilder
    // output buffer, so they should have deterministic values. If we rehash
    // _secondary_supers, its elements will appear in a deterministic order.
    //
    // Note that the bitmap is guaranteed to be deterministic, regardless of the
    // actual addresses of the elements in _secondary_supers. So rehashing shouldn't
    // change it.
    uintx bitmap = hash_secondary_supers(secondary_supers(), true);
    assert(bitmap == _secondary_supers_bitmap, "bitmap should not be changed due to rehashing");
  }
}

void Klass::remove_java_mirror() {
  assert(CDSConfig::is_dumping_archive(), "sanity");
  if (log_is_enabled(Trace, aot, unshareable)) {
    ResourceMark rm;
    log_trace(aot, unshareable)("remove java_mirror: %s", external_name());
  }

#if INCLUDE_CDS_JAVA_HEAP
  _archived_mirror_index = -1;
  if (CDSConfig::is_dumping_heap()) {
    Klass* src_k = ArchiveBuilder::current()->get_source_addr(this);
    oop orig_mirror = src_k->java_mirror();
    if (orig_mirror == nullptr) {
      assert(CDSConfig::is_dumping_final_static_archive(), "sanity");
      if (is_instance_klass()) {
        assert(InstanceKlass::cast(this)->defined_by_other_loaders(), "sanity");
      } else {
        precond(is_objArray_klass());
        Klass *k = ObjArrayKlass::cast(this)->bottom_klass();
        precond(k->is_instance_klass());
        assert(InstanceKlass::cast(k)->defined_by_other_loaders(), "sanity");
      }
    } else {
      oop scratch_mirror = HeapShared::scratch_java_mirror(orig_mirror);
      if (scratch_mirror != nullptr) {
        _archived_mirror_index = HeapShared::append_root(scratch_mirror);
      }
    }
  }
#endif

  // Just null out the mirror.  The class_loader_data() no longer exists.
  clear_java_mirror_handle();
}

void Klass::restore_unshareable_info(ClassLoaderData* loader_data, Handle protection_domain, TRAPS) {
  assert(is_klass(), "ensure C++ vtable is restored");
  assert(is_shared(), "must be set");
  assert(secondary_supers()->length() >= (int)population_count(_secondary_supers_bitmap), "must be");
  JFR_ONLY(RESTORE_ID(this);)
  if (log_is_enabled(Trace, aot, unshareable)) {
    ResourceMark rm(THREAD);
    oop class_loader = loader_data->class_loader();
    log_trace(aot, unshareable)("restore: %s with class loader: %s", external_name(),
      class_loader != nullptr ? class_loader->klass()->external_name() : "boot");
  }

  // If an exception happened during CDS restore, some of these fields may already be
  // set.  We leave the class on the CLD list, even if incomplete so that we don't
  // modify the CLD list outside a safepoint.
  if (class_loader_data() == nullptr) {
    set_class_loader_data(loader_data);

    // Add to class loader list first before creating the mirror
    // (same order as class file parsing)
    loader_data->add_class(this);
  }

  Handle loader(THREAD, loader_data->class_loader());
  ModuleEntry* module_entry = nullptr;
  Klass* k = this;
  if (k->is_objArray_klass()) {
    k = ObjArrayKlass::cast(k)->bottom_klass();
  }
  // Obtain klass' module.
  if (k->is_instance_klass()) {
    InstanceKlass* ik = (InstanceKlass*) k;
    module_entry = ik->module();
  } else {
    module_entry = ModuleEntryTable::javabase_moduleEntry();
  }
  // Obtain java.lang.Module, if available
  Handle module_handle(THREAD, ((module_entry != nullptr) ? module_entry->module_oop() : (oop)nullptr));

  if (this->has_archived_mirror_index()) {
    ResourceMark rm(THREAD);
    log_debug(aot, mirror)("%s has raw archived mirror", external_name());
    if (ArchiveHeapLoader::is_in_use()) {
      bool present = java_lang_Class::restore_archived_mirror(this, loader, module_handle,
                                                              protection_domain,
                                                              CHECK);
      if (present) {
        return;
      }
    }

    // No archived mirror data
    log_debug(aot, mirror)("No archived mirror data for %s", external_name());
    clear_java_mirror_handle();
    this->clear_archived_mirror_index();
  }

  // Only recreate it if not present.  A previous attempt to restore may have
  // gotten an OOM later but keep the mirror if it was created.
  if (java_mirror() == nullptr) {
    ResourceMark rm(THREAD);
    log_trace(aot, mirror)("Recreate mirror for %s", external_name());
    java_lang_Class::create_mirror(this, loader, module_handle, protection_domain, Handle(), CHECK);
  }
}
#endif // INCLUDE_CDS

#if INCLUDE_CDS_JAVA_HEAP
oop Klass::archived_java_mirror() {
  assert(has_archived_mirror_index(), "must have archived mirror");
  return HeapShared::get_root(_archived_mirror_index);
}

void Klass::clear_archived_mirror_index() {
  if (_archived_mirror_index >= 0) {
    HeapShared::clear_root(_archived_mirror_index);
  }
  _archived_mirror_index = -1;
}
#endif // INCLUDE_CDS_JAVA_HEAP

void Klass::check_array_allocation_length(int length, int max_length, TRAPS) {
  if (length > max_length) {
    if (!THREAD->is_in_internal_oome_mark()) {
      report_java_out_of_memory("Requested array size exceeds VM limit");
      JvmtiExport::post_array_size_exhausted();
      THROW_OOP(Universe::out_of_memory_error_array_size());
    } else {
      THROW_OOP(Universe::out_of_memory_error_java_heap_without_backtrace());
    }
  } else if (length < 0) {
    THROW_MSG(vmSymbols::java_lang_NegativeArraySizeException(), err_msg("%d", length));
  }
}

// Replace the last '+' char with '/'.
static char* convert_hidden_name_to_java(Symbol* name) {
  size_t name_len = name->utf8_length();
  char* result = NEW_RESOURCE_ARRAY(char, name_len + 1);
  name->as_klass_external_name(result, (int)name_len + 1);
  for (int index = (int)name_len; index > 0; index--) {
    if (result[index] == '+') {
      result[index] = JVM_SIGNATURE_SLASH;
      break;
    }
  }
  return result;
}

// In product mode, this function doesn't have virtual function calls so
// there might be some performance advantage to handling InstanceKlass here.
const char* Klass::external_name() const {
  if (is_instance_klass()) {
    const InstanceKlass* ik = static_cast<const InstanceKlass*>(this);
    if (ik->is_hidden()) {
      char* result = convert_hidden_name_to_java(name());
      return result;
    }
  } else if (is_objArray_klass() && ObjArrayKlass::cast(this)->bottom_klass()->is_hidden()) {
    char* result = convert_hidden_name_to_java(name());
    return result;
  }
  if (name() == nullptr)  return "<unknown>";
  return name()->as_klass_external_name();
}

const char* Klass::signature_name() const {
  if (name() == nullptr)  return "<unknown>";
  if (is_objArray_klass() && ObjArrayKlass::cast(this)->bottom_klass()->is_hidden()) {
    size_t name_len = name()->utf8_length();
    char* result = NEW_RESOURCE_ARRAY(char, name_len + 1);
    name()->as_C_string(result, (int)name_len + 1);
    for (int index = (int)name_len; index > 0; index--) {
      if (result[index] == '+') {
        result[index] = JVM_SIGNATURE_DOT;
        break;
      }
    }
    return result;
  }
  return name()->as_C_string();
}

const char* Klass::external_kind() const {
  if (is_interface()) return "interface";
  if (is_abstract()) return "abstract class";
  return "class";
}

// Unless overridden, jvmti_class_status has no flags set.
jint Klass::jvmti_class_status() const {
  return 0;
}


// Printing

void Klass::print_on(outputStream* st) const {
  ResourceMark rm;
  // print title
  st->print("%s", internal_name());
  print_address_on(st);
  st->cr();
}

#define BULLET  " - "

// Caller needs ResourceMark
void Klass::oop_print_on(oop obj, outputStream* st) {
  // print title
  st->print_cr("%s ", internal_name());
  obj->print_address_on(st);

  if (WizardMode) {
     // print header
     obj->mark().print_on(st);
     st->cr();
     if (UseCompactObjectHeaders) {
       st->print(BULLET"prototype_header: " INTPTR_FORMAT, _prototype_header.value());
       st->cr();
     }
  }

  // print class
  st->print(BULLET"klass: ");
  obj->klass()->print_value_on(st);
  st->print(BULLET"flags: "); _misc_flags.print_on(st); st->cr();
  st->cr();
}

void Klass::oop_print_value_on(oop obj, outputStream* st) {
  // print title
  ResourceMark rm;              // Cannot print in debug mode without this
  st->print("%s", internal_name());
  obj->print_address_on(st);
}

// Verification

void Klass::verify_on(outputStream* st) {

  // This can be expensive, but it is worth checking that this klass is actually
  // in the CLD graph but not in production.
#ifdef ASSERT
  if (UseCompressedClassPointers && needs_narrow_id()) {
    // Stricter checks for both correct alignment and placement
    CompressedKlassPointers::check_encodable(this);
  } else {
    assert(Metaspace::contains((address)this), "Should be");
  }
#endif // ASSERT

  guarantee(this->is_klass(),"should be klass");

  if (super() != nullptr) {
    guarantee(super()->is_klass(), "should be klass");
  }
  if (secondary_super_cache() != nullptr) {
    Klass* ko = secondary_super_cache();
    guarantee(ko->is_klass(), "should be klass");
  }
  for ( uint i = 0; i < primary_super_limit(); i++ ) {
    Klass* ko = _primary_supers[i];
    if (ko != nullptr) {
      guarantee(ko->is_klass(), "should be klass");
    }
  }

  if (java_mirror_no_keepalive() != nullptr) {
    guarantee(java_lang_Class::is_instance(java_mirror_no_keepalive()), "should be instance");
  }
}

void Klass::oop_verify_on(oop obj, outputStream* st) {
  guarantee(oopDesc::is_oop(obj),  "should be oop");
  guarantee(obj->klass()->is_klass(), "klass field is not a klass");
}

// Note: this function is called with an address that may or may not be a Klass.
// The point is not to assert it is but to check if it could be.
bool Klass::is_valid(Klass* k) {
  if (!is_aligned(k, sizeof(MetaWord))) return false;
  if ((size_t)k < os::min_page_size()) return false;

  if (!os::is_readable_range(k, k + 1)) return false;
  if (!Metaspace::contains(k)) return false;

  if (!Symbol::is_valid(k->name())) return false;
  return ClassLoaderDataGraph::is_valid(k->class_loader_data());
}

Method* Klass::method_at_vtable(int index)  {
#ifndef PRODUCT
  assert(index >= 0, "valid vtable index");
  if (DebugVtables) {
    verify_vtable_index(index);
  }
#endif
  return start_of_vtable()[index].method();
}


#ifndef PRODUCT

bool Klass::verify_vtable_index(int i) {
  int limit = vtable_length()/vtableEntry::size();
  assert(i >= 0 && i < limit, "index %d out of bounds %d", i, limit);
  return true;
}

#endif // PRODUCT

// Caller needs ResourceMark
// joint_in_module_of_loader provides an optimization if 2 classes are in
// the same module to succinctly print out relevant information about their
// module name and class loader's name_and_id for error messages.
// Format:
//   <fully-qualified-external-class-name1> and <fully-qualified-external-class-name2>
//                      are in module <module-name>[@<version>]
//                      of loader <loader-name_and_id>[, parent loader <parent-loader-name_and_id>]
const char* Klass::joint_in_module_of_loader(const Klass* class2, bool include_parent_loader) const {
  assert(module() == class2->module(), "classes do not have the same module");
  const char* class1_name = external_name();
  size_t len = strlen(class1_name) + 1;

  const char* class2_description = class2->class_in_module_of_loader(true, include_parent_loader);
  len += strlen(class2_description);

  len += strlen(" and ");

  char* joint_description = NEW_RESOURCE_ARRAY_RETURN_NULL(char, len);

  // Just return the FQN if error when allocating string
  if (joint_description == nullptr) {
    return class1_name;
  }

  jio_snprintf(joint_description, len, "%s and %s",
               class1_name,
               class2_description);

  return joint_description;
}

// Caller needs ResourceMark
// class_in_module_of_loader provides a standard way to include
// relevant information about a class, such as its module name as
// well as its class loader's name_and_id, in error messages and logging.
// Format:
//   <fully-qualified-external-class-name> is in module <module-name>[@<version>]
//                                         of loader <loader-name_and_id>[, parent loader <parent-loader-name_and_id>]
const char* Klass::class_in_module_of_loader(bool use_are, bool include_parent_loader) const {
  // 1. fully qualified external name of class
  const char* klass_name = external_name();
  size_t len = strlen(klass_name) + 1;

  // 2. module name + @version
  const char* module_name = "";
  const char* version = "";
  bool has_version = false;
  bool module_is_named = false;
  const char* module_name_phrase = "";
  const Klass* bottom_klass = is_objArray_klass() ?
                                ObjArrayKlass::cast(this)->bottom_klass() : this;
  if (bottom_klass->is_instance_klass()) {
    ModuleEntry* module = InstanceKlass::cast(bottom_klass)->module();
    if (module->is_named()) {
      module_is_named = true;
      module_name_phrase = "module ";
      module_name = module->name()->as_C_string();
      len += strlen(module_name);
      // Use version if exists and is not a jdk module
      if (module->should_show_version()) {
        has_version = true;
        version = module->version()->as_C_string();
        // Include stlen(version) + 1 for the "@"
        len += strlen(version) + 1;
      }
    } else {
      module_name = UNNAMED_MODULE;
      len += UNNAMED_MODULE_LEN;
    }
  } else {
    // klass is an array of primitives, module is java.base
    module_is_named = true;
    module_name_phrase = "module ";
    module_name = JAVA_BASE_NAME;
    len += JAVA_BASE_NAME_LEN;
  }

  // 3. class loader's name_and_id
  ClassLoaderData* cld = class_loader_data();
  assert(cld != nullptr, "class_loader_data should not be null");
  const char* loader_name_and_id = cld->loader_name_and_id();
  len += strlen(loader_name_and_id);

  // 4. include parent loader information
  const char* parent_loader_phrase = "";
  const char* parent_loader_name_and_id = "";
  if (include_parent_loader &&
      !cld->is_builtin_class_loader_data()) {
    oop parent_loader = java_lang_ClassLoader::parent(class_loader());
    ClassLoaderData *parent_cld = ClassLoaderData::class_loader_data_or_null(parent_loader);
    // The parent loader's ClassLoaderData could be null if it is
    // a delegating class loader that has never defined a class.
    // In this case the loader's name must be obtained via the parent loader's oop.
    if (parent_cld == nullptr) {
      oop cl_name_and_id = java_lang_ClassLoader::nameAndId(parent_loader);
      if (cl_name_and_id != nullptr) {
        parent_loader_name_and_id = java_lang_String::as_utf8_string(cl_name_and_id);
      }
    } else {
      parent_loader_name_and_id = parent_cld->loader_name_and_id();
    }
    parent_loader_phrase = ", parent loader ";
    len += strlen(parent_loader_phrase) + strlen(parent_loader_name_and_id);
  }

  // Start to construct final full class description string
  len += ((use_are) ? strlen(" are in ") : strlen(" is in "));
  len += strlen(module_name_phrase) + strlen(" of loader ");

  char* class_description = NEW_RESOURCE_ARRAY_RETURN_NULL(char, len);

  // Just return the FQN if error when allocating string
  if (class_description == nullptr) {
    return klass_name;
  }

  jio_snprintf(class_description, len, "%s %s in %s%s%s%s of loader %s%s%s",
               klass_name,
               (use_are) ? "are" : "is",
               module_name_phrase,
               module_name,
               (has_version) ? "@" : "",
               (has_version) ? version : "",
               loader_name_and_id,
               parent_loader_phrase,
               parent_loader_name_and_id);

  return class_description;
}

class LookupStats : StackObj {
 private:
  uint _no_of_samples;
  uint _worst;
  uint _worst_count;
  uint _average;
  uint _best;
  uint _best_count;
 public:
  LookupStats() : _no_of_samples(0), _worst(0), _worst_count(0), _average(0), _best(INT_MAX), _best_count(0) {}

  ~LookupStats() {
    assert(_best <= _worst || _no_of_samples == 0, "sanity");
  }

  void sample(uint value) {
    ++_no_of_samples;
    _average += value;

    if (_worst < value) {
      _worst = value;
      _worst_count = 1;
    } else if (_worst == value) {
      ++_worst_count;
    }

    if (_best > value) {
      _best = value;
      _best_count = 1;
    } else if (_best == value) {
      ++_best_count;
    }
  }

  void print_on(outputStream* st) const {
    st->print("best: %2d (%4.1f%%)", _best, (100.0 * _best_count) / _no_of_samples);
    if (_best_count < _no_of_samples) {
      st->print("; average: %4.1f; worst: %2d (%4.1f%%)",
                (1.0 * _average) / _no_of_samples,
                _worst, (100.0 * _worst_count) / _no_of_samples);
    }
  }
};

static void print_positive_lookup_stats(Array<Klass*>* secondary_supers, uintx bitmap, outputStream* st) {
  int num_of_supers = secondary_supers->length();

  LookupStats s;
  for (int i = 0; i < num_of_supers; i++) {
    Klass* secondary_super = secondary_supers->at(i);
    int home_slot = Klass::compute_home_slot(secondary_super, bitmap);
    uint score = 1 + ((i - home_slot) & Klass::SECONDARY_SUPERS_TABLE_MASK);
    s.sample(score);
  }
  st->print("positive_lookup: "); s.print_on(st);
}

static uint compute_distance_to_nearest_zero(int slot, uintx bitmap) {
  assert(~bitmap != 0, "no zeroes");
  uintx start = rotate_right(bitmap, slot);
  return count_trailing_zeros(~start);
}

static void print_negative_lookup_stats(uintx bitmap, outputStream* st) {
  LookupStats s;
  for (int slot = 0; slot < Klass::SECONDARY_SUPERS_TABLE_SIZE; slot++) {
    uint score = compute_distance_to_nearest_zero(slot, bitmap);
    s.sample(score);
  }
  st->print("negative_lookup: "); s.print_on(st);
}

void Klass::print_secondary_supers_on(outputStream* st) const {
  if (secondary_supers() != nullptr) {
    st->print("  - "); st->print("%d elements;", _secondary_supers->length());
    st->print_cr(" bitmap: " UINTX_FORMAT_X_0, _secondary_supers_bitmap);
    if (_secondary_supers_bitmap != SECONDARY_SUPERS_BITMAP_EMPTY &&
        _secondary_supers_bitmap != SECONDARY_SUPERS_BITMAP_FULL) {
      st->print("  - "); print_positive_lookup_stats(secondary_supers(),
                                                     _secondary_supers_bitmap, st); st->cr();
      st->print("  - "); print_negative_lookup_stats(_secondary_supers_bitmap, st); st->cr();
    }
  } else {
    st->print("null");
  }
}

void Klass::on_secondary_supers_verification_failure(Klass* super, Klass* sub, bool linear_result, bool table_result, const char* msg) {
  ResourceMark rm;
  super->print();
  sub->print();
  fatal("%s: %s implements %s: linear_search: %d; table_lookup: %d",
        msg, sub->external_name(), super->external_name(), linear_result, table_result);
}
