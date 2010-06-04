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

import java.lang.management.ClassLoadingMXBean;
import java.lang.management.ManagementFactory;
// jmx imports
//
import javax.management.MBeanServer;
import com.sun.jmx.snmp.SnmpString;
import com.sun.jmx.snmp.SnmpStatusException;

// jdmk imports
//
import com.sun.jmx.snmp.agent.SnmpMib;

import sun.management.snmp.jvmmib.JvmClassLoadingMBean;
import sun.management.snmp.jvmmib.EnumJvmClassesVerboseLevel;
import sun.management.snmp.util.MibLogger;

/**
 * The class is used for implementing the "JvmClassLoading" group.
 */
public class JvmClassLoadingImpl implements JvmClassLoadingMBean {

    /**
     * Variable for storing the value of "JvmClassesVerboseLevel".
     *
     * "verbose: if the -verbose:class flag is set.
     * silent:  otherwise.
     *
     * See java.management.ClassLoadingMXBean.isVerbose(),
     * java.management.ClassLoadingMXBean.setVerbose()
     * "
     *
     */
    static final EnumJvmClassesVerboseLevel JvmClassesVerboseLevelVerbose =
        new EnumJvmClassesVerboseLevel("verbose");
    static final EnumJvmClassesVerboseLevel JvmClassesVerboseLevelSilent =
        new EnumJvmClassesVerboseLevel("silent");

    /**
     * Constructor for the "JvmClassLoading" group.
     * If the group contains a table, the entries created through an
     * SNMP SET will not be registered in Java DMK.
     */
    public JvmClassLoadingImpl(SnmpMib myMib) {
    }

    /**
     * Constructor for the "JvmClassLoading" group.
     * If the group contains a table, the entries created through an SNMP SET
     * will be AUTOMATICALLY REGISTERED in Java DMK.
     */
    public JvmClassLoadingImpl(SnmpMib myMib, MBeanServer server) {
    }

    static ClassLoadingMXBean getClassLoadingMXBean() {
        return ManagementFactory.getClassLoadingMXBean();
    }

    /**
     * Getter for the "JvmClassesVerboseLevel" variable.
     */
    public EnumJvmClassesVerboseLevel getJvmClassesVerboseLevel()
        throws SnmpStatusException {
        if(getClassLoadingMXBean().isVerbose())
            return JvmClassesVerboseLevelVerbose;
        else
            return JvmClassesVerboseLevelSilent;
    }

    /**
     * Setter for the "JvmClassesVerboseLevel" variable.
     */
    public void setJvmClassesVerboseLevel(EnumJvmClassesVerboseLevel x)
        throws SnmpStatusException {
        final boolean verbose;
        if (JvmClassesVerboseLevelVerbose.equals(x)) verbose=true;
        else if (JvmClassesVerboseLevelSilent.equals(x)) verbose=false;
        // Should never happen, this case is handled by
        // checkJvmClassesVerboseLevel();
        else throw new
            SnmpStatusException(SnmpStatusException.snmpRspWrongValue);
        getClassLoadingMXBean().setVerbose(verbose);
    }

    /**
     * Checker for the "JvmClassesVerboseLevel" variable.
     */
    public void checkJvmClassesVerboseLevel(EnumJvmClassesVerboseLevel x)
        throws SnmpStatusException {
        //
        // Add your own checking policy.
        //
        if (JvmClassesVerboseLevelVerbose.equals(x)) return;
        if (JvmClassesVerboseLevelSilent.equals(x))  return;
        throw new SnmpStatusException(SnmpStatusException.snmpRspWrongValue);

    }

    /**
     * Getter for the "JvmClassesUnloadedCount" variable.
     */
    public Long getJvmClassesUnloadedCount() throws SnmpStatusException {
        return new Long(getClassLoadingMXBean().getUnloadedClassCount());
    }

    /**
     * Getter for the "JvmClassesTotalLoadedCount" variable.
     */
    public Long getJvmClassesTotalLoadedCount() throws SnmpStatusException {
        return new Long(getClassLoadingMXBean().getTotalLoadedClassCount());
    }

    /**
     * Getter for the "JvmClassesLoadedCount" variable.
     */
    public Long getJvmClassesLoadedCount() throws SnmpStatusException {
        return new Long(getClassLoadingMXBean().getLoadedClassCount());
    }

}
