/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.hotspot;

import org.graalvm.compiler.debug.GraalError;

import jdk.vm.ci.meta.JavaConstant;

/**
 * A context for scoping the lifetime of foreign objects.
 *
 * The need for this mechanism is best explained with an example. When folding a GETFIELD bytecode
 * denoting a {@code final static} non-primitive field, libgraal can create a {@link JavaConstant}
 * wrapping a handle to the field's value in the HotSpot heap. This handle must be released before
 * HotSpot can reclaim the object it references. Performing a compilation in the scope of a
 * {@linkplain HotSpotGraalServices#openLocalCompilationContext local} context ensures the handle is
 * released once the compilation completes, allowing the HotSpot GC to subsequently reclaim the
 * HotSpot object. When libgraal creates data structures that outlive a single compilation and may
 * contain foreign object references (e.g. snippet graphs), it must enter the
 * {@linkplain HotSpotGraalServices#enterGlobalCompilationContext global} context. Foreign object
 * handles created in the global context are only released once their {@link JavaConstant} wrappers
 * are reclaimed by the libgraal GC.
 *
 * {@link CompilationContext}s have no impact on {@link JavaConstant}s that do not encapsulate a
 * foreign object reference.
 *
 * The object returned by {@link HotSpotGraalServices#enterGlobalCompilationContext} or
 * {@link HotSpotGraalServices#openLocalCompilationContext} should be used in a try-with-resources
 * statement. Failure to close a context will almost certainly result in foreign objects being
 * leaked.
 */
public class CompilationContext implements AutoCloseable {
    private final AutoCloseable impl;

    CompilationContext(AutoCloseable impl) {
        this.impl = impl;
    }

    @Override
    public void close() {
        try {
            impl.close();
        } catch (Exception e) {
            GraalError.shouldNotReachHere(e);
        }
    }
}
