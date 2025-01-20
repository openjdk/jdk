/*
 * Copyright (c) 2012, 2025, Oracle and/or its affiliates. All rights reserved.
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
#include "jfr/recorder/jfrRecorder.hpp"
#include "jfr/recorder/service/jfrPostBox.hpp"
#include "jfr/recorder/service/jfrRecorderService.hpp"
#include "jfr/recorder/service/jfrRecorderThread.hpp"
#include "jfr/recorder/jfrRecorder.hpp"
#include "logging/log.hpp"
#include "runtime/handles.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/mutexLocker.hpp"

//
// Entry point for "JFR Recorder Thread" message loop.
// The recorder thread executes service requests collected from the message system.
//
void recorderthread_entry(JavaThread* thread, JavaThread* unused) {
  assert(thread != nullptr, "invariant");
  #define START (msgs & (MSGBIT(MSG_START)))
  #define SHUTDOWN (msgs & MSGBIT(MSG_SHUTDOWN))
  #define ROTATE (msgs & (MSGBIT(MSG_ROTATE)|MSGBIT(MSG_STOP)))
  #define FLUSHPOINT (msgs & (MSGBIT(MSG_FLUSHPOINT)))
  #define PROCESS_FULL_BUFFERS (msgs & (MSGBIT(MSG_ROTATE)|MSGBIT(MSG_STOP)|MSGBIT(MSG_FULLBUFFER)))

  JfrPostBox& post_box = JfrRecorderThread::post_box();
  log_debug(jfr, system)("Recorder thread STARTED");

  {
    // Run as _thread_in_native to minimize impact on safepoint synchronization.
    NoHandleMark nhm;
    ThreadToNativeFromVM transition(thread);

    bool done = false;
    int msgs = 0;
    JfrRecorderService service;

    MonitorLocker msg_lock(JfrMsg_lock, Mutex::_no_safepoint_check_flag);

    // JFR MESSAGE LOOP PROCESSING - BEGIN
    while (!done) {
      if (post_box.is_empty()) {
        msg_lock.wait();
      }
      msgs = post_box.collect();
      {
        MutexUnlocker mul(JfrMsg_lock, Mutex::_no_safepoint_check_flag);
        if (PROCESS_FULL_BUFFERS) {
          service.process_full_buffers();
        }
        // Check amount of data written to chunk already
        // if it warrants asking for a new chunk.
        service.evaluate_chunk_size_for_rotation();
        if (START) {
          service.start();
        } else if (ROTATE) {
          service.rotate(msgs);
        } else if (FLUSHPOINT) {
          service.flushpoint();
        }
      }
      post_box.notify_waiters();
      if (SHUTDOWN) {
        log_debug(jfr, system)("Request to STOP recorder");
        done = true;
      }
    } // JFR MESSAGE LOOP PROCESSING - END
    post_box.notify_collection_stop();
  } // JfrMsg_lock scope and the thread returns to _thread_in_vm

  assert(!JfrMsg_lock->owned_by_self(), "invariant");
  JfrRecorder::on_recorder_thread_exit();

  #undef START
  #undef SHUTDOWN
  #undef ROTATE
  #undef FLUSHPOINT
  #undef PROCESS_FULL_BUFFERS
  #undef SCAVENGE
}
