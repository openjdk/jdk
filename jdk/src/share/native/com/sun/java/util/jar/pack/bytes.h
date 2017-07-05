/*
 * Copyright 2001-2008 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 */

#ifdef WIN32_LEAN_AND_MEAN
typedef signed char byte ;
#endif

struct bytes {
  byte*  ptr;
  size_t len;
  byte*  limit() { return ptr+len; }

  void set(byte* ptr_, size_t len_) { ptr = ptr_; len = len_; }
  void set(const char* str)         { ptr = (byte*)str; len = strlen(str); }
  bool inBounds(const void* p);     // p in [ptr, limit)
  void malloc(size_t len_);
  void realloc(size_t len_);
  void free();
  void copyFrom(const void* ptr_, size_t len_, size_t offset = 0);
  void saveFrom(const void* ptr_, size_t len_);
  void saveFrom(const char* str) { saveFrom(str, strlen(str)); }
  void copyFrom(bytes& other, size_t offset = 0) {
    copyFrom(other.ptr, other.len, offset);
  }
  void saveFrom(bytes& other) {
    saveFrom(other.ptr, other.len);
  }
  void clear(int fill_byte = 0) { memset(ptr, fill_byte, len); }
  byte* writeTo(byte* bp);
  bool equals(bytes& other) { return 0 == compareTo(other); }
  int compareTo(bytes& other);
  bool contains(byte c) { return indexOf(c) >= 0; }
  int indexOf(byte c);
  // substrings:
  static bytes of(byte* ptr, size_t len) {
    bytes res;
    res.set(ptr, len);
    return res;
  }
  bytes slice(size_t beg, size_t end) {
    bytes res;
    res.ptr = ptr + beg;
    res.len = end - beg;
    assert(res.len == 0 || inBounds(res.ptr) && inBounds(res.limit()-1));
    return res;
  }
  // building C strings inside byte buffers:
  bytes& strcat(const char* str) { ::strcat((char*)ptr, str); return *this; }
  bytes& strcat(bytes& other) { ::strncat((char*)ptr, (char*)other.ptr, other.len); return *this; }
  char* strval() { assert(strlen((char*)ptr) == len); return (char*) ptr; }
#ifdef PRODUCT
  const char* string() { return 0; }
#else
  const char* string();
#endif
};
#define BYTES_OF(var) (bytes::of((byte*)&(var), sizeof(var)))

struct fillbytes {
  bytes b;
  size_t allocated;

  byte*  base()               { return b.ptr; }
  size_t size()               { return b.len; }
  byte*  limit()              { return b.limit(); }          // logical limit
  void   setLimit(byte* lp)   { assert(isAllocated(lp)); b.len = lp - b.ptr; }
  byte*  end()                { return b.ptr + allocated; }  // physical limit
  byte*  loc(size_t o)        { assert(o < b.len); return b.ptr + o; }
  void   init()               { allocated = 0; b.set(null, 0); }
  void   init(size_t s)       { init(); ensureSize(s); }
  void   free()               { if (allocated != 0) b.free(); allocated = 0; }
  void   empty()              { b.len = 0; }
  byte*  grow(size_t s);      // grow so that limit() += s
  int    getByte(uint i)      { return *loc(i) & 0xFF; }
  void   addByte(byte x)      { *grow(1) = x; }
  void   ensureSize(size_t s); // make sure allocated >= s
  void   trimToSize()         { if (allocated > size())  b.realloc(allocated = size()); }
  bool   canAppend(size_t s)  { return allocated > b.len+s; }
  bool   isAllocated(byte* p) { return p >= base() && p <= end(); } //asserts
  void   set(bytes& src)      { set(src.ptr, src.len); }

  void set(byte* ptr, size_t len) {
    b.set(ptr, len);
    allocated = 0;   // mark as not reallocatable
  }

  // block operations on resizing byte buffer:
  fillbytes& append(const void* ptr_, size_t len_)
    { memcpy(grow(len_), ptr_, len_); return (*this); }
  fillbytes& append(bytes& other)
    { return append(other.ptr, other.len); }
  fillbytes& append(const char* str)
    { return append(str, strlen(str)); }
};

struct ptrlist : fillbytes {
  typedef const void* cvptr;
  int    length()     { return (int)(size() / sizeof(cvptr)); }
  cvptr* base()       { return (cvptr*) fillbytes::base(); }
  cvptr& get(int i)   { return *(cvptr*)loc(i * sizeof(cvptr)); }
  cvptr* limit()      { return (cvptr*) fillbytes::limit(); }
  void   add(cvptr x) { *(cvptr*)grow(sizeof(x)) = x; }
  void   popTo(int l) { assert(l <= length()); b.len = l * sizeof(cvptr); }
  int    indexOf(cvptr x);
  bool   contains(cvptr x) { return indexOf(x) >= 0; }
  void   freeAll();   // frees every ptr on the list, plus the list itself
};
// Use a macro rather than mess with subtle mismatches
// between member and non-member function pointers.
#define PTRLIST_QSORT(ptrls, fn) \
  ::qsort((ptrls).base(), (ptrls).length(), sizeof(void*), fn)

struct intlist : fillbytes {
  int    length()     { return (int)(size() / sizeof(int)); }
  int*   base()       { return (int*) fillbytes::base(); }
  int&   get(int i)   { return *(int*)loc(i * sizeof(int)); }
  int*   limit()      { return (int*) fillbytes::limit(); }
  void   add(int x)   { *(int*)grow(sizeof(x)) = x; }
  void   popTo(int l) { assert(l <= length()); b.len = l * sizeof(int); }
  int    indexOf(int x);
  bool   contains(int x) { return indexOf(x) >= 0; }
};
