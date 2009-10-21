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

import java.lang.management.*;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanRegistrationException;
import javax.management.NotCompliantMBeanException;
import javax.management.RuntimeOperationsException;
import java.nio.BufferPoolMXBean;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import sun.security.action.LoadLibraryAction;

import java.util.ArrayList;
import java.util.List;
import com.sun.management.OSMBeanFactory;
import com.sun.management.HotSpotDiagnosticMXBean;

import static java.lang.management.ManagementFactory.*;

/**
 * ManagementFactoryHelper provides static factory methods to create
 * instances of the management interface.
 */
public class ManagementFactoryHelper {
    private ManagementFactoryHelper() {};

    private static VMManagement jvm;

    private static ClassLoadingImpl    classMBean = null;
    private static MemoryImpl          memoryMBean = null;
    private static ThreadImpl          threadMBean = null;
    private static RuntimeImpl         runtimeMBean = null;
    private static CompilationImpl     compileMBean = null;
    private static OperatingSystemImpl osMBean = null;

    public static synchronized ClassLoadingMXBean getClassLoadingMXBean() {
        if (classMBean == null) {
            classMBean = new ClassLoadingImpl(jvm);
        }
        return classMBean;
    }

    public static synchronized MemoryMXBean getMemoryMXBean() {
        if (memoryMBean == null) {
            memoryMBean = new MemoryImpl(jvm);
        }
        return memoryMBean;
    }

    public static synchronized ThreadMXBean getThreadMXBean() {
        if (threadMBean == null) {
            threadMBean = new ThreadImpl(jvm);
        }
        return threadMBean;
    }

    public static synchronized RuntimeMXBean getRuntimeMXBean() {
        if (runtimeMBean == null) {
            runtimeMBean = new RuntimeImpl(jvm);
        }
        return runtimeMBean;
    }

    public static synchronized CompilationMXBean getCompilationMXBean() {
        if (compileMBean == null && jvm.getCompilerName() != null) {
            compileMBean = new CompilationImpl(jvm);
        }
        return compileMBean;
    }

    public static synchronized OperatingSystemMXBean getOperatingSystemMXBean() {
        if (osMBean == null) {
            osMBean = (OperatingSystemImpl)
                          OSMBeanFactory.getOperatingSystemMXBean(jvm);
        }
        return osMBean;
    }

    public static List<MemoryPoolMXBean> getMemoryPoolMXBeans() {
        MemoryPoolMXBean[] pools = MemoryImpl.getMemoryPools();
        List<MemoryPoolMXBean> list = new ArrayList<MemoryPoolMXBean>(pools.length);
        for (MemoryPoolMXBean p : pools) {
            list.add(p);
        }
        return list;
    }

    public static List<MemoryManagerMXBean> getMemoryManagerMXBeans() {
        MemoryManagerMXBean[]  mgrs = MemoryImpl.getMemoryManagers();
        List<MemoryManagerMXBean> result = new ArrayList<MemoryManagerMXBean>(mgrs.length);
        for (MemoryManagerMXBean m : mgrs) {
            result.add(m);
        }
        return result;
    }

    public static List<GarbageCollectorMXBean> getGarbageCollectorMXBeans() {
        MemoryManagerMXBean[]  mgrs = MemoryImpl.getMemoryManagers();
        List<GarbageCollectorMXBean> result = new ArrayList<GarbageCollectorMXBean>(mgrs.length);
        for (MemoryManagerMXBean m : mgrs) {
            if (GarbageCollectorMXBean.class.isInstance(m)) {
                 result.add(GarbageCollectorMXBean.class.cast(m));
            }
        }
        return result;
    }

    public static List<BufferPoolMXBean> getBufferPoolMXBeans() {
        List<BufferPoolMXBean> pools = new ArrayList<BufferPoolMXBean>(2);
        pools.add(createBufferPoolMXBean(sun.misc.SharedSecrets.getJavaNioAccess()
            .getDirectBufferPool()));
        pools.add(createBufferPoolMXBean(sun.nio.ch.FileChannelImpl
            .getMappedBufferPool()));
        return pools;
    }

    private final static String BUFFER_POOL_MXBEAN_NAME = "java.nio:type=BufferPool";

    /**
     * Creates management interface for the given buffer pool.
     */
    private static BufferPoolMXBean
        createBufferPoolMXBean(final sun.misc.JavaNioAccess.BufferPool pool)
    {
        return new BufferPoolMXBean() {
            private volatile ObjectName objname;  // created lazily
            @Override
            public ObjectName getObjectName() {
                ObjectName result = objname;
                if (result == null) {
                    synchronized (this) {
                        if (objname == null) {
                            result = Util.newObjectName(BUFFER_POOL_MXBEAN_NAME +
                                ",name=" + pool.getName());
                            objname = result;
                        }
                    }
                }
                return result;
            }
            @Override
            public String getName() {
                return pool.getName();
            }
            @Override
            public long getCount() {
                return pool.getCount();
            }
            @Override
            public long getTotalCapacity() {
                return pool.getTotalCapacity();
            }
            @Override
            public long getMemoryUsed() {
                return pool.getMemoryUsed();
            }
        };
    }

    private static HotSpotDiagnostic hsDiagMBean = null;
    private static HotspotRuntime hsRuntimeMBean = null;
    private static HotspotClassLoading hsClassMBean = null;
    private static HotspotThread hsThreadMBean = null;
    private static HotspotCompilation hsCompileMBean = null;
    private static HotspotMemory hsMemoryMBean = null;

    public static synchronized HotSpotDiagnosticMXBean getDiagnosticMXBean() {
        if (hsDiagMBean == null) {
            hsDiagMBean = new HotSpotDiagnostic();
        }
        return hsDiagMBean;
    }

    /**
     * This method is for testing only.
     */
    public static synchronized HotspotRuntimeMBean getHotspotRuntimeMBean() {
        if (hsRuntimeMBean == null) {
            hsRuntimeMBean = new HotspotRuntime(jvm);
        }
        return hsRuntimeMBean;
    }

    /**
     * This method is for testing only.
     */
    public static synchronized HotspotClassLoadingMBean getHotspotClassLoadingMBean() {
        if (hsClassMBean == null) {
            hsClassMBean = new HotspotClassLoading(jvm);
        }
        return hsClassMBean;
    }

    /**
     * This method is for testing only.
     */
    public static synchronized HotspotThreadMBean getHotspotThreadMBean() {
        if (hsThreadMBean == null) {
            hsThreadMBean = new HotspotThread(jvm);
        }
        return hsThreadMBean;
    }

    /**
     * This method is for testing only.
     */
    public static synchronized HotspotMemoryMBean getHotspotMemoryMBean() {
        if (hsMemoryMBean == null) {
            hsMemoryMBean = new HotspotMemory(jvm);
        }
        return hsMemoryMBean;
    }

    /**
     * This method is for testing only.
     */
    public static synchronized HotspotCompilationMBean getHotspotCompilationMBean() {
        if (hsCompileMBean == null) {
            hsCompileMBean = new HotspotCompilation(jvm);
        }
        return hsCompileMBean;
    }

    /**
     * Registers a given MBean if not registered in the MBeanServer;
     * otherwise, just return.
     */
    private static void addMBean(MBeanServer mbs, Object mbean, String mbeanName) {
        try {
            final ObjectName objName = Util.newObjectName(mbeanName);

            // inner class requires these fields to be final
            final MBeanServer mbs0 = mbs;
            final Object mbean0 = mbean;
            AccessController.doPrivileged(new PrivilegedExceptionAction<Void>() {
                public Void run() throws MBeanRegistrationException,
                                         NotCompliantMBeanException {
                    try {
                        mbs0.registerMBean(mbean0, objName);
                        return null;
                    } catch (InstanceAlreadyExistsException e) {
                        // if an instance with the object name exists in
                        // the MBeanServer ignore the exception
                    }
                    return null;
                }
            });
        } catch (PrivilegedActionException e) {
            throw Util.newException(e.getException());
        }
    }

    private final static String HOTSPOT_CLASS_LOADING_MBEAN_NAME =
        "sun.management:type=HotspotClassLoading";

    private final static String HOTSPOT_COMPILATION_MBEAN_NAME =
        "sun.management:type=HotspotCompilation";

    private final static String HOTSPOT_MEMORY_MBEAN_NAME =
        "sun.management:type=HotspotMemory";

    private static final String HOTSPOT_RUNTIME_MBEAN_NAME =
        "sun.management:type=HotspotRuntime";

    private final static String HOTSPOT_THREAD_MBEAN_NAME =
        "sun.management:type=HotspotThreading";

    static void registerInternalMBeans(MBeanServer mbs) {
        // register all internal MBeans if not registered
        // No exception is thrown if a MBean with that object name
        // already registered
        addMBean(mbs, getHotspotClassLoadingMBean(),
            HOTSPOT_CLASS_LOADING_MBEAN_NAME);
        addMBean(mbs, getHotspotMemoryMBean(),
            HOTSPOT_MEMORY_MBEAN_NAME);
        addMBean(mbs, getHotspotRuntimeMBean(),
            HOTSPOT_RUNTIME_MBEAN_NAME);
        addMBean(mbs, getHotspotThreadMBean(),
            HOTSPOT_THREAD_MBEAN_NAME);

        // CompilationMBean may not exist
        if (getCompilationMXBean() != null) {
            addMBean(mbs, getHotspotCompilationMBean(),
                HOTSPOT_COMPILATION_MBEAN_NAME);
        }
    }

    private static void unregisterMBean(MBeanServer mbs, String mbeanName) {
        try {
            final ObjectName objName = Util.newObjectName(mbeanName);

            // inner class requires these fields to be final
            final MBeanServer mbs0 = mbs;
            AccessController.doPrivileged(new PrivilegedExceptionAction<Void>() {
                public Void run() throws MBeanRegistrationException,
                                           RuntimeOperationsException  {
                    try {
                        mbs0.unregisterMBean(objName);
                    } catch (InstanceNotFoundException e) {
                        // ignore exception if not found
                    }
                    return null;
                }
            });
        } catch (PrivilegedActionException e) {
            throw Util.newException(e.getException());
        }
    }

    static void unregisterInternalMBeans(MBeanServer mbs) {
        // unregister all internal MBeans
        unregisterMBean(mbs, HOTSPOT_CLASS_LOADING_MBEAN_NAME);
        unregisterMBean(mbs, HOTSPOT_MEMORY_MBEAN_NAME);
        unregisterMBean(mbs, HOTSPOT_RUNTIME_MBEAN_NAME);
        unregisterMBean(mbs, HOTSPOT_THREAD_MBEAN_NAME);

        // CompilationMBean may not exist
        if (getCompilationMXBean() != null) {
            unregisterMBean(mbs, HOTSPOT_COMPILATION_MBEAN_NAME);
        }
    }

    static {
        AccessController.doPrivileged(new LoadLibraryAction("management"));
        jvm = new VMManagementImpl();
    }

    public static boolean isThreadSuspended(int state) {
        return ((state & JMM_THREAD_STATE_FLAG_SUSPENDED) != 0);
    }

    public static boolean isThreadRunningNative(int state) {
        return ((state & JMM_THREAD_STATE_FLAG_NATIVE) != 0);
    }

    public static Thread.State toThreadState(int state) {
        // suspended and native bits may be set in state
        int threadStatus = state & ~JMM_THREAD_STATE_FLAG_MASK;
        return sun.misc.VM.toThreadState(threadStatus);
    }

    // These values are defined in jmm.h
    private static final int JMM_THREAD_STATE_FLAG_MASK = 0xFFF00000;
    private static final int JMM_THREAD_STATE_FLAG_SUSPENDED = 0x00100000;
    private static final int JMM_THREAD_STATE_FLAG_NATIVE = 0x00400000;

}
