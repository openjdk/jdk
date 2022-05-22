/*
 * Copyright (c) 1998, 2022, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_ADLC_ADLARENA_HPP
#define SHARE_ADLC_ADLARENA_HPP

void* AdlAllocateHeap(size_t size);
void* AdlReAllocateHeap(void* old_ptr, size_t size);

// All classes in adlc may be derived
// from one of the following allocation classes:
//
// For objects allocated in the C-heap (managed by: malloc & free).
// - CHeapObj
//
// For classes used as name spaces.
// - AdlAllStatic
//

class AdlCHeapObj {
 public:
  void* operator new(size_t size) throw();
  void  operator delete(void* p);
  void* new_array(size_t size);
};

// Base class for classes that constitute name spaces.

class AdlAllStatic {
 public:
  void* operator new(size_t size) throw();
  void operator delete(void* p);
};


//------------------------------AdlChunk------------------------------------------
// Linked list of raw memory chunks
class AdlChunk: public AdlCHeapObj {
 private:
  // This ordinary operator delete is needed even though not used, so the
  // below two-argument operator delete will be treated as a placement
  // delete rather than an ordinary sized delete; see C++14 3.7.4.2/p2.
  void operator delete(void* p);
 public:
  void* operator new(size_t size, size_t length) throw();
  void  operator delete(void* p, size_t length);
  AdlChunk(size_t length);

  enum {
      init_size =  1*1024,      // Size of first chunk
      size      = 32*1024       // Default size of an AdlArena chunk (following the first)
  };
  AdlChunk*       _next;        // Next AdlChunk in list
  size_t       _len;            // Size of this AdlChunk

  void chop();                  // Chop this chunk
  void next_chop();             // Chop next chunk

  // Boundaries of data area (possibly unused)
  char* bottom() const { return ((char*) this) + sizeof(AdlChunk);  }
  char* top()    const { return bottom() + _len; }
};


//------------------------------AdlArena------------------------------------------
// Fast allocation of memory
class AdlArena: public AdlCHeapObj {
protected:
  friend class ResourceMark;
  friend class HandleMark;
  friend class NoHandleMark;
  AdlChunk *_first;             // First chunk
  AdlChunk *_chunk;             // current chunk
  char *_hwm, *_max;            // High water mark and max in current chunk
  void* grow(size_t x);         // Get a new AdlChunk of at least size x
  size_t _size_in_bytes;          // Size of arena (used for memory usage tracing)
public:
  AdlArena();
  AdlArena(size_t init_size);
  AdlArena(AdlArena *old);
  ~AdlArena()                   { _first->chop(); }
  char* hwm() const             { return _hwm; }

  // Fast allocate in the arena.  Common case is: pointer test + increment.
  void* Amalloc(size_t x) {
#ifdef _LP64
    x = (x + (8-1)) & ((unsigned)(-8));
#else
    x = (x + (4-1)) & ((unsigned)(-4));
#endif
    if (_hwm + x > _max) {
      return grow(x);
    } else {
      char *old = _hwm;
      _hwm += x;
      return old;
    }
  }
  // Further assume size is padded out to words
  void *AmallocWords(size_t x) {
    assert( (x&(sizeof(char*)-1)) == 0, "misaligned size" );
    if (_hwm + x > _max) {
      return grow(x);
    } else {
      char *old = _hwm;
      _hwm += x;
      return old;
    }
  }

  // Fast delete in area.  Common case is: NOP (except for storage reclaimed)
  void Afree(void *ptr, size_t size) {
    if (((char*)ptr) + size == _hwm) _hwm = (char*)ptr;
  }

  void *Acalloc( size_t items, size_t x );
  void *Arealloc( void *old_ptr, size_t old_size, size_t new_size );

  // Reset this AdlArena to empty, and return this AdlArenas guts in a new AdlArena.
  AdlArena *reset(void);

  // Determine if pointer belongs to this AdlArena or not.
  bool contains( const void *ptr ) const;

  // Total of all chunks in use (not thread-safe)
  size_t used() const;

  // Total # of bytes used
  size_t size_in_bytes() const         {  return _size_in_bytes; }
  void   set_size_in_bytes(size_t size)  { _size_in_bytes = size;   }
};

#endif // SHARE_ADLC_ADLARENA_HPP
