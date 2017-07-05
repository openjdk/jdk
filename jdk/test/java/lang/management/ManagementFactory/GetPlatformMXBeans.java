/*
 * Copyright (c) 2008, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/*
 * @test
 * @bug     6610094
 * @summary Basic unit test of ManagementFactory.getPlatformMXBeans()
 *          and also PlatformManagedObject.getObjectName()
 * @author  Mandy Chung
 *
 * @run main GetPlatformMXBeans
 */

import java.lang.management.*;
import static java.lang.management.ManagementFactory.*;
import java.util.*;
import javax.management.*;

public class GetPlatformMXBeans {
    private static MBeanServer platformMBeanServer =
            getPlatformMBeanServer();
    public static void main(String[] argv) throws Exception {
        checkPlatformMXBean(getClassLoadingMXBean(),
                            ClassLoadingMXBean.class,
                            CLASS_LOADING_MXBEAN_NAME);
        checkPlatformMXBean(getCompilationMXBean(),
                            CompilationMXBean.class,
                            COMPILATION_MXBEAN_NAME);
        checkPlatformMXBean(getMemoryMXBean(),
                            MemoryMXBean.class,
                            MEMORY_MXBEAN_NAME);
        checkPlatformMXBean(getOperatingSystemMXBean(),
                            OperatingSystemMXBean.class,
                            OPERATING_SYSTEM_MXBEAN_NAME);
        checkPlatformMXBean(getRuntimeMXBean(),
                            RuntimeMXBean.class,
                            RUNTIME_MXBEAN_NAME);
        checkPlatformMXBean(getThreadMXBean(),
                            ThreadMXBean.class,
                            THREAD_MXBEAN_NAME);
        checkGarbageCollectorMXBeans(getGarbageCollectorMXBeans());
        checkMemoryManagerMXBeans(getMemoryManagerMXBeans());
        checkMemoryPoolMXBeans(getMemoryPoolMXBeans());
    }

    private static <T extends PlatformManagedObject>
        void checkPlatformMXBean(T obj, Class<T> mxbeanInterface,
                                 String mxbeanName) throws Exception
    {
        int numElements = (obj != null ? 1 : 0);
        // verify local list of platform MXBeans
        List<? extends PlatformManagedObject> mxbeans =
            getPlatformMXBeans(mxbeanInterface);
        if (mxbeans.size() != numElements) {
            throw new RuntimeException("Unmatched number of platform MXBeans "
                + mxbeans.size() + ". Expected = " + numElements);
        }

        if (obj != null) {
            PlatformManagedObject pmo = mxbeans.get(0);
            if (obj != pmo) {
                throw new RuntimeException("The list returned by getPlatformMXBeans"
                    + " not matched");
            }
            ObjectName on = new ObjectName(mxbeanName);
            if (!on.equals(pmo.getObjectName())) {
                throw new RuntimeException("Unmatched ObjectName " +
                    pmo.getObjectName() + " Expected = " + on);
            }
        }

        // verify platform MXBeans in the platform MBeanServer
        mxbeans = getPlatformMXBeans(platformMBeanServer, mxbeanInterface);
        if (mxbeans.size() != numElements) {
            throw new RuntimeException("Unmatched number of platform MXBeans "
                + mxbeans.size() + ". Expected = " + numElements);
        }
    }

    private static void checkMemoryManagerMXBeans(List<MemoryManagerMXBean> objs)
        throws Exception
    {
        checkPlatformMXBeans(objs, MemoryManagerMXBean.class);
        for (MemoryManagerMXBean mxbean : objs) {
            String domainAndType;
            if (mxbean instanceof GarbageCollectorMXBean) {
                domainAndType = GARBAGE_COLLECTOR_MXBEAN_DOMAIN_TYPE;
            } else {
                domainAndType = MEMORY_MANAGER_MXBEAN_DOMAIN_TYPE;
            }
            ObjectName on = new ObjectName(domainAndType +
                                           ",name=" + mxbean.getName());
            if (!on.equals(mxbean.getObjectName())) {
                throw new RuntimeException("Unmatched ObjectName " +
                    mxbean.getObjectName() + " Expected = " + on);
            }
        }
    }
    private static void checkMemoryPoolMXBeans(List<MemoryPoolMXBean> objs)
        throws Exception
    {
        checkPlatformMXBeans(objs, MemoryPoolMXBean.class);
        for (MemoryPoolMXBean mxbean : objs) {
            ObjectName on = new ObjectName(MEMORY_POOL_MXBEAN_DOMAIN_TYPE +
                                           ",name=" + mxbean.getName());
            if (!on.equals(mxbean.getObjectName())) {
                throw new RuntimeException("Unmatched ObjectName " +
                    mxbean.getObjectName() + " Expected = " + on);
            }
        }
    }

    private static void checkGarbageCollectorMXBeans(List<GarbageCollectorMXBean> objs)
        throws Exception
    {
        checkPlatformMXBeans(objs, GarbageCollectorMXBean.class);
        for (GarbageCollectorMXBean mxbean : objs) {
            ObjectName on = new ObjectName(GARBAGE_COLLECTOR_MXBEAN_DOMAIN_TYPE +
                                           ",name=" + mxbean.getName());
            if (!on.equals(mxbean.getObjectName())) {
                throw new RuntimeException("Unmatched ObjectName " +
                    mxbean.getObjectName() + " Expected = " + on);
            }
        }
    }

    private static <T extends PlatformManagedObject>
        void checkPlatformMXBeans(List<T> objs, Class<T> mxbeanInterface)
            throws Exception
    {
        // verify local list of platform MXBeans
        List<? extends PlatformManagedObject> mxbeans =
            getPlatformMXBeans(mxbeanInterface);
        if (objs.size() != mxbeans.size()) {
            throw new RuntimeException("Unmatched number of platform MXBeans "
                + mxbeans.size() + ". Expected = " + objs.size());
        }
        List<T> list = new ArrayList<T>(objs);
        for (PlatformManagedObject pmo : mxbeans) {
            if (list.contains(pmo)) {
                list.remove(pmo);
            } else {
                throw new RuntimeException(pmo +
                    " not in the platform MXBean list");
            }
        }

        if (!list.isEmpty()) {
            throw new RuntimeException("The list returned by getPlatformMXBeans"
                + " not matched");
        }

        // verify platform MXBeans in the platform MBeanServer
        mxbeans = getPlatformMXBeans(platformMBeanServer, mxbeanInterface);
        if (objs.size() != mxbeans.size()) {
            throw new RuntimeException("Unmatched number of platform MXBeans "
                + mxbeans.size() + ". Expected = " + objs.size());
        }
    }
}
