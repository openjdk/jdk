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

import static jdk.nashorn.internal.runtime.logging.DebugLogger.quote;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import jdk.nashorn.internal.codegen.Compiler.CompilationPhases;
import jdk.nashorn.internal.ir.Block;
import jdk.nashorn.internal.ir.FunctionNode;
import jdk.nashorn.internal.ir.LiteralNode;
import jdk.nashorn.internal.ir.Node;
import jdk.nashorn.internal.ir.Symbol;
import jdk.nashorn.internal.ir.debug.ASTWriter;
import jdk.nashorn.internal.ir.debug.PrintVisitor;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;
import jdk.nashorn.internal.ir.visitor.SimpleNodeVisitor;
import jdk.nashorn.internal.runtime.CodeInstaller;
import jdk.nashorn.internal.runtime.RecompilableScriptFunctionData;
import jdk.nashorn.internal.runtime.ScriptEnvironment;
import jdk.nashorn.internal.runtime.logging.DebugLogger;

/**
 * A compilation phase is a step in the processes of turning a JavaScript
 * FunctionNode into bytecode. It has an optional return value.
 */
abstract class CompilationPhase {

    private static final class ConstantFoldingPhase extends CompilationPhase {
        @Override
        FunctionNode transform(final Compiler compiler, final CompilationPhases phases, final FunctionNode fn) {
            return transformFunction(fn, new FoldConstants(compiler));
        }

        @Override
        public String toString() {
            return "'Constant Folding'";
        }
    }

    /**
     * Constant folding pass Simple constant folding that will make elementary
     * constructs go away
     */
    static final CompilationPhase CONSTANT_FOLDING_PHASE = new ConstantFoldingPhase();

    private static final class LoweringPhase extends CompilationPhase {
        @Override
        FunctionNode transform(final Compiler compiler, final CompilationPhases phases, final FunctionNode fn) {
            return transformFunction(fn, new Lower(compiler));
        }

        @Override
        public String toString() {
            return "'Control Flow Lowering'";
        }
    }

    /**
     * Lower (Control flow pass) Finalizes the control flow. Clones blocks for
     * finally constructs and similar things. Establishes termination criteria
     * for nodes Guarantee return instructions to method making sure control
     * flow cannot fall off the end. Replacing high level nodes with lower such
     * as runtime nodes where applicable.
     */
    static final CompilationPhase LOWERING_PHASE = new LoweringPhase();

    private static final class ApplySpecializationPhase extends CompilationPhase {
        @Override
        FunctionNode transform(final Compiler compiler, final CompilationPhases phases, final FunctionNode fn) {
            return transformFunction(fn, new ApplySpecialization(compiler));
        }

        @Override
        public String toString() {
            return "'Builtin Replacement'";
        }
    };

    /**
     * Phase used to transform Function.prototype.apply.
     */
    static final CompilationPhase APPLY_SPECIALIZATION_PHASE = new ApplySpecializationPhase();

    private static final class SplittingPhase extends CompilationPhase {
        @Override
        FunctionNode transform(final Compiler compiler, final CompilationPhases phases, final FunctionNode fn) {
            final CompileUnit  outermostCompileUnit = compiler.addCompileUnit(0L);

            FunctionNode newFunctionNode;

            //ensure elementTypes, postsets and presets exist for splitter and arraynodes
            newFunctionNode = transformFunction(fn, new SimpleNodeVisitor() {
                @Override
                public LiteralNode<?> leaveLiteralNode(final LiteralNode<?> literalNode) {
                    return literalNode.initialize(lc);
                }
            });

            newFunctionNode = new Splitter(compiler, newFunctionNode, outermostCompileUnit).split(newFunctionNode, true);
            newFunctionNode = transformFunction(newFunctionNode, new SplitIntoFunctions(compiler));
            assert newFunctionNode.getCompileUnit() == outermostCompileUnit : "fn=" + fn.getName() + ", fn.compileUnit (" + newFunctionNode.getCompileUnit() + ") != " + outermostCompileUnit;
            assert newFunctionNode.isStrict() == compiler.isStrict() : "functionNode.isStrict() != compiler.isStrict() for " + quote(newFunctionNode.getName());

            return newFunctionNode;
        }

        @Override
        public String toString() {
            return "'Code Splitting'";
        }
    };

    /**
     * Splitter Split the AST into several compile units based on a heuristic size calculation.
     * Split IR can lead to scope information being changed.
     */
    static final CompilationPhase SPLITTING_PHASE = new SplittingPhase();

    private static final class ProgramPointPhase extends CompilationPhase {
        @Override
        FunctionNode transform(final Compiler compiler, final CompilationPhases phases, final FunctionNode fn) {
            return transformFunction(fn, new ProgramPoints());
        }

        @Override
        public String toString() {
            return "'Program Point Calculation'";
        }
    };

    /**
     * Phase used only when doing optimistic code generation. It assigns all potentially
     * optimistic ops a program point so that an UnwarrantedException knows from where
     * a guess went wrong when creating the continuation to roll back this execution
     */
    static final CompilationPhase PROGRAM_POINT_PHASE = new ProgramPointPhase();

    private static final class CacheAstPhase extends CompilationPhase {
        @Override
        FunctionNode transform(final Compiler compiler, final CompilationPhases phases, final FunctionNode fn) {
            if (!compiler.isOnDemandCompilation()) {
                // Only do this on initial preprocessing of the source code. For on-demand compilations from
                // source, FindScopeDepths#leaveFunctionNode() calls data.setCachedAst() for the sole function
                // being compiled.
                transformFunction(fn, new CacheAst(compiler));
            }
            // NOTE: we're returning the original fn as we have destructively modified the cached functions by
            // removing their bodies. This step is associating FunctionNode objects with
            // RecompilableScriptFunctionData; it's not really modifying the AST.
            return fn;
        }

        @Override
        public String toString() {
            return "'Cache ASTs'";
        }
    };

    static final CompilationPhase CACHE_AST_PHASE = new CacheAstPhase();

    private static final class SymbolAssignmentPhase extends CompilationPhase {
        @Override
        FunctionNode transform(final Compiler compiler, final CompilationPhases phases, final FunctionNode fn) {
            return transformFunction(fn, new AssignSymbols(compiler));
        }

        @Override
        public String toString() {
            return "'Symbol Assignment'";
        }
    };

    static final CompilationPhase SYMBOL_ASSIGNMENT_PHASE = new SymbolAssignmentPhase();

    private static final class ScopeDepthComputationPhase extends CompilationPhase {
        @Override
        FunctionNode transform(final Compiler compiler, final CompilationPhases phases, final FunctionNode fn) {
            return transformFunction(fn, new FindScopeDepths(compiler));
        }

        @Override
        public String toString() {
            return "'Scope Depth Computation'";
        }
    }

    static final CompilationPhase SCOPE_DEPTH_COMPUTATION_PHASE = new ScopeDepthComputationPhase();

    private static final class DeclareLocalSymbolsPhase extends CompilationPhase {
        @Override
        FunctionNode transform(final Compiler compiler, final CompilationPhases phases, final FunctionNode fn) {
            // It's not necessary to guard the marking of symbols as locals with this "if" condition for
            // correctness, it's just an optimization -- runtime type calculation is not used when the compilation
            // is not an on-demand optimistic compilation, so we can skip locals marking then.
            if (compiler.useOptimisticTypes() && compiler.isOnDemandCompilation()) {
                fn.getBody().accept(new SimpleNodeVisitor() {
                    @Override
                    public boolean enterFunctionNode(final FunctionNode functionNode) {
                        // OTOH, we must not declare symbols from nested functions to be locals. As we're doing on-demand
                        // compilation, and we're skipping parsing the function bodies for nested functions, this
                        // basically only means their parameters. It'd be enough to mistakenly declare to be a local a
                        // symbol in the outer function named the same as one of the parameters, though.
                        return false;
                    };
                    @Override
                    public boolean enterBlock(final Block block) {
                        for (final Symbol symbol: block.getSymbols()) {
                            if (!symbol.isScope()) {
                                compiler.declareLocalSymbol(symbol.getName());
                            }
                        }
                        return true;
                    };
                });
            }
            return fn;
        }

        @Override
        public String toString() {
            return "'Local Symbols Declaration'";
        }
    };

    static final CompilationPhase DECLARE_LOCAL_SYMBOLS_PHASE = new DeclareLocalSymbolsPhase();

    private static final class OptimisticTypeAssignmentPhase extends CompilationPhase {
        @Override
        FunctionNode transform(final Compiler compiler, final CompilationPhases phases, final FunctionNode fn) {
            if (compiler.useOptimisticTypes()) {
                return transformFunction(fn, new OptimisticTypesCalculator(compiler));
            }
            return fn;
        }

        @Override
        public String toString() {
            return "'Optimistic Type Assignment'";
        }
    }

    static final CompilationPhase OPTIMISTIC_TYPE_ASSIGNMENT_PHASE = new OptimisticTypeAssignmentPhase();

    private static final class LocalVariableTypeCalculationPhase extends CompilationPhase {
        @Override
        FunctionNode transform(final Compiler compiler, final CompilationPhases phases, final FunctionNode fn) {
            final FunctionNode newFunctionNode = transformFunction(fn, new LocalVariableTypesCalculator(compiler));
            final ScriptEnvironment senv = compiler.getScriptEnvironment();
            final PrintWriter       err  = senv.getErr();

            //TODO separate phase for the debug printouts for abstraction and clarity
            if (senv._print_lower_ast || fn.getFlag(FunctionNode.IS_PRINT_LOWER_AST)) {
                err.println("Lower AST for: " + quote(newFunctionNode.getName()));
                err.println(new ASTWriter(newFunctionNode));
            }

            if (senv._print_lower_parse || fn.getFlag(FunctionNode.IS_PRINT_LOWER_PARSE)) {
                err.println("Lower AST for: " + quote(newFunctionNode.getName()));
                err.println(new PrintVisitor(newFunctionNode));
            }

            return newFunctionNode;
        }

        @Override
        public String toString() {
            return "'Local Variable Type Calculation'";
        }
    };

    static final CompilationPhase LOCAL_VARIABLE_TYPE_CALCULATION_PHASE = new LocalVariableTypeCalculationPhase();

    private static final class ReuseCompileUnitsPhase extends CompilationPhase {
        @Override
        FunctionNode transform(final Compiler compiler, final CompilationPhases phases, final FunctionNode fn) {
            assert phases.isRestOfCompilation() : "reuse compile units currently only used for Rest-Of methods";

            final Map<CompileUnit, CompileUnit> map = new HashMap<>();
            final Set<CompileUnit> newUnits = CompileUnit.createCompileUnitSet();

            final DebugLogger log = compiler.getLogger();

            log.fine("Clearing bytecode cache");
            compiler.clearBytecode();

            for (final CompileUnit oldUnit : compiler.getCompileUnits()) {
                assert map.get(oldUnit) == null;
                final CompileUnit newUnit = createNewCompileUnit(compiler, phases);
                log.fine("Creating new compile unit ", oldUnit, " => ", newUnit);
                map.put(oldUnit, newUnit);
                assert newUnit != null;
                newUnits.add(newUnit);
            }

            log.fine("Replacing compile units in Compiler...");
            compiler.replaceCompileUnits(newUnits);
            log.fine("Done");

            //replace old compile units in function nodes, if any are assigned,
            //for example by running the splitter on this function node in a previous
            //partial code generation
            final FunctionNode newFunctionNode = transformFunction(fn, new ReplaceCompileUnits() {
                @Override
                CompileUnit getReplacement(final CompileUnit original) {
                    return map.get(original);
                }

                @Override
                public Node leaveDefault(final Node node) {
                    return node.ensureUniqueLabels(lc);
                }
            });

            return newFunctionNode;
        }

        @Override
        public String toString() {
            return "'Reuse Compile Units'";
        }
    }

    /**
     * Reuse compile units, if they are already present. We are using the same compiler
     * to recompile stuff
     */
    static final CompilationPhase REUSE_COMPILE_UNITS_PHASE = new ReuseCompileUnitsPhase();

    private static final class ReinitializeCachedPhase extends CompilationPhase {
        @Override
        FunctionNode transform(final Compiler compiler, final CompilationPhases phases, final FunctionNode fn) {
            final Set<CompileUnit> unitSet = CompileUnit.createCompileUnitSet();
            final Map<CompileUnit, CompileUnit> unitMap = new HashMap<>();

            // Ensure that the FunctionNode's compile unit is the first in the list of new units. Install phase
            // will use that as the root class.
            createCompileUnit(fn.getCompileUnit(), unitSet, unitMap, compiler, phases);

            final FunctionNode newFn = transformFunction(fn, new ReplaceCompileUnits() {
                @Override
                CompileUnit getReplacement(final CompileUnit oldUnit) {
                    final CompileUnit existing = unitMap.get(oldUnit);
                    if (existing != null) {
                        return existing;
                    }
                    return createCompileUnit(oldUnit, unitSet, unitMap, compiler, phases);
                }

                @Override
                public Node leaveFunctionNode(final FunctionNode fn2) {
                    return super.leaveFunctionNode(
                            // restore flags for deserialized nested function nodes
                            compiler.getScriptFunctionData(fn2.getId()).restoreFlags(lc, fn2));
                };
            });
            compiler.replaceCompileUnits(unitSet);
            return newFn;
        }

        private CompileUnit createCompileUnit(final CompileUnit oldUnit, final Set<CompileUnit> unitSet,
                final Map<CompileUnit, CompileUnit> unitMap, final Compiler compiler, final CompilationPhases phases) {
            final CompileUnit newUnit = createNewCompileUnit(compiler, phases);
            unitMap.put(oldUnit, newUnit);
            unitSet.add(newUnit);
            return newUnit;
        }

        @Override
        public String toString() {
            return "'Reinitialize cached'";
        }
    }

    static final CompilationPhase REINITIALIZE_CACHED = new ReinitializeCachedPhase();

    private static final class BytecodeGenerationPhase extends CompilationPhase {
        @Override
        FunctionNode transform(final Compiler compiler, final CompilationPhases phases, final FunctionNode fn) {
            final ScriptEnvironment senv = compiler.getScriptEnvironment();

            FunctionNode newFunctionNode = fn;

            //root class is special, as it is bootstrapped from createProgramFunction, thus it's skipped
            //in CodeGeneration - the rest can be used as a working "is compile unit used" metric
            fn.getCompileUnit().setUsed();

            compiler.getLogger().fine("Starting bytecode generation for ", quote(fn.getName()), " - restOf=", phases.isRestOfCompilation());

            final CodeGenerator codegen = new CodeGenerator(compiler, phases.isRestOfCompilation() ? compiler.getContinuationEntryPoints() : null);

            try {
                // Explicitly set BYTECODE_GENERATED here; it can not be set in case of skipping codegen for :program
                // in the lazy + optimistic world. See CodeGenerator.skipFunction().
                newFunctionNode = transformFunction(newFunctionNode, codegen);
                codegen.generateScopeCalls();
            } catch (final VerifyError e) {
                if (senv._verify_code || senv._print_code) {
                    senv.getErr().println(e.getClass().getSimpleName() + ": "  + e.getMessage());
                    if (senv._dump_on_error) {
                        e.printStackTrace(senv.getErr());
                    }
                } else {
                    throw e;
                }
            } catch (final Throwable e) {
                // Provide source file and line number being compiled when the assertion occurred
                throw new AssertionError("Failed generating bytecode for " + fn.getSourceName() + ":" + codegen.getLastLineNumber(), e);
            }

            for (final CompileUnit compileUnit : compiler.getCompileUnits()) {
                final ClassEmitter classEmitter = compileUnit.getClassEmitter();
                classEmitter.end();

                if (!compileUnit.isUsed()) {
                    compiler.getLogger().fine("Skipping unused compile unit ", compileUnit);
                    continue;
                }

                final byte[] bytecode = classEmitter.toByteArray();
                assert bytecode != null;

                final String className = compileUnit.getUnitClassName();
                compiler.addClass(className, bytecode); //classes are only added to the bytecode map if compile unit is used

                CompileUnit.increaseEmitCount();

                // should we verify the generated code?
                if (senv._verify_code) {
                    compiler.getCodeInstaller().verify(bytecode);
                }

                DumpBytecode.dumpBytecode(senv, compiler.getLogger(), bytecode, className);
            }

            return newFunctionNode;
        }

        @Override
        public String toString() {
            return "'Bytecode Generation'";
        }
    }

    /**
     * Bytecode generation:
     *
     * Generate the byte code class(es) resulting from the compiled FunctionNode
     */
    static final CompilationPhase BYTECODE_GENERATION_PHASE = new BytecodeGenerationPhase();

    private static final class InstallPhase extends CompilationPhase {
        @Override
        FunctionNode transform(final Compiler compiler, final CompilationPhases phases, final FunctionNode fn) {
            final DebugLogger log = compiler.getLogger();

            final Map<String, Class<?>> installedClasses = new LinkedHashMap<>();

            boolean first = true;
            Class<?> rootClass = null;
            long length = 0L;

            final CodeInstaller origCodeInstaller = compiler.getCodeInstaller();
            final Map<String, byte[]> bytecode = compiler.getBytecode();
            final CodeInstaller codeInstaller = bytecode.size() > 1 ? origCodeInstaller.getMultiClassCodeInstaller() : origCodeInstaller;

            for (final Entry<String, byte[]> entry : bytecode.entrySet()) {
                final String className = entry.getKey();
                //assert !first || className.equals(compiler.getFirstCompileUnit().getUnitClassName()) : "first=" + first + " className=" + className + " != " + compiler.getFirstCompileUnit().getUnitClassName();
                final byte[] code = entry.getValue();
                length += code.length;

                final Class<?> clazz = codeInstaller.install(className, code);
                if (first) {
                    rootClass = clazz;
                    first = false;
                }
                installedClasses.put(className, clazz);
            }

            if (rootClass == null) {
                throw new CompilationException("Internal compiler error: root class not found!");
            }

            final Object[] constants = compiler.getConstantData().toArray();
            codeInstaller.initialize(installedClasses.values(), compiler.getSource(), constants);

            // initialize transient fields on recompilable script function data
            for (final Object constant: constants) {
                if (constant instanceof RecompilableScriptFunctionData) {
                    ((RecompilableScriptFunctionData)constant).initTransients(compiler.getSource(), codeInstaller);
                }
            }

            // initialize function in the compile units
            for (final CompileUnit unit : compiler.getCompileUnits()) {
                if (!unit.isUsed()) {
                    continue;
                }
                unit.setCode(installedClasses.get(unit.getUnitClassName()));
                unit.initializeFunctionsCode();
            }

            if (log.isEnabled()) {
                final StringBuilder sb = new StringBuilder();

                sb.append("Installed class '").
                    append(rootClass.getSimpleName()).
                    append('\'').
                    append(" [").
                    append(rootClass.getName()).
                    append(", size=").
                    append(length).
                    append(" bytes, ").
                    append(compiler.getCompileUnits().size()).
                    append(" compile unit(s)]");

                log.fine(sb.toString());
            }

            return fn.setRootClass(null, rootClass);
        }

        @Override
        public String toString() {
            return "'Class Installation'";
        }
    }

    static final CompilationPhase INSTALL_PHASE = new InstallPhase();

    /** start time of transform - used for timing, see {@link jdk.nashorn.internal.runtime.Timing} */
    private long startTime;

    /** start time of transform - used for timing, see {@link jdk.nashorn.internal.runtime.Timing} */
    private long endTime;

    /** boolean that is true upon transform completion */
    private boolean isFinished;

    private CompilationPhase() {}

    /**
     * Start a compilation phase
     * @param compiler the compiler to use
     * @param functionNode function to compile
     * @return function node
     */
    protected FunctionNode begin(final Compiler compiler, final FunctionNode functionNode) {
        compiler.getLogger().indent();
        startTime = System.nanoTime();

         return functionNode;
     }

    /**
     * End a compilation phase
     * @param compiler the compiler
     * @param functionNode function node to compile
     * @return function node
     */
    protected FunctionNode end(final Compiler compiler, final FunctionNode functionNode) {
        compiler.getLogger().unindent();
        endTime = System.nanoTime();
        compiler.getScriptEnvironment()._timing.accumulateTime(toString(), endTime - startTime);

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

    abstract FunctionNode transform(final Compiler compiler, final CompilationPhases phases, final FunctionNode functionNode) throws CompilationException;

    /**
     * Apply a transform to a function node, returning the transformed function node. If the transform is not
     * applicable, an exception is thrown. Every transform requires the function to have a certain number of
     * states to operate. It can have more states set, but not fewer. The state list, i.e. the constructor
     * arguments to any of the CompilationPhase enum entries, is a set of REQUIRED states.
     *
     * @param compiler     compiler
     * @param phases       current complete pipeline of which this phase is one
     * @param functionNode function node to transform
     *
     * @return transformed function node
     *
     * @throws CompilationException if function node lacks the state required to run the transform on it
     */
    final FunctionNode apply(final Compiler compiler, final CompilationPhases phases, final FunctionNode functionNode) throws CompilationException {
        assert phases.contains(this);

        return end(compiler, transform(compiler, phases, begin(compiler, functionNode)));
    }

    private static FunctionNode transformFunction(final FunctionNode fn, final NodeVisitor<?> visitor) {
        return (FunctionNode) fn.accept(visitor);
    }

    private static CompileUnit createNewCompileUnit(final Compiler compiler, final CompilationPhases phases) {
        final StringBuilder sb = new StringBuilder(compiler.nextCompileUnitName());
        if (phases.isRestOfCompilation()) {
            sb.append("$restOf");
        }
        //it's ok to not copy the initCount, methodCount and clinitCount here, as codegen is what
        //fills those out anyway. Thus no need for a copy constructor
        return compiler.createCompileUnit(sb.toString(), 0);
    }
}
