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

#ifndef SHARE_JFR_SUPPORT_JFRTHREADEXTENSION_HPP
#define SHARE_JFR_SUPPORT_JFRTHREADEXTENSION_HPP

#include "jfr/recorder/storage/jfrBuffer.hpp"
#include "jfr/support/jfrThreadId.hpp"

#define DEFINE_THREAD_LOCAL_FIELD_JFR mutable JfrThreadLocal _jfr_thread_local

#define DEFINE_THREAD_LOCAL_OFFSET_JFR \
  static ByteSize jfr_thread_local_offset() { return byte_offset_of(Thread, _jfr_thread_local); }

#define THREAD_LOCAL_OFFSET_JFR Thread::jfr_thread_local_offset()

#define DEFINE_THREAD_LOCAL_TRACE_ID_OFFSET_JFR \
  static ByteSize trace_id_offset() { return byte_offset_of(JfrThreadLocal, _trace_id); }

#define DEFINE_THREAD_LOCAL_ACCESSOR_JFR \
  JfrThreadLocal* jfr_thread_local() const { return &_jfr_thread_local; }

#define VTHREAD_ID_OFFSET_JFR JfrThreadLocal::vthread_id_offset()

#define VTHREAD_OFFSET_JFR JfrThreadLocal::vthread_offset()

#define VTHREAD_EPOCH_OFFSET_JFR JfrThreadLocal::vthread_epoch_offset()

#define VTHREAD_EXCLUDED_OFFSET_JFR JfrThreadLocal::vthread_excluded_offset()

#define JAVA_BUFFER_OFFSET_JFR \
  JfrThreadLocal::java_buffer_offset() + THREAD_LOCAL_OFFSET_JFR

#define NOTIFY_OFFSET_JFR \
  JfrThreadLocal::notified_offset() + THREAD_LOCAL_OFFSET_JFR

#define JFR_BUFFER_POS_OFFSET \
  JfrBuffer::pos_offset()

#define JFR_BUFFER_FLAGS_OFFSET \
  JfrBuffer::flags_offset()

#define THREAD_LOCAL_WRITER_OFFSET_JFR \
  JfrThreadLocal::java_event_writer_offset() + THREAD_LOCAL_OFFSET_JFR

#define SAMPLE_STATE_OFFSET_JFR \
  JfrThreadLocal::sample_state_offset() + THREAD_LOCAL_OFFSET_JFR

#define SAMPLING_CRITICAL_SECTION_OFFSET_JFR \
  JfrThreadLocal::sampling_critical_section_offset() + THREAD_LOCAL_OFFSET_JFR

#endif // SHARE_JFR_SUPPORT_JFRTHREADEXTENSION_HPP
