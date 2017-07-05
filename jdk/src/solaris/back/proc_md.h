/*
 * Copyright 2003-2005 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/* Posix threads (Solaris and Linux) */

#include <unistd.h>
#include <sys/wait.h>
#include <time.h>
#include <sys/time.h>
#include <pthread.h>

#define MUTEX_T pthread_mutex_t
#define MUTEX_INIT PTHREAD_MUTEX_INITIALIZER
#define MUTEX_LOCK(x)   (void)pthread_mutex_lock(&x)
#define MUTEX_UNLOCK(x) (void)pthread_mutex_unlock(&x)
#define GET_THREAD_ID() pthread_self()
#define THREAD_T pthread_t
#define PID_T pid_t
#define GETPID() getpid()
#define GETMILLSECS(millisecs)                                  \
        {                                                       \
                struct timeval tval;                            \
                (void)gettimeofday(&tval,NULL);                 \
                millisecs = ((int)(tval.tv_usec/1000));         \
        }
