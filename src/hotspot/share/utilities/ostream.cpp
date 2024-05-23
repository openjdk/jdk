/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "cds/classListWriter.hpp"
#include "compiler/compileLog.hpp"
#include "jvm.h"
#include "memory/allocation.inline.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/arguments.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/orderAccess.hpp"
#include "runtime/os.inline.hpp"
#include "runtime/safepoint.hpp"
#include "runtime/vm_version.hpp"
#include "utilities/defaultStream.hpp"
#include "utilities/macros.hpp"
#include "utilities/ostream.hpp"
#include "utilities/vmError.hpp"
#include "utilities/xmlstream.hpp"

// Declarations of jvm methods
extern "C" void jio_print(const char* s, size_t len);
extern "C" int jio_printf(const char *fmt, ...);

outputStream::outputStream() {
  _position    = 0;
  _precount    = 0;
  _indentation = 0;
  _scratch     = nullptr;
  _scratch_len = 0;
}

outputStream::outputStream(bool has_time_stamps) {
  _position    = 0;
  _precount    = 0;
  _indentation = 0;
  _scratch     = nullptr;
  _scratch_len = 0;
  if (has_time_stamps)  _stamp.update();
}

bool outputStream::update_position(const char* s, size_t len) {
  bool saw_newline = false;
  for (size_t i = 0; i < len; i++) {
    char ch = s[i];
    if (ch == '\n') {
      saw_newline = true;
      _precount += _position + 1;
      _position = 0;
    } else if (ch == '\t') {
      int tw = 8 - (_position & 7);
      _position += tw;
      _precount -= tw-1;  // invariant:  _precount + _position == total count
    } else {
      _position += 1;
    }
  }
  return saw_newline;
}

// Execute a vsprintf, using the given buffer if necessary.
// Return a pointer to the formatted string.
const char* outputStream::do_vsnprintf(char* buffer, size_t buflen,
                                       const char* format, va_list ap,
                                       bool add_cr,
                                       size_t& result_len) {
  assert(buflen >= 2, "buffer too small");

  const char* result;
  if (add_cr)  buflen--;
  if (!strchr(format, '%')) {
    // constant format string
    result = format;
    result_len = strlen(result);
    if (add_cr && result_len >= buflen)  result_len = buflen-1;  // truncate
  } else if (format[0] == '%' && format[1] == 's' && format[2] == '\0') {
    // trivial copy-through format string
    result = va_arg(ap, const char*);
    result_len = strlen(result);
    if (add_cr && result_len >= buflen)  result_len = buflen-1;  // truncate
  } else {
    int required_len = os::vsnprintf(buffer, buflen, format, ap);
    assert(required_len >= 0, "vsnprintf encoding error");
    result = buffer;
    if ((size_t)required_len < buflen) {
      result_len = required_len;
    } else {
      DEBUG_ONLY(warning("outputStream::do_vsnprintf output truncated -- buffer length is %d bytes but %d bytes are needed.",
                         add_cr ? (int)buflen + 1 : (int)buflen, add_cr ? required_len + 2 : required_len + 1);)
      result_len = buflen - 1;
    }
  }
  if (add_cr) {
    if (result != buffer) {
      memcpy(buffer, result, result_len);
      result = buffer;
    }
    buffer[result_len++] = '\n';
    buffer[result_len] = 0;
  }
  return result;
}

void outputStream::do_vsnprintf_and_write_with_automatic_buffer(const char* format, va_list ap, bool add_cr) {
  char buffer[O_BUFLEN];
  size_t len;
  const char* str = do_vsnprintf(buffer, sizeof(buffer), format, ap, add_cr, len);
  write(str, len);
}

void outputStream::do_vsnprintf_and_write_with_scratch_buffer(const char* format, va_list ap, bool add_cr) {
  size_t len;
  const char* str = do_vsnprintf(_scratch, _scratch_len, format, ap, add_cr, len);
  write(str, len);
}

void outputStream::do_vsnprintf_and_write(const char* format, va_list ap, bool add_cr) {
  if (_scratch) {
    do_vsnprintf_and_write_with_scratch_buffer(format, ap, add_cr);
  } else {
    do_vsnprintf_and_write_with_automatic_buffer(format, ap, add_cr);
  }
}

void outputStream::print(const char* format, ...) {
  va_list ap;
  va_start(ap, format);
  do_vsnprintf_and_write(format, ap, false);
  va_end(ap);
}

void outputStream::print_cr(const char* format, ...) {
  va_list ap;
  va_start(ap, format);
  do_vsnprintf_and_write(format, ap, true);
  va_end(ap);
}

void outputStream::vprint(const char *format, va_list argptr) {
  do_vsnprintf_and_write(format, argptr, false);
}

void outputStream::vprint_cr(const char* format, va_list argptr) {
  do_vsnprintf_and_write(format, argptr, true);
}

void outputStream::fill_to(int col) {
  int need_fill = col - position();
  sp(need_fill);
}

void outputStream::move_to(int col, int slop, int min_space) {
  if (position() >= col + slop)
    cr();
  int need_fill = col - position();
  if (need_fill < min_space)
    need_fill = min_space;
  sp(need_fill);
}

void outputStream::put(char ch) {
  assert(ch != 0, "please fix call site");
  char buf[] = { ch, '\0' };
  write(buf, 1);
}

void outputStream::sp(int count) {
  if (count < 0)  return;

  while (count > 0) {
    int nw = (count > 8) ? 8 : count;
    this->write("        ", nw);
    count -= nw;
  }
}

void outputStream::cr() {
  this->write("\n", 1);
}

void outputStream::cr_indent() {
  cr(); indent();
}

void outputStream::stamp() {
  if (! _stamp.is_updated()) {
    _stamp.update(); // start at 0 on first call to stamp()
  }

  // outputStream::stamp() may get called by ostream_abort(), use snprintf
  // to avoid allocating large stack buffer in print().
  char buf[40];
  jio_snprintf(buf, sizeof(buf), "%.3f", _stamp.seconds());
  print_raw(buf);
}

void outputStream::stamp(bool guard,
                         const char* prefix,
                         const char* suffix) {
  if (!guard) {
    return;
  }
  print_raw(prefix);
  stamp();
  print_raw(suffix);
}

void outputStream::date_stamp(bool guard,
                              const char* prefix,
                              const char* suffix) {
  if (!guard) {
    return;
  }
  print_raw(prefix);
  static const char error_time[] = "yyyy-mm-ddThh:mm:ss.mmm+zzzz";
  static const int buffer_length = 32;
  char buffer[buffer_length];
  const char* iso8601_result = os::iso8601_time(buffer, buffer_length);
  if (iso8601_result != nullptr) {
    print_raw(buffer);
  } else {
    print_raw(error_time);
  }
  print_raw(suffix);
  return;
}

outputStream& outputStream::indent() {
  sp(_indentation - _position);
  return *this;
}

void outputStream::print_jlong(jlong value) {
  print(JLONG_FORMAT, value);
}

void outputStream::print_julong(julong value) {
  print(JULONG_FORMAT, value);
}

/**
 * This prints out hex data in a 'windbg' or 'xxd' form, where each line is:
 *   <hex-address>: 8 * <hex-halfword> <ascii translation (optional)>
 * example:
 * 0000000: 7f44 4f46 0102 0102 0000 0000 0000 0000  .DOF............
 * 0000010: 0000 0000 0000 0040 0000 0020 0000 0005  .......@... ....
 * 0000020: 0000 0000 0000 0040 0000 0000 0000 015d  .......@.......]
 * ...
 *
 * indent is applied to each line.  Ends with a CR.
 */
void outputStream::print_data(void* data, size_t len, bool with_ascii, bool rel_addr) {
  size_t limit = (len + 16) / 16 * 16;
  for (size_t i = 0; i < limit; ++i) {
    if (i % 16 == 0) {
      if (rel_addr) {
        indent().print("%07" PRIxPTR ":", i);
      } else {
        indent().print(PTR_FORMAT ":", p2i((unsigned char*)data + i));
      }
    }
    if (i % 2 == 0) {
      print(" ");
    }
    if (i < len) {
      print("%02x", ((unsigned char*)data)[i]);
    } else {
      print("  ");
    }
    if ((i + 1) % 16 == 0) {
      if (with_ascii) {
        print("  ");
        for (size_t j = 0; j < 16; ++j) {
          size_t idx = i + j - 15;
          if (idx < len) {
            char c = ((char*)data)[idx];
            print("%c", c >= 32 && c <= 126 ? c : '.');
          }
        }
      }
      cr();
    }
  }
}

stringStream::stringStream(size_t initial_capacity) :
  outputStream(),
  _buffer(_small_buffer),
  _written(0),
  _capacity(sizeof(_small_buffer)),
  _is_fixed(false)
{
  if (initial_capacity > _capacity) {
    grow(initial_capacity);
  }
  zero_terminate();
}

// useful for output to fixed chunks of memory, such as performance counters
stringStream::stringStream(char* fixed_buffer, size_t fixed_buffer_size) :
  outputStream(),
  _buffer(fixed_buffer),
  _written(0),
  _capacity(fixed_buffer_size),
  _is_fixed(true)
{
  zero_terminate();
}

// Grow backing buffer to desired capacity. Don't call for fixed buffers
void stringStream::grow(size_t new_capacity) {
  assert(!_is_fixed, "Don't call for caller provided buffers");
  assert(new_capacity > _capacity, "Sanity");
  assert(new_capacity > sizeof(_small_buffer), "Sanity");
  if (_buffer == _small_buffer) {
    _buffer = NEW_C_HEAP_ARRAY(char, new_capacity, mtInternal);
    _capacity = new_capacity;
    if (_written > 0) {
      ::memcpy(_buffer, _small_buffer, _written);
    }
    zero_terminate();
  } else {
    _buffer = REALLOC_C_HEAP_ARRAY(char, _buffer, new_capacity, mtInternal);
    _capacity = new_capacity;
  }
}

void stringStream::write(const char* s, size_t len) {
  assert(_is_frozen == false, "Modification forbidden");
  assert(_capacity >= _written + 1, "Sanity");
  if (len == 0) {
    return;
  }
  const size_t reasonable_max_len = 1 * G;
  if (len >= reasonable_max_len) {
    assert(false, "bad length? (" SIZE_FORMAT ")", len);
    return;
  }
  size_t write_len = 0;
  if (_is_fixed) {
    write_len = MIN2(len, _capacity - _written - 1);
  } else {
    write_len = len;
    size_t needed = _written + len + 1;
    if (needed > _capacity) {
      grow(MAX2(needed, _capacity * 2));
    }
  }
  assert(_written + write_len + 1 <= _capacity, "stringStream oob");
  if (write_len > 0) {
    ::memcpy(_buffer + _written, s, write_len);
    _written += write_len;
    zero_terminate();
  }

  // Note that the following does not depend on write_len.
  // This means that position and count get updated
  // even when overflow occurs.
  update_position(s, len);
}

void stringStream::zero_terminate() {
  assert(_buffer != nullptr &&
         _written < _capacity, "sanity");
  _buffer[_written] = '\0';
}

void stringStream::reset() {
  assert(_is_frozen == false, "Modification forbidden");
  _written = 0; _precount = 0; _position = 0;
  zero_terminate();
}

char* stringStream::as_string(bool c_heap) const {
  char* copy = c_heap ?
    NEW_C_HEAP_ARRAY(char, _written + 1, mtInternal) : NEW_RESOURCE_ARRAY(char, _written + 1);
  ::memcpy(copy, _buffer, _written);
  copy[_written] = '\0';  // terminating null
  if (c_heap) {
    // Need to ensure our content is written to memory before we return
    // the pointer to it.
    OrderAccess::storestore();
  }
  return copy;
}

stringStream::~stringStream() {
  if (!_is_fixed && _buffer != _small_buffer) {
    FREE_C_HEAP_ARRAY(char, _buffer);
  }
}

// tty needs to be always accessible since there are code paths that may write to it
// outside of the VM lifespan.
// Examples for pre-VM-init accesses: Early NMT init, Early UL init
// Examples for post-VM-exit accesses: many, e.g. NMT C-heap bounds checker, signal handling, AGCT, ...
// During lifetime tty is served by an instance of defaultStream. That instance's deletion cannot
// be (easily) postponed or omitted since it has ties to the JVM infrastructure.
// The policy followed here is a compromise reached during review of JDK-8292351:
// - pre-init: we silently swallow all output. We won't see anything, but at least won't crash
// - post-exit: we write to a simple fdStream, but somewhat mimic the behavior of the real defaultStream
static nullStream tty_preinit_stream;
outputStream* tty = &tty_preinit_stream;

xmlStream*   xtty;

#define EXTRACHARLEN   32
#define CURRENTAPPX    ".current"
// convert YYYY-MM-DD HH:MM:SS to YYYY-MM-DD_HH-MM-SS
static char* get_datetime_string(char *buf, size_t len) {
  os::local_time_string(buf, len);
  int i = (int)strlen(buf);
  while (--i >= 0) {
    if (buf[i] == ' ') buf[i] = '_';
    else if (buf[i] == ':') buf[i] = '-';
  }
  return buf;
}

static const char* make_log_name_internal(const char* log_name, const char* force_directory,
                                                int pid, const char* tms) {
  const char* basename = log_name;
  char file_sep = os::file_separator()[0];
  const char* cp;
  char  pid_text[32];

  for (cp = log_name; *cp != '\0'; cp++) {
    if (*cp == '/' || *cp == file_sep) {
      basename = cp + 1;
    }
  }
  const char* nametail = log_name;
  // Compute buffer length
  size_t buffer_length;
  if (force_directory != nullptr) {
    buffer_length = strlen(force_directory) + strlen(os::file_separator()) +
                    strlen(basename) + 1;
  } else {
    buffer_length = strlen(log_name) + 1;
  }

  const char* pts = strstr(basename, "%p");
  int pid_pos = (pts == nullptr) ? -1 : (pts - nametail);

  if (pid_pos >= 0) {
    jio_snprintf(pid_text, sizeof(pid_text), "pid%u", pid);
    buffer_length += strlen(pid_text);
  }

  pts = strstr(basename, "%t");
  int tms_pos = (pts == nullptr) ? -1 : (pts - nametail);
  if (tms_pos >= 0) {
    buffer_length += strlen(tms);
  }

  // File name is too long.
  if (buffer_length > JVM_MAXPATHLEN) {
    return nullptr;
  }

  // Create big enough buffer.
  char *buf = NEW_C_HEAP_ARRAY(char, buffer_length, mtInternal);

  strcpy(buf, "");
  if (force_directory != nullptr) {
    strcat(buf, force_directory);
    strcat(buf, os::file_separator());
    nametail = basename;       // completely skip directory prefix
  }

  // who is first, %p or %t?
  int first = -1, second = -1;
  const char *p1st = nullptr;
  const char *p2nd = nullptr;

  if (pid_pos >= 0 && tms_pos >= 0) {
    // contains both %p and %t
    if (pid_pos < tms_pos) {
      // case foo%pbar%tmonkey.log
      first  = pid_pos;
      p1st   = pid_text;
      second = tms_pos;
      p2nd   = tms;
    } else {
      // case foo%tbar%pmonkey.log
      first  = tms_pos;
      p1st   = tms;
      second = pid_pos;
      p2nd   = pid_text;
    }
  } else if (pid_pos >= 0) {
    // contains %p only
    first  = pid_pos;
    p1st   = pid_text;
  } else if (tms_pos >= 0) {
    // contains %t only
    first  = tms_pos;
    p1st   = tms;
  }

  int buf_pos = (int)strlen(buf);
  const char* tail = nametail;

  if (first >= 0) {
    tail = nametail + first + 2;
    strncpy(&buf[buf_pos], nametail, first);
    strcpy(&buf[buf_pos + first], p1st);
    buf_pos = (int)strlen(buf);
    if (second >= 0) {
      strncpy(&buf[buf_pos], tail, second - first - 2);
      strcpy(&buf[buf_pos + second - first - 2], p2nd);
      tail = nametail + second + 2;
    }
  }
  strcat(buf, tail);      // append rest of name, or all of name
  return buf;
}

// log_name comes from -XX:LogFile=log_name or
// -XX:DumpLoadedClassList=<file_name>
// in log_name, %p => pid1234 and
//              %t => YYYY-MM-DD_HH-MM-SS
const char* make_log_name(const char* log_name, const char* force_directory) {
  char timestr[32];
  get_datetime_string(timestr, sizeof(timestr));
  return make_log_name_internal(log_name, force_directory, os::current_process_id(),
                                timestr);
}

fileStream::fileStream(const char* file_name) {
  _file = os::fopen(file_name, "w");
  if (_file != nullptr) {
    _need_close = true;
  } else {
    warning("Cannot open file %s due to %s\n", file_name, os::strerror(errno));
    _need_close = false;
  }
}

fileStream::fileStream(const char* file_name, const char* opentype) {
  _file = os::fopen(file_name, opentype);
  if (_file != nullptr) {
    _need_close = true;
  } else {
    warning("Cannot open file %s due to %s\n", file_name, os::strerror(errno));
    _need_close = false;
  }
}

void fileStream::write(const char* s, size_t len) {
  if (_file != nullptr)  {
    // Make an unused local variable to avoid warning from gcc compiler.
    size_t count = fwrite(s, 1, len, _file);
    update_position(s, len);
  }
}

long fileStream::fileSize() {
  long size = -1;
  if (_file != nullptr) {
    long pos = ::ftell(_file);
    if (pos < 0) return pos;
    if (::fseek(_file, 0, SEEK_END) == 0) {
      size = ::ftell(_file);
    }
    ::fseek(_file, pos, SEEK_SET);
  }
  return size;
}

fileStream::~fileStream() {
  if (_file != nullptr) {
    close();
    _file = nullptr;
  }
}

void fileStream::flush() {
  if (_file != nullptr) {
    fflush(_file);
  }
}

fdStream fdStream::_stdout_stream(1);
fdStream fdStream::_stderr_stream(2);

void fdStream::write(const char* s, size_t len) {
  if (_fd != -1) {
    // Make an unused local variable to avoid warning from gcc compiler.
    ssize_t count = ::write(_fd, s, (int)len);
    update_position(s, len);
  }
}

defaultStream* defaultStream::instance = nullptr;
int defaultStream::_output_fd = 1;
int defaultStream::_error_fd  = 2;
FILE* defaultStream::_output_stream = stdout;
FILE* defaultStream::_error_stream  = stderr;

#define LOG_MAJOR_VERSION 160
#define LOG_MINOR_VERSION 1

void defaultStream::init() {
  _inited = true;
  if (LogVMOutput || LogCompilation) {
    init_log();
  }
}

bool defaultStream::has_log_file() {
  // lazily create log file (at startup, LogVMOutput is false even
  // if +LogVMOutput is used, because the flags haven't been parsed yet)
  // For safer printing during fatal error handling, do not init logfile
  // if a VM error has been reported.
  if (!_inited && !VMError::is_error_reported())  init();
  return _log_file != nullptr;
}

fileStream* defaultStream::open_file(const char* log_name) {
  const char* try_name = make_log_name(log_name, nullptr);
  if (try_name == nullptr) {
    warning("Cannot open file %s: file name is too long.\n", log_name);
    return nullptr;
  }

  fileStream* file = new (mtInternal) fileStream(try_name);
  FREE_C_HEAP_ARRAY(char, try_name);
  if (file->is_open()) {
    return file;
  }

  // Try again to open the file in the temp directory.
  delete file;
  // Note: This feature is for maintainer use only.  No need for L10N.
  jio_printf("Warning:  Cannot open log file: %s\n", log_name);
  try_name = make_log_name(log_name, os::get_temp_directory());
  if (try_name == nullptr) {
    warning("Cannot open file %s: file name is too long for directory %s.\n", log_name, os::get_temp_directory());
    return nullptr;
  }

  jio_printf("Warning:  Forcing option -XX:LogFile=%s\n", try_name);

  file = new (mtInternal) fileStream(try_name);
  FREE_C_HEAP_ARRAY(char, try_name);
  if (file->is_open()) {
    return file;
  }

  delete file;
  return nullptr;
}

void defaultStream::init_log() {
  // %%% Need a MutexLocker?
  const char* log_name = LogFile != nullptr ? LogFile : "hotspot_%p.log";
  fileStream* file = open_file(log_name);

  if (file != nullptr) {
    _log_file = file;
    _outer_xmlStream = new(mtInternal) xmlStream(file);
    start_log();
  } else {
    // and leave xtty as null
    LogVMOutput = false;
    DisplayVMOutput = true;
    LogCompilation = false;
  }
}

void defaultStream::start_log() {
  xmlStream*xs = _outer_xmlStream;
    if (this == tty)  xtty = xs;
    // Write XML header.
    xs->print_cr("<?xml version='1.0' encoding='UTF-8'?>");
    // (For now, don't bother to issue a DTD for this private format.)

    // Calculate the start time of the log as ms since the epoch: this is
    // the current time in ms minus the uptime in ms.
    jlong time_ms = os::javaTimeMillis() - tty->time_stamp().milliseconds();
    xs->head("hotspot_log version='%d %d'"
             " process='%d' time_ms='" INT64_FORMAT "'",
             LOG_MAJOR_VERSION, LOG_MINOR_VERSION,
             os::current_process_id(), (int64_t)time_ms);
    // Write VM version header immediately.
    xs->head("vm_version");
    xs->head("name"); xs->text("%s", VM_Version::vm_name()); xs->cr();
    xs->tail("name");
    xs->head("release"); xs->text("%s", VM_Version::vm_release()); xs->cr();
    xs->tail("release");
    xs->head("info"); xs->text("%s", VM_Version::internal_vm_info_string()); xs->cr();
    xs->tail("info");
    xs->tail("vm_version");
    // Record information about the command-line invocation.
    xs->head("vm_arguments");  // Cf. Arguments::print_on()
    if (Arguments::num_jvm_flags() > 0) {
      xs->head("flags");
      Arguments::print_jvm_flags_on(xs->text());
      xs->tail("flags");
    }
    if (Arguments::num_jvm_args() > 0) {
      xs->head("args");
      Arguments::print_jvm_args_on(xs->text());
      xs->tail("args");
    }
    if (Arguments::java_command() != nullptr) {
      xs->head("command"); xs->text()->print_cr("%s", Arguments::java_command());
      xs->tail("command");
    }
    if (Arguments::sun_java_launcher() != nullptr) {
      xs->head("launcher"); xs->text()->print_cr("%s", Arguments::sun_java_launcher());
      xs->tail("launcher");
    }
    if (Arguments::system_properties() !=  nullptr) {
      xs->head("properties");
      // Print it as a java-style property list.
      // System properties don't generally contain newlines, so don't bother with unparsing.
      outputStream *text = xs->text();
      for (SystemProperty* p = Arguments::system_properties(); p != nullptr; p = p->next()) {
        assert(p->key() != nullptr, "p->key() is null");
        if (p->readable()) {
          // Print in two stages to avoid problems with long
          // keys/values.
          text->print_raw(p->key());
          text->put('=');
          assert(p->value() != nullptr, "p->value() is null");
          text->print_raw_cr(p->value());
        }
      }
      xs->tail("properties");
    }
    xs->tail("vm_arguments");
    // tty output per se is grouped under the <tty>...</tty> element.
    xs->head("tty");
    // All further non-markup text gets copied to the tty:
    xs->_text = this;  // requires friend declaration!
}

// finish_log() is called during normal VM shutdown. finish_log_on_error() is
// called by ostream_abort() after a fatal error.
//
void defaultStream::finish_log() {
  xmlStream* xs = _outer_xmlStream;
  xs->done("tty");

  // Other log forks are appended here, at the End of Time:
  CompileLog::finish_log(xs->out());  // write compile logging, if any, now

  xs->done("hotspot_log");
  xs->flush();

  fileStream* file = _log_file;
  _log_file = nullptr;

  delete _outer_xmlStream;
  _outer_xmlStream = nullptr;

  file->flush();
  delete file;
}

void defaultStream::finish_log_on_error(char *buf, int buflen) {
  xmlStream* xs = _outer_xmlStream;

  if (xs && xs->out()) {

    xs->done_raw("tty");

    // Other log forks are appended here, at the End of Time:
    CompileLog::finish_log_on_error(xs->out(), buf, buflen);  // write compile logging, if any, now

    xs->done_raw("hotspot_log");
    xs->flush();

    fileStream* file = _log_file;
    _log_file = nullptr;
    _outer_xmlStream = nullptr;

    if (file) {
      file->flush();

      // Can't delete or close the file because delete and fclose aren't
      // async-safe. We are about to die, so leave it to the kernel.
      // delete file;
    }
  }
}

intx defaultStream::hold(intx writer_id) {
  bool has_log = has_log_file();  // check before locking
  if (// impossible, but who knows?
      writer_id == NO_WRITER ||

      // bootstrap problem
      tty_lock == nullptr ||

      // can't grab a lock if current Thread isn't set
      Thread::current_or_null() == nullptr ||

      // developer hook
      !SerializeVMOutput ||

      // VM already unhealthy
      VMError::is_error_reported() ||

      // safepoint == global lock (for VM only)
      (SafepointSynchronize::is_synchronizing() &&
       Thread::current()->is_VM_thread())
      ) {
    // do not attempt to lock unless we know the thread and the VM is healthy
    return NO_WRITER;
  }
  if (_writer == writer_id) {
    // already held, no need to re-grab the lock
    return NO_WRITER;
  }
  tty_lock->lock_without_safepoint_check();
  // got the lock
  if (writer_id != _last_writer) {
    if (has_log) {
      _log_file->bol();
      // output a hint where this output is coming from:
      _log_file->print_cr("<writer thread='" UINTX_FORMAT "'/>", writer_id);
    }
    _last_writer = writer_id;
  }
  _writer = writer_id;
  return writer_id;
}

void defaultStream::release(intx holder) {
  if (holder == NO_WRITER) {
    // nothing to release:  either a recursive lock, or we scribbled (too bad)
    return;
  }
  if (_writer != holder) {
    return;  // already unlocked, perhaps via break_tty_lock_for_safepoint
  }
  _writer = NO_WRITER;
  tty_lock->unlock();
}

void defaultStream::write(const char* s, size_t len) {
  intx thread_id = os::current_thread_id();
  intx holder = hold(thread_id);

  if (DisplayVMOutput &&
      (_outer_xmlStream == nullptr || !_outer_xmlStream->inside_attrs())) {
    // print to output stream. It can be redirected by a vfprintf hook
    jio_print(s, len);
  }

  // print to log file
  if (has_log_file() && _outer_xmlStream != nullptr) {
     _outer_xmlStream->write_text(s, len);
    bool nl = update_position(s, len);
    // flush the log file too, if there were any newlines
    if (nl) {
      flush();
    }
  } else {
    update_position(s, len);
  }

  release(holder);
}

intx ttyLocker::hold_tty() {
  if (defaultStream::instance == nullptr)  return defaultStream::NO_WRITER;
  intx thread_id = os::current_thread_id();
  return defaultStream::instance->hold(thread_id);
}

void ttyLocker::release_tty(intx holder) {
  if (holder == defaultStream::NO_WRITER)  return;
  defaultStream::instance->release(holder);
}

bool ttyLocker::release_tty_if_locked() {
  intx thread_id = os::current_thread_id();
  if (defaultStream::instance->writer() == thread_id) {
    // release the lock and return true so callers know if was
    // previously held.
    release_tty(thread_id);
    return true;
  }
  return false;
}

void ttyLocker::break_tty_lock_for_safepoint(intx holder) {
  if (defaultStream::instance != nullptr &&
      defaultStream::instance->writer() == holder) {
    if (xtty != nullptr) {
      xtty->print_cr("<!-- safepoint while printing -->");
    }
    defaultStream::instance->release(holder);
  }
  // (else there was no lock to break)
}

void ostream_init() {
  if (defaultStream::instance == nullptr) {
    defaultStream::instance = new(mtInternal) defaultStream();
    tty = defaultStream::instance;

    // We want to ensure that time stamps in GC logs consider time 0
    // the time when the JVM is initialized, not the first time we ask
    // for a time stamp. So, here, we explicitly update the time stamp
    // of tty.
    tty->time_stamp().update_to(1);
  }
}

void ostream_init_log() {
  // Note : this must be called AFTER ostream_init()

  ClassListWriter::init();

  // If we haven't lazily initialized the logfile yet, do it now,
  // to avoid the possibility of lazy initialization during a VM
  // crash, which can affect the stability of the fatal error handler.
  defaultStream::instance->has_log_file();
}

// ostream_exit() is called during normal VM exit to finish log files, flush
// output and free resource.
void ostream_exit() {
  static bool ostream_exit_called = false;
  if (ostream_exit_called)  return;
  ostream_exit_called = true;
  ClassListWriter::delete_classlist();
  // Make sure tty works after VM exit by assigning an always-on functioning fdStream.
  outputStream* tmp = tty;
  tty = DisplayVMOutputToStderr ? fdStream::stdout_stream() : fdStream::stderr_stream();
  if (tmp != &tty_preinit_stream && tmp != defaultStream::instance) {
    delete tmp;
  }
  delete defaultStream::instance;
  xtty = nullptr;
  defaultStream::instance = nullptr;
}

// ostream_abort() is called by os::abort() when VM is about to die.
void ostream_abort() {
  // Here we can't delete tty, just flush its output
  if (tty) tty->flush();

  if (defaultStream::instance != nullptr) {
    static char buf[4096];
    defaultStream::instance->finish_log_on_error(buf, sizeof(buf));
  }
}

bufferedStream::bufferedStream(size_t initial_size, size_t bufmax) : outputStream() {
  buffer_length = initial_size;
  buffer        = NEW_C_HEAP_ARRAY(char, buffer_length, mtInternal);
  buffer_pos    = 0;
  buffer_max    = bufmax;
  truncated     = false;
}

void bufferedStream::write(const char* s, size_t len) {

  if (truncated) {
    return;
  }

  if(buffer_pos + len > buffer_max) {
    flush(); // Note: may be a noop.
  }

  size_t end = buffer_pos + len;
  if (end >= buffer_length) {
    // For small overruns, double the buffer.  For larger ones,
    // increase to the requested size.
    if (end < buffer_length * 2) {
      end = buffer_length * 2;
    }
    // Impose a cap beyond which the buffer cannot grow - a size which
    // in all probability indicates a real error, e.g. faulty printing
    // code looping, while not affecting cases of just-very-large-but-its-normal
    // output.
    const size_t reasonable_cap = MAX2(100 * M, buffer_max * 2);
    if (end > reasonable_cap) {
      // In debug VM, assert right away.
      assert(false, "Exceeded max buffer size for this string.");
      // Release VM: silently truncate. We do this since these kind of errors
      // are both difficult to predict with testing (depending on logging content)
      // and usually not serious enough to kill a production VM for it.
      end = reasonable_cap;
      size_t remaining = end - buffer_pos;
      if (len >= remaining) {
        len = remaining - 1;
        truncated = true;
      }
    }
    if (buffer_length < end) {
      buffer = REALLOC_C_HEAP_ARRAY(char, buffer, end, mtInternal);
      buffer_length = end;
    }
  }
  if (len > 0) {
    memcpy(buffer + buffer_pos, s, len);
    buffer_pos += len;
    update_position(s, len);
  }
}

char* bufferedStream::as_string() {
  char* copy = NEW_RESOURCE_ARRAY(char, buffer_pos+1);
  strncpy(copy, buffer, buffer_pos);
  copy[buffer_pos] = 0;  // terminating null
  return copy;
}

bufferedStream::~bufferedStream() {
  FREE_C_HEAP_ARRAY(char, buffer);
}

#ifndef PRODUCT

#if defined(LINUX) || defined(AIX) || defined(_ALLBSD_SOURCE)
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <netdb.h>
#include <arpa/inet.h>
#elif defined(_WINDOWS)
#include <Ws2tcpip.h>
#endif

// Network access
networkStream::networkStream() : bufferedStream(1024*10, 1024*10) {

  _socket = -1;

  int result = ::socket(AF_INET, SOCK_STREAM, 0);
  if (result <= 0) {
    assert(false, "Socket could not be created!");
  } else {
    _socket = result;
  }
}

ssize_t networkStream::read(char *buf, size_t len) {
  return os::recv(_socket, buf, len, 0);
}

void networkStream::flush() {
  if (size() != 0) {
    ssize_t result = os::raw_send(_socket, (char *)base(), size(), 0);
    assert(result != -1, "connection error");
    assert(result >= 0 && (size_t)result == size(), "didn't send enough data");
  }
  reset();
}

networkStream::~networkStream() {
  close();
}

void networkStream::close() {
  if (_socket != -1) {
    flush();
    os::socket_close(_socket);
    _socket = -1;
  }
}

// host could be IP address, or a host name
bool networkStream::connect(const char *host, short port) {

  char s_port[6]; // 5 digits max plus terminator
  int ret = os::snprintf(s_port, sizeof(s_port), "%hu", (unsigned short) port);
  assert(ret > 0, "snprintf failed: %d", ret);

  struct addrinfo* addr_info = nullptr;
  struct addrinfo hints;

  memset(&hints, 0, sizeof(hints));
  hints.ai_family = AF_INET;       // Allow IPv4 only
  hints.ai_socktype = SOCK_STREAM; // TCP only

  // getaddrinfo can resolve both an IP address and a host name
  ret = getaddrinfo(host, s_port, &hints, &addr_info);
  if (ret != 0) {
    warning("networkStream::connect getaddrinfo for host %s and port %s failed: %s",
            host, s_port, gai_strerror(ret));
    return false;
  }

  ssize_t conn = os::connect(_socket, addr_info->ai_addr, (socklen_t)addr_info->ai_addrlen);
  freeaddrinfo(addr_info);
  return (conn >= 0);
}

#endif
