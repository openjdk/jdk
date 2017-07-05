/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Verifies the behaviour of Unsafe.allocateInstance
 * @library /testlibrary
 * @modules java.base/sun.misc
 *          java.management
 * @run main AllocateInstance
 */

import com.oracle.java.testlibrary.*;
import sun.misc.Unsafe;
import static com.oracle.java.testlibrary.Asserts.*;

public class AllocateInstance {
    public static void main(String args[]) throws Exception {
        Unsafe unsafe = Utils.getUnsafe();

        // allocateInstance() should not result in a call to the constructor
        TestClass tc = (TestClass)unsafe.allocateInstance(TestClass.class);
        assertFalse(tc.calledConstructor);

        // allocateInstance() on an abstract class should result in an InstantiationException
        try {
            AbstractClass ac = (AbstractClass)unsafe.allocateInstance(AbstractClass.class);
            throw new RuntimeException("Did not get expected InstantiationException");
        } catch (InstantiationException e) {
            // Expected
        }
    }

    class TestClass {
        public boolean calledConstructor = false;

        public TestClass() {
            calledConstructor = true;
        }
    }

    abstract class AbstractClass {
        public AbstractClass() {}
    }
}
