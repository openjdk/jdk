/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "jfr/recorder/repository/jfrChunkState.hpp"
#include "jfr/recorder/repository/jfrChunkWriter.hpp"
#include "jfr/recorder/service/jfrOptionSet.hpp"
#include "jfr/utilities/jfrTime.hpp"
#include "jfr/utilities/jfrTypes.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/os.hpp"
#include "runtime/os.inline.hpp"

const u2 JFR_VERSION_MAJOR = 2;
const u2 JFR_VERSION_MINOR = 0;

static const size_t MAGIC_LEN = 4;
static const size_t FILEHEADER_SLOT_SIZE = 8;
static const size_t CHUNK_SIZE_OFFSET = 8;

JfrChunkWriter::JfrChunkWriter() : JfrChunkWriterBase(NULL), _chunkstate(NULL) {}

bool JfrChunkWriter::initialize() {
  assert(_chunkstate == NULL, "invariant");
  _chunkstate = new JfrChunkState();
  return _chunkstate != NULL;
}

static fio_fd open_existing(const char* path) {
  return os::open(path, O_RDWR, S_IREAD | S_IWRITE);
}

static fio_fd open_chunk(const char* path) {
  assert(JfrStream_lock->owned_by_self(), "invariant");
  return path != NULL ? open_existing(path) : invalid_fd;
}

bool JfrChunkWriter::open() {
  assert(_chunkstate != NULL, "invariant");
  JfrChunkWriterBase::reset(open_chunk(_chunkstate->path()));
  const bool is_open = this->has_valid_fd();
  if (is_open) {
    this->bytes("FLR", MAGIC_LEN);
    this->be_write((u2)JFR_VERSION_MAJOR);
    this->be_write((u2)JFR_VERSION_MINOR);
    this->reserve(6 * FILEHEADER_SLOT_SIZE);
    // u8 chunk_size
    // u8 initial checkpoint offset
    // u8 metadata section offset
    // u8 chunk start nanos
    // u8 chunk duration nanos
    // u8 chunk start ticks
    this->be_write(JfrTime::frequency());
    // chunk capabilities, CompressedIntegers etc
    this->be_write((u4)JfrOptionSet::compressed_integers() ? 1 : 0);
    _chunkstate->reset();
  }
  return is_open;
}

size_t JfrChunkWriter::close(intptr_t metadata_offset) {
  write_header(metadata_offset);
  this->flush();
  this->close_fd();
  return size_written();
}

void JfrChunkWriter::write_header(intptr_t metadata_offset) {
  assert(this->is_valid(), "invariant");
  // Chunk size
  this->write_be_at_offset(size_written(), CHUNK_SIZE_OFFSET);
  // initial checkpoint event offset
  this->write_be_at_offset(_chunkstate->previous_checkpoint_offset(), CHUNK_SIZE_OFFSET + (1 * FILEHEADER_SLOT_SIZE));
  // metadata event offset
  this->write_be_at_offset(metadata_offset, CHUNK_SIZE_OFFSET + (2 * FILEHEADER_SLOT_SIZE));
  // start of chunk in nanos since epoch
  this->write_be_at_offset(_chunkstate->previous_start_nanos(), CHUNK_SIZE_OFFSET + (3 * FILEHEADER_SLOT_SIZE));
  // duration of chunk in nanos
  this->write_be_at_offset(_chunkstate->last_chunk_duration(), CHUNK_SIZE_OFFSET + (4 * FILEHEADER_SLOT_SIZE));
  // start of chunk in ticks
  this->write_be_at_offset(_chunkstate->previous_start_ticks(), CHUNK_SIZE_OFFSET + (5 * FILEHEADER_SLOT_SIZE));
}

void JfrChunkWriter::set_chunk_path(const char* chunk_path) {
  _chunkstate->set_path(chunk_path);
}

intptr_t JfrChunkWriter::size_written() const {
  return this->is_valid() ? this->current_offset() : 0;
}

intptr_t JfrChunkWriter::previous_checkpoint_offset() const {
  return _chunkstate->previous_checkpoint_offset();
}

void JfrChunkWriter::set_previous_checkpoint_offset(intptr_t offset) {
  _chunkstate->set_previous_checkpoint_offset(offset);
}

void JfrChunkWriter::time_stamp_chunk_now() {
  _chunkstate->update_time_to_now();
}
