/*
 * Copyright (c) 2000, 2003, Oracle and/or its affiliates. All rights reserved.
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

#ifndef _SERVER_LISTS_
#define _SERVER_LISTS_

#include <vector>
#include <winsock2.h>
#include "IOBuf.hpp"

//
// NOTE:
//
// All of these lists are guarded by the global lock managed by the
// Lists class. Lists::init() must be called at the start of the
// program.
//

class Lists {
  friend class ListsLocker;
public:
  static void init();
private:
  static void lock();
  static void unlock();
  static CRITICAL_SECTION crit;
};

// Should be allocated on stack. Ensures proper locking/unlocking
// pairing.
class ListsLocker {
public:
  ListsLocker();
  ~ListsLocker();
};

// We must keep track of all of the child processes we have forked to
// handle attaching to a target process. This is necessary because we
// allow clients to detach from processes, but the child processes we
// fork must necessarily stay alive for the duration of the target
// application. A subsequent attach operation to the target process
// results in the same child process being reused. For this reason,
// child processes are known to be in one of two states: attached and
// detached.

class ClientInfo;

class ChildInfo {
public:
  /** The pid of the ChildInfo indicates the process ID of the target
      process which the subprocess was created to debug, not the pid
      of the subprocess itself. */
  ChildInfo(DWORD pid, HANDLE childProcessHandle,
            HANDLE writeToStdinHandle, HANDLE readFromStdoutHandle,
            HANDLE auxHandle1, HANDLE auxHandle2);

  DWORD getPid();
  HANDLE getChildProcessHandle();
  HANDLE getWriteToStdinHandle();
  HANDLE getReadFromStdoutHandle();

  /** Set the client which is currently attached to the target process
      via this child process. Set this to NULL to indicate that the
      child process is ready to accept another attachment. */
  void setClient(ClientInfo* clientInfo);

  ClientInfo* getClient();

  /** This is NOT automatically called in the destructor */
  void closeAll();

private:
  DWORD pid;
  HANDLE childProcessHandle;
  HANDLE writeToStdinHandle;
  HANDLE readFromStdoutHandle;
  HANDLE auxHandle1;
  HANDLE auxHandle2;
  ClientInfo* client;
};

// We keep track of a list of child debugger processes, each of which
// is responsible for debugging a certain target process. These
// debugger processes can serve multiple clients during their
// lifetime. When a client detaches from a given process or tells the
// debugger to "exit", the debug server is notified that the child
// process is once again available to accept connections from clients.

class ChildList {
private:
  typedef std::vector<ChildInfo*> ChildInfoList;

public:
  ChildList();
  ~ChildList();

  void addChild(ChildInfo*);

  /** Removes and returns the ChildInfo* associated with the given
      child process handle. */
  ChildInfo* removeChild(HANDLE childProcessHandle);

  /** Removes the given ChildInfo. */
  void removeChild(ChildInfo* info);

  /** Return the ChildInfo* associated with a given process ID without
      removing it from the list. */
  ChildInfo* getChildByPid(DWORD pid);

  /** Iteration support */
  int size();

  /** Iteration support */
  ChildInfo* getChildByIndex(int index);

private:
  ChildInfoList childList;
};

// We also keep a list of clients whose requests we are responsible
// for serving. Clients can attach and detach from child processes.

class ClientInfo {
public:
  ClientInfo(SOCKET dataSocket);
  ~ClientInfo();

  SOCKET getDataSocket();
  /** Gets an IOBuf configured for the data socket, which should be
      used for all communication with the client. */
  IOBuf* getIOBuf();

  /** Set the information for the process to which this client is
      attached. Set this to NULL to indicate that the client is not
      currently attached to any target process. */
  void setTarget(ChildInfo* childInfo);

  /** Get the information for the process to which this client is
      currently attached, or NULL if none. */
  ChildInfo* getTarget();

  /** Close down the socket connection to this client. This is NOT
      automatically called by the destructor. */
  void closeAll();

private:
  SOCKET dataSocket;
  IOBuf* buf;
  ChildInfo* target;
};

class ClientList {
private:
  typedef std::vector<ClientInfo*> ClientInfoList;

public:
  ClientList();
  ~ClientList();

  /** Adds a client to the list. */
  void addClient(ClientInfo* info);

  /** Check to see whether the parent socket of any of the ClientInfo
      objects is readable in the given fd_set. If so, returns TRUE and
      sets the given ClientInfo* (a non-NULL pointer to which must be
      given) appropriately. */
  bool isAnyDataSocketSet(fd_set* fds, ClientInfo** info);

  /** Removes a client from the list. User is responsible for deleting
      the ClientInfo* using operator delete. */
  void removeClient(ClientInfo* client);

  /** Iteration support. */
  int size();

  /** Iteration support. */
  ClientInfo* get(int num);

private:
  ClientInfoList clientList;
};

#endif  // #defined _SERVER_LISTS_
