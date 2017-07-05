/*
 * Copyright (c) 2009, 2013, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.annotation.*;
import static java.lang.annotation.ElementType.*;

import java.util.*;
import java.io.*;

/*
 * @Xtest
 * @summary compiler accepts all values
 * @author Mahmood Ali
 * @author Yuri Gaevsky
 * @compile TargetTypes.java
 */

@Target({TYPE_USE, TYPE_PARAMETER, TYPE})
@Retention(RetentionPolicy.RUNTIME)
@interface A {}

/** wildcard bound */
class T0x1C {
    void m0x1C(List<? extends @A String> lst) {}
}

/** wildcard bound generic/array */
class T0x1D<T> {
    void m0x1D(List<? extends @A List<int[]>> lst) {}
}

/** typecast */
class T0x00 {
    void m0x00(Long l1) {
        Object l2 = (@A Long) l1;
    }
}

/** typecast generic/array */
class T0x01<T> {
    void m0x01(List<T> list) {
        List<T> l = (List<@A T>) list;
    }
}

/** instanceof */
class T0x02 {
    boolean m0x02(String s) {
        return (s instanceof @A String);
    }
}

/** object creation (new) */
class T0x04 {
    void m0x04() {
        new @A ArrayList<String>();
    }
}

/** local variable */
class T0x08 {
    void m0x08() {
      @A String s = null;
    }
}

/** method parameter generic/array */
class T0x0D {
    void m0x0D(HashMap<@A Object, List<@A List<@A Class>>> s1) {}
}

/** method receiver */
class T0x06 {
    void m0x06(@A T0x06 this) {}
}

/** method return type generic/array */
class T0x0B {
    Class<@A Object> m0x0B() { return null; }
}

/** field generic/array */
class T0x0F {
    HashMap<@A Object, @A Object> c1;
}

/** method type parameter */
class T0x20<T, U> {
    <@A T, @A U> void m0x20() {}
}

/** class type parameter */
class T0x22<@A T, @A U> {
}

/** class type parameter bound */
class T0x10<T extends @A Object> {
}

/** method type parameter bound */
class T0x12<T> {
    <T extends @A Object> void m0x12() {}
}

/** class type parameter bound generic/array */
class T0x11<T extends List<@A T>> {
}


/** method type parameter bound generic/array */
class T0x13 {
    static <T extends Comparable<@A T>> T m0x13() {
        return null;
    }
}

/** class extends/implements generic/array */
class T0x15<T> extends ArrayList<@A T> {
}

/** type test (instanceof) generic/array */
class T0x03<T> {
    void m0x03(T typeObj, Object obj) {
        boolean ok = obj instanceof String @A [];
    }
}

/** object creation (new) generic/array */
class T0x05<T> {
    void m0x05() {
        new ArrayList<@A T>();
    }
}

/** local variable generic/array */
class T0x09<T> {
    void g() {
        List<@A String> l = null;
    }

    void a() {
        String @A [] as = null;
    }
}

/** type argument in constructor call generic/array */
class T0x19 {
    <T> T0x19() {}

    void g() {
       new <List<@A String>> T0x19();
    }
}

/** type argument in method call generic/array */
class T0x1B<T> {
    void m0x1B() {
        Collections.<T @A []>emptyList();
    }
}

/** type argument in constructor call */
class T0x18<T> {
    <T> T0x18() {}

    void m() {
        new <@A Integer> T0x18();
    }
}

/** type argument in method call */
class T0x1A<T,U> {
    public static <T, U> T m() { return null; }
    static void m0x1A() {
        T0x1A.<@A Integer, @A Short>m();
    }
}

/** class extends/implements */
class T0x14 extends @A Object implements @A Serializable, @A Cloneable {
}

/** exception type in throws */
class T0x16 {
    void m0x16() throws @A Exception {}
}

/** resource variables **/
class ResourceVariables {
    void m() throws Exception {
        try (@A InputStream is = new @A FileInputStream("x")){}
    }
}

/** exception parameters **/
class ExceptionParameters {
    void multipleExceptions() {
        try {
            new Object();
        } catch (@A Exception e) {}
        try {
            new Object();
        } catch (@A Exception e) {}
        try {
            new Object();
        } catch (@A Exception e) {}
    }
}
