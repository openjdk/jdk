/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8005681
 * @summary Repeated annotations on new,array,cast.
 * @enablePreview
 * @modules java.base/jdk.internal.classfile.impl
 */
import java.lang.classfile.*;
import java.lang.classfile.attribute.*;
import java.lang.annotation.*;
import java.io.*;
import java.util.List;

import java.lang.annotation.*;
import java.util.Objects;
import static java.lang.annotation.RetentionPolicy.*;
import static java.lang.annotation.ElementType.*;

public class TestNewCastArray {
    int errors = 0;
    List<String> failedTests = new java.util.LinkedList<>();

    // 'b' tests fail with only even numbers of annotations (8005681).
    String[] testclasses = {"Test1",
        "Test2a", "Test3a", "Test4a", "Test5a",
        "Test2b", "Test3b", "Test4b", "Test5b"
    };

    public static void main(String[] args) throws Exception {
        new TestNewCastArray().run();
    }

    void check(String testcase, int expected, int actual) {
        String res = testcase + ": (expected) " + expected + ", " + actual + " (actual): ";
        if(expected == actual) {
            res = res.concat("PASS");
        } else {
            errors++;
            res = res.concat("FAIL");
            failedTests.add(res);
        }
        System.out.println(res);
    }

    void report() {
        if(errors!=0) {
            System.err.println("Failed tests: " + errors +
                                   "\nfailed test cases:\n");
            for(String t: failedTests)
                System.err.println("  " + t);
           throw new RuntimeException("FAIL: There were test failures.");
           } else
            System.out.println("PASS");
    }

    <T extends Attribute<T>> void test(String clazz, AttributedElement m, AttributeMapper<T> name, Boolean codeattr) {
        int actual = 0;
        int expected = 0, cexpected = 0;
        String memberName;
        Attribute<T> attr = null;
        CodeAttribute cAttr;
        String testcase;
        switch (m) {
            case MethodModel mm -> {
                memberName = mm.methodName().stringValue();
                if(codeattr) {
                    //fetch index of and code attribute and annotations from code attribute.
                    cAttr = mm.findAttribute(Attributes.CODE).orElse(null);
                    if(cAttr != null) {
                        attr = cAttr.findAttribute(name).orElse(null);
                    }
                } else {
                    attr = mm.findAttribute(name).orElse(null);
                }
            }
            case FieldModel fm -> {
                memberName = fm.fieldName().stringValue();
                if(codeattr) {
                    cAttr = fm.findAttribute(Attributes.CODE).orElse(null);
                    if(cAttr != null) {
                        attr = cAttr.findAttribute(name).orElse(null);
                    }
                } else {
                    attr = fm.findAttribute(name).orElse(null);
                }
            }
            default -> throw new AssertionError();
        }
        testcase = clazz+" , Local: "+ codeattr + ": " + memberName + ", " + name;
        if(attr != null) {
            //count RuntimeTypeAnnotations
            switch (attr) {
                case RuntimeVisibleTypeAnnotationsAttribute tAttr -> {
                    actual += tAttr.annotations().size();
                }
                case RuntimeInvisibleTypeAnnotationsAttribute tAttr -> {
                    actual += tAttr.annotations().size();
                }
                default -> throw new AssertionError();
            }
        }
        assert memberName != null;
        if(memberName.compareTo("<init>")==0) memberName=clazz+memberName;
        switch ( memberName ) {
            //METHOD:
            case "Test1<init>": expected=0; break;
            case "testr22_22": expected=4; break;
            case "testr11_11": expected=4; break;
            case "testr12_21": expected=4; break;
            case "testr20_02": expected=2; break;

            case "Test2a<init>": cexpected=0; break;
            case "test00_00_11_11": cexpected=4; break;
            case "test21_12_21_12": cexpected=8; break;
            case "test_new1": cexpected=2; break;
            case "test_new2": cexpected=2; break;
            case "test_cast1": cexpected=2; break;
            case "test_cast2": cexpected=2; break;

            case "Test2b<init>": cexpected=0; break;
            case "test20_02_20_02": cexpected=4; break;
            case "test22_22_22_22": cexpected=8; break;
            case "test_new3": cexpected=1; break;
            case "test_new4": cexpected=1; break;
            case "test_new5": cexpected=2; break;
            case "test_cast3": cexpected=1; break;
            case "test_cast4": cexpected=2; break;

            case "Test3a<init>": cexpected=10; break;
            case "SA_21_12c": cexpected = 0; break;
            case "SA_01_10c": expected = 0; break;
            case "SA_11_11c": expected = 0; break;

            case "Test3b<init>": cexpected=6; break;
            case "SA_22_22c": cexpected = 0; break;
            case "SA_20_02c": cexpected = 0; break;

            case "Test3c<init>": cexpected=8; break;
            case "SA_10_10": cexpected = 0; break;
            case "SA_10_01": cexpected = 0; break;
            case "SA_21_12": cexpected = 0; break;

            case "Test3d<init>": cexpected=6; break;
            case "SA_20_02": cexpected = 0; break;
            case "SA_22_22": cexpected = 0; break;

            case "Test4a<init>": cexpected=4; break;
            case "nS_21": cexpected = 0; break;
            case "nS_12": cexpected = 0; break;

            case "Test4b<init>": cexpected=4; break;
            case "nS20":  cexpected = 0; break;
            case "nS02":  cexpected = 0; break;
            case "nS22":  cexpected = 0; break;

            case "Test5a<init>": cexpected=4; break;
            case "ci11": expected = 0; break;
            case "ci21": expected = 0; break;

            case "Test5b<init>": cexpected=3; break;
            case "ci2":  expected = 0; break;
            case "ci22": expected = 0; break;

            default: expected = 0; break;
        }
        if(codeattr)
            check(testcase, cexpected, actual);
        else
            check(testcase, expected, actual);
    }

    public void run() {
        ClassModel cm = null;
        InputStream in;
        for( String clazz : testclasses) {
            String testclazz = "TestNewCastArray$" + clazz + ".class";
            System.out.println("Testing " + testclazz);
            try {
                in = Objects.requireNonNull(getClass().getResource(testclazz)).openStream();
                cm = ClassFile.of().parse(in.readAllBytes());
                in.close();
            } catch(Exception e) { e.printStackTrace();  }

            assert cm != null;
            if(clazz.startsWith("Test1")) {
                for (FieldModel fm: cm.fields())
                    test(clazz, fm, Attributes.RUNTIME_VISIBLE_TYPE_ANNOTATIONS, false);
                for (MethodModel mm: cm.methods())
                    test(clazz, mm, Attributes.RUNTIME_VISIBLE_TYPE_ANNOTATIONS, false);
            } else {
                for (FieldModel fm: cm.fields())
                    test(clazz, fm, Attributes.RUNTIME_VISIBLE_TYPE_ANNOTATIONS, true);
                for (MethodModel mm: cm.methods())
                    test(clazz, mm, Attributes.RUNTIME_VISIBLE_TYPE_ANNOTATIONS, true);
            }
        }
        report();
    }

    //////// test class //////////////////////////
    // "Test1" not in code attribute.
    // on arrays on and in method return
    static class Test1 {
        Test1(){}
        // OK expect 5, got 5
        String @A @A @B @B[] @A @A @B @B [] testr22_22(Test1 this, String param, String ... vararg) {
            String [][] sarray = new String [2][2];
            return sarray;
        }
        // OK expect 5, got 5
        String @A @B [] @A @B [] testr11_11(Test1 this, String param, String ... vararg) {
            String [][] sarray = new String [2][2];
            return sarray;
        }
        // OK expect 5, got 5
        String @A @B @B []@B @B @A[] testr12_21(Test1 this, String param, String ... vararg) {
            String [][] sarray = new String [2][2];
            return sarray;
        }
        // OK expect 3, got 3
        String @A @A [] @B @B [] testr20_02(Test1 this, String param, String ... vararg) {
            String [][] sarray = new String [2][2];
            return sarray;
        }
    }

    // Inside method body (in method's code attribute)
    static class Test2a {
        Test2a(){}
        Object o = new Integer(1);
        // expect 4
        String[][] test00_00_11_11(Test2a this, String param, String ... vararg) {
            String [] [] sarray = new String @A @B[2] @A @B [2];
            return sarray;
        }

        // expect 8
        String[][] test21_12_21_12(Test2a this, String param, String ... vararg) {
            String @A @A @B [] @A @B @B [] sarray = new String @A @A @B[2] @A @B @B [2];
            return sarray;
        }

        void test_new1() { String nS_21 = new @A @A @B String("Hello");   }
        void test_new2() { String nS_12 = new @A @B @B String("Hello");   }
        void test_cast1() { String tcs11 = (@A @B String)o;      }
        void test_cast2() { String tcs21 = (@A @A @B String)o;   }
    }

    static class Test2b {
        Test2b(){}
        Object o = new Integer(1);
        // expect 4
        String[][] test20_02_20_02(Test2b this, String param, String ... vararg) {
            String @A @A [] @B @B [] sarray = new String @A @A[2] @B @B [2];
            return sarray;
        }

        // expect 8
        String[][] test22_22_22_22(Test2b this, String param, String ... vararg) {
            String @A @A @B @B [] @A @A @B @B [] sarray = new String @A @A @B @B [2] @A @A @B @B [2];
            return sarray;
        }

        void test_new3() { String nS20 = new @A @A String("Hello");       }
        void test_new4() { String nS02 = new @B @B String("Hello");       }
        void test_new5() { String nS22 = new @A @A @B @B String("Hello"); }
        void test_cast3() { String tcs2 =  (@A @A String)o;      }
        void test_cast4() { String tcs22 = (@A @A @B @B String)o;}
    }

    // array levels
    static class Test3a {
        Test3a(){}
        // expect 4+2+4=10
        String [][] SA_21_12c  = new  String @A @A @B [2] @A @B @B[2];
        String [][] SA_01_10c  = new  String @B [2] @A [2];
        String [][] SA_11_11c = new  String @A @B [2] @A @B [2];
    }

    static class Test3b {
        Test3b(){}
        // expect 4+2=6
        String [][] SA_22_22c  = new  String @A @A @B @B[2] @A @A @B @B[2];
        String [][] SA_20_02c  = new  String @A @A [2] @B @B[2];
    }
    static class Test3c {
        Test3c(){}
        // OK expect 4
        String @A [] @A[] SA_10_10  = new  String [2][2];
        String @A [] @B[] SA_10_01  = new  String [2][2];
        String @A @A @B[] @A @B @B [] SA_21_12  = new  String [2][2];
    }

    static class Test3d {
        Test3d(){}
        // OK expect 4
        String @A @A [] @B @B [] SA_20_02  = new  String [2][2];
        String @A @A @B @B[] @A @A @B @B [] SA_22_22  = new  String [2][2];
    }

    // on new
    static class Test4a {
        Test4a(){}
        // expect 2+2=4
        String nS_21 = new @A @A @B String("Hello");
        String nS_12 = new @A @B @B String("Hello");
    }

    static class Test4b {
        Test4b(){}
        // expect 1+1+2=4
        String nS20 = new @A @A String("Hello");
        String nS02 = new @B @B String("Hello");
        String nS22 = new @A @A @B @B String("Hello");
    }

    // Cast expressions
    static class Test5a {
        Test5a(){}
        Object o = 1;
        // expect 2+2=4
        Integer ci11 = (@A @B Integer)o;       // OK expect 3, got 3
        Integer ci21 = (@A @A @B Integer)o;    // OK expect 3, got 3
    }

    static class Test5b {
        Test5b(){}
        Object o = 1;
        // Cast expressions
        // expect 1+2=3
        Integer ci2 =  (@A @A Integer)o;       // FAIL expect 2, got 1
        Integer ci22 = (@A @A @B @B Integer)o; // FAIL expect 3, got 1
    }

@Retention(RUNTIME) @Target({TYPE_USE}) @Repeatable( AC.class ) @interface A { }
@Retention(RUNTIME) @Target({TYPE_USE}) @Repeatable( BC.class ) @interface B { }
@Retention(RUNTIME) @Target({FIELD}) @Repeatable( FC.class ) @interface F { }
@Retention(RUNTIME) @Target({TYPE_USE}) @interface AC { A[] value(); }
@Retention(RUNTIME) @Target({TYPE_USE}) @interface BC { B[] value(); }
@Retention(RUNTIME) @Target({FIELD}) @interface FC { F[] value(); }

}

