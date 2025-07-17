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

#include "classfile/javaClasses.inline.hpp"
#include "jfr/recorder/checkpoint/types/jfrThreadState.hpp"
#include "jfr/recorder/checkpoint/jfrCheckpointWriter.hpp"
#include "jfr/support/jfrThreadLocal.hpp"
#include "jvmtifiles/jvmti.h"
#include "runtime/javaThread.hpp"
#include "runtime/osThread.hpp"

struct jvmti_thread_state {
  u8 id;
  const char* description;
};

static jvmti_thread_state states[] = {
  {
    JVMTI_JAVA_LANG_THREAD_STATE_NEW,
    "STATE_NEW"
  },
  {
    JVMTI_THREAD_STATE_TERMINATED,
    "STATE_TERMINATED"
  },
  {
    JVMTI_JAVA_LANG_THREAD_STATE_RUNNABLE,
    "STATE_RUNNABLE"
  },
  {
    (JVMTI_THREAD_STATE_ALIVE | JVMTI_THREAD_STATE_WAITING | JVMTI_THREAD_STATE_WAITING_WITH_TIMEOUT | JVMTI_THREAD_STATE_SLEEPING),
    "STATE_SLEEPING"
  },
  {
    (JVMTI_THREAD_STATE_ALIVE | JVMTI_THREAD_STATE_WAITING | JVMTI_THREAD_STATE_WAITING_INDEFINITELY | JVMTI_THREAD_STATE_IN_OBJECT_WAIT),
    "STATE_IN_OBJECT_WAIT"
  },
  {
    (JVMTI_THREAD_STATE_ALIVE | JVMTI_THREAD_STATE_WAITING | JVMTI_THREAD_STATE_WAITING_WITH_TIMEOUT | JVMTI_THREAD_STATE_IN_OBJECT_WAIT),
    "STATE_IN_OBJECT_WAIT_TIMED"
  },
  {
    (JVMTI_THREAD_STATE_ALIVE | JVMTI_THREAD_STATE_WAITING | JVMTI_THREAD_STATE_WAITING_INDEFINITELY | JVMTI_THREAD_STATE_PARKED),
    "STATE_PARKED"
  },
  {
    (JVMTI_THREAD_STATE_ALIVE | JVMTI_THREAD_STATE_WAITING | JVMTI_THREAD_STATE_WAITING_WITH_TIMEOUT | JVMTI_THREAD_STATE_PARKED),
    "STATE_PARKED_TIMED"
  },
  {
    JVMTI_JAVA_LANG_THREAD_STATE_BLOCKED,
    "STATE_BLOCKED_ON_MONITOR_ENTER"
  }
};

void JfrThreadState::serialize(JfrCheckpointWriter& writer) {
  const u4 number_of_states = sizeof(states) / sizeof(jvmti_thread_state);
  writer.write_count(number_of_states);
  for (u4 i = 0; i < number_of_states; ++i) {
    writer.write_key(states[i].id);
    writer.write(states[i].description);
  }
}

traceid JfrThreadId::id(const Thread* t, oop vthread) {
  assert(t != nullptr, "invariant");
  if (!t->is_Java_thread()) {
    return os_id(t);
  }
  if (vthread != nullptr) {
    return java_lang_Thread::thread_id(vthread);
  }
  const oop thread_obj = JavaThread::cast(t)->threadObj();
  return thread_obj != nullptr ? java_lang_Thread::thread_id(thread_obj) : 0;
}

traceid JfrThreadId::os_id(const Thread* t) {
  assert(t != nullptr, "invariant");
  const OSThread* const os_thread = t->osthread();
  return os_thread != nullptr ? os_thread->thread_id() : 0;
}

traceid JfrThreadId::jfr_id(const Thread* t, traceid tid) {
  assert(t != nullptr, "invariant");
  return tid != 0 ? tid : JfrThreadLocal::jvm_thread_id(t);
}

// caller needs ResourceMark
static const char* get_java_thread_name(const JavaThread* jt, int& length, oop vthread) {
  assert(jt != nullptr, "invariant");
  oop thread_obj;
  if (vthread != nullptr) {
    thread_obj = vthread;
  } else {
    thread_obj = jt->threadObj();
    if (thread_obj == nullptr) {
      return nullptr;
    }
  }
  assert(thread_obj != nullptr, "invariant");
  const oop name = java_lang_Thread::name(thread_obj);
  size_t utf8_len;
  const char* ret = name != nullptr ? java_lang_String::as_utf8_string(name, utf8_len) : nullptr;
  length = checked_cast<int>(utf8_len); // Thread names should be short
  return ret;
}

const char* JfrThreadName::name(const Thread* t, int& length, oop vthread) {
  assert(t != nullptr, "invariant");
  return t->is_Java_thread() ? get_java_thread_name(JavaThread::cast(t), length, vthread) : t->name();
}
