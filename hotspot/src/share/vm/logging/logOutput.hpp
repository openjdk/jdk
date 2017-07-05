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
#include "logging/logLevel.hpp"
#include "logging/logMessageBuffer.hpp"
#include "memory/allocation.hpp"
#include "utilities/globalDefinitions.hpp"

class LogDecorations;
class LogMessageBuffer;
class LogTagSet;

// The base class/interface for log outputs.
// Keeps track of the latest configuration string,
// and its selected decorators.
class LogOutput : public CHeapObj<mtLogging> {
  // Make LogConfiguration a friend to allow it to modify the configuration string.
  friend class LogConfiguration;

 private:
  static const size_t InitialConfigBufferSize = 256;
  char* _config_string;
  size_t _config_string_buffer_size;

 protected:
  LogDecorators _decorators;

  // Clears any previous config description in preparation of reconfiguration.
  void clear_config_string();
  // Adds the tagset on the given level to the config description (e.g. "tag1+tag2=level").
  void add_to_config_string(const LogTagSet* ts, LogLevelType level);
  // Replaces the current config description with the given string.
  void set_config_string(const char* string);

 public:
  void set_decorators(const LogDecorators &decorators) {
    _decorators = decorators;
  }

  const LogDecorators& decorators() const {
    return _decorators;
  }

  const char* config_string() const {
    return _config_string;
  }

  LogOutput() : _config_string(NULL), _config_string_buffer_size(0) {
  }

  virtual ~LogOutput();

  // If the output can be rotated, trigger a forced rotation, otherwise do nothing.
  // Log outputs with rotation capabilities should override this.
  virtual void force_rotate() {
    // Do nothing by default.
  }

  virtual void describe(outputStream *out);

  virtual const char* name() const = 0;
  virtual bool initialize(const char* options, outputStream* errstream) = 0;
  virtual int write(const LogDecorations& decorations, const char* msg) = 0;
  virtual int write(LogMessageBuffer::Iterator msg_iterator) = 0;
};

#endif // SHARE_VM_LOGGING_LOGOUTPUT_HPP
