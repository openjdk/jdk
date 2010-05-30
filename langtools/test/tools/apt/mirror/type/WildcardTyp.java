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
 * @bug 4853450 5009396 5010636 5031156
 * @summary WildcardType tests
 * @library ../../lib
 * @compile -source 1.5 WildcardTyp.java
 * @run main/othervm WildcardTyp
 */


import java.util.*;
import com.sun.mirror.declaration.*;
import com.sun.mirror.type.*;
import com.sun.mirror.util.*;


public class WildcardTyp extends Tester {

    public static void main(String[] args) {
        (new WildcardTyp()).run();
    }


    // Declarations to use for testing

    interface G<T> {
    }

    interface G1<N extends Number & Runnable> {
    }

    interface G2<T extends G2<T>> {
    }

    // Some wildcard types to test.
    private G<?> f0;                    // unbound
    private G<? extends Number> f1;     // covariant
    private G<? super Number> f2;       // contravariant
    private G<? extends Object> f3;     // <sigh>
    private G1<?> f4;   // "true" upper bound is an intersection type
    private G2<?> f5;   // 'true" upper bound is a recursive F-bound and
                        // not expressible
    private static final int NUMTYPES = 6;

    // Type mirrors corresponding to the wildcard types of the above fields
    private WildcardType[] t = new WildcardType[NUMTYPES];


    protected void init() {
        for (int i = 0; i < t.length; i++) {
            DeclaredType type = (DeclaredType) getField("f"+i).getType();
            t[i] = (WildcardType)
                type.getActualTypeArguments().iterator().next();
        }
    }

    private WildcardType wildcardFor(String field) {
        DeclaredType d = (DeclaredType) getField(field).getType();
        return (WildcardType) d.getActualTypeArguments().iterator().next();
    }


    // TypeMirror methods

    @Test(result="wild thing")
    Collection<String> accept() {
        final Collection<String> res = new ArrayList<String>();

        t[0].accept(new SimpleTypeVisitor() {
            public void visitTypeMirror(TypeMirror t) {
                res.add("type");
            }
            public void visitReferenceType(ReferenceType t) {
                res.add("ref type");
            }
            public void visitWildcardType(WildcardType t) {
                res.add("wild thing");
            }
        });
        return res;
    }

    @Test(result={
                "?",
                "? extends java.lang.Number",
                "? super java.lang.Number",
                "? extends java.lang.Object",
                "?",
                "?"
          },
          ordered=true)
    Collection<String> toStringTests() {
        Collection<String> res = new ArrayList<String>();
        for (WildcardType w : t) {
            res.add(w.toString());
        }
        return res;
    }


    // WildcardType methods

    @Test(result={
                "null",
                "null",
                "java.lang.Number",
                "null",
                "null",
                "null"
          },
          ordered=true)
    Collection<ReferenceType> getLowerBounds() {
        Collection<ReferenceType> res = new ArrayList<ReferenceType>();
        for (WildcardType w : t) {
            Collection<ReferenceType> bounds = w.getLowerBounds();
            int num = bounds.size();
            if (num > 1) {
                throw new AssertionError("Bounds abound");
            }
            res.add((num > 0) ? bounds.iterator().next() : null);
        }
        return res;
    }

    @Test(result={
                "null",
                "java.lang.Number",
                "null",
                "java.lang.Object",
                "null",
                "null"
          },
          ordered=true)
    Collection<ReferenceType> getUpperBounds() {
        Collection<ReferenceType> res = new ArrayList<ReferenceType>();
        for (WildcardType w : t) {
            Collection<ReferenceType> bounds = w.getUpperBounds();
            int num = bounds.size();
            if (num > 1) {
                throw new AssertionError("Bounds abound");
            }
            res.add((num > 0) ? bounds.iterator().next() : null);
        }
        return res;
    }
}
