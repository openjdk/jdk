/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2018, Red Hat Inc. All rights reserved.
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

import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.replacements.Snippet.ConstantParameter;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node.NodeIntrinsic;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.DeoptimizeNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.IntegerDivRemNode;
import org.graalvm.compiler.nodes.calc.SignedDivNode;
import org.graalvm.compiler.nodes.calc.SignedRemNode;
import org.graalvm.compiler.nodes.calc.UnsignedDivNode;
import org.graalvm.compiler.nodes.calc.UnsignedRemNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.SnippetTemplate;
import org.graalvm.compiler.replacements.SnippetTemplate.AbstractTemplates;
import org.graalvm.compiler.replacements.SnippetTemplate.Arguments;
import org.graalvm.compiler.replacements.Snippets;

import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaKind;

/**
 * Division in AArch64 ISA does not generate a trap when dividing by zero, but instead sets the
 * result to 0. These snippets throw an ArithmethicException if the denominator is 0 and otherwise
 * forward to the LIRGenerator.
 */
public class AArch64IntegerArithmeticSnippets extends AbstractTemplates implements Snippets {

    private final SnippetTemplate.SnippetInfo idiv;
    private final SnippetTemplate.SnippetInfo ldiv;
    private final SnippetTemplate.SnippetInfo irem;
    private final SnippetTemplate.SnippetInfo lrem;

    private final SnippetTemplate.SnippetInfo uidiv;
    private final SnippetTemplate.SnippetInfo uldiv;
    private final SnippetTemplate.SnippetInfo uirem;
    private final SnippetTemplate.SnippetInfo ulrem;

    public AArch64IntegerArithmeticSnippets(OptionValues options, Iterable<DebugHandlersFactory> factories, Providers providers, SnippetReflectionProvider snippetReflection,
                    TargetDescription target) {
        super(options, factories, providers, snippetReflection, target);
        idiv = snippet(AArch64IntegerArithmeticSnippets.class, "idivSnippet");
        ldiv = snippet(AArch64IntegerArithmeticSnippets.class, "ldivSnippet");
        irem = snippet(AArch64IntegerArithmeticSnippets.class, "iremSnippet");
        lrem = snippet(AArch64IntegerArithmeticSnippets.class, "lremSnippet");

        uidiv = snippet(AArch64IntegerArithmeticSnippets.class, "uidivSnippet");
        uldiv = snippet(AArch64IntegerArithmeticSnippets.class, "uldivSnippet");
        uirem = snippet(AArch64IntegerArithmeticSnippets.class, "uiremSnippet");
        ulrem = snippet(AArch64IntegerArithmeticSnippets.class, "ulremSnippet");
    }

    public void lower(IntegerDivRemNode node, LoweringTool tool) {
        JavaKind kind = node.stamp(NodeView.DEFAULT).getStackKind();
        assert kind == JavaKind.Int || kind == JavaKind.Long;
        SnippetTemplate.SnippetInfo snippet;
        if (node instanceof SafeNode) {
            // We already introduced the zero division check, nothing to do.
            return;
        } else if (node instanceof SignedDivNode) {
            snippet = kind == JavaKind.Int ? idiv : ldiv;
        } else if (node instanceof SignedRemNode) {
            snippet = kind == JavaKind.Int ? irem : lrem;
        } else if (node instanceof UnsignedDivNode) {
            snippet = kind == JavaKind.Int ? uidiv : uldiv;
        } else if (node instanceof UnsignedRemNode) {
            snippet = kind == JavaKind.Int ? uirem : ulrem;
        } else {
            throw GraalError.shouldNotReachHere();
        }
        StructuredGraph graph = node.graph();
        Arguments args = new Arguments(snippet, graph.getGuardsStage(), tool.getLoweringStage());
        args.add("x", node.getX());
        args.add("y", node.getY());

        IntegerStamp yStamp = (IntegerStamp) node.getY().stamp(NodeView.DEFAULT);
        args.addConst("needsZeroCheck", node.getZeroCheck() == null && yStamp.contains(0));

        template(node, args).instantiate(providers.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
    }

    @Snippet
    public static int idivSnippet(int x, int y, @ConstantParameter boolean needsZeroCheck) {
        if (needsZeroCheck) {
            checkForZero(y);
        }
        return safeDiv(x, y);
    }

    @Snippet
    public static long ldivSnippet(long x, long y, @ConstantParameter boolean needsZeroCheck) {
        if (needsZeroCheck) {
            checkForZero(y);
        }
        return safeDiv(x, y);
    }

    @Snippet
    public static int iremSnippet(int x, int y, @ConstantParameter boolean needsZeroCheck) {
        if (needsZeroCheck) {
            checkForZero(y);
        }
        return safeRem(x, y);
    }

    @Snippet
    public static long lremSnippet(long x, long y, @ConstantParameter boolean needsZeroCheck) {
        if (needsZeroCheck) {
            checkForZero(y);
        }
        return safeRem(x, y);
    }

    @Snippet
    public static int uidivSnippet(int x, int y, @ConstantParameter boolean needsZeroCheck) {
        if (needsZeroCheck) {
            checkForZero(y);
        }
        return safeUDiv(x, y);
    }

    @Snippet
    public static long uldivSnippet(long x, long y, @ConstantParameter boolean needsZeroCheck) {
        if (needsZeroCheck) {
            checkForZero(y);
        }
        return safeUDiv(x, y);
    }

    @Snippet
    public static int uiremSnippet(int x, int y, @ConstantParameter boolean needsZeroCheck) {
        if (needsZeroCheck) {
            checkForZero(y);
        }
        return safeURem(x, y);
    }

    @Snippet
    public static long ulremSnippet(long x, long y, @ConstantParameter boolean needsZeroCheck) {
        if (needsZeroCheck) {
            checkForZero(y);
        }
        return safeURem(x, y);
    }

    private static void checkForZero(int y) {
        if (y == 0) {
            // "/ by zero"
            DeoptimizeNode.deopt(DeoptimizationAction.InvalidateReprofile, DeoptimizationReason.ArithmeticException);
        }
    }

    private static void checkForZero(long y) {
        if (y == 0) {
            // "/ by zero"
            DeoptimizeNode.deopt(DeoptimizationAction.InvalidateReprofile, DeoptimizationReason.ArithmeticException);
        }
    }

    @NodeIntrinsic(SafeSignedDivNode.class)
    private static native int safeDiv(int x, int y);

    @NodeIntrinsic(SafeSignedDivNode.class)
    private static native long safeDiv(long x, long y);

    @NodeIntrinsic(SafeSignedRemNode.class)
    private static native int safeRem(int x, int y);

    @NodeIntrinsic(SafeSignedRemNode.class)
    private static native long safeRem(long x, long y);

    @NodeIntrinsic(SafeUnsignedDivNode.class)
    private static native int safeUDiv(int x, int y);

    @NodeIntrinsic(SafeUnsignedDivNode.class)
    private static native long safeUDiv(long x, long y);

    @NodeIntrinsic(SafeUnsignedRemNode.class)
    private static native int safeURem(int x, int y);

    @NodeIntrinsic(SafeUnsignedRemNode.class)
    private static native long safeURem(long x, long y);

    /**
     * Marker interface to distinguish untreated nodes from ones where we have installed the
     * additional checks.
     */
    private interface SafeNode {
    }

    @NodeInfo
    static class SafeSignedDivNode extends SignedDivNode implements SafeNode {
        public static final NodeClass<SafeSignedDivNode> TYPE = NodeClass.create(SafeSignedDivNode.class);

        protected SafeSignedDivNode(ValueNode x, ValueNode y) {
            super(TYPE, x, y, null);
        }

        @Override
        public void generate(NodeLIRBuilderTool gen) {
            // override to ensure we always pass a null frame state
            // the parent method expects to create one from a non null before state
            gen.setResult(this, gen.getLIRGeneratorTool().getArithmetic().emitDiv(gen.operand(getX()), gen.operand(getY()), null));
        }
    }

    @NodeInfo
    static class SafeSignedRemNode extends SignedRemNode implements SafeNode {
        public static final NodeClass<SafeSignedRemNode> TYPE = NodeClass.create(SafeSignedRemNode.class);

        protected SafeSignedRemNode(ValueNode x, ValueNode y) {
            super(TYPE, x, y, null);
        }

        @Override
        public void generate(NodeLIRBuilderTool gen) {
            // override to ensure we always pass a null frame state
            // the parent method expects to create one from a non null before state
            gen.setResult(this, gen.getLIRGeneratorTool().getArithmetic().emitRem(gen.operand(getX()), gen.operand(getY()), null));
        }
    }

    @NodeInfo
    static class SafeUnsignedDivNode extends UnsignedDivNode implements SafeNode {
        public static final NodeClass<SafeUnsignedDivNode> TYPE = NodeClass.create(SafeUnsignedDivNode.class);

        protected SafeUnsignedDivNode(ValueNode x, ValueNode y) {
            super(TYPE, x, y, null);
        }

        @Override
        public void generate(NodeLIRBuilderTool gen) {
            // override to ensure we always pass a null frame state
            // the parent method expects to create one from a non null before state
            gen.setResult(this, gen.getLIRGeneratorTool().getArithmetic().emitUDiv(gen.operand(getX()), gen.operand(getY()), null));
        }
    }

    @NodeInfo
    static class SafeUnsignedRemNode extends UnsignedRemNode implements SafeNode {
        public static final NodeClass<SafeUnsignedRemNode> TYPE = NodeClass.create(SafeUnsignedRemNode.class);

        protected SafeUnsignedRemNode(ValueNode x, ValueNode y) {
            super(TYPE, x, y, null);
        }

        @Override
        public void generate(NodeLIRBuilderTool gen) {
            // override to ensure we always pass a null frame state
            // the parent method expects to create one from a non null before state
            gen.setResult(this, gen.getLIRGeneratorTool().getArithmetic().emitURem(gen.operand(getX()), gen.operand(getY()), null));
        }
    }

}
