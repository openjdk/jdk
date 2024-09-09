/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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
 */

#ifndef SHARE_GC_Z_ZSTOREBARRIERBUFFER_HPP
#define SHARE_GC_Z_ZSTOREBARRIERBUFFER_HPP

#include "gc/z/zAddress.hpp"
#include "gc/z/zGenerationId.hpp"
#include "gc/z/zLock.hpp"
#include "memory/allocation.hpp"
#include "utilities/sizes.hpp"

struct ZStoreBarrierEntry {
  volatile zpointer* _p;
  zpointer           _prev;

  static ByteSize p_offset();
  static ByteSize prev_offset();
};

class ZStoreBarrierBuffer : public CHeapObj<mtGC> {
  friend class ZVerify;

private:
  static const size_t _buffer_length     = 32;
  static const size_t _buffer_size_bytes = _buffer_length * sizeof(ZStoreBarrierEntry);

  ZStoreBarrierEntry _buffer[_buffer_length];

  // Color from previous phase this buffer was processed
  uintptr_t          _last_processed_color;

  // Use as a claim mechanism for installing base pointers
  uintptr_t          _last_installed_color;

  ZLock              _base_pointer_lock;
  zaddress_unsafe    _base_pointers[_buffer_length];

  // sizeof(ZStoreBarrierEntry) scaled index growing downwards
  size_t             _current;

  void on_new_phase_relocate(size_t i);
  void on_new_phase_remember(size_t i);
  void on_new_phase_mark(size_t i);

  void clear();

  bool is_old_mark() const;
  bool stored_during_old_mark() const;
  bool is_empty() const;
  size_t current() const;

  void install_base_pointers_inner();

  void on_error(outputStream* st);
  class OnError;

public:
  ZStoreBarrierBuffer();

  static ByteSize buffer_offset();
  static ByteSize current_offset();

  static ZStoreBarrierBuffer* buffer_for_store(bool heal);

  void initialize();
  void on_new_phase();

  void install_base_pointers();

  void flush();
  void add(volatile zpointer* p, zpointer prev);

  // Check if p is contained in any store barrier buffer entry in the system
  static bool is_in(volatile zpointer* p);
};

#endif // SHARE_GC_Z_ZSTOREBARRIERBUFFER_HPP
