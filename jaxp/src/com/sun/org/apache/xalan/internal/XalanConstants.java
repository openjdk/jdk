/*
 * Copyright (c) 2011 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.org.apache.xalan.internal;

import com.sun.org.apache.xerces.internal.impl.*;
import java.util.Enumeration;
import java.util.NoSuchElementException;

/**
 * Commonly used constants.
 *
 * @author Huizhe Wang, Oracle
 *
 * @version $Id: Constants.java,v 1.14 2011-06-07 04:39:40 joehw Exp $
 */
public final class XalanConstants {

    //
    // Constants
    //
    // Oracle Feature:
        /**
         * <p>Use Service Mechanism</p>
         *
         * <ul>
         *   <li>
         *     <code>true</code> instructs the implementation to use service mechanism to find implementation.
         *     This is the default behavior.
         *   </li>
         *   <li>
         *     <code>false</code> instructs the implementation to skip service mechanism and use the default implementation.
         *   </li>
         * </ul>
         */
    public static final String ORACLE_FEATURE_SERVICE_MECHANISM = "http://www.oracle.com/feature/use-service-mechanism";

} // class Constants
