/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8392648
 * @summary Verify casts to boolean and Boolean work reasonably in condition
 *          position, w.r.t. DA/DU.
 * @modules java.base/jdk.internal.classfile.impl
 * @compile GenCondCast.java
 * @run main GenCondCast
 */

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.TypeAnnotation.TargetInfo;
import java.lang.classfile.TypeAnnotation.TypeArgumentTarget;
import java.lang.classfile.attribute.RuntimeInvisibleTypeAnnotationsAttribute;
import java.util.List;
import java.util.Objects;
import java.util.function.ToIntFunction;
import jdk.internal.classfile.impl.LabelImpl;

public class GenCondCast {
    static int test1(boolean b) {
        int result;
        if ((boolean) (b && (result = 1) > 0)) return result;
        return 0;
    }

    static int test1_flip(boolean b) {
        int result;
        if ((boolean) ((result = 1) > 0) && b) return result;
        return 0;
    }

    static int test1_anno(boolean b) {
        int result;
        if ((@Ann boolean) (b && (result = 1) > 0)) return result;
        return 0;
    }

    static int test1_flip_anno(boolean b) {
        int result;
        if ((@Ann boolean) ((result = 1) > 0) && b) return result;
        return 0;
    }

    static int test2(boolean b) {
        int result;
        if ((Boolean) (b && (result = 1) > 0)) return result;
        return 0;
    }

    static int test2_flip(boolean b) {
        int result;
        if ((Boolean) ((result = 1) > 0) && b) return result;
        return 0;
    }

    static int test2_anno(boolean b) {
        int result;
        if ((@Ann Boolean) (b && (result = 1) > 0)) return result;
        return 0;
    }

    static int test2_flip_anno(boolean b) {
        int result;
        if ((@Ann Boolean) ((result = 1) > 0) && b) return result;
        return 0;
    }

    static int test3(boolean b) {
        if ((boolean) id(b)) return 1;
        else return 0;
    }

    static int test3_anno(boolean b) {
        if ((@Ann boolean) id(b)) return 1;
        else return 0;
    }

    static int test4(boolean b) {
        if ((Boolean) id(b)) return 1;
        else return 0;
    }

    static int test4_anno(boolean b) {
        if ((@Ann Boolean) id(b)) return 1;
        else return 0;
    }

    static int testLarge_anno(boolean b) {
        int result;
        if ((@Ann Boolean) (b && (result = 1) > 0)) {
            //enough code here to force long jumps:
            record R(String s0, String s1, String s2, String s3, String s4) {}
            Object r = new R("", "", "", "", "");
            switch (r) {
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                case R(var s0, var s1, var s2, var s3, var s4) when !s0.isEmpty() ->
                    System.out.print(s0 + s1 + s2 + s3 + s4);
                default -> {}
            }
            return result;
        } else {
            return 0;
        }
    }

    static int testRedundant1_anno(boolean b) {
        int result;
        if ((@Ann Boolean) (@Ann Boolean) (@Ann Boolean) (b && (result = 1) > 0)) return result;
        return 0;
    }

    static int testRedundant2_anno(boolean b) {
        int result;
        if ((@Ann Boolean) (@Ann boolean) (@Ann Boolean) (b && (result = 1) > 0)) return result;
        return 0;
    }

    static int testCastChainObject1_anno(boolean b) {
        int result;
        if ((@Ann Boolean) (@Ann Object) (@Ann Boolean) (b && (result = 1) > 0)) return result;
        return 0;
    }

    static int testCastChainObject2_anno(boolean b) {
        int result;
        if ((@Ann Boolean) (@Ann Object) (b && (result = 1) > 0)) return result;
        return 0;
    }

    static int testExpectCCE(boolean b) {
        int result;
        if ((@Ann Boolean) (@Ann Object) (@Ann String) (@Ann Object) (@Ann Boolean) (b && (result = 1) > 0)) {
            return result;
        }
        return 0;
    }

    static int annotated(boolean b) {
        {
            int result;
            if ((@Ann boolean) (b && (result = 1) > 0)) return result;
        }
        {
            int result;
            if ((@Ann boolean) ((result = 1) > 0) && b) return result;
        }
        //note that the boxing&unboxing is duplicated, but the type annotations
        //is only assigned to the second duplicate. That's consistent with
        //how duplicated code for e.g. try-finally works:
        {
            int result;
            if ((@Ann Boolean) (b && (result = 1) > 0)) return result;
        }
        {
            int result;
            if ((@Ann Boolean) ((result = 1) > 0) && b) return result;
        }
        boolean _ = (@Ann boolean) b;
        boolean _ = (@Ann Boolean) b;
        return 0;
    }

    static Object id(boolean b) {
        return b;
    }

    static void doTest(ToIntFunction<Boolean> testMethod) {
        if (testMethod.applyAsInt(true) != 1) {
            throw new IllegalStateException("Wrong value!");
        }
        if (testMethod.applyAsInt(false) != 0) {
            throw new IllegalStateException("Wrong value!");
        }
    }

    public static void main(String... args) throws Exception {
        doTest(GenCondCast::test1);
        doTest(GenCondCast::test1_flip);
        doTest(GenCondCast::test1_anno);
        doTest(GenCondCast::test1_flip_anno);
        doTest(GenCondCast::test2);
        doTest(GenCondCast::test2_flip);
        doTest(GenCondCast::test2_anno);
        doTest(GenCondCast::test2_flip_anno);
        doTest(GenCondCast::test3);
        doTest(GenCondCast::test3_anno);
        doTest(GenCondCast::test4);
        doTest(GenCondCast::test4_anno);
        doTest(GenCondCast::testLarge_anno);
        doTest(GenCondCast::testRedundant1_anno);
        doTest(GenCondCast::testRedundant2_anno);
        doTest(GenCondCast::testCastChainObject1_anno);
        doTest(GenCondCast::testCastChainObject2_anno);
        try {
            testExpectCCE(true);
            throw new AssertionError("Should have got an exception.");
        } catch (ClassCastException _) {
            //correct
        }
        try {
            testExpectCCE(false);
            throw new AssertionError("Should have got an exception.");
        } catch (ClassCastException _) {
            //correct
        }

        byte[] classBytes = GenCondCast.class.getClassLoader().getResourceAsStream(GenCondCast.class.getName().replace('.', '/') + ".class").readAllBytes();
        ClassModel model = ClassFile.of().parse(classBytes);
        {
            MethodModel annotated = model.methods().stream().filter(m -> m.methodName().equalsString("annotated")).findAny().orElseThrow();
            RuntimeInvisibleTypeAnnotationsAttribute typeAnnotations = annotated.code().orElseThrow().findAttribute(Attributes.runtimeInvisibleTypeAnnotations()).orElseThrow();
            List<String> actualTAs = typeAnnotations.annotations().stream().map(ta -> toString(ta.targetInfo())  + ", " + ta.targetPath() + ", " + ta.annotation()).toList();
            List<String> expectedTAs = List.of(
                "CAST:0:7, [], Annotation[LGenCondCast$Ann;]",
                "CAST:0:15, [], Annotation[LGenCondCast$Ann;]",
                "CAST:0:44, [], Annotation[LGenCondCast$Ann;]",
                "CAST:0:66, [], Annotation[LGenCondCast$Ann;]",
                "CAST:0:77, [], Annotation[LGenCondCast$Ann;]",
                "CAST:0:82, [], Annotation[LGenCondCast$Ann;]"
            );

            if (!Objects.equals(expectedTAs, actualTAs)) {
                throw new AssertionError("expected: " + expectedTAs + ", got: " + actualTAs);
            }
        }
        {
            MethodModel testLarge_anno = model.methods().stream().filter(m -> m.methodName().equalsString("testLarge_anno")).findAny().orElseThrow();
            RuntimeInvisibleTypeAnnotationsAttribute typeAnnotations = testLarge_anno.code().orElseThrow().findAttribute(Attributes.runtimeInvisibleTypeAnnotations()).orElseThrow();
            List<String> actualTAs = typeAnnotations.annotations().stream().map(ta -> toString(ta.targetInfo())  + ", " + ta.targetPath() + ", " + ta.annotation()).toList();
            List<String> expectedTAs = List.of(
                "CAST:0:32, [], Annotation[LGenCondCast$Ann;]"
            );

            if (!Objects.equals(expectedTAs, actualTAs)) {
                throw new AssertionError("expected: " + expectedTAs + ", got: " + actualTAs);
            }
        }
        {
            MethodModel testRedundant1_anno = model.methods().stream().filter(m -> m.methodName().equalsString("testRedundant1_anno")).findAny().orElseThrow();
            RuntimeInvisibleTypeAnnotationsAttribute typeAnnotations = testRedundant1_anno.code().orElseThrow().findAttribute(Attributes.runtimeInvisibleTypeAnnotations()).orElseThrow();
            List<String> actualTAs = typeAnnotations.annotations().stream().map(ta -> toString(ta.targetInfo())  + ", " + ta.targetPath() + ", " + ta.annotation()).toList();
            List<String> expectedTAs = List.of(
                "CAST:0:20, [], Annotation[LGenCondCast$Ann;]",
                "CAST:0:20, [], Annotation[LGenCondCast$Ann;]",
                "CAST:0:20, [], Annotation[LGenCondCast$Ann;]"
            );

            if (!Objects.equals(expectedTAs, actualTAs)) {
                throw new AssertionError("expected: " + expectedTAs + ", got: " + actualTAs);
            }
        }
        {
            MethodModel testRedundant2_anno = model.methods().stream().filter(m -> m.methodName().equalsString("testRedundant2_anno")).findAny().orElseThrow();
            RuntimeInvisibleTypeAnnotationsAttribute typeAnnotations = testRedundant2_anno.code().orElseThrow().findAttribute(Attributes.runtimeInvisibleTypeAnnotations()).orElseThrow();
            List<String> actualTAs = typeAnnotations.annotations().stream().map(ta -> toString(ta.targetInfo())  + ", " + ta.targetPath() + ", " + ta.annotation()).toList();
            List<String> expectedTAs = List.of(
                "CAST:0:34, [], Annotation[LGenCondCast$Ann;]",
                "CAST:0:24, [], Annotation[LGenCondCast$Ann;]",
                "CAST:0:20, [], Annotation[LGenCondCast$Ann;]"
            );

            if (!Objects.equals(expectedTAs, actualTAs)) {
                throw new AssertionError("expected: " + expectedTAs + ", got: " + actualTAs);
            }
        }
        {
            MethodModel testCastChainObject_anno = model.methods().stream().filter(m -> m.methodName().equalsString("testCastChainObject1_anno")).findAny().orElseThrow();
            RuntimeInvisibleTypeAnnotationsAttribute typeAnnotations = testCastChainObject_anno.code().orElseThrow().findAttribute(Attributes.runtimeInvisibleTypeAnnotations()).orElseThrow();
            List<String> actualTAs = typeAnnotations.annotations().stream().map(ta -> toString(ta.targetInfo())  + ", " + ta.targetPath() + ", " + ta.annotation()).toList();
            List<String> expectedTAs = List.of(
                "CAST:0:23, [], Annotation[LGenCondCast$Ann;]",
                "CAST:0:23, [], Annotation[LGenCondCast$Ann;]",
                "CAST:0:23, [], Annotation[LGenCondCast$Ann;]"
            );

            if (!Objects.equals(expectedTAs, actualTAs)) {
                throw new AssertionError("expected: " + expectedTAs + ", got: " + actualTAs);
            }
        }
    }

    static String toString(TargetInfo ti) {
        return switch (ti) {
            case TypeArgumentTarget tat -> tat.targetType() + ":" + tat.typeArgumentIndex() + ":" + ((LabelImpl) tat.target()).getBCI();
            default -> ti.getClass().getName();
        };
    }

    @Target(ElementType.TYPE_USE)
    @interface Ann {}
}
