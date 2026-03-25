/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
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

package compiler.valhalla.inlinetypes;

import jdk.test.lib.Utils;
import compiler.lib.ir_framework.Scenario;
import compiler.lib.ir_framework.TestFramework;

public class InlineTypes {
    public static final int  rI = Utils.getRandomInstance().nextInt() % 1000;
    public static final long rL = Utils.getRandomInstance().nextLong() % 1000;
    public static final double rD = Utils.getRandomInstance().nextDouble() % 1000;

    public static final Scenario[] DEFAULT_SCENARIOS = {
            new Scenario(0,
                         "--enable-preview",
                         "--add-exports", "java.base/jdk.internal.value=ALL-UNNAMED",
                         "--add-exports", "java.base/jdk.internal.vm.annotation=ALL-UNNAMED",
                         "--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED",
                         "-XX:+IgnoreUnrecognizedVMOptions",
                         "-XX:-UseACmpProfile",
                         "-XX:+AlwaysIncrementalInline",
                         "-XX:FlatArrayElementMaxOops=5",
                         "-XX:+UseArrayFlattening",
                         "-XX:-UseArrayLoadStoreProfile",
                         "-XX:+UseFieldFlattening",
                         "-XX:+InlineTypePassFieldsAsArgs",
                         "-XX:+InlineTypeReturnedAsFields"
            ),
            new Scenario(1,
                         "--enable-preview",
                         "--add-exports", "java.base/jdk.internal.value=ALL-UNNAMED",
                         "--add-exports", "java.base/jdk.internal.vm.annotation=ALL-UNNAMED",
                         "--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED",
                         "-XX:+IgnoreUnrecognizedVMOptions",
                         "-XX:-UseACmpProfile",
                         "-XX:-UseCompressedOops",
                         "-XX:FlatArrayElementMaxOops=5",
                         "-XX:+UseArrayFlattening",
                         "-XX:-UseArrayLoadStoreProfile",
                         "-XX:+UseFieldFlattening",
                         "-XX:-InlineTypePassFieldsAsArgs",
                         "-XX:-InlineTypeReturnedAsFields"
            ),
            new Scenario(2,
                         "--enable-preview",
                         "--add-exports", "java.base/jdk.internal.value=ALL-UNNAMED",
                         "--add-exports", "java.base/jdk.internal.vm.annotation=ALL-UNNAMED",
                         "--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED",
                         "-XX:+IgnoreUnrecognizedVMOptions",
                         "-XX:-UseACmpProfile",
                         "-XX:-UseCompressedOops",
                         "-XX:FlatArrayElementMaxOops=0",
                         "-XX:-UseArrayFlattening",
                         "-XX:-UseArrayLoadStoreProfile",
                         "-XX:+UseFieldFlattening",
                         "-XX:+InlineTypePassFieldsAsArgs",
                         "-XX:+InlineTypeReturnedAsFields"
            ),
            new Scenario(3,
                         "--enable-preview",
                         "--add-exports", "java.base/jdk.internal.value=ALL-UNNAMED",
                         "--add-exports", "java.base/jdk.internal.vm.annotation=ALL-UNNAMED",
                         "--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED",
                         "-XX:+IgnoreUnrecognizedVMOptions",
                         "-DVerifyIR=false",
                         "-XX:+AlwaysIncrementalInline",
                         "-XX:FlatArrayElementMaxOops=0",
                         "-XX:-UseArrayFlattening",
                         "-XX:-UseFieldFlattening",
                         "-XX:+InlineTypePassFieldsAsArgs",
                         "-XX:+InlineTypeReturnedAsFields"
            ),
            new Scenario(4,
                         "--enable-preview",
                         "--add-exports", "java.base/jdk.internal.value=ALL-UNNAMED",
                         "--add-exports", "java.base/jdk.internal.vm.annotation=ALL-UNNAMED",
                         "--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED",
                         "-XX:+IgnoreUnrecognizedVMOptions",
                         "-DVerifyIR=false",
                         "-XX:FlatArrayElementMaxOops=-1",
                         "-XX:+UseArrayFlattening",
                         "-XX:-UseFieldFlattening",
                         "-XX:+InlineTypePassFieldsAsArgs",
                         "-XX:-InlineTypeReturnedAsFields",
                         "-XX:-ReduceInitialCardMarks"
            ),
            new Scenario(5,
                         "--enable-preview",
                         "--add-exports", "java.base/jdk.internal.value=ALL-UNNAMED",
                         "--add-exports", "java.base/jdk.internal.vm.annotation=ALL-UNNAMED",
                         "--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED",
                         "-XX:+IgnoreUnrecognizedVMOptions",
                         "-XX:-UseACmpProfile",
                         "-XX:+AlwaysIncrementalInline",
                         "-XX:FlatArrayElementMaxOops=5",
                         "-XX:+UseArrayFlattening",
                         "-XX:-UseArrayLoadStoreProfile",
                         "-XX:+UseFieldFlattening",
                         "-XX:-InlineTypePassFieldsAsArgs",
                         "-XX:-InlineTypeReturnedAsFields"
            ),
            new Scenario(6,
                         "--enable-preview",
                         "--add-exports", "java.base/jdk.internal.value=ALL-UNNAMED",
                         "--add-exports", "java.base/jdk.internal.vm.annotation=ALL-UNNAMED",
                         "--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED",
                         "-XX:+IgnoreUnrecognizedVMOptions",
                         "-XX:-UseACmpProfile",
                         "-XX:+AlwaysIncrementalInline",
                         "-XX:FlatArrayElementMaxOops=5",
                         "-XX:+UseArrayFlattening",
                         "-XX:-UseArrayLoadStoreProfile",
                         "-XX:+UseFieldFlattening",
                         "-XX:+UseNullableValueFlattening",
                         "-XX:+UseAtomicValueFlattening",
                         "-XX:+UseNonAtomicValueFlattening",
                         "-XX:+InlineTypePassFieldsAsArgs",
                         "-XX:+InlineTypeReturnedAsFields"
            ),
    };

    public static TestFramework getFramework() {
        StackWalker walker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
        return new TestFramework(walker.getCallerClass()).setDefaultWarmup(251);
    }
}
