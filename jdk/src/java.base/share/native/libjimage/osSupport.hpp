/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

#ifndef LIBJIMAGE_OSSUPPORT_HPP
#define LIBJIMAGE_OSSUPPORT_HPP

#ifdef WIN32
#include <Windows.h>
#else
#include <pthread.h>
#endif

class osSupport {
public:
    /**
     * Open a regular file read-only.
     * Return the file descriptor.
     */
    static jint openReadOnly(const char *path);

    /**
     * Close a file descriptor.
     */
    static jint close(jint fd);

    /**
     * Return the size of a regular file.
     */
    static jlong size(const char *path);

    /**
     * Read nBytes at offset into a buffer.
     */
    static jlong read(jint fd, char *buf, jlong nBytes, jlong offset);

    /**
     * Map nBytes at offset into memory and return the address.
     * The system chooses the address.
     */
    static void* map_memory(jint fd, const char *filename, size_t file_offset, size_t bytes);

    /**
     * Unmap nBytes of memory at address.
     */
    static int unmap_memory(void* addr, size_t bytes);
};

/**
 * A CriticalSection to protect a small section of code.
 */
class SimpleCriticalSection {
    friend class SimpleCriticalSectionLock;
private:
    void enter();
    void exit();
public:
    SimpleCriticalSection();
    //~SimpleCriticalSection(); // Cretes a dependency on Solaris on a C++ exit registration

private:
#ifdef WIN32
    CRITICAL_SECTION critical_section;
#else
    pthread_mutex_t mutex;
#endif // WIN32
};

/**
 * SimpleCriticalSectionLock instance.
 * The constructor locks a SimpleCriticalSection and the
 * destructor does the unlock.
 */
class SimpleCriticalSectionLock {
private:
    SimpleCriticalSection *lock;
public:

    SimpleCriticalSectionLock(SimpleCriticalSection *cslock) {
        this->lock = cslock;
        lock->enter();
    }

    ~SimpleCriticalSectionLock() {
        lock->exit();
    }
};

#endif  // LIBJIMAGE_OSSUPPORT_HPP
