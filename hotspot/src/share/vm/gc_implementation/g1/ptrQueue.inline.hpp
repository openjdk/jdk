/*
 * Copyright 2001-2007 Sun Microsystems, Inc.  All Rights Reserved.
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

void PtrQueue::handle_zero_index() {
  assert(0 == _index, "Precondition.");
  // This thread records the full buffer and allocates a new one (while
  // holding the lock if there is one).
  void** buf = _buf;
  _buf = qset()->allocate_buffer();
  _sz = qset()->buffer_size();
  _index = _sz;
  assert(0 <= _index && _index <= _sz, "Invariant.");
  if (buf != NULL) {
    if (_lock) {
      locking_enqueue_completed_buffer(buf);
    } else {
      qset()->enqueue_complete_buffer(buf);
    }
  }
}
