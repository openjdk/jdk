/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @library /test/lib /testlibrary /
 * @requires vm.bits == "64" & os.arch == "amd64" & os.family == "linux"
 * @modules java.base/jdk.internal.org.objectweb.asm
 *          java.base/jdk.internal.misc
 * @build compiler.calls.common.InvokeDynamic
 *        compiler.calls.common.InvokeDynamicPatcher
 *        compiler.aot.AotCompiler
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 *      sun.hotspot.WhiteBox$WhiteBoxPermission
 * @run main compiler.calls.common.InvokeDynamicPatcher
 * @run main compiler.aot.AotCompiler
 *      -libname InterpretedInvokeDynamic2AotTest.so
 *      -class compiler.calls.common.InvokeDynamic
 *      -compile compiler.calls.common.InvokeDynamic.callee.*
 * @run main/othervm -XX:+UseAOT
 *      -XX:AOTLibrary=./InterpretedInvokeDynamic2AotTest.so
 *      -XX:CompileCommand=exclude,compiler.calls.common.InvokeDynamic::caller
 *      -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:.
 *      compiler.calls.common.InvokeDynamic -checkCalleeCompileLevel -1
 * @summary check calls from interpreted to aot code using invokedynamic
 */
