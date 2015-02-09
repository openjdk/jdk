/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.management.internal;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.management.DynamicMBean;
import javax.management.ObjectName;
import sun.management.ManagementFactoryHelper;
import sun.management.spi.PlatformMBeanProvider;

public final class PlatformMBeanProviderImpl extends PlatformMBeanProvider {
    private final List<PlatformComponent<?>> mxbeanList;

    public PlatformMBeanProviderImpl() {
        mxbeanList = Collections.unmodifiableList(init());
    }

    @Override
    public List<PlatformComponent<?>> getPlatformComponentList() {
        return mxbeanList;
    }

    private List<PlatformComponent<?>> init() {
        ArrayList<PlatformComponent<?>> initMBeanList = new ArrayList<>();
        /**
         * Garbage Collector in the Java virtual machine.
         */
        initMBeanList.add(new PlatformComponent<java.lang.management.MemoryManagerMXBean>() {
            private final Set<String> garbageCollectorMXBeanInterfaceNames
                    = Collections.unmodifiableSet(
                            Stream.of("java.lang.management.MemoryManagerMXBean",
                                    "java.lang.management.GarbageCollectorMXBean",
                                    "com.sun.management.GarbageCollectorMXBean")
                            .collect(Collectors.toSet()));

            @Override
            public Set<Class<? extends java.lang.management.MemoryManagerMXBean>> mbeanInterfaces() {
                return Stream.of(java.lang.management.MemoryManagerMXBean.class,
                        java.lang.management.GarbageCollectorMXBean.class,
                        com.sun.management.GarbageCollectorMXBean.class)
                        .collect(Collectors.toSet());
            }

            @Override
            public Set<String> mbeanInterfaceNames() {
                return garbageCollectorMXBeanInterfaceNames;
            }

            @Override
            public String getObjectNamePattern() {
                return ManagementFactory.GARBAGE_COLLECTOR_MXBEAN_DOMAIN_TYPE + ",name=*";
            }

            @Override
            public boolean isSingleton() {
                return false; // zero or more instances
            }

            @Override
            public Map<String, java.lang.management.MemoryManagerMXBean> nameToMBeanMap() {
                List<java.lang.management.GarbageCollectorMXBean> list
                        = ManagementFactoryHelper.getGarbageCollectorMXBeans();;
                Map<String, java.lang.management.MemoryManagerMXBean> map;
                if (list.isEmpty()) {
                    map = Collections.<String, java.lang.management.MemoryManagerMXBean>emptyMap();
                } else {
                    map = new HashMap<>(list.size());
                    for (java.lang.management.MemoryManagerMXBean gcm : list) {
                        map.put(gcm.getObjectName().getCanonicalName(),
                                gcm);
                    }
                }
                return map;
            }
        });

        /**
         * OperatingSystemMXBean
         */
        initMBeanList.add(new PlatformComponent<java.lang.management.OperatingSystemMXBean>() {
            private final Set<String> operatingSystemMXBeanInterfaceNames
                    = Collections.unmodifiableSet(
                            Stream.of("java.lang.management.OperatingSystemMXBean",
                                    "com.sun.management.OperatingSystemMXBean",
                                    "com.sun.management.UnixOperatingSystemMXBean")
                            .collect(Collectors.toSet()));

            @Override
            public Set<Class<? extends java.lang.management.OperatingSystemMXBean>> mbeanInterfaces() {
                return Stream.of(java.lang.management.OperatingSystemMXBean.class,
                        com.sun.management.OperatingSystemMXBean.class,
                        com.sun.management.UnixOperatingSystemMXBean.class)
                        .collect(Collectors.toSet());
            }

            @Override
            public Set<String> mbeanInterfaceNames() {
                return operatingSystemMXBeanInterfaceNames;
            }

            @Override
            public String getObjectNamePattern() {
                return ManagementFactory.OPERATING_SYSTEM_MXBEAN_NAME;
            }

            @Override
            public Map<String, java.lang.management.OperatingSystemMXBean> nameToMBeanMap() {
                return Collections.<String, java.lang.management.OperatingSystemMXBean>singletonMap(
                        ManagementFactory.OPERATING_SYSTEM_MXBEAN_NAME,
                        ManagementFactoryHelper.getOperatingSystemMXBean());
            }
        });

        /**
         * Diagnostic support for the HotSpot Virtual Machine.
         */
        initMBeanList.add(new PlatformComponent<com.sun.management.HotSpotDiagnosticMXBean>() {
            private final Set<String> hotSpotDiagnosticMXBeanInterfaceNames =
                    Collections.unmodifiableSet(Collections.<String>singleton("com.sun.management.HotSpotDiagnosticMXBean"));

            @Override
            public Set<Class<? extends com.sun.management.HotSpotDiagnosticMXBean>> mbeanInterfaces() {
                return Collections.singleton(com.sun.management.HotSpotDiagnosticMXBean.class);
            }

            @Override
            public Set<String> mbeanInterfaceNames() {
                return hotSpotDiagnosticMXBeanInterfaceNames;
            }

            @Override
            public String getObjectNamePattern() {
                return "com.sun.management:type=HotSpotDiagnostic";
            }

            @Override
            public Map<String, com.sun.management.HotSpotDiagnosticMXBean> nameToMBeanMap() {
                return Collections.<String, com.sun.management.HotSpotDiagnosticMXBean>singletonMap(
                        "com.sun.management:type=HotSpotDiagnostic",
                        ManagementFactoryHelper.getDiagnosticMXBean());
            }
        });

        /**
         * DynamicMBean
         */
        HashMap<ObjectName, DynamicMBean> dynmbeans
                = ManagementFactoryHelper.getPlatformDynamicMBeans();
        final Set<String> dynamicMBeanInterfaceNames =
            Collections.unmodifiableSet(Collections.<String>singleton("javax.management.DynamicMBean"));
        for (Map.Entry<ObjectName, DynamicMBean> e : dynmbeans.entrySet()) {
            initMBeanList.add(new PlatformComponent<DynamicMBean>() {
                @Override
                public Set<String> mbeanInterfaceNames() {
                    return dynamicMBeanInterfaceNames;
                }

                @Override
                public Set<Class<? extends DynamicMBean>> mbeanInterfaces() {
                    return Collections.emptySet(); // DynamicMBean cannot be used to find an MBean by ManagementFactory
                }

                @Override
                public String getObjectNamePattern() {
                    return e.getKey().getCanonicalName();
                }

                @Override
                public Map<String, DynamicMBean> nameToMBeanMap() {
                    return Collections.<String, DynamicMBean>singletonMap(
                            e.getKey().getCanonicalName(),
                            e.getValue());
                }
            });
        }
        initMBeanList.trimToSize();
        return initMBeanList;
    }
}
