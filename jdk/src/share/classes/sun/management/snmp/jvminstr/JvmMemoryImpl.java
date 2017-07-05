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

// jmx imports
//
import javax.management.MBeanServer;
import com.sun.jmx.snmp.SnmpStatusException;
import com.sun.jmx.snmp.SnmpDefinitions;

// jdmk imports
//
import com.sun.jmx.snmp.agent.SnmpMib;

import java.util.Map;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.lang.management.MemoryType;
import java.lang.management.MemoryMXBean;
import javax.management.openmbean.CompositeData;

import sun.management.snmp.jvmmib.JvmMemoryMBean;
import sun.management.snmp.jvmmib.EnumJvmMemoryGCCall;
import sun.management.snmp.jvmmib.EnumJvmMemoryGCVerboseLevel;
import sun.management.snmp.util.MibLogger;
import sun.management.snmp.util.JvmContextFactory;

/**
 * The class is used for implementing the "JvmMemory" group.
 */
public class JvmMemoryImpl implements JvmMemoryMBean {

    /**
     * Variable for storing the value of "JvmMemoryGCCall".
     *
     * "This object makes it possible to remotelly trigger the
     * Garbage Collector in the JVM.
     *
     * This object's syntax is an enumeration which defines:
     *
     * * Two state values, that can be returned from a GET request:
     *
     * unsupported(1): means that remote invocation of gc() is not
     * supported by the SNMP agent.
     * supported(2)  : means that remote invocation of gc() is supported
     * by the SNMP agent.
     *
     * * One action value, that can be provided in a SET request to
     * trigger the garbage collector:
     *
     * start(3)      : means that a manager wishes to trigger
     * garbage collection.
     *
     * * Two result value, that will be returned as a result of a
     * SET request when remote invocation of gc is supported
     * by the SNMP agent:
     *
     * started(4)       : means that garbage collection was
     * successfully triggered. It does not mean
     * however that the action was successfullly
     * completed: gc might still be running when
     * this value is returned.
     * failed(5)     : means that garbage collection couldn't be
     * triggered.
     *
     * * If remote invocation is not supported by the SNMP agent, then
     * unsupported(1) will always be returned as a result of either
     * a GET request, or a SET request with start(3) as input value.
     *
     * * If a SET request with anything but start(3) is received, then
     * the agent will return a wrongValue error.
     *
     * See java.management.MemoryMXBean.gc()
     * "
     *
     */
    final static EnumJvmMemoryGCCall JvmMemoryGCCallSupported
        = new EnumJvmMemoryGCCall("supported");
    final static EnumJvmMemoryGCCall JvmMemoryGCCallStart
        = new EnumJvmMemoryGCCall("start");
    final static EnumJvmMemoryGCCall JvmMemoryGCCallFailed
        = new EnumJvmMemoryGCCall("failed");
    final static EnumJvmMemoryGCCall JvmMemoryGCCallStarted
        = new EnumJvmMemoryGCCall("started");

    /**
     * Variable for storing the value of "JvmMemoryGCVerboseLevel".
     *
     * "State of the -verbose:gc state.
     *
     * verbose: if the -verbose:gc flag is on,
     * silent:  otherwise.
     *
     * See java.management.MemoryMXBean.isVerbose(),
     * java.management.MemoryMXBean.setVerbose()
     * "
     *
     */
    final static EnumJvmMemoryGCVerboseLevel JvmMemoryGCVerboseLevelVerbose =
        new EnumJvmMemoryGCVerboseLevel("verbose");
    final static EnumJvmMemoryGCVerboseLevel JvmMemoryGCVerboseLevelSilent =
        new EnumJvmMemoryGCVerboseLevel("silent");

    /**
     * Constructor for the "JvmMemory" group.
     * If the group contains a table, the entries created through an
     * SNMP SET will not be registered in Java DMK.
     */
    public JvmMemoryImpl(SnmpMib myMib) {
    }


    /**
     * Constructor for the "JvmMemory" group.
     * If the group contains a table, the entries created through an
     * SNMP SET will be AUTOMATICALLY REGISTERED in Java DMK.
     */
    public JvmMemoryImpl(SnmpMib myMib, MBeanServer server) {
        // no entry will be registered since the table is virtual.
    }

    final static String heapMemoryTag = "jvmMemory.getHeapMemoryUsage";
    final static String nonHeapMemoryTag = "jvmMemory.getNonHeapMemoryUsage";

    private MemoryUsage getMemoryUsage(MemoryType type) {
        if (type == MemoryType.HEAP) {
            return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        } else {
            return ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage();
        }
    }

    MemoryUsage getNonHeapMemoryUsage() {
        try {
            final Map<Object, Object> m = JvmContextFactory.getUserData();

            if (m != null) {
                final MemoryUsage cached = (MemoryUsage)
                    m.get(nonHeapMemoryTag);
                if (cached != null) {
                    log.debug("getNonHeapMemoryUsage",
                          "jvmMemory.getNonHeapMemoryUsage found in cache.");
                    return cached;
                }

                final MemoryUsage u = getMemoryUsage(MemoryType.NON_HEAP);

                //  getNonHeapMemoryUsage() never returns null.
                //
                // if (u == null) u=MemoryUsage.INVALID;

                m.put(nonHeapMemoryTag,u);
                return u;
            }
            // Should never come here.
            // Log error!
            log.trace("getNonHeapMemoryUsage",
                      "ERROR: should never come here!");
            return getMemoryUsage(MemoryType.NON_HEAP);
        } catch (RuntimeException x) {
            log.trace("getNonHeapMemoryUsage",
                  "Failed to get NonHeapMemoryUsage: " + x);
            log.debug("getNonHeapMemoryUsage",x);
            throw x;
        }

    }

    MemoryUsage getHeapMemoryUsage() {
        try {
            final Map<Object, Object> m = JvmContextFactory.getUserData();

            if (m != null) {
                final MemoryUsage cached = (MemoryUsage)m.get(heapMemoryTag);
                if (cached != null) {
                    log.debug("getHeapMemoryUsage",
                          "jvmMemory.getHeapMemoryUsage found in cache.");
                    return cached;
                }

                final MemoryUsage u = getMemoryUsage(MemoryType.HEAP);

                // getHeapMemoryUsage() never returns null.
                //
                // if (u == null) u=MemoryUsage.INVALID;

                m.put(heapMemoryTag,u);
                return u;
            }

            // Should never come here.
            // Log error!
            log.trace("getHeapMemoryUsage", "ERROR: should never come here!");
            return getMemoryUsage(MemoryType.HEAP);
        } catch (RuntimeException x) {
            log.trace("getHeapMemoryUsage",
                  "Failed to get HeapMemoryUsage: " + x);
            log.debug("getHeapMemoryUsage",x);
            throw x;
        }
    }

    static final Long Long0 = new Long(0);

    /**
     * Getter for the "JvmMemoryNonHeapMaxSize" variable.
     */
    public Long getJvmMemoryNonHeapMaxSize()
        throws SnmpStatusException {
        final long val = getNonHeapMemoryUsage().getMax();
        if (val > -1) return  new Long(val);
        else return Long0;
    }

    /**
     * Getter for the "JvmMemoryNonHeapCommitted" variable.
     */
    public Long getJvmMemoryNonHeapCommitted() throws SnmpStatusException {
        final long val = getNonHeapMemoryUsage().getCommitted();
        if (val > -1) return new Long(val);
        else return Long0;
    }

    /**
     * Getter for the "JvmMemoryNonHeapUsed" variable.
     */
    public Long getJvmMemoryNonHeapUsed() throws SnmpStatusException {
        final long val = getNonHeapMemoryUsage().getUsed();
        if (val > -1) return new Long(val);
        else return Long0;
    }

    /**
     * Getter for the "JvmMemoryNonHeapInitSize" variable.
     */
    public Long getJvmMemoryNonHeapInitSize() throws SnmpStatusException {
        final long val = getNonHeapMemoryUsage().getInit();
        if (val > -1) return new Long(val);
        else return Long0;
    }

    /**
     * Getter for the "JvmMemoryHeapMaxSize" variable.
     */
    public Long getJvmMemoryHeapMaxSize() throws SnmpStatusException {
        final long val = getHeapMemoryUsage().getMax();
        if (val > -1) return new Long(val);
        else return Long0;
    }

    /**
     * Getter for the "JvmMemoryGCCall" variable.
     */
    public EnumJvmMemoryGCCall getJvmMemoryGCCall()
        throws SnmpStatusException {
        final Map<Object,Object> m = JvmContextFactory.getUserData();

        if (m != null) {
            final EnumJvmMemoryGCCall cached
                = (EnumJvmMemoryGCCall) m.get("jvmMemory.getJvmMemoryGCCall");
            if (cached != null) return cached;
        }
        return JvmMemoryGCCallSupported;
    }

    /**
     * Setter for the "JvmMemoryGCCall" variable.
     */
    public void setJvmMemoryGCCall(EnumJvmMemoryGCCall x)
        throws SnmpStatusException {
        if (x.intValue() == JvmMemoryGCCallStart.intValue()) {
            final Map<Object, Object> m = JvmContextFactory.getUserData();

            try {
                ManagementFactory.getMemoryMXBean().gc();
                if (m != null) m.put("jvmMemory.getJvmMemoryGCCall",
                                     JvmMemoryGCCallStarted);
            } catch (Exception ex) {
                if (m != null) m.put("jvmMemory.getJvmMemoryGCCall",
                                     JvmMemoryGCCallFailed);
            }
            return;
        }
        throw new SnmpStatusException(SnmpDefinitions.snmpRspWrongValue);
    }

    /**
     * Checker for the "JvmMemoryGCCall" variable.
     */
    public void checkJvmMemoryGCCall(EnumJvmMemoryGCCall x)
        throws SnmpStatusException {
        if (x.intValue() != JvmMemoryGCCallStart.intValue())
        throw new SnmpStatusException(SnmpDefinitions.snmpRspWrongValue);
    }

    /**
     * Getter for the "JvmMemoryHeapCommitted" variable.
     */
    public Long getJvmMemoryHeapCommitted() throws SnmpStatusException {
        final long val = getHeapMemoryUsage().getCommitted();
        if (val > -1) return new Long(val);
        else return Long0;
    }

    /**
     * Getter for the "JvmMemoryGCVerboseLevel" variable.
     */
    public EnumJvmMemoryGCVerboseLevel getJvmMemoryGCVerboseLevel()
        throws SnmpStatusException {
        if (ManagementFactory.getMemoryMXBean().isVerbose())
            return JvmMemoryGCVerboseLevelVerbose;
        else
            return JvmMemoryGCVerboseLevelSilent;
    }

    /**
     * Setter for the "JvmMemoryGCVerboseLevel" variable.
     */
    public void setJvmMemoryGCVerboseLevel(EnumJvmMemoryGCVerboseLevel x)
        throws SnmpStatusException {
        if (JvmMemoryGCVerboseLevelVerbose.intValue() == x.intValue())
            ManagementFactory.getMemoryMXBean().setVerbose(true);
        else
            ManagementFactory.getMemoryMXBean().setVerbose(false);
    }

    /**
     * Checker for the "JvmMemoryGCVerboseLevel" variable.
     */
    public void checkJvmMemoryGCVerboseLevel(EnumJvmMemoryGCVerboseLevel x)
        throws SnmpStatusException {
        // Nothing to check...
    }

    /**
     * Getter for the "JvmMemoryHeapUsed" variable.
     */
    public Long getJvmMemoryHeapUsed() throws SnmpStatusException {
        final long val = getHeapMemoryUsage().getUsed();
        if (val > -1) return new Long(val);
        else return Long0;
    }

    /**
     * Getter for the "JvmMemoryHeapInitSize" variable.
     */
    public Long getJvmMemoryHeapInitSize() throws SnmpStatusException {
        final long val = getHeapMemoryUsage().getInit();
        if (val > -1) return new Long(val);
        else return Long0;
    }

    /**
     * Getter for the "JvmMemoryPendingFinalCount" variable.
     */
    public Long getJvmMemoryPendingFinalCount()
        throws SnmpStatusException {
        final long val = ManagementFactory.getMemoryMXBean().
            getObjectPendingFinalizationCount();

        if (val > -1) return new Long((int)val);

        // Should never happen... but stay safe all the same.
        //
        else return new Long(0);
    }

    static final MibLogger log = new MibLogger(JvmMemoryImpl.class);
}
