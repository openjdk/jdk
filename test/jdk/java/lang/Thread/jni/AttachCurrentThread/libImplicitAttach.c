/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

#include <pthread.h>
#include <stdio.h>

#include "export.h"

#define STACK_SIZE 0x100000

/**
 * Creates n threads to execute the given function.
 */
EXPORT void start_threads(int n, void *(*f)(void *)) {
    pthread_t tid;
    pthread_attr_t attr;
    int i;

    pthread_attr_init(&attr);
    pthread_attr_setstacksize(&attr, STACK_SIZE);
    for (i = 0; i < n ; i++) {
        int res = pthread_create(&tid, &attr, f, NULL);
        if (res != 0) {
            fprintf(stderr, "pthread_create failed: %d\n", res);
        }
    }
}
