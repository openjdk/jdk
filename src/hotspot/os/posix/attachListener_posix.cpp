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

#include "logging/log.hpp"
#include "memory/allocation.inline.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/os.inline.hpp"
#include "os_posix.hpp"
#include "services/attachListener.hpp"
#include "utilities/checkedCast.hpp"
#include "utilities/macros.hpp"

#include <unistd.h>
#include <signal.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <sys/stat.h>

#if INCLUDE_SERVICES
#ifndef AIX

#ifndef UNIX_PATH_MAX
#define UNIX_PATH_MAX   sizeof(sockaddr_un::sun_path)
#endif

// The attach mechanism on Linux and BSD uses a UNIX domain socket. An attach
// listener thread is created at startup or is created on-demand via a signal
// from the client tool. The attach listener creates a socket and binds it to a
// file in the filesystem. The attach listener then acts as a simple (single-
// threaded) server - it waits for a client to connect, reads the request,
// executes it, and returns the response to the client via the socket
// connection.
//
// As the socket is a UNIX domain socket it means that only clients on the
// local machine can connect. In addition there are two other aspects to
// the security:
// 1. The well known file that the socket is bound to has permission 400
// 2. When a client connect, the SO_PEERCRED socket option is used to
//    obtain the credentials of client. We check that the effective uid
//    of the client matches this process.

// forward reference
class PosixAttachOperation;

class PosixAttachListener: AllStatic {
 private:
  // the path to which we bind the UNIX domain socket
  static char _path[UNIX_PATH_MAX];
  static bool _has_path;

  // the file descriptor for the listening socket
  static volatile int _listener;

  static bool _atexit_registered;

 public:
  static void set_path(char* path) {
    if (path == nullptr) {
      _path[0] = '\0';
      _has_path = false;
    } else {
      strncpy(_path, path, UNIX_PATH_MAX);
      _path[UNIX_PATH_MAX-1] = '\0';
      _has_path = true;
    }
  }

  static void set_listener(int s)               { _listener = s; }

  // initialize the listener, returns 0 if okay
  static int init();

  static char* path()                   { return _path; }
  static bool has_path()                { return _has_path; }
  static int listener()                 { return _listener; }

  static PosixAttachOperation* dequeue();
};

class SocketChannel : public AttachOperation::RequestReader, public AttachOperation::ReplyWriter {
private:
  int _socket;
public:
  SocketChannel(int socket) : _socket(socket) {}
  ~SocketChannel() {
    close();
  }

  bool opened() const {
    return _socket != -1;
  }

  void close() {
    if (opened()) {
      ::close(_socket);
      _socket = -1;
    }
  }

  // RequestReader
  int read(void* buffer, int size) override {
    ssize_t n;
    RESTARTABLE(::read(_socket, buffer, (size_t)size), n);
    return checked_cast<int>(n);
  }

  // ReplyWriter
  int write(const void* buffer, int size) override {
    ssize_t n;
    RESTARTABLE(::write(_socket, buffer, size), n);
    return checked_cast<int>(n);
  }
  // called after writing all data
  void flush() override {
    ::shutdown(_socket, SHUT_RDWR);
  }
};

class PosixAttachOperation: public AttachOperation {
 private:
  // the connection to the client
  SocketChannel _socket_channel;

 public:
  void complete(jint res, bufferedStream* st) override;

  PosixAttachOperation(int socket) : AttachOperation(), _socket_channel(socket) {
  }

  bool read_request() {
    return AttachOperation::read_request(&_socket_channel, &_socket_channel);
  }
};

// statics
char PosixAttachListener::_path[UNIX_PATH_MAX];
bool PosixAttachListener::_has_path;
volatile int PosixAttachListener::_listener = -1;
bool PosixAttachListener::_atexit_registered = false;

// atexit hook to stop listener and unlink the file that it is
// bound too.
extern "C" {
  static void listener_cleanup() {
    int s = PosixAttachListener::listener();
    if (s != -1) {
      PosixAttachListener::set_listener(-1);
      ::shutdown(s, SHUT_RDWR);
      ::close(s);
    }
    if (PosixAttachListener::has_path()) {
      ::unlink(PosixAttachListener::path());
      PosixAttachListener::set_path(nullptr);
    }
  }
}

// Initialization - create a listener socket and bind it to a file

int PosixAttachListener::init() {
  char path[UNIX_PATH_MAX];          // socket file
  char initial_path[UNIX_PATH_MAX];  // socket file during setup
  int listener;                      // listener socket (file descriptor)

  static_assert(sizeof(off_t) == 8, "Expected Large File Support in this file");

  // register function to cleanup
  if (!_atexit_registered) {
    _atexit_registered = true;
    ::atexit(listener_cleanup);
  }

  int n = snprintf(path, UNIX_PATH_MAX, "%s/.java_pid%d",
                   os::get_temp_directory(), os::current_process_id());
  if (n < (int)UNIX_PATH_MAX) {
    n = snprintf(initial_path, UNIX_PATH_MAX, "%s.tmp", path);
  }
  if (n >= (int)UNIX_PATH_MAX) {
    return -1;
  }

  // create the listener socket
  listener = ::socket(PF_UNIX, SOCK_STREAM, 0);
  if (listener == -1) {
    return -1;
  }

  // bind socket
  struct sockaddr_un addr;
  memset((void *)&addr, 0, sizeof(addr));
  addr.sun_family = AF_UNIX;
  strcpy(addr.sun_path, initial_path);
  ::unlink(initial_path);
  int res = ::bind(listener, (struct sockaddr*)&addr, sizeof(addr));
  if (res == -1) {
    ::close(listener);
    return -1;
  }

  // put in listen mode, set permissions, and rename into place
  res = ::listen(listener, 5);
  if (res == 0) {
    RESTARTABLE(::chmod(initial_path, S_IREAD|S_IWRITE), res);
    if (res == 0) {
      // make sure the file is owned by the effective user and effective group
      // e.g. the group could be inherited from the directory in case the s bit
      // is set. The default behavior on mac is that new files inherit the group
      // of the directory that they are created in.
      RESTARTABLE(::chown(initial_path, geteuid(), getegid()), res);
      if (res == 0) {
        res = ::rename(initial_path, path);
      }
    }
  }
  if (res == -1) {
    ::close(listener);
    ::unlink(initial_path);
    return -1;
  }
  set_path(path);
  set_listener(listener);

  return 0;
}

// Dequeue an operation
//
// In the Linux and BSD implementations, there is only a single operation and
// clients cannot queue commands (except at the socket level).
//
PosixAttachOperation* PosixAttachListener::dequeue() {
  for (;;) {
    int s;

    // wait for client to connect
    struct sockaddr addr;
    socklen_t len = sizeof(addr);
    RESTARTABLE(::accept(listener(), &addr, &len), s);
    if (s == -1) {
      return nullptr;      // log a warning?
    }

    // get the credentials of the peer and check the effective uid/guid
#ifdef LINUX
    struct ucred cred_info;
    socklen_t optlen = sizeof(cred_info);
    if (::getsockopt(s, SOL_SOCKET, SO_PEERCRED, (void *)&cred_info, &optlen) ==
        -1) {
      log_debug(attach)("Failed to get socket option SO_PEERCRED");
      ::close(s);
      continue;
    }

    if (!os::Posix::matches_effective_uid_and_gid_or_root(cred_info.uid,
                                                          cred_info.gid)) {
      log_debug(attach)("euid/egid check failed (%d/%d vs %d/%d)",
                        cred_info.uid, cred_info.gid, geteuid(), getegid());
      ::close(s);
      continue;
    }
#endif
#ifdef BSD
    uid_t puid;
    gid_t pgid;
    if (::getpeereid(s, &puid, &pgid) != 0) {
      log_debug(attach)("Failed to get peer id");
      ::close(s);
      continue;
    }

    if (!os::Posix::matches_effective_uid_and_gid_or_root(puid, pgid)) {
      log_debug(attach)("euid/egid check failed (%d/%d vs %d/%d)", puid, pgid,
                        geteuid(), getegid());
      ::close(s);
      continue;
    }
#endif

    // peer credential look okay so we read the request
    PosixAttachOperation* op = new PosixAttachOperation(s);
    if (!op->read_request()) {
      delete op;
      continue;
    } else {
      return op;
    }
  }
}

// Complete an operation by sending the operation result and any result
// output to the client. At this time the socket is in blocking mode so
// potentially we can block if there is a lot of data and the client is
// non-responsive. For most operations this is a non-issue because the
// default send buffer is sufficient to buffer everything. In the future
// if there are operations that involves a very big reply then it the
// socket could be made non-blocking and a timeout could be used.

void PosixAttachOperation::complete(jint result, bufferedStream* st) {
  JavaThread* thread = JavaThread::current();
  ThreadBlockInVM tbivm(thread);

  write_reply(&_socket_channel, result, st);

  delete this;
}


// AttachListener functions

AttachOperation* AttachListener::dequeue() {
  JavaThread* thread = JavaThread::current();
  ThreadBlockInVM tbivm(thread);

  AttachOperation* op = PosixAttachListener::dequeue();

  return op;
}

// Performs initialization at vm startup
// For Linux and BSD we remove any stale .java_pid file which could cause
// an attaching process to think we are ready to receive on the
// domain socket before we are properly initialized

void AttachListener::vm_start() {
  char fn[UNIX_PATH_MAX];
  struct stat st;
  int ret;

  int n = snprintf(fn, UNIX_PATH_MAX, "%s/.java_pid%d",
           os::get_temp_directory(), os::current_process_id());
  assert(n < (int)UNIX_PATH_MAX, "java_pid file name buffer overflow");

  RESTARTABLE(::stat(fn, &st), ret);
  if (ret == 0) {
    ret = ::unlink(fn);
    if (ret == -1) {
      log_debug(attach)("Failed to remove stale attach pid file at %s", fn);
    }
  }
}

int AttachListener::pd_init() {
  AttachListener::set_supported_version(ATTACH_API_V2);

  JavaThread* thread = JavaThread::current();
  ThreadBlockInVM tbivm(thread);

  int ret_code = PosixAttachListener::init();

  return ret_code;
}

bool AttachListener::check_socket_file() {
  int ret;
  struct stat st;
  ret = stat(PosixAttachListener::path(), &st);
  if (ret == -1) { // need to restart attach listener.
    log_debug(attach)("Socket file %s does not exist - Restart Attach Listener",
                      PosixAttachListener::path());

    listener_cleanup();

    // wait to terminate current attach listener instance...
    {
      // avoid deadlock if AttachListener thread is blocked at safepoint
      ThreadBlockInVM tbivm(JavaThread::current());
      while (AttachListener::transit_state(AL_INITIALIZING,
                                           AL_NOT_INITIALIZED) != AL_NOT_INITIALIZED) {
        os::naked_yield();
      }
    }
    return is_init_trigger();
  }
  return false;
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
  struct stat st;
  os::snprintf_checked(fn, sizeof(fn), ".attach_pid%d",
                       os::current_process_id());
  RESTARTABLE(::stat(fn, &st), ret);
  if (ret == -1) {
    log_trace(attach)("Failed to find attach file: %s, trying alternate", fn);
    snprintf(fn, sizeof(fn), "%s/.attach_pid%d", os::get_temp_directory(),
             os::current_process_id());
    RESTARTABLE(::stat(fn, &st), ret);
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

// if VM aborts then remove listener
void AttachListener::abort() {
  listener_cleanup();
}

void AttachListener::pd_data_dump() {
  os::signal_notify(SIGQUIT);
}

void AttachListener::pd_detachall() {
  // do nothing for now
}

#endif // !AIX

#endif // INCLUDE_SERVICES
