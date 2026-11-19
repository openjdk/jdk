/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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
#ifndef SHARE_LOGGING_LOGFILESTREAMOUTPUT_HPP
#define SHARE_LOGGING_LOGFILESTREAMOUTPUT_HPP

#include "logging/logDecorators.hpp"
#include "logging/logOutput.hpp"
#include "runtime/os.hpp"
#include "utilities/globalDefinitions.hpp"

class LogDecorations;

// Base class for all FileStream-based log outputs.
class LogFileStreamOutput : public LogOutput {
 private:
  static const char* const FoldMultilinesOptionKey;
  bool                _fold_multilines;
  bool                _write_error_is_shown;

 protected:
  FILE*               _stream;
  int                 _decorator_padding[LogDecorators::Count];

  LogFileStreamOutput(FILE *stream)
    : _fold_multilines(false),
      _write_error_is_shown(false),
      _stream(stream),
      _decorator_padding()
  {}

  int write_decorations(const LogDecorations& decorations);
  int write_internal(const LogDecorations& decorations, const char* msg);
  bool flush();

 public:
  virtual bool set_option(const char* key, const char* value, outputStream* errstream);
  virtual int write(const LogDecorations& decorations, const char* msg);
  virtual int write(LogMessageBuffer::Iterator msg_iterator);
  // Write API used by AsyncLogWriter
  virtual int write_blocking(const LogDecorations& decorations, const char* msg);
  virtual void describe(outputStream* out);
};

class LogStdoutOutput : public LogFileStreamOutput {
  friend class LogFileStreamInitializer;
 private:
  virtual bool initialize(const char* options, outputStream* errstream) {
    return false;
  }
 public:
  LogStdoutOutput() : LogFileStreamOutput(stdout) {}
  virtual const char* name() const {
    return "stdout";
  }
};

class LogStderrOutput : public LogFileStreamOutput {
  friend class LogFileStreamInitializer;
 private:
  virtual bool initialize(const char* options, outputStream* errstream) {
    return false;
  }
 public:
  LogStderrOutput() : LogFileStreamOutput(stderr) {}
  virtual const char* name() const {
    return "stderr";
  }
};

#endif // SHARE_LOGGING_LOGFILESTREAMOUTPUT_HPP
