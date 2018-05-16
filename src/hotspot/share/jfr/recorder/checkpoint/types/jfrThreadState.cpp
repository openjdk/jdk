/*
* Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "jfr/recorder/checkpoint/types/jfrThreadState.hpp"
#include "jfr/recorder/checkpoint/jfrCheckpointWriter.hpp"
#include "jvmtifiles/jvmti.h"

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

