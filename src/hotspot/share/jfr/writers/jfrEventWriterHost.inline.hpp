/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_JFR_WRITERS_JFREVENTWRITERHOST_INLINE_HPP
#define SHARE_JFR_WRITERS_JFREVENTWRITERHOST_INLINE_HPP

#include "jfr/writers/jfrEventWriterHost.hpp"

template <typename BE, typename IE, typename WriterPolicyImpl>
template <typename StorageType>
inline EventWriterHost<BE, IE, WriterPolicyImpl>::
EventWriterHost(StorageType* storage, Thread* thread) : WriterHost<BE, IE, WriterPolicyImpl>(storage, thread) {}

template <typename BE, typename IE, typename WriterPolicyImpl>
inline EventWriterHost<BE, IE, WriterPolicyImpl>::EventWriterHost(Thread* thread) : WriterHost<BE, IE, WriterPolicyImpl>(thread) {
}

template <typename BE, typename IE, typename WriterPolicyImpl>
inline void EventWriterHost<BE, IE, WriterPolicyImpl>::begin_write() {
  assert(this->is_valid(), "invariant");
  assert(!this->is_acquired(), "calling begin with writer already in acquired state!");
  this->acquire();
  assert(this->used_offset() == 0, "invariant");
  assert(this->is_acquired(), "invariant");
}

template <typename BE, typename IE, typename WriterPolicyImpl>
inline intptr_t EventWriterHost<BE, IE, WriterPolicyImpl>::end_write(void) {
  assert(this->is_acquired(),
    "state corruption, calling end with writer with non-acquired state!");
  return this->is_valid() ? (intptr_t)this->used_offset() : 0;
}

template <typename BE, typename IE, typename WriterPolicyImpl>
inline void EventWriterHost<BE, IE, WriterPolicyImpl>::begin_event_write() {
  assert(this->is_valid(), "invariant");
  assert(!this->is_acquired(), "calling begin with writer already in acquired state!");
  this->begin_write();
  this->reserve(sizeof(u4)); // reserve the event size slot
}

template <typename BE, typename IE, typename WriterPolicyImpl>
inline intptr_t EventWriterHost<BE, IE, WriterPolicyImpl>::end_event_write() {
  assert(this->is_acquired(), "invariant");
  if (!this->is_valid()) {
    this->release();
    return 0;
  }
  const u4 written = (u4)end_write();
  if (written > sizeof(u4)) { // larger than header reserve
    this->write_padded_at_offset(written, 0);
    this->commit();
  }
  this->release();
  assert(!this->is_acquired(), "invariant");
  return written;
}

template <typename BE, typename IE, typename WriterPolicyImpl>
template <typename StorageType>
inline StackEventWriterHost<BE, IE, WriterPolicyImpl>::
StackEventWriterHost(StorageType* storage, Thread* thread) : EventWriterHost<BE, IE, WriterPolicyImpl>(storage, thread) {
  this->begin_event_write();
}

template <typename BE, typename IE, typename WriterPolicyImpl>
inline StackEventWriterHost<BE, IE, WriterPolicyImpl>::StackEventWriterHost(Thread* thread) : EventWriterHost<BE, IE, WriterPolicyImpl>(thread) {
  this->begin_event_write();
}

template <typename BE, typename IE, typename WriterPolicyImpl>
inline StackEventWriterHost<BE, IE, WriterPolicyImpl>::~StackEventWriterHost() {
  this->end_event_write();
}

#endif // SHARE_JFR_WRITERS_JFREVENTWRITERHOST_INLINE_HPP
