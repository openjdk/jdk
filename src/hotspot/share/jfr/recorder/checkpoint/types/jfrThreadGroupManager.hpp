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

#ifndef SHARE_JFR_RECORDER_CHECKPOINT_TYPES_JFRTHREADGROUPMANAGER_HPP
#define SHARE_JFR_RECORDER_CHECKPOINT_TYPES_JFRTHREADGROUPMANAGER_HPP

#include "jfr/utilities/jfrTypes.hpp"
#include "memory/allStatic.hpp"

class JfrCheckpointWriter;

class JfrThreadGroupManager : public AllStatic {
  friend class JfrRecorder;

 private:
  static bool create();
  static void destroy();

 public:
  static void serialize(JfrCheckpointWriter& w);
  static void serialize(JfrCheckpointWriter& w, traceid tgid, bool is_blob);

  static traceid thread_group_id(JavaThread* thread);
  static traceid thread_group_id(const JavaThread* thread, Thread* current);
};

#endif // SHARE_JFR_RECORDER_CHECKPOINT_TYPES_JFRTHREADGROUPMANAGER_HPP
