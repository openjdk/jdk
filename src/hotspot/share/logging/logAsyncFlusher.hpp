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
#include "memory/resourceArea.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/task.hpp"
#include "utilities/linkedlist.hpp"
#include "utilities/pair.hpp"

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
      log_drop(h->data());
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

  void log_drop(E* e) {}
};

class LogFileOutput;

class AsyncLogMessage {
  LogFileOutput& _output;
  mutable char* _message;
  LogDecorators _decorators;
  LogLevelType _level;
  const LogTagSet& _tagset;

public:
  AsyncLogMessage(LogFileOutput& output, const LogDecorations& decorations, const char* msg)
    : _output(output), _decorators(decorations.get_decorators()),
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
};

typedef LinkedListDeque<AsyncLogMessage> AsyncLogBuffer;

class LogAsyncFlusher : public PeriodicTask {
 private:
  static LogAsyncFlusher* _instance;
  Mutex _lock;
  AsyncLogBuffer _buffer;

  LogAsyncFlusher(size_t interval/*ms*/) : PeriodicTask(interval),
                  _lock(Mutex::tty, "logAsyncFlusher",
                  Mutex::_allow_vm_block_flag, Mutex::_safepoint_check_never) {
    this->enroll();
  }

 protected:
  void task();

 public:
  void enqueue(LogFileOutput& output, const LogDecorations& decorations, const char* msg);
  void flush() { task(); }

  // none of following functions are thread-safe.
  // Meyer's singleton is not thread-safe until C++11.
  static void initialize();
  static void cleanup();
  static LogAsyncFlusher* instance();
};

#endif // SHARE_LOGGING_ASYNC_FLUSHER_HPP
