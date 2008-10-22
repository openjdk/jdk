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

import java.lang.management.RuntimeMXBean;
import java.lang.management.ManagementFactory;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Properties;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;
import javax.management.openmbean.OpenDataException;
import javax.management.ObjectName;

/**
 * Implementation class for the runtime subsystem.
 * Standard and committed hotspot-specific metrics if any.
 *
 * ManagementFactory.getRuntimeMXBean() returns an instance
 * of this class.
 */
class RuntimeImpl implements RuntimeMXBean {

    private final VMManagement jvm;
    private final long vmStartupTime;

    /**
     * Constructor of RuntimeImpl class.
     */
    RuntimeImpl(VMManagement vm) {
        this.jvm = vm;
        this.vmStartupTime = jvm.getStartupTime();
    }

    public String getName() {
        return jvm.getVmId();
    }

    public String getManagementSpecVersion() {
        return jvm.getManagementVersion();
    }

    public String getVmName() {
        return jvm.getVmName();
    }

    public String getVmVendor() {
        return jvm.getVmVendor();
    }

    public String getVmVersion() {
        return jvm.getVmVersion();
    }

    public String getSpecName() {
        return jvm.getVmSpecName();
    }

    public String getSpecVendor() {
        return jvm.getVmSpecVendor();
    }

    public String getSpecVersion() {
        return jvm.getVmSpecVersion();
    }

    public String getClassPath() {
        return jvm.getClassPath();
    }

    public String getLibraryPath() {
        return jvm.getLibraryPath();
    }

    public String getBootClassPath() {
        if (!isBootClassPathSupported()) {
            throw new UnsupportedOperationException(
                "Boot class path mechanism is not supported");
        }
        Util.checkMonitorAccess();
        return jvm.getBootClassPath();
    }

    public List<String> getInputArguments() {
        Util.checkMonitorAccess();
        return jvm.getVmArguments();
    }

    public long getUptime() {
        long current = System.currentTimeMillis();

        // TODO: If called from client side when we support
        // MBean proxy to read performance counters from shared memory,
        // need to check if the monitored VM exitd.
        return (current - vmStartupTime);
    }

    public long getStartTime() {
        return vmStartupTime;
    }

    public boolean isBootClassPathSupported() {
        return jvm.isBootClassPathSupported();
    }

    public Map<String,String> getSystemProperties() {
        Properties sysProps = System.getProperties();
        Map<String,String> map = new HashMap<String, String>();

        // Properties.entrySet() does not include the entries in
        // the default properties.  So use Properties.stringPropertyNames()
        // to get the list of property keys including the default ones.
        Set<String> keys = sysProps.stringPropertyNames();
        for (String k : keys) {
            String value = sysProps.getProperty(k);
            map.put(k, value);
        }

        return map;
    }

    public ObjectName getObjectName() {
        return ObjectName.valueOf(ManagementFactory.RUNTIME_MXBEAN_NAME);
    }

}
