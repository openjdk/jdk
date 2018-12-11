/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.debug;

import java.util.Iterator;
import java.util.List;

import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.serviceprovider.GraalServices;

/**
 * Factory for creating {@link DebugHandler}s.
 */
public interface DebugHandlersFactory {

    /**
     * Creates {@link DebugHandler}s based on {@code options}.
     *
     * @param options options to control type and name of the channel
     * @return list of debug handers that have been created
     */
    List<DebugHandler> createHandlers(OptionValues options);

    /**
     * Loads {@link DebugHandlersFactory}s on demand via {@link GraalServices#load(Class)}.
     */
    Iterable<DebugHandlersFactory> LOADER = new Iterable<DebugHandlersFactory>() {
        @Override
        public Iterator<DebugHandlersFactory> iterator() {
            return GraalServices.load(DebugHandlersFactory.class).iterator();
        }
    };
}
