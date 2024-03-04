/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, Datadog, Inc. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 */

#include "precompiled.hpp"

#include "jfr/jni/jfrJavaSupport.hpp"
#include "jfr/support/jfrContext.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/threads.hpp"
#include "utilities/debug.hpp"

// It will mark the current context as in use
// If there is no JfrContext instance associated with the current thread yet, it will do nothing
void JfrContext::mark_context_in_use() {
  JavaThread* const jt = JavaThread::current();
  assert(jt != nullptr, "invariant");
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_native(jt));
  JfrThreadLocal* const tl = jt->jfr_thread_local();
  assert(tl != nullptr, "invariant");
  if (tl->has_context()) {
    tl->get_context()->mark_context_in_use();
  }
}

void JfrContext::mark_context_in_use(JfrThreadLocal* tl) {
  assert(tl != nullptr, "invariant");
  if (tl->has_context()) {
    tl->get_context()->mark_context_in_use();
  }
}

u8 JfrContext::open() {
  JavaThread* const jt = JavaThread::current();
  assert(jt != nullptr, "invariant");
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_native(jt));
  JfrThreadLocal* const tl = jt->jfr_thread_local();
  assert(tl != nullptr, "invariant");
  JfrThreadContext* ctx = tl->get_context();
  ctx->open();
  return ctx->offset();
}

u8 JfrContext::close() {
  JavaThread* const jt = JavaThread::current();
  assert(jt != nullptr, "invariant");
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_native(jt));
  JfrThreadLocal* const tl = jt->jfr_thread_local();
  assert(tl != nullptr, "invariant");
  JfrThreadContext* ctx = tl->get_context();
  ctx->close();
  return ctx->offset();
}

u8 JfrContext::swap(u8 other) {
  JavaThread* const jt = JavaThread::current();
  assert(jt != nullptr, "invariant");
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_native(jt));
  JfrThreadLocal* const tl = jt->jfr_thread_local();
  assert(tl != nullptr, "invariant");
  JfrThreadContext* ctx = tl->get_context();
  return ctx->swap(other);
}

bool JfrContext::is_present() {
  JavaThread* const jt = JavaThread::current();
  assert(jt != nullptr, "invariant");
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_native(jt));
  JfrThreadLocal* const tl = jt->jfr_thread_local();
  assert(tl != nullptr, "invariant");
  if (!tl->has_context()) {
    return false;
  }
  return tl->get_context()->is_active();
}

bool JfrContext::is_present(JfrThreadLocal* tl) {
  assert(tl != nullptr, "invariant");
  if (!tl->has_context()) {
    return false;
  }
  return tl->get_context()->is_active();
}
