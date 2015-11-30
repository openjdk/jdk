/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac.comp;

import com.sun.source.tree.LambdaExpressionTree;
import com.sun.tools.javac.code.Source;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.comp.ArgumentAttr.LocalCacheContext;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCBlock;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCDoWhileLoop;
import com.sun.tools.javac.tree.JCTree.JCEnhancedForLoop;
import com.sun.tools.javac.tree.JCTree.JCForLoop;
import com.sun.tools.javac.tree.JCTree.JCIf;
import com.sun.tools.javac.tree.JCTree.JCLambda;
import com.sun.tools.javac.tree.JCTree.JCLambda.ParameterKind;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCMethodInvocation;
import com.sun.tools.javac.tree.JCTree.JCNewClass;
import com.sun.tools.javac.tree.JCTree.JCStatement;
import com.sun.tools.javac.tree.JCTree.JCSwitch;
import com.sun.tools.javac.tree.JCTree.JCTypeApply;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.tree.JCTree.JCWhileLoop;
import com.sun.tools.javac.tree.JCTree.Tag;
import com.sun.tools.javac.tree.TreeCopier;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.DefinedBy;
import com.sun.tools.javac.util.DefinedBy.Api;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticType;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Names;
import com.sun.tools.javac.util.Options;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

import static com.sun.tools.javac.code.Flags.GENERATEDCONSTR;
import static com.sun.tools.javac.code.Flags.SYNTHETIC;
import static com.sun.tools.javac.code.TypeTag.CLASS;
import static com.sun.tools.javac.tree.JCTree.Tag.APPLY;
import static com.sun.tools.javac.tree.JCTree.Tag.METHODDEF;
import static com.sun.tools.javac.tree.JCTree.Tag.NEWCLASS;
import static com.sun.tools.javac.tree.JCTree.Tag.TYPEAPPLY;

/**
 * Helper class for defining custom code analysis, such as finding instance creation expression
 * that can benefit from diamond syntax.
 */
public class Analyzer {
    protected static final Context.Key<Analyzer> analyzerKey = new Context.Key<>();

    final Types types;
    final Log log;
    final Attr attr;
    final DeferredAttr deferredAttr;
    final ArgumentAttr argumentAttr;
    final TreeMaker make;
    final Names names;
    private final boolean allowDiamondWithAnonymousClassCreation;

    final EnumSet<AnalyzerMode> analyzerModes;

    public static Analyzer instance(Context context) {
        Analyzer instance = context.get(analyzerKey);
        if (instance == null)
            instance = new Analyzer(context);
        return instance;
    }

    protected Analyzer(Context context) {
        context.put(analyzerKey, this);
        types = Types.instance(context);
        log = Log.instance(context);
        attr = Attr.instance(context);
        deferredAttr = DeferredAttr.instance(context);
        argumentAttr = ArgumentAttr.instance(context);
        make = TreeMaker.instance(context);
        names = Names.instance(context);
        Options options = Options.instance(context);
        String findOpt = options.get("find");
        //parse modes
        Source source = Source.instance(context);
        allowDiamondWithAnonymousClassCreation = source.allowDiamondWithAnonymousClassCreation();
        analyzerModes = AnalyzerMode.getAnalyzerModes(findOpt, source);
    }

    /**
     * This enum defines supported analyzer modes, as well as defining the logic for decoding
     * the {@code -XDfind} option.
     */
    enum AnalyzerMode {
        DIAMOND("diamond", Source::allowDiamond),
        LAMBDA("lambda", Source::allowLambda),
        METHOD("method", Source::allowGraphInference);

        final String opt;
        final Predicate<Source> sourceFilter;

        AnalyzerMode(String opt, Predicate<Source> sourceFilter) {
            this.opt = opt;
            this.sourceFilter = sourceFilter;
        }

        /**
         * This method is used to parse the {@code find} option.
         * Possible modes are separated by colon; a mode can be excluded by
         * prepending '-' to its name. Finally, the special mode 'all' can be used to
         * add all modes to the resulting enum.
         */
        static EnumSet<AnalyzerMode> getAnalyzerModes(String opt, Source source) {
            if (opt == null) {
                return EnumSet.noneOf(AnalyzerMode.class);
            }
            List<String> modes = List.from(opt.split(","));
            EnumSet<AnalyzerMode> res = EnumSet.noneOf(AnalyzerMode.class);
            if (modes.contains("all")) {
                res = EnumSet.allOf(AnalyzerMode.class);
            }
            for (AnalyzerMode mode : values()) {
                if (modes.contains(mode.opt)) {
                    res.add(mode);
                } else if (modes.contains("-" + mode.opt) || !mode.sourceFilter.test(source)) {
                    res.remove(mode);
                }
            }
            return res;
        }
    }

    /**
     * A statement analyzer is a work-unit that matches certain AST nodes (of given type {@code S}),
     * rewrites them to different AST nodes (of type {@code T}) and then generates some meaningful
     * messages in case the analysis has been successful.
     */
    abstract class StatementAnalyzer<S extends JCTree, T extends JCTree> {

        AnalyzerMode mode;
        JCTree.Tag tag;

        StatementAnalyzer(AnalyzerMode mode, Tag tag) {
            this.mode = mode;
            this.tag = tag;
        }

        /**
         * Is this analyzer allowed to run?
         */
        boolean isEnabled() {
            return analyzerModes.contains(mode);
        }

        /**
         * Should this analyzer be rewriting the given tree?
         */
        abstract boolean match(S tree);

        /**
         * Rewrite a given AST node into a new one
         */
        abstract T map(S oldTree, S newTree);

        /**
         * Entry-point for comparing results and generating diagnostics.
         */
        abstract void process(S oldTree, T newTree, boolean hasErrors);

    }

    /**
     * This analyzer checks if generic instance creation expression can use diamond syntax.
     */
    class DiamondInitializer extends StatementAnalyzer<JCNewClass, JCNewClass> {

        DiamondInitializer() {
            super(AnalyzerMode.DIAMOND, NEWCLASS);
        }

        @Override
        boolean match(JCNewClass tree) {
            return tree.clazz.hasTag(TYPEAPPLY) &&
                    !TreeInfo.isDiamond(tree) &&
                    (tree.def == null || allowDiamondWithAnonymousClassCreation);
        }

        @Override
        JCNewClass map(JCNewClass oldTree, JCNewClass newTree) {
            if (newTree.clazz.hasTag(TYPEAPPLY)) {
                ((JCTypeApply)newTree.clazz).arguments = List.nil();
            }
            return newTree;
        }

        @Override
        void process(JCNewClass oldTree, JCNewClass newTree, boolean hasErrors) {
            if (!hasErrors) {
                List<Type> inferredArgs, explicitArgs;
                if (oldTree.def != null) {
                    inferredArgs = newTree.def.implementing.nonEmpty()
                                      ? newTree.def.implementing.get(0).type.getTypeArguments()
                                      : newTree.def.extending.type.getTypeArguments();
                    explicitArgs = oldTree.def.implementing.nonEmpty()
                                      ? oldTree.def.implementing.get(0).type.getTypeArguments()
                                      : oldTree.def.extending.type.getTypeArguments();
                } else {
                    inferredArgs = newTree.type.getTypeArguments();
                    explicitArgs = oldTree.type.getTypeArguments();
                }
                for (Type t : inferredArgs) {
                    if (!types.isSameType(t, explicitArgs.head)) {
                        return;
                    }
                    explicitArgs = explicitArgs.tail;
                }
                //exact match
                log.warning(oldTree.clazz, "diamond.redundant.args");
            }
        }
    }

    /**
     * This analyzer checks if anonymous instance creation expression can replaced by lambda.
     */
    class LambdaAnalyzer extends StatementAnalyzer<JCNewClass, JCLambda> {

        LambdaAnalyzer() {
            super(AnalyzerMode.LAMBDA, NEWCLASS);
        }

        @Override
        boolean match (JCNewClass tree){
            Type clazztype = tree.clazz.type;
            return tree.def != null &&
                    clazztype.hasTag(CLASS) &&
                    types.isFunctionalInterface(clazztype.tsym) &&
                    decls(tree.def).length() == 1;
        }
        //where
            private List<JCTree> decls(JCClassDecl decl) {
                ListBuffer<JCTree> decls = new ListBuffer<>();
                for (JCTree t : decl.defs) {
                    if (t.hasTag(METHODDEF)) {
                        JCMethodDecl md = (JCMethodDecl)t;
                        if ((md.getModifiers().flags & GENERATEDCONSTR) == 0) {
                            decls.add(md);
                        }
                    } else {
                        decls.add(t);
                    }
                }
                return decls.toList();
            }

        @Override
        JCLambda map (JCNewClass oldTree, JCNewClass newTree){
            JCMethodDecl md = (JCMethodDecl)decls(newTree.def).head;
            List<JCVariableDecl> params = md.params;
            JCBlock body = md.body;
            return make.Lambda(params, body);
        }
        @Override
        void process (JCNewClass oldTree, JCLambda newTree, boolean hasErrors){
            if (!hasErrors) {
                log.warning(oldTree.def, "potential.lambda.found");
            }
        }
    }

    /**
     * This analyzer checks if generic method call has redundant type arguments.
     */
    class RedundantTypeArgAnalyzer extends StatementAnalyzer<JCMethodInvocation, JCMethodInvocation> {

        RedundantTypeArgAnalyzer() {
            super(AnalyzerMode.METHOD, APPLY);
        }

        @Override
        boolean match (JCMethodInvocation tree){
            return tree.typeargs != null &&
                    tree.typeargs.nonEmpty();
        }
        @Override
        JCMethodInvocation map (JCMethodInvocation oldTree, JCMethodInvocation newTree){
            newTree.typeargs = List.nil();
            return newTree;
        }
        @Override
        void process (JCMethodInvocation oldTree, JCMethodInvocation newTree, boolean hasErrors){
            if (!hasErrors) {
                //exact match
                log.warning(oldTree, "method.redundant.typeargs");
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    StatementAnalyzer<JCTree, JCTree>[] analyzers = new StatementAnalyzer[] {
            new DiamondInitializer(),
            new LambdaAnalyzer(),
            new RedundantTypeArgAnalyzer()
    };

    /**
     * Analyze an AST node if needed.
     */
    void analyzeIfNeeded(JCTree tree, Env<AttrContext> env) {
        if (!analyzerModes.isEmpty() &&
                !env.info.isSpeculative &&
                TreeInfo.isStatement(tree)) {
            JCStatement stmt = (JCStatement)tree;
            analyze(stmt, env);
        }
    }

    /**
     * Analyze an AST node; this involves collecting a list of all the nodes that needs rewriting,
     * and speculatively type-check the rewritten code to compare results against previously attributed code.
     */
    void analyze(JCStatement statement, Env<AttrContext> env) {
        AnalysisContext context = new AnalysisContext();
        StatementScanner statementScanner = new StatementScanner(context);
        statementScanner.scan(statement);

        if (!context.treesToAnalyzer.isEmpty()) {

            //add a block to hoist potential dangling variable declarations
            JCBlock fakeBlock = make.Block(SYNTHETIC, List.of(statement));

            TreeMapper treeMapper = new TreeMapper(context);
            //TODO: to further refine the analysis, try all rewriting combinations
            LocalCacheContext localCacheContext = argumentAttr.withLocalCacheContext();
            try {
                deferredAttr.attribSpeculative(fakeBlock, env, attr.statInfo, treeMapper,
                        t -> new AnalyzeDeferredDiagHandler(context));
            } finally {
                localCacheContext.leave();
            }

            context.treeMap.entrySet().forEach(e -> {
                context.treesToAnalyzer.get(e.getKey())
                        .process(e.getKey(), e.getValue(), context.errors.nonEmpty());
            });
        }
    }

    /**
     * Simple deferred diagnostic handler which filters out all messages and keep track of errors.
     */
    class AnalyzeDeferredDiagHandler extends Log.DeferredDiagnosticHandler {
        AnalysisContext context;

        public AnalyzeDeferredDiagHandler(AnalysisContext context) {
            super(log, d -> {
                if (d.getType() == DiagnosticType.ERROR) {
                    context.errors.add(d);
                }
                return true;
            });
            this.context = context;
        }
    }

    /**
     * This class is used to pass around contextual information bewteen analyzer classes, such as
     * trees to be rewritten, errors occurred during the speculative attribution step, etc.
     */
    class AnalysisContext {
        /** Map from trees to analyzers. */
        Map<JCTree, StatementAnalyzer<JCTree, JCTree>> treesToAnalyzer = new HashMap<>();

        /** Map from original AST nodes to rewritten AST nodes */
        Map<JCTree, JCTree> treeMap = new HashMap<>();

        /** Errors in rewritten tree */
        ListBuffer<JCDiagnostic> errors = new ListBuffer<>();
    }

    /**
     * Subclass of {@link com.sun.tools.javac.tree.TreeScanner} which visit AST-nodes w/o crossing
     * statement boundaries.
     */
    class StatementScanner extends TreeScanner {

        /** context */
        AnalysisContext context;

        StatementScanner(AnalysisContext context) {
            this.context = context;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void scan(JCTree tree) {
            if (tree != null) {
                for (StatementAnalyzer<JCTree, JCTree> analyzer : analyzers) {
                    if (analyzer.isEnabled() &&
                            tree.hasTag(analyzer.tag) &&
                            analyzer.match(tree)) {
                        context.treesToAnalyzer.put(tree, analyzer);
                        break; //TODO: cover cases where multiple matching analyzers are found
                    }
                }
            }
            super.scan(tree);
        }

        @Override
        public void visitClassDef(JCClassDecl tree) {
            //do nothing (prevents seeing same stuff twice
        }

        @Override
        public void visitMethodDef(JCMethodDecl tree) {
            //do nothing (prevents seeing same stuff twice
        }

        @Override
        public void visitBlock(JCBlock tree) {
            //do nothing (prevents seeing same stuff twice
        }

        @Override
        public void visitSwitch(JCSwitch tree) {
            scan(tree.getExpression());
        }

        @Override
        public void visitForLoop(JCForLoop tree) {
            scan(tree.getInitializer());
            scan(tree.getCondition());
            scan(tree.getUpdate());
        }

        @Override
        public void visitForeachLoop(JCEnhancedForLoop tree) {
            scan(tree.getExpression());
        }

        @Override
        public void visitWhileLoop(JCWhileLoop tree) {
            scan(tree.getCondition());
        }

        @Override
        public void visitDoLoop(JCDoWhileLoop tree) {
            scan(tree.getCondition());
        }

        @Override
        public void visitIf(JCIf tree) {
            scan(tree.getCondition());
        }
    }

    /**
     * Subclass of TreeCopier that maps nodes matched by analyzers onto new AST nodes.
     */
    class TreeMapper extends TreeCopier<Void> {

        AnalysisContext context;

        TreeMapper(AnalysisContext context) {
            super(make);
            this.context = context;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <Z extends JCTree> Z copy(Z tree, Void _unused) {
            Z newTree = super.copy(tree, _unused);
            StatementAnalyzer<JCTree, JCTree> analyzer = context.treesToAnalyzer.get(tree);
            if (analyzer != null) {
                newTree = (Z)analyzer.map(tree, newTree);
                context.treeMap.put(tree, newTree);
            }
            return newTree;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public JCTree visitLambdaExpression(LambdaExpressionTree node, Void _unused) {
            JCLambda oldLambda = (JCLambda)node;
            JCLambda newLambda = (JCLambda)super.visitLambdaExpression(node, _unused);
            if (oldLambda.paramKind == ParameterKind.IMPLICIT) {
                //reset implicit lambda parameters (whose type might have been set during attr)
                newLambda.paramKind = ParameterKind.IMPLICIT;
                newLambda.params.forEach(p -> p.vartype = null);
            }
            return newLambda;
        }
    }
}
