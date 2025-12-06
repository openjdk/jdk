/*
 * Copyright (c) 2004, 2022, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvmstat.perfdata.monitor.v1_0;

import sun.jvmstat.monitor.*;
import sun.jvmstat.perfdata.monitor.*;
import java.util.*;
import java.nio.*;

/**
 * The concrete implementation of version 1.0 of the HotSpot PerfData
 * Instrumentation buffer. This class is responsible for parsing the
 * instrumentation memory and constructing the necessary objects to
 * represent and access the instrumentation objects contained in the
 * memory buffer.
 *
 * @author Brian Doherty
 * @since 1.5
 * @see AbstractPerfDataBuffer
 */
public class PerfDataBuffer extends PerfDataBufferImpl {

    // 8028357 removed old, inefficient debug logging

    private static final int syncWaitMs =
            Integer.getInteger("sun.jvmstat.perdata.syncWaitMs", 5000);
    private static final ArrayList<Monitor> EMPTY_LIST = new ArrayList<Monitor>(0);

    /*
     * the following constants must be kept in sync with struct
     * PerfDataEntry in perfMemory.hpp
     */
    private static final int PERFDATA_ENTRYLENGTH_OFFSET=0;
    private static final int PERFDATA_ENTRYLENGTH_SIZE=4;   // sizeof(int)
    private static final int PERFDATA_NAMELENGTH_OFFSET=4;
    private static final int PERFDATA_NAMELENGTH_SIZE=4;    // sizeof(int)
    private static final int PERFDATA_VECTORLENGTH_OFFSET=8;
    private static final int PERFDATA_VECTORLENGTH_SIZE=4;  // sizeof(int)
    private static final int PERFDATA_DATATYPE_OFFSET=12;
    private static final int PERFDATA_DATATYPE_SIZE=1;      // sizeof(byte)
    private static final int PERFDATA_FLAGS_OFFSET=13;
    private static final int PERFDATA_FLAGS_SIZE=1;        // sizeof(byte)
    private static final int PERFDATA_DATAUNITS_OFFSET=14;
    private static final int PERFDATA_DATAUNITS_SIZE=1;     // sizeof(byte)
    private static final int PERFDATA_DATAATTR_OFFSET=15;
    private static final int PERFDATA_DATAATTR_SIZE=1;      // sizeof(byte)
    private static final int PERFDATA_NAME_OFFSET=16;

    PerfDataBufferPrologue prologue;
    int nextEntry;
    int pollForEntry;
    int perfDataItem;
    long lastModificationTime;
    int lastUsed;
    IntegerMonitor overflow;
    ArrayList<Monitor> insertedMonitors;

    /**
     * Construct a PerfDataBufferImpl instance.
     * <p>
     * This class is dynamically loaded by
     * {@link AbstractPerfDataBuffer#createPerfDataBuffer}, and this
     * constructor is called to instantiate the instance.
     *
     * @param buffer the buffer containing the instrumentation data
     * @param lvmid the Local Java Virtual Machine Identifier for this
     *              instrumentation buffer.
     */
    public PerfDataBuffer(ByteBuffer buffer, int lvmid)
           throws MonitorException {
        super(buffer, lvmid);
        prologue = new PerfDataBufferPrologue(buffer);
        this.buffer.order(prologue.getByteOrder());
    }

    /**
     * {@inheritDoc}
     */
    protected void buildMonitorMap(Map<String, Monitor> map) throws MonitorException {
        assert Thread.holdsLock(this);

        // start at the beginning of the buffer
        buffer.rewind();

        // create pseudo monitors
        buildPseudoMonitors(map);

        // position buffer to start of the data section
        buffer.position(prologue.getSize());
        nextEntry = buffer.position();
        perfDataItem = 0;

        int used = prologue.getUsed();
        long modificationTime = prologue.getModificationTimeStamp();

        Monitor m = getNextMonitorEntry();
        while (m != null) {
            map.put(m.getName(), m);
            m = getNextMonitorEntry();
        }

        /*
         * set the last modification data. These are set to the values
         * recorded before parsing the data structure. This allows the
         * the data structure to be modified while the Map is being built.
         * The Map may contain more entries than indicated based on the
         * time stamp, but this is handled by ignoring duplicate entries
         * when the Map is updated in getNewMonitors().
         */
        lastUsed = used;
        lastModificationTime = modificationTime;

        // synchronize with the target jvm
        synchWithTarget(map);

        insertedMonitors = new ArrayList<Monitor>(map.values());
    }

    /**
     * {@inheritDoc}
     */
    protected void getNewMonitors(Map<String, Monitor> map) throws MonitorException {
        assert Thread.holdsLock(this);

        int used = prologue.getUsed();
        long modificationTime = prologue.getModificationTimeStamp();

        if ((used > lastUsed) || (lastModificationTime > modificationTime)) {

            lastUsed = used;
            lastModificationTime = modificationTime;

            Monitor monitor = getNextMonitorEntry();
            while (monitor != null) {
                String name = monitor.getName();

                // guard against duplicate entries
                if (!map.containsKey(name)) {
                    map.put(name, monitor);

                    /*
                     * insertedMonitors is null when called from pollFor()
                     * via buildMonitorMap(). Since we update insertedMonitors
                     * at the end of buildMonitorMap(), it's ok to skip the
                     * add here.
                     */
                    if (insertedMonitors != null) {
                        insertedMonitors.add(monitor);
                    }
                }
                monitor = getNextMonitorEntry();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    protected MonitorStatus getMonitorStatus(Map<String, Monitor> map) throws MonitorException {
        assert Thread.holdsLock(this);
        assert insertedMonitors != null;

        // load any new monitors
        getNewMonitors(map);

        // current implementation doesn't support deletion or reuse of entries
        ArrayList<Monitor> removed = EMPTY_LIST;
        ArrayList<Monitor> inserted = insertedMonitors;

        insertedMonitors = new ArrayList<Monitor>();
        return new MonitorStatus(inserted, removed);
    }

    /**
     * Build the pseudo monitors used to map the prolog data into counters.
     */
    protected void buildPseudoMonitors(Map<String, Monitor> map) {
        Monitor monitor = null;
        String name = null;
        IntBuffer ib = null;

        name = PerfDataBufferPrologue.PERFDATA_MAJOR_NAME;
        ib = prologue.majorVersionBuffer();
        monitor = new PerfIntegerMonitor(name, Units.NONE,
                                         Variability.CONSTANT, false, ib);
        map.put(name, monitor);

        name = PerfDataBufferPrologue.PERFDATA_MINOR_NAME;
        ib = prologue.minorVersionBuffer();
        monitor = new PerfIntegerMonitor(name, Units.NONE,
                                         Variability.CONSTANT, false, ib);
        map.put(name, monitor);

        name = PerfDataBufferPrologue.PERFDATA_BUFFER_SIZE_NAME;
        ib = prologue.sizeBuffer();
        monitor = new PerfIntegerMonitor(name, Units.BYTES,
                                         Variability.MONOTONIC, false, ib);
        map.put(name, monitor);

        name = PerfDataBufferPrologue.PERFDATA_BUFFER_USED_NAME;
        ib = prologue.usedBuffer();
        monitor = new PerfIntegerMonitor(name, Units.BYTES,
                                         Variability.MONOTONIC, false, ib);
        map.put(name, monitor);

        name = PerfDataBufferPrologue.PERFDATA_OVERFLOW_NAME;
        ib = prologue.overflowBuffer();
        monitor = new PerfIntegerMonitor(name, Units.BYTES,
                                         Variability.MONOTONIC, false, ib);
        map.put(name, monitor);
        this.overflow = (IntegerMonitor)monitor;

        name = PerfDataBufferPrologue.PERFDATA_MODTIMESTAMP_NAME;
        LongBuffer lb = prologue.modificationTimeStampBuffer();
        monitor = new PerfLongMonitor(name, Units.TICKS,
                                      Variability.MONOTONIC, false, lb);
        map.put(name, monitor);
    }

    /**
     * Method to provide a gross level of synchronization with the
     * target monitored jvm.
     *
     * gross synchronization works by polling for the hotspot.rt.hrt.frequency
     * constant, which is created by the PerfData initialization code. The
     * creation of constants is one of the last things done in vm initialization.
     */
    protected void synchWithTarget(Map<String, Monitor> map) throws MonitorException {
        /*
         * synch must happen with syncWaitMs from now. Default is 5 seconds,
         * which is reasonabally generous and should provide for extreme
         * situations like startup delays due to allocation of large ISM heaps.
         */
        long timeLimit = System.currentTimeMillis() + syncWaitMs;

        String name = "hotspot.rt.hrt.frequency";
        LongMonitor ticks = (LongMonitor)pollFor(map, name, timeLimit);

        /*
         * loop waiting for the ticks counter to be non zero. This is
         * an indication that the jvm is initialized.
         */
        while (ticks.longValue() == 0) {
            try { Thread.sleep(20); } catch (InterruptedException e) { }

            if (System.currentTimeMillis() > timeLimit) {
                throw new MonitorException("Could Not Synchronize with target");
            }
        }
    }

    /**
     * Method to poll the instrumentation memory for a counter with
     * the given name. The polling period is bounded by the timeLimit
     * argument.
     */
    protected Monitor pollFor(Map<String, Monitor> map, String name, long timeLimit)
                      throws MonitorException {
        Monitor monitor = null;

        pollForEntry = nextEntry;
        while ((monitor = map.get(name)) == null) {

            try { Thread.sleep(20); } catch (InterruptedException e) { }

            long t = System.currentTimeMillis();
            if ((t > timeLimit) || (overflow.intValue() > 0)) {
                throw new MonitorException("Could not find expected counter");
            }

            getNewMonitors(map);
        }
        return monitor;
    }

    /**
     * method to extract the next monitor entry from the instrumentation memory.
     * assumes that nextEntry is the offset into the byte array
     * at which to start the search for the next entry. method leaves
     * next entry pointing to the next entry or to the end of data.
     */
    protected Monitor getNextMonitorEntry() throws MonitorException {
        Monitor monitor = null;

        // entries are always 4 byte aligned.
        if ((nextEntry % 4) != 0) {
            throw new MonitorStructureException(
                   "Entry index not properly aligned: " + nextEntry);
        }

        // protect against a corrupted shared memory region.
        if ((nextEntry < 0) || (nextEntry > buffer.limit())) {
            throw new MonitorStructureException(
                   "Entry index out of bounds: nextEntry = " + nextEntry
                   + ", limit = " + buffer.limit());
        }

        // check for the end of the buffer
        if (nextEntry == buffer.limit()) {
            return null;
        }

        buffer.position(nextEntry);

        int entryStart = buffer.position();
        int entryLength = buffer.getInt();

        // check for valid entry length
        if ((entryLength < 0) || (entryLength > buffer.limit())) {
            throw new MonitorStructureException(
                   "Invalid entry length: entryLength = " + entryLength);
        }

        // check if last entry occurs before the eof.
        if ((entryStart + entryLength) > buffer.limit()) {
            throw new MonitorStructureException(
                   "Entry extends beyond end of buffer: "
                   + " entryStart = " + entryStart
                   + " entryLength = " + entryLength
                   + " buffer limit = " + buffer.limit());
        }

        if (entryLength == 0) {
            // end of data
            return null;
        }

        int nameLength = buffer.getInt();
        int vectorLength = buffer.getInt();
        byte dataType = buffer.get();
        byte flags = buffer.get();
        Units u = Units.toUnits(buffer.get());
        Variability v = Variability.toVariability(buffer.get());
        boolean supported = (flags & 0x01) != 0;

        // defend against corrupt entries
        if ((nameLength <= 0) || (nameLength > entryLength)) {
            throw new MonitorStructureException(
                   "Invalid Monitor name length: " + nameLength);
        }

        if ((vectorLength < 0) || (vectorLength > entryLength)) {
            throw new MonitorStructureException(
                   "Invalid Monitor vector length: " + vectorLength);
        }

        // read in the perfData item name, casting bytes to chars. skip the
        // null terminator
        //
        byte[] nameBytes = new byte[nameLength-1];
        for (int i = 0; i < nameLength-1; i++) {
            nameBytes[i] = buffer.get();
        }

        // convert name into a String
        String name = new String(nameBytes, 0, nameLength-1);

        if (v == Variability.INVALID) {
            throw new MonitorDataException("Invalid variability attribute:"
                                           + " entry index = " + perfDataItem
                                           + " name = " + name);
        }
        if (u == Units.INVALID) {
            throw new MonitorDataException("Invalid units attribute: "
                                           + " entry index = " + perfDataItem
                                           + " name = " + name);
        }

        int offset;
        if (vectorLength == 0) {
            // scalar Types
            if (dataType == BasicType.LONG.intValue()) {
                offset = entryStart + entryLength - 8;  /* 8 = sizeof(long) */
                buffer.position(offset);
                LongBuffer lb = buffer.asLongBuffer();
                lb.limit(1);
                monitor = new PerfLongMonitor(name, u, v, supported, lb);
                perfDataItem++;
            } else {
                // bad data types.
                throw new MonitorTypeException("Invalid Monitor type:"
                                    + " entry index = " + perfDataItem
                                    + " name = " + name
                                    + " type = " + dataType);
            }
        } else {
            // vector types
            if (dataType == BasicType.BYTE.intValue()) {
                if (u != Units.STRING) {
                    // only byte arrays of type STRING are currently supported
                    throw new MonitorTypeException("Invalid Monitor type:"
                                      + " entry index = " + perfDataItem
                                      + " name = " + name
                                      + " type = " + dataType);
                }

                offset = entryStart + PERFDATA_NAME_OFFSET + nameLength;
                buffer.position(offset);
                ByteBuffer bb = buffer.slice();
                bb.limit(vectorLength);
                bb.position(0);

                if (v == Variability.CONSTANT) {
                    monitor = new PerfStringConstantMonitor(name, supported,
                                                            bb);
                } else if (v == Variability.VARIABLE) {
                    monitor = new PerfStringVariableMonitor(name, supported,
                                                            bb, vectorLength-1);
                } else {
                    // Monotonically increasing byte arrays are not supported
                    throw new MonitorDataException(
                            "Invalid variability attribute:"
                            + " entry index = " + perfDataItem
                            + " name = " + name
                            + " variability = " + v);
                }
                perfDataItem++;
            } else {
                // bad data types.
                throw new MonitorTypeException(
                        "Invalid Monitor type:" + " entry index = "
                        + perfDataItem + " name = " + name
                        + " type = " + dataType);
            }
        }

        // setup index to next entry for next iteration of the loop.
        nextEntry = entryStart + entryLength;
        return monitor;
    }
}
