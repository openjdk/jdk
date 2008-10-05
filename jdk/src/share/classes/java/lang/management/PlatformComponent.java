/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
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

package java.lang.management;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.LoggingMXBean;
import java.util.logging.LogManager;
import java.nio.BufferPoolMXBean;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import com.sun.management.HotSpotDiagnosticMXBean;
import com.sun.management.UnixOperatingSystemMXBean;

import sun.management.ManagementFactoryHelper;

/**
 * This enum class defines the list of platform components
 * that provides monitoring and management support.
 * Each enum represents one MXBean interface. A MXBean
 * instance could implement one or more MXBean interfaces.
 *
 * For example, com.sun.management.GarbageCollectorMXBean
 * extends java.lang.management.GarbageCollectorMXBean
 * and there is one set of garbage collection MXBean instances,
 * each of which implements both c.s.m. and j.l.m. interfaces.
 * There are two separate enums GARBAGE_COLLECTOR
 * and SUN_GARBAGE_COLLECTOR so that ManagementFactory.getPlatformMXBeans(Class)
 * will return the list of MXBeans of the specified type.
 *
 * To add a new MXBean interface for the Java platform,
 * add a new enum constant and implement the MXBeanFetcher.
 */
enum PlatformComponent {

    /**
     * Class loading system of the Java virtual machine.
     */
    CLASS_LOADING(
        "java.lang.management.ClassLoadingMXBean",
        "java.lang", "ClassLoading", defaultKeyProperties(),
        new MXBeanFetcher<ClassLoadingMXBean>() {
            public List<ClassLoadingMXBean> getMXBeans() {
                return Collections.singletonList(ManagementFactoryHelper.getClassLoadingMXBean());
            }
        }),

    /**
     * Compilation system of the Java virtual machine.
     */
    COMPILATION(
        "java.lang.management.CompilationMXBean",
        "java.lang", "Compilation", defaultKeyProperties(),
        new MXBeanFetcher<CompilationMXBean>() {
            public List<CompilationMXBean> getMXBeans() {
                CompilationMXBean m = ManagementFactoryHelper.getCompilationMXBean();
                if (m == null) {
                   return Collections.emptyList();
                } else {
                   return Collections.singletonList(m);
                }
            }
        }),

    /**
     * Memory system of the Java virtual machine.
     */
    MEMORY(
        "java.lang.management.MemoryMXBean",
        "java.lang", "Memory", defaultKeyProperties(),
        new MXBeanFetcher<MemoryMXBean>() {
            public List<MemoryMXBean> getMXBeans() {
                return Collections.singletonList(ManagementFactoryHelper.getMemoryMXBean());
            }
        }),

    /**
     * Garbage Collector in the Java virtual machine.
     */
    GARBAGE_COLLECTOR(
        "java.lang.management.GarbageCollectorMXBean",
        "java.lang", "GarbageCollector", keyProperties("name"),
        new MXBeanFetcher<GarbageCollectorMXBean>() {
            public List<GarbageCollectorMXBean> getMXBeans() {
                return ManagementFactoryHelper.
                           getGarbageCollectorMXBeans();
            }
        }),

    /**
     * Memory manager in the Java virtual machine.
     */
    MEMORY_MANAGER(
        "java.lang.management.MemoryManagerMXBean",
        "java.lang", "MemoryManager", keyProperties("name"),
        new MXBeanFetcher<MemoryManagerMXBean>() {
            public List<MemoryManagerMXBean> getMXBeans() {
                return ManagementFactoryHelper.getMemoryManagerMXBeans();
            }
        },
        GARBAGE_COLLECTOR),

    /**
     * Memory pool in the Java virtual machine.
     */
    MEMORY_POOL(
        "java.lang.management.MemoryPoolMXBean",
        "java.lang", "MemoryPool", keyProperties("name"),
        new MXBeanFetcher<MemoryPoolMXBean>() {
            public List<MemoryPoolMXBean> getMXBeans() {
                return ManagementFactoryHelper.getMemoryPoolMXBeans();
            }
        }),

    /**
     * Operating system on which the Java virtual machine is running
     */
    OPERATING_SYSTEM(
        "java.lang.management.OperatingSystemMXBean",
        "java.lang", "OperatingSystem", defaultKeyProperties(),
        new MXBeanFetcher<OperatingSystemMXBean>() {
            public List<OperatingSystemMXBean> getMXBeans() {
                return Collections.singletonList(ManagementFactoryHelper.getOperatingSystemMXBean());
            }
        }),

    /**
     * Runtime system of the Java virtual machine.
     */
    RUNTIME(
        "java.lang.management.RuntimeMXBean",
        "java.lang", "Runtime", defaultKeyProperties(),
        new MXBeanFetcher<RuntimeMXBean>() {
            public List<RuntimeMXBean> getMXBeans() {
                return Collections.singletonList(ManagementFactoryHelper.getRuntimeMXBean());
            }
        }),

    /**
     * Threading system of the Java virtual machine.
     */
    THREADING(
        "java.lang.management.ThreadMXBean",
        "java.lang", "Threading", defaultKeyProperties(),
        new MXBeanFetcher<ThreadMXBean>() {
            public List<ThreadMXBean> getMXBeans() {
                return Collections.singletonList(ManagementFactoryHelper.getThreadMXBean());
            }
        }),


    /**
     * Logging facility.
     */
    LOGGING(
        "java.util.logging.LoggingMXBean",
        "java.util.logging", "Logging", defaultKeyProperties(),
        new MXBeanFetcher<LoggingMXBean>() {
            public List<LoggingMXBean> getMXBeans() {
                return Collections.singletonList(LogManager.getLoggingMXBean());
            }
        }),


    /**
     * Buffer pools.
     */
    BUFFER_POOL(
        "java.nio.BufferPoolMXBean",
        "java.nio", "BufferPool", keyProperties("name"),
        new MXBeanFetcher<BufferPoolMXBean>() {
            public List<BufferPoolMXBean> getMXBeans() {
                List<BufferPoolMXBean> pools = new ArrayList<BufferPoolMXBean>(2);
                pools.add( sun.misc.SharedSecrets.getJavaNioAccess().getDirectBufferPoolMXBean() );
                pools.add( sun.nio.ch.FileChannelImpl.getMappedBufferPoolMXBean() );
                return pools;
            }
        }),


    // Sun Platform Extension

    /**
     * Sun extension garbage collector that performs collections in cycles.
     */
    SUN_GARBAGE_COLLECTOR(
        "com.sun.management.GarbageCollectorMXBean",
        "java.lang", "GarbageCollector", keyProperties("name"),
        new MXBeanFetcher<com.sun.management.GarbageCollectorMXBean>() {
            public List<com.sun.management.GarbageCollectorMXBean> getMXBeans() {
                return getGcMXBeanList(com.sun.management.GarbageCollectorMXBean.class);
            }
        }),

    /**
     * Sun extension operating system on which the Java virtual machine
     * is running.
     */
    SUN_OPERATING_SYSTEM(
        "com.sun.management.OperatingSystemMXBean",
        "java.lang", "OperatingSystem", defaultKeyProperties(),
        new MXBeanFetcher<com.sun.management.OperatingSystemMXBean>() {
            public List<com.sun.management.OperatingSystemMXBean> getMXBeans() {
                return getOSMXBeanList(com.sun.management.OperatingSystemMXBean.class);
            }
        }),

    /**
     * Unix operating system.
     */
    SUN_UNIX_OPERATING_SYSTEM(
        "com.sun.management.UnixOperatingSystemMXBean",
        "java.lang", "OperatingSystem", defaultKeyProperties(),
        new MXBeanFetcher<UnixOperatingSystemMXBean>() {
            public List<UnixOperatingSystemMXBean> getMXBeans() {
                return getOSMXBeanList(com.sun.management.UnixOperatingSystemMXBean.class);
            }
        }),

    /**
     * Diagnostic support for the HotSpot Virtual Machine.
     */
    HOTSPOT_DIAGNOSTIC(
        "com.sun.management.HotSpotDiagnosticMXBean",
        "com.sun.management", "HotSpotDiagnostic", defaultKeyProperties(),
        new MXBeanFetcher<HotSpotDiagnosticMXBean>() {
            public List<HotSpotDiagnosticMXBean> getMXBeans() {
                return Collections.singletonList(ManagementFactoryHelper.getDiagnosticMXBean());
            }
        });


    /**
     * A task that returns the MXBeans for a component.
     */
    interface MXBeanFetcher<T extends PlatformManagedObject> {
        public List<T> getMXBeans();
    }

    /*
     * Returns a list of the GC MXBeans of the given type.
     */
    private static <T extends GarbageCollectorMXBean>
            List<T> getGcMXBeanList(Class<T> gcMXBeanIntf) {
        List<GarbageCollectorMXBean> list =
            ManagementFactoryHelper.getGarbageCollectorMXBeans();
        List<T> result = new ArrayList<T>(list.size());
        for (GarbageCollectorMXBean m : list) {
            if (gcMXBeanIntf.isInstance(m)) {
                result.add(gcMXBeanIntf.cast(m));
            }
        }
        return result;
    }

    /*
     * Returns the OS mxbean instance of the given type.
     */
    private static <T extends OperatingSystemMXBean>
            List<T> getOSMXBeanList(Class<T> osMXBeanIntf) {
        OperatingSystemMXBean m =
            ManagementFactoryHelper.getOperatingSystemMXBean();
        if (osMXBeanIntf.isInstance(m)) {
            return Collections.singletonList(osMXBeanIntf.cast(m));
        } else {
            return Collections.emptyList();
        }
    }

    private final String mxbeanInterfaceName;
    private final String domain;
    private final String type;
    private final Set<String> keyProperties;
    private final MXBeanFetcher fetcher;
    private final PlatformComponent[] subComponents;

    private PlatformComponent(String intfName,
                              String domain, String type,
                              Set<String> keyProperties,
                              MXBeanFetcher fetcher) {
        this.mxbeanInterfaceName = intfName;
        this.domain = domain;
        this.type = type;
        this.keyProperties = keyProperties;
        this.fetcher = fetcher;
        this.subComponents = new PlatformComponent[0];
    }
    private PlatformComponent(String intfName,
                              String domain, String type,
                              Set<String> keyProperties,
                              MXBeanFetcher fetcher,
                              PlatformComponent... subComponents) {
        this.mxbeanInterfaceName = intfName;
        this.domain = domain;
        this.type = type;
        this.keyProperties = keyProperties;
        this.fetcher = fetcher;
        this.subComponents = subComponents;
    }

    private static Set<String> defaultKeyProps;
    private static Set<String> defaultKeyProperties() {
        if (defaultKeyProps == null) {
            defaultKeyProps = Collections.singleton("type");
        }
        return defaultKeyProps;
    }

    private static Set<String> keyProperties(String... keyNames) {
        Set<String> set = new HashSet<String>();
        set.add("type");
        for (String s : keyNames) {
            set.add(s);
        }
        return set;
    }

    String getMXBeanInterfaceName() {
        return mxbeanInterfaceName;
    }

    @SuppressWarnings("unchecked")
    Class<? extends PlatformManagedObject> getMXBeanInterface() {
        try {
            // Lazy loading the MXBean interface only when it is needed
            return (Class<? extends PlatformManagedObject>)
                       Class.forName(mxbeanInterfaceName, false, null);
        } catch (ClassNotFoundException x) {
            throw new AssertionError(x);
        }
    }

    @SuppressWarnings("unchecked")
    <T extends PlatformManagedObject>
        List<T> getMXBeans(Class<T> mxbeanInterface)
    {
        return fetcher.getMXBeans();
    }

    <T extends PlatformManagedObject>
        List<T> getMXBeans(MBeanServerConnection mbs, Class<T> mxbeanInterface)
        throws java.io.IOException
    {
        List<T> result = new ArrayList<T>();
        for (ObjectName on : getObjectNames(mbs)) {
            result.add(ManagementFactory.
                newPlatformMXBeanProxy(mbs,
                                       on.getCanonicalName(),
                                       mxbeanInterface)
            );
        }
        return result;
    }

    private Set<ObjectName> getObjectNames(MBeanServerConnection mbs)
        throws java.io.IOException
    {
        String domainAndType = domain + ":type=" + type;
        if (keyProperties.size() > 1) {
            // if there are more than 1 key properties (i.e. other than "type")
            domainAndType += ",*";
        }
        ObjectName on = ObjectName.valueOf(domainAndType);
        Set<ObjectName> set =  mbs.queryNames(on, null);
        for (PlatformComponent pc : subComponents) {
            set.addAll(pc.getObjectNames(mbs));
        }
        return set;
    }

    private static final long serialVersionUID = 6992337162326171013L;
}
