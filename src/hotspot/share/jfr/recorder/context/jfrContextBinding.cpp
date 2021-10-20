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
#include "jfr/recorder/context/jfrContextBinding.hpp"

JfrContextBinding::JfrContextBinding(const char** entries, int entries_len)
    : _entries_len(entries_len),
      _entries(JfrCHeapObj::new_array<JfrContextEntry>(_entries_len)) {
  assert(entries != NULL, "invariant");
  for (int i = 0; i < _entries_len; i++) {
    _entries[i] = JfrContextEntry(entries[i * 2 + 0], entries[i * 2 + 1]);
  }
}

JfrContextBinding::~JfrContextBinding() {
  JfrCHeapObj::free(_entries, sizeof(JfrContextEntry) * _entries_len);
}

bool JfrContextBinding::contains_key(const char* key) {
  for (int i = 0; i < _entries_len; i++) {
    if (_entries[i].contains_key(key)) {
      return true;
    }
  }
  return false;
}

void JfrContextBinding::set_current(JfrContextBinding* current) {
  JavaThread *thread = JavaThread::current();
  thread->set_jfr_context_binding(current);
}

JfrContextBinding* JfrContextBinding::current() {
  Thread *thread = Thread::current_or_null();
  if (!thread || !thread->is_Java_thread()) return NULL;
  return JavaThread::cast(thread)->jfr_context_binding();
}
