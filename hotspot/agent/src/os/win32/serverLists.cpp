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

#include <assert.h>
#include "serverLists.hpp"

//----------------------------------------------------------------------
// Lists
//

CRITICAL_SECTION Lists::crit;

void
Lists::init() {
  InitializeCriticalSection(&crit);
}

void
Lists::lock() {
  EnterCriticalSection(&crit);
}

void
Lists::unlock() {
  LeaveCriticalSection(&crit);
}

//----------------------------------------------------------------------
// ListsLocker
//

ListsLocker::ListsLocker() {
  Lists::lock();
}

ListsLocker::~ListsLocker() {
  Lists::unlock();
}

//----------------------------------------------------------------------
// ChildInfo
//

ChildInfo::ChildInfo(DWORD pid, HANDLE childProcessHandle,
                     HANDLE writeToStdinHandle, HANDLE readFromStdoutHandle,
                     HANDLE auxHandle1, HANDLE auxHandle2) {
  this->pid = pid;
  this->childProcessHandle = childProcessHandle;
  this->writeToStdinHandle = writeToStdinHandle;
  this->readFromStdoutHandle = readFromStdoutHandle;
  this->auxHandle1 = auxHandle1;
  this->auxHandle2 = auxHandle2;
  client = NULL;
}

DWORD
ChildInfo::getPid() {
  return pid;
}

HANDLE
ChildInfo::getChildProcessHandle() {
  return childProcessHandle;
}

HANDLE
ChildInfo::getWriteToStdinHandle() {
  return writeToStdinHandle;
}

HANDLE
ChildInfo::getReadFromStdoutHandle() {
  return readFromStdoutHandle;
}

void
ChildInfo::setClient(ClientInfo* clientInfo) {
  client = clientInfo;
}

ClientInfo*
ChildInfo::getClient() {
  return client;
}

void
ChildInfo::closeAll() {
  CloseHandle(childProcessHandle);
  CloseHandle(writeToStdinHandle);
  CloseHandle(readFromStdoutHandle);
  CloseHandle(auxHandle1);
  CloseHandle(auxHandle2);
}

//----------------------------------------------------------------------
// ChildList
//

ChildList::ChildList() {
}

ChildList::~ChildList() {
}

void
ChildList::addChild(ChildInfo* info) {
  // Could store these in binary sorted order by pid for efficiency
  childList.push_back(info);
}

ChildInfo*
ChildList::removeChild(HANDLE childProcessHandle) {
  for (ChildInfoList::iterator iter = childList.begin(); iter != childList.end();
       iter++) {
    ChildInfo* info = *iter;
    if (info->getChildProcessHandle() == childProcessHandle) {
      childList.erase(iter);
      return info;
    }
  }
  assert(false);
  return NULL;
}

void
ChildList::removeChild(ChildInfo* info) {
  for (ChildInfoList::iterator iter = childList.begin(); iter != childList.end();
       iter++) {
    if (*iter == info) {
      childList.erase(iter);
      return;
    }
  }
  assert(false);
}

ChildInfo*
ChildList::getChildByPid(DWORD pid) {
  for (ChildInfoList::iterator iter = childList.begin(); iter != childList.end();
       iter++) {
    ChildInfo* info = *iter;
    if (info->getPid() == pid) {
      return info;
    }
  }
  return NULL;
}

int
ChildList::size() {
  return childList.size();
}

ChildInfo*
ChildList::getChildByIndex(int index) {
  return childList[index];
}

//----------------------------------------------------------------------
// ClientInfo
//

ClientInfo::ClientInfo(SOCKET dataSocket) {
  this->dataSocket = dataSocket;
  buf = new IOBuf(32768, 131072);
  buf->setSocket(dataSocket);
  target = NULL;
}

ClientInfo::~ClientInfo() {
  delete buf;
}

SOCKET
ClientInfo::getDataSocket() {
  return dataSocket;
}

IOBuf*
ClientInfo::getIOBuf() {
  return buf;
}

void
ClientInfo::setTarget(ChildInfo* childInfo) {
  target = childInfo;
}

ChildInfo*
ClientInfo::getTarget() {
  return target;
}

void
ClientInfo::closeAll() {
  shutdown(dataSocket, SD_BOTH);
  closesocket(dataSocket);
  dataSocket = INVALID_SOCKET;
}

//----------------------------------------------------------------------
// ClientList
//

ClientList::ClientList() {
}

ClientList::~ClientList() {
}

void
ClientList::addClient(ClientInfo* info) {
  clientList.push_back(info);
}

bool
ClientList::isAnyDataSocketSet(fd_set* fds, ClientInfo** out) {
  for (ClientInfoList::iterator iter = clientList.begin(); iter != clientList.end();
       iter++) {
    ClientInfo* info = *iter;
    if (FD_ISSET(info->getDataSocket(), fds)) {
      *out = info;
      return true;
    }
  }
  return false;
}

void
ClientList::removeClient(ClientInfo* client) {
  for (ClientInfoList::iterator iter = clientList.begin(); iter != clientList.end();
       iter++) {
    if (*iter == client) {
      clientList.erase(iter);
      return;
    }
  }
  assert(false);
}

int
ClientList::size() {
  return clientList.size();
}

ClientInfo*
ClientList::get(int num) {
  return clientList[num];
}
