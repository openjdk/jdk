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

import static org.graalvm.compiler.core.common.GraalOptions.GeneratePIC;
import static org.graalvm.compiler.hotspot.meta.HotSpotAOTProfilingPlugin.Options.TieredAOT;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.JAVA_THREAD_THREAD_OBJECT_LOCATION;
import static org.graalvm.compiler.java.BytecodeParserOptions.InlineDuringParsing;
import static org.graalvm.compiler.serviceprovider.GraalServices.Java8OrEarlier;

import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MutableCallSite;
import java.lang.invoke.VolatileCallSite;
import java.lang.reflect.Array;
import java.math.BigInteger;
import java.util.zip.CRC32;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.bytecode.BytecodeProvider;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.TypeReference;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.nodes.CurrentJavaThreadNode;
import org.graalvm.compiler.hotspot.replacements.AESCryptSubstitutions;
import org.graalvm.compiler.hotspot.replacements.BigIntegerSubstitutions;
import org.graalvm.compiler.hotspot.replacements.CRC32CSubstitutions;
import org.graalvm.compiler.hotspot.replacements.CRC32Substitutions;
import org.graalvm.compiler.hotspot.replacements.CallSiteTargetNode;
import org.graalvm.compiler.hotspot.replacements.CipherBlockChainingSubstitutions;
import org.graalvm.compiler.hotspot.replacements.ClassGetHubNode;
import org.graalvm.compiler.hotspot.replacements.HotSpotArraySubstitutions;
import org.graalvm.compiler.hotspot.replacements.HotSpotClassSubstitutions;
import org.graalvm.compiler.hotspot.replacements.IdentityHashCodeNode;
import org.graalvm.compiler.hotspot.replacements.ObjectCloneNode;
import org.graalvm.compiler.hotspot.replacements.ObjectSubstitutions;
import org.graalvm.compiler.hotspot.replacements.ReflectionGetCallerClassNode;
import org.graalvm.compiler.hotspot.replacements.ReflectionSubstitutions;
import org.graalvm.compiler.hotspot.replacements.SHA2Substitutions;
import org.graalvm.compiler.hotspot.replacements.SHA5Substitutions;
import org.graalvm.compiler.hotspot.replacements.SHASubstitutions;
import org.graalvm.compiler.hotspot.replacements.ThreadSubstitutions;
import org.graalvm.compiler.hotspot.word.HotSpotWordTypes;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.calc.IntegerConvertNode;
import org.graalvm.compiler.nodes.calc.LeftShiftNode;
import org.graalvm.compiler.nodes.graphbuilderconf.ForeignCallPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin.Receiver;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import org.graalvm.compiler.nodes.graphbuilderconf.NodeIntrinsicPluginFactory;
import org.graalvm.compiler.nodes.memory.HeapAccess.BarrierType;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.tiers.CompilerConfiguration;
import org.graalvm.compiler.replacements.InlineDuringParsingPlugin;
import org.graalvm.compiler.replacements.MethodHandlePlugin;
import org.graalvm.compiler.replacements.NodeIntrinsificationProvider;
import org.graalvm.compiler.replacements.ReplacementsImpl;
import org.graalvm.compiler.replacements.StandardGraphBuilderPlugins;
import org.graalvm.compiler.replacements.arraycopy.ArrayCopyNode;
import org.graalvm.compiler.serviceprovider.GraalServices;
import org.graalvm.compiler.word.WordOperationPlugin;
import org.graalvm.compiler.word.WordTypes;
import jdk.internal.vm.compiler.word.LocationIdentity;

import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
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
     */
    public static Plugins create(CompilerConfiguration compilerConfiguration, GraalHotSpotVMConfig config, HotSpotWordTypes wordTypes, MetaAccessProvider metaAccess,
                    ConstantReflectionProvider constantReflection, SnippetReflectionProvider snippetReflection, ForeignCallsProvider foreignCalls, ReplacementsImpl replacements) {
        InvocationPlugins invocationPlugins = new HotSpotInvocationPlugins(config, compilerConfiguration);

        Plugins plugins = new Plugins(invocationPlugins);
        NodeIntrinsificationProvider nodeIntrinsificationProvider = new NodeIntrinsificationProvider(metaAccess, snippetReflection, foreignCalls, wordTypes);
        HotSpotWordOperationPlugin wordOperationPlugin = new HotSpotWordOperationPlugin(snippetReflection, wordTypes);
        HotSpotNodePlugin nodePlugin = new HotSpotNodePlugin(wordOperationPlugin, config, wordTypes);

        plugins.appendTypePlugin(nodePlugin);
        plugins.appendNodePlugin(nodePlugin);
        OptionValues options = replacements.getOptions();
        if (!GeneratePIC.getValue(options)) {
            plugins.appendNodePlugin(new MethodHandlePlugin(constantReflection.getMethodHandleAccess(), true));
        }
        plugins.appendInlineInvokePlugin(replacements);
        if (InlineDuringParsing.getValue(options)) {
            plugins.appendInlineInvokePlugin(new InlineDuringParsingPlugin());
        }

        if (GeneratePIC.getValue(options)) {
            plugins.setClassInitializationPlugin(new HotSpotClassInitializationPlugin());
            if (TieredAOT.getValue(options)) {
                plugins.setProfilingPlugin(new HotSpotAOTProfilingPlugin());
            }
        }

        invocationPlugins.defer(new Runnable() {

            @Override
            public void run() {
                BytecodeProvider replacementBytecodeProvider = replacements.getDefaultReplacementBytecodeProvider();
                registerObjectPlugins(invocationPlugins, options, config, replacementBytecodeProvider);
                registerClassPlugins(plugins, config, replacementBytecodeProvider);
                registerSystemPlugins(invocationPlugins, foreignCalls);
                registerThreadPlugins(invocationPlugins, metaAccess, wordTypes, config, replacementBytecodeProvider);
                if (!GeneratePIC.getValue(options)) {
                    registerCallSitePlugins(invocationPlugins);
                }
                registerReflectionPlugins(invocationPlugins, replacementBytecodeProvider);
                registerConstantPoolPlugins(invocationPlugins, wordTypes, config, replacementBytecodeProvider);
                registerAESPlugins(invocationPlugins, config, replacementBytecodeProvider);
                registerCRC32Plugins(invocationPlugins, config, replacementBytecodeProvider);
                registerCRC32CPlugins(invocationPlugins, config, replacementBytecodeProvider);
                registerBigIntegerPlugins(invocationPlugins, config, replacementBytecodeProvider);
                registerSHAPlugins(invocationPlugins, config, replacementBytecodeProvider);
                registerUnsafePlugins(invocationPlugins, replacementBytecodeProvider);
                StandardGraphBuilderPlugins.registerInvocationPlugins(metaAccess, snippetReflection, invocationPlugins, replacementBytecodeProvider, true, false);
                registerArrayPlugins(invocationPlugins, replacementBytecodeProvider);

                for (NodeIntrinsicPluginFactory factory : GraalServices.load(NodeIntrinsicPluginFactory.class)) {
                    factory.registerPlugins(invocationPlugins, nodeIntrinsificationProvider);
                }
            }
        });
        return plugins;
    }

    private static void registerObjectPlugins(InvocationPlugins plugins, OptionValues options, GraalHotSpotVMConfig config, BytecodeProvider bytecodeProvider) {
        Registration r = new Registration(plugins, Object.class, bytecodeProvider);
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
                    b.addPush(JavaKind.Object, new ObjectCloneNode(b.getInvokeKind(), targetMethod, b.bci(), b.getInvokeReturnStamp(b.getAssumptions()), object));
                    return true;
                }

                @Override
                public boolean inlineOnly() {
                    return true;
                }
            });
        }
        r.registerMethodSubstitution(ObjectSubstitutions.class, "hashCode", Receiver.class);
        if (config.inlineNotify()) {
            r.registerMethodSubstitution(ObjectSubstitutions.class, "notify", Receiver.class);
        }
        if (config.inlineNotifyAll()) {
            r.registerMethodSubstitution(ObjectSubstitutions.class, "notifyAll", Receiver.class);
        }
    }

    private static void registerClassPlugins(Plugins plugins, GraalHotSpotVMConfig config, BytecodeProvider bytecodeProvider) {
        Registration r = new Registration(plugins.getInvocationPlugins(), Class.class, bytecodeProvider);

        r.registerMethodSubstitution(HotSpotClassSubstitutions.class, "getModifiers", Receiver.class);
        r.registerMethodSubstitution(HotSpotClassSubstitutions.class, "isInterface", Receiver.class);
        r.registerMethodSubstitution(HotSpotClassSubstitutions.class, "isArray", Receiver.class);
        r.registerMethodSubstitution(HotSpotClassSubstitutions.class, "isPrimitive", Receiver.class);
        r.registerMethodSubstitution(HotSpotClassSubstitutions.class, "getSuperclass", Receiver.class);

        if (config.getFieldOffset("ArrayKlass::_component_mirror", Integer.class, "oop", Integer.MAX_VALUE) != Integer.MAX_VALUE) {
            r.registerMethodSubstitution(HotSpotClassSubstitutions.class, "getComponentType", Receiver.class);
        }
    }

    private static void registerCallSitePlugins(InvocationPlugins plugins) {
        InvocationPlugin plugin = new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                ValueNode callSite = receiver.get();
                ValueNode folded = CallSiteTargetNode.tryFold(GraphUtil.originalValue(callSite), b.getMetaAccess(), b.getAssumptions());
                if (folded != null) {
                    b.addPush(JavaKind.Object, folded);
                } else {
                    b.addPush(JavaKind.Object, new CallSiteTargetNode(b.getInvokeKind(), targetMethod, b.bci(), b.getInvokeReturnStamp(b.getAssumptions()), callSite));
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

    private static void registerReflectionPlugins(InvocationPlugins plugins, BytecodeProvider bytecodeProvider) {
        Registration r = new Registration(plugins, reflectionClass, bytecodeProvider);
        r.register0("getCallerClass", new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.addPush(JavaKind.Object, new ReflectionGetCallerClassNode(b.getInvokeKind(), targetMethod, b.bci(), b.getInvokeReturnStamp(b.getAssumptions())));
                return true;
            }

            @Override
            public boolean inlineOnly() {
                return true;
            }
        });
        r.registerMethodSubstitution(ReflectionSubstitutions.class, "getClassAccessFlags", Class.class);
    }

    private static void registerUnsafePlugins(InvocationPlugins plugins, BytecodeProvider replacementBytecodeProvider) {
        Registration r;
        if (Java8OrEarlier) {
            r = new Registration(plugins, Unsafe.class, replacementBytecodeProvider);
        } else {
            r = new Registration(plugins, "jdk.internal.misc.Unsafe", replacementBytecodeProvider);
        }
        r.registerMethodSubstitution(HotSpotUnsafeSubstitutions.class, HotSpotUnsafeSubstitutions.copyMemoryName, "copyMemory", Receiver.class, Object.class, long.class, Object.class, long.class,
                        long.class);
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

    private static void registerConstantPoolPlugins(InvocationPlugins plugins, WordTypes wordTypes, GraalHotSpotVMConfig config, BytecodeProvider bytecodeProvider) {
        Registration r = new Registration(plugins, constantPoolClass, bytecodeProvider);

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

    private static void registerSystemPlugins(InvocationPlugins plugins, ForeignCallsProvider foreignCalls) {
        Registration r = new Registration(plugins, System.class);
        r.register0("currentTimeMillis", new ForeignCallPlugin(foreignCalls, HotSpotHostForeignCallsProvider.JAVA_TIME_MILLIS));
        r.register0("nanoTime", new ForeignCallPlugin(foreignCalls, HotSpotHostForeignCallsProvider.JAVA_TIME_NANOS));
        r.register1("identityHashCode", Object.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode object) {
                b.addPush(JavaKind.Int, new IdentityHashCodeNode(object));
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

    private static void registerArrayPlugins(InvocationPlugins plugins, BytecodeProvider bytecodeProvider) {
        Registration r = new Registration(plugins, Array.class, bytecodeProvider);
        r.setAllowOverwrite(true);
        r.registerMethodSubstitution(HotSpotArraySubstitutions.class, "newInstance", Class.class, int.class);
    }

    private static void registerThreadPlugins(InvocationPlugins plugins, MetaAccessProvider metaAccess, WordTypes wordTypes, GraalHotSpotVMConfig config, BytecodeProvider bytecodeProvider) {
        Registration r = new Registration(plugins, Thread.class, bytecodeProvider);
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

        r.registerMethodSubstitution(ThreadSubstitutions.class, "isInterrupted", Receiver.class, boolean.class);
    }

    public static final String cbcEncryptName;
    public static final String cbcDecryptName;
    public static final String aesEncryptName;
    public static final String aesDecryptName;

    public static final String reflectionClass;
    public static final String constantPoolClass;

    static {
        if (Java8OrEarlier) {
            cbcEncryptName = "encrypt";
            cbcDecryptName = "decrypt";
            aesEncryptName = "encryptBlock";
            aesDecryptName = "decryptBlock";
            reflectionClass = "sun.reflect.Reflection";
            constantPoolClass = "sun.reflect.ConstantPool";
        } else {
            cbcEncryptName = "implEncrypt";
            cbcDecryptName = "implDecrypt";
            aesEncryptName = "implEncryptBlock";
            aesDecryptName = "implDecryptBlock";
            reflectionClass = "jdk.internal.reflect.Reflection";
            constantPoolClass = "jdk.internal.reflect.ConstantPool";
        }
    }

    private static void registerAESPlugins(InvocationPlugins plugins, GraalHotSpotVMConfig config, BytecodeProvider bytecodeProvider) {
        if (config.useAESIntrinsics) {
            assert config.aescryptEncryptBlockStub != 0L;
            assert config.aescryptDecryptBlockStub != 0L;
            assert config.cipherBlockChainingEncryptAESCryptStub != 0L;
            assert config.cipherBlockChainingDecryptAESCryptStub != 0L;
            String arch = config.osArch;
            String decryptSuffix = arch.equals("sparc") ? "WithOriginalKey" : "";
            Registration r = new Registration(plugins, "com.sun.crypto.provider.CipherBlockChaining", bytecodeProvider);
            r.registerMethodSubstitution(CipherBlockChainingSubstitutions.class, cbcEncryptName, Receiver.class, byte[].class, int.class, int.class, byte[].class, int.class);
            r.registerMethodSubstitution(CipherBlockChainingSubstitutions.class, cbcDecryptName, cbcDecryptName + decryptSuffix, Receiver.class, byte[].class, int.class, int.class, byte[].class,
                            int.class);
            r = new Registration(plugins, "com.sun.crypto.provider.AESCrypt", bytecodeProvider);
            r.registerMethodSubstitution(AESCryptSubstitutions.class, aesEncryptName, Receiver.class, byte[].class, int.class, byte[].class, int.class);
            r.registerMethodSubstitution(AESCryptSubstitutions.class, aesDecryptName, aesDecryptName + decryptSuffix, Receiver.class, byte[].class, int.class, byte[].class, int.class);
        }
    }

    private static void registerBigIntegerPlugins(InvocationPlugins plugins, GraalHotSpotVMConfig config, BytecodeProvider bytecodeProvider) {
        Registration r = new Registration(plugins, BigInteger.class, bytecodeProvider);
        if (config.useMultiplyToLenIntrinsic()) {
            assert config.multiplyToLen != 0L;
            if (Java8OrEarlier) {
                r.registerMethodSubstitution(BigIntegerSubstitutions.class, "multiplyToLen", "multiplyToLenStatic", int[].class, int.class, int[].class, int.class,
                                int[].class);
            } else {
                r.registerMethodSubstitution(BigIntegerSubstitutions.class, "implMultiplyToLen", "multiplyToLenStatic", int[].class, int.class, int[].class, int.class,
                                int[].class);
            }
        }
        if (config.useMulAddIntrinsic()) {
            r.registerMethodSubstitution(BigIntegerSubstitutions.class, "implMulAdd", int[].class, int[].class, int.class, int.class, int.class);
        }
        if (config.useMontgomeryMultiplyIntrinsic()) {
            r.registerMethodSubstitution(BigIntegerSubstitutions.class, "implMontgomeryMultiply", int[].class, int[].class, int[].class, int.class, long.class, int[].class);
        }
        if (config.useMontgomerySquareIntrinsic()) {
            r.registerMethodSubstitution(BigIntegerSubstitutions.class, "implMontgomerySquare", int[].class, int[].class, int.class, long.class, int[].class);
        }
        if (config.useSquareToLenIntrinsic()) {
            r.registerMethodSubstitution(BigIntegerSubstitutions.class, "implSquareToLen", int[].class, int.class, int[].class, int.class);
        }
    }

    private static void registerSHAPlugins(InvocationPlugins plugins, GraalHotSpotVMConfig config, BytecodeProvider bytecodeProvider) {
        if (config.useSHA1Intrinsics()) {
            assert config.sha1ImplCompress != 0L;
            Registration r = new Registration(plugins, "sun.security.provider.SHA", bytecodeProvider);
            r.registerMethodSubstitution(SHASubstitutions.class, SHASubstitutions.implCompressName, "implCompress0", Receiver.class, byte[].class, int.class);
        }
        if (config.useSHA256Intrinsics()) {
            assert config.sha256ImplCompress != 0L;
            Registration r = new Registration(plugins, "sun.security.provider.SHA2", bytecodeProvider);
            r.registerMethodSubstitution(SHA2Substitutions.class, SHA2Substitutions.implCompressName, "implCompress0", Receiver.class, byte[].class, int.class);
        }
        if (config.useSHA512Intrinsics()) {
            assert config.sha512ImplCompress != 0L;
            Registration r = new Registration(plugins, "sun.security.provider.SHA5", bytecodeProvider);
            r.registerMethodSubstitution(SHA5Substitutions.class, SHA5Substitutions.implCompressName, "implCompress0", Receiver.class, byte[].class, int.class);
        }
    }

    private static void registerCRC32Plugins(InvocationPlugins plugins, GraalHotSpotVMConfig config, BytecodeProvider bytecodeProvider) {
        if (config.useCRC32Intrinsics) {
            Registration r = new Registration(plugins, CRC32.class, bytecodeProvider);
            r.registerMethodSubstitution(CRC32Substitutions.class, "update", int.class, int.class);
            if (Java8OrEarlier) {
                r.registerMethodSubstitution(CRC32Substitutions.class, "updateBytes", int.class, byte[].class, int.class, int.class);
                r.registerMethodSubstitution(CRC32Substitutions.class, "updateByteBuffer", int.class, long.class, int.class, int.class);
            } else {
                r.registerMethodSubstitution(CRC32Substitutions.class, "updateBytes0", int.class, byte[].class, int.class, int.class);
                r.registerMethodSubstitution(CRC32Substitutions.class, "updateByteBuffer0", int.class, long.class, int.class, int.class);
            }
        }
    }

    private static void registerCRC32CPlugins(InvocationPlugins plugins, GraalHotSpotVMConfig config, BytecodeProvider bytecodeProvider) {
        if (config.useCRC32CIntrinsics) {
            Registration r = new Registration(plugins, "java.util.zip.CRC32C", bytecodeProvider);
            r.registerMethodSubstitution(CRC32CSubstitutions.class, "updateBytes", int.class, byte[].class, int.class, int.class);
            r.registerMethodSubstitution(CRC32CSubstitutions.class, "updateDirectByteBuffer", int.class, long.class, int.class, int.class);
        }
    }
}
