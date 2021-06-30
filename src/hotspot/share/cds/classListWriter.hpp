/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_CDS_CLASSLISTWRITER_HPP
#define SHARE_CDS_CLASSLISTWRITER_HPP

#include "runtime/mutexLocker.hpp"
#include "runtime/thread.hpp"
#include "utilities/ostream.hpp"

class ClassListWriter {
  friend const char* make_log_name(const char* log_name, const char* force_directory);

  static fileStream* _classlist_file;
  MutexLocker _locker;
public:
#if INCLUDE_CDS
  ClassListWriter() : _locker(Thread::current(), ClassListFile_lock, Mutex::_no_safepoint_check_flag) {}
#else
  ClassListWriter() : _locker(Thread::current(), NULL, Mutex::_no_safepoint_check_flag) {}
#endif

  outputStream* stream() {
    return _classlist_file;
  }

  static bool is_enabled() {
#if INCLUDE_CDS
    return _classlist_file != NULL && _classlist_file->is_open();
#else
    return false;
#endif
  }

  static void init() {
#if INCLUDE_CDS
  // For -XX:DumpLoadedClassList=<file> option
  if (DumpLoadedClassList != NULL) {
    const char* list_name = make_log_name(DumpLoadedClassList, NULL);
    _classlist_file = new(ResourceObj::C_HEAP, mtInternal)
                         fileStream(list_name);
    _classlist_file->print_cr("# NOTE: Do not modify this file.");
    _classlist_file->print_cr("#");
    _classlist_file->print_cr("# This file is generated via the -XX:DumpLoadedClassList=<class_list_file> option");
    _classlist_file->print_cr("# and is used at CDS archive dump time (see -Xshare:dump).");
    _classlist_file->print_cr("#");
    FREE_C_HEAP_ARRAY(char, list_name);
  }
#endif
  }

  static void delete_classlist() {
#if INCLUDE_CDS
    if (_classlist_file != NULL) {
        delete _classlist_file;
    }
#endif
  }
};

#endif // SHARE_CDS_CLASSLISTWRITER_HPP
