/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "gc/shared/gcId.hpp"
#include "runtime/safepoint.hpp"
#include "runtime/thread.inline.hpp"
#include "runtime/threadLocalStorage.hpp"

uint GCId::_next_id = 0;

NamedThread* currentNamedthread() {
  assert(Thread::current()->is_Named_thread(), "This thread must be NamedThread");
  return (NamedThread*)Thread::current();
}

const uint GCId::create() {
  return _next_id++;
}

const uint GCId::peek() {
  return _next_id;
}

const uint GCId::current() {
  assert(currentNamedthread()->gc_id() != undefined(), "Using undefined GC id.");
  return current_raw();
}

const uint GCId::current_raw() {
  return currentNamedthread()->gc_id();
}

size_t GCId::print_prefix(char* buf, size_t len) {
  if (ThreadLocalStorage::is_initialized() && ThreadLocalStorage::thread()->is_Named_thread()) {
    uint gc_id = current_raw();
    if (gc_id != undefined()) {
      int ret = jio_snprintf(buf, len, "GC(%u) ", gc_id);
      assert(ret > 0, "Failed to print prefix. Log buffer too small?");
      return (size_t)ret;
    }
  }
  return 0;
}

GCIdMark::GCIdMark() : _gc_id(GCId::create()) {
  currentNamedthread()->set_gc_id(_gc_id);
}

GCIdMark::GCIdMark(uint gc_id) : _gc_id(gc_id) {
  currentNamedthread()->set_gc_id(_gc_id);
}

GCIdMark::~GCIdMark() {
  currentNamedthread()->set_gc_id(GCId::undefined());
}

GCIdMarkAndRestore::GCIdMarkAndRestore() : _gc_id(GCId::create()) {
  _previous_gc_id = GCId::current_raw();
  currentNamedthread()->set_gc_id(_gc_id);
}

GCIdMarkAndRestore::GCIdMarkAndRestore(uint gc_id) : _gc_id(gc_id) {
  _previous_gc_id = GCId::current_raw();
  currentNamedthread()->set_gc_id(_gc_id);
}

GCIdMarkAndRestore::~GCIdMarkAndRestore() {
  currentNamedthread()->set_gc_id(_previous_gc_id);
}
