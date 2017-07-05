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
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;

// jmx imports
//
import javax.management.MBeanServer;
import com.sun.jmx.snmp.SnmpString;
import com.sun.jmx.snmp.SnmpStatusException;

// jdmk imports
//
import com.sun.jmx.snmp.agent.SnmpMib;

import sun.management.snmp.jvmmib.JvmOSMBean;

/**
 * The class is used for implementing the "JvmOS" group.
 */
public class JvmOSImpl implements JvmOSMBean, Serializable {

    /**
     * Constructor for the "JvmOS" group.
     * If the group contains a table, the entries created through an
     * SNMP SET will not be registered in Java DMK.
     */
    public JvmOSImpl(SnmpMib myMib) {
    }


    /**
     * Constructor for the "JvmOS" group.
     * If the group contains a table, the entries created through an
     * SNMP SET will be AUTOMATICALLY REGISTERED in Java DMK.
     */
    public JvmOSImpl(SnmpMib myMib, MBeanServer server) {
    }

    static OperatingSystemMXBean getOSMBean() {
        return ManagementFactory.getOperatingSystemMXBean();
    }

    private static String validDisplayStringTC(String str) {
        return JVM_MANAGEMENT_MIB_IMPL.validDisplayStringTC(str);
    }

    private static String validJavaObjectNameTC(String str) {
        return JVM_MANAGEMENT_MIB_IMPL.validJavaObjectNameTC(str);
    }

    /**
     * Getter for the "JvmRTProcessorCount" variable.
     */
    public Integer getJvmOSProcessorCount() throws SnmpStatusException {
        return new Integer(getOSMBean().getAvailableProcessors());

    }

    /**
     * Getter for the "JvmOSVersion" variable.
     */
    public String getJvmOSVersion() throws SnmpStatusException {
        return validDisplayStringTC(getOSMBean().getVersion());
    }

    /**
     * Getter for the "JvmOSArch" variable.
     */
    public String getJvmOSArch() throws SnmpStatusException {
        return validDisplayStringTC(getOSMBean().getArch());
    }

    /**
     * Getter for the "JvmOSName" variable.
     */
    public String getJvmOSName() throws SnmpStatusException {
        return validJavaObjectNameTC(getOSMBean().getName());
    }

}
