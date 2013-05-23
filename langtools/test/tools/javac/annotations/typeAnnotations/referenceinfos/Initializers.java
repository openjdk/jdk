/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 1234567
 * @summary Test population of reference info for instance and class initializers
 * @author Werner Dietl
 * @compile -g Driver.java ReferenceInfoUtil.java Initializers.java
 * @run main Driver Initializers
 */
public class Initializers {

    @TADescriptions({
        @TADescription(annotation = "TA", type = NEW, offset = ReferenceInfoUtil.IGNORE_VALUE),
        @TADescription(annotation = "TB", type = NEW,
                genericLocation = { 3, 0 }, offset = ReferenceInfoUtil.IGNORE_VALUE)
    })
    public String instanceInit1() {
        return "class Test { { Object o = new @TA ArrayList<@TB String>(); } }";
    }

    @TADescriptions({
        @TADescription(annotation = "TA", type = NEW, offset = ReferenceInfoUtil.IGNORE_VALUE),
        @TADescription(annotation = "TB", type = NEW,
                genericLocation = { 3, 0 }, offset = ReferenceInfoUtil.IGNORE_VALUE),
        @TADescription(annotation = "TC", type = NEW, offset = ReferenceInfoUtil.IGNORE_VALUE),
        @TADescription(annotation = "TD", type = NEW,
                genericLocation = { 3, 0 }, offset = ReferenceInfoUtil.IGNORE_VALUE)
    })
    public String instanceInit2() {
        return "class Test { Object f = new @TA ArrayList<@TB String>(); " +
                " { Object o = new @TC ArrayList<@TD String>(); } }";
    }

    @TADescriptions({
        @TADescription(annotation = "TA", type = NEW, offset = ReferenceInfoUtil.IGNORE_VALUE),
        @TADescription(annotation = "TB", type = NEW,
                genericLocation = { 3, 0 }, offset = ReferenceInfoUtil.IGNORE_VALUE)
    })
    public String staticInit1() {
        return "class Test { static { Object o = new @TA ArrayList<@TB String>(); } }";
    }

    @TADescriptions({
        @TADescription(annotation = "TA", type = NEW, offset = ReferenceInfoUtil.IGNORE_VALUE),
        @TADescription(annotation = "TB", type = NEW,
                genericLocation = { 3, 0 }, offset = ReferenceInfoUtil.IGNORE_VALUE),
        @TADescription(annotation = "TC", type = NEW, offset = ReferenceInfoUtil.IGNORE_VALUE),
        @TADescription(annotation = "TD", type = NEW,
                genericLocation = { 3, 0 }, offset = ReferenceInfoUtil.IGNORE_VALUE),
        @TADescription(annotation = "TE", type = NEW, offset = ReferenceInfoUtil.IGNORE_VALUE),
        @TADescription(annotation = "TF", type = NEW,
                genericLocation = { 3, 0 }, offset = ReferenceInfoUtil.IGNORE_VALUE)
    })
    public String staticInit2() {
        return "class Test { Object f = new @TA ArrayList<@TB String>(); " +
                " static Object g = new @TC ArrayList<@TD String>(); " +
                " static { Object o = new @TE ArrayList<@TF String>(); } }";
    }

    // TODO: test interaction with several constructors, especially non-initial constuctors.
    // I don't think this kind of test is possible here.

    @TADescriptions({
        @TADescription(annotation = "TA", type = CAST,
                typeIndex = 0, offset = ReferenceInfoUtil.IGNORE_VALUE),
    })
    public String lazyConstantCast1() {
        return "class Test { public static final Object o = (@TA Object) null; }";
    }

}
