/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "memory/allocation.hpp"
#include "nmt/memTracker.hpp"
#include "nmt/memoryFileTracker.hpp"
#include "nmt/nmtCommon.hpp"
#include "nmt/nmtNativeCallStackStorage.hpp"
#include "nmt/vmatree.hpp"
#include "runtime/mutex.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/nativeCallStack.hpp"
#include "utilities/ostream.hpp"

MemoryFileTracker* MemoryFileTracker::Instance::_tracker = nullptr;
PlatformMutex* MemoryFileTracker::Instance::_mutex = nullptr;

MemoryFileTracker::MemoryFileTracker(bool is_detailed_mode)
  : _stack_storage(is_detailed_mode), _files() {}

void MemoryFileTracker::allocate_memory(MemoryFile* file, size_t offset,
                                        size_t size, const NativeCallStack& stack,
                                        MEMFLAGS flag) {
  NativeCallStackStorage::StackIndex sidx = _stack_storage.push(stack);
  VMATree::RegionData regiondata(sidx, flag);
  VMATree::SummaryDiff diff = file->_tree.commit_mapping(offset, size, regiondata);
  for (int i = 0; i < mt_number_of_types; i++) {
    VirtualMemory* summary = file->_summary.by_type(NMTUtil::index_to_flag(i));
    summary->reserve_memory(diff.flag[i].commit);
    summary->commit_memory(diff.flag[i].commit);
  }
}

void MemoryFileTracker::free_memory(MemoryFile* file, size_t offset, size_t size) {
  VMATree::SummaryDiff diff = file->_tree.release_mapping(offset, size);
  for (int i = 0; i < mt_number_of_types; i++) {
    VirtualMemory* summary = file->_summary.by_type(NMTUtil::index_to_flag(i));
    summary->reserve_memory(diff.flag[i].commit);
    summary->commit_memory(diff.flag[i].commit);
  }
}

void MemoryFileTracker::print_report_on(const MemoryFile* file, outputStream* stream, size_t scale) {
  assert(MemTracker::tracking_level() == NMT_detail, "must");

  stream->print_cr("Memory map of %s", file->_descriptive_name);
  stream->cr();
  VMATree::TreapNode* prev = nullptr;
#ifdef ASSERT
  VMATree::TreapNode* broken_start = nullptr;
  VMATree::TreapNode* broken_end = nullptr;
#endif
  file->_tree.visit_in_order([&](VMATree::TreapNode* current) {
    if (prev == nullptr) {
      // Must be first node.
      prev = current;
      return;
    }
#ifdef ASSERT
    if (broken_start != nullptr && prev->val().out.type() != current->val().in.type()) {
      broken_start = prev;
      broken_end = current;
    }
#endif
    if (prev->val().out.type() == VMATree::StateType::Committed) {
      const VMATree::position& start_addr = prev->key();
      const VMATree::position& end_addr = current->key();
      stream->print_cr("[" PTR_FORMAT " - " PTR_FORMAT "] allocated " SIZE_FORMAT "%s" " for %s from",
                       start_addr, end_addr,
                       NMTUtil::amount_in_scale(end_addr - start_addr, scale),
                       NMTUtil::scale_name(scale),
                       NMTUtil::flag_to_name(prev->val().out.flag()));
      {
        streamIndentor si(stream, 4);
        _stack_storage.get(prev->val().out.stack()).print_on(stream);
      }
      stream->cr();
    }
    prev = current;
  });
#ifdef ASSERT
  if (broken_start != nullptr) {
    tty->print_cr("Broken tree found with first occurrence at nodes %zu, %zu",
                  broken_start->key(), broken_end->key());
    tty->print_cr("Expected start out to have same type as end in, but was: %s, %s",
                  VMATree::statetype_to_string(broken_start->val().out.type()),
                  VMATree::statetype_to_string(broken_end->val().in.type()));
  }
#endif
}

MemoryFileTracker::MemoryFile* MemoryFileTracker::make_file(const char* descriptive_name) {
  MemoryFile* file_place = new MemoryFile{descriptive_name};
  _files.push(file_place);
  return file_place;
}

void MemoryFileTracker::free_file(MemoryFile* file) {
  if (file == nullptr) return;
  _files.remove(file);
  delete file;
}

const GrowableArrayCHeap<MemoryFileTracker::MemoryFile*, mtNMT>& MemoryFileTracker::files() {
  return _files;
}

bool MemoryFileTracker::Instance::initialize(NMT_TrackingLevel tracking_level) {
  if (tracking_level == NMT_TrackingLevel::NMT_off) return true;
  _tracker = static_cast<MemoryFileTracker*>(os::malloc(sizeof(MemoryFileTracker), mtNMT));
  if (_tracker == nullptr) return false;
  new (_tracker) MemoryFileTracker(tracking_level == NMT_TrackingLevel::NMT_detail);
  _mutex = new PlatformMutex();
  return true;
}

void MemoryFileTracker::Instance::allocate_memory(MemoryFile* file, size_t offset,
                                                  size_t size, const NativeCallStack& stack,
                                                  MEMFLAGS flag) {
  _tracker->allocate_memory(file, offset, size, stack, flag);
}

void MemoryFileTracker::Instance::free_memory(MemoryFile* file, size_t offset, size_t size) {
  _tracker->free_memory(file, offset, size);
}

MemoryFileTracker::MemoryFile*
MemoryFileTracker::Instance::make_file(const char* descriptive_name) {
  return _tracker->make_file(descriptive_name);
}

void MemoryFileTracker::Instance::free_file(MemoryFileTracker::MemoryFile* file) {
  return _tracker->free_file(file);
}

void MemoryFileTracker::Instance::print_report_on(const MemoryFile* file,
                                                  outputStream* stream, size_t scale) {
  assert(file != nullptr, "must be");
  assert(stream != nullptr, "must be");
  _tracker->print_report_on(file, stream, scale);
}

void MemoryFileTracker::Instance::print_all_reports_on(outputStream* stream, size_t scale) {
  const GrowableArrayCHeap<MemoryFileTracker::MemoryFile*, mtNMT>& files =
      MemoryFileTracker::Instance::files();
  stream->cr();
  stream->print_cr("Memory file details");
  stream->cr();
  for (int i = 0; i < files.length(); i++) {
    MemoryFileTracker::MemoryFile* file = files.at(i);
    MemoryFileTracker::Instance::print_report_on(file, stream, scale);
  }
}

const GrowableArrayCHeap<MemoryFileTracker::MemoryFile*, mtNMT>& MemoryFileTracker::Instance::files() {
  return _tracker->files();
};

void MemoryFileTracker::summary_snapshot(VirtualMemorySnapshot* snapshot) const {
  for (int d = 0; d < _files.length(); d++) {
    const MemoryFile* file = _files.at(d);
    for (int i = 0; i < mt_number_of_types; i++) {
      VirtualMemory* snap = snapshot->by_type(NMTUtil::index_to_flag(i));
      const VirtualMemory* current = file->_summary.by_type(NMTUtil::index_to_flag(i));
      // Only account the committed memory.
      snap->commit_memory(current->committed());
    }
  }
}

void MemoryFileTracker::Instance::summary_snapshot(VirtualMemorySnapshot* snapshot) {
  _tracker->summary_snapshot(snapshot);
}

MemoryFileTracker::Instance::Locker::Locker() {
  MemoryFileTracker::Instance::_mutex->lock();
}

MemoryFileTracker::Instance::Locker::~Locker() {
  MemoryFileTracker::Instance::_mutex->unlock();
}
