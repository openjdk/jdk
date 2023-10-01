/*
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

#ifndef SHARE_SERVICES_HEAPDUMPERCOMPRESSION_HPP
#define SHARE_SERVICES_HEAPDUMPERCOMPRESSION_HPP

#include "memory/allocation.hpp"


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

  void* load_gzip_func(char const* name);

public:
  GZipCompressor(int level) : _level(level), _block_size(0), _is_first(false) {
  }

  virtual char const* init(size_t block_size, size_t* needed_out_size,
                           size_t* needed_tmp_size);

  virtual char const* compress(char* in, size_t in_size, char* out, size_t out_size,
                               char* tmp, size_t tmp_size, size_t* compressed_size);
};

#endif // SHARE_SERVICES_HEAPDUMPERCOMPRESSION_HPP
