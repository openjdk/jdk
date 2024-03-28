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

#ifndef SHARE_NMT_NMTMEMORYFILETRACKER_HPP
#define SHARE_NMT_NMTMEMORYFILETRACKER_HPP

#include "memory/allocation.hpp"
#include "nmt/nmtCommon.hpp"
#include "nmt/nmtNativeCallStackStorage.hpp"
#include "nmt/virtualMemoryTracker.hpp"
#include "nmt/vmatree.hpp"
#include "runtime/mutex.hpp"
#include "runtime/os.inline.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/nativeCallStack.hpp"
#include "utilities/ostream.hpp"

// The PhysicalDeviceTracker tracks memory of 'physical devices',
// storage with its own memory space separate from the process.
// A typical example of such a device is a memory mapped file.
class MemoryFileTracker {
  friend class MemoryFileTrackerTest;
  // Provide caching of stacks.
  NativeCallStackStorage _stack_storage;

  // Each device has its own memory space.
  using DeviceSpace = VMATree;
public:
  class MemoryFile : public CHeapObj<mtNMT> {
    friend MemoryFileTracker;
    friend class MemoryFileTrackerTest;
    const char* _descriptive_name;
    VirtualMemorySnapshot _summary;
    DeviceSpace _tree;
  public:
    NONCOPYABLE(MemoryFile);
    MemoryFile(const char* descriptive_name)
      : _descriptive_name(descriptive_name) {
    }
  };

private:
  // We need pointers to each allocated device.
  GrowableArrayCHeap<MemoryFile*, mtNMT> _devices;

public:
  MemoryFileTracker(bool is_detailed_mode);

  void allocate_memory(MemoryFile* device, size_t offset, size_t size, MEMFLAGS flag,
                       const NativeCallStack& stack);
  void free_memory(MemoryFile* device, size_t offset, size_t size);

  MemoryFile* make_device(const char* descriptive_name);
  void free_device(MemoryFile* device);

  const VirtualMemorySnapshot& summary_for(const MemoryFile* device);

  void summary_snapshot(VirtualMemorySnapshot* snapshot) const;

  // Print detailed report of device
  void print_report_on(const MemoryFile* device, outputStream* stream, size_t scale);

  const GrowableArrayCHeap<MemoryFile*, mtNMT>& devices();

  class Instance : public AllStatic {
    static MemoryFileTracker* _tracker;
    static PlatformMutex* _mutex;

  public:
    class Locker : public StackObj {
    public:
      Locker();
      ~Locker();
    };

    static bool initialize(NMT_TrackingLevel tracking_level);

    static MemoryFile* make_device(const char* descriptive_name);
    static void free_device(MemoryFile* device);

    static void allocate_memory(MemoryFile* device, size_t offset, size_t size,
                                MEMFLAGS flag, const NativeCallStack& stack);
    static void free_memory(MemoryFile* device, size_t offset, size_t size);

    static const VirtualMemorySnapshot& summary_for(const MemoryFile* device);

    static void summary_snapshot(VirtualMemorySnapshot* snapshot);

    static void print_report_on(const MemoryFile* device, outputStream* stream, size_t scale);

    static const GrowableArrayCHeap<MemoryFile*, mtNMT>& devices();
  };
};

#endif // SHARE_NMT_NMTMEMORYFILETRACKER_HPP
