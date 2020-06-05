/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.vm.ci.services.Services.IS_IN_NATIVE_IMAGE;
import static org.graalvm.compiler.core.common.GraalOptions.GeneratePIC;
import static org.graalvm.compiler.hotspot.GraalHotSpotVMConfigAccess.JDK;
import static org.graalvm.compiler.hotspot.HotSpotBackend.BASE64_ENCODE_BLOCK;
import static org.graalvm.compiler.hotspot.HotSpotBackend.GHASH_PROCESS_BLOCKS;
import static org.graalvm.compiler.hotspot.meta.HotSpotAOTProfilingPlugin.Options.TieredAOT;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.JAVA_THREAD_THREAD_OBJECT_LOCATION;
import static org.graalvm.compiler.java.BytecodeParserOptions.InlineDuringParsing;

import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MutableCallSite;
import java.lang.invoke.VolatileCallSite;
import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.math.BigInteger;
import java.util.zip.CRC32;

import jdk.internal.vm.compiler.collections.Pair;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.TypeReference;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.HotSpotGraalRuntimeProvider;
import org.graalvm.compiler.hotspot.nodes.CurrentJavaThreadNode;
import org.graalvm.compiler.hotspot.replacements.AESCryptSubstitutions;
import org.graalvm.compiler.hotspot.replacements.ArraysSupportSubstitutions;
import org.graalvm.compiler.hotspot.replacements.BigIntegerSubstitutions;
import org.graalvm.compiler.hotspot.replacements.CRC32CSubstitutions;
import org.graalvm.compiler.hotspot.replacements.CRC32Substitutions;
import org.graalvm.compiler.hotspot.replacements.CallSiteTargetNode;
import org.graalvm.compiler.hotspot.replacements.CipherBlockChainingSubstitutions;
import org.graalvm.compiler.hotspot.replacements.ClassGetHubNode;
import org.graalvm.compiler.hotspot.replacements.CounterModeSubstitutions;
import org.graalvm.compiler.hotspot.replacements.DigestBaseSubstitutions;
import org.graalvm.compiler.hotspot.replacements.FastNotifyNode;
import org.graalvm.compiler.hotspot.replacements.HotSpotArraySubstitutions;
import org.graalvm.compiler.hotspot.replacements.HotSpotClassSubstitutions;
import org.graalvm.compiler.hotspot.replacements.HotSpotReflectionGetCallerClassNode;
import org.graalvm.compiler.hotspot.replacements.IdentityHashCodeNode;
import org.graalvm.compiler.hotspot.replacements.ObjectCloneNode;
import org.graalvm.compiler.hotspot.replacements.ReflectionSubstitutions;
import org.graalvm.compiler.hotspot.replacements.SHA2Substitutions;
import org.graalvm.compiler.hotspot.replacements.SHA5Substitutions;
import org.graalvm.compiler.hotspot.replacements.SHASubstitutions;
import org.graalvm.compiler.hotspot.replacements.StringUTF16Substitutions;
import org.graalvm.compiler.hotspot.replacements.ThreadSubstitutions;
import org.graalvm.compiler.hotspot.word.HotSpotWordTypes;
import org.graalvm.compiler.nodes.ComputeObjectAddressNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.calc.IntegerConvertNode;
import org.graalvm.compiler.nodes.calc.LeftShiftNode;
import org.graalvm.compiler.nodes.extended.ForeignCallNode;
import org.graalvm.compiler.nodes.graphbuilderconf.ForeignCallPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin.Receiver;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import org.graalvm.compiler.nodes.graphbuilderconf.NodeIntrinsicPluginFactory;
import org.graalvm.compiler.nodes.java.DynamicNewInstanceNode;
import org.graalvm.compiler.nodes.memory.OnHeapMemoryAccess.BarrierType;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.nodes.spi.Replacements;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.tiers.CompilerConfiguration;
import org.graalvm.compiler.replacements.InlineDuringParsingPlugin;
import org.graalvm.compiler.replacements.MethodHandlePlugin;
import org.graalvm.compiler.replacements.NodeIntrinsificationProvider;
import org.graalvm.compiler.replacements.ReplacementsImpl;
import org.graalvm.compiler.replacements.StandardGraphBuilderPlugins;
import org.graalvm.compiler.replacements.arraycopy.ArrayCopyNode;
import org.graalvm.compiler.replacements.nodes.MacroNode.MacroParams;
import org.graalvm.compiler.serviceprovider.GraalServices;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.compiler.word.WordOperationPlugin;
import org.graalvm.compiler.word.WordTypes;
import jdk.internal.vm.compiler.word.LocationIdentity;

import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.hotspot.VMIntrinsicMethod;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.services.Services;
import sun.misc.Unsafe;

/**
 * Defines the {@link Plugins} used when running on HotSpot.
 */
public class HotSpotGraphBuilderPlugins {

    /**
     * Creates a {@link Plugins} object that should be used when running on HotSpot.
     *
     * @param constantReflection
     * @param snippetReflection
     * @param foreignCalls
     * @param options
     * @param target
     */
    public static Plugins create(HotSpotGraalRuntimeProvider graalRuntime,
                    CompilerConfiguration compilerConfiguration,
                    GraalHotSpotVMConfig config,
                    HotSpotWordTypes wordTypes,
                    MetaAccessProvider metaAccess,
                    ConstantReflectionProvider constantReflection,
                    SnippetReflectionProvider snippetReflection,
                    ForeignCallsProvider foreignCalls,
                    ReplacementsImpl replacements,
                    OptionValues options,
                    TargetDescription target) {
        InvocationPlugins invocationPlugins = new HotSpotInvocationPlugins(graalRuntime, config, compilerConfiguration);

        Plugins plugins = new Plugins(invocationPlugins);
        NodeIntrinsificationProvider nodeIntrinsificationProvider = new NodeIntrinsificationProvider(metaAccess, snippetReflection, foreignCalls, wordTypes, target);
        if (!IS_IN_NATIVE_IMAGE) {
            HotSpotWordOperationPlugin wordOperationPlugin = new HotSpotWordOperationPlugin(snippetReflection, wordTypes);
            HotSpotNodePlugin nodePlugin = new HotSpotNodePlugin(wordOperationPlugin, config, wordTypes);

            plugins.appendTypePlugin(nodePlugin);
            plugins.appendNodePlugin(nodePlugin);
        }
        if (!GeneratePIC.getValue(options)) {
            plugins.appendNodePlugin(new MethodHandlePlugin(constantReflection.getMethodHandleAccess(), true));
        }
        plugins.appendInlineInvokePlugin(replacements);
        if (InlineDuringParsing.getValue(options)) {
            plugins.appendInlineInvokePlugin(new InlineDuringParsingPlugin());
        }

        if (GeneratePIC.getValue(options)) {
            plugins.setClassInitializationPlugin(new HotSpotAOTClassInitializationPlugin());
            if (TieredAOT.getValue(options)) {
                plugins.setProfilingPlugin(new HotSpotAOTProfilingPlugin());
            }
        } else {
            if (config.instanceKlassInitThreadOffset != -1) {
                plugins.setClassInitializationPlugin(new HotSpotJITClassInitializationPlugin());
            }
        }

        invocationPlugins.defer(new Runnable() {

            @Override
            public void run() {
                registerObjectPlugins(invocationPlugins, options, config, replacements);
                registerClassPlugins(plugins, config, replacements);
                registerSystemPlugins(invocationPlugins);
                registerThreadPlugins(invocationPlugins, metaAccess, wordTypes, config, replacements);
                if (!GeneratePIC.getValue(options)) {
                    registerCallSitePlugins(invocationPlugins);
                }
                registerReflectionPlugins(invocationPlugins, replacements);
                registerConstantPoolPlugins(invocationPlugins, wordTypes, config, replacements);
                registerAESPlugins(invocationPlugins, config, replacements);
                registerCRC32Plugins(invocationPlugins, config, replacements);
                registerCRC32CPlugins(invocationPlugins, config, replacements);
                registerBigIntegerPlugins(invocationPlugins, config, replacements);
                registerSHAPlugins(invocationPlugins, config, replacements);
                registerGHASHPlugins(invocationPlugins, config, metaAccess);
                registerCounterModePlugins(invocationPlugins, config, replacements);
                registerBase64Plugins(invocationPlugins, config, metaAccess);
                registerUnsafePlugins(invocationPlugins, config, replacements);
                StandardGraphBuilderPlugins.registerInvocationPlugins(metaAccess, snippetReflection, invocationPlugins, replacements, true, false, true);
                registerArrayPlugins(invocationPlugins, replacements);
                registerStringPlugins(invocationPlugins, replacements);
                registerArraysSupportPlugins(invocationPlugins, config, replacements);

                for (NodeIntrinsicPluginFactory factory : GraalServices.load(NodeIntrinsicPluginFactory.class)) {
                    factory.registerPlugins(invocationPlugins, nodeIntrinsificationProvider);
                }
            }
        });
        return plugins;
    }

    private static void registerObjectPlugins(InvocationPlugins plugins, OptionValues options, GraalHotSpotVMConfig config, Replacements replacements) {
        Registration r = new Registration(plugins, Object.class, replacements);
        if (!GeneratePIC.getValue(options)) {
            // FIXME: clone() requires speculation and requires a fix in here (to check that
            // b.getAssumptions() != null), and in ReplacementImpl.getSubstitution() where there is
            // an instantiation of IntrinsicGraphBuilder using a constructor that sets
            // AllowAssumptions to YES automatically. The former has to inherit the assumptions
            // settings from the root compile instead. So, for now, I'm disabling it for
            // GeneratePIC.
            r.register1("clone", Receiver.class, new InvocationPlugin() {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                    ValueNode object = receiver.get();
                    b.addPush(JavaKind.Object, new ObjectCloneNode(MacroParams.of(b, targetMethod, object)));
                    return true;
                }

                @Override
                public boolean inlineOnly() {
                    return true;
                }
            });
        }
        r.register1("hashCode", Receiver.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                ValueNode object = receiver.get();
                b.addPush(JavaKind.Int, new IdentityHashCodeNode(object, b.bci()));
                return true;
            }

            @Override
            public boolean inlineOnly() {
                return true;
            }
        });
        if (config.inlineNotify()) {
            r.register1("notify", Receiver.class, new InvocationPlugin() {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                    ValueNode object = receiver.get();
                    b.add(new FastNotifyNode(object, false, b.bci()));
                    return true;
                }

                @Override
                public boolean inlineOnly() {
                    return true;
                }
            });
        }
        if (config.inlineNotifyAll()) {
            r.register1("notifyAll", Receiver.class, new InvocationPlugin() {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                    ValueNode object = receiver.get();
                    b.add(new FastNotifyNode(object, true, b.bci()));
                    return true;
                }

                @Override
                public boolean inlineOnly() {
                    return true;
                }
            });
        }
    }

    private static void registerClassPlugins(Plugins plugins, GraalHotSpotVMConfig config, Replacements replacements) {
        Registration r = new Registration(plugins.getInvocationPlugins(), Class.class, replacements);

        r.registerMethodSubstitution(HotSpotClassSubstitutions.class, "getModifiers", Receiver.class);
        r.registerMethodSubstitution(HotSpotClassSubstitutions.class, "isInterface", Receiver.class);
        r.registerMethodSubstitution(HotSpotClassSubstitutions.class, "isArray", Receiver.class);
        r.registerMethodSubstitution(HotSpotClassSubstitutions.class, "isPrimitive", Receiver.class);
        r.registerMethodSubstitution(HotSpotClassSubstitutions.class, "getSuperclass", Receiver.class);

        if (config.jvmAccIsHiddenClass != 0) {
            r.registerMethodSubstitution(HotSpotClassSubstitutions.class, "isHidden", Receiver.class);
        }

        if (config.getFieldOffset("ArrayKlass::_component_mirror", Integer.class, "oop", Integer.MAX_VALUE, JDK <= 8) != Integer.MAX_VALUE) {
            r.registerMethodSubstitution(HotSpotClassSubstitutions.class, "getComponentType", Receiver.class);
        }
    }

    private static void registerCallSitePlugins(InvocationPlugins plugins) {
        InvocationPlugin plugin = new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                ValueNode callSite = receiver.get();
                ValueNode folded = CallSiteTargetNode.tryFold(GraphUtil.originalValue(callSite, true), b.getMetaAccess(), b.getAssumptions());
                if (folded != null) {
                    b.addPush(JavaKind.Object, folded);
                } else {
                    b.addPush(JavaKind.Object, new CallSiteTargetNode(MacroParams.of(b, targetMethod, callSite)));
                }
                return true;
            }

            @Override
            public boolean inlineOnly() {
                return true;
            }
        };
        plugins.register(plugin, ConstantCallSite.class, "getTarget", Receiver.class);
        plugins.register(plugin, MutableCallSite.class, "getTarget", Receiver.class);
        plugins.register(plugin, VolatileCallSite.class, "getTarget", Receiver.class);
    }

    private static void registerReflectionPlugins(InvocationPlugins plugins, Replacements replacements) {
        Registration r = new Registration(plugins, reflectionClass, replacements);
        r.register0("getCallerClass", new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.addPush(JavaKind.Object, new HotSpotReflectionGetCallerClassNode(MacroParams.of(b, targetMethod)));
                return true;
            }

            @Override
            public boolean inlineOnly() {
                return true;
            }
        });
        r.registerMethodSubstitution(ReflectionSubstitutions.class, "getClassAccessFlags", Class.class);
    }

    private static void registerUnsafePlugins(InvocationPlugins plugins, GraalHotSpotVMConfig config, Replacements replacements) {
        Registration r;
        if (JavaVersionUtil.JAVA_SPEC <= 8) {
            r = new Registration(plugins, Unsafe.class, replacements);
        } else {
            r = new Registration(plugins, "jdk.internal.misc.Unsafe", replacements);
        }
        String substituteMethodName = config.doingUnsafeAccessOffset != Integer.MAX_VALUE ? "copyMemoryGuarded" : "copyMemory";
        r.registerMethodSubstitution(HotSpotUnsafeSubstitutions.class, HotSpotUnsafeSubstitutions.copyMemoryName, substituteMethodName, Receiver.class, Object.class, long.class, Object.class,
                        long.class, long.class);

        r.register2("allocateInstance", Receiver.class, Class.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver unsafe, ValueNode clazz) {
                /* Emits a null-check for the otherwise unused receiver. */
                unsafe.get();
                /*
                 * Note that the provided clazz might not be initialized. The HotSpot lowering
                 * snippet for DynamicNewInstanceNode performs the necessary class initialization
                 * check. Such a DynamicNewInstanceNode is also never constant folded to a
                 * NewInstanceNode.
                 */
                b.addPush(JavaKind.Object, new DynamicNewInstanceNode(b.nullCheckedValue(clazz, DeoptimizationAction.None), true));
                return true;
            }
        });
    }

    private static final LocationIdentity INSTANCE_KLASS_CONSTANTS = NamedLocationIdentity.immutable("InstanceKlass::_constants");
    private static final LocationIdentity CONSTANT_POOL_LENGTH = NamedLocationIdentity.immutable("ConstantPool::_length");

    /**
     * Emits a node to get the metaspace {@code ConstantPool} pointer given the value of the
     * {@code constantPoolOop} field in a ConstantPool value.
     *
     * @param constantPoolOop value of the {@code constantPoolOop} field in a ConstantPool value
     * @return a node representing the metaspace {@code ConstantPool} pointer associated with
     *         {@code constantPoolOop}
     */
    private static ValueNode getMetaspaceConstantPool(GraphBuilderContext b, ValueNode constantPoolOop, WordTypes wordTypes, GraalHotSpotVMConfig config) {
        // ConstantPool.constantPoolOop is in fact the holder class.
        ValueNode value = b.nullCheckedValue(constantPoolOop, DeoptimizationAction.None);
        ValueNode klass = b.add(ClassGetHubNode.create(value, b.getMetaAccess(), b.getConstantReflection(), false));

        boolean notCompressible = false;
        AddressNode constantsAddress = b.add(new OffsetAddressNode(klass, b.add(ConstantNode.forLong(config.instanceKlassConstantsOffset))));
        return WordOperationPlugin.readOp(b, wordTypes.getWordKind(), constantsAddress, INSTANCE_KLASS_CONSTANTS, BarrierType.NONE, notCompressible);
    }

    /**
     * Emits a node representing an element in a metaspace {@code ConstantPool}.
     *
     * @param constantPoolOop value of the {@code constantPoolOop} field in a ConstantPool value
     */
    private static boolean readMetaspaceConstantPoolElement(GraphBuilderContext b, ValueNode constantPoolOop, ValueNode index, JavaKind elementKind, WordTypes wordTypes, GraalHotSpotVMConfig config) {
        ValueNode constants = getMetaspaceConstantPool(b, constantPoolOop, wordTypes, config);
        int shift = CodeUtil.log2(wordTypes.getWordKind().getByteCount());
        ValueNode scaledIndex = b.add(new LeftShiftNode(IntegerConvertNode.convert(index, StampFactory.forKind(JavaKind.Long), NodeView.DEFAULT), b.add(ConstantNode.forInt(shift))));
        ValueNode offset = b.add(new AddNode(scaledIndex, b.add(ConstantNode.forLong(config.constantPoolSize))));
        AddressNode elementAddress = b.add(new OffsetAddressNode(constants, offset));
        boolean notCompressible = false;
        ValueNode elementValue = WordOperationPlugin.readOp(b, elementKind, elementAddress, NamedLocationIdentity.getArrayLocation(elementKind), BarrierType.NONE, notCompressible);
        b.addPush(elementKind, elementValue);
        return true;
    }

    private static void registerConstantPoolPlugins(InvocationPlugins plugins, WordTypes wordTypes, GraalHotSpotVMConfig config, Replacements replacements) {
        Registration r = new Registration(plugins, constantPoolClass, replacements);

        r.register2("getSize0", Receiver.class, Object.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode constantPoolOop) {
                boolean notCompressible = false;
                ValueNode constants = getMetaspaceConstantPool(b, constantPoolOop, wordTypes, config);
                AddressNode lengthAddress = b.add(new OffsetAddressNode(constants, b.add(ConstantNode.forLong(config.constantPoolLengthOffset))));
                ValueNode length = WordOperationPlugin.readOp(b, JavaKind.Int, lengthAddress, CONSTANT_POOL_LENGTH, BarrierType.NONE, notCompressible);
                b.addPush(JavaKind.Int, length);
                return true;
            }
        });

        r.register3("getIntAt0", Receiver.class, Object.class, int.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode constantPoolOop, ValueNode index) {
                return readMetaspaceConstantPoolElement(b, constantPoolOop, index, JavaKind.Int, wordTypes, config);
            }
        });
        r.register3("getLongAt0", Receiver.class, Object.class, int.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode constantPoolOop, ValueNode index) {
                return readMetaspaceConstantPoolElement(b, constantPoolOop, index, JavaKind.Long, wordTypes, config);
            }
        });
        r.register3("getFloatAt0", Receiver.class, Object.class, int.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode constantPoolOop, ValueNode index) {
                return readMetaspaceConstantPoolElement(b, constantPoolOop, index, JavaKind.Float, wordTypes, config);
            }
        });
        r.register3("getDoubleAt0", Receiver.class, Object.class, int.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode constantPoolOop, ValueNode index) {
                return readMetaspaceConstantPoolElement(b, constantPoolOop, index, JavaKind.Double, wordTypes, config);
            }
        });
    }

    private static void registerSystemPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, System.class);
        r.register0("currentTimeMillis", new ForeignCallPlugin(HotSpotHostForeignCallsProvider.JAVA_TIME_MILLIS));
        r.register0("nanoTime", new ForeignCallPlugin(HotSpotHostForeignCallsProvider.JAVA_TIME_NANOS));
        r.register1("identityHashCode", Object.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode object) {
                b.addPush(JavaKind.Int, new IdentityHashCodeNode(object, b.bci()));
                return true;
            }

            @Override
            public boolean inlineOnly() {
                return true;
            }
        });
        r.register5("arraycopy", Object.class, int.class, Object.class, int.class, int.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode src, ValueNode srcPos, ValueNode dst, ValueNode dstPos, ValueNode length) {
                b.add(new ArrayCopyNode(b.bci(), src, srcPos, dst, dstPos, length));
                return true;
            }

            @Override
            public boolean inlineOnly() {
                return true;
            }
        });
    }

    private static void registerArrayPlugins(InvocationPlugins plugins, Replacements replacements) {
        Registration r = new Registration(plugins, Array.class, replacements);
        r.setAllowOverwrite(true);
        r.registerMethodSubstitution(HotSpotArraySubstitutions.class, "newInstance", Class.class, int.class);
    }

    private static void registerStringPlugins(InvocationPlugins plugins, Replacements replacements) {
        if (JavaVersionUtil.JAVA_SPEC > 8) {
            final Registration utf16r = new Registration(plugins, "java.lang.StringUTF16", replacements);
            utf16r.registerMethodSubstitution(StringUTF16Substitutions.class, "toBytes", char[].class, int.class, int.class);
            utf16r.registerMethodSubstitution(StringUTF16Substitutions.class, "getChars", byte[].class, int.class, int.class, char[].class, int.class);
        }
    }

    private static void registerThreadPlugins(InvocationPlugins plugins, MetaAccessProvider metaAccess, WordTypes wordTypes, GraalHotSpotVMConfig config, Replacements replacements) {
        Registration r = new Registration(plugins, Thread.class, replacements);
        r.register0("currentThread", new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                CurrentJavaThreadNode thread = b.add(new CurrentJavaThreadNode(wordTypes.getWordKind()));
                ValueNode offset = b.add(ConstantNode.forLong(config.threadObjectOffset));
                AddressNode address = b.add(new OffsetAddressNode(thread, offset));
                // JavaThread::_threadObj is never compressed
                ObjectStamp stamp = StampFactory.objectNonNull(TypeReference.create(b.getAssumptions(), metaAccess.lookupJavaType(Thread.class)));
                b.addPush(JavaKind.Object, new ReadNode(address, JAVA_THREAD_THREAD_OBJECT_LOCATION, stamp, BarrierType.NONE));
                return true;
            }
        });

        if (config.osThreadInterruptedOffset != Integer.MAX_VALUE) {
            r.registerMethodSubstitution(ThreadSubstitutions.class, "isInterrupted", Receiver.class, boolean.class);
        }

    }

    public static final String reflectionClass;
    public static final String constantPoolClass;

    static {
        if (JavaVersionUtil.JAVA_SPEC <= 8) {
            reflectionClass = "sun.reflect.Reflection";
            constantPoolClass = "sun.reflect.ConstantPool";
        } else {
            reflectionClass = "jdk.internal.reflect.Reflection";
            constantPoolClass = "jdk.internal.reflect.ConstantPool";
        }
    }

    public static String lookupIntrinsicName(GraalHotSpotVMConfig config, String className, String name1, String name2) {
        return selectIntrinsicName(config, className, name1, name2).getLeft();
    }

    /**
     * Returns a pair of Strings where the left one represents the matched intrinsic name and the
     * right one represents the mismatched intrinsic name.
     */
    public static Pair<String, String> selectIntrinsicName(GraalHotSpotVMConfig config, String className, String name1, String name2) {
        boolean foundName1 = false;
        boolean foundName2 = false;
        for (VMIntrinsicMethod intrinsic : config.getStore().getIntrinsics()) {
            if (className.equals(intrinsic.declaringClass)) {
                if (name1.equals(intrinsic.name)) {
                    foundName1 = true;
                } else if (name2.equals(intrinsic.name)) {
                    foundName2 = true;
                }
            }
        }
        if (foundName1 && !foundName2) {
            return Pair.create(name1, name2);
        } else if (foundName2 && !foundName1) {
            return Pair.create(name2, name1);
        }
        throw GraalError.shouldNotReachHere();
    }

    public static boolean isIntrinsicName(GraalHotSpotVMConfig config, String className, String name) {
        for (VMIntrinsicMethod intrinsic : config.getStore().getIntrinsics()) {
            if (className.equals(intrinsic.declaringClass)) {
                if (name.equals(intrinsic.name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void registerAESPlugins(InvocationPlugins plugins, GraalHotSpotVMConfig config, Replacements replacements) {
        if (config.useAESIntrinsics) {
            assert config.aescryptEncryptBlockStub != 0L;
            assert config.aescryptDecryptBlockStub != 0L;
            assert config.cipherBlockChainingEncryptAESCryptStub != 0L;
            assert config.cipherBlockChainingDecryptAESCryptStub != 0L;
            String arch = config.osArch;
            String decryptSuffix = arch.equals("sparc") ? "WithOriginalKey" : "";

            Registration r = new Registration(plugins, "com.sun.crypto.provider.CipherBlockChaining", replacements);

            Pair<String, String> cbcEncryptName = selectIntrinsicName(config, "com/sun/crypto/provider/CipherBlockChaining", "implEncrypt", "encrypt");
            registerAndCheckMismatch(r, CipherBlockChainingSubstitutions.class, cbcEncryptName, Receiver.class, byte[].class, int.class, int.class,
                            byte[].class, int.class);

            Pair<String, String> cbcDecryptName = selectIntrinsicName(config, "com/sun/crypto/provider/CipherBlockChaining", "implDecrypt", "decrypt");
            registerAndCheckMismatch(r, CipherBlockChainingSubstitutions.class, cbcDecryptName, cbcDecryptName.getLeft() + decryptSuffix, Receiver.class, byte[].class, int.class, int.class,
                            byte[].class, int.class);

            r = new Registration(plugins, "com.sun.crypto.provider.AESCrypt", replacements);

            Pair<String, String> aesEncryptName = selectIntrinsicName(config, "com/sun/crypto/provider/AESCrypt", "implEncryptBlock", "encryptBlock");
            registerAndCheckMismatch(r, AESCryptSubstitutions.class, aesEncryptName, Receiver.class, byte[].class, int.class, byte[].class, int.class);

            Pair<String, String> aesDecryptName = selectIntrinsicName(config, "com/sun/crypto/provider/AESCrypt", "implDecryptBlock", "decryptBlock");
            registerAndCheckMismatch(r, AESCryptSubstitutions.class, aesDecryptName, aesDecryptName.getLeft() + decryptSuffix, Receiver.class, byte[].class, int.class, byte[].class, int.class);
        }
    }

    private static void registerAndCheckMismatch(Registration r, Class<?> substitutionClass, Pair<String, String> intrinsicNames, Type... argumentTypes) {
        try {
            r.registerMethodSubstitution(substitutionClass, intrinsicNames.getLeft(), argumentTypes);
        } catch (NoSuchMethodError e) {
            throw new GraalError(e, "Found method named '%s' instead of '%s' in class '%s'. This is most likely because the JVMCI JDK in %s was built on an incompatible base JDK.",
                            intrinsicNames.getRight(), intrinsicNames.getLeft(), r.getDeclaringType().getTypeName(), Services.getSavedProperties().get("java.home"));
        }
    }

    private static void registerAndCheckMismatch(Registration r, Class<?> substitutionClass, Pair<String, String> intrinsicNames, String substituteName, Type... argumentTypes) {
        try {
            r.registerMethodSubstitution(substitutionClass, intrinsicNames.getLeft(), substituteName, argumentTypes);
        } catch (NoSuchMethodError e) {
            throw new GraalError(e, "Found method named '%s' instead of '%s' in class '%s'. This is most likely because the JVMCI JDK in %s was built on an incompatible base JDK.",
                            intrinsicNames.getRight(), intrinsicNames.getLeft(), r.getDeclaringType().getTypeName(), Services.getSavedProperties().get("java.home"));
        }
    }

    private static void registerBigIntegerPlugins(InvocationPlugins plugins, GraalHotSpotVMConfig config, Replacements replacements) {
        Registration r = new Registration(plugins, BigInteger.class, replacements);
        assert !config.useMultiplyToLenIntrinsic() || config.multiplyToLen != 0L;
        if (JavaVersionUtil.JAVA_SPEC <= 8) {
            r.registerConditionalMethodSubstitution(config.useMultiplyToLenIntrinsic(), BigIntegerSubstitutions.class, "multiplyToLen", "multiplyToLenStatic", int[].class, int.class, int[].class,
                            int.class, int[].class);
        } else {
            r.registerConditionalMethodSubstitution(config.useMultiplyToLenIntrinsic(), BigIntegerSubstitutions.class, "implMultiplyToLen", "multiplyToLenStatic", int[].class, int.class, int[].class,
                            int.class, int[].class);
        }
        r.registerConditionalMethodSubstitution(config.useMulAddIntrinsic(), BigIntegerSubstitutions.class, "implMulAdd", int[].class, int[].class, int.class, int.class, int.class);
        r.registerConditionalMethodSubstitution(config.useMontgomeryMultiplyIntrinsic(), BigIntegerSubstitutions.class, "implMontgomeryMultiply", int[].class, int[].class, int[].class, int.class,
                        long.class, int[].class);
        r.registerConditionalMethodSubstitution(config.useMontgomerySquareIntrinsic(), BigIntegerSubstitutions.class, "implMontgomerySquare", int[].class, int[].class, int.class, long.class,
                        int[].class);
        r.registerConditionalMethodSubstitution(config.useSquareToLenIntrinsic(), BigIntegerSubstitutions.class, "implSquareToLen", int[].class, int.class, int[].class, int.class);
    }

    private static void registerSHAPlugins(InvocationPlugins plugins, GraalHotSpotVMConfig config, Replacements replacements) {
        boolean useSha1 = config.useSHA1Intrinsics();
        boolean useSha256 = config.useSHA256Intrinsics();
        boolean useSha512 = config.useSHA512Intrinsics();

        if (isIntrinsicName(config, "sun/security/provider/DigestBase", "implCompressMultiBlock0") && (useSha1 || useSha256 || useSha512)) {
            Registration r = new Registration(plugins, "sun.security.provider.DigestBase", replacements);
            r.registerMethodSubstitution(DigestBaseSubstitutions.class, "implCompressMultiBlock0", Receiver.class, byte[].class, int.class, int.class);
        }

        Pair<String, String> implCompressName = selectIntrinsicName(config, "sun/security/provider/SHA", "implCompress", "implCompress0");
        if (useSha1) {
            assert config.sha1ImplCompress != 0L;
            Registration r = new Registration(plugins, "sun.security.provider.SHA", replacements);
            registerAndCheckMismatch(r, SHASubstitutions.class, implCompressName, "implCompress0", Receiver.class, byte[].class, int.class);
        }
        if (useSha256) {
            assert config.sha256ImplCompress != 0L;
            Registration r = new Registration(plugins, "sun.security.provider.SHA2", replacements);
            registerAndCheckMismatch(r, SHA2Substitutions.class, implCompressName, "implCompress0", Receiver.class, byte[].class, int.class);
        }
        if (useSha512) {
            assert config.sha512ImplCompress != 0L;
            Registration r = new Registration(plugins, "sun.security.provider.SHA5", replacements);
            registerAndCheckMismatch(r, SHA5Substitutions.class, implCompressName, "implCompress0", Receiver.class, byte[].class, int.class);
        }
    }

    private static void registerGHASHPlugins(InvocationPlugins plugins, GraalHotSpotVMConfig config, MetaAccessProvider metaAccess) {
        if (config.useGHASHIntrinsics()) {
            assert config.ghashProcessBlocks != 0L;
            Registration r = new Registration(plugins, "com.sun.crypto.provider.GHASH");
            r.register5("processBlocks",
                            byte[].class,
                            int.class,
                            int.class,
                            long[].class,
                            long[].class,
                            new InvocationPlugin() {
                                @Override
                                public boolean apply(GraphBuilderContext b,
                                                ResolvedJavaMethod targetMethod,
                                                Receiver receiver,
                                                ValueNode data,
                                                ValueNode inOffset,
                                                ValueNode blocks,
                                                ValueNode state,
                                                ValueNode hashSubkey) {
                                    int longArrayBaseOffset = metaAccess.getArrayBaseOffset(JavaKind.Long);
                                    int byteArrayBaseOffset = metaAccess.getArrayBaseOffset(JavaKind.Byte);
                                    ValueNode dataOffset = AddNode.create(ConstantNode.forInt(byteArrayBaseOffset), inOffset, NodeView.DEFAULT);
                                    ComputeObjectAddressNode dataAddress = b.add(new ComputeObjectAddressNode(data, dataOffset));
                                    ComputeObjectAddressNode stateAddress = b.add(new ComputeObjectAddressNode(state, ConstantNode.forInt(longArrayBaseOffset)));
                                    ComputeObjectAddressNode hashSubkeyAddress = b.add(new ComputeObjectAddressNode(hashSubkey, ConstantNode.forInt(longArrayBaseOffset)));
                                    b.add(new ForeignCallNode(GHASH_PROCESS_BLOCKS, stateAddress, hashSubkeyAddress, dataAddress, blocks));
                                    return true;
                                }
                            });
        }
    }

    private static void registerCounterModePlugins(InvocationPlugins plugins, GraalHotSpotVMConfig config, Replacements replacements) {
        if (isIntrinsicName(config, "com/sun/crypto/provider/CounterMode", "implCrypt")) {
            assert !config.useAESCTRIntrinsics || config.counterModeAESCrypt != 0L;
            Registration r = new Registration(plugins, "com.sun.crypto.provider.CounterMode", replacements);
            r.registerConditionalMethodSubstitution(config.useAESCTRIntrinsics, CounterModeSubstitutions.class, "implCrypt", Receiver.class, byte[].class, int.class, int.class, byte[].class,
                            int.class);
        }
    }

    private static void registerBase64Plugins(InvocationPlugins plugins, GraalHotSpotVMConfig config, MetaAccessProvider metaAccess) {
        if (config.useBase64Intrinsics()) {
            Registration r = new Registration(plugins, "java.util.Base64$Encoder");
            r.register7("encodeBlock",
                            Receiver.class,
                            byte[].class,
                            int.class,
                            int.class,
                            byte[].class,
                            int.class,
                            boolean.class,
                            new InvocationPlugin() {
                                @Override
                                public boolean apply(GraphBuilderContext b,
                                                ResolvedJavaMethod targetMethod,
                                                Receiver receiver,
                                                ValueNode src,
                                                ValueNode sp,
                                                ValueNode sl,
                                                ValueNode dst,
                                                ValueNode dp,
                                                ValueNode isURL) {
                                    int byteArrayBaseOffset = metaAccess.getArrayBaseOffset(JavaKind.Byte);
                                    ComputeObjectAddressNode srcAddress = b.add(new ComputeObjectAddressNode(src, ConstantNode.forInt(byteArrayBaseOffset)));
                                    ComputeObjectAddressNode dstAddress = b.add(new ComputeObjectAddressNode(dst, ConstantNode.forInt(byteArrayBaseOffset)));
                                    b.add(new ForeignCallNode(BASE64_ENCODE_BLOCK, srcAddress, sp, sl, dstAddress, dp, isURL));
                                    return true;
                                }
                            });
        }
    }

    private static void registerCRC32Plugins(InvocationPlugins plugins, GraalHotSpotVMConfig config, Replacements replacements) {
        Registration r = new Registration(plugins, CRC32.class, replacements);
        r.registerConditionalMethodSubstitution(config.useCRC32Intrinsics, CRC32Substitutions.class, "update", int.class, int.class);
        if (JavaVersionUtil.JAVA_SPEC <= 8) {
            r.registerConditionalMethodSubstitution(config.useCRC32Intrinsics, CRC32Substitutions.class, "updateBytes", int.class, byte[].class, int.class, int.class);
            r.registerConditionalMethodSubstitution(config.useCRC32Intrinsics, CRC32Substitutions.class, "updateByteBuffer", int.class, long.class, int.class, int.class);
        } else {
            r.registerConditionalMethodSubstitution(config.useCRC32Intrinsics, CRC32Substitutions.class, "updateBytes0", int.class, byte[].class, int.class, int.class);
            r.registerConditionalMethodSubstitution(config.useCRC32Intrinsics, CRC32Substitutions.class, "updateByteBuffer0", int.class, long.class, int.class, int.class);
        }
    }

    private static void registerCRC32CPlugins(InvocationPlugins plugins, GraalHotSpotVMConfig config, Replacements replacements) {
        if (JavaVersionUtil.JAVA_SPEC > 8) {
            Registration r = new Registration(plugins, "java.util.zip.CRC32C", replacements);
            r.registerConditionalMethodSubstitution(config.useCRC32CIntrinsics, CRC32CSubstitutions.class, "updateBytes", int.class, byte[].class, int.class, int.class);
            r.registerConditionalMethodSubstitution(config.useCRC32CIntrinsics, CRC32CSubstitutions.class, "updateDirectByteBuffer", int.class, long.class, int.class, int.class);
        }
    }

    private static void registerArraysSupportPlugins(InvocationPlugins plugins, GraalHotSpotVMConfig config, Replacements replacements) {
        if (JavaVersionUtil.JAVA_SPEC > 8) {
            Registration r = new Registration(plugins, "jdk.internal.util.ArraysSupport", replacements);
            r.registerConditionalMethodSubstitution(config.useVectorizedMismatchIntrinsic, ArraysSupportSubstitutions.class, "vectorizedMismatch", Object.class, long.class, Object.class, long.class,
                            int.class, int.class);
        }
    }
}
