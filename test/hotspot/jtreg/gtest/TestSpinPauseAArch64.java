/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
 * @test id=default_armv8_0
 * @bug 8362193
 * @summary Run SpinPause gtest using different instructions for SpinPause
 * @library /test/lib
 * @requires vm.flagless
 * @requires os.arch=="aarch64"
 * @run main/native GTestWrapper --gtest_filter=SpinPause*
 * @run main/native GTestWrapper --gtest_filter=SpinPause* -XX:+UnlockDiagnosticVMOptions -XX:OnSpinWaitInst=none
 * @run main/native GTestWrapper --gtest_filter=SpinPause* -XX:+UnlockDiagnosticVMOptions -XX:OnSpinWaitInst=nop
 * @run main/native GTestWrapper --gtest_filter=SpinPause* -XX:+UnlockDiagnosticVMOptions -XX:OnSpinWaitInst=isb
 * @run main/native GTestWrapper --gtest_filter=SpinPause* -XX:+UnlockDiagnosticVMOptions -XX:OnSpinWaitInst=yield
 */

/*
 * @test id=sb_armv8_5
 * @bug 8362193
 * @summary Run SpinPause gtest using SB instruction for SpinPause
 * @library /test/lib
 * @requires vm.flagless
 * @requires (os.arch=="aarch64" & vm.cpu.features ~= ".*sb.*")
 * @run main/native GTestWrapper --gtest_filter=SpinPause* -XX:+UnlockDiagnosticVMOptions -XX:OnSpinWaitInst=sb
 */
