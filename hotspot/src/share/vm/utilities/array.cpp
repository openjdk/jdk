/*
 * Copyright (c) 2000, 2004, Oracle and/or its affiliates. All rights reserved.
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
# include "incls/_array.cpp.incl"


#ifdef ASSERT
void ResourceArray::init_nesting() {
  _nesting = Thread::current()->resource_area()->nesting();
}
#endif


void ResourceArray::sort(size_t esize, ftype f) {
  if (!is_empty()) qsort(_data, length(), esize, f);
}
void CHeapArray::sort(size_t esize, ftype f) {
  if (!is_empty()) qsort(_data, length(), esize, f);
}


void ResourceArray::expand(size_t esize, int i, int& size) {
  // make sure we are expanding within the original resource mark
  assert(
    _nesting == Thread::current()->resource_area()->nesting(),
    "allocating outside original resource mark"
  );
  // determine new size
  if (size == 0) size = 4; // prevent endless loop
  while (i >= size) size *= 2;
  // allocate and initialize new data section
  void* data = resource_allocate_bytes(esize * size);
  memcpy(data, _data, esize * length());
  _data = data;
}


void CHeapArray::expand(size_t esize, int i, int& size) {
  // determine new size
  if (size == 0) size = 4; // prevent endless loop
  while (i >= size) size *= 2;
  // allocate and initialize new data section
  void* data = NEW_C_HEAP_ARRAY(char*, esize * size);
  memcpy(data, _data, esize * length());
  FREE_C_HEAP_ARRAY(char*, _data);
  _data = data;
}


void ResourceArray::remove_at(size_t esize, int i) {
  assert(0 <= i && i < length(), "index out of bounds");
  _length--;
  void* dst = (char*)_data + i*esize;
  void* src = (char*)dst + esize;
  size_t cnt = (length() - i)*esize;
  memmove(dst, src, cnt);
}

void CHeapArray::remove_at(size_t esize, int i) {
  assert(0 <= i && i < length(), "index out of bounds");
  _length--;
  void* dst = (char*)_data + i*esize;
  void* src = (char*)dst + esize;
  size_t cnt = (length() - i)*esize;
  memmove(dst, src, cnt);
}
