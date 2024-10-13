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
 * @bug 8312909
 * @summary Test monomorphic interface call to with invalid receiver.
 * @modules java.base/jdk.internal.vm.annotation
 * @compile TestInvokeinterfaceWithBadReceiverHelper.jasm
 * @run main/bootclasspath/othervm -XX:CompileCommand=compileonly,TestInvokeinterfaceWithBadReceiverHelper::test
 *                                 -Xcomp -XX:TieredStopAtLevel=1 TestInvokeinterfaceWithBadReceiver
 */

import jdk.internal.vm.annotation.Stable;

interface MyInterface {
    public String get();
}

// Single implementor
class MyClass implements MyInterface {
    @Stable
    String field = "42";

    public String get() {
        return field;
    }
}

public class TestInvokeinterfaceWithBadReceiver {

    public static void main(String[] args) {
        try {
            TestInvokeinterfaceWithBadReceiverHelper.test(new MyClass());
            throw new RuntimeException("No IncompatibleClassChangeError thrown!");
        } catch (IncompatibleClassChangeError e) {
            // Expected
        }
    }
}
