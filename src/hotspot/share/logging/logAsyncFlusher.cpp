/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
#include "logging/logAsyncFlusher.hpp"
#include "logging/logFileOutput.hpp"
#include "logging/logHandle.hpp"

void AsyncLogMessage::writeback() {
  if (_message != NULL) {
    // should cache this object somehow
    LogDecorations decorations(_level, _tagset, _decorators);
    _output.write_blocking(decorations, _message);
  }
}

void LogAsyncFlusher::enqueue_impl(const AsyncLogMessage& msg) {
  assert_lock_strong(&_lock);

  if (_buffer.size() >= AsyncLogBufferSize)  {
    if (Verbose) {
      const AsyncLogMessage* h = _buffer.front();
      assert(h != NULL, "sanity check");
      if (h->message() != NULL) {
        // Temporarily turn off SerializeVMOutput so defaultStream will not
        // invoke set_owner(self) for tty_lock.
        FlagSetting t(SerializeVMOutput, false);
        // The writing below can not guarantee non-blocking because tty may be piped by the filesystems
        // or throttled by XOFF, so only dump the dropping message in Verbose mode.
        tty->print_cr("asynclog dropping message: %s", h->message());
      }
    }

    _buffer.pop_front();
  }
  assert(_buffer.size() < AsyncLogBufferSize, "_buffer is over-sized.");
  _buffer.push_back(msg);
}

void LogAsyncFlusher::task() {
  LinkedListImpl<AsyncLogMessage, ResourceObj::C_HEAP, mtLogging> logs;

  { // critical area
    MutexLocker ml(&_lock, Mutex::_no_safepoint_check_flag);
    _buffer.pop_all(&logs);
  }

  LinkedListIterator<AsyncLogMessage> it(logs.head());
  while (!it.is_empty()) {
    AsyncLogMessage* e = it.next();
    e->writeback();
  }
}

void LogAsyncFlusher::enqueue(LogFileOutput& output, const LogDecorations& decorations, const char* msg) {
  AsyncLogMessage m(output, decorations, msg);

  { // critical area
    MutexLocker ml(&_lock, Mutex::_no_safepoint_check_flag);
    enqueue_impl(m);
  }
}

// LogMessageBuffer consists of a multiple-part/multiple-line messsage.
// the mutex here gurantees its interity.
void LogAsyncFlusher::enqueue(LogFileOutput& output, LogMessageBuffer::Iterator msg_iterator) {
  MutexLocker ml(&_lock, Mutex::_no_safepoint_check_flag);

  for (; !msg_iterator.is_at_end(); msg_iterator++) {
    AsyncLogMessage m(output, msg_iterator.decorations(), msg_iterator.message());
    enqueue_impl(m);
  }
}

LogAsyncFlusher* LogAsyncFlusher::_instance = NULL;

void LogAsyncFlusher::initialize() {
  if (!_instance) {
    _instance = new LogAsyncFlusher(LogAsyncInterval);
  }
}

void LogAsyncFlusher::cleanup() {
  if (_instance != NULL) {
    _instance->flush();
    delete _instance;
    _instance = NULL;
  }
}

LogAsyncFlusher* LogAsyncFlusher::instance() {
  return _instance;
}
