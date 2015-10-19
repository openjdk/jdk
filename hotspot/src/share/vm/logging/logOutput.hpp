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
#ifndef SHARE_VM_LOGGING_LOGOUTPUT_HPP
#define SHARE_VM_LOGGING_LOGOUTPUT_HPP

#include "logging/logDecorators.hpp"
#include "memory/allocation.hpp"
#include "utilities/globalDefinitions.hpp"

class LogDecorations;

// The base class/interface for log outputs.
// Keeps track of the latest configuration string,
// and its selected decorators.
class LogOutput : public CHeapObj<mtLogging> {
 protected:
  LogDecorators _decorators;
  char* _config_string;

 public:
  static LogOutput* const Stdout;
  static LogOutput* const Stderr;

  void set_decorators(const LogDecorators &decorators) {
    _decorators = decorators;
  }

  const LogDecorators& decorators() const {
    return _decorators;
  }

  const char* config_string() const {
    return _config_string;
  }

  LogOutput() : _config_string(NULL) {
  }

  virtual ~LogOutput();
  void set_config_string(const char* string);

  virtual const char* name() const = 0;
  virtual bool initialize(const char* options) = 0;
  virtual int write(const LogDecorations &decorations, const char* msg) = 0;
};

#endif // SHARE_VM_LOGGING_LOGOUTPUT_HPP
