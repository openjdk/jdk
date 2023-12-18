/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @library ../ /test/lib
 * @requires jdk.foreign.linker != "FALLBACK"
 * @run testng/othervm/native
 *   --enable-native-access=ALL-UNNAMED
 *   TestStubAllocFailure
 */

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.testng.annotations.Test;

import static org.testng.Assert.fail;

public class TestStubAllocFailure extends UpcallTestHelper {

    @Test
    public void testUpcallAllocFailure() throws IOException, InterruptedException {
        runInNewProcess(UpcallRunner.class, true, List.of("-XX:ReservedCodeCacheSize=3M"), List.of())
                .shouldNotHaveExitValue(0)
                .shouldNotHaveFatalError();
    }

    @Test
    public void testUDowncallAllocFailure() throws IOException, InterruptedException {
        runInNewProcess(DowncallRunner.class, true, List.of("-XX:ReservedCodeCacheSize=3M"), List.of())
                .shouldNotHaveExitValue(0)
                .shouldNotHaveFatalError();
    }

    public static class UpcallRunner extends NativeTestHelper {
        public static void main(String[] args) throws Throwable {
            FunctionDescriptor descriptor = FunctionDescriptor.ofVoid();
            MethodHandle target = MethodHandles.lookup().findStatic(UpcallRunner.class, "target", descriptor.toMethodType());
            while (true) {
                LINKER.upcallStub(target, descriptor, Arena.ofAuto());
            }
        }

        public static void target() {
            fail("Should not get here");
        }
    }

    public static class DowncallRunner extends NativeTestHelper {

        private static final int MAX_ARITY = 5;

        private static void mapper(FunctionDescriptor fd, Consumer<FunctionDescriptor> sink) {
            for (MemoryLayout l : List.of(C_INT, C_LONG_LONG, C_DOUBLE, C_FLOAT, C_SHORT)) {
                sink.accept(fd.appendArgumentLayouts(l));
            }
        }

        public static void main(String[] args) throws Throwable {
            Linker linker = Linker.nativeLinker();
            Stream<FunctionDescriptor> stream = Stream.of(FunctionDescriptor.ofVoid());
            for (int i = 0; i < MAX_ARITY; i++) {
                stream = stream.mapMulti(DowncallRunner::mapper);
            }

            stream.forEach(linker::downcallHandle);
        }
    }
}
