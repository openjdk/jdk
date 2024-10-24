/*
 * Copyright (c) 2005, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_SERVICES_ATTACHLISTENER_HPP
#define SHARE_SERVICES_ATTACHLISTENER_HPP

#include "memory/allStatic.hpp"
#include "runtime/atomic.hpp"
#include "runtime/globals.hpp"
#include "runtime/javaThread.inline.hpp"
#include "utilities/debug.hpp"
#include "utilities/exceptions.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/macros.hpp"
#include "utilities/ostream.hpp"

// The AttachListener thread services a queue of operations that are enqueued
// by client tools. Each operation is identified by a name and has up to 3
// arguments. The operation name is mapped to a function which performs the
// operation. The function is called with an outputStream which is can use to
// write any result data (for examples the properties command serializes
// properties names and values to the output stream). When the function
// complets the result value and any result data is returned to the client
// tool.

class AttachOperation;

typedef jint (*AttachOperationFunction)(AttachOperation* op, outputStream* out);

struct AttachOperationFunctionInfo {
  const char* name;
  AttachOperationFunction func;
};

enum AttachListenerState {
  AL_NOT_INITIALIZED,
  AL_INITIALIZING,
  AL_INITIALIZED
};

/*
Version 1 (since jdk6): attach operations always have 3 (AttachOperation::arg_count_max)
  arguments, each up to 1024 (AttachOperation::arg_length_max) chars.
Version 2 (since jdk24): attach operations may have any number of arguments of any length;
  for safety default implementation restricts attach operation request size by 256KB.
  To detect if target VM supports version 2, client sends "getversion" command.
  Old VM reports "Operation not recognized" error, newer VM reports version supported by the implementation.
  If the target VM does not support version 2, client uses version 1 to enqueue operations.
*/
enum AttachAPIVersion: int {
    ATTACH_API_V1 = 1,
    ATTACH_API_V2 = 2
};

class AttachListenerThread : public JavaThread {
private:
  static void thread_entry(JavaThread* thread, TRAPS);

public:
  AttachListenerThread() : JavaThread(&AttachListenerThread::thread_entry) {}
  bool is_AttachListener_thread() const { return true; }
};

class AttachListener: AllStatic {
 public:
  static void vm_start() NOT_SERVICES_RETURN;
  static void init()  NOT_SERVICES_RETURN;
  static void abort() NOT_SERVICES_RETURN;

  // invoke to perform clean-up tasks when all clients detach
  static void detachall() NOT_SERVICES_RETURN;

  // check unix domain socket file on filesystem
  static bool check_socket_file() NOT_SERVICES_RETURN_(false);

  // indicates if the Attach Listener needs to be created at startup
  static bool init_at_startup() NOT_SERVICES_RETURN_(false);

  // indicates if we have a trigger to start the Attach Listener
  static bool is_init_trigger() NOT_SERVICES_RETURN_(false);

#if !INCLUDE_SERVICES
  static bool is_attach_supported()             { return false; }
#else

 private:
  static volatile AttachListenerState _state;

  static AttachAPIVersion _supported_version;

 public:
  static void set_supported_version(AttachAPIVersion version);
  static AttachAPIVersion get_supported_version();

  static void set_state(AttachListenerState new_state) {
    Atomic::store(&_state, new_state);
  }

  static AttachListenerState get_state() {
    return Atomic::load(&_state);
  }

  static AttachListenerState transit_state(AttachListenerState new_state,
                                           AttachListenerState cmp_state) {
    return Atomic::cmpxchg(&_state, cmp_state, new_state);
  }

  static bool is_initialized() {
    return Atomic::load(&_state) == AL_INITIALIZED;
  }

  static void set_initialized() {
    Atomic::store(&_state, AL_INITIALIZED);
  }

  // indicates if this VM supports attach-on-demand
  static bool is_attach_supported()             { return !DisableAttachMechanism; }

  // platform specific initialization
  static int pd_init();

  // platform specific detachall
  static void pd_detachall();

  // platform specific data dump
  static void pd_data_dump();

  // dequeue the next operation
  static AttachOperation* dequeue();
#endif // !INCLUDE_SERVICES

 private:
  static bool has_init_error(TRAPS);
};

#if INCLUDE_SERVICES
class AttachOperation: public CHeapObj<mtServiceability> {
public:
  // v1 constants
  enum {
    name_length_max = 16,       // maximum length of  name
    arg_length_max = 1024,      // maximum length of argument
    arg_count_max = 3           // maximum number of arguments
  };

  // name of special operation that can be enqueued when all
  // clients detach
  static char* detachall_operation_name() { return (char*)"detachall"; }

private:
  char* _name;
  GrowableArrayCHeap<char*, mtServiceability> _args;

  static char* copy_str(const char* value) {
    return value == nullptr ? nullptr : os::strdup(value, mtServiceability);
  }

public:
  const char* name() const { return _name; }

  // set the operation name
  void set_name(const char* name) {
    os::free(_name);
    _name = copy_str(name);
  }

  int arg_count() const {
    return _args.length();
  }

  // get an argument value
  const char* arg(int i) const {
    // Historically clients expect empty string for absent or null arguments.
    if (i >= _args.length() || _args.at(i) == nullptr) {
      static char empty_str[] = "";
      return empty_str;
    }
    return _args.at(i);
  }

  // appends an argument
  void append_arg(const char* arg) {
    _args.append(copy_str(arg));
  }

  // set an argument value
  void set_arg(int i, const char* arg) {
    _args.at_put_grow(i, copy_str(arg), nullptr);
  }

  // create an v1 operation of a given name (for compatibility, deprecated)
  AttachOperation(const char* name) : _name(nullptr) {
    set_name(name);
    for (int i = 0; i < arg_count_max; i++) {
      set_arg(i, nullptr);
    }
  }

  AttachOperation() : _name(nullptr) {
  }

  virtual ~AttachOperation() {
    os::free(_name);
    for (GrowableArrayIterator<char*> it = _args.begin(); it != _args.end(); ++it) {
      os::free(*it);
    }
  }

  // complete operation by sending result code and any result data to the client
  virtual void complete(jint result, bufferedStream* result_stream) = 0;

  // Helper classes/methods for platform-specific implementations.
  class RequestReader {
  public:
    // Returns number of bytes read,
    // 0 on EOF, negative value on error.
    virtual int read(void* buffer, int size) = 0;

    // Reads unsigned value, returns -1 on error.
    int read_uint();
  };

  // Reads standard operation request (v1 or v2).
  bool read_request(RequestReader* reader);

  class ReplyWriter {
  public:
    // Returns number of bytes written, negative value on error.
    virtual int write(const void* buffer, int size) = 0;

    virtual void flush() {}

    bool write_fully(const void* buffer, int size);
  };

  // Writes standard operation reply (to be called from 'complete' method).
  bool write_reply(ReplyWriter* writer, jint result, bufferedStream* result_stream);

private:
  bool read_request_data(AttachOperation::RequestReader* reader, int buffer_size, int min_str_count, int min_read_size);

};

#endif // INCLUDE_SERVICES

#endif // SHARE_SERVICES_ATTACHLISTENER_HPP
