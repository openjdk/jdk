/*
* Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_JFR_SUPPORT_JFRDEPRECATIONEVENTWRITER_HPP
#define SHARE_JFR_SUPPORT_JFRDEPRECATIONEVENTWRITER_HPP

#include "memory/allocation.hpp"
#include "jfr/utilities/jfrBlob.hpp"
#include "jfr/utilities/jfrTime.hpp"

class JfrCheckpointWriter;
class JfrChunkWriter;
class JfrDeprecatedEdge;

// This writer will collapse all individual stacktrace blobs into a single TYPE_STACKTRACE checkpoint.
class JfrDeprecatedStackTraceWriter : public StackObj{
 private:
  JfrChunkWriter& _cw;
  int64_t _begin_offset;
  int64_t _elements_offset;
  size_t _processed;
  uint32_t _elements;
  bool _for_removal;
 public:
  JfrDeprecatedStackTraceWriter(JfrChunkWriter& cw);
  ~JfrDeprecatedStackTraceWriter();
  size_t elements() const { return _elements; }
  size_t processed() const { return _processed; }
  bool process(const JfrDeprecatedEdge* edge);

  static void install_stacktrace_blob(JfrDeprecatedEdge* edge, JfrCheckpointWriter& writer, JavaThread* jt);
};

class JfrDeprecatedEventWriter : public StackObj {
 private:
  JfrTicks _now;
  JfrChunkWriter& _cw;
  JfrCheckpointWriter& _tsw;
  bool _for_removal;
  bool _stacktrace;
 public:
  JfrDeprecatedEventWriter(JfrChunkWriter& cw, JfrCheckpointWriter& tsw, bool stacktrace);
  bool process(const JfrDeprecatedEdge* edge);
};

class JfrDeprecatedEventClear : public StackObj {
 public:
  JfrDeprecatedEventClear();
  bool process(const JfrDeprecatedEdge* edge);
};

class JfrDeprecatedEventWriterState : AllStatic {
 public:
  static void on_initialization();
  static void on_level_setting_update(int64_t new_level);
};

#endif // SHARE_JFR_SUPPORT_JFRDEPRECATIONEVENTWRITER_HPP
