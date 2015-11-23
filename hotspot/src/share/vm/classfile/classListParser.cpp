/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/classListParser.hpp"
#include "runtime/os.hpp"
#include "runtime/java.hpp"

ClassListParser::ClassListParser(const char* file) {
  _classlist_file = file;
  _file = fopen(file, "r");
  if (_file == NULL) {
    char errmsg[JVM_MAXPATHLEN];
    os::lasterror(errmsg, JVM_MAXPATHLEN);
    vm_exit_during_initialization("Loading classlist failed", errmsg);
  }
}

ClassListParser::~ClassListParser() {
  if (_file) {
    fclose(_file);
  }
}

bool ClassListParser::parse_one_line() {
  for (;;) {
    if (fgets(_line, sizeof(_line), _file) == NULL) {
      return false;
    }
    int line_len = (int)strlen(_line);
    if (line_len > _max_allowed_line_len) {
      tty->print_cr("input line too long (must be no longer than %d chars)", _max_allowed_line_len);
      vm_exit_during_initialization("Loading classlist failed");
    }
    if (*_line == '#') { // comment
      continue;
    }
    break;
  }

  // Remove trailing \r\n
  _line[strcspn(_line, "\r\n")] = 0;
  return true;
}

