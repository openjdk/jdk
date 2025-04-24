/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;

/**
  * @test
  * @bug 8340812
  * @summary Verify that LambdaForm customization via MethodHandle::updateForm is thread safe.
  * @run main TestLambdaFormCustomization
  * @run main/othervm -Djava.lang.invoke.MethodHandle.CUSTOMIZE_THRESHOLD=0 TestLambdaFormCustomization
  */
public class TestLambdaFormCustomization {

    String str = "test";
    static final String value = "test" + 42;

    // Trigger concurrent LambdaForm customization for VarHandle invokers
    void test() throws NoSuchFieldException, IllegalAccessException {
        VarHandle varHandle = MethodHandles.lookup().in(getClass()).findVarHandle(getClass(), "str", String.class);

        ArrayList<Thread> threads = new ArrayList<>();
        for (int threadIdx = 0; threadIdx < 10; threadIdx++) {
            threads.add(new Thread(() -> {
                for (int i = 0; i < 1000; i++) {
                    varHandle.compareAndExchange(this, value, value);
                    varHandle.compareAndExchange(this, value, value);
                    varHandle.compareAndExchange(this, value, value);
                }
            }));
        }
        threads.forEach(Thread::start);
        threads.forEach(t -> {
            try {
                t.join();
            } catch (Throwable e) {
                throw new IllegalStateException(e);
            }
        });
    }

    public static void main(String[] args) throws Exception {
        TestLambdaFormCustomization t = new TestLambdaFormCustomization();
        for (int i = 0; i < 4000; ++i) {
            t.test();
        }
    }
}
