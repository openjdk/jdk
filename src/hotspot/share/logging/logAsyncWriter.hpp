/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
#ifndef SHARE_LOGGING_LOGASYNCWRITER_HPP
#define SHARE_LOGGING_LOGASYNCWRITER_HPP
#include "logging/log.hpp"
#include "logging/logDecorations.hpp"
#include "logging/logMessageBuffer.hpp"
#include "memory/allocation.hpp"
#include "runtime/mutex.hpp"
#include "runtime/nonJavaThread.hpp"
#include "runtime/semaphore.hpp"
#include "utilities/resourceHash.hpp"

class LogFileStreamOutput;

//
// ASYNC LOGGING SUPPORT
//
// Summary:
// Async Logging is working on the basis of singleton AsyncLogWriter, which manages an intermediate buffer and a flushing thread.
//
// Interface:
//
// initialize() is called once when JVM is initialized. It creates and initializes the singleton instance of AsyncLogWriter.
// Once async logging is established, there's no way to turn it off.
//
// instance() is MT-safe and returns the pointer of the singleton instance if and only if async logging is enabled and has
// successfully initialized. Clients can use its return value to determine async logging is established or not.
//
// enqueue() is the basic operation of AsyncLogWriter. Two overloading versions of it are provided to match LogOutput::write().
// They are both MT-safe and non-blocking. Derived classes of LogOutput can invoke the corresponding enqueue() in write() and
// return 0. AsyncLogWriter is responsible of copying necessary data.
//
// flush() ensures that all pending messages have been written out before it returns. It is not MT-safe in itself. When users
// change the logging configuration via jcmd, LogConfiguration::configure_output() calls flush() under the protection of the
// ConfigurationLock. In addition flush() is called during JVM termination, via LogConfiguration::finalize.
class AsyncLogWriter : public NonJavaThread {
  friend class AsyncLogTest;
  friend class AsyncLogTest_logBuffer_vm_Test;
  class AsyncLogLocker;

  // account for dropped messages
  template <AnyObj::allocation_type ALLOC_TYPE>
  using AsyncLogMap = ResourceHashtable<LogFileStreamOutput*,
                          uint32_t, 17, /*table_size*/
                          ALLOC_TYPE, mtLogging>;

  // Messsage is the envelope of a log line and its associative data.
  // Its length is variable because of the zero-terminated c-str. It is only valid when we create it using placement new
  // within a buffer.
  //
  // Example layout:
  // ---------------------------------------------
  // |_output|_decorations|"a log line", |pad| <- Message aligned.
  // |_output|_decorations|"yet another",|pad|
  // ...
  // |nullptr|_decorations|"",|pad| <- flush token
  // |<- _pos
  // ---------------------------------------------
  class Message {
    NONCOPYABLE(Message);
    ~Message() = delete;
    LogFileStreamOutput* const _output;
    const LogDecorations _decorations;
   public:
    // msglen excludes NUL-byte
    Message(LogFileStreamOutput* output, const LogDecorations& decorations, const char* msg, const size_t msglen)
      : _output(output), _decorations(decorations) {
      assert(msg != nullptr, "c-str message can not be null!");
      memcpy(reinterpret_cast<char* >(this+1), msg, msglen + 1);
    }

    // Calculate the size for a prospective Message object depending on its message length including the trailing zero
    static constexpr size_t calc_size(size_t message_len) {
      return align_up(sizeof(Message) + message_len + 1, alignof(Message));
    }

    size_t size() const {
      return calc_size(strlen(message()));
    }

    inline bool is_token() const { return _output == nullptr; }
    LogFileStreamOutput* output() const { return _output; }
    const LogDecorations& decorations() const { return _decorations; }
    const char* message() const { return reinterpret_cast<const char *>(this+1); }
  };

  class Buffer : public CHeapObj<mtLogging> {
    char* _buf;
    size_t _pos;
    const size_t _capacity;

   public:
    Buffer(size_t capacity) :  _pos(0), _capacity(capacity) {
      _buf = NEW_C_HEAP_ARRAY(char, capacity, mtLogging);
      // Ensure _pos is Message-aligned
      _pos = align_up(_buf, alignof(Message)) - _buf;
      assert(capacity >= Message::calc_size(0), "capcity must be great a token size");
    }

    ~Buffer() {
      FREE_C_HEAP_ARRAY(char, _buf);
    }

    void push_flush_token();
    bool push_back(LogFileStreamOutput* output, const LogDecorations& decorations, const char* msg);

    void reset() {
      // Ensure _pos is Message-aligned
      _pos = align_up(_buf, alignof(Message)) - _buf;
    }

    class Iterator {
      const Buffer& _buf;
      size_t _curr;

    public:
      Iterator(const Buffer& buffer): _buf(buffer), _curr(0) {}

      bool hasNext() const {
        return _curr < _buf._pos;
      }

      const Message* next() {
        assert(hasNext(), "sanity check");
        auto msg = reinterpret_cast<Message*>(_buf._buf + _curr);
        _curr = MIN2(_curr + msg->size(), _buf._pos);
        return msg;
      }
    };

    Iterator iterator() const {
      return Iterator(*this);
    }
  };

  static AsyncLogWriter* _instance;
  Semaphore _flush_sem;
  // Can't use a Monitor here as we need a low-level API that can be used without Thread::current().
  PlatformMonitor _lock;
  bool _data_available;
  volatile bool _initialized;
  AsyncLogMap<AnyObj::C_HEAP> _stats;

  // ping-pong buffers
  Buffer* _buffer;
  Buffer* _buffer_staging;

  static const LogDecorations& None;

  AsyncLogWriter();
  void enqueue_locked(LogFileStreamOutput* output, const LogDecorations& decorations, const char* msg);
  void write();
  void run() override;
  void pre_run() override {
    NonJavaThread::pre_run();
    log_debug(logging, thread)("starting AsyncLog Thread tid = " INTX_FORMAT, os::current_thread_id());
  }
  const char* name() const override { return "AsyncLog Thread"; }
  const char* type_name() const override { return "AsyncLogWriter"; }
  void print_on(outputStream* st) const override {
    st->print("\"%s\" ", name());
    Thread::print_on(st);
    st->cr();
  }

  // for testing-only
  class BufferUpdater {
    Buffer* _buf1;
    Buffer* _buf2;

   public:
    BufferUpdater(size_t newsize);
    ~BufferUpdater();
  };

 public:
  void enqueue(LogFileStreamOutput& output, const LogDecorations& decorations, const char* msg);
  void enqueue(LogFileStreamOutput& output, LogMessageBuffer::Iterator msg_iterator);

  static AsyncLogWriter* instance();
  static void initialize();
  static void flush();
};

#endif // SHARE_LOGGING_LOGASYNCWRITER_HPP
