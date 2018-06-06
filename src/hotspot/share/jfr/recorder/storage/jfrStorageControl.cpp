/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "jfr/recorder/storage/jfrStorageControl.hpp"
#include "runtime/atomic.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/orderAccess.hpp"

// returns the updated value
static jlong atomic_add(size_t value, size_t volatile* const dest) {
  size_t compare_value;
  size_t exchange_value;
  do {
    compare_value = OrderAccess::load_acquire(dest);
    exchange_value = compare_value + value;
  } while (Atomic::cmpxchg(exchange_value, dest, compare_value) != compare_value);
  return exchange_value;
}

static jlong atomic_dec(size_t volatile* const dest) {
  size_t compare_value;
  size_t exchange_value;
  do {
    compare_value = OrderAccess::load_acquire(dest);
    assert(compare_value >= 1, "invariant");
    exchange_value = compare_value - 1;
  } while (Atomic::cmpxchg(exchange_value, dest, compare_value) != compare_value);
  return exchange_value;
}

const size_t max_lease_factor = 2;
JfrStorageControl::JfrStorageControl(size_t global_count_total, size_t in_memory_discard_threshold) :
  _global_count_total(global_count_total),
  _full_count(0),
  _global_lease_count(0),
  _dead_count(0),
  _to_disk_threshold(0),
  _in_memory_discard_threshold(in_memory_discard_threshold),
  _global_lease_threshold(global_count_total / max_lease_factor),
  _scavenge_threshold(0),
  _to_disk(false) {}

bool JfrStorageControl::to_disk() const {
  return _to_disk;
}

void JfrStorageControl::set_to_disk(bool enable) {
  _to_disk = enable;
}

size_t JfrStorageControl::full_count() const {
  return _full_count;
}

// mutexed access
size_t JfrStorageControl::increment_full() {
  assert(JfrBuffer_lock->owned_by_self(), "invariant");
  return ++_full_count;
}

size_t JfrStorageControl::decrement_full() {
  assert(JfrBuffer_lock->owned_by_self(), "invariant");
  assert(_full_count > 0, "invariant");
  return --_full_count;
}

void JfrStorageControl::reset_full() {
  assert(JfrBuffer_lock->owned_by_self(), "invariant");
  _full_count = 0;
}

bool JfrStorageControl::should_post_buffer_full_message() const {
  return to_disk() && (full_count() > _to_disk_threshold);
}

bool JfrStorageControl::should_discard() const {
  return !to_disk() && full_count() >= _in_memory_discard_threshold;
}

// concurrent with accuracy requirement

size_t JfrStorageControl::global_lease_count() const {
  return OrderAccess::load_acquire(&_global_lease_count);
}

size_t JfrStorageControl::increment_leased() {
  return atomic_add(1, &_global_lease_count);
}

size_t JfrStorageControl::decrement_leased() {
  return atomic_dec(&_global_lease_count);
}

bool JfrStorageControl::is_global_lease_allowed() const {
  return global_lease_count() <= _global_lease_threshold;
}

// concurrent with lax requirement

size_t JfrStorageControl::dead_count() const {
  return _dead_count;
}

size_t JfrStorageControl::increment_dead() {
  return atomic_add(1, &_dead_count);
}

size_t JfrStorageControl::decrement_dead() {
  return atomic_dec(&_dead_count);
}

bool JfrStorageControl::should_scavenge() const {
  return dead_count() >= _scavenge_threshold;
}

void JfrStorageControl::set_scavenge_threshold(size_t number_of_dead_buffers) {
  _scavenge_threshold = number_of_dead_buffers;
}

