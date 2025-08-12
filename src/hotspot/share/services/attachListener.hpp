/*
 * Copyright (c) 2005, 2025, Oracle and/or its affiliates. All rights reserved.
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
jdk25 update: client may specify additional options in the attach operation request.
  The options are passed as part of the operation command name: "command option1,option2,option3".
  "getversion" command with "options" argument returns list of comma-separated options
  supported by the target VM.
  Option "streaming":
    - "streaming=1" turns on streaming output. Output data are sent as they become available.
    - "streaming=0" turns off streaming output. Output is buffered and sent after the operation is complete.
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

  static bool _default_streaming_output;

 public:
  static void set_supported_version(AttachAPIVersion version);
  static AttachAPIVersion get_supported_version();

  static void set_default_streaming(bool value) {
    _default_streaming_output = value;
  }
  static bool get_default_streaming() {
    return _default_streaming_output;
  }

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
  // error codes (reported as status to clients)
  enum {
    ATTACH_ERROR_BADVERSION = 101
  };

  // name of special operation that can be enqueued when all
  // clients detach
  static char* detachall_operation_name() { return (char*)"detachall"; }

private:
  char* _name;
  GrowableArrayCHeap<char*, mtServiceability> _args;
  bool _streaming; // streaming output is requested

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
      return "";
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

  bool streaming_output() const {
    return _streaming;
  }
  void set_streaming_output(bool value) {
    _streaming = value;
  }

  // create an v1 operation of a given name (for compatibility, deprecated)
  AttachOperation(const char* name) : _name(nullptr), _streaming(AttachListener::get_default_streaming()) {
    set_name(name);
    for (int i = 0; i < arg_count_max; i++) {
      set_arg(i, nullptr);
    }
  }

  AttachOperation() : _name(nullptr), _streaming(AttachListener::get_default_streaming()) {
  }

  virtual ~AttachOperation() {
    os::free(_name);
    for (GrowableArrayIterator<char*> it = _args.begin(); it != _args.end(); ++it) {
      os::free(*it);
    }
  }

  // complete operation by sending result code and any result data to the client
  virtual void complete(jint result, bufferedStream* result_stream) = 0;

  class ReplyWriter; // forward declaration

  // Helper classes/methods for platform-specific implementations.
  class RequestReader {
  public:
    // Returns number of bytes read,
    // 0 on EOF, negative value on error.
    virtual int read(void* buffer, int size) = 0;

    // Reads unsigned value, returns -1 on error.
    //
    // Attach client can make sanity connect/disconnect.
    // In that case we get "premature EOF" error.
    // If may_be_empty is true, the error is not logged.
    int read_uint(bool may_be_empty = false);


    // Reads standard operation request (v1 or v2), sets properties of the provided AttachOperation.
    // Some errors known by clients are reported to error_writer.
    bool read_request(AttachOperation* op, ReplyWriter* error_writer);

  private:
    bool read_request_data(AttachOperation* op, int buffer_size, int min_str_count, int min_read_size);
    // Parses options.
    // Note: the buffer is modified to zero-terminate option names and values.
    void parse_options(AttachOperation* op, char* str);
    // Gets option name and value.
    // Returns pointer to the next option.
    char* get_option(char* src, char** name, char** value);
  };


  class ReplyWriter {
  public:
    // Returns number of bytes written, negative value on error.
    virtual int write(const void* buffer, int size) = 0;

    virtual void flush() {}

    bool write_fully(const void* buffer, int size);

    // Writes standard operation reply.
    bool write_reply(jint result, const char* message, int message_len = -1);
    bool write_reply(jint result, bufferedStream* result_stream);
  };

  // Platform implementation needs to implement the method to support streaming output.
  virtual ReplyWriter* get_reply_writer() {
    return nullptr;
  }

};

#endif // INCLUDE_SERVICES

#endif // SHARE_SERVICES_ATTACHLISTENER_HPP
