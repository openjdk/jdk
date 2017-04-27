/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.stubs;

import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.options.OptionKey;

//JaCoCo Exclude

/**
 * Options related to HotSpot Graal-generated stubs.
 *
 * Note: This must be a top level class to work around for
 * <a href="https://bugs.eclipse.org/bugs/show_bug.cgi?id=477597">Eclipse bug 477597</a>.
 */
public class StubOptions {
    // @formatter:off
    @Option(help = "Trace execution of stub used to handle an exception thrown by a callee.", type = OptionType.Debug)
    static final OptionKey<Boolean> TraceExceptionHandlerStub = new OptionKey<>(false);

    @Option(help = "Trace execution of the stub that routes an exception to a handler in the calling frame.", type = OptionType.Debug)
    static final OptionKey<Boolean> TraceUnwindStub = new OptionKey<>(false);

    @Option(help = "Trace execution of slow path stub for array allocation.", type = OptionType.Debug)
    static final OptionKey<Boolean> TraceNewArrayStub = new OptionKey<>(false);

    @Option(help = "Trace execution of slow path stub for non-array object allocation.", type = OptionType.Debug)
    static final OptionKey<Boolean> TraceNewInstanceStub = new OptionKey<>(false);

    @Option(help = "Force non-array object allocation to always use the slow path.", type = OptionType.Debug)
    static final OptionKey<Boolean> ForceUseOfNewInstanceStub = new OptionKey<>(false);
    //@formatter:on
}
