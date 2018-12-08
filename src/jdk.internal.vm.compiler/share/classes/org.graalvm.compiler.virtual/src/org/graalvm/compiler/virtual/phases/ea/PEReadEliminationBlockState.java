/*
 * Copyright (c) 2011, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.virtual.phases.ea;

import java.util.Iterator;
import java.util.List;

import jdk.internal.vm.compiler.collections.EconomicMap;
import jdk.internal.vm.compiler.collections.Equivalence;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.nodes.FieldLocationIdentity;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.virtual.AllocatedObjectNode;
import org.graalvm.compiler.nodes.virtual.VirtualInstanceNode;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;
import org.graalvm.compiler.options.OptionValues;
import jdk.internal.vm.compiler.word.LocationIdentity;

import jdk.vm.ci.meta.JavaKind;

public final class PEReadEliminationBlockState extends PartialEscapeBlockState<PEReadEliminationBlockState> {

    final EconomicMap<ReadCacheEntry, ValueNode> readCache;

    static final class ReadCacheEntry {

        public final LocationIdentity identity;
        public final ValueNode object;
        public final int index;
        public final JavaKind kind;

        /* This flag does not affect hashCode or equals implementations. */
        public final boolean overflowAccess;

        ReadCacheEntry(LocationIdentity identity, ValueNode object, int index, JavaKind kind, boolean overflowAccess) {
            this.identity = identity;
            this.object = object;
            this.index = index;
            this.kind = kind;
            this.overflowAccess = overflowAccess;
        }

        @Override
        public int hashCode() {
            int result = 31 + ((identity == null) ? 0 : identity.hashCode());
            result = 31 * result + ((object == null) ? 0 : System.identityHashCode(object));
            result = 31 * result + kind.ordinal();
            return result * 31 + index;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof ReadCacheEntry)) {
                return false;
            }
            ReadCacheEntry other = (ReadCacheEntry) obj;
            return identity.equals(other.identity) && object == other.object && index == other.index && kind == other.kind;
        }

        @Override
        public String toString() {
            return index == -1 ? (object + ":" + kind + "<" + identity + ">") : (object + "[" + index + "]:" + kind + "<" + identity + ">");
        }
    }

    public PEReadEliminationBlockState(OptionValues options, DebugContext debug) {
        super(options, debug);
        readCache = EconomicMap.create(Equivalence.DEFAULT);
    }

    public PEReadEliminationBlockState(PEReadEliminationBlockState other) {
        super(other);
        readCache = EconomicMap.create(Equivalence.DEFAULT, other.readCache);
    }

    @Override
    public String toString() {
        return super.toString() + " " + readCache;
    }

    private static JavaKind stampToJavaKind(Stamp stamp) {
        if (stamp instanceof IntegerStamp) {
            switch (((IntegerStamp) stamp).getBits()) {
                case 1:
                    return JavaKind.Boolean;
                case 8:
                    return JavaKind.Byte;
                case 16:
                    return ((IntegerStamp) stamp).isPositive() ? JavaKind.Char : JavaKind.Short;
                case 32:
                    return JavaKind.Int;
                case 64:
                    return JavaKind.Long;
                default:
                    throw new IllegalArgumentException("unexpected IntegerStamp " + stamp);
            }
        } else {
            return stamp.getStackKind();
        }
    }

    @Override
    protected void objectMaterialized(VirtualObjectNode virtual, AllocatedObjectNode representation, List<ValueNode> values) {
        if (virtual instanceof VirtualInstanceNode) {
            VirtualInstanceNode instance = (VirtualInstanceNode) virtual;
            for (int i = 0; i < instance.entryCount(); i++) {
                JavaKind declaredKind = instance.field(i).getJavaKind();
                if (declaredKind == stampToJavaKind(values.get(i).stamp(NodeView.DEFAULT))) {
                    // We won't cache unaligned field writes upon instantiation unless we add
                    // support for non-array objects in PEReadEliminationClosure.processUnsafeLoad.
                    readCache.put(new ReadCacheEntry(new FieldLocationIdentity(instance.field(i)), representation, -1, declaredKind, false), values.get(i));
                }
            }
        }
    }

    @Override
    public boolean equivalentTo(PEReadEliminationBlockState other) {
        if (!isSubMapOf(readCache, other.readCache)) {
            return false;
        }
        return super.equivalentTo(other);
    }

    public void addReadCache(ValueNode object, LocationIdentity identity, int index, JavaKind kind, boolean overflowAccess, ValueNode value, PartialEscapeClosure<?> closure) {
        ValueNode cacheObject;
        ObjectState obj = closure.getObjectState(this, object);
        if (obj != null) {
            assert !obj.isVirtual();
            cacheObject = obj.getMaterializedValue();
        } else {
            cacheObject = object;
        }
        readCache.put(new ReadCacheEntry(identity, cacheObject, index, kind, overflowAccess), value);
    }

    public ValueNode getReadCache(ValueNode object, LocationIdentity identity, int index, JavaKind kind, PartialEscapeClosure<?> closure) {
        ValueNode cacheObject;
        ObjectState obj = closure.getObjectState(this, object);
        if (obj != null) {
            assert !obj.isVirtual() : object;
            cacheObject = obj.getMaterializedValue();
        } else {
            cacheObject = object;
        }
        ValueNode cacheValue = readCache.get(new ReadCacheEntry(identity, cacheObject, index, kind, false));
        obj = closure.getObjectState(this, cacheValue);
        if (obj != null) {
            assert !obj.isVirtual();
            cacheValue = obj.getMaterializedValue();
        } else {
            // assert !scalarAliases.containsKey(cacheValue);
            cacheValue = closure.getScalarAlias(cacheValue);
        }
        return cacheValue;
    }

    public void killReadCache() {
        readCache.clear();
    }

    public void killReadCache(LocationIdentity identity, int index) {
        Iterator<ReadCacheEntry> iter = readCache.getKeys().iterator();
        while (iter.hasNext()) {
            ReadCacheEntry entry = iter.next();
            if (entry.identity.equals(identity) && (index == -1 || entry.index == -1 || index == entry.index || entry.overflowAccess)) {
                iter.remove();
            }
        }
    }

    public EconomicMap<ReadCacheEntry, ValueNode> getReadCache() {
        return readCache;
    }
}
