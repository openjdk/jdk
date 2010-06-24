/*
 * Copyright (c) 1998, 2005, Oracle and/or its affiliates. All rights reserved.
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

// Recording and retrieval of oop relocations in compiled code.

class CodeBlob;

class OopRecorder : public ResourceObj {
 public:
  // A two-way mapping from positive indexes to oop handles.
  // The zero index is reserved for a constant (sharable) null.
  // Indexes may not be negative.

  // Use the given arena to manage storage, if not NULL.
  // By default, uses the current ResourceArea.
  OopRecorder(Arena* arena = NULL);

  // Generate a new index on which CodeBlob::oop_addr_at will work.
  // allocate_index and find_index never return the same index,
  // and allocate_index never returns the same index twice.
  // In fact, two successive calls to allocate_index return successive ints.
  int allocate_index(jobject h) {
    return add_handle(h, false);
  }

  // For a given jobject, this will return the same index repeatedly.
  // The index can later be given to oop_at to retrieve the oop.
  // However, the oop must not be changed via CodeBlob::oop_addr_at.
  int find_index(jobject h) {
    int index = maybe_find_index(h);
    if (index < 0) {  // previously unallocated
      index = add_handle(h, true);
    }
    return index;
  }

  // variant of find_index which does not allocate if not found (yields -1)
  int maybe_find_index(jobject h);

  // returns the size of the generated oop table, for sizing the CodeBlob.
  // must be called after all oops are allocated!
  int oop_size();

  // Retrieve the oop handle at a given index.
  jobject handle_at(int index);

  int element_count() {
    // there is always a NULL virtually present as first object
    return _handles->length() + first_index;
  }

  // copy the generated oop table to CodeBlob
  void copy_to(CodeBlob* code);  // => code->copy_oops(_handles)

  bool is_unused() { return _handles == NULL && !_complete; }
#ifdef ASSERT
  bool is_complete() { return _complete; }
#endif

 private:
  // leaky hash table of handle => index, to help detect duplicate insertion
  class IndexCache: public ResourceObj {
    // This class is only used by the OopRecorder class.
    friend class OopRecorder;
    enum {
      _log_cache_size = 9,
      _cache_size = (1<<_log_cache_size),
      // Index entries are ints.  The LSBit is a collision indicator.
      _collision_bit_shift = 0,
      _collision_bit = 1,
      _index_shift = _collision_bit_shift+1
    };
    int _cache[_cache_size];
    static juint cache_index(jobject handle) {
      juint ci = (int) (intptr_t) handle;
      ci ^= ci >> (BitsPerByte*2);
      ci += ci >> (BitsPerByte*1);
      return ci & (_cache_size-1);
    }
    int* cache_location(jobject handle) {
      return &_cache[ cache_index(handle) ];
    }
    static bool cache_location_collision(int* cloc) {
      return ((*cloc) & _collision_bit) != 0;
    }
    static int cache_location_index(int* cloc) {
      return (*cloc) >> _index_shift;
    }
    static void set_cache_location_index(int* cloc, int index) {
      int cval0 = (*cloc);
      int cval1 = (index << _index_shift);
      if (cval0 != 0 && cval1 != cval0)  cval1 += _collision_bit;
      (*cloc) = cval1;
    }
    IndexCache();
  };

  // Helper function; returns false for NULL or Universe::non_oop_word().
  inline bool is_real_jobject(jobject h);

  void maybe_initialize();
  int add_handle(jobject h, bool make_findable);

  enum { null_index = 0, first_index = 1, index_cache_threshold = 20 };

  GrowableArray<jobject>*   _handles;  // ordered list (first is always NULL)
  GrowableArray<int>*       _no_finds; // all unfindable indexes; usually empty
  IndexCache*               _indexes;  // map: jobject -> its probable index
  Arena*                    _arena;
  bool                      _complete;

#ifdef ASSERT
  static int _find_index_calls, _hit_indexes, _missed_indexes;
#endif
};
