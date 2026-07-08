/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8386475
 * @summary Verify no assertions with -XX:+UseBMI2Instructions
 * @requires os.simpleArch == "x64"
 * @run main/othervm -XX:+UseBMI2Instructions ${test.main.class}
 */

/*
 * @test
 * @bug 8386475
 * @summary Verify no assertions with -XX:-UseBMI2Instructions
 * @requires os.simpleArch == "x64"
 * @run main/othervm -Xcomp -XX:CompileCommand=compileonly,java.lang.CharacterDataLatin1::<clinit> -XX:+UnlockDiagnosticVMOptions -XX:CopyAVX3Threshold=0 -XX:-UseBMI2Instructions ${test.main.class}
 */

/*
 * @test
 * @bug 8386475
 * @summary Verify no assertions when generating vectorizedMismatch stub with -XX:-UseBMI2Instructions
 * @requires os.simpleArch == "x64" & vm.cpu.features ~= ".*avx2.*"
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:AVX3Threshold=0 -XX:-UseBMI2Instructions ${test.main.class}
 */

/*
 * @test
 * @bug 8386475
 * @summary Verify no assertions when generating string_indexof stub with -XX:-UseBMI2Instructions
 * @requires os.simpleArch == "x64" & vm.cpu.features ~= ".*avx2.*"
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:UseAVX=2 -XX:+EnableX86ECoreOpts -XX:-UseBMI2Instructions ${test.main.class}
 */

package compiler.cpuflags;

public class TestUseBMI2Instructions {
  public static void main(String args[]) {
    // intentionally empty
  }
}
