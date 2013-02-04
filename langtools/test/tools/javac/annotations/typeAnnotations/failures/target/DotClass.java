import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.ElementType.TYPE_PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/*
 * Copyright (c) 2009 Oracle and/or its affiliates. All rights reserved.
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
 * @summary Class literals are not type uses and cannot be annotated
 * @author Werner Dietl
 * @compile/fail/ref=DotClass.out -XDrawDiagnostics DotClass.java
 */

@Target({TYPE_USE, TYPE_PARAMETER, TYPE})
@Retention(RetentionPolicy.RUNTIME)
@interface A {}

@interface B { int value(); }

class T0x1E {
    void m0x1E() {
        Class<Object> c = @A Object.class;
    }

    Class<?> c = @A String.class;

    Class<? extends @A String> as = @A String.class;
}

class ClassLiterals {
    public static void main(String[] args) {
        if (String.class != @A String.class) throw new Error();
        if (@A int.class != int.class) throw new Error();
        if (@A int.class != Integer.TYPE) throw new Error();
        if (@A int @B(0) [].class != int[].class) throw new Error();

        if (String[].class != @A String[].class) throw new Error();
        if (String[].class != String @A [].class) throw new Error();
        if (@A int[].class != int[].class) throw new Error();
        if (@A int @B(0) [].class != int[].class) throw new Error();
    }

    Object classLit1 = @A String @C [] @B(0) [].class;
    Object classLit2 = @A String @C []       [].class;
    Object classLit3 = @A String    [] @B(0) [].class;
    Object classLit4 =    String    [] @B(0) [].class;
    Object classLit5 =    String @C []       [].class;
    Object classLit6 =    String    []       [].class;
}
