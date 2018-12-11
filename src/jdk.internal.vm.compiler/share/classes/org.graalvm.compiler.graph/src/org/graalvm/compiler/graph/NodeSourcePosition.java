/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.graph;

import static org.graalvm.compiler.graph.NodeSourcePosition.Marker.None;
import static org.graalvm.compiler.graph.NodeSourcePosition.Marker.Placeholder;
import static org.graalvm.compiler.graph.NodeSourcePosition.Marker.Substitution;

import java.util.Iterator;
import java.util.Objects;

import org.graalvm.compiler.bytecode.BytecodeDisassembler;
import org.graalvm.compiler.bytecode.Bytecodes;

import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.MetaUtil;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class NodeSourcePosition extends BytecodePosition implements Iterable<NodeSourcePosition> {

    private static final boolean STRICT_SOURCE_POSITION = Boolean.getBoolean("debug.graal.SourcePositionStrictChecks");
    private static final boolean SOURCE_POSITION_BYTECODES = Boolean.getBoolean("debug.graal.SourcePositionDisassemble");

    private final int hashCode;
    private final Marker marker;
    private final SourceLanguagePosition sourceLanguagePosition;

    /**
     * Remove marker frames.
     */
    public NodeSourcePosition trim() {
        NodeSourcePosition lastMarker = null;
        for (NodeSourcePosition current = this; current != null; current = current.getCaller()) {
            if (current.marker != None) {
                lastMarker = current;
            }
        }
        if (lastMarker == null) {
            return this;
        }
        return lastMarker.getCaller();
    }

    public ResolvedJavaMethod getRootMethod() {
        NodeSourcePosition cur = this;
        while (cur.getCaller() != null) {
            cur = cur.getCaller();
        }
        return cur.getMethod();
    }

    public boolean verifyRootMethod(ResolvedJavaMethod root) {
        JavaMethod currentRoot = getRootMethod();
        assert root.equals(currentRoot) || root.getName().equals(currentRoot.getName()) && root.getSignature().toMethodDescriptor().equals(currentRoot.getSignature().toMethodDescriptor()) &&
                        root.getDeclaringClass().getName().equals(currentRoot.getDeclaringClass().getName()) : root + " " + currentRoot;
        return true;
    }

    @Override
    public Iterator<NodeSourcePosition> iterator() {
        return new Iterator<NodeSourcePosition>() {
            private NodeSourcePosition currentPosition = NodeSourcePosition.this;

            @Override
            public boolean hasNext() {
                return currentPosition != null;
            }

            @Override
            public NodeSourcePosition next() {
                NodeSourcePosition current = currentPosition;
                currentPosition = currentPosition.getCaller();
                return current;
            }
        };
    }

    enum Marker {
        None,
        Placeholder,
        Substitution
    }

    public NodeSourcePosition(NodeSourcePosition caller, ResolvedJavaMethod method, int bci) {
        this(caller, method, bci, None);
    }

    public NodeSourcePosition(NodeSourcePosition caller, ResolvedJavaMethod method, int bci, Marker marker) {
        this(null, caller, method, bci, marker);

    }

    public NodeSourcePosition(SourceLanguagePosition sourceLanguagePosition, NodeSourcePosition caller, ResolvedJavaMethod method, int bci) {
        this(sourceLanguagePosition, caller, method, bci, None);
    }

    public NodeSourcePosition(SourceLanguagePosition sourceLanguagePosition, NodeSourcePosition caller, ResolvedJavaMethod method, int bci, Marker marker) {
        super(caller, method, bci);
        if (caller == null) {
            this.hashCode = 31 * bci + method.hashCode();
        } else {
            this.hashCode = caller.hashCode * 7 + 31 * bci + method.hashCode();
        }
        this.marker = marker;
        this.sourceLanguagePosition = sourceLanguagePosition;
    }

    public static NodeSourcePosition placeholder(ResolvedJavaMethod method) {
        return new NodeSourcePosition(null, method, BytecodeFrame.INVALID_FRAMESTATE_BCI, Placeholder);
    }

    public static NodeSourcePosition placeholder(ResolvedJavaMethod method, int bci) {
        return new NodeSourcePosition(null, method, bci, Placeholder);
    }

    public boolean isPlaceholder() {
        return marker == Placeholder;
    }

    public static NodeSourcePosition substitution(ResolvedJavaMethod method) {
        return substitution(null, method, BytecodeFrame.INVALID_FRAMESTATE_BCI);
    }

    public static NodeSourcePosition substitution(ResolvedJavaMethod method, int bci) {
        return substitution(null, method, bci);
    }

    public static NodeSourcePosition substitution(NodeSourcePosition caller, ResolvedJavaMethod method) {
        return substitution(caller, method, BytecodeFrame.INVALID_FRAMESTATE_BCI);
    }

    public static NodeSourcePosition substitution(NodeSourcePosition caller, ResolvedJavaMethod method, int bci) {
        return new NodeSourcePosition(caller, method, bci, Substitution);
    }

    public boolean isSubstitution() {
        return marker == Substitution;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj != null && getClass() == obj.getClass()) {
            NodeSourcePosition that = (NodeSourcePosition) obj;
            if (hashCode != that.hashCode) {
                return false;
            }
            if (this.getBCI() == that.getBCI() && Objects.equals(this.getMethod(), that.getMethod()) && Objects.equals(this.getCaller(), that.getCaller()) &&
                            Objects.equals(this.sourceLanguagePosition, that.sourceLanguagePosition)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    public int depth() {
        int d = 0;
        NodeSourcePosition pos = this;
        while (pos != null) {
            d++;
            pos = pos.getCaller();
        }
        return d;
    }

    public SourceLanguagePosition getSourceLanguage() {
        return sourceLanguagePosition;
    }

    @Override
    public NodeSourcePosition getCaller() {
        return (NodeSourcePosition) super.getCaller();
    }

    public NodeSourcePosition addCaller(SourceLanguagePosition newSourceLanguagePosition, NodeSourcePosition link) {
        return addCaller(newSourceLanguagePosition, link, false);
    }

    public NodeSourcePosition addCaller(NodeSourcePosition link) {
        return addCaller(null, link, false);
    }

    public NodeSourcePosition addCaller(NodeSourcePosition link, boolean isSubstitution) {
        return addCaller(null, link, isSubstitution);
    }

    public NodeSourcePosition addCaller(SourceLanguagePosition newSourceLanguagePosition, NodeSourcePosition link, boolean isSubstitution) {
        if (getCaller() == null) {
            if (isPlaceholder()) {
                return new NodeSourcePosition(newSourceLanguagePosition, link, getMethod(), 0);
            }
            assert link == null || isSubstitution || verifyCaller(this, link) : link;
            assert !isSubstitution || marker == None;
            return new NodeSourcePosition(newSourceLanguagePosition, link, getMethod(), getBCI(), isSubstitution ? Substitution : None);
        } else {
            return new NodeSourcePosition(getCaller().addCaller(newSourceLanguagePosition, link, isSubstitution), getMethod(), getBCI(), marker);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(100);
        NodeSourcePosition pos = this;
        while (pos != null) {
            format(sb, pos);
            if (pos.sourceLanguagePosition != null) {
                sb.append(" source=" + pos.sourceLanguagePosition.toShortString());
            }
            pos = pos.getCaller();
            if (pos != null) {
                sb.append(CodeUtil.NEW_LINE);
            }
        }
        return sb.toString();
    }

    private static void format(StringBuilder sb, NodeSourcePosition pos) {
        MetaUtil.appendLocation(sb.append("at "), pos.getMethod(), pos.getBCI());
        if (pos.marker != None) {
            sb.append(" " + pos.marker);
        }
        if (SOURCE_POSITION_BYTECODES) {
            String disassembly = BytecodeDisassembler.disassembleOne(pos.getMethod(), pos.getBCI());
            if (disassembly != null && disassembly.length() > 0) {
                sb.append(" // ");
                sb.append(disassembly);
            }
        }
    }

    String shallowToString() {
        StringBuilder sb = new StringBuilder(100);
        format(sb, this);
        return sb.toString();
    }

    public boolean verify() {
        NodeSourcePosition current = this;
        NodeSourcePosition caller = getCaller();
        while (caller != null) {
            assert verifyCaller(current, caller) : current;
            current = caller;
            caller = caller.getCaller();
        }
        return true;
    }

    private static boolean verifyCaller(NodeSourcePosition current, NodeSourcePosition caller) {
        if (!STRICT_SOURCE_POSITION) {
            return true;
        }
        if (BytecodeFrame.isPlaceholderBci(caller.getBCI())) {
            return true;
        }
        int opcode = BytecodeDisassembler.getBytecodeAt(caller.getMethod(), caller.getBCI());
        JavaMethod method = BytecodeDisassembler.getInvokedMethodAt(caller.getMethod(), caller.getBCI());
        /*
         * It's not really possible to match the declaring classes since this might be an interface
         * invoke. Matching name and signature probably provides enough accuracy.
         */
        assert method == null || (method.getName().equals(current.getMethod().getName()) &&
                        method.getSignature().equals(current.getMethod().getSignature())) ||
                        caller.getMethod().getName().equals("linkToTargetMethod") ||
                        opcode == Bytecodes.INVOKEDYNAMIC ||
                        caller.getMethod().getDeclaringClass().getName().startsWith("Ljava/lang/invoke/LambdaForm$") ||
                        current.getMethod().getName().equals("callInlined") : "expected " + method + " but found " +
                                        current.getMethod();
        return true;
    }
}
