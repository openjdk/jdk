/*
 * Copyright (c) 1994, 1998, Oracle and/or its affiliates. All rights reserved.
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

/*
 * Interface to mutex HPI implementation for Solaris
 */

#ifndef _JAVASOFT_MUTEX_MD_H_
#define _JAVASOFT_MUTEX_MD_H_

#include "porting.h"

/*
 * Generally, we would typedef mutex_t to be whatever the system
 * supplies.  But Solaris gives us mutex_t directly.
 */

#ifdef USE_PTHREADS
#define mutexInit(m) pthread_mutex_init(m, 0)
#else
#define mutexInit(m) mutex_init(m, USYNC_THREAD, 0)
#endif
#define mutexDestroy(m) mutex_destroy(m)
#define mutexLock(m) mutex_lock(m)
#define mutexUnlock(m) mutex_unlock(m)
bool_t mutexLocked(mutex_t *);

#endif /* !_JAVASOFT_MUTEX_MD_H_ */
