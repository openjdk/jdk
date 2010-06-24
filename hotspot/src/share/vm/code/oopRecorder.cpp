/*
 * Copyright (c) 1998, 2007, Oracle and/or its affiliates. All rights reserved.
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
# include "incls/_oopRecorder.cpp.incl"

#ifdef ASSERT
int OopRecorder::_find_index_calls = 0;
int OopRecorder::_hit_indexes      = 0;
int OopRecorder::_missed_indexes   = 0;
#endif //ASSERT


OopRecorder::OopRecorder(Arena* arena) {
  _handles  = NULL;
  _indexes  = NULL;
  _arena    = arena;
  _complete = false;
}

OopRecorder::IndexCache::IndexCache() {
  assert(first_index > 0, "initial zero state of cache must be invalid index");
  Copy::zero_to_bytes(&_cache[0], sizeof(_cache));
}

int OopRecorder::oop_size() {
  _complete = true;
  if (_handles == NULL)  return 0;
  return _handles->length() * sizeof(oop);
}

void OopRecorder::copy_to(CodeBlob* code) {
  assert(_complete, "must be frozen");
  maybe_initialize();  // get non-null handles, even if we have no oops
  code->copy_oops(_handles);
}

void OopRecorder::maybe_initialize() {
  if (_handles == NULL) {
    if (_arena != NULL) {
      _handles  = new(_arena) GrowableArray<jobject>(_arena, 10, 0, 0);
      _no_finds = new(_arena) GrowableArray<int>(    _arena, 10, 0, 0);
    } else {
      _handles  = new GrowableArray<jobject>(10, 0, 0);
      _no_finds = new GrowableArray<int>(    10, 0, 0);
    }
  }
}


jobject OopRecorder::handle_at(int index) {
  // there is always a NULL virtually present as first object
  if (index == null_index)  return NULL;
  return _handles->at(index - first_index);
}


// Local definition.  Used only in this module.
inline bool OopRecorder::is_real_jobject(jobject h) {
  return h != NULL && h != (jobject)Universe::non_oop_word();
}


int OopRecorder::add_handle(jobject h, bool make_findable) {
  assert(!_complete, "cannot allocate more elements after size query");
  maybe_initialize();
  // indexing uses 1 as an origin--0 means null
  int index = _handles->length() + first_index;
  _handles->append(h);

  // Support correct operation of find_index().
  assert(!(make_findable && !is_real_jobject(h)), "nulls are not findable");
  if (make_findable) {
    // This index may be returned from find_index().
    if (_indexes != NULL) {
      int* cloc = _indexes->cache_location(h);
      _indexes->set_cache_location_index(cloc, index);
    } else if (index == index_cache_threshold && _arena != NULL) {
      _indexes = new(_arena) IndexCache();
      for (int i = 0; i < _handles->length(); i++) {
        // Load the cache with pre-existing elements.
        int index0 = i + first_index;
        if (_no_finds->contains(index0))  continue;
        int* cloc = _indexes->cache_location(_handles->at(i));
        _indexes->set_cache_location_index(cloc, index0);
      }
    }
  } else if (is_real_jobject(h)) {
    // Remember that this index is not to be returned from find_index().
    // This case is rare, because most or all uses of allocate_index pass
    // a jobject argument of NULL or Universe::non_oop_word.
    // Thus, the expected length of _no_finds is zero.
    _no_finds->append(index);
  }

  return index;
}


int OopRecorder::maybe_find_index(jobject h) {
  debug_only(_find_index_calls++);
  assert(!_complete, "cannot allocate more elements after size query");
  maybe_initialize();
  if (h == NULL)  return null_index;
  assert(is_real_jobject(h), "must be valid jobject");
  int* cloc = (_indexes == NULL)? NULL: _indexes->cache_location(h);
  if (cloc != NULL) {
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
      if (cloc != NULL) {
        _indexes->set_cache_location_index(cloc, findex);
      }
      debug_only(_missed_indexes++);
      return findex;
    }
  }
  return -1;
}
