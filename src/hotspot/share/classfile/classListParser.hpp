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

#ifndef SHARE_VM_MEMORY_CLASSLISTPARSER_HPP
#define SHARE_VM_MEMORY_CLASSLISTPARSER_HPP

#include "utilities/exceptions.hpp"
#include "utilities/globalDefinitions.hpp"

class ClassListParser : public StackObj {
  enum {
    // Max number of bytes allowed per line in the classlist.
    // Theoretically Java class names could be 65535 bytes in length. In reality,
    // 4K bytes is more than enough.
    _max_allowed_line_len = 4096,
    _line_buf_extra       = 10, // for detecting input too long
    _line_buf_size        = _max_allowed_line_len + _line_buf_extra
  };

  const char* _classlist_file;
  FILE* _file;
  char  _line[_line_buf_size];  // The buffer that holds the current line.

public:
  ClassListParser(const char* file);
  ~ClassListParser();
  bool parse_one_line();

  const char* current_class_name() {
    return _line;
  }
};


#endif // SHARE_VM_MEMORY_CLASSLISTPARSER_HPP
