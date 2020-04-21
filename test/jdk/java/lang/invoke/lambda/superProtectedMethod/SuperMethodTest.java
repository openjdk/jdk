/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8227415
 * @run main p.SuperMethodTest
 * @summary method reference to a protected method inherited from its
 *          superclass in a different package must be accessed via
 *          a bridge method.  Lambda proxy class has no access to it.
 */

package p;

import q.I;
import q.J;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Function;

public class SuperMethodTest  {
    public static void main(String... args) {
        Sub_I sub = new Sub_I();
        sub.test(Paths.get("test"));
    }

    public static class Sub_J extends J {
        Sub_J(Function<Path,String> function) {
            super(function);
        }
    }

    public static class Sub_I extends I {
        public void test(Path path) {
            /*
             * The method reference to an inherited protected method
             * in another package is desugared with REF_invokeVirtual on
             * a bridge method to invoke protected q.I::filename method
             */
            Sub_J c = new Sub_J(this::filename);
            c.check(path);
        }
    }
}
