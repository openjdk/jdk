/*
 * Copyright (c) 2003, 2006, Oracle and/or its affiliates. All rights reserved.
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
import com.sun.jmx.mbeanserver.Util;
import java.io.Serializable;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Map;

// jmx imports
//
import javax.management.MBeanServer;
import com.sun.jmx.snmp.SnmpString;
import com.sun.jmx.snmp.SnmpStatusException;

// jdmk imports
//
import com.sun.jmx.snmp.agent.SnmpMib;

import sun.management.snmp.jvmmib.JvmRuntimeMBean;
import sun.management.snmp.jvmmib.EnumJvmRTBootClassPathSupport;
import sun.management.snmp.util.JvmContextFactory;

/**
 * The class is used for implementing the "JvmRuntime" group.
 */
public class JvmRuntimeImpl implements JvmRuntimeMBean {

    /**
     * Variable for storing the value of "JvmRTBootClassPathSupport".
     *
     * "Indicates whether the Java virtual machine supports the
     * boot class path mechanism used by the system class loader
     * to search for class files.
     *
     * See java.management.RuntimeMXBean.isBootClassPathSupported()
     * "
     *
     */
    static final EnumJvmRTBootClassPathSupport
        JvmRTBootClassPathSupportSupported =
        new EnumJvmRTBootClassPathSupport("supported");
    static final EnumJvmRTBootClassPathSupport
        JvmRTBootClassPathSupportUnSupported =
        new EnumJvmRTBootClassPathSupport("unsupported");

    /**
     * Constructor for the "JvmRuntime" group.
     * If the group contains a table, the entries created through an SNMP SET
     * will not be registered in Java DMK.
     */
    public JvmRuntimeImpl(SnmpMib myMib) {

    }


    /**
     * Constructor for the "JvmRuntime" group.
     * If the group contains a table, the entries created through an SNMP SET
     * will be AUTOMATICALLY REGISTERED in Java DMK.
     */
    public JvmRuntimeImpl(SnmpMib myMib, MBeanServer server) {

    }

    static RuntimeMXBean getRuntimeMXBean() {
        return ManagementFactory.getRuntimeMXBean();
    }

    private static String validDisplayStringTC(String str) {
        return JVM_MANAGEMENT_MIB_IMPL.validDisplayStringTC(str);
    }

    private static String validPathElementTC(String str) {
        return JVM_MANAGEMENT_MIB_IMPL.validPathElementTC(str);
    }

    private static String validJavaObjectNameTC(String str) {
        return JVM_MANAGEMENT_MIB_IMPL.validJavaObjectNameTC(str);
    }


    static String[] splitPath(String path) {
        final String[] items = path.split(java.io.File.pathSeparator);
        // for (int i=0;i<items.length;i++) {
        //    items[i]=validPathElementTC(items[i]);
        // }
        return items;
    }

    static String[] getClassPath(Object userData) {
        final Map<Object, Object> m =
                Util.cast((userData instanceof Map)?userData:null);
        final String tag = "JvmRuntime.getClassPath";

        // If the list is in the cache, simply return it.
        //
        if (m != null) {
            final String[] cached = (String[])m.get(tag);
            if (cached != null) return cached;
        }

        final String[] args = splitPath(getRuntimeMXBean().getClassPath());

        if (m != null) m.put(tag,args);
        return args;
    }

    static String[] getBootClassPath(Object userData) {
        if (!getRuntimeMXBean().isBootClassPathSupported())
        return new String[0];

        final Map<Object, Object> m =
                Util.cast((userData instanceof Map)?userData:null);
        final String tag = "JvmRuntime.getBootClassPath";

        // If the list is in the cache, simply return it.
        //
        if (m != null) {
            final String[] cached = (String[])m.get(tag);
            if (cached != null) return cached;
        }

        final String[] args = splitPath(getRuntimeMXBean().getBootClassPath());

        if (m != null) m.put(tag,args);
        return args;
    }

    static String[] getLibraryPath(Object userData) {
        final Map<Object, Object> m =
                Util.cast((userData instanceof Map)?userData:null);
        final String tag = "JvmRuntime.getLibraryPath";

        // If the list is in the cache, simply return it.
        //
        if (m != null) {
            final String[] cached = (String[])m.get(tag);
            if (cached != null) return cached;
        }

        final String[] args = splitPath(getRuntimeMXBean().getLibraryPath());

        if (m != null) m.put(tag,args);
        return args;
    }

    static String[] getInputArguments(Object userData) {
        final Map<Object, Object> m =
                Util.cast((userData instanceof Map)?userData:null);
        final String tag = "JvmRuntime.getInputArguments";

        // If the list is in the cache, simply return it.
        //
        if (m != null) {
            final String[] cached = (String[])m.get(tag);
            if (cached != null) return cached;
        }

        final List<String> l = getRuntimeMXBean().getInputArguments();
        final String[] args = l.toArray(new String[0]);

        if (m != null) m.put(tag,args);
        return args;
    }

    /**
     * Getter for the "JvmRTSpecVendor" variable.
     */
    public String getJvmRTSpecVendor() throws SnmpStatusException {
        return validDisplayStringTC(getRuntimeMXBean().getSpecVendor());
    }

    /**
     * Getter for the "JvmRTSpecName" variable.
     */
    public String getJvmRTSpecName() throws SnmpStatusException {
        return validDisplayStringTC(getRuntimeMXBean().getSpecName());
    }

    /**
     * Getter for the "JvmRTVersion" variable.
     */
    public String getJvmRTVMVersion() throws SnmpStatusException {
        return validDisplayStringTC(getRuntimeMXBean().getVmVersion());
    }

    /**
     * Getter for the "JvmRTVendor" variable.
     */
    public String getJvmRTVMVendor() throws SnmpStatusException {
        return validDisplayStringTC(getRuntimeMXBean().getVmVendor());
    }

    /**
     * Getter for the "JvmRTManagementSpecVersion" variable.
     */
    public String getJvmRTManagementSpecVersion() throws SnmpStatusException {
        return validDisplayStringTC(getRuntimeMXBean().
                                    getManagementSpecVersion());
    }

    /**
     * Getter for the "JvmRTVMName" variable.
     */
    public String getJvmRTVMName() throws SnmpStatusException {
        return validJavaObjectNameTC(getRuntimeMXBean().getVmName());
    }


    /**
     * Getter for the "JvmRTInputArgsCount" variable.
     */
    public Integer getJvmRTInputArgsCount() throws SnmpStatusException {

        final String[] args = getInputArguments(JvmContextFactory.
                                                getUserData());
        return new Integer(args.length);
    }

    /**
     * Getter for the "JvmRTBootClassPathSupport" variable.
     */
    public EnumJvmRTBootClassPathSupport getJvmRTBootClassPathSupport()
        throws SnmpStatusException {
        if(getRuntimeMXBean().isBootClassPathSupported())
            return JvmRTBootClassPathSupportSupported;
        else
            return JvmRTBootClassPathSupportUnSupported;
    }

    /**
     * Getter for the "JvmRTUptimeMs" variable.
     */
    public Long getJvmRTUptimeMs() throws SnmpStatusException {
        return new Long(getRuntimeMXBean().getUptime());
    }

    /**
     * Getter for the "JvmRTStartTimeMs" variable.
     */
    public Long getJvmRTStartTimeMs() throws SnmpStatusException {
        return new Long(getRuntimeMXBean().getStartTime());
    }

    /**
     * Getter for the "JvmRTSpecVersion" variable.
     */
    public String getJvmRTSpecVersion() throws SnmpStatusException {
        return validDisplayStringTC(getRuntimeMXBean().getSpecVersion());
    }

    /**
     * Getter for the "JvmRTName" variable.
     */
    public String getJvmRTName() throws SnmpStatusException {
        return validDisplayStringTC(getRuntimeMXBean().getName());
    }

}
