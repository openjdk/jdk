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
package jdk.tools.jaotc;

import org.graalvm.compiler.hotspot.meta.HotSpotInvokeDynamicPlugin.DynamicTypeStore;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.InvokeDynamicPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;

import jdk.vm.ci.hotspot.HotSpotConstantPool;
import jdk.vm.ci.hotspot.HotSpotConstantPoolObject;
import jdk.vm.ci.hotspot.HotSpotConstantReflectionProvider;
import jdk.vm.ci.hotspot.HotSpotObjectConstant;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.hotspot.HotSpotResolvedObjectType;
import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

final class AOTDynamicTypeStore implements DynamicTypeStore {

    public static class Location {
        private HotSpotResolvedObjectType holder;
        private int cpi;

        Location(HotSpotResolvedObjectType holder, int cpi) {
            this.holder = holder;
            this.cpi = cpi;
        }

        public HotSpotResolvedObjectType getHolder() {
            return holder;
        }
        public int getCpi() {
            return cpi;
        }
        public String toString() {
            return getHolder().getName() + "@" + cpi;
        }
        public int hashCode() {
            return holder.hashCode() + getClass().hashCode() + cpi;
        }
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (getClass() != o.getClass()) {
                return false;
            }
            Location l = (Location)o;
            return cpi == l.cpi && holder.equals(l.holder);
        }
    }

    public static class AdapterLocation extends Location {
        private int methodId;

        AdapterLocation(HotSpotResolvedObjectType holder, int cpi, int methodId) {
            super(holder, cpi);
            this.methodId = methodId;
        }
        public int getMethodId() {
            return methodId;
        }
        public String toString() {
            return "adapter:" + methodId + "@" + super.toString();
        }
    }

    public static class AppendixLocation extends Location {
        AppendixLocation(HotSpotResolvedObjectType holder, int cpi) {
            super(holder, cpi);
        }
        public String toString() {
            return "appendix@" + super.toString();
        }
    }

    private HashMap<HotSpotResolvedObjectType, HashSet<Location>> typeMap = new HashMap<>();
    private HashMap<HotSpotResolvedObjectType, HashSet<HotSpotResolvedObjectType>> holderMap = new HashMap<>();

    public Set<HotSpotResolvedObjectType> getDynamicTypes() {
        synchronized (typeMap) {
            return typeMap.keySet();
        }
    }

    public Set<HotSpotResolvedObjectType> getDynamicHolders() {
        synchronized (holderMap) {
            return holderMap.keySet();
        }
    }

    @Override
    public void recordAdapter(int opcode, HotSpotResolvedObjectType holder, int index, HotSpotResolvedJavaMethod adapter) {
        int cpi = ((HotSpotConstantPool)holder.getConstantPool()).rawIndexToConstantPoolIndex(index, opcode);
        int methodId = adapter.methodIdnum();
        HotSpotResolvedObjectType adapterType = adapter.getDeclaringClass();
        recordDynamicTypeLocation(new AdapterLocation(holder, cpi, methodId), adapterType);
    }

    @Override
    public JavaConstant recordAppendix(int opcode, HotSpotResolvedObjectType holder, int index, JavaConstant appendix) {
        int cpi = ((HotSpotConstantPool)holder.getConstantPool()).rawIndexToConstantPoolIndex(index, opcode);
        HotSpotResolvedObjectType appendixType = ((HotSpotObjectConstant)appendix).getType();
        recordDynamicTypeLocation(new AppendixLocation(holder, cpi), appendixType);
        // Make the constant locatable
        return HotSpotConstantPoolObject.forObject(holder, cpi, appendix);
    }

    private static <T> void recordDynamicMapValue(HashMap<HotSpotResolvedObjectType, HashSet<T>> map, HotSpotResolvedObjectType type, T v) {
        synchronized (map) {
            HashSet<T> set = map.get(type);
            if (set == null) {
                set = new HashSet<>();
                map.put(type, set);
            }
            set.add(v);
        }
    }

    private void recordDynamicTypeLocation(Location l, HotSpotResolvedObjectType type) {
        recordDynamicMapValue(typeMap, type, l);
        HotSpotResolvedObjectType holder = l.getHolder();
        recordDynamicMapValue(holderMap, holder, type);
    }

    public Set<Location> getDynamicClassLocationsForType(HotSpotResolvedObjectType type) {
        synchronized (typeMap) {
            return typeMap.get(type);
        }
    }

    public Set<HotSpotResolvedObjectType> getDynamicTypesForHolder(HotSpotResolvedObjectType holder) {
        synchronized (holderMap) {
            return holderMap.get(holder);
        }
    }

}
