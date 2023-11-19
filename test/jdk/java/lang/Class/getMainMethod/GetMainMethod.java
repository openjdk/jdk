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

 import java.lang.reflect.Method;

/**
 * @test
 * @bug 8320255
 * @enablePreview
 * @summary Verify Class::getMainMethod handles expected variations.
 */

 public class GetMainMethod {
    private static boolean hasErrors = false;

    public static void main(String [] args) {
        foundMain(GetMainMethod.class);
        noMain(A.class);
        foundMain(B.class);
        foundMain(C.class);
        foundMain(D.class);
        foundMain(E.class);
        foundMain(F.class);
        foundMain(G.class);
        foundMain(H.class);
        foundMain(I.class);
        noMain(J.class);
        noMain(K.class);
        noMain(L.class);
        noMain(M.class);
        foundMain(N.class);
        foundMain(O.class);
        foundMain(P.class);
        foundMain(Q.class);
        foundMain(Record.class);
        foundMain(Color.class);
        foundMain(Root.class, Super.class);
        foundMain(RootStatic.class, SuperStatic.class);
        foundMain(Interface.class);
        foundMain(RootInterface.class, Interface.class);
        oneArg(ChooseOne.class);
        oneArg(InheritOne.class);
        oneArg(ImplementOne.class);

        if (hasErrors) {
            throw new RuntimeException("Has Errors");
        }
    }

    private static void foundMain(Class<?> root) {
        foundMain(root, root);
    }

    private static void foundMain(Class<?> root, Class<?> declared) {
        Method main = root.getMainMethod();
        test(main != null, "No main found in " + root);
        test(main != null && main.getDeclaringClass() == declared, "Wrong main found for " + root);
    }

    private static void noMain(Class<?> root) {
        Method main = root.getMainMethod();
        test(main == null, "Main in wrong class from " + root);
    }

    private static void oneArg(Class<?> root) {
        Method main = root.getMainMethod();
        test(main.getParameterCount() == 1, "Main found with no arguments in " + root);
    }


    private static void test(boolean test, String message) {
        if (!test) {
            System.err.println("Failure: " + message);
            hasErrors = true;
        }
    }
}

class A {  }
class B { public static void main(String [] args) {} }
class C { public static void main() {} }
class D { public void main(String [] args) {} }
class E { public void main() {} }
class F { protected static void main(String [] args) {} }
class G { protected static void main() {} }
class H { protected void main(String [] args) {} }
class I { protected void main() {} }
class J { private static void main(String [] args) {} }
class K { private static void main() {} }
class L { private void main(String [] args) {} }
class M { private void main() {} }
class N { static void main(String [] args) {} }
class O { static void main() {} }
class P { void main(String [] args) {} }
class Q { void main() {} }

record Record(int a) { public static void main(String [] args) {} }
enum Color { RED, GREEN, BLUE; public static void main(String [] args) {} }

class Super { public void main(String [] args) {} }
class Root extends Super {}

class SuperStatic { public static void main(String [] args) {} }
class RootStatic extends SuperStatic {}

interface Interface { default void main(String [] args) {} }
class RootInterface implements Interface {}

class ChooseOne { public static void main(String [] args) {} public static void main() {} }
class InheritOne extends SuperStatic { public static void main() {} }
class ImplementOne implements Interface { public static void main() {} }
