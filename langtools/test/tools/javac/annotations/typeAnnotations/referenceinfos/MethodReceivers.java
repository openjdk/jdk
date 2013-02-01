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

import static com.sun.tools.classfile.TypeAnnotation.TargetType.*;

/*
 * @test
 * @summary Test population of reference info for method receivers
 * @compile -g Driver.java ReferenceInfoUtil.java MethodReceivers.java
 * @run main Driver MethodReceivers
 */
public class MethodReceivers {

    @TADescription(annotation = "TA", type = METHOD_RECEIVER)
    public String regularMethod() {
        return "class Test { void test(@TA Test this) { } }";
    }

    @TADescription(annotation = "TA", type = METHOD_RECEIVER)
    public String abstractMethod() {
        return "abstract class Test { abstract void test(@TA Test this); }";
    }

    @TADescription(annotation = "TA", type = METHOD_RECEIVER)
    public String interfaceMethod() {
        return "interface Test { void test(@TA Test this); }";
    }

    @TADescription(annotation = "TA", type = METHOD_RECEIVER)
    public String regularWithThrows() {
        return "class Test { void test(@TA Test this) throws Exception { } }";
    }

    @TADescriptions({
        @TADescription(annotation = "TA", type = METHOD_RECEIVER,
                genericLocation = {}),
        @TADescription(annotation = "TB", type = METHOD_RECEIVER,
                genericLocation = {1, 0})
    })
    @TestClass("TestOuter$TestInner")
    public String nestedtypes1() {
        return "class TestOuter { class TestInner { void test(@TA TestOuter. @TB TestInner this) { } } }";
    }

    @TADescription(annotation = "TA", type = METHOD_RECEIVER,
            genericLocation = {})
    @TestClass("TestOuter$TestInner")
    public String nestedtypes2() {
        return "class TestOuter { class TestInner { void test(@TA TestOuter.TestInner this) { } } }";
    }

    @TADescription(annotation = "TB", type = METHOD_RECEIVER,
            genericLocation = {1, 0})
    @TestClass("TestOuter$TestInner")
    public String nestedtypes3() {
        return "class TestOuter { class TestInner { void test(TestOuter. @TB TestInner this) { } } }";
    }

}
