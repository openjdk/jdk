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
 * @bug 4853450 5009360 5055963
 * @summary ClassType tests
 * @library ../../lib
 * @run main/othervm ClassTyp
 */


import java.util.*;
import com.sun.mirror.declaration.*;
import com.sun.mirror.type.*;
import com.sun.mirror.util.*;


public class ClassTyp<T1,T2> extends Tester {

    public static void main(String[] args) {
        (new ClassTyp()).run();
    }


    // Declarations used by tests

    static class C1<S> extends AbstractSet<S> implements Set<S> {
        class C2<R> {
        }

        static class C3<R> {
            class C4<Q> {
            }
        }

        public Iterator<S> iterator() {
            return null;
        }

        public int size() {
            return 0;
        }
    }


    // Generate some class types to test.
    private C1<T1> f0;
    private C1<String> f1;
    private C1 f2;
    private C1.C3<T2> f3;
    private C1<T1>.C2<T2> f4;
    private C1.C2 f5;
    private C1<T1> f6;
    private C1.C3<T2>.C4<T1> f7;
    private static final int NUMTYPES = 8;

    // Type mirrors corresponding to the types of the above fields
    private ClassType[] t = new ClassType[NUMTYPES];

    // One more type:  our own.
    private ClassTyp<T1,T2> me = this;


    protected void init() {
        for (int i = 0; i < t.length; i++) {
            t[i] = (ClassType) getField("f"+i).getType();
        }
    }


    // TypeMirror methods

    @Test(result="class")
    Collection<String> accept() {
        final Collection<String> res = new ArrayList<String>();

        t[0].accept(new SimpleTypeVisitor() {
            public void visitReferenceType(ReferenceType t) {
                res.add("ref type");
            }
            public void visitClassType(ClassType t) {
                res.add("class");
            }
            public void visitInterfaceType(InterfaceType t) {
                res.add("interface");
            }
        });
        return res;
    }

    @Test(result="true")
    boolean equals1() {
        return t[0].equals(t[0]);
    }

    @Test(result="false")
    boolean equals2() {
        return t[0].equals(t[1]);
    }

    // Raw type is not same as type instantiated with unbounded type var.
    @Test(result="false")
    boolean equals3() {
        return t[0].equals(t[2]);
    }

    // C1<T1> is same type as C1<T1>
    @Test(result="true")
    boolean equals4() {
        return t[0].equals(t[6]);
    }

    @Test(result={
              "ClassTyp.C1<T1>",
              "ClassTyp.C1<java.lang.String>",
              "ClassTyp.C1",
              "ClassTyp.C1.C3<T2>",
              "ClassTyp.C1<T1>.C2<T2>",
              "ClassTyp.C1.C2",
              "ClassTyp.C1<T1>",
              "ClassTyp.C1.C3<T2>.C4<T1>"
          },
          ordered=true)
    Collection<String> toStringTests() {
        Collection<String> res = new ArrayList<String>();
        for (ClassType c : t) {
            res.add(c.toString());
        }
        return res;
    }


    // DeclaredType methods

    @Test(result={"T1"})
    Collection<TypeMirror> getActualTypeArguments1() {
        return t[0].getActualTypeArguments();
    }

    @Test(result={})
    Collection<TypeMirror> getActualTypeArguments2() {
        return t[2].getActualTypeArguments();
    }

    @Test(result={"T2"})
    Collection<TypeMirror> getActualTypeArguments3() {
        return t[3].getActualTypeArguments();
    }

    @Test(result="null")
    DeclaredType getContainingType1() {
        ClassType thisType = (ClassType) getField("me").getType();
        return thisType.getContainingType();
    }

    @Test(result="ClassTyp")
    DeclaredType getContainingType2() {
        return t[0].getContainingType();
    }

    @Test(result="ClassTyp.C1")
    DeclaredType getContainingType3() {
        return t[3].getContainingType();
    }

    @Test(result="ClassTyp.C1<T1>")
    DeclaredType getContainingType4() {
        return t[4].getContainingType();
    }

    @Test(result={"java.util.Set<T1>"})
    Collection<InterfaceType> getSuperinterfaces() {
        return t[0].getSuperinterfaces();
    }


    // ClassType methods

    @Test(result="ClassTyp.C1<S>")
    ClassDeclaration getDeclaration1() {
        return t[0].getDeclaration();
    }

    @Test(result="ClassTyp.C1.C3<R>")
    ClassDeclaration getDeclaration2() {
        return t[3].getDeclaration();
    }

    @Test(result="ClassTyp.C1<S>.C2<R>")
    ClassDeclaration getDeclaration3a() {
        return t[4].getDeclaration();
    }

    @Test(result="ClassTyp.C1<S>.C2<R>")
    ClassDeclaration getDeclaration3b() {
        return t[5].getDeclaration();
    }

    @Test(result="true")
    boolean getDeclarationEq() {
        return t[0].getDeclaration() == t[6].getDeclaration();
    }

    @Test(result="java.util.AbstractSet<T1>")
    ClassType getSuperclass1() {
        return t[0].getSuperclass();
    }

    @Test(result="java.lang.Object")
    ClassType getSuperclass2() {
        return t[4].getSuperclass();
    }

    @Test(result="null")
    ClassType getSuperclassOfObject() {
        return t[4].getSuperclass().getSuperclass();
    }
}
