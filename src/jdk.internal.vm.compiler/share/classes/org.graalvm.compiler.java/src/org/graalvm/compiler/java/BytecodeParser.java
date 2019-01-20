/*
 * Copyright (c) 2009, 2019, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.java;

import static java.lang.String.format;
import static java.lang.reflect.Modifier.STATIC;
import static java.lang.reflect.Modifier.SYNCHRONIZED;
import static jdk.vm.ci.meta.DeoptimizationAction.InvalidateRecompile;
import static jdk.vm.ci.meta.DeoptimizationAction.InvalidateReprofile;
import static jdk.vm.ci.meta.DeoptimizationAction.None;
import static jdk.vm.ci.meta.DeoptimizationReason.ClassCastException;
import static jdk.vm.ci.meta.DeoptimizationReason.JavaSubroutineMismatch;
import static jdk.vm.ci.meta.DeoptimizationReason.NullCheckException;
import static jdk.vm.ci.meta.DeoptimizationReason.RuntimeConstraint;
import static jdk.vm.ci.meta.DeoptimizationReason.TypeCheckedInliningViolated;
import static jdk.vm.ci.meta.DeoptimizationReason.UnreachedCode;
import static jdk.vm.ci.meta.DeoptimizationReason.Unresolved;
import static jdk.vm.ci.runtime.JVMCICompiler.INVOCATION_ENTRY_BCI;
import static org.graalvm.compiler.bytecode.Bytecodes.AALOAD;
import static org.graalvm.compiler.bytecode.Bytecodes.AASTORE;
import static org.graalvm.compiler.bytecode.Bytecodes.ACONST_NULL;
import static org.graalvm.compiler.bytecode.Bytecodes.ALOAD;
import static org.graalvm.compiler.bytecode.Bytecodes.ALOAD_0;
import static org.graalvm.compiler.bytecode.Bytecodes.ALOAD_1;
import static org.graalvm.compiler.bytecode.Bytecodes.ALOAD_2;
import static org.graalvm.compiler.bytecode.Bytecodes.ALOAD_3;
import static org.graalvm.compiler.bytecode.Bytecodes.ANEWARRAY;
import static org.graalvm.compiler.bytecode.Bytecodes.ARETURN;
import static org.graalvm.compiler.bytecode.Bytecodes.ARRAYLENGTH;
import static org.graalvm.compiler.bytecode.Bytecodes.ASTORE;
import static org.graalvm.compiler.bytecode.Bytecodes.ASTORE_0;
import static org.graalvm.compiler.bytecode.Bytecodes.ASTORE_1;
import static org.graalvm.compiler.bytecode.Bytecodes.ASTORE_2;
import static org.graalvm.compiler.bytecode.Bytecodes.ASTORE_3;
import static org.graalvm.compiler.bytecode.Bytecodes.ATHROW;
import static org.graalvm.compiler.bytecode.Bytecodes.BALOAD;
import static org.graalvm.compiler.bytecode.Bytecodes.BASTORE;
import static org.graalvm.compiler.bytecode.Bytecodes.BIPUSH;
import static org.graalvm.compiler.bytecode.Bytecodes.BREAKPOINT;
import static org.graalvm.compiler.bytecode.Bytecodes.CALOAD;
import static org.graalvm.compiler.bytecode.Bytecodes.CASTORE;
import static org.graalvm.compiler.bytecode.Bytecodes.CHECKCAST;
import static org.graalvm.compiler.bytecode.Bytecodes.D2F;
import static org.graalvm.compiler.bytecode.Bytecodes.D2I;
import static org.graalvm.compiler.bytecode.Bytecodes.D2L;
import static org.graalvm.compiler.bytecode.Bytecodes.DADD;
import static org.graalvm.compiler.bytecode.Bytecodes.DALOAD;
import static org.graalvm.compiler.bytecode.Bytecodes.DASTORE;
import static org.graalvm.compiler.bytecode.Bytecodes.DCMPG;
import static org.graalvm.compiler.bytecode.Bytecodes.DCMPL;
import static org.graalvm.compiler.bytecode.Bytecodes.DCONST_0;
import static org.graalvm.compiler.bytecode.Bytecodes.DCONST_1;
import static org.graalvm.compiler.bytecode.Bytecodes.DDIV;
import static org.graalvm.compiler.bytecode.Bytecodes.DLOAD;
import static org.graalvm.compiler.bytecode.Bytecodes.DLOAD_0;
import static org.graalvm.compiler.bytecode.Bytecodes.DLOAD_1;
import static org.graalvm.compiler.bytecode.Bytecodes.DLOAD_2;
import static org.graalvm.compiler.bytecode.Bytecodes.DLOAD_3;
import static org.graalvm.compiler.bytecode.Bytecodes.DMUL;
import static org.graalvm.compiler.bytecode.Bytecodes.DNEG;
import static org.graalvm.compiler.bytecode.Bytecodes.DREM;
import static org.graalvm.compiler.bytecode.Bytecodes.DRETURN;
import static org.graalvm.compiler.bytecode.Bytecodes.DSTORE;
import static org.graalvm.compiler.bytecode.Bytecodes.DSTORE_0;
import static org.graalvm.compiler.bytecode.Bytecodes.DSTORE_1;
import static org.graalvm.compiler.bytecode.Bytecodes.DSTORE_2;
import static org.graalvm.compiler.bytecode.Bytecodes.DSTORE_3;
import static org.graalvm.compiler.bytecode.Bytecodes.DSUB;
import static org.graalvm.compiler.bytecode.Bytecodes.DUP;
import static org.graalvm.compiler.bytecode.Bytecodes.DUP2;
import static org.graalvm.compiler.bytecode.Bytecodes.DUP2_X1;
import static org.graalvm.compiler.bytecode.Bytecodes.DUP2_X2;
import static org.graalvm.compiler.bytecode.Bytecodes.DUP_X1;
import static org.graalvm.compiler.bytecode.Bytecodes.DUP_X2;
import static org.graalvm.compiler.bytecode.Bytecodes.F2D;
import static org.graalvm.compiler.bytecode.Bytecodes.F2I;
import static org.graalvm.compiler.bytecode.Bytecodes.F2L;
import static org.graalvm.compiler.bytecode.Bytecodes.FADD;
import static org.graalvm.compiler.bytecode.Bytecodes.FALOAD;
import static org.graalvm.compiler.bytecode.Bytecodes.FASTORE;
import static org.graalvm.compiler.bytecode.Bytecodes.FCMPG;
import static org.graalvm.compiler.bytecode.Bytecodes.FCMPL;
import static org.graalvm.compiler.bytecode.Bytecodes.FCONST_0;
import static org.graalvm.compiler.bytecode.Bytecodes.FCONST_1;
import static org.graalvm.compiler.bytecode.Bytecodes.FCONST_2;
import static org.graalvm.compiler.bytecode.Bytecodes.FDIV;
import static org.graalvm.compiler.bytecode.Bytecodes.FLOAD;
import static org.graalvm.compiler.bytecode.Bytecodes.FLOAD_0;
import static org.graalvm.compiler.bytecode.Bytecodes.FLOAD_1;
import static org.graalvm.compiler.bytecode.Bytecodes.FLOAD_2;
import static org.graalvm.compiler.bytecode.Bytecodes.FLOAD_3;
import static org.graalvm.compiler.bytecode.Bytecodes.FMUL;
import static org.graalvm.compiler.bytecode.Bytecodes.FNEG;
import static org.graalvm.compiler.bytecode.Bytecodes.FREM;
import static org.graalvm.compiler.bytecode.Bytecodes.FRETURN;
import static org.graalvm.compiler.bytecode.Bytecodes.FSTORE;
import static org.graalvm.compiler.bytecode.Bytecodes.FSTORE_0;
import static org.graalvm.compiler.bytecode.Bytecodes.FSTORE_1;
import static org.graalvm.compiler.bytecode.Bytecodes.FSTORE_2;
import static org.graalvm.compiler.bytecode.Bytecodes.FSTORE_3;
import static org.graalvm.compiler.bytecode.Bytecodes.FSUB;
import static org.graalvm.compiler.bytecode.Bytecodes.GETFIELD;
import static org.graalvm.compiler.bytecode.Bytecodes.GETSTATIC;
import static org.graalvm.compiler.bytecode.Bytecodes.GOTO;
import static org.graalvm.compiler.bytecode.Bytecodes.GOTO_W;
import static org.graalvm.compiler.bytecode.Bytecodes.I2B;
import static org.graalvm.compiler.bytecode.Bytecodes.I2C;
import static org.graalvm.compiler.bytecode.Bytecodes.I2D;
import static org.graalvm.compiler.bytecode.Bytecodes.I2F;
import static org.graalvm.compiler.bytecode.Bytecodes.I2L;
import static org.graalvm.compiler.bytecode.Bytecodes.I2S;
import static org.graalvm.compiler.bytecode.Bytecodes.IADD;
import static org.graalvm.compiler.bytecode.Bytecodes.IALOAD;
import static org.graalvm.compiler.bytecode.Bytecodes.IAND;
import static org.graalvm.compiler.bytecode.Bytecodes.IASTORE;
import static org.graalvm.compiler.bytecode.Bytecodes.ICONST_0;
import static org.graalvm.compiler.bytecode.Bytecodes.ICONST_1;
import static org.graalvm.compiler.bytecode.Bytecodes.ICONST_2;
import static org.graalvm.compiler.bytecode.Bytecodes.ICONST_3;
import static org.graalvm.compiler.bytecode.Bytecodes.ICONST_4;
import static org.graalvm.compiler.bytecode.Bytecodes.ICONST_5;
import static org.graalvm.compiler.bytecode.Bytecodes.ICONST_M1;
import static org.graalvm.compiler.bytecode.Bytecodes.IDIV;
import static org.graalvm.compiler.bytecode.Bytecodes.IFEQ;
import static org.graalvm.compiler.bytecode.Bytecodes.IFGE;
import static org.graalvm.compiler.bytecode.Bytecodes.IFGT;
import static org.graalvm.compiler.bytecode.Bytecodes.IFLE;
import static org.graalvm.compiler.bytecode.Bytecodes.IFLT;
import static org.graalvm.compiler.bytecode.Bytecodes.IFNE;
import static org.graalvm.compiler.bytecode.Bytecodes.IFNONNULL;
import static org.graalvm.compiler.bytecode.Bytecodes.IFNULL;
import static org.graalvm.compiler.bytecode.Bytecodes.IF_ACMPEQ;
import static org.graalvm.compiler.bytecode.Bytecodes.IF_ACMPNE;
import static org.graalvm.compiler.bytecode.Bytecodes.IF_ICMPEQ;
import static org.graalvm.compiler.bytecode.Bytecodes.IF_ICMPGE;
import static org.graalvm.compiler.bytecode.Bytecodes.IF_ICMPGT;
import static org.graalvm.compiler.bytecode.Bytecodes.IF_ICMPLE;
import static org.graalvm.compiler.bytecode.Bytecodes.IF_ICMPLT;
import static org.graalvm.compiler.bytecode.Bytecodes.IF_ICMPNE;
import static org.graalvm.compiler.bytecode.Bytecodes.IINC;
import static org.graalvm.compiler.bytecode.Bytecodes.ILOAD;
import static org.graalvm.compiler.bytecode.Bytecodes.ILOAD_0;
import static org.graalvm.compiler.bytecode.Bytecodes.ILOAD_1;
import static org.graalvm.compiler.bytecode.Bytecodes.ILOAD_2;
import static org.graalvm.compiler.bytecode.Bytecodes.ILOAD_3;
import static org.graalvm.compiler.bytecode.Bytecodes.IMUL;
import static org.graalvm.compiler.bytecode.Bytecodes.INEG;
import static org.graalvm.compiler.bytecode.Bytecodes.INSTANCEOF;
import static org.graalvm.compiler.bytecode.Bytecodes.INVOKEDYNAMIC;
import static org.graalvm.compiler.bytecode.Bytecodes.INVOKEINTERFACE;
import static org.graalvm.compiler.bytecode.Bytecodes.INVOKESPECIAL;
import static org.graalvm.compiler.bytecode.Bytecodes.INVOKESTATIC;
import static org.graalvm.compiler.bytecode.Bytecodes.INVOKEVIRTUAL;
import static org.graalvm.compiler.bytecode.Bytecodes.IOR;
import static org.graalvm.compiler.bytecode.Bytecodes.IREM;
import static org.graalvm.compiler.bytecode.Bytecodes.IRETURN;
import static org.graalvm.compiler.bytecode.Bytecodes.ISHL;
import static org.graalvm.compiler.bytecode.Bytecodes.ISHR;
import static org.graalvm.compiler.bytecode.Bytecodes.ISTORE;
import static org.graalvm.compiler.bytecode.Bytecodes.ISTORE_0;
import static org.graalvm.compiler.bytecode.Bytecodes.ISTORE_1;
import static org.graalvm.compiler.bytecode.Bytecodes.ISTORE_2;
import static org.graalvm.compiler.bytecode.Bytecodes.ISTORE_3;
import static org.graalvm.compiler.bytecode.Bytecodes.ISUB;
import static org.graalvm.compiler.bytecode.Bytecodes.IUSHR;
import static org.graalvm.compiler.bytecode.Bytecodes.IXOR;
import static org.graalvm.compiler.bytecode.Bytecodes.JSR;
import static org.graalvm.compiler.bytecode.Bytecodes.JSR_W;
import static org.graalvm.compiler.bytecode.Bytecodes.L2D;
import static org.graalvm.compiler.bytecode.Bytecodes.L2F;
import static org.graalvm.compiler.bytecode.Bytecodes.L2I;
import static org.graalvm.compiler.bytecode.Bytecodes.LADD;
import static org.graalvm.compiler.bytecode.Bytecodes.LALOAD;
import static org.graalvm.compiler.bytecode.Bytecodes.LAND;
import static org.graalvm.compiler.bytecode.Bytecodes.LASTORE;
import static org.graalvm.compiler.bytecode.Bytecodes.LCMP;
import static org.graalvm.compiler.bytecode.Bytecodes.LCONST_0;
import static org.graalvm.compiler.bytecode.Bytecodes.LCONST_1;
import static org.graalvm.compiler.bytecode.Bytecodes.LDC;
import static org.graalvm.compiler.bytecode.Bytecodes.LDC2_W;
import static org.graalvm.compiler.bytecode.Bytecodes.LDC_W;
import static org.graalvm.compiler.bytecode.Bytecodes.LDIV;
import static org.graalvm.compiler.bytecode.Bytecodes.LLOAD;
import static org.graalvm.compiler.bytecode.Bytecodes.LLOAD_0;
import static org.graalvm.compiler.bytecode.Bytecodes.LLOAD_1;
import static org.graalvm.compiler.bytecode.Bytecodes.LLOAD_2;
import static org.graalvm.compiler.bytecode.Bytecodes.LLOAD_3;
import static org.graalvm.compiler.bytecode.Bytecodes.LMUL;
import static org.graalvm.compiler.bytecode.Bytecodes.LNEG;
import static org.graalvm.compiler.bytecode.Bytecodes.LOOKUPSWITCH;
import static org.graalvm.compiler.bytecode.Bytecodes.LOR;
import static org.graalvm.compiler.bytecode.Bytecodes.LREM;
import static org.graalvm.compiler.bytecode.Bytecodes.LRETURN;
import static org.graalvm.compiler.bytecode.Bytecodes.LSHL;
import static org.graalvm.compiler.bytecode.Bytecodes.LSHR;
import static org.graalvm.compiler.bytecode.Bytecodes.LSTORE;
import static org.graalvm.compiler.bytecode.Bytecodes.LSTORE_0;
import static org.graalvm.compiler.bytecode.Bytecodes.LSTORE_1;
import static org.graalvm.compiler.bytecode.Bytecodes.LSTORE_2;
import static org.graalvm.compiler.bytecode.Bytecodes.LSTORE_3;
import static org.graalvm.compiler.bytecode.Bytecodes.LSUB;
import static org.graalvm.compiler.bytecode.Bytecodes.LUSHR;
import static org.graalvm.compiler.bytecode.Bytecodes.LXOR;
import static org.graalvm.compiler.bytecode.Bytecodes.MONITORENTER;
import static org.graalvm.compiler.bytecode.Bytecodes.MONITOREXIT;
import static org.graalvm.compiler.bytecode.Bytecodes.MULTIANEWARRAY;
import static org.graalvm.compiler.bytecode.Bytecodes.NEW;
import static org.graalvm.compiler.bytecode.Bytecodes.NEWARRAY;
import static org.graalvm.compiler.bytecode.Bytecodes.NOP;
import static org.graalvm.compiler.bytecode.Bytecodes.POP;
import static org.graalvm.compiler.bytecode.Bytecodes.POP2;
import static org.graalvm.compiler.bytecode.Bytecodes.PUTFIELD;
import static org.graalvm.compiler.bytecode.Bytecodes.PUTSTATIC;
import static org.graalvm.compiler.bytecode.Bytecodes.RET;
import static org.graalvm.compiler.bytecode.Bytecodes.RETURN;
import static org.graalvm.compiler.bytecode.Bytecodes.SALOAD;
import static org.graalvm.compiler.bytecode.Bytecodes.SASTORE;
import static org.graalvm.compiler.bytecode.Bytecodes.SIPUSH;
import static org.graalvm.compiler.bytecode.Bytecodes.SWAP;
import static org.graalvm.compiler.bytecode.Bytecodes.TABLESWITCH;
import static org.graalvm.compiler.bytecode.Bytecodes.nameOf;
import static org.graalvm.compiler.core.common.GraalOptions.DeoptALot;
import static org.graalvm.compiler.core.common.GraalOptions.GeneratePIC;
import static org.graalvm.compiler.core.common.GraalOptions.HotSpotPrintInlining;
import static org.graalvm.compiler.core.common.GraalOptions.PrintProfilingInformation;
import static org.graalvm.compiler.core.common.GraalOptions.StressExplicitExceptionCode;
import static org.graalvm.compiler.core.common.GraalOptions.StressInvokeWithExceptionNode;
import static org.graalvm.compiler.core.common.type.StampFactory.objectNonNull;
import static org.graalvm.compiler.debug.GraalError.guarantee;
import static org.graalvm.compiler.debug.GraalError.shouldNotReachHere;
import static org.graalvm.compiler.java.BytecodeParserOptions.InlinePartialIntrinsicExitDuringParsing;
import static org.graalvm.compiler.java.BytecodeParserOptions.TraceBytecodeParserLevel;
import static org.graalvm.compiler.java.BytecodeParserOptions.TraceInlineDuringParsing;
import static org.graalvm.compiler.java.BytecodeParserOptions.TraceParserPlugins;
import static org.graalvm.compiler.java.BytecodeParserOptions.UseGuardedIntrinsics;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.FAST_PATH_PROBABILITY;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.LUDICROUSLY_FAST_PATH_PROBABILITY;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.LUDICROUSLY_SLOW_PATH_PROBABILITY;
import static org.graalvm.compiler.nodes.graphbuilderconf.IntrinsicContext.CompilationContext.INLINE_DURING_PARSING;
import static org.graalvm.compiler.nodes.type.StampTool.isPointerNonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Formatter;
import java.util.List;
import java.util.function.Supplier;

import jdk.internal.vm.compiler.collections.EconomicMap;
import jdk.internal.vm.compiler.collections.Equivalence;
import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.bytecode.Bytecode;
import org.graalvm.compiler.bytecode.BytecodeDisassembler;
import org.graalvm.compiler.bytecode.BytecodeLookupSwitch;
import org.graalvm.compiler.bytecode.BytecodeProvider;
import org.graalvm.compiler.bytecode.BytecodeStream;
import org.graalvm.compiler.bytecode.BytecodeSwitch;
import org.graalvm.compiler.bytecode.BytecodeTableSwitch;
import org.graalvm.compiler.bytecode.Bytecodes;
import org.graalvm.compiler.bytecode.Bytes;
import org.graalvm.compiler.bytecode.ResolvedJavaMethodBytecode;
import org.graalvm.compiler.bytecode.ResolvedJavaMethodBytecodeProvider;
import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.core.common.PermanentBailoutException;
import org.graalvm.compiler.core.common.calc.CanonicalCondition;
import org.graalvm.compiler.core.common.calc.Condition;
import org.graalvm.compiler.core.common.calc.Condition.CanonicalizedCondition;
import org.graalvm.compiler.core.common.calc.FloatConvert;
import org.graalvm.compiler.core.common.spi.ConstantFieldProvider;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.StampPair;
import org.graalvm.compiler.core.common.type.TypeReference;
import org.graalvm.compiler.core.common.util.Util;
import org.graalvm.compiler.debug.Assertions;
import org.graalvm.compiler.debug.CounterKey;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugOptions;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.debug.Indent;
import org.graalvm.compiler.debug.MethodFilter;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.graph.Graph.Mark;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.java.BciBlockMapping.BciBlock;
import org.graalvm.compiler.java.BciBlockMapping.ExceptionDispatchBlock;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.BeginNode;
import org.graalvm.compiler.nodes.BeginStateSplitNode;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.CallTargetNode.InvokeKind;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.ControlSplitNode;
import org.graalvm.compiler.nodes.DeoptimizeNode;
import org.graalvm.compiler.nodes.EndNode;
import org.graalvm.compiler.nodes.EntryMarkerNode;
import org.graalvm.compiler.nodes.EntryProxyNode;
import org.graalvm.compiler.nodes.FieldLocationIdentity;
import org.graalvm.compiler.nodes.FixedGuardNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.FullInfopointNode;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.InliningLog;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.InvokeNode;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.KillingBeginNode;
import org.graalvm.compiler.nodes.LogicConstantNode;
import org.graalvm.compiler.nodes.LogicNegationNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.LoopEndNode;
import org.graalvm.compiler.nodes.LoopExitNode;
import org.graalvm.compiler.nodes.MergeNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StartNode;
import org.graalvm.compiler.nodes.StateSplit;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.UnwindNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.calc.AndNode;
import org.graalvm.compiler.nodes.calc.CompareNode;
import org.graalvm.compiler.nodes.calc.ConditionalNode;
import org.graalvm.compiler.nodes.calc.FloatConvertNode;
import org.graalvm.compiler.nodes.calc.FloatDivNode;
import org.graalvm.compiler.nodes.calc.IntegerBelowNode;
import org.graalvm.compiler.nodes.calc.IntegerEqualsNode;
import org.graalvm.compiler.nodes.calc.IntegerLessThanNode;
import org.graalvm.compiler.nodes.calc.IsNullNode;
import org.graalvm.compiler.nodes.calc.LeftShiftNode;
import org.graalvm.compiler.nodes.calc.MulNode;
import org.graalvm.compiler.nodes.calc.NarrowNode;
import org.graalvm.compiler.nodes.calc.NegateNode;
import org.graalvm.compiler.nodes.calc.NormalizeCompareNode;
import org.graalvm.compiler.nodes.calc.ObjectEqualsNode;
import org.graalvm.compiler.nodes.calc.OrNode;
import org.graalvm.compiler.nodes.calc.RemNode;
import org.graalvm.compiler.nodes.calc.RightShiftNode;
import org.graalvm.compiler.nodes.calc.SignExtendNode;
import org.graalvm.compiler.nodes.calc.SignedDivNode;
import org.graalvm.compiler.nodes.calc.SignedRemNode;
import org.graalvm.compiler.nodes.calc.SubNode;
import org.graalvm.compiler.nodes.calc.UnsignedRightShiftNode;
import org.graalvm.compiler.nodes.calc.XorNode;
import org.graalvm.compiler.nodes.calc.ZeroExtendNode;
import org.graalvm.compiler.nodes.extended.AnchoringNode;
import org.graalvm.compiler.nodes.extended.BranchProbabilityNode;
import org.graalvm.compiler.nodes.extended.BytecodeExceptionNode;
import org.graalvm.compiler.nodes.extended.BytecodeExceptionNode.BytecodeExceptionKind;
import org.graalvm.compiler.nodes.extended.GuardingNode;
import org.graalvm.compiler.nodes.extended.IntegerSwitchNode;
import org.graalvm.compiler.nodes.extended.LoadArrayComponentHubNode;
import org.graalvm.compiler.nodes.extended.LoadHubNode;
import org.graalvm.compiler.nodes.extended.LoadMethodNode;
import org.graalvm.compiler.nodes.extended.MembarNode;
import org.graalvm.compiler.nodes.extended.StateSplitProxyNode;
import org.graalvm.compiler.nodes.extended.ValueAnchorNode;
import org.graalvm.compiler.nodes.graphbuilderconf.ClassInitializationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.BytecodeExceptionMode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin.InlineInfo;
import org.graalvm.compiler.nodes.graphbuilderconf.IntrinsicContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.InvocationPluginReceiver;
import org.graalvm.compiler.nodes.graphbuilderconf.InvokeDynamicPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.NodePlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.ProfilingPlugin;
import org.graalvm.compiler.nodes.java.ArrayLengthNode;
import org.graalvm.compiler.nodes.java.ExceptionObjectNode;
import org.graalvm.compiler.nodes.java.FinalFieldBarrierNode;
import org.graalvm.compiler.nodes.java.InstanceOfDynamicNode;
import org.graalvm.compiler.nodes.java.InstanceOfNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.java.LoadIndexedNode;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.java.MonitorEnterNode;
import org.graalvm.compiler.nodes.java.MonitorExitNode;
import org.graalvm.compiler.nodes.java.MonitorIdNode;
import org.graalvm.compiler.nodes.java.NewArrayNode;
import org.graalvm.compiler.nodes.java.NewInstanceNode;
import org.graalvm.compiler.nodes.java.NewMultiArrayNode;
import org.graalvm.compiler.nodes.java.RegisterFinalizerNode;
import org.graalvm.compiler.nodes.java.StoreFieldNode;
import org.graalvm.compiler.nodes.java.StoreIndexedNode;
import org.graalvm.compiler.nodes.spi.StampProvider;
import org.graalvm.compiler.nodes.type.StampTool;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.util.ValueMergeUtil;
import org.graalvm.compiler.serviceprovider.GraalServices;
import jdk.internal.vm.compiler.word.LocationIdentity;

import jdk.vm.ci.code.BailoutException;
import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.code.site.InfopointReason;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaField;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.JavaTypeProfile;
import jdk.vm.ci.meta.JavaTypeProfile.ProfiledType;
import jdk.vm.ci.meta.LineNumberTable;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.RawConstant;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;
import jdk.vm.ci.meta.TriState;

/**
 * The {@code GraphBuilder} class parses the bytecode of a method and builds the IR graph.
 */
public class BytecodeParser implements GraphBuilderContext {

    /**
     * The minimum value to which {@link BytecodeParserOptions#TraceBytecodeParserLevel} must be set
     * to trace the bytecode instructions as they are parsed.
     */
    public static final int TRACELEVEL_INSTRUCTIONS = 1;

    /**
     * The minimum value to which {@link BytecodeParserOptions#TraceBytecodeParserLevel} must be set
     * to emit the frame state for each traced bytecode instruction.
     */
    public static final int TRACELEVEL_STATE = 2;

    /**
     * The minimum value to which {@link BytecodeParserOptions#TraceBytecodeParserLevel} must be set
     * to emit the block map for each traced method.
     */
    public static final int TRACELEVEL_BLOCKMAP = 3;

    /**
     * Meters the number of actual bytecodes parsed.
     */
    public static final CounterKey BytecodesParsed = DebugContext.counter("BytecodesParsed");

    protected static final CounterKey EXPLICIT_EXCEPTIONS = DebugContext.counter("ExplicitExceptions");

    /**
     * A scoped object for tasks to be performed after parsing an intrinsic such as processing
     * {@linkplain BytecodeFrame#isPlaceholderBci(int) placeholder} frames states.
     */
    static class IntrinsicScope implements AutoCloseable {
        FrameState stateBefore;
        final Mark mark;
        final BytecodeParser parser;
        List<ReturnToCallerData> returnDataList;

        /**
         * Creates a scope for root parsing an intrinsic.
         *
         * @param parser the parsing context of the intrinsic
         */
        IntrinsicScope(BytecodeParser parser) {
            this.parser = parser;
            assert parser.parent == null;
            assert parser.bci() == 0;
            mark = null;
        }

        /**
         * Creates a scope for parsing an intrinsic during graph builder inlining.
         *
         * @param parser the parsing context of the (non-intrinsic) method calling the intrinsic
         * @param args the arguments to the call
         */
        IntrinsicScope(BytecodeParser parser, JavaKind[] argSlotKinds, ValueNode[] args) {
            assert !parser.parsingIntrinsic();
            this.parser = parser;
            mark = parser.getGraph().getMark();
            stateBefore = parser.frameState.create(parser.bci(), parser.getNonIntrinsicAncestor(), false, argSlotKinds, args);
        }

        @Override
        public void close() {
            IntrinsicContext intrinsic = parser.intrinsicContext;
            if (intrinsic != null && intrinsic.isPostParseInlined()) {
                return;
            }

            processPlaceholderFrameStates(intrinsic);
        }

        /**
         * Fixes up the {@linkplain BytecodeFrame#isPlaceholderBci(int) placeholder} frame states
         * added to the graph while parsing/inlining the intrinsic for which this object exists.
         */
        private void processPlaceholderFrameStates(IntrinsicContext intrinsic) {
            StructuredGraph graph = parser.getGraph();
            graph.getDebug().dump(DebugContext.DETAILED_LEVEL, graph, "Before processPlaceholderFrameStates in %s", parser.method);
            boolean sawInvalidFrameState = false;
            for (Node node : graph.getNewNodes(mark)) {
                if (node instanceof FrameState) {
                    FrameState frameState = (FrameState) node;
                    if (BytecodeFrame.isPlaceholderBci(frameState.bci)) {
                        if (frameState.bci == BytecodeFrame.AFTER_BCI) {
                            if (parser.getInvokeReturnType() == null) {
                                // A frame state in a root compiled intrinsic.
                                assert intrinsic.isCompilationRoot();
                                FrameState newFrameState = graph.add(new FrameState(BytecodeFrame.INVALID_FRAMESTATE_BCI));
                                frameState.replaceAndDelete(newFrameState);
                            } else {
                                JavaKind returnKind = parser.getInvokeReturnType().getJavaKind();
                                FrameStateBuilder frameStateBuilder = parser.frameState;
                                assert !frameState.rethrowException();
                                if (frameState.stackSize() != 0) {
                                    ValueNode returnVal = frameState.stackAt(0);
                                    if (!ReturnToCallerData.containsReturnValue(returnDataList, returnVal)) {
                                        throw new GraalError("AFTER_BCI frame state within an intrinsic has a non-return value on the stack: %s", returnVal);
                                    }

                                    // Swap the top-of-stack value with the return value
                                    ValueNode tos = frameStateBuilder.pop(returnKind);
                                    assert tos.getStackKind() == returnVal.getStackKind();
                                    FrameState newFrameState = frameStateBuilder.create(parser.stream.nextBCI(), parser.getNonIntrinsicAncestor(), false, new JavaKind[]{returnKind},
                                                    new ValueNode[]{returnVal});
                                    frameState.replaceAndDelete(newFrameState);
                                    newFrameState.setNodeSourcePosition(frameState.getNodeSourcePosition());
                                    frameStateBuilder.push(returnKind, tos);
                                } else if (returnKind != JavaKind.Void) {
                                    // If the intrinsic returns a non-void value, then any frame
                                    // state with an empty stack is invalid as it cannot
                                    // be used to deoptimize to just after the call returns.
                                    // These invalid frame states are expected to be removed
                                    // by later compilation stages.
                                    FrameState newFrameState = graph.add(new FrameState(BytecodeFrame.INVALID_FRAMESTATE_BCI));
                                    newFrameState.setNodeSourcePosition(frameState.getNodeSourcePosition());
                                    frameState.replaceAndDelete(newFrameState);
                                    sawInvalidFrameState = true;
                                } else {
                                    // An intrinsic for a void method.
                                    FrameState newFrameState = frameStateBuilder.create(parser.stream.nextBCI(), null);
                                    newFrameState.setNodeSourcePosition(frameState.getNodeSourcePosition());
                                    frameState.replaceAndDelete(newFrameState);
                                }
                            }
                        } else if (frameState.bci == BytecodeFrame.BEFORE_BCI) {
                            if (stateBefore == null) {
                                stateBefore = graph.start().stateAfter();
                            }
                            if (stateBefore != frameState) {
                                frameState.replaceAndDelete(stateBefore);
                            }
                        } else if (frameState.bci == BytecodeFrame.AFTER_EXCEPTION_BCI) {
                            // This is a frame state for the entry point to an exception
                            // dispatcher in an intrinsic. For example, the invoke denoting
                            // a partial intrinsic exit will have an edge to such a
                            // dispatcher if the profile for the original invoke being
                            // intrinsified indicates an exception was seen. As per JVM
                            // bytecode semantics, the interpreter expects a single
                            // value on the stack on entry to an exception handler,
                            // namely the exception object.
                            assert frameState.rethrowException();
                            ValueNode exceptionValue = frameState.stackAt(0);
                            ExceptionObjectNode exceptionObject = (ExceptionObjectNode) GraphUtil.unproxify(exceptionValue);
                            FrameStateBuilder dispatchState = parser.frameState.copy();
                            dispatchState.clearStack();
                            dispatchState.push(JavaKind.Object, exceptionValue);
                            dispatchState.setRethrowException(true);
                            FrameState newFrameState = dispatchState.create(parser.bci(), exceptionObject);
                            frameState.replaceAndDelete(newFrameState);
                            newFrameState.setNodeSourcePosition(frameState.getNodeSourcePosition());
                        } else {
                            assert frameState.bci == BytecodeFrame.INVALID_FRAMESTATE_BCI;
                        }
                    }
                }
            }
            if (sawInvalidFrameState) {
                JavaKind returnKind = parser.getInvokeReturnType().getJavaKind();
                FrameStateBuilder frameStateBuilder = parser.frameState;
                ValueNode returnValue = frameStateBuilder.pop(returnKind);
                StateSplitProxyNode proxy = graph.add(new StateSplitProxyNode(returnValue));
                parser.lastInstr.setNext(proxy);
                frameStateBuilder.push(returnKind, proxy);
                proxy.setStateAfter(parser.createFrameState(parser.stream.nextBCI(), proxy));
                parser.lastInstr = proxy;
            }
            graph.getDebug().dump(DebugContext.DETAILED_LEVEL, graph, "After processPlaceholderFrameStates in %s", parser.method);
        }
    }

    private static class Target {
        FixedNode fixed;
        FrameStateBuilder state;

        Target(FixedNode fixed, FrameStateBuilder state) {
            this.fixed = fixed;
            this.state = state;
        }
    }

    @SuppressWarnings("serial")
    public static class BytecodeParserError extends GraalError {

        public BytecodeParserError(Throwable cause) {
            super(cause);
        }

        public BytecodeParserError(String msg, Object... args) {
            super(msg, args);
        }
    }

    protected static class ReturnToCallerData {
        protected final ValueNode returnValue;
        protected final FixedWithNextNode beforeReturnNode;

        protected ReturnToCallerData(ValueNode returnValue, FixedWithNextNode beforeReturnNode) {
            this.returnValue = returnValue;
            this.beforeReturnNode = beforeReturnNode;
        }

        static boolean containsReturnValue(List<ReturnToCallerData> list, ValueNode value) {
            for (ReturnToCallerData e : list) {
                if (e.returnValue == value) {
                    return true;
                }
            }
            return false;
        }
    }

    private final GraphBuilderPhase.Instance graphBuilderInstance;
    protected final StructuredGraph graph;
    protected final OptionValues options;
    protected final DebugContext debug;

    private BciBlockMapping blockMap;
    private LocalLiveness liveness;
    protected final int entryBCI;
    private final BytecodeParser parent;

    private LineNumberTable lnt;
    private int previousLineNumber;
    private int currentLineNumber;

    private ValueNode methodSynchronizedObject;

    private List<ReturnToCallerData> returnDataList;
    private ValueNode unwindValue;
    private FixedWithNextNode beforeUnwindNode;

    protected FixedWithNextNode lastInstr;                 // the last instruction added
    private boolean controlFlowSplit;
    private final InvocationPluginReceiver invocationPluginReceiver = new InvocationPluginReceiver(this);

    private FixedWithNextNode[] firstInstructionArray;
    private FrameStateBuilder[] entryStateArray;

    private boolean finalBarrierRequired;
    private ValueNode originalReceiver;
    private final boolean eagerInitializing;
    private final boolean uninitializedIsError;
    private final int traceLevel;

    protected BytecodeParser(GraphBuilderPhase.Instance graphBuilderInstance, StructuredGraph graph, BytecodeParser parent, ResolvedJavaMethod method,
                    int entryBCI, IntrinsicContext intrinsicContext) {
        this.bytecodeProvider = intrinsicContext == null ? new ResolvedJavaMethodBytecodeProvider() : intrinsicContext.getBytecodeProvider();
        this.code = bytecodeProvider.getBytecode(method);
        this.method = code.getMethod();
        this.graphBuilderInstance = graphBuilderInstance;
        this.graph = graph;
        this.options = graph.getOptions();
        this.debug = graph.getDebug();
        this.graphBuilderConfig = graphBuilderInstance.graphBuilderConfig;
        this.optimisticOpts = graphBuilderInstance.optimisticOpts;
        this.metaAccess = graphBuilderInstance.metaAccess;
        this.stampProvider = graphBuilderInstance.stampProvider;
        this.constantReflection = graphBuilderInstance.constantReflection;
        this.constantFieldProvider = graphBuilderInstance.constantFieldProvider;
        this.stream = new BytecodeStream(code.getCode());
        this.profilingInfo = graph.useProfilingInfo() ? code.getProfilingInfo() : null;
        this.constantPool = code.getConstantPool();
        this.intrinsicContext = intrinsicContext;
        this.entryBCI = entryBCI;
        this.parent = parent;

        ClassInitializationPlugin classInitializationPlugin = graphBuilderConfig.getPlugins().getClassInitializationPlugin();
        if (classInitializationPlugin != null && graphBuilderConfig.eagerResolving()) {
            uninitializedIsError = eagerInitializing = !classInitializationPlugin.supportsLazyInitialization(constantPool);
        } else {
            eagerInitializing = graphBuilderConfig.eagerResolving();
            uninitializedIsError = graphBuilderConfig.unresolvedIsError();
        }

        assert code.getCode() != null : "method must contain bytecodes: " + method;

        if (graphBuilderConfig.insertFullInfopoints() && !parsingIntrinsic()) {
            lnt = code.getLineNumberTable();
            previousLineNumber = -1;
        }

        assert !GraalOptions.TrackNodeSourcePosition.getValue(options) || graph.trackNodeSourcePosition();
        if (graphBuilderConfig.trackNodeSourcePosition() || (parent != null && parent.graph.trackNodeSourcePosition())) {
            graph.setTrackNodeSourcePosition();
        }

        int level = TraceBytecodeParserLevel.getValue(options);
        this.traceLevel = level != 0 ? refineTraceLevel(level) : 0;
    }

    private int refineTraceLevel(int level) {
        ResolvedJavaMethod tmethod = graph.method();
        if (tmethod == null) {
            tmethod = method;
        }
        String filterValue = DebugOptions.MethodFilter.getValue(options);
        if (filterValue != null) {
            MethodFilter[] filters = MethodFilter.parse(filterValue);
            if (!MethodFilter.matches(filters, tmethod)) {
                return 0;
            }
        }
        return level;
    }

    protected GraphBuilderPhase.Instance getGraphBuilderInstance() {
        return graphBuilderInstance;
    }

    public ValueNode getUnwindValue() {
        return unwindValue;
    }

    public FixedWithNextNode getBeforeUnwindNode() {
        return this.beforeUnwindNode;
    }

    @SuppressWarnings("try")
    protected void buildRootMethod() {
        FrameStateBuilder startFrameState = new FrameStateBuilder(this, code, graph);
        startFrameState.initializeForMethodStart(graph.getAssumptions(), graphBuilderConfig.eagerResolving() || intrinsicContext != null, graphBuilderConfig.getPlugins());

        try (IntrinsicScope s = intrinsicContext != null ? new IntrinsicScope(this) : null) {
            build(graph.start(), startFrameState);
        }

        cleanupFinalGraph();
        ComputeLoopFrequenciesClosure.compute(graph);
    }

    @SuppressWarnings("try")
    protected void build(FixedWithNextNode startInstruction, FrameStateBuilder startFrameState) {
        if (PrintProfilingInformation.getValue(options) && profilingInfo != null) {
            TTY.println("Profiling info for " + method.format("%H.%n(%p)"));
            TTY.println(Util.indent(profilingInfo.toString(method, CodeUtil.NEW_LINE), "  "));
        }

        try (Indent indent = debug.logAndIndent("build graph for %s", method)) {
            if (bytecodeProvider.shouldRecordMethodDependencies()) {
                assert getParent() != null || method.equals(graph.method());
                // Record method dependency in the graph
                graph.recordMethod(method);
            }

            // compute the block map, setup exception handlers and get the entrypoint(s)
            BciBlockMapping newMapping = BciBlockMapping.create(stream, code, options, graph.getDebug());
            this.blockMap = newMapping;
            this.firstInstructionArray = new FixedWithNextNode[blockMap.getBlockCount()];
            this.entryStateArray = new FrameStateBuilder[blockMap.getBlockCount()];
            if (!method.isStatic()) {
                originalReceiver = startFrameState.loadLocal(0, JavaKind.Object);
            }

            /*
             * Configure the assertion checking behavior of the FrameStateBuilder. This needs to be
             * done only when assertions are enabled, so it is wrapped in an assertion itself.
             */
            assert computeKindVerification(startFrameState);

            try (DebugContext.Scope s = debug.scope("LivenessAnalysis")) {
                int maxLocals = method.getMaxLocals();
                liveness = LocalLiveness.compute(debug, stream, blockMap.getBlocks(), maxLocals, blockMap.getLoopCount());
            } catch (Throwable e) {
                throw debug.handle(e);
            }

            lastInstr = startInstruction;
            this.setCurrentFrameState(startFrameState);
            stream.setBCI(0);

            BciBlock startBlock = blockMap.getStartBlock();
            if (this.parent == null) {
                StartNode startNode = graph.start();
                if (method.isSynchronized()) {
                    assert !parsingIntrinsic();
                    startNode.setStateAfter(createFrameState(BytecodeFrame.BEFORE_BCI, startNode));
                } else {
                    if (!parsingIntrinsic()) {
                        if (graph.method() != null && graph.method().isJavaLangObjectInit()) {
                            /*
                             * Don't clear the receiver when Object.<init> is the compilation root.
                             * The receiver is needed as input to RegisterFinalizerNode.
                             */
                        } else {
                            frameState.clearNonLiveLocals(startBlock, liveness, true);
                        }
                        assert bci() == 0;
                        startNode.setStateAfter(createFrameState(bci(), startNode));
                    } else {
                        if (startNode.stateAfter() == null) {
                            FrameState stateAfterStart = createStateAfterStartOfReplacementGraph();
                            startNode.setStateAfter(stateAfterStart);
                        }
                    }
                }
            }

            try (DebugCloseable context = openNodeContext()) {
                if (method.isSynchronized()) {
                    finishPrepare(lastInstr, BytecodeFrame.BEFORE_BCI, frameState);

                    // add a monitor enter to the start block
                    methodSynchronizedObject = synchronizedObject(frameState, method);
                    frameState.clearNonLiveLocals(startBlock, liveness, true);
                    assert bci() == 0;
                    genMonitorEnter(methodSynchronizedObject, bci());
                }

                ProfilingPlugin profilingPlugin = this.graphBuilderConfig.getPlugins().getProfilingPlugin();
                if (profilingPlugin != null && profilingPlugin.shouldProfile(this, method)) {
                    FrameState stateBefore = createCurrentFrameState();
                    profilingPlugin.profileInvoke(this, method, stateBefore);
                }

                finishPrepare(lastInstr, 0, frameState);

                genInfoPointNode(InfopointReason.METHOD_START, null);
            }

            currentBlock = blockMap.getStartBlock();
            setEntryState(startBlock, frameState);
            if (startBlock.isLoopHeader()) {
                appendGoto(startBlock);
            } else {
                setFirstInstruction(startBlock, lastInstr);
            }

            BciBlock[] blocks = blockMap.getBlocks();
            for (BciBlock block : blocks) {
                processBlock(block);
            }
        }
    }

    private boolean computeKindVerification(FrameStateBuilder startFrameState) {
        if (blockMap.hasJsrBytecodes) {
            /*
             * The JSR return address is an int value, but stored using the astore bytecode. Instead
             * of weakening the kind assertion checking for all methods, we disable it completely
             * for methods that contain a JSR bytecode.
             */
            startFrameState.disableKindVerification();
        }

        for (NodePlugin plugin : graphBuilderConfig.getPlugins().getNodePlugins()) {
            if (plugin.canChangeStackKind(this)) {
                /*
                 * We have a plugin that can change the kind of values, so no kind assertion
                 * checking is possible.
                 */
                startFrameState.disableKindVerification();
            }
        }
        return true;
    }

    /**
     * Hook for subclasses to modify synthetic code (start nodes and unwind nodes).
     *
     * @param instruction the current last instruction
     * @param bci the current bci
     * @param state The current frame state.
     */
    protected void finishPrepare(FixedWithNextNode instruction, int bci, FrameStateBuilder state) {
    }

    protected void cleanupFinalGraph() {
        GraphUtil.normalizeLoops(graph);

        // Remove dead parameters.
        for (ParameterNode param : graph.getNodes(ParameterNode.TYPE)) {
            if (param.hasNoUsages()) {
                assert param.inputs().isEmpty();
                param.safeDelete();
            }
        }

        // Remove redundant begin nodes.
        for (BeginNode beginNode : graph.getNodes(BeginNode.TYPE)) {
            Node predecessor = beginNode.predecessor();
            if (predecessor instanceof ControlSplitNode) {
                // The begin node is necessary.
            } else if (!beginNode.hasUsages()) {
                GraphUtil.unlinkFixedNode(beginNode);
                beginNode.safeDelete();
            }
        }
    }

    /**
     * Creates the frame state after the start node of a graph for an {@link IntrinsicContext
     * intrinsic} that is the parse root (either for root compiling or for post-parse inlining).
     */
    private FrameState createStateAfterStartOfReplacementGraph() {
        assert parent == null;
        assert frameState.getMethod().equals(intrinsicContext.getIntrinsicMethod());
        assert bci() == 0;
        assert frameState.stackSize() == 0;
        FrameState stateAfterStart;
        if (intrinsicContext.isPostParseInlined()) {
            stateAfterStart = graph.add(new FrameState(BytecodeFrame.BEFORE_BCI));
        } else {
            ResolvedJavaMethod original = intrinsicContext.getOriginalMethod();
            ValueNode[] locals;
            if (original.getMaxLocals() == frameState.localsSize() || original.isNative()) {
                locals = new ValueNode[original.getMaxLocals()];
                for (int i = 0; i < locals.length; i++) {
                    ValueNode node = frameState.locals[i];
                    if (node == FrameState.TWO_SLOT_MARKER) {
                        node = null;
                    }
                    locals[i] = node;
                }
            } else {
                locals = new ValueNode[original.getMaxLocals()];
                int parameterCount = original.getSignature().getParameterCount(!original.isStatic());
                for (int i = 0; i < parameterCount; i++) {
                    ValueNode param = frameState.locals[i];
                    if (param == FrameState.TWO_SLOT_MARKER) {
                        param = null;
                    }
                    locals[i] = param;
                    assert param == null || param instanceof ParameterNode || param.isConstant();
                }
            }
            ValueNode[] stack = {};
            int stackSize = 0;
            ValueNode[] locks = {};
            List<MonitorIdNode> monitorIds = Collections.emptyList();
            stateAfterStart = graph.add(new FrameState(null, new ResolvedJavaMethodBytecode(original), 0, locals, stack, stackSize, locks, monitorIds, false, false));
        }
        return stateAfterStart;
    }

    /**
     * @param type the unresolved type of the constant
     */
    protected void handleUnresolvedLoadConstant(JavaType type) {
        assert !graphBuilderConfig.unresolvedIsError();
        DeoptimizeNode deopt = append(new DeoptimizeNode(InvalidateRecompile, Unresolved));
        /*
         * Track source position for deopt nodes even if
         * GraphBuilderConfiguration.trackNodeSourcePosition is not set.
         */
        deopt.updateNodeSourcePosition(() -> createBytecodePosition());
    }

    /**
     * @param type the unresolved type of the type check
     * @param object the object value whose type is being checked against {@code type}
     */
    protected void handleUnresolvedCheckCast(JavaType type, ValueNode object) {
        assert !graphBuilderConfig.unresolvedIsError();
        append(new FixedGuardNode(graph.addOrUniqueWithInputs(IsNullNode.create(object)), Unresolved, InvalidateRecompile));
        frameState.push(JavaKind.Object, appendConstant(JavaConstant.NULL_POINTER));
    }

    /**
     * @param type the unresolved type of the type check
     * @param object the object value whose type is being checked against {@code type}
     */
    protected void handleUnresolvedInstanceOf(JavaType type, ValueNode object) {
        assert !graphBuilderConfig.unresolvedIsError();
        AbstractBeginNode successor = graph.add(new BeginNode());
        DeoptimizeNode deopt = graph.add(new DeoptimizeNode(InvalidateRecompile, Unresolved));
        deopt.updateNodeSourcePosition(() -> createBytecodePosition());
        append(new IfNode(graph.addOrUniqueWithInputs(IsNullNode.create(object)), successor, deopt, 1));
        lastInstr = successor;
        frameState.push(JavaKind.Int, appendConstant(JavaConstant.INT_0));
    }

    /**
     * @param type the type being instantiated
     */
    protected void handleUnresolvedNewInstance(JavaType type) {
        assert !graphBuilderConfig.unresolvedIsError();
        DeoptimizeNode deopt = append(new DeoptimizeNode(InvalidateRecompile, Unresolved));
        deopt.updateNodeSourcePosition(() -> createBytecodePosition());
    }

    /**
     * @param type the type being instantiated
     */
    protected void handleIllegalNewInstance(JavaType type) {
        assert !graphBuilderConfig.unresolvedIsError();
        DeoptimizeNode deopt = append(new DeoptimizeNode(InvalidateRecompile, Unresolved));
        deopt.updateNodeSourcePosition(() -> createBytecodePosition());
    }

    /**
     * @param type the type of the array being instantiated
     * @param length the length of the array
     */
    protected void handleUnresolvedNewObjectArray(JavaType type, ValueNode length) {
        assert !graphBuilderConfig.unresolvedIsError();
        DeoptimizeNode deopt = append(new DeoptimizeNode(InvalidateRecompile, Unresolved));
        deopt.updateNodeSourcePosition(() -> createBytecodePosition());
    }

    /**
     * @param type the type being instantiated
     * @param dims the dimensions for the multi-array
     */
    protected void handleUnresolvedNewMultiArray(JavaType type, ValueNode[] dims) {
        assert !graphBuilderConfig.unresolvedIsError();
        DeoptimizeNode deopt = append(new DeoptimizeNode(InvalidateRecompile, Unresolved));
        deopt.updateNodeSourcePosition(() -> createBytecodePosition());
    }

    /**
     * @param field the unresolved field
     * @param receiver the object containing the field or {@code null} if {@code field} is static
     */
    protected void handleUnresolvedLoadField(JavaField field, ValueNode receiver) {
        assert !graphBuilderConfig.unresolvedIsError();
        DeoptimizeNode deopt = append(new DeoptimizeNode(InvalidateRecompile, Unresolved));
        deopt.updateNodeSourcePosition(() -> createBytecodePosition());
    }

    /**
     * @param field the unresolved field
     * @param value the value being stored to the field
     * @param receiver the object containing the field or {@code null} if {@code field} is static
     */
    protected void handleUnresolvedStoreField(JavaField field, ValueNode value, ValueNode receiver) {
        assert !graphBuilderConfig.unresolvedIsError();
        DeoptimizeNode deopt = append(new DeoptimizeNode(InvalidateRecompile, Unresolved));
        deopt.updateNodeSourcePosition(() -> createBytecodePosition());
    }

    /**
     * @param type
     */
    protected void handleUnresolvedExceptionType(JavaType type) {
        assert !graphBuilderConfig.unresolvedIsError();
        DeoptimizeNode deopt = append(new DeoptimizeNode(InvalidateRecompile, Unresolved));
        deopt.updateNodeSourcePosition(() -> createBytecodePosition());
    }

    /**
     * @param javaMethod
     * @param invokeKind
     */
    protected void handleUnresolvedInvoke(JavaMethod javaMethod, InvokeKind invokeKind) {
        assert !graphBuilderConfig.unresolvedIsError();
        DeoptimizeNode deopt = append(new DeoptimizeNode(InvalidateRecompile, Unresolved));
        deopt.updateNodeSourcePosition(() -> createBytecodePosition());
    }

    /**
     * @return the entry point to exception dispatch
     */
    private AbstractBeginNode handleException(ValueNode exceptionObject, int bci, boolean deoptimizeOnly) {
        FixedWithNextNode currentLastInstr = lastInstr;
        assert bci == BytecodeFrame.BEFORE_BCI || bci == bci() : "invalid bci";
        debug.log("Creating exception dispatch edges at %d, exception object=%s, exception seen=%s", bci, exceptionObject, (profilingInfo == null ? "" : profilingInfo.getExceptionSeen(bci)));

        FrameStateBuilder dispatchState = frameState.copy();
        dispatchState.clearStack();

        AbstractBeginNode dispatchBegin;
        if (exceptionObject == null) {
            ExceptionObjectNode newExceptionObject = graph.add(new ExceptionObjectNode(metaAccess));
            dispatchBegin = newExceptionObject;
            dispatchState.push(JavaKind.Object, dispatchBegin);
            dispatchState.setRethrowException(true);
            newExceptionObject.setStateAfter(dispatchState.create(bci, newExceptionObject));
        } else {
            dispatchBegin = graph.add(new BeginNode());
            dispatchState.push(JavaKind.Object, exceptionObject);
            dispatchState.setRethrowException(true);
        }
        this.controlFlowSplit = true;
        FixedWithNextNode afterExceptionLoaded = finishInstruction(dispatchBegin, dispatchState);

        if (deoptimizeOnly) {
            DeoptimizeNode deoptimizeNode = graph.add(new DeoptimizeNode(DeoptimizationAction.None, DeoptimizationReason.TransferToInterpreter));
            afterExceptionLoaded.setNext(BeginNode.begin(deoptimizeNode));
        } else {
            createHandleExceptionTarget(afterExceptionLoaded, bci, dispatchState);
        }
        assert currentLastInstr == lastInstr;
        return dispatchBegin;
    }

    protected void createHandleExceptionTarget(FixedWithNextNode afterExceptionLoaded, int bci, FrameStateBuilder dispatchState) {
        FixedWithNextNode afterInstrumentation = afterExceptionLoaded;
        for (NodePlugin plugin : graphBuilderConfig.getPlugins().getNodePlugins()) {
            afterInstrumentation = plugin.instrumentExceptionDispatch(graph, afterInstrumentation);
            assert afterInstrumentation.next() == null : "exception dispatch instrumentation will be linked to dispatch block";
        }

        BciBlock dispatchBlock = currentBlock.exceptionDispatchBlock();
        /*
         * The exception dispatch block is always for the last bytecode of a block, so if we are not
         * at the endBci yet, there is no exception handler for this bci and we can unwind
         * immediately.
         */
        if (bci != currentBlock.endBci || dispatchBlock == null) {
            dispatchBlock = blockMap.getUnwindBlock();
        }

        FixedNode target = createTarget(dispatchBlock, dispatchState);
        afterInstrumentation.setNext(target);
    }

    protected ValueNode genLoadIndexed(ValueNode array, ValueNode index, GuardingNode boundsCheck, JavaKind kind) {
        return LoadIndexedNode.create(graph.getAssumptions(), array, index, boundsCheck, kind, metaAccess, constantReflection);
    }

    protected void genStoreIndexed(ValueNode array, ValueNode index, GuardingNode boundsCheck, GuardingNode storeCheck, JavaKind kind, ValueNode value) {
        add(new StoreIndexedNode(array, index, boundsCheck, storeCheck, kind, value));
    }

    protected ValueNode genIntegerAdd(ValueNode x, ValueNode y) {
        return AddNode.create(x, y, NodeView.DEFAULT);
    }

    protected ValueNode genIntegerSub(ValueNode x, ValueNode y) {
        return SubNode.create(x, y, NodeView.DEFAULT);
    }

    protected ValueNode genIntegerMul(ValueNode x, ValueNode y) {
        return MulNode.create(x, y, NodeView.DEFAULT);
    }

    protected ValueNode genFloatAdd(ValueNode x, ValueNode y) {
        return AddNode.create(x, y, NodeView.DEFAULT);
    }

    protected ValueNode genFloatSub(ValueNode x, ValueNode y) {
        return SubNode.create(x, y, NodeView.DEFAULT);
    }

    protected ValueNode genFloatMul(ValueNode x, ValueNode y) {
        return MulNode.create(x, y, NodeView.DEFAULT);
    }

    protected ValueNode genFloatDiv(ValueNode x, ValueNode y) {
        return FloatDivNode.create(x, y, NodeView.DEFAULT);
    }

    protected ValueNode genFloatRem(ValueNode x, ValueNode y) {
        return RemNode.create(x, y, NodeView.DEFAULT);
    }

    protected ValueNode genIntegerDiv(ValueNode x, ValueNode y, GuardingNode zeroCheck) {
        return SignedDivNode.create(x, y, zeroCheck, NodeView.DEFAULT);
    }

    protected ValueNode genIntegerRem(ValueNode x, ValueNode y, GuardingNode zeroCheck) {
        return SignedRemNode.create(x, y, zeroCheck, NodeView.DEFAULT);
    }

    protected ValueNode genNegateOp(ValueNode x) {
        return NegateNode.create(x, NodeView.DEFAULT);
    }

    protected ValueNode genLeftShift(ValueNode x, ValueNode y) {
        return LeftShiftNode.create(x, y, NodeView.DEFAULT);
    }

    protected ValueNode genRightShift(ValueNode x, ValueNode y) {
        return RightShiftNode.create(x, y, NodeView.DEFAULT);
    }

    protected ValueNode genUnsignedRightShift(ValueNode x, ValueNode y) {
        return UnsignedRightShiftNode.create(x, y, NodeView.DEFAULT);
    }

    protected ValueNode genAnd(ValueNode x, ValueNode y) {
        return AndNode.create(x, y, NodeView.DEFAULT);
    }

    protected ValueNode genOr(ValueNode x, ValueNode y) {
        return OrNode.create(x, y, NodeView.DEFAULT);
    }

    protected ValueNode genXor(ValueNode x, ValueNode y) {
        return XorNode.create(x, y, NodeView.DEFAULT);
    }

    protected ValueNode genNormalizeCompare(ValueNode x, ValueNode y, boolean isUnorderedLess) {
        return NormalizeCompareNode.create(x, y, isUnorderedLess, JavaKind.Int, constantReflection);
    }

    protected ValueNode genFloatConvert(FloatConvert op, ValueNode input) {
        return FloatConvertNode.create(op, input, NodeView.DEFAULT);
    }

    protected ValueNode genNarrow(ValueNode input, int bitCount) {
        return NarrowNode.create(input, bitCount, NodeView.DEFAULT);
    }

    protected ValueNode genSignExtend(ValueNode input, int bitCount) {
        return SignExtendNode.create(input, bitCount, NodeView.DEFAULT);
    }

    protected ValueNode genZeroExtend(ValueNode input, int bitCount) {
        return ZeroExtendNode.create(input, bitCount, NodeView.DEFAULT);
    }

    protected void genGoto() {
        ProfilingPlugin profilingPlugin = this.graphBuilderConfig.getPlugins().getProfilingPlugin();
        if (profilingPlugin != null && profilingPlugin.shouldProfile(this, method)) {
            FrameState stateBefore = createCurrentFrameState();
            int targetBci = currentBlock.getSuccessor(0).startBci;
            profilingPlugin.profileGoto(this, method, bci(), targetBci, stateBefore);
        }
        appendGoto(currentBlock.getSuccessor(0));
        assert currentBlock.numNormalSuccessors() == 1;
    }

    protected LogicNode genObjectEquals(ValueNode x, ValueNode y) {
        return ObjectEqualsNode.create(constantReflection, metaAccess, options, x, y, NodeView.DEFAULT);
    }

    protected LogicNode genIntegerEquals(ValueNode x, ValueNode y) {
        return IntegerEqualsNode.create(constantReflection, metaAccess, options, null, x, y, NodeView.DEFAULT);
    }

    protected LogicNode genIntegerLessThan(ValueNode x, ValueNode y) {
        return IntegerLessThanNode.create(constantReflection, metaAccess, options, null, x, y, NodeView.DEFAULT);
    }

    protected ValueNode genUnique(ValueNode x) {
        return graph.addOrUniqueWithInputs(x);
    }

    protected LogicNode genUnique(LogicNode x) {
        return graph.addOrUniqueWithInputs(x);
    }

    protected ValueNode genIfNode(LogicNode condition, FixedNode falseSuccessor, FixedNode trueSuccessor, double d) {
        return new IfNode(condition, falseSuccessor, trueSuccessor, d);
    }

    protected void genThrow() {
        genInfoPointNode(InfopointReason.BYTECODE_POSITION, null);

        ValueNode exception = maybeEmitExplicitNullCheck(frameState.pop(JavaKind.Object));
        if (!StampTool.isPointerNonNull(exception.stamp(NodeView.DEFAULT))) {
            FixedGuardNode nullCheck = append(new FixedGuardNode(graph.addOrUniqueWithInputs(IsNullNode.create(exception)), NullCheckException, InvalidateReprofile, true));
            exception = graph.maybeAddOrUnique(PiNode.create(exception, exception.stamp(NodeView.DEFAULT).join(objectNonNull()), nullCheck));
        }
        lastInstr.setNext(handleException(exception, bci(), false));
    }

    protected LogicNode createInstanceOf(TypeReference type, ValueNode object) {
        return InstanceOfNode.create(type, object);
    }

    protected AnchoringNode createAnchor(JavaTypeProfile profile) {
        if (profile == null || profile.getNotRecordedProbability() > 0.0) {
            return null;
        } else {
            return append(new ValueAnchorNode(null));
        }
    }

    protected LogicNode createInstanceOf(TypeReference type, ValueNode object, JavaTypeProfile profile) {
        return InstanceOfNode.create(type, object, profile, createAnchor(profile));
    }

    protected LogicNode createInstanceOfAllowNull(TypeReference type, ValueNode object, JavaTypeProfile profile) {
        return InstanceOfNode.createAllowNull(type, object, profile, createAnchor(profile));
    }

    protected ValueNode genConditional(ValueNode x) {
        return ConditionalNode.create((LogicNode) x, NodeView.DEFAULT);
    }

    protected NewInstanceNode createNewInstance(ResolvedJavaType type, boolean fillContents) {
        return new NewInstanceNode(type, fillContents);
    }

    protected NewArrayNode createNewArray(ResolvedJavaType elementType, ValueNode length, boolean fillContents) {
        return new NewArrayNode(elementType, length, fillContents);
    }

    protected NewMultiArrayNode createNewMultiArray(ResolvedJavaType type, ValueNode[] dimensions) {
        return new NewMultiArrayNode(type, dimensions);
    }

    protected ValueNode genLoadField(ValueNode receiver, ResolvedJavaField field) {
        StampPair stamp = graphBuilderConfig.getPlugins().getOverridingStamp(this, field.getType(), false);
        if (stamp == null) {
            return LoadFieldNode.create(getConstantFieldProvider(), getConstantReflection(), getMetaAccess(), getOptions(),
                            getAssumptions(), receiver, field, false, false);
        } else {
            return LoadFieldNode.createOverrideStamp(getConstantFieldProvider(), getConstantReflection(), getMetaAccess(), getOptions(),
                            stamp, receiver, field, false, false);
        }
    }

    protected StateSplitProxyNode genVolatileFieldReadProxy(ValueNode fieldRead) {
        return new StateSplitProxyNode(fieldRead);
    }

    protected ValueNode maybeEmitExplicitNullCheck(ValueNode receiver) {
        if (StampTool.isPointerNonNull(receiver.stamp(NodeView.DEFAULT)) || !needsExplicitNullCheckException(receiver)) {
            return receiver;
        }
        LogicNode condition = genUnique(IsNullNode.create(receiver));
        AbstractBeginNode passingSuccessor = emitBytecodeExceptionCheck(condition, false, BytecodeExceptionKind.NULL_POINTER);
        return genUnique(PiNode.create(receiver, objectNonNull(), passingSuccessor));
    }

    protected GuardingNode maybeEmitExplicitBoundsCheck(ValueNode receiver, ValueNode index) {
        if (!needsExplicitBoundsCheckException(receiver, index)) {
            return null;
        }
        ValueNode length = append(genArrayLength(receiver));
        LogicNode condition = genUnique(IntegerBelowNode.create(constantReflection, metaAccess, options, null, index, length, NodeView.DEFAULT));
        return emitBytecodeExceptionCheck(condition, true, BytecodeExceptionKind.OUT_OF_BOUNDS, index, length);
    }

    protected GuardingNode maybeEmitExplicitStoreCheck(ValueNode array, JavaKind elementKind, ValueNode value) {
        if (elementKind != JavaKind.Object || StampTool.isPointerAlwaysNull(value) || !needsExplicitStoreCheckException(array, value)) {
            return null;
        }
        ValueNode arrayClass = genUnique(LoadHubNode.create(array, stampProvider, metaAccess, constantReflection));
        ValueNode componentHub = append(LoadArrayComponentHubNode.create(arrayClass, stampProvider, metaAccess, constantReflection));
        LogicNode condition = genUnique(InstanceOfDynamicNode.create(graph.getAssumptions(), getConstantReflection(), componentHub, value, true));
        return emitBytecodeExceptionCheck(condition, true, BytecodeExceptionKind.ARRAY_STORE, value);
    }

    protected GuardingNode maybeEmitExplicitDivisionByZeroCheck(ValueNode y) {
        if (!((IntegerStamp) y.stamp(NodeView.DEFAULT)).contains(0) || !needsExplicitDivisionByZeroException(y)) {
            return null;
        }
        ConstantNode zero = ConstantNode.defaultForKind(y.getStackKind(), graph);
        LogicNode condition = genUnique(IntegerEqualsNode.create(constantReflection, metaAccess, options, null, y, zero, NodeView.DEFAULT));
        return emitBytecodeExceptionCheck(condition, false, BytecodeExceptionKind.DIVISION_BY_ZERO);
    }

    private AbstractBeginNode emitBytecodeExceptionCheck(LogicNode condition, boolean passingOnTrue, BytecodeExceptionKind exceptionKind, ValueNode... arguments) {
        if (passingOnTrue ? condition.isTautology() : condition.isContradiction()) {
            return null;
        }

        BytecodeExceptionNode exception = graph.add(new BytecodeExceptionNode(metaAccess, exceptionKind, arguments));
        AbstractBeginNode passingSuccessor = graph.add(new BeginNode());

        FixedNode trueSuccessor = passingOnTrue ? passingSuccessor : exception;
        FixedNode falseSuccessor = passingOnTrue ? exception : passingSuccessor;
        append(new IfNode(condition, trueSuccessor, falseSuccessor, passingOnTrue ? LUDICROUSLY_FAST_PATH_PROBABILITY : LUDICROUSLY_SLOW_PATH_PROBABILITY));
        lastInstr = passingSuccessor;

        exception.setStateAfter(createFrameState(bci(), exception));
        exception.setNext(handleException(exception, bci(), false));
        EXPLICIT_EXCEPTIONS.increment(debug);

        return passingSuccessor;
    }

    protected ValueNode genArrayLength(ValueNode x) {
        return ArrayLengthNode.create(x, constantReflection);
    }

    protected void genStoreField(ValueNode receiver, ResolvedJavaField field, ValueNode value) {
        StoreFieldNode storeFieldNode = new StoreFieldNode(receiver, field, maskSubWordValue(value, field.getJavaKind()));
        append(storeFieldNode);
        storeFieldNode.setStateAfter(this.createFrameState(stream.nextBCI(), storeFieldNode));
    }

    /**
     * Ensure that concrete classes are at least linked before generating an invoke. Interfaces may
     * never be linked so simply return true for them.
     *
     * @param target
     * @return true if the declared holder is an interface or is linked
     */
    private static boolean callTargetIsResolved(JavaMethod target) {
        if (target instanceof ResolvedJavaMethod) {
            ResolvedJavaMethod resolvedTarget = (ResolvedJavaMethod) target;
            ResolvedJavaType resolvedType = resolvedTarget.getDeclaringClass();
            return resolvedType.isInterface() || resolvedType.isLinked();
        }
        return false;
    }

    /**
     * Check if a type is resolved. Can be overwritten by sub-classes to implement different type
     * resolution rules.
     */
    protected boolean typeIsResolved(JavaType type) {
        return type instanceof ResolvedJavaType;
    }

    protected void genInvokeStatic(int cpi, int opcode) {
        JavaMethod target = lookupMethod(cpi, opcode);
        assert !uninitializedIsError ||
                        (target instanceof ResolvedJavaMethod && ((ResolvedJavaMethod) target).getDeclaringClass().isInitialized()) : target;
        genInvokeStatic(target);
    }

    void genInvokeStatic(JavaMethod target) {
        if (callTargetIsResolved(target)) {
            ResolvedJavaMethod resolvedTarget = (ResolvedJavaMethod) target;
            ResolvedJavaType holder = resolvedTarget.getDeclaringClass();
            maybeEagerlyInitialize(holder);
            ClassInitializationPlugin classInitializationPlugin = graphBuilderConfig.getPlugins().getClassInitializationPlugin();
            if (!holder.isInitialized() && classInitializationPlugin == null) {
                handleUnresolvedInvoke(target, InvokeKind.Static);
                return;
            }

            ValueNode[] classInit = {null};
            if (classInitializationPlugin != null) {
                classInitializationPlugin.apply(this, resolvedTarget.getDeclaringClass(), this::createCurrentFrameState, classInit);
            }

            ValueNode[] args = frameState.popArguments(resolvedTarget.getSignature().getParameterCount(false));
            Invoke invoke = appendInvoke(InvokeKind.Static, resolvedTarget, args);
            if (invoke != null && classInit[0] != null) {
                invoke.setClassInit(classInit[0]);
            }
        } else {
            handleUnresolvedInvoke(target, InvokeKind.Static);
        }
    }

    /**
     * Creates a frame state for the current parse position.
     */
    private FrameState createCurrentFrameState() {
        return frameState.create(bci(), getNonIntrinsicAncestor(), false, null, null);
    }

    protected void genInvokeInterface(int cpi, int opcode) {
        JavaMethod target = lookupMethod(cpi, opcode);
        genInvokeInterface(target);
    }

    protected void genInvokeInterface(JavaMethod target) {
        if (callTargetIsResolved(target)) {
            ValueNode[] args = frameState.popArguments(target.getSignature().getParameterCount(true));
            appendInvoke(InvokeKind.Interface, (ResolvedJavaMethod) target, args);
        } else {
            handleUnresolvedInvoke(target, InvokeKind.Interface);
        }
    }

    protected void genInvokeDynamic(int cpi, int opcode) {
        JavaMethod target = lookupMethod(cpi, opcode);
        genInvokeDynamic(target);
    }

    void genInvokeDynamic(JavaMethod target) {
        if (!(target instanceof ResolvedJavaMethod) || !genDynamicInvokeHelper((ResolvedJavaMethod) target, stream.readCPI4(), INVOKEDYNAMIC)) {
            handleUnresolvedInvoke(target, InvokeKind.Static);
        }
    }

    protected void genInvokeVirtual(int cpi, int opcode) {
        JavaMethod target = lookupMethod(cpi, opcode);
        if (callTargetIsResolved(target)) {
            genInvokeVirtual((ResolvedJavaMethod) target);
        } else {
            handleUnresolvedInvoke(target, InvokeKind.Virtual);
        }
    }

    protected void genInvokeVirtual(ResolvedJavaMethod resolvedTarget) {
        int cpi = stream.readCPI();

        /*
         * Special handling for runtimes that rewrite an invocation of MethodHandle.invoke(...) or
         * MethodHandle.invokeExact(...) to a static adapter. HotSpot does this - see
         * https://wiki.openjdk.java.net/display/HotSpot/Method+handles+and+invokedynamic
         */

        if (genDynamicInvokeHelper(resolvedTarget, cpi, INVOKEVIRTUAL)) {
            return;
        }

        ValueNode[] args = frameState.popArguments(resolvedTarget.getSignature().getParameterCount(true));
        appendInvoke(InvokeKind.Virtual, resolvedTarget, args);
    }

    private boolean genDynamicInvokeHelper(ResolvedJavaMethod target, int cpi, int opcode) {
        assert opcode == INVOKEDYNAMIC || opcode == INVOKEVIRTUAL;

        InvokeDynamicPlugin invokeDynamicPlugin = graphBuilderConfig.getPlugins().getInvokeDynamicPlugin();

        if (opcode == INVOKEVIRTUAL && invokeDynamicPlugin != null && !invokeDynamicPlugin.isResolvedDynamicInvoke(this, cpi, opcode)) {
            // regular invokevirtual, let caller handle it
            return false;
        }

        if (GeneratePIC.getValue(options) && (invokeDynamicPlugin == null || !invokeDynamicPlugin.supportsDynamicInvoke(this, cpi, opcode))) {
            // bail out if static compiler and no dynamic type support
            append(new DeoptimizeNode(InvalidateRecompile, Unresolved));
            return true;
        }

        JavaConstant appendix = constantPool.lookupAppendix(cpi, opcode);
        ValueNode appendixNode = null;

        if (appendix != null) {
            if (invokeDynamicPlugin != null) {
                invokeDynamicPlugin.recordDynamicMethod(this, cpi, opcode, target);

                // Will perform runtime type checks and static initialization
                FrameState stateBefore = createCurrentFrameState();
                appendixNode = invokeDynamicPlugin.genAppendixNode(this, cpi, opcode, appendix, stateBefore);
            } else {
                appendixNode = ConstantNode.forConstant(appendix, metaAccess, graph);
            }

            frameState.push(JavaKind.Object, appendixNode);

        } else if (GeneratePIC.getValue(options)) {
            // Need to emit runtime guard and perform static initialization.
            // Not implemented yet.
            append(new DeoptimizeNode(InvalidateRecompile, Unresolved));
            return true;
        }

        boolean hasReceiver = (opcode == INVOKEDYNAMIC) ? false : !target.isStatic();
        ValueNode[] args = frameState.popArguments(target.getSignature().getParameterCount(hasReceiver));
        if (hasReceiver) {
            appendInvoke(InvokeKind.Virtual, target, args);
        } else {
            appendInvoke(InvokeKind.Static, target, args);
        }

        return true;
    }

    protected void genInvokeSpecial(int cpi, int opcode) {
        JavaMethod target = lookupMethod(cpi, opcode);
        genInvokeSpecial(target);
    }

    void genInvokeSpecial(JavaMethod target) {
        if (callTargetIsResolved(target)) {
            assert target != null;
            assert target.getSignature() != null;
            ValueNode[] args = frameState.popArguments(target.getSignature().getParameterCount(true));
            appendInvoke(InvokeKind.Special, (ResolvedJavaMethod) target, args);
        } else {
            handleUnresolvedInvoke(target, InvokeKind.Special);
        }
    }

    static class CurrentInvoke {
        final ValueNode[] args;
        final InvokeKind kind;
        final JavaType returnType;

        CurrentInvoke(ValueNode[] args, InvokeKind kind, JavaType returnType) {
            this.args = args;
            this.kind = kind;
            this.returnType = returnType;
        }
    }

    private CurrentInvoke currentInvoke;
    protected FrameStateBuilder frameState;
    protected BciBlock currentBlock;
    protected final BytecodeStream stream;
    protected final GraphBuilderConfiguration graphBuilderConfig;
    protected final ResolvedJavaMethod method;
    protected final Bytecode code;
    protected final BytecodeProvider bytecodeProvider;
    protected final ProfilingInfo profilingInfo;
    protected final OptimisticOptimizations optimisticOpts;
    protected final ConstantPool constantPool;
    protected final MetaAccessProvider metaAccess;
    private final ConstantReflectionProvider constantReflection;
    private final ConstantFieldProvider constantFieldProvider;
    private final StampProvider stampProvider;
    protected final IntrinsicContext intrinsicContext;

    @Override
    public InvokeKind getInvokeKind() {
        return currentInvoke == null ? null : currentInvoke.kind;
    }

    @Override
    public JavaType getInvokeReturnType() {
        return currentInvoke == null ? null : currentInvoke.returnType;
    }

    private boolean forceInliningEverything;

    @Override
    public void handleReplacedInvoke(InvokeKind invokeKind, ResolvedJavaMethod targetMethod, ValueNode[] args, boolean inlineEverything) {
        boolean previous = forceInliningEverything;
        forceInliningEverything = previous || inlineEverything;
        try {
            appendInvoke(invokeKind, targetMethod, args);
        } finally {
            forceInliningEverything = previous;
        }
    }

    @Override
    public void handleReplacedInvoke(CallTargetNode callTarget, JavaKind resultType) {
        BytecodeParser intrinsicCallSiteParser = getNonIntrinsicAncestor();
        ExceptionEdgeAction exceptionEdgeAction = intrinsicCallSiteParser == null ? getActionForInvokeExceptionEdge(null) : intrinsicCallSiteParser.getActionForInvokeExceptionEdge(null);
        createNonInlinedInvoke(exceptionEdgeAction, bci(), callTarget, resultType);
    }

    protected Invoke appendInvoke(InvokeKind initialInvokeKind, ResolvedJavaMethod initialTargetMethod, ValueNode[] args) {
        ResolvedJavaMethod targetMethod = initialTargetMethod;
        InvokeKind invokeKind = initialInvokeKind;
        if (initialInvokeKind.isIndirect()) {
            ResolvedJavaType contextType = this.frameState.getMethod().getDeclaringClass();
            ResolvedJavaMethod specialCallTarget = MethodCallTargetNode.findSpecialCallTarget(initialInvokeKind, args[0], initialTargetMethod, contextType);
            if (specialCallTarget != null) {
                invokeKind = InvokeKind.Special;
                targetMethod = specialCallTarget;
            }
        }

        JavaKind resultType = targetMethod.getSignature().getReturnKind();
        if (!parsingIntrinsic() && DeoptALot.getValue(options)) {
            append(new DeoptimizeNode(DeoptimizationAction.None, RuntimeConstraint));
            frameState.pushReturn(resultType, ConstantNode.defaultForKind(resultType, graph));
            return null;
        }

        JavaType returnType = targetMethod.getSignature().getReturnType(method.getDeclaringClass());
        if (graphBuilderConfig.eagerResolving() || parsingIntrinsic()) {
            returnType = returnType.resolve(targetMethod.getDeclaringClass());
        }
        if (invokeKind.hasReceiver()) {
            args[0] = maybeEmitExplicitNullCheck(args[0]);
        }

        if (initialInvokeKind == InvokeKind.Special && !targetMethod.isConstructor()) {
            emitCheckForInvokeSuperSpecial(args);
        } else if (initialInvokeKind == InvokeKind.Interface && targetMethod.isPrivate()) {
            emitCheckForDeclaringClassChange(targetMethod.getDeclaringClass(), args);
        }

        InlineInfo inlineInfo = null;
        try {
            currentInvoke = new CurrentInvoke(args, invokeKind, returnType);
            if (tryNodePluginForInvocation(args, targetMethod)) {
                if (TraceParserPlugins.getValue(options)) {
                    traceWithContext("used node plugin for %s", targetMethod.format("%h.%n(%p)"));
                }
                return null;
            }

            if (invokeKind.hasReceiver() && args[0].isNullConstant()) {
                append(new DeoptimizeNode(InvalidateRecompile, NullCheckException));
                return null;
            }

            if (!invokeKind.isIndirect() || (UseGuardedIntrinsics.getValue(options) && !GeneratePIC.getValue(options))) {
                if (tryInvocationPlugin(invokeKind, args, targetMethod, resultType, returnType)) {
                    if (TraceParserPlugins.getValue(options)) {
                        traceWithContext("used invocation plugin for %s", targetMethod.format("%h.%n(%p)"));
                    }
                    return null;
                }
            }
            if (invokeKind.isDirect()) {

                inlineInfo = tryInline(args, targetMethod);
                if (inlineInfo == SUCCESSFULLY_INLINED) {
                    return null;
                }
            }
        } finally {
            currentInvoke = null;
        }

        int invokeBci = bci();
        JavaTypeProfile profile = getProfileForInvoke(invokeKind);
        ExceptionEdgeAction edgeAction = getActionForInvokeExceptionEdge(inlineInfo);
        boolean partialIntrinsicExit = false;
        if (intrinsicContext != null && intrinsicContext.isCallToOriginal(targetMethod)) {
            partialIntrinsicExit = true;
            ResolvedJavaMethod originalMethod = intrinsicContext.getOriginalMethod();
            BytecodeParser intrinsicCallSiteParser = getNonIntrinsicAncestor();
            if (intrinsicCallSiteParser != null) {
                // When exiting a partial intrinsic, the invoke to the original
                // must use the same context as the call to the intrinsic.
                invokeBci = intrinsicCallSiteParser.bci();
                profile = intrinsicCallSiteParser.getProfileForInvoke(invokeKind);
                edgeAction = intrinsicCallSiteParser.getActionForInvokeExceptionEdge(inlineInfo);
            } else {
                // We are parsing the intrinsic for the root compilation or for inlining,
                // This call is a partial intrinsic exit, and we do not have profile information
                // for this callsite. We also have to assume that the call needs an exception
                // edge. Finally, we know that this intrinsic is parsed for late inlining,
                // so the bci must be set to unknown, so that the inliner patches it later.
                assert intrinsicContext.isPostParseInlined();
                invokeBci = BytecodeFrame.UNKNOWN_BCI;
                profile = null;
                edgeAction = graph.method().getAnnotation(Snippet.class) == null ? ExceptionEdgeAction.INCLUDE_AND_HANDLE : ExceptionEdgeAction.OMIT;
            }

            if (originalMethod.isStatic()) {
                invokeKind = InvokeKind.Static;
            } else {
                // The original call to the intrinsic must have been devirtualized
                // otherwise we wouldn't be here.
                invokeKind = InvokeKind.Special;
            }
            Signature sig = originalMethod.getSignature();
            returnType = sig.getReturnType(method.getDeclaringClass());
            resultType = sig.getReturnKind();
            assert intrinsicContext.allowPartialIntrinsicArgumentMismatch() || checkPartialIntrinsicExit(intrinsicCallSiteParser == null ? null : intrinsicCallSiteParser.currentInvoke.args, args);
            targetMethod = originalMethod;
        }
        Invoke invoke = createNonInlinedInvoke(edgeAction, invokeBci, args, targetMethod, invokeKind, resultType, returnType, profile);
        graph.getInliningLog().addDecision(invoke, false, "GraphBuilderPhase", null, null, "bytecode parser did not replace invoke");
        if (partialIntrinsicExit) {
            // This invoke must never be later inlined as it might select the intrinsic graph.
            // Until there is a mechanism to guarantee that any late inlining will not select
            // the intrinsic graph, prevent this invoke from being inlined.
            invoke.setUseForInlining(false);
        }
        return invoke;
    }

    /**
     * Checks that the class of the receiver of an {@link Bytecodes#INVOKEINTERFACE} invocation of a
     * private method is assignable to the interface that declared the method. If not, then
     * deoptimize so that the interpreter can throw an {@link IllegalAccessError}.
     *
     * This is a check not performed by the verifier and so must be performed at runtime.
     *
     * @param declaringClass interface declaring the callee
     * @param args arguments to an {@link Bytecodes#INVOKEINTERFACE} call to a private method
     *            declared in a interface
     */
    private void emitCheckForDeclaringClassChange(ResolvedJavaType declaringClass, ValueNode[] args) {
        ValueNode receiver = args[0];
        TypeReference checkedType = TypeReference.createTrusted(graph.getAssumptions(), declaringClass);
        LogicNode condition = genUnique(createInstanceOf(checkedType, receiver, null));
        FixedGuardNode fixedGuard = append(new FixedGuardNode(condition, ClassCastException, None, false));
        args[0] = append(PiNode.create(receiver, StampFactory.object(checkedType, true), fixedGuard));
    }

    /**
     * Checks that the class of the receiver of an {@link Bytecodes#INVOKESPECIAL} in a method
     * declared in an interface (i.e., a default method) is assignable to the interface. If not,
     * then deoptimize so that the interpreter can throw an {@link IllegalAccessError}.
     *
     * This is a check not performed by the verifier and so must be performed at runtime.
     *
     * @param args arguments to an {@link Bytecodes#INVOKESPECIAL} implementing a direct call to a
     *            method in a super class
     */
    protected void emitCheckForInvokeSuperSpecial(ValueNode[] args) {
        ResolvedJavaType callingClass = method.getDeclaringClass();
        if (callingClass.getHostClass() != null) {
            callingClass = callingClass.getHostClass();
        }
        if (callingClass.isInterface()) {
            ValueNode receiver = args[0];
            TypeReference checkedType = TypeReference.createTrusted(graph.getAssumptions(), callingClass);
            LogicNode condition = genUnique(createInstanceOf(checkedType, receiver, null));
            FixedGuardNode fixedGuard = append(new FixedGuardNode(condition, ClassCastException, None, false));
            args[0] = append(PiNode.create(receiver, StampFactory.object(checkedType, true), fixedGuard));
        }
    }

    protected JavaTypeProfile getProfileForInvoke(InvokeKind invokeKind) {
        if (invokeKind.isIndirect() && profilingInfo != null && this.optimisticOpts.useTypeCheckHints(getOptions())) {
            return profilingInfo.getTypeProfile(bci());
        }
        return null;
    }

    /**
     * A partial intrinsic exits by (effectively) calling the intrinsified method. This call must
     * use exactly the arguments to the call being intrinsified.
     *
     * @param originalArgs arguments of original call to intrinsified method
     * @param recursiveArgs arguments of recursive call to intrinsified method
     */
    private static boolean checkPartialIntrinsicExit(ValueNode[] originalArgs, ValueNode[] recursiveArgs) {
        if (originalArgs != null) {
            for (int i = 0; i < originalArgs.length; i++) {
                ValueNode arg = GraphUtil.unproxify(recursiveArgs[i]);
                ValueNode icArg = GraphUtil.unproxify(originalArgs[i]);
                assert arg == icArg : String.format("argument %d of call denoting partial intrinsic exit should be %s, not %s", i, icArg, arg);
            }
        } else {
            for (int i = 0; i < recursiveArgs.length; i++) {
                ValueNode arg = GraphUtil.unproxify(recursiveArgs[i]);
                assert arg instanceof ParameterNode && ((ParameterNode) arg).index() == i : String.format("argument %d of call denoting partial intrinsic exit should be a %s with index %d, not %s",
                                i, ParameterNode.class.getSimpleName(), i, arg);
            }
        }
        return true;
    }

    protected Invoke createNonInlinedInvoke(ExceptionEdgeAction exceptionEdge, int invokeBci, ValueNode[] invokeArgs, ResolvedJavaMethod targetMethod,
                    InvokeKind invokeKind, JavaKind resultType, JavaType returnType, JavaTypeProfile profile) {

        StampPair returnStamp = graphBuilderConfig.getPlugins().getOverridingStamp(this, returnType, false);
        if (returnStamp == null) {
            returnStamp = StampFactory.forDeclaredType(graph.getAssumptions(), returnType, false);
        }

        MethodCallTargetNode callTarget = graph.add(createMethodCallTarget(invokeKind, targetMethod, invokeArgs, returnStamp, profile));
        Invoke invoke = createNonInlinedInvoke(exceptionEdge, invokeBci, callTarget, resultType);

        for (InlineInvokePlugin plugin : graphBuilderConfig.getPlugins().getInlineInvokePlugins()) {
            plugin.notifyNotInlined(this, targetMethod, invoke);
        }

        return invoke;
    }

    protected Invoke createNonInlinedInvoke(ExceptionEdgeAction exceptionEdge, int invokeBci, CallTargetNode callTarget, JavaKind resultType) {
        if (exceptionEdge == ExceptionEdgeAction.OMIT) {
            return createInvoke(invokeBci, callTarget, resultType);
        } else {
            Invoke invoke = createInvokeWithException(invokeBci, callTarget, resultType, exceptionEdge);
            AbstractBeginNode beginNode = graph.add(KillingBeginNode.create(LocationIdentity.any()));
            invoke.setNext(beginNode);
            lastInstr = beginNode;
            return invoke;
        }
    }

    /**
     * Describes what should be done with the exception edge of an invocation. The edge can be
     * omitted or included. An included edge can handle the exception or transfer execution to the
     * interpreter for handling (deoptimize).
     */
    protected enum ExceptionEdgeAction {
        OMIT,
        INCLUDE_AND_HANDLE,
        INCLUDE_AND_DEOPTIMIZE
    }

    protected ExceptionEdgeAction getActionForInvokeExceptionEdge(InlineInfo lastInlineInfo) {
        if (lastInlineInfo == InlineInfo.DO_NOT_INLINE_WITH_EXCEPTION) {
            return ExceptionEdgeAction.INCLUDE_AND_HANDLE;
        } else if (lastInlineInfo == InlineInfo.DO_NOT_INLINE_NO_EXCEPTION) {
            return ExceptionEdgeAction.OMIT;
        } else if (lastInlineInfo == InlineInfo.DO_NOT_INLINE_DEOPTIMIZE_ON_EXCEPTION) {
            return ExceptionEdgeAction.INCLUDE_AND_DEOPTIMIZE;
        } else if (graphBuilderConfig.getBytecodeExceptionMode() == BytecodeExceptionMode.CheckAll) {
            return ExceptionEdgeAction.INCLUDE_AND_HANDLE;
        } else if (graphBuilderConfig.getBytecodeExceptionMode() == BytecodeExceptionMode.ExplicitOnly) {
            return ExceptionEdgeAction.INCLUDE_AND_HANDLE;
        } else if (graphBuilderConfig.getBytecodeExceptionMode() == BytecodeExceptionMode.OmitAll) {
            return ExceptionEdgeAction.OMIT;
        } else {
            assert graphBuilderConfig.getBytecodeExceptionMode() == BytecodeExceptionMode.Profile;
            // be conservative if information was not recorded (could result in endless
            // recompiles otherwise)
            if (!StressInvokeWithExceptionNode.getValue(options)) {
                if (optimisticOpts.useExceptionProbability(getOptions())) {
                    if (profilingInfo != null) {
                        TriState exceptionSeen = profilingInfo.getExceptionSeen(bci());
                        if (exceptionSeen == TriState.FALSE) {
                            return ExceptionEdgeAction.OMIT;
                        }
                    }
                }
            }
            return ExceptionEdgeAction.INCLUDE_AND_HANDLE;
        }
    }

    /**
     * Contains all the assertion checking logic around the application of an
     * {@link InvocationPlugin}. This class is only loaded when assertions are enabled.
     */
    class InvocationPluginAssertions {
        final InvocationPlugin plugin;
        final ValueNode[] args;
        final ResolvedJavaMethod targetMethod;
        final JavaKind resultType;
        final int beforeStackSize;
        final boolean needsNullCheck;
        final int nodeCount;
        final Mark mark;

        InvocationPluginAssertions(InvocationPlugin plugin, ValueNode[] args, ResolvedJavaMethod targetMethod, JavaKind resultType) {
            guarantee(Assertions.assertionsEnabled(), "%s should only be loaded and instantiated if assertions are enabled", getClass().getSimpleName());
            this.plugin = plugin;
            this.targetMethod = targetMethod;
            this.args = args;
            this.resultType = resultType;
            this.beforeStackSize = frameState.stackSize();
            this.needsNullCheck = !targetMethod.isStatic() && args[0].getStackKind() == JavaKind.Object && !StampTool.isPointerNonNull(args[0].stamp(NodeView.DEFAULT));
            this.nodeCount = graph.getNodeCount();
            this.mark = graph.getMark();
        }

        String error(String format, Object... a) {
            return String.format(format, a) + String.format("%n\tplugin at %s", plugin.getApplySourceLocation(metaAccess));
        }

        boolean check(boolean pluginResult) {
            if (pluginResult) {
                /*
                 * If lastInstr is null, even if this method has a non-void return type, the method
                 * doesn't return a value, it probably throws an exception.
                 */
                int expectedStackSize = beforeStackSize + resultType.getSlotCount();
                assert lastInstr == null || expectedStackSize == frameState.stackSize() : error("plugin manipulated the stack incorrectly: expected=%d, actual=%d", expectedStackSize,
                                frameState.stackSize());

                NodeIterable<Node> newNodes = graph.getNewNodes(mark);
                assert !needsNullCheck || isPointerNonNull(args[0].stamp(NodeView.DEFAULT)) : error("plugin needs to null check the receiver of %s: receiver=%s", targetMethod.format("%H.%n(%p)"),
                                args[0]);
                for (Node n : newNodes) {
                    if (n instanceof StateSplit) {
                        StateSplit stateSplit = (StateSplit) n;
                        assert stateSplit.stateAfter() != null || !stateSplit.hasSideEffect() : error("%s node added by plugin for %s need to have a non-null frame state: %s",
                                        StateSplit.class.getSimpleName(), targetMethod.format("%H.%n(%p)"), stateSplit);
                    }
                }
                try {
                    graphBuilderConfig.getPlugins().getInvocationPlugins().checkNewNodes(BytecodeParser.this, plugin, newNodes);
                } catch (Throwable t) {
                    throw new AssertionError(error("Error in plugin"), t);
                }
            } else {
                assert nodeCount == graph.getNodeCount() : error("plugin that returns false must not create new nodes");
                assert beforeStackSize == frameState.stackSize() : error("plugin that returns false must not modify the stack");
            }
            return true;
        }
    }

    protected static class IntrinsicGuard {
        final FixedWithNextNode lastInstr;
        final Mark mark;
        final AbstractBeginNode nonIntrinsicBranch;
        final ValueNode receiver;
        final JavaTypeProfile profile;

        public IntrinsicGuard(FixedWithNextNode lastInstr, ValueNode receiver, Mark mark, AbstractBeginNode nonIntrinsicBranch, JavaTypeProfile profile) {
            this.lastInstr = lastInstr;
            this.receiver = receiver;
            this.mark = mark;
            this.nonIntrinsicBranch = nonIntrinsicBranch;
            this.profile = profile;
        }
    }

    /**
     * Weaves a test of the receiver type to ensure the dispatch will select {@code targetMethod}
     * and not another method that overrides it. This should only be called if there is an
     * {@link InvocationPlugin} for {@code targetMethod} and the invocation is indirect.
     *
     * The control flow woven around the intrinsic is as follows:
     *
     * <pre>
     *  if (LoadMethod(LoadHub(receiver)) == targetMethod) {
     *       <intrinsic for targetMethod>
     *  } else {
     *       <virtual call to targetMethod>
     *  }
     * </pre>
     *
     * The {@code else} branch is woven by {@link #afterInvocationPluginExecution}.
     *
     * @return {@code null} if the intrinsic cannot be used otherwise an object to be used by
     *         {@link #afterInvocationPluginExecution} to weave code for the non-intrinsic branch
     */
    protected IntrinsicGuard guardIntrinsic(ValueNode[] args, ResolvedJavaMethod targetMethod, InvocationPluginReceiver pluginReceiver) {
        ValueNode intrinsicReceiver = args[0];
        ResolvedJavaType receiverType = StampTool.typeOrNull(intrinsicReceiver);
        if (receiverType == null) {
            // The verifier guarantees it to be at least type declaring targetMethod
            receiverType = targetMethod.getDeclaringClass();
        }
        ResolvedJavaMethod resolvedMethod = receiverType.resolveMethod(targetMethod, method.getDeclaringClass());
        if (resolvedMethod == null || resolvedMethod.equals(targetMethod)) {
            assert resolvedMethod == null || targetMethod.getDeclaringClass().isAssignableFrom(resolvedMethod.getDeclaringClass());
            Mark mark = graph.getMark();
            FixedWithNextNode currentLastInstr = lastInstr;
            ValueNode nonNullReceiver = pluginReceiver.get();
            Stamp methodStamp = stampProvider.createMethodStamp();
            LoadHubNode hub = graph.unique(new LoadHubNode(stampProvider, nonNullReceiver));
            LoadMethodNode actual = append(new LoadMethodNode(methodStamp, targetMethod, receiverType, method.getDeclaringClass(), hub));
            ConstantNode expected = graph.unique(ConstantNode.forConstant(methodStamp, targetMethod.getEncoding(), getMetaAccess()));
            LogicNode compare = graph.addOrUniqueWithInputs(CompareNode.createCompareNode(constantReflection, metaAccess, options, null, CanonicalCondition.EQ, actual, expected, NodeView.DEFAULT));

            JavaTypeProfile profile = null;
            if (profilingInfo != null && this.optimisticOpts.useTypeCheckHints(getOptions())) {
                profile = profilingInfo.getTypeProfile(bci());
                if (profile != null) {
                    JavaTypeProfile newProfile = adjustProfileForInvocationPlugin(profile, targetMethod);
                    if (newProfile != profile) {
                        if (newProfile.getTypes().length == 0) {
                            // All profiled types select the intrinsic so
                            // emit a fixed guard instead of an if-then-else.
                            lastInstr = append(new FixedGuardNode(compare, TypeCheckedInliningViolated, InvalidateReprofile, false));
                            return new IntrinsicGuard(currentLastInstr, intrinsicReceiver, mark, null, null);
                        }
                    } else {
                        // No profiled types select the intrinsic so emit a virtual call
                        return null;
                    }
                    profile = newProfile;
                }
            }

            AbstractBeginNode intrinsicBranch = graph.add(new BeginNode());
            AbstractBeginNode nonIntrinsicBranch = graph.add(new BeginNode());
            append(new IfNode(compare, intrinsicBranch, nonIntrinsicBranch, FAST_PATH_PROBABILITY));
            lastInstr = intrinsicBranch;
            return new IntrinsicGuard(currentLastInstr, intrinsicReceiver, mark, nonIntrinsicBranch, profile);
        } else {
            // Receiver selects an overriding method so emit a virtual call
            return null;
        }
    }

    /**
     * Adjusts the profile for an indirect invocation of a virtual method for which there is an
     * intrinsic. The adjustment made by this method is to remove all types from the profile that do
     * not override {@code targetMethod}.
     *
     * @param profile the profile to adjust
     * @param targetMethod the virtual method for which there is an intrinsic
     * @return the adjusted profile or the original {@code profile} object if no adjustment was made
     */
    protected JavaTypeProfile adjustProfileForInvocationPlugin(JavaTypeProfile profile, ResolvedJavaMethod targetMethod) {
        if (profile.getTypes().length > 0) {
            List<ProfiledType> retained = new ArrayList<>();
            double notRecordedProbability = profile.getNotRecordedProbability();
            for (ProfiledType ptype : profile.getTypes()) {
                if (!ptype.getType().resolveMethod(targetMethod, method.getDeclaringClass()).equals(targetMethod)) {
                    retained.add(ptype);
                } else {
                    notRecordedProbability += ptype.getProbability();
                }
            }
            if (!retained.isEmpty()) {
                if (retained.size() != profile.getTypes().length) {
                    return new JavaTypeProfile(profile.getNullSeen(), notRecordedProbability, retained.toArray(new ProfiledType[retained.size()]));
                }
            } else {
                return new JavaTypeProfile(profile.getNullSeen(), notRecordedProbability, new ProfiledType[0]);
            }
        }
        return profile;
    }

    /**
     * Performs any action required after execution of an invocation plugin. This includes
     * {@linkplain InvocationPluginAssertions#check checking} invocation plugin invariants as well
     * as weaving the {@code else} branch of the code woven by {@link #guardIntrinsic} if
     * {@code guard != null}.
     */
    protected void afterInvocationPluginExecution(boolean pluginHandledInvoke, InvocationPluginAssertions assertions, IntrinsicGuard intrinsicGuard,
                    InvokeKind invokeKind, ValueNode[] args, ResolvedJavaMethod targetMethod, JavaKind resultType, JavaType returnType) {
        assert assertions.check(pluginHandledInvoke);
        if (intrinsicGuard != null) {
            if (pluginHandledInvoke) {
                if (intrinsicGuard.nonIntrinsicBranch != null) {
                    // Intrinsic emitted: emit a virtual call to the target method and
                    // merge it with the intrinsic branch
                    EndNode intrinsicEnd = append(new EndNode());

                    FrameStateBuilder intrinsicState = null;
                    FrameStateBuilder nonIntrinisicState = null;
                    if (resultType != JavaKind.Void) {
                        intrinsicState = frameState.copy();
                        frameState.pop(resultType);
                        nonIntrinisicState = frameState;
                    }

                    lastInstr = intrinsicGuard.nonIntrinsicBranch;
                    createNonInlinedInvoke(getActionForInvokeExceptionEdge(null), bci(), args, targetMethod, invokeKind, resultType, returnType, intrinsicGuard.profile);

                    EndNode nonIntrinsicEnd = append(new EndNode());
                    AbstractMergeNode mergeNode = graph.add(new MergeNode());

                    mergeNode.addForwardEnd(intrinsicEnd);
                    if (intrinsicState != null) {
                        intrinsicState.merge(mergeNode, nonIntrinisicState);
                        frameState = intrinsicState;
                    }
                    mergeNode.addForwardEnd(nonIntrinsicEnd);
                    mergeNode.setStateAfter(frameState.create(stream.nextBCI(), mergeNode));

                    lastInstr = mergeNode;
                }
            } else {
                // Intrinsic was not applied: remove intrinsic guard
                // and restore the original receiver node in the arguments array
                intrinsicGuard.lastInstr.setNext(null);
                GraphUtil.removeNewNodes(graph, intrinsicGuard.mark);
                lastInstr = intrinsicGuard.lastInstr;
                args[0] = intrinsicGuard.receiver;
            }
        }
    }

    @SuppressWarnings("try")
    protected boolean tryInvocationPlugin(InvokeKind invokeKind, ValueNode[] args, ResolvedJavaMethod targetMethod, JavaKind resultType, JavaType returnType) {
        InvocationPlugin plugin = graphBuilderConfig.getPlugins().getInvocationPlugins().lookupInvocation(targetMethod);
        if (plugin != null) {

            if (intrinsicContext != null && intrinsicContext.isCallToOriginal(targetMethod)) {
                // Self recursive intrinsic means the original method should be called.
                return false;
            }

            InvocationPluginReceiver pluginReceiver = invocationPluginReceiver.init(targetMethod, args);

            IntrinsicGuard intrinsicGuard = null;
            if (invokeKind.isIndirect()) {
                intrinsicGuard = guardIntrinsic(args, targetMethod, pluginReceiver);
                if (intrinsicGuard == null) {
                    return false;
                } else if (intrinsicGuard.nonIntrinsicBranch == null) {
                    assert lastInstr instanceof FixedGuardNode;
                }
            }

            InvocationPluginAssertions assertions = Assertions.assertionsEnabled() ? new InvocationPluginAssertions(plugin, args, targetMethod, resultType) : null;
            try (DebugCloseable context = openNodeContext(targetMethod)) {
                if (plugin.execute(this, targetMethod, pluginReceiver, args)) {
                    afterInvocationPluginExecution(true, assertions, intrinsicGuard, invokeKind, args, targetMethod, resultType, returnType);
                    return !plugin.isDecorator();
                } else {
                    afterInvocationPluginExecution(false, assertions, intrinsicGuard, invokeKind, args, targetMethod, resultType, returnType);
                }
            }
        }
        return false;
    }

    private boolean tryNodePluginForInvocation(ValueNode[] args, ResolvedJavaMethod targetMethod) {
        for (NodePlugin plugin : graphBuilderConfig.getPlugins().getNodePlugins()) {
            if (plugin.handleInvoke(this, targetMethod, args)) {
                return true;
            }
        }
        return false;
    }

    private static final InlineInfo SUCCESSFULLY_INLINED = InlineInfo.createStandardInlineInfo(null);

    /**
     * Try to inline a method. If the method was inlined, returns {@link #SUCCESSFULLY_INLINED}.
     * Otherwise, it returns the {@link InlineInfo} that lead to the decision to not inline it, or
     * {@code null} if there is no {@link InlineInfo} for this method.
     */
    private InlineInfo tryInline(ValueNode[] args, ResolvedJavaMethod targetMethod) {
        boolean canBeInlined = forceInliningEverything || parsingIntrinsic() || targetMethod.canBeInlined();
        if (!canBeInlined) {
            return null;
        }

        if (forceInliningEverything) {
            if (inline(targetMethod, targetMethod, null, args)) {
                return SUCCESSFULLY_INLINED;
            } else {
                return null;
            }
        }

        for (InlineInvokePlugin plugin : graphBuilderConfig.getPlugins().getInlineInvokePlugins()) {
            InlineInfo inlineInfo = plugin.shouldInlineInvoke(this, targetMethod, args);
            if (inlineInfo != null) {
                if (inlineInfo.getMethodToInline() != null) {
                    if (inline(targetMethod, inlineInfo.getMethodToInline(), inlineInfo.getIntrinsicBytecodeProvider(), args)) {
                        return SUCCESSFULLY_INLINED;
                    }
                    inlineInfo = null;
                }
                /* Do not inline, and do not ask the remaining plugins. */
                return inlineInfo;
            }
        }

        // There was no inline plugin with a definite answer to whether or not
        // to inline. If we're parsing an intrinsic, then we need to enforce the
        // invariant here that methods are always force inlined in intrinsics/snippets.
        if (parsingIntrinsic()) {
            if (inline(targetMethod, targetMethod, this.bytecodeProvider, args)) {
                return SUCCESSFULLY_INLINED;
            }
        }
        return null;
    }

    private static final int ACCESSOR_BYTECODE_LENGTH = 5;

    /**
     * Tries to inline {@code targetMethod} if it is an instance field accessor. This avoids the
     * overhead of creating and using a nested {@link BytecodeParser} object.
     */
    @SuppressWarnings("try")
    private boolean tryFastInlineAccessor(ValueNode[] args, ResolvedJavaMethod targetMethod) {
        byte[] bytecode = targetMethod.getCode();
        if (bytecode != null && bytecode.length == ACCESSOR_BYTECODE_LENGTH &&
                        Bytes.beU1(bytecode, 0) == ALOAD_0 &&
                        Bytes.beU1(bytecode, 1) == GETFIELD) {
            int b4 = Bytes.beU1(bytecode, 4);
            if (b4 >= IRETURN && b4 <= ARETURN) {
                int cpi = Bytes.beU2(bytecode, 2);
                JavaField field = targetMethod.getConstantPool().lookupField(cpi, targetMethod, GETFIELD);
                if (field instanceof ResolvedJavaField) {
                    ValueNode receiver = invocationPluginReceiver.init(targetMethod, args).get();
                    ResolvedJavaField resolvedField = (ResolvedJavaField) field;
                    try (DebugCloseable context = openNodeContext(targetMethod, 1)) {
                        genGetField(resolvedField, receiver);
                        notifyBeforeInline(targetMethod);
                        printInlining(targetMethod, targetMethod, true, "inline accessor method (bytecode parsing)");
                        notifyAfterInline(targetMethod);
                    }
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean intrinsify(BytecodeProvider intrinsicBytecodeProvider, ResolvedJavaMethod targetMethod, ResolvedJavaMethod substitute, InvocationPlugin.Receiver receiver, ValueNode[] args) {
        if (receiver != null) {
            receiver.get();
        }
        boolean res = inline(targetMethod, substitute, intrinsicBytecodeProvider, args);
        assert res : "failed to inline " + substitute;
        return res;
    }

    private boolean inline(ResolvedJavaMethod targetMethod, ResolvedJavaMethod inlinedMethod, BytecodeProvider intrinsicBytecodeProvider, ValueNode[] args) {
        try (InliningLog.RootScope scope = graph.getInliningLog().openRootScope(targetMethod, bci())) {
            IntrinsicContext intrinsic = this.intrinsicContext;

            if (intrinsic == null && !graphBuilderConfig.insertFullInfopoints() &&
                            targetMethod.equals(inlinedMethod) &&
                            (targetMethod.getModifiers() & (STATIC | SYNCHRONIZED)) == 0 &&
                            tryFastInlineAccessor(args, targetMethod)) {
                return true;
            }

            if (intrinsic != null && intrinsic.isCallToOriginal(targetMethod)) {
                if (intrinsic.isCompilationRoot()) {
                    // A root compiled intrinsic needs to deoptimize
                    // if the slow path is taken. During frame state
                    // assignment, the deopt node will get its stateBefore
                    // from the start node of the intrinsic
                    append(new DeoptimizeNode(InvalidateRecompile, RuntimeConstraint));
                    printInlining(targetMethod, inlinedMethod, true, "compilation root (bytecode parsing)");
                    if (scope != null) {
                        graph.getInliningLog().addDecision(scope.getInvoke(), true, "GraphBuilderPhase", null, null, "compilation root");
                    }
                    return true;
                } else {
                    if (intrinsic.getOriginalMethod().isNative()) {
                        printInlining(targetMethod, inlinedMethod, false, "native method (bytecode parsing)");
                        if (scope != null) {
                            graph.getInliningLog().addDecision(scope.getInvoke(), false, "GraphBuilderPhase", null, null, "native method");
                        }
                        return false;
                    }
                    if (canInlinePartialIntrinsicExit() && InlinePartialIntrinsicExitDuringParsing.getValue(options)) {
                        // Otherwise inline the original method. Any frame state created
                        // during the inlining will exclude frame(s) in the
                        // intrinsic method (see FrameStateBuilder.create(int bci)).
                        notifyBeforeInline(inlinedMethod);
                        printInlining(targetMethod, inlinedMethod, true, "partial intrinsic exit (bytecode parsing)");
                        if (scope != null) {
                            graph.getInliningLog().addDecision(scope.getInvoke(), true, "GraphBuilderPhase", null, null, "partial intrinsic exit");
                        }
                        parseAndInlineCallee(intrinsic.getOriginalMethod(), args, null);
                        notifyAfterInline(inlinedMethod);
                        return true;
                    } else {
                        printInlining(targetMethod, inlinedMethod, false, "partial intrinsic exit (bytecode parsing)");
                        if (scope != null) {
                            graph.getInliningLog().addDecision(scope.getInvoke(), false, "GraphBuilderPhase", null, null, "partial intrinsic exit");
                        }
                        return false;
                    }
                }
            } else {
                boolean isIntrinsic = intrinsicBytecodeProvider != null;
                if (intrinsic == null && isIntrinsic) {
                    assert !inlinedMethod.equals(targetMethod);
                    intrinsic = new IntrinsicContext(targetMethod, inlinedMethod, intrinsicBytecodeProvider, INLINE_DURING_PARSING);
                }
                if (inlinedMethod.hasBytecodes()) {
                    notifyBeforeInline(inlinedMethod);
                    printInlining(targetMethod, inlinedMethod, true, "inline method (bytecode parsing)");
                    if (scope != null) {
                        graph.getInliningLog().addDecision(scope.getInvoke(), true, "GraphBuilderPhase", null, null, "inline method");
                    }
                    parseAndInlineCallee(inlinedMethod, args, intrinsic);
                    notifyAfterInline(inlinedMethod);
                } else {
                    printInlining(targetMethod, inlinedMethod, false, "no bytecodes (abstract or native) (bytecode parsing)");
                    if (scope != null) {
                        graph.getInliningLog().addDecision(scope.getInvoke(), false, "GraphBuilderPhase", null, null, "no bytecodes (abstract or native)");
                    }
                    return false;
                }
            }
            return true;
        }
    }

    protected void notifyBeforeInline(ResolvedJavaMethod inlinedMethod) {
        for (InlineInvokePlugin plugin : graphBuilderConfig.getPlugins().getInlineInvokePlugins()) {
            plugin.notifyBeforeInline(inlinedMethod);
        }
    }

    protected void notifyAfterInline(ResolvedJavaMethod inlinedMethod) {
        for (InlineInvokePlugin plugin : graphBuilderConfig.getPlugins().getInlineInvokePlugins()) {
            plugin.notifyAfterInline(inlinedMethod);
        }
    }

    /**
     * Determines if a partial intrinsic exit (i.e., a call to the original method within an
     * intrinsic) can be inlined.
     */
    protected boolean canInlinePartialIntrinsicExit() {
        return true;
    }

    private void printInlining(ResolvedJavaMethod targetMethod, ResolvedJavaMethod inlinedMethod, boolean success, String msg) {
        if (success) {
            if (TraceInlineDuringParsing.getValue(options) || TraceParserPlugins.getValue(options)) {
                if (targetMethod.equals(inlinedMethod)) {
                    traceWithContext("inlining call to %s", inlinedMethod.format("%h.%n(%p)"));
                } else {
                    traceWithContext("inlining call to %s as intrinsic for %s", inlinedMethod.format("%h.%n(%p)"), targetMethod.format("%h.%n(%p)"));
                }
            }
        }
        if (HotSpotPrintInlining.getValue(options)) {
            if (targetMethod.equals(inlinedMethod)) {
                Util.printInlining(inlinedMethod, bci(), getDepth(), success, "%s", msg);
            } else {
                Util.printInlining(inlinedMethod, bci(), getDepth(), success, "%s intrinsic for %s", msg, targetMethod.format("%h.%n(%p)"));
            }
        }
    }

    /**
     * Prints a line to {@link TTY} with a prefix indicating the current parse context. The prefix
     * is of the form:
     *
     * <pre>
     * {SPACE * n} {name of method being parsed} "(" {file name} ":" {line number} ")"
     * </pre>
     *
     * where {@code n} is the current inlining depth.
     *
     * @param format a format string
     * @param args arguments to the format string
     */

    protected void traceWithContext(String format, Object... args) {
        StackTraceElement where = code.asStackTraceElement(bci());
        String s = format("%s%s (%s:%d) %s", nSpaces(getDepth()), method.isConstructor() ? method.format("%h.%n") : method.getName(), where.getFileName(), where.getLineNumber(),
                        format(format, args));
        TTY.println(s);
    }

    protected RuntimeException throwParserError(Throwable e) {
        if (e instanceof BytecodeParserError) {
            throw (BytecodeParserError) e;
        }
        BytecodeParser bp = this;
        BytecodeParserError res = new BytecodeParserError(e);
        while (bp != null) {
            res.addContext("parsing " + bp.code.asStackTraceElement(bp.bci()));
            bp = bp.parent;
        }
        throw res;
    }

    protected void parseAndInlineCallee(ResolvedJavaMethod targetMethod, ValueNode[] args, IntrinsicContext calleeIntrinsicContext) {
        FixedWithNextNode calleeBeforeUnwindNode = null;
        ValueNode calleeUnwindValue = null;

        try (IntrinsicScope s = calleeIntrinsicContext != null && !parsingIntrinsic() ? new IntrinsicScope(this, targetMethod.getSignature().toParameterKinds(!targetMethod.isStatic()), args) : null) {
            BytecodeParser parser = graphBuilderInstance.createBytecodeParser(graph, this, targetMethod, INVOCATION_ENTRY_BCI, calleeIntrinsicContext);
            FrameStateBuilder startFrameState = new FrameStateBuilder(parser, parser.code, graph);
            if (!targetMethod.isStatic()) {
                args[0] = nullCheckedValue(args[0]);
            }
            startFrameState.initializeFromArgumentsArray(args);
            parser.build(this.lastInstr, startFrameState);

            if (parser.returnDataList == null) {
                /* Callee does not return. */
                lastInstr = null;
            } else {
                ValueNode calleeReturnValue;
                MergeNode returnMergeNode = null;
                if (s != null) {
                    s.returnDataList = parser.returnDataList;
                }
                if (parser.returnDataList.size() == 1) {
                    /* Callee has a single return, we can continue parsing at that point. */
                    ReturnToCallerData singleReturnData = parser.returnDataList.get(0);
                    lastInstr = singleReturnData.beforeReturnNode;
                    calleeReturnValue = singleReturnData.returnValue;
                } else {
                    assert parser.returnDataList.size() > 1;
                    /* Callee has multiple returns, we need to insert a control flow merge. */
                    returnMergeNode = graph.add(new MergeNode());
                    calleeReturnValue = ValueMergeUtil.mergeValueProducers(returnMergeNode, parser.returnDataList, returnData -> returnData.beforeReturnNode, returnData -> returnData.returnValue);
                }

                if (calleeReturnValue != null) {
                    frameState.push(targetMethod.getSignature().getReturnKind().getStackKind(), calleeReturnValue);
                }
                if (returnMergeNode != null) {
                    returnMergeNode.setStateAfter(createFrameState(stream.nextBCI(), returnMergeNode));
                    lastInstr = finishInstruction(returnMergeNode, frameState);
                }
            }
            /*
             * Propagate any side effects into the caller when parsing intrinsics.
             */
            if (parser.frameState.isAfterSideEffect() && parsingIntrinsic()) {
                for (StateSplit sideEffect : parser.frameState.sideEffects()) {
                    frameState.addSideEffect(sideEffect);
                }
            }

            calleeBeforeUnwindNode = parser.getBeforeUnwindNode();
            if (calleeBeforeUnwindNode != null) {
                calleeUnwindValue = parser.getUnwindValue();
                assert calleeUnwindValue != null;
            }
        }

        /*
         * Method handleException will call createTarget, which wires this exception edge to the
         * corresponding exception dispatch block in the caller. In the case where it wires to the
         * caller's unwind block, any FrameState created meanwhile, e.g., FrameState for
         * LoopExitNode, would be instantiated with AFTER_EXCEPTION_BCI. Such frame states should
         * not be fixed by IntrinsicScope.close, as they denote the states of the caller. Thus, the
         * following code should be placed outside the IntrinsicScope, so that correctly created
         * FrameStates are not replaced.
         */
        if (calleeBeforeUnwindNode != null) {
            calleeBeforeUnwindNode.setNext(handleException(calleeUnwindValue, bci(), false));
        }
    }

    public MethodCallTargetNode createMethodCallTarget(InvokeKind invokeKind, ResolvedJavaMethod targetMethod, ValueNode[] args, StampPair returnStamp, JavaTypeProfile profile) {
        return new MethodCallTargetNode(invokeKind, targetMethod, args, returnStamp, profile);
    }

    protected InvokeNode createInvoke(int invokeBci, CallTargetNode callTarget, JavaKind resultType) {
        InvokeNode invoke = append(new InvokeNode(callTarget, invokeBci));
        frameState.pushReturn(resultType, invoke);
        invoke.setStateAfter(createFrameState(stream.nextBCI(), invoke));
        return invoke;
    }

    protected InvokeWithExceptionNode createInvokeWithException(int invokeBci, CallTargetNode callTarget, JavaKind resultType, ExceptionEdgeAction exceptionEdgeAction) {
        if (currentBlock != null && stream.nextBCI() > currentBlock.endBci) {
            /*
             * Clear non-live locals early so that the exception handler entry gets the cleared
             * state.
             */
            frameState.clearNonLiveLocals(currentBlock, liveness, false);
        }

        AbstractBeginNode exceptionEdge = handleException(null, bci(), exceptionEdgeAction == ExceptionEdgeAction.INCLUDE_AND_DEOPTIMIZE);
        InvokeWithExceptionNode invoke = append(new InvokeWithExceptionNode(callTarget, exceptionEdge, invokeBci));
        frameState.pushReturn(resultType, invoke);
        invoke.setStateAfter(createFrameState(stream.nextBCI(), invoke));
        return invoke;
    }

    protected void genReturn(ValueNode returnVal, JavaKind returnKind) {
        if (parsingIntrinsic() && returnVal != null) {
            if (returnVal instanceof StateSplit) {
                StateSplit stateSplit = (StateSplit) returnVal;
                FrameState stateAfter = stateSplit.stateAfter();
                if (stateSplit.hasSideEffect()) {
                    assert stateSplit != null;
                    if (stateAfter.bci == BytecodeFrame.AFTER_BCI) {
                        assert stateAfter.usages().count() == 1;
                        assert stateAfter.usages().first() == stateSplit;
                        stateAfter.replaceAtUsages(graph.add(new FrameState(BytecodeFrame.AFTER_BCI, returnVal)));
                        GraphUtil.killWithUnusedFloatingInputs(stateAfter);
                    } else {
                        /*
                         * This must be the return value from within a partial intrinsification.
                         */
                        assert !BytecodeFrame.isPlaceholderBci(stateAfter.bci);
                    }
                } else {
                    assert stateAfter == null;
                }
            }
        }

        ValueNode realReturnVal = processReturnValue(returnVal, returnKind);

        frameState.setRethrowException(false);
        frameState.clearStack();
        beforeReturn(realReturnVal, returnKind);
        if (parent == null) {
            append(new ReturnNode(realReturnVal));
        } else {
            if (returnDataList == null) {
                returnDataList = new ArrayList<>();
            }
            returnDataList.add(new ReturnToCallerData(realReturnVal, lastInstr));
            lastInstr = null;
        }
    }

    private ValueNode processReturnValue(ValueNode value, JavaKind kind) {
        JavaKind returnKind = method.getSignature().getReturnKind();
        if (kind != returnKind) {
            // sub-word integer
            assert returnKind.isNumericInteger() && returnKind.getStackKind() == JavaKind.Int;
            IntegerStamp stamp = (IntegerStamp) value.stamp(NodeView.DEFAULT);

            // the bytecode verifier doesn't check that the value is in the correct range
            if (stamp.lowerBound() < returnKind.getMinValue() || returnKind.getMaxValue() < stamp.upperBound()) {
                return maskSubWordValue(value, returnKind);
            }
        }

        return value;
    }

    private void beforeReturn(ValueNode x, JavaKind kind) {
        if (graph.method() != null && graph.method().isJavaLangObjectInit()) {
            /*
             * Get the receiver from the initial state since bytecode rewriting could do arbitrary
             * things to the state of the locals.
             */
            ValueNode receiver = graph.start().stateAfter().localAt(0);
            assert receiver != null && receiver.getStackKind() == JavaKind.Object;
            if (RegisterFinalizerNode.mayHaveFinalizer(receiver, graph.getAssumptions())) {
                append(new RegisterFinalizerNode(receiver));
            }
        }
        genInfoPointNode(InfopointReason.METHOD_END, x);
        if (finalBarrierRequired) {
            assert originalReceiver != null;
            /*
             * When compiling an OSR with a final field store, don't bother tracking the original
             * receiver since the receiver cannot be EA'ed.
             */
            append(new FinalFieldBarrierNode(entryBCI == INVOCATION_ENTRY_BCI ? originalReceiver : null));
        }
        synchronizedEpilogue(BytecodeFrame.AFTER_BCI, x, kind);
    }

    protected MonitorEnterNode createMonitorEnterNode(ValueNode x, MonitorIdNode monitorId) {
        return new MonitorEnterNode(x, monitorId);
    }

    protected void genMonitorEnter(ValueNode x, int bci) {
        MonitorIdNode monitorId = graph.add(new MonitorIdNode(frameState.lockDepth(true)));
        MonitorEnterNode monitorEnter = append(createMonitorEnterNode(x, monitorId));
        frameState.pushLock(x, monitorId);
        monitorEnter.setStateAfter(createFrameState(bci, monitorEnter));
    }

    protected void genMonitorExit(ValueNode x, ValueNode escapedReturnValue, int bci) {
        if (frameState.lockDepth(false) == 0) {
            throw bailout("unbalanced monitors: too many exits");
        }
        MonitorIdNode monitorId = frameState.peekMonitorId();
        ValueNode lockedObject = frameState.popLock();
        if (GraphUtil.originalValue(lockedObject) != GraphUtil.originalValue(x)) {
            throw bailout(String.format("unbalanced monitors: mismatch at monitorexit, %s != %s", GraphUtil.originalValue(x), GraphUtil.originalValue(lockedObject)));
        }
        MonitorExitNode monitorExit = append(new MonitorExitNode(lockedObject, monitorId, escapedReturnValue));
        monitorExit.setStateAfter(createFrameState(bci, monitorExit));
    }

    protected void genJsr(int dest) {
        BciBlock successor = currentBlock.getJsrSuccessor();
        assert successor.startBci == dest : successor.startBci + " != " + dest + " @" + bci();
        JsrScope scope = currentBlock.getJsrScope();
        int nextBci = getStream().nextBCI();
        if (!successor.getJsrScope().pop().equals(scope)) {
            throw new JsrNotSupportedBailout("unstructured control flow (internal limitation)");
        }
        if (successor.getJsrScope().nextReturnAddress() != nextBci) {
            throw new JsrNotSupportedBailout("unstructured control flow (internal limitation)");
        }
        ConstantNode nextBciNode = getJsrConstant(nextBci);
        frameState.push(JavaKind.Object, nextBciNode);
        appendGoto(successor);
    }

    protected void genRet(int localIndex) {
        BciBlock successor = currentBlock.getRetSuccessor();
        ValueNode local = frameState.loadLocal(localIndex, JavaKind.Object);
        JsrScope scope = currentBlock.getJsrScope();
        int retAddress = scope.nextReturnAddress();
        ConstantNode returnBciNode = getJsrConstant(retAddress);
        LogicNode guard = IntegerEqualsNode.create(constantReflection, metaAccess, options, null, local, returnBciNode, NodeView.DEFAULT);
        guard = graph.addOrUniqueWithInputs(guard);
        append(new FixedGuardNode(guard, JavaSubroutineMismatch, InvalidateReprofile));
        if (!successor.getJsrScope().equals(scope.pop())) {
            throw new JsrNotSupportedBailout("unstructured control flow (ret leaves more than one scope)");
        }
        appendGoto(successor);
    }

    private ConstantNode getJsrConstant(long bci) {
        JavaConstant nextBciConstant = new RawConstant(bci);
        Stamp nextBciStamp = StampFactory.forConstant(nextBciConstant);
        ConstantNode nextBciNode = new ConstantNode(nextBciConstant, nextBciStamp);
        return graph.unique(nextBciNode);
    }

    protected void genIntegerSwitch(ValueNode value, ArrayList<BciBlock> actualSuccessors, int[] keys, double[] keyProbabilities, int[] keySuccessors) {
        if (value.isConstant()) {
            JavaConstant constant = (JavaConstant) value.asConstant();
            int constantValue = constant.asInt();
            for (int i = 0; i < keys.length; ++i) {
                if (keys[i] == constantValue) {
                    appendGoto(actualSuccessors.get(keySuccessors[i]));
                    return;
                }
            }
            appendGoto(actualSuccessors.get(keySuccessors[keys.length]));
        } else {
            this.controlFlowSplit = true;
            double[] successorProbabilities = successorProbabilites(actualSuccessors.size(), keySuccessors, keyProbabilities);
            IntegerSwitchNode switchNode = append(new IntegerSwitchNode(value, actualSuccessors.size(), keys, keyProbabilities, keySuccessors));
            for (int i = 0; i < actualSuccessors.size(); i++) {
                switchNode.setBlockSuccessor(i, createBlockTarget(successorProbabilities[i], actualSuccessors.get(i), frameState));
            }
        }
    }

    /**
     * Helper function that sums up the probabilities of all keys that lead to a specific successor.
     *
     * @return an array of size successorCount with the accumulated probability for each successor.
     */
    private static double[] successorProbabilites(int successorCount, int[] keySuccessors, double[] keyProbabilities) {
        double[] probability = new double[successorCount];
        for (int i = 0; i < keySuccessors.length; i++) {
            probability[keySuccessors[i]] += keyProbabilities[i];
        }
        return probability;
    }

    protected ConstantNode appendConstant(JavaConstant constant) {
        assert constant != null;
        return ConstantNode.forConstant(constant, metaAccess, graph);
    }

    @Override
    public <T extends ValueNode> T append(T v) {
        assert !graph.trackNodeSourcePosition() || graph.currentNodeSourcePosition() != null || currentBlock == blockMap.getUnwindBlock() || currentBlock instanceof ExceptionDispatchBlock;
        if (v.graph() != null) {
            return v;
        }
        T added = graph.addOrUniqueWithInputs(v);
        if (added == v) {
            updateLastInstruction(v);
        }
        return added;
    }

    private <T extends ValueNode> void updateLastInstruction(T v) {
        if (v instanceof FixedNode) {
            FixedNode fixedNode = (FixedNode) v;
            if (lastInstr != null) {
                lastInstr.setNext(fixedNode);
            }
            if (fixedNode instanceof FixedWithNextNode) {
                FixedWithNextNode fixedWithNextNode = (FixedWithNextNode) fixedNode;
                assert fixedWithNextNode.next() == null : "cannot append instruction to instruction which isn't end";
                lastInstr = fixedWithNextNode;
            } else {
                lastInstr = null;
            }
        }
    }

    private Target checkLoopExit(FixedNode target, BciBlock targetBlock, FrameStateBuilder state) {
        if (currentBlock != null) {
            long exits = currentBlock.loops & ~targetBlock.loops;
            if (exits != 0) {
                LoopExitNode firstLoopExit = null;
                LoopExitNode lastLoopExit = null;

                int pos = 0;
                ArrayList<BciBlock> exitLoops = new ArrayList<>(Long.bitCount(exits));
                do {
                    long lMask = 1L << pos;
                    if ((exits & lMask) != 0) {
                        exitLoops.add(blockMap.getLoopHeader(pos));
                        exits &= ~lMask;
                    }
                    pos++;
                } while (exits != 0);

                Collections.sort(exitLoops, new Comparator<BciBlock>() {

                    @Override
                    public int compare(BciBlock o1, BciBlock o2) {
                        return Long.bitCount(o2.loops) - Long.bitCount(o1.loops);
                    }
                });

                int bci = targetBlock.startBci;
                if (targetBlock instanceof ExceptionDispatchBlock) {
                    bci = ((ExceptionDispatchBlock) targetBlock).deoptBci;
                }
                FrameStateBuilder newState = state.copy();
                for (BciBlock loop : exitLoops) {
                    LoopBeginNode loopBegin = (LoopBeginNode) getFirstInstruction(loop);
                    LoopExitNode loopExit = graph.add(new LoopExitNode(loopBegin));
                    if (lastLoopExit != null) {
                        lastLoopExit.setNext(loopExit);
                    }
                    if (firstLoopExit == null) {
                        firstLoopExit = loopExit;
                    }
                    lastLoopExit = loopExit;
                    debug.log("Target %s Exits %s, scanning framestates...", targetBlock, loop);
                    newState.clearNonLiveLocals(targetBlock, liveness, true);
                    newState.insertLoopProxies(loopExit, getEntryState(loop));
                    loopExit.setStateAfter(newState.create(bci, loopExit));
                }

                lastLoopExit.setNext(target);
                return new Target(firstLoopExit, newState);
            }
        }
        return new Target(target, state);
    }

    private FrameStateBuilder getEntryState(BciBlock block) {
        return entryStateArray[block.id];
    }

    private void setEntryState(BciBlock block, FrameStateBuilder entryState) {
        this.entryStateArray[block.id] = entryState;
    }

    private void setFirstInstruction(BciBlock block, FixedWithNextNode firstInstruction) {
        this.firstInstructionArray[block.id] = firstInstruction;
    }

    private FixedWithNextNode getFirstInstruction(BciBlock block) {
        return firstInstructionArray[block.id];
    }

    private FixedNode createTarget(double probability, BciBlock block, FrameStateBuilder stateAfter) {
        assert probability >= 0 && probability <= 1.01 : probability;
        if (isNeverExecutedCode(probability)) {
            return graph.add(new DeoptimizeNode(InvalidateReprofile, UnreachedCode));
        } else {
            assert block != null;
            return createTarget(block, stateAfter);
        }
    }

    private FixedNode createTarget(BciBlock block, FrameStateBuilder state) {
        return createTarget(block, state, false, false);
    }

    @SuppressWarnings("try")
    private FixedNode createTarget(BciBlock block, FrameStateBuilder state, boolean canReuseInstruction, boolean canReuseState) {
        assert block != null && state != null;
        assert !block.isExceptionEntry() || state.stackSize() == 1;

        try (DebugCloseable context = openNodeContext(state, block.startBci)) {
            if (getFirstInstruction(block) == null) {
                /*
                 * This is the first time we see this block as a branch target. Create and return a
                 * placeholder that later can be replaced with a MergeNode when we see this block
                 * again.
                 */
                FixedNode targetNode;
                if (canReuseInstruction && (block.getPredecessorCount() == 1 || !controlFlowSplit) && !block.isLoopHeader() && (currentBlock.loops & ~block.loops) == 0) {
                    setFirstInstruction(block, lastInstr);
                    lastInstr = null;
                } else {
                    setFirstInstruction(block, graph.add(new BeginNode()));
                }
                targetNode = getFirstInstruction(block);
                Target target = checkLoopExit(targetNode, block, state);
                FixedNode result = target.fixed;
                FrameStateBuilder currentEntryState = target.state == state ? (canReuseState ? state : state.copy()) : target.state;
                setEntryState(block, currentEntryState);
                currentEntryState.clearNonLiveLocals(block, liveness, true);

                debug.log("createTarget %s: first visit, result: %s", block, targetNode);
                return result;
            }

            // We already saw this block before, so we have to merge states.
            if (!getEntryState(block).isCompatibleWith(state)) {
                throw bailout(String.format("stacks do not match on merge from %d into %s; bytecodes would not verify:%nexpect: %s%nactual: %s", bci(), block, getEntryState(block), state));
            }

            if (getFirstInstruction(block) instanceof LoopBeginNode) {
                assert (block.isLoopHeader() && currentBlock.getId() >= block.getId()) : "must be backward branch";
                /*
                 * Backward loop edge. We need to create a special LoopEndNode and merge with the
                 * loop begin node created before.
                 */
                LoopBeginNode loopBegin = (LoopBeginNode) getFirstInstruction(block);
                LoopEndNode loopEnd = graph.add(new LoopEndNode(loopBegin));
                Target target = checkLoopExit(loopEnd, block, state);
                FixedNode result = target.fixed;
                getEntryState(block).merge(loopBegin, target.state);

                debug.log("createTarget %s: merging backward branch to loop header %s, result: %s", block, loopBegin, result);
                return result;
            }
            assert currentBlock == null || currentBlock.getId() < block.getId() : "must not be backward branch";
            assert getFirstInstruction(block).next() == null : "bytecodes already parsed for block";

            if (getFirstInstruction(block) instanceof AbstractBeginNode && !(getFirstInstruction(block) instanceof AbstractMergeNode)) {
                /*
                 * This is the second time we see this block. Create the actual MergeNode and the
                 * End Node for the already existing edge.
                 */
                AbstractBeginNode beginNode = (AbstractBeginNode) getFirstInstruction(block);

                // The EndNode for the already existing edge.
                EndNode end = graph.add(new EndNode());
                // The MergeNode that replaces the placeholder.
                AbstractMergeNode mergeNode = graph.add(new MergeNode());
                FixedNode next = beginNode.next();

                if (beginNode.predecessor() instanceof ControlSplitNode) {
                    beginNode.setNext(end);
                } else {
                    beginNode.replaceAtPredecessor(end);
                    beginNode.safeDelete();
                }

                mergeNode.addForwardEnd(end);
                mergeNode.setNext(next);

                setFirstInstruction(block, mergeNode);
            }

            AbstractMergeNode mergeNode = (AbstractMergeNode) getFirstInstruction(block);

            // The EndNode for the newly merged edge.
            EndNode newEnd = graph.add(new EndNode());
            Target target = checkLoopExit(newEnd, block, state);
            FixedNode result = target.fixed;
            getEntryState(block).merge(mergeNode, target.state);
            mergeNode.addForwardEnd(newEnd);

            debug.log("createTarget %s: merging state, result: %s", block, result);
            return result;
        }
    }

    /**
     * Returns a block begin node with the specified state. If the specified probability is 0, the
     * block deoptimizes immediately.
     */
    private AbstractBeginNode createBlockTarget(double probability, BciBlock block, FrameStateBuilder stateAfter) {
        FixedNode target = createTarget(probability, block, stateAfter);
        AbstractBeginNode begin = BeginNode.begin(target);

        assert !(target instanceof DeoptimizeNode && begin instanceof BeginStateSplitNode &&
                        ((BeginStateSplitNode) begin).stateAfter() != null) : "We are not allowed to set the stateAfter of the begin node," +
                                        " because we have to deoptimize to a bci _before_ the actual if, so that the interpreter can update the profiling information.";
        return begin;
    }

    private ValueNode synchronizedObject(FrameStateBuilder state, ResolvedJavaMethod target) {
        if (target.isStatic()) {
            return appendConstant(getConstantReflection().asJavaClass(target.getDeclaringClass()));
        } else {
            return state.loadLocal(0, JavaKind.Object);
        }
    }

    @SuppressWarnings("try")
    protected void processBlock(BciBlock block) {
        // Ignore blocks that have no predecessors by the time their bytecodes are parsed
        FixedWithNextNode firstInstruction = getFirstInstruction(block);
        if (firstInstruction == null) {
            debug.log("Ignoring block %s", block);
            return;
        }
        try (Indent indent = debug.logAndIndent("Parsing block %s  firstInstruction: %s  loopHeader: %b", block, firstInstruction, block.isLoopHeader())) {

            lastInstr = firstInstruction;
            frameState = getEntryState(block);
            setCurrentFrameState(frameState);
            currentBlock = block;

            if (block != blockMap.getUnwindBlock() && !(block instanceof ExceptionDispatchBlock)) {
                frameState.setRethrowException(false);
            }

            if (firstInstruction instanceof AbstractMergeNode) {
                setMergeStateAfter(block, firstInstruction);
            }

            if (block == blockMap.getUnwindBlock()) {
                handleUnwindBlock((ExceptionDispatchBlock) block);
            } else if (block instanceof ExceptionDispatchBlock) {
                createExceptionDispatch((ExceptionDispatchBlock) block);
            } else {
                iterateBytecodesForBlock(block);
            }
        }
    }

    private void handleUnwindBlock(ExceptionDispatchBlock block) {
        if (parent == null) {
            finishPrepare(lastInstr, block.deoptBci, frameState);
            frameState.setRethrowException(false);
            createUnwind();
        } else {
            ValueNode exception = frameState.pop(JavaKind.Object);
            this.unwindValue = exception;
            this.beforeUnwindNode = this.lastInstr;
        }
    }

    private void setMergeStateAfter(BciBlock block, FixedWithNextNode firstInstruction) {
        AbstractMergeNode abstractMergeNode = (AbstractMergeNode) firstInstruction;
        if (abstractMergeNode.stateAfter() == null) {
            int bci = block.startBci;
            if (block instanceof ExceptionDispatchBlock) {
                bci = ((ExceptionDispatchBlock) block).deoptBci;
            }
            abstractMergeNode.setStateAfter(createFrameState(bci, abstractMergeNode));
        }
    }

    @SuppressWarnings("try")
    private void createUnwind() {
        assert frameState.stackSize() == 1 : frameState;
        synchronizedEpilogue(BytecodeFrame.AFTER_EXCEPTION_BCI, null, null);
        try (DebugCloseable context = openNodeContext(frameState, BytecodeFrame.UNWIND_BCI)) {
            ValueNode exception = frameState.pop(JavaKind.Object);
            append(new UnwindNode(exception));
        }
    }

    @SuppressWarnings("try")
    private void synchronizedEpilogue(int bci, ValueNode currentReturnValue, JavaKind currentReturnValueKind) {
        try (DebugCloseable context = openNodeContext(frameState, bci)) {
            if (method.isSynchronized()) {
                if (currentReturnValue != null) {
                    frameState.push(currentReturnValueKind, currentReturnValue);
                }
                genMonitorExit(methodSynchronizedObject, currentReturnValue, bci);
                assert !frameState.rethrowException();
                finishPrepare(lastInstr, bci, frameState);
            }
            if (frameState.lockDepth(false) != 0) {
                throw bailout("unbalanced monitors: too few exits exiting frame");
            }
        }
    }

    @SuppressWarnings("try")
    private void createExceptionDispatch(ExceptionDispatchBlock block) {
        try (DebugCloseable context = openNodeContext(frameState, BytecodeFrame.AFTER_EXCEPTION_BCI)) {
            lastInstr = finishInstruction(lastInstr, frameState);

            assert frameState.stackSize() == 1 : frameState;
            if (block.handler.isCatchAll()) {
                assert block.getSuccessorCount() == 1;
                appendGoto(block.getSuccessor(0));
                return;
            }

            JavaType catchType = block.handler.getCatchType();
            if (graphBuilderConfig.eagerResolving()) {
                catchType = lookupType(block.handler.catchTypeCPI(), INSTANCEOF);
            }
            if (typeIsResolved(catchType)) {
                TypeReference checkedCatchType = TypeReference.createTrusted(graph.getAssumptions(), (ResolvedJavaType) catchType);

                if (graphBuilderConfig.getSkippedExceptionTypes() != null) {
                    for (ResolvedJavaType skippedType : graphBuilderConfig.getSkippedExceptionTypes()) {
                        if (skippedType.isAssignableFrom(checkedCatchType.getType())) {
                            BciBlock nextBlock = block.getSuccessorCount() == 1 ? blockMap.getUnwindBlock() : block.getSuccessor(1);
                            ValueNode exception = frameState.stack[0];
                            FixedNode trueSuccessor = graph.add(new DeoptimizeNode(InvalidateReprofile, UnreachedCode));
                            FixedNode nextDispatch = createTarget(nextBlock, frameState);
                            append(new IfNode(graph.addOrUniqueWithInputs(createInstanceOf(checkedCatchType, exception)), trueSuccessor, nextDispatch, 0));
                            return;
                        }
                    }
                }

                BciBlock nextBlock = block.getSuccessorCount() == 1 ? blockMap.getUnwindBlock() : block.getSuccessor(1);
                ValueNode exception = frameState.stack[0];
                /*
                 * Anchor for the piNode, which must be before any LoopExit inserted by
                 * createTarget.
                 */
                BeginNode piNodeAnchor = graph.add(new BeginNode());
                ObjectStamp checkedStamp = StampFactory.objectNonNull(checkedCatchType);
                PiNode piNode = graph.addWithoutUnique(new PiNode(exception, checkedStamp));
                frameState.pop(JavaKind.Object);
                frameState.push(JavaKind.Object, piNode);
                FixedNode catchSuccessor = createTarget(block.getSuccessor(0), frameState);
                frameState.pop(JavaKind.Object);
                frameState.push(JavaKind.Object, exception);
                FixedNode nextDispatch = createTarget(nextBlock, frameState);
                piNodeAnchor.setNext(catchSuccessor);
                IfNode ifNode = append(new IfNode(graph.unique(createInstanceOf(checkedCatchType, exception)), piNodeAnchor, nextDispatch, 0.5));
                assert ifNode.trueSuccessor() == piNodeAnchor;
                piNode.setGuard(ifNode.trueSuccessor());
            } else {
                handleUnresolvedExceptionType(catchType);
            }
        }
    }

    private void appendGoto(BciBlock successor) {
        FixedNode targetInstr = createTarget(successor, frameState, true, true);
        if (lastInstr != null && lastInstr != targetInstr) {
            lastInstr.setNext(targetInstr);
        }
    }

    @SuppressWarnings("try")
    protected void iterateBytecodesForBlock(BciBlock block) {
        if (block.isLoopHeader()) {
            // Create the loop header block, which later will merge the backward branches of
            // the loop.
            controlFlowSplit = true;
            LoopBeginNode loopBegin = appendLoopBegin(this.lastInstr, block.startBci);
            lastInstr = loopBegin;

            // Create phi functions for all local variables and operand stack slots.
            frameState.insertLoopPhis(liveness, block.loopId, loopBegin, forceLoopPhis(), stampFromValueForForcedPhis());
            loopBegin.setStateAfter(createFrameState(block.startBci, loopBegin));

            /*
             * We have seen all forward branches. All subsequent backward branches will merge to the
             * loop header. This ensures that the loop header has exactly one non-loop predecessor.
             */
            setFirstInstruction(block, loopBegin);
            /*
             * We need to preserve the frame state builder of the loop header so that we can merge
             * values for phi functions, so make a copy of it.
             */
            setEntryState(block, frameState.copy());

            debug.log("  created loop header %s", loopBegin);
        } else if (lastInstr instanceof MergeNode) {
            /*
             * All inputs of non-loop phi nodes are known by now. We can infer the stamp for the
             * phi, so that parsing continues with more precise type information.
             */
            frameState.inferPhiStamps((AbstractMergeNode) lastInstr);
        }
        assert lastInstr.next() == null : "instructions already appended at block " + block;
        debug.log("  frameState: %s", frameState);

        lastInstr = finishInstruction(lastInstr, frameState);

        int endBCI = stream.endBCI();

        stream.setBCI(block.startBci);
        int bci = block.startBci;
        BytecodesParsed.add(debug, block.endBci - bci);

        /* Reset line number for new block */
        if (graphBuilderConfig.insertFullInfopoints()) {
            previousLineNumber = -1;
        }

        while (bci < endBCI) {
            try (DebugCloseable context = openNodeContext()) {
                if (graphBuilderConfig.insertFullInfopoints() && !parsingIntrinsic()) {
                    currentLineNumber = lnt != null ? lnt.getLineNumber(bci) : -1;
                    if (currentLineNumber != previousLineNumber) {
                        genInfoPointNode(InfopointReason.BYTECODE_POSITION, null);
                        previousLineNumber = currentLineNumber;
                    }
                }

                // read the opcode
                int opcode = stream.currentBC();
                if (traceLevel != 0) {
                    traceInstruction(bci, opcode, bci == block.startBci);
                }
                if (parent == null && bci == entryBCI) {
                    if (block.getJsrScope() != JsrScope.EMPTY_SCOPE) {
                        throw new JsrNotSupportedBailout("OSR into a JSR scope is not supported");
                    }
                    EntryMarkerNode x = append(new EntryMarkerNode());
                    frameState.insertProxies(value -> graph.unique(new EntryProxyNode(value, x)));
                    x.setStateAfter(createFrameState(bci, x));
                }

                processBytecode(bci, opcode);
            } catch (BailoutException e) {
                // Don't wrap bailouts as parser errors
                throw e;
            } catch (Throwable e) {
                throw throwParserError(e);
            }

            if (lastInstr == null || lastInstr.next() != null) {
                break;
            }

            stream.next();
            bci = stream.currentBCI();

            assert block == currentBlock;
            assert checkLastInstruction();
            lastInstr = finishInstruction(lastInstr, frameState);
            if (bci < endBCI) {
                if (bci > block.endBci) {
                    assert !block.getSuccessor(0).isExceptionEntry();
                    assert block.numNormalSuccessors() == 1;
                    // we fell through to the next block, add a goto and break
                    appendGoto(block.getSuccessor(0));
                    break;
                }
            }
        }
    }

    private DebugCloseable openNodeContext(FrameStateBuilder state, int startBci) {
        if (graph.trackNodeSourcePosition()) {
            return graph.withNodeSourcePosition(state.createBytecodePosition(startBci));
        }
        return null;
    }

    private DebugCloseable openNodeContext(ResolvedJavaMethod targetMethod) {
        return openNodeContext(targetMethod, -1);
    }

    private DebugCloseable openNodeContext(ResolvedJavaMethod targetMethod, int bci) {
        if (graph.trackNodeSourcePosition()) {
            return graph.withNodeSourcePosition(new NodeSourcePosition(createBytecodePosition(), targetMethod, bci));
        }
        return null;
    }

    private DebugCloseable openNodeContext() {
        return openNodeContext(frameState, bci());
    }

    /* Also a hook for subclasses. */
    protected boolean forceLoopPhis() {
        return graph.isOSR();
    }

    /* Hook for subclasses. */
    protected boolean stampFromValueForForcedPhis() {
        return false;
    }

    protected boolean checkLastInstruction() {
        if (lastInstr instanceof BeginNode) {
            // ignore
        } else if (lastInstr instanceof StateSplit) {
            StateSplit stateSplit = (StateSplit) lastInstr;
            if (stateSplit.hasSideEffect()) {
                assert stateSplit.stateAfter() != null : "side effect " + lastInstr + " requires a non-null stateAfter";
            }
        }
        return true;
    }

    /* Also a hook for subclasses. */
    protected boolean disableLoopSafepoint() {
        return parsingIntrinsic();
    }

    @SuppressWarnings("try")
    private LoopBeginNode appendLoopBegin(FixedWithNextNode fixedWithNext, int startBci) {
        try (DebugCloseable context = openNodeContext(frameState, startBci)) {
            EndNode preLoopEnd = graph.add(new EndNode());
            LoopBeginNode loopBegin = graph.add(new LoopBeginNode());
            if (disableLoopSafepoint()) {
                loopBegin.disableSafepoint();
            }
            fixedWithNext.setNext(preLoopEnd);
            // Add the single non-loop predecessor of the loop header.
            loopBegin.addForwardEnd(preLoopEnd);
            return loopBegin;
        }
    }

    /**
     * Hook for subclasses to modify the last instruction or add other instructions.
     *
     * @param instr The last instruction (= fixed node) which was added.
     * @param state The current frame state.
     * @return Returns the (new) last instruction.
     */
    protected FixedWithNextNode finishInstruction(FixedWithNextNode instr, FrameStateBuilder state) {
        return instr;
    }

    private void genInfoPointNode(InfopointReason reason, ValueNode escapedReturnValue) {
        if (!parsingIntrinsic() && graphBuilderConfig.insertFullInfopoints()) {
            append(new FullInfopointNode(reason, createFrameState(bci(), null), escapedReturnValue));
        }
    }

    protected void genIf(ValueNode x, Condition cond, ValueNode y) {
        assert x.getStackKind() == y.getStackKind();
        assert currentBlock.getSuccessorCount() == 2;
        BciBlock trueBlock = currentBlock.getSuccessor(0);
        BciBlock falseBlock = currentBlock.getSuccessor(1);

        if (trueBlock == falseBlock) {
            // The target block is the same independent of the condition.
            appendGoto(trueBlock);
            return;
        }

        ValueNode a = x;
        ValueNode b = y;
        BciBlock trueSuccessor = trueBlock;
        BciBlock falseSuccessor = falseBlock;

        CanonicalizedCondition canonicalizedCondition = cond.canonicalize();

        // Check whether the condition needs to mirror the operands.
        if (canonicalizedCondition.mustMirror()) {
            a = y;
            b = x;
        }
        if (canonicalizedCondition.mustNegate()) {
            trueSuccessor = falseBlock;
            falseSuccessor = trueBlock;
        }

        // Create the logic node for the condition.
        LogicNode condition = createLogicNode(canonicalizedCondition.getCanonicalCondition(), a, b);

        double probability = -1;
        if (condition instanceof IntegerEqualsNode) {
            probability = extractInjectedProbability((IntegerEqualsNode) condition);
            // the probability coming from here is about the actual condition
        }

        if (probability == -1) {
            probability = getProfileProbability(canonicalizedCondition.mustNegate());
        }

        probability = clampProbability(probability);
        genIf(condition, trueSuccessor, falseSuccessor, probability);
    }

    protected double getProfileProbability(boolean negate) {
        if (profilingInfo == null) {
            return 0.5;
        }

        assert assertAtIfBytecode();
        double probability = profilingInfo.getBranchTakenProbability(bci());

        if (probability < 0) {
            assert probability == -1 : "invalid probability";
            debug.log("missing probability in %s at bci %d", code, bci());
            return 0.5;
        }

        if (negate && shouldComplementProbability()) {
            // the probability coming from profile is about the original condition
            probability = 1 - probability;
        }
        return probability;
    }

    private static double extractInjectedProbability(IntegerEqualsNode condition) {
        // Propagate injected branch probability if any.
        IntegerEqualsNode equalsNode = condition;
        BranchProbabilityNode probabilityNode = null;
        ValueNode other = null;
        if (equalsNode.getX() instanceof BranchProbabilityNode) {
            probabilityNode = (BranchProbabilityNode) equalsNode.getX();
            other = equalsNode.getY();
        } else if (equalsNode.getY() instanceof BranchProbabilityNode) {
            probabilityNode = (BranchProbabilityNode) equalsNode.getY();
            other = equalsNode.getX();
        }

        if (probabilityNode != null && probabilityNode.getProbability().isConstant() && other != null && other.isConstant()) {
            double probabilityValue = probabilityNode.getProbability().asJavaConstant().asDouble();
            return other.asJavaConstant().asInt() == 0 ? 1.0 - probabilityValue : probabilityValue;
        }
        return -1;
    }

    protected void genIf(LogicNode conditionInput, BciBlock trueBlockInput, BciBlock falseBlockInput, double probabilityInput) {
        BciBlock trueBlock = trueBlockInput;
        BciBlock falseBlock = falseBlockInput;
        LogicNode condition = conditionInput;
        double probability = probabilityInput;
        FrameState stateBefore = null;
        ProfilingPlugin profilingPlugin = this.graphBuilderConfig.getPlugins().getProfilingPlugin();
        if (profilingPlugin != null && profilingPlugin.shouldProfile(this, method)) {
            stateBefore = createCurrentFrameState();
        }

        // Remove a logic negation node.
        if (condition instanceof LogicNegationNode) {
            LogicNegationNode logicNegationNode = (LogicNegationNode) condition;
            BciBlock tmpBlock = trueBlock;
            trueBlock = falseBlock;
            falseBlock = tmpBlock;
            if (shouldComplementProbability()) {
                // the probability coming from profile is about the original condition
                probability = 1 - probability;
            }
            condition = logicNegationNode.getValue();
        }

        if (condition instanceof LogicConstantNode) {
            genConstantTargetIf(trueBlock, falseBlock, condition);
        } else {
            if (condition.graph() == null) {
                condition = genUnique(condition);
            }

            NodeSourcePosition currentPosition = graph.currentNodeSourcePosition();
            if (isNeverExecutedCode(probability)) {
                NodeSourcePosition survivingSuccessorPosition = graph.trackNodeSourcePosition()
                                ? new NodeSourcePosition(currentPosition.getCaller(), currentPosition.getMethod(), falseBlock.startBci)
                                : null;
                append(new FixedGuardNode(condition, UnreachedCode, InvalidateReprofile, true, survivingSuccessorPosition));
                if (profilingPlugin != null && profilingPlugin.shouldProfile(this, method)) {
                    profilingPlugin.profileGoto(this, method, bci(), falseBlock.startBci, stateBefore);
                }
                appendGoto(falseBlock);
                return;
            } else if (isNeverExecutedCode(1 - probability)) {
                NodeSourcePosition survivingSuccessorPosition = graph.trackNodeSourcePosition()
                                ? new NodeSourcePosition(currentPosition.getCaller(), currentPosition.getMethod(), trueBlock.startBci)
                                : null;
                append(new FixedGuardNode(condition, UnreachedCode, InvalidateReprofile, false, survivingSuccessorPosition));
                if (profilingPlugin != null && profilingPlugin.shouldProfile(this, method)) {
                    profilingPlugin.profileGoto(this, method, bci(), trueBlock.startBci, stateBefore);
                }
                appendGoto(trueBlock);
                return;
            }

            if (profilingPlugin != null && profilingPlugin.shouldProfile(this, method)) {
                profilingPlugin.profileIf(this, method, bci(), condition, trueBlock.startBci, falseBlock.startBci, stateBefore);
            }

            int oldBci = stream.currentBCI();
            int trueBlockInt = checkPositiveIntConstantPushed(trueBlock);
            if (trueBlockInt != -1) {
                int falseBlockInt = checkPositiveIntConstantPushed(falseBlock);
                if (falseBlockInt != -1) {
                    if (tryGenConditionalForIf(trueBlock, falseBlock, condition, oldBci, trueBlockInt, falseBlockInt)) {
                        return;
                    }
                }
            }

            this.controlFlowSplit = true;
            FixedNode trueSuccessor = createTarget(trueBlock, frameState, false, false);
            FixedNode falseSuccessor = createTarget(falseBlock, frameState, false, true);
            ValueNode ifNode = genIfNode(condition, trueSuccessor, falseSuccessor, probability);
            postProcessIfNode(ifNode);
            append(ifNode);
        }
    }

    /**
     * Hook for subclasses to decide whether the IfNode probability should be complemented during
     * conversion to Graal IR.
     */
    protected boolean shouldComplementProbability() {
        return true;
    }

    /**
     * Hook for subclasses to generate custom nodes before an IfNode.
     */
    @SuppressWarnings("unused")
    protected void postProcessIfNode(ValueNode node) {
    }

    private boolean tryGenConditionalForIf(BciBlock trueBlock, BciBlock falseBlock, LogicNode condition, int oldBci, int trueBlockInt, int falseBlockInt) {
        if (gotoOrFallThroughAfterConstant(trueBlock) && gotoOrFallThroughAfterConstant(falseBlock) && trueBlock.getSuccessor(0) == falseBlock.getSuccessor(0)) {
            genConditionalForIf(trueBlock, condition, oldBci, trueBlockInt, falseBlockInt, false);
            return true;
        } else if (this.parent != null && returnAfterConstant(trueBlock) && returnAfterConstant(falseBlock)) {
            genConditionalForIf(trueBlock, condition, oldBci, trueBlockInt, falseBlockInt, true);
            return true;
        }
        return false;
    }

    private void genConditionalForIf(BciBlock trueBlock, LogicNode condition, int oldBci, int trueBlockInt, int falseBlockInt, boolean genReturn) {
        ConstantNode trueValue = graph.unique(ConstantNode.forInt(trueBlockInt));
        ConstantNode falseValue = graph.unique(ConstantNode.forInt(falseBlockInt));
        ValueNode conditionalNode = ConditionalNode.create(condition, trueValue, falseValue, NodeView.DEFAULT);
        if (conditionalNode.graph() == null) {
            conditionalNode = graph.addOrUniqueWithInputs(conditionalNode);
        }
        if (genReturn) {
            JavaKind returnKind = method.getSignature().getReturnKind().getStackKind();
            this.genReturn(conditionalNode, returnKind);
        } else {
            frameState.push(JavaKind.Int, conditionalNode);
            appendGoto(trueBlock.getSuccessor(0));
            stream.setBCI(oldBci);
        }
    }

    private LogicNode createLogicNode(CanonicalCondition cond, ValueNode a, ValueNode b) {
        assert !a.getStackKind().isNumericFloat();
        switch (cond) {
            case EQ:
                if (a.getStackKind() == JavaKind.Object) {
                    return genObjectEquals(a, b);
                } else {
                    return genIntegerEquals(a, b);
                }
            case LT:
                assert a.getStackKind() != JavaKind.Object;
                return genIntegerLessThan(a, b);
            default:
                throw GraalError.shouldNotReachHere("Unexpected condition: " + cond);
        }
    }

    private void genConstantTargetIf(BciBlock trueBlock, BciBlock falseBlock, LogicNode condition) {
        LogicConstantNode constantLogicNode = (LogicConstantNode) condition;
        boolean value = constantLogicNode.getValue();
        BciBlock nextBlock = falseBlock;
        if (value) {
            nextBlock = trueBlock;
        }
        int startBci = nextBlock.startBci;
        int targetAtStart = stream.readUByte(startBci);
        if (targetAtStart == Bytecodes.GOTO && nextBlock.getPredecessorCount() == 1) {
            // This is an empty block. Skip it.
            BciBlock successorBlock = nextBlock.successors.get(0);
            ProfilingPlugin profilingPlugin = graphBuilderConfig.getPlugins().getProfilingPlugin();
            if (profilingPlugin != null && profilingPlugin.shouldProfile(this, method)) {
                FrameState stateBefore = createCurrentFrameState();
                profilingPlugin.profileGoto(this, method, bci(), successorBlock.startBci, stateBefore);
            }
            appendGoto(successorBlock);
            assert nextBlock.numNormalSuccessors() == 1;
        } else {
            ProfilingPlugin profilingPlugin = graphBuilderConfig.getPlugins().getProfilingPlugin();
            if (profilingPlugin != null && profilingPlugin.shouldProfile(this, method)) {
                FrameState stateBefore = createCurrentFrameState();
                profilingPlugin.profileGoto(this, method, bci(), nextBlock.startBci, stateBefore);
            }
            appendGoto(nextBlock);
        }
    }

    private int checkPositiveIntConstantPushed(BciBlock block) {
        stream.setBCI(block.startBci);
        int currentBC = stream.currentBC();
        if (currentBC >= Bytecodes.ICONST_0 && currentBC <= Bytecodes.ICONST_5) {
            int constValue = currentBC - Bytecodes.ICONST_0;
            return constValue;
        }
        return -1;
    }

    private boolean gotoOrFallThroughAfterConstant(BciBlock block) {
        stream.setBCI(block.startBci);
        int currentBCI = stream.nextBCI();
        stream.setBCI(currentBCI);
        int currentBC = stream.currentBC();
        return stream.currentBCI() > block.endBci || currentBC == Bytecodes.GOTO || currentBC == Bytecodes.GOTO_W;
    }

    private boolean returnAfterConstant(BciBlock block) {
        stream.setBCI(block.startBci);
        int currentBCI = stream.nextBCI();
        stream.setBCI(currentBCI);
        int currentBC = stream.currentBC();
        return currentBC == Bytecodes.IRETURN;
    }

    @Override
    public StampProvider getStampProvider() {
        return stampProvider;
    }

    @Override
    public MetaAccessProvider getMetaAccess() {
        return metaAccess;
    }

    @Override
    public void push(JavaKind slotKind, ValueNode value) {
        assert value.isAlive();
        frameState.push(slotKind, value);
    }

    @Override
    public ValueNode pop(JavaKind slotKind) {
        return frameState.pop(slotKind);
    }

    @Override
    public ConstantReflectionProvider getConstantReflection() {
        return constantReflection;
    }

    @Override
    public ConstantFieldProvider getConstantFieldProvider() {
        return constantFieldProvider;
    }

    /**
     * Gets the graph being processed by this builder.
     */
    @Override
    public StructuredGraph getGraph() {
        return graph;
    }

    @Override
    public BytecodeParser getParent() {
        return parent;
    }

    @Override
    public IntrinsicContext getIntrinsic() {
        return intrinsicContext;
    }

    @Override
    public String toString() {
        Formatter fmt = new Formatter();
        BytecodeParser bp = this;
        String indent = "";
        while (bp != null) {
            if (bp != this) {
                fmt.format("%n%s", indent);
            }
            fmt.format("%s [bci: %d, intrinsic: %s]", bp.code.asStackTraceElement(bp.bci()), bp.bci(), bp.parsingIntrinsic());
            fmt.format("%n%s", new BytecodeDisassembler().disassemble(bp.code, bp.bci(), bp.bci() + 10));
            bp = bp.parent;
            indent += " ";
        }
        return fmt.toString();
    }

    @Override
    public BailoutException bailout(String string) {
        FrameState currentFrameState = createFrameState(bci(), null);
        StackTraceElement[] elements = GraphUtil.approxSourceStackTraceElement(currentFrameState);
        BailoutException bailout = new PermanentBailoutException(string);
        throw GraphUtil.createBailoutException(string, bailout, elements);
    }

    private FrameState createFrameState(int bci, StateSplit forStateSplit) {
        if (currentBlock != null && bci > currentBlock.endBci) {
            frameState.clearNonLiveLocals(currentBlock, liveness, false);
        }
        return frameState.create(bci, forStateSplit);
    }

    @Override
    public void setStateAfter(StateSplit sideEffect) {
        assert sideEffect.hasSideEffect() || sideEffect instanceof AbstractMergeNode;
        FrameState stateAfter = createFrameState(stream.nextBCI(), sideEffect);
        sideEffect.setStateAfter(stateAfter);
    }

    protected NodeSourcePosition createBytecodePosition() {
        NodeSourcePosition bytecodePosition = frameState.createBytecodePosition(bci());
        return bytecodePosition;
    }

    public void setCurrentFrameState(FrameStateBuilder frameState) {
        this.frameState = frameState;
    }

    protected final BytecodeStream getStream() {
        return stream;
    }

    @Override
    public int bci() {
        return stream.currentBCI();
    }

    public void loadLocal(int index, JavaKind kind) {
        ValueNode value = frameState.loadLocal(index, kind);
        frameState.push(kind, value);
    }

    @SuppressWarnings("try")
    public void loadLocalObject(int index) {
        ValueNode value = frameState.loadLocal(index, JavaKind.Object);

        int nextBCI = stream.nextBCI();
        int nextBC = stream.readUByte(nextBCI);
        if (nextBCI <= currentBlock.endBci && nextBC == Bytecodes.GETFIELD) {
            stream.next();
            try (DebugCloseable ignored = openNodeContext()) {
                genGetField(stream.readCPI(), Bytecodes.GETFIELD, value);
            }
        } else {
            frameState.push(JavaKind.Object, value);
        }
    }

    public void storeLocal(JavaKind kind, int index) {
        ValueNode value = frameState.pop(kind);
        frameState.storeLocal(index, kind, value);
    }

    protected void genLoadConstant(int cpi, int opcode) {
        Object con = lookupConstant(cpi, opcode);

        if (con instanceof JavaType) {
            // this is a load of class constant which might be unresolved
            JavaType type = (JavaType) con;
            if (typeIsResolved(type)) {
                frameState.push(JavaKind.Object, appendConstant(getConstantReflection().asJavaClass((ResolvedJavaType) type)));
            } else {
                handleUnresolvedLoadConstant(type);
            }
        } else if (con instanceof JavaConstant) {
            JavaConstant constant = (JavaConstant) con;
            frameState.push(constant.getJavaKind(), appendConstant(constant));
        } else {
            throw new Error("lookupConstant returned an object of incorrect type");
        }
    }

    private JavaKind refineComponentType(ValueNode array, JavaKind kind) {
        if (kind == JavaKind.Byte) {
            JavaType type = array.stamp(NodeView.DEFAULT).javaType(metaAccess);
            if (type.isArray()) {
                JavaType componentType = type.getComponentType();
                if (componentType != null) {
                    JavaKind refinedKind = componentType.getJavaKind();
                    assert refinedKind == JavaKind.Byte || refinedKind == JavaKind.Boolean;
                    return refinedKind;
                }
            }
        }
        return kind;
    }

    private void genLoadIndexed(JavaKind kind) {
        ValueNode index = frameState.pop(JavaKind.Int);
        ValueNode array = frameState.pop(JavaKind.Object);

        array = maybeEmitExplicitNullCheck(array);
        GuardingNode boundsCheck = maybeEmitExplicitBoundsCheck(array, index);

        for (NodePlugin plugin : graphBuilderConfig.getPlugins().getNodePlugins()) {
            if (plugin.handleLoadIndexed(this, array, index, boundsCheck, kind)) {
                return;
            }
        }

        JavaKind actualKind = refineComponentType(array, kind);
        frameState.push(actualKind, append(genLoadIndexed(array, index, boundsCheck, actualKind)));
    }

    private void genStoreIndexed(JavaKind kind) {
        ValueNode value = frameState.pop(kind);
        ValueNode index = frameState.pop(JavaKind.Int);
        ValueNode array = frameState.pop(JavaKind.Object);

        array = maybeEmitExplicitNullCheck(array);
        GuardingNode boundsCheck = maybeEmitExplicitBoundsCheck(array, index);
        GuardingNode storeCheck = maybeEmitExplicitStoreCheck(array, kind, value);

        for (NodePlugin plugin : graphBuilderConfig.getPlugins().getNodePlugins()) {
            if (plugin.handleStoreIndexed(this, array, index, boundsCheck, storeCheck, kind, value)) {
                return;
            }
        }

        JavaKind actualKind = refineComponentType(array, kind);
        genStoreIndexed(array, index, boundsCheck, storeCheck, actualKind, maskSubWordValue(value, actualKind));
    }

    private void genArithmeticOp(JavaKind kind, int opcode) {
        ValueNode y = frameState.pop(kind);
        ValueNode x = frameState.pop(kind);
        ValueNode v;
        switch (opcode) {
            case IADD:
            case LADD:
                v = genIntegerAdd(x, y);
                break;
            case FADD:
            case DADD:
                v = genFloatAdd(x, y);
                break;
            case ISUB:
            case LSUB:
                v = genIntegerSub(x, y);
                break;
            case FSUB:
            case DSUB:
                v = genFloatSub(x, y);
                break;
            case IMUL:
            case LMUL:
                v = genIntegerMul(x, y);
                break;
            case FMUL:
            case DMUL:
                v = genFloatMul(x, y);
                break;
            case FDIV:
            case DDIV:
                v = genFloatDiv(x, y);
                break;
            case FREM:
            case DREM:
                v = genFloatRem(x, y);
                break;
            default:
                throw shouldNotReachHere();
        }
        frameState.push(kind, append(v));
    }

    private void genIntegerDivOp(JavaKind kind, int opcode) {
        ValueNode y = frameState.pop(kind);
        ValueNode x = frameState.pop(kind);

        GuardingNode zeroCheck = maybeEmitExplicitDivisionByZeroCheck(y);

        ValueNode v;
        switch (opcode) {
            case IDIV:
            case LDIV:
                v = genIntegerDiv(x, y, zeroCheck);
                break;
            case IREM:
            case LREM:
                v = genIntegerRem(x, y, zeroCheck);
                break;
            default:
                throw shouldNotReachHere();
        }
        frameState.push(kind, append(v));
    }

    private void genNegateOp(JavaKind kind) {
        ValueNode x = frameState.pop(kind);
        frameState.push(kind, append(genNegateOp(x)));
    }

    private void genShiftOp(JavaKind kind, int opcode) {
        ValueNode s = frameState.pop(JavaKind.Int);
        ValueNode x = frameState.pop(kind);
        ValueNode v;
        switch (opcode) {
            case ISHL:
            case LSHL:
                v = genLeftShift(x, s);
                break;
            case ISHR:
            case LSHR:
                v = genRightShift(x, s);
                break;
            case IUSHR:
            case LUSHR:
                v = genUnsignedRightShift(x, s);
                break;
            default:
                throw shouldNotReachHere();
        }
        frameState.push(kind, append(v));
    }

    private void genLogicOp(JavaKind kind, int opcode) {
        ValueNode y = frameState.pop(kind);
        ValueNode x = frameState.pop(kind);
        ValueNode v;
        switch (opcode) {
            case IAND:
            case LAND:
                v = genAnd(x, y);
                break;
            case IOR:
            case LOR:
                v = genOr(x, y);
                break;
            case IXOR:
            case LXOR:
                v = genXor(x, y);
                break;
            default:
                throw shouldNotReachHere();
        }
        frameState.push(kind, append(v));
    }

    private void genCompareOp(JavaKind kind, boolean isUnorderedLess) {
        ValueNode y = frameState.pop(kind);
        ValueNode x = frameState.pop(kind);
        frameState.push(JavaKind.Int, append(genNormalizeCompare(x, y, isUnorderedLess)));
    }

    private void genFloatConvert(FloatConvert op, JavaKind from, JavaKind to) {
        ValueNode input = frameState.pop(from);
        frameState.push(to, append(genFloatConvert(op, input)));
    }

    private void genSignExtend(JavaKind from, JavaKind to) {
        ValueNode input = frameState.pop(from);
        if (from != from.getStackKind()) {
            input = append(genNarrow(input, from.getBitCount()));
        }
        frameState.push(to, append(genSignExtend(input, to.getBitCount())));
    }

    private void genZeroExtend(JavaKind from, JavaKind to) {
        ValueNode input = frameState.pop(from);
        if (from != from.getStackKind()) {
            input = append(genNarrow(input, from.getBitCount()));
        }
        frameState.push(to, append(genZeroExtend(input, to.getBitCount())));
    }

    private void genNarrow(JavaKind from, JavaKind to) {
        ValueNode input = frameState.pop(from);
        frameState.push(to, append(genNarrow(input, to.getBitCount())));
    }

    private void genIncrement() {
        int index = getStream().readLocalIndex();
        int delta = getStream().readIncrement();
        ValueNode x = frameState.loadLocal(index, JavaKind.Int);
        ValueNode y = appendConstant(JavaConstant.forInt(delta));
        frameState.storeLocal(index, JavaKind.Int, append(genIntegerAdd(x, y)));
    }

    private void genIfZero(Condition cond) {
        ValueNode y = appendConstant(JavaConstant.INT_0);
        ValueNode x = frameState.pop(JavaKind.Int);
        genIf(x, cond, y);
    }

    private void genIfNull(Condition cond) {
        ValueNode y = appendConstant(JavaConstant.NULL_POINTER);
        ValueNode x = frameState.pop(JavaKind.Object);
        genIf(x, cond, y);
    }

    private void genIfSame(JavaKind kind, Condition cond) {
        ValueNode y = frameState.pop(kind);
        ValueNode x = frameState.pop(kind);
        genIf(x, cond, y);
    }

    private static void initialize(ResolvedJavaType resolvedType) {
        /*
         * Since we're potentially triggering class initialization here, we need synchronization to
         * mitigate the potential for class initialization related deadlock being caused by the
         * compiler (e.g., https://github.com/graalvm/graal-core/pull/232/files#r90788550).
         */
        synchronized (BytecodeParser.class) {
            resolvedType.initialize();
        }
    }

    protected JavaType lookupType(int cpi, int bytecode) {
        maybeEagerlyResolve(cpi, bytecode);
        JavaType result = constantPool.lookupType(cpi, bytecode);
        assert !graphBuilderConfig.unresolvedIsError() || result instanceof ResolvedJavaType;
        return result;
    }

    private String unresolvedMethodAssertionMessage(JavaMethod result) {
        String message = result.format("%H.%n(%P)%R");
        if (GraalServices.Java8OrEarlier) {
            JavaType declaringClass = result.getDeclaringClass();
            String className = declaringClass.getName();
            switch (className) {
                case "Ljava/nio/ByteBuffer;":
                case "Ljava/nio/ShortBuffer;":
                case "Ljava/nio/CharBuffer;":
                case "Ljava/nio/IntBuffer;":
                case "Ljava/nio/LongBuffer;":
                case "Ljava/nio/FloatBuffer;":
                case "Ljava/nio/DoubleBuffer;":
                case "Ljava/nio/MappedByteBuffer;": {
                    switch (result.getName()) {
                        case "position":
                        case "limit":
                        case "mark":
                        case "reset":
                        case "clear":
                        case "flip":
                        case "rewind": {
                            String returnType = result.getSignature().getReturnType(null).toJavaName();
                            if (returnType.equals(declaringClass.toJavaName())) {
                                message += String.format(" [Probably cause: %s was compiled with javac from JDK 9+ using " +
                                                "`-target 8` and `-source 8` options. See https://bugs.openjdk.java.net/browse/JDK-4774077 for details.]", method.getDeclaringClass().toClassName());
                            }
                        }
                    }
                    break;
                }
            }
        }
        return message;
    }

    private JavaMethod lookupMethod(int cpi, int opcode) {
        maybeEagerlyResolve(cpi, opcode);
        JavaMethod result = constantPool.lookupMethod(cpi, opcode);
        assert !graphBuilderConfig.unresolvedIsError() || result instanceof ResolvedJavaMethod : unresolvedMethodAssertionMessage(result);
        return result;
    }

    protected JavaField lookupField(int cpi, int opcode) {
        maybeEagerlyResolve(cpi, opcode);
        JavaField result = constantPool.lookupField(cpi, method, opcode);
        return lookupField(result);
    }

    protected JavaField lookupField(JavaField result) {
        assert !graphBuilderConfig.unresolvedIsError() || result instanceof ResolvedJavaField : "Not resolved: " + result;
        if (parsingIntrinsic() || eagerInitializing) {
            if (result instanceof ResolvedJavaField) {
                ResolvedJavaType declaringClass = ((ResolvedJavaField) result).getDeclaringClass();
                if (!declaringClass.isInitialized()) {
                    // Even with eager initialization, superinterfaces are not always initialized.
                    // See StaticInterfaceFieldTest
                    assert !eagerInitializing || declaringClass.isInterface() : "Declaring class not initialized but not an interface? " + declaringClass;
                    initialize(declaringClass);
                }
            }
        }
        assert !uninitializedIsError || (result instanceof ResolvedJavaField && ((ResolvedJavaField) result).getDeclaringClass().isInitialized()) : result;
        return result;
    }

    private Object lookupConstant(int cpi, int opcode) {
        maybeEagerlyResolve(cpi, opcode);
        Object result = constantPool.lookupConstant(cpi);
        assert !graphBuilderConfig.unresolvedIsError() || !(result instanceof JavaType) || (result instanceof ResolvedJavaType) : result;
        return result;
    }

    protected void maybeEagerlyResolve(int cpi, int bytecode) {
        if (intrinsicContext != null) {
            constantPool.loadReferencedType(cpi, bytecode);
        } else if (graphBuilderConfig.eagerResolving()) {
            /*
             * Since we're potentially triggering class initialization here, we need synchronization
             * to mitigate the potential for class initialization related deadlock being caused by
             * the compiler (e.g., https://github.com/graalvm/graal-core/pull/232/files#r90788550).
             */
            synchronized (BytecodeParser.class) {
                ClassInitializationPlugin classInitializationPlugin = graphBuilderConfig.getPlugins().getClassInitializationPlugin();
                if (classInitializationPlugin != null) {
                    classInitializationPlugin.loadReferencedType(this, constantPool, cpi, bytecode);
                } else {
                    constantPool.loadReferencedType(cpi, bytecode);
                }
            }
        }
    }

    protected void maybeEagerlyInitialize(ResolvedJavaType resolvedType) {
        if (!resolvedType.isInitialized() && eagerInitializing) {
            initialize(resolvedType);
        }
    }

    private JavaTypeProfile getProfileForTypeCheck(TypeReference type) {
        if (parsingIntrinsic() || profilingInfo == null || !optimisticOpts.useTypeCheckHints(getOptions()) || type.isExact()) {
            return null;
        } else {
            return profilingInfo.getTypeProfile(bci());
        }
    }

    private void genCheckCast(int cpi) {
        JavaType type = lookupType(cpi, CHECKCAST);
        ValueNode object = frameState.pop(JavaKind.Object);
        genCheckCast(type, object);
    }

    protected void genCheckCast(JavaType type, ValueNode object) {
        if (typeIsResolved(type)) {
            genCheckCast((ResolvedJavaType) type, object);
        } else {
            handleUnresolvedCheckCast(type, object);
        }
    }

    protected void genCheckCast(ResolvedJavaType resolvedType, ValueNode objectIn) {
        ValueNode object = objectIn;
        TypeReference checkedType = TypeReference.createTrusted(graph.getAssumptions(), resolvedType);
        JavaTypeProfile profile = getProfileForTypeCheck(checkedType);

        for (NodePlugin plugin : graphBuilderConfig.getPlugins().getNodePlugins()) {
            if (plugin.handleCheckCast(this, object, checkedType.getType(), profile)) {
                return;
            }
        }

        ValueNode castNode = null;
        if (profile != null) {
            if (profile.getNullSeen().isFalse()) {
                object = nullCheckedValue(object);
                ResolvedJavaType singleType = profile.asSingleType();
                if (singleType != null && checkedType.getType().isAssignableFrom(singleType)) {
                    LogicNode typeCheck = append(createInstanceOf(TypeReference.createExactTrusted(singleType), object, profile));
                    if (typeCheck.isTautology()) {
                        castNode = object;
                    } else {
                        FixedGuardNode fixedGuard = append(new FixedGuardNode(typeCheck, DeoptimizationReason.TypeCheckedInliningViolated, DeoptimizationAction.InvalidateReprofile, false));
                        castNode = append(PiNode.create(object, StampFactory.objectNonNull(TypeReference.createExactTrusted(singleType)), fixedGuard));
                    }
                }
            }
        }

        boolean nonNull = ((ObjectStamp) object.stamp(NodeView.DEFAULT)).nonNull();
        if (castNode == null) {
            LogicNode condition = genUnique(createInstanceOfAllowNull(checkedType, object, null));
            if (condition.isTautology()) {
                castNode = object;
            } else {
                GuardingNode guard;
                if (needsExplicitClassCastException(object)) {
                    Constant hub = getConstantReflection().asObjectHub(resolvedType);
                    Stamp hubStamp = getStampProvider().createHubStamp(StampFactory.object(TypeReference.createExactTrusted(resolvedType)));
                    ConstantNode hubConstant = ConstantNode.forConstant(hubStamp, hub, getMetaAccess(), graph);
                    guard = emitBytecodeExceptionCheck(condition, true, BytecodeExceptionKind.CLASS_CAST, object, hubConstant);
                } else {
                    guard = append(new FixedGuardNode(condition, DeoptimizationReason.ClassCastException, DeoptimizationAction.InvalidateReprofile, false));
                }
                castNode = append(PiNode.create(object, StampFactory.object(checkedType, nonNull), guard.asNode()));
            }
        }
        frameState.push(JavaKind.Object, castNode);
    }

    private void genInstanceOf(int cpi) {
        JavaType type = lookupType(cpi, INSTANCEOF);
        ValueNode object = frameState.pop(JavaKind.Object);
        genInstanceOf(type, object);
    }

    protected void genInstanceOf(JavaType type, ValueNode object) {
        if (typeIsResolved(type)) {
            genInstanceOf((ResolvedJavaType) type, object);
        } else {
            handleUnresolvedInstanceOf(type, object);
        }
    }

    protected void genInstanceOf(ResolvedJavaType resolvedType, ValueNode objectIn) {
        ValueNode object = objectIn;
        TypeReference checkedType = TypeReference.createTrusted(graph.getAssumptions(), resolvedType);
        JavaTypeProfile profile = getProfileForTypeCheck(checkedType);

        for (NodePlugin plugin : graphBuilderConfig.getPlugins().getNodePlugins()) {
            if (plugin.handleInstanceOf(this, object, checkedType.getType(), profile)) {
                return;
            }
        }

        LogicNode instanceOfNode = null;
        if (profile != null) {
            if (profile.getNullSeen().isFalse()) {
                object = nullCheckedValue(object);
                ResolvedJavaType singleType = profile.asSingleType();
                if (singleType != null) {
                    LogicNode typeCheck = append(createInstanceOf(TypeReference.createExactTrusted(singleType), object, profile));
                    if (!typeCheck.isTautology()) {
                        append(new FixedGuardNode(typeCheck, DeoptimizationReason.TypeCheckedInliningViolated, DeoptimizationAction.InvalidateReprofile));
                    }
                    instanceOfNode = LogicConstantNode.forBoolean(checkedType.getType().isAssignableFrom(singleType));
                }
            }
        }
        if (instanceOfNode == null) {
            instanceOfNode = createInstanceOf(checkedType, object, null);
        }
        LogicNode logicNode = genUnique(instanceOfNode);

        int next = getStream().nextBCI();
        int value = getStream().readUByte(next);
        if (next <= currentBlock.endBci && (value == Bytecodes.IFEQ || value == Bytecodes.IFNE)) {
            getStream().next();
            BciBlock firstSucc = currentBlock.getSuccessor(0);
            BciBlock secondSucc = currentBlock.getSuccessor(1);
            if (firstSucc != secondSucc) {
                boolean negate = value != Bytecodes.IFNE;
                if (negate) {
                    BciBlock tmp = firstSucc;
                    firstSucc = secondSucc;
                    secondSucc = tmp;
                }
                genIf(instanceOfNode, firstSucc, secondSucc, getProfileProbability(negate));
            } else {
                appendGoto(firstSucc);
            }
        } else {
            // Most frequent for value is IRETURN, followed by ISTORE.
            frameState.push(JavaKind.Int, append(genConditional(logicNode)));
        }
    }

    protected void genNewInstance(int cpi) {
        JavaType type = lookupType(cpi, NEW);
        genNewInstance(type);
    }

    protected void genNewInstance(JavaType type) {
        if (typeIsResolved(type)) {
            genNewInstance((ResolvedJavaType) type);
        } else {
            handleUnresolvedNewInstance(type);
        }
    }

    protected void genNewInstance(ResolvedJavaType resolvedType) {
        if (resolvedType.isAbstract() || resolvedType.isInterface()) {
            handleIllegalNewInstance(resolvedType);
            return;
        }
        maybeEagerlyInitialize(resolvedType);

        ClassInitializationPlugin classInitializationPlugin = graphBuilderConfig.getPlugins().getClassInitializationPlugin();
        if (!resolvedType.isInitialized() && classInitializationPlugin == null) {
            handleIllegalNewInstance(resolvedType);
            return;
        }

        ResolvedJavaType[] skippedExceptionTypes = this.graphBuilderConfig.getSkippedExceptionTypes();
        if (skippedExceptionTypes != null) {
            for (ResolvedJavaType exceptionType : skippedExceptionTypes) {
                if (exceptionType.isAssignableFrom(resolvedType)) {
                    append(new DeoptimizeNode(DeoptimizationAction.InvalidateRecompile, RuntimeConstraint));
                    return;
                }
            }
        }

        if (classInitializationPlugin != null) {
            classInitializationPlugin.apply(this, resolvedType, this::createCurrentFrameState);
        }

        for (NodePlugin plugin : graphBuilderConfig.getPlugins().getNodePlugins()) {
            if (plugin.handleNewInstance(this, resolvedType)) {
                return;
            }
        }

        frameState.push(JavaKind.Object, append(createNewInstance(resolvedType, true)));
    }

    /**
     * Gets the kind of array elements for the array type code that appears in a
     * {@link Bytecodes#NEWARRAY} bytecode.
     *
     * @param code the array type code
     * @return the kind from the array type code
     */
    private static Class<?> arrayTypeCodeToClass(int code) {
        switch (code) {
            case 4:
                return boolean.class;
            case 5:
                return char.class;
            case 6:
                return float.class;
            case 7:
                return double.class;
            case 8:
                return byte.class;
            case 9:
                return short.class;
            case 10:
                return int.class;
            case 11:
                return long.class;
            default:
                throw new IllegalArgumentException("unknown array type code: " + code);
        }
    }

    private void genNewPrimitiveArray(int typeCode) {
        ResolvedJavaType elementType = metaAccess.lookupJavaType(arrayTypeCodeToClass(typeCode));
        ValueNode length = frameState.pop(JavaKind.Int);

        for (NodePlugin plugin : graphBuilderConfig.getPlugins().getNodePlugins()) {
            if (plugin.handleNewArray(this, elementType, length)) {
                return;
            }
        }

        frameState.push(JavaKind.Object, append(createNewArray(elementType, length, true)));
    }

    private void genNewObjectArray(int cpi) {
        JavaType type = lookupType(cpi, ANEWARRAY);
        genNewObjectArray(type);
    }

    private void genNewObjectArray(JavaType type) {
        if (typeIsResolved(type)) {
            genNewObjectArray((ResolvedJavaType) type);
        } else {
            ValueNode length = frameState.pop(JavaKind.Int);
            handleUnresolvedNewObjectArray(type, length);
        }
    }

    private void genNewObjectArray(ResolvedJavaType resolvedType) {

        ClassInitializationPlugin classInitializationPlugin = this.graphBuilderConfig.getPlugins().getClassInitializationPlugin();
        if (classInitializationPlugin != null) {
            classInitializationPlugin.apply(this, resolvedType.getArrayClass(), this::createCurrentFrameState);
        }

        ValueNode length = frameState.pop(JavaKind.Int);
        for (NodePlugin plugin : graphBuilderConfig.getPlugins().getNodePlugins()) {
            if (plugin.handleNewArray(this, resolvedType, length)) {
                return;
            }
        }

        frameState.push(JavaKind.Object, append(createNewArray(resolvedType, length, true)));
    }

    private void genNewMultiArray(int cpi) {
        JavaType type = lookupType(cpi, MULTIANEWARRAY);
        int rank = getStream().readUByte(bci() + 3);
        ValueNode[] dims = new ValueNode[rank];
        genNewMultiArray(type, rank, dims);
    }

    private void genNewMultiArray(JavaType type, int rank, ValueNode[] dims) {
        if (typeIsResolved(type)) {
            genNewMultiArray((ResolvedJavaType) type, rank, dims);
        } else {
            for (int i = rank - 1; i >= 0; i--) {
                dims[i] = frameState.pop(JavaKind.Int);
            }
            handleUnresolvedNewMultiArray(type, dims);
        }
    }

    private void genNewMultiArray(ResolvedJavaType resolvedType, int rank, ValueNode[] dims) {

        ClassInitializationPlugin classInitializationPlugin = this.graphBuilderConfig.getPlugins().getClassInitializationPlugin();
        if (classInitializationPlugin != null) {
            classInitializationPlugin.apply(this, resolvedType, this::createCurrentFrameState);
        }

        for (int i = rank - 1; i >= 0; i--) {
            dims[i] = frameState.pop(JavaKind.Int);
        }

        for (NodePlugin plugin : graphBuilderConfig.getPlugins().getNodePlugins()) {
            if (plugin.handleNewMultiArray(this, resolvedType, dims)) {
                return;
            }
        }

        frameState.push(JavaKind.Object, append(createNewMultiArray(resolvedType, dims)));
    }

    protected void genGetField(int cpi, int opcode) {
        genGetField(cpi, opcode, frameState.pop(JavaKind.Object));
    }

    protected void genGetField(int cpi, int opcode, ValueNode receiverInput) {
        JavaField field = lookupField(cpi, opcode);
        genGetField(field, receiverInput);
    }

    private void genGetField(JavaField field, ValueNode receiverInput) {
        if (field instanceof ResolvedJavaField) {
            ValueNode receiver = maybeEmitExplicitNullCheck(receiverInput);
            ResolvedJavaField resolvedField = (ResolvedJavaField) field;
            genGetField(resolvedField, receiver);
        } else {
            handleUnresolvedLoadField(field, receiverInput);
        }
    }

    private void genGetField(ResolvedJavaField resolvedField, ValueNode receiver) {
        if (!parsingIntrinsic() && GeneratePIC.getValue(getOptions())) {
            graph.recordField(resolvedField);
        }

        for (NodePlugin plugin : graphBuilderConfig.getPlugins().getNodePlugins()) {
            if (plugin.handleLoadField(this, receiver, resolvedField)) {
                return;
            }
        }

        ValueNode fieldRead = append(genLoadField(receiver, resolvedField));

        if (resolvedField.getDeclaringClass().getName().equals("Ljava/lang/ref/Reference;") && resolvedField.getName().equals("referent")) {
            LocationIdentity referentIdentity = new FieldLocationIdentity(resolvedField);
            append(new MembarNode(0, referentIdentity));
        }

        JavaKind fieldKind = resolvedField.getJavaKind();

        pushLoadField(resolvedField, fieldRead, fieldKind);
    }

    /**
     * Returns true if an explicit null check should be emitted for the given object.
     *
     * @param object The object that is accessed.
     */
    protected boolean needsExplicitNullCheckException(ValueNode object) {
        return needsExplicitException();
    }

    /**
     * Returns true if an explicit null check should be emitted for the given object.
     *
     * @param array The array that is accessed.
     * @param index The array index that is accessed.
     */
    protected boolean needsExplicitBoundsCheckException(ValueNode array, ValueNode index) {
        return needsExplicitException();
    }

    /**
     * Returns true if an explicit check for a {@link ClassCastException} should be emitted for the
     * given object.
     *
     * @param object The object that is accessed.
     */
    protected boolean needsExplicitClassCastException(ValueNode object) {
        return needsExplicitException();
    }

    /**
     * Returns true if an explicit null check should be emitted for the given object.
     *
     * @param array The array that is accessed.
     * @param value The value that is stored into the array.
     */
    protected boolean needsExplicitStoreCheckException(ValueNode array, ValueNode value) {
        return needsExplicitException();
    }

    /**
     * Returns true if an explicit null check should be emitted for the given object.
     *
     * @param y The dividend.
     */
    protected boolean needsExplicitDivisionByZeroException(ValueNode y) {
        return needsExplicitException();
    }

    @Override
    public boolean needsExplicitException() {
        BytecodeExceptionMode exceptionMode = graphBuilderConfig.getBytecodeExceptionMode();
        if (exceptionMode == BytecodeExceptionMode.CheckAll || StressExplicitExceptionCode.getValue(options)) {
            return true;
        } else if (exceptionMode == BytecodeExceptionMode.Profile && profilingInfo != null) {
            return profilingInfo.getExceptionSeen(bci()) == TriState.TRUE;
        }
        return false;
    }

    @Override
    public AbstractBeginNode genExplicitExceptionEdge(BytecodeExceptionKind exceptionKind) {
        BytecodeExceptionNode exceptionNode = graph.add(new BytecodeExceptionNode(metaAccess, exceptionKind));
        exceptionNode.setStateAfter(createFrameState(bci(), exceptionNode));
        AbstractBeginNode exceptionDispatch = handleException(exceptionNode, bci(), false);
        exceptionNode.setNext(exceptionDispatch);
        return BeginNode.begin(exceptionNode);
    }

    protected void genPutField(int cpi, int opcode) {
        JavaField field = lookupField(cpi, opcode);
        genPutField(field);
    }

    protected void genPutField(JavaField field) {
        genPutField(field, frameState.pop(field.getJavaKind()));
    }

    private void genPutField(JavaField field, ValueNode value) {
        ValueNode receiverInput = frameState.pop(JavaKind.Object);

        if (field instanceof ResolvedJavaField) {
            ValueNode receiver = maybeEmitExplicitNullCheck(receiverInput);
            ResolvedJavaField resolvedField = (ResolvedJavaField) field;

            if (!parsingIntrinsic() && GeneratePIC.getValue(getOptions())) {
                graph.recordField(resolvedField);
            }

            for (NodePlugin plugin : graphBuilderConfig.getPlugins().getNodePlugins()) {
                if (plugin.handleStoreField(this, receiver, resolvedField, value)) {
                    return;
                }
            }

            if (resolvedField.isFinal() && method.isConstructor()) {
                finalBarrierRequired = true;
            }
            genStoreField(receiver, resolvedField, value);
        } else {
            handleUnresolvedStoreField(field, value, receiverInput);
        }
    }

    protected void genGetStatic(int cpi, int opcode) {
        JavaField field = lookupField(cpi, opcode);
        genGetStatic(field);
    }

    private void genGetStatic(JavaField field) {
        ResolvedJavaField resolvedField = resolveStaticFieldAccess(field, null);
        if (resolvedField == null) {
            return;
        }

        if (!parsingIntrinsic() && GeneratePIC.getValue(getOptions())) {
            graph.recordField(resolvedField);
        }

        /*
         * Javac does not allow use of "$assertionsDisabled" for a field name but Eclipse does, in
         * which case a suffix is added to the generated field.
         */
        if ((parsingIntrinsic() || graphBuilderConfig.omitAssertions()) && resolvedField.isSynthetic() && resolvedField.getName().startsWith("$assertionsDisabled")) {
            frameState.push(field.getJavaKind(), ConstantNode.forBoolean(true, graph));
            return;
        }

        ResolvedJavaType holder = resolvedField.getDeclaringClass();
        ClassInitializationPlugin classInitializationPlugin = this.graphBuilderConfig.getPlugins().getClassInitializationPlugin();
        if (classInitializationPlugin != null) {
            classInitializationPlugin.apply(this, holder, this::createCurrentFrameState);
        }

        for (NodePlugin plugin : graphBuilderConfig.getPlugins().getNodePlugins()) {
            if (plugin.handleLoadStaticField(this, resolvedField)) {
                return;
            }
        }

        ValueNode fieldRead = append(genLoadField(null, resolvedField));
        JavaKind fieldKind = resolvedField.getJavaKind();

        pushLoadField(resolvedField, fieldRead, fieldKind);
    }

    /**
     * Pushes a loaded field onto the stack. If the loaded field is volatile, a
     * {@link StateSplitProxyNode} is appended so that deoptimization does not deoptimize to a point
     * before the field load.
     */
    private void pushLoadField(ResolvedJavaField resolvedField, ValueNode fieldRead, JavaKind fieldKind) {
        if (resolvedField.isVolatile() && fieldRead instanceof LoadFieldNode) {
            StateSplitProxyNode readProxy = append(genVolatileFieldReadProxy(fieldRead));
            frameState.push(fieldKind, readProxy);
            readProxy.setStateAfter(frameState.create(stream.nextBCI(), readProxy));
        } else {
            frameState.push(fieldKind, fieldRead);
        }
    }

    private ResolvedJavaField resolveStaticFieldAccess(JavaField field, ValueNode value) {
        if (field instanceof ResolvedJavaField) {
            ResolvedJavaField resolvedField = (ResolvedJavaField) field;
            ResolvedJavaType resolvedType = resolvedField.getDeclaringClass();
            maybeEagerlyInitialize(resolvedType);

            if (resolvedType.isInitialized() || graphBuilderConfig.getPlugins().getClassInitializationPlugin() != null) {
                return resolvedField;
            }

            /*
             * Static fields have initialization semantics but may be safely accessed under certain
             * conditions while the class is being initialized. Executing in the clinit or init of
             * subclasses (but not implementers) of the field holder are sure to be running in a
             * context where the access is safe.
             */
            if (!resolvedType.isInterface() && resolvedType.isAssignableFrom(method.getDeclaringClass())) {
                if (method.isClassInitializer() || method.isConstructor()) {
                    return resolvedField;
                }
            }
        }
        if (value == null) {
            handleUnresolvedLoadField(field, null);
        } else {
            handleUnresolvedStoreField(field, value, null);

        }
        return null;
    }

    protected void genPutStatic(int cpi, int opcode) {
        JavaField field = lookupField(cpi, opcode);
        genPutStatic(field);
    }

    protected void genPutStatic(JavaField field) {
        int stackSizeBefore = frameState.stackSize();
        ValueNode value = frameState.pop(field.getJavaKind());
        ResolvedJavaField resolvedField = resolveStaticFieldAccess(field, value);
        if (resolvedField == null) {
            return;
        }

        if (!parsingIntrinsic() && GeneratePIC.getValue(getOptions())) {
            graph.recordField(resolvedField);
        }

        ClassInitializationPlugin classInitializationPlugin = this.graphBuilderConfig.getPlugins().getClassInitializationPlugin();
        ResolvedJavaType holder = resolvedField.getDeclaringClass();
        if (classInitializationPlugin != null) {
            Supplier<FrameState> stateBefore = () -> {
                JavaKind[] pushedSlotKinds = {field.getJavaKind()};
                ValueNode[] pushedValues = {value};
                FrameState fs = frameState.create(bci(), getNonIntrinsicAncestor(), false, pushedSlotKinds, pushedValues);
                assert stackSizeBefore == fs.stackSize();
                return fs;
            };
            classInitializationPlugin.apply(this, holder, stateBefore);
        }

        for (NodePlugin plugin : graphBuilderConfig.getPlugins().getNodePlugins()) {
            if (plugin.handleStoreStaticField(this, resolvedField, value)) {
                return;
            }
        }

        genStoreField(null, resolvedField, value);
    }

    private double[] switchProbability(int numberOfCases, int bci) {
        double[] prob = (profilingInfo == null ? null : profilingInfo.getSwitchProbabilities(bci));
        if (prob != null) {
            assert prob.length == numberOfCases;
        } else {
            debug.log("Missing probability (switch) in %s at bci %d", method, bci);
            prob = new double[numberOfCases];
            for (int i = 0; i < numberOfCases; i++) {
                prob[i] = 1.0d / numberOfCases;
            }
        }
        assert allPositive(prob);
        return prob;
    }

    private static boolean allPositive(double[] a) {
        for (double d : a) {
            if (d < 0) {
                return false;
            }
        }
        return true;
    }

    static class SuccessorInfo {
        final int blockIndex;
        int actualIndex;

        SuccessorInfo(int blockSuccessorIndex) {
            this.blockIndex = blockSuccessorIndex;
            actualIndex = -1;
        }
    }

    private void genSwitch(BytecodeSwitch bs) {
        int bci = bci();
        ValueNode value = frameState.pop(JavaKind.Int);

        int nofCases = bs.numberOfCases();
        int nofCasesPlusDefault = nofCases + 1;
        double[] keyProbabilities = switchProbability(nofCasesPlusDefault, bci);

        EconomicMap<Integer, SuccessorInfo> bciToBlockSuccessorIndex = EconomicMap.create(Equivalence.DEFAULT);
        for (int i = 0; i < currentBlock.getSuccessorCount(); i++) {
            assert !bciToBlockSuccessorIndex.containsKey(currentBlock.getSuccessor(i).startBci);
            bciToBlockSuccessorIndex.put(currentBlock.getSuccessor(i).startBci, new SuccessorInfo(i));
        }

        ArrayList<BciBlock> actualSuccessors = new ArrayList<>();
        int[] keys = new int[nofCases];
        int[] keySuccessors = new int[nofCasesPlusDefault];
        int deoptSuccessorIndex = -1;
        int nextSuccessorIndex = 0;
        boolean constantValue = value.isConstant();
        for (int i = 0; i < nofCasesPlusDefault; i++) {
            if (i < nofCases) {
                keys[i] = bs.keyAt(i);
            }

            if (!constantValue && isNeverExecutedCode(keyProbabilities[i])) {
                if (deoptSuccessorIndex < 0) {
                    deoptSuccessorIndex = nextSuccessorIndex++;
                    actualSuccessors.add(null);
                }
                keySuccessors[i] = deoptSuccessorIndex;
            } else {
                int targetBci = i < nofCases ? bs.targetAt(i) : bs.defaultTarget();
                SuccessorInfo info = bciToBlockSuccessorIndex.get(targetBci);
                if (info.actualIndex < 0) {
                    info.actualIndex = nextSuccessorIndex++;
                    actualSuccessors.add(currentBlock.getSuccessor(info.blockIndex));
                }
                keySuccessors[i] = info.actualIndex;
            }
        }
        /*
         * When the profile indicates a case is never taken, the above code will cause the case to
         * deopt should it be subsequently encountered. However, the case may share code with
         * another case that is taken according to the profile.
         *
         * For example:
         * // @formatter:off
         * switch (opcode) {
         *     case GOTO:
         *     case GOTO_W: {
         *         // emit goto code
         *         break;
         *     }
         * }
         * // @formatter:on
         *
         * The profile may indicate the GOTO_W case is never taken, and thus a deoptimization stub
         * will be emitted. There might be optimization opportunity if additional branching based
         * on opcode is within the case block. Specially, if there is only single case that
         * reaches a target, we have better chance cutting out unused branches. Otherwise,
         * it might be beneficial routing to the same code instead of deopting.
         *
         * The following code rewires deoptimization stub to existing resolved branch target if
         * the target is connected by more than 1 cases.
         */
        if (deoptSuccessorIndex >= 0) {
            int[] connectedCases = new int[nextSuccessorIndex];
            for (int i = 0; i < nofCasesPlusDefault; i++) {
                connectedCases[keySuccessors[i]]++;
            }

            for (int i = 0; i < nofCasesPlusDefault; i++) {
                if (keySuccessors[i] == deoptSuccessorIndex) {
                    int targetBci = i < nofCases ? bs.targetAt(i) : bs.defaultTarget();
                    SuccessorInfo info = bciToBlockSuccessorIndex.get(targetBci);
                    int rewiredIndex = info.actualIndex;
                    if (rewiredIndex >= 0 && connectedCases[rewiredIndex] > 1) {
                        keySuccessors[i] = info.actualIndex;
                    }
                }
            }
        }

        genIntegerSwitch(value, actualSuccessors, keys, keyProbabilities, keySuccessors);

    }

    protected boolean isNeverExecutedCode(double probability) {
        return probability == 0 && optimisticOpts.removeNeverExecutedCode(getOptions());
    }

    private double clampProbability(double probability) {
        if (!optimisticOpts.removeNeverExecutedCode(getOptions())) {
            if (probability == 0) {
                return LUDICROUSLY_SLOW_PATH_PROBABILITY;
            } else if (probability == 1) {
                return LUDICROUSLY_FAST_PATH_PROBABILITY;
            }
        }
        return probability;
    }

    private boolean assertAtIfBytecode() {
        int bytecode = stream.currentBC();
        switch (bytecode) {
            case IFEQ:
            case IFNE:
            case IFLT:
            case IFGE:
            case IFGT:
            case IFLE:
            case IF_ICMPEQ:
            case IF_ICMPNE:
            case IF_ICMPLT:
            case IF_ICMPGE:
            case IF_ICMPGT:
            case IF_ICMPLE:
            case IF_ACMPEQ:
            case IF_ACMPNE:
            case IFNULL:
            case IFNONNULL:
                return true;
        }
        assert false : String.format("%x is not an if bytecode", bytecode);
        return true;
    }

    public final void processBytecode(int bci, int opcode) {
        int cpi;

        // @formatter:off
        // Checkstyle: stop
        switch (opcode) {
            case NOP            : /* nothing to do */ break;
            case ACONST_NULL    : frameState.push(JavaKind.Object, appendConstant(JavaConstant.NULL_POINTER)); break;
            case ICONST_M1      : // fall through
            case ICONST_0       : // fall through
            case ICONST_1       : // fall through
            case ICONST_2       : // fall through
            case ICONST_3       : // fall through
            case ICONST_4       : // fall through
            case ICONST_5       : frameState.push(JavaKind.Int, appendConstant(JavaConstant.forInt(opcode - ICONST_0))); break;
            case LCONST_0       : // fall through
            case LCONST_1       : frameState.push(JavaKind.Long, appendConstant(JavaConstant.forLong(opcode - LCONST_0))); break;
            case FCONST_0       : // fall through
            case FCONST_1       : // fall through
            case FCONST_2       : frameState.push(JavaKind.Float, appendConstant(JavaConstant.forFloat(opcode - FCONST_0))); break;
            case DCONST_0       : // fall through
            case DCONST_1       : frameState.push(JavaKind.Double, appendConstant(JavaConstant.forDouble(opcode - DCONST_0))); break;
            case BIPUSH         : frameState.push(JavaKind.Int, appendConstant(JavaConstant.forInt(stream.readByte()))); break;
            case SIPUSH         : frameState.push(JavaKind.Int, appendConstant(JavaConstant.forInt(stream.readShort()))); break;
            case LDC            : // fall through
            case LDC_W          : // fall through
            case LDC2_W         : genLoadConstant(stream.readCPI(), opcode); break;
            case ILOAD          : loadLocal(stream.readLocalIndex(), JavaKind.Int); break;
            case LLOAD          : loadLocal(stream.readLocalIndex(), JavaKind.Long); break;
            case FLOAD          : loadLocal(stream.readLocalIndex(), JavaKind.Float); break;
            case DLOAD          : loadLocal(stream.readLocalIndex(), JavaKind.Double); break;
            case ALOAD          : loadLocalObject(stream.readLocalIndex()); break;
            case ILOAD_0        : // fall through
            case ILOAD_1        : // fall through
            case ILOAD_2        : // fall through
            case ILOAD_3        : loadLocal(opcode - ILOAD_0, JavaKind.Int); break;
            case LLOAD_0        : // fall through
            case LLOAD_1        : // fall through
            case LLOAD_2        : // fall through
            case LLOAD_3        : loadLocal(opcode - LLOAD_0, JavaKind.Long); break;
            case FLOAD_0        : // fall through
            case FLOAD_1        : // fall through
            case FLOAD_2        : // fall through
            case FLOAD_3        : loadLocal(opcode - FLOAD_0, JavaKind.Float); break;
            case DLOAD_0        : // fall through
            case DLOAD_1        : // fall through
            case DLOAD_2        : // fall through
            case DLOAD_3        : loadLocal(opcode - DLOAD_0, JavaKind.Double); break;
            case ALOAD_0        : // fall through
            case ALOAD_1        : // fall through
            case ALOAD_2        : // fall through
            case ALOAD_3        : loadLocalObject(opcode - ALOAD_0); break;
            case IALOAD         : genLoadIndexed(JavaKind.Int   ); break;
            case LALOAD         : genLoadIndexed(JavaKind.Long  ); break;
            case FALOAD         : genLoadIndexed(JavaKind.Float ); break;
            case DALOAD         : genLoadIndexed(JavaKind.Double); break;
            case AALOAD         : genLoadIndexed(JavaKind.Object); break;
            case BALOAD         : genLoadIndexed(JavaKind.Byte  ); break;
            case CALOAD         : genLoadIndexed(JavaKind.Char  ); break;
            case SALOAD         : genLoadIndexed(JavaKind.Short ); break;
            case ISTORE         : storeLocal(JavaKind.Int, stream.readLocalIndex()); break;
            case LSTORE         : storeLocal(JavaKind.Long, stream.readLocalIndex()); break;
            case FSTORE         : storeLocal(JavaKind.Float, stream.readLocalIndex()); break;
            case DSTORE         : storeLocal(JavaKind.Double, stream.readLocalIndex()); break;
            case ASTORE         : storeLocal(JavaKind.Object, stream.readLocalIndex()); break;
            case ISTORE_0       : // fall through
            case ISTORE_1       : // fall through
            case ISTORE_2       : // fall through
            case ISTORE_3       : storeLocal(JavaKind.Int, opcode - ISTORE_0); break;
            case LSTORE_0       : // fall through
            case LSTORE_1       : // fall through
            case LSTORE_2       : // fall through
            case LSTORE_3       : storeLocal(JavaKind.Long, opcode - LSTORE_0); break;
            case FSTORE_0       : // fall through
            case FSTORE_1       : // fall through
            case FSTORE_2       : // fall through
            case FSTORE_3       : storeLocal(JavaKind.Float, opcode - FSTORE_0); break;
            case DSTORE_0       : // fall through
            case DSTORE_1       : // fall through
            case DSTORE_2       : // fall through
            case DSTORE_3       : storeLocal(JavaKind.Double, opcode - DSTORE_0); break;
            case ASTORE_0       : // fall through
            case ASTORE_1       : // fall through
            case ASTORE_2       : // fall through
            case ASTORE_3       : storeLocal(JavaKind.Object, opcode - ASTORE_0); break;
            case IASTORE        : genStoreIndexed(JavaKind.Int   ); break;
            case LASTORE        : genStoreIndexed(JavaKind.Long  ); break;
            case FASTORE        : genStoreIndexed(JavaKind.Float ); break;
            case DASTORE        : genStoreIndexed(JavaKind.Double); break;
            case AASTORE        : genStoreIndexed(JavaKind.Object); break;
            case BASTORE        : genStoreIndexed(JavaKind.Byte  ); break;
            case CASTORE        : genStoreIndexed(JavaKind.Char  ); break;
            case SASTORE        : genStoreIndexed(JavaKind.Short ); break;
            case POP            : // fall through
            case POP2           : // fall through
            case DUP            : // fall through
            case DUP_X1         : // fall through
            case DUP_X2         : // fall through
            case DUP2           : // fall through
            case DUP2_X1        : // fall through
            case DUP2_X2        : // fall through
            case SWAP           : frameState.stackOp(opcode); break;
            case IADD           : // fall through
            case ISUB           : // fall through
            case IMUL           : genArithmeticOp(JavaKind.Int, opcode); break;
            case IDIV           : // fall through
            case IREM           : genIntegerDivOp(JavaKind.Int, opcode); break;
            case LADD           : // fall through
            case LSUB           : // fall through
            case LMUL           : genArithmeticOp(JavaKind.Long, opcode); break;
            case LDIV           : // fall through
            case LREM           : genIntegerDivOp(JavaKind.Long, opcode); break;
            case FADD           : // fall through
            case FSUB           : // fall through
            case FMUL           : // fall through
            case FDIV           : // fall through
            case FREM           : genArithmeticOp(JavaKind.Float, opcode); break;
            case DADD           : // fall through
            case DSUB           : // fall through
            case DMUL           : // fall through
            case DDIV           : // fall through
            case DREM           : genArithmeticOp(JavaKind.Double, opcode); break;
            case INEG           : genNegateOp(JavaKind.Int); break;
            case LNEG           : genNegateOp(JavaKind.Long); break;
            case FNEG           : genNegateOp(JavaKind.Float); break;
            case DNEG           : genNegateOp(JavaKind.Double); break;
            case ISHL           : // fall through
            case ISHR           : // fall through
            case IUSHR          : genShiftOp(JavaKind.Int, opcode); break;
            case IAND           : // fall through
            case IOR            : // fall through
            case IXOR           : genLogicOp(JavaKind.Int, opcode); break;
            case LSHL           : // fall through
            case LSHR           : // fall through
            case LUSHR          : genShiftOp(JavaKind.Long, opcode); break;
            case LAND           : // fall through
            case LOR            : // fall through
            case LXOR           : genLogicOp(JavaKind.Long, opcode); break;
            case IINC           : genIncrement(); break;
            case I2F            : genFloatConvert(FloatConvert.I2F, JavaKind.Int, JavaKind.Float); break;
            case I2D            : genFloatConvert(FloatConvert.I2D, JavaKind.Int, JavaKind.Double); break;
            case L2F            : genFloatConvert(FloatConvert.L2F, JavaKind.Long, JavaKind.Float); break;
            case L2D            : genFloatConvert(FloatConvert.L2D, JavaKind.Long, JavaKind.Double); break;
            case F2I            : genFloatConvert(FloatConvert.F2I, JavaKind.Float, JavaKind.Int); break;
            case F2L            : genFloatConvert(FloatConvert.F2L, JavaKind.Float, JavaKind.Long); break;
            case F2D            : genFloatConvert(FloatConvert.F2D, JavaKind.Float, JavaKind.Double); break;
            case D2I            : genFloatConvert(FloatConvert.D2I, JavaKind.Double, JavaKind.Int); break;
            case D2L            : genFloatConvert(FloatConvert.D2L, JavaKind.Double, JavaKind.Long); break;
            case D2F            : genFloatConvert(FloatConvert.D2F, JavaKind.Double, JavaKind.Float); break;
            case L2I            : genNarrow(JavaKind.Long, JavaKind.Int); break;
            case I2L            : genSignExtend(JavaKind.Int, JavaKind.Long); break;
            case I2B            : genSignExtend(JavaKind.Byte, JavaKind.Int); break;
            case I2S            : genSignExtend(JavaKind.Short, JavaKind.Int); break;
            case I2C            : genZeroExtend(JavaKind.Char, JavaKind.Int); break;
            case LCMP           : genCompareOp(JavaKind.Long, false); break;
            case FCMPL          : genCompareOp(JavaKind.Float, true); break;
            case FCMPG          : genCompareOp(JavaKind.Float, false); break;
            case DCMPL          : genCompareOp(JavaKind.Double, true); break;
            case DCMPG          : genCompareOp(JavaKind.Double, false); break;
            case IFEQ           : genIfZero(Condition.EQ); break;
            case IFNE           : genIfZero(Condition.NE); break;
            case IFLT           : genIfZero(Condition.LT); break;
            case IFGE           : genIfZero(Condition.GE); break;
            case IFGT           : genIfZero(Condition.GT); break;
            case IFLE           : genIfZero(Condition.LE); break;
            case IF_ICMPEQ      : genIfSame(JavaKind.Int, Condition.EQ); break;
            case IF_ICMPNE      : genIfSame(JavaKind.Int, Condition.NE); break;
            case IF_ICMPLT      : genIfSame(JavaKind.Int, Condition.LT); break;
            case IF_ICMPGE      : genIfSame(JavaKind.Int, Condition.GE); break;
            case IF_ICMPGT      : genIfSame(JavaKind.Int, Condition.GT); break;
            case IF_ICMPLE      : genIfSame(JavaKind.Int, Condition.LE); break;
            case IF_ACMPEQ      : genIfSame(JavaKind.Object, Condition.EQ); break;
            case IF_ACMPNE      : genIfSame(JavaKind.Object, Condition.NE); break;
            case GOTO           : genGoto(); break;
            case JSR            : genJsr(stream.readBranchDest()); break;
            case RET            : genRet(stream.readLocalIndex()); break;
            case TABLESWITCH    : genSwitch(new BytecodeTableSwitch(getStream(), bci())); break;
            case LOOKUPSWITCH   : genSwitch(new BytecodeLookupSwitch(getStream(), bci())); break;
            case IRETURN        : genReturn(frameState.pop(JavaKind.Int), JavaKind.Int); break;
            case LRETURN        : genReturn(frameState.pop(JavaKind.Long), JavaKind.Long); break;
            case FRETURN        : genReturn(frameState.pop(JavaKind.Float), JavaKind.Float); break;
            case DRETURN        : genReturn(frameState.pop(JavaKind.Double), JavaKind.Double); break;
            case ARETURN        : genReturn(frameState.pop(JavaKind.Object), JavaKind.Object); break;
            case RETURN         : genReturn(null, JavaKind.Void); break;
            case GETSTATIC      : cpi = stream.readCPI(); genGetStatic(cpi, opcode); break;
            case PUTSTATIC      : cpi = stream.readCPI(); genPutStatic(cpi, opcode); break;
            case GETFIELD       : cpi = stream.readCPI(); genGetField(cpi, opcode); break;
            case PUTFIELD       : cpi = stream.readCPI(); genPutField(cpi, opcode); break;
            case INVOKEVIRTUAL  : cpi = stream.readCPI(); genInvokeVirtual(cpi, opcode); break;
            case INVOKESPECIAL  : cpi = stream.readCPI(); genInvokeSpecial(cpi, opcode); break;
            case INVOKESTATIC   : cpi = stream.readCPI(); genInvokeStatic(cpi, opcode); break;
            case INVOKEINTERFACE: cpi = stream.readCPI(); genInvokeInterface(cpi, opcode); break;
            case INVOKEDYNAMIC  : cpi = stream.readCPI4(); genInvokeDynamic(cpi, opcode); break;
            case NEW            : genNewInstance(stream.readCPI()); break;
            case NEWARRAY       : genNewPrimitiveArray(stream.readLocalIndex()); break;
            case ANEWARRAY      : genNewObjectArray(stream.readCPI()); break;
            case ARRAYLENGTH    : genArrayLength(); break;
            case ATHROW         : genThrow(); break;
            case CHECKCAST      : genCheckCast(stream.readCPI()); break;
            case INSTANCEOF     : genInstanceOf(stream.readCPI()); break;
            case MONITORENTER   : genMonitorEnter(frameState.pop(JavaKind.Object), stream.nextBCI()); break;
            case MONITOREXIT    : genMonitorExit(frameState.pop(JavaKind.Object), null, stream.nextBCI()); break;
            case MULTIANEWARRAY : genNewMultiArray(stream.readCPI()); break;
            case IFNULL         : genIfNull(Condition.EQ); break;
            case IFNONNULL      : genIfNull(Condition.NE); break;
            case GOTO_W         : genGoto(); break;
            case JSR_W          : genJsr(stream.readBranchDest()); break;
            case BREAKPOINT     : throw new PermanentBailoutException("concurrent setting of breakpoint");
            default             : throw new PermanentBailoutException("Unsupported opcode %d (%s) [bci=%d]", opcode, nameOf(opcode), bci);
        }
        // @formatter:on
        // Checkstyle: resume
    }

    private void genArrayLength() {
        ValueNode array = maybeEmitExplicitNullCheck(frameState.pop(JavaKind.Object));
        frameState.push(JavaKind.Int, append(genArrayLength(array)));
    }

    @Override
    public ResolvedJavaMethod getMethod() {
        return method;
    }

    @Override
    public Bytecode getCode() {
        return code;
    }

    public FrameStateBuilder getFrameStateBuilder() {
        return frameState;
    }

    private boolean firstTraceEmitted;

    protected void traceInstruction(int bci, int opcode, boolean blockStart) {
        String indent = new String(new char[getDepth() * 2]).replace('\0', ' ');
        StringBuilder sb = new StringBuilder(40);
        String nl = System.lineSeparator();
        if (!firstTraceEmitted) {
            sb.append(indent).append(method.format("Parsing %H.%n(%p)%r")).append(nl);
            if (traceLevel >= TRACELEVEL_BLOCKMAP) {
                sb.append(indent).append("Blocks:").append(nl);
                String bm = blockMap.toString().replace(nl, nl + indent + "  ");
                sb.append(indent).append("  ").append(bm).append(nl);
            }
            firstTraceEmitted = true;
        }
        if (traceLevel >= TRACELEVEL_STATE) {
            sb.append(indent).append(frameState).append(nl);
        }
        sb.append(indent);
        sb.append(blockStart ? '+' : '|');
        if (bci < 10) {
            sb.append("  ");
        } else if (bci < 100) {
            sb.append(' ');
        }
        sb.append(bci).append(": ").append(Bytecodes.nameOf(opcode));
        for (int i = bci + 1; i < stream.nextBCI(); ++i) {
            sb.append(' ').append(stream.readUByte(i));
        }
        if (!currentBlock.getJsrScope().isEmpty()) {
            sb.append(' ').append(currentBlock.getJsrScope());
        }
        TTY.println("%s", sb);
    }

    @Override
    public boolean parsingIntrinsic() {
        return intrinsicContext != null;
    }

    @Override
    public BytecodeParser getNonIntrinsicAncestor() {
        BytecodeParser ancestor = parent;
        while (ancestor != null && ancestor.parsingIntrinsic()) {
            ancestor = ancestor.parent;
        }
        return ancestor;
    }

    static String nSpaces(int n) {
        return n == 0 ? "" : format("%" + n + "s", "");
    }
}
