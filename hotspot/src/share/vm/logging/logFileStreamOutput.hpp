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
#ifndef SHARE_VM_LOGGING_LOGFILESTREAMOUTPUT_HPP
#define SHARE_VM_LOGGING_LOGFILESTREAMOUTPUT_HPP

#include "logging/logDecorators.hpp"
#include "logging/logOutput.hpp"
#include "utilities/globalDefinitions.hpp"

class LogDecorations;

// Base class for all FileStream-based log outputs.
class LogFileStreamOutput : public LogOutput {
 protected:
  FILE*               _stream;
  size_t              _decorator_padding[LogDecorators::Count];

  LogFileStreamOutput(FILE *stream) : _stream(stream) {
    for (size_t i = 0; i < LogDecorators::Count; i++) {
      _decorator_padding[i] = 0;
    }
    _decorator_padding[LogDecorators::level_decorator] = 7;
  }

 public:
  virtual int write(const LogDecorations &decorations, const char* msg);
};

class LogStdoutOutput : public LogFileStreamOutput {
  friend class LogOutput;
 private:
  static LogStdoutOutput _instance;
  LogStdoutOutput() : LogFileStreamOutput(stdout) {
    set_config_string("all=off");
  }
  virtual bool initialize(const char* options) {
    return false;
  }
 public:
  virtual const char* name() const {
    return "stdout";
  }
};

class LogStderrOutput : public LogFileStreamOutput {
  friend class LogOutput;
 private:
  static LogStderrOutput _instance;
  LogStderrOutput() : LogFileStreamOutput(stderr) {
    set_config_string("all=warning");
  }
  virtual bool initialize(const char* options) {
    return false;
  }
 public:
  virtual const char* name() const {
    return "stderr";
  }
};

#endif // SHARE_VM_LOGGING_LOGFILESTREAMOUTPUT_HPP
