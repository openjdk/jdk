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
import java.lang.management.ManagementFactory;
import java.lang.management.CompilationMXBean;

// jmx imports
//
import javax.management.MBeanServer;
import com.sun.jmx.snmp.SnmpString;
import com.sun.jmx.snmp.SnmpStatusException;

// jdmk imports
//
import com.sun.jmx.snmp.agent.SnmpMib;

import sun.management.snmp.jvmmib.JvmCompilationMBean;
import sun.management.snmp.jvmmib.EnumJvmJITCompilerTimeMonitoring;
import sun.management.snmp.util.MibLogger;

/**
 * The class is used for implementing the "JvmCompilation" group.
 */
public class JvmCompilationImpl implements JvmCompilationMBean {

    /**
     * Variable for storing the value of "JvmJITCompilerTimeMonitoring".
     *
     * "Indicates whether the Java virtual machine supports
     * compilation time monitoring.
     *
     * See java.management.CompilationMXBean.
     * isCompilationTimeMonitoringSupported()
     * "
     *
     */
    static final EnumJvmJITCompilerTimeMonitoring
        JvmJITCompilerTimeMonitoringSupported =
        new EnumJvmJITCompilerTimeMonitoring("supported");
    static final EnumJvmJITCompilerTimeMonitoring
        JvmJITCompilerTimeMonitoringUnsupported =
        new EnumJvmJITCompilerTimeMonitoring("unsupported");


    /**
     * Constructor for the "JvmCompilation" group.
     * If the group contains a table, the entries created through an SNMP SET
     * will not be registered in Java DMK.
     */
    public JvmCompilationImpl(SnmpMib myMib) {
    }


    /**
     * Constructor for the "JvmCompilation" group.
     * If the group contains a table, the entries created through an SNMP
     * SET will be AUTOMATICALLY REGISTERED in Java DMK.
     */
    public JvmCompilationImpl(SnmpMib myMib, MBeanServer server) {
    }

    private static CompilationMXBean getCompilationMXBean() {
        return ManagementFactory.getCompilationMXBean();
    }

    /**
     * Getter for the "JvmJITCompilerTimeMonitoring" variable.
     */
    public EnumJvmJITCompilerTimeMonitoring getJvmJITCompilerTimeMonitoring()
        throws SnmpStatusException {

        // If we reach this point, then we can safely assume that
        // getCompilationMXBean() will not return null, because this
        // object will not be instantiated when there is no compilation
        // system (see JVM_MANAGEMENT_MIB_IMPL).
        //
        if(getCompilationMXBean().isCompilationTimeMonitoringSupported())
            return JvmJITCompilerTimeMonitoringSupported;
        else
            return JvmJITCompilerTimeMonitoringUnsupported;
    }

    /**
     * Getter for the "JvmJITCompilerTimeMs" variable.
     */
    public Long getJvmJITCompilerTimeMs() throws SnmpStatusException {
        final long t;
        if(getCompilationMXBean().isCompilationTimeMonitoringSupported())
            t = getCompilationMXBean().getTotalCompilationTime();
        else
            t = 0;
        return new Long(t);
    }

    /**
     * Getter for the "JvmJITCompilerName" variable.
     */
    public String getJvmJITCompilerName() throws SnmpStatusException {
        return JVM_MANAGEMENT_MIB_IMPL.
            validJavaObjectNameTC(getCompilationMXBean().getName());
    }

}
