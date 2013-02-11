/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.nashorn.internal.codegen.CompilerConstants.CONSTANTS;
import static jdk.nashorn.internal.codegen.CompilerConstants.DEFAULT_SCRIPT_NAME;
import static jdk.nashorn.internal.codegen.CompilerConstants.RUN_SCRIPT;
import static jdk.nashorn.internal.codegen.CompilerConstants.SCOPE;
import static jdk.nashorn.internal.codegen.CompilerConstants.SOURCE;
import static jdk.nashorn.internal.codegen.CompilerConstants.THIS;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import jdk.nashorn.internal.codegen.ClassEmitter.Flag;
import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.ir.FunctionNode;
import jdk.nashorn.internal.ir.Node;
import jdk.nashorn.internal.ir.debug.ASTWriter;
import jdk.nashorn.internal.ir.debug.PrintVisitor;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;
import jdk.nashorn.internal.parser.Parser;
import jdk.nashorn.internal.runtime.CodeInstaller;
import jdk.nashorn.internal.runtime.Context;
import jdk.nashorn.internal.runtime.DebugLogger;
import jdk.nashorn.internal.runtime.ECMAErrors;
import jdk.nashorn.internal.runtime.ErrorManager;
import jdk.nashorn.internal.runtime.Source;
import jdk.nashorn.internal.runtime.options.Options;
import org.dynalang.dynalink.support.NameCodec;

/**
 * Responsible for converting JavaScripts to java byte code. Main entry
 * point for code generator
 */
public final class Compiler {

    /** Compiler states available */
    public enum State {
        /** compiler is ready */
        INITIALIZED,
        /** method has been parsed */
        PARSED,
        /** constant folding pass */
        CONSTANT_FOLDED,
        /** method has been lowered */
        LOWERED,
        /** method hass been attributed */
        ATTR,
        /** method has been split */
        SPLIT,
        /** method has had its types finalized */
        FINALIZED,
        /** method has been emitted to bytecode */
        EMITTED
    }

    /** Current context */
    private final Context context;

    /** Currently compiled source */
    private final Source source;

    /** Current error manager */
    private final ErrorManager errors;

    /** Names uniqueName for this compile. */
    private final Namespace namespace;

    /** Current function node, or null if compiling from source until parsed */
    private FunctionNode functionNode;

    /** Current compiler state */
    private final EnumSet<State> state;

    /** Name of the scripts package */
    public static final String SCRIPTS_PACKAGE = "jdk/nashorn/internal/scripts";

    /** Name of the objects package */
    public static final String OBJECTS_PACKAGE = "jdk/nashorn/internal/objects";

    /** Name of the runtime package */
    public static final String RUNTIME_PACKAGE = "jdk/nashorn/internal/runtime";

    /** Name of the Global object, cannot be referred to as .class, @see CodeGenerator */
    public static final String GLOBAL_OBJECT = OBJECTS_PACKAGE + '/' + "Global";

    /** Name of the ScriptFunctionImpl, cannot be referred to as .class @see FunctionObjectCreator */
    public static final String SCRIPTFUNCTION_IMPL_OBJECT = OBJECTS_PACKAGE + '/' + "ScriptFunctionImpl";

    /** Name of the Trampoline, cannot be referred to as .class @see FunctionObjectCreator */
    public static final String TRAMPOLINE_OBJECT = OBJECTS_PACKAGE + '/' + "Trampoline";

    /** Compile unit (class) table. */
    private final Set<CompileUnit> compileUnits;

    /** All the "complex" constants used in the code. */
    private final ConstantData constantData;

    static final DebugLogger LOG = new DebugLogger("compiler");

    /** Script name */
    private String scriptName;

    /** Should we dump classes to disk and compile only? */
    private final boolean dumpClass;

    /** Code map class name -> byte code for all classes generated from this Source or FunctionNode */
    private Map<String, byte[]> code;

    /** Are we compiling in strict mode? */
    private boolean strict;

    /** Is this a lazy compilation - i.e. not from source, but jitting a previously parsed FunctionNode? */
    private boolean isLazy;

    /** Lazy jitting is disabled by default */
    private static final boolean LAZY_JIT = false;

    /**
     * Should we use integers for arithmetic operations as well?
     * TODO: We currently generate no overflow checks so this is
     * disabled
     *
     * @see #shouldUseIntegers()
     *
     * @return true if arithmetic operations should not widen integer
     *   operands by default.
     */
    static boolean shouldUseIntegerArithmetic() {
        return USE_INT_ARITH;
    }

    private static final boolean USE_INT_ARITH;

    static {
        USE_INT_ARITH  =  Options.getBooleanProperty("nashorn.compiler.intarithmetic");

        assert !USE_INT_ARITH : "Integer arithmetic is not enabled";
    }

    /**
     * Factory method for compiler that should compile from source to bytecode
     *
     * @param source  the source
     * @param context context
     *
     * @return compiler instance
     */
    public static Compiler compiler(final Source source, final Context context) {
        return Compiler.compiler(source, context, context.getErrorManager(), context._strict);
    }

    /**
     * Factory method to get a compiler that goes from from source to bytecode
     *
     * @param source  source code
     * @param context context
     * @param errors  error manager
     * @param strict  compilation in strict mode?
     *
     * @return compiler instance
     */
    public static Compiler compiler(final Source source, final Context context, final ErrorManager errors, final boolean strict) {
        return new Compiler(source, context, errors, strict);
    }

    /**
     * Factory method to get a compiler that goes from FunctionNode (parsed) to bytecode
     * Requires previous compiler for state
     *
     * @param compiler primordial compiler
     * @param functionNode functionNode to compile
     *
     * @return compiler
     */
    public static Compiler compiler(final Compiler compiler, final FunctionNode functionNode) {
        assert false : "lazy jit - not implemented";
        final Compiler newCompiler = new Compiler(compiler);
        newCompiler.state.add(State.PARSED);
        newCompiler.functionNode = functionNode;
        newCompiler.isLazy = true;
        return compiler;
    }

    private Compiler(final Compiler compiler) {
        this(compiler.source, compiler.context, compiler.errors, compiler.strict);
    }

    /**
     * Constructor
     *
     * @param source  the source to compile
     * @param context context
     * @param errors  error manager
     * @param strict  compile in strict mode
     */
    private Compiler(final Source source, final Context context, final ErrorManager errors, final boolean strict) {
        this.source       = source;
        this.context      = context;
        this.errors       = errors;
        this.strict       = strict;
        this.namespace    = new Namespace(context.getNamespace());
        this.compileUnits = new HashSet<>();
        this.constantData = new ConstantData();
        this.state        = EnumSet.of(State.INITIALIZED);
        this.dumpClass    = context._compile_only && context._dest_dir != null;
    }

    private String scriptsPackageName() {
        return dumpClass ? "" : (SCRIPTS_PACKAGE + '/');
    }

    private int nextCompileUnitIndex() {
        return compileUnits.size() + 1;
    }

    private String firstCompileUnitName() {
        return scriptsPackageName() + scriptName;
    }

    private String nextCompileUnitName() {
        return firstCompileUnitName() + '$' + nextCompileUnitIndex();
    }

    private CompileUnit addCompileUnit(final long initialWeight) {
        return addCompileUnit(nextCompileUnitName(), initialWeight);
    }

    private CompileUnit addCompileUnit(final String unitClassName, final long initialWeight) {
        final CompileUnit compileUnit = initCompileUnit(unitClassName, initialWeight);
        compileUnits.add(compileUnit);
        LOG.info("Added compile unit " + compileUnit);
        return compileUnit;
    }

    private CompileUnit initCompileUnit(final String unitClassName, final long initialWeight) {
        final ClassEmitter classEmitter = new ClassEmitter(this, unitClassName, strict);
        final CompileUnit  compileUnit  = new CompileUnit(unitClassName, classEmitter, initialWeight);

        classEmitter.begin();

        final MethodEmitter initMethod = classEmitter.init(EnumSet.of(Flag.PRIVATE));
        initMethod.begin();
        initMethod.load(Type.OBJECT, 0);
        initMethod.newInstance(jdk.nashorn.internal.scripts.JS$.class);
        initMethod.returnVoid();
        initMethod.end();

        return compileUnit;
    }

    /**
     * Perform compilation
     *
     * @return true if successful, false otherwise - if false check the error manager
     */
    public boolean compile() {
        assert state.contains(State.INITIALIZED);

        /** do we need to parse source? */
        if (!state.contains(State.PARSED)) {
            LOG.info("Parsing '" + source + "'");

            assert this.functionNode == null;
            this.functionNode = new Parser(this, strict).parse(RUN_SCRIPT.tag());

            state.add(State.PARSED);
            debugPrintParse();

            if (errors.hasErrors() || context._parse_only) {
                return false;
            }

            assert !isLazy;
            //tag lazy nodes for later code generation and trampolines
            functionNode.accept(new NodeVisitor() {
                @Override
                public Node enter(final FunctionNode node) {
                    if (LAZY_JIT) {
                        node.setIsLazy(!node.isScript());
                    }
                    return node;
                }
            });
        } else {
            assert isLazy;
            functionNode.accept(new NodeVisitor() {
                @Override
                public Node enter(final FunctionNode node) {
                    node.setIsLazy(false);
                    return null; //TODO do we want to do this recursively? then return "node" instead
                }
            });
        }

        assert functionNode != null;
        final boolean oldStrict = strict;

        try {
            strict |= functionNode.isStrictMode();

            /*
             * These are the compile phases:
             *
             * Constant folding pass
             *   Simple constant folding that will make elementary constructs go away
             *
             * Lower (Control flow pass)
             *   Finalizes the control flow. Clones blocks for finally constructs and
             *   similar things. Establishes termination criteria for nodes
             *   Guarantee return instructions to method making sure control flow
             *   cannot fall off the end. Replacing high level nodes with lower such
             *   as runtime nodes where applicable.
             *
             * Attr
             *   Assign symbols and types to all nodes.
             *
             * Splitter
             *   Split the AST into several compile units based on a size heuristic
             *   Splitter needs attributed AST for weight calculations (e.g. is
             *   a + b a ScriptRuntime.ADD with call overhead or a dadd with much
             *   less). Split IR can lead to scope information being changed.
             *
             * Contract: all variables must have slot assignments and scope assignments
             * before lowering.
             *
             * FinalizeTypes
             *   This pass finalizes the types for nodes. If Attr created wider types than
             *   known during the first pass, convert nodes are inserted or access nodes
             *   are specialized where scope accesses.
             *
             *   Runtime nodes may be removed and primitivized or reintroduced depending
             *   on information that was established in Attr.
             *
             * CodeGeneration
             *   Emit bytecode
             *
             */

            debugPrintAST();

            if (!state.contains(State.FINALIZED)) {
                LOG.info("Folding constants in '" + functionNode.getName() + "'");
                functionNode.accept(new FoldConstants());
                state.add(State.CONSTANT_FOLDED);

                LOG.info("Lowering '" + functionNode.getName() + "'");
                functionNode.accept(new Lower(this));
                state.add(State.LOWERED);
                debugPrintAST();

                LOG.info("Attributing types '" + functionNode.getName() + "'");
                functionNode.accept(new Attr(this));
                state.add(State.ATTR);

                this.scriptName = computeNames();

                // Main script code always goes to this compile unit. Note that since we start this with zero weight
                // and add script code last this class may end up slightly larger than others, but reserving one class
                // just for the main script seems wasteful.
                final CompileUnit scriptCompileUnit = addCompileUnit(firstCompileUnitName(), 0L);
                LOG.info("Splitting '" + functionNode.getName() + "'");
                new Splitter(this, functionNode, scriptCompileUnit).split();
                state.add(State.SPLIT);
                assert functionNode.getCompileUnit() == scriptCompileUnit;

                assert strict == functionNode.isStrictMode() : "strict == " + strict + " but functionNode == " + functionNode.isStrictMode();
                if (functionNode.isStrictMode()) {
                    strict = true;
                }

                LOG.info("Finalizing types for '" + functionNode.getName() + "'");
                functionNode.accept(new FinalizeTypes(this));
                state.add(State.FINALIZED);

                // print ast and parse if --print-lower-ast and/or --print-lower-parse are selected
                debugPrintAST();
                debugPrintParse();

                if (errors.hasErrors()) {
                    return false;
                }
            }

            try {
                LOG.info("Emitting bytecode for '" + functionNode.getName() + "'");
                final CodeGenerator codegen = new CodeGenerator(this);
                functionNode.accept(codegen);
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

            state.add(State.EMITTED);

            code = new TreeMap<>();
            for (final CompileUnit compileUnit : compileUnits) {
                final ClassEmitter classEmitter = compileUnit.getClassEmitter();
                classEmitter.end();

                if (!errors.hasErrors()) {
                    final byte[] bytecode = classEmitter.toByteArray();
                    if (bytecode != null) {
                        code.put(compileUnit.getUnitClassName(), bytecode);
                        debugDisassemble();
                        debugVerify();
                    }
               }
            }

            if (code.isEmpty()) {
                return false;
            }

            try {
                dumpClassFiles();
            } catch (final IOException e) {
                throw new RuntimeException(e);
            }

            return true;
        } finally {
            strict = oldStrict;
            LOG.info("Done with '" + functionNode.getName() + "'");
        }
    }

    /**
     * Install compiled classes into a given loader
     * @param installer that takes the generated classes and puts them in the system
     * @return root script class - if there are several compile units they will also be installed
     */
    public Class<?> install(final CodeInstaller installer) {
        assert state.contains(State.EMITTED);
        assert scriptName != null;

        Class<?> rootClass = null;

        for (final Entry<String, byte[]> entry : code.entrySet()) {
            final String     className = entry.getKey();
            LOG.info("Installing class " + className);

            final byte[]     bytecode  = entry.getValue();
            final Class<?>   clazz     = installer.install(Compiler.binaryName(className), bytecode);

            if (rootClass == null && firstCompileUnitName().equals(className)) {
                rootClass = clazz;
            }

            try {
                //use reflection to write source and constants table to installed classes
                clazz.getField(SOURCE.tag()).set(null, source);
                clazz.getField(CONSTANTS.tag()).set(null, constantData.toArray());
            } catch (final NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }

        LOG.info("Root class: " + rootClass);

        return rootClass;
    }

    /**
     * Find a unit that will hold a node of the specified weight.
     *
     * @param weight Weight of a node
     * @return Unit to hold node.
     */
    CompileUnit findUnit(final long weight) {
        for (final CompileUnit unit : compileUnits) {
            if (unit.canHold(weight)) {
                unit.addWeight(weight);
                return unit;
            }
        }

        return addCompileUnit(weight);
    }

    /**
     * Generate a uniqueName name. Public as {@link Parser} is using this to
     * create symbols in a different package
     *
     * @param  name to base unique name on
     * @return unique name
     */
    public String uniqueName(final String name) {
        return namespace.uniqueName(name);
    }

    /**
     * Internal function to compute reserved names and base names for class to
     * be generated
     *
     * @return scriptName
     */
    private String computeNames() {
        // Reserve internally used names.
        addReservedNames();

        if (dumpClass) {
            // get source file name and remove ".js"
            final String baseName = getSource().getName();
            final int    index    = baseName.lastIndexOf(".js");
            if (index != -1) {
                return baseName.substring(0, index);
            }
            return baseName;
        }

        return namespace.getParent().uniqueName(
            DEFAULT_SCRIPT_NAME.tag() +
            '$' +
            safeSourceName(source) +
            (isLazy ? CompilerConstants.LAZY.tag() : "")
        );
    }

    private static String safeSourceName(final Source source) {
        String baseName = new File(source.getName()).getName();
        final int index = baseName.lastIndexOf(".js");
        if (index != -1) {
            baseName = baseName.substring(0, index);
        }

        baseName = baseName.replace('.', '_').replace('-', '_');
        final String mangled = NameCodec.encode(baseName);

        baseName = mangled != null ? mangled : baseName;
        return baseName;
    }

    static void verify(final Context context, final byte[] code) {
        context.verify(code);
    }

    /**
     * Fill in the namespace with internally reserved names.
     */
    private void addReservedNames() {
        namespace.uniqueName(SCOPE.tag());
        namespace.uniqueName(THIS.tag());
    }

    /**
     * Get the constant data for this Compiler
     *
     * @return the constant data
     */
    public ConstantData getConstantData() {
        return constantData;
    }

    /**
     * Get the Context used for Compilation
     * @see Context
     * @return the context
     */
    public Context getContext() {
        return context;
    }

    /**
     * Get the Source being compiled
     * @see Source
     * @return the source
     */
    public Source getSource() {
        return source;
    }

    /**
     * Get the error manager used for this compiler
     * @return the error manager
     */
    public ErrorManager getErrors() {
        return errors;
    }

    /**
     * Get the namespace used for this Compiler
     * @see Namespace
     * @return the namespace
     */
    public Namespace getNamespace() {
        return namespace;
    }

    /*
     * Debugging
     */

    /**
     * Print the AST before or after lowering, see --print-ast, --print-lower-ast
     */
    private void debugPrintAST() {
        assert functionNode != null;
        if (context._print_lower_ast && state.contains(State.LOWERED) ||
            context._print_ast && !state.contains(State.LOWERED)) {
            context.getErr().println(new ASTWriter(functionNode));
        }
    }

    /**
     * Print the parsed code before or after lowering, see --print-parse, --print-lower-parse
     */
    private boolean debugPrintParse() {
        if (errors.hasErrors()) {
            return false;
        }

        assert functionNode != null;

        if (context._print_lower_parse && state.contains(State.LOWERED) ||
             context._print_parse && !state.contains(State.LOWERED)) {
            final PrintVisitor pv = new PrintVisitor();
            functionNode.accept(pv);
            context.getErr().print(pv);
            context.getErr().flush();
        }

        return true;
    }

    private void debugDisassemble() {
        assert code != null;
        if (context._print_code) {
            for (final Map.Entry<String, byte[]> entry : code.entrySet()) {
                context.getErr().println("CLASS: " + entry.getKey());
                context.getErr().println();
                ClassEmitter.disassemble(context, entry.getValue());
                context.getErr().println("======");
            }
        }
    }

    private void debugVerify() {
        if (context._verify_code) {
            for (final Map.Entry<String, byte[]> entry : code.entrySet()) {
                Compiler.verify(context, entry.getValue());
            }
        }
    }

    /**
     * Implements the "-d" option - dump class files from script to specified output directory
     *
     * @throws IOException if classes cannot be written
     */
    private void dumpClassFiles() throws IOException {
        if (context._dest_dir == null) {
            return;
        }

        assert code != null;

        for (final Entry<String, byte[]> entry : code.entrySet()) {
            final String className = entry.getKey();
            final String fileName  = className.replace('.', File.separatorChar) + ".class";
            final int    index     = fileName.lastIndexOf(File.separatorChar);

            if (index != -1) {
                final File dir = new File(fileName.substring(0, index));
                if (!dir.exists() && !dir.mkdirs()) {
                    throw new IOException(ECMAErrors.getMessage("io.error.cant.write", dir.toString()));
                }
            }

            final byte[] bytecode = entry.getValue();
            final File outFile = new File(context._dest_dir, fileName);
            try (final FileOutputStream fos = new FileOutputStream(outFile)) {
                fos.write(bytecode);
            }
        }
    }

    /**
     * Convert a package/class name to a binary name.
     *
     * @param name Package/class name.
     * @return Binary name.
     */
    public static String binaryName(final String name) {
        return name.replace('/', '.');
    }

    /**
     * Convert a binary name to a package/class name.
     *
     * @param name Binary name.
     * @return Package/class name.
     */
    public static String pathName(final String name) {
        return name.replace('.', '/');
    }


}
