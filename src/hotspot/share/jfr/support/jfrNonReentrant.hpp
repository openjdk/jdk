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

#ifndef SHARE_JFR_SUPPORT_JFRNONREENTRANT_HPP
#define SHARE_JFR_SUPPORT_JFRNONREENTRANT_HPP

#include "runtime/thread.hpp"

template <typename EventType>
class JfrNonReentrant : public EventType {
 private:
  Thread* const _thread;
  int32_t _previous_nesting;
 public:
  JfrNonReentrant(EventStartTime timing = TIMED) :
    EventType(timing), _thread(Thread::current()),
    _previous_nesting(JfrThreadLocal::make_non_reentrant(_thread)) {
    assert(_thread != nullptr, "invariant");
  }

  JfrNonReentrant(Thread* thread, EventStartTime timing = TIMED) :
    EventType(timing), _thread(thread),
    _previous_nesting(JfrThreadLocal::make_non_reentrant(_thread)) {
    assert(_thread != nullptr, "invariant");
  }

  ~JfrNonReentrant() {
    if (_previous_nesting != -1) {
      JfrThreadLocal::make_reentrant(_thread, _previous_nesting);
    }
  }
};

#endif // SHARE_JFR_SUPPORT_JFRNONREENTRANT_HPP
