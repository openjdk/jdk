/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

package pkg1;
/**
 * For index testing only
 */
public class ZZTop {

    /**
     * just an empty param method.
     */
    public void   add(){}

    /**
     * @param d param
     */
    public void   add(double d){}

    /**
     * @param i param
     * @param f param
     */
    public void   add(int i, float f){}

    /**
     * @param f param
     * @param i param
     */
    public void   add(float f, int i){}

    /**
     * @param d param
     * @param b param
     */
    public void   add(double d, byte b){}

    /**
     * @param d param
     * @return Double
     */
    public Double add(Double d) {return (double) 22/7;}

    /**
     * @param d1 param
     * @param d2 param
     * @return double
     */
    public double add(double d1, double d2) {return d1 + d2;}

    /**
     * @param d1 param
     * @param d2 param
     * @return double
     */
    public double add(double d1, Double  d2) {return d1 + d2;}

    /**
     * @param f param
     * @return Float
     */
    public Float  add(float f) {return (float) 22/7;}

    /**
     * @param i param
     */
    public void   add(int i){}

    /**
     * @param i param
     * @return double
     */
    public int    add(Integer i) {return 0;}
}
