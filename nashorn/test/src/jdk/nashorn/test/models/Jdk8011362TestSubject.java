/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.test.models;

/**
 * Test class used by JDK-8011362.js
 */
@SuppressWarnings("javadoc")
public class Jdk8011362TestSubject {
    // This is selected for overloaded("", null)
    public String overloaded(final String a, final String b) {
        return "overloaded(String, String)";
    }

    // This is selected for overloaded(0, null)
    public String overloaded(final Double a, final Double b) {
        return "overloaded(Double, Double)";
    }

    // This method is added to test that null will not match a primitive type, that is overloaded(0, null) will always
    // select the (Double, Double) over (Double, double).
    public String overloaded(final Double a, final double b) {
        return "overloaded(Double, double)";
    }
}
