/*
 * Copyright (c) 1998, 2010, Oracle and/or its affiliates. All rights reserved.
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
#include "prims/jvm.h"
#include "runtime/interfaceSupport.hpp"
#include "runtime/osThread.hpp"

#include <signal.h>

// sun.misc.Signal ///////////////////////////////////////////////////////////
// Signal code is mostly copied from classic vm, signals_md.c   1.4 98/08/23
/*
 * This function is included primarily as a debugging aid. If Java is
 * running in a console window, then pressing <CTRL-\\> will cause
 * the current state of all active threads and monitors to be written
 * to the console window.
 */

JVM_ENTRY_NO_ENV(void*, JVM_RegisterSignal(jint sig, void* handler))
  // Copied from classic vm
  // signals_md.c       1.4 98/08/23
  void* newHandler = handler == (void *)2
                   ? os::user_handler()
                   : handler;
  switch (sig) {
    /* The following are already used by the VM. */
    case SIGFPE:
    case SIGILL:
    case SIGSEGV:

    /* The following signal is used by the VM to dump thread stacks unless
       ReduceSignalUsage is set, in which case the user is allowed to set
       his own _native_ handler for this signal; thus, in either case,
       we do not allow JVM_RegisterSignal to change the handler. */
    case BREAK_SIGNAL:
      return (void *)-1;

    /* The following signals are used for Shutdown Hooks support. However, if
       ReduceSignalUsage (-Xrs) is set, Shutdown Hooks must be invoked via
       System.exit(), Java is not allowed to use these signals, and the the
       user is allowed to set his own _native_ handler for these signals and
       invoke System.exit() as needed. Terminator.setup() is avoiding
       registration of these signals when -Xrs is present.
       - If the HUP signal is ignored (from the nohup) command, then Java
         is not allowed to use this signal.
     */
    case SHUTDOWN1_SIGNAL:
    case SHUTDOWN2_SIGNAL:
    case SHUTDOWN3_SIGNAL:
      if (ReduceSignalUsage) return (void*)-1;
      if (os::Solaris::is_sig_ignored(sig)) return (void*)1;
  }

  /* Check parameterized signals. Don't allow sharing of our interrupt signal */
  if (sig == os::Solaris::SIGinterrupt()) {
      return (void *)-1;
  }

  void* oldHandler = os::signal(sig, newHandler);
  if (oldHandler == os::user_handler()) {
      return (void *)2;
  } else {
      return oldHandler;
  }
JVM_END


JVM_ENTRY_NO_ENV(jboolean, JVM_RaiseSignal(jint sig))
  if (ReduceSignalUsage) {
    // do not allow SHUTDOWN1_SIGNAL,SHUTDOWN2_SIGNAL,SHUTDOWN3_SIGNAL,
    // BREAK_SIGNAL to be raised when ReduceSignalUsage is set, since
    // no handler for them is actually registered in JVM or via
    // JVM_RegisterSignal.
    if (sig == SHUTDOWN1_SIGNAL || sig == SHUTDOWN2_SIGNAL ||
        sig == SHUTDOWN3_SIGNAL || sig == BREAK_SIGNAL) {
      return JNI_FALSE;
    }
  }
  else if ((sig == SHUTDOWN1_SIGNAL || sig == SHUTDOWN2_SIGNAL ||
            sig == SHUTDOWN3_SIGNAL) && os::Solaris::is_sig_ignored(sig)) {
    // do not allow SHUTDOWN1_SIGNAL to be raised when SHUTDOWN1_SIGNAL
    // is ignored, since no handler for them is actually registered in JVM
    // or via JVM_RegisterSignal.
    // This also applies for SHUTDOWN2_SIGNAL and SHUTDOWN3_SIGNAL
    return JNI_FALSE;
  }

  os::signal_raise(sig);
  return JNI_TRUE;
JVM_END


/*
  All the defined signal names for Solaris are defined by str2sig().

  NOTE that not all of these names are accepted by our Java implementation

  Via an existing claim by the VM, sigaction restrictions, or
  the "rules of Unix" some of these names will be rejected at runtime.
  For example the VM sets up to handle USR1, sigaction returns EINVAL for
  CANCEL, and Solaris simply doesn't allow catching of KILL.

  Here are the names currently accepted by a user of sun.misc.Signal with
  1.4.1 (ignoring potential interaction with use of chaining, etc):

      HUP, INT, TRAP, IOT, ABRT, EMT, BUS, SYS, PIPE, ALRM, TERM, USR2,
      CLD, CHLD, PWR, WINCH, URG, POLL, IO, TSTP, CONT, TTIN, TTOU, VTALRM,
      PROF, XCPU, XFSZ, FREEZE, THAW, LOST
*/

JVM_ENTRY_NO_ENV(jint, JVM_FindSignal(const char *name))

  int sig;

  /* return the named signal's number */

  if(str2sig(name, &sig))
    return -1;
  else
    return sig;

JVM_END


//Reconciliation History
// 1.4 98/10/07 13:39:41 jvm_win32.cpp
// 1.6 99/06/22 16:39:00 jvm_win32.cpp
//End
