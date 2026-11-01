/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 */

#ifndef CHILDPROC_ERRORCODES_H
#define CHILDPROC_ERRORCODES_H

#include <sys/types.h>
#include <stdbool.h>

typedef struct errcode_t_ {
    unsigned step : 8;
    unsigned hint : 16;
    unsigned errno_ : 8;
} errcode_t;

/* Helper macros for printing an errcode_t */
#define ERRCODE_FORMAT "(%u-%u-%u)"
#define ERRCODE_FORMAT_ARGS(errcode) errcode.step, errcode.hint, errcode.errno_


/* Builds up an error code.
 * Note:
 * - hint will be capped at 2^16
 * - both step and errno_ must fit into 8 bits. */
void buildErrorCode(errcode_t* errcode, int step, int hint, int errno_);

/* Sends an error code down a pipe. Returns true if sent successfully. */
bool sendErrorCode(int fd, errcode_t errcode);

/* Build an exit code for an errcode (used as child process exit code
 * in addition to the errcode being sent to parent). */
int exitCodeFromErrorCode(errcode_t errcode);

/* Sends alive ping down a pipe. Returns true if sent successfully. */
bool sendAlivePing(int fd);

#define ESTEP_UNKNOWN               0

/* not an error code, but an "I am alive" ping from the child.
 * hint is child pid, errno is 0. */
#define ESTEP_CHILD_ALIVE           255

/* JspawnHelper */
#define ESTEP_JSPAWN_ARG_ERROR                  1
#define ESTEP_JSPAWN_VERSION_ERROR              2

/* Checking file descriptor setup
 * hint is the (16-bit-capped) fd number */
#define ESTEP_JSPAWN_INVALID_FD                 3
#define ESTEP_JSPAWN_NOT_A_PIPE                 4

/* Allocation fail in jspawnhelper.
 * hint is the (16-bit-capped) fail size */
#define ESTEP_JSPAWN_ALLOC_FAILED               5

/* Receiving Childstuff from parent, communication error.
 * hint is the substep. */
#define ESTEP_JSPAWN_RCV_CHILDSTUFF_COMM_FAIL   6

/* Expand if needed ... */

/* childproc() */

/* Failed to send aliveness ping
 * hint is the (16-bit-capped) fd. */
#define ESTEP_SENDALIVE_FAIL                    10

/* Failed to close a pipe in fork mode
 * hint is the (16-bit-capped) fd. */
#define ESTEP_PIPECLOSE_FAIL                    11

/* Failed to dup2 a file descriptor in fork mode.
 * hint is the (16-bit-capped) fd_to (!) */
#define ESTEP_DUP2_STDIN_FAIL                   13
#define ESTEP_DUP2_STDOUT_FAIL                  14
#define ESTEP_DUP2_STDERR_REDIRECT_FAIL         15
#define ESTEP_DUP2_STDERR_FAIL                  16
#define ESTEP_DUP2_FAILPIPE_FAIL                17

/* Failed to mark a file descriptor as CLOEXEC
 * hint is the (16-bit-capped) fd */
#define ESTEP_CLOEXEC_FAIL                      18

/* Failed to chdir into the target working directory */
#define ESTEP_CHDIR_FAIL                        19

/* Failed to change signal disposition for SIGPIPE to default */
#define ESTEP_SET_SIGPIPE                       20

/* Expand if needed ... */

/* All modes: exec() failed */
#define ESTEP_EXEC_FAIL                         30

#endif /* CHILDPROC_MD_H */
