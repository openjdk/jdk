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
 * @bug 5033381
 * @summary Test the type creation methods in Types.
 * @library ../../lib
 * @run main/othervm TypeCreation
 */


import java.util.*;
import com.sun.mirror.declaration.*;
import com.sun.mirror.type.*;
import com.sun.mirror.util.*;

import static com.sun.mirror.type.PrimitiveType.Kind.*;


public class TypeCreation extends Tester {

    public static void main(String[] args) {
        (new TypeCreation()).run();
    }


    // Declarations used by tests

    class A {
    }

    class O<T> {
        class I<S> {
        }
    }


    private Types types;

    private TypeDeclaration A;
    private TypeDeclaration O;
    private TypeDeclaration I;

    private DeclaredType AType;

    protected void init() {
        types = env.getTypeUtils();
        A = env.getTypeDeclaration("TypeCreation.A");
        O = env.getTypeDeclaration("TypeCreation.O");
        I = env.getTypeDeclaration("TypeCreation.O.I");

        AType = types.getDeclaredType(A);
    }


    @Test(result="boolean")
    PrimitiveType getPrimitiveType() {
        return types.getPrimitiveType(BOOLEAN);
    }

    @Test(result="void")
    VoidType getVoidType() {
        return types.getVoidType();
    }

    @Test(result="boolean[]")
    ArrayType getArrayType1() {
        return types.getArrayType(
                types.getPrimitiveType(BOOLEAN));
    }

    @Test(result="TypeCreation.A[]")
    ArrayType getArrayType2() {
        return types.getArrayType(AType);
    }

    @Test(result="? extends TypeCreation.A")
    WildcardType getWildcardType() {
        Collection<ReferenceType> uppers = new ArrayList<ReferenceType>();
        Collection<ReferenceType> downers = new ArrayList<ReferenceType>();
        uppers.add(AType);
        return types.getWildcardType(uppers, downers);
    }

    @Test(result="TypeCreation.O<java.lang.String>")
    DeclaredType getDeclaredType1() {
        TypeDeclaration stringDecl = env.getTypeDeclaration("java.lang.String");
        DeclaredType stringType = types.getDeclaredType(stringDecl);
        return types.getDeclaredType(O, stringType);
    }

    @Test(result="TypeCreation.O<java.lang.String>.I<java.lang.Number>")
    DeclaredType getDeclaredType2() {
        TypeDeclaration numDecl = env.getTypeDeclaration("java.lang.Number");
        DeclaredType numType = types.getDeclaredType(numDecl);
        DeclaredType OType = getDeclaredType1();
        return types.getDeclaredType(OType, I, numType);
    }
}
