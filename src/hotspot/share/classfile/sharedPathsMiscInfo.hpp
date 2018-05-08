/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_CLASSFILE_SHAREDPATHSMISCINFO_HPP
#define SHARE_VM_CLASSFILE_SHAREDPATHSMISCINFO_HPP

#include "classfile/classLoader.hpp"
#include "runtime/os.hpp"

class outputStream;
// During dumping time, when processing class paths, we build up the dump-time
// classpath. The JAR files that exist are stored in the list ClassLoader::_first_append_entry.
// However, we need to store other "misc" information for run-time checking, such as
//
// + The values of Arguments::get_sysclasspath() used during dumping.
//
// + The class path elements specified during dumping but did not exist --
//   these elements must also be specified at run time, and they also must not
//   exist at run time.
//
// These misc items are stored in a linear buffer in SharedPathsMiscInfo.
// The storage format is stream oriented to minimize its size.
//
// When writing the information to the archive file, SharedPathsMiscInfo is stored in
// the archive file header. At run-time, this information is used only during initialization
// (accessed using read() instead of mmap()), and is deallocated afterwards to save space.
//
// The SharedPathsMiscInfo class is used for both creating the the information (during
// dumping time) and validation (at run time). Different constructors are used in the
// two situations. See below.

class SharedPathsMiscInfo : public CHeapObj<mtClass> {
private:
  int   _app_offset;
protected:
  char* _buf_start;
  char* _cur_ptr;
  char* _end_ptr;
  int   _buf_size;
  bool  _allocated;   // was _buf_start allocated by me?
  void ensure_size(size_t needed_bytes);
  void add_path(const char* path, int type);

  void write(const void* ptr, size_t size);
  bool read(void* ptr, size_t size);

protected:
  static bool fail(const char* msg, const char* name = NULL);
  bool check(jint type, const char* path);

public:
  enum {
    INITIAL_BUF_SIZE = 128
  };
  // This constructor is used when creating the misc information (during dump)
  SharedPathsMiscInfo();
  // This constructor is used when validating the misc info (during run time)
  SharedPathsMiscInfo(char *buff, int size) {
    _app_offset = 0;
    _cur_ptr = _buf_start = buff;
    _end_ptr = _buf_start + size;
    _buf_size = size;
    _allocated = false;
  }
  ~SharedPathsMiscInfo();

  int get_used_bytes() {
    return _cur_ptr - _buf_start;
  }
  void* buffer() {
    return _buf_start;
  }

  // writing --

  // The path must not exist at run-time
  void add_nonexist_path(const char* path) {
    add_path(path, NON_EXIST);
  }

  // The path must exist, and must contain exactly <num_entries> files/dirs
  void add_boot_classpath(const char* path) {
    add_path(path, BOOT_PATH);
  }

  void add_app_classpath(const char* path) {
    add_path(path, APP_PATH);
  }
  void record_app_offset() {
    _app_offset = get_used_bytes();
  }
  void pop_app() {
    _cur_ptr = _buf_start + _app_offset;
    write_jint(0);
  }

  int write_jint(jint num) {
    write(&num, sizeof(num));
    return 0;
  }
  void write_time(time_t t) {
    write(&t, sizeof(t));
  }
  void write_long(long l) {
    write(&l, sizeof(l));
  }

  bool dump_to_file(int fd) {
    int n = get_used_bytes();
    return (os::write(fd, _buf_start, n) == (size_t)n);
  }

  // reading --

private:
  enum {
    BOOT_PATH      = 1,
    APP_PATH       = 2,
    NON_EXIST      = 3
  };

  const char* type_name(int type) {
    switch (type) {
    case BOOT_PATH:   return "BOOT";
    case APP_PATH:    return "APP";
    case NON_EXIST:   return "NON_EXIST";
    default:          ShouldNotReachHere(); return "?";
    }
  }

  void print_path(outputStream* os, int type, const char* path);

  bool read_jint(jint *ptr) {
    return read(ptr, sizeof(jint));
  }
  bool read_long(long *ptr) {
    return read(ptr, sizeof(long));
  }
  bool read_time(time_t *ptr) {
    return read(ptr, sizeof(time_t));
  }

public:
  bool check();
};

#endif // SHARE_VM_CLASSFILE_SHAREDPATHSMISCINFO_HPP
