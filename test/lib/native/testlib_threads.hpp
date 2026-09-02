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

#ifndef TEST_LIB_NATIVE_THREAD_HPP
#define TEST_LIB_NATIVE_THREAD_HPP

// Header only library for using threads in tests

#include <stdlib.h>
#include <stdio.h>

#ifdef _WIN32
#include <windows.h>
#else
#include <unistd.h>
#include <pthread.h>
#endif

extern "C" {

typedef void(*PROCEDURE)(void*);

#ifdef _WIN32
typedef HANDLE THREAD;
#else
typedef pthread_t THREAD;
#endif

struct ThreadHelper {
    THREAD _thread;
    PROCEDURE proc;
    void* context;
};

static void fatal(const char* message, int code) {
    fputs(message, stderr);
    // exit the test with a non-zero exit code to avoid accidental false positives
    exit(code);
}

// Adapt from the callback type the OS API expects to
// our OS-independent PROCEDURE type.
#ifdef _WIN32
DWORD WINAPI procedure(_In_ LPVOID ctxt) {
#else
void* procedure(void* ctxt) {
#endif
    ThreadHelper* helper = (ThreadHelper*)ctxt;
    helper->proc(helper->context);
    return 0;
}

void create_thread(ThreadHelper* helper) {
#ifdef _WIN32
    helper->_thread = CreateThread(nullptr, 0, procedure, helper, 0, nullptr);
    if (helper->_thread == nullptr) {
        fatal("failed to create thread", GetLastError());
    }
#else
    pthread_attr_t attr;
    pthread_attr_init(&attr);
    size_t stack_size = 0x100000;
    pthread_attr_setstacksize(&attr, stack_size);
    int result = pthread_create(&helper->_thread, &attr, procedure, helper);
    if (result != 0) {
        fatal("failed to create thread", result);
    }
    pthread_attr_destroy(&attr);
#endif
}

void join_thread(ThreadHelper* helper) {
#ifdef _WIN32
    if (WaitForSingleObject(helper->_thread, INFINITE) != WAIT_OBJECT_0) {
        // Should be WAIT_FAILED, since this is not a mutex, and
        // we set no timeout.
        fatal("failed to join thread", GetLastError());
    }
#else
    int result = pthread_join(helper->_thread, nullptr);
    if (result != 0) {
        fatal("failed to join thread", result);
    }
#endif
}

// Run 'proc' in a newly started thread, passing 'context' to it
// as an argument, and then join that thread.
void run_in_new_thread_and_join(PROCEDURE proc, void* context) {
    struct ThreadHelper helper;
    helper.proc = proc;
    helper.context = context;

    create_thread(&helper);
    join_thread(&helper);
}

}

#endif // TEST_LIB_NATIVE_THREAD_HPP
