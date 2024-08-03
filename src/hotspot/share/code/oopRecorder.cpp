/*
 * Copyright (c) 1998, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "ci/ciEnv.hpp"
#include "ci/ciInstance.hpp"
#include "ci/ciMetadata.hpp"
#include "code/oopRecorder.inline.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "memory/allocation.inline.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/jniHandles.inline.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/os.hpp"
#include "utilities/copy.hpp"

#ifdef ASSERT
template <class T> int ValueRecorder<T>::_find_index_calls = 0;
template <class T> int ValueRecorder<T>::_hit_indexes      = 0;
template <class T> int ValueRecorder<T>::_missed_indexes   = 0;
#endif //ASSERT


template <class T> ValueRecorder<T>::ValueRecorder(Arena* arena) {
  _handles  = nullptr;
  _indexes  = nullptr;
  _arena    = arena;
  _complete = false;
}

template <class T> template <class X>  ValueRecorder<T>::IndexCache<X>::IndexCache() {
  assert(first_index > 0, "initial zero state of cache must be invalid index");
  Copy::zero_to_bytes(&_cache[0], sizeof(_cache));
}

template <class T> int ValueRecorder<T>::size() {
  _complete = true;
  if (_handles == nullptr)  return 0;
  return _handles->length() * sizeof(T);
}

template <class T> void ValueRecorder<T>::copy_values_to(nmethod* nm) {
  assert(_complete, "must be frozen");
  maybe_initialize();  // get non-null handles, even if we have no oops
  nm->copy_values(_handles);
}

template <class T> void ValueRecorder<T>::maybe_initialize() {
  if (_handles == nullptr) {
    if (_arena != nullptr) {
      _handles  = new(_arena) GrowableArray<T>(_arena, 10, 0, T{});
      _no_finds = new(_arena) GrowableArray<int>(_arena, 10, 0, 0);
    } else {
      _handles  = new GrowableArray<T>(10, 0, T{});
      _no_finds = new GrowableArray<int>(10, 0, 0);
    }
  }
}


template <class T> T ValueRecorder<T>::at(int index) {
  // there is always a nullptr virtually present as first object
  if (index == null_index)  return nullptr;
  return _handles->at(index - first_index);
}


template <class T> int ValueRecorder<T>::add_handle(T h, bool make_findable) {
  assert(!_complete, "cannot allocate more elements after size query");
  maybe_initialize();
  // indexing uses 1 as an origin--0 means null
  int index = _handles->length() + first_index;
  _handles->append(h);

  // Support correct operation of find_index().
  assert(!(make_findable && !is_real(h)), "nulls are not findable");
  if (make_findable) {
    // This index may be returned from find_index().
    if (_indexes != nullptr) {
      int* cloc = _indexes->cache_location(h);
      _indexes->set_cache_location_index(cloc, index);
    } else if (index == index_cache_threshold && _arena != nullptr) {
      _indexes = new(_arena) IndexCache<T>();
      for (int i = 0; i < _handles->length(); i++) {
        // Load the cache with pre-existing elements.
        int index0 = i + first_index;
        if (_no_finds->contains(index0))  continue;
        int* cloc = _indexes->cache_location(_handles->at(i));
        _indexes->set_cache_location_index(cloc, index0);
      }
    }
  } else if (is_real(h)) {
    // Remember that this index is not to be returned from find_index().
    // This case is rare, because most or all uses of allocate_index pass
    // an argument of nullptr or Universe::non_oop_word.
    // Thus, the expected length of _no_finds is zero.
    _no_finds->append(index);
  }

  return index;
}


template <class T> int ValueRecorder<T>::maybe_find_index(T h) {
  debug_only(_find_index_calls++);
  assert(!_complete, "cannot allocate more elements after size query");
  maybe_initialize();
  if (h == nullptr)  return null_index;
  assert(is_real(h), "must be valid");
  int* cloc = (_indexes == nullptr)? nullptr: _indexes->cache_location(h);
  if (cloc != nullptr) {
    int cindex = _indexes->cache_location_index(cloc);
    if (cindex == 0) {
      return -1;   // We know this handle is completely new.
    }
    if (cindex >= first_index && _handles->at(cindex - first_index) == h) {
      debug_only(_hit_indexes++);
      return cindex;
    }
    if (!_indexes->cache_location_collision(cloc)) {
      return -1;   // We know the current cache occupant is unique to that cloc.
    }
  }

  // Not found in cache, due to a cache collision.  (Or, no cache at all.)
  // Do a linear search, most recent to oldest.
  for (int i = _handles->length() - 1; i >= 0; i--) {
    if (_handles->at(i) == h) {
      int findex = i + first_index;
      if (_no_finds->contains(findex))  continue;  // oops; skip this one
      if (cloc != nullptr) {
        _indexes->set_cache_location_index(cloc, findex);
      }
      debug_only(_missed_indexes++);
      return findex;
    }
  }
  return -1;
}

// Explicitly instantiate these types
template class ValueRecorder<Metadata*>;
template class ValueRecorder<jobject>;

oop ObjectLookup::ObjectEntry::oop_value() const { return JNIHandles::resolve(_value); }

ObjectLookup::ObjectLookup(): _values(4), _gc_count(Universe::heap()->total_collections()) {}

void ObjectLookup::maybe_resort() {
  // The values are kept sorted by address which may be invalidated
  // after a GC, so resort if a GC has occurred since last time.
  if (_gc_count != Universe::heap()->total_collections()) {
    _gc_count = Universe::heap()->total_collections();
    _values.sort(sort_by_address);
  }
}

int ObjectLookup::sort_by_address(oop a, oop b) {
  // oopDesc::compare returns the opposite of what this function returned
  return -(oopDesc::compare(a, b));
}

int ObjectLookup::sort_by_address(ObjectEntry* a, ObjectEntry* b) {
  return sort_by_address(a->oop_value(), b->oop_value());
}

int ObjectLookup::sort_oop_by_address(oop const& a, ObjectEntry const& b) {
  return sort_by_address(a, b.oop_value());
}

int ObjectLookup::find_index(jobject handle, OopRecorder* oop_recorder) {
  if (handle == nullptr) {
    return 0;
  }
  oop object = JNIHandles::resolve(handle);
  maybe_resort();
  bool found;
  int location = _values.find_sorted<oop, sort_oop_by_address>(object, found);
  if (!found) {
    jobject handle = JNIHandles::make_local(object);
    ObjectEntry r(handle, oop_recorder->allocate_oop_index(handle));
    _values.insert_before(location, r);
    return r.index();
  }
  return _values.at(location).index();
}

OopRecorder::OopRecorder(Arena* arena, bool deduplicate): _oops(arena), _metadata(arena) {
  if (deduplicate) {
    _object_lookup = new ObjectLookup();
  } else {
    _object_lookup = nullptr;
  }
}

// Explicitly instantiate
template class ValueRecorder<address>;

ExternalsRecorder* ExternalsRecorder::_recorder = nullptr;

ExternalsRecorder::ExternalsRecorder(): _arena(mtCode), _externals(&_arena) {}

#ifndef PRODUCT
static int total_access_count = 0;
static GrowableArray<int>* extern_hist = nullptr;
#endif

void ExternalsRecorder_init() {
  ExternalsRecorder::initialize();
}

void ExternalsRecorder::initialize() {
  // After Mutex and before CodeCache are initialized
  assert(_recorder == nullptr, "should initialize only once");
  _recorder = new ExternalsRecorder();
#ifndef PRODUCT
  if (PrintNMethodStatistics) {
    Arena* arena = &_recorder->_arena;
    extern_hist = new(arena) GrowableArray<int>(arena, 512, 512, 0);
  }
#endif
}

int ExternalsRecorder::find_index(address adr) {
  assert(_recorder != nullptr, "sanity");
  MutexLocker ml(ExternalsRecorder_lock, Mutex::_no_safepoint_check_flag);
  int index = _recorder->_externals.find_index(adr);
#ifndef PRODUCT
  if (PrintNMethodStatistics) {
    total_access_count++;
    int n = extern_hist->at_grow(index, 0);
    extern_hist->at_put(index, (n + 1));
  }
#endif
  return index;
}

address ExternalsRecorder::at(int index) {
  assert(_recorder != nullptr, "sanity");
  // find_index() may resize array by reallocating it and freeing old,
  // we need loock here to make sure we not accessing to old freed array.
  MutexLocker ml(ExternalsRecorder_lock, Mutex::_no_safepoint_check_flag);
  return _recorder->_externals.at(index);
}

int ExternalsRecorder::count() {
  assert(_recorder != nullptr, "sanity");
  MutexLocker ml(ExternalsRecorder_lock, Mutex::_no_safepoint_check_flag);
  return _recorder->_externals.count();
}

#ifndef PRODUCT
extern "C" {
  // Order from large to small values
  static int count_cmp(const void *i, const void *j) {
    int a = *(int*)i;
    int b = *(int*)j;
    return a < b ? 1 : a > b ? -1 : 0;
  }
}

void ExternalsRecorder::print_statistics() {
  int cnt = count();
  tty->print_cr("External addresses table: %d entries, %d accesses", cnt, total_access_count);
  { // Print most accessed entries in the table.
    int* array = NEW_C_HEAP_ARRAY(int, (2 * cnt), mtCode);
    for (int i = 0; i < cnt; i++) {
      array[(2 * i) + 0] = extern_hist->at(i);
      array[(2 * i) + 1] = i;
    }
    // Reverse sort to have "hottest" addresses first.
    qsort(array, cnt, 2*sizeof(int), count_cmp);
    // Print all entries with Verbose flag otherwise only top 5.
    int limit = (Verbose || cnt <= 5) ? cnt : 5;
    int j = 0;
    for (int i = 0; i < limit; i++) {
      int index = array[(2 * i) + 1];
      int n = extern_hist->at(index);
      if (n > 0) {
        address addr = at(index);
        tty->print("%d: %8d " INTPTR_FORMAT " :", j++, n, p2i(addr));
        if (addr != nullptr) {
          if (StubRoutines::contains(addr)) {
            StubCodeDesc* desc = StubCodeDesc::desc_for(addr);
            if (desc == nullptr) {
              desc = StubCodeDesc::desc_for(addr + frame::pc_return_offset);
            }
            const char* stub_name = (desc != nullptr) ? desc->name() : "<unknown>";
            tty->print(" stub: %s", stub_name);
          } else {
            ResourceMark rm;
            const int buflen = 1024;
            char* buf = NEW_RESOURCE_ARRAY(char, buflen);
            int offset = 0;
            if (os::dll_address_to_function_name(addr, buf, buflen, &offset)) {
              tty->print(" extn: %s", buf);
              if (offset != 0) {
                tty->print("+%d", offset);
              }
            } else {
              if (CodeCache::contains((void*)addr)) {
                // Something in CodeCache
                tty->print(" in CodeCache");
              } else {
                // It could be string
                memcpy(buf, (char*)addr, 80);
                buf[80] = '\0';
                tty->print(" '%s'", buf);
              }
            }
          }
        }
        tty->cr();
      }
    }
  }
}
#endif
