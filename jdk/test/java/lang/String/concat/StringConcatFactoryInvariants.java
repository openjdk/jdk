/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.io.Serializable;
import java.lang.invoke.*;
import java.util.concurrent.Callable;

/**
 * @test
 * @summary Test input invariants for StringConcatFactory
 *
 * @compile StringConcatFactoryInvariants.java
 *
 * @run main/othervm -Xverify:all -Djava.lang.invoke.stringConcat=BC_SB                                                                                                          StringConcatFactoryInvariants
 * @run main/othervm -Xverify:all -Djava.lang.invoke.stringConcat=BC_SB_SIZED                                                                                                    StringConcatFactoryInvariants
 * @run main/othervm -Xverify:all -Djava.lang.invoke.stringConcat=MH_SB_SIZED                                                                                                    StringConcatFactoryInvariants
 * @run main/othervm -Xverify:all -Djava.lang.invoke.stringConcat=BC_SB_SIZED_EXACT                                                                                              StringConcatFactoryInvariants
 * @run main/othervm -Xverify:all -Djava.lang.invoke.stringConcat=MH_SB_SIZED_EXACT                                                                                              StringConcatFactoryInvariants
 * @run main/othervm -Xverify:all -Djava.lang.invoke.stringConcat=MH_INLINE_SIZED_EXACT                                                                                          StringConcatFactoryInvariants
 *
 * @run main/othervm -Xverify:all -Djava.lang.invoke.stringConcat=BC_SB                  -Djava.lang.invoke.stringConcat.debug=true                                              StringConcatFactoryInvariants
 * @run main/othervm -Xverify:all -Djava.lang.invoke.stringConcat=BC_SB_SIZED            -Djava.lang.invoke.stringConcat.debug=true                                              StringConcatFactoryInvariants
 * @run main/othervm -Xverify:all -Djava.lang.invoke.stringConcat=MH_SB_SIZED            -Djava.lang.invoke.stringConcat.debug=true                                              StringConcatFactoryInvariants
 * @run main/othervm -Xverify:all -Djava.lang.invoke.stringConcat=BC_SB_SIZED_EXACT      -Djava.lang.invoke.stringConcat.debug=true                                              StringConcatFactoryInvariants
 * @run main/othervm -Xverify:all -Djava.lang.invoke.stringConcat=MH_SB_SIZED_EXACT      -Djava.lang.invoke.stringConcat.debug=true                                              StringConcatFactoryInvariants
 * @run main/othervm -Xverify:all -Djava.lang.invoke.stringConcat=MH_INLINE_SIZED_EXACT  -Djava.lang.invoke.stringConcat.debug=true                                              StringConcatFactoryInvariants
 *
 * @run main/othervm -Xverify:all -Djava.lang.invoke.stringConcat=BC_SB                                                              -Djava.lang.invoke.stringConcat.cache=true  StringConcatFactoryInvariants
 * @run main/othervm -Xverify:all -Djava.lang.invoke.stringConcat=BC_SB_SIZED                                                        -Djava.lang.invoke.stringConcat.cache=true  StringConcatFactoryInvariants
 * @run main/othervm -Xverify:all -Djava.lang.invoke.stringConcat=MH_SB_SIZED                                                        -Djava.lang.invoke.stringConcat.cache=true  StringConcatFactoryInvariants
 * @run main/othervm -Xverify:all -Djava.lang.invoke.stringConcat=BC_SB_SIZED_EXACT                                                  -Djava.lang.invoke.stringConcat.cache=true  StringConcatFactoryInvariants
 * @run main/othervm -Xverify:all -Djava.lang.invoke.stringConcat=MH_SB_SIZED_EXACT                                                  -Djava.lang.invoke.stringConcat.cache=true  StringConcatFactoryInvariants
 * @run main/othervm -Xverify:all -Djava.lang.invoke.stringConcat=MH_INLINE_SIZED_EXACT                                              -Djava.lang.invoke.stringConcat.cache=true  StringConcatFactoryInvariants
 *
 * @run main/othervm -Xverify:all -Djava.lang.invoke.stringConcat=BC_SB                  -Djava.lang.invoke.stringConcat.debug=true  -Djava.lang.invoke.stringConcat.cache=true  StringConcatFactoryInvariants
 * @run main/othervm -Xverify:all -Djava.lang.invoke.stringConcat=BC_SB_SIZED            -Djava.lang.invoke.stringConcat.debug=true  -Djava.lang.invoke.stringConcat.cache=true  StringConcatFactoryInvariants
 * @run main/othervm -Xverify:all -Djava.lang.invoke.stringConcat=MH_SB_SIZED            -Djava.lang.invoke.stringConcat.debug=true  -Djava.lang.invoke.stringConcat.cache=true  StringConcatFactoryInvariants
 * @run main/othervm -Xverify:all -Djava.lang.invoke.stringConcat=BC_SB_SIZED_EXACT      -Djava.lang.invoke.stringConcat.debug=true  -Djava.lang.invoke.stringConcat.cache=true  StringConcatFactoryInvariants
 * @run main/othervm -Xverify:all -Djava.lang.invoke.stringConcat=MH_SB_SIZED_EXACT      -Djava.lang.invoke.stringConcat.debug=true  -Djava.lang.invoke.stringConcat.cache=true  StringConcatFactoryInvariants
 * @run main/othervm -Xverify:all -Djava.lang.invoke.stringConcat=MH_INLINE_SIZED_EXACT  -Djava.lang.invoke.stringConcat.debug=true  -Djava.lang.invoke.stringConcat.cache=true  StringConcatFactoryInvariants
 *
*/
public class StringConcatFactoryInvariants {

    private static final char TAG_ARG   = '\u0001';
    private static final char TAG_CONST = '\u0002';

    public static void main(String[] args) throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        String methodName = "foo";
        MethodType mt = MethodType.methodType(String.class, String.class, int.class);
        String recipe = "" + TAG_ARG + TAG_ARG + TAG_CONST;
        String[] constants = new String[]{"bar"};

        final int LIMIT = 200;

        // Simple factory: check for dynamic arguments overflow
        Class<?>[] underThreshold = new Class<?>[LIMIT - 1];
        Class<?>[] threshold      = new Class<?>[LIMIT];
        Class<?>[] overThreshold  = new Class<?>[LIMIT + 1];

        StringBuilder sbUnderThreshold = new StringBuilder();
        sbUnderThreshold.append(TAG_CONST);
        for (int c = 0; c < LIMIT - 1; c++) {
            underThreshold[c] = int.class;
            threshold[c] = int.class;
            overThreshold[c] = int.class;
            sbUnderThreshold.append(TAG_ARG);
        }
        threshold[LIMIT - 1] = int.class;
        overThreshold[LIMIT - 1] = int.class;
        overThreshold[LIMIT] = int.class;

        String recipeEmpty = "";
        String recipeUnderThreshold = sbUnderThreshold.toString();
        String recipeThreshold = sbUnderThreshold.append(TAG_ARG).toString();
        String recipeOverThreshold = sbUnderThreshold.append(TAG_ARG).toString();

        MethodType mtEmpty = MethodType.methodType(String.class);
        MethodType mtUnderThreshold = MethodType.methodType(String.class, underThreshold);
        MethodType mtThreshold = MethodType.methodType(String.class, threshold);
        MethodType mtOverThreshold = MethodType.methodType(String.class, overThreshold);


        // Check the basic functionality is working
        {
            CallSite cs = StringConcatFactory.makeConcat(lookup, methodName, mt);
            test("foo42", (String) cs.getTarget().invokeExact("foo", 42));
        }

        {
            CallSite cs = StringConcatFactory.makeConcatWithConstants(lookup, methodName, mt, recipe, constants);
            test("foo42bar", (String) cs.getTarget().invokeExact("foo", 42));
        }

        // Simple factory, check for nulls:
        failNPE("Lookup is null",
                () -> StringConcatFactory.makeConcat(null, methodName, mt));

        failNPE("Method name is null",
                () -> StringConcatFactory.makeConcat(lookup, null, mt));

        failNPE("MethodType is null",
                () -> StringConcatFactory.makeConcat(lookup, methodName, null));

        // Advanced factory, check for nulls:
        failNPE("Lookup is null",
                () -> StringConcatFactory.makeConcatWithConstants(null, methodName, mt, recipe, constants));

        failNPE("Method name is null",
                () -> StringConcatFactory.makeConcatWithConstants(lookup, null, mt, recipe, constants));

        failNPE("MethodType is null",
                () -> StringConcatFactory.makeConcatWithConstants(lookup, methodName, null, recipe, constants));

        failNPE("Recipe is null",
                () -> StringConcatFactory.makeConcatWithConstants(lookup, methodName, mt, null, constants));

        failNPE("Constants vararg is null",
                () -> StringConcatFactory.makeConcatWithConstants(lookup, methodName, mt, recipe, null));

        // Simple factory, check for return type
        fail("Return type: void",
                () -> StringConcatFactory.makeConcat(lookup, methodName, MethodType.methodType(void.class, String.class, int.class)));

        fail("Return type: int",
                () -> StringConcatFactory.makeConcat(lookup, methodName, MethodType.methodType(int.class, String.class, int.class)));

        fail("Return type: StringBuilder",
                () -> StringConcatFactory.makeConcat(lookup, methodName, MethodType.methodType(StringBuilder.class, String.class, int.class)));

        ok("Return type: Object",
                () -> StringConcatFactory.makeConcat(lookup, methodName, MethodType.methodType(Object.class, String.class, int.class)));

        ok("Return type: CharSequence",
                () -> StringConcatFactory.makeConcat(lookup, methodName, MethodType.methodType(CharSequence.class, String.class, int.class)));

        ok("Return type: Serializable",
                () -> StringConcatFactory.makeConcat(lookup, methodName, MethodType.methodType(Serializable.class, String.class, int.class)));

        // Advanced factory, check for return types
        fail("Return type: void",
                () -> StringConcatFactory.makeConcatWithConstants(lookup, methodName, MethodType.methodType(void.class, String.class, int.class), recipe, constants));

        fail("Return type: int",
                () -> StringConcatFactory.makeConcatWithConstants(lookup, methodName, MethodType.methodType(int.class, String.class, int.class), recipe, constants));

        fail("Return type: StringBuilder",
                () -> StringConcatFactory.makeConcatWithConstants(lookup, methodName, MethodType.methodType(StringBuilder.class, String.class, int.class), recipe, constants));

        ok("Return type: Object",
                () -> StringConcatFactory.makeConcatWithConstants(lookup, methodName, MethodType.methodType(Object.class, String.class, int.class), recipe, constants));

        ok("Return type: CharSequence",
                () -> StringConcatFactory.makeConcatWithConstants(lookup, methodName, MethodType.methodType(CharSequence.class, String.class, int.class), recipe, constants));

        ok("Return type: Serializable",
                () -> StringConcatFactory.makeConcatWithConstants(lookup, methodName, MethodType.methodType(Serializable.class, String.class, int.class), recipe, constants));

        // Simple factory: check for dynamic arguments overflow
        ok("Dynamic arguments is under limit",
                () -> StringConcatFactory.makeConcat(lookup, methodName, mtUnderThreshold));

        ok("Dynamic arguments is at the limit",
                () -> StringConcatFactory.makeConcat(lookup, methodName, mtThreshold));

        fail("Dynamic arguments is over the limit",
                () -> StringConcatFactory.makeConcat(lookup, methodName, mtOverThreshold));

        // Advanced factory: check for dynamic arguments overflow
        ok("Dynamic arguments is under limit",
                () -> StringConcatFactory.makeConcatWithConstants(lookup, methodName, mtUnderThreshold, recipeUnderThreshold, constants));

        ok("Dynamic arguments is at the limit",
                () -> StringConcatFactory.makeConcatWithConstants(lookup, methodName, mtThreshold, recipeThreshold, constants));

        fail("Dynamic arguments is over the limit",
                () -> StringConcatFactory.makeConcatWithConstants(lookup, methodName, mtOverThreshold, recipeOverThreshold, constants));

        // Advanced factory: check for mismatched recipe and Constants
        ok("Static arguments and recipe match",
                () -> StringConcatFactory.makeConcatWithConstants(lookup, methodName, mtThreshold, recipeThreshold, "bar"));

        fail("Static arguments and recipe mismatch",
                () -> StringConcatFactory.makeConcatWithConstants(lookup, methodName, mtThreshold, recipeThreshold, "bar", "baz"));

        // Advanced factory: check for mismatched recipe and dynamic arguments
        fail("Dynamic arguments and recipe mismatch",
                () -> StringConcatFactory.makeConcatWithConstants(lookup, methodName, mtThreshold, recipeUnderThreshold, constants));

        ok("Dynamic arguments and recipe match",
                () -> StringConcatFactory.makeConcatWithConstants(lookup, methodName, mtThreshold, recipeThreshold, constants));

        fail("Dynamic arguments and recipe mismatch",
                () -> StringConcatFactory.makeConcatWithConstants(lookup, methodName, mtThreshold, recipeOverThreshold, constants));

        // Test passing array as constant
        {
            String[] arg = {"boo", "bar"};

            CallSite cs1 = StringConcatFactory.makeConcatWithConstants(lookup, methodName, MethodType.methodType(String.class, int.class), "" + TAG_ARG + TAG_CONST + TAG_CONST, arg);
            test("42boobar", (String) cs1.getTarget().invokeExact(42));
        }

        // Test passing null constant
        ok("Can pass regular constants",
                () -> StringConcatFactory.makeConcatWithConstants(lookup, methodName, MethodType.methodType(String.class, int.class), "" + TAG_ARG + TAG_CONST, "foo"));

        failNPE("Cannot pass null constants",
                () -> StringConcatFactory.makeConcatWithConstants(lookup, methodName, MethodType.methodType(String.class, int.class), "" + TAG_ARG + TAG_CONST, new String[]{null}));

        // Simple factory: test empty arguments
        ok("Ok to pass empty arguments",
                () -> StringConcatFactory.makeConcat(lookup, methodName, mtEmpty));

        // Advanced factory: test empty arguments
        ok("Ok to pass empty arguments",
                () -> StringConcatFactory.makeConcatWithConstants(lookup, methodName, mtEmpty, recipeEmpty));

        // Simple factory: public Lookup is rejected
        fail("Passing public Lookup",
                () -> StringConcatFactory.makeConcat(MethodHandles.publicLookup(), methodName, mtEmpty));

        // Advanced factory: public Lookup is rejected
        fail("Passing public Lookup",
                () -> StringConcatFactory.makeConcatWithConstants(MethodHandles.publicLookup(), methodName, mtEmpty, recipeEmpty));
    }

    public static void ok(String msg, Callable runnable) {
        try {
            runnable.call();
        } catch (Throwable e) {
            e.printStackTrace();
            throw new IllegalStateException(msg + ", should have passed", e);
        }
    }

    public static void fail(String msg, Callable runnable) {
        boolean expected = false;
        try {
            runnable.call();
        } catch (StringConcatException e) {
            expected = true;
        } catch (Throwable e) {
            e.printStackTrace();
        }

        if (!expected) {
            throw new IllegalStateException(msg + ", should have failed with StringConcatException");
        }
    }


    public static void failNPE(String msg, Callable runnable) {
        boolean expected = false;
        try {
            runnable.call();
        } catch (NullPointerException e) {
            expected = true;
        } catch (Throwable e) {
            e.printStackTrace();
        }

        if (!expected) {
            throw new IllegalStateException(msg + ", should have failed with NullPointerException");
        }
    }

    public static void test(String expected, String actual) {
       // Fingers crossed: String concat should work.
       if (!expected.equals(actual)) {
           StringBuilder sb = new StringBuilder();
           sb.append("Expected = ");
           sb.append(expected);
           sb.append(", actual = ");
           sb.append(actual);
           throw new IllegalStateException(sb.toString());
       }
    }

}
