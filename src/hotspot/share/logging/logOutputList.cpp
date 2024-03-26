/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "logging/logLevel.hpp"
#include "logging/logOutputList.hpp"
#include "memory/allocation.inline.hpp"
#include "runtime/atomic.hpp"
#include "runtime/orderAccess.hpp"
#include "utilities/globalDefinitions.hpp"

jint LogOutputList::increase_readers() {
  jint result = Atomic::add(&_active_readers, 1);
  assert(_active_readers > 0, "Ensure we have consistent state");
  return result;
}

jint LogOutputList::decrease_readers() {
  jint result = Atomic::add(&_active_readers, -1);
  assert(result >= 0, "Ensure we have consistent state");
  return result;
}

void LogOutputList::wait_until_no_readers() const {
  OrderAccess::storeload();
  while (Atomic::load(&_active_readers) != 0) {
    // Busy wait
  }
  // Prevent mutations to the output list to float above the active reader check.
  // Such a reordering would lead to readers loading faulty data.
  OrderAccess::loadstore();
}

void LogOutputList::set_output_level(LogOutput* output, LogLevelType level) {
  assert(output != nullptr, "LogOutput is null");
  LogOutputNode* node = find(output);
  if (level == LogLevel::Off && node != nullptr) {
    remove_output(node);
  } else if (level != LogLevel::Off && node == nullptr) {
    add_output(output, level);
  } else if (node != nullptr) {
    update_output_level(node, level);
  }
}

LogOutputList::LogOutputNode* LogOutputList::find(const LogOutput* output) const {
  for (LogOutputNode* node = _level_start[LogLevel::Last]; node != nullptr; node = node->_next) {
    if (output == node->_value) {
      return node;
    }
  }
  return nullptr;
}

void LogOutputList::clear() {

  // Grab the linked list
  LogOutputNode* cur = _level_start[LogLevel::Last];

  // Clear _level_start
  for (uint level = LogLevel::First; level < LogLevel::Count; level++) {
    _level_start[level] = nullptr;
  }

  // Delete all nodes from the linked list
  wait_until_no_readers();
  while (cur != nullptr) {
    LogOutputNode* next = cur->_next;
    delete cur;
    cur = next;
  }
}

void LogOutputList::remove_output(LogOutputList::LogOutputNode* node) {
  assert(node != nullptr, "Node must be non-null");

  // Remove node from _level_start first
  bool found = false;
  for (uint level = LogLevel::First; level < LogLevel::Count; level++) {
    if (_level_start[level] == node) {
      found = true;
      _level_start[level] = node->_next;
    }
  }

  // Now remove it from the linked list
  for (LogOutputNode* cur = _level_start[LogLevel::Last]; cur != nullptr; cur = cur->_next) {
    if (cur->_next == node) {
      found = true;
      cur->_next = node->_next;
      break;
    }
  }
  assert(found, "Node to be removed should always be found");

  wait_until_no_readers();
  delete node;
}

void LogOutputList::add_output(LogOutput* output, LogLevelType level) {
  LogOutputNode* node = new LogOutputNode();
  node->_value = output;
  node->_level = level;

  // Set the next pointer to the first node of a lower level
  for (node->_next = _level_start[level];
       node->_next != nullptr && node->_next->_level == level;
       node->_next = node->_next->_next) {
  }

  // To allow lock-free iteration of the output list the updates in the loops
  // below require release semantics.
  OrderAccess::release();

  // Update the _level_start index
  for (int l = LogLevel::Last; l >= level; l--) {
    LogOutputNode* lnode = Atomic::load(&_level_start[l]);
    if (lnode == nullptr || lnode->_level < level) {
      Atomic::store(&_level_start[l], node);
    }
  }

  // Add the node the list
  for (LogOutputNode* cur = Atomic::load(&_level_start[LogLevel::Last]); cur != nullptr; cur = Atomic::load(&cur->_next)) {
    if (cur != node && Atomic::load(&cur->_next) == node->_next) {
      Atomic::store(&cur->_next, node);
      break;
    }
  }
}

void LogOutputList::update_output_level(LogOutputList::LogOutputNode* node, LogLevelType level) {
  add_output(node->_value, level);
  wait_until_no_readers();
  remove_output(node);
}
