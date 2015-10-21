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
#include "logging/logDecorators.hpp"
#include "logging/logDecorations.hpp"
#include "logging/logFileStreamOutput.hpp"
#include "memory/allocation.inline.hpp"

LogStdoutOutput LogStdoutOutput::_instance;
LogStderrOutput LogStderrOutput::_instance;

int LogFileStreamOutput::write(const LogDecorations& decorations, const char* msg) {
  char decoration_buf[LogDecorations::DecorationsBufferSize];
  char* position = decoration_buf;
  int total_written = 0;

  for (uint i = 0; i < LogDecorators::Count; i++) {
    LogDecorators::Decorator decorator = static_cast<LogDecorators::Decorator>(i);
    if (!_decorators.is_decorator(decorator)) {
      continue;
    }
    int written = jio_snprintf(position, sizeof(decoration_buf) - total_written, "[%-*s]",
                               _decorator_padding[decorator],
                               decorations.decoration(decorator));
    if (written <= 0) {
      return -1;
    } else if (static_cast<size_t>(written - 2) > _decorator_padding[decorator]) {
      _decorator_padding[decorator] = written - 2;
    }
    position += written;
    total_written += written;
  }

  if (total_written == 0) {
    total_written = jio_fprintf(_stream, "%s\n", msg);
  } else {
    total_written = jio_fprintf(_stream, "%s %s\n", decoration_buf, msg);
  }
  fflush(_stream);
  return total_written;
}
