/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.microbenchmarks.graal.util;

import org.graalvm.compiler.api.test.Graal;
import org.graalvm.compiler.core.target.Backend;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.runtime.RuntimeProvider;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

import jdk.vm.ci.meta.MetaAccessProvider;

/**
 * Read-only, thread-local state providing Graal runtime context. This has to be thread-local due to
 * requirements that {@link DebugContext} objects be single threaded in their usage.
 */
@State(Scope.Thread)
public class GraalState {

    public final OptionValues options;
    public final DebugContext debug;
    public final Backend backend;
    public final Providers providers;
    public final MetaAccessProvider metaAccess;

    public GraalState() {
        options = Graal.getRequiredCapability(OptionValues.class);
        debug = DebugContext.create(options, DebugHandlersFactory.LOADER);
        backend = Graal.getRequiredCapability(RuntimeProvider.class).getHostBackend();
        providers = backend.getProviders();
        metaAccess = providers.getMetaAccess();
    }
}
