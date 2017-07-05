/*
 * Copyright (c) 2003, 2012, Oracle and/or its affiliates. All rights reserved.
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
import com.sun.jmx.snmp.SnmpCounter;
import com.sun.jmx.snmp.SnmpCounter64;
import com.sun.jmx.snmp.SnmpGauge;
import com.sun.jmx.snmp.SnmpInt;
import com.sun.jmx.snmp.SnmpUnsignedInt;
import com.sun.jmx.snmp.SnmpIpAddress;
import com.sun.jmx.snmp.SnmpTimeticks;
import com.sun.jmx.snmp.SnmpOpaque;
import com.sun.jmx.snmp.SnmpString;
import com.sun.jmx.snmp.SnmpStringFixed;
import com.sun.jmx.snmp.SnmpOid;
import com.sun.jmx.snmp.SnmpNull;
import com.sun.jmx.snmp.SnmpValue;
import com.sun.jmx.snmp.SnmpVarBind;
import com.sun.jmx.snmp.SnmpStatusException;

// jdmk imports
//
import com.sun.jmx.snmp.agent.SnmpMib;
import com.sun.jmx.snmp.agent.SnmpMibGroup;
import com.sun.jmx.snmp.agent.SnmpStandardObjectServer;
import com.sun.jmx.snmp.agent.SnmpStandardMetaServer;
import com.sun.jmx.snmp.agent.SnmpMibSubRequest;
import com.sun.jmx.snmp.agent.SnmpMibTable;
import com.sun.jmx.snmp.EnumRowStatus;

import sun.management.snmp.jvmmib.JvmRuntimeMeta;
import sun.management.snmp.jvmmib.JvmRTInputArgsTableMeta;
import sun.management.snmp.jvmmib.JvmRTClassPathTableMeta;
import sun.management.snmp.jvmmib.JvmRTBootClassPathTableMeta;
import sun.management.snmp.jvmmib.JvmRTLibraryPathTableMeta;

/**
 * The class is used for representing SNMP metadata for the "JvmRuntime" group.
 */
public class JvmRuntimeMetaImpl extends JvmRuntimeMeta {

     static final long serialVersionUID = -6570428414857608618L;
    /**
     * Constructor for the metadata associated to "JvmRuntime".
     */
    public JvmRuntimeMetaImpl(SnmpMib myMib,
                              SnmpStandardObjectServer objserv) {
        super(myMib, objserv);
    }

    /**
     * Factory method for "JvmRTInputArgsTable" table metadata class.
     *
     * You can redefine this method if you need to replace the default
     * generated metadata class with your own customized class.
     *
     * @param tableName Name of the table object ("JvmRTInputArgsTable")
     * @param groupName Name of the group to which this table belong
     *        ("JvmRuntime")
     * @param mib The SnmpMib object in which this table is registered
     * @param server MBeanServer for this table entries (may be null)
     *
     * @return An instance of the metadata class generated for the
     *         "JvmRTInputArgsTable" table (JvmRTInputArgsTableMeta)
     *
     **/
    protected JvmRTInputArgsTableMeta
        createJvmRTInputArgsTableMetaNode(String tableName, String groupName,
                                          SnmpMib mib, MBeanServer server)  {
        return new JvmRTInputArgsTableMetaImpl(mib, objectserver);
    }

    /**
     * Factory method for "JvmRTLibraryPathTable" table metadata class.
     *
     * You can redefine this method if you need to replace the default
     * generated metadata class with your own customized class.
     *
     * @param tableName Name of the table object ("JvmRTLibraryPathTable")
     * @param groupName Name of the group to which this table belong
     *        ("JvmRuntime")
     * @param mib The SnmpMib object in which this table is registered
     * @param server MBeanServer for this table entries (may be null)
     *
     * @return An instance of the metadata class generated for the
     *         "JvmRTLibraryPathTable" table (JvmRTLibraryPathTableMeta)
     *
     **/
    protected JvmRTLibraryPathTableMeta
        createJvmRTLibraryPathTableMetaNode(String tableName,
                                            String groupName,
                                            SnmpMib mib,
                                            MBeanServer server)  {
        return new JvmRTLibraryPathTableMetaImpl(mib, objectserver);
    }


    /**
     * Factory method for "JvmRTClassPathTable" table metadata class.
     *
     * You can redefine this method if you need to replace the default
     * generated metadata class with your own customized class.
     *
     * @param tableName Name of the table object ("JvmRTClassPathTable")
     * @param groupName Name of the group to which this table belong
     *        ("JvmRuntime")
     * @param mib The SnmpMib object in which this table is registered
     * @param server MBeanServer for this table entries (may be null)
     *
     * @return An instance of the metadata class generated for the
     *         "JvmRTClassPathTable" table (JvmRTClassPathTableMeta)
     *
     **/
    protected JvmRTClassPathTableMeta
        createJvmRTClassPathTableMetaNode(String tableName, String groupName,
                                          SnmpMib mib, MBeanServer server)  {
        return new JvmRTClassPathTableMetaImpl(mib, objectserver);
    }


    /**
     * Factory method for "JvmRTBootClassPathTable" table metadata class.
     *
     * You can redefine this method if you need to replace the default
     * generated metadata class with your own customized class.
     *
     * @param tableName Name of the table object ("JvmRTBootClassPathTable")
     * @param groupName Name of the group to which this table belong
     *        ("JvmRuntime")
     * @param mib The SnmpMib object in which this table is registered
     * @param server MBeanServer for this table entries (may be null)
     *
     * @return An instance of the metadata class generated for the
     *         "JvmRTBootClassPathTable" table (JvmRTBootClassPathTableMeta)
     *
     **/
    protected JvmRTBootClassPathTableMeta
        createJvmRTBootClassPathTableMetaNode(String tableName,
                                              String groupName,
                                              SnmpMib mib,
                                              MBeanServer server)  {
        return new JvmRTBootClassPathTableMetaImpl(mib, objectserver);
    }


}
