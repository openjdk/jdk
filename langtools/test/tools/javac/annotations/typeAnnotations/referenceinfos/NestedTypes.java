/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test population of reference info for nested types
 * @compile -g Driver.java ReferenceInfoUtil.java NestedTypes.java
 * @run main Driver NestedTypes
 */
public class NestedTypes {

    // method parameters

    @TADescriptions({
        @TADescription(annotation = "TA", type = METHOD_FORMAL_PARAMETER,
                genericLocation = {}, paramIndex = 0),
        @TADescription(annotation = "TB", type = METHOD_FORMAL_PARAMETER,
                genericLocation = {1, 0}, paramIndex = 0)
    })
    public String testParam1() {
        return "void test(@TA Outer.@TB Inner a) { }";
    }

    @TADescriptions({
        @TADescription(annotation = "TA", type = METHOD_FORMAL_PARAMETER,
                genericLocation = {3, 0}, paramIndex = 0),
        @TADescription(annotation = "TB", type = METHOD_FORMAL_PARAMETER,
                genericLocation = {3, 0, 1, 0}, paramIndex = 0)
    })
    public String testParam1b() {
        return "void test(List<@TA Outer.@TB Inner> a) { }";
    }

    // TODO: the tests that use @TA Map.Entry should fail, as
    // Map cannot be annotated.
    // We need some tests for the fully qualified name syntax.
    /*
    @TADescription(annotation = "TA", type = METHOD_FORMAL_PARAMETER,
            genericLocation = {}, paramIndex = 0)
    public String testParam1c() {
        return "void test(java.util.@TA Map.Entry a) { }";
    }

    @TADescriptions({
        @TADescription(annotation = "TA", type = METHOD_FORMAL_PARAMETER,
                genericLocation = {}, paramIndex = 0),
        @TADescription(annotation = "TB", type = METHOD_FORMAL_PARAMETER,
                genericLocation = {1, 0}, paramIndex = 0)
    })
    public String testParam1d() {
        return "void test(java.util.@TA Map.@TB Entry a) { }";
    }

    @TADescription(annotation = "TA", type = METHOD_FORMAL_PARAMETER,
            genericLocation = {3, 0}, paramIndex = 0)
    public String testParam1e() {
        return "void test(List<java.util.@TA Map.Entry> a) { }";
    }

    @TADescriptions({
        @TADescription(annotation = "TA", type = METHOD_FORMAL_PARAMETER,
                genericLocation = {3, 0}, paramIndex = 0),
        @TADescription(annotation = "TB", type = METHOD_FORMAL_PARAMETER,
                genericLocation = {3, 0, 1, 0}, paramIndex = 0)
    })
    public String testParam1f() {
        return "void test(List<java.util.@TA Map. @TB Entry> a) { }";
    }
    */

    @TADescription(annotation = "TB", type = METHOD_FORMAL_PARAMETER,
           genericLocation = {3, 0}, paramIndex = 0)
    public String testParam1g() {
        return "void test(List<java.util.Map. @TB Entry> a) { }";
    }

    @TADescriptions({
        @TADescription(annotation = "TA", type = METHOD_FORMAL_PARAMETER,
                genericLocation = {}, paramIndex = 0),
        @TADescription(annotation = "TB", type = METHOD_FORMAL_PARAMETER,
                genericLocation = {1, 0}, paramIndex = 0)
    })
    public String testParam2() {
        return "void test(@TA GOuter<String,String>.@TB GInner<String,String> a) { }";
    }

    @TADescriptions({
        @TADescription(annotation = "TA", type = METHOD_FORMAL_PARAMETER,
                genericLocation = {3, 0}, paramIndex = 0),
        @TADescription(annotation = "TB", type = METHOD_FORMAL_PARAMETER,
                genericLocation = {3, 0, 1, 0}, paramIndex = 0)
    })
    public String testParam2b() {
        return "void test(List<@TA GOuter<String,String>.@TB GInner<String,String>> a) { }";
    }

    @TADescriptions({
        @TADescription(annotation = "TA", type = METHOD_FORMAL_PARAMETER,
                genericLocation = {0, 0, 0, 0}, paramIndex = 0),
        @TADescription(annotation = "TB", type = METHOD_FORMAL_PARAMETER,
                genericLocation = {0, 0, 0, 0, 1, 0}, paramIndex = 0),
        @TADescription(annotation = "TC", type = METHOD_FORMAL_PARAMETER,
                genericLocation = {0, 0, 0, 0, 1, 0, 3, 0}, paramIndex = 0),
        @TADescription(annotation = "TD", type = METHOD_FORMAL_PARAMETER,
                genericLocation = {0, 0, 0, 0, 1, 0, 3, 0, 3, 0, 0, 0, 0, 0}, paramIndex = 0),
        @TADescription(annotation = "TE", type = METHOD_FORMAL_PARAMETER,
                genericLocation = {0, 0, 0, 0, 1, 0, 3, 0, 3, 0}, paramIndex = 0),
        @TADescription(annotation = "TF", type = METHOD_FORMAL_PARAMETER,
                genericLocation = {0, 0, 0, 0, 1, 0, 3, 0, 3, 0, 0, 0}, paramIndex = 0),
        @TADescription(annotation = "TG", type = METHOD_FORMAL_PARAMETER,
                genericLocation = {0, 0, 0, 0, 1, 0, 1, 0}, paramIndex = 0),
        @TADescription(annotation = "TH", type = METHOD_FORMAL_PARAMETER,
                genericLocation = {0, 0, 0, 0, 1, 0, 1, 0, 3, 0}, paramIndex = 0),
        @TADescription(annotation = "TI", type = METHOD_FORMAL_PARAMETER,
                genericLocation = {0, 0, 0, 0, 1, 0, 1, 0, 3, 1}, paramIndex = 0),
        @TADescription(annotation = "TJ", type = METHOD_FORMAL_PARAMETER, paramIndex = 0),
        @TADescription(annotation = "TK", type = METHOD_FORMAL_PARAMETER,
                genericLocation = {0, 0}, paramIndex = 0)
    })
    public String testParam3() {
        return "class Outer {\n" +
                " class GInner<X> {\n" +
                "  class GInner2<Y, Z> {}\n" +
                "}}\n\n" +
                "class Test {\n" +
                " void test(@TA Outer . @TB GInner<@TC List<@TD Object @TE[] @TF[]>>. @TG GInner2<@TH Integer, @TI Object> @TJ[] @TK[] a) { }\n" +
                "}";
    }

    @TADescriptions({
        @TADescription(annotation = "TA", type = METHOD_FORMAL_PARAMETER,
                genericLocation = {3, 0, 0, 0, 0, 0}, paramIndex = 0),
        @TADescription(annotation = "TB", type = METHOD_FORMAL_PARAMETER,
                genericLocation = {3, 0, 0, 0, 0, 0, 1, 0}, paramIndex = 0),
        @TADescription(annotation = "TC", type = METHOD_FORMAL_PARAMETER,
                genericLocation = {3, 0, 0, 0, 0, 0, 1, 0, 3, 0}, paramIndex = 0),
        @TADescription(annotation = "TD", type = METHOD_FORMAL_PARAMETER,
                genericLocation = {3, 0, 0, 0, 0, 0, 1, 0, 3, 0, 3, 0, 0, 0, 0, 0}, paramIndex = 0),
        @TADescription(annotation = "TE", type = METHOD_FORMAL_PARAMETER,
                genericLocation = {3, 0, 0, 0, 0, 0, 1, 0, 3, 0, 3, 0}, paramIndex = 0),
        @TADescription(annotation = "TF", type = METHOD_FORMAL_PARAMETER,
                genericLocation = {3, 0, 0, 0, 0, 0, 1, 0, 3, 0, 3, 0, 0, 0}, paramIndex = 0),
        @TADescription(annotation = "TG", type = METHOD_FORMAL_PARAMETER,
                genericLocation = {3, 0, 0, 0, 0, 0, 1, 0, 1, 0}, paramIndex = 0),
        @TADescription(annotation = "TH", type = METHOD_FORMAL_PARAMETER,
                genericLocation = {3, 0, 0, 0, 0, 0, 1, 0, 1, 0, 3, 0}, paramIndex = 0),
        @TADescription(annotation = "TI", type = METHOD_FORMAL_PARAMETER,
                genericLocation = {3, 0, 0, 0, 0, 0, 1, 0, 1, 0, 3, 1}, paramIndex = 0),
        @TADescription(annotation = "TJ", type = METHOD_FORMAL_PARAMETER,
                genericLocation = {3, 0}, paramIndex = 0),
        @TADescription(annotation = "TK", type = METHOD_FORMAL_PARAMETER,
                genericLocation = {3, 0, 0, 0}, paramIndex = 0)
    })
    public String testParam4() {
        return "class Outer {\n" +
                " class GInner<X> {\n" +
                "  class GInner2<Y, Z> {}\n" +
                "}}\n\n" +
                "class Test {\n" +
                " void test(List<@TA Outer . @TB GInner<@TC List<@TD Object @TE[] @TF[]>>. @TG GInner2<@TH Integer, @TI Object> @TJ[] @TK[]> a) { }\n" +
                "}";
    }


    // Local variables

    @TADescriptions({
        @TADescription(annotation = "TA", type = LOCAL_VARIABLE,
                genericLocation = {},
                lvarOffset = {2}, lvarLength = {1}, lvarIndex = {1}),
        @TADescription(annotation = "TB", type = LOCAL_VARIABLE,
                genericLocation = {1, 0},
                lvarOffset = {2}, lvarLength = {1}, lvarIndex = {1})
    })
    public String testLocal1a() {
        return "void test() { @TA Outer.@TB Inner a = null; }";
    }

    @TADescription(annotation = "TA", type = LOCAL_VARIABLE,
            genericLocation = {},
            lvarOffset = {2}, lvarLength = {1}, lvarIndex = {1})
    public String testLocal1b() {
        return "void test() { @TA Outer.Inner a = null; }";
    }

    @TADescription(annotation = "TB", type = LOCAL_VARIABLE,
            genericLocation = {1, 0},
            lvarOffset = {2}, lvarLength = {1}, lvarIndex = {1})
    public String testLocal1c() {
        return "void test() { Outer.@TB Inner a = null; }";
    }

    @TADescriptions({
        @TADescription(annotation = "TA", type = LOCAL_VARIABLE,
                genericLocation = {},
                lvarOffset = {2}, lvarLength = {1}, lvarIndex = {1}),
        @TADescription(annotation = "TB", type = LOCAL_VARIABLE,
                genericLocation = {1, 0},
                lvarOffset = {2}, lvarLength = {1}, lvarIndex = {1})
    })
    public String testLocal2() {
        return "void test() { @TA GOuter<String,String>.@TB GInner<String,String> a = null; }";
    }

    @TADescriptions({
        @TADescription(annotation = "TA", type = LOCAL_VARIABLE,
                genericLocation = {0, 0, 0, 0},
                lvarOffset = {5}, lvarLength = {1}, lvarIndex = {1}),
        @TADescription(annotation = "TB", type = LOCAL_VARIABLE,
                genericLocation = {0, 0, 0, 0, 1, 0},
                lvarOffset = {5}, lvarLength = {1}, lvarIndex = {1}),
        @TADescription(annotation = "TC", type = LOCAL_VARIABLE,
                genericLocation = {0, 0, 0, 0, 1, 0, 3, 0},
                lvarOffset = {5}, lvarLength = {1}, lvarIndex = {1}),
        @TADescription(annotation = "TD", type = LOCAL_VARIABLE,
                genericLocation = {0, 0, 0, 0, 1, 0, 3, 0, 3, 0, 0, 0, 0, 0},
                lvarOffset = {5}, lvarLength = {1}, lvarIndex = {1}),
        @TADescription(annotation = "TE", type = LOCAL_VARIABLE,
                genericLocation = {0, 0, 0, 0, 1, 0, 3, 0, 3, 0},
                lvarOffset = {5}, lvarLength = {1}, lvarIndex = {1}),
        @TADescription(annotation = "TF", type = LOCAL_VARIABLE,
                genericLocation = {0, 0, 0, 0, 1, 0, 3, 0, 3, 0, 0, 0},
                lvarOffset = {5}, lvarLength = {1}, lvarIndex = {1}),
        @TADescription(annotation = "TG", type = LOCAL_VARIABLE,
                genericLocation = {0, 0, 0, 0, 1, 0, 1, 0},
                lvarOffset = {5}, lvarLength = {1}, lvarIndex = {1}),
        @TADescription(annotation = "TH", type = LOCAL_VARIABLE,
                genericLocation = {0, 0, 0, 0, 1, 0, 1, 0, 3, 0},
                lvarOffset = {5}, lvarLength = {1}, lvarIndex = {1}),
        @TADescription(annotation = "TI", type = LOCAL_VARIABLE,
                genericLocation = {0, 0, 0, 0, 1, 0, 1, 0, 3, 1},
                lvarOffset = {5}, lvarLength = {1}, lvarIndex = {1}),
        @TADescription(annotation = "TJ", type = LOCAL_VARIABLE,
                genericLocation = {},
                lvarOffset = {5}, lvarLength = {1}, lvarIndex = {1}),
        @TADescription(annotation = "TK", type = LOCAL_VARIABLE,
                genericLocation = {0, 0},
                lvarOffset = {5}, lvarLength = {1}, lvarIndex = {1})
    })
    public String testLocal3() {
        return "class Outer {\n" +
                " class GInner<X> {\n" +
                "  class GInner2<Y, Z> {}\n" +
                "}}\n\n" +
                "class Test {\n" +
                " void test() { @TA Outer . @TB GInner<@TC List<@TD Object @TE[] @TF[]>>. @TG GInner2<@TH Integer, @TI Object> @TJ[] @TK[] a = null; }\n" +
                "}";
    }

    @TADescriptions({
        @TADescription(annotation = "TA", type = LOCAL_VARIABLE,
                genericLocation = {3, 0, 0, 0, 0, 0},
                lvarOffset = {2}, lvarLength = {1}, lvarIndex = {1}),
        @TADescription(annotation = "TB", type = LOCAL_VARIABLE,
                genericLocation = {3, 0, 0, 0, 0, 0, 1, 0},
                lvarOffset = {2}, lvarLength = {1}, lvarIndex = {1}),
        @TADescription(annotation = "TC", type = LOCAL_VARIABLE,
                genericLocation = {3, 0, 0, 0, 0, 0, 1, 0, 3, 0},
                lvarOffset = {2}, lvarLength = {1}, lvarIndex = {1}),
        @TADescription(annotation = "TD", type = LOCAL_VARIABLE,
                genericLocation = {3, 0, 0, 0, 0, 0, 1, 0, 3, 0, 3, 0, 0, 0, 0, 0},
                lvarOffset = {2}, lvarLength = {1}, lvarIndex = {1}),
        @TADescription(annotation = "TE", type = LOCAL_VARIABLE,
                genericLocation = {3, 0, 0, 0, 0, 0, 1, 0, 3, 0, 3, 0},
                lvarOffset = {2}, lvarLength = {1}, lvarIndex = {1}),
        @TADescription(annotation = "TF", type = LOCAL_VARIABLE,
                genericLocation = {3, 0, 0, 0, 0, 0, 1, 0, 3, 0, 3, 0, 0, 0},
                lvarOffset = {2}, lvarLength = {1}, lvarIndex = {1}),
        @TADescription(annotation = "TG", type = LOCAL_VARIABLE,
                genericLocation = {3, 0, 0, 0, 0, 0, 1, 0, 1, 0},
                lvarOffset = {2}, lvarLength = {1}, lvarIndex = {1}),
        @TADescription(annotation = "TH", type = LOCAL_VARIABLE,
                genericLocation = {3, 0, 0, 0, 0, 0, 1, 0, 1, 0, 3, 0},
                lvarOffset = {2}, lvarLength = {1}, lvarIndex = {1}),
        @TADescription(annotation = "TI", type = LOCAL_VARIABLE,
                genericLocation = {3, 0, 0, 0, 0, 0, 1, 0, 1, 0, 3, 1},
                lvarOffset = {2}, lvarLength = {1}, lvarIndex = {1}),
        @TADescription(annotation = "TJ", type = LOCAL_VARIABLE,
                genericLocation = {3, 0},
                lvarOffset = {2}, lvarLength = {1}, lvarIndex = {1}),
        @TADescription(annotation = "TK", type = LOCAL_VARIABLE,
                genericLocation = {3, 0, 0, 0},
                lvarOffset = {2}, lvarLength = {1}, lvarIndex = {1})
    })
    public String testLocal4() {
        return "class Outer {\n" +
                " class GInner<X> {\n" +
                "  class GInner2<Y, Z> {}\n" +
                "}}\n\n" +
                "class Test {\n" +
                " void test() { List<@TA Outer . @TB GInner<@TC List<@TD Object @TE[] @TF[]>>. @TG GInner2<@TH Integer, @TI Object> @TJ[] @TK[]> a = null; }\n" +
                "}";
    }


    // fields

    @TADescriptions({
        @TADescription(annotation = "TA", type = FIELD,
                genericLocation = {}),
        @TADescription(annotation = "TB", type = FIELD,
                genericLocation = {1, 0})
    })
    public String testField1a() {
        return "@TA Outer.@TB Inner a;";
    }

    @TADescription(annotation = "TA", type = FIELD,
            genericLocation = {})
    public String testField1b() {
        return "@TA Outer.Inner a;";
    }

    @TADescription(annotation = "TB", type = FIELD,
            genericLocation = {1, 0})
    public String testField1c() {
        return "Outer.@TB Inner a;";
    }

    @TADescriptions({
        @TADescription(annotation = "TA", type = FIELD,
                genericLocation = {}),
        @TADescription(annotation = "TB", type = FIELD,
                genericLocation = {1, 0})
    })
    public String testField2() {
        return "@TA GOuter<String,String>.@TB GInner<String,String> a;";
    }

    @TADescriptions({
        @TADescription(annotation = "TA", type = FIELD,
                genericLocation = {0, 0, 0, 0}),
        @TADescription(annotation = "TB", type = FIELD,
                genericLocation = {0, 0, 0, 0, 1, 0}),
        @TADescription(annotation = "TC", type = FIELD,
                genericLocation = {0, 0, 0, 0, 1, 0, 3, 0}),
        @TADescription(annotation = "TD", type = FIELD,
                genericLocation = {0, 0, 0, 0, 1, 0, 3, 0, 3, 0, 0, 0, 0, 0}),
        @TADescription(annotation = "TE", type = FIELD,
                genericLocation = {0, 0, 0, 0, 1, 0, 3, 0, 3, 0}),
        @TADescription(annotation = "TF", type = FIELD,
                genericLocation = {0, 0, 0, 0, 1, 0, 3, 0, 3, 0, 0, 0}),
        @TADescription(annotation = "TG", type = FIELD,
                genericLocation = {0, 0, 0, 0, 1, 0, 1, 0}),
        @TADescription(annotation = "TH", type = FIELD,
                genericLocation = {0, 0, 0, 0, 1, 0, 1, 0, 3, 0}),
        @TADescription(annotation = "TI", type = FIELD,
                genericLocation = {0, 0, 0, 0, 1, 0, 1, 0, 3, 1}),
        @TADescription(annotation = "TJ", type = FIELD),
        @TADescription(annotation = "TK", type = FIELD,
                genericLocation = {0, 0})
    })
    public String testField3() {
        return "class Outer {\n" +
                " class GInner<X> {\n" +
                "  class GInner2<Y, Z> {}\n" +
                "}}\n\n" +
                "class Test {\n" +
                " @TA Outer . @TB GInner<@TC List<@TD Object @TE[] @TF[]>>. @TG GInner2<@TH Integer, @TI Object> @TJ[] @TK[] a;\n" +
                "}";
    }

    @TADescriptions({
        @TADescription(annotation = "TA", type = FIELD,
                genericLocation = {3, 0, 0, 0, 0, 0}),
        @TADescription(annotation = "TB", type = FIELD,
                genericLocation = {3, 0, 0, 0, 0, 0, 1, 0}),
        @TADescription(annotation = "TC", type = FIELD,
                genericLocation = {3, 0, 0, 0, 0, 0, 1, 0, 3, 0}),
        @TADescription(annotation = "TD", type = FIELD,
                genericLocation = {3, 0, 0, 0, 0, 0, 1, 0, 3, 0, 3, 0, 0, 0, 0, 0}),
        @TADescription(annotation = "TE", type = FIELD,
                genericLocation = {3, 0, 0, 0, 0, 0, 1, 0, 3, 0, 3, 0}),
        @TADescription(annotation = "TF", type = FIELD,
                genericLocation = {3, 0, 0, 0, 0, 0, 1, 0, 3, 0, 3, 0, 0, 0}),
        @TADescription(annotation = "TG", type = FIELD,
                genericLocation = {3, 0, 0, 0, 0, 0, 1, 0, 1, 0}),
        @TADescription(annotation = "TH", type = FIELD,
                genericLocation = {3, 0, 0, 0, 0, 0, 1, 0, 1, 0, 3, 0}),
        @TADescription(annotation = "TI", type = FIELD,
                genericLocation = {3, 0, 0, 0, 0, 0, 1, 0, 1, 0, 3, 1}),
        @TADescription(annotation = "TJ", type = FIELD,
                genericLocation = {3, 0}),
        @TADescription(annotation = "TK", type = FIELD,
                genericLocation = {3, 0, 0, 0})
    })
    public String testField4() {
        return "class Outer {\n" +
                " class GInner<X> {\n" +
                "  class GInner2<Y, Z> {}\n" +
                "}}\n\n" +
                "class Test {\n" +
                " List<@TA Outer . @TB GInner<@TC List<@TD Object @TE[] @TF[]>>. @TG GInner2<@TH Integer, @TI Object> @TJ[] @TK[]> a;\n" +
                "}";
    }


    // return types

    @TADescriptions({
        @TADescription(annotation = "TA", type = METHOD_RETURN,
                genericLocation = {}),
        @TADescription(annotation = "TB", type = METHOD_RETURN,
                genericLocation = {1, 0})
    })
    public String testReturn1() {
        return "@TA Outer.@TB Inner test() { return null; }";
    }

    @TADescriptions({
        @TADescription(annotation = "TA", type = METHOD_RETURN,
                genericLocation = {}),
        @TADescription(annotation = "TB", type = METHOD_RETURN,
                genericLocation = {1, 0})
    })
    public String testReturn2() {
        return "@TA GOuter<String,String>.@TB GInner<String,String> test() { return null; }";
    }

    @TADescriptions({
        @TADescription(annotation = "TA", type = METHOD_RETURN,
                genericLocation = {0, 0, 0, 0}),
        @TADescription(annotation = "TB", type = METHOD_RETURN,
                genericLocation = {0, 0, 0, 0, 1, 0}),
        @TADescription(annotation = "TC", type = METHOD_RETURN,
                genericLocation = {0, 0, 0, 0, 1, 0, 3, 0}),
        @TADescription(annotation = "TD", type = METHOD_RETURN,
                genericLocation = {0, 0, 0, 0, 1, 0, 3, 0, 3, 0, 0, 0, 0, 0}),
        @TADescription(annotation = "TE", type = METHOD_RETURN,
                genericLocation = {0, 0, 0, 0, 1, 0, 3, 0, 3, 0}),
        @TADescription(annotation = "TF", type = METHOD_RETURN,
                genericLocation = {0, 0, 0, 0, 1, 0, 3, 0, 3, 0, 0, 0}),
        @TADescription(annotation = "TG", type = METHOD_RETURN,
                genericLocation = {0, 0, 0, 0, 1, 0, 1, 0}),
        @TADescription(annotation = "TH", type = METHOD_RETURN,
                genericLocation = {0, 0, 0, 0, 1, 0, 1, 0, 3, 0}),
        @TADescription(annotation = "TI", type = METHOD_RETURN,
                genericLocation = {0, 0, 0, 0, 1, 0, 1, 0, 3, 1}),
        @TADescription(annotation = "TJ", type = METHOD_RETURN),
        @TADescription(annotation = "TK", type = METHOD_RETURN,
                genericLocation = {0, 0})
    })
    public String testReturn3() {
        return "class Outer {\n" +
                " class GInner<X> {\n" +
                "  class GInner2<Y, Z> {}\n" +
                "}}\n\n" +
                "class Test {\n" +
                " @TA Outer . @TB GInner<@TC List<@TD Object @TE[] @TF[]>>. @TG GInner2<@TH Integer, @TI Object> @TJ[] @TK[] test() { return null; }\n" +
                "}";
    }

    @TADescriptions({
        @TADescription(annotation = "TA", type = METHOD_RETURN,
                genericLocation = {3, 0, 0, 0, 0, 0}),
        @TADescription(annotation = "TB", type = METHOD_RETURN,
                genericLocation = {3, 0, 0, 0, 0, 0, 1, 0}),
        @TADescription(annotation = "TC", type = METHOD_RETURN,
                genericLocation = {3, 0, 0, 0, 0, 0, 1, 0, 3, 0}),
        @TADescription(annotation = "TD", type = METHOD_RETURN,
                genericLocation = {3, 0, 0, 0, 0, 0, 1, 0, 3, 0, 3, 0, 0, 0, 0, 0}),
        @TADescription(annotation = "TE", type = METHOD_RETURN,
                genericLocation = {3, 0, 0, 0, 0, 0, 1, 0, 3, 0, 3, 0}),
        @TADescription(annotation = "TF", type = METHOD_RETURN,
                genericLocation = {3, 0, 0, 0, 0, 0, 1, 0, 3, 0, 3, 0, 0, 0}),
        @TADescription(annotation = "TG", type = METHOD_RETURN,
                genericLocation = {3, 0, 0, 0, 0, 0, 1, 0, 1, 0}),
        @TADescription(annotation = "TH", type = METHOD_RETURN,
                genericLocation = {3, 0, 0, 0, 0, 0, 1, 0, 1, 0, 3, 0}),
        @TADescription(annotation = "TI", type = METHOD_RETURN,
                genericLocation = {3, 0, 0, 0, 0, 0, 1, 0, 1, 0, 3, 1}),
        @TADescription(annotation = "TJ", type = METHOD_RETURN,
                genericLocation = {3, 0}),
        @TADescription(annotation = "TK", type = METHOD_RETURN,
                genericLocation = {3, 0, 0, 0})
    })
    public String testReturn4() {
        return "class Outer {\n" +
                " class GInner<X> {\n" +
                "  class GInner2<Y, Z> {}\n" +
                "}}\n\n" +
                "class Test {\n" +
                " List<@TA Outer . @TB GInner<@TC List<@TD Object @TE[] @TF[]>>. @TG GInner2<@TH Integer, @TI Object> @TJ[] @TK[]> test() { return null; }\n" +
                "}";
    }

    @TADescriptions({
        @TADescription(annotation = "TA", type = METHOD_RETURN,
                genericLocation = {3, 0}),
        @TADescription(annotation = "TB", type = METHOD_RETURN,
                genericLocation = {3, 0, 3, 0}),
        @TADescription(annotation = "TC", type = METHOD_RETURN,
                genericLocation = {3, 0, 3, 1}),
        @TADescription(annotation = "TD", type = METHOD_RETURN,
                genericLocation = {3, 0, 3, 1, 3, 0}),
        @TADescription(annotation = "TE", type = METHOD_RETURN,
                genericLocation = {3, 0, 1, 0}),
        @TADescription(annotation = "TF", type = METHOD_RETURN,
                genericLocation = {3, 0, 1, 0, 3, 0}),
        @TADescription(annotation = "TG", type = METHOD_RETURN,
                genericLocation = {3, 0, 1, 0, 3, 0, 3, 0, 0, 0, 0, 0}),
        @TADescription(annotation = "TH", type = METHOD_RETURN,
                genericLocation = {3, 0, 1, 0, 3, 0, 3, 0}),
        @TADescription(annotation = "TI", type = METHOD_RETURN,
                genericLocation = {3, 0, 1, 0, 3, 0, 3, 0, 0, 0}),
        @TADescription(annotation = "TJ", type = METHOD_RETURN,
                genericLocation = {3, 0, 1, 0, 1, 0}),
    })
    public String testReturn5() {
        return "class GOuter<A, B> {\n" +
                " class GInner<X> {\n" +
                "  class GInner2<Y, Z> {}\n" +
                "}}\n\n" +
                "class Test {\n" +
                " List<@TA GOuter<@TB String, @TC List<@TD Object>> . @TE GInner<@TF List<@TG Object @TH[] @TI[]>>. @TJ GInner2<String, String>> test() { return null; }\n" +
                "}";
    }


    // type parameters

    @TADescriptions({
        @TADescription(annotation = "TA", type = METHOD_TYPE_PARAMETER_BOUND,
                genericLocation = {}, paramIndex = 0, boundIndex = 0),
        @TADescription(annotation = "TB", type = METHOD_TYPE_PARAMETER_BOUND,
                genericLocation = {1, 0}, paramIndex = 0, boundIndex = 0)
    })
    public String testTypeparam1() {
        return "<X extends @TA Outer.@TB Inner> X test() { return null; }";
    }

    @TADescriptions({
        @TADescription(annotation = "TA", type = METHOD_TYPE_PARAMETER_BOUND,
                genericLocation = {}, paramIndex = 0, boundIndex = 0),
        @TADescription(annotation = "TB", type = METHOD_TYPE_PARAMETER_BOUND,
                genericLocation = {1, 0}, paramIndex = 0, boundIndex = 0)
    })
    public String testTypeparam2() {
        return "<X extends @TA GOuter<String,String>.@TB GInner<String,String>> X test() { return null; }";
    }

    @TADescriptions({
        @TADescription(annotation = "TA", type = METHOD_TYPE_PARAMETER_BOUND,
                genericLocation = {},
                paramIndex = 0, boundIndex = 0),
        @TADescription(annotation = "TB", type = METHOD_TYPE_PARAMETER_BOUND,
                genericLocation = {1, 0},
                paramIndex = 0, boundIndex = 0),
        @TADescription(annotation = "TC", type = METHOD_TYPE_PARAMETER_BOUND,
                genericLocation = {1, 0, 3, 0},
                paramIndex = 0, boundIndex = 0),
        @TADescription(annotation = "TD", type = METHOD_TYPE_PARAMETER_BOUND,
                genericLocation = {1, 0, 3, 0, 3, 0, 0, 0, 0, 0},
                paramIndex = 0, boundIndex = 0),
        @TADescription(annotation = "TE", type = METHOD_TYPE_PARAMETER_BOUND,
                genericLocation = {1, 0, 3, 0, 3, 0},
                paramIndex = 0, boundIndex = 0),
        @TADescription(annotation = "TF", type = METHOD_TYPE_PARAMETER_BOUND,
                genericLocation = {1, 0, 3, 0, 3, 0, 0, 0},
                paramIndex = 0, boundIndex = 0),
        @TADescription(annotation = "TG", type = METHOD_TYPE_PARAMETER_BOUND,
                genericLocation = {1, 0, 1, 0},
                paramIndex = 0, boundIndex = 0),
        @TADescription(annotation = "TH", type = METHOD_TYPE_PARAMETER_BOUND,
                genericLocation = {1, 0, 1, 0, 3, 0},
                paramIndex = 0, boundIndex = 0),
        @TADescription(annotation = "TI", type = METHOD_TYPE_PARAMETER_BOUND,
                genericLocation = {1, 0, 1, 0, 3, 1},
                paramIndex = 0, boundIndex = 0),
    })
    public String testTypeparam3() {
        return "class Outer {\n" +
                " class GInner<X> {\n" +
                "  class GInner2<Y, Z> {}\n" +
                "}}\n\n" +
                "class Test {\n" +
                " <X extends @TA Outer . @TB GInner<@TC List<@TD Object @TE[] @TF[]>>. @TG GInner2<@TH Integer, @TI Object>> X test() { return null; }\n" +
                "}";
    }

    @TADescriptions({
        @TADescription(annotation = "TA", type = METHOD_TYPE_PARAMETER_BOUND,
                genericLocation = {3, 0, 0, 0, 0, 0},
                paramIndex = 0, boundIndex = 1),
        @TADescription(annotation = "TB", type = METHOD_TYPE_PARAMETER_BOUND,
                genericLocation = {3, 0, 0, 0, 0, 0, 1, 0},
                paramIndex = 0, boundIndex = 1),
        @TADescription(annotation = "TC", type = METHOD_TYPE_PARAMETER_BOUND,
                genericLocation = {3, 0, 0, 0, 0, 0, 1, 0, 3, 0},
                paramIndex = 0, boundIndex = 1),
        @TADescription(annotation = "TD", type = METHOD_TYPE_PARAMETER_BOUND,
                genericLocation = {3, 0, 0, 0, 0, 0, 1, 0, 3, 0, 3, 0, 0, 0, 0, 0},
                paramIndex = 0, boundIndex = 1),
        @TADescription(annotation = "TE", type = METHOD_TYPE_PARAMETER_BOUND,
                genericLocation = {3, 0, 0, 0, 0, 0, 1, 0, 3, 0, 3, 0},
                paramIndex = 0, boundIndex = 1),
        @TADescription(annotation = "TF", type = METHOD_TYPE_PARAMETER_BOUND,
                genericLocation = {3, 0, 0, 0, 0, 0, 1, 0, 3, 0, 3, 0, 0, 0},
                paramIndex = 0, boundIndex = 1),
        @TADescription(annotation = "TG", type = METHOD_TYPE_PARAMETER_BOUND,
                genericLocation = {3, 0, 0, 0, 0, 0, 1, 0, 1, 0},
                paramIndex = 0, boundIndex = 1),
        @TADescription(annotation = "TH", type = METHOD_TYPE_PARAMETER_BOUND,
                genericLocation = {3, 0, 0, 0, 0, 0, 1, 0, 1, 0, 3, 0},
                paramIndex = 0, boundIndex = 1),
        @TADescription(annotation = "TI", type = METHOD_TYPE_PARAMETER_BOUND,
                genericLocation = {3, 0, 0, 0, 0, 0, 1, 0, 1, 0, 3, 1},
                paramIndex = 0, boundIndex = 1),
        @TADescription(annotation = "TJ", type = METHOD_TYPE_PARAMETER_BOUND,
                genericLocation = {3, 0},
                paramIndex = 0, boundIndex = 1),
        @TADescription(annotation = "TK", type = METHOD_TYPE_PARAMETER_BOUND,
                genericLocation = {3, 0, 0, 0},
                paramIndex = 0, boundIndex = 1)
    })
    public String testTypeparam4() {
        return "class Outer {\n" +
                " class GInner<X> {\n" +
                "  class GInner2<Y, Z> {}\n" +
                "}}\n\n" +
                "class Test {\n" +
                " <X extends List<@TA Outer . @TB GInner<@TC List<@TD Object @TE[] @TF[]>>. @TG GInner2<@TH Integer, @TI Object> @TJ[] @TK[]>> X test() { return null; }\n" +
                "}";
    }

    @TADescriptions({
        @TADescription(annotation = "TA", type = METHOD_TYPE_PARAMETER_BOUND,
                genericLocation = {3, 0}, paramIndex = 0, boundIndex = 1),
        @TADescription(annotation = "TB", type = METHOD_TYPE_PARAMETER_BOUND,
                genericLocation = {3, 0, 3, 0}, paramIndex = 0, boundIndex = 1),
        @TADescription(annotation = "TC", type = METHOD_TYPE_PARAMETER_BOUND,
                genericLocation = {3, 0, 3, 1}, paramIndex = 0, boundIndex = 1),
        @TADescription(annotation = "TD", type = METHOD_TYPE_PARAMETER_BOUND,
                genericLocation = {3, 0, 3, 1, 3, 0}, paramIndex = 0, boundIndex = 1),
        @TADescription(annotation = "TE", type = METHOD_TYPE_PARAMETER_BOUND,
                genericLocation = {3, 0, 1, 0}, paramIndex = 0, boundIndex = 1),
        @TADescription(annotation = "TF", type = METHOD_TYPE_PARAMETER_BOUND,
                genericLocation = {3, 0, 1, 0, 3, 0}, paramIndex = 0, boundIndex = 1),
        @TADescription(annotation = "TG", type = METHOD_TYPE_PARAMETER_BOUND,
                genericLocation = {3, 0, 1, 0, 3, 0, 3, 0, 0, 0, 0, 0}, paramIndex = 0, boundIndex = 1),
        @TADescription(annotation = "TH", type = METHOD_TYPE_PARAMETER_BOUND,
                genericLocation = {3, 0, 1, 0, 3, 0, 3, 0}, paramIndex = 0, boundIndex = 1),
        @TADescription(annotation = "TI", type = METHOD_TYPE_PARAMETER_BOUND,
                genericLocation = {3, 0, 1, 0, 3, 0, 3, 0, 0, 0}, paramIndex = 0, boundIndex = 1),
        @TADescription(annotation = "TJ", type = METHOD_TYPE_PARAMETER_BOUND,
                genericLocation = {3, 0, 1, 0, 1, 0}, paramIndex = 0, boundIndex = 1),
    })
    public String testTypeparam5() {
        return "class GOuter<A, B> {\n" +
                " class GInner<X> {\n" +
                "  class GInner2<Y, Z> {}\n" +
                "}}\n\n" +
                "class Test {\n" +
                " <X extends List<@TA GOuter<@TB String, @TC List<@TD Object>> . @TE GInner<@TF List<@TG Object @TH[] @TI[]>>. @TJ GInner2<String, String>>> X test() { return null; }\n" +
                "}";
    }

    @TADescription(annotation = "TA", type = FIELD,
            genericLocation = {3, 0, 1, 0})
    public String testUses1a() {
        return "class Test { class Inner {}    List<@TA Inner> f; }";
    }

    @TADescription(annotation = "TA", type = FIELD,
            genericLocation = {3, 0})
    public String testUses1b() {
        return "class Test { class Inner {}    List<@TA Test.Inner> f; }";
    }

    @TADescription(annotation = "TA", type = FIELD,
            genericLocation = {3, 0, 1, 0, 1, 0})
    @TestClass("Test$Inner")
    public String testUses2a() {
        return "class Test { class Inner { class Inner2{}    List<@TA Inner2> f; }}";
    }

    @TADescription(annotation = "TA", type = FIELD,
            genericLocation = {3, 0, 1, 0})
    @TestClass("Test$Inner")
    public String testUses2b() {
        return "class Test { class Inner { class Inner2{}    List<@TA Inner.Inner2> f; }}";
    }

    @TADescription(annotation = "TA", type = FIELD,
            genericLocation = {3, 0, 1, 0, 1, 0})
    @TestClass("Test$Inner")
    public String testUses2c() {
        return "class Test { class Inner { class Inner2{}    List<Inner.@TA Inner2> f; }}";
    }

    @TADescription(annotation = "TA", type = FIELD,
            genericLocation = {3, 0})
    @TestClass("Test$Inner")
    public String testUses2d() {
        return "class Test{ class Inner { class Inner2{}    List<@TA Test.Inner.Inner2> f; }}";
    }

    @TADescription(annotation = "TA", type = FIELD,
            genericLocation = {3, 0, 1, 0})
    @TestClass("Test$Inner")
    public String testUses2e() {
        return "class Test { class Inner { class Inner2{}    List<Test.@TA Inner.Inner2> f; }}";
    }

    @TADescription(annotation = "TA", type = FIELD,
            genericLocation = {3, 0, 1, 0, 1, 0})
    @TestClass("Test$Inner")
    public String testUses2f() {
        return "class Test { class Inner { class Inner2{}    List<Test.Inner.@TA Inner2> f; }}";
    }

    @TADescription(annotation = "TA", type = FIELD,
            genericLocation = {3, 0, 1, 0, 1, 0})
    @TestClass("Test$Inner")
    public String testUses3a() {
        return "class Test { class Inner<A, B> { class Inner2<C, D>{}\n" +
                "    List<Test.Inner.@TA Inner2> f; }}";
    }

    @TADescription(annotation = "TA", type = FIELD,
            genericLocation = {3, 0, 1, 0})
    @TestClass("Test$Inner")
    public String testUses3b() {
        return "class Test { class Inner<A, B> { class Inner2<C, D>{}\n" +
                "    List<Test.@TA Inner.Inner2> f; }}";
    }

    @TADescriptions({
        @TADescription(annotation = "TA", type = FIELD,
                genericLocation = {}),
        @TADescription(annotation = "TB", type = FIELD,
                genericLocation = {3, 0})
    })
    public String testUses4() {
        return "class Test { static class TInner {}\n" +
                "    @TA TInner f; \n" +
                "    List<@TB TInner> g; }";
    }

    @TADescription(annotation = "TA", type = FIELD,
            genericLocation = {3, 0, 1, 0, 3, 1})
    @TestClass("Test$Inner")
    public String testUses3c() {
        return "class Test { class Inner<A, B> { class Inner2<C, D>{}\n" +
                "    List<Test.Inner<String, @TA Object>.Inner2<Test, Test>> f; }}";
    }

    @TADescription(annotation = "TA", type = METHOD_FORMAL_PARAMETER, paramIndex=0)
    public String testFullyQualified1() {
        return "void testme(java.security.@TA ProtectionDomain protectionDomain) {}";
    }

    @TADescription(annotation = "TA", type = METHOD_FORMAL_PARAMETER, paramIndex=0,
            genericLocation = {3, 0})
    public String testFullyQualified2() {
        return "void testme(List<java.security.@TA ProtectionDomain> protectionDomain) {}";
    }

    @TADescriptions({
        @TADescription(annotation = "TA", type = LOCAL_VARIABLE,
                genericLocation = {},
                lvarOffset = ReferenceInfoUtil.IGNORE_VALUE,
                lvarLength = ReferenceInfoUtil.IGNORE_VALUE,
                lvarIndex = ReferenceInfoUtil.IGNORE_VALUE),
        @TADescription(annotation = "TB", type = LOCAL_VARIABLE,
                genericLocation = {1, 0},
                lvarOffset = ReferenceInfoUtil.IGNORE_VALUE,
                lvarLength = ReferenceInfoUtil.IGNORE_VALUE,
                lvarIndex = ReferenceInfoUtil.IGNORE_VALUE),
        @TADescription(annotation = "TC", type = LOCAL_VARIABLE,
                // Only classes count, not methods.
                genericLocation = {1, 0, 1, 0},
                lvarOffset = ReferenceInfoUtil.IGNORE_VALUE,
                lvarLength = ReferenceInfoUtil.IGNORE_VALUE,
                lvarIndex = ReferenceInfoUtil.IGNORE_VALUE),
    })
    @TestClass("Outer$Inner")
    public String testMethodNesting1() {
        return "class Outer {\n" +
                " class Inner {\n" +
                "  void foo() {\n" +
                "    class MInner {}\n" +
                "    @TA Outer . @TB Inner l1 = null;\n" +
                "    @TC MInner l2 = null;\n" +
                "  }\n" +
                "}}\n";
    }

    @TADescriptions({
        @TADescription(annotation = "TA", type = NEW,
                genericLocation = {},
                offset = 0),
        @TADescription(annotation = "TB", type = NEW,
                genericLocation = {1, 0},
                offset = 0),
        @TADescription(annotation = "TC", type = NEW,
                // Only classes count, not methods.
                genericLocation = {1, 0, 1, 0},
                offset = 12),
    })
    @TestClass("Outer$Inner")
    public String testMethodNesting2() {
        return "class Outer {\n" +
                " class Inner {\n" +
                "  void foo() {\n" +
                "    class MInner {}\n" +
                "    Object o1 = new @TA Outer . @TB Inner();" +
                "    Object o2 = new @TC MInner();\n" +
                "  }\n" +
                "}}\n";
    }

    @TADescriptions({
        @TADescription(annotation = "TA", type = CLASS_EXTENDS,
                genericLocation = {}, typeIndex = -1),
        @TADescription(annotation = "TB", type = CLASS_EXTENDS,
                genericLocation = {3, 0}, typeIndex = -1),
        @TADescription(annotation = "TC", type = CLASS_EXTENDS,
                genericLocation = {3, 1}, typeIndex = -1),
        @TADescription(annotation = "TD", type = CLASS_EXTENDS,
                genericLocation = {1, 0}, typeIndex = -1),
        @TADescription(annotation = "TE", type = CLASS_EXTENDS,
                genericLocation = {1, 0, 3, 0}, typeIndex = -1),
        @TADescription(annotation = "TF", type = CLASS_EXTENDS,
                genericLocation = {1, 0, 3, 1}, typeIndex = -1)
    })
    @TestClass("GOuter$GInner$Test")
    public String testExtends1() {
        return "class GOuter<A, B> {\n" +
                "  class GInner<X, Y> {\n" +
                "    class Test extends @TA GOuter<@TB String, @TC String>.@TD GInner<@TE String, @TF String> {}" +
                "  }" +
                "}";
    }

    @TADescriptions({
        @TADescription(annotation = "TA", type = CLASS_TYPE_PARAMETER,
                genericLocation = {}, paramIndex = 0),
        @TADescription(annotation = "TB", type = CLASS_TYPE_PARAMETER_BOUND,
                genericLocation = {}, paramIndex = 0, boundIndex = 0),
        @TADescription(annotation = "TC", type = FIELD,
                genericLocation = {}),
        @TADescription(annotation = "TD", type = FIELD,
                genericLocation = {3, 0})
    })
    @TestClass("Test$1Nested")
    public String testNestedInMethod1() {
        return "class Test {\n" +
                "  void foobar() {\n" +
                "    class Nested<@TA X extends @TB Object> {\n" +
                "      @TC List<@TD Object> f;\n" +
                "    }\n" +
                "  }" +
                "}";
    }
}
