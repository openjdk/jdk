/*
 * Copyright (c) 2003, 2013, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Hashtable;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.lang.ref.WeakReference;

// jmx imports
//
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.InstanceAlreadyExistsException;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.Notification;
import javax.management.ListenerNotFoundException;
import javax.management.openmbean.CompositeData;

// jdmk imports
//
import com.sun.jmx.snmp.agent.SnmpMib;
import com.sun.jmx.snmp.daemon.SnmpAdaptorServer;
import com.sun.jmx.snmp.SnmpPeer;
import com.sun.jmx.snmp.SnmpParameters;

import com.sun.jmx.snmp.SnmpOidTable;
import com.sun.jmx.snmp.SnmpOid;
import com.sun.jmx.snmp.SnmpVarBindList;
import com.sun.jmx.snmp.SnmpVarBind;
import com.sun.jmx.snmp.SnmpCounter;
import com.sun.jmx.snmp.SnmpCounter64;
import com.sun.jmx.snmp.SnmpString;
import com.sun.jmx.snmp.SnmpInt;
import com.sun.jmx.snmp.Enumerated;
import com.sun.jmx.snmp.agent.SnmpMibTable;

import sun.management.snmp.jvmmib.JVM_MANAGEMENT_MIBOidTable;
import sun.management.snmp.jvmmib.JVM_MANAGEMENT_MIB;
import sun.management.snmp.jvmmib.JvmMemoryMeta;
import sun.management.snmp.jvmmib.JvmThreadingMeta;
import sun.management.snmp.jvmmib.JvmRuntimeMeta;
import sun.management.snmp.jvmmib.JvmClassLoadingMeta;
import sun.management.snmp.jvmmib.JvmCompilationMeta;
import sun.management.snmp.util.MibLogger;
import sun.management.snmp.util.SnmpCachedData;
import sun.management.snmp.util.SnmpTableHandler;

//java management imports
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryNotificationInfo;
import java.lang.management.MemoryType;

public class JVM_MANAGEMENT_MIB_IMPL extends JVM_MANAGEMENT_MIB {
    private static final long serialVersionUID = -8104825586888859831L;

    private static final MibLogger log =
        new MibLogger(JVM_MANAGEMENT_MIB_IMPL.class);

    private static WeakReference<SnmpOidTable> tableRef;

    public static SnmpOidTable getOidTable() {
        SnmpOidTable table = null;
        if(tableRef == null) {
            table =  new JVM_MANAGEMENT_MIBOidTable();
            tableRef = new WeakReference<>(table);
            return table;
        }

        table = tableRef.get();
        if(table == null) {
            table = new JVM_MANAGEMENT_MIBOidTable();
            tableRef = new WeakReference<>(table);
        }

        return table;
    }

    /**
     * Handler waiting for memory <CODE>Notification</CODE>.
     * Translate each JMX notification in SNMP trap.
     */
    private class NotificationHandler implements NotificationListener {
        public void handleNotification(Notification notification,
                                       Object handback) {
            log.debug("handleNotification", "Received notification [ " +
                      notification.getType() + "]");

            String type = notification.getType();
            if (type.equals(MemoryNotificationInfo.MEMORY_THRESHOLD_EXCEEDED) ||
                type.equals(MemoryNotificationInfo.
                    MEMORY_COLLECTION_THRESHOLD_EXCEEDED)) {
                MemoryNotificationInfo minfo = MemoryNotificationInfo.
                    from((CompositeData) notification.getUserData());
                SnmpCounter64 count = new SnmpCounter64(minfo.getCount());
                SnmpCounter64 used =
                    new SnmpCounter64(minfo.getUsage().getUsed());
                SnmpString poolName = new SnmpString(minfo.getPoolName());
                SnmpOid entryIndex =
                    getJvmMemPoolEntryIndex(minfo.getPoolName());

                if (entryIndex == null) {
                    log.error("handleNotification",
                              "Error: Can't find entry index for Memory Pool: "
                              + minfo.getPoolName() +": " +
                              "No trap emitted for " + type);
                    return;
                }

                SnmpOid trap = null;

                final SnmpOidTable mibTable = getOidTable();
                try {
                    SnmpOid usedOid  = null;
                    SnmpOid countOid = null;

                    if (type.equals(MemoryNotificationInfo.
                                   MEMORY_THRESHOLD_EXCEEDED)) {
                        trap = new SnmpOid(mibTable.
                        resolveVarName("jvmLowMemoryPoolUsageNotif").getOid());
                        usedOid =
                            new SnmpOid(mibTable.
                            resolveVarName("jvmMemPoolUsed").getOid() +
                                    "." + entryIndex);
                        countOid =
                            new SnmpOid(mibTable.
                            resolveVarName("jvmMemPoolThreshdCount").getOid()
                                    + "." + entryIndex);
                    } else if  (type.equals(MemoryNotificationInfo.
                                   MEMORY_COLLECTION_THRESHOLD_EXCEEDED)) {
                        trap = new SnmpOid(mibTable.
                        resolveVarName("jvmLowMemoryPoolCollectNotif").
                                           getOid());
                        usedOid =
                            new SnmpOid(mibTable.
                            resolveVarName("jvmMemPoolCollectUsed").getOid() +
                                        "." + entryIndex);
                        countOid =
                            new SnmpOid(mibTable.
                            resolveVarName("jvmMemPoolCollectThreshdCount").
                                        getOid() +
                                        "." + entryIndex);
                    }

                    //Datas
                    SnmpVarBindList list = new SnmpVarBindList();
                    SnmpOid poolNameOid =
                        new SnmpOid(mibTable.
                                    resolveVarName("jvmMemPoolName").getOid() +
                                    "." + entryIndex);

                    SnmpVarBind varCount = new SnmpVarBind(countOid, count);
                    SnmpVarBind varUsed = new SnmpVarBind(usedOid, used);
                    SnmpVarBind varPoolName = new SnmpVarBind(poolNameOid,
                                              poolName);

                    list.add(varPoolName);
                    list.add(varCount);
                    list.add(varUsed);

                    sendTrap(trap, list);
                }catch(Exception e) {
                    log.error("handleNotification",
                              "Exception occurred : " + e);
                }
            }
        }
    }

    /**
     * List of notification targets.
     */
    private ArrayList<NotificationTarget> notificationTargets =
            new ArrayList<>();
    private final NotificationEmitter emitter;
    private final NotificationHandler handler;


    /**
     * Instantiate a JVM MIB intrusmentation.
     * A <CODE>NotificationListener</CODE> is added to the <CODE>MemoryMXBean</CODE>
     * <CODE>NotificationEmitter</CODE>
     */
    public JVM_MANAGEMENT_MIB_IMPL() {
        handler = new NotificationHandler();
        emitter = (NotificationEmitter) ManagementFactory.getMemoryMXBean();
        emitter.addNotificationListener(handler, null, null);
    }

    private synchronized void sendTrap(SnmpOid trap, SnmpVarBindList list) {
        final Iterator<NotificationTarget> iterator = notificationTargets.iterator();
        final SnmpAdaptorServer adaptor =
            (SnmpAdaptorServer) getSnmpAdaptor();

        if (adaptor == null) {
            log.error("sendTrap", "Cannot send trap: adaptor is null.");
            return;
        }

        if (!adaptor.isActive()) {
            log.config("sendTrap", "Adaptor is not active: trap not sent.");
            return;
        }

        while(iterator.hasNext()) {
            NotificationTarget target = null;
            try {
                target = iterator.next();
                SnmpPeer peer =
                    new SnmpPeer(target.getAddress(), target.getPort());
                SnmpParameters p = new SnmpParameters();
                p.setRdCommunity(target.getCommunity());
                peer.setParams(p);
                log.debug("handleNotification", "Sending trap to " +
                          target.getAddress() + ":" + target.getPort());
                adaptor.snmpV2Trap(peer, trap, list, null);
            }catch(Exception e) {
                log.error("sendTrap",
                          "Exception occurred while sending trap to [" +
                          target + "]. Exception : " + e);
                log.debug("sendTrap",e);
            }
        }
    }

    /**
     * Add a notification target.
     * @param target The target to add
     * @throws IllegalArgumentException If target parameter is null.
     */
    public synchronized void addTarget(NotificationTarget target)
        throws IllegalArgumentException {
        if(target == null)
            throw new IllegalArgumentException("Target is null");

        notificationTargets.add(target);
    }

    /**
     * Remove notification listener.
     */
    public void terminate() {
        try {
            emitter.removeNotificationListener(handler);
        }catch(ListenerNotFoundException e) {
            log.error("terminate", "Listener Not found : " + e);
        }
    }

    /**
     * Add notification targets.
     * @param targets A list of
     * <CODE>sun.management.snmp.jvminstr.NotificationTarget</CODE>
     * @throws IllegalArgumentException If targets parameter is null.
     */
    public synchronized void addTargets(List<NotificationTarget> targets)
        throws IllegalArgumentException {
        if(targets == null)
            throw new IllegalArgumentException("Target list is null");

        notificationTargets.addAll(targets);
    }

    /**
     * Factory method for "JvmMemory" group MBean.
     *
     * You can redefine this method if you need to replace the default
     * generated MBean class with your own customized class.
     *
     * @param groupName Name of the group ("JvmMemory")
     * @param groupOid  OID of this group
     * @param groupObjname ObjectName for this group (may be null)
     * @param server    MBeanServer for this group (may be null)
     *
     * @return An instance of the MBean class generated for the
     *         "JvmMemory" group (JvmMemory)
     *
     * Note that when using standard metadata,
     * the returned object must implement the "JvmMemoryMBean"
     * interface.
     **/
    protected Object createJvmMemoryMBean(String groupName,
                String groupOid,  ObjectName groupObjname,
                                          MBeanServer server)  {

        // Note that when using standard metadata,
        // the returned object must implement the "JvmMemoryMBean"
        // interface.
        //
        if (server != null)
            return new JvmMemoryImpl(this,server);
        else
            return new JvmMemoryImpl(this);
    }

    /**
     * Factory method for "JvmMemory" group metadata class.
     *
     * You can redefine this method if you need to replace the default
     * generated metadata class with your own customized class.
     *
     * @param groupName Name of the group ("JvmMemory")
     * @param groupOid  OID of this group
     * @param groupObjname ObjectName for this group (may be null)
     * @param server    MBeanServer for this group (may be null)
     *
     * @return An instance of the metadata class generated for the
     *         "JvmMemory" group (JvmMemoryMeta)
     *
     **/
    protected JvmMemoryMeta createJvmMemoryMetaNode(String groupName,
                                                    String groupOid,
                                                    ObjectName groupObjname,
                                                    MBeanServer server) {
        return new JvmMemoryMetaImpl(this, objectserver);
    }

    /**
     * Factory method for "JvmThreading" group metadata class.
     *
     * You can redefine this method if you need to replace the default
     * generated metadata class with your own customized class.
     *
     * @param groupName Name of the group ("JvmThreading")
     * @param groupOid  OID of this group
     * @param groupObjname ObjectName for this group (may be null)
     * @param server    MBeanServer for this group (may be null)
     *
     * @return An instance of the metadata class generated for the
     *         "JvmThreading" group (JvmThreadingMeta)
     *
     **/
    protected JvmThreadingMeta createJvmThreadingMetaNode(String groupName,
                                                          String groupOid,
                                                          ObjectName groupObjname,
                                                          MBeanServer server)  {
        return new JvmThreadingMetaImpl(this, objectserver);
    }

    /**
     * Factory method for "JvmThreading" group MBean.
     *
     * You can redefine this method if you need to replace the default
     * generated MBean class with your own customized class.
     *
     * @param groupName Name of the group ("JvmThreading")
     * @param groupOid  OID of this group
     * @param groupObjname ObjectName for this group (may be null)
     * @param server    MBeanServer for this group (may be null)
     *
     * @return An instance of the MBean class generated for the
     *         "JvmThreading" group (JvmThreading)
     *
     * Note that when using standard metadata,
     * the returned object must implement the "JvmThreadingMBean"
     * interface.
     **/
    protected Object createJvmThreadingMBean(String groupName,
                                             String groupOid,
                                             ObjectName groupObjname,
                                             MBeanServer server)  {

        // Note that when using standard metadata,
        // the returned object must implement the "JvmThreadingMBean"
        // interface.
        //
        if (server != null)
            return new JvmThreadingImpl(this,server);
        else
            return new JvmThreadingImpl(this);
    }

    /**
     * Factory method for "JvmRuntime" group metadata class.
     *
     * You can redefine this method if you need to replace the default
     * generated metadata class with your own customized class.
     *
     * @param groupName Name of the group ("JvmRuntime")
     * @param groupOid  OID of this group
     * @param groupObjname ObjectName for this group (may be null)
     * @param server    MBeanServer for this group (may be null)
     *
     * @return An instance of the metadata class generated for the
     *         "JvmRuntime" group (JvmRuntimeMeta)
     *
     **/
    protected JvmRuntimeMeta createJvmRuntimeMetaNode(String groupName,
                                                      String groupOid,
                                                      ObjectName groupObjname,
                                                      MBeanServer server)  {
        return new JvmRuntimeMetaImpl(this, objectserver);
    }

    /**
     * Factory method for "JvmRuntime" group MBean.
     *
     * You can redefine this method if you need to replace the default
     * generated MBean class with your own customized class.
     *
     * @param groupName Name of the group ("JvmRuntime")
     * @param groupOid  OID of this group
     * @param groupObjname ObjectName for this group (may be null)
     * @param server    MBeanServer for this group (may be null)
     *
     * @return An instance of the MBean class generated for the
     *         "JvmRuntime" group (JvmRuntime)
     *
     * Note that when using standard metadata,
     * the returned object must implement the "JvmRuntimeMBean"
     * interface.
     **/
    protected Object createJvmRuntimeMBean(String groupName,
                                           String groupOid,
                                           ObjectName groupObjname,
                                           MBeanServer server)  {

        // Note that when using standard metadata,
        // the returned object must implement the "JvmRuntimeMBean"
        // interface.
        //
        if (server != null)
            return new JvmRuntimeImpl(this,server);
        else
            return new JvmRuntimeImpl(this);
    }

    /**
     * Factory method for "JvmCompilation" group metadata class.
     *
     * You can redefine this method if you need to replace the default
     * generated metadata class with your own customized class.
     *
     * @param groupName Name of the group ("JvmCompilation")
     * @param groupOid  OID of this group
     * @param groupObjname ObjectName for this group (may be null)
     * @param server    MBeanServer for this group (may be null)
     *
     * @return An instance of the metadata class generated for the
     *         "JvmCompilation" group (JvmCompilationMeta)
     *
     **/
    protected JvmCompilationMeta
        createJvmCompilationMetaNode(String groupName,
                                     String groupOid,
                                     ObjectName groupObjname,
                                     MBeanServer server)  {
        // If there is no compilation system, the jvmCompilation  will not
        // be instantiated.
        //
        if (ManagementFactory.getCompilationMXBean() == null) return null;
        return super.createJvmCompilationMetaNode(groupName,groupOid,
                                                  groupObjname,server);
    }

    /**
     * Factory method for "JvmCompilation" group MBean.
     *
     * You can redefine this method if you need to replace the default
     * generated MBean class with your own customized class.
     *
     * @param groupName Name of the group ("JvmCompilation")
     * @param groupOid  OID of this group
     * @param groupObjname ObjectName for this group (may be null)
     * @param server    MBeanServer for this group (may be null)
     *
     * @return An instance of the MBean class generated for the
     *         "JvmCompilation" group (JvmCompilation)
     *
     * Note that when using standard metadata,
     * the returned object must implement the "JvmCompilationMBean"
     * interface.
     **/
    protected Object createJvmCompilationMBean(String groupName,
                String groupOid,  ObjectName groupObjname, MBeanServer server)  {

        // Note that when using standard metadata,
        // the returned object must implement the "JvmCompilationMBean"
        // interface.
        //
        if (server != null)
            return new JvmCompilationImpl(this,server);
        else
            return new JvmCompilationImpl(this);
    }

    /**
     * Factory method for "JvmOS" group MBean.
     *
     * You can redefine this method if you need to replace the default
     * generated MBean class with your own customized class.
     *
     * @param groupName Name of the group ("JvmOS")
     * @param groupOid  OID of this group
     * @param groupObjname ObjectName for this group (may be null)
     * @param server    MBeanServer for this group (may be null)
     *
     * @return An instance of the MBean class generated for the
     *         "JvmOS" group (JvmOS)
     *
     * Note that when using standard metadata,
     * the returned object must implement the "JvmOSMBean"
     * interface.
     **/
    protected Object createJvmOSMBean(String groupName,
                String groupOid,  ObjectName groupObjname, MBeanServer server)  {

        // Note that when using standard metadata,
        // the returned object must implement the "JvmOSMBean"
        // interface.
        //
        if (server != null)
            return new JvmOSImpl(this,server);
        else
            return new JvmOSImpl(this);
    }


    /**
     * Factory method for "JvmClassLoading" group MBean.
     *
     * You can redefine this method if you need to replace the default
     * generated MBean class with your own customized class.
     *
     * @param groupName Name of the group ("JvmClassLoading")
     * @param groupOid  OID of this group
     * @param groupObjname ObjectName for this group (may be null)
     * @param server    MBeanServer for this group (may be null)
     *
     * @return An instance of the MBean class generated for the
     *         "JvmClassLoading" group (JvmClassLoading)
     *
     * Note that when using standard metadata,
     * the returned object must implement the "JvmClassLoadingMBean"
     * interface.
     **/
    protected Object createJvmClassLoadingMBean(String groupName,
                                                String groupOid,
                                                ObjectName groupObjname,
                                                MBeanServer server)  {

        // Note that when using standard metadata,
        // the returned object must implement the "JvmClassLoadingMBean"
        // interface.
        //
        if (server != null)
            return new JvmClassLoadingImpl(this,server);
        else
            return new JvmClassLoadingImpl(this);
    }

    static String validDisplayStringTC(String str) {

        if(str == null) return "";

        if(str.length() > DISPLAY_STRING_MAX_LENGTH) {
            return str.substring(0, DISPLAY_STRING_MAX_LENGTH);
        }
        else
            return str;
    }

    static String validJavaObjectNameTC(String str) {

        if(str == null) return "";

        if(str.length() > JAVA_OBJECT_NAME_MAX_LENGTH) {
            return str.substring(0, JAVA_OBJECT_NAME_MAX_LENGTH);
        }
        else
            return str;
    }

    static String validPathElementTC(String str) {

        if(str == null) return "";

        if(str.length() > PATH_ELEMENT_MAX_LENGTH) {
            return str.substring(0, PATH_ELEMENT_MAX_LENGTH);
        }
        else
            return str;
    }
    static String validArgValueTC(String str) {

        if(str == null) return "";

        if(str.length() > ARG_VALUE_MAX_LENGTH) {
            return str.substring(0, ARG_VALUE_MAX_LENGTH);
        }
        else
            return str;
    }

    /**
     * WARNING: This should probably be moved to JvmMemPoolTableMetaImpl
     **/
    private SnmpTableHandler getJvmMemPoolTableHandler(Object userData) {
        final SnmpMibTable meta =
            getRegisteredTableMeta("JvmMemPoolTable");
        if (! (meta instanceof JvmMemPoolTableMetaImpl)) {
            final String err = ((meta==null)?"No metadata for JvmMemPoolTable":
                                "Bad metadata class for JvmMemPoolTable: " +
                                meta.getClass().getName());
            log.error("getJvmMemPoolTableHandler", err);
            return null;
        }
        final JvmMemPoolTableMetaImpl memPoolTable =
            (JvmMemPoolTableMetaImpl) meta;
        return memPoolTable.getHandler(userData);
    }

    /**
     * WARNING: This should probably be moved to JvmMemPoolTableMetaImpl
     **/
    private int findInCache(SnmpTableHandler handler,
                            String poolName) {

        if (!(handler instanceof SnmpCachedData)) {
            if (handler != null) {
                final String err = "Bad class for JvmMemPoolTable datas: " +
                    handler.getClass().getName();
                log.error("getJvmMemPoolEntry", err);
            }
            return -1;
        }

        final SnmpCachedData data = (SnmpCachedData)handler;
        final int len = data.datas.length;
        for (int i=0; i < data.datas.length ; i++) {
            final MemoryPoolMXBean pool = (MemoryPoolMXBean) data.datas[i];
            if (poolName.equals(pool.getName())) return i;
        }
        return -1;
    }

    /**
     * WARNING: This should probably be moved to JvmMemPoolTableMetaImpl
     **/
    private SnmpOid getJvmMemPoolEntryIndex(SnmpTableHandler handler,
                                            String poolName) {
        final int index = findInCache(handler,poolName);
        if (index < 0) return null;
        return ((SnmpCachedData)handler).indexes[index];
    }

    private SnmpOid getJvmMemPoolEntryIndex(String poolName) {
        return getJvmMemPoolEntryIndex(getJvmMemPoolTableHandler(null),
                                       poolName);
    }

    // cache validity
    //
    // Should we define a property for this? Should we have different
    // cache validity periods depending on which table we cache?
    //
    public long validity() {
        return DEFAULT_CACHE_VALIDITY_PERIOD;
    }

    // Defined in RFC 2579
    private final static int DISPLAY_STRING_MAX_LENGTH=255;
    private final static int JAVA_OBJECT_NAME_MAX_LENGTH=1023;
    private final static int PATH_ELEMENT_MAX_LENGTH=1023;
    private final static int ARG_VALUE_MAX_LENGTH=1023;
    private final static int DEFAULT_CACHE_VALIDITY_PERIOD=1000;
}
