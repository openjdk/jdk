/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_JFR_RECORDER_REPOSITORY_JFRCHUNKWRITER_HPP
#define SHARE_JFR_RECORDER_REPOSITORY_JFRCHUNKWRITER_HPP

#include "jfr/writers/jfrStorageAdapter.hpp"
#include "jfr/writers/jfrStreamWriterHost.inline.hpp"
#include "jfr/writers/jfrWriterHost.inline.hpp"

typedef MallocAdapter<M> JfrStreamBuffer; // 1 mb buffered writes
typedef StreamWriterHost<JfrStreamBuffer, JfrCHeapObj> JfrBufferedStreamWriter;
typedef WriterHost<BigEndianEncoder, CompressedIntegerEncoder, JfrBufferedStreamWriter> JfrChunkWriterBase;

class JfrChunkState;

class JfrChunkWriter : public JfrChunkWriterBase {
  friend class JfrRepository;
 private:
  JfrChunkState* _chunkstate;

  bool open();
  size_t close(int64_t metadata_offset);
  void write_header(int64_t metadata_offset);
  void set_chunk_path(const char* chunk_path);

 public:
  JfrChunkWriter();
  bool initialize();
  int64_t size_written() const;
  int64_t previous_checkpoint_offset() const;
  void set_previous_checkpoint_offset(int64_t offset);
  void time_stamp_chunk_now();
};

#endif // SHARE_JFR_RECORDER_REPOSITORY_JFRCHUNKWRITER_HPP
