/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026, IBM Corp.
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
 */

#ifndef TESTLIB_THREAD_BARRIERS_H
#define TESTLIB_THREAD_BARRIERS_H

/* MacOS does not have pthread barriers; implement a fallback using condvars. */

#ifndef _WIN32
#if !defined _POSIX_BARRIERS || _POSIX_BARRIERS < 0

#include <pthread.h>

#define PTHREAD_BARRIER_SERIAL_THREAD       1

#define pthread_barrier_t                       barr_t
#define pthread_barrier_init(barr, attr, need)  barr_init(barr, attr, need)
#define pthread_barrier_destroy(barr)           barr_destroy(barr)
#define pthread_barrier_wait(barr)              barr_wait(barr)

typedef struct {
    pthread_mutex_t mutex;
    pthread_cond_t cond;
    int have, need, trigger_count;
} barr_t;

int barr_init(barr_t* b, void* ignored, int need) {
    b->have = b->trigger_count = 0;
    b->need = need;
    pthread_mutex_init(&b->mutex, NULL);
    pthread_cond_init(&b->cond, NULL);
    return 0;
}

int barr_destroy(barr_t* b) {
    pthread_mutex_destroy(&b->mutex);
    pthread_cond_destroy(&b->cond);
    return 0;
}

int barr_wait(barr_t* b) {
    pthread_mutex_lock(&b->mutex);
    int my_trigger_count = b->trigger_count;
    b->have++;
    if (b->have == b->need) {
        b->have = 0;
        b->trigger_count++;
        pthread_cond_broadcast(&b->cond);
        pthread_mutex_unlock(&b->mutex);
        return PTHREAD_BARRIER_SERIAL_THREAD;
    }
    while (my_trigger_count == b->trigger_count) { // no spurious wakeups
        pthread_cond_wait(&b->cond, &b->mutex);
    }
    pthread_mutex_unlock(&b->mutex);
    return 0;
}

#endif // !_POSIX_BARRIERS
#endif // !_WIN32

#endif // TESTLIB_THREAD_BARRIERS_H
