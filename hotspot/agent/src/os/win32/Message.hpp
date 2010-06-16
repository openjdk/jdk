/*
 * Copyright (c) 2000, 2001, Oracle and/or its affiliates. All rights reserved.
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

#ifndef _MESSAGE_
#define _MESSAGE_

// These are the commands sent from the server to the child processes
// over the child processes' stdin pipes. A subset of the commands
// understood by the overall system, these require responses from the
// child process. Having a data structure rather than sending text
// simplifies parsing on the child side. The child replies by sending
// back fully-formatted replies which are copied by the server process
// to the clients' sockets.

struct PeekArg {
  DWORD address;
  DWORD numBytes;
};

// NOTE: when sending a PokeArg to the child process, we handle the
// buffer specially
struct PokeArg {
  DWORD address;
  DWORD numBytes;
  void* data;
};

// Used for continueevent
struct BoolArg {
  bool val;
};

// Used for duphandle, closehandle, and getcontext
struct HandleArg {
  HANDLE handle;
};

// Used for setcontext
const int NUM_REGS_IN_CONTEXT = 22;
struct SetContextArg {
  HANDLE handle;
  DWORD  Eax;
  DWORD  Ebx;
  DWORD  Ecx;
  DWORD  Edx;
  DWORD  Esi;
  DWORD  Edi;
  DWORD  Ebp;
  DWORD  Esp;
  DWORD  Eip;
  DWORD  Ds;
  DWORD  Es;
  DWORD  Fs;
  DWORD  Gs;
  DWORD  Cs;
  DWORD  Ss;
  DWORD  EFlags;
  DWORD  Dr0;
  DWORD  Dr1;
  DWORD  Dr2;
  DWORD  Dr3;
  DWORD  Dr6;
  DWORD  Dr7;
};

// Used for selectorentry
struct SelectorEntryArg {
  HANDLE handle;
  DWORD  selector;
};

struct Message {
  typedef enum {
    ATTACH,
    DETACH,
    LIBINFO,
    PEEK,
    POKE,
    THREADLIST,
    DUPHANDLE,
    CLOSEHANDLE,
    GETCONTEXT,
    SETCONTEXT,
    SELECTORENTRY,
    SUSPEND,
    RESUME,
    POLLEVENT,
    CONTINUEEVENT
  } Type;

  Type type;
  union {
    PeekArg          peekArg;
    PokeArg          pokeArg;
    BoolArg          boolArg;
    HandleArg        handleArg;
    SetContextArg    setContextArg;
    SelectorEntryArg selectorArg;
  };
};

#endif  // #defined _MESSAGE_
