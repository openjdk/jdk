/*
 * Copyright (c) 2000, 2003, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.jmx.snmp.agent;

import javax.management.ObjectName;
import com.sun.jmx.snmp.SnmpStatusException;
import com.sun.jmx.snmp.SnmpOid;
import com.sun.jmx.snmp.agent.SnmpMibTable;

/**
 * This interface ensures the synchronization between Metadata table objects
 * and bean-like table objects.
 *
 * It is used between mibgen generated table meta and table classes.
 * <p><b><i>
 * You should never need to use this interface directly.
 * </p></b></i>
 *
 * <p><b>This API is a Sun Microsystems internal API  and is subject
 * to change without notice.</b></p>
 **/
public interface SnmpTableCallbackHandler {
    /**
     * This method is called by the SNMP runtime after a new entry
     * has been added to the table.
     *
     * If an SnmpStatusException is raised, the entry will be removed
     * and the operation will be aborted. In this case, the removeEntryCb()
     * callback will not be called.
     *
     * <p><b><i>
     * You should never need to use this method directly.
     * </p></b></i>
     *
     **/
    public void addEntryCb(int pos, SnmpOid row, ObjectName name,
                           Object entry, SnmpMibTable meta)
        throws SnmpStatusException;

    /**
     * This method is called by the SNMP runtime after a new entry
     * has been removed from the table.
     *
     * If raised, SnmpStatusException will be ignored.
     *
     * <p><b><i>
     * You should never need to use this method directly.
     * </p></b></i>
     *
     **/
    public void removeEntryCb(int pos, SnmpOid row, ObjectName name,
                              Object entry, SnmpMibTable meta)
        throws SnmpStatusException;
}
