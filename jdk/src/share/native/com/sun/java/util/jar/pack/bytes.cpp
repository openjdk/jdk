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


#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "defines.h"
#include "bytes.h"
#include "utils.h"


static byte dummy[1 << 10];

bool bytes::inBounds(const void* p) {
  return p >= ptr && p < limit();
}

void bytes::malloc(size_t len_) {
  len = len_;
  ptr = NEW(byte, len_+1);  // add trailing zero byte always
  if (ptr == null) {
    // set ptr to some victim memory, to ease escape
    set(dummy, sizeof(dummy)-1);
    unpack_abort(ERROR_ENOMEM);
  }
}

void bytes::realloc(size_t len_) {
  if (len == len_)   return;  // nothing to do
  if (ptr == dummy)  return;  // escaping from an error
  if (ptr == null) {
    malloc(len_);
    return;
  }
  byte* oldptr = ptr;
  ptr = (byte*)::realloc(ptr, len_+1);
  if (ptr != null)  {
    mtrace('r', oldptr, 0);
    mtrace('m', ptr, len_+1);
    if (len < len_)  memset(ptr+len, 0, len_-len);
    ptr[len_] = 0;
    len = len_;
  } else {
    ptr = oldptr;  // ease our escape
    unpack_abort(ERROR_ENOMEM);
  }
}

void bytes::free() {
  if (ptr == dummy)  return;  // escaping from an error
  if (ptr != null) {
    mtrace('f', ptr, 0);
    ::free(ptr);
  }
  len = 0;
  ptr = 0;
}

int bytes::indexOf(byte c) {
  byte* p = (byte*) memchr(ptr, c, len);
  return (p == 0) ? -1 : (int)(p - ptr);
}

byte* bytes::writeTo(byte* bp) {
  memcpy(bp, ptr, len);
  return bp+len;
}

int bytes::compareTo(bytes& other) {
  size_t l1 = len;
  size_t l2 = other.len;
  int cmp = memcmp(ptr, other.ptr, (l1 < l2) ? l1 : l2);
  if (cmp != 0)  return cmp;
  return (l1 < l2) ? -1 : (l1 > l2) ? 1 : 0;
}

void bytes::saveFrom(const void* ptr_, size_t len_) {
  malloc(len_);
  // Save as much as possible.  (Helps unpacker::abort.)
  if (len_ > len) {
    assert(ptr == dummy);  // error recovery
    len_ = len;
  }
  copyFrom(ptr_, len_);
}

//#TODO: Need to fix for exception handling
void bytes::copyFrom(const void* ptr_, size_t len_, size_t offset) {
  assert(len_ == 0 || inBounds(ptr + offset));
  assert(len_ == 0 || inBounds(ptr + offset+len_-1));
  memcpy(ptr+offset, ptr_, len_);
}


#ifndef PRODUCT
const char* bytes::string() {
  if (len == 0)  return "";
  if (ptr[len] == 0 && strlen((char*)ptr) == len)  return (const char*) ptr;
  bytes junk;
  junk.saveFrom(*this);
  return (char*) junk.ptr;
}
#endif

// Make sure there are 'o' bytes beyond the fill pointer,
// advance the fill pointer, and return the old fill pointer.
byte* fillbytes::grow(size_t s) {
  size_t nlen = b.len+s;
  if (nlen <= allocated) {
    b.len = nlen;
    return limit()-s;
  }
  size_t maxlen = nlen;
  if (maxlen < 128)          maxlen = 128;
  if (maxlen < allocated*2)  maxlen = allocated*2;
  if (allocated == 0) {
    // Initial buffer was not malloced.  Do not reallocate it.
    bytes old = b;
    b.malloc(maxlen);
    if (b.len == maxlen)
      old.writeTo(b.ptr);
  } else {
    b.realloc(maxlen);
  }
  allocated = b.len;
  if (allocated != maxlen) {
    assert(unpack_aborting());
    b.len = nlen-s;  // back up
    return dummy;    // scribble during error recov.
  }
  // after realloc, recompute pointers
  b.len = nlen;
  assert(b.len <= allocated);
  return limit()-s;
}

void fillbytes::ensureSize(size_t s) {
  if (allocated >= s)  return;
  size_t len0 = b.len;
  grow(s - size());
  b.len = len0;  // put it back
}

int ptrlist::indexOf(const void* x) {
  int len = length();
  for (int i = 0; i < len; i++) {
    if (get(i) == x)  return i;
  }
  return -1;
}

void ptrlist::freeAll() {
  int len = length();
  for (int i = 0; i < len; i++) {
    void* p = (void*) get(i);
    if (p != null)  {
      mtrace('f', p, 0);
      ::free(p);
    }
  }
  free();
}

int intlist::indexOf(int x) {
  int len = length();
  for (int i = 0; i < len; i++) {
    if (get(i) == x)  return i;
  }
  return -1;
}
