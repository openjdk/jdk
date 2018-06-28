/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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

import jdk.vm.ci.hotspot.HotSpotVMConfigAccess;
import jdk.vm.ci.hotspot.HotSpotVMConfigStore;

/**
 * Used to access native configuration details for static compilation support.
 */
public class CompilerRuntimeHotSpotVMConfig extends HotSpotVMConfigAccess {

    public CompilerRuntimeHotSpotVMConfig(HotSpotVMConfigStore store) {
        super(store);
    }

    public final long resolveStringBySymbol = getAddress("CompilerRuntime::resolve_string_by_symbol");
    public final long resolveDynamicInvoke = getAddress("CompilerRuntime::resolve_dynamic_invoke");
    public final long resolveKlassBySymbol = getAddress("CompilerRuntime::resolve_klass_by_symbol");
    public final long resolveMethodBySymbolAndLoadCounters = getAddress("CompilerRuntime::resolve_method_by_symbol_and_load_counters");
    public final long initializeKlassBySymbol = getAddress("CompilerRuntime::initialize_klass_by_symbol");
    public final long invocationEvent = getAddress("CompilerRuntime::invocation_event");
    public final long backedgeEvent = getAddress("CompilerRuntime::backedge_event");
}
