/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @summary Functional test for continuations walked with StackWalker's LiveStackFrames
 * @build java.base/java.lang.LiveFrames
 * @modules java.base/jdk.internal.vm
 *
 * @run main/othervm --enable-preview -XX:+UnlockDiagnosticVMOptions -Xint LiveFramesDriver
 * @run main/othervm --enable-preview -XX:+UnlockDiagnosticVMOptions -XX:-TieredCompilation -Xcomp -XX:CompileOnly=jdk/internal/vm/Continuation,java/lang/LiveFrames LiveFramesDriver
 * @run main/othervm --enable-preview -XX:+UnlockDiagnosticVMOptions -XX:+TieredCompilation -XX:TieredStopAtLevel=3 -Xcomp -XX:CompileOnly=jdk/internal/vm/Continuation,java/lang/LiveFrames LiveFramesDriver
 */


public class LiveFramesDriver {
    public static void main(String[] args) {
        java.lang.LiveFrames.main(args);
    }
}
