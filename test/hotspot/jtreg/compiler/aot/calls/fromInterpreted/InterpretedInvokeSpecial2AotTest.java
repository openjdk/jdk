/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @requires vm.aot
 * @library /test/lib /testlibrary /
 * @modules java.base/jdk.internal.misc
 * @build compiler.calls.common.InvokeSpecial
 *        sun.hotspot.WhiteBox
 * @run driver ClassFileInstaller sun.hotspot.WhiteBox
 * @run driver compiler.aot.AotCompiler
 *      -libname InterpretedInvokeSpecial2AotTest.so
 *      -class compiler.calls.common.InvokeSpecial
 *      -compile compiler.calls.common.InvokeSpecial.callee.*
 * @run main/othervm -XX:+UnlockExperimentalVMOptions -XX:+UseAOT
 *      -XX:AOTLibrary=./InterpretedInvokeSpecial2AotTest.so
 *      -XX:CompileCommand=exclude,compiler.calls.common.InvokeSpecial::caller
 *      -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:.
 *      compiler.calls.common.InvokeSpecial -checkCalleeCompileLevel -1
 * @summary check calls from interpreted to aot code using invokespecial
 */
