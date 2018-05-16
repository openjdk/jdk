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

#ifndef SHARE_VM_JFR_RECORDER_CHECKPOINT_JFRCHECKPOINTBLOB_HPP
#define SHARE_VM_JFR_RECORDER_CHECKPOINT_JFRCHECKPOINTBLOB_HPP

#include "jfr/utilities/jfrAllocation.hpp"
#include "jfr/utilities/jfrRefCountPointer.hpp"

class JfrCheckpointBlob;
class JfrCheckpointWriter;

typedef RefCountPointer<JfrCheckpointBlob, MultiThreadedRefCounter> JfrCheckpointBlobReference;
typedef RefCountHandle<JfrCheckpointBlobReference> JfrCheckpointBlobHandle;

class JfrCheckpointBlob : public JfrCHeapObj {
  template <typename, typename>
  friend class RefCountPointer;
 private:
  const u1* _checkpoint;
  const size_t _size;
  JfrCheckpointBlobHandle _next;
  mutable bool _written;

  JfrCheckpointBlob(const u1* checkpoint, size_t size);
  ~JfrCheckpointBlob();
  const JfrCheckpointBlobHandle& next() const;
  void write_this(JfrCheckpointWriter& writer) const;

 public:
  void write(JfrCheckpointWriter& writer) const;
  void exclusive_write(JfrCheckpointWriter& writer) const;
  void reset_write_state() const;
  void set_next(const JfrCheckpointBlobHandle& ref);
  static JfrCheckpointBlobHandle make(const u1* checkpoint, size_t size);
};

#endif // SHARE_VM_JFR_RECORDER_CHECKPOINT_JFRCHECKPOINTBLOB_HPP
