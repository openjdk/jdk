/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "jfr/jni/jfrJavaSupport.hpp"
#include "jfr/recorder/checkpoint/jfrCheckpointWriter.hpp"
#include "jfr/recorder/checkpoint/types/jfrThreadGroupManager.hpp"
#include "jfr/recorder/checkpoint/types/traceid/jfrTraceIdEpoch.hpp"
#include "jfr/utilities/jfrAllocation.hpp"
#include "jfr/utilities/jfrLinkedList.inline.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/jniHandles.inline.hpp"
#include "runtime/safepoint.hpp"
#include "runtime/semaphore.hpp"
#include "runtime/thread.inline.hpp"
#include "utilities/growableArray.hpp"

class ThreadGroupExclusiveAccess : public StackObj {
 private:
  static Semaphore _mutex_semaphore;
 public:
  ThreadGroupExclusiveAccess() { _mutex_semaphore.wait(); }
  ~ThreadGroupExclusiveAccess() { _mutex_semaphore.signal(); }
};

Semaphore ThreadGroupExclusiveAccess::_mutex_semaphore(1);

static traceid next_id() {
  static traceid _tgid = 1; // 1 is reserved for thread group "VirtualThreads"
  return ++_tgid;
}

class JfrThreadGroup : public JfrCHeapObj {
  template <typename, typename>
  friend class JfrLinkedList;
 private:
  mutable const JfrThreadGroup* _next;
  const JfrThreadGroup* _parent;
  traceid _tgid;
  char* _tg_name; // utf8 format
  jweak _tg_handle;
  mutable u2 _generation;

 public:
  JfrThreadGroup(Handle tg, const JfrThreadGroup* parent) :
    _next(nullptr), _parent(parent), _tgid(next_id()), _tg_name(nullptr),
    _tg_handle(JNIHandles::make_weak_global(tg)), _generation(0) {
    const char* name = java_lang_ThreadGroup::name(tg());
    if (name != nullptr) {
      const size_t len = strlen(name);
      _tg_name = JfrCHeapObj::new_array<char>(len + 1);
      strncpy(_tg_name, name, len + 1);
    }
  }

  ~JfrThreadGroup() {
    JNIHandles::destroy_weak_global(_tg_handle);
    if (_tg_name != nullptr) {
      JfrCHeapObj::free(_tg_name, strlen(_tg_name) + 1);
    }
  }

  const JfrThreadGroup* next() const { return _next; }

  traceid id() const { return _tgid; }

  const char* name() const {
    return _tg_name;
  }

  const JfrThreadGroup* parent() const { return _parent; }

  traceid parent_id() const {
    return _parent != nullptr ? _parent->id() : 0;
  }

  bool is_dead() const {
    return JNIHandles::resolve(_tg_handle) == nullptr;
  }

  bool operator==(oop tg) const {
    assert(tg != nullptr, "invariant");
    return tg == JNIHandles::resolve(_tg_handle);
  }

  bool should_write() const {
    return !JfrTraceIdEpoch::is_current_epoch_generation(_generation);
  }

  void set_written() const {
    assert(should_write(), "invariant");
    _generation = JfrTraceIdEpoch::epoch_generation();
  }
};

typedef JfrLinkedList<const JfrThreadGroup> JfrThreadGroupList;

static JfrThreadGroupList* _list = nullptr;

static JfrThreadGroupList& list() {
  assert(_list != nullptr, "invariant");
  return *_list;
}

bool JfrThreadGroupManager::create() {
  assert(_list == nullptr, "invariant");
  _list = new JfrThreadGroupList();
  return _list != nullptr;
}

void JfrThreadGroupManager::destroy() {
  delete _list;
  _list = nullptr;
}

static int populate(GrowableArray<Handle>* hierarchy, const JavaThread* jt, Thread* current) {
  assert(hierarchy != nullptr, "invariant");
  assert(jt != nullptr, "invariant");
  assert(current == Thread::current(), "invariant");

  oop thread_oop = jt->threadObj();
  if (thread_oop == nullptr) {
    return 0;
  }
  // Immediate thread group.
  const Handle tg_handle(current, java_lang_Thread::threadGroup(thread_oop));
  if (tg_handle.is_null()) {
    return 0;
  }
  hierarchy->append(tg_handle);

  // Thread group parent and then its parents...
  Handle parent_tg_handle(current, java_lang_ThreadGroup::parent(tg_handle()));

  while (parent_tg_handle != nullptr) {
    hierarchy->append(parent_tg_handle);
    parent_tg_handle = Handle(current, java_lang_ThreadGroup::parent(parent_tg_handle()));
  }

  return hierarchy->length();
}

class JfrThreadGroupLookup : public ResourceObj {
  static const int invalid_iterator = -1;
 private:
  GrowableArray<Handle>* _hierarchy;
  mutable int _iterator;

 public:
  JfrThreadGroupLookup(const JavaThread* jt, Thread* current) :
    _hierarchy(new GrowableArray<Handle>(16)),
    _iterator(populate(_hierarchy, jt, current) - 1) {}

  bool has_next() const {
    return _iterator > invalid_iterator;
  }

  const Handle& next() const {
    assert(has_next(), "invariant");
    return _hierarchy->at(_iterator--);
  }
};

static const JfrThreadGroup* find_or_add(const Handle& tg_oop, const JfrThreadGroup* parent) {
  assert(parent == nullptr || list().in_list(parent), "invariant");
  const JfrThreadGroup* tg = list().head();
  const JfrThreadGroup* result = nullptr;
  while (tg != nullptr) {
    if (*tg == tg_oop()) {
      assert(tg->parent() == parent, "invariant");
      result = tg;
      tg = nullptr;
      continue;
    }
    tg = tg->next();
  }
  if (result == nullptr) {
    result = new JfrThreadGroup(tg_oop, parent);
    list().add(result);
  }
  return result;
}

static traceid find_tgid(const JfrThreadGroupLookup& lookup) {
  const JfrThreadGroup* tg = nullptr;
  const JfrThreadGroup* ptg = nullptr;
  while (lookup.has_next()) {
    tg = find_or_add(lookup.next(), ptg);
    ptg = tg;
  }
  return tg != nullptr ? tg->id() : 0;
}

static traceid find(const JfrThreadGroupLookup& lookup) {
  ThreadGroupExclusiveAccess lock;
  return find_tgid(lookup);
}

traceid JfrThreadGroupManager::thread_group_id(JavaThread* jt) {
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_vm(jt);)
  ResourceMark rm(jt);
  HandleMark hm(jt);
  const JfrThreadGroupLookup lookup(jt, jt);
  return find(lookup);
}

traceid JfrThreadGroupManager::thread_group_id(const JavaThread* jt, Thread* current) {
  assert(jt != nullptr, "invariant");
  assert(current != nullptr, "invariant");
  assert(!current->is_Java_thread() || JavaThread::cast(current)->thread_state() == _thread_in_vm, "invariant");
  ResourceMark rm(current);
  HandleMark hm(current);
  const JfrThreadGroupLookup lookup(jt, current);
  return find(lookup);
}

static void write_virtual_thread_group(JfrCheckpointWriter& writer) {
  writer.write_key(1);      // 1 is reserved for VirtualThread group
  writer.write<traceid>(0); // parent
  const oop vgroup = java_lang_Thread_Constants::get_VTHREAD_GROUP();
  assert(vgroup != (oop)nullptr, "invariant");
  const char* const vgroup_name = java_lang_ThreadGroup::name(vgroup);
  assert(vgroup_name != nullptr, "invariant");
  writer.write(vgroup_name);
}

static int write_thread_group(JfrCheckpointWriter& writer, const JfrThreadGroup* tg, bool to_blob = false) {
  assert(tg != nullptr, "invariant");
  if (tg->should_write() || to_blob) {
    writer.write_key(tg->id());
    writer.write(tg->parent_id());
    writer.write(tg->name());
    if (!to_blob) {
      tg->set_written();
    }
    return 1;
  }
  return 0;
}

// For writing all live thread groups while removing and deleting dead thread groups.
void JfrThreadGroupManager::serialize(JfrCheckpointWriter& writer) {
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_vm(JavaThread::current());)

  const uint64_t count_offset = writer.reserve(sizeof(u4)); // Don't know how many yet

  // First write the pre-defined ThreadGroup for virtual threads.
  write_virtual_thread_group(writer);
  int number_of_groups_written = 1;

  const JfrThreadGroup* next = nullptr;
  const JfrThreadGroup* prev = nullptr;

  {
    ThreadGroupExclusiveAccess lock;
    const JfrThreadGroup* tg = list().head();
    while (tg != nullptr) {
      next = tg->next();
      if (tg->is_dead()) {
        prev = list().excise(prev, tg);
        assert(!list().in_list(tg), "invariant");
        delete tg;
        tg = next;
        continue;
      }
      number_of_groups_written += write_thread_group(writer, tg);
      prev = tg;
      tg = next;
    }
  }

  assert(number_of_groups_written > 0, "invariant");
  writer.write_count(number_of_groups_written, count_offset);
}

// For writing a specific thread group and its ancestry.
void JfrThreadGroupManager::serialize(JfrCheckpointWriter& writer, traceid tgid, bool to_blob) {
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_vm(JavaThread::current());)
  // save context
  const JfrCheckpointContext ctx = writer.context();

  writer.write_type(TYPE_THREADGROUP);
  const uint64_t count_offset = writer.reserve(sizeof(u4)); // Don't know how many yet

  int number_of_groups_written = 0;

  {
    ThreadGroupExclusiveAccess lock;
    const JfrThreadGroup* tg = list().head();
    while (tg != nullptr) {
      if (tgid == tg->id()) {
        while (tg != nullptr) {
          number_of_groups_written += write_thread_group(writer, tg, to_blob);
          tg = tg->parent();
        }
        break;
      }
      tg = tg->next();
    }
  }

  if (number_of_groups_written == 0) {
    // nothing to write, restore context
    writer.set_context(ctx);
    return;
  }

  assert(number_of_groups_written > 0, "invariant");
  writer.write_count(number_of_groups_written, count_offset);
}
