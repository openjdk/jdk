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

#include "precompiled.hpp"
#include "jfr/recorder/checkpoint/jfrCheckpointManager.hpp"
#include "jfr/recorder/checkpoint/jfrCheckpointWriter.hpp"
#include "jfr/writers/jfrBigEndianWriter.hpp"

JfrCheckpointFlush::JfrCheckpointFlush(Type* old, size_t used, size_t requested, Thread* t) :
  _result(JfrCheckpointManager::flush(old, used, requested, t)) {}

JfrCheckpointWriter::JfrCheckpointWriter(bool flushpoint, bool header, Thread* thread) :
  JfrCheckpointWriterBase(JfrCheckpointManager::lease_buffer(thread), thread),
  _time(JfrTicks::now()),
  _offset(0),
  _count(0),
  _flushpoint(flushpoint),
  _header(header) {
  assert(this->is_acquired(), "invariant");
  assert(0 == this->current_offset(), "invariant");
  if (_header) {
    reserve(sizeof(JfrCheckpointEntry));
  }
}

static void write_checkpoint_header(u1* pos, jlong size, jlong time, bool flushpoint, juint type_count) {
  assert(pos != NULL, "invariant");
  JfrBigEndianWriter be_writer(pos, sizeof(JfrCheckpointEntry));
  be_writer.write(size);
  be_writer.write(time);
  be_writer.write(JfrTicks::now().value() - time);
  be_writer.write(flushpoint ? (juint)1 : (juint)0);
  be_writer.write(type_count);
  assert(be_writer.is_valid(), "invariant");
}

JfrCheckpointWriter::~JfrCheckpointWriter() {
  assert(this->is_acquired(), "invariant");
  if (!this->is_valid() || !_header) {
    release();
    return;
  }
  if (0 == count()) {
    assert(this->used_size() == sizeof(JfrCheckpointEntry), "invariant");
    this->seek(_offset);
    release();
    return;
  }
  assert(_header, "invariant");
  assert(this->is_valid(), "invariant");
  assert(count() > 0, "invariant");
  assert(this->used_size() > sizeof(JfrCheckpointEntry), "invariant");
  const jlong size = this->current_offset();
  assert(size + this->start_pos() == this->current_pos(), "invariant");
  write_checkpoint_header(const_cast<u1*>(this->start_pos()), size, _time, is_flushpoint(), count());
  release();
}

void JfrCheckpointWriter::set_flushpoint(bool flushpoint) {
  _flushpoint = flushpoint;
}

bool JfrCheckpointWriter::is_flushpoint() const {
  return _flushpoint;
}

juint JfrCheckpointWriter::count() const {
  return _count;
}

void JfrCheckpointWriter::set_count(juint count) {
  _count = count;
}

void JfrCheckpointWriter::release() {
  assert(this->is_acquired(), "invariant");
  if (!this->is_valid() || this->used_size() == 0) {
    return;
  }
  assert(this->used_size() > 0, "invariant");
  // write through to backing storage
  this->commit();
  assert(0 == this->current_offset(), "invariant");
}

void JfrCheckpointWriter::write_type(JfrTypeId type_id) {
  assert(type_id < TYPES_END, "invariant");
  write<u8>(type_id);
  increment();
}

void JfrCheckpointWriter::write_key(u8 key) {
  write<u8>(key);
}

void JfrCheckpointWriter::increment() {
  ++_count;
}

void JfrCheckpointWriter::write_count(u4 nof_entries) {
  write<u4>((u4)nof_entries);
}

void JfrCheckpointWriter::write_count(u4 nof_entries, jlong offset) {
  write_padded_at_offset(nof_entries, offset);
}

const u1* JfrCheckpointWriter::session_data(size_t* size, const JfrCheckpointContext* ctx /* 0 */) {
  assert(this->is_acquired(), "wrong state!");
  if (!this->is_valid()) {
    *size = 0;
    return NULL;
  }
  if (ctx != NULL) {
    const u1* session_start_pos = this->start_pos() + ctx->offset;
    *size = this->current_pos() - session_start_pos;
    return session_start_pos;
  }
  *size = this->used_size();
  assert(this->start_pos() + *size == this->current_pos(), "invariant");
  write_checkpoint_header(const_cast<u1*>(this->start_pos()), this->used_offset(), _time, is_flushpoint(), count());
  this->seek(_offset + (_header ? sizeof(JfrCheckpointEntry) : 0));
  set_count(0);
  return this->start_pos();
}

const JfrCheckpointContext JfrCheckpointWriter::context() const {
  JfrCheckpointContext ctx;
  ctx.offset = this->current_offset();
  ctx.count = this->count();
  return ctx;
}

void JfrCheckpointWriter::set_context(const JfrCheckpointContext ctx) {
  this->seek(ctx.offset);
  set_count(ctx.count);
}
bool JfrCheckpointWriter::has_data() const {
  return this->used_size() > sizeof(JfrCheckpointEntry);
}

JfrCheckpointBlobHandle JfrCheckpointWriter::checkpoint_blob() {
  size_t size = 0;
  const u1* data = session_data(&size);
  return JfrCheckpointBlob::make(data, size);
}

JfrCheckpointBlobHandle JfrCheckpointWriter::copy(const JfrCheckpointContext* ctx /* 0 */) {
  if (ctx == NULL) {
    return checkpoint_blob();
  }
  size_t size = 0;
  const u1* data = session_data(&size, ctx);
  return JfrCheckpointBlob::make(data, size);
}

JfrCheckpointBlobHandle JfrCheckpointWriter::move(const JfrCheckpointContext* ctx /* 0 */) {
  JfrCheckpointBlobHandle data = copy(ctx);
  if (ctx != NULL) {
    const_cast<JfrCheckpointContext*>(ctx)->count = 0;
    set_context(*ctx);
  }
  return data;
}
