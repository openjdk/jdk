/*
 * Copyright 2003-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

import java.lang.management.OperatingSystemMXBean;
import java.lang.management.ManagementFactory;
import javax.management.ObjectName;
import sun.misc.Unsafe;

/**
 * Implementation class for the operating system.
 * Standard and committed hotspot-specific metrics if any.
 *
 * ManagementFactory.getOperatingSystemMXBean() returns an instance
 * of this class.
 */
public class OperatingSystemImpl implements OperatingSystemMXBean {

    private final VMManagement jvm;

    /**
     * Constructor of OperatingSystemImpl class.
     */
    protected OperatingSystemImpl(VMManagement vm) {
        this.jvm = vm;
    }

    public String getName() {
        return jvm.getOsName();
    }

    public String getArch() {
        return jvm.getOsArch();
    }

    public String getVersion() {
        return jvm.getOsVersion();
    }

    public int getAvailableProcessors() {
        return jvm.getAvailableProcessors();
    }

    private static final Unsafe unsafe = Unsafe.getUnsafe();
    private double[] loadavg = new double[1];
    public double getSystemLoadAverage() {
        if (unsafe.getLoadAverage(loadavg, 1) == 1) {
             return loadavg[0];
        } else {
             return -1.0;
        }
    }
    public ObjectName getObjectName() {
        return ObjectName.valueOf(ManagementFactory.OPERATING_SYSTEM_MXBEAN_NAME);
    }

}

