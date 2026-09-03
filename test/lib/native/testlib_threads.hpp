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
#include <memory>

#ifdef _WIN32
#include <windows.h>
#else
#include <unistd.h>
#include <pthread.h>
#endif

extern "C" {
    typedef void(*PROCEDURE)(void*);
}

static void fatal(const char* message, int code) {
    fputs(message, stderr);
    // exit the test with a non-zero exit code to avoid accidental false positives
    exit(code);
}

class TestThread {
private:
    using proc_t = PROCEDURE;
#ifdef _WIN32
    using thread_t = HANDLE;
#else
    using thread_t = pthread_t;
#endif
    thread_t _thread;
    struct CallbackData {
        proc_t _proc;
        void* _context;
        CallbackData(proc_t proc, void* context)
                : _proc(proc), _context(context) {}
    };
    // Keep the data the callback needs out-of-line
    // so that if this TestThread object is copied,
    // the pointer remains valid
    std::shared_ptr<CallbackData> _data;

    TestThread(proc_t proc, void* context)
            : _data(std::make_shared<CallbackData>(proc, context)) {
#ifdef _WIN32
        _thread = CreateThread(nullptr, 0, TestThread::procedure, _data.get(), 0, nullptr);
        if (_thread == nullptr) {
            fatal("failed to create thread", GetLastError());
        }
#else
        pthread_attr_t attr;
        pthread_attr_init(&attr);
        size_t stack_size = 0x100000;
        pthread_attr_setstacksize(&attr, stack_size);
        int result = pthread_create(&_thread, &attr, TestThread::procedure, _data.get());
        if (result != 0) {
            fatal("failed to create thread", result);
        }
        pthread_attr_destroy(&attr);
#endif
    }
public:
    TestThread() { }

    static TestThread start(proc_t proc, void* context) {
        return TestThread(proc, context);
    }

    void join() const {
        if (_data.get() == nullptr) {
            fatal("Joining TestThread that is not initialized", 1);
        }
#ifdef _WIN32
        if (WaitForSingleObject(_thread, INFINITE) != WAIT_OBJECT_0) {
            // Should be WAIT_FAILED, since this is not a mutex, and
            // we set no timeout.
            fatal("failed to join thread", GetLastError());
        }
#else
        int result = pthread_join(_thread, nullptr);
        if (result != 0) {
            fatal("failed to join thread", result);
        }
#endif
    }

private:

    // Adapt from the callback type the OS API expects to
    // our OS-independent PROCEDURE type.
    static
    #ifdef _WIN32
    DWORD WINAPI procedure(_In_ LPVOID ctxt) {
    #else
    void* procedure(void* ctxt) {
    #endif
        CallbackData* data = (CallbackData*)ctxt;
        data->_proc(data->_context);
        return 0;
    }
};

extern "C" {

// Run 'proc' in a newly started thread, passing 'context' to it
// as an argument, and then join that thread.
void run_in_new_thread_and_join(PROCEDURE proc, void* context) {
    TestThread thread = TestThread::start(proc, context);
    thread.join();
}

}

#endif // TEST_LIB_NATIVE_THREAD_HPP
