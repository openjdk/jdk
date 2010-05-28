/*
 * Copyright (c) 2003, 2008, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Map;

// jmx imports
//
import com.sun.jmx.snmp.SnmpStatusException;
import com.sun.jmx.snmp.SnmpDefinitions;

// jdmk imports
//

import java.lang.management.MemoryUsage;
import java.lang.management.MemoryType;
import java.lang.management.MemoryPoolMXBean;

import sun.management.snmp.jvmmib.JvmMemPoolEntryMBean;
import sun.management.snmp.jvmmib.EnumJvmMemPoolState;
import sun.management.snmp.jvmmib.EnumJvmMemPoolType;
import sun.management.snmp.jvmmib.EnumJvmMemPoolThreshdSupport;
import sun.management.snmp.jvmmib.EnumJvmMemPoolCollectThreshdSupport;
import sun.management.snmp.util.MibLogger;
import sun.management.snmp.util.JvmContextFactory;

/**
 * The class is used for implementing the "JvmMemPoolEntry" group.
 */
public class JvmMemPoolEntryImpl implements JvmMemPoolEntryMBean {

    /**
     * Variable for storing the value of "JvmMemPoolIndex".
     *
     * "An index value opaquely computed by the agent which uniquely
     * identifies a row in the jvmMemPoolTable.
     * "
     *
     */
    final protected int jvmMemPoolIndex;


    final static String memoryTag = "jvmMemPoolEntry.getUsage";
    final static String peakMemoryTag = "jvmMemPoolEntry.getPeakUsage";
    final static String collectMemoryTag =
        "jvmMemPoolEntry.getCollectionUsage";
    final static MemoryUsage ZEROS = new MemoryUsage(0,0,0,0);

    final String entryMemoryTag;
    final String entryPeakMemoryTag;
    final String entryCollectMemoryTag;

    MemoryUsage getMemoryUsage() {
        try {
            final Map<Object, Object> m = JvmContextFactory.getUserData();

            if (m != null) {
                final MemoryUsage cached = (MemoryUsage)
                    m.get(entryMemoryTag);
                if (cached != null) {
                    log.debug("getMemoryUsage",entryMemoryTag+
                          " found in cache.");
                    return cached;
                }

                MemoryUsage u = pool.getUsage();
                if (u == null) u = ZEROS;

                m.put(entryMemoryTag,u);
                return u;
            }
            // Should never come here.
            // Log error!
            log.trace("getMemoryUsage", "ERROR: should never come here!");
            return pool.getUsage();
        } catch (RuntimeException x) {
            log.trace("getMemoryUsage",
                  "Failed to get MemoryUsage: " + x);
            log.debug("getMemoryUsage",x);
            throw x;
        }

    }

    MemoryUsage getPeakMemoryUsage() {
        try {
            final Map<Object, Object> m = JvmContextFactory.getUserData();

            if (m != null) {
                final MemoryUsage cached = (MemoryUsage)
                    m.get(entryPeakMemoryTag);
                if (cached != null) {
                    if (log.isDebugOn())
                        log.debug("getPeakMemoryUsage",
                              entryPeakMemoryTag + " found in cache.");
                    return cached;
                }

                MemoryUsage u = pool.getPeakUsage();
                if (u == null) u = ZEROS;

                m.put(entryPeakMemoryTag,u);
                return u;
            }
            // Should never come here.
            // Log error!
            log.trace("getPeakMemoryUsage", "ERROR: should never come here!");
            return ZEROS;
        } catch (RuntimeException x) {
            log.trace("getPeakMemoryUsage",
                  "Failed to get MemoryUsage: " + x);
            log.debug("getPeakMemoryUsage",x);
            throw x;
        }

    }

    MemoryUsage getCollectMemoryUsage() {
        try {
            final Map<Object, Object> m = JvmContextFactory.getUserData();

            if (m != null) {
                final MemoryUsage cached = (MemoryUsage)
                    m.get(entryCollectMemoryTag);
                if (cached != null) {
                    if (log.isDebugOn())
                        log.debug("getCollectMemoryUsage",
                                  entryCollectMemoryTag + " found in cache.");
                    return cached;
                }

                MemoryUsage u = pool.getCollectionUsage();
                if (u == null) u = ZEROS;

                m.put(entryCollectMemoryTag,u);
                return u;
            }
            // Should never come here.
            // Log error!
            log.trace("getCollectMemoryUsage",
                      "ERROR: should never come here!");
            return ZEROS;
        } catch (RuntimeException x) {
            log.trace("getPeakMemoryUsage",
                  "Failed to get MemoryUsage: " + x);
            log.debug("getPeakMemoryUsage",x);
            throw x;
        }

    }

    final MemoryPoolMXBean pool;

    /**
     * Constructor for the "JvmMemPoolEntry" group.
     */
    public JvmMemPoolEntryImpl(MemoryPoolMXBean mp, final int index) {
        this.pool=mp;
        this.jvmMemPoolIndex = index;
        this.entryMemoryTag = memoryTag + "." + index;
        this.entryPeakMemoryTag = peakMemoryTag + "." + index;
        this.entryCollectMemoryTag = collectMemoryTag + "." + index;
    }

    /**
     * Getter for the "JvmMemPoolMaxSize" variable.
     */
    public Long getJvmMemPoolMaxSize() throws SnmpStatusException {
        final long val = getMemoryUsage().getMax();
        if (val > -1) return  new Long(val);
        else return JvmMemoryImpl.Long0;
    }

    /**
     * Getter for the "JvmMemPoolUsed" variable.
     */
    public Long getJvmMemPoolUsed() throws SnmpStatusException {
        final long val = getMemoryUsage().getUsed();
        if (val > -1) return  new Long(val);
        else return JvmMemoryImpl.Long0;
    }

    /**
     * Getter for the "JvmMemPoolInitSize" variable.
     */
    public Long getJvmMemPoolInitSize() throws SnmpStatusException {
        final long val = getMemoryUsage().getInit();
        if (val > -1) return  new Long(val);
        else return JvmMemoryImpl.Long0;
    }

    /**
     * Getter for the "JvmMemPoolCommitted" variable.
     */
    public Long getJvmMemPoolCommitted() throws SnmpStatusException {
        final long val = getMemoryUsage().getCommitted();
        if (val > -1) return  new Long(val);
        else return JvmMemoryImpl.Long0;
    }

    /**
     * Getter for the "JvmMemPoolPeakMaxSize" variable.
     */
    public Long getJvmMemPoolPeakMaxSize() throws SnmpStatusException {
        final long val = getPeakMemoryUsage().getMax();
        if (val > -1) return  new Long(val);
        else return JvmMemoryImpl.Long0;
    }

    /**
     * Getter for the "JvmMemPoolPeakUsed" variable.
     */
    public Long getJvmMemPoolPeakUsed() throws SnmpStatusException {
        final long val = getPeakMemoryUsage().getUsed();
        if (val > -1) return  new Long(val);
        else return JvmMemoryImpl.Long0;
    }

    /**
     * Getter for the "JvmMemPoolPeakCommitted" variable.
     */
    public Long getJvmMemPoolPeakCommitted() throws SnmpStatusException {
        final long val = getPeakMemoryUsage().getCommitted();
        if (val > -1) return  new Long(val);
        else return JvmMemoryImpl.Long0;
    }

    /**
     * Getter for the "JvmMemPoolCollectMaxSize" variable.
     */
    public Long getJvmMemPoolCollectMaxSize() throws SnmpStatusException {
        final long val = getCollectMemoryUsage().getMax();
        if (val > -1) return  new Long(val);
        else return JvmMemoryImpl.Long0;
    }

    /**
     * Getter for the "JvmMemPoolCollectUsed" variable.
     */
    public Long getJvmMemPoolCollectUsed() throws SnmpStatusException {
        final long val = getCollectMemoryUsage().getUsed();
        if (val > -1) return  new Long(val);
        else return JvmMemoryImpl.Long0;
    }

    /**
     * Getter for the "JvmMemPoolCollectCommitted" variable.
     */
    public Long getJvmMemPoolCollectCommitted() throws SnmpStatusException {
        final long val = getCollectMemoryUsage().getCommitted();
        if (val > -1) return  new Long(val);
        else return JvmMemoryImpl.Long0;
    }

    /**
     * Getter for the "JvmMemPoolThreshold" variable.
     */
    public Long getJvmMemPoolThreshold() throws SnmpStatusException {
        if (!pool.isUsageThresholdSupported())
            return JvmMemoryImpl.Long0;
        final long val = pool.getUsageThreshold();
        if (val > -1) return  new Long(val);
        else return JvmMemoryImpl.Long0;
    }

    /**
     * Setter for the "JvmMemPoolThreshold" variable.
     */
    public void setJvmMemPoolThreshold(Long x) throws SnmpStatusException {
        final long val = x.longValue();
        if (val < 0 )
            throw new SnmpStatusException(SnmpDefinitions.snmpRspWrongValue);
        // This should never throw an exception has the checks have
        // already been performed in checkJvmMemPoolThreshold().
        //
        pool.setUsageThreshold(val);
    }

    /**
     * Checker for the "JvmMemPoolThreshold" variable.
     */
    public void checkJvmMemPoolThreshold(Long x) throws SnmpStatusException {
        // if threshold is -1, it means that low memory detection is not
        // supported.

        if (!pool.isUsageThresholdSupported())
            throw new
                SnmpStatusException(SnmpDefinitions.snmpRspInconsistentValue);
        final long val = x.longValue();
        if (val < 0 )
            throw new SnmpStatusException(SnmpDefinitions.snmpRspWrongValue);
    }

    /**
     * Getter for the "JvmMemPoolThreshdSupport" variable.
     */
    public EnumJvmMemPoolThreshdSupport getJvmMemPoolThreshdSupport()
        throws SnmpStatusException {
        if (pool.isUsageThresholdSupported())
            return EnumJvmMemPoolThreshdSupported;
        else
            return EnumJvmMemPoolThreshdUnsupported;
    }

    /**
     * Getter for the "JvmMemPoolThreshdCount" variable.
     */
    public Long getJvmMemPoolThreshdCount()
        throws SnmpStatusException {
        if (!pool.isUsageThresholdSupported())
            return JvmMemoryImpl.Long0;
        final long val = pool.getUsageThresholdCount();
        if (val > -1) return  new Long(val);
        else return JvmMemoryImpl.Long0;
    }

    /**
     * Getter for the "JvmMemPoolCollectThreshold" variable.
     */
    public Long getJvmMemPoolCollectThreshold() throws SnmpStatusException {
        if (!pool.isCollectionUsageThresholdSupported())
            return JvmMemoryImpl.Long0;
        final long val = pool.getCollectionUsageThreshold();
        if (val > -1) return  new Long(val);
        else return JvmMemoryImpl.Long0;
    }

    /**
     * Setter for the "JvmMemPoolCollectThreshold" variable.
     */
    public void setJvmMemPoolCollectThreshold(Long x)
        throws SnmpStatusException {
        final long val = x.longValue();
        if (val < 0 )
            throw new SnmpStatusException(SnmpDefinitions.snmpRspWrongValue);
        // This should never throw an exception has the checks have
        // already been performed in checkJvmMemPoolCollectThreshold().
        //
        pool.setCollectionUsageThreshold(val);
    }

    /**
     * Checker for the "JvmMemPoolCollectThreshold" variable.
     */
    public void checkJvmMemPoolCollectThreshold(Long x)
        throws SnmpStatusException {
        // if threshold is -1, it means that low memory detection is not
        // supported.

        if (!pool.isCollectionUsageThresholdSupported())
            throw new
                SnmpStatusException(SnmpDefinitions.snmpRspInconsistentValue);
        final long val = x.longValue();
        if (val < 0 )
            throw new SnmpStatusException(SnmpDefinitions.snmpRspWrongValue);
    }

    /**
     * Getter for the "JvmMemPoolThreshdSupport" variable.
     */
    public EnumJvmMemPoolCollectThreshdSupport
        getJvmMemPoolCollectThreshdSupport()
        throws SnmpStatusException {
        if (pool.isCollectionUsageThresholdSupported())
            return EnumJvmMemPoolCollectThreshdSupported;
        else
            return EnumJvmMemPoolCollectThreshdUnsupported;
    }

    /**
     * Getter for the "JvmMemPoolCollectThreshdCount" variable.
     */
    public Long getJvmMemPoolCollectThreshdCount()
        throws SnmpStatusException {
        if (!pool.isCollectionUsageThresholdSupported())
            return JvmMemoryImpl.Long0;
        final long val = pool.getCollectionUsageThresholdCount();
        if (val > -1) return  new Long(val);
        else return JvmMemoryImpl.Long0;
    }

    public static EnumJvmMemPoolType jvmMemPoolType(MemoryType type)
        throws SnmpStatusException {
        if (type.equals(MemoryType.HEAP))
            return  EnumJvmMemPoolTypeHeap;
        else if (type.equals(MemoryType.NON_HEAP))
            return EnumJvmMemPoolTypeNonHeap;
        throw new SnmpStatusException(SnmpStatusException.snmpRspWrongValue);
    }

    /**
     * Getter for the "JvmMemPoolType" variable.
     */
    public EnumJvmMemPoolType getJvmMemPoolType() throws SnmpStatusException {
        return jvmMemPoolType(pool.getType());
    }

    /**
     * Getter for the "JvmMemPoolName" variable.
     */
    public String getJvmMemPoolName() throws SnmpStatusException {
        return JVM_MANAGEMENT_MIB_IMPL.validJavaObjectNameTC(pool.getName());
    }

    /**
     * Getter for the "JvmMemPoolIndex" variable.
     */
    public Integer getJvmMemPoolIndex() throws SnmpStatusException {
        return new Integer(jvmMemPoolIndex);
    }


    /**
     * Getter for the "JvmMemPoolState" variable.
     */
    public EnumJvmMemPoolState getJvmMemPoolState()
        throws SnmpStatusException {
        if (pool.isValid())
            return JvmMemPoolStateValid;
        else
            return JvmMemPoolStateInvalid;
    }

    /**
     * Getter for the "JvmMemPoolPeakReset" variable.
     */
    public synchronized Long getJvmMemPoolPeakReset()
        throws SnmpStatusException {
        return new Long(jvmMemPoolPeakReset);
    }

    /**
     * Setter for the "JvmMemPoolPeakReset" variable.
     */
    public synchronized void setJvmMemPoolPeakReset(Long x)
        throws SnmpStatusException {
        final long l = x.longValue();
        if (l > jvmMemPoolPeakReset) {
            final long stamp = System.currentTimeMillis();
            pool.resetPeakUsage();
            jvmMemPoolPeakReset = stamp;
            log.debug("setJvmMemPoolPeakReset",
                      "jvmMemPoolPeakReset="+stamp);
        }
    }

    /**
     * Checker for the "JvmMemPoolPeakReset" variable.
     */
    public void checkJvmMemPoolPeakReset(Long x) throws SnmpStatusException {
    }

    /* Last time peak usage was reset */
    private long jvmMemPoolPeakReset = 0;

    private final static EnumJvmMemPoolState JvmMemPoolStateValid =
        new EnumJvmMemPoolState("valid");
    private final static EnumJvmMemPoolState JvmMemPoolStateInvalid =
        new EnumJvmMemPoolState("invalid");

    private static final EnumJvmMemPoolType EnumJvmMemPoolTypeHeap =
        new EnumJvmMemPoolType("heap");
    private static final EnumJvmMemPoolType EnumJvmMemPoolTypeNonHeap =
        new EnumJvmMemPoolType("nonheap");

    private static final EnumJvmMemPoolThreshdSupport
        EnumJvmMemPoolThreshdSupported =
        new EnumJvmMemPoolThreshdSupport("supported");
    private static final EnumJvmMemPoolThreshdSupport
        EnumJvmMemPoolThreshdUnsupported =
        new EnumJvmMemPoolThreshdSupport("unsupported");

    private static final EnumJvmMemPoolCollectThreshdSupport
        EnumJvmMemPoolCollectThreshdSupported =
        new EnumJvmMemPoolCollectThreshdSupport("supported");
    private static final EnumJvmMemPoolCollectThreshdSupport
        EnumJvmMemPoolCollectThreshdUnsupported=
        new EnumJvmMemPoolCollectThreshdSupport("unsupported");


    static final MibLogger log = new MibLogger(JvmMemPoolEntryImpl.class);
}
