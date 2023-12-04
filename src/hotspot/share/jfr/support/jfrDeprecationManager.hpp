/*
* Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_JFR_SUPPORT_JFRDEPRECATIONMANAGER_HPP
#define SHARE_JFR_SUPPORT_JFRDEPRECATIONMANAGER_HPP

#include "memory/allocation.hpp"
#include "jfr/utilities/jfrBlob.hpp"
#include "jfr/utilities/jfrTypes.hpp"

class JavaThread;
class JfrCheckpointWriter;
class JfrChunkWriter;
class Method;
class Thread;

class JfrDeprecatedEdge : public CHeapObj<mtTracing> {
  template<typename, typename>
  friend class JfrLinkedList;
  friend class JfrDeprecatedStackTraceWriter;
 private:
  JfrTicks _invocation_time;
  JfrBlobHandle _stacktrace;
  JfrDeprecatedEdge* _next;
  InstanceKlass* _deprecated_ik;
  traceid _deprecated_methodid;
  InstanceKlass* _sender_ik;
  traceid _sender_methodid;
  traceid _stack_trace_id;
  int _bci;
  int _linenumber;
  u1 _frame_type;
  bool _for_removal;

  void set_stacktrace(const JfrBlobHandle& blob);

 public:
  JfrDeprecatedEdge(const Method* method, Method* sender, int bci, u1 frame_type, JavaThread* jt);

  const JfrDeprecatedEdge* next() const { return _next; }
  void set_next(JfrDeprecatedEdge* edge) { _next = edge; }

  bool has_event() const;
  const JfrBlobHandle& event() const;
  const JfrBlobHandle& event_no_stacktrace() const;
  bool has_stacktrace() const;
  const JfrBlobHandle& stacktrace() const;
  void install_stacktrace_blob(JavaThread* jt);

  const InstanceKlass* deprecated_ik() const { return _deprecated_ik; }
  traceid deprecated_methodid() const { return _deprecated_methodid; }

  const InstanceKlass* sender_ik() const { return _sender_ik; }
  traceid sender_methodid() const { return _sender_methodid; }

  const JfrTicks& invocation_time() const { return _invocation_time; }
  traceid stacktrace_id() const { return _stack_trace_id; }

  int bci() const { return _bci; }
  u1 frame_type() const { return _frame_type; }
  bool for_removal() const { return _for_removal; }
  int linenumber() const { return _linenumber; }
};

class JfrDeprecationManager : AllStatic {
 public:
  static void on_safepoint_clear();
  static void on_safepoint_write();
  static void on_recorder_stop();
  static void prepare_type_set(JavaThread* jt);
  static void on_type_set(JfrCheckpointWriter& writer, JfrChunkWriter* cw, Thread* thread);
  static void on_type_set_unload(JfrCheckpointWriter& writer);
  static void write_edges(JfrChunkWriter& cw, Thread* thread, bool on_error = false);
  static void on_link(const Method* method, Method* sender, int bci, u1 frame_type, JavaThread* thread);
  static void on_level_setting_update(int64_t new_level);
};

#endif // SHARE_JFR_SUPPORT_JFRDEPRECATIONMANAGER_HPP
