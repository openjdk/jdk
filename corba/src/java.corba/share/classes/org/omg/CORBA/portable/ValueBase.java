/*
 * Copyright (c) 1999, Oracle and/or its affiliates. All rights reserved.
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
 * Licensed Materials - Property of IBM
 * RMI-IIOP v1.0
 * Copyright IBM Corp. 1998 1999  All Rights Reserved
 *
 */

package org.omg.CORBA.portable;
/**
 * The generated Java classes corresponding to valuetype IDL types
 * implement this interface. In other words, the Java mapping of
 * valuetype objects implement the ValueBase interface. The generated
 * Java class for valuetype's shall provide an implementation of the
 * ValueBase interface for the corresponding value type.
 * For value types that are streamable (i.e. non-custom),
 * the generated Java class shall also provide an implementation
 * for the org.omg.CORBA.portable.Streamable interface.
 * (CORBA::ValueBase is mapped to java.io.Serializable.)
 */
public interface ValueBase extends IDLEntity {
    /**
     * Provides truncatable repository ids.
     * @return a String array--list of truncatable repository ids.
     */
    String[] _truncatable_ids();
}
