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
 * @summary Test population of reference info for field
 * @compile -g Driver.java ReferenceInfoUtil.java Fields.java
 * @run main Driver Fields
 */
public class Fields {

    // field types
    @TADescription(annotation = "TA", type = FIELD)
    public String fieldAsPrimitive() {
        return "@TA int test;";
    }

    @TADescription(annotation = "TA", type = FIELD)
    public String fieldAsObject() {
        return "@TA Object test;";
    }

    @TADescriptions({
        @TADescription(annotation = "TA", type = FIELD),
        @TADescription(annotation = "TB", type = FIELD,
                genericLocation = { 3, 0 }),
        @TADescription(annotation = "TC", type = FIELD,
                genericLocation = { 3, 1 }),
        @TADescription(annotation = "TD", type = FIELD,
                genericLocation = { 3, 1, 3, 0 })
    })
    public String fieldAsParametrized() {
        return "@TA Map<@TB String, @TC List<@TD String>> test;";
    }

    @TADescriptions({
        @TADescription(annotation = "TA", type = FIELD),
        @TADescription(annotation = "TB", type = FIELD,
                genericLocation = { 0, 0 }),
        @TADescription(annotation = "TC", type = FIELD,
                genericLocation = { 0, 0, 0, 0 })
    })
    public String fieldAsArray() {
        return "@TC String @TA [] @TB [] test;";
    }

    @TADescriptions({
        @TADescription(annotation = "TA", type = FIELD),
        @TADescription(annotation = "TB", type = FIELD,
                genericLocation = { 0, 0 }),
        @TADescription(annotation = "TC", type = FIELD,
                genericLocation = { 0, 0, 0, 0 })
    })
    public String fieldAsArrayOld() {
        return "@TC String test @TA [] @TB [];";
    }

    @TADescriptions({})
    public String fieldWithDeclarationAnnotatin() {
        return "@Decl String test;";
    }

    @TADescriptions({})
    public String fieldWithNoTargetAnno() {
        return "@A String test;";
    }

    // Smoke tests
    @TADescription(annotation = "TA", type = FIELD)
    public String interfacefieldAsObject() {
        return "interface Test { @TA String test = null; }";
    }

    @TADescription(annotation = "TA", type = FIELD)
    public String abstractfieldAsObject() {
        return "abstract class Test { @TA String test; }";
    }

    @TADescriptions({
        @TADescription(annotation = "TA", type = FIELD),
        @TADescription(annotation = "TB", type = FIELD,
                genericLocation = { 3, 0 }),
        @TADescription(annotation = "TC", type = FIELD,
                genericLocation = { 3, 1 }),
        @TADescription(annotation = "TD", type = FIELD,
                genericLocation = { 3, 1, 3, 0 })
    })
    public String interfacefieldAsParametrized() {
        return "interface Test { @TA Map<@TB String, @TC List<@TD String>> test = null; }";
    }


    @TADescriptions({
        @TADescription(annotation = "TA", type = FIELD),
        @TADescription(annotation = "TB", type = FIELD,
                genericLocation = { 3, 0 }),
        @TADescription(annotation = "TC", type = FIELD,
                genericLocation = { 3, 1 }),
        @TADescription(annotation = "TD", type = FIELD,
                genericLocation = { 3, 1, 3, 0 })
    })
    public String staticFieldAsParametrized() {
        return "static @TA Map<@TB String, @TC List<@TD String>> test;";
    }

}
