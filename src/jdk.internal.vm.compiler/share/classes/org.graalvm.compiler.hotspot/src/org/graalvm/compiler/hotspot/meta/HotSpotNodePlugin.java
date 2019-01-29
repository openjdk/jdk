/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.hotspot.meta;

import static jdk.vm.ci.meta.DeoptimizationAction.None;
import static jdk.vm.ci.meta.DeoptimizationReason.TransferToInterpreter;
import static org.graalvm.compiler.core.common.GraalOptions.ImmutableCode;

import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.StampPair;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.HotSpotCompilationIdentifier;
import org.graalvm.compiler.hotspot.nodes.CurrentJavaThreadNode;
import org.graalvm.compiler.hotspot.word.HotSpotWordTypes;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedGuardNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.IntegerEqualsNode;
import org.graalvm.compiler.nodes.extended.GuardingNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderTool;
import org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.NodePlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.TypePlugin;
import org.graalvm.compiler.nodes.memory.HeapAccess.BarrierType;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.nodes.util.ConstantFoldUtil;
import org.graalvm.compiler.word.Word;
import org.graalvm.compiler.word.WordOperationPlugin;
import jdk.internal.vm.compiler.word.LocationIdentity;

import jdk.vm.ci.hotspot.HotSpotCompilationRequest;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.JavaTypeProfile;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

import java.lang.reflect.Field;
import sun.misc.Unsafe;

/**
 * This plugin does HotSpot-specific customization of bytecode parsing:
 * <ul>
 * <li>{@link Word}-type rewriting for {@link GraphBuilderContext#parsingIntrinsic intrinsic}
 * functions (snippets and method substitutions), by forwarding to the {@link WordOperationPlugin}.
 * Note that we forward the {@link NodePlugin} and {@link TypePlugin} methods, but not the
 * {@link InlineInvokePlugin} methods implemented by {@link WordOperationPlugin}. The latter is not
 * necessary because HotSpot only uses the {@link Word} type in methods that are force-inlined,
 * i.e., there are never non-inlined invokes that involve the {@link Word} type.</li>
 * <li>Constant folding of field loads.</li>
 * </ul>
 */
public final class HotSpotNodePlugin implements NodePlugin, TypePlugin {
    protected final WordOperationPlugin wordOperationPlugin;
    private final GraalHotSpotVMConfig config;
    private final HotSpotWordTypes wordTypes;

    public HotSpotNodePlugin(WordOperationPlugin wordOperationPlugin, GraalHotSpotVMConfig config, HotSpotWordTypes wordTypes) {
        this.wordOperationPlugin = wordOperationPlugin;
        this.config = config;
        this.wordTypes = wordTypes;
    }

    @Override
    public boolean canChangeStackKind(GraphBuilderContext b) {
        if (b.parsingIntrinsic()) {
            return wordOperationPlugin.canChangeStackKind(b);
        }
        return false;
    }

    @Override
    public StampPair interceptType(GraphBuilderTool b, JavaType declaredType, boolean nonNull) {
        if (b.parsingIntrinsic()) {
            return wordOperationPlugin.interceptType(b, declaredType, nonNull);
        }
        return null;
    }

    @Override
    public boolean handleInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
        if (b.parsingIntrinsic() && wordOperationPlugin.handleInvoke(b, method, args)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean handleLoadField(GraphBuilderContext b, ValueNode object, ResolvedJavaField field) {
        if (!ImmutableCode.getValue(b.getOptions()) || b.parsingIntrinsic()) {
            if (object.isConstant()) {
                JavaConstant asJavaConstant = object.asJavaConstant();
                if (tryReadField(b, field, asJavaConstant)) {
                    return true;
                }
            }
        }
        if (b.parsingIntrinsic() && wordOperationPlugin.handleLoadField(b, object, field)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean handleLoadStaticField(GraphBuilderContext b, ResolvedJavaField field) {
        if (!ImmutableCode.getValue(b.getOptions()) || b.parsingIntrinsic()) {
            if (tryReadField(b, field, null)) {
                return true;
            }
        }
        if (b.parsingIntrinsic() && wordOperationPlugin.handleLoadStaticField(b, field)) {
            return true;
        }
        return false;
    }

    private static boolean tryReadField(GraphBuilderContext b, ResolvedJavaField field, JavaConstant object) {
        return tryConstantFold(b, field, object);
    }

    private static boolean tryConstantFold(GraphBuilderContext b, ResolvedJavaField field, JavaConstant object) {
        ConstantNode result = ConstantFoldUtil.tryConstantFold(b.getConstantFieldProvider(), b.getConstantReflection(), b.getMetaAccess(), field, object, b.getOptions());
        if (result != null) {
            result = b.getGraph().unique(result);
            b.push(field.getJavaKind(), result);
            return true;
        }
        return false;
    }

    @Override
    public boolean handleStoreField(GraphBuilderContext b, ValueNode object, ResolvedJavaField field, ValueNode value) {
        if (b.parsingIntrinsic() && wordOperationPlugin.handleStoreField(b, object, field, value)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean handleStoreStaticField(GraphBuilderContext b, ResolvedJavaField field, ValueNode value) {
        if (b.parsingIntrinsic() && wordOperationPlugin.handleStoreStaticField(b, field, value)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean handleLoadIndexed(GraphBuilderContext b, ValueNode array, ValueNode index, GuardingNode boundsCheck, JavaKind elementKind) {
        if (b.parsingIntrinsic() && wordOperationPlugin.handleLoadIndexed(b, array, index, boundsCheck, elementKind)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean handleStoreIndexed(GraphBuilderContext b, ValueNode array, ValueNode index, GuardingNode boundsCheck, GuardingNode storeCheck, JavaKind elementKind, ValueNode value) {
        if (b.parsingIntrinsic() && wordOperationPlugin.handleStoreIndexed(b, array, index, boundsCheck, storeCheck, elementKind, value)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean handleCheckCast(GraphBuilderContext b, ValueNode object, ResolvedJavaType type, JavaTypeProfile profile) {
        if (b.parsingIntrinsic() && wordOperationPlugin.handleCheckCast(b, object, type, profile)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean handleInstanceOf(GraphBuilderContext b, ValueNode object, ResolvedJavaType type, JavaTypeProfile profile) {
        if (b.parsingIntrinsic() && wordOperationPlugin.handleInstanceOf(b, object, type, profile)) {
            return true;
        }
        return false;
    }

    @Override
    public FixedWithNextNode instrumentExceptionDispatch(StructuredGraph graph, FixedWithNextNode afterExceptionLoaded) {
        CompilationIdentifier id = graph.compilationId();
        if (id instanceof HotSpotCompilationIdentifier) {
            HotSpotCompilationRequest request = ((HotSpotCompilationIdentifier) id).getRequest();
            if (request != null) {
                long compileState = request.getJvmciEnv();
                if (compileState != 0 &&
                                config.jvmciCompileStateCanPostOnExceptionsOffset != Integer.MIN_VALUE &&
                                config.javaThreadShouldPostOnExceptionsFlagOffset != Integer.MIN_VALUE) {
                    long canPostOnExceptionsOffset = compileState + config.jvmciCompileStateCanPostOnExceptionsOffset;
                    boolean canPostOnExceptions = UNSAFE.getByte(canPostOnExceptionsOffset) != 0;
                    if (canPostOnExceptions) {
                        // If the exception capability is set, then generate code
                        // to check the JavaThread.should_post_on_exceptions flag to see
                        // if we actually need to report exception events for the current
                        // thread. If not, take the fast path otherwise deoptimize.
                        CurrentJavaThreadNode thread = graph.unique(new CurrentJavaThreadNode(wordTypes.getWordKind()));
                        ValueNode offset = graph.unique(ConstantNode.forLong(config.javaThreadShouldPostOnExceptionsFlagOffset));
                        AddressNode address = graph.unique(new OffsetAddressNode(thread, offset));
                        ReadNode shouldPostException = graph.add(new ReadNode(address, JAVA_THREAD_SHOULD_POST_ON_EXCEPTIONS_FLAG_LOCATION, StampFactory.intValue(), BarrierType.NONE));
                        afterExceptionLoaded.setNext(shouldPostException);
                        ValueNode zero = graph.unique(ConstantNode.forInt(0));
                        LogicNode cond = graph.unique(new IntegerEqualsNode(shouldPostException, zero));
                        FixedGuardNode check = graph.add(new FixedGuardNode(cond, TransferToInterpreter, None, false));
                        shouldPostException.setNext(check);
                        return check;
                    }
                }
            }
        }
        return afterExceptionLoaded;
    }

    private static final LocationIdentity JAVA_THREAD_SHOULD_POST_ON_EXCEPTIONS_FLAG_LOCATION = NamedLocationIdentity.mutable("JavaThread::_should_post_on_exceptions_flag");

    private static final Unsafe UNSAFE = initUnsafe();

    private static Unsafe initUnsafe() {
        try {
            // Fast path when we are trusted.
            return Unsafe.getUnsafe();
        } catch (SecurityException se) {
            // Slow path when we are not trusted.
            try {
                Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
                theUnsafe.setAccessible(true);
                return (Unsafe) theUnsafe.get(Unsafe.class);
            } catch (Exception e) {
                throw new RuntimeException("exception while trying to get Unsafe", e);
            }
        }
    }
}
