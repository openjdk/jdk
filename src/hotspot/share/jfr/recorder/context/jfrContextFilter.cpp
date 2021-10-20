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


JfrContextFilter::JfrContextFilter()
    : _matches_set(false) {
}

JfrContextFilter::~JfrContextFilter() {
}

JfrContextFilter* JfrContextFilter::current() {
  Thread *thread = Thread::current_or_null();
  if (!thread || !thread->is_Java_thread()) return NULL;
  return JavaThread::cast(thread)->jfr_context_filter();
}

void JfrContextFilter::set_current(JfrContextFilter* context) {
  JavaThread *thread = JavaThread::current();
  thread->set_jfr_context_filter(context);
}

bool JfrContextFilter::accept(JfrEventId event_id) {
  JfrContextFilter *current = JfrContextFilter::current();
  if (!current || !current->_matches_set) {
    // There are no filters, it matches by default
    return true;
  }
  assert(FIRST_EVENT_ID <= event_id && event_id <= LAST_EVENT_ID, "should not reach here");
  if (current->_matches[event_id + 1] != -1) {
    return current->_matches[event_id + 1] != 0;
  }
  return current->_matches[0] == 1;
}

void JfrContextFilter::configure(int *matches, int matches_len) {
  JfrContextFilter *current = JfrContextFilter::current();
  if (!current) {
    JfrContextFilter::set_current(current = new JfrContextFilter());
  }
  if (!matches) {
    current->_matches_set = false;
    return;
  }
  assert(matches_len > 0, "invariant");
  // Because the matches are ordered by event id, and we know the predefined type ids have
  // lower event ids, we can first deal with them, then with the dynamically declared ones
  // Set current->_matches
  memset(current->_matches, (char)-1, sizeof(current->_matches));
  for (int i = 0; i < matches_len && matches[i+0] <= LAST_EVENT_ID; i += 2) {
    current->_matches[matches[i+0] + 1] = matches[i+1];
  }
  current->_matches_set = true;
  // Ignore non pre-defined events, we assert on this case in JfrContextFilter::accept
}
