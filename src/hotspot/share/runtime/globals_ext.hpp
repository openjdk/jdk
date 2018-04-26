/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_RUNTIME_GLOBALS_EXT_HPP
#define SHARE_VM_RUNTIME_GLOBALS_EXT_HPP

#include "runtime/flags/jvmFlag.hpp"

// globals_extension.hpp extension

// Additional JVMFlags enum values
#define JVMFLAGS_EXT

// Additional JVMFlagsWithType enum values
#define JVMFLAGSWITHTYPE_EXT


// globals.cpp extension

// Additional flag definitions
#define MATERIALIZE_FLAGS_EXT

// Additional flag descriptors: see flagTable definition
#define FLAGTABLE_EXT


// Default method implementations

inline bool JVMFlag::is_unlocker_ext() const {
  return false;
}

inline bool JVMFlag::is_unlocked_ext() const {
  return true;
}

inline bool JVMFlag::is_writeable_ext() const {
  return false;
}

inline bool JVMFlag::is_external_ext() const {
  return false;
}

inline JVMFlag::MsgType JVMFlag::get_locked_message_ext(char* buf, int buflen) const {
  assert(buf != NULL, "Buffer cannot be NULL");
  buf[0] = '\0';
  return JVMFlag::NONE;
}

#endif // SHARE_VM_RUNTIME_GLOBALS_EXT_HPP
