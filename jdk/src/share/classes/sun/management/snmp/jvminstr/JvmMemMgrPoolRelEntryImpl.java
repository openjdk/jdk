/*
 * Copyright (c) 2003, 2004, Oracle and/or its affiliates. All rights reserved.
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
package sun.management.snmp.jvminstr;

// jmx imports
//
import com.sun.jmx.snmp.SnmpStatusException;

// jdmk imports
//


import sun.management.snmp.jvmmib.JvmMemMgrPoolRelEntryMBean;

/**
 * The class is used for implementing the "JvmMemMgrPoolRelEntry" group.
 */
public class JvmMemMgrPoolRelEntryImpl
    implements JvmMemMgrPoolRelEntryMBean {

    /**
     * Variable for storing the value of "JvmMemManagerIndex".
     *
     * "An index opaquely computed by the agent and which uniquely
     * identifies a Memory Manager."
     *
     */
    final protected int JvmMemManagerIndex;

    /**
     * Variable for storing the value of "JvmMemPoolIndex".
     *
     * "An index value opaquely computed by the agent which uniquely
     * identifies a row in the jvmMemPoolTable.
     * "
     *
     */
    final protected int JvmMemPoolIndex;
    final protected String mmmName;
    final protected String mpmName;

    /**
     * Constructor for the "JvmMemMgrPoolRelEntry" group.
     */
    public JvmMemMgrPoolRelEntryImpl(String mmmName,
                                     String mpmName,
                                     int mmarc, int mparc) {
        JvmMemManagerIndex = mmarc;
        JvmMemPoolIndex    = mparc;

        this.mmmName = mmmName;
        this.mpmName = mpmName;
    }

    /**
     * Getter for the "JvmMemMgrRelPoolName" variable.
     */
    public String getJvmMemMgrRelPoolName() throws SnmpStatusException {
        return JVM_MANAGEMENT_MIB_IMPL.validJavaObjectNameTC(mpmName);
    }

    /**
     * Getter for the "JvmMemMgrRelManagerName" variable.
     */
    public String getJvmMemMgrRelManagerName() throws SnmpStatusException {
        return JVM_MANAGEMENT_MIB_IMPL.validJavaObjectNameTC(mmmName);
    }

    /**
     * Getter for the "JvmMemManagerIndex" variable.
     */
    public Integer getJvmMemManagerIndex() throws SnmpStatusException {
        return new Integer(JvmMemManagerIndex);
    }

    /**
     * Getter for the "JvmMemPoolIndex" variable.
     */
    public Integer getJvmMemPoolIndex() throws SnmpStatusException {
        return new Integer(JvmMemPoolIndex);
    }

}
