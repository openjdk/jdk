/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "export.h"
#include "testlib_threads.hpp"

#include <stdbool.h>

static TestThread THREAD;
static volatile bool FLAG = false;

static void proc(void* ctxt) {
    void (*callback)(void) = (void (*)(void)) ctxt;
    callback();
    puts("[proc] waiting for flag...");
    while (!FLAG) {} // keep the thread alive until we can call join
    puts("[proc] done waiting for flag");
}

static void await_join() {
    puts("[await_join] joining...");
    FLAG = true;
    THREAD.join();
    puts("[await_join] done joining");
}

extern "C"
EXPORT void create_thread_and_register_atexit(void (*callback)(void)) {
    puts("[create_thread_and_register_atexit] creating native thread");
    THREAD = TestThread::start(proc, (void*) callback);
    atexit(&await_join);
}
