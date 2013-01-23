/*
 * Copyright (c) 2012 Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test population of reference info for constructor results
 * @compile -g Driver.java ReferenceInfoUtil.java Constructors.java
 * @run main Driver Constructors
 */
public class Constructors {

    @TADescriptions({
        @TADescription(annotation = "TA", type = METHOD_RETURN),
        @TADescription(annotation = "TB", type = METHOD_RETURN),
        @TADescription(annotation = "TC", type = METHOD_FORMAL_PARAMETER, paramIndex = 0)
    })
    public String regularClass() {
        return "class Test { @TA Test() {}" +
                           " @TB Test(@TC int b) {} }";
    }

    @TADescriptions({
        @TADescription(annotation = "TA", type = METHOD_RETURN),
        @TADescription(annotation = "TB", type = METHOD_RETURN),
        @TADescription(annotation = "TC", type = METHOD_FORMAL_PARAMETER, paramIndex = 0)
    })
    @TestClass("Test$Inner")
    public String innerClass() {
        return "class Test { class Inner {" +
               " @TA Inner() {}" +
               " @TB Inner(@TC int b) {}" +
               " } }";
    }

    /* TODO: Outer.this annotation support.
    @TADescriptions({
        @TADescription(annotation = "TA", type = METHOD_RECEIVER),
        @TADescription(annotation = "TB", type = METHOD_RETURN),
        @TADescription(annotation = "TC", type = METHOD_RECEIVER),
        @TADescription(annotation = "TD", type = METHOD_RETURN),
        @TADescription(annotation = "TE", type = METHOD_FORMAL_PARAMETER, paramIndex = 0)
    })
    @TestClass("Test$Inner")
    public String innerClass2() {
        return "class Test { class Inner {" +
               " @TB Inner(@TA Test Test.this) {}" +
               " @TD Inner(@TC Test Test.this, @TE int b) {}" +
               " } }";
    }
    */
}
