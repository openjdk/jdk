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


package org.graalvm.compiler.replacements.amd64;

import static org.graalvm.compiler.replacements.StandardGraphBuilderPlugins.registerPlatformSpecificUnsafePlugins;
import static org.graalvm.compiler.replacements.nodes.BinaryMathIntrinsicNode.BinaryOperation.POW;
import static org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.COS;
import static org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.EXP;
import static org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.LOG;
import static org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.LOG10;
import static org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.SIN;
import static org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.TAN;
import static org.graalvm.compiler.serviceprovider.GraalServices.JAVA_SPECIFICATION_VERSION;
import static org.graalvm.compiler.serviceprovider.GraalServices.Java8OrEarlier;

import java.util.Arrays;

import org.graalvm.compiler.bytecode.BytecodeProvider;
import org.graalvm.compiler.lir.amd64.AMD64ArithmeticLIRGeneratorTool.RoundingMode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin.Receiver;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import org.graalvm.compiler.nodes.java.AtomicReadAndAddNode;
import org.graalvm.compiler.nodes.java.AtomicReadAndWriteNode;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.replacements.ArraysSubstitutions;
import org.graalvm.compiler.replacements.IntegerSubstitutions;
import org.graalvm.compiler.replacements.LongSubstitutions;
import org.graalvm.compiler.replacements.StandardGraphBuilderPlugins.UnsafeAccessPlugin;
import org.graalvm.compiler.replacements.StandardGraphBuilderPlugins.UnsafeGetPlugin;
import org.graalvm.compiler.replacements.StandardGraphBuilderPlugins.UnsafePutPlugin;
import org.graalvm.compiler.replacements.nodes.BinaryMathIntrinsicNode;
import org.graalvm.compiler.replacements.nodes.BinaryMathIntrinsicNode.BinaryOperation;
import org.graalvm.compiler.replacements.nodes.BitCountNode;
import org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode;
import org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64.CPUFeature;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import sun.misc.Unsafe;

public class AMD64GraphBuilderPlugins {

    public static void register(Plugins plugins, BytecodeProvider replacementsBytecodeProvider, AMD64 arch, boolean arithmeticStubs, boolean explicitUnsafeNullChecks) {
        InvocationPlugins invocationPlugins = plugins.getInvocationPlugins();
        invocationPlugins.defer(new Runnable() {
            @Override
            public void run() {
                registerIntegerLongPlugins(invocationPlugins, IntegerSubstitutions.class, JavaKind.Int, arch, replacementsBytecodeProvider);
                registerIntegerLongPlugins(invocationPlugins, LongSubstitutions.class, JavaKind.Long, arch, replacementsBytecodeProvider);
                registerPlatformSpecificUnsafePlugins(invocationPlugins, replacementsBytecodeProvider, explicitUnsafeNullChecks,
                                new JavaKind[]{JavaKind.Int, JavaKind.Long, JavaKind.Object, JavaKind.Boolean, JavaKind.Byte, JavaKind.Short, JavaKind.Char, JavaKind.Float, JavaKind.Double});
                registerUnsafePlugins(invocationPlugins, replacementsBytecodeProvider, explicitUnsafeNullChecks);
                registerStringPlugins(invocationPlugins, arch, replacementsBytecodeProvider);
                registerStringLatin1Plugins(invocationPlugins, replacementsBytecodeProvider);
                registerStringUTF16Plugins(invocationPlugins, replacementsBytecodeProvider);
                registerMathPlugins(invocationPlugins, arch, arithmeticStubs, replacementsBytecodeProvider);
                registerArraysEqualsPlugins(invocationPlugins, replacementsBytecodeProvider);
            }
        });
    }

    private static void registerIntegerLongPlugins(InvocationPlugins plugins, Class<?> substituteDeclaringClass, JavaKind kind, AMD64 arch, BytecodeProvider bytecodeProvider) {
        Class<?> declaringClass = kind.toBoxedJavaClass();
        Class<?> type = kind.toJavaClass();
        Registration r = new Registration(plugins, declaringClass, bytecodeProvider);
        if (arch.getFeatures().contains(AMD64.CPUFeature.LZCNT) && arch.getFlags().contains(AMD64.Flag.UseCountLeadingZerosInstruction)) {
            r.register1("numberOfLeadingZeros", type, new InvocationPlugin() {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                    ValueNode folded = AMD64CountLeadingZerosNode.tryFold(value);
                    if (folded != null) {
                        b.addPush(JavaKind.Int, folded);
                    } else {
                        b.addPush(JavaKind.Int, new AMD64CountLeadingZerosNode(value));
                    }
                    return true;
                }
            });
        } else {
            r.registerMethodSubstitution(substituteDeclaringClass, "numberOfLeadingZeros", type);
        }
        if (arch.getFeatures().contains(AMD64.CPUFeature.BMI1) && arch.getFlags().contains(AMD64.Flag.UseCountTrailingZerosInstruction)) {
            r.register1("numberOfTrailingZeros", type, new InvocationPlugin() {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                    ValueNode folded = AMD64CountTrailingZerosNode.tryFold(value);
                    if (folded != null) {
                        b.addPush(JavaKind.Int, folded);
                    } else {
                        b.addPush(JavaKind.Int, new AMD64CountTrailingZerosNode(value));
                    }
                    return true;
                }
            });
        } else {
            r.registerMethodSubstitution(substituteDeclaringClass, "numberOfTrailingZeros", type);
        }

        if (arch.getFeatures().contains(AMD64.CPUFeature.POPCNT)) {
            r.register1("bitCount", type, new InvocationPlugin() {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                    b.push(JavaKind.Int, b.append(new BitCountNode(value).canonical(null)));
                    return true;
                }
            });
        }
    }

    private static void registerMathPlugins(InvocationPlugins plugins, AMD64 arch, boolean arithmeticStubs, BytecodeProvider bytecodeProvider) {
        Registration r = new Registration(plugins, Math.class, bytecodeProvider);
        registerUnaryMath(r, "log", LOG);
        registerUnaryMath(r, "log10", LOG10);
        registerUnaryMath(r, "exp", EXP);
        registerBinaryMath(r, "pow", POW);
        if (arithmeticStubs) {
            registerUnaryMath(r, "sin", SIN);
            registerUnaryMath(r, "cos", COS);
            registerUnaryMath(r, "tan", TAN);
        } else {
            r.registerMethodSubstitution(AMD64MathSubstitutions.class, "sin", double.class);
            r.registerMethodSubstitution(AMD64MathSubstitutions.class, "cos", double.class);
            r.registerMethodSubstitution(AMD64MathSubstitutions.class, "tan", double.class);
        }

        if (arch.getFeatures().contains(CPUFeature.SSE4_1)) {
            registerRound(r, "rint", RoundingMode.NEAREST);
            registerRound(r, "ceil", RoundingMode.UP);
            registerRound(r, "floor", RoundingMode.DOWN);
        }
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

    private static void registerBinaryMath(Registration r, String name, BinaryOperation operation) {
        r.register2(name, Double.TYPE, Double.TYPE, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                b.push(JavaKind.Double, b.append(BinaryMathIntrinsicNode.create(x, y, operation)));
                return true;
            }
        });
    }

    private static void registerRound(Registration r, String name, RoundingMode mode) {
        r.register1(name, Double.TYPE, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg) {
                b.push(JavaKind.Double, b.append(new AMD64RoundNode(arg, mode)));
                return true;
            }
        });
    }

    private static void registerStringPlugins(InvocationPlugins plugins, AMD64 arch, BytecodeProvider replacementsBytecodeProvider) {
        if (Java8OrEarlier) {
            Registration r;
            r = new Registration(plugins, String.class, replacementsBytecodeProvider);
            r.setAllowOverwrite(true);
            if (arch.getFeatures().contains(CPUFeature.SSE4_2)) {
                r.registerMethodSubstitution(AMD64StringSubstitutions.class, "indexOf", char[].class, int.class,
                                int.class, char[].class, int.class, int.class, int.class);
            }
            r.registerMethodSubstitution(AMD64StringSubstitutions.class, "compareTo", Receiver.class, String.class);
        }
    }

    private static void registerStringLatin1Plugins(InvocationPlugins plugins, BytecodeProvider replacementsBytecodeProvider) {
        if (JAVA_SPECIFICATION_VERSION >= 9) {
            Registration r = new Registration(plugins, "java.lang.StringLatin1", replacementsBytecodeProvider);
            r.setAllowOverwrite(true);
            r.registerMethodSubstitution(AMD64StringLatin1Substitutions.class, "compareTo", byte[].class, byte[].class);
            r.registerMethodSubstitution(AMD64StringLatin1Substitutions.class, "compareToUTF16", byte[].class, byte[].class);
        }
    }

    private static void registerStringUTF16Plugins(InvocationPlugins plugins, BytecodeProvider replacementsBytecodeProvider) {
        if (JAVA_SPECIFICATION_VERSION >= 9) {
            Registration r = new Registration(plugins, "java.lang.StringUTF16", replacementsBytecodeProvider);
            r.setAllowOverwrite(true);
            r.registerMethodSubstitution(AMD64StringUTF16Substitutions.class, "compareTo", byte[].class, byte[].class);
            r.registerMethodSubstitution(AMD64StringUTF16Substitutions.class, "compareToLatin1", byte[].class, byte[].class);
        }
    }

    private static void registerUnsafePlugins(InvocationPlugins plugins, BytecodeProvider replacementsBytecodeProvider, boolean explicitUnsafeNullChecks) {
        registerUnsafePlugins(new Registration(plugins, Unsafe.class), explicitUnsafeNullChecks, new JavaKind[]{JavaKind.Int, JavaKind.Long, JavaKind.Object});
        if (!Java8OrEarlier) {
            registerUnsafePlugins(new Registration(plugins, "jdk.internal.misc.Unsafe", replacementsBytecodeProvider), explicitUnsafeNullChecks,
                            new JavaKind[]{JavaKind.Boolean, JavaKind.Byte, JavaKind.Char, JavaKind.Short, JavaKind.Int, JavaKind.Long, JavaKind.Object});
        }
    }

    private static void registerUnsafePlugins(Registration r, boolean explicitUnsafeNullChecks, JavaKind[] unsafeJavaKinds) {
        for (JavaKind kind : unsafeJavaKinds) {
            Class<?> javaClass = kind == JavaKind.Object ? Object.class : kind.toJavaClass();

            r.register4("getAndSet" + kind.name(), Receiver.class, Object.class, long.class, javaClass, new UnsafeAccessPlugin(kind, explicitUnsafeNullChecks) {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver unsafe, ValueNode object, ValueNode offset, ValueNode value) {
                    // Emits a null-check for the otherwise unused receiver
                    unsafe.get();
                    createUnsafeAccess(object, b, (obj, loc) -> new AtomicReadAndWriteNode(obj, offset, value, kind, loc));
                    return true;
                }
            });
            if (kind != JavaKind.Boolean && kind.isNumericInteger()) {
                r.register4("getAndAdd" + kind.name(), Receiver.class, Object.class, long.class, javaClass, new UnsafeAccessPlugin(kind, explicitUnsafeNullChecks) {
                    @Override
                    public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver unsafe, ValueNode object, ValueNode offset, ValueNode delta) {
                        // Emits a null-check for the otherwise unused receiver
                        unsafe.get();
                        createUnsafeAccess(object, b, (obj, loc) -> new AtomicReadAndAddNode(b.add(new OffsetAddressNode(obj, offset)), delta, kind, loc));
                        return true;
                    }
                });
            }
        }

        for (JavaKind kind : new JavaKind[]{JavaKind.Char, JavaKind.Short, JavaKind.Int, JavaKind.Long}) {
            Class<?> javaClass = kind.toJavaClass();
            r.registerOptional3("get" + kind.name() + "Unaligned", Receiver.class, Object.class, long.class, new UnsafeGetPlugin(kind, explicitUnsafeNullChecks));
            r.registerOptional4("put" + kind.name() + "Unaligned", Receiver.class, Object.class, long.class, javaClass, new UnsafePutPlugin(kind, explicitUnsafeNullChecks));
        }
    }

    private static void registerArraysEqualsPlugins(InvocationPlugins plugins, BytecodeProvider bytecodeProvider) {
        Registration r = new Registration(plugins, Arrays.class, bytecodeProvider);
        r.registerMethodSubstitution(ArraysSubstitutions.class, "equals", float[].class, float[].class);
        r.registerMethodSubstitution(ArraysSubstitutions.class, "equals", double[].class, double[].class);
    }
}
