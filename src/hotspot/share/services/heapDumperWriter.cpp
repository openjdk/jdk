/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020 SAP SE. All rights reserved.
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
#include "jvm.h"
#include "oops/klass.inline.hpp"
#include "runtime/os.hpp"
#include "services/heapDumperWriter.hpp"
#include "utilities/zipLibrary.hpp"


char const* FileWriter::open_writer() {
  assert(_fd < 0, "Must not already be open");

  _fd = os::create_binary_file(_path, _overwrite);

  if (_fd < 0) {
    return os::strerror(errno);
  }

  return nullptr;
}

FileWriter::~FileWriter() {
  if (_fd >= 0) {
    ::close(_fd);
    _fd = -1;
  }
}

char const* FileWriter::write_buf(char* buf, ssize_t size) {
  assert(_fd >= 0, "Must be open");
  assert(size > 0, "Must write at least one byte");

  if (!os::write(_fd, buf, (size_t)size)) {
    return os::strerror(errno);
  }

  return nullptr;
}

char const* GZipCompressor::init(size_t block_size, size_t* needed_out_size,
                                 size_t* needed_tmp_size) {
  _block_size = block_size;
  _is_first = true;
  char const* result = ZipLibrary::init_params(block_size, needed_out_size,
                                               needed_tmp_size, _level);
  *needed_out_size += 1024; // Add extra space for the comment in the first chunk.
  return result;
}

char const* GZipCompressor::compress(char* in, size_t in_size, char* out, size_t out_size,
                                     char* tmp, size_t tmp_size, size_t* compressed_size) {
  char const* msg = nullptr;
  if (_is_first) {
    char buf[128];
    // Write the block size used as a comment in the first gzip chunk, so the
    // code used to read it later can make a good choice of the buffer sizes it uses.
    jio_snprintf(buf, sizeof(buf), "HPROF BLOCKSIZE=" SIZE_FORMAT, _block_size);
    *compressed_size = ZipLibrary::compress(in, in_size, out, out_size, tmp, tmp_size, _level, buf, &msg);
    _is_first = false;
  } else {
    *compressed_size = ZipLibrary::compress(in, in_size, out, out_size, tmp, tmp_size, _level, nullptr, &msg);
  }

  return msg;
}


void AbstractDumpWriter::write_fast(const void* s, size_t len) {
  assert(!_in_dump_segment || (_sub_record_left >= len), "sub-record too large");
  assert(buffer_size() - position() >= len, "Must fit");
  debug_only(_sub_record_left -= len);
  memcpy(buffer() + position(), s, len);
  set_position(position() + len);
}

bool AbstractDumpWriter::can_write_fast(size_t len) {
  return buffer_size() - position() >= len;
}

// write raw bytes
void AbstractDumpWriter::write_raw(const void* s, size_t len) {
  assert(!_in_dump_segment || (_sub_record_left >= len), "sub-record too large");
  debug_only(_sub_record_left -= len);

  // flush buffer to make room.
  while (len > buffer_size() - position()) {
    assert(!_in_dump_segment || _is_huge_sub_record,
           "Cannot overflow in non-huge sub-record.");
    size_t to_write = buffer_size() - position();
    memcpy(buffer() + position(), s, to_write);
    s = (void*) ((char*) s + to_write);
    len -= to_write;
    set_position(position() + to_write);
    flush();
  }

  memcpy(buffer() + position(), s, len);
  set_position(position() + len);
}

// Makes sure we inline the fast write into the write_u* functions. This is a big speedup.
#define WRITE_KNOWN_TYPE(p, len) do { if (can_write_fast((len))) write_fast((p), (len)); \
                                      else write_raw((p), (len)); } while (0)

void AbstractDumpWriter::write_u1(u1 x) {
  WRITE_KNOWN_TYPE(&x, 1);
}

void AbstractDumpWriter::write_u2(u2 x) {
  u2 v;
  Bytes::put_Java_u2((address)&v, x);
  WRITE_KNOWN_TYPE(&v, 2);
}

void AbstractDumpWriter::write_u4(u4 x) {
  u4 v;
  Bytes::put_Java_u4((address)&v, x);
  WRITE_KNOWN_TYPE(&v, 4);
}

void AbstractDumpWriter::write_u8(u8 x) {
  u8 v;
  Bytes::put_Java_u8((address)&v, x);
  WRITE_KNOWN_TYPE(&v, 8);
}

void AbstractDumpWriter::write_address(address a) {
#ifdef _LP64
  write_u8((u8)a);
#else
  write_u4((u4)a);
#endif
}

void AbstractDumpWriter::write_objectID(oop o) {
  write_address(cast_from_oop<address>(o));
}

void AbstractDumpWriter::write_rootID(oop* p) {
  write_address((address)p);
}

void AbstractDumpWriter::write_symbolID(Symbol* s) {
  write_address((address)((uintptr_t)s));
}

void AbstractDumpWriter::write_id(u4 x) {
#ifdef _LP64
  write_u8((u8) x);
#else
  write_u4(x);
#endif
}

// We use java mirror as the class ID
void AbstractDumpWriter::write_classID(Klass* k) {
  write_objectID(k->java_mirror());
}

void AbstractDumpWriter::finish_dump_segment() {
  if (_in_dump_segment) {
    assert(_sub_record_left == 0, "Last sub-record not written completely");
    assert(_sub_record_ended, "sub-record must have ended");

    // Fix up the dump segment length if we haven't written a huge sub-record last
    // (in which case the segment length was already set to the correct value initially).
    if (!_is_huge_sub_record) {
      assert(position() > dump_segment_header_size, "Dump segment should have some content");
      Bytes::put_Java_u4((address) (buffer() + 5),
                         (u4) (position() - dump_segment_header_size));
    } else {
      // Finish process huge sub record
      // Set _is_huge_sub_record to false so the parallel dump writer can flush data to file.
      _is_huge_sub_record = false;
    }

    _in_dump_segment = false;
    flush();
  }
}

void AbstractDumpWriter::start_sub_record(u1 tag, u4 len) {
  if (!_in_dump_segment) {
    if (position() > 0) {
      flush();
    }

    assert(position() == 0 && buffer_size() > dump_segment_header_size, "Must be at the start");

    write_u1(HPROF_HEAP_DUMP_SEGMENT);
    write_u4(0); // timestamp
    // Will be fixed up later if we add more sub-records.  If this is a huge sub-record,
    // this is already the correct length, since we don't add more sub-records.
    write_u4(len);
    assert(Bytes::get_Java_u4((address)(buffer() + 5)) == len, "Inconsistent size!");
    _in_dump_segment = true;
    _is_huge_sub_record = len > buffer_size() - dump_segment_header_size;
  } else if (_is_huge_sub_record || (len > buffer_size() - position())) {
    // This object will not fit in completely or the last sub-record was huge.
    // Finish the current segment and try again.
    finish_dump_segment();
    start_sub_record(tag, len);

    return;
  }

  debug_only(_sub_record_left = len);
  debug_only(_sub_record_ended = false);

  write_u1(tag);
}

void AbstractDumpWriter::end_sub_record() {
  assert(_in_dump_segment, "must be in dump segment");
  assert(_sub_record_left == 0, "sub-record not written completely");
  assert(!_sub_record_ended, "Must not have ended yet");
  debug_only(_sub_record_ended = true);
}


DumpWriter::DumpWriter(const char* path, bool overwrite, AbstractCompressor* compressor) :
  AbstractDumpWriter(),
  _writer(new (std::nothrow) FileWriter(path, overwrite)),
  _compressor(compressor),
  _bytes_written(0),
  _error(nullptr),
  _out_buffer(nullptr),
  _out_size(0),
  _out_pos(0),
  _tmp_buffer(nullptr),
  _tmp_size(0) {
  _error = (char*)_writer->open_writer();
  if (_error == nullptr) {
    _buffer = (char*)os::malloc(io_buffer_max_size, mtInternal);
    if (compressor != nullptr) {
      _error = (char*)_compressor->init(io_buffer_max_size, &_out_size, &_tmp_size);
      if (_error == nullptr) {
        if (_out_size > 0) {
          _out_buffer = (char*)os::malloc(_out_size, mtInternal);
        }
        if (_tmp_size > 0) {
          _tmp_buffer = (char*)os::malloc(_tmp_size, mtInternal);
        }
      }
    }
  }
  // initialize internal buffer
  _pos = 0;
  _size = io_buffer_max_size;
}

DumpWriter::~DumpWriter(){
  if (_buffer != nullptr) {
    os::free(_buffer);
  }
  if (_out_buffer != nullptr) {
    os::free(_out_buffer);
  }
  if (_tmp_buffer != nullptr) {
    os::free(_tmp_buffer);
  }
  if (_writer != NULL) {
    delete _writer;
  }
  _bytes_written = -1;
}

// flush any buffered bytes to the file
void DumpWriter::flush() {
  if (_pos <= 0) {
    return;
  }
  if (has_error()) {
    _pos = 0;
    return;
  }
  char* result = nullptr;
  if (_compressor == nullptr) {
    result = (char*)_writer->write_buf(_buffer, _pos);
    _bytes_written += _pos;
  } else {
    do_compress();
    if (!has_error()) {
      result = (char*)_writer->write_buf(_out_buffer, _out_pos);
      _bytes_written += _out_pos;
    }
  }
  _pos = 0; // reset pos to make internal buffer available

  if (result != nullptr) {
    set_error(result);
  }
}

void DumpWriter::do_compress() {
  const char* msg = _compressor->compress(_buffer, _pos, _out_buffer, _out_size,
                                          _tmp_buffer, _tmp_size, &_out_pos);

  if (msg != nullptr) {
    set_error(msg);
  }
}