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

#ifndef _IO_BUF_
#define _IO_BUF_

// This file is currently used for os/solaris/agent/ too.  At some point in time
// the source will be reorganized to avoid these ifdefs.
// Note that this class can read/write from a file as well as a socket.  This
// file capability is only implemented on win32.

#ifdef WIN32
  #include <winsock2.h>
#else
  #include <sys/types.h>
  #include <sys/socket.h>
  // These are from win32 winsock2.h
  typedef unsigned int SOCKET;
  typedef void * HANDLE;
  typedef unsigned long DWORD;
  #define INVALID_SOCKET (SOCKET)(~0)
#endif

#include <vector>
#include "Buffer.hpp"

/** Manages an input/output buffer pair for a socket or file handle. */
class IOBuf {
public:
  IOBuf(int inBufLen, int outBufLen);
  ~IOBuf();

  enum ReadLineResult {
    RL_GOT_DATA,
    RL_NO_DATA,
    RL_ERROR
  };

  /** Change the socket with which this buffer is associated */
  void setSocket(SOCKET sock);

  // Reading/writing files is only supported on windows.
#ifdef WIN32
  /** Change the output file handle with which this buffer is
      associated. Currently IOBufs can not be used to read from a file
      handle. */
  void setOutputFileHandle(HANDLE handle);
#endif

  /** Reset the input and output buffers, without flushing the output
      data to the socket */
  void reset();

  /** Try to read a line of data from the given socket without
      blocking. If was able to read a complete line of data, returns a
      character pointer to the beginning of the (null-terminated)
      string. If not, returns NULL, but maintains enough state that
      subsequent calls to tryReadLine() will not ignore the data
      already read. NOTE: this skips end-of-line characters (typically
      CR/LF) as defined by "isEOL()". When switching back and forth
      between binary and text modes, to be sure no data is lost, pad
      the beginning and end of the binary transmission with bytes
      which can not be confused with these characters. */
  ReadLineResult tryReadLine();

  /** Read a line of data from the given socket, blocking until a
      line, including EOL, appears.  Return the line, or NULL if
      something goes wrong. */
  char *readLine();

  /** Get the pointer to the beginning of the (null-terminated) line.
      This should only be called if tryReadLine() has returned
      RL_GOT_DATA. This sets the "parsing cursor" to the beginning of
      the line. */
  char* getLine();

  // NOTE: any further data-acquisition routines must ALWAYS call
  // fixupData() at the beginning!

  //----------------------------------------------------------------------
  // Output routines
  //

  /** Flush the output buffer to the socket. Returns true if
      succeeded, false if write error occurred. */
  bool flush();

  /** Write the given string to the output buffer. May flush if output
      buffer becomes too full to store the data. Not guaranteed to
      work if string is longer than the size of the output buffer.
      Does not include the null terminator of the string. Returns true
      if succeeded, false if write error occurred. */
  bool writeString(const char* str);

  /** Write the given int to the output buffer. May flush if output
      buffer becomes too full to store the data. Returns true if
      succeeded, false if write error occurred. */
  bool writeInt(int val);

  /** Write the given unsigned int to the output buffer. May flush if
      output buffer becomes too full to store the data. Returns true
      if succeeded, false if write error occurred. */
  bool writeUnsignedInt(unsigned int val);

  /** Write the given boolean to the output buffer. May flush if
      output buffer becomes too full to store the data. Returns true
      if succeeded, false if write error occurred. */
  bool writeBoolAsInt(bool val);

  /** Write the given address to the output buffer. May flush if
      output buffer becomes too full to store the data. Returns true
      if succeeded, false if write error occurred. */
  bool writeAddress(void* val);

  /** Writes a space to the output buffer. May flush if output buffer
      becomes too full to store the data. Returns true if succeeded,
      false if write error occurred. */
  bool writeSpace();

  /** Writes an end-of-line sequence to the output buffer. May flush
      if output buffer becomes too full to store the data. Returns
      true if succeeded, false if write error occurred. */
  bool writeEOL();

  /** Writes a binary character to the output buffer. */
  bool writeBinChar(char c);

  /** Writes a binary unsigned short in network (big-endian) byte
      order to the output buffer. */
  bool writeBinUnsignedShort(unsigned short i);

  /** Writes a binary unsigned int in network (big-endian) byte order
      to the output buffer. */
  bool writeBinUnsignedInt(unsigned int i);

  /** Writes a binary buffer to the output buffer. */
  bool writeBinBuf(char* buf, int size);

#ifdef WIN32
  enum FillState {
    DONE = 1,
    MORE_DATA_PENDING = 2,
    FAILED = 3
  };

  /** Very specialized routine; fill the output buffer from the given
      file handle. Caller is responsible for ensuring that there is
      data to be read on the file handle. */
  FillState fillFromFileHandle(HANDLE fh, DWORD* numRead);
#endif

  /** Binary utility routine (for poke) */
  static bool isBinEscapeChar(char c);

private:
  IOBuf(const IOBuf&);
  IOBuf& operator=(const IOBuf&);

  // Returns -1 if non-blocking and no data available
  int readChar(bool block);
  // Line-oriented reading
  std::vector<char> curLine;
  bool gotDataLastTime;

  ReadLineResult doReadLine(bool);

  bool flushImpl(bool moreDataToCome);

  SOCKET fd;
  HANDLE outHandle;
  bool usingSocket;

  // Buffers
  Buffer* inBuf;
  Buffer* outBuf;

  // Simple finite-state machine to handle binary data
  enum State {
    TEXT_STATE,
    BIN_STATE,
    EOL_STATE
  };
  enum Action {
    NO_ACTION,
    GOT_LINE,     // TEXT_STATE -> EOL_STATE transition
    SKIP_EOL_CHAR // EOL_STATE -> EOL_STATE transition
  };

  State state;
  Action processChar(char c);

  // Handling incoming binary buffers (poke command)
  int   binPos;    // Number of binary characters read so far;
                   // total number to read is binLength + 4
  int   binLength; // Number of binary characters in message;
                   // not valid until binPos >= 4

  bool isEOL(char c);
};

#endif  // #defined _IO_BUF_
