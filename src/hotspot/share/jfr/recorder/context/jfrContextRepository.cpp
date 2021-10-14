/*
 * Copyright (c) 2011, 2021, Oracle and/or its affiliates. All rights reserved.
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
#include "jfr/metadata/jfrSerializer.hpp"
#include "jfr/recorder/checkpoint/jfrCheckpointWriter.hpp"
#include "jfr/recorder/repository/jfrChunkWriter.hpp"
#include "jfr/recorder/context/jfrContextRepository.hpp"
#include "jfr/support/jfrThreadLocal.hpp"
#include "runtime/mutexLocker.hpp"

/*
 * There are two separate repository instances.
 * One instance is dedicated to contexts taken as part of the leak profiler subsystem.
 * It is kept separate because at the point of insertion, it is unclear if a trace will be serialized,
 * which is a decision postponed and taken during rotation.
 */

static JfrContextRepository* _instance = NULL;
static JfrContextRepository* _leak_profiler_instance = NULL;
static traceid _next_id = 0;

JfrContextRepository& JfrContextRepository::instance() {
  assert(_instance != NULL, "invariant");
  return *_instance;
}

static JfrContextRepository& leak_profiler_instance() {
  assert(_leak_profiler_instance != NULL, "invariant");
  return *_leak_profiler_instance;
}

JfrContextRepository::JfrContextRepository() : _last_entries(0), _entries(0) {
  memset(_table, 0, sizeof(_table));
}

JfrContextRepository* JfrContextRepository::create() {
  assert(_instance == NULL, "invariant");
  assert(_leak_profiler_instance == NULL, "invariant");
  _leak_profiler_instance = new JfrContextRepository();
  if (_leak_profiler_instance == NULL) {
    return NULL;
  }
  _instance = new JfrContextRepository();
  return _instance;
}

bool JfrContextRepository::initialize() {
  if (!JfrContext::initialize()) {
    return false;
  }
  return true;
}

void JfrContextRepository::destroy() {
  assert(_instance != NULL, "invarinat");
  delete _instance;
  _instance = NULL;
  delete _leak_profiler_instance;
  _leak_profiler_instance = NULL;
}

bool JfrContextRepository::is_modified() const {
  return _last_entries != _entries;
}

size_t JfrContextRepository::write(JfrChunkWriter& sw, bool clear) {
  if (_entries == 0) {
    return 0;
  }
  MutexLocker lock(JfrContext_lock, Mutex::_no_safepoint_check_flag);
  assert(_entries > 0, "invariant");
  int count = 0;
  for (u4 i = 0; i < TABLE_SIZE; ++i) {
    JfrContext* context = _table[i];
    while (context != NULL) {
      JfrContext* next = const_cast<JfrContext*>(context->next());
      if (context->should_write()) {
        context->write(sw);
        ++count;
      }
      if (clear) {
        delete context;
      }
      context = next;
    }
  }
  if (clear) {
    memset(_table, 0, sizeof(_table));
    _entries = 0;
  }
  _last_entries = _entries;
  return count;
}

size_t JfrContextRepository::clear(JfrContextRepository& repo) {
  MutexLocker lock(JfrContext_lock, Mutex::_no_safepoint_check_flag);
  if (repo._entries == 0) {
    return 0;
  }
  for (u4 i = 0; i < TABLE_SIZE; ++i) {
    JfrContext* context = repo._table[i];
    while (context != NULL) {
      JfrContext* next = const_cast<JfrContext*>(context->next());
      delete context;
      context = next;
    }
  }
  memset(repo._table, 0, sizeof(repo._table));
  const size_t processed = repo._entries;
  repo._entries = 0;
  repo._last_entries = 0;
  return processed;
}

traceid JfrContextRepository::record(Thread* thread, int skip /* 0 */) {
  assert(thread == Thread::current(), "invariant");
  JfrThreadLocal* const tl = thread->jfr_thread_local();
  assert(tl != NULL, "invariant");
  if (tl->has_cached_context()) {
    return tl->cached_context_id();
  }
  if (!thread->is_Java_thread() || thread->is_hidden_from_external_view() || tl->is_excluded()) {
    return 0;
  }
  JfrContextEntry* contextentries = tl->contextentries();
  if (contextentries == NULL) {
    // pending oom
    return 0;
  }
  assert(contextentries != NULL, "invariant");
  assert(tl->contextentries() == contextentries, "invariant");
  return instance().record_for(JavaThread::cast(thread), skip, contextentries, tl->contextsize());
}

traceid JfrContextRepository::record_for(JavaThread* thread, int skip, JfrContextEntry *contextentries, u4 max_contextentries) {
  JfrContext context(contextentries, max_contextentries);
  return context.record_safe(thread, skip) ? add(instance(), context) : 0;
}
traceid JfrContextRepository::add(JfrContextRepository& repo, const JfrContext& context) {
  traceid tid = repo.add_context(context);
  if (tid == 0) {
    tid = repo.add_context(context);
  }
  assert(tid != 0, "invariant");
  return tid;
}

traceid JfrContextRepository::add(const JfrContext& context) {
  return add(instance(), context);
}

void JfrContextRepository::record_for_leak_profiler(JavaThread* thread, int skip /* 0 */) {
  assert(thread != NULL, "invariant");
  JfrThreadLocal* const tl = thread->jfr_thread_local();
  assert(tl != NULL, "invariant");
  assert(!tl->has_cached_context(), "invariant");
  JfrContext context(tl->contextentries(), tl->contextsize());
  context.record_safe(thread, skip);
  const unsigned int hash = context.hash();
  if (hash != 0) {
    tl->set_cached_context_id(add(leak_profiler_instance(), context), hash);
  }
}

traceid JfrContextRepository::add_context(const JfrContext& context) {
  MutexLocker lock(JfrContext_lock, Mutex::_no_safepoint_check_flag);
  const size_t index = context._hash % TABLE_SIZE;
  const JfrContext* table_entry = _table[index];

  while (table_entry != NULL) {
    if (table_entry->equals(context)) {
      return table_entry->id();
    }
    table_entry = table_entry->next();
  }

  traceid id = ++_next_id;
  _table[index] = new JfrContext(id, context, _table[index]);
  ++_entries;
  return id;
}

// invariant is that the entry to be resolved actually exists in the table
const JfrContext* JfrContextRepository::lookup_for_leak_profiler(unsigned int hash, traceid id) {
  const size_t index = (hash % TABLE_SIZE);
  const JfrContext* trace = leak_profiler_instance()._table[index];
  while (trace != NULL && trace->id() != id) {
    trace = trace->next();
  }
  assert(trace != NULL, "invariant");
  assert(trace->hash() == hash, "invariant");
  assert(trace->id() == id, "invariant");
  return trace;
}

void JfrContextRepository::clear_leak_profiler() {
  clear(leak_profiler_instance());
}

size_t JfrContextRepository::clear() {
  clear_leak_profiler();
  return clear(instance());
}
