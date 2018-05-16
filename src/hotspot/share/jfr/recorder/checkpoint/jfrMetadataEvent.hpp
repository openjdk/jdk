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

#ifndef SHARE_VM_JFR_RECORDER_CHECKPOINT_JFRMETADATAEVENT_HPP
#define SHARE_VM_JFR_RECORDER_CHECKPOINT_JFRMETADATAEVENT_HPP

#include "jni.h"
#include "memory/allocation.hpp"

class JfrChunkWriter;

//
// Metadata is continuously updated in Java as event classes are loaded / unloaded.
// Using update(), Java stores a binary representation back to native.
// This is for easy access on chunk finalization as well as having it readily available in the case of fatal error.
//
class JfrMetadataEvent : AllStatic {
 public:
  static void lock();
  static void unlock();
  static size_t write(JfrChunkWriter& writer, jlong metadata_offset);
  static void update(jbyteArray metadata);
};

#endif // SHARE_VM_JFR_RECORDER_CHECKPOINT_JFRMETADATAEVENT_HPP
