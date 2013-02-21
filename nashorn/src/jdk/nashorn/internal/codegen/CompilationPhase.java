package jdk.nashorn.internal.codegen;

import static jdk.nashorn.internal.ir.FunctionNode.CompilationState.ATTR;
import static jdk.nashorn.internal.ir.FunctionNode.CompilationState.CONSTANT_FOLDED;
import static jdk.nashorn.internal.ir.FunctionNode.CompilationState.EMITTED;
import static jdk.nashorn.internal.ir.FunctionNode.CompilationState.FINALIZED;
import static jdk.nashorn.internal.ir.FunctionNode.CompilationState.INITIALIZED;
import static jdk.nashorn.internal.ir.FunctionNode.CompilationState.LOWERED;
import static jdk.nashorn.internal.ir.FunctionNode.CompilationState.SPLIT;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.ir.CallNode;
import jdk.nashorn.internal.ir.FunctionNode;
import jdk.nashorn.internal.ir.FunctionNode.CompilationState;
import jdk.nashorn.internal.ir.Node;
import jdk.nashorn.internal.ir.ReferenceNode;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;
import jdk.nashorn.internal.ir.debug.ASTWriter;
import jdk.nashorn.internal.ir.debug.PrintVisitor;
import jdk.nashorn.internal.runtime.Context;
import jdk.nashorn.internal.runtime.ECMAErrors;
import jdk.nashorn.internal.runtime.Timing;

/**
 * A compilation phase is a step in the processes of turning a JavaScript FunctionNode
 * into bytecode. It has an optional return value.
 */
enum CompilationPhase {

    /*
     * Lazy initialization - tag all function nodes not the script as lazy as
     * default policy. The will get trampolines and only be generated when
     * called
     */
    LAZY_INITIALIZATION_PHASE(EnumSet.of(FunctionNode.CompilationState.INITIALIZED)) {
        @Override
        void transform(final Compiler compiler, final FunctionNode fn) {

            /*
             * For lazy compilation, we might be given a node previously marked as lazy
             * to compile as the outermost function node in the compiler. Unmark it
             * so it can be compiled and not cause recursion. Make sure the return type
             * is unknown so it can be correctly deduced. Return types are always
             * Objects in Lazy nodes as we haven't got a change to generate code for
             * them and decude its parameter specialization
             *
             * TODO: in the future specializations from a callsite will be passed here
             * so we can generate a better non-lazy version of a function from a trampoline
             */
            //compute the signature from the callsite - todo - now just clone object params
            final FunctionNode outermostFunctionNode = compiler.getFunctionNode();
            outermostFunctionNode.setIsLazy(false);
            outermostFunctionNode.setReturnType(Type.UNKNOWN);

            final Set<FunctionNode> neverLazy = new HashSet<>();
            final Set<FunctionNode> lazy = new HashSet<>();

            outermostFunctionNode.accept(new NodeVisitor() {
                // self references are done with invokestatic and thus cannot have trampolines - never lazy
                @Override
                public Node enter(final CallNode node) {
                    final Node callee = node.getFunction();
                    if (callee instanceof ReferenceNode) {
                        neverLazy.add(((ReferenceNode)callee).getReference());
                        return null;
                    }
                    return node;
                }

                @Override
                public Node enter(final FunctionNode node) {
                    if (node == outermostFunctionNode) {
                        return node;
                    }
                    assert Compiler.LAZY_JIT;
                    lazy.add(node);

                    return node;
                }
            });

            for (final FunctionNode node : neverLazy) {
                Compiler.LOG.fine("Marking " + node.getName() + " as non lazy, as it's a self reference");
                node.setIsLazy(false);
                lazy.remove(node);
            }

            for (final FunctionNode node : lazy) {
                Compiler.LOG.fine("Marking " + node.getName() + " as lazy");
                node.setIsLazy(true);
                final FunctionNode parent = node.findParentFunction();
                if (parent != null) {
                    Compiler.LOG.fine("Marking " + parent.getName() + " as having lazy children - it needs scope for all variables");
                    parent.setHasLazyChildren();
                }
            }
        }

        @Override
        public String toString() {
            return "[Lazy JIT Initialization]";
        }
    },

    /*
     * Constant folding pass
     *   Simple constant folding that will make elementary constructs go away
     */
    CONSTANT_FOLDING_PHASE(EnumSet.of(INITIALIZED), CONSTANT_FOLDED) {
        @Override
        void transform(final Compiler compiler, final FunctionNode fn) {
            fn.accept(new FoldConstants());
        }

        @Override
        public String toString() {
            return "[Constant Folding]";
        }
    },

    /*
     * Lower (Control flow pass)
     *   Finalizes the control flow. Clones blocks for finally constructs and
     *   similar things. Establishes termination criteria for nodes
     *   Guarantee return instructions to method making sure control flow
     *   cannot fall off the end. Replacing high level nodes with lower such
     *   as runtime nodes where applicable.
     *
     */
    LOWERING_PHASE(EnumSet.of(INITIALIZED, CONSTANT_FOLDED), LOWERED) {
        @Override
        void transform(final Compiler compiler, final FunctionNode fn) {
            fn.accept(new Lower());
        }

        @Override
        public String toString() {
            return "[Control Flow Lowering]";
        }
    },

    /*
     * Attribution
     *   Assign symbols and types to all nodes.
     */
    ATTRIBUTION_PHASE(EnumSet.of(INITIALIZED, CONSTANT_FOLDED, LOWERED), ATTR) {
        @Override
        void transform(final Compiler compiler, final FunctionNode fn) {
            final Context context = compiler.getContext();

            fn.accept(new Attr(context));
            if (context._print_lower_ast) {
                context.getErr().println(new ASTWriter(fn));
            }

            if (context._print_lower_parse) {
                context.getErr().println(new PrintVisitor(fn));
           }
        }

        @Override
        public String toString() {
            return "[Type Attribution]";
        }
    },

    /*
     * Splitter
     *   Split the AST into several compile units based on a size heuristic
     *   Splitter needs attributed AST for weight calculations (e.g. is
     *   a + b a ScriptRuntime.ADD with call overhead or a dadd with much
     *   less). Split IR can lead to scope information being changed.
     */
    SPLITTING_PHASE(EnumSet.of(INITIALIZED, CONSTANT_FOLDED, LOWERED, ATTR), SPLIT) {
        @Override
        void transform(final Compiler compiler, final FunctionNode fn) {
            final CompileUnit outermostCompileUnit = compiler.addCompileUnit(compiler.firstCompileUnitName());

            new Splitter(compiler, fn, outermostCompileUnit).split();

            assert fn.getCompileUnit() == outermostCompileUnit : "fn.compileUnit (" + fn.getCompileUnit() + ") != " + outermostCompileUnit;

            if (fn.isStrictMode()) {
                assert compiler.getStrictMode();
                compiler.setStrictMode(true);
            }
        }

        @Override
        public String toString() {
            return "[Code Splitting]";
        }
    },

    /*
     * FinalizeTypes
     *
     *   This pass finalizes the types for nodes. If Attr created wider types than
     *   known during the first pass, convert nodes are inserted or access nodes
     *   are specialized where scope accesses.
     *
     *   Runtime nodes may be removed and primitivized or reintroduced depending
     *   on information that was established in Attr.
     *
     * Contract: all variables must have slot assignments and scope assignments
     * before type finalization.
     */
    TYPE_FINALIZATION_PHASE(EnumSet.of(INITIALIZED, CONSTANT_FOLDED, LOWERED, ATTR, SPLIT), FINALIZED) {
        @Override
        void transform(final Compiler compiler, final FunctionNode fn) {
            fn.accept(new FinalizeTypes());
        }

        @Override
        public String toString() {
            return "[Type Finalization]";
        }
    },

    /*
     * Bytecode generation:
     *
     *   Generate the byte code class(es) resulting from the compiled FunctionNode
     */
    BYTECODE_GENERATION_PHASE(EnumSet.of(INITIALIZED, CONSTANT_FOLDED, LOWERED, ATTR, SPLIT, FINALIZED), EMITTED) {
        @Override
        void transform(final Compiler compiler, final FunctionNode fn) {
            final Context context = compiler.getContext();

            try {
                final CodeGenerator codegen = new CodeGenerator(compiler);
                fn.accept(codegen);
                codegen.generateScopeCalls();

            } catch (final VerifyError e) {
                if (context._verify_code || context._print_code) {
                    context.getErr().println(e.getClass().getSimpleName() + ": " + e.getMessage());
                    if (context._dump_on_error) {
                        e.printStackTrace(context.getErr());
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

                //should could be printed to stderr for generate class?
                if (context._print_code) {
                    final StringBuilder sb = new StringBuilder();
                    sb.append("class: " + className).
                        append('\n').
                        append(ClassEmitter.disassemble(bytecode)).
                        append("=====");
                    context.getErr().println(sb);
                }

                //should we verify the generated code?
                if (context._verify_code) {
                    context.verify(bytecode);
                }

                //should code be dumped to disk - only valid in compile_only mode?
                if (context._dest_dir != null && context._compile_only) {
                    final String fileName = className.replace('.', File.separatorChar) + ".class";
                    final int    index    = fileName.lastIndexOf(File.separatorChar);

                    if (index != -1) {
                        final File dir = new File(fileName.substring(0, index));
                        try {
                            if (!dir.exists() && !dir.mkdirs()) {
                                throw new IOException(dir.toString());
                            }
                            final File file = new File(context._dest_dir, fileName);
                            try (final FileOutputStream fos = new FileOutputStream(file)) {
                                fos.write(bytecode);
                            }
                        } catch (final IOException e) {
                            Compiler.LOG.warning("Skipping class dump for " + className + ": " + ECMAErrors.getMessage("io.error.cant.write", dir.toString()));
                        }
                    }
                }
            }
        }

        @Override
        public String toString() {
            return "[Bytecode Generation]";
        }
    };

    private final EnumSet<CompilationState> pre;
    private final CompilationState post;
    private long startTime;
    private long endTime;
    private boolean isFinished;

    private CompilationPhase(final EnumSet<CompilationState> pre) {
        this(pre, null);
    }

    private CompilationPhase(final EnumSet<CompilationState> pre, final CompilationState post) {
        this.pre  = pre;
        this.post = post;
    }

    boolean isApplicable(final FunctionNode functionNode) {
        return functionNode.hasState(pre);
    }

    protected void begin(final FunctionNode functionNode) {
        if (pre != null) {
            //check that everything in pre is present
            for (final CompilationState state : pre) {
                assert functionNode.hasState(state);
            }
            //check that nothing else is present
            for (final CompilationState state : CompilationState.values()) {
                assert !(functionNode.hasState(state) && !pre.contains(state));
            }
        }

        startTime = System.currentTimeMillis();
    }

    protected void end(final FunctionNode functionNode) {
        endTime = System.currentTimeMillis();
        Timing.accumulateTime(toString(), endTime - startTime);

        if (post != null) {
            functionNode.setState(post);
        }

        isFinished = true;
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

    abstract void transform(final Compiler compiler, final FunctionNode functionNode) throws CompilationException;

    final void apply(final Compiler compiler, final FunctionNode functionNode) throws CompilationException {
        if (!isApplicable(functionNode)) {
            throw new CompilationException("compile phase not applicable: " + this);
        }
        begin(functionNode);
        transform(compiler, functionNode);
        end(functionNode);
    }

}
