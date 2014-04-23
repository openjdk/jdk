/*
 * Copyright (c) 2010, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.nashorn.internal.codegen;

import static jdk.nashorn.internal.ir.FunctionNode.CompilationState.ATTR;
import static jdk.nashorn.internal.ir.FunctionNode.CompilationState.CONSTANT_FOLDED;
import static jdk.nashorn.internal.ir.FunctionNode.CompilationState.FINALIZED;
import static jdk.nashorn.internal.ir.FunctionNode.CompilationState.INITIALIZED;
import static jdk.nashorn.internal.ir.FunctionNode.CompilationState.LOWERED;
import static jdk.nashorn.internal.ir.FunctionNode.CompilationState.PARSED;
import static jdk.nashorn.internal.ir.FunctionNode.CompilationState.SCOPE_DEPTHS_COMPUTED;
import static jdk.nashorn.internal.ir.FunctionNode.CompilationState.SPLIT;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;

import jdk.nashorn.internal.codegen.types.Range;
import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.ir.Expression;
import jdk.nashorn.internal.ir.FunctionNode;
import jdk.nashorn.internal.ir.FunctionNode.CompilationState;
import jdk.nashorn.internal.ir.LexicalContext;
import jdk.nashorn.internal.ir.Node;
import jdk.nashorn.internal.ir.ReturnNode;
import jdk.nashorn.internal.ir.Symbol;
import jdk.nashorn.internal.ir.TemporarySymbols;
import jdk.nashorn.internal.ir.debug.ASTWriter;
import jdk.nashorn.internal.ir.debug.PrintVisitor;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;
import jdk.nashorn.internal.objects.Global;
import jdk.nashorn.internal.runtime.ScriptEnvironment;
import jdk.nashorn.internal.runtime.Timing;

/**
 * A compilation phase is a step in the processes of turning a JavaScript
 * FunctionNode into bytecode. It has an optional return value.
 */
enum CompilationPhase {
    /**
     * Constant folding pass Simple constant folding that will make elementary
     * constructs go away
     */
    CONSTANT_FOLDING_PHASE(EnumSet.of(INITIALIZED, PARSED)) {
        @Override
        FunctionNode transform(final Compiler compiler, final FunctionNode fn) {
            return (FunctionNode)fn.accept(new FoldConstants());
        }

        @Override
        public String toString() {
            return "[Constant Folding]";
        }
    },

    /**
     * Lower (Control flow pass) Finalizes the control flow. Clones blocks for
     * finally constructs and similar things. Establishes termination criteria
     * for nodes Guarantee return instructions to method making sure control
     * flow cannot fall off the end. Replacing high level nodes with lower such
     * as runtime nodes where applicable.
     */
    LOWERING_PHASE(EnumSet.of(INITIALIZED, PARSED, CONSTANT_FOLDED)) {
        @Override
        FunctionNode transform(final Compiler compiler, final FunctionNode fn) {
            return (FunctionNode)fn.accept(new Lower(compiler.getCodeInstaller()));
        }

        @Override
        public String toString() {
            return "[Control Flow Lowering]";
        }
    },

    /**
     * Phase used only when doing optimistic code generation. It assigns all potentially
     * optimistic ops a program point so that an UnwarrantedException knows from where
     * a guess went wrong when creating the continuation to roll back this execution
     */
    PROGRAM_POINT_PHASE(EnumSet.of(INITIALIZED, PARSED, CONSTANT_FOLDED, LOWERED)) {
        @Override
        FunctionNode transform(final Compiler compiler, final FunctionNode fn) {
            return (FunctionNode)fn.accept(new ProgramPoints());
        }

        @Override
        public String toString() {
            return "[Program Point Calculation]";
        }
    },

    /**
     * Splitter Split the AST into several compile units based on a heuristic size calculation.
     * Split IR can lead to scope information being changed.
     */
    SPLITTING_PHASE(EnumSet.of(INITIALIZED, PARSED, CONSTANT_FOLDED, LOWERED)) {
        @Override
        FunctionNode transform(final Compiler compiler, final FunctionNode fn) {
            final CompileUnit outermostCompileUnit = compiler.addCompileUnit(compiler.firstCompileUnitName());

            final FunctionNode newFunctionNode = new Splitter(compiler, fn, outermostCompileUnit).split(fn, true);

            assert newFunctionNode.getCompileUnit() == outermostCompileUnit : "fn=" + fn.getName() + ", fn.compileUnit (" + newFunctionNode.getCompileUnit() + ") != " + outermostCompileUnit;

            if (newFunctionNode.isStrict()) {
                assert compiler.getCompilationEnvironment().isStrict();
                compiler.getCompilationEnvironment().setIsStrict(true);
            }

            return newFunctionNode;
        }

        @Override
        public String toString() {
            return "[Code Splitting]";
        }
    },

    /**
     * Attribution Assign symbols and types to all nodes.
     */
    ATTRIBUTION_PHASE(EnumSet.of(INITIALIZED, PARSED, CONSTANT_FOLDED, LOWERED, SPLIT)) {
        @Override
        FunctionNode transform(final Compiler compiler, final FunctionNode fn) {
            final TemporarySymbols ts = compiler.getTemporarySymbols();
            final FunctionNode     newFunctionNode =
                    (FunctionNode)enterAttr(fn, ts).
                        accept(new Attr(compiler.getCompilationEnvironment(), ts));

            if (compiler.getEnv()._print_mem_usage) {
                compiler.getLogger().info("Attr temporary symbol count:", ts.getTotalSymbolCount());
            }

            return newFunctionNode;
        }

        /**
         * Pessimistically set all lazy functions' return types to Object
         * and the function symbols to object
         * @param functionNode node where to start iterating
         */
        private FunctionNode enterAttr(final FunctionNode functionNode, final TemporarySymbols ts) {
            return (FunctionNode)functionNode.accept(new NodeVisitor<LexicalContext>(new LexicalContext()) {
                @Override
                public Node leaveFunctionNode(final FunctionNode node) {
                    return node.setReturnType(lc, Type.UNKNOWN).setSymbol(lc, null);
                }
            });
        }

        @Override
        public String toString() {
            return "[Type Attribution]";
        }
    },

    /**
     * Range analysis
     *    Conservatively prove that certain variables can be narrower than
     *    the most generic number type
     */
    RANGE_ANALYSIS_PHASE(EnumSet.of(INITIALIZED, PARSED, CONSTANT_FOLDED, LOWERED, SPLIT, ATTR)) {
        @Override
        FunctionNode transform(final Compiler compiler, final FunctionNode fn) {
            if (!compiler.getEnv()._range_analysis) {
                return fn;
            }

            FunctionNode newFunctionNode = (FunctionNode)fn.accept(new RangeAnalyzer());
            final List<ReturnNode> returns = new ArrayList<>();

            newFunctionNode = (FunctionNode)newFunctionNode.accept(new NodeVisitor<LexicalContext>(new LexicalContext()) {
                private final Deque<ArrayList<ReturnNode>> returnStack = new ArrayDeque<>();

                @Override
                public boolean enterFunctionNode(final FunctionNode functionNode) {
                    returnStack.push(new ArrayList<ReturnNode>());
                    return true;
                }

                @Override
                public Node leaveFunctionNode(final FunctionNode functionNode) {
                    Type returnType = Type.UNKNOWN;
                    for (final ReturnNode ret : returnStack.pop()) {
                        if (ret.getExpression() == null) {
                            returnType = Type.OBJECT;
                            break;
                        }
                        returnType = Type.widest(returnType, ret.getExpression().getType());
                    }
                    return functionNode.setReturnType(lc, returnType);
                }

                @Override
                public Node leaveReturnNode(final ReturnNode returnNode) {
                    final ReturnNode result = (ReturnNode)leaveDefault(returnNode);
                    returns.add(result);
                    return result;
                }

                @Override
                public Node leaveDefault(final Node node) {
                    if(node instanceof Expression) {
                        final Expression expr = (Expression)node;
                        final Symbol symbol = expr.getSymbol();
                        if (symbol != null) {
                            final Range range      = symbol.getRange();
                            final Type  symbolType = symbol.getSymbolType();

                            if (!symbolType.isUnknown() && !symbolType.isNumeric()) {
                                return expr;
                            }

                            final Type rangeType  = range.getType();
                            if (!rangeType.isUnknown() && !Type.areEquivalent(symbolType, rangeType) && Type.widest(symbolType, rangeType) == symbolType) { //we can narrow range
                                Global.instance().getLogger(RangeAnalyzer.class).info("[", lc.getCurrentFunction().getName(), "] ", symbol, " can be ", range.getType(), " ", symbol.getRange());
                                return expr.setSymbol(lc, symbol.setTypeOverrideShared(range.getType(), compiler.getTemporarySymbols()));
                            }
                        }
                    }
                    return node;
                }
            });

            Type returnType = Type.UNKNOWN;
            for (final ReturnNode node : returns) {
                if (node.getExpression() != null) {
                    returnType = Type.widest(returnType, node.getExpression().getType());
                } else {
                    returnType = Type.OBJECT;
                    break;
                }
            }

            return newFunctionNode.setReturnType(null, returnType);
        }

        @Override
        public String toString() {
            return "[Range Analysis]";
        }
    },

    /**
     * FinalizeTypes
     *
     * This pass finalizes the types for nodes. If Attr created wider types than
     * known during the first pass, convert nodes are inserted or access nodes
     * are specialized where scope accesses.
     *
     * Runtime nodes may be removed and primitivized or reintroduced depending
     * on information that was established in Attr.
     *
     * Contract: all variables must have slot assignments and scope assignments
     * before type finalization.
     */
    TYPE_FINALIZATION_PHASE(EnumSet.of(INITIALIZED, PARSED, CONSTANT_FOLDED, LOWERED, ATTR, SPLIT)) {
        @Override
        FunctionNode transform(final Compiler compiler, final FunctionNode fn) {
            final ScriptEnvironment env = compiler.getEnv();

            final FunctionNode newFunctionNode = (FunctionNode)fn.accept(new FinalizeTypes());

            if (env._print_lower_ast) {
                env.getErr().println(new ASTWriter(newFunctionNode));
            }

            if (env._print_lower_parse) {
                env.getErr().println(new PrintVisitor(newFunctionNode));
            }

            return newFunctionNode;
        }

        @Override
        public String toString() {
            return "[Type Finalization]";
        }
    },

    SCOPE_DEPTH_COMPUTATION_PHASE(EnumSet.of(INITIALIZED, PARSED, CONSTANT_FOLDED, LOWERED, ATTR, SPLIT, FINALIZED)) {
        @Override
        FunctionNode transform(final Compiler compiler, final FunctionNode fn) {
            return (FunctionNode)fn.accept(new FindScopeDepths(compiler));
        }

        @Override
        public String toString() {
            return "[Scope Depth Computation]";
        }
    },

    /**
     * Bytecode generation:
     *
     * Generate the byte code class(es) resulting from the compiled FunctionNode
     */
    BYTECODE_GENERATION_PHASE(EnumSet.of(INITIALIZED, PARSED, CONSTANT_FOLDED, LOWERED, ATTR, SPLIT, FINALIZED, SCOPE_DEPTHS_COMPUTED)) {
        @Override
        FunctionNode transform(final Compiler compiler, final FunctionNode fn) {
            final ScriptEnvironment env = compiler.getEnv();
            FunctionNode newFunctionNode = fn;

            try {
                final CodeGenerator codegen = new CodeGenerator(compiler);
                newFunctionNode = (FunctionNode)newFunctionNode.accept(codegen);
                codegen.generateScopeCalls();
            } catch (final VerifyError e) {
                if (env._verify_code || env._print_code) {
                    env.getErr().println(e.getClass().getSimpleName() + ": "  + e.getMessage());
                    if (env._dump_on_error) {
                        e.printStackTrace(env.getErr());
                    }
                } else {
                    throw e;
                }
            }

            for (final CompileUnit compileUnit : compiler.getCompileUnits()) {
                final ClassEmitter classEmitter = compileUnit.getClassEmitter();
                classEmitter.end();

                final byte[] bytecode = classEmitter.toByteArray();
                assert bytecode != null;

                final String className = compileUnit.getUnitClassName();

                compiler.addClass(className, bytecode);

                // should we verify the generated code?
                if (env._verify_code) {
                    compiler.getCodeInstaller().verify(bytecode);
                }

                DumpBytecode.dumpBytecode(env, compiler, bytecode, className);
            }

            return newFunctionNode;
        }

        @Override
        public String toString() {
            return "[Bytecode Generation]";
        }
    };

    private final EnumSet<CompilationState> pre;
    private long startTime;
    private long endTime;
    private boolean isFinished;

    private CompilationPhase(final EnumSet<CompilationState> pre) {
        this.pre = pre;
    }

    boolean isApplicable(final FunctionNode functionNode) {
        return functionNode.hasState(pre);
    }

    /**
     * Start a compilation phase
     * @param functionNode function to compile
     * @return function node
     */
    protected FunctionNode begin(final FunctionNode functionNode) {
        if (pre != null) {
            // check that everything in pre is present
            for (final CompilationState state : pre) {
                assert functionNode.hasState(state);
            }
            // check that nothing else is present
            for (final CompilationState state : CompilationState.values()) {
                assert !(functionNode.hasState(state) && !pre.contains(state));
            }
        }

        startTime = System.currentTimeMillis();
        return functionNode;
    }

    /**
     * End a compilation phase
     * @param functionNode function node to compile
     * @return fucntion node
     */
    protected FunctionNode end(final FunctionNode functionNode) {
        endTime = System.currentTimeMillis();
        Timing.accumulateTime(toString(), endTime - startTime);

        isFinished = true;
        return functionNode;
    }

    boolean isFinished() {
        return isFinished;
    }

    long getStartTime() {
        return startTime;
    }

    long getEndTime() {
        return endTime;
    }

    abstract FunctionNode transform(final Compiler compiler, final FunctionNode functionNode) throws CompilationException;

    final FunctionNode apply(final Compiler compiler, final FunctionNode functionNode) throws CompilationException {
        if (!isApplicable(functionNode)) {
            throw new CompilationException("compile phase not applicable: " + this + " to " + functionNode.getName() + " state=" + functionNode.getState());
        }
        return end(transform(compiler, begin(functionNode)));
    }

}
