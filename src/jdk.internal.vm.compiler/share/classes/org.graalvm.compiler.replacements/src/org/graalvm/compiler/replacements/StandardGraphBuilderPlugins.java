/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements;

import static jdk.vm.ci.code.MemoryBarriers.JMM_POST_VOLATILE_READ;
import static jdk.vm.ci.code.MemoryBarriers.JMM_POST_VOLATILE_WRITE;
import static jdk.vm.ci.code.MemoryBarriers.JMM_PRE_VOLATILE_READ;
import static jdk.vm.ci.code.MemoryBarriers.JMM_PRE_VOLATILE_WRITE;
import static jdk.vm.ci.code.MemoryBarriers.LOAD_LOAD;
import static jdk.vm.ci.code.MemoryBarriers.LOAD_STORE;
import static jdk.vm.ci.code.MemoryBarriers.STORE_LOAD;
import static jdk.vm.ci.code.MemoryBarriers.STORE_STORE;
import static org.graalvm.compiler.nodes.NamedLocationIdentity.OFF_HEAP_LOCATION;
import static org.graalvm.compiler.serviceprovider.JDK9Method.Java8OrEarlier;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.Arrays;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.bytecode.BytecodeProvider;
import org.graalvm.compiler.core.common.calc.Condition;
import org.graalvm.compiler.core.common.calc.UnsignedMath;
import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.TypeReference;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Edges;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeList;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.DeoptimizeNode;
import org.graalvm.compiler.nodes.FixedGuardNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.AbsNode;
import org.graalvm.compiler.nodes.calc.CompareNode;
import org.graalvm.compiler.nodes.calc.ConditionalNode;
import org.graalvm.compiler.nodes.calc.IntegerEqualsNode;
import org.graalvm.compiler.nodes.calc.NarrowNode;
import org.graalvm.compiler.nodes.calc.ReinterpretNode;
import org.graalvm.compiler.nodes.calc.RightShiftNode;
import org.graalvm.compiler.nodes.calc.SignExtendNode;
import org.graalvm.compiler.nodes.calc.SqrtNode;
import org.graalvm.compiler.nodes.calc.UnsignedDivNode;
import org.graalvm.compiler.nodes.calc.UnsignedRemNode;
import org.graalvm.compiler.nodes.calc.ZeroExtendNode;
import org.graalvm.compiler.nodes.debug.BindToRegisterNode;
import org.graalvm.compiler.nodes.debug.BlackholeNode;
import org.graalvm.compiler.nodes.debug.ControlFlowAnchorNode;
import org.graalvm.compiler.nodes.debug.OpaqueNode;
import org.graalvm.compiler.nodes.debug.SpillRegistersNode;
import org.graalvm.compiler.nodes.extended.BoxNode;
import org.graalvm.compiler.nodes.extended.BranchProbabilityNode;
import org.graalvm.compiler.nodes.extended.GetClassNode;
import org.graalvm.compiler.nodes.extended.MembarNode;
import org.graalvm.compiler.nodes.extended.RawLoadNode;
import org.graalvm.compiler.nodes.extended.RawStoreNode;
import org.graalvm.compiler.nodes.extended.UnboxNode;
import org.graalvm.compiler.nodes.extended.UnsafeMemoryLoadNode;
import org.graalvm.compiler.nodes.extended.UnsafeMemoryStoreNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin.Receiver;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import org.graalvm.compiler.nodes.java.ClassIsAssignableFromNode;
import org.graalvm.compiler.nodes.java.DynamicNewArrayNode;
import org.graalvm.compiler.nodes.java.DynamicNewInstanceNode;
import org.graalvm.compiler.nodes.java.InstanceOfDynamicNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.java.RegisterFinalizerNode;
import org.graalvm.compiler.nodes.java.UnsafeCompareAndSwapNode;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.nodes.virtual.EnsureVirtualizedNode;
import org.graalvm.compiler.replacements.nodes.ReverseBytesNode;
import org.graalvm.compiler.replacements.nodes.VirtualizableInvokeMacroNode;
import org.graalvm.compiler.replacements.nodes.arithmetic.IntegerAddExactNode;
import org.graalvm.compiler.replacements.nodes.arithmetic.IntegerMulExactNode;
import org.graalvm.compiler.replacements.nodes.arithmetic.IntegerSubExactNode;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import sun.misc.Unsafe;

/**
 * Provides non-runtime specific {@link InvocationPlugin}s.
 */
public class StandardGraphBuilderPlugins {

    public static void registerInvocationPlugins(MetaAccessProvider metaAccess, SnippetReflectionProvider snippetReflection, InvocationPlugins plugins, BytecodeProvider bytecodeProvider,
                    boolean allowDeoptimization) {
        registerObjectPlugins(plugins);
        registerClassPlugins(plugins);
        registerMathPlugins(plugins, allowDeoptimization);
        registerUnsignedMathPlugins(plugins);
        registerStringPlugins(plugins, bytecodeProvider, snippetReflection);
        registerCharacterPlugins(plugins);
        registerShortPlugins(plugins);
        registerIntegerLongPlugins(plugins, JavaKind.Int);
        registerIntegerLongPlugins(plugins, JavaKind.Long);
        registerFloatPlugins(plugins);
        registerDoublePlugins(plugins);
        registerArraysPlugins(plugins, bytecodeProvider);
        registerArrayPlugins(plugins, bytecodeProvider);
        registerUnsafePlugins(plugins, bytecodeProvider);
        registerEdgesPlugins(metaAccess, plugins);
        registerGraalDirectivesPlugins(plugins);
        registerBoxingPlugins(plugins);
        registerJMHBlackholePlugins(plugins, bytecodeProvider);
        registerJFRThrowablePlugins(plugins, bytecodeProvider);
        registerMethodHandleImplPlugins(plugins, snippetReflection, bytecodeProvider);
        registerJcovCollectPlugins(plugins, bytecodeProvider);
    }

    private static final Field STRING_VALUE_FIELD;

    static {
        try {
            STRING_VALUE_FIELD = String.class.getDeclaredField("value");
        } catch (NoSuchFieldException e) {
            throw new GraalError(e);
        }
    }

    private static void registerStringPlugins(InvocationPlugins plugins, BytecodeProvider bytecodeProvider, SnippetReflectionProvider snippetReflection) {
        final Registration r = new Registration(plugins, String.class, bytecodeProvider);
        r.register1("hashCode", Receiver.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                if (receiver.isConstant()) {
                    String s = snippetReflection.asObject(String.class, (JavaConstant) receiver.get().asConstant());
                    b.addPush(JavaKind.Int, b.add(ConstantNode.forInt(s.hashCode())));
                    return true;
                }
                return false;
            }
        });

        if (Java8OrEarlier) {
            r.registerMethodSubstitution(StringSubstitutions.class, "equals", Receiver.class, Object.class);
            r.register7("indexOf", char[].class, int.class, int.class, char[].class, int.class, int.class, int.class, new StringIndexOfConstantPlugin());

            Registration sr = new Registration(plugins, StringSubstitutions.class);
            sr.register1("getValue", String.class, new InvocationPlugin() {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                    ResolvedJavaField field = b.getMetaAccess().lookupJavaField(STRING_VALUE_FIELD);
                    b.addPush(JavaKind.Object, LoadFieldNode.create(b.getConstantFieldProvider(), b.getConstantReflection(), b.getMetaAccess(),
                                    b.getOptions(), b.getAssumptions(), value, field, false, false));
                    return true;
                }
            });
        }
    }

    private static void registerArraysPlugins(InvocationPlugins plugins, BytecodeProvider bytecodeProvider) {
        Registration r = new Registration(plugins, Arrays.class, bytecodeProvider);
        r.registerMethodSubstitution(ArraysSubstitutions.class, "equals", boolean[].class, boolean[].class);
        r.registerMethodSubstitution(ArraysSubstitutions.class, "equals", byte[].class, byte[].class);
        r.registerMethodSubstitution(ArraysSubstitutions.class, "equals", short[].class, short[].class);
        r.registerMethodSubstitution(ArraysSubstitutions.class, "equals", char[].class, char[].class);
        r.registerMethodSubstitution(ArraysSubstitutions.class, "equals", int[].class, int[].class);
        r.registerMethodSubstitution(ArraysSubstitutions.class, "equals", long[].class, long[].class);
    }

    private static void registerArrayPlugins(InvocationPlugins plugins, BytecodeProvider bytecodeProvider) {
        Registration r = new Registration(plugins, Array.class, bytecodeProvider);
        r.register2("newInstance", Class.class, int.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver unused, ValueNode componentType, ValueNode length) {
                b.addPush(JavaKind.Object, new DynamicNewArrayNode(componentType, length, true));
                return true;
            }
        });
        r.registerMethodSubstitution(ArraySubstitutions.class, "getLength", Object.class);
    }

    private static void registerUnsafePlugins(InvocationPlugins plugins, BytecodeProvider bytecodeProvider) {
        Registration r;
        if (Java8OrEarlier) {
            r = new Registration(plugins, Unsafe.class);
        } else {
            r = new Registration(plugins, "jdk.internal.misc.Unsafe", bytecodeProvider);
        }

        for (JavaKind kind : JavaKind.values()) {
            if ((kind.isPrimitive() && kind != JavaKind.Void) || kind == JavaKind.Object) {
                Class<?> javaClass = kind == JavaKind.Object ? Object.class : kind.toJavaClass();
                String kindName = kind.name();
                String getName = "get" + kindName;
                String putName = "put" + kindName;
                // Object-based accesses
                r.register3(getName, Receiver.class, Object.class, long.class, new UnsafeGetPlugin(kind, false));
                r.register4(putName, Receiver.class, Object.class, long.class, javaClass, new UnsafePutPlugin(kind, false));
                // Volatile object-based accesses
                r.register3(getName + "Volatile", Receiver.class, Object.class, long.class, new UnsafeGetPlugin(kind, true));
                r.register4(putName + "Volatile", Receiver.class, Object.class, long.class, javaClass, new UnsafePutPlugin(kind, true));
                // Ordered object-based accesses
                if (Java8OrEarlier) {
                    if (kind == JavaKind.Int || kind == JavaKind.Long || kind == JavaKind.Object) {
                        r.register4("putOrdered" + kindName, Receiver.class, Object.class, long.class, javaClass, new UnsafePutPlugin(kind, true));
                    }
                } else {
                    r.register4("put" + kindName + "Release", Receiver.class, Object.class, long.class, javaClass, new UnsafePutPlugin(kind, true));
                }
                if (kind != JavaKind.Boolean && kind != JavaKind.Object) {
                    // Raw accesses to memory addresses
                    r.register2(getName, Receiver.class, long.class, new UnsafeGetPlugin(kind, false));
                    r.register3(putName, Receiver.class, long.class, kind.toJavaClass(), new UnsafePutPlugin(kind, false));
                }
            }
        }

        // Accesses to native memory addresses.
        r.register2("getAddress", Receiver.class, long.class, new UnsafeGetPlugin(JavaKind.Long, false));
        r.register3("putAddress", Receiver.class, long.class, long.class, new UnsafePutPlugin(JavaKind.Long, false));

        for (JavaKind kind : new JavaKind[]{JavaKind.Int, JavaKind.Long, JavaKind.Object}) {
            Class<?> javaClass = kind == JavaKind.Object ? Object.class : kind.toJavaClass();
            String casName;
            if (Java8OrEarlier) {
                casName = "compareAndSwap";
            } else {
                casName = "compareAndSet";
            }
            r.register5(casName + kind.name(), Receiver.class, Object.class, long.class, javaClass, javaClass, new InvocationPlugin() {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver unsafe, ValueNode object, ValueNode offset, ValueNode expected, ValueNode x) {
                    // Emits a null-check for the otherwise unused receiver
                    unsafe.get();
                    b.addPush(JavaKind.Int, new UnsafeCompareAndSwapNode(object, offset, expected, x, kind, LocationIdentity.any()));
                    b.getGraph().markUnsafeAccess();
                    return true;
                }
            });
        }

        r.register2("allocateInstance", Receiver.class, Class.class, new InvocationPlugin() {

            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver unsafe, ValueNode clazz) {
                // Emits a null-check for the otherwise unused receiver
                unsafe.get();
                b.addPush(JavaKind.Object, new DynamicNewInstanceNode(b.nullCheckedValue(clazz, DeoptimizationAction.None), true));
                return true;
            }

        });

        r.register1("loadFence", Receiver.class, new UnsafeFencePlugin(LOAD_LOAD | LOAD_STORE));
        r.register1("storeFence", Receiver.class, new UnsafeFencePlugin(STORE_STORE | LOAD_STORE));
        r.register1("fullFence", Receiver.class, new UnsafeFencePlugin(LOAD_LOAD | STORE_STORE | LOAD_STORE | STORE_LOAD));
    }

    private static void registerIntegerLongPlugins(InvocationPlugins plugins, JavaKind kind) {
        Class<?> declaringClass = kind.toBoxedJavaClass();
        Class<?> type = kind.toJavaClass();
        Registration r = new Registration(plugins, declaringClass);
        r.register1("reverseBytes", type, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(new ReverseBytesNode(value).canonical(null)));
                return true;
            }
        });
        r.register2("divideUnsigned", type, type, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode dividend, ValueNode divisor) {
                b.push(kind, b.append(UnsignedDivNode.create(dividend, divisor, NodeView.DEFAULT)));
                return true;
            }
        });
        r.register2("remainderUnsigned", type, type, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode dividend, ValueNode divisor) {
                b.push(kind, b.append(UnsignedRemNode.create(dividend, divisor, NodeView.DEFAULT)));
                return true;
            }
        });
    }

    private static void registerCharacterPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, Character.class);
        r.register1("reverseBytes", char.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                // return (char) (Integer.reverse(i) >> 16);
                ReverseBytesNode reverse = b.add(new ReverseBytesNode(value));
                RightShiftNode rightShift = b.add(new RightShiftNode(reverse, b.add(ConstantNode.forInt(16))));
                ZeroExtendNode charCast = b.add(new ZeroExtendNode(b.add(new NarrowNode(rightShift, 16)), 32));
                b.push(JavaKind.Char, b.append(charCast.canonical(null)));
                return true;
            }
        });
    }

    private static void registerShortPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, Short.class);
        r.register1("reverseBytes", short.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                // return (short) (Integer.reverse(i) >> 16);
                ReverseBytesNode reverse = b.add(new ReverseBytesNode(value));
                RightShiftNode rightShift = b.add(new RightShiftNode(reverse, b.add(ConstantNode.forInt(16))));
                SignExtendNode charCast = b.add(new SignExtendNode(b.add(new NarrowNode(rightShift, 16)), 32));
                b.push(JavaKind.Short, b.append(charCast.canonical(null)));
                return true;
            }
        });
    }

    private static void registerFloatPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, Float.class);
        r.register1("floatToRawIntBits", float.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Int, b.append(ReinterpretNode.create(JavaKind.Int, value, NodeView.DEFAULT)));
                return true;
            }
        });
        r.register1("intBitsToFloat", int.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Float, b.append(ReinterpretNode.create(JavaKind.Float, value, NodeView.DEFAULT)));
                return true;
            }
        });
    }

    private static void registerDoublePlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, Double.class);
        r.register1("doubleToRawLongBits", double.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Long, b.append(ReinterpretNode.create(JavaKind.Long, value, NodeView.DEFAULT)));
                return true;
            }
        });
        r.register1("longBitsToDouble", long.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Double, b.append(ReinterpretNode.create(JavaKind.Double, value, NodeView.DEFAULT)));
                return true;
            }
        });
    }

    private static void registerMathPlugins(InvocationPlugins plugins, boolean allowDeoptimization) {
        Registration r = new Registration(plugins, Math.class);
        if (allowDeoptimization) {
            for (JavaKind kind : new JavaKind[]{JavaKind.Int, JavaKind.Long}) {
                Class<?> type = kind.toJavaClass();
                r.register2("addExact", type, type, new InvocationPlugin() {
                    @Override
                    public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                        b.addPush(kind, new IntegerAddExactNode(x, y));
                        return true;
                    }
                });
                r.register2("subtractExact", type, type, new InvocationPlugin() {
                    @Override
                    public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                        b.addPush(kind, new IntegerSubExactNode(x, y));
                        return true;
                    }
                });
                r.register2("multiplyExact", type, type, new InvocationPlugin() {
                    @Override
                    public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                        b.addPush(kind, new IntegerMulExactNode(x, y));
                        return true;
                    }
                });
            }
        }
        r.register1("abs", Float.TYPE, new InvocationPlugin() {

            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Float, b.append(new AbsNode(value).canonical(null)));
                return true;
            }
        });
        r.register1("abs", Double.TYPE, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Double, b.append(new AbsNode(value).canonical(null)));
                return true;
            }
        });
        r.register1("sqrt", Double.TYPE, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Double, b.append(SqrtNode.create(value, NodeView.DEFAULT)));
                return true;
            }
        });
    }

    public static final class StringIndexOfConstantPlugin implements InvocationPlugin {
        @Override
        public boolean inlineOnly() {
            return true;
        }

        @Override
        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, InvocationPlugin.Receiver receiver, ValueNode source, ValueNode sourceOffset, ValueNode sourceCount,
                        ValueNode target, ValueNode targetOffset, ValueNode targetCount, ValueNode origFromIndex) {
            if (target.isConstant()) {
                b.addPush(JavaKind.Int, new StringIndexOfNode(b.getInvokeKind(), targetMethod, b.bci(), b.getInvokeReturnStamp(b.getAssumptions()), source, sourceOffset, sourceCount,
                                target, targetOffset, targetCount, origFromIndex));
                return true;
            }
            return false;
        }
    }

    public static class UnsignedMathPlugin implements InvocationPlugin {
        private final Condition condition;

        public UnsignedMathPlugin(Condition condition) {
            this.condition = condition;
        }

        @Override
        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
            // the mirroring and negation operations get the condition into canonical form
            boolean mirror = condition.canonicalMirror();
            boolean negate = condition.canonicalNegate();
            StructuredGraph graph = b.getGraph();

            ValueNode lhs = mirror ? y : x;
            ValueNode rhs = mirror ? x : y;

            ValueNode trueValue = ConstantNode.forBoolean(!negate, graph);
            ValueNode falseValue = ConstantNode.forBoolean(negate, graph);

            Condition cond = mirror ? condition.mirror() : condition;
            if (negate) {
                cond = cond.negate();
            }

            LogicNode compare = CompareNode.createCompareNode(graph, b.getConstantReflection(), b.getMetaAccess(), b.getOptions(), null, cond, lhs, rhs, NodeView.DEFAULT);
            b.addPush(JavaKind.Boolean, new ConditionalNode(compare, trueValue, falseValue));
            return true;
        }
    }

    private static void registerUnsignedMathPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, UnsignedMath.class);
        r.register2("aboveThan", int.class, int.class, new UnsignedMathPlugin(Condition.AT));
        r.register2("aboveThan", long.class, long.class, new UnsignedMathPlugin(Condition.AT));
        r.register2("belowThan", int.class, int.class, new UnsignedMathPlugin(Condition.BT));
        r.register2("belowThan", long.class, long.class, new UnsignedMathPlugin(Condition.BT));
        r.register2("aboveOrEqual", int.class, int.class, new UnsignedMathPlugin(Condition.AE));
        r.register2("aboveOrEqual", long.class, long.class, new UnsignedMathPlugin(Condition.AE));
        r.register2("belowOrEqual", int.class, int.class, new UnsignedMathPlugin(Condition.BE));
        r.register2("belowOrEqual", long.class, long.class, new UnsignedMathPlugin(Condition.BE));
    }

    protected static void registerBoxingPlugins(InvocationPlugins plugins) {
        for (JavaKind kind : JavaKind.values()) {
            if (kind.isPrimitive() && kind != JavaKind.Void) {
                new BoxPlugin(kind).register(plugins);
                new UnboxPlugin(kind).register(plugins);
            }
        }
    }

    private static void registerObjectPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, Object.class);
        r.register1("<init>", Receiver.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                /*
                 * Object.<init> is a common instrumentation point so only perform this rewrite if
                 * the current definition is the normal empty method with a single return bytecode.
                 * The finalizer registration will instead be performed by the BytecodeParser.
                 */
                if (targetMethod.getCodeSize() == 1) {
                    ValueNode object = receiver.get();
                    if (RegisterFinalizerNode.mayHaveFinalizer(object, b.getAssumptions())) {
                        b.add(new RegisterFinalizerNode(object));
                    }
                    return true;
                }
                return false;
            }
        });
        r.register1("getClass", Receiver.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                ValueNode object = receiver.get();
                ValueNode folded = GetClassNode.tryFold(b.getMetaAccess(), b.getConstantReflection(), NodeView.DEFAULT, GraphUtil.originalValue(object));
                if (folded != null) {
                    b.addPush(JavaKind.Object, folded);
                } else {
                    Stamp stamp = StampFactory.objectNonNull(TypeReference.createTrusted(b.getAssumptions(), b.getMetaAccess().lookupJavaType(Class.class)));
                    b.addPush(JavaKind.Object, new GetClassNode(stamp, object));
                }
                return true;
            }
        });
    }

    private static void registerClassPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, Class.class);
        r.register2("isInstance", Receiver.class, Object.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver type, ValueNode object) {
                LogicNode condition = b.append(InstanceOfDynamicNode.create(b.getAssumptions(), b.getConstantReflection(), type.get(), object, false));
                b.push(JavaKind.Boolean, b.append(new ConditionalNode(condition).canonical(null)));
                return true;
            }
        });
        r.register2("isAssignableFrom", Receiver.class, Class.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver type, ValueNode otherType) {
                ClassIsAssignableFromNode condition = b.append(new ClassIsAssignableFromNode(type.get(), otherType));
                b.push(JavaKind.Boolean, b.append(new ConditionalNode(condition).canonical(null)));
                return true;
            }
        });
    }

    /**
     * Substitutions for improving the performance of some critical methods in {@link Edges}. These
     * substitutions improve the performance by forcing the relevant methods to be inlined
     * (intrinsification being a special form of inlining) and removing a checked cast.
     */
    private static void registerEdgesPlugins(MetaAccessProvider metaAccess, InvocationPlugins plugins) {
        Registration r = new Registration(plugins, Edges.class);
        for (Class<?> c : new Class<?>[]{Node.class, NodeList.class}) {
            r.register2("get" + c.getSimpleName() + "Unsafe", Node.class, long.class, new InvocationPlugin() {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode node, ValueNode offset) {
                    ObjectStamp stamp = StampFactory.object(TypeReference.createTrusted(b.getAssumptions(), metaAccess.lookupJavaType(c)));
                    RawLoadNode value = b.add(new RawLoadNode(stamp, node, offset, LocationIdentity.any(), JavaKind.Object));
                    b.addPush(JavaKind.Object, value);
                    return true;
                }
            });
            r.register3("put" + c.getSimpleName() + "Unsafe", Node.class, long.class, c, new InvocationPlugin() {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode node, ValueNode offset, ValueNode value) {
                    b.add(new RawStoreNode(node, offset, value, JavaKind.Object, LocationIdentity.any()));
                    return true;
                }
            });
        }
    }

    public static class BoxPlugin implements InvocationPlugin {

        private final JavaKind kind;

        BoxPlugin(JavaKind kind) {
            this.kind = kind;
        }

        @Override
        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
            if (b.parsingIntrinsic()) {
                ResolvedJavaMethod rootMethod = b.getGraph().method();
                if (b.getMetaAccess().lookupJavaType(BoxingSnippets.class).isAssignableFrom(rootMethod.getDeclaringClass())) {
                    // Disable invocation plugins for boxing snippets so that the
                    // original JDK methods are inlined
                    return false;
                }
            }
            ResolvedJavaType resultType = b.getMetaAccess().lookupJavaType(kind.toBoxedJavaClass());
            b.addPush(JavaKind.Object, new BoxNode(value, resultType, kind));
            return true;
        }

        void register(InvocationPlugins plugins) {
            plugins.register(this, kind.toBoxedJavaClass(), "valueOf", kind.toJavaClass());
        }
    }

    public static class UnboxPlugin implements InvocationPlugin {

        private final JavaKind kind;

        UnboxPlugin(JavaKind kind) {
            this.kind = kind;
        }

        @Override
        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
            if (b.parsingIntrinsic()) {
                ResolvedJavaMethod rootMethod = b.getGraph().method();
                if (b.getMetaAccess().lookupJavaType(BoxingSnippets.class).isAssignableFrom(rootMethod.getDeclaringClass())) {
                    // Disable invocation plugins for unboxing snippets so that the
                    // original JDK methods are inlined
                    return false;
                }
            }
            ValueNode valueNode = UnboxNode.create(b.getMetaAccess(), b.getConstantReflection(), receiver.get(), kind);
            b.addPush(kind, valueNode);
            return true;
        }

        void register(InvocationPlugins plugins) {
            String name = kind.toJavaClass().getSimpleName() + "Value";
            plugins.register(this, kind.toBoxedJavaClass(), name, Receiver.class);
        }
    }

    public static class UnsafeGetPlugin implements InvocationPlugin {

        private final JavaKind returnKind;
        private final boolean isVolatile;

        public UnsafeGetPlugin(JavaKind returnKind, boolean isVolatile) {
            this.returnKind = returnKind;
            this.isVolatile = isVolatile;
        }

        @Override
        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver unsafe, ValueNode address) {
            // Emits a null-check for the otherwise unused receiver
            unsafe.get();
            b.addPush(returnKind, new UnsafeMemoryLoadNode(address, returnKind, OFF_HEAP_LOCATION));
            b.getGraph().markUnsafeAccess();
            return true;
        }

        @Override
        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver unsafe, ValueNode object, ValueNode offset) {
            // Emits a null-check for the otherwise unused receiver
            unsafe.get();
            if (isVolatile) {
                b.add(new MembarNode(JMM_PRE_VOLATILE_READ));
            }
            LocationIdentity locationIdentity = object.isNullConstant() ? OFF_HEAP_LOCATION : LocationIdentity.any();
            b.addPush(returnKind, new RawLoadNode(object, offset, returnKind, locationIdentity));
            if (isVolatile) {
                b.add(new MembarNode(JMM_POST_VOLATILE_READ));
            }
            b.getGraph().markUnsafeAccess();
            return true;
        }
    }

    public static class UnsafePutPlugin implements InvocationPlugin {

        private final JavaKind kind;
        private final boolean isVolatile;

        public UnsafePutPlugin(JavaKind kind, boolean isVolatile) {
            this.kind = kind;
            this.isVolatile = isVolatile;
        }

        @Override
        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver unsafe, ValueNode address, ValueNode value) {
            // Emits a null-check for the otherwise unused receiver
            unsafe.get();
            b.add(new UnsafeMemoryStoreNode(address, value, kind, OFF_HEAP_LOCATION));
            b.getGraph().markUnsafeAccess();
            return true;
        }

        @Override
        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver unsafe, ValueNode object, ValueNode offset, ValueNode value) {
            // Emits a null-check for the otherwise unused receiver
            unsafe.get();
            if (isVolatile) {
                b.add(new MembarNode(JMM_PRE_VOLATILE_WRITE));
            }
            LocationIdentity locationIdentity = object.isNullConstant() ? OFF_HEAP_LOCATION : LocationIdentity.any();
            b.add(new RawStoreNode(object, offset, value, kind, locationIdentity));
            if (isVolatile) {
                b.add(new MembarNode(JMM_POST_VOLATILE_WRITE));
            }
            b.getGraph().markUnsafeAccess();
            return true;
        }
    }

    public static class UnsafeFencePlugin implements InvocationPlugin {

        private final int barriers;

        public UnsafeFencePlugin(int barriers) {
            this.barriers = barriers;
        }

        @Override
        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver unsafe) {
            // Emits a null-check for the otherwise unused receiver
            unsafe.get();
            b.add(new MembarNode(barriers));
            return true;
        }
    }

    private static void registerGraalDirectivesPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, GraalDirectives.class);
        r.register0("deoptimize", new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.add(new DeoptimizeNode(DeoptimizationAction.None, DeoptimizationReason.TransferToInterpreter));
                return true;
            }
        });

        r.register0("deoptimizeAndInvalidate", new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.add(new DeoptimizeNode(DeoptimizationAction.InvalidateReprofile, DeoptimizationReason.TransferToInterpreter));
                return true;
            }
        });

        r.register0("inCompiledCode", new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.addPush(JavaKind.Boolean, ConstantNode.forBoolean(true));
                return true;
            }
        });

        r.register0("controlFlowAnchor", new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.add(new ControlFlowAnchorNode());
                return true;
            }
        });

        r.register2("injectBranchProbability", double.class, boolean.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode probability, ValueNode condition) {
                b.addPush(JavaKind.Boolean, new BranchProbabilityNode(probability, condition));
                return true;
            }
        });

        InvocationPlugin blackholePlugin = new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.add(new BlackholeNode(value));
                return true;
            }
        };

        InvocationPlugin bindToRegisterPlugin = new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.add(new BindToRegisterNode(value));
                return true;
            }
        };
        for (JavaKind kind : JavaKind.values()) {
            if ((kind.isPrimitive() && kind != JavaKind.Void) || kind == JavaKind.Object) {
                Class<?> javaClass = kind == JavaKind.Object ? Object.class : kind.toJavaClass();
                r.register1("blackhole", javaClass, blackholePlugin);
                r.register1("bindToRegister", javaClass, bindToRegisterPlugin);

                r.register1("opaque", javaClass, new InvocationPlugin() {
                    @Override
                    public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                        b.addPush(kind, new OpaqueNode(value));
                        return true;
                    }
                });
            }
        }

        InvocationPlugin spillPlugin = new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.add(new SpillRegistersNode());
                return true;
            }
        };
        r.register0("spillRegisters", spillPlugin);

        r.register1("guardingNonNull", Object.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.addPush(value.getStackKind(), b.nullCheckedValue(value));
                return true;
            }
        });

        r.register1("ensureVirtualized", Object.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode object) {
                b.add(new EnsureVirtualizedNode(object, false));
                return true;
            }
        });
        r.register1("ensureVirtualizedHere", Object.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode object) {
                b.add(new EnsureVirtualizedNode(object, true));
                return true;
            }
        });
    }

    private static void registerJMHBlackholePlugins(InvocationPlugins plugins, BytecodeProvider bytecodeProvider) {
        InvocationPlugin blackholePlugin = new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver blackhole, ValueNode value) {
                blackhole.get();
                b.add(new BlackholeNode(value));
                return true;
            }
        };
        String[] names = {"org.openjdk.jmh.infra.Blackhole", "org.openjdk.jmh.logic.BlackHole"};
        for (String name : names) {
            Registration r = new Registration(plugins, name, bytecodeProvider);
            for (JavaKind kind : JavaKind.values()) {
                if ((kind.isPrimitive() && kind != JavaKind.Void) || kind == JavaKind.Object) {
                    Class<?> javaClass = kind == JavaKind.Object ? Object.class : kind.toJavaClass();
                    r.registerOptional2("consume", Receiver.class, javaClass, blackholePlugin);
                }
            }
            r.registerOptional2("consume", Receiver.class, Object[].class, blackholePlugin);
        }
    }

    private static void registerJFRThrowablePlugins(InvocationPlugins plugins, BytecodeProvider bytecodeProvider) {
        Registration r = new Registration(plugins, "oracle.jrockit.jfr.jdkevents.ThrowableTracer", bytecodeProvider);
        r.register2("traceThrowable", Throwable.class, String.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode throwable, ValueNode message) {
                b.add(new VirtualizableInvokeMacroNode(b.getInvokeKind(), targetMethod, b.bci(), b.getInvokeReturnStamp(b.getAssumptions()), throwable, message));
                return true;
            }

            @Override
            public boolean inlineOnly() {
                return true;
            }
        });
    }

    private static void registerMethodHandleImplPlugins(InvocationPlugins plugins, SnippetReflectionProvider snippetReflection, BytecodeProvider bytecodeProvider) {
        Registration r = new Registration(plugins, "java.lang.invoke.MethodHandleImpl", bytecodeProvider);
        r.register2("profileBoolean", boolean.class, int[].class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode result, ValueNode counters) {
                if (result.isConstant()) {
                    b.push(JavaKind.Boolean, result);
                    return true;
                }
                if (counters.isConstant()) {
                    ValueNode newResult = result;
                    int[] ctrs = snippetReflection.asObject(int[].class, (JavaConstant) counters.asConstant());
                    if (ctrs != null && ctrs.length == 2) {
                        int falseCount = ctrs[0];
                        int trueCount = ctrs[1];
                        int totalCount = trueCount + falseCount;

                        if (totalCount == 0) {
                            b.add(new DeoptimizeNode(DeoptimizationAction.InvalidateReprofile, DeoptimizationReason.TransferToInterpreter));
                        } else if (falseCount == 0 || trueCount == 0) {
                            boolean expected = falseCount == 0;
                            LogicNode condition = b.addWithInputs(
                                            IntegerEqualsNode.create(b.getConstantReflection(), b.getMetaAccess(), b.getOptions(), null, result, b.add(ConstantNode.forBoolean(!expected)),
                                                            NodeView.DEFAULT));
                            b.append(new FixedGuardNode(condition, DeoptimizationReason.UnreachedCode, DeoptimizationAction.InvalidateReprofile, true));
                            newResult = b.add(ConstantNode.forBoolean(expected));
                        } else {
                            // We cannot use BranchProbabilityNode here since there's no guarantee
                            // the result of MethodHandleImpl.profileBoolean() is used as the
                            // test in an `if` statement (as required by BranchProbabilityNode).
                        }
                    }
                    b.addPush(JavaKind.Boolean, newResult);
                    return true;
                }
                return false;
            }
        });
    }

    /**
     * Registers a plugin to ignore {@code com.sun.tdk.jcov.runtime.Collect.hit} within an
     * intrinsic.
     */
    private static void registerJcovCollectPlugins(InvocationPlugins plugins, BytecodeProvider bytecodeProvider) {
        Registration r = new Registration(plugins, "com.sun.tdk.jcov.runtime.Collect", bytecodeProvider);
        r.register1("hit", int.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode object) {
                if (b.parsingIntrinsic()) {
                    return true;
                }
                return false;
            }
        });
    }
}
