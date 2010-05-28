/*
 * Copyright (c) 2008, Oracle and/or its affiliates. All rights reserved.
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

package sun.tracing.dtrace;

import java.lang.ref.WeakReference;
import java.lang.ref.ReferenceQueue;
import java.security.Permission;
import java.util.HashSet;

class Activation {
    private SystemResource resource;
    private int referenceCount;

    Activation(String moduleName, DTraceProvider[] providers) {
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            Permission perm =
                new RuntimePermission("com.sun.tracing.dtrace.createProvider");
            security.checkPermission(perm);
        }
        referenceCount = providers.length;
        for (DTraceProvider p : providers) {
            p.setActivation(this);
        }
        resource = new SystemResource(
            this, JVM.activate(moduleName, providers));
    }

    void disposeProvider(DTraceProvider p) {
        if (--referenceCount == 0) {
            resource.dispose();
        }
    }
}

/**
 * The native resource part of an Activation.
 *
 * This holds the native handle.
 *
 * If the user loses a reference to a set of Providers without disposing them,
 * and GC determines the Activation is unreachable, then the next
 * activation or flush call will automatically dispose the unreachable objects
 *
 * The SystemResource instances are creating during activation, and
 * unattached during disposal.  When created, they always have a
 * strong reference to them via the {@code resources} static member.  Explicit
 * {@code dispose} calls will unregister the native resource and remove
 * references to the SystemResource object.  Absent an explicit dispose,
 * when their associated Activation object becomes garbage, the SystemResource
 * object will be enqueued on the reference queue and disposed at the
 * next call to {@code flush}.
 */
class SystemResource extends WeakReference<Activation> {

    private long handle;

    private static ReferenceQueue<Activation> referenceQueue =
        referenceQueue = new ReferenceQueue<Activation>();
    static HashSet<SystemResource> resources = new HashSet<SystemResource>();

    SystemResource(Activation activation, long handle) {
        super(activation, referenceQueue);
        this.handle = handle;
        flush();
        resources.add(this);
    }

    void dispose() {
        JVM.dispose(handle);
        resources.remove(this);
        handle = 0;
    }

    static void flush() {
        SystemResource resource = null;
        while ((resource = (SystemResource)referenceQueue.poll()) != null) {
            if (resource.handle != 0) {
                resource.dispose();
            }
        }
    }
}

