/*
 * Copyright (c) 2009, 2013, Oracle and/or its affiliates. All rights reserved.
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


package com.sun.org.glassfish.external.statistics;

/**
 * Custom statistic type created for the Sun ONE Application Server.
 * The goal is to be able to represent changing attribute values that are strings
 * in the form of Statistics. Semantically, it is analogous to a {@link CountStatistic},
 * the only difference being in the value that is returned. Unlike a CountStatistic
 * (which always is unidirectional), this Statistic type is not having any
 * specified direction, simply because there is no natural order. An example
 * of the values that an instance of this statistic type can assume is: A State
 * Statistic which can have "CONNECTED, CLOSED, DISCONNECTED" as the permissible
 * values and the current value can be any one of them (and them only).
 * The permissible values are upto a particular implementation.
 */

public interface StringStatistic extends Statistic {
    /**
     * Returns the String value of the statistic
     */
    String getCurrent();
}
