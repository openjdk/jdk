/*
 * Copyright (c) 2017, 2018, Red Hat, Inc. All rights reserved.
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

#ifndef SHARE_VM_GC_SHENANDOAH_SHENANDOAHSTRINGDEDUPQUEUE_INLINE_HPP
#define SHARE_VM_GC_SHENANDOAH_SHENANDOAHSTRINGDEDUPQUEUE_INLINE_HPP

#include "gc/shenandoah/shenandoahStrDedupQueue.hpp"

template <uint buffer_size>
ShenandoahOopBuffer<buffer_size>::ShenandoahOopBuffer() :
  _index(0), _next(NULL) {
}

template <uint buffer_size>
bool ShenandoahOopBuffer<buffer_size>::is_full() const {
  return _index >= buffer_size;
}

template <uint buffer_size>
bool ShenandoahOopBuffer<buffer_size>::is_empty() const {
  return _index == 0;
}

template <uint buffer_size>
uint ShenandoahOopBuffer<buffer_size>::size() const {
  return _index;
}

template <uint buffer_size>
void ShenandoahOopBuffer<buffer_size>::push(oop obj) {
  assert(!is_full(),  "Buffer is full");
  _buf[_index ++] = obj;
}

template <uint buffer_size>
oop ShenandoahOopBuffer<buffer_size>::pop() {
  assert(!is_empty(), "Buffer is empty");
  return _buf[--_index];
}

template <uint buffer_size>
void ShenandoahOopBuffer<buffer_size>::set_next(ShenandoahOopBuffer<buffer_size>* next) {
  _next = next;
}

template <uint buffer_size>
ShenandoahOopBuffer<buffer_size>* ShenandoahOopBuffer<buffer_size>::next() const {
  return _next;
}

template <uint buffer_size>
void ShenandoahOopBuffer<buffer_size>::reset() {
  _index = 0;
  _next = NULL;
}

template <uint buffer_size>
void ShenandoahOopBuffer<buffer_size>::unlink_or_oops_do(StringDedupUnlinkOrOopsDoClosure* cl) {
  for (uint index = 0; index < size(); index ++) {
    oop* obj_addr = &_buf[index];
    if (*obj_addr != NULL) {
      if (cl->is_alive(*obj_addr)) {
        cl->keep_alive(obj_addr);
      } else {
        *obj_addr = NULL;
      }
    }
  }
}

template <uint buffer_size>
void ShenandoahOopBuffer<buffer_size>::oops_do(OopClosure* cl) {
  for (uint index = 0; index < size(); index ++) {
    cl->do_oop(&_buf[index]);
  }
}

#endif // SHARE_VM_GC_SHENANDOAH_SHENANDOAHSTRINGDEDUPQUEUE_INLINE_HPP
