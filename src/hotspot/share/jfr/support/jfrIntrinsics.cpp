/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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
#include "jfr/recorder/checkpoint/types/traceid/jfrTraceIdEpoch.hpp"
#include "jfr/recorder/checkpoint/types/traceid/jfrTraceIdLoadBarrier.inline.hpp"
#include "jfr/support/jfrIntrinsics.hpp"
#include "jfr/support/jfrThreadId.inline.hpp"
#include "jfr/support/jfrThreadLocal.hpp"
#include "jfr/writers/jfrJavaEventWriter.hpp"
#include "runtime/interfaceSupport.inline.hpp"

#ifdef ASSERT
static void assert_precondition(JavaThread* jt) {
  assert(jt != nullptr, "invariant");
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_java(jt);)
  assert(jt->has_last_Java_frame(), "invariant");
}
#endif

void* JfrIntrinsicSupport::write_checkpoint(JavaThread* jt) {
  DEBUG_ONLY(assert_precondition(jt);)
  assert(JfrThreadLocal::is_vthread(jt), "invariant");
  const traceid vthread_tid = JfrThreadLocal::vthread_id(jt);
  // Transition before reading the epoch generation, now as _thread_in_vm.
  MACOS_AARCH64_ONLY(ThreadWXEnable __wx(WXWrite, jt));
  ThreadInVMfromJava transition(jt);
  JfrThreadLocal::set_vthread_epoch(jt, vthread_tid, ThreadIdAccess::current_epoch());
  return JfrJavaEventWriter::event_writer(jt);
}

void* JfrIntrinsicSupport::return_lease(JavaThread* jt) {
  DEBUG_ONLY(assert_precondition(jt);)
  MACOS_AARCH64_ONLY(ThreadWXEnable __wx(WXWrite, jt));
  ThreadInVMfromJava transition(jt);
  assert(jt->jfr_thread_local()->has_java_event_writer(), "invariant");
  assert(jt->jfr_thread_local()->shelved_buffer() != nullptr, "invariant");
  JfrJavaEventWriter::flush(jt->jfr_thread_local()->java_event_writer(), 0, 0, jt);
  assert(jt->jfr_thread_local()->shelved_buffer() == nullptr, "invariant");
  return nullptr;
}

void JfrIntrinsicSupport::load_barrier(const Klass* klass) {
  assert(klass != nullptr, "sanity");
  JfrTraceIdLoadBarrier::load_barrier(klass);
}

address JfrIntrinsicSupport::epoch_address() {
  return JfrTraceIdEpoch::epoch_address();
}

address JfrIntrinsicSupport::epoch_generation_address() {
  return JfrTraceIdEpoch::epoch_generation_address();
}

address JfrIntrinsicSupport::signal_address() {
  return JfrTraceIdEpoch::signal_address();
}
