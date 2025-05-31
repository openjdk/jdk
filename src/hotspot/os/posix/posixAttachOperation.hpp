/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef OS_POSIX_POSIXATTACHOPERATION_HPP
#define OS_POSIX_POSIXATTACHOPERATION_HPP

#include "os_posix.hpp"
#include "services/attachListener.hpp"

#if INCLUDE_SERVICES
#ifndef AIX

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
      ::shutdown(_socket, SHUT_RDWR);
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

  void flush() override {
  }
};

class PosixAttachOperation: public AttachOperation {
 private:
  // the connection to the client
  SocketChannel _socket_channel;

 public:
  PosixAttachOperation(int socket) : AttachOperation(), _socket_channel(socket) {}

  void complete(jint res, bufferedStream* st) override;

  ReplyWriter* get_reply_writer() override {
    return &_socket_channel;
  }

  bool read_request() {
    return _socket_channel.read_request(this, &_socket_channel);
  }
};

#endif // !AIX

#endif // INCLUDE_SERVICES

#endif // OS_POSIX_POSIXATTACHOPERATION_HPP
