/*
 * Copyright (c) 2005, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "logging/log.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/os.inline.hpp"
#include "services/attachListener.hpp"
#include "services/dtraceAttacher.hpp"
#include "utilities/vmError.hpp"

#include <door.h>
#include <limits.h>
#include <string.h>
#include <signal.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <sys/stat.h>

// stropts.h uses STR in stream ioctl defines
#undef STR
#include <stropts.h>
#undef STR
#define STR(a) #a

// The attach mechanism on Solaris is implemented using the Doors IPC
// mechanism. The first tool to attempt to attach causes the attach
// listener thread to startup. This thread creats a door that is
// associated with a function that enqueues an operation to the attach
// listener. The door is attached to a file in the file system so that
// client (tools) can locate it. To enqueue an operation to the VM the
// client calls through the door which invokes the enqueue function in
// this process. The credentials of the client are checked and if the
// effective uid matches this process then the operation is enqueued.
// When an operation completes the attach listener is required to send the
// operation result and any result data to the client. In this implementation
// the result is returned via a UNIX domain socket. A pair of connected
// sockets (socketpair) is created in the enqueue function and the file
// descriptor for one of the sockets is returned to the client as the
// return from the door call. The other end is retained in this process.
// When the operation completes the result is sent to the client and
// the socket is closed.

// forward reference
class SolarisAttachOperation;

class SolarisAttachListener: AllStatic {
 private:

  // the path to which we attach the door file descriptor
  static char _door_path[PATH_MAX+1];
  static volatile bool _has_door_path;

  // door descriptor returned by door_create
  static int _door_descriptor;

  static void set_door_path(char* path) {
    if (path == NULL) {
      _has_door_path = false;
    } else {
      strncpy(_door_path, path, PATH_MAX);
      _door_path[PATH_MAX] = '\0';      // ensure it's nul terminated
      _has_door_path = true;
    }
  }

  static void set_door_descriptor(int dd)               { _door_descriptor = dd; }

  // mutex to protect operation list
  static mutex_t _mutex;

  // semaphore to wakeup listener thread
  static sema_t _wakeup;

  static mutex_t* mutex()                               { return &_mutex; }
  static sema_t* wakeup()                               { return &_wakeup; }

  // enqueued operation list
  static SolarisAttachOperation* _head;
  static SolarisAttachOperation* _tail;

  static SolarisAttachOperation* head()                 { return _head; }
  static void set_head(SolarisAttachOperation* head)    { _head = head; }

  static SolarisAttachOperation* tail()                 { return _tail; }
  static void set_tail(SolarisAttachOperation* tail)    { _tail = tail; }

  // create the door
  static int create_door();

 public:
  enum {
    ATTACH_PROTOCOL_VER = 1                             // protocol version
  };
  enum {
    ATTACH_ERROR_BADREQUEST     = 100,                  // error code returned by
    ATTACH_ERROR_BADVERSION     = 101,                  // the door call
    ATTACH_ERROR_RESOURCE       = 102,
    ATTACH_ERROR_INTERNAL       = 103,
    ATTACH_ERROR_DENIED         = 104
  };

  // initialize the listener
  static int init();

  static bool has_door_path()                           { return _has_door_path; }
  static char* door_path()                              { return _door_path; }
  static int door_descriptor()                          { return _door_descriptor; }

  // enqueue an operation
  static void enqueue(SolarisAttachOperation* op);

  // dequeue an operation
  static SolarisAttachOperation* dequeue();
};


// SolarisAttachOperation is an AttachOperation that additionally encapsulates
// a socket connection to the requesting client/tool. SolarisAttachOperation
// can additionally be held in a linked list.

class SolarisAttachOperation: public AttachOperation {
 private:
  friend class SolarisAttachListener;

  // connection to client
  int _socket;

  // linked list support
  SolarisAttachOperation* _next;

  SolarisAttachOperation* next()                         { return _next; }
  void set_next(SolarisAttachOperation* next)            { _next = next; }

 public:
  void complete(jint res, bufferedStream* st);

  int socket() const                                     { return _socket; }
  void set_socket(int s)                                 { _socket = s; }

  SolarisAttachOperation(char* name) : AttachOperation(name) {
    set_socket(-1);
    set_next(NULL);
  }
};

// statics
char SolarisAttachListener::_door_path[PATH_MAX+1];
volatile bool SolarisAttachListener::_has_door_path;
int SolarisAttachListener::_door_descriptor = -1;
mutex_t SolarisAttachListener::_mutex;
sema_t SolarisAttachListener::_wakeup;
SolarisAttachOperation* SolarisAttachListener::_head = NULL;
SolarisAttachOperation* SolarisAttachListener::_tail = NULL;

// Supporting class to help split a buffer into individual components
class ArgumentIterator : public StackObj {
 private:
  char* _pos;
  char* _end;
 public:
  ArgumentIterator(char* arg_buffer, size_t arg_size) {
    _pos = arg_buffer;
    _end = _pos + arg_size - 1;
  }
  char* next() {
    if (*_pos == '\0') {
      // advance the iterator if possible (null arguments)
      if (_pos < _end) {
        _pos += 1;
      }
      return NULL;
    }
    char* res = _pos;
    char* next_pos = strchr(_pos, '\0');
    if (next_pos < _end)  {
      next_pos++;
    }
    _pos = next_pos;
    return res;
  }
};

// Calls from the door function to check that the client credentials
// match this process. Returns 0 if credentials okay, otherwise -1.
static int check_credentials() {
  ucred_t *cred_info = NULL;
  int ret = -1; // deny by default

  // get client credentials
  if (door_ucred(&cred_info) == -1) {
    return -1; // unable to get them, deny
  }

  // get euid/egid from ucred_free
  uid_t ucred_euid = ucred_geteuid(cred_info);
  gid_t ucred_egid = ucred_getegid(cred_info);

  // check that the effective uid/gid matches
  if (os::Posix::matches_effective_uid_and_gid_or_root(ucred_euid, ucred_egid)) {
    ret =  0;  // allow
  }

  ucred_free(cred_info);
  return ret;
}


// Parses the argument buffer to create an AttachOperation that we should
// enqueue to the attach listener.
// The buffer is expected to be formatted as follows:
// <ver>0<cmd>0<arg>0<arg>0<arg>0
// where <ver> is the version number (must be "1"), <cmd> is the command
// name ("load, "datadump", ...) and <arg> is an argument.
//
static SolarisAttachOperation* create_operation(char* argp, size_t arg_size, int* err) {
  // assume bad request until parsed
  *err = SolarisAttachListener::ATTACH_ERROR_BADREQUEST;

  if (arg_size < 2 || argp[arg_size-1] != '\0') {
    return NULL;   // no ver or not null terminated
  }

  // Use supporting class to iterate over the buffer
  ArgumentIterator args(argp, arg_size);

  // First check the protocol version
  char* ver = args.next();
  if (ver == NULL) {
    return NULL;
  }
  if (atoi(ver) != SolarisAttachListener::ATTACH_PROTOCOL_VER) {
    *err = SolarisAttachListener::ATTACH_ERROR_BADVERSION;
    return NULL;
  }

  // Get command name and create the operation
  char* name = args.next();
  if (name == NULL || strlen(name) > AttachOperation::name_length_max) {
    return NULL;
  }
  SolarisAttachOperation* op = new SolarisAttachOperation(name);

  // Iterate over the arguments
  for (int i=0; i<AttachOperation::arg_count_max; i++) {
    char* arg = args.next();
    if (arg == NULL) {
      op->set_arg(i, NULL);
    } else {
      if (strlen(arg) > AttachOperation::arg_length_max) {
        delete op;
        return NULL;
      }
      op->set_arg(i, arg);
    }
  }

  // return operation
  *err = 0;
  return op;
}

// create special operation to indicate all clients have detached
static SolarisAttachOperation* create_detachall_operation() {
  return new SolarisAttachOperation(AttachOperation::detachall_operation_name());
}

// This is door function which the client executes via a door_call.
extern "C" {
  static void enqueue_proc(void* cookie, char* argp, size_t arg_size,
                           door_desc_t* dt, uint_t n_desc)
  {
    int return_fd = -1;
    SolarisAttachOperation* op = NULL;

    // no listener
    jint res = 0;
    if (!AttachListener::is_initialized()) {
      // how did we get here?
      debug_only(warning("door_call when not enabled"));
      res = (jint)SolarisAttachListener::ATTACH_ERROR_INTERNAL;
    }

    // check client credentials
    if (res == 0) {
      if (check_credentials() != 0) {
        res = (jint)SolarisAttachListener::ATTACH_ERROR_DENIED;
      }
    }

    // if we are stopped at ShowMessageBoxOnError then maybe we can
    // load a diagnostic library
    if (res == 0 && VMError::is_error_reported()) {
      if (ShowMessageBoxOnError) {
        // TBD - support loading of diagnostic library here
      }

      // can't enqueue operation after fatal error
      res = (jint)SolarisAttachListener::ATTACH_ERROR_RESOURCE;
    }

    // create the operation
    if (res == 0) {
      int err;
      op = create_operation(argp, arg_size, &err);
      res = (op == NULL) ? (jint)err : 0;
    }

    // create a pair of connected sockets. Store the file descriptor
    // for one end in the operation and enqueue the operation. The
    // file descriptor for the other end wil be returned to the client.
    if (res == 0) {
      int s[2];
      if (socketpair(PF_UNIX, SOCK_STREAM, 0, s) < 0) {
        delete op;
        res = (jint)SolarisAttachListener::ATTACH_ERROR_RESOURCE;
      } else {
        op->set_socket(s[0]);
        return_fd = s[1];
        SolarisAttachListener::enqueue(op);
      }
    }

    // Return 0 (success) + file descriptor, or non-0 (error)
    if (res == 0) {
      door_desc_t desc;
      // DOOR_RELEASE flag makes sure fd is closed after passing it to
      // the client.  See door_return(3DOOR) man page.
      desc.d_attributes = DOOR_DESCRIPTOR | DOOR_RELEASE;
      desc.d_data.d_desc.d_descriptor = return_fd;
      door_return((char*)&res, sizeof(res), &desc, 1);
    } else {
      door_return((char*)&res, sizeof(res), NULL, 0);
    }
  }
}

// atexit hook to detach the door and remove the file
extern "C" {
  static void listener_cleanup() {
    static int cleanup_done;
    if (!cleanup_done) {
      cleanup_done = 1;
      int dd = SolarisAttachListener::door_descriptor();
      if (dd >= 0) {
        ::close(dd);
      }
      if (SolarisAttachListener::has_door_path()) {
        char* path = SolarisAttachListener::door_path();
        ::fdetach(path);
        ::unlink(path);
      }
    }
  }
}

// Create the door
int SolarisAttachListener::create_door() {
  char door_path[PATH_MAX+1];
  char initial_path[PATH_MAX+1];
  int fd, res;

  // register exit function
  ::atexit(listener_cleanup);

  // create the door descriptor
  int dd = ::door_create(enqueue_proc, NULL, 0);
  if (dd < 0) {
    return -1;
  }

  // create initial file to attach door descriptor
  snprintf(door_path, sizeof(door_path), "%s/.java_pid%d",
           os::get_temp_directory(), os::current_process_id());
  snprintf(initial_path, sizeof(initial_path), "%s.tmp", door_path);
  RESTARTABLE(::creat(initial_path, S_IRUSR | S_IWUSR), fd);
  if (fd == -1) {
    log_debug(attach)("attempt to create door file %s failed (%d)", initial_path, errno);
    ::door_revoke(dd);
    return -1;
  }
  assert(fd >= 0, "bad file descriptor");
  ::close(fd);

  // attach the door descriptor to the file
  if ((res = ::fattach(dd, initial_path)) == -1) {
    // if busy then detach and try again
    if (errno == EBUSY) {
      ::fdetach(initial_path);
      res = ::fattach(dd, initial_path);
    }
    if (res == -1) {
      log_debug(attach)("unable to create door - fattach failed (%d)", errno);
      ::door_revoke(dd);
      dd = -1;
    }
  }

  // rename file so that clients can attach
  if (dd >= 0) {
    if (::rename(initial_path, door_path) == -1) {
        ::close(dd);
        ::fdetach(initial_path);
        log_debug(attach)("unable to create door - rename %s to %s failed (%d)", errno);
        dd = -1;
    }
  }
  if (dd >= 0) {
    set_door_descriptor(dd);
    set_door_path(door_path);
    log_trace(attach)("door file %s created succesfully", door_path);
  } else {
    // unable to create door, attach it to file, or rename file into place
    ::unlink(initial_path);
    return -1;
  }

  return 0;
}

// Initialization - create the door, locks, and other initialization
int SolarisAttachListener::init() {
  if (create_door()) {
    return -1;
  }

  int status = os::Solaris::mutex_init(&_mutex);
  assert_status(status==0, status, "mutex_init");

  status = ::sema_init(&_wakeup, 0, NULL, NULL);
  assert_status(status==0, status, "sema_init");

  set_head(NULL);
  set_tail(NULL);

  return 0;
}

// Dequeue an operation
SolarisAttachOperation* SolarisAttachListener::dequeue() {
  for (;;) {
    int res;

    // wait for somebody to enqueue something
    while ((res = ::sema_wait(wakeup())) == EINTR)
      ;
    if (res) {
      warning("sema_wait failed: %s", os::strerror(res));
      return NULL;
    }

    // lock the list
    res = os::Solaris::mutex_lock(mutex());
    assert(res == 0, "mutex_lock failed");

    // remove the head of the list
    SolarisAttachOperation* op = head();
    if (op != NULL) {
      set_head(op->next());
      if (head() == NULL) {
        set_tail(NULL);
      }
    }

    // unlock
    os::Solaris::mutex_unlock(mutex());

    // if we got an operation when return it.
    if (op != NULL) {
      return op;
    }
  }
}

// Enqueue an operation
void SolarisAttachListener::enqueue(SolarisAttachOperation* op) {
  // lock list
  int res = os::Solaris::mutex_lock(mutex());
  assert(res == 0, "mutex_lock failed");

  // enqueue at tail
  op->set_next(NULL);
  if (head() == NULL) {
    set_head(op);
  } else {
    tail()->set_next(op);
  }
  set_tail(op);

  // wakeup the attach listener
  RESTARTABLE(::sema_post(wakeup()), res);
  assert(res == 0, "sema_post failed");

  // unlock
  os::Solaris::mutex_unlock(mutex());
}


// support function - writes the (entire) buffer to a socket
static int write_fully(int s, char* buf, int len) {
  do {
    int n = ::write(s, buf, len);
    if (n == -1) {
      if (errno != EINTR) return -1;
    } else {
      buf += n;
      len -= n;
    }
  }
  while (len > 0);
  return 0;
}

// Complete an operation by sending the operation result and any result
// output to the client. At this time the socket is in blocking mode so
// potentially we can block if there is a lot of data and the client is
// non-responsive. For most operations this is a non-issue because the
// default send buffer is sufficient to buffer everything. In the future
// if there are operations that involves a very big reply then it the
// socket could be made non-blocking and a timeout could be used.

void SolarisAttachOperation::complete(jint res, bufferedStream* st) {
  if (this->socket() >= 0) {
    JavaThread* thread = JavaThread::current();
    ThreadBlockInVM tbivm(thread);

    thread->set_suspend_equivalent();
    // cleared by handle_special_suspend_equivalent_condition() or
    // java_suspend_self() via check_and_wait_while_suspended()

    // write operation result
    char msg[32];
    sprintf(msg, "%d\n", res);
    int rc = write_fully(this->socket(), msg, strlen(msg));

    // write any result data
    if (rc == 0) {
      write_fully(this->socket(), (char*) st->base(), st->size());
      ::shutdown(this->socket(), 2);
    }

    // close socket and we're done
    ::close(this->socket());

    // were we externally suspended while we were waiting?
    thread->check_and_wait_while_suspended();
  }
  delete this;
}


// AttachListener functions

AttachOperation* AttachListener::dequeue() {
  JavaThread* thread = JavaThread::current();
  ThreadBlockInVM tbivm(thread);

  thread->set_suspend_equivalent();
  // cleared by handle_special_suspend_equivalent_condition() or
  // java_suspend_self() via check_and_wait_while_suspended()

  AttachOperation* op = SolarisAttachListener::dequeue();

  // were we externally suspended while we were waiting?
  thread->check_and_wait_while_suspended();

  return op;
}


// Performs initialization at vm startup
// For Solaris we remove any stale .java_pid file which could cause
// an attaching process to think we are ready to receive a door_call
// before we are properly initialized

void AttachListener::vm_start() {
  char fn[PATH_MAX+1];
  struct stat64 st;
  int ret;

  int n = snprintf(fn, sizeof(fn), "%s/.java_pid%d",
           os::get_temp_directory(), os::current_process_id());
  assert(n < sizeof(fn), "java_pid file name buffer overflow");

  RESTARTABLE(::stat64(fn, &st), ret);
  if (ret == 0) {
    ret = ::unlink(fn);
    if (ret == -1) {
      log_debug(attach)("Failed to remove stale attach pid file at %s", fn);
    }
  }
}

int AttachListener::pd_init() {
  JavaThread* thread = JavaThread::current();
  ThreadBlockInVM tbivm(thread);

  thread->set_suspend_equivalent();
  // cleared by handle_special_suspend_equivalent_condition() or
  // java_suspend_self()

  int ret_code = SolarisAttachListener::init();

  // were we externally suspended while we were waiting?
  thread->check_and_wait_while_suspended();

  return ret_code;
}

// Attach Listener is started lazily except in the case when
// +ReduseSignalUsage is used
bool AttachListener::init_at_startup() {
  if (ReduceSignalUsage) {
    return true;
  } else {
    return false;
  }
}

// If the file .attach_pid<pid> exists in the working directory
// or /tmp then this is the trigger to start the attach mechanism
bool AttachListener::is_init_trigger() {
  if (init_at_startup() || is_initialized()) {
    return false;               // initialized at startup or already initialized
  }
  char fn[PATH_MAX + 1];
  int ret;
  struct stat64 st;
  sprintf(fn, ".attach_pid%d", os::current_process_id());
  RESTARTABLE(::stat64(fn, &st), ret);
  if (ret == -1) {
    log_trace(attach)("Failed to find attach file: %s, trying alternate", fn);
    snprintf(fn, sizeof(fn), "%s/.attach_pid%d",
             os::get_temp_directory(), os::current_process_id());
    RESTARTABLE(::stat64(fn, &st), ret);
    if (ret == -1) {
      log_debug(attach)("Failed to find attach file: %s", fn);
    }
  }
  if (ret == 0) {
    // simple check to avoid starting the attach mechanism when
    // a bogus non-root user creates the file
    if (os::Posix::matches_effective_uid_or_root(st.st_uid)) {
      init();
      log_trace(attach)("Attach triggered by %s", fn);
      return true;
    } else {
      log_debug(attach)("File %s has wrong user id %d (vs %d). Attach is not triggered", fn, st.st_uid, geteuid());
    }
  }
  return false;
}

// if VM aborts then detach/cleanup
void AttachListener::abort() {
  listener_cleanup();
}

void AttachListener::pd_data_dump() {
  os::signal_notify(SIGQUIT);
}

static jint enable_dprobes(AttachOperation* op, outputStream* out) {
  const char* probe = op->arg(0);
  if (probe == NULL || probe[0] == '\0') {
    out->print_cr("No probe specified");
    return JNI_ERR;
  } else {
    char *end;
    long val = strtol(probe, &end, 10);
    if (end == probe || val < 0 || val > INT_MAX) {
      out->print_cr("invalid probe type");
      return JNI_ERR;
    } else {
      int probe_typess = (int) val;
      DTrace::enable_dprobes(probe_typess);
      return JNI_OK;
    }
  }
}

// platform specific operations table
static AttachOperationFunctionInfo funcs[] = {
  { "enabledprobes", enable_dprobes },
  { NULL, NULL }
};

AttachOperationFunctionInfo* AttachListener::pd_find_operation(const char* name) {
  int i;
  for (i = 0; funcs[i].name != NULL; i++) {
    if (strcmp(funcs[i].name, name) == 0) {
      return &funcs[i];
    }
  }
  return NULL;
}

// Solaris specific global flag set. Currently, we support only
// changing ExtendedDTraceProbes flag.
jint AttachListener::pd_set_flag(AttachOperation* op, outputStream* out) {
  const char* name = op->arg(0);
  assert(name != NULL, "flag name should not be null");
  bool flag = true;
  const char* arg1;
  if ((arg1 = op->arg(1)) != NULL) {
    char *end;
    flag = (strtol(arg1, &end, 10) != 0);
    if (arg1 == end) {
      out->print_cr("flag value has to be an integer");
      return JNI_ERR;
    }
  }

  if (strcmp(name, "ExtendedDTraceProbes") == 0) {
    DTrace::set_extended_dprobes(flag);
    return JNI_OK;
  }

  if (strcmp(name, "DTraceMonitorProbes") == 0) {
    DTrace::set_monitor_dprobes(flag);
    return JNI_OK;
  }

  out->print_cr("flag '%s' cannot be changed", name);
  return JNI_ERR;
}

void AttachListener::pd_detachall() {
  DTrace::detach_all_clients();
}
