/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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
package vm.share.vmcrasher;

import vm.share.options.BasicOptionObjectFactory;
import vm.share.options.FClass;
import vm.share.options.Factory;

@Factory(placeholder_text="Crasher type", default_value="signal", classlist={
        @FClass(key="signal", description="Crash VM with a signal", type=SignalCrasher.class),
        @FClass(key="unsafe.gc", description="Crash VM in GC with Unsafe interface", type=UnsafeGCCrasher.class),
        @FClass(key="unsafe.java", description="Crash VM in Java thread with Unsafe interface", type=UnsafeJavaCrasher.class),
})
public class CrasherFactory extends BasicOptionObjectFactory<Runnable> {
}
