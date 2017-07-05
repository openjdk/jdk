/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.phases.verify;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.debug.DebugMethodMetrics;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.java.NewArrayNode;
import org.graalvm.compiler.nodes.java.StoreIndexedNode;
import org.graalvm.compiler.phases.VerifyPhase;
import org.graalvm.compiler.phases.tiers.PhaseContext;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 *
 * Verifies that call sites calling one of the methods in {@link Debug} use them correctly. Correct
 * usage of the methods in {@link Debug} requires call sites to not eagerly evaluate their
 * arguments. Additionally this phase verifies that no argument is the result of a call to
 * {@link StringBuilder#toString()} or {@link StringBuffer#toString()}. Ideally the parameters at
 * call sites of {@link Debug} are eliminated, and do not produce additional allocations, if
 * {@link Debug#isDumpEnabled(int)} (or {@link Debug#isLogEnabled(int)}, ...) is {@code false}.
 *
 * Methods in {@link Debug} checked by this phase are various different versions of
 * {@link Debug#log(String)} , {@link Debug#dump(int, Object, String)},
 * {@link Debug#logAndIndent(String)} and {@link Debug#verify(Object, String)}.
 */
public class VerifyDebugUsage extends VerifyPhase<PhaseContext> {

    @Override
    public boolean checkContract() {
        return false;
    }

    @Override
    protected boolean verify(StructuredGraph graph, PhaseContext context) {
        ResolvedJavaType debugType = context.getMetaAccess().lookupJavaType(Debug.class);
        ResolvedJavaType nodeType = context.getMetaAccess().lookupJavaType(Node.class);
        ResolvedJavaType stringType = context.getMetaAccess().lookupJavaType(String.class);
        ResolvedJavaType debugMethodMetricsType = context.getMetaAccess().lookupJavaType(DebugMethodMetrics.class);
        ResolvedJavaType graalErrorType = context.getMetaAccess().lookupJavaType(GraalError.class);

        for (MethodCallTargetNode t : graph.getNodes(MethodCallTargetNode.TYPE)) {
            ResolvedJavaMethod callee = t.targetMethod();
            String calleeName = callee.getName();
            if (callee.getDeclaringClass().equals(debugType)) {
                if (calleeName.equals("log") || calleeName.equals("logAndIndent") || calleeName.equals("verify") || calleeName.equals("dump")) {
                    verifyParameters(t, graph, t.arguments(), stringType, calleeName.equals("dump") ? 2 : 1);
                }
            }
            if (callee.getDeclaringClass().isAssignableFrom(nodeType)) {
                if (calleeName.equals("assertTrue") || calleeName.equals("assertFalse")) {
                    verifyParameters(t, graph, t.arguments(), stringType, 1);
                }
            }
            if (callee.getDeclaringClass().equals(debugMethodMetricsType)) {
                if (calleeName.equals("addToMetric") || calleeName.equals("getCurrentMetricValue") || calleeName.equals("incrementMetric")) {
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
        return true;
    }

    private static void verifyParameters(MethodCallTargetNode callTarget, StructuredGraph callerGraph, NodeInputList<? extends Node> args, ResolvedJavaType stringType, int startArgIdx) {
        if (callTarget.targetMethod().isVarArgs() && args.get(args.count() - 1) instanceof NewArrayNode) {
            // unpack the arguments to the var args
            List<Node> unpacked = new ArrayList<>(args.snapshot());
            NewArrayNode varArgParameter = (NewArrayNode) unpacked.remove(unpacked.size() - 1);
            int firstVarArg = unpacked.size();
            for (Node usage : varArgParameter.usages()) {
                if (usage instanceof StoreIndexedNode) {
                    StoreIndexedNode si = (StoreIndexedNode) usage;
                    unpacked.add(si.value());
                }
            }
            verifyParameters(callerGraph, callTarget.targetMethod(), unpacked, stringType, startArgIdx, firstVarArg);
        } else {
            verifyParameters(callerGraph, callTarget.targetMethod(), args, stringType, startArgIdx, -1);
        }
    }

    private static void verifyParameters(StructuredGraph callerGraph, ResolvedJavaMethod verifiedCallee, List<? extends Node> args, ResolvedJavaType stringType, int startArgIdx, int varArgsIndex) {
        int argIdx = startArgIdx;
        int varArgsElementIndex = 0;
        boolean reportVarArgs = false;
        for (int i = 0; i < args.size(); i++) {
            Node arg = args.get(i);
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
            if (varArgsIndex >= 0 && i >= varArgsIndex) {
                varArgsElementIndex++;
            }
            argIdx++;
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
                                "In %s: parameter %d of call to %s appears to be a String concatenation expression.%n", e, argIdx, verifiedCallee.format("%H.%n(%p)"));
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
