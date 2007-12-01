/*
 * Copyright 2004 Sun Microsystems, Inc.  All Rights Reserved.
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

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;
import java.util.Set;
import java.io.IOException;

import sun.misc.Perf;
import sun.management.counter.Units;
import sun.management.counter.Counter;
import sun.management.counter.perf.PerfInstrumentation;

/**
 * A utility class to support the exporting and importing of the address
 * of a connector server using the instrumentation buffer.
 *
 * @since 1.5
 */
public class ConnectorAddressLink {

    private static final String CONNECTOR_ADDRESS_COUNTER =
        "sun.management.JMXConnectorServer.address";

    /**
     * Exports the specified connector address to the instrumentation buffer
     * so that it can be read by this or other Java virtual machines running
     * on the same system.
     *
     * @param   address         The connector address.
     */
    public static void export(String address) {
        if (address == null || address.length() == 0) {
            throw new IllegalArgumentException("address not specified");
        }
        Perf perf = Perf.getPerf();
        perf.createString(CONNECTOR_ADDRESS_COUNTER, 1, Units.STRING.intValue(), address);
    }

    /**
     * Imports the connector address from the instrument buffer
     * of the specified Java virtual machine.
     *
     * @param   vmid    an identifier that uniquely identifies a local
     *                  Java virtual machine, or <code>0</code> to indicate
     *                  the current Java virtual machine.
     *
     * @return  the value of the connector address, or <code>null</code>
     *          if the target VM has not exported a connector address.
     *
     * @throws  IOException     An I/O error occurred while trying to acquire
     *                          the instrumentation buffer.
     */
    public static String importFrom(int vmid) throws IOException {
        Perf perf = Perf.getPerf();
        ByteBuffer bb;
        try {
            bb = perf.attach(vmid, "r");
        } catch (IllegalArgumentException iae) {
            throw new IOException(iae.getMessage());
        }
        List counters = (new PerfInstrumentation(bb)).findByPattern(CONNECTOR_ADDRESS_COUNTER);
        Iterator i = counters.iterator();
        if (i.hasNext()) {
            Counter c = (Counter)i.next();
            return (String)c.getValue();
        } else {
            return null;
        }
    }

}
