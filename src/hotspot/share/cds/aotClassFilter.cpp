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

#include "cds/aotClassFilter.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/mutexLocker.hpp"

AOTClassFilter::FilterMark* AOTClassFilter::_current_mark = nullptr;
Thread* AOTClassFilter::_filtering_thread = nullptr;

AOTClassFilter::FilterMark::FilterMark() {
  MutexLocker ml(DumpTimeTable_lock, Mutex::_no_safepoint_check_flag);
  assert(_current_mark == nullptr &&_filtering_thread == nullptr,
         "impl note: we support only a single AOTClassFilter used by a single thread");
  _current_mark = this;
  _filtering_thread = Thread::current();
}

AOTClassFilter::FilterMark::~FilterMark() {
  MutexLocker ml(DumpTimeTable_lock, Mutex::_no_safepoint_check_flag);
  assert(_current_mark == this && _filtering_thread == Thread::current(), "sanity");
  _current_mark = nullptr;
  _filtering_thread = nullptr;
}

// Is called only from SystemDictionaryShared::init_dumptime_info(), which holds DumpTimeTable_lock
bool AOTClassFilter::is_aot_tooling_class(InstanceKlass* ik) {
  assert_lock_strong(DumpTimeTable_lock);
  if (_current_mark == nullptr || _filtering_thread != Thread::current()) {
    return false;
  } else {
    return _current_mark->is_aot_tooling_class(ik);
  }
}
