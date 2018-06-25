/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2018 SAP SE. All rights reserved.
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

#ifndef SHARE_VM_RUNTIME_THREADSTATISTICS_HPP
#define SHARE_VM_RUNTIME_THREADSTATISTICS_HPP

#include "jni.h"
#include "runtime/os.hpp"
#include "utilities/globalDefinitions.hpp"


class ThreadStatisticalInfo {
  // The time stamp the thread was started.
  const uint64_t _start_time_stamp;
  uint64_t _define_class_count;

public:
  ThreadStatisticalInfo() : _start_time_stamp(os::javaTimeMillis()), _define_class_count(0) {}
  uint64_t getStartTime() const             { return _start_time_stamp; }
  uint64_t getDefineClassCount() const                    { return  _define_class_count; }
  void     setDefineClassCount(uint64_t defineClassCount) { _define_class_count = defineClassCount; }
  void     incr_define_class_count()                      { _define_class_count += 1; }
  uint64_t getElapsedTime() const           { return os::javaTimeMillis() - getStartTime(); }
};

#endif // SHARE_VM_RUNTIME_THREADSTATISTICS_HPP
