/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.replacements.aarch64;

import static org.graalvm.compiler.replacements.StandardGraphBuilderPlugins.registerPlatformSpecificUnsafePlugins;
import static org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.COS;
import static org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.EXP;
import static org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.LOG;
import static org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.LOG10;
import static org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.SIN;
import static org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.TAN;
import static org.graalvm.compiler.serviceprovider.JavaVersionUtil.JAVA_SPECIFICATION_VERSION;
import static org.graalvm.compiler.serviceprovider.JavaVersionUtil.Java11OrEarlier;
import static org.graalvm.compiler.serviceprovider.JavaVersionUtil.Java8OrEarlier;

import org.graalvm.compiler.bytecode.BytecodeProvider;
import org.graalvm.compiler.lir.aarch64.AArch64ArithmeticLIRGeneratorTool.RoundingMode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin.Receiver;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import org.graalvm.compiler.nodes.java.AtomicReadAndAddNode;
import org.graalvm.compiler.nodes.java.AtomicReadAndWriteNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.replacements.nodes.BinaryMathIntrinsicNode;
import org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode;
import org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation;
import jdk.internal.vm.compiler.word.LocationIdentity;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import sun.misc.Unsafe;

public class AArch64GraphBuilderPlugins {

    public static void register(Plugins plugins, BytecodeProvider bytecodeProvider, boolean explicitUnsafeNullChecks) {
        InvocationPlugins invocationPlugins = plugins.getInvocationPlugins();
        invocationPlugins.defer(new Runnable() {
            @Override
            public void run() {
                registerIntegerLongPlugins(invocationPlugins, AArch64IntegerSubstitutions.class, JavaKind.Int, bytecodeProvider);
                registerIntegerLongPlugins(invocationPlugins, AArch64LongSubstitutions.class, JavaKind.Long, bytecodeProvider);
                registerMathPlugins(invocationPlugins);
                registerStringLatin1Plugins(invocationPlugins, bytecodeProvider);
                registerStringUTF16Plugins(invocationPlugins, bytecodeProvider);
                registerUnsafePlugins(invocationPlugins, bytecodeProvider);
                // This is temporarily disabled until we implement correct emitting of the CAS
                // instructions of the proper width.
                registerPlatformSpecificUnsafePlugins(invocationPlugins, bytecodeProvider, explicitUnsafeNullChecks,
                                new JavaKind[]{JavaKind.Int, JavaKind.Long, JavaKind.Object});
            }
        });
    }

    private static void registerIntegerLongPlugins(InvocationPlugins plugins, Class<?> substituteDeclaringClass, JavaKind kind, BytecodeProvider bytecodeProvider) {
        Class<?> declaringClass = kind.toBoxedJavaClass();
        Class<?> type = kind.toJavaClass();
        Registration r = new Registration(plugins, declaringClass, bytecodeProvider);
        r.register1("numberOfLeadingZeros", type, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                ValueNode folded = AArch64CountLeadingZerosNode.tryFold(value);
                if (folded != null) {
                    b.addPush(JavaKind.Int, folded);
                } else {
                    b.addPush(JavaKind.Int, new AArch64CountLeadingZerosNode(value));
                }
                return true;
            }
        });
        r.register1("numberOfTrailingZeros", type, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                ValueNode folded = AArch64CountTrailingZerosNode.tryFold(value);
                if (folded != null) {
                    b.addPush(JavaKind.Int, folded);
                } else {
                    b.addPush(JavaKind.Int, new AArch64CountTrailingZerosNode(value));
                }
                return true;
            }
        });
        r.registerMethodSubstitution(substituteDeclaringClass, "bitCount", type);
    }

    private static void registerMathPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, Math.class);
        registerUnaryMath(r, "sin", SIN);
        registerUnaryMath(r, "cos", COS);
        registerUnaryMath(r, "tan", TAN);
        registerUnaryMath(r, "exp", EXP);
        registerUnaryMath(r, "log", LOG);
        registerUnaryMath(r, "log10", LOG10);
        r.register2("pow", Double.TYPE, Double.TYPE, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                b.push(JavaKind.Double, b.append(BinaryMathIntrinsicNode.create(x, y, BinaryMathIntrinsicNode.BinaryOperation.POW)));
                return true;
            }
        });
        registerRound(r, "rint", RoundingMode.NEAREST);
        registerRound(r, "ceil", RoundingMode.UP);
        registerRound(r, "floor", RoundingMode.DOWN);
    }

    private static void registerUnaryMath(Registration r, String name, UnaryOperation operation) {
        r.register1(name, Double.TYPE, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Double, b.append(UnaryMathIntrinsicNode.create(value, operation)));
                return true;
            }
        });
    }

    private static void registerRound(Registration r, String name, RoundingMode mode) {
        r.register1(name, Double.TYPE, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg) {
                b.push(JavaKind.Double, b.append(new AArch64RoundNode(arg, mode)));
                return true;
            }
        });
    }

    private static void registerStringLatin1Plugins(InvocationPlugins plugins, BytecodeProvider replacementsBytecodeProvider) {
        if (JAVA_SPECIFICATION_VERSION >= 9) {
            Registration r = new Registration(plugins, "java.lang.StringLatin1", replacementsBytecodeProvider);
            r.setAllowOverwrite(true);
            r.registerMethodSubstitution(AArch64StringLatin1Substitutions.class, "compareTo", byte[].class, byte[].class);
            r.registerMethodSubstitution(AArch64StringLatin1Substitutions.class, "compareToUTF16", byte[].class, byte[].class);
        }
    }

    private static void registerStringUTF16Plugins(InvocationPlugins plugins, BytecodeProvider replacementsBytecodeProvider) {
        if (JAVA_SPECIFICATION_VERSION >= 9) {
            Registration r = new Registration(plugins, "java.lang.StringUTF16", replacementsBytecodeProvider);
            r.setAllowOverwrite(true);
            r.registerMethodSubstitution(AArch64StringUTF16Substitutions.class, "compareTo", byte[].class, byte[].class);
            r.registerMethodSubstitution(AArch64StringUTF16Substitutions.class, "compareToLatin1", byte[].class, byte[].class);
        }
    }

    private static void registerUnsafePlugins(InvocationPlugins plugins, BytecodeProvider replacementsBytecodeProvider) {
        registerUnsafePlugins(new Registration(plugins, Unsafe.class),
                        new JavaKind[]{JavaKind.Int, JavaKind.Long, JavaKind.Object}, "Object");
        if (!Java8OrEarlier) {
            registerUnsafePlugins(new Registration(plugins, "jdk.internal.misc.Unsafe", replacementsBytecodeProvider),
                            new JavaKind[]{JavaKind.Int, JavaKind.Long, JavaKind.Object},
                            Java11OrEarlier ? "Object" : "Reference");
        }
    }

    private static void registerUnsafePlugins(Registration r, JavaKind[] unsafeJavaKinds, String objectKindName) {

        for (JavaKind kind : unsafeJavaKinds) {
            Class<?> javaClass = kind == JavaKind.Object ? Object.class : kind.toJavaClass();
            String kindName = kind == JavaKind.Object ? objectKindName : kind.name();
            r.register4("getAndSet" + kindName, Receiver.class, Object.class, long.class, javaClass, new InvocationPlugin() {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver unsafe, ValueNode object, ValueNode offset, ValueNode value) {
                    // Emits a null-check for the otherwise unused receiver
                    unsafe.get();
                    b.addPush(kind, new AtomicReadAndWriteNode(object, offset, value, kind, LocationIdentity.any()));
                    b.getGraph().markUnsafeAccess();
                    return true;
                }
            });

            if (kind != JavaKind.Boolean && kind.isNumericInteger()) {
                r.register4("getAndAdd" + kindName, Receiver.class, Object.class, long.class, javaClass, new InvocationPlugin() {
                    @Override
                    public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver unsafe, ValueNode object, ValueNode offset, ValueNode delta) {
                        // Emits a null-check for the otherwise unused receiver
                        unsafe.get();
                        AddressNode address = b.add(new OffsetAddressNode(object, offset));
                        b.addPush(kind, new AtomicReadAndAddNode(address, delta, kind, LocationIdentity.any()));
                        b.getGraph().markUnsafeAccess();
                        return true;
                    }
                });
            }
        }
    }
}
