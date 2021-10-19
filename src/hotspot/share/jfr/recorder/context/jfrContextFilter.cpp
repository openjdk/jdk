/*
 * Copyright (c) 2021, Datadog, Inc. All rights reserved.
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

#include "precompiled.hpp"
#include "runtime/thread.hpp"
#include "jfr/recorder/context/jfrContext.hpp"
#include "jfr/recorder/context/jfrContextFilter.hpp"

void JfrContextFilter::set_current(JfrContextFilter* current) {
  JavaThread *thread = JavaThread::current();
  thread->set_jfr_context_filter(current);
}

JfrContextFilter* JfrContextFilter::current() {
  Thread *thread = Thread::current_or_null();
  if (!thread || !thread->is_Java_thread()) return NULL;
  return JavaThread::cast(thread)->jfr_context_filter();
}

void JfrContextFilter::configure(JfrEventId event_id, bool matches_filter) {
  JfrContextFilter *current = JfrContextFilter::current();
  if (!current) current = new JfrContextFilter();
  current->_matches_filter = matches_filter;
  JfrContextFilter::set_current(current);
}

bool JfrContextFilter::accept(JfrEventId event_id) {
  JfrContextFilter *current = JfrContextFilter::current();
  if (!current) return true;
  return current->_matches_filter;
}
