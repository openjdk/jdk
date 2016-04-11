/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
#ifndef SHARE_VM_LOGGING_LOGSTREAM_INLINE_HPP
#define SHARE_VM_LOGGING_LOGSTREAM_INLINE_HPP

#include "logging/log.hpp"
#include "logging/logHandle.hpp"
#include "logging/logStream.hpp"
#include "memory/resourceArea.hpp"
#include "utilities/ostream.hpp"

template <class streamClass>
inline void LogStreamBase<streamClass>::write(const char* s, size_t len) {
  if (len > 0 && s[len - 1] == '\n') {
    _current_line.write(s, len - 1);
    _current_line.write("\0", 1);
    _log_handle.print("%s", _current_line.base());
    _current_line.reset();
  } else {
    _current_line.write(s, len);
  }
  update_position(s, len);
}

#endif // SHARE_VM_LOGGING_LOGSTREAM_INLINE_HPP
