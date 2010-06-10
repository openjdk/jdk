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

// This is the source code for the subprocess forked by the Simple
// Windows Debug Server. It assumes most of the responsibility for the
// debug session, and processes all of the commands sent by clients.

// Disable too-long symbol warnings
#pragma warning ( disable : 4786 )

#include <iostream>
#include <vector>
#include <stdlib.h>
#include <assert.h>
// Must come before windows.h
#include <winsock2.h>
#include <windows.h>
#include "IOBuf.hpp"
#include "libInfo.hpp"
#include "LockableList.hpp"
#include "Message.hpp"
#include "Monitor.hpp"
#include "nt4internals.hpp"

// Uncomment the #define below to get messages on stderr
// #define DEBUGGING

using namespace std;

DWORD pid;
HANDLE procHandle;
IOBuf* ioBuf;

// State flags indicating whether the attach to the remote process
// definitively succeeded or failed
volatile bool attachFailed    = false;
volatile bool attachSucceeded = false;

// State flag indicating whether the target process is suspended.
// Modified by suspend()/resume(), viewed by debug thread, but only
// under cover of the threads lock.
volatile bool suspended       = false;

// State flags indicating whether we are considered to be attached to
// the target process and are therefore queuing up events to be sent
// back to the debug server. These flags are only accessed and
// modified under the cover of the eventLock.
Monitor* eventLock;
// The following is set to true when a client is attached to this process
volatile bool generateDebugEvents = false;
// Pointer to current debug event; non-NULL indicates a debug event is
// waiting to be sent to the client. Main thread sets this to NULL to
// indicate that the event has been consumed; also sets
// passEventToClient, below.
volatile DEBUG_EVENT* curDebugEvent = NULL;
// Set by main thread to indicate whether the most recently posted
// debug event should be passed on to the target process.
volatile bool passEventToClient = true;

void conditionalPostDebugEvent(DEBUG_EVENT* ev, DWORD* continueOrNotHandledFlag) {
  // FIXME: make it possible for the client to enable and disable
  // certain types of events (have to do so in a platform-independent
  // manner)
  switch (ev->dwDebugEventCode) {
  case EXCEPTION_DEBUG_EVENT:
    switch (ev->u.Exception.ExceptionRecord.ExceptionCode) {
    case EXCEPTION_BREAKPOINT:  break;
    case EXCEPTION_SINGLE_STEP: break;
    case EXCEPTION_ACCESS_VIOLATION: break;
    default: return;
    }
  }
  eventLock->lock();
  if (generateDebugEvents) {
    curDebugEvent = ev;
    while (curDebugEvent != NULL) {
      eventLock->wait();
    }
    if (passEventToClient) {
      *continueOrNotHandledFlag = DBG_EXCEPTION_NOT_HANDLED;
    } else {
      *continueOrNotHandledFlag = DBG_CONTINUE;
    }
  }
  eventLock->unlock();
}


//----------------------------------------------------------------------
// Module list
//

vector<LibInfo> libs;

//----------------------------------------------------------------------
// Thread list
//

struct ThreadInfo {
  DWORD tid;
  HANDLE thread;

  ThreadInfo(DWORD tid, HANDLE thread) {
    this->tid = tid;
    this->thread = thread;
  }
};

class ThreadList : public LockableList<ThreadInfo> {
public:
  bool removeByThreadID(DWORD tid) {
    for (InternalListType::iterator iter = internalList.begin();
         iter != internalList.end(); iter++) {
      if ((*iter).tid == tid) {
        internalList.erase(iter);
        return true;
      }
    }
    return false;
  }
  HANDLE threadIDToHandle(DWORD tid) {
    for (InternalListType::iterator iter = internalList.begin();
         iter != internalList.end(); iter++) {
      if ((*iter).tid == tid) {
        return (*iter).thread;
      }
    }
    return NULL;
  }
};

ThreadList threads;

//----------------------------------------------------------------------
// INITIALIZATION AND TERMINATION
//

void
printError(const char* prefix) {
  DWORD detail = GetLastError();
  LPTSTR message;
  FormatMessage(FORMAT_MESSAGE_ALLOCATE_BUFFER |
                FORMAT_MESSAGE_FROM_SYSTEM,
                0,
                detail,
                0,
                (LPTSTR) &message,
                1,
                NULL);
  // FIXME: This is signaling an error: "The handle is invalid." ?
  // Do I have to do all of my WaitForDebugEvent calls from the same thread?
  cerr << prefix << ": " << message << endl;
  LocalFree(message);
}

void
endProcess(bool waitForProcess = true) {
  NT4::unloadNTDLL();
  if (waitForProcess) {
    // Though we're exiting because of an error, do not tear down the
    // target process.
    WaitForSingleObject(procHandle, INFINITE);
  }
  CloseHandle(procHandle);
  exit(0);
}

DWORD WINAPI
debugThreadEntry(void*) {
#ifdef DEBUGGING
  DWORD lastMsgId = 0;
  int count = 0;
#endif

  if (!DebugActiveProcess(pid)) {
    attachFailed = true;
    return 0;
  }

  // Wait for debug events. We keep the information from some of these
  // on the side in anticipation of later queries by the client. NOTE
  // that we leave the process running. The main thread is responsible
  // for suspending and resuming all currently-active threads upon
  // client attach and detach.

  while (true) {
    DEBUG_EVENT ev;
    if (!WaitForDebugEvent(&ev, INFINITE)) {
#ifdef DEBUGGING
      if (++count < 10) {
        // FIXME: This is signaling an error: "The handle is invalid." ?
        // Do I have to do all of my WaitForDebugEvent calls from the same thread?
        printError("WaitForDebugEvent failed");
      }
#endif
    } else {

#ifdef DEBUGGING
      if (ev.dwDebugEventCode != lastMsgId) {
        lastMsgId = ev.dwDebugEventCode;
        count = 0;
        cerr << "Debug thread received event " << ev.dwDebugEventCode << endl;
      } else {
        if (++count < 10) {
          cerr << "Debug thread received event " << ev.dwDebugEventCode << endl;
        }
      }
#endif

      DWORD dbgContinueMode = DBG_CONTINUE;

      switch (ev.dwDebugEventCode) {
      case LOAD_DLL_DEBUG_EVENT:
        conditionalPostDebugEvent(&ev, &dbgContinueMode);
        break;

      case UNLOAD_DLL_DEBUG_EVENT:
        conditionalPostDebugEvent(&ev, &dbgContinueMode);
        break;

      case CREATE_PROCESS_DEBUG_EVENT:
        threads.lock();
        // FIXME: will this deal properly with child processes? If
        // not, is it possible to make it do so?
#ifdef DEBUGGING
        cerr << "CREATE_PROCESS_DEBUG_EVENT " << ev.dwThreadId
             << " " << ev.u.CreateProcessInfo.hThread << endl;
#endif
        if (ev.u.CreateProcessInfo.hThread != NULL) {
          threads.add(ThreadInfo(ev.dwThreadId, ev.u.CreateProcessInfo.hThread));
        }
        threads.unlock();
        break;

      case CREATE_THREAD_DEBUG_EVENT:
        threads.lock();
#ifdef DEBUGGING
        cerr << "CREATE_THREAD_DEBUG_EVENT " << ev.dwThreadId
             << " " << ev.u.CreateThread.hThread << endl;
#endif
        if (suspended) {
          // Suspend this thread before adding it to the thread list
          SuspendThread(ev.u.CreateThread.hThread);
        }
        threads.add(ThreadInfo(ev.dwThreadId, ev.u.CreateThread.hThread));
        threads.unlock();
        break;

      case EXIT_THREAD_DEBUG_EVENT:
        threads.lock();
#ifdef DEBUGGING
        cerr << "EXIT_THREAD_DEBUG_EVENT " << ev.dwThreadId << endl;
#endif
        threads.removeByThreadID(ev.dwThreadId);
        threads.unlock();
        break;

      case EXCEPTION_DEBUG_EVENT:
        //      cerr << "EXCEPTION_DEBUG_EVENT" << endl;
        switch (ev.u.Exception.ExceptionRecord.ExceptionCode) {
        case EXCEPTION_BREAKPOINT:
          //        cerr << "EXCEPTION_BREAKPOINT" << endl;
          if (!attachSucceeded && !attachFailed) {
            attachSucceeded = true;
          }
          break;

        default:
          dbgContinueMode = DBG_EXCEPTION_NOT_HANDLED;
          break;
        }
        conditionalPostDebugEvent(&ev, &dbgContinueMode);
        break;

      case EXIT_PROCESS_DEBUG_EVENT:
        endProcess(false);
        // NOT REACHED
        break;

      default:
#ifdef DEBUGGING
        cerr << "Received debug event " << ev.dwDebugEventCode << endl;
#endif
        break;
      }

      ContinueDebugEvent(ev.dwProcessId, ev.dwThreadId, dbgContinueMode);
    }
  }
}

bool
attachToProcess() {
  // Create event lock
  eventLock = new Monitor();

  // Get a process handle for later
  procHandle = OpenProcess(PROCESS_ALL_ACCESS, FALSE, pid);
  if (procHandle == NULL) {
    return false;
  }

  // Start up the debug thread
  DWORD debugThreadId;
  if (CreateThread(NULL, 0, &debugThreadEntry, NULL, 0, &debugThreadId) == NULL) {
    // Failed to make background debug thread. Fail.
    return false;
  }

  while ((!attachSucceeded) && (!attachFailed)) {
    Sleep(1);
  }

  if (attachFailed) {
    return false;
  }

  assert(attachSucceeded);

  return true;
}

bool
readMessage(Message* msg) {
  DWORD numRead;
  if (!ReadFile(GetStdHandle(STD_INPUT_HANDLE),
                msg,
                sizeof(Message),
                &numRead,
                NULL)) {
    return false;
  }
  if (numRead != sizeof(Message)) {
    return false;
  }
  // For "poke" messages, must follow up by reading raw data
  if (msg->type == Message::POKE) {
    char* dataBuf = new char[msg->pokeArg.numBytes];
    if (dataBuf == NULL) {
      return false;
    }
    if (!ReadFile(GetStdHandle(STD_INPUT_HANDLE),
                  dataBuf,
                  msg->pokeArg.numBytes,
                  &numRead,
                  NULL)) {
      delete[] dataBuf;
      return false;
    }
    if (numRead != msg->pokeArg.numBytes) {
      delete[] dataBuf;
      return false;
    }
    msg->pokeArg.data = (void *) dataBuf;
  }
  return true;
}

void
handlePeek(Message* msg) {
#ifdef DEBUGGING
  cerr << "Entering handlePeek()" << endl;
#endif

  char* memBuf = new char[msg->peekArg.numBytes];
  if (memBuf == NULL) {
    ioBuf->writeString("B");
    ioBuf->writeBinChar(0);
    ioBuf->flush();
    delete[] memBuf;
    return;
  }

  // Try fast case first
  DWORD numRead;
  BOOL res = ReadProcessMemory(procHandle,
                               (LPCVOID) msg->peekArg.address,
                               memBuf,
                               msg->peekArg.numBytes,
                               &numRead);
  if (res && (numRead == msg->peekArg.numBytes)) {

    // OK, complete success. Phew.
#ifdef DEBUGGING
    cerr << "Peek success case" << endl;
#endif
    ioBuf->writeString("B");
    ioBuf->writeBinChar(1);
    ioBuf->writeBinUnsignedInt(numRead);
    ioBuf->writeBinChar(1);
    ioBuf->writeBinBuf(memBuf, numRead);
  } else {
#ifdef DEBUGGING
    cerr << "*** Peek slow case ***" << endl;
#endif

    ioBuf->writeString("B");
    ioBuf->writeBinChar(1);

    // Use VirtualQuery to speed things up a bit
    DWORD numLeft = msg->peekArg.numBytes;
    char* curAddr = (char*) msg->peekArg.address;
    while (numLeft > 0) {
      MEMORY_BASIC_INFORMATION memInfo;
      VirtualQueryEx(procHandle, curAddr, &memInfo, sizeof(memInfo));
      DWORD numToRead = memInfo.RegionSize;
      if (numToRead > numLeft) {
        numToRead = numLeft;
      }
      DWORD numRead;
      if (memInfo.State == MEM_COMMIT) {
        // Read the process memory at this address for this length
        // FIXME: should check the result of this read
        ReadProcessMemory(procHandle, curAddr, memBuf,
                          numToRead, &numRead);
        // Write this out
#ifdef DEBUGGING
        cerr << "*** Writing " << numToRead << " bytes as mapped ***" << endl;
#endif
        ioBuf->writeBinUnsignedInt(numToRead);
        ioBuf->writeBinChar(1);
        ioBuf->writeBinBuf(memBuf, numToRead);
      } else {
        // Indicate region is free
#ifdef DEBUGGING
        cerr << "*** Writing " << numToRead << " bytes as unmapped ***" << endl;
#endif
        ioBuf->writeBinUnsignedInt(numToRead);
        ioBuf->writeBinChar(0);
      }
      curAddr += numToRead;
      numLeft -= numToRead;
    }
  }

  ioBuf->flush();
  delete[] memBuf;
#ifdef DEBUGGING
  cerr << "Exiting handlePeek()" << endl;
#endif
}

void
handlePoke(Message* msg) {
#ifdef DEBUGGING
  cerr << "Entering handlePoke()" << endl;
#endif
  DWORD numWritten;
  BOOL res = WriteProcessMemory(procHandle,
                                (LPVOID) msg->pokeArg.address,
                                msg->pokeArg.data,
                                msg->pokeArg.numBytes,
                                &numWritten);
  if (res && (numWritten == msg->pokeArg.numBytes)) {
    // Success
    ioBuf->writeBoolAsInt(true);
#ifdef DEBUGGING
    cerr << " (Succeeded)" << endl;
#endif
  } else {
    // Failure
    ioBuf->writeBoolAsInt(false);
#ifdef DEBUGGING
    cerr << " (Failed)" << endl;
#endif
  }
  ioBuf->writeEOL();
  ioBuf->flush();
  // We clean up the data
  char* dataBuf = (char*) msg->pokeArg.data;
  delete[] dataBuf;
#ifdef DEBUGGING
  cerr << "Exiting handlePoke()" << endl;
#endif
}

bool
suspend() {
  if (suspended) {
    return false;
  }
  // Before we suspend, we must take a snapshot of the loaded module
  // names and base addresses, since acquiring this snapshot requires
  // starting and exiting a thread in the remote process (at least on
  // NT 4).
  libs.clear();
#ifdef DEBUGGING
  cerr << "Starting suspension" << endl;
#endif
  libInfo(pid, libs);
#ifdef DEBUGGING
  cerr << "  Got lib info" << endl;
#endif
  threads.lock();
#ifdef DEBUGGING
  cerr << "  Got thread lock" << endl;
#endif
  suspended = true;
  int j = 0;
  for (int i = 0; i < threads.size(); i++) {
    j++;
    SuspendThread(threads.get(i).thread);
  }
#ifdef DEBUGGING
  cerr << "Suspended " << j << " threads" << endl;
#endif
  threads.unlock();
  return true;
}

bool
resume() {
  if (!suspended) {
    return false;
  }
  threads.lock();
  suspended = false;
  for (int i = 0; i < threads.size(); i++) {
    ResumeThread(threads.get(i).thread);
  }
  threads.unlock();
#ifdef DEBUGGING
  cerr << "Resumed process" << endl;
#endif
  return true;
}

int
main(int argc, char **argv)
{
  if (argc != 2) {
    // Should only be used by performing CreateProcess within SwDbgSrv
    exit(1);
  }

  if (sscanf(argv[1], "%u", &pid) != 1) {
    exit(1);
  }

  // Try to attach to process
  if (!attachToProcess()) {
    // Attach failed. Notify parent by writing result to stdout file
    // handle.
    char res = 0;
    DWORD numBytes;
    WriteFile(GetStdHandle(STD_OUTPUT_HANDLE), &res, sizeof(res),
              &numBytes, NULL);
    exit(1);
  }

  // Server is expecting success result back.
  char res = 1;
  DWORD numBytes;
  WriteFile(GetStdHandle(STD_OUTPUT_HANDLE), &res, sizeof(res),
            &numBytes, NULL);

  // Initialize our I/O buffer
  ioBuf = new IOBuf(32768, 131072);
  ioBuf->setOutputFileHandle(GetStdHandle(STD_OUTPUT_HANDLE));

  // At this point we are attached. Enter our main loop which services
  // requests from the server. Note that in order to handle attach/
  // detach properly (i.e., resumption of process upon "detach") we
  // will need another thread which handles debug events.
  while (true) {
    // Read a message from the server
    Message msg;
    if (!readMessage(&msg)) {
      endProcess();
    }

#ifdef DEBUGGING
    cerr << "Main thread read message: " << msg.type << endl;
#endif

    switch (msg.type) {
    // ATTACH and DETACH messages MUST come in pairs
    case Message::ATTACH:
      suspend();
      eventLock->lock();
      generateDebugEvents = true;
      eventLock->unlock();
      break;

    case Message::DETACH:
      eventLock->lock();
      generateDebugEvents = false;
      // Flush remaining event if any
      if (curDebugEvent != NULL) {
        curDebugEvent = NULL;
        eventLock->notifyAll();
      }
      eventLock->unlock();
      resume();
      break;

    case Message::LIBINFO:
      {
        if (!suspended) {
          ioBuf->writeInt(0);
        } else {
          // Send back formatted text
          ioBuf->writeInt(libs.size());
          for (int i = 0; i < libs.size(); i++) {
            ioBuf->writeSpace();
            ioBuf->writeInt(1);
            ioBuf->writeSpace();
            ioBuf->writeInt(libs[i].name.size());
            ioBuf->writeSpace();
            ioBuf->writeString(libs[i].name.c_str());
            ioBuf->writeSpace();
            ioBuf->writeAddress(libs[i].base);
          }
        }
        ioBuf->writeEOL();
        ioBuf->flush();
        break;
      }

    case Message::PEEK:
      handlePeek(&msg);
      break;

    case Message::POKE:
      handlePoke(&msg);
      break;

    case Message::THREADLIST:
      {
        if (!suspended) {
          ioBuf->writeInt(0);
        } else {
          threads.lock();
          ioBuf->writeInt(threads.size());
          for (int i = 0; i < threads.size(); i++) {
            ioBuf->writeSpace();
            ioBuf->writeAddress((void*) threads.get(i).thread);
          }
          threads.unlock();
        }
        ioBuf->writeEOL();
        ioBuf->flush();
        break;
      }

    case Message::DUPHANDLE:
      {
        HANDLE dup;
        if (DuplicateHandle(procHandle,
                            msg.handleArg.handle,
                            GetCurrentProcess(),
                            &dup,
                            0,
                            FALSE,
                            DUPLICATE_SAME_ACCESS)) {
          ioBuf->writeBoolAsInt(true);
          ioBuf->writeSpace();
          ioBuf->writeAddress((void*) dup);
        } else {
          ioBuf->writeBoolAsInt(false);
        }
        ioBuf->writeEOL();
        ioBuf->flush();
        break;
      }

    case Message::CLOSEHANDLE:
      {
        CloseHandle(msg.handleArg.handle);
        break;
      }

    case Message::GETCONTEXT:
      {
        if (!suspended) {
          ioBuf->writeBoolAsInt(false);
        } else {
          CONTEXT context;
          context.ContextFlags = CONTEXT_FULL | CONTEXT_DEBUG_REGISTERS;
          if (GetThreadContext(msg.handleArg.handle, &context)) {
            ioBuf->writeBoolAsInt(true);
            // EAX, EBX, ECX, EDX, ESI, EDI, EBP, ESP, EIP, DS, ES, FS, GS,
            // CS, SS, EFLAGS, DR0, DR1, DR2, DR3, DR6, DR7
            // See README-commands.txt
            ioBuf->writeSpace(); ioBuf->writeAddress((void*) context.Eax);
            ioBuf->writeSpace(); ioBuf->writeAddress((void*) context.Ebx);
            ioBuf->writeSpace(); ioBuf->writeAddress((void*) context.Ecx);
            ioBuf->writeSpace(); ioBuf->writeAddress((void*) context.Edx);
            ioBuf->writeSpace(); ioBuf->writeAddress((void*) context.Esi);
            ioBuf->writeSpace(); ioBuf->writeAddress((void*) context.Edi);
            ioBuf->writeSpace(); ioBuf->writeAddress((void*) context.Ebp);
            ioBuf->writeSpace(); ioBuf->writeAddress((void*) context.Esp);
            ioBuf->writeSpace(); ioBuf->writeAddress((void*) context.Eip);
            ioBuf->writeSpace(); ioBuf->writeAddress((void*) context.SegDs);
            ioBuf->writeSpace(); ioBuf->writeAddress((void*) context.SegEs);
            ioBuf->writeSpace(); ioBuf->writeAddress((void*) context.SegFs);
            ioBuf->writeSpace(); ioBuf->writeAddress((void*) context.SegGs);
            ioBuf->writeSpace(); ioBuf->writeAddress((void*) context.SegCs);
            ioBuf->writeSpace(); ioBuf->writeAddress((void*) context.SegSs);
            ioBuf->writeSpace(); ioBuf->writeAddress((void*) context.EFlags);
            ioBuf->writeSpace(); ioBuf->writeAddress((void*) context.Dr0);
            ioBuf->writeSpace(); ioBuf->writeAddress((void*) context.Dr1);
            ioBuf->writeSpace(); ioBuf->writeAddress((void*) context.Dr2);
            ioBuf->writeSpace(); ioBuf->writeAddress((void*) context.Dr3);
            ioBuf->writeSpace(); ioBuf->writeAddress((void*) context.Dr6);
            ioBuf->writeSpace(); ioBuf->writeAddress((void*) context.Dr7);
          } else {
            ioBuf->writeBoolAsInt(false);
          }
        }
        ioBuf->writeEOL();
        ioBuf->flush();
        break;
      }

    case Message::SETCONTEXT:
      {
        if (!suspended) {
          ioBuf->writeBoolAsInt(false);
        } else {
          CONTEXT context;
          context.ContextFlags = CONTEXT_FULL | CONTEXT_DEBUG_REGISTERS;
          context.Eax    = msg.setContextArg.Eax;
          context.Ebx    = msg.setContextArg.Ebx;
          context.Ecx    = msg.setContextArg.Ecx;
          context.Edx    = msg.setContextArg.Edx;
          context.Esi    = msg.setContextArg.Esi;
          context.Edi    = msg.setContextArg.Edi;
          context.Ebp    = msg.setContextArg.Ebp;
          context.Esp    = msg.setContextArg.Esp;
          context.Eip    = msg.setContextArg.Eip;
          context.SegDs  = msg.setContextArg.Ds;
          context.SegEs  = msg.setContextArg.Es;
          context.SegFs  = msg.setContextArg.Fs;
          context.SegGs  = msg.setContextArg.Gs;
          context.SegCs  = msg.setContextArg.Cs;
          context.SegSs  = msg.setContextArg.Ss;
          context.EFlags = msg.setContextArg.EFlags;
          context.Dr0    = msg.setContextArg.Dr0;
          context.Dr1    = msg.setContextArg.Dr1;
          context.Dr2    = msg.setContextArg.Dr2;
          context.Dr3    = msg.setContextArg.Dr3;
          context.Dr6    = msg.setContextArg.Dr6;
          context.Dr7    = msg.setContextArg.Dr7;
          if (SetThreadContext(msg.setContextArg.handle, &context)) {
            ioBuf->writeBoolAsInt(true);
          } else {
            ioBuf->writeBoolAsInt(false);
          }
        }
        ioBuf->writeEOL();
        ioBuf->flush();
        break;
      }

    case Message::SELECTORENTRY:
      {
        LDT_ENTRY entry;

        if (GetThreadSelectorEntry(msg.selectorArg.handle,
                                   msg.selectorArg.selector,
                                   &entry)) {
          ioBuf->writeBoolAsInt(true);
          ioBuf->writeSpace(); ioBuf->writeAddress((void*) entry.LimitLow);
          ioBuf->writeSpace(); ioBuf->writeAddress((void*) entry.BaseLow);
          ioBuf->writeSpace(); ioBuf->writeAddress((void*) entry.HighWord.Bytes.BaseMid);
          ioBuf->writeSpace(); ioBuf->writeAddress((void*) entry.HighWord.Bytes.Flags1);
          ioBuf->writeSpace(); ioBuf->writeAddress((void*) entry.HighWord.Bytes.Flags2);
          ioBuf->writeSpace(); ioBuf->writeAddress((void*) entry.HighWord.Bytes.BaseHi);
        } else {
          ioBuf->writeBoolAsInt(false);
        }

        ioBuf->writeEOL();
        ioBuf->flush();
        break;
      }

    case Message::SUSPEND:
      suspend();
      break;

    case Message::RESUME:
      resume();
      break;

    case Message::POLLEVENT:
      eventLock->lock();
      if (curDebugEvent == NULL) {
        ioBuf->writeBoolAsInt(false);
      } else {
        ioBuf->writeBoolAsInt(true);
        ioBuf->writeSpace();
        threads.lock();
        ioBuf->writeAddress((void*) threads.threadIDToHandle(curDebugEvent->dwThreadId));
        threads.unlock();
        ioBuf->writeSpace();
        ioBuf->writeUnsignedInt(curDebugEvent->dwDebugEventCode);
        // Figure out what else to write
        switch (curDebugEvent->dwDebugEventCode) {
        case LOAD_DLL_DEBUG_EVENT:
          ioBuf->writeSpace();
          ioBuf->writeAddress(curDebugEvent->u.LoadDll.lpBaseOfDll);
          break;

        case UNLOAD_DLL_DEBUG_EVENT:
          ioBuf->writeSpace();
          ioBuf->writeAddress(curDebugEvent->u.UnloadDll.lpBaseOfDll);
          break;

        case EXCEPTION_DEBUG_EVENT:
          {
            DWORD code = curDebugEvent->u.Exception.ExceptionRecord.ExceptionCode;
            ioBuf->writeSpace();
            ioBuf->writeUnsignedInt(code);
            ioBuf->writeSpace();
            ioBuf->writeAddress(curDebugEvent->u.Exception.ExceptionRecord.ExceptionAddress);
            switch (curDebugEvent->u.Exception.ExceptionRecord.ExceptionCode) {
            case EXCEPTION_ACCESS_VIOLATION:
              ioBuf->writeSpace();
              ioBuf->writeBoolAsInt(curDebugEvent->u.Exception.ExceptionRecord.ExceptionInformation[0] != 0);
              ioBuf->writeSpace();
              ioBuf->writeAddress((void*) curDebugEvent->u.Exception.ExceptionRecord.ExceptionInformation[1]);
              break;

            default:
              break;
            }
            break;
          }

        default:
          break;
        }
      }
      eventLock->unlock();
      ioBuf->writeEOL();
      ioBuf->flush();
      break;

    case Message::CONTINUEEVENT:
      eventLock->lock();
      if (curDebugEvent == NULL) {
        ioBuf->writeBoolAsInt(false);
      } else {
        curDebugEvent = NULL;
        passEventToClient = msg.boolArg.val;
        ioBuf->writeBoolAsInt(true);
        eventLock->notify();
      }
      eventLock->unlock();
      ioBuf->writeEOL();
      ioBuf->flush();
      break;
    }
  }

  endProcess();

  // NOT REACHED
  return 0;
}
