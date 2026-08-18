/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_JFR_UTILITIES_JFREPOCHSHIFTLOCK_HPP
#define SHARE_JFR_UTILITIES_JFREPOCHSHIFTLOCK_HPP

/*
 * This lock is the lightweight mutex JFR uses when issuing an epoch shift.
 * Its purpose is to coordinate with non-Java threads, so they do not write
 * an event that could interleave with the epoch shift.
 *
 * Since a JFR epoch shift always happens during a safepoint, JavaThreads
 * are excluded already and need not coordinate over this lock.
 *
* If you are writing an event in a non-Java thread,  outside of a stop-the-world VM operation,
* an event that uses non-primitive types, you should ensure you acquire this lock before calling event.commit().
*
* Non-primitive types (e.g. InstanceKlass, Method, CLD, etc.) are tagged as part of commit() with
* epoch-relative bits and must therfore be guarded not to interleave with a concurrent epoch shift.
*
* Currently, this lock is a nop unless using a concurrent GC, such as Shenandoah or ZGC.
*
*/

#include "memory/allocation.hpp"

class Thread;

class JfrEpochShiftLock : StackObj {
 private:
  const bool _acquired;
 public:
  JfrEpochShiftLock(Thread* t);
  JfrEpochShiftLock();
  ~JfrEpochShiftLock();

  bool acquired() const {
    return _acquired;
  }
};

#endif // SHARE_JFR_UTILITIES_JFREPOCHSHIFTLOCK_HPP
