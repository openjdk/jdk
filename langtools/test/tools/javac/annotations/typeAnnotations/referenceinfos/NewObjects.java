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

import static com.sun.tools.classfile.TypeAnnotation.TargetType.*;

/*
 * @test
 * @summary Test population of reference info for new object creations
 * @compile -g Driver.java ReferenceInfoUtil.java NewObjects.java
 * @run main Driver NewObjects
 */
public class NewObjects {

    @TADescription(annotation = "TA", type = NEW, offset = ReferenceInfoUtil.IGNORE_VALUE)
    public String returnObject() {
        return "Object returnObject() { return new @TA String(); }";
    }

    @TADescriptions({
        @TADescription(annotation = "TA", type = NEW, offset = ReferenceInfoUtil.IGNORE_VALUE),
        @TADescription(annotation = "TB", type = NEW,
                genericLocation = { 3, 0 }, offset = ReferenceInfoUtil.IGNORE_VALUE)
    })
    public String returnObjectGeneric() {
        return "Object returnObjectGeneric() { return new @TA ArrayList<@TB String>(); }";
    }

    @TADescription(annotation = "TA", type = NEW, offset = ReferenceInfoUtil.IGNORE_VALUE)
    public String initObject() {
        return "void initObject() { Object a =  new @TA String(); }";
    }

    @TADescriptions({
        @TADescription(annotation = "TA", type = NEW, offset = ReferenceInfoUtil.IGNORE_VALUE),
        @TADescription(annotation = "TB", type = NEW,
                genericLocation = { 3, 0 }, offset = ReferenceInfoUtil.IGNORE_VALUE),
        @TADescription(annotation = "TC", type = NEW,
                genericLocation = { 3, 1 }, offset = ReferenceInfoUtil.IGNORE_VALUE)
    })
    public String initObjectGeneric() {
        return "void initObjectGeneric() { Object a = new @TA HashMap<@TB String, @TC String>(); }";
    }

    @TADescription(annotation = "TA", type = NEW, offset = ReferenceInfoUtil.IGNORE_VALUE)
    public String eqtestObject() {
        return "void eqtestObject() { if (null == new @TA String()); }";
    }

    @TADescriptions({
        @TADescription(annotation = "TA", type = NEW, offset = ReferenceInfoUtil.IGNORE_VALUE),
        @TADescription(annotation = "TB", type = NEW,
                genericLocation = { 3, 0 }, offset = ReferenceInfoUtil.IGNORE_VALUE)
    })
    public String eqtestObjectGeneric() {
        return "void eqtestObjectGeneric() { if (null == new @TA ArrayList<@TB String >()); }";
    }

    @TADescriptions({
        @TADescription(annotation = "TA", type = NEW, offset = ReferenceInfoUtil.IGNORE_VALUE,
                genericLocation = {0, 0}),
        @TADescription(annotation = "TB", type = NEW, offset = ReferenceInfoUtil.IGNORE_VALUE)
    })
    public String returnNewArray1() {
        return "Object returnNewArray1() { return new @TA String @TB[1]; }";
    }

    @TADescriptions({
        @TADescription(annotation = "TA", type = NEW, offset = ReferenceInfoUtil.IGNORE_VALUE,
                genericLocation = {0, 0, 0, 0}),
        @TADescription(annotation = "TB", type = NEW, offset = ReferenceInfoUtil.IGNORE_VALUE),
        @TADescription(annotation = "TC", type = NEW, offset = ReferenceInfoUtil.IGNORE_VALUE,
                genericLocation = {0, 0}),
    })
    public String returnNewArray2() {
        return "Object returnNewArray2() { return new @TA String @TB [1] @TC [2]; }";
    }

    @TADescriptions({
        @TADescription(annotation = "TA", type = NEW, offset = ReferenceInfoUtil.IGNORE_VALUE,
                genericLocation = {0, 0, 0, 0}),
        @TADescription(annotation = "TB", type = NEW, offset = ReferenceInfoUtil.IGNORE_VALUE,
                genericLocation = {0, 0, 0, 0, 1, 0}),
        @TADescription(annotation = "TC", type = NEW, offset = ReferenceInfoUtil.IGNORE_VALUE),
        @TADescription(annotation = "TD", type = NEW, offset = ReferenceInfoUtil.IGNORE_VALUE,
                genericLocation = {0, 0}),
    })
    public String returnNewArray3() {
        return "Object returnNewArray3() { return new @TA Outer. @TB Inner @TC [1] @TD [2]; }";
    }

    @TADescriptions({
        @TADescription(annotation = "TA", type = NEW, offset = ReferenceInfoUtil.IGNORE_VALUE,
                genericLocation = {0, 0, 0, 0}),
        @TADescription(annotation = "TB", type = NEW, offset = ReferenceInfoUtil.IGNORE_VALUE,
                genericLocation = {0, 0, 0, 0, 1, 0}),
        @TADescription(annotation = "TC", type = NEW, offset = ReferenceInfoUtil.IGNORE_VALUE,
                genericLocation = {0, 0, 0, 0, 1, 0, 1, 0}),
        @TADescription(annotation = "TD", type = NEW, offset = ReferenceInfoUtil.IGNORE_VALUE),
        @TADescription(annotation = "TE", type = NEW, offset = ReferenceInfoUtil.IGNORE_VALUE,
                genericLocation = {0, 0}),
    })
    public String returnNewArray4() {
        return "Object returnNewArray4() { return new @TA Outer. @TB Middle. @TC MInner @TD [1] @TE [2]; }";
    }

    @TADescriptions({
        @TADescription(annotation = "TA", type = NEW, offset = ReferenceInfoUtil.IGNORE_VALUE),
        @TADescription(annotation = "TB", type = NEW, offset = ReferenceInfoUtil.IGNORE_VALUE,
                genericLocation = {3, 0, 0, 0, 0, 0}),
        @TADescription(annotation = "TC", type = NEW, offset = ReferenceInfoUtil.IGNORE_VALUE,
                genericLocation = {3, 0, 0, 0, 0, 0, 1, 0}),
        @TADescription(annotation = "TD", type = NEW, offset = ReferenceInfoUtil.IGNORE_VALUE,
                genericLocation = {3, 0, 0, 0, 0, 0, 1, 0, 1, 0}),
        @TADescription(annotation = "TE", type = NEW, offset = ReferenceInfoUtil.IGNORE_VALUE,
                genericLocation = {3, 0}),
        @TADescription(annotation = "TF", type = NEW, offset = ReferenceInfoUtil.IGNORE_VALUE,
                genericLocation = {3, 0, 0, 0}),
    })
    public String returnNewArray5() {
        return "Object returnNewArray5() { return new @TA ArrayList<@TB Outer. @TC Middle. @TD MInner @TE [] @TF []>(); }";
    }

    @TADescriptions({
        @TADescription(annotation = "TA", type = FIELD, offset = ReferenceInfoUtil.IGNORE_VALUE,
                genericLocation = {0, 0, 0, 0}),
        @TADescription(annotation = "TB", type = FIELD, offset = ReferenceInfoUtil.IGNORE_VALUE,
                genericLocation = {0, 0, 0, 0, 1, 0}),
        @TADescription(annotation = "TC", type = FIELD, offset = ReferenceInfoUtil.IGNORE_VALUE),
        @TADescription(annotation = "TD", type = FIELD, offset = ReferenceInfoUtil.IGNORE_VALUE,
        genericLocation = {0, 0}),
    })
    public String arrayField() {
        return "@TA Outer. @TB Inner @TC [] @TD [] f;";
    }
}
