/*
 * Copyright (c) 2004, 2008, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 5037165
 * @summary Test the Declarations.overrides method
 * @library ../../lib
 * @run main/othervm Overrides
 */


import java.util.*;
import com.sun.mirror.declaration.*;
import com.sun.mirror.type.*;
import com.sun.mirror.util.*;


public class Overrides extends Tester {

    public static void main(String[] args) {
        (new Overrides()).run();
    }


    // Declarations used by tests

    static class A {
        void m1(int i) {};              // does not override itself
        void m2(int i) {};
        static void m3(int i) {};
    }

        static class B extends A {
            void m1(int j) {};          // overrides A.m1
            void m1(String i) {};       // does not override A.m1
            void m4(int i) {};          // does not override A.m1
        }

            static class C extends B {
                void m1(int i) {};      // overrides A.m1 and B.m1
                void m2(int i) {};      // overrides A.m2
            }

        static class D extends A {
            static void m3(int i) {};   // does not override A.m3
        }

    static class E {
        void m1(int i) {};              // does not override A.m1
    }



    private Declarations decls;

    private TypeDeclaration A;
    private TypeDeclaration B;
    private TypeDeclaration C;
    private TypeDeclaration D;
    private TypeDeclaration E;
    private MethodDeclaration Am1;
    private MethodDeclaration Am2;
    private MethodDeclaration Am3;
    private MethodDeclaration Bm1;
    private MethodDeclaration Bm1b;
    private MethodDeclaration Bm4;
    private MethodDeclaration Cm1;
    private MethodDeclaration Cm2;
    private MethodDeclaration Dm3;
    private MethodDeclaration Em1;

    protected void init() {
        decls = env.getDeclarationUtils();

        A = env.getTypeDeclaration("Overrides.A");
        B = env.getTypeDeclaration("Overrides.B");
        C = env.getTypeDeclaration("Overrides.C");
        D = env.getTypeDeclaration("Overrides.D");
        E = env.getTypeDeclaration("Overrides.E");

        Am1  = getMethod(A, "m1", "i");
        Am2  = getMethod(A, "m2", "i");
        Am3  = getMethod(A, "m3", "i");
        Bm1  = getMethod(B, "m1", "j");
        Bm1b = getMethod(B, "m1", "i");
        Bm4  = getMethod(B, "m4", "i");
        Cm1  = getMethod(C, "m1", "i");
        Cm2  = getMethod(C, "m2", "i");
        Dm3  = getMethod(D, "m3", "i");
        Em1  = getMethod(E, "m1", "i");
    }

    private MethodDeclaration getMethod(TypeDeclaration t,
                                        String methodName, String paramName) {
        for (MethodDeclaration m : t.getMethods()) {
            if (methodName.equals(m.getSimpleName()) &&
                    paramName.equals(m.getParameters().iterator().next()
                                                        .getSimpleName())) {
                return m;
            }
        }
        throw new AssertionError();
    }


    // Declarations methods

    @Test(result={"false",
                  "true",
                  "false",
                  "false",
                  "true",
                  "true",
                  "true",
                  "false",
                  "false"},
          ordered=true)
    List<Boolean> overrides() {
        return Arrays.asList(
                decls.overrides(Am1, Am1),
                decls.overrides(Bm1, Am1),
                decls.overrides(Bm1b,Am1),
                decls.overrides(Bm4, Am1),
                decls.overrides(Cm1, Am1),
                decls.overrides(Cm1, Bm1),
                decls.overrides(Cm2, Am2),
                decls.overrides(Dm3, Am3),
                decls.overrides(Em1, Am1));
    }
}
