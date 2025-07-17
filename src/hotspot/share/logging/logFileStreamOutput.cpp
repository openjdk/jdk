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
#include "jvm.h"
#include "logging/logAsyncWriter.hpp"
#include "logging/logDecorations.hpp"
#include "logging/logDecorators.hpp"
#include "logging/logFileStreamOutput.hpp"
#include "logging/logMessageBuffer.hpp"
#include "memory/allocation.inline.hpp"
#include "utilities/defaultStream.hpp"
#include <string.h>

const char* const LogFileStreamOutput::FoldMultilinesOptionKey = "foldmultilines";

bool LogFileStreamOutput::set_option(const char* key, const char* value, outputStream* errstream) {
  bool success = false;
  if (strcmp(FoldMultilinesOptionKey, key) == 0) {
    if (strcmp(value, "true") == 0) {
      _fold_multilines = true;
      success = true;
    } else if (strcmp(value, "false") == 0) {
      _fold_multilines = false;
      success = true;
    } else {
      errstream->print_cr("Invalid option: %s must be 'true' or 'false'.", key);
    }
  }
  return success;
}

int LogFileStreamOutput::write_decorations(const LogDecorations& decorations) {
  int total_written = 0;
  char buf[LogDecorations::max_decoration_size + 1];

  for (uint i = 0; i < LogDecorators::Count; i++) {
    LogDecorators::Decorator decorator = static_cast<LogDecorators::Decorator>(i);
    if (!_decorators.is_decorator(decorator)) {
      continue;
    }

    int written = jio_fprintf(_stream, "[%-*s]",
                              _decorator_padding[decorator],
                              decorations.decoration(decorator, buf, sizeof(buf)));
    if (written <= 0) {
      return -1;
    } else if ((written - 2) > _decorator_padding[decorator]) {
      _decorator_padding[decorator] = written - 2;
    }
    total_written += written;
  }
  return total_written;
}

class FileLocker : public StackObj {
private:
  FILE *_file;

public:
  FileLocker(FILE *file) : _file(file) {
    os::flockfile(_file);
  }

  ~FileLocker() {
    os::funlockfile(_file);
  }
};

bool LogFileStreamOutput::flush() {
  bool result = true;
  if (fflush(_stream) != 0) {
    if (!_write_error_is_shown) {
      jio_fprintf(defaultStream::error_stream(),
                  "Could not flush log: %s (%s (%d))\n", name(), os::strerror(errno), errno);
      jio_fprintf(_stream, "\nERROR: Could not flush log (%d)\n", errno);
      _write_error_is_shown = true;
    }
    result = false;
  }
  return result;
}

#define WRITE_LOG_WITH_RESULT_CHECK(op, total)                \
{                                                             \
  int result = op;                                            \
  if (result < 0) {                                           \
    if (!_write_error_is_shown) {                             \
      jio_fprintf(defaultStream::error_stream(),              \
                  "Could not write log: %s\n", name());       \
      jio_fprintf(_stream, "\nERROR: Could not write log\n"); \
      _write_error_is_shown = true;                           \
      return -1;                                              \
    }                                                         \
  }                                                           \
  total += result;                                            \
}

int LogFileStreamOutput::write_internal(const LogDecorations& decorations, const char* msg) {
  int written = 0;
  const bool use_decorations = !_decorators.is_empty();

  if (!_fold_multilines) {
    const char* base = msg;
    int decorator_padding = 0;
    if (use_decorations) {
      WRITE_LOG_WITH_RESULT_CHECK(write_decorations(decorations), decorator_padding);
      WRITE_LOG_WITH_RESULT_CHECK(jio_fprintf(_stream, " "), written);
    }
    written += decorator_padding;

    // Search for newlines in the string and repeatedly print the substrings that end
    // with each newline.
    const char* next = strstr(msg, "\n");
    while (next != nullptr) {  // We have some newlines to print
      int to_print = next - base;
      WRITE_LOG_WITH_RESULT_CHECK(jio_fprintf(_stream, "%.*s\n", to_print, base), written);
      if (use_decorations) {
        WRITE_LOG_WITH_RESULT_CHECK(jio_fprintf(_stream, "[%*c] ", decorator_padding - 2, ' '), written); // Substracting 2 because decorator_padding includes the brackets
      }
      base = next + 1;
      next = strstr(base, "\n");
    }

    // Print the end of the message
    WRITE_LOG_WITH_RESULT_CHECK(jio_fprintf(_stream, "%s\n", base), written);
  } else {
    if (use_decorations) {
      WRITE_LOG_WITH_RESULT_CHECK(write_decorations(decorations), written);
      WRITE_LOG_WITH_RESULT_CHECK(jio_fprintf(_stream, " "), written);
    }
    char *dupstr = os::strdup_check_oom(msg, mtLogging);
    char *cur = dupstr;
    char *next;
    do {
      next = strpbrk(cur, "\n\\");
      if (next == nullptr) {
        WRITE_LOG_WITH_RESULT_CHECK(jio_fprintf(_stream, "%s\n", cur), written);
      } else {
        const char *found = (*next == '\n') ? "\\n" : "\\\\";
        *next = '\0';
        WRITE_LOG_WITH_RESULT_CHECK(jio_fprintf(_stream, "%s%s", cur, found), written);
        cur = next + 1;
      }
    } while (next != nullptr);
    os::free(dupstr);
  }
  return written;
}

int LogFileStreamOutput::write_blocking(const LogDecorations& decorations, const char* msg) {
  FileLocker flocker(_stream);
  int written = write_internal(decorations, msg);
  return flush() ? written : -1;
}

int LogFileStreamOutput::write(const LogDecorations& decorations, const char* msg) {
  if (AsyncLogWriter::enqueue(*this, decorations, msg)) {
    return 0;
  }

  return write_blocking(decorations, msg);
}

int LogFileStreamOutput::write(LogMessageBuffer::Iterator msg_iterator) {
  if (AsyncLogWriter::enqueue(*this, msg_iterator)) {
    return 0;
  }

  int written = 0;
  FileLocker flocker(_stream);
  for (; !msg_iterator.is_at_end(); msg_iterator++) {
    written += write_internal(msg_iterator.decorations(), msg_iterator.message());
  }

  return flush() ? written : -1;
}

void LogFileStreamOutput::describe(outputStream *out) {
  LogOutput::describe(out);
  out->print(" ");

  out->print("foldmultilines=%s", _fold_multilines ? "true" : "false");
}
