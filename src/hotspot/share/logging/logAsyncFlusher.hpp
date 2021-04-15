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
#ifndef SHARE_LOGGING_ASYNC_FLUSHER_HPP
#define SHARE_LOGGING_ASYNC_FLUSHER_HPP
#include "logging/logDecorations.hpp"
#include "logging/logFileOutput.hpp"
#include "logging/logMessageBuffer.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/nonJavaThread.hpp"
#include "utilities/hashtable.hpp"
#include "utilities/linkedlist.hpp"

template <typename E>
class LinkedListDeque : private LinkedListImpl<E, ResourceObj::C_HEAP, mtLogging> {
 private:
  LinkedListNode<E>* _tail;
  size_t _size;

 public:
  LinkedListDeque() : _tail(NULL), _size(0) {}
  void push_back(const E& e) {
    if (!_tail)
      _tail = this->add(e);
    else
      _tail = this->insert_after(e, _tail);

    ++_size;
  }

  void pop_all(LinkedList<E>* logs) {
    logs->move(static_cast<LinkedList<E>* >(this));
    _tail = NULL;
    _size = 0;
  }

  void pop_front() {
    LinkedListNode<E>* h = this->unlink_head();
    if (h == _tail) {
      _tail = NULL;
    }

    if (h != NULL) {
      --_size;
      this->delete_node(h);
    }
  }

  size_t size() const { return _size; }

  const E* front() const {
    return this->_head == NULL ? NULL : this->_head->peek();
  }

  const E* back() const {
    return _tail == NULL ? NULL : _tail->peek();
  }
};

class AsyncLogMessage {
  LogFileOutput& _output;
  mutable char* _message;
  LogDecorators _decorators;
  LogLevelType _level;
  const LogTagSet& _tagset;

public:
  AsyncLogMessage(LogFileOutput& output, const LogDecorations& decorations, const char* msg)
    : _output(output), _decorators(output.decorators()),
    _level(decorations.get_level()), _tagset(decorations.get_logTagSet()) {
      // allow to fail here, then _message is NULL
      _message = os::strdup(msg, mtLogging);
    }

  ~AsyncLogMessage() {
    if (_message != NULL) {
      os::free(_message);
      _message = NULL;
    }
  }

  AsyncLogMessage(const AsyncLogMessage& o)
    :_output(o._output), _decorators(o._decorators), _level(o._level), _tagset(o._tagset) {
    _message = o._message;
    o._message = NULL; // transfer the ownership of _message to this
  }

  void writeback();

  bool equals(const AsyncLogMessage& o) const {
    return (&_output == &o._output) && (_message == o._message || !strcmp(_message, o._message));
  }

  const char* message() const { return _message; }
  LogFileOutput* output() const { return &_output; }
};

typedef LinkedListDeque<AsyncLogMessage> AsyncLogBuffer;
typedef KVHashtable<LogFileOutput*, uintx, mtLogging> AsyncLogMap;
struct AsyncLogMapIterator {
  bool do_entry(LogFileOutput* output, uintx* counter);
};

// Flusher is a NonJavaThread which manages a FIFO capacity-bound buffer.
class LogAsyncFlusher : public NonJavaThread {
 private:
  static LogAsyncFlusher* _instance;

  volatile bool _should_terminate;
  // The semantics of _lock is like JVM monitor.
  // This thread sleeps and only wakes up by the monitor if any of events happen.
  //   1. buffer is half-full
  //   2. buffer is full
  //   3. timeout defined by LogAsyncInterval
  //
  // It also roles as a lock to consolidate buffer's MT-safety.
  Monitor _lock;
  AsyncLogMap _stats; // statistics of dropping messages.
  AsyncLogBuffer _buffer;

  LogAsyncFlusher();
  void enqueue_impl(const AsyncLogMessage& msg);
  void run() override;
  char* name() const override { return (char*)"AsyncLog Thread"; }
 public:
  void enqueue(LogFileOutput& output, const LogDecorations& decorations, const char* msg);
  void enqueue(LogFileOutput& output, LogMessageBuffer::Iterator msg_iterator);
  void flush();

  static LogAsyncFlusher* instance();
  // None of following functions are thread-safe.
  static void initialize();
  static void terminate();
};

#endif // SHARE_LOGGING_ASYNC_FLUSHER_HPP
