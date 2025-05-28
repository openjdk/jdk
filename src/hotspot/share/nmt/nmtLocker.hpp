/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_NMT_NMTLOCKER_HPP
#define SHARE_NMT_NMTLOCKER_HPP

#include "runtime/mutexLocker.hpp"

/*
   * NmtVirtualMemoryLocker is similar to MutexLocker but can be used during VM init before mutexes are ready or
   * current thread has been assigned. Performs no action during VM init.
   *
   * Unlike malloc, NMT requires locking for virtual memory operations. This is because it must synchronize the usage
   * of global data structures used for modelling the effect of virtual memory operations.
   * It is important that locking is used such that the actual OS memory operations (mmap) are done atomically with the
   * corresponding NMT accounting (updating the internal model). Currently, this is not the case in all situations
   * (see JDK-8341491), but this should be changed in the future.
   *
   * An issue with using Mutex is that NMT is used early during VM initialization before mutexes are initialized
   * and current thread is attached. Mutexes do not work under those conditions, so we must use a flag to avoid
   * attempting to lock until initialization is finished. Lack of synchronization here should not be a problem since it
   * is single threaded at that point in time anyway.
   */
class NmtVirtualMemoryLocker : StackObj {
  // Returns true if it is safe to start using this locker.
  static bool _safe_to_use;
  ConditionalMutexLocker _cml;

public:
  NmtVirtualMemoryLocker()
    : _cml(NmtVirtualMemory_lock, _safe_to_use, Mutex::_no_safepoint_check_flag) {
  }

  static inline bool is_safe_to_use() {
    return _safe_to_use;
  }

  // Set in Threads::create_vm once threads and mutexes have been initialized.
  static inline void set_safe_to_use() {
    _safe_to_use = true;
  }
};

class NmtMemTagLocker : StackObj {
  // Returns true if it is safe to start using this locker.
  static bool _safe_to_use;
  ConditionalMutexLocker _cml;

public:
  NmtMemTagLocker()
    : _cml(NmtMemTag_lock, _safe_to_use, Mutex::_no_safepoint_check_flag) {
  }

  static inline bool is_safe_to_use() {
    return _safe_to_use;
  }

  // Set in Threads::create_vm once threads and mutexes have been initialized.
  static inline void set_safe_to_use() {
    _safe_to_use = true;
  }
};


#endif // SHARE_NMT_NMTLOCKER_HPP
