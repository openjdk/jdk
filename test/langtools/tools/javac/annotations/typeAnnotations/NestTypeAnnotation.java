/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8274942
 * @summary javac should attribute the internal annotations of the annotation element value
 * @compile NestTypeAnnotation.java
 */

import java.lang.annotation.*;

public class NestTypeAnnotation {
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
    public @interface OuterAnnotation {
        int intVal();
        float floatVal();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
    public @interface InnerAnnotation { }

    public static void main(String[] args) {
        int intVal1 = (@OuterAnnotation(intVal = (@InnerAnnotation() int) 2.5, floatVal = (@InnerAnnotation() float) 2.5) int) 2.4;
        int[] arr = new int []{1, 2}; // use `2.4 * arr[0] + arr[1]` to prevent optimization.
        int intVal2 = (@OuterAnnotation(intVal = (@InnerAnnotation() int) 2.5, floatVal = (@InnerAnnotation() float) 2.5) int) (2.4 * arr[0] + arr[1]);

        int[] singleArr1 = new @OuterAnnotation(intVal = (@InnerAnnotation() int) 2.5, floatVal = (@InnerAnnotation() float) 2.5) int [2];
        @OuterAnnotation(intVal = (@InnerAnnotation() int) 2.5, floatVal = (@InnerAnnotation() float) 2.5) int[] singleArr2 = new int [2];
        int[] singleArr3 = new  int @OuterAnnotation(intVal = (@InnerAnnotation() int) 2.5, floatVal = (@InnerAnnotation() float) 2.5) [2];

        int[][] multiArr1 = new int @OuterAnnotation(intVal = (@InnerAnnotation() int) 2.5, floatVal = (@InnerAnnotation() float) 2.5) [2][3];
        int[][] multiArr2 = new int [2] @OuterAnnotation(intVal = (@InnerAnnotation() int) 2.5, floatVal = (@InnerAnnotation() float) 2.5) [3];
    }
}
