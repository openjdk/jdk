/*
 * Copyright (c) 2008, 2010, Oracle and/or its affiliates. All rights reserved.
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
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sun.org.glassfish.gmbal;

/**
 *
 * @author ken
 */
public enum Impact {
    /** Indicates that an action is read-like, generally only returning
     * information without modifying any state.
     * Corresponds to MBeanOperationInfo.INFO.
     */
    INFO,

    /** Indicates that an action is write-like, and may modify the state
     * of an MBean in some way.
     * Corresponds to MBeanOperationInfo.ACTION.
     */
    ACTION,

    /** Indicates that an action is both read-like and write-like.
     * Corresponds to MBeanOperationInfo.ACTION_INFO.
     */
    ACTION_INFO,

    /** Indicates that an action has an unknown nature.
     * Corresponds to MBeanOperationInfo.UNKNOWN.
     */
    UNKNOWN
}
