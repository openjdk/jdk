/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.policy;


/**
 * This interface defines method that is used to handle actual equality comparison and hash code generation for PolicyMapKey object.
 * <p/>
 * The different implementations of this interface may allow different strategies to be applied for operations mentioned above. This feature
 * is used within {@link WSPolicyMap} to restrict set of fields to be compared when searching different policy scope maps.
 *
 *
 *
 * @author Marek Potociar
 */
interface PolicyMapKeyHandler {
    boolean areEqual(PolicyMapKey locator1, PolicyMapKey locator2);

    int generateHashCode(PolicyMapKey locator);
}
