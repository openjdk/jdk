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
 * @bug 8157892 8157977
 * @summary Verify file name, line number and bci of native methods
 * @run main/othervm/native -Xcheck:jni NativeMethod
 */

import java.lang.StackWalker.Option;
import java.lang.StackWalker.StackFrame;
import java.util.List;
import java.util.stream.Collectors;

public class NativeMethod {
    public static void main(String... args) throws Exception {
        new NativeMethod().test();
    }

    private final StackWalker walker;
    NativeMethod() {
        this.walker = StackWalker.getInstance(Option.SHOW_REFLECT_FRAMES);
    }

    // this native method will invoke NativeMethod::walk method
    public native void test();

    public void walk() {
        List<StackFrame> nativeFrames = walker.walk(s ->
            s.filter(StackFrame::isNativeMethod)
             .collect(Collectors.toList())
        );

        assertTrue(nativeFrames.size() > 0, "native frame not found");
        // find NativeMethod::test native frame
        nativeFrames.stream().filter(f -> f.isNativeMethod()
                                            && f.getClassName().equals("NativeMethod")
                                            && f.getMethodName().equals("test"))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("NativeMethod::test native method not found"));

        for (StackFrame f : nativeFrames) {
            assertTrue(f.getFileName() != null, "source file expected to be found");
            assertTrue(f.getLineNumber() < 0, "line number expected to be unavailable");
            assertTrue(f.getByteCodeIndex() < 0, "bci expected to be unavailable");
        }
    }

    private static void assertTrue(boolean value, String msg) {
        if (value != true)
            throw new AssertionError(msg);
    }

    static {
        System.loadLibrary("nativeMethod");
    }
}
