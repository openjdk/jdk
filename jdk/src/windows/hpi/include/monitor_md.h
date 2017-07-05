/*
 * Copyright 1994-1998 Sun Microsystems, Inc.  All Rights Reserved.
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

/*
 * Win32 implementation of Java monitors
 */

#ifndef _JAVASOFT_WIN32_MONITOR_MD_H_
#define _JAVASOFT_WIN32_MONITOR_MD_H_

#include <windows.h>

#include "threads_md.h"
#include "mutex_md.h"

#define SYS_MID_NULL ((sys_mon_t *) 0)

typedef struct sys_mon {
    long            atomic_count;   /* Variable for atomic compare swap */
    HANDLE          semaphore;      /* Semaphore used for the contention */
    sys_thread_t   *monitor_owner;  /* Current owner of this monitor */
    long            entry_count;    /* Recursion depth */
    sys_thread_t   *monitor_waiter; /* Monitor waiting queue head */
    long            waiter_count;   /* For debugging purpose */
} sys_mon_t;

#endif /* !_JAVASOFT_WIN32_MONITOR_MD_H_ */
