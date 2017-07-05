/*
 * Copyright 2003-2004 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */
package sun.management.snmp.jvminstr;

// java imports
//
import java.io.Serializable;

// jmx imports
//
import com.sun.jmx.snmp.SnmpStatusException;

// jdmk imports
//
import com.sun.jmx.snmp.agent.SnmpMib;

import java.lang.management.MemoryManagerMXBean;

import sun.management.snmp.jvmmib.JvmMemManagerEntryMBean;
import sun.management.snmp.jvmmib.EnumJvmMemManagerState;


/**
 * The class is used for implementing the "JvmMemManagerEntry" group.
 * The group is defined with the following
 */
public class JvmMemManagerEntryImpl implements JvmMemManagerEntryMBean {

    /**
     * Variable for storing the value of "JvmMemManagerIndex".
     *
     * "An index opaquely computed by the agent and which uniquely
     * identifies a Memory Manager."
     *
     */
    protected final int JvmMemManagerIndex;

    protected MemoryManagerMXBean manager;

    /**
     * Constructor for the "JvmMemManagerEntry" group.
     */
    public JvmMemManagerEntryImpl(MemoryManagerMXBean m, int myindex) {
        manager = m;
        JvmMemManagerIndex = myindex;
    }

    /**
     * Getter for the "JvmMemManagerName" variable.
     */
    public String getJvmMemManagerName() throws SnmpStatusException {
        return JVM_MANAGEMENT_MIB_IMPL.
            validJavaObjectNameTC(manager.getName());
    }

    /**
     * Getter for the "JvmMemManagerIndex" variable.
     */
    public Integer getJvmMemManagerIndex() throws SnmpStatusException {
        return new Integer(JvmMemManagerIndex);
    }

    /**
     * Getter for the "JvmMemManagerState" variable.
     */
    public EnumJvmMemManagerState getJvmMemManagerState()
        throws SnmpStatusException {
        if (manager.isValid())
            return JvmMemManagerStateValid;
        else
            return JvmMemManagerStateInvalid;
    }

    private final static EnumJvmMemManagerState JvmMemManagerStateValid =
        new EnumJvmMemManagerState("valid");
    private final static EnumJvmMemManagerState JvmMemManagerStateInvalid =
        new EnumJvmMemManagerState("invalid");

}
