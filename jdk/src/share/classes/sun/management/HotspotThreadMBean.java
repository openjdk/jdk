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

package sun.management;

import sun.management.counter.Counter;

/**
 * Hotspot internal management interface for the thread system.
 */
public interface HotspotThreadMBean {

    /**
     * Returns the current number of VM internal threads.
     *
     * @return the current number of VM internal threads.
     */
    public int getInternalThreadCount();

    /**
     * Returns a <tt>Map</tt> of the name of all VM internal threads
     * to the thread CPU time in nanoseconds.  The returned value is
     * of nanoseconds precision but not necessarily nanoseconds accuracy.
     * <p>
     *
     * @return a <tt>Map</tt> object of the name of all VM internal threads
     * to the thread CPU time in nanoseconds.
     *
     * @throws java.lang.UnsupportedOperationException if the Java virtual
     * machine does not support CPU time measurement.
     *
     * @see java.lang.management.ThreadMBean#isThreadCpuTimeSupported
     */
    public java.util.Map<String,Long> getInternalThreadCpuTimes();

    /**
     * Returns a list of internal counters maintained in the Java
     * virtual machine for the thread system.
     *
     * @return a list of internal counters maintained in the VM
     * for the thread system.
     */
    public java.util.List<Counter> getInternalThreadingCounters();
}
