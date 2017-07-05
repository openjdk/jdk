/*
 * Copyright (c) 2003, Oracle and/or its affiliates. All rights reserved.
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
package sun.management.snmp.util;

import com.sun.jmx.snmp.SnmpOid;

/**
 * Defines the interface implemented by an object that holds
 * table data.
 **/
public interface SnmpTableHandler {

    /**
     * Returns the data associated with the given index.
     * If the given index is not found, null is returned.
     * Note that returning null does not necessarily means that
     * the index was not found.
     **/
    public Object  getData(SnmpOid index);

    /**
     * Returns the index that immediately follows the given
     * <var>index</var>. The returned index is strictly greater
     * than the given <var>index</var>, and is contained in the table.
     * <br>If the given <var>index</var> is null, returns the first
     * index in the table.
     * <br>If there are no index after the given <var>index</var>,
     * returns null.
     **/
    public SnmpOid getNext(SnmpOid index);

    /**
     * Returns true if the given <var>index</var> is present.
     **/
    public boolean contains(SnmpOid index);

}
