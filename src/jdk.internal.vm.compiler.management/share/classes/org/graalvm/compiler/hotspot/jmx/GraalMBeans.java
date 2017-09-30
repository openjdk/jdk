/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.jmx;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jdk.vm.ci.runtime.JVMCI;
import jdk.vm.ci.runtime.JVMCICompiler;
import jdk.vm.ci.runtime.JVMCIRuntime;
import org.graalvm.compiler.hotspot.HotSpotGraalCompiler;
import sun.management.spi.PlatformMBeanProvider;
import sun.management.spi.PlatformMBeanProvider.PlatformComponent;

public final class GraalMBeans extends PlatformMBeanProvider {
    @Override
    public List<PlatformComponent<?>> getPlatformComponentList() {
        List<PlatformComponent<?>> components = new ArrayList<>();
        try {
            Object bean = findGraalRuntimeBean();
            if (bean != null) {
                components.add(new HotSpotRuntimeMBeanComponent(bean));
            }
        } catch (InternalError | LinkageError err) {
            // go on and ignore
        }
        return components;
    }

    public static Object findGraalRuntimeBean() {
        JVMCIRuntime r = JVMCI.getRuntime();
        JVMCICompiler c = r.getCompiler();
        if (c instanceof HotSpotGraalCompiler) {
            return ((HotSpotGraalCompiler) c).mbean();
        }
        return null;
    }

    private static final class HotSpotRuntimeMBeanComponent implements PlatformComponent<Object> {

        private final String name;
        private final Object mbean;

        HotSpotRuntimeMBeanComponent(Object mbean) {
            this.name = "org.graalvm.compiler.hotspot:type=Options";
            this.mbean = mbean;
        }

        @Override
        public Set<Class<?>> mbeanInterfaces() {
            return Collections.emptySet();
        }

        @Override
        public Set<String> mbeanInterfaceNames() {
            return Collections.emptySet();
        }

        @Override
        public String getObjectNamePattern() {
            return name;
        }

        @Override
        public Map<String, Object> nameToMBeanMap() {
            return Collections.<String, Object>singletonMap(name, mbean);
        }
    }
}
