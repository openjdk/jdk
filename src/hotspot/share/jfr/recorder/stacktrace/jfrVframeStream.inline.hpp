/*
 * Copyright (c) 2011, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_JFR_RECORDER_STACKTRACE_JFRVFRAMESTREAM_INLINE_HPP
#define SHARE_JFR_RECORDER_STACKTRACE_JFRVFRAMESTREAM_INLINE_HPP

#include "jfr/recorder/stacktrace/jfrVframeStream.hpp"
#include "runtime/continuationEntry.inline.hpp"
#include "runtime/vframe.inline.hpp"

inline void JfrVframeStream::next_frame() {
  do {
    if (_vthread && Continuation::is_continuation_enterSpecial(_frame)) {
      if (_cont_entry->is_virtual_thread()) {
        // An entry of a vthread continuation is a termination point.
        _mode = at_end_mode;
        break;
      }
      _cont_entry = _cont_entry->parent();
    }

    _frame = _frame.sender(&_reg_map);

  } while (!fill_from_frame());
}

inline void JfrVframeStream::next_vframe() {
  // handle frames with inlining
  if (_mode == compiled_mode && fill_in_compiled_inlined_sender()) {
    return;
  }
  next_frame();
}

#endif // SHARE_JFR_RECORDER_STACKTRACE_JFRVFRAMESTREAM_INLINE_HPP
