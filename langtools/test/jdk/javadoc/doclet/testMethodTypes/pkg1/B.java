/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
 * This interface has different types of methods such as "Instance Methods" and
 * "Abstract Methods". All the tabs will display same list of methods.
 */
public interface B {

    /**
     * This is the first abstract instance method.
     */
    public void setName();

    /**
     * This is the second abstract instance method.
     */
    public String getName();

    /**
     * This is the third abstract instance method.
     */
    public boolean addEntry();

    /**
     * This is the fourth abstract instance method.
     */
    public boolean removeEntry();

    /**
     * This is the fifth abstract instance method.
     */
    public String getPermissions();
}
