/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8367002 8370766
 * @summary Compilers might not generate handlers for recursive exceptions
 *
 * @compile IllegalAccessInCatch.jasm
 * @run main/othervm -Xbatch
 *   -XX:CompileCommand=compileonly,IllegalAccessInCatch*::test
 *   -XX:+IgnoreUnrecognizedVMOptions -XX:+VerifyStack
 *   -XX:-TieredCompilation
 *   TestAccessErrorInCatch
 * @run main/othervm -Xbatch
 *   -XX:CompileCommand=compileonly,IllegalAccessInCatch*::test
 *   -XX:+IgnoreUnrecognizedVMOptions -XX:+VerifyStack
 *   -XX:TieredStopAtLevel=3
 *   TestAccessErrorInCatch
 */

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import java.nio.file.Files;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;

public class TestAccessErrorInCatch {

    public static void main(String[] args) throws Throwable {
        Path TEST_CLASSES_DIR = FileSystems.getDefault().getPath(System.getProperty("test.classes"));
        byte[] bytes = Files.readAllBytes(TEST_CLASSES_DIR.resolve("IllegalAccessInCatch.class"));

        var l = MethodHandles.lookup().defineHiddenClass(bytes, true);
        Class<?> anonClass = l.lookupClass();
        MethodHandle mh = l.findStatic(anonClass, "test", MethodType.methodType(int.class));
        for (int i = 0; i < 16_000; i++) {
            invoke(mh);
        }
        System.out.println(invoke(mh));
    }

    private static int invoke(MethodHandle mh) throws Throwable {
        int expected = 1;
        int ret = (int) mh.invokeExact();
        if (ret != expected) {
            throw new RuntimeException("Returned " + ret + " but expected " + expected);
        }
        return ret;
    }
}
