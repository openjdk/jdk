/*
 * Copyright (c) 2020 SAP SE. All rights reserved.
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

#ifndef SHARE_SERVICES_HEAPDUMPERCOMPRESSION_HPP
#define SHARE_SERVICES_HEAPDUMPERCOMPRESSION_HPP

#include "memory/allocation.hpp"
#include "services/heapDumper.hpp"


// Interface for a compression  implementation.
class AbstractCompressor : public CHeapObj<mtInternal> {
public:
  virtual ~AbstractCompressor() { }

  // Initializes the compressor. Returns a static error message in case of an error.
  // Otherwise initializes the needed out and tmp size for the given block size.
  virtual char const* init(size_t block_size, size_t* needed_out_size,
                           size_t* needed_tmp_size) = 0;

  // Does the actual compression. Returns null on success and a static error
  // message otherwise. Sets the 'compressed_size'.
  virtual char const* compress(char* in, size_t in_size, char* out, size_t out_size,
                               char* tmp, size_t tmp_size, size_t* compressed_size) = 0;
};

// Interface for a writer implementation.
class AbstractWriter : public CHeapObj<mtInternal> {
public:
  virtual ~AbstractWriter() { }

  // Opens the writer. Returns null on success and a static error message otherwise.
  virtual char const* open_writer() = 0;

  // Does the write. Returns null on success and a static error message otherwise.
  virtual char const* write_buf(char* buf, ssize_t size) = 0;
};


// A writer for a file.
class FileWriter : public AbstractWriter {
private:
  char const* _path;
  bool _overwrite;
  int _fd;

public:
  FileWriter(char const* path, bool overwrite) : _path(path), _overwrite(overwrite), _fd(-1) { }

  ~FileWriter();

  // Opens the writer. Returns null on success and a static error message otherwise.
  virtual char const* open_writer();

  // Does the write. Returns null on success and a static error message otherwise.
  virtual char const* write_buf(char* buf, ssize_t size);

  const char* get_file_path() { return _path; }

  bool is_overwrite() const { return _overwrite; }

  int get_fd() const {return _fd; }
};


// A compressor using the gzip format.
class GZipCompressor : public AbstractCompressor {
private:
  int _level;
  size_t _block_size;
  bool _is_first;

public:
  GZipCompressor(int level) : _level(level), _block_size(0), _is_first(false) {
  }

  virtual char const* init(size_t block_size, size_t* needed_out_size,
                           size_t* needed_tmp_size);

  virtual char const* compress(char* in, size_t in_size, char* out, size_t out_size,
                               char* tmp, size_t tmp_size, size_t* compressed_size);
};

// Supports I/O operations for a dump
// Base class for dump and parallel dump
class AbstractDumpWriter : public CHeapObj<mtInternal> {
 protected:
  enum {
    io_buffer_max_size = 1*M,
    dump_segment_header_size = 9
  };

  char* _buffer;    // internal buffer
  size_t _size;
  size_t _pos;

  bool _in_dump_segment; // Are we currently in a dump segment?
  bool _is_huge_sub_record; // Are we writing a sub-record larger than the buffer size?
  DEBUG_ONLY(size_t _sub_record_left;) // The bytes not written for the current sub-record.
  DEBUG_ONLY(bool _sub_record_ended;) // True if we have called the end_sub_record().

  char* buffer() const                          { return _buffer; }
  size_t buffer_size() const                    { return _size; }
  void set_position(size_t pos)                 { _pos = pos; }

  // Can be called if we have enough room in the buffer.
  void write_fast(const void* s, size_t len);

  // Returns true if we have enough room in the buffer for 'len' bytes.
  bool can_write_fast(size_t len);

  void write_address(address a);

 public:
  AbstractDumpWriter() :
    _buffer(nullptr),
    _size(io_buffer_max_size),
    _pos(0),
    _in_dump_segment(false) { }

  // Total number of bytes written to the disk
  virtual julong bytes_written() const = 0;
  // Return non-null if error occurred
  virtual char const* error() const = 0;

  size_t position() const                       { return _pos; }
  // writer functions
  virtual void write_raw(const void* s, size_t len);
  void write_u1(u1 x);
  void write_u2(u2 x);
  void write_u4(u4 x);
  void write_u8(u8 x);
  void write_objectID(oop o);
  void write_rootID(oop* p);
  void write_symbolID(Symbol* o);
  void write_classID(Klass* k);
  void write_id(u4 x);

  // Start a new sub-record. Starts a new heap dump segment if needed.
  void start_sub_record(u1 tag, u4 len);
  // Ends the current sub-record.
  void end_sub_record();
  // Finishes the current dump segment if not already finished.
  void finish_dump_segment();
  // Flush internal buffer to persistent storage
  virtual void flush() = 0;
};

// Supports I/O operations for a dump

class DumpWriter : public AbstractDumpWriter {
private:
  FileWriter* _writer;
  AbstractCompressor* _compressor;
  size_t _bytes_written;
  char* _error;
  // Compression support
  char* _out_buffer;
  size_t _out_size;
  size_t _out_pos;
  char* _tmp_buffer;
  size_t _tmp_size;

private:
  void do_compress();

public:
  DumpWriter(const char* path, bool overwrite, AbstractCompressor* compressor);
  ~DumpWriter();
  julong bytes_written() const override        { return (julong) _bytes_written; }
  void set_bytes_written(julong bytes_written) { _bytes_written = bytes_written; }
  char const* error() const override           { return _error; }
  void set_error(const char* error)            { _error = (char*)error; }
  bool has_error() const                       { return _error != nullptr; }
  const char* get_file_path() const            { return _writer->get_file_path(); }
  AbstractCompressor* compressor()             { return _compressor; }
  void set_compressor(AbstractCompressor* p)   { _compressor = p; }
  bool is_overwrite() const                    { return _writer->is_overwrite(); }
  int get_fd() const                           { return _writer->get_fd(); }

  void flush() override;
};

#endif // SHARE_SERVICES_HEAPDUMPERCOMPRESSION_HPP
