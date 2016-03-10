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

package jdk.jshell;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.lang.model.element.Modifier;
import com.sun.source.tree.ArrayTypeTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ModifiersTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.Pretty;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.LinkedHashSet;
import java.util.Set;
import jdk.jshell.ClassTracker.ClassInfo;
import jdk.jshell.Key.ErroneousKey;
import jdk.jshell.Key.MethodKey;
import jdk.jshell.Key.TypeDeclKey;
import jdk.jshell.Snippet.SubKind;
import jdk.jshell.TaskFactory.AnalyzeTask;
import jdk.jshell.TaskFactory.BaseTask;
import jdk.jshell.TaskFactory.CompileTask;
import jdk.jshell.TaskFactory.ParseTask;
import jdk.jshell.TreeDissector.ExpressionInfo;
import jdk.jshell.Wrap.Range;
import jdk.jshell.Snippet.Status;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static jdk.internal.jshell.debug.InternalDebugControl.DBG_GEN;
import static jdk.jshell.Util.*;
import static jdk.internal.jshell.remote.RemoteCodes.DOIT_METHOD_NAME;
import static jdk.internal.jshell.remote.RemoteCodes.prefixPattern;
import static jdk.jshell.Snippet.SubKind.SINGLE_TYPE_IMPORT_SUBKIND;
import static jdk.jshell.Snippet.SubKind.SINGLE_STATIC_IMPORT_SUBKIND;
import static jdk.jshell.Snippet.SubKind.TYPE_IMPORT_ON_DEMAND_SUBKIND;
import static jdk.jshell.Snippet.SubKind.STATIC_IMPORT_ON_DEMAND_SUBKIND;

/**
 * The Evaluation Engine. Source internal analysis, wrapping control,
 * compilation, declaration. redefinition, replacement, and execution.
 *
 * @author Robert Field
 */
class Eval {

    private static final Pattern IMPORT_PATTERN = Pattern.compile("import\\p{javaWhitespace}+(?<static>static\\p{javaWhitespace}+)?(?<fullname>[\\p{L}\\p{N}_\\$\\.]+\\.(?<name>[\\p{L}\\p{N}_\\$]+|\\*))");

    private int varNumber = 0;

    private final JShell state;

    Eval(JShell state) {
        this.state = state;
    }

    List<SnippetEvent> eval(String userSource) throws IllegalStateException {
        String compileSource = Util.trimEnd(new MaskCommentsAndModifiers(userSource, false).cleared());
        if (compileSource.length() == 0) {
            return Collections.emptyList();
        }
        // String folding messes up position information.
        ParseTask pt = state.taskFactory.new ParseTask(compileSource);
        if (pt.getDiagnostics().hasOtherThanNotStatementErrors()) {
            return compileFailResult(pt, userSource);
        }

        List<? extends Tree> units = pt.units();
        if (units.isEmpty()) {
            return compileFailResult(pt, userSource);
        }
        // Erase illegal modifiers
        compileSource = new MaskCommentsAndModifiers(compileSource, true).cleared();
        Tree unitTree = units.get(0);
        state.debug(DBG_GEN, "Kind: %s -- %s\n", unitTree.getKind(), unitTree);
        switch (unitTree.getKind()) {
            case IMPORT:
                return processImport(userSource, compileSource);
            case VARIABLE:
                return processVariables(userSource, units, compileSource, pt);
            case EXPRESSION_STATEMENT:
                return processExpression(userSource, compileSource);
            case CLASS:
                return processClass(userSource, unitTree, compileSource, SubKind.CLASS_SUBKIND, pt);
            case ENUM:
                return processClass(userSource, unitTree, compileSource, SubKind.ENUM_SUBKIND, pt);
            case ANNOTATION_TYPE:
                return processClass(userSource, unitTree, compileSource, SubKind.ANNOTATION_TYPE_SUBKIND, pt);
            case INTERFACE:
                return processClass(userSource, unitTree, compileSource, SubKind.INTERFACE_SUBKIND, pt);
            case METHOD:
                return processMethod(userSource, unitTree, compileSource, pt);
            default:
                return processStatement(userSource, compileSource);
        }
    }

    private List<SnippetEvent> processImport(String userSource, String compileSource) {
        Wrap guts = Wrap.importWrap(compileSource);
        Matcher mat = IMPORT_PATTERN.matcher(compileSource);
        String fullname;
        String name;
        boolean isStatic;
        if (mat.find()) {
            isStatic = mat.group("static") != null;
            name = mat.group("name");
            fullname = mat.group("fullname");
        } else {
            // bad import -- fake it
            isStatic = compileSource.contains("static");
            name = fullname = compileSource;
        }
        String fullkey = (isStatic ? "static-" : "") + fullname;
        boolean isStar = name.equals("*");
        String keyName = isStar
                ? fullname
                : name;
        SubKind snippetKind = isStar
                ? (isStatic ? STATIC_IMPORT_ON_DEMAND_SUBKIND : TYPE_IMPORT_ON_DEMAND_SUBKIND)
                : (isStatic ? SINGLE_STATIC_IMPORT_SUBKIND : SINGLE_TYPE_IMPORT_SUBKIND);
        Snippet snip = new ImportSnippet(state.keyMap.keyForImport(keyName, snippetKind),
                userSource, guts, fullname, name, snippetKind, fullkey, isStatic, isStar);
        return declare(snip);
    }

    private static class EvalPretty extends Pretty {

        private final Writer out;

        public EvalPretty(Writer writer, boolean bln) {
            super(writer, bln);
            this.out = writer;
        }

        /**
         * Print string, DO NOT replacing all non-ascii character with unicode
         * escapes.
         */
        @Override
        public void print(Object o) throws IOException {
            out.write(o.toString());
        }

        static String prettyExpr(JCTree tree, boolean bln) {
            StringWriter out = new StringWriter();
            try {
                new EvalPretty(out, bln).printExpr(tree);
            } catch (IOException e) {
                throw new AssertionError(e);
            }
            return out.toString();
        }
    }

    private List<SnippetEvent> processVariables(String userSource, List<? extends Tree> units, String compileSource, ParseTask pt) {
        List<SnippetEvent> allEvents = new ArrayList<>();
        TreeDissector dis = TreeDissector.createByFirstClass(pt);
        for (Tree unitTree : units) {
            VariableTree vt = (VariableTree) unitTree;
            String name = vt.getName().toString();
            String typeName = EvalPretty.prettyExpr((JCTree) vt.getType(), false);
            Tree baseType = vt.getType();
            TreeDependencyScanner tds = new TreeDependencyScanner();
            tds.scan(baseType); // Not dependent on initializer
            StringBuilder sbBrackets = new StringBuilder();
            while (baseType instanceof ArrayTypeTree) {
                //TODO handle annotations too
                baseType = ((ArrayTypeTree) baseType).getType();
                sbBrackets.append("[]");
            }
            Range rtype = dis.treeToRange(baseType);
            Range runit = dis.treeToRange(vt);
            runit = new Range(runit.begin, runit.end - 1);
            ExpressionTree it = vt.getInitializer();
            Range rinit = null;
            int nameMax = runit.end - 1;
            SubKind subkind;
            if (it != null) {
                subkind = SubKind.VAR_DECLARATION_WITH_INITIALIZER_SUBKIND;
                rinit = dis.treeToRange(it);
                nameMax = rinit.begin - 1;
            } else {
                subkind = SubKind.VAR_DECLARATION_SUBKIND;
            }
            int nameStart = compileSource.lastIndexOf(name, nameMax);
            if (nameStart < 0) {
                throw new AssertionError("Name '" + name + "' not found");
            }
            int nameEnd = nameStart + name.length();
            Range rname = new Range(nameStart, nameEnd);
            Wrap guts = Wrap.varWrap(compileSource, rtype, sbBrackets.toString(), rname, rinit);
            Snippet snip = new VarSnippet(state.keyMap.keyForVariable(name), userSource, guts,
                    name, subkind, typeName,
                    tds.declareReferences());
            DiagList modDiag = modifierDiagnostics(vt.getModifiers(), dis, true);
            List<SnippetEvent> res1 = declare(snip, modDiag);
            allEvents.addAll(res1);
        }

        return allEvents;
    }

    private List<SnippetEvent> processExpression(String userSource, String compileSource) {
        String name = null;
        ExpressionInfo ei = typeOfExpression(compileSource);
        ExpressionTree assignVar;
        Wrap guts;
        Snippet snip;
        if (ei != null && ei.isNonVoid) {
            String typeName = ei.typeName;
            SubKind subkind;
            if (ei.tree instanceof IdentifierTree) {
                IdentifierTree id = (IdentifierTree) ei.tree;
                name = id.getName().toString();
                subkind = SubKind.VAR_VALUE_SUBKIND;

            } else if (ei.tree instanceof AssignmentTree
                    && (assignVar = ((AssignmentTree) ei.tree).getVariable()) instanceof IdentifierTree) {
                name = assignVar.toString();
                subkind = SubKind.ASSIGNMENT_SUBKIND;
            } else {
                subkind = SubKind.OTHER_EXPRESSION_SUBKIND;
            }
            if (shouldGenTempVar(subkind)) {
                if (state.tempVariableNameGenerator != null) {
                    name = state.tempVariableNameGenerator.get();
                }
                while (name == null || state.keyMap.doesVariableNameExist(name)) {
                    name = "$" + ++varNumber;
                }
                guts = Wrap.tempVarWrap(compileSource, typeName, name);
                Collection<String> declareReferences = null; //TODO
                snip = new VarSnippet(state.keyMap.keyForVariable(name), userSource, guts,
                        name, SubKind.TEMP_VAR_EXPRESSION_SUBKIND, typeName, declareReferences);
            } else {
                guts = Wrap.methodReturnWrap(compileSource);
                snip = new ExpressionSnippet(state.keyMap.keyForExpression(name, typeName), userSource, guts,
                        name, subkind);
            }
        } else {
            guts = Wrap.methodWrap(compileSource);
            if (ei == null) {
                // We got no type info, check for not a statement by trying
                AnalyzeTask at = trialCompile(guts);
                if (at.getDiagnostics().hasNotStatement()) {
                    guts = Wrap.methodReturnWrap(compileSource);
                    at = trialCompile(guts);
                }
                if (at.hasErrors()) {
                    return compileFailResult(at, userSource);
                }
            }
            snip = new StatementSnippet(state.keyMap.keyForStatement(), userSource, guts);
        }
        return declare(snip);
    }

    private List<SnippetEvent> processClass(String userSource, Tree unitTree, String compileSource, SubKind snippetKind, ParseTask pt) {
        TreeDependencyScanner tds = new TreeDependencyScanner();
        tds.scan(unitTree);

        TreeDissector dis = TreeDissector.createByFirstClass(pt);

        ClassTree klassTree = (ClassTree) unitTree;
        String name = klassTree.getSimpleName().toString();
        Wrap guts = Wrap.classMemberWrap(compileSource);
        TypeDeclKey key = state.keyMap.keyForClass(name);
        Wrap corralled = new Corraller(key.index(), compileSource, dis).corralType(klassTree, 1);
        Snippet snip = new TypeDeclSnippet(state.keyMap.keyForClass(name), userSource, guts,
                name, snippetKind,
                corralled, tds.declareReferences(), tds.bodyReferences());
        DiagList modDiag = modifierDiagnostics(klassTree.getModifiers(), dis, false);
        return declare(snip, modDiag);
    }

    private List<SnippetEvent> processStatement(String userSource, String compileSource) {
        Wrap guts = Wrap.methodWrap(compileSource);
        // Check for unreachable by trying
        AnalyzeTask at = trialCompile(guts);
        if (at.hasErrors()) {
            if (at.getDiagnostics().hasUnreachableError()) {
                guts = Wrap.methodUnreachableSemiWrap(compileSource);
                at = trialCompile(guts);
                if (at.hasErrors()) {
                    if (at.getDiagnostics().hasUnreachableError()) {
                        // Without ending semicolon
                        guts = Wrap.methodUnreachableWrap(compileSource);
                        at = trialCompile(guts);
                    }
                    if (at.hasErrors()) {
                        return compileFailResult(at, userSource);
                    }
                }
            } else {
                return compileFailResult(at, userSource);
            }
        }
        Snippet snip = new StatementSnippet(state.keyMap.keyForStatement(), userSource, guts);
        return declare(snip);
    }

    private OuterWrap wrapInClass(String className, Set<Key> except, String userSource, Wrap guts, Collection<Snippet> plus) {
        String imports = state.maps.packageAndImportsExcept(except, plus);
        return OuterWrap.wrapInClass(state.maps.packageName(), className, imports, userSource, guts);
    }

    OuterWrap wrapInClass(Snippet snip, Set<Key> except, Wrap guts, Collection<Snippet> plus) {
        return wrapInClass(snip.className(), except, snip.source(), guts, plus);
    }

    private AnalyzeTask trialCompile(Wrap guts) {
        OuterWrap outer = wrapInClass(REPL_DOESNOTMATTER_CLASS_NAME,
                Collections.emptySet(), "", guts, null);
        return state.taskFactory.new AnalyzeTask(outer);
    }

    private List<SnippetEvent> processMethod(String userSource, Tree unitTree, String compileSource, ParseTask pt) {
        TreeDependencyScanner tds = new TreeDependencyScanner();
        tds.scan(unitTree);

        MethodTree mt = (MethodTree) unitTree;
        TreeDissector dis = TreeDissector.createByFirstClass(pt);
        DiagList modDiag = modifierDiagnostics(mt.getModifiers(), dis, true);
        if (modDiag.hasErrors()) {
            return compileFailResult(modDiag, userSource);
        }
        String unitName = mt.getName().toString();
        Wrap guts = Wrap.classMemberWrap(compileSource);

        Range typeRange = dis.treeToRange(mt.getReturnType());
        String name = mt.getName().toString();

        String parameterTypes
                = mt.getParameters()
                .stream()
                .map(param -> dis.treeToRange(param.getType()).part(compileSource))
                .collect(Collectors.joining(","));
        String signature = "(" + parameterTypes + ")" + typeRange.part(compileSource);

        MethodKey key = state.keyMap.keyForMethod(name, parameterTypes);
        // rewrap with correct Key index
        Wrap corralled = new Corraller(key.index(), compileSource, dis).corralMethod(mt);
        Snippet snip = new MethodSnippet(key, userSource, guts,
                unitName, signature,
                corralled, tds.declareReferences(), tds.bodyReferences());
        return declare(snip, modDiag);
    }

    /**
     * The snippet has failed, return with the rejected event
     *
     * @param xt the task from which to extract the failure diagnostics
     * @param userSource the incoming bad user source
     * @return a rejected snippet event
     */
    private List<SnippetEvent> compileFailResult(BaseTask xt, String userSource) {
        return compileFailResult(xt.getDiagnostics(), userSource);
    }

    /**
     * The snippet has failed, return with the rejected event
     *
     * @param diags the failure diagnostics
     * @param userSource the incoming bad user source
     * @return a rejected snippet event
     */
    private List<SnippetEvent> compileFailResult(DiagList diags, String userSource) {
        ErroneousKey key = state.keyMap.keyForErroneous();
        Snippet snip = new ErroneousSnippet(key, userSource, null, SubKind.UNKNOWN_SUBKIND);
        snip.setFailed(diags);
        state.maps.installSnippet(snip);
        return Collections.singletonList(new SnippetEvent(
                snip, Status.NONEXISTENT, Status.REJECTED,
                false, null, null, null)
        );
    }

    private ExpressionInfo typeOfExpression(String expression) {
        Wrap guts = Wrap.methodReturnWrap(expression);
        TaskFactory.AnalyzeTask at = trialCompile(guts);
        if (!at.hasErrors() && at.firstCuTree() != null) {
            return TreeDissector.createByFirstClass(at)
                    .typeOfReturnStatement(at, state);
        }
        return null;
    }

    /**
     * Should a temp var wrap the expression. TODO make this user configurable.
     *
     * @param snippetKind
     * @return
     */
    private boolean shouldGenTempVar(SubKind snippetKind) {
        return snippetKind == SubKind.OTHER_EXPRESSION_SUBKIND;
    }

    List<SnippetEvent> drop(Snippet si) {
        Unit c = new Unit(state, si);

        Set<Unit> ins = c.dependents().collect(toSet());
        Set<Unit> outs = compileAndLoad(ins);

        return events(c, outs, null, null);
    }

    private List<SnippetEvent> declare(Snippet si) {
        return declare(si, new DiagList());
    }

    private List<SnippetEvent> declare(Snippet si, DiagList generatedDiagnostics) {
        Unit c = new Unit(state, si, null, generatedDiagnostics);

        // Ignores duplicates
        //TODO: remove, modify, or move to edit
        if (c.isRedundant()) {
            return Collections.emptyList();
        }

        Set<Unit> ins = new LinkedHashSet<>();
        ins.add(c);
        Set<Unit> outs = compileAndLoad(ins);

        if (!si.status().isDefined
                && si.diagnostics().isEmpty()
                && si.unresolved().isEmpty()) {
            // did not succeed, but no record of it, extract from others
            si.setDiagnostics(outs.stream()
                    .flatMap(u -> u.snippet().diagnostics().stream())
                    .collect(Collectors.toCollection(DiagList::new)));
        }

        // If appropriate, execute the snippet
        String value = null;
        Exception exception = null;
        if (si.isExecutable() && si.status().isDefined) {
            try {
                value = state.executionControl().commandInvoke(state.maps.classFullName(si));
                value = si.subKind().hasValue()
                        ? expunge(value)
                        : "";
            } catch (EvalException ex) {
                exception = translateExecutionException(ex);
            } catch (UnresolvedReferenceException ex) {
                exception = ex;
            }
        }
        return events(c, outs, value, exception);
    }

    private List<SnippetEvent> events(Unit c, Collection<Unit> outs, String value, Exception exception) {
        List<SnippetEvent> events = new ArrayList<>();
        events.add(c.event(value, exception));
        events.addAll(outs.stream()
                .filter(u -> u != c)
                .map(u -> u.event(null, null))
                .collect(Collectors.toList()));
        events.addAll(outs.stream()
                .flatMap(u -> u.secondaryEvents().stream())
                .collect(Collectors.toList()));
        //System.err.printf("Events: %s\n", events);
        return events;
    }

    private Set<Unit> compileAndLoad(Set<Unit> ins) {
        if (ins.isEmpty()) {
            return ins;
        }
        Set<Unit> replaced = new LinkedHashSet<>();
        while (true) {
            state.debug(DBG_GEN, "compileAndLoad  %s\n", ins);

            ins.stream().forEach(u -> u.initialize(ins));
            AnalyzeTask at = state.taskFactory.new AnalyzeTask(ins);
            ins.stream().forEach(u -> u.setDiagnostics(at));

            // corral any Snippets that need it
            AnalyzeTask cat;
            if (ins.stream().anyMatch(u -> u.corralIfNeeded(ins))) {
                // if any were corralled, re-analyze everything
                cat = state.taskFactory.new AnalyzeTask(ins);
                ins.stream().forEach(u -> u.setCorralledDiagnostics(cat));
            } else {
                cat = at;
            }
            ins.stream().forEach(u -> u.setStatus(cat));
            // compile and load the legit snippets
            boolean success;
            while (true) {
                List<Unit> legit = ins.stream()
                        .filter(u -> u.isDefined())
                        .collect(toList());
                state.debug(DBG_GEN, "compileAndLoad ins = %s -- legit = %s\n",
                        ins, legit);
                if (legit.isEmpty()) {
                    // no class files can be generated
                    success = true;
                } else {
                    // re-wrap with legit imports
                    legit.stream().forEach(u -> u.setWrap(ins, legit));

                    // generate class files for those capable
                    CompileTask ct = state.taskFactory.new CompileTask(legit);
                    if (!ct.compile()) {
                        // oy! compile failed because of recursive new unresolved
                        if (legit.stream()
                                .filter(u -> u.smashingErrorDiagnostics(ct))
                                .count() > 0) {
                            // try again, with the erroreous removed
                            continue;
                        } else {
                            state.debug(DBG_GEN, "Should never happen error-less failure - %s\n",
                                    legit);
                        }
                    }

                    // load all new classes
                    load(legit.stream()
                            .flatMap(u -> u.classesToLoad(ct.classInfoList(u)))
                            .collect(toList()));
                    // attempt to redefine the remaining classes
                    List<Unit> toReplace = legit.stream()
                            .filter(u -> !u.doRedefines())
                            .collect(toList());

                    // prevent alternating redefine/replace cyclic dependency
                    // loop by replacing all that have been replaced
                    if (!toReplace.isEmpty()) {
                        replaced.addAll(toReplace);
                        replaced.stream().forEach(u -> u.markForReplacement());
                    }

                    success = toReplace.isEmpty();
                }
                break;
            }

            // add any new dependencies to the working set
            List<Unit> newDependencies = ins.stream()
                    .flatMap(u -> u.effectedDependents())
                    .collect(toList());
            state.debug(DBG_GEN, "compileAndLoad %s -- deps: %s  success: %s\n",
                    ins, newDependencies, success);
            if (!ins.addAll(newDependencies) && success) {
                // all classes that could not be directly loaded (because they
                // are new) have been redefined, and no new dependnencies were
                // identified
                ins.stream().forEach(u -> u.finish());
                return ins;
            }
        }
    }

    private void load(List<ClassInfo> cil) {
        if (!cil.isEmpty()) {
            state.executionControl().commandLoad(cil);
        }
    }

    private EvalException translateExecutionException(EvalException ex) {
        StackTraceElement[] raw = ex.getStackTrace();
        int last = raw.length;
        do {
            if (last == 0) {
                last = raw.length - 1;
                break;
            }
        } while (!isWrap(raw[--last]));
        StackTraceElement[] elems = new StackTraceElement[last + 1];
        for (int i = 0; i <= last; ++i) {
            StackTraceElement r = raw[i];
            String rawKlass = r.getClassName();
            Matcher matcher = prefixPattern.matcher(rawKlass);
            String num;
            if (matcher.find() && (num = matcher.group("num")) != null) {
                int end = matcher.end();
                if (rawKlass.charAt(end - 1) == '$') {
                    --end;
                }
                int id = Integer.parseInt(num);
                Snippet si = state.maps.getSnippet(id);
                String klass = expunge(rawKlass);
                String method = r.getMethodName().equals(DOIT_METHOD_NAME) ? "" : r.getMethodName();
                String file = "#" + id;
                int line = si.outerWrap().wrapLineToSnippetLine(r.getLineNumber() - 1) + 1;
                elems[i] = new StackTraceElement(klass, method, file, line);
            } else if (r.getFileName().equals("<none>")) {
                elems[i] = new StackTraceElement(r.getClassName(), r.getMethodName(), null, r.getLineNumber());
            } else {
                elems[i] = r;
            }
        }
        String msg = ex.getMessage();
        if (msg.equals("<none>")) {
            msg = null;
        }
        return new EvalException(msg, ex.getExceptionClassName(), elems);
    }

    private boolean isWrap(StackTraceElement ste) {
        return prefixPattern.matcher(ste.getClassName()).find();
    }

    private DiagList modifierDiagnostics(ModifiersTree modtree,
            final TreeDissector dis, boolean isAbstractProhibited) {

        class ModifierDiagnostic extends Diag {

            final boolean fatal;
            final String message;

            ModifierDiagnostic(List<Modifier> list, boolean fatal) {
                this.fatal = fatal;
                StringBuilder sb = new StringBuilder();
                sb.append((list.size() > 1) ? "Modifiers " : "Modifier ");
                for (Modifier mod : list) {
                    sb.append("'");
                    sb.append(mod.toString());
                    sb.append("' ");
                }
                sb.append("not permitted in top-level declarations");
                if (!fatal) {
                    sb.append(", ignored");
                }
                this.message = sb.toString();
            }

            @Override
            public boolean isError() {
                return fatal;
            }

            @Override
            public long getPosition() {
                return dis.getStartPosition(modtree);
            }

            @Override
            public long getStartPosition() {
                return dis.getStartPosition(modtree);
            }

            @Override
            public long getEndPosition() {
                return dis.getEndPosition(modtree);
            }

            @Override
            public String getCode() {
                return fatal
                        ? "jdk.eval.error.illegal.modifiers"
                        : "jdk.eval.warn.illegal.modifiers";
            }

            @Override
            public String getMessage(Locale locale) {
                return message;
            }

            @Override
            Unit unitOrNull() {
                return null;
            }
        }

        List<Modifier> list = new ArrayList<>();
        boolean fatal = false;
        for (Modifier mod : modtree.getFlags()) {
            switch (mod) {
                case SYNCHRONIZED:
                case NATIVE:
                    list.add(mod);
                    fatal = true;
                    break;
                case ABSTRACT:
                    if (isAbstractProhibited) {
                        list.add(mod);
                        fatal = true;
                    }
                    break;
                case PUBLIC:
                case PROTECTED:
                case PRIVATE:
                case STATIC:
                case FINAL:
                    list.add(mod);
                    break;
            }
        }
        return list.isEmpty()
                ? new DiagList()
                : new DiagList(new ModifierDiagnostic(list, fatal));
    }

}
