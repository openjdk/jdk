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

// A Simple Windows Debug Server.
//
// This software provides a socket-based debug server which uses
// mostly ASCII protocols to communicate with its clients. Since the
// Windows security model is largely based around being able to run
// programs on the machine, this server only accepts connections
// coming from localhost.
//
// When run as a service (under Windows NT), this software provides
// clients the ability to attach to and detach from processes without
// killing those processes. Ordinarily this is forbidden by the
// Windows debugging APIs (although more recent debugging environments
// from Microsoft seem to have circumvented this restriction, perhaps
// in a different way). This is achieved by forking a persistent
// subprocess for each debugging session which remains alive as long
// as the target process is.
//
// At this point the client can read information out of the target
// process's address space. Future work includes exposing more
// functionality like writing to the remote address space and
// suspending and resuming threads.

#include <iostream>
#include <vector>
#include <stdlib.h>
// Must come before everything else
#include <winsock2.h>
#include <assert.h>
#include "Dispatcher.hpp"
#include "Handler.hpp"
#include "initWinsock.hpp"
#include "ioUtils.hpp"
#include "isNT4.hpp"
#include "Message.hpp"
#include "nt4internals.hpp"
#include "ports.h"
#include "procList.hpp"
#include "serverLists.hpp"
#include "Reaper.hpp"

// Uncomment the #define below to get messages on stderr
// #define DEBUGGING

using namespace std;

static ChildList childList;
static ClientList clientList;
static Reaper* reaper = NULL;

// Needed prototypes
void shutdownChild(ChildInfo* childInfo);
void detachClient(ClientInfo* clientInfo);
void shutdownClient(ClientInfo* clientInfo);

char *
longToDotFormat(long addr)
{
  char *temp_s = new char[20];

  sprintf(temp_s, "%d.%d.%d.%d", ((addr & 0xff000000) >> 24),
          ((addr & 0x00ff0000) >> 16), ((addr & 0x0000ff00) >> 8),
          (addr & 0x000000ff));

  return temp_s;
}

// NOTE that we do this query every time. It is a bad idea to cache IP
// addresses. For example, we might be hosted on a machine using DHCP
// and the connection addresses might change over time. (Yes, this
// actually happened.)
bool
isConnectionOkay(ULONG connAddr) {
  if (connAddr == INADDR_LOOPBACK) {
    return true;
  }

  const int MAXNAME = 1024;
  char myname[MAXNAME];
  gethostname(myname, MAXNAME);
  struct hostent* myInfo = gethostbyname(myname);
  if (myInfo == NULL) {
#ifdef DEBUGGING
    cerr << "My host information was null" << endl;
#endif
  } else {
    // Run down the list of IP addresses for myself
    assert(myInfo->h_length == sizeof(ULONG));
#ifdef DEBUGGING
    cerr << "My known IP addresses: " << endl;
#endif
    for (char** pp = myInfo->h_addr_list; *pp != NULL; pp++) {
      char* p = *pp;
      ULONG altAddr = ntohl(*((ULONG*) p));
#ifdef DEBUGGING
      char* name = longToDotFormat(altAddr);
      cerr << name << endl;
      delete[] name;
#endif
      if (altAddr == connAddr) {
#ifdef DEBUGGING
        cerr << "FOUND" << endl;
#endif
        return true;
      }
    }
#ifdef DEBUGGING
    cerr << "Done." << endl;
#endif
  }

  return false;
}

SOCKET
setupListeningSocket(short port) {
  SOCKET listening = socket(AF_INET, SOCK_STREAM, 0);
  if (listening == INVALID_SOCKET) {
    cerr << "Error creating listening socket" << endl;
    exit(1);
  }

  int reuseAddress = 1;
  if (setsockopt(listening, SOL_SOCKET, SO_REUSEADDR,
                 (char *)&reuseAddress, sizeof(reuseAddress)) == -1) {
    cerr << "Error reusing address" << endl;
    exit(1);
  }

  struct sockaddr_in serverInfo;

  memset((char *)&serverInfo, 0, sizeof(serverInfo));
  serverInfo.sin_addr.s_addr = INADDR_ANY;
  serverInfo.sin_family = AF_INET;
  serverInfo.sin_port = htons(port);

  if (bind(listening, (struct sockaddr *) &serverInfo, sizeof(serverInfo)) < 0) {
    cerr << "Error binding socket" << endl;
    exit(1);
  }

  if (listen(listening, 5) < 0) {
    cerr << "Error listening" << endl;
    exit(1);
  }

  return listening;
}

/** Accepts a connection from the given listening socket, but only if
    the connection came from localhost. Returns INVALID_SOCKET if the
    connection came from any other IP address or if an error occurred
    during the call to accept(). */
SOCKET
acceptFromLocalhost(SOCKET listening) {
  struct sockaddr_in peerAddr;
  int peerAddrLen = sizeof(peerAddr);
  SOCKET fd = accept(listening, (sockaddr*) &peerAddr, &peerAddrLen);
  if (fd == INVALID_SOCKET) {
    return fd;
  }

  if (!isConnectionOkay(ntohl(peerAddr.sin_addr.s_addr))) {
    // Reject connections from other machines for security purposes.
    // The Windows security model seems to assume one user per
    // machine, and that security is compromised if another user is
    // able to run executables on the given host. (If these
    // assumptions are not strict enough, we will have to change
    // this.)
    shutdown(fd, SD_BOTH);
    closesocket(fd);
    return INVALID_SOCKET;
  }

  // Disable TCP buffering on all sockets. We send small amounts of
  // data back and forth and don't want buffering.
  int buffer_val = 1;
  if (setsockopt(fd, IPPROTO_IP, TCP_NODELAY,
                 (char *) &buffer_val, sizeof(buffer_val)) < 0) {
    shutdown(fd, SD_BOTH);
    closesocket(fd);
  }

  return fd;
}

void
reapCB(void* arg) {
  ChildInfo* info = (ChildInfo*) arg;
  ListsLocker ll;
  DWORD pid = info->getPid();
  shutdownChild(info);
#ifdef DEBUGGING
  cerr << "Reaped child for process " << pid << endl;
#endif
}

/** Starts a child process with stdin and stdout redirected to pipes,
    handles to which are returned. auxHandle1 and auxHandle2 should be
    closed as well when the child process exits. Returns false if
    process creation failed. */
bool
startChildProcess(DWORD pidToDebug,
                  DWORD childStdinBufSize,
                  DWORD childStdoutBufSize,
                  LPHANDLE childProcessHandle,
                  LPHANDLE writeToStdinHandle,
                  LPHANDLE readFromStdoutHandle,
                  LPHANDLE auxHandle1,
                  LPHANDLE auxHandle2) {
  // Code adapted from Microsoft example
  // "Creating a Child Process with Redirected Input and Output"

  SECURITY_ATTRIBUTES saAttr;
  BOOL fSuccess;

  HANDLE hChildStdinRd, hChildStdinWr, hChildStdinWrDup,
    hChildStdoutRd, hChildStdoutWr, hChildStdoutRdDup,
    hSaveStdin, hSaveStdout;

  // Set the bInheritHandle flag so pipe handles are inherited.
  saAttr.nLength = sizeof(SECURITY_ATTRIBUTES);
  saAttr.bInheritHandle = TRUE;
  saAttr.lpSecurityDescriptor = NULL;

  // The steps for redirecting child process's STDOUT:
  //   1. Save current STDOUT, to be restored later.
  //   2. Create anonymous pipe to be STDOUT for child process.
  //   3. Set STDOUT of the parent process to be write handle to
  //      the pipe, so it is inherited by the child process.
  //   4. Create a noninheritable duplicate of the read handle and
  //      close the inheritable read handle.

  // Save the handle to the current STDOUT.
  hSaveStdout = GetStdHandle(STD_OUTPUT_HANDLE);
  // Create a pipe for the child process's STDOUT.
  if (! CreatePipe(&hChildStdoutRd, &hChildStdoutWr, &saAttr, childStdoutBufSize)) {
    return false;
  }
  // Set a write handle to the pipe to be STDOUT.
  if (! SetStdHandle(STD_OUTPUT_HANDLE, hChildStdoutWr)) {
    return false;
  }
  // Create noninheritable read handle and close the inheritable read
  // handle.
  fSuccess = DuplicateHandle(GetCurrentProcess(), hChildStdoutRd,
                             GetCurrentProcess(), &hChildStdoutRdDup,
                             0, FALSE,
                             DUPLICATE_SAME_ACCESS);
  if( !fSuccess ) {
    return false;
  }
  CloseHandle(hChildStdoutRd);

  // The steps for redirecting child process's STDIN:
  //   1.  Save current STDIN, to be restored later.
  //   2.  Create anonymous pipe to be STDIN for child process.
  //   3.  Set STDIN of the parent to be the read handle to the
  //       pipe, so it is inherited by the child process.
  //   4.  Create a noninheritable duplicate of the write handle,
  //       and close the inheritable write handle.
  // Save the handle to the current STDIN.
  hSaveStdin = GetStdHandle(STD_INPUT_HANDLE);
  // Create a pipe for the child process's STDIN.
  if (! CreatePipe(&hChildStdinRd, &hChildStdinWr, &saAttr, childStdinBufSize)) {
    return false;
  }
  // Set a read handle to the pipe to be STDIN.
  if (! SetStdHandle(STD_INPUT_HANDLE, hChildStdinRd)) {
    return false;
  }
  // Duplicate the write handle to the pipe so it is not inherited.
  fSuccess = DuplicateHandle(GetCurrentProcess(), hChildStdinWr,
                             GetCurrentProcess(), &hChildStdinWrDup, 0,
                             FALSE,                  // not inherited
                             DUPLICATE_SAME_ACCESS);
  if (! fSuccess) {
    return false;
  }
  CloseHandle(hChildStdinWr);

  // Create the child process
  char cmdLine[256];
  sprintf(cmdLine, "SwDbgSub.exe %u", pidToDebug);
  PROCESS_INFORMATION procInfo;
  STARTUPINFO startInfo;
  memset((char*) &startInfo, 0, sizeof(startInfo));
  startInfo.cb = sizeof(startInfo);
  BOOL res = CreateProcess(NULL,
                           cmdLine,
                           NULL,
                           NULL,
                           TRUE, // inherit handles: important
                           0,
                           NULL,
                           NULL,
                           &startInfo,
                           &procInfo);
  if (!res) {
    return false;
  }
  // After process creation, restore the saved STDIN and STDOUT.
  if (! SetStdHandle(STD_INPUT_HANDLE, hSaveStdin)) {
    return false;
  }
  if (! SetStdHandle(STD_OUTPUT_HANDLE, hSaveStdout)) {
    return false;
  }

  // hChildStdinWrDup can be used to write to the child's stdin
  // hChildStdoutRdDup can be used to read from the child's stdout

  // NOTE: example code closes hChildStdoutWr before reading from
  // hChildStdoutRdDup. "Close the write end of the pipe before
  // reading from the read end of the pipe"??? Looks like this is
  // example-specific.

  // Set up return arguments
  // hChildStdoutRd and hChildStdinWr are already closed at this point
  *childProcessHandle = procInfo.hProcess;
  *writeToStdinHandle = hChildStdinWrDup;
  *readFromStdoutHandle = hChildStdoutRdDup;
  *auxHandle1 = hChildStdinRd;
  *auxHandle2 = hChildStdoutWr;
  return true;
}

/** Clears the event and writes the message to the child process */
bool
sendMessage(ChildInfo* child, Message* message) {
  DWORD numBytesWritten;
  if (!WriteFile(child->getWriteToStdinHandle(),
                 message, sizeof(Message), &numBytesWritten, NULL)) {
    return false;
  }
  if (numBytesWritten != sizeof(Message)) {
    return false;
  }
  // Follow up "poke" messages with the raw data
  if (message->type == Message::POKE) {
    if (!WriteFile(child->getWriteToStdinHandle(),
                   message->pokeArg.data, message->pokeArg.numBytes, &numBytesWritten, NULL)) {
      return false;
    }
    if (numBytesWritten != message->pokeArg.numBytes) {
      return false;
    }
  }
  return true;
}

/** Copies data from child's stdout to the client's IOBuf and sends it
    along */
bool
forwardReplyToClient(ChildInfo* child, ClientInfo* client) {
  DWORD total = 0;
  IOBuf::FillState ret;

  do {
    DWORD temp;
    ret = client->getIOBuf()->fillFromFileHandle(child->getReadFromStdoutHandle(),
                                                 &temp);
    if (ret == IOBuf::DONE || ret == IOBuf::MORE_DATA_PENDING) {
      if (!client->getIOBuf()->flush()) {
#ifdef DEBUGGING
        cerr << "Forward failed because flush failed" << endl;
#endif
        return false;
      }
      total += temp;
    }
  } while (ret == IOBuf::MORE_DATA_PENDING);

  return (ret == IOBuf::FAILED) ? false : true;
}

//----------------------------------------------------------------------
// Server Handler
//

class ServerHandler : public Handler {
public:
  ServerHandler();

  // Starts up in Unicode mode by default
  bool getASCII();

  void setIOBuf(IOBuf* ioBuf);

  void procList(char* arg);

  // Must be called before calling one of the routines below
  void setClientInfo(ClientInfo* info);

  // Indicates to outer loop that exit was called or that an error
  // occurred and that the client exited.
  bool exited();
  // Clears this state
  void clearExited();

  void ascii(char* arg);
  void unicode(char* arg);
  void attach(char* arg);
  void detach(char* arg);
  void libInfo(char* arg);
  void peek(char* arg);
  void poke(char* arg);
  void threadList(char* arg);
  void dupHandle(char* arg);
  void closeHandle(char* arg);
  void getContext(char* arg);
  void setContext(char* arg);
  void selectorEntry(char* arg);
  void suspend(char* arg);
  void resume(char* arg);
  void pollEvent(char* arg);
  void continueEvent(char* arg);
  void exit(char* arg);

  // This is pretty gross. Needed to make the target process know
  // about clients that have disconnected unexpectedly while attached.
  friend void shutdownClient(ClientInfo*);
private:
  // Writes: charSize <space> numChars <space> <binary string>
  // Handles both ASCII and UNICODE modes
  void writeString(USHORT len, WCHAR* str);

  // Handles only ASCII mode
  void writeString(USHORT len, char* str);

  ClientInfo* clientInfo;
  IOBuf* ioBuf;
  bool _exited;
  bool _ascii;
};

static ServerHandler* handler;

ServerHandler::ServerHandler() {
  _exited = false;
  _ascii = false;
  ioBuf = NULL;
}

bool
ServerHandler::getASCII() {
  return _ascii;
}

void
ServerHandler::setIOBuf(IOBuf* buf) {
  ioBuf = buf;
}

void
ServerHandler::setClientInfo(ClientInfo* info) {
  clientInfo = info;
}

bool
ServerHandler::exited() {
  return _exited;
}

void
ServerHandler::clearExited() {
  _exited = false;
}

void
ServerHandler::ascii(char* arg) {
  _ascii = true;
}

void
ServerHandler::unicode(char* arg) {
  _ascii = false;
}

void
ServerHandler::procList(char* arg) {
#ifdef DEBUGGING
  cerr << "proclist" << endl;
#endif

  ProcEntryList processes;
  ::procList(processes);

  ioBuf->writeInt(processes.size());

  for (ProcEntryList::iterator iter = processes.begin();
       iter != processes.end(); iter++) {
    ProcEntry& entry = *iter;
    ioBuf->writeSpace();
    ioBuf->writeUnsignedInt(entry.getPid());
    ioBuf->writeSpace();
    writeString(entry.getNameLength(), entry.getName());
  }

  ioBuf->writeEOL();
  ioBuf->flush();
}

void
ServerHandler::attach(char* arg) {
  // If the client is already attached to a process, fail.
  if (clientInfo->getTarget() != NULL) {
    ioBuf->writeBoolAsInt(false);
    ioBuf->writeEOL();
    ioBuf->flush();
    return;
  }

  // Try to get pid
  DWORD pid;
  if (!scanUnsignedLong(&arg, &pid)) {
    ioBuf->writeBoolAsInt(false);
    ioBuf->writeEOL();
    ioBuf->flush();
    return;
  }

  // See whether this pid is already forked
  ListsLocker ll;
  ChildInfo* childInfo = childList.getChildByPid(pid);
  if (childInfo != NULL) {
    // If this child already has a client, return false
    if (childInfo->getClient() != NULL) {
      ioBuf->writeBoolAsInt(false);
      ioBuf->writeEOL();
      ioBuf->flush();
      return;
    }

    // Otherwise, can associate this client with this child process
    childInfo->setClient(clientInfo);
    clientInfo->setTarget(childInfo);

    // Tell the child we are attaching so it can suspend the target
    // process
    Message msg;
    msg.type = Message::ATTACH;
    sendMessage(childInfo, &msg);

    ioBuf->writeBoolAsInt(true);
    ioBuf->writeEOL();
    ioBuf->flush();
    return;
  } else {
    // Have to fork a new child subprocess
    HANDLE childProcessHandle;
    HANDLE writeToStdinHandle;
    HANDLE readFromStdoutHandle;
    HANDLE auxHandle1;
    HANDLE auxHandle2;
    if (!startChildProcess(pid,
                           32768,
                           131072,
                           &childProcessHandle,
                           &writeToStdinHandle,
                           &readFromStdoutHandle,
                           &auxHandle1,
                           &auxHandle2)) {
      ioBuf->writeBoolAsInt(false);
      ioBuf->writeEOL();
      ioBuf->flush();
      return;
    }

    // See whether the child succeeded in attaching to the process
    char res;
    DWORD numRead;
    if (!ReadFile(readFromStdoutHandle,
                  &res,
                  sizeof(char),
                  &numRead,
                  NULL)) {
      ioBuf->writeBoolAsInt(false);
      ioBuf->writeEOL();
      ioBuf->flush();
      return;
    }

    if (!res) {
      ioBuf->writeBoolAsInt(false);
      ioBuf->writeEOL();
      ioBuf->flush();
      return;
    }

    // OK, success.
    childInfo = new ChildInfo(pid, childProcessHandle,
                              writeToStdinHandle, readFromStdoutHandle,
                              auxHandle1, auxHandle2);
    childList.addChild(childInfo);
    reaper->registerProcess(childProcessHandle, childInfo);
    // Associate this client with this child process
    childInfo->setClient(clientInfo);
    clientInfo->setTarget(childInfo);

    // Tell the child process to actually suspend the target process
    Message msg;
    msg.type = Message::ATTACH;
    sendMessage(childInfo, &msg);

    // Write result to client
    ioBuf->writeBoolAsInt(true);
    ioBuf->writeEOL();
    ioBuf->flush();
    return;
  }
}

void
ServerHandler::detach(char* arg) {
  // If the client is not attached, fail.
  if (clientInfo->getTarget() == NULL) {
    ioBuf->writeBoolAsInt(false);
    ioBuf->writeEOL();
    ioBuf->flush();
    return;
  }

  detachClient(clientInfo);

  ioBuf->writeBoolAsInt(true);
  ioBuf->writeEOL();
  ioBuf->flush();
}

void
ServerHandler::libInfo(char* arg) {
  ListsLocker ll;
  ChildInfo* child = clientInfo->getTarget();
  if (child == NULL) {
    ioBuf->writeInt(0);
    ioBuf->writeEOL();
    ioBuf->flush();
    return;
  }

  // Send message to child
  Message msg;
  msg.type = Message::LIBINFO;
  sendMessage(child, &msg);

  // Forward reply to client
  forwardReplyToClient(child, clientInfo);
}

void
ServerHandler::peek(char* arg) {
  ListsLocker ll;
  ChildInfo* child = clientInfo->getTarget();
  if (child == NULL) {
    ioBuf->writeString("B");
    ioBuf->writeBinChar(0);
    ioBuf->flush();
    return;
  }

  // Try to get address
  DWORD address;
  if (!scanAddress(&arg, &address)) {
    ioBuf->writeString("B");
    ioBuf->writeBinChar(0);
    ioBuf->flush();
    return;
  }

  // Try to get number of bytes
  DWORD numBytes;
  if (!scanUnsignedLong(&arg, &numBytes)) {
    ioBuf->writeString("B");
    ioBuf->writeBinChar(0);
    ioBuf->flush();
    return;
  }

  // Send message to child
  Message msg;
  msg.type = Message::PEEK;
  msg.peekArg.address = address;
  msg.peekArg.numBytes = numBytes;
  sendMessage(child, &msg);

  // Forward reply to client
  forwardReplyToClient(child, clientInfo);
}

void
ServerHandler::poke(char* arg) {
#ifdef DEBUGGING
  cerr << "ServerHandler::poke" << endl;
#endif
  ListsLocker ll;
  ChildInfo* child = clientInfo->getTarget();
  if (child == NULL) {
    ioBuf->writeBoolAsInt(false);
    ioBuf->flush();
    return;
  }

  // Try to get address
  DWORD address;
  if (!scanAddress(&arg, &address)) {
    ioBuf->writeBoolAsInt(false);
    ioBuf->flush();
    return;
  }

  // Try to get number of bytes
  if (!scanAndSkipBinEscapeChar(&arg)) {
    ioBuf->writeBoolAsInt(false);
    ioBuf->flush();
    return;
  }
  DWORD numBytes;
  if (!scanBinUnsignedLong(&arg, &numBytes)) {
    ioBuf->writeBoolAsInt(false);
    ioBuf->flush();
    return;
  }

  // Raw data is now in "arg"
  // Send message to child
  Message msg;
  msg.type = Message::POKE;
  msg.pokeArg.address = address;
  msg.pokeArg.numBytes = numBytes;
  msg.pokeArg.data = arg;
  sendMessage(child, &msg);

  // Forward reply to client
  forwardReplyToClient(child, clientInfo);
}

void
ServerHandler::threadList(char* arg) {
  ListsLocker ll;
  ChildInfo* child = clientInfo->getTarget();
  if (child == NULL) {
    ioBuf->writeBoolAsInt(false);
    ioBuf->flush();
    return;
  }

  // Send message to child
  Message msg;
  msg.type = Message::THREADLIST;
  sendMessage(child, &msg);

  // Forward reply to client
  forwardReplyToClient(child, clientInfo);
}

void
ServerHandler::dupHandle(char* arg) {
  ListsLocker ll;
  ChildInfo* child = clientInfo->getTarget();
  if (child == NULL) {
    ioBuf->writeBoolAsInt(false);
    ioBuf->flush();
    return;
  }

  // Try to get handle
  DWORD address;
  if (!scanAddress(&arg, &address)) {
    ioBuf->writeBoolAsInt(false);
    ioBuf->flush();
  }

  // Send message to child
  Message msg;
  msg.type = Message::DUPHANDLE;
  msg.handleArg.handle = (HANDLE) address;
  sendMessage(child, &msg);

  // Forward reply to client
  forwardReplyToClient(child, clientInfo);
}

void
ServerHandler::closeHandle(char* arg) {
  ListsLocker ll;
  ChildInfo* child = clientInfo->getTarget();
  if (child == NULL) {
    return;
  }

  // Try to get handle
  DWORD address;
  if (!scanAddress(&arg, &address)) {
    return;
  }

  // Send message to child
  Message msg;
  msg.type = Message::CLOSEHANDLE;
  msg.handleArg.handle = (HANDLE) address;
  sendMessage(child, &msg);

  // No reply
}

void
ServerHandler::getContext(char* arg) {
  ListsLocker ll;
  ChildInfo* child = clientInfo->getTarget();
  if (child == NULL) {
    ioBuf->writeBoolAsInt(false);
    ioBuf->flush();
    return;
  }

  // Try to get handle
  DWORD address;
  if (!scanAddress(&arg, &address)) {
    ioBuf->writeBoolAsInt(false);
    ioBuf->flush();
    return;
  }

  // Send message to child
  Message msg;
  msg.type = Message::GETCONTEXT;
  msg.handleArg.handle = (HANDLE) address;
  sendMessage(child, &msg);

  // Forward reply to client
  forwardReplyToClient(child, clientInfo);
}

void
ServerHandler::setContext(char* arg) {
  ListsLocker ll;
  ChildInfo* child = clientInfo->getTarget();
  if (child == NULL) {
    ioBuf->writeBoolAsInt(false);
    ioBuf->flush();
    return;
  }

  // Try to get handle
  DWORD address;
  if (!scanAddress(&arg, &address)) {
    ioBuf->writeBoolAsInt(false);
    ioBuf->flush();
    return;
  }

  // Try to get context
  DWORD regs[NUM_REGS_IN_CONTEXT];
  for (int i = 0; i < NUM_REGS_IN_CONTEXT; i++) {
    if (!scanAddress(&arg, &regs[i])) {
      ioBuf->writeBoolAsInt(false);
      ioBuf->flush();
      return;
    }
  }

  // Send message to child
  Message msg;
  msg.type = Message::SETCONTEXT;
  msg.setContextArg.handle = (HANDLE) address;
  msg.setContextArg.Eax    = regs[0];
  msg.setContextArg.Ebx    = regs[1];
  msg.setContextArg.Ecx    = regs[2];
  msg.setContextArg.Edx    = regs[3];
  msg.setContextArg.Esi    = regs[4];
  msg.setContextArg.Edi    = regs[5];
  msg.setContextArg.Ebp    = regs[6];
  msg.setContextArg.Esp    = regs[7];
  msg.setContextArg.Eip    = regs[8];
  msg.setContextArg.Ds     = regs[9];
  msg.setContextArg.Es     = regs[10];
  msg.setContextArg.Fs     = regs[11];
  msg.setContextArg.Gs     = regs[12];
  msg.setContextArg.Cs     = regs[13];
  msg.setContextArg.Ss     = regs[14];
  msg.setContextArg.EFlags = regs[15];
  msg.setContextArg.Dr0    = regs[16];
  msg.setContextArg.Dr1    = regs[17];
  msg.setContextArg.Dr2    = regs[18];
  msg.setContextArg.Dr3    = regs[19];
  msg.setContextArg.Dr6    = regs[20];
  msg.setContextArg.Dr7    = regs[21];
  sendMessage(child, &msg);

  // Forward reply to client
  forwardReplyToClient(child, clientInfo);
}

void
ServerHandler::selectorEntry(char* arg) {
  ListsLocker ll;
  ChildInfo* child = clientInfo->getTarget();
  if (child == NULL) {
    ioBuf->writeBoolAsInt(false);
    ioBuf->flush();
    return;
  }

  // Try to get thread handle
  DWORD address;
  if (!scanAddress(&arg, &address)) {
    ioBuf->writeBoolAsInt(false);
    ioBuf->flush();
    return;
  }

  // Try to get selector
  DWORD selector;
  if (!scanUnsignedLong(&arg, &selector)) {
    ioBuf->writeBoolAsInt(false);
    ioBuf->flush();
    return;
  }

  // Send message to child
  Message msg;
  msg.type = Message::SELECTORENTRY;
  msg.selectorArg.handle   = (HANDLE) address;
  msg.selectorArg.selector = selector;
  sendMessage(child, &msg);

  // Forward reply to client
  forwardReplyToClient(child, clientInfo);
}

void
ServerHandler::suspend(char* arg) {
  ListsLocker ll;
  ChildInfo* child = clientInfo->getTarget();
  if (child == NULL) {
    return;
  }

  // Send message to child
  Message msg;
  msg.type = Message::SUSPEND;
  sendMessage(child, &msg);

  // No reply
}

void
ServerHandler::resume(char* arg) {
  ListsLocker ll;
  ChildInfo* child = clientInfo->getTarget();
  if (child == NULL) {
    return;
  }

  // Send message to child
  Message msg;
  msg.type = Message::RESUME;
  sendMessage(child, &msg);

  // No reply
}

void
ServerHandler::pollEvent(char* arg) {
  ListsLocker ll;
  ChildInfo* child = clientInfo->getTarget();
  if (child == NULL) {
    ioBuf->writeBoolAsInt(false);
    ioBuf->flush();
    return;
  }

  // Send message to child
  Message msg;
  msg.type = Message::POLLEVENT;
  sendMessage(child, &msg);

  // Forward reply to client
  forwardReplyToClient(child, clientInfo);
}

void
ServerHandler::continueEvent(char* arg) {
  ListsLocker ll;
  ChildInfo* child = clientInfo->getTarget();
  if (child == NULL) {
    ioBuf->writeBoolAsInt(false);
    ioBuf->flush();
    return;
  }

  // Try to get bool arg
  int passEventToClient;
  if (!scanInt(&arg, &passEventToClient)) {
    ioBuf->writeBoolAsInt(false);
    ioBuf->flush();
    return;
  }

  // Send message to child
  Message msg;
  msg.type = Message::CONTINUEEVENT;
  msg.boolArg.val = ((passEventToClient != 0) ? true : false);
  sendMessage(child, &msg);

  // Forward reply to client
  forwardReplyToClient(child, clientInfo);
}

void
ServerHandler::exit(char* arg) {
  shutdownClient(clientInfo);
  _exited = true;
}

void
ServerHandler::writeString(USHORT len, WCHAR* str) {
  if (_ascii) {
    char* cStr = new char[len + 1];
    sprintf(cStr, "%.*ls", len, str);
    writeString(len, cStr);
    delete[] cStr;
  } else {
    ioBuf->writeInt(sizeof(unsigned short));
    ioBuf->writeSpace();
    ioBuf->writeInt(len);
    ioBuf->writeSpace();
    for (int i = 0; i < len; i++) {
      ioBuf->writeBinUnsignedShort(str[i]);
    }
  }
}

void
ServerHandler::writeString(USHORT len, char* str) {
  ioBuf->writeInt(1);
  ioBuf->writeSpace();
  ioBuf->writeInt(len);
  ioBuf->writeSpace();
  ioBuf->writeString(str);
}

//
//----------------------------------------------------------------------

//----------------------------------------------------------------------
// Shutdown routines
//

void
shutdownChild(ChildInfo* childInfo) {
  childList.removeChild(childInfo);
  childInfo->closeAll();
  if (childInfo->getClient() != NULL) {
    shutdownClient(childInfo->getClient());
  }
  delete childInfo;
}

void
detachClient(ClientInfo* info) {
  ListsLocker ll;
  // May have been dissociated while not under cover of lock
  if (info->getTarget() == NULL) {
    return;
  }

  // Tell the child that we have detached to let the target process
  // continue running
  Message msg;
  msg.type = Message::DETACH;
  sendMessage(info->getTarget(), &msg);

  // Dissociate the client and the target
  info->getTarget()->setClient(NULL);
  info->setTarget(NULL);
}

void
shutdownClient(ClientInfo* clientInfo) {
#ifdef DEBUGGING
  cerr << "Shutting down client" << endl;
#endif

  // If we're connected, inform the target process that we're
  // disconnecting
  detachClient(clientInfo);

  // Remove this client from the list and delete it
  clientList.removeClient(clientInfo);
  if (clientInfo->getTarget() != NULL) {
    clientInfo->getTarget()->setClient(NULL);
  }
  clientInfo->closeAll();
  delete clientInfo;
}

//
//----------------------------------------------------------------------


/** Main dispatcher for client commands. NOTE: do not refer to this
    clientInfo data structure after calling this routine, as it may be
    deleted internally. */
void
readAndDispatch(ClientInfo* clientInfo) {
  IOBuf::ReadLineResult res;
  IOBuf* ioBuf = clientInfo->getIOBuf();
  unsigned long howMany;
  ioctlsocket(clientInfo->getDataSocket(), FIONREAD, &howMany);
  if (howMany == 0) {
    // Client closed down.
    shutdownClient(clientInfo);
    return;
  }
  // Read and process as much data as possible
  do {
    res = ioBuf->tryReadLine();
    if (res == IOBuf::RL_ERROR) {
#ifdef DEBUGGING
      cerr << "Error while reading line" << endl;
#endif
      shutdownClient(clientInfo);
      return;
    } else if (res == IOBuf::RL_GOT_DATA) {
#ifdef DEBUGGING
      cerr << "Got data: \"" << ioBuf->getLine() << "\"" << endl;
#endif
      handler->setIOBuf(ioBuf);
      handler->setClientInfo(clientInfo);
      handler->clearExited();
      Dispatcher::dispatch(ioBuf->getLine(), handler);
    }
  } while (res == IOBuf::RL_GOT_DATA && (!handler->exited()));
#ifdef DEBUGGING
  cerr << "Exiting readAndDispatch" << endl;
#endif
}

int
main(int argc, char **argv)
{
  initWinsock();

  if (isNT4()) {
    loadPSAPIDLL(); // Will exit if not present
  }

  SOCKET clientListeningSock = setupListeningSocket(CLIENT_PORT);

  handler = new ServerHandler();
  Lists::init();

  reaper = new Reaper(&reapCB);
  if (!reaper->start()) {
    exit(1);
  }

  while (true) {
    // Select on all sockets:
    //  - client listening socket
    //  - sockets for all client connections

    // When one of the client connections closes, close its socket
    // handles.

    fd_set set;
    SOCKET maxSock = 0;

    // Set up fd_set
    {
      int i;
      FD_ZERO(&set);
      FD_SET(clientListeningSock, &set);
      if (clientListeningSock > maxSock) {
        maxSock = clientListeningSock;
      }
      for (i = 0; i < clientList.size(); i++) {
        ClientInfo* info = clientList.get(i);
        if (info->getDataSocket() > maxSock) {
          maxSock = info->getDataSocket();
        }
        FD_SET(info->getDataSocket(), &set);
      }
    }
    struct timeval timeout;
    timeout.tv_sec = 300; // 5 minutes
    timeout.tv_usec = 0;
    int res = select(maxSock, &set, NULL, NULL, &timeout);
    if (res > 0) {

      ////////////////
      // New client //
      ////////////////
      if (FD_ISSET(clientListeningSock, &set)) {
        SOCKET fd = acceptFromLocalhost(clientListeningSock);
        if (fd != INVALID_SOCKET) {
          // Create new client information object
          ClientInfo* info = new ClientInfo(fd);
          // Add to list of clients
          clientList.addClient(info);
#ifdef DEBUGGING
          cerr << "New client" << endl;
#endif
        }
      }

      ///////////////////////////
      // Commands from clients //
      ///////////////////////////
      ClientInfo* clientInfo;
      if (clientList.isAnyDataSocketSet(&set, &clientInfo)) {
        readAndDispatch(clientInfo);
      }
    } else if (res < 0) {
      // Looks like one of the clients was killed. Try to figure out which one.
      bool found = false;
      fd_set set;
      struct timeval timeout;
      timeout.tv_sec = 0;
      timeout.tv_usec = 0;
      for (int i = 0; i < clientList.size(); i++) {
        ClientInfo* info = clientList.get(i);
        FD_ZERO(&set);
        FD_SET(info->getDataSocket(), &set);
        if (select(1 + info->getDataSocket(), &set, NULL, NULL, &timeout) < 0) {
          found = true;
          clientList.removeClient(info);
          info->closeAll();
          delete info;
          break;
        }
      }
      if (!found) {
        // This indicates trouble -- one of our listening sockets died.
        exit(1);
      }
    }
  }

  return 0;
}
