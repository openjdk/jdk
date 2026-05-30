/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug     8XXXXXXX
 * @summary TypeAnnotationParser should read u2 (2 bytes) for EXCEPTION_PARAMETER target info
 * @run main TestExceptionParameterTypeAnnotation
 */

import java.lang.annotation.*;
import java.lang.reflect.*;

/**
 * Tests that type annotations on exception parameters (EXCEPTION_PARAMETER,
 * target_type 0x42) are correctly parsed. Per JVMS 4.7.20.1, the catch_target
 * contains a u2 exception_table_index (2 bytes). A previous bug read only
 * 1 byte, causing buffer position misalignment that corrupted parsing of
 * subsequent type annotations in the same Code attribute.
 *
 * This test uses multiple catch blocks with type annotations followed by
 * a type annotation on a method return type, to verify that type annotation
 * parsing of the entire method is not corrupted.
 */
public class TestExceptionParameterTypeAnnotation {

    @Target(ElementType.TYPE_USE)
    @Retention(RetentionPolicy.RUNTIME)
    @interface TA {
        String value() default "";
    }

    // Method with multiple type annotations including on catch parameters.
    // The EXCEPTION_PARAMETER annotations in the Code attribute must not
    // corrupt parsing of the method-level return type annotation.
    public static @TA("return") String testMethod() {
        try {
            return "ok";
        } catch (@TA("catch1") RuntimeException e) {
            return e.getMessage();
        } catch (@TA("catch2") Exception e) {
            return e.getMessage();
        }
    }

    // Method with type annotation on catch parameter followed by
    // a cast type annotation (both in the Code attribute).
    public static Object testMethodWithCast() {
        try {
            return null;
        } catch (@TA("catch") Exception e) {
            return (@TA("cast") String) e.getMessage();
        }
    }

    public static void main(String[] args) throws Exception {
        // Test 1: Verify return type annotation is not corrupted
        Method m1 = TestExceptionParameterTypeAnnotation.class
                .getMethod("testMethod");
        AnnotatedType returnType = m1.getAnnotatedReturnType();
        TA returnAnno = returnType.getAnnotation(TA.class);
        if (returnAnno == null) {
            throw new RuntimeException(
                    "Missing @TA on return type of testMethod() — " +
                    "type annotation parsing may be corrupted");
        }
        if (!"return".equals(returnAnno.value())) {
            throw new RuntimeException(
                    "Wrong @TA value on return type: expected 'return', got '"
                    + returnAnno.value() + "'");
        }

        // Test 2: Verify the class loads and methods are accessible
        // without AnnotationFormatError (which would indicate buffer
        // corruption during type annotation parsing)
        Method m2 = TestExceptionParameterTypeAnnotation.class
                .getMethod("testMethodWithCast");
        // Just accessing the method should not throw
        m2.getAnnotatedReturnType();

        System.out.println("Test passed.");
    }
}
