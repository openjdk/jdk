/*
 * Copyright (c) 1998, 2000, Oracle and/or its affiliates. All rights reserved.
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

#ifndef _JAVASOFT_PORTING_H_
#define _JAVASOFT_PORTING_H_

#ifndef USE_PTHREADS

#include <thread.h>
#include <sys/lwp.h>
#include <synch.h>

#else  /* USE_PTHREADS */

#include <pthread.h>

/* There is a handshake between a newly created thread and its creator
 * at thread startup because the creator thread needs to suspend the
 * new thread.  Currently there are two ways to do this -- with
 * semaphores and with mutexes.  The semaphore based implementation is
 * cleaner and hence is the default.  We wish the mutex based one will
 * go away, but turns out the implementation of semaphores on
 * Linux/ppc etc is flaky, so the mutex based solution lives for now.
 */
#ifndef USE_MUTEX_HANDSHAKE
#include <semaphore.h>
#endif

#undef BOUND_THREADS

#define thread_t                pthread_t

#define mutex_t                 pthread_mutex_t
#define mutex_lock              pthread_mutex_lock
#define mutex_trylock           pthread_mutex_trylock
#define mutex_unlock            pthread_mutex_unlock
#define mutex_destroy           pthread_mutex_destroy

#define cond_t                  pthread_cond_t
#define cond_destroy            pthread_cond_destroy
#define cond_wait               pthread_cond_wait
#define cond_timedwait          pthread_cond_timedwait
#define cond_signal             pthread_cond_signal
#define cond_broadcast          pthread_cond_broadcast

#define thread_key_t            pthread_key_t
#define thr_setspecific         pthread_setspecific
#define thr_keycreate           pthread_key_create

#define thr_sigsetmask          pthread_sigmask
#define thr_self                pthread_self
#define thr_yield               sched_yield
#define thr_kill                pthread_kill
#define thr_exit                pthread_exit
#ifdef __linux__
void intrHandler(void*);
#endif
#endif /* USE_PTHREADS  */

#endif /* !_JAVASOFT_PORTING_H_ */
