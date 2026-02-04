/*
* Copyright (c) 2026, Datadog, Inc. All rights reserved.
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

#ifndef SHARE_RUNTIME_STACKWALKER_INLINE_HPP
#define SHARE_RUNTIME_STACKWALKER_INLINE_HPP

#include "runtime/stackWalker.hpp"
#include "runtime/javaThread.hpp"

void StackWalker::check_and_process_requests(JavaThread* jt) {
  StackWalkerThreadLocal& tl = jt->stackwalker_thread_local();
  // Protect agains re-entrant calls. This can happen when we are
  // currently processing sampling requests and one is calling into
  // a JVMTI callback. Calling into a callback requires that we
  // transition the thread state from VM to native, which also
  // involves a safepoint check. That safepoint would then go ahead
  // and call into JFR again.
  if (tl.has_requests() && !tl.is_processing_requests()) {
    tl.set_processing_requests(true);
    process_requests(jt, jt, true);
    tl.set_processing_requests(false);
  }
}

#endif // SHARE_RUNTIME_STACKWALKER_INLINE_HPP