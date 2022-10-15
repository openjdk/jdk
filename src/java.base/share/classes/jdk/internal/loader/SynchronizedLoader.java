/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.loader;

import java.util.concurrent.ConcurrentHashMap;
import java.lang.ClassLoader;
import java.security.AccessController;
import java.security.PrivilegedAction;

/**
 * Defines a version of loadClass that handles synchronization on the ClassLoader object
 * for applications that release the ClassLoader lock but still expect the multiple
 * threads to allow the first thread to successfully load the class and the other threads
 * to wait then also succeed.
 * This is for compatibility with long standing VM behavior, and may be deprecated in future releases.
 **/
public class SynchronizedLoader {

    private static class NameLoaderRef {
        private String name;
        private ClassLoader loader;
        public NameLoaderRef(String name, ClassLoader loader) {
            this.name = name;
            this.loader = loader;
        }
        @Override
        public int hashCode() { return loader.hashCode() ^ name.hashCode(); }

        public boolean equals(NameLoaderRef key) {
            return key.name.equals(this.name) && key.loader == this.loader;
        }
        @Override
        public boolean equals(Object obj) {
            assert (obj instanceof NameLoaderRef);
            return equals((NameLoaderRef) obj);
        }

        public ClassLoader loader() { return loader; }
    }

    // Synchronization for class loading when the current class loader is NOT
    // parallel capable.  The non-parallel capable class loader locks
    // the ClassLoader object. This maps a class name to the thread that is currently
    // loading the class in case the ClassLoader lock is broken.
    private static final ConcurrentHashMap<NameLoaderRef, Thread> threadLoadingClassMap
        = new ConcurrentHashMap<>();

    // The threadLoadingClassMap is a concurrent hashtable that keeps track of the threads
    // that are currently loading each class by this class loader.  The class name is mapped to
    // the thread that is first seen loading that class, then removed when loading is complete
    // for that class.
    private static final Thread threadLoadingClass(NameLoaderRef ref, Thread thread) {
        return threadLoadingClassMap.putIfAbsent(ref, thread);
    }

    private static final void removeThreadLoadingClass(NameLoaderRef ref) {
        threadLoadingClassMap.remove(ref);
        // Notify threads waiting on the class loader lock
        // that this class has been loaded or failed.
        ref.loader().notifyAll();
    }

    // We only get here if the application has released the
    // class loader lock (wait) in the middle of loading a
    // superclass/superinterface for this class, and now
    // this thread is also trying to load this class.
    // To minimize surprises, this thread waits while the first thread
    // that started to load a class completes the loading or fails.
    @SuppressWarnings("removal")
    private static Thread waitForThreadLoadingClass(NameLoaderRef ref, Thread currentThread) {
        Thread thread;
        boolean interrupted = false;
        while ((thread = threadLoadingClass(ref, currentThread)) != null) {
            try {
                ref.loader().wait();
            } catch (InterruptedException e) {
                interrupted = true;
                // keep waiting, must be uninterruptible
            }
        }
        if (interrupted) {
            // reassert the interrupt status before exiting.
            AccessController.doPrivileged(new PrivilegedAction<Void>() {
                public Void run() {
                    currentThread.interrupt();
                    return null;
                }
            });
        }
        return thread;
    }

    // This method is called by VM to load a class for a non-parallel capable ClassLoader.
    // It checks if another thread has released the class loader lock (wait) while loading a class
    // by this name, and will notify the other thread to complete loading the class.
    // This method provides compatibility for class loaders that release the class loader lock,
    // otherwise they throw LinkageError: duplicate class definition.
    private static final Class<?> loadClass(ClassLoader loader, String name)
        throws ClassNotFoundException
    {
        // The JVM calls ClassLoader.loadClass directly for parallel-capable class loaders
        assert (!loader.isRegisteredAsParallelCapable());
        Thread currentThread = Thread.currentThread();
        NameLoaderRef ref = new NameLoaderRef(name, loader);
        synchronized(loader) {
            Thread thread = threadLoadingClass(ref, currentThread);
            // Another thread is loading the class.
            if (thread != null && thread != currentThread) {
                // notify loading thread once
                loader.notifyAll();
                thread = waitForThreadLoadingClass(ref, currentThread);
            }
            // Now load class if no other thread is loading it.
            if (thread == null) {
                Class<?> loadedClass = null;
                try {
                    loadedClass = loader.loadClass(name);
                } finally {
                    removeThreadLoadingClass(ref);
                }
                return loadedClass;
            } else {
                // A class circularity error is detected while loading this class
                assert(thread == currentThread);
                removeThreadLoadingClass(ref);
                throw new ClassCircularityError(name);
            }
        }
    }
}
