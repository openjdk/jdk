/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test id=Xint_outer_inner
 * @requires vm.flagless
 * @summary Tests recursive locking in -Xint in outer then inner mode.
 * @library /testlibrary /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 *
 * @run main/othervm/timeout=240 -Xbootclasspath/a:.
 *     -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *     -Xint
 *     -Xms256m -Xmx256m
 *     TestRecursiveLocking 120 1
 */

/*
 * @test id=Xint_alternate_AB
 * @requires vm.flagless
 * @summary Tests recursive locking in -Xint in alternate A and B mode.
 * @library /testlibrary /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 *
 * @run main/othervm/timeout=240 -Xbootclasspath/a:.
 *     -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *     -Xint
 *     -Xms256m -Xmx256m
 *     TestRecursiveLocking 120 2
 */

/*
 * @test id=C1_outer_inner
 * @requires vm.flagless
 * @requires vm.compiler1.enabled
 * @summary Tests recursive locking in C1 in outer then inner mode.
 * @library /testlibrary /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 *
 * @run main/othervm/timeout=240 -Xbootclasspath/a:.
 *     -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *     -XX:TieredStopAtLevel=1
 *     -Xms256m -Xmx256m
 *     TestRecursiveLocking 120 1
 */

/*
 * @test id=C1_alternate_AB
 * @requires vm.flagless
 * @requires vm.compiler1.enabled
 * @summary Tests recursive locking in C1 in alternate A and B mode.
 * @library /testlibrary /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 *
 * @run main/othervm/timeout=240 -Xbootclasspath/a:.
 *     -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *     -XX:TieredStopAtLevel=1
 *     -Xms256m -Xmx256m
 *     TestRecursiveLocking 120 2
 */

/*
 * @test id=C2_outer_inner
 * @requires vm.flagless
 * @requires vm.compiler2.enabled
 * @summary Tests recursive locking in C2 in outer then inner mode.
 * @library /testlibrary /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 *
 * @run main/othervm/timeout=240 -Xbootclasspath/a:.
 *     -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *     -XX:-EliminateNestedLocks
 *     -Xms256m -Xmx256m
 *     TestRecursiveLocking 120 1
 */

/*
 * @test id=C2_alternate_AB
 * @requires vm.flagless
 * @requires vm.compiler2.enabled
 * @summary Tests recursive locking in C2 in alternate A and B mode.
 * @library /testlibrary /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 *
 * @run main/othervm/timeout=240 -Xbootclasspath/a:.
 *     -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *     -XX:-EliminateNestedLocks
 *     -Xms256m -Xmx256m
 *     TestRecursiveLocking 120 2
 */
