/*
 * Copyright (c) 1998, 2015, Oracle and/or its affiliates. All rights reserved.
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

#ifndef OS_SOLARIS_VM_JVM_SOLARIS_H
#define OS_SOLARIS_VM_JVM_SOLARIS_H

/*
// HotSpot integration note:
//
// This is derived from the JDK classic file:
// "$JDK/src/solaris/javavm/export/jvm_md.h":15 (ver. 1.10 98/04/22)
// All local includes have been commented out.
*/

#ifndef JVM_MD_H
#define JVM_MD_H

/*
 * This file is currently collecting system-specific dregs for the
 * JNI conversion, which should be sorted out later.
 */

#define __USE_LEGACY_PROTOTYPES__
#include <dirent.h>             /* For DIR */
#undef __USE_LEGACY_PROTOTYPES__
#include <sys/param.h>          /* For MAXPATHLEN */
#include <sys/socket.h>         /* For socklen_t */
#include <unistd.h>             /* For F_OK, R_OK, W_OK */
#include <sys/int_types.h>      /* for intptr_t types (64 Bit cleanliness) */

#define JNI_ONLOAD_SYMBOLS      {"JNI_OnLoad"}
#define JNI_ONUNLOAD_SYMBOLS    {"JNI_OnUnload"}
#define JVM_ONLOAD_SYMBOLS      {"JVM_OnLoad"}
#define AGENT_ONLOAD_SYMBOLS    {"Agent_OnLoad"}
#define AGENT_ONUNLOAD_SYMBOLS  {"Agent_OnUnload"}
#define AGENT_ONATTACH_SYMBOLS  {"Agent_OnAttach"}

#define JNI_LIB_PREFIX "lib"
#define JNI_LIB_SUFFIX ".so"

#define JVM_MAXPATHLEN MAXPATHLEN

#define JVM_R_OK    R_OK
#define JVM_W_OK    W_OK
#define JVM_X_OK    X_OK
#define JVM_F_OK    F_OK

/*
 * File I/O
 */

#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <errno.h>

/* O Flags */

#define JVM_O_RDONLY     O_RDONLY
#define JVM_O_WRONLY     O_WRONLY
#define JVM_O_RDWR       O_RDWR
#define JVM_O_O_APPEND   O_APPEND
#define JVM_O_EXCL       O_EXCL
#define JVM_O_CREAT      O_CREAT

/* Signal definitions */

#define BREAK_SIGNAL     SIGQUIT           /* Thread dumping support.    */
#define ASYNC_SIGNAL     SIGUSR2           /* Watcher & async err support. */
#define SHUTDOWN1_SIGNAL SIGHUP            /* Shutdown Hooks support.    */
#define SHUTDOWN2_SIGNAL SIGINT
#define SHUTDOWN3_SIGNAL SIGTERM
/* alternative signals used with -XX:+UseAltSigs (or for backward
   compatibility with 1.2, -Xusealtsigs) flag. Chosen to be
   unlikely to conflict with applications embedding the vm */
#define ALT_ASYNC_SIGNAL     (SIGRTMIN + SIGRTMAX)/2  /* alternate async signal */

/* With 1.4.1 libjsig added versioning: used in os_solaris.cpp and jsig.c */
#define JSIG_VERSION_1_4_1   0x30140100

#endif /* JVM_MD_H */

#endif // OS_SOLARIS_VM_JVM_SOLARIS_H
