/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

// Class Stack (below) grows and shrinks by linking together "segments" which
// are allocated on demand.  Segments are arrays of the element type (E) plus an
// extra pointer-sized field to store the segment link.  Recently emptied
// segments are kept in a cache and reused.
//
// Notes/caveats:
//
// The size of an element must either evenly divide the size of a pointer or be
// a multiple of the size of a pointer.
//
// Destructors are not called for elements popped off the stack, so element
// types which rely on destructors for things like reference counting will not
// work properly.
//
// Class Stack allocates segments from the C heap.  However, two protected
// virtual methods are used to alloc/free memory which subclasses can override:
//
//      virtual void* alloc(size_t bytes);
//      virtual void  free(void* addr, size_t bytes);
//
// The alloc() method must return storage aligned for any use.  The
// implementation in class Stack assumes that alloc() will terminate the process
// if the allocation fails.

template <class E> class StackIterator;

// StackBase holds common data/methods that don't depend on the element type,
// factored out to reduce template code duplication.
class StackBase
{
public:
  size_t segment_size()   const { return _seg_size; } // Elements per segment.
  size_t max_size()       const { return _max_size; } // Max elements allowed.
  size_t max_cache_size() const { return _max_cache_size; } // Max segments
                                                            // allowed in cache.

  size_t cache_size() const { return _cache_size; }   // Segments in the cache.

protected:
  // The ctor arguments correspond to the like-named functions above.
  // segment_size:    number of items per segment
  // max_cache_size:  maxmium number of *segments* to cache
  // max_size:        maximum number of items allowed, rounded to a multiple of
  //                  the segment size (0 == unlimited)
  inline StackBase(size_t segment_size, size_t max_cache_size, size_t max_size);

  // Round max_size to a multiple of the segment size.  Treat 0 as unlimited.
  static inline size_t adjust_max_size(size_t max_size, size_t seg_size);

protected:
  const size_t _seg_size;       // Number of items per segment.
  const size_t _max_size;       // Maximum number of items allowed in the stack.
  const size_t _max_cache_size; // Maximum number of segments to cache.
  size_t       _cur_seg_size;   // Number of items in the current segment.
  size_t       _full_seg_size;  // Number of items in already-filled segments.
  size_t       _cache_size;     // Number of segments in the cache.
};

#ifdef __GNUC__
#define inline
#endif // __GNUC__

template <class E>
class Stack:  public StackBase
{
public:
  friend class StackIterator<E>;

  // segment_size:    number of items per segment
  // max_cache_size:  maxmium number of *segments* to cache
  // max_size:        maximum number of items allowed, rounded to a multiple of
  //                  the segment size (0 == unlimited)
  inline Stack(size_t segment_size = default_segment_size(),
               size_t max_cache_size = 4, size_t max_size = 0);
  inline ~Stack() { clear(true); }

  inline bool is_empty() const { return _cur_seg == NULL; }
  inline bool is_full()  const { return _full_seg_size >= max_size(); }

  // Performance sensitive code should use is_empty() instead of size() == 0 and
  // is_full() instead of size() == max_size().  Using a conditional here allows
  // just one var to be updated when pushing/popping elements instead of two;
  // _full_seg_size is updated only when pushing/popping segments.
  inline size_t size() const {
    return is_empty() ? 0 : _full_seg_size + _cur_seg_size;
  }

  inline void push(E elem);
  inline E    pop();

  // Clear everything from the stack, releasing the associated memory.  If
  // clear_cache is true, also release any cached segments.
  void clear(bool clear_cache = false);

  static inline size_t default_segment_size();

protected:
  // Each segment includes space for _seg_size elements followed by a link
  // (pointer) to the previous segment; the space is allocated as a single block
  // of size segment_bytes().  _seg_size is rounded up if necessary so the link
  // is properly aligned.  The C struct for the layout would be:
  //
  // struct segment {
  //   E     elements[_seg_size];
  //   E*    link;
  // };

  // Round up seg_size to keep the link field aligned.
  static inline size_t adjust_segment_size(size_t seg_size);

  // Methods for allocation size and getting/setting the link.
  inline size_t link_offset() const;              // Byte offset of link field.
  inline size_t segment_bytes() const;            // Segment size in bytes.
  inline E**    link_addr(E* seg) const;          // Address of the link field.
  inline E*     get_link(E* seg) const;           // Extract the link from seg.
  inline E*     set_link(E* new_seg, E* old_seg); // new_seg.link = old_seg.

  virtual E*    alloc(size_t bytes);
  virtual void  free(E* addr, size_t bytes);

  void push_segment();
  void pop_segment();

  void free_segments(E* seg);          // Free all segments in the list.
  inline void reset(bool reset_cache); // Reset all data fields.

  DEBUG_ONLY(void verify(bool at_empty_transition) const;)
  DEBUG_ONLY(void zap_segment(E* seg, bool zap_link_field) const;)

private:
  E* _cur_seg;    // Current segment.
  E* _cache;      // Segment cache to avoid ping-ponging.
};

template <class E> class ResourceStack:  public Stack<E>, public ResourceObj
{
public:
  // If this class becomes widely used, it may make sense to save the Thread
  // and use it when allocating segments.
  ResourceStack(size_t segment_size = Stack<E>::default_segment_size()):
    Stack<E>(segment_size, max_uintx)
    { }

  // Set the segment pointers to NULL so the parent dtor does not free them;
  // that must be done by the ResourceMark code.
  ~ResourceStack() { Stack<E>::reset(true); }

protected:
  virtual E*   alloc(size_t bytes);
  virtual void free(E* addr, size_t bytes);

private:
  void clear(bool clear_cache = false);
};

template <class E>
class StackIterator: public StackObj
{
public:
  StackIterator(Stack<E>& stack): _stack(stack) { sync(); }

  Stack<E>& stack() const { return _stack; }

  bool is_empty() const { return _cur_seg == NULL; }

  E  next() { return *next_addr(); }
  E* next_addr();

  void sync(); // Sync the iterator's state to the stack's current state.

private:
  Stack<E>& _stack;
  size_t    _cur_seg_size;
  E*        _cur_seg;
  size_t    _full_seg_size;
};

#ifdef __GNUC__
#undef inline
#endif // __GNUC__
