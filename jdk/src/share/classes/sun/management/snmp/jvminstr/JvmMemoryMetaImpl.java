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

// java imports
//
import java.io.Serializable;

// jmx imports
//
import javax.management.MBeanServer;
import com.sun.jmx.snmp.SnmpOid;
import com.sun.jmx.snmp.SnmpStatusException;

// jdmk imports
//
import com.sun.jmx.snmp.agent.SnmpMib;
import com.sun.jmx.snmp.agent.SnmpStandardObjectServer;

import sun.management.snmp.jvmmib.JvmMemoryMeta;
import sun.management.snmp.jvmmib.JvmMemManagerTableMeta;
import sun.management.snmp.jvmmib.JvmMemGCTableMeta;
import sun.management.snmp.jvmmib.JvmMemPoolTableMeta;
import sun.management.snmp.jvmmib.JvmMemMgrPoolRelTableMeta;
import sun.management.snmp.util.MibLogger;

/**
 * The class is used for representing SNMP metadata for the "JvmMemory" group.
 */
public class JvmMemoryMetaImpl extends JvmMemoryMeta {
    /**
     * Constructor for the metadata associated to "JvmMemory".
     */
    public JvmMemoryMetaImpl(SnmpMib myMib, SnmpStandardObjectServer objserv) {
        super(myMib,objserv);
    }

    /**
     * Factory method for "JvmMemManagerTable" table metadata class.
     *
     * You can redefine this method if you need to replace the default
     * generated metadata class with your own customized class.
     *
     * @param tableName Name of the table object ("JvmMemManagerTable")
     * @param groupName Name of the group to which this table belong
     *        ("JvmMemory")
     * @param mib The SnmpMib object in which this table is registered
     * @param server MBeanServer for this table entries (may be null)
     *
     * @return An instance of the metadata class generated for the
     *         "JvmMemManagerTable" table (JvmMemManagerTableMeta)
     *
     **/
    protected JvmMemManagerTableMeta createJvmMemManagerTableMetaNode(
        String tableName, String groupName, SnmpMib mib, MBeanServer server)  {
        return new JvmMemManagerTableMetaImpl(mib, objectserver);
    }


    /**
     * Factory method for "JvmMemGCTable" table metadata class.
     *
     * You can redefine this method if you need to replace the default
     * generated metadata class with your own customized class.
     *
     * @param tableName Name of the table object ("JvmMemGCTable")
     * @param groupName Name of the group to which this table belong
     *        ("JvmMemory")
     * @param mib The SnmpMib object in which this table is registered
     * @param server MBeanServer for this table entries (may be null)
     *
     * @return An instance of the metadata class generated for the
     *         "JvmMemGCTable" table (JvmMemGCTableMeta)
     *
     **/
    protected JvmMemGCTableMeta createJvmMemGCTableMetaNode(String tableName,
                      String groupName, SnmpMib mib, MBeanServer server)  {
        return new JvmMemGCTableMetaImpl(mib, objectserver);
    }


    /**
     * Factory method for "JvmMemPoolTable" table metadata class.
     *
     * You can redefine this method if you need to replace the default
     * generated metadata class with your own customized class.
     *
     * @param tableName Name of the table object ("JvmMemPoolTable")
     * @param groupName Name of the group to which this table belong
     *        ("JvmMemory")
     * @param mib The SnmpMib object in which this table is registered
     * @param server MBeanServer for this table entries (may be null)
     *
     * @return An instance of the metadata class generated for the
     *         "JvmMemPoolTable" table (JvmMemPoolTableMeta)
     *
     **/
    protected JvmMemPoolTableMeta
        createJvmMemPoolTableMetaNode(String tableName, String groupName,
                                      SnmpMib mib, MBeanServer server)  {
        return new JvmMemPoolTableMetaImpl(mib, objectserver);
    }

    /**
     * Factory method for "JvmMemMgrPoolRelTable" table metadata class.
     *
     * You can redefine this method if you need to replace the default
     * generated metadata class with your own customized class.
     *
     * @param tableName Name of the table object ("JvmMemMgrPoolRelTable")
     * @param groupName Name of the group to which this table belong
     *        ("JvmMemory")
     * @param mib The SnmpMib object in which this table is registered
     * @param server MBeanServer for this table entries (may be null)
     *
     * @return An instance of the metadata class generated for the
     *         "JvmMemMgrPoolRelTable" table (JvmMemMgrPoolRelTableMeta)
     *
     **/
    protected JvmMemMgrPoolRelTableMeta
        createJvmMemMgrPoolRelTableMetaNode(String tableName,
                                            String groupName,
                                            SnmpMib mib, MBeanServer server) {
        return new JvmMemMgrPoolRelTableMetaImpl(mib, objectserver);
    }

}
