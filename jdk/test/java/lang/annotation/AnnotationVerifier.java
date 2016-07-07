/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 * Create class file using ASM, slightly modified the ASMifier output
 */

import sun.reflect.annotation.AnnotationType;
import java.lang.annotation.AnnotationFormatError;
import org.testng.annotations.*;

/*
 * @test
 * @bug 8158510
 * @summary Verify valid annotation
 * @modules java.base/jdk.internal.org.objectweb.asm
 * @modules java.base/sun.reflect.annotation
 * @clean AnnotationWithVoidReturn.class AnnotationWithParameter.class
 * @compile -XDignore.symbol.file ClassFileGenerator.java
 * @run main ClassFileGenerator
 * @run testng AnnotationVerifier
 */

public class AnnotationVerifier {

    @AnnotationWithParameter
    @AnnotationWithVoidReturn
    static class BadAnnotation {
    }

    @Test
    @ExpectedExceptions(IllegalArgumentException.class)
    public void annotationValidationIAE() {
        AnnotationType.getInstance(AnnotationWithParameter.class);
    }

    @Test(expectedExceptions = AnnotationFormatError.class)
    public void annotationValidationAFE() {
        BadAnnotation.class.getAnnotation(AnnotationWithVoidReturn.class);
    }
}
