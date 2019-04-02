/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.core.test;

import static org.graalvm.compiler.debug.DebugContext.BASIC_LEVEL;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Graph;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.java.NewArrayNode;
import org.graalvm.compiler.nodes.java.StoreIndexedNode;
import org.graalvm.compiler.phases.VerifyPhase;
import org.graalvm.compiler.phases.tiers.PhaseContext;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Verifies that call sites calling one of the methods in {@link DebugContext} use them correctly.
 * Correct usage of the methods in {@link DebugContext} requires call sites to not eagerly evaluate
 * their arguments. Additionally this phase verifies that no argument is the result of a call to
 * {@link StringBuilder#toString()} or {@link StringBuffer#toString()}. Ideally the parameters at
 * call sites of {@link DebugContext} are eliminated, and do not produce additional allocations, if
 * {@link DebugContext#isDumpEnabled(int)} (or {@link DebugContext#isLogEnabled(int)}, ...) is
 * {@code false}.
 *
 * Methods in {@link DebugContext} checked by this phase are various different versions of
 * {@link DebugContext#log(String)} , {@link DebugContext#dump(int, Object, String)},
 * {@link DebugContext#logAndIndent(String)} and {@link DebugContext#verify(Object, String)}.
 */
public class VerifyDebugUsage extends VerifyPhase<PhaseContext> {

    @Override
    public boolean checkContract() {
        return false;
    }

    MetaAccessProvider metaAccess;

    @Override
    protected void verify(StructuredGraph graph, PhaseContext context) {
        metaAccess = context.getMetaAccess();
        ResolvedJavaType debugType = metaAccess.lookupJavaType(DebugContext.class);
        ResolvedJavaType nodeType = metaAccess.lookupJavaType(Node.class);
        ResolvedJavaType stringType = metaAccess.lookupJavaType(String.class);
        ResolvedJavaType graalErrorType = metaAccess.lookupJavaType(GraalError.class);

        for (MethodCallTargetNode t : graph.getNodes(MethodCallTargetNode.TYPE)) {
            ResolvedJavaMethod callee = t.targetMethod();
            String calleeName = callee.getName();
            if (callee.getDeclaringClass().equals(debugType)) {
                boolean isDump = calleeName.equals("dump");
                if (calleeName.equals("log") || calleeName.equals("logAndIndent") || calleeName.equals("verify") || isDump) {
                    verifyParameters(t, graph, t.arguments(), stringType, isDump ? 2 : 1);
                }
            }
            if (callee.getDeclaringClass().isAssignableFrom(nodeType)) {
                if (calleeName.equals("assertTrue") || calleeName.equals("assertFalse")) {
                    verifyParameters(t, graph, t.arguments(), stringType, 1);
                }
            }
            if (callee.getDeclaringClass().isAssignableFrom(graalErrorType) && !graph.method().getDeclaringClass().isAssignableFrom(graalErrorType)) {
                if (calleeName.equals("guarantee")) {
                    verifyParameters(t, graph, t.arguments(), stringType, 0);
                }
                if (calleeName.equals("<init>") && callee.getSignature().getParameterCount(false) == 2) {
                    verifyParameters(t, graph, t.arguments(), stringType, 1);
                }
            }
        }
    }

    private void verifyParameters(MethodCallTargetNode callTarget, StructuredGraph callerGraph, NodeInputList<? extends ValueNode> args, ResolvedJavaType stringType, int startArgIdx) {
        if (callTarget.targetMethod().isVarArgs() && args.get(args.count() - 1) instanceof NewArrayNode) {
            // unpack the arguments to the var args
            List<ValueNode> unpacked = new ArrayList<>(args.snapshot());
            NewArrayNode varArgParameter = (NewArrayNode) unpacked.remove(unpacked.size() - 1);
            int firstVarArg = unpacked.size();
            for (Node usage : varArgParameter.usages()) {
                if (usage instanceof StoreIndexedNode) {
                    StoreIndexedNode si = (StoreIndexedNode) usage;
                    unpacked.add(si.value());
                }
            }
            verifyParameters(callerGraph, callTarget, unpacked, stringType, startArgIdx, firstVarArg);
        } else {
            verifyParameters(callerGraph, callTarget, args, stringType, startArgIdx, -1);
        }
    }

    private static final Set<Integer> DebugLevels = new HashSet<>(
                    Arrays.asList(DebugContext.ENABLED_LEVEL, BASIC_LEVEL, DebugContext.INFO_LEVEL, DebugContext.VERBOSE_LEVEL, DebugContext.DETAILED_LEVEL, DebugContext.VERY_DETAILED_LEVEL));

    /**
     * The set of methods allowed to call a {@code Debug.dump(...)} method with the {@code level}
     * parameter bound to {@link DebugContext#BASIC_LEVEL} and the {@code object} parameter bound to
     * a {@link StructuredGraph} value.
     *
     * This whitelist exists to ensure any increase in graph dumps is in line with the policy
     * outlined by {@link DebugContext#BASIC_LEVEL}. If you add a *justified* graph dump at this
     * level, then update the whitelist.
     */
    private static final Set<String> BasicLevelStructuredGraphDumpWhitelist = new HashSet<>(Arrays.asList(
                    "org.graalvm.compiler.phases.BasePhase.dumpAfter",
                    "org.graalvm.compiler.phases.BasePhase.dumpBefore",
                    "org.graalvm.compiler.core.GraalCompiler.emitFrontEnd",
                    "org.graalvm.compiler.truffle.compiler.PartialEvaluator.fastPartialEvaluation",
                    "org.graalvm.compiler.truffle.compiler.PartialEvaluator$PerformanceInformationHandler.reportPerformanceWarnings",
                    "org.graalvm.compiler.truffle.compiler.TruffleCompilerImpl.compilePEGraph",
                    "org.graalvm.compiler.core.test.VerifyDebugUsageTest$ValidDumpUsagePhase.run",
                    "org.graalvm.compiler.core.test.VerifyDebugUsageTest$InvalidConcatDumpUsagePhase.run",
                    "org.graalvm.compiler.core.test.VerifyDebugUsageTest$InvalidDumpUsagePhase.run",
                    "org.graalvm.compiler.hotspot.SymbolicSnippetEncoder.verifySnippetEncodeDecode"));

    /**
     * The set of methods allowed to call a {@code Debug.dump(...)} method with the {@code level}
     * parameter bound to {@link DebugContext#INFO_LEVEL} and the {@code object} parameter bound to
     * a {@link StructuredGraph} value.
     *
     * This whitelist exists to ensure any increase in graph dumps is in line with the policy
     * outlined by {@link DebugContext#INFO_LEVEL}. If you add a *justified* graph dump at this
     * level, then update the whitelist.
     */
    private static final Set<String> InfoLevelStructuredGraphDumpWhitelist = new HashSet<>(Arrays.asList(
                    "org.graalvm.compiler.core.GraalCompiler.emitFrontEnd",
                    "org.graalvm.compiler.phases.BasePhase.dumpAfter",
                    "org.graalvm.compiler.replacements.ReplacementsImpl$GraphMaker.makeGraph",
                    "org.graalvm.compiler.replacements.SnippetTemplate.instantiate",
                    "org.graalvm.compiler.replacements.SnippetTemplate.<init>",
                    "org.graalvm.compiler.hotspot.SymbolicSnippetEncoder.verifySnippetEncodeDecode"));

    private void verifyParameters(StructuredGraph callerGraph, MethodCallTargetNode debugCallTarget, List<? extends ValueNode> args, ResolvedJavaType stringType, int startArgIdx,
                    int varArgsIndex) {
        ResolvedJavaMethod verifiedCallee = debugCallTarget.targetMethod();
        Integer dumpLevel = null;
        int argIdx = startArgIdx;
        int varArgsElementIndex = 0;
        boolean reportVarArgs = false;
        for (int i = 0; i < args.size(); i++) {
            ValueNode arg = args.get(i);
            if (arg instanceof Invoke) {
                reportVarArgs = varArgsIndex >= 0 && argIdx >= varArgsIndex;
                Invoke invoke = (Invoke) arg;
                CallTargetNode callTarget = invoke.callTarget();
                if (callTarget instanceof MethodCallTargetNode) {
                    ResolvedJavaMethod m = ((MethodCallTargetNode) callTarget).targetMethod();
                    if (m.getName().equals("toString")) {
                        int bci = invoke.bci();
                        int nonVarArgIdx = reportVarArgs ? argIdx - varArgsElementIndex : argIdx;
                        verifyStringConcat(callerGraph, verifiedCallee, bci, nonVarArgIdx, reportVarArgs ? varArgsElementIndex : -1, m);
                        verifyToStringCall(callerGraph, verifiedCallee, stringType, m, bci, nonVarArgIdx, reportVarArgs ? varArgsElementIndex : -1);
                    } else if (m.getName().equals("format")) {
                        int bci = invoke.bci();
                        int nonVarArgIdx = reportVarArgs ? argIdx - varArgsElementIndex : argIdx;
                        verifyFormatCall(callerGraph, verifiedCallee, stringType, m, bci, nonVarArgIdx, reportVarArgs ? varArgsElementIndex : -1);

                    }
                }
            }
            if (i == 1) {
                if (verifiedCallee.getName().equals("dump")) {
                    dumpLevel = verifyDumpLevelParameter(callerGraph, debugCallTarget, verifiedCallee, arg);
                }
            } else if (i == 2) {
                if (dumpLevel != null) {
                    verifyDumpObjectParameter(callerGraph, debugCallTarget, arg, verifiedCallee, dumpLevel);
                }
            }
            if (varArgsIndex >= 0 && i >= varArgsIndex) {
                varArgsElementIndex++;
            }
            argIdx++;
        }
    }

    /**
     * The {@code level} arg for the {@code Debug.dump(...)} methods must be a reference to one of
     * the {@code Debug.*_LEVEL} constants.
     */
    protected Integer verifyDumpLevelParameter(StructuredGraph callerGraph, MethodCallTargetNode debugCallTarget, ResolvedJavaMethod verifiedCallee, ValueNode arg)
                    throws org.graalvm.compiler.phases.VerifyPhase.VerificationError {
        // The 'level' arg for the Debug.dump(...) methods must be a reference to one of
        // the Debug.*_LEVEL constants.

        Constant c = arg.asConstant();
        if (c != null) {
            Integer dumpLevel = ((PrimitiveConstant) c).asInt();
            if (!DebugLevels.contains(dumpLevel)) {
                StackTraceElement e = callerGraph.method().asStackTraceElement(debugCallTarget.invoke().bci());
                throw new VerificationError(
                                "In %s: parameter 0 of call to %s does not match a Debug.*_LEVEL constant: %s.%n", e, verifiedCallee.format("%H.%n(%p)"), dumpLevel);
            }
            return dumpLevel;
        }
        StackTraceElement e = callerGraph.method().asStackTraceElement(debugCallTarget.invoke().bci());
        throw new VerificationError(
                        "In %s: parameter 0 of call to %s must be a constant, not %s.%n", e, verifiedCallee.format("%H.%n(%p)"), arg);
    }

    protected void verifyDumpObjectParameter(StructuredGraph callerGraph, MethodCallTargetNode debugCallTarget, ValueNode arg, ResolvedJavaMethod verifiedCallee, Integer dumpLevel)
                    throws org.graalvm.compiler.phases.VerifyPhase.VerificationError {
        ResolvedJavaType argType = ((ObjectStamp) arg.stamp(NodeView.DEFAULT)).type();
        if (metaAccess.lookupJavaType(Graph.class).isAssignableFrom(argType)) {
            verifyStructuredGraphDumping(callerGraph, debugCallTarget, verifiedCallee, dumpLevel);
        }
    }

    /**
     * Verifies that dumping a {@link StructuredGraph} at level {@link DebugContext#BASIC_LEVEL} or
     * {@link DebugContext#INFO_LEVEL} only occurs in white-listed methods.
     */
    protected void verifyStructuredGraphDumping(StructuredGraph callerGraph, MethodCallTargetNode debugCallTarget, ResolvedJavaMethod verifiedCallee, Integer dumpLevel)
                    throws org.graalvm.compiler.phases.VerifyPhase.VerificationError {
        if (dumpLevel == DebugContext.BASIC_LEVEL) {
            StackTraceElement e = callerGraph.method().asStackTraceElement(debugCallTarget.invoke().bci());
            String qualifiedMethod = e.getClassName() + "." + e.getMethodName();
            if (!BasicLevelStructuredGraphDumpWhitelist.contains(qualifiedMethod)) {
                throw new VerificationError(
                                "In %s: call to %s with level == DebugContext.BASIC_LEVEL not in %s.BasicLevelDumpWhitelist.%n", e, verifiedCallee.format("%H.%n(%p)"),
                                getClass().getName());
            }
        } else if (dumpLevel == DebugContext.INFO_LEVEL) {
            StackTraceElement e = callerGraph.method().asStackTraceElement(debugCallTarget.invoke().bci());
            String qualifiedMethod = e.getClassName() + "." + e.getMethodName();
            if (!InfoLevelStructuredGraphDumpWhitelist.contains(qualifiedMethod)) {
                throw new VerificationError(
                                "In %s: call to %s with level == Debug.INFO_LEVEL not in %s.InfoLevelDumpWhitelist.%n", e, verifiedCallee.format("%H.%n(%p)"),
                                getClass().getName());
            }
        }
    }

    /**
     * Checks that a given call is not to {@link StringBuffer#toString()} or
     * {@link StringBuilder#toString()}.
     */
    private static void verifyStringConcat(StructuredGraph callerGraph, ResolvedJavaMethod verifiedCallee, int bci, int argIdx, int varArgsElementIndex, ResolvedJavaMethod callee) {
        if (callee.getDeclaringClass().getName().equals("Ljava/lang/StringBuilder;") || callee.getDeclaringClass().getName().equals("Ljava/lang/StringBuffer;")) {
            StackTraceElement e = callerGraph.method().asStackTraceElement(bci);
            if (varArgsElementIndex >= 0) {
                throw new VerificationError(
                                "In %s: element %d of parameter %d of call to %s appears to be a String concatenation expression.%n", e, varArgsElementIndex, argIdx,
                                verifiedCallee.format("%H.%n(%p)"));
            } else {
                throw new VerificationError(
                                "In %s: parameter %d of call to %s appears to be a String concatenation expression.", e, argIdx, verifiedCallee.format("%H.%n(%p)"));
            }
        }
    }

    /**
     * Checks that a given call is not to {@link Object#toString()}.
     */
    private static void verifyToStringCall(StructuredGraph callerGraph, ResolvedJavaMethod verifiedCallee, ResolvedJavaType stringType, ResolvedJavaMethod callee, int bci, int argIdx,
                    int varArgsElementIndex) {
        if (callee.getSignature().getParameterCount(false) == 0 && callee.getSignature().getReturnType(callee.getDeclaringClass()).equals(stringType)) {
            StackTraceElement e = callerGraph.method().asStackTraceElement(bci);
            if (varArgsElementIndex >= 0) {
                throw new VerificationError(
                                "In %s: element %d of parameter %d of call to %s is a call to toString() which is redundant (the callee will do it) and forces unnecessary eager evaluation.",
                                e, varArgsElementIndex, argIdx, verifiedCallee.format("%H.%n(%p)"));
            } else {
                throw new VerificationError("In %s: parameter %d of call to %s is a call to toString() which is redundant (the callee will do it) and forces unnecessary eager evaluation.", e, argIdx,
                                verifiedCallee.format("%H.%n(%p)"));
            }
        }
    }

    /**
     * Checks that a given call is not to {@link String#format(String, Object...)} or
     * {@link String#format(java.util.Locale, String, Object...)}.
     */
    private static void verifyFormatCall(StructuredGraph callerGraph, ResolvedJavaMethod verifiedCallee, ResolvedJavaType stringType, ResolvedJavaMethod callee, int bci, int argIdx,
                    int varArgsElementIndex) {
        if (callee.getDeclaringClass().equals(stringType) && callee.getSignature().getReturnType(callee.getDeclaringClass()).equals(stringType)) {
            StackTraceElement e = callerGraph.method().asStackTraceElement(bci);
            if (varArgsElementIndex >= 0) {
                throw new VerificationError(
                                "In %s: element %d of parameter %d of call to %s is a call to String.format() which is redundant (%s does formatting) and forces unnecessary eager evaluation.",
                                e, varArgsElementIndex, argIdx, verifiedCallee.format("%H.%n(%p)"), verifiedCallee.format("%h.%n"));
            } else {
                throw new VerificationError("In %s: parameter %d of call to %s is a call to String.format() which is redundant (%s does formatting) and forces unnecessary eager evaluation.", e,
                                argIdx,
                                verifiedCallee.format("%H.%n(%p)"), verifiedCallee.format("%h.%n"));
            }
        }
    }
}
