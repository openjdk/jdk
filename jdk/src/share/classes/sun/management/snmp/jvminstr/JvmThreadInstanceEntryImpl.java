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
import java.lang.management.ThreadInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

// jmx imports
//
import com.sun.jmx.snmp.SnmpStatusException;

// jdmk imports
//
import com.sun.jmx.snmp.agent.SnmpMib;
import com.sun.jmx.snmp.SnmpOid;
import com.sun.jmx.snmp.SnmpDefinitions;
import com.sun.jmx.snmp.SnmpOidTable;
import com.sun.jmx.snmp.SnmpOidRecord;

import sun.management.snmp.jvmmib.JvmThreadInstanceEntryMBean;
import sun.management.snmp.jvmmib.JVM_MANAGEMENT_MIBOidTable;
import sun.management.snmp.util.MibLogger;

/**
 * The class is used for implementing the "JvmThreadInstanceEntry" group.
 */
public class JvmThreadInstanceEntryImpl
    implements JvmThreadInstanceEntryMBean, Serializable {

    static final long serialVersionUID = 910173589985461347L;

    public final static class ThreadStateMap {
        public final static class Byte0 {
            public final static byte inNative     = (byte)0x80; // bit 1
            public final static byte suspended    = (byte)0x40; // bit 2
            public final static byte newThread    = (byte)0x20; // bit 3
            public final static byte runnable     = (byte)0x10; // bit 4
            public final static byte blocked      = (byte)0x08; // bit 5
            public final static byte terminated   = (byte)0x04; // bit 6
            public final static byte waiting      = (byte)0x02; // bit 7
            public final static byte timedWaiting = (byte)0x01; // bit 8
        }
        public final static class Byte1 {
            public final static byte other        = (byte)0x80; // bit 9
            public final static byte reserved10   = (byte)0x40; // bit 10
            public final static byte reserved11   = (byte)0x20; // bit 11
            public final static byte reserved12   = (byte)0x10; // bit 12
            public final static byte reserved13   = (byte)0x08; // bit 13
            public final static byte reserved14   = (byte)0x04; // bit 14
            public final static byte reserved15   = (byte)0x02; // bit 15
            public final static byte reserved16   = (byte)0x01; // bit 16
        }

        public final static byte mask0 = (byte)0x3F;
        public final static byte mask1 = (byte)0x80;

        private static void setBit(byte[] bitmap, int index, byte state) {
            bitmap[index] = (byte) (bitmap[index] | state);
        }
        public static void setNative(byte[] bitmap) {
            setBit(bitmap,0,Byte0.inNative);
        }
        public static void setSuspended(byte[] bitmap) {
            setBit(bitmap,0,Byte0.suspended);
        }
        public static void setState(byte[] bitmap, Thread.State state) {
            switch(state) {
            case BLOCKED:
                setBit(bitmap,0,Byte0.blocked);
                return;
            case NEW:
                setBit(bitmap,0,Byte0.newThread);
                return;
            case RUNNABLE:
                setBit(bitmap,0,Byte0.runnable);
                return;
            case TERMINATED:
                setBit(bitmap,0,Byte0.terminated);
                return;
            case TIMED_WAITING:
                setBit(bitmap,0,Byte0.timedWaiting);
                return;
            case WAITING:
                setBit(bitmap,0,Byte0.waiting);
                return;
            }
        }

        public static void checkOther(byte[] bitmap) {
            if (((bitmap[0]&mask0)==(byte)0x00) &&
                ((bitmap[1]&mask1)==(byte)0x00))
                setBit(bitmap,1,Byte1.other);
        }

        public static Byte[] getState(ThreadInfo info) {
            byte[] bitmap = new byte[] {(byte)0x00, (byte)0x00};
            try {
                final Thread.State state = info.getThreadState();
                final boolean inNative  = info.isInNative();
                final boolean suspended = info.isSuspended();
                log.debug("getJvmThreadInstState",
                          "[State=" + state +
                          ",isInNative=" + inNative +
                          ",isSuspended=" + suspended + "]");
                setState(bitmap,state);
                if (inNative)  setNative(bitmap);
                if (suspended) setSuspended(bitmap);
                checkOther(bitmap);
            } catch (RuntimeException r) {
                bitmap[0]=(byte)0x00;
                bitmap[1]=Byte1.other;
                log.trace("getJvmThreadInstState",
                          "Unexpected exception: " + r);
                log.debug("getJvmThreadInstState",r);
            }
            Byte[] result = { new Byte(bitmap[0]), new Byte(bitmap[1]) };
            return result;
        }
    }

    private final ThreadInfo info;
    private final Byte[] index;

    /**
     * Constructor for the "JvmThreadInstanceEntry" group.
     */
    public JvmThreadInstanceEntryImpl(ThreadInfo info,
                                      Byte[] index) {
        this.info = info;
        this.index = index;
    }


    private static String  jvmThreadInstIndexOid = null;
    public static String getJvmThreadInstIndexOid()
        throws SnmpStatusException {
        if (jvmThreadInstIndexOid == null) {
            final SnmpOidTable  table = new JVM_MANAGEMENT_MIBOidTable();
            final SnmpOidRecord record =
                table.resolveVarName("jvmThreadInstIndex");
            jvmThreadInstIndexOid = record.getOid();
        }
        return jvmThreadInstIndexOid;
    }



    /**
     * Getter for the "JvmThreadInstLockedOwnerId" variable.
     */
    public String getJvmThreadInstLockOwnerPtr() throws SnmpStatusException {
       long id = info.getLockOwnerId();

       if(id == -1)
           return new String("0.0");

       SnmpOid oid = JvmThreadInstanceTableMetaImpl.makeOid(id);

       return getJvmThreadInstIndexOid() + "." + oid.toString();
    }

    private String validDisplayStringTC(String str) {
        return JVM_MANAGEMENT_MIB_IMPL.validDisplayStringTC(str);
    }

    private String validJavaObjectNameTC(String str) {
        return JVM_MANAGEMENT_MIB_IMPL.validJavaObjectNameTC(str);
    }

    private String validPathElementTC(String str) {
        return JVM_MANAGEMENT_MIB_IMPL.validPathElementTC(str);
    }

    /**
     * Getter for the "JvmThreadInstLockName" variable.
     */
    public String getJvmThreadInstLockName() throws SnmpStatusException {
        return validJavaObjectNameTC(info.getLockName());
    }

    /**
     * Getter for the "JvmThreadInstName" variable.
     */
    public String getJvmThreadInstName() throws SnmpStatusException {
        return validJavaObjectNameTC(info.getThreadName());
    }

    /**
     * Getter for the "JvmThreadInstCpuTimeNs" variable.
     */
    public Long getJvmThreadInstCpuTimeNs() throws SnmpStatusException {
        long l = 0;
        final ThreadMXBean tmb = JvmThreadingImpl.getThreadMXBean();

        try {
            if (tmb.isThreadCpuTimeSupported()) {
                l = tmb.getThreadCpuTime(info.getThreadId());
                log.debug("getJvmThreadInstCpuTimeNs", "Cpu time ns : " + l);

                //Cpu time measurement is disabled or the id is not valid.
                if(l == -1) l = 0;
            }
        } catch (UnsatisfiedLinkError e) {
            // XXX Revisit: catch TO BE EVENTUALLY REMOVED
            log.debug("getJvmThreadInstCpuTimeNs",
                      "Operation not supported: " + e);
        }
        return new Long(l);
    }

    /**
     * Getter for the "JvmThreadInstBlockTimeMs" variable.
     */
    public Long getJvmThreadInstBlockTimeMs() throws SnmpStatusException {
        long l = 0;

        final ThreadMXBean tmb = JvmThreadingImpl.getThreadMXBean();

        if (tmb.isThreadContentionMonitoringSupported()) {
            l = info.getBlockedTime();

            //Monitoring is disabled
            if(l == -1) l = 0;
        }
        return new Long(l);
    }

    /**
     * Getter for the "JvmThreadInstBlockCount" variable.
     */
    public Long getJvmThreadInstBlockCount() throws SnmpStatusException {
        return new Long(info.getBlockedCount());
    }

    /**
     * Getter for the "JvmThreadInstWaitTimeMs" variable.
     */
    public Long getJvmThreadInstWaitTimeMs() throws SnmpStatusException {
        long l = 0;

        final ThreadMXBean tmb = JvmThreadingImpl.getThreadMXBean();

        if (tmb.isThreadContentionMonitoringSupported()) {
            l = info.getWaitedTime();

            //Monitoring is disabled
            if(l == -1) l = 0;
        }
        return new Long(l);
    }

    /**
     * Getter for the "JvmThreadInstWaitCount" variable.
     */
    public Long getJvmThreadInstWaitCount() throws SnmpStatusException {
        return new Long(info.getWaitedCount());
    }

    /**
     * Getter for the "JvmThreadInstState" variable.
     */
    public Byte[] getJvmThreadInstState()
        throws SnmpStatusException {
        return ThreadStateMap.getState(info);
    }

    /**
     * Getter for the "JvmThreadInstId" variable.
     */
    public Long getJvmThreadInstId() throws SnmpStatusException {
        return new Long(info.getThreadId());
    }

    /**
     * Getter for the "JvmThreadInstIndex" variable.
     */
    public Byte[] getJvmThreadInstIndex() throws SnmpStatusException {
        return index;
    }

    /**
     * Getter for the "JvmThreadInstStackTrace" variable.
     */
    private String getJvmThreadInstStackTrace() throws SnmpStatusException {
        StackTraceElement[] stackTrace = info.getStackTrace();
        //We append the stack trace in a buffer
        // XXX Revisit: should check isDebugOn()
        StringBuilder sb = new StringBuilder();
        final int stackSize = stackTrace.length;
        log.debug("getJvmThreadInstStackTrace", "Stack size : " + stackSize);
        for(int i = 0; i < stackSize; i++) {
            log.debug("getJvmThreadInstStackTrace", "Append " +
                      stackTrace[i].toString());
            sb.append(stackTrace[i].toString());
            //Append \n at the end of each line except the last one
            if(i < stackSize)
                sb.append("\n");
        }
        //The stack trace is truncated if its size exceeds 255.
        return validPathElementTC(sb.toString());
    }
    static final MibLogger log =
        new MibLogger(JvmThreadInstanceEntryImpl.class);
}
