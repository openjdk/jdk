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

import jdk.jshell.SourceCodeAnalysis.Completeness;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ErroneousTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Scope;
import com.sun.source.tree.Tree;
import com.sun.source.tree.Tree.Kind;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.tools.javac.api.JavacScope;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Symbol.CompletionFailure;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Type.ClassType;
import com.sun.tools.javac.util.DefinedBy;
import com.sun.tools.javac.util.DefinedBy.Api;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;
import com.sun.tools.javac.util.Pair;
import jdk.jshell.CompletenessAnalyzer.CaInfo;
import jdk.jshell.TaskFactory.AnalyzeTask;
import jdk.jshell.TaskFactory.ParseTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

import static jdk.internal.jshell.debug.InternalDebugControl.DBG_COMPA;

import java.io.IOException;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.QualifiedNameable;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.JavaFileManager.Location;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;

import static jdk.jshell.Util.REPL_DOESNOTMATTER_CLASS_NAME;

/**
 * The concrete implementation of SourceCodeAnalysis.
 * @author Robert Field
 */
class SourceCodeAnalysisImpl extends SourceCodeAnalysis {
    private final JShell proc;
    private final CompletenessAnalyzer ca;

    SourceCodeAnalysisImpl(JShell proc) {
        this.proc = proc;
        this.ca = new CompletenessAnalyzer(proc);
    }

    @Override
    public CompletionInfo analyzeCompletion(String srcInput) {
        MaskCommentsAndModifiers mcm = new MaskCommentsAndModifiers(srcInput, false);
        String cleared = mcm.cleared();
        String trimmedInput = Util.trimEnd(cleared);
        if (trimmedInput.isEmpty()) {
            // Just comment or empty
            return new CompletionInfo(Completeness.EMPTY, srcInput.length(), srcInput, "");
        }
        CaInfo info = ca.scan(trimmedInput);
        Completeness status = info.status;
        int unitEndPos = info.unitEndPos;
        if (unitEndPos > srcInput.length()) {
            unitEndPos = srcInput.length();
        }
        int nonCommentNonWhiteLength = trimmedInput.length();
        String src = srcInput.substring(0, unitEndPos);
        switch (status) {
            case COMPLETE:
                if (unitEndPos == nonCommentNonWhiteLength) {
                    // The unit is the whole non-coment/white input plus semicolon
                    String compileSource = src
                            + mcm.mask().substring(nonCommentNonWhiteLength);
                    proc.debug(DBG_COMPA, "Complete: %s\n", compileSource);
                    proc.debug(DBG_COMPA, "   nothing remains.\n");
                    return new CompletionInfo(status, unitEndPos, compileSource, "");
                } else {
                    String remain = srcInput.substring(unitEndPos);
                    proc.debug(DBG_COMPA, "Complete: %s\n", src);
                    proc.debug(DBG_COMPA, "          remaining: %s\n", remain);
                    return new CompletionInfo(status, unitEndPos, src, remain);
                }
            case COMPLETE_WITH_SEMI:
                // The unit is the whole non-coment/white input plus semicolon
                String compileSource = src
                        + ";"
                        + mcm.mask().substring(nonCommentNonWhiteLength);
                proc.debug(DBG_COMPA, "Complete with semi: %s\n", compileSource);
                proc.debug(DBG_COMPA, "   nothing remains.\n");
                return new CompletionInfo(status, unitEndPos, compileSource, "");
            case DEFINITELY_INCOMPLETE:
                proc.debug(DBG_COMPA, "Incomplete: %s\n", srcInput);
                return new CompletionInfo(status, unitEndPos, null, srcInput + '\n');
            case CONSIDERED_INCOMPLETE:
                proc.debug(DBG_COMPA, "Considered incomplete: %s\n", srcInput);
                return new CompletionInfo(status, unitEndPos, null, srcInput + '\n');
            case EMPTY:
                proc.debug(DBG_COMPA, "Detected empty: %s\n", srcInput);
                return new CompletionInfo(status, unitEndPos, srcInput, "");
            case UNKNOWN:
                proc.debug(DBG_COMPA, "Detected error: %s\n", srcInput);
                return new CompletionInfo(status, unitEndPos, srcInput, "");
        }
        throw new InternalError();
    }

    private OuterWrap wrapInClass(Wrap guts) {
        String imports = proc.maps.packageAndImportsExcept(null, null);
        return OuterWrap.wrapInClass(proc.maps.packageName(), REPL_DOESNOTMATTER_CLASS_NAME, imports, "", guts);
    }

    private Tree.Kind guessKind(String code) {
        ParseTask pt = proc.taskFactory.new ParseTask(code);
        List<? extends Tree> units = pt.units();
        if (units.isEmpty()) {
            return Tree.Kind.BLOCK;
        }
        Tree unitTree = units.get(0);
        proc.debug(DBG_COMPA, "Kind: %s -- %s\n", unitTree.getKind(), unitTree);
        return unitTree.getKind();
    }

    //TODO: would be better handled through a lexer:
    private final Pattern JAVA_IDENTIFIER = Pattern.compile("\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*");

    @Override
    public List<Suggestion> completionSuggestions(String code, int cursor, int[] anchor) {
        code = code.substring(0, cursor);
        Matcher m = JAVA_IDENTIFIER.matcher(code);
        String identifier = "";
        while (m.find()) {
            if (m.end() == code.length()) {
                cursor = m.start();
                code = code.substring(0, cursor);
                identifier = m.group();
            }
        }
        code = code.substring(0, cursor);
        if (code.trim().isEmpty()) { //TODO: comment handling
            code += ";";
        }
        OuterWrap codeWrap;
        switch (guessKind(code)) {
            case IMPORT:
                codeWrap = OuterWrap.wrapImport(null, Wrap.importWrap(code + "any.any"));
                break;
            case METHOD:
                codeWrap = wrapInClass(Wrap.classMemberWrap(code));
                break;
            default:
                codeWrap = wrapInClass(Wrap.methodWrap(code));
                break;
        }
        String requiredPrefix = identifier;
        return computeSuggestions(codeWrap, cursor, anchor).stream()
                .filter(s -> s.continuation.startsWith(requiredPrefix) && !s.continuation.equals(REPL_DOESNOTMATTER_CLASS_NAME))
                .sorted(Comparator.comparing(s -> s.continuation))
                .collect(collectingAndThen(toList(), Collections::unmodifiableList));
    }

    private List<Suggestion> computeSuggestions(OuterWrap code, int cursor, int[] anchor) {
        AnalyzeTask at = proc.taskFactory.new AnalyzeTask(code);
        SourcePositions sp = at.trees().getSourcePositions();
        CompilationUnitTree topLevel = at.cuTree();
        List<Suggestion> result = new ArrayList<>();
        TreePath tp = pathFor(topLevel, sp, code.snippetIndexToWrapIndex(cursor));
        if (tp != null) {
            Scope scope = at.trees().getScope(tp);
            Predicate<Element> accessibility = createAccessibilityFilter(at, tp);
            Predicate<Element> smartTypeFilter;
            Predicate<Element> smartFilter;
            Iterable<TypeMirror> targetTypes = findTargetType(at, tp);
            if (targetTypes != null) {
                smartTypeFilter = el -> {
                    TypeMirror resultOf = resultTypeOf(el);
                    return Util.stream(targetTypes)
                            .anyMatch(targetType -> at.getTypes().isAssignable(resultOf, targetType));
                };

                smartFilter = IS_CLASS.negate()
                                      .and(IS_INTERFACE.negate())
                                      .and(IS_PACKAGE.negate())
                                      .and(smartTypeFilter);
            } else {
                smartFilter = TRUE;
                smartTypeFilter = TRUE;
            }
            switch (tp.getLeaf().getKind()) {
                case MEMBER_SELECT: {
                    MemberSelectTree mst = (MemberSelectTree)tp.getLeaf();
                    if (mst.getIdentifier().contentEquals("*"))
                        break;
                    TreePath exprPath = new TreePath(tp, mst.getExpression());
                    TypeMirror site = at.trees().getTypeMirror(exprPath);
                    boolean staticOnly = isStaticContext(at, exprPath);
                    ImportTree it = findImport(tp);
                    boolean isImport = it != null;

                    List<? extends Element> members = membersOf(at, site, staticOnly && !isImport);
                    Predicate<Element> filter = accessibility;
                    Function<Boolean, String> paren = DEFAULT_PAREN;

                    if (isNewClass(tp)) { // new xxx.|
                        Predicate<Element> constructorFilter = accessibility.and(IS_CONSTRUCTOR)
                            .and(el -> {
                                if (el.getEnclosingElement().getEnclosingElement().getKind() == ElementKind.CLASS) {
                                    return el.getEnclosingElement().getModifiers().contains(Modifier.STATIC);
                                }
                                return true;
                            });
                        addElements(membersOf(at, members), constructorFilter, smartFilter, result);

                        filter = filter.and(IS_PACKAGE);
                    } else if (isThrowsClause(tp)) {
                        staticOnly = true;
                        filter = filter.and(IS_PACKAGE.or(IS_CLASS).or(IS_INTERFACE));
                        smartFilter = IS_PACKAGE.negate().and(smartTypeFilter);
                    } else if (isImport) {
                        paren = NO_PAREN;
                        if (!it.isStatic()) {
                            filter = filter.and(IS_PACKAGE.or(IS_CLASS).or(IS_INTERFACE));
                        }
                    } else {
                        filter = filter.and(IS_CONSTRUCTOR.negate());
                    }

                    filter = filter.and(staticOnly ? STATIC_ONLY : INSTANCE_ONLY);

                    addElements(members, filter, smartFilter, paren, result);
                    break;
                }
                case IDENTIFIER:
                    if (isNewClass(tp)) {
                        Function<Element, Iterable<? extends Element>> listEnclosed =
                                el -> el.getKind() == ElementKind.PACKAGE ? Collections.singletonList(el)
                                                                          : el.getEnclosedElements();
                        Predicate<Element> filter = accessibility.and(IS_CONSTRUCTOR.or(IS_PACKAGE));
                        NewClassTree newClassTree = (NewClassTree)tp.getParentPath().getLeaf();
                        ExpressionTree enclosingExpression = newClassTree.getEnclosingExpression();
                        if (enclosingExpression != null) { // expr.new IDENT|
                            TypeMirror site = at.trees().getTypeMirror(new TreePath(tp, enclosingExpression));
                            filter = filter.and(el -> el.getEnclosingElement().getKind() == ElementKind.CLASS && !el.getEnclosingElement().getModifiers().contains(Modifier.STATIC));
                            addElements(membersOf(at, membersOf(at, site, false)), filter, smartFilter, result);
                        } else {
                            addScopeElements(at, scope, listEnclosed, filter, smartFilter, result);
                        }
                        break;
                    }
                    if (isThrowsClause(tp)) {
                        Predicate<Element> accept = accessibility.and(STATIC_ONLY)
                                .and(IS_PACKAGE.or(IS_CLASS).or(IS_INTERFACE));
                        addScopeElements(at, scope, IDENTITY, accept, IS_PACKAGE.negate().and(smartTypeFilter), result);
                        break;
                    }
                    ImportTree it = findImport(tp);
                    if (it != null) {
                        addElements(membersOf(at, at.getElements().getPackageElement("").asType(), false), it.isStatic() ? STATIC_ONLY.and(accessibility) : accessibility, smartFilter, result);
                    }
                    break;
                case ERRONEOUS:
                case EMPTY_STATEMENT: {
                    boolean staticOnly = ReplResolve.isStatic(((JavacScope)scope).getEnv());
                    Predicate<Element> accept = accessibility.and(staticOnly ? STATIC_ONLY : TRUE);
                    addScopeElements(at, scope, IDENTITY, accept, smartFilter, result);

                    Tree parent = tp.getParentPath().getLeaf();
                    switch (parent.getKind()) {
                        case VARIABLE:
                            accept = ((VariableTree)parent).getType() == tp.getLeaf() ?
                                    IS_VOID.negate() :
                                    TRUE;
                            break;
                        case PARAMETERIZED_TYPE: // TODO: JEP 218: Generics over Primitive Types
                        case TYPE_PARAMETER:
                        case CLASS:
                        case INTERFACE:
                        case ENUM:
                            accept = FALSE;
                            break;
                        default:
                            accept = TRUE;
                            break;
                    }
                    addElements(primitivesOrVoid(at), accept, smartFilter, result);
                    break;
                }
            }
        }
        anchor[0] = cursor;
        return result;
    }

    private boolean isStaticContext(AnalyzeTask at, TreePath path) {
        switch (path.getLeaf().getKind()) {
            case ARRAY_TYPE:
            case PRIMITIVE_TYPE:
                return true;
            default:
                Element selectEl = at.trees().getElement(path);
                return selectEl != null && (selectEl.getKind().isClass() || selectEl.getKind().isInterface() || selectEl.getKind() == ElementKind.TYPE_PARAMETER) && selectEl.asType().getKind() != TypeKind.ERROR;
        }
    }

    private TreePath pathFor(CompilationUnitTree topLevel, SourcePositions sp, int pos) {
        TreePath[] deepest = new TreePath[1];

        new TreePathScanner<Void, Void>() {
            @Override @DefinedBy(Api.COMPILER_TREE)
            public Void scan(Tree tree, Void p) {
                if (tree == null)
                    return null;

                long start = sp.getStartPosition(topLevel, tree);
                long end = sp.getEndPosition(topLevel, tree);

                if (start <= pos && pos <= end) {
                    deepest[0] = new TreePath(getCurrentPath(), tree);
                    return super.scan(tree, p);
                }

                return null;
            }
            @Override @DefinedBy(Api.COMPILER_TREE)
            public Void visitErroneous(ErroneousTree node, Void p) {
                return scan(node.getErrorTrees(), null);
            }
        }.scan(topLevel, null);

        return deepest[0];
    }

    private boolean isNewClass(TreePath tp) {
        return tp.getParentPath() != null &&
               tp.getParentPath().getLeaf().getKind() == Kind.NEW_CLASS &&
               ((NewClassTree) tp.getParentPath().getLeaf()).getIdentifier() == tp.getLeaf();
    }

    private boolean isThrowsClause(TreePath tp) {
        Tree parent = tp.getParentPath().getLeaf();
        return parent.getKind() == Kind.METHOD &&
                ((MethodTree)parent).getThrows().contains(tp.getLeaf());
    }

    private ImportTree findImport(TreePath tp) {
        while (tp != null && tp.getLeaf().getKind() != Kind.IMPORT) {
            tp = tp.getParentPath();
        }
        return tp != null ? (ImportTree)tp.getLeaf() : null;
    }

    private Predicate<Element> createAccessibilityFilter(AnalyzeTask at, TreePath tp) {
        Scope scope = at.trees().getScope(tp);
        return el -> {
            switch (el.getKind()) {
                case ANNOTATION_TYPE: case CLASS: case ENUM: case INTERFACE:
                    return at.trees().isAccessible(scope, (TypeElement) el);
                case PACKAGE:
                case EXCEPTION_PARAMETER: case PARAMETER: case LOCAL_VARIABLE: case RESOURCE_VARIABLE:
                    return true;
                default:
                    TypeMirror type = el.getEnclosingElement().asType();
                    if (type.getKind() == TypeKind.DECLARED)
                        return at.trees().isAccessible(scope, el, (DeclaredType) type);
                    else
                        return true;
            }
        };
    }

    private final Predicate<Element> TRUE = el -> true;
    private final Predicate<Element> FALSE = TRUE.negate();
    private final Predicate<Element> IS_STATIC = el -> el.getModifiers().contains(Modifier.STATIC);
    private final Predicate<Element> IS_CONSTRUCTOR = el -> el.getKind() == ElementKind.CONSTRUCTOR;
    private final Predicate<Element> IS_METHOD = el -> el.getKind() == ElementKind.METHOD;
    private final Predicate<Element> IS_PACKAGE = el -> el.getKind() == ElementKind.PACKAGE;
    private final Predicate<Element> IS_CLASS = el -> el.getKind().isClass();
    private final Predicate<Element> IS_INTERFACE = el -> el.getKind().isInterface();
    private final Predicate<Element> IS_VOID = el -> el.asType().getKind() == TypeKind.VOID;
    private final Predicate<Element> STATIC_ONLY = el -> {
        ElementKind kind = el.getKind();
        Element encl = el.getEnclosingElement();
        ElementKind enclKind = encl != null ? encl.getKind() : ElementKind.OTHER;

        return IS_STATIC.or(IS_PACKAGE).or(IS_CLASS).or(IS_INTERFACE).test(el) || IS_PACKAGE.test(encl) ||
                (kind == ElementKind.TYPE_PARAMETER && !enclKind.isClass() && !enclKind.isInterface());
    };
    private final Predicate<Element> INSTANCE_ONLY = el -> {
        Element encl = el.getEnclosingElement();

        return IS_STATIC.or(IS_CLASS).or(IS_INTERFACE).negate().test(el) ||
                IS_PACKAGE.test(encl);
    };
    private final Function<Element, Iterable<? extends Element>> IDENTITY = el -> Collections.singletonList(el);
    private final Function<Boolean, String> DEFAULT_PAREN = hasParams -> hasParams ? "(" : "()";
    private final Function<Boolean, String> NO_PAREN = hasParams -> "";

    private void addElements(Iterable<? extends Element> elements, Predicate<Element> accept, Predicate<Element> smart, List<Suggestion> result) {
        addElements(elements, accept, smart, DEFAULT_PAREN, result);
    }
    private void addElements(Iterable<? extends Element> elements, Predicate<Element> accept, Predicate<Element> smart, Function<Boolean, String> paren, List<Suggestion> result) {
        Set<String> hasParams = Util.stream(elements)
                .filter(accept)
                .filter(IS_CONSTRUCTOR.or(IS_METHOD))
                .filter(c -> !((ExecutableElement)c).getParameters().isEmpty())
                .map(this::simpleName)
                .collect(toSet());

        for (Element c : elements) {
            if (!accept.test(c))
                continue;
            String simpleName = simpleName(c);
            if (c.getKind() == ElementKind.CONSTRUCTOR || c.getKind() == ElementKind.METHOD) {
                simpleName += paren.apply(hasParams.contains(simpleName));
            }
            result.add(new Suggestion(simpleName, smart.test(c)));
        }
    }

    private String simpleName(Element el) {
        return el.getKind() == ElementKind.CONSTRUCTOR ? el.getEnclosingElement().getSimpleName().toString()
                                                       : el.getSimpleName().toString();
    }

    private List<? extends Element> membersOf(AnalyzeTask at, TypeMirror site, boolean shouldGenerateDotClassItem) {
        if (site  == null)
            return Collections.emptyList();

        switch (site.getKind()) {
            case DECLARED: {
                TypeElement element = (TypeElement) at.getTypes().asElement(site);
                List<Element> result = new ArrayList<>();
                result.addAll(at.getElements().getAllMembers(element));
                if (shouldGenerateDotClassItem) {
                    result.add(createDotClassSymbol(at, site));
                }
                result.removeIf(el -> el.getKind() == ElementKind.STATIC_INIT);
                return result;
            }
            case ERROR: {
                //try current qualified name as a package:
                TypeElement typeElement = (TypeElement) at.getTypes().asElement(site);
                Element enclosingElement = typeElement.getEnclosingElement();
                String parentPackageName = enclosingElement instanceof QualifiedNameable ?
                    ((QualifiedNameable)enclosingElement).getQualifiedName().toString() :
                    "";
                Set<PackageElement> packages = listPackages(at, parentPackageName);
                return packages.stream()
                               .filter(p -> p.getQualifiedName().equals(typeElement.getQualifiedName()))
                               .findAny()
                               .map(p -> membersOf(at, p.asType(), false))
                               .orElse(Collections.emptyList());
            }
            case PACKAGE: {
                String packageName = site.toString()/*XXX*/;
                List<Element> result = new ArrayList<>();
                result.addAll(getEnclosedElements(at.getElements().getPackageElement(packageName)));
                result.addAll(listPackages(at, packageName));
                return result;
            }
            case BOOLEAN: case BYTE: case SHORT: case CHAR:
            case INT: case FLOAT: case LONG: case DOUBLE:
            case VOID: {
                return shouldGenerateDotClassItem ?
                    Collections.singletonList(createDotClassSymbol(at, site)) :
                    Collections.emptyList();
            }
            case ARRAY: {
                List<Element> result = new ArrayList<>();
                result.add(createArrayLengthSymbol(at, site));
                if (shouldGenerateDotClassItem)
                    result.add(createDotClassSymbol(at, site));
                return result;
            }
            default:
                return Collections.emptyList();
        }
    }

    private List<? extends Element> membersOf(AnalyzeTask at, List<? extends Element> elements) {
        return elements.stream()
                .flatMap(e -> membersOf(at, e.asType(), true).stream())
                .collect(toList());
    }

    private List<? extends Element> getEnclosedElements(PackageElement packageEl) {
        if (packageEl == null) {
            return Collections.emptyList();
        }
        //workaround for: JDK-8024687
        while (true) {
            try {
                return packageEl.getEnclosedElements()
                                .stream()
                                .filter(el -> el.asType() != null)
                                .filter(el -> el.asType().getKind() != TypeKind.ERROR)
                                .collect(toList());
            } catch (CompletionFailure cf) {
                //ignore...
            }
        }
    }

    private List<? extends Element> primitivesOrVoid(AnalyzeTask at) {
        Types types = at.getTypes();
        return Stream.of(
                TypeKind.BOOLEAN, TypeKind.BYTE, TypeKind.CHAR,
                TypeKind.DOUBLE, TypeKind.FLOAT, TypeKind.INT,
                TypeKind.LONG, TypeKind.SHORT, TypeKind.VOID)
                .map(tk -> (Type)(tk == TypeKind.VOID ? types.getNoType(tk) : types.getPrimitiveType(tk)))
                .map(Type::asElement)
                .collect(toList());
    }

    private Set<PackageElement> listPackages(AnalyzeTask at, String enclosingPackage) {
        Set<PackageElement> packs = new HashSet<>();
        listPackages(at, StandardLocation.PLATFORM_CLASS_PATH, enclosingPackage, packs);
        listPackages(at, StandardLocation.CLASS_PATH, enclosingPackage, packs);
        listPackages(at, StandardLocation.SOURCE_PATH, enclosingPackage, packs);
        return packs;
    }

    private void listPackages(AnalyzeTask at, Location loc, String currentPackage, Set<PackageElement> packs) {
        try {
            MemoryFileManager fm = proc.taskFactory.fileManager();
            for (JavaFileObject file : fm.list(loc, currentPackage, fileKinds, true)) {
                String binaryName = fm.inferBinaryName(loc, file);
                if (!currentPackage.isEmpty() && !binaryName.startsWith(currentPackage + "."))
                    continue;
                int nextDot = binaryName.indexOf('.', !currentPackage.isEmpty() ? currentPackage.length() + 1 : 0);
                if (nextDot == (-1))
                    continue;
                Elements elements = at.getElements();
                PackageElement pack =
                        elements.getPackageElement(binaryName.substring(0, nextDot));
                if (pack == null) {
                    //if no types in the package have ever been seen, the package will be unknown
                    //try to load a type, and then try to recognize the package again:
                    elements.getTypeElement(binaryName);
                    pack = elements.getPackageElement(binaryName.substring(0, nextDot));
                }
                if (pack != null)
                    packs.add(pack);
            }
        } catch (IOException ex) {
            //TODO: should log?
        }
    }
    //where:
        private final Set<JavaFileObject.Kind> fileKinds = EnumSet.of(JavaFileObject.Kind.CLASS);

    private Element createArrayLengthSymbol(AnalyzeTask at, TypeMirror site) {
        Name length = Names.instance(at.getContext()).length;
        Type intType = Symtab.instance(at.getContext()).intType;

        return new VarSymbol(Flags.PUBLIC | Flags.FINAL, length, intType, ((Type) site).tsym);
    }

    private Element createDotClassSymbol(AnalyzeTask at, TypeMirror site) {
        Name _class = Names.instance(at.getContext())._class;
        Type classType = Symtab.instance(at.getContext()).classType;
        Type erasedSite = (Type)at.getTypes().erasure(site);
        classType = new ClassType(classType.getEnclosingType(), com.sun.tools.javac.util.List.of(erasedSite), classType.asElement());

        return new VarSymbol(Flags.PUBLIC | Flags.STATIC | Flags.FINAL, _class, classType, erasedSite.tsym);
    }

    private Iterable<? extends Element> scopeContent(AnalyzeTask at, Scope scope, Function<Element, Iterable<? extends Element>> elementConvertor) {
        Iterable<Scope> scopeIterable = () -> new Iterator<Scope>() {
            private Scope currentScope = scope;
            @Override
            public boolean hasNext() {
                return currentScope != null;
            }
            @Override
            public Scope next() {
                if (!hasNext())
                    throw new NoSuchElementException();
                try {
                    return currentScope;
                } finally {
                    currentScope = currentScope.getEnclosingScope();
                }
            }
        };
        @SuppressWarnings("unchecked")
        List<Element> result = Util.stream(scopeIterable)
                             .flatMap(s -> Util.stream((Iterable<Element>)s.getLocalElements()))
                             .flatMap(el -> Util.stream((Iterable<Element>)elementConvertor.apply(el)))
                             .collect(toCollection(ArrayList :: new));
        result.addAll(listPackages(at, ""));
        return result;
    }

    @SuppressWarnings("fallthrough")
    private Iterable<TypeMirror> findTargetType(AnalyzeTask at, TreePath forPath) {
        if (forPath.getParentPath() == null)
            return null;

        Tree current = forPath.getLeaf();

        switch (forPath.getParentPath().getLeaf().getKind()) {
            case ASSIGNMENT: {
                AssignmentTree tree = (AssignmentTree) forPath.getParentPath().getLeaf();
                if (tree.getExpression() == current)
                    return Collections.singletonList(at.trees().getTypeMirror(new TreePath(forPath.getParentPath(), tree.getVariable())));
                break;
            }
            case VARIABLE: {
                VariableTree tree = (VariableTree) forPath.getParentPath().getLeaf();
                if (tree.getInitializer()== current)
                    return Collections.singletonList(at.trees().getTypeMirror(forPath.getParentPath()));
                break;
            }
            case ERRONEOUS:
                return findTargetType(at, forPath.getParentPath());
            case NEW_CLASS: {
                NewClassTree nct = (NewClassTree) forPath.getParentPath().getLeaf();
                List<TypeMirror> actuals = computeActualInvocationTypes(at, nct.getArguments(), forPath);

                if (actuals != null) {
                    Iterable<Pair<ExecutableElement, ExecutableType>> candidateConstructors = newClassCandidates(at, forPath.getParentPath());

                    return computeSmartTypesForExecutableType(at, candidateConstructors, actuals);
                } else {
                    return findTargetType(at, forPath.getParentPath());
                }
            }
            case METHOD:
                if (!isThrowsClause(forPath)) {
                    break;
                }
                // fall through
            case THROW:
                return Collections.singletonList(at.getElements().getTypeElement("java.lang.Throwable").asType());
            case METHOD_INVOCATION: {
                MethodInvocationTree mit = (MethodInvocationTree) forPath.getParentPath().getLeaf();
                List<TypeMirror> actuals = computeActualInvocationTypes(at, mit.getArguments(), forPath);

                if (actuals == null)
                    return null;

                Iterable<Pair<ExecutableElement, ExecutableType>> candidateMethods = methodCandidates(at, forPath.getParentPath());

                return computeSmartTypesForExecutableType(at, candidateMethods, actuals);
            }
        }

        return null;
    }

    private List<TypeMirror> computeActualInvocationTypes(AnalyzeTask at, List<? extends ExpressionTree> arguments, TreePath currentArgument) {
        if (currentArgument == null)
            return null;

        int paramIndex = arguments.indexOf(currentArgument.getLeaf());

        if (paramIndex == (-1))
            return null;

        List<TypeMirror> actuals = new ArrayList<>();

        for (ExpressionTree arg : arguments.subList(0, paramIndex)) {
            actuals.add(at.trees().getTypeMirror(new TreePath(currentArgument.getParentPath(), arg)));
        }

        return actuals;
    }

    private List<Pair<ExecutableElement, ExecutableType>> filterExecutableTypesByArguments(AnalyzeTask at, Iterable<Pair<ExecutableElement, ExecutableType>> candidateMethods, List<TypeMirror> precedingActualTypes) {
        List<Pair<ExecutableElement, ExecutableType>> candidate = new ArrayList<>();
        int paramIndex = precedingActualTypes.size();

        OUTER:
        for (Pair<ExecutableElement, ExecutableType> method : candidateMethods) {
            boolean varargInvocation = paramIndex >= method.snd.getParameterTypes().size();

            for (int i = 0; i < paramIndex; i++) {
                TypeMirror actual = precedingActualTypes.get(i);

                if (this.parameterType(method.fst, method.snd, i, !varargInvocation)
                        .noneMatch(formal -> at.getTypes().isAssignable(actual, formal))) {
                    continue OUTER;
                }
            }
            candidate.add(method);
        }

        return candidate;
    }

    private Stream<TypeMirror> parameterType(ExecutableElement method, ExecutableType methodType, int paramIndex, boolean allowVarArgsArray) {
        int paramCount = methodType.getParameterTypes().size();
        if (paramIndex >= paramCount && !method.isVarArgs())
            return Stream.empty();
        if (paramIndex < paramCount - 1 || !method.isVarArgs())
            return Stream.of(methodType.getParameterTypes().get(paramIndex));
        TypeMirror varargType = methodType.getParameterTypes().get(paramCount - 1);
        TypeMirror elemenType = ((ArrayType) varargType).getComponentType();
        if (paramIndex >= paramCount || !allowVarArgsArray)
            return Stream.of(elemenType);
        return Stream.of(varargType, elemenType);
    }

    private List<TypeMirror> computeSmartTypesForExecutableType(AnalyzeTask at, Iterable<Pair<ExecutableElement, ExecutableType>> candidateMethods, List<TypeMirror> precedingActualTypes) {
        List<TypeMirror> candidate = new ArrayList<>();
        int paramIndex = precedingActualTypes.size();

        this.filterExecutableTypesByArguments(at, candidateMethods, precedingActualTypes)
            .stream()
            .flatMap(method -> parameterType(method.fst, method.snd, paramIndex, true))
            .forEach(candidate::add);

        return candidate;
    }


    private TypeMirror resultTypeOf(Element el) {
        //TODO: should reflect the type of site!
        switch (el.getKind()) {
            case METHOD:
                return ((ExecutableElement) el).getReturnType();
            case CONSTRUCTOR:
            case INSTANCE_INIT: case STATIC_INIT: //TODO: should be filtered out
                return el.getEnclosingElement().asType();
            default:
                return el.asType();
        }
    }

    private void addScopeElements(AnalyzeTask at, Scope scope, Function<Element, Iterable<? extends Element>> elementConvertor, Predicate<Element> filter, Predicate<Element> smartFilter, List<Suggestion> result) {
        addElements(scopeContent(at, scope, elementConvertor), filter, smartFilter, result);
    }

    private Iterable<Pair<ExecutableElement, ExecutableType>> methodCandidates(AnalyzeTask at, TreePath invocation) {
        MethodInvocationTree mit = (MethodInvocationTree) invocation.getLeaf();
        ExpressionTree select = mit.getMethodSelect();
        List<Pair<ExecutableElement, ExecutableType>> result = new ArrayList<>();
        Predicate<Element> accessibility = createAccessibilityFilter(at, invocation);

        switch (select.getKind()) {
            case MEMBER_SELECT:
                MemberSelectTree mst = (MemberSelectTree) select;
                TreePath tp = new TreePath(new TreePath(invocation, select), mst.getExpression());
                TypeMirror site = at.trees().getTypeMirror(tp);

                if (site == null || site.getKind() != TypeKind.DECLARED)
                    break;

                Element siteEl = at.getTypes().asElement(site);

                if (siteEl == null)
                    break;

                if (isStaticContext(at, tp)) {
                    accessibility = accessibility.and(STATIC_ONLY);
                }

                for (ExecutableElement ee : ElementFilter.methodsIn(membersOf(at, siteEl.asType(), false))) {
                    if (ee.getSimpleName().contentEquals(mst.getIdentifier())) {
                        if (accessibility.test(ee)) {
                            result.add(Pair.of(ee, (ExecutableType) at.getTypes().asMemberOf((DeclaredType) site, ee)));
                        }
                    }
                }
                break;
            case IDENTIFIER:
                IdentifierTree it = (IdentifierTree) select;
                for (ExecutableElement ee : ElementFilter.methodsIn(scopeContent(at, at.trees().getScope(invocation), IDENTITY))) {
                    if (ee.getSimpleName().contentEquals(it.getName())) {
                        if (accessibility.test(ee)) {
                            result.add(Pair.of(ee, (ExecutableType) ee.asType())); //XXX: proper site
                        }
                    }
                }
                break;
            default:
                break;
        }

        return result;
    }

    private Iterable<Pair<ExecutableElement, ExecutableType>> newClassCandidates(AnalyzeTask at, TreePath newClassPath) {
        NewClassTree nct = (NewClassTree) newClassPath.getLeaf();
        Element type = at.trees().getElement(new TreePath(newClassPath.getParentPath(), nct.getIdentifier()));
        TypeMirror targetType = at.trees().getTypeMirror(newClassPath);
        if (targetType == null || targetType.getKind() != TypeKind.DECLARED) {
            Iterable<TypeMirror> targetTypes = findTargetType(at, newClassPath);
            if (targetTypes == null)
                targetTypes = Collections.emptyList();
            targetType =
                    StreamSupport.stream(targetTypes.spliterator(), false)
                                 .filter(t -> at.getTypes().asElement(t) == type)
                                 .findAny()
                                 .orElse(at.getTypes().erasure(type.asType()));
        }
        List<Pair<ExecutableElement, ExecutableType>> candidateConstructors = new ArrayList<>();
        Predicate<Element> accessibility = createAccessibilityFilter(at, newClassPath);

        if (targetType != null &&
            targetType.getKind() == TypeKind.DECLARED &&
            type != null &&
            (type.getKind().isClass() || type.getKind().isInterface())) {
            for (ExecutableElement constr : ElementFilter.constructorsIn(type.getEnclosedElements())) {
                if (accessibility.test(constr)) {
                    ExecutableType constrType =
                            (ExecutableType) at.getTypes().asMemberOf((DeclaredType) targetType, constr);
                    candidateConstructors.add(Pair.of(constr, constrType));
                }
            }
        }

        return candidateConstructors;
    }

    @Override
    public String documentation(String code, int cursor) {
        code = code.substring(0, cursor);
        if (code.trim().isEmpty()) { //TODO: comment handling
            code += ";";
        }

        if (guessKind(code) == Kind.IMPORT)
            return null;

        OuterWrap codeWrap = wrapInClass(Wrap.methodWrap(code));
        AnalyzeTask at = proc.taskFactory.new AnalyzeTask(codeWrap);
        SourcePositions sp = at.trees().getSourcePositions();
        CompilationUnitTree topLevel = at.cuTree();
        TreePath tp = pathFor(topLevel, sp, codeWrap.snippetIndexToWrapIndex(cursor));

        if (tp == null)
            return null;

        TreePath prevPath = null;
        while (tp != null && tp.getLeaf().getKind() != Kind.METHOD_INVOCATION && tp.getLeaf().getKind() != Kind.NEW_CLASS) {
            prevPath = tp;
            tp = tp.getParentPath();
        }

        if (tp == null)
            return null;

        Iterable<Pair<ExecutableElement, ExecutableType>> candidates;
        List<? extends ExpressionTree> arguments;

        if (tp.getLeaf().getKind() == Kind.METHOD_INVOCATION) {
            MethodInvocationTree mit = (MethodInvocationTree) tp.getLeaf();
            candidates = methodCandidates(at, tp);
            arguments = mit.getArguments();
        } else {
            NewClassTree nct = (NewClassTree) tp.getLeaf();
            candidates = newClassCandidates(at, tp);
            arguments = nct.getArguments();
        }

        if (!isEmptyArgumentsContext(arguments)) {
            List<TypeMirror> actuals = computeActualInvocationTypes(at, arguments, prevPath);
            List<TypeMirror> fullActuals = actuals != null ? actuals : Collections.emptyList();

            candidates =
                    this.filterExecutableTypesByArguments(at, candidates, fullActuals)
                        .stream()
                        .filter(method -> parameterType(method.fst, method.snd, fullActuals.size(), true).findAny().isPresent())
                        .collect(Collectors.toList());
        }

        return Util.stream(candidates)
                .map(method -> Util.expunge(element2String(method.fst)))
                .collect(joining("\n"));
    }

    private boolean isEmptyArgumentsContext(List<? extends ExpressionTree> arguments) {
        if (arguments.size() == 1) {
            Tree firstArgument = arguments.get(0);
            return firstArgument.getKind() == Kind.ERRONEOUS;
        }
        return false;
    }

    private String element2String(Element el) {
        switch (el.getKind()) {
            case ANNOTATION_TYPE: case CLASS: case ENUM: case INTERFACE:
                return ((TypeElement) el).getQualifiedName().toString();
            case FIELD:
                return element2String(el.getEnclosingElement()) + "." + el.getSimpleName() + ":" + el.asType();
            case ENUM_CONSTANT:
                return element2String(el.getEnclosingElement()) + "." + el.getSimpleName();
            case EXCEPTION_PARAMETER: case LOCAL_VARIABLE: case PARAMETER: case RESOURCE_VARIABLE:
                return el.getSimpleName() + ":" + el.asType();
            case CONSTRUCTOR: case METHOD:
                StringBuilder header = new StringBuilder();
                header.append(element2String(el.getEnclosingElement()));
                if (el.getKind() == ElementKind.METHOD) {
                    header.append(".");
                    header.append(el.getSimpleName());
                }
                header.append("(");
                String sep = "";
                ExecutableElement method = (ExecutableElement) el;
                for (Iterator<? extends VariableElement> i = method.getParameters().iterator(); i.hasNext();) {
                    VariableElement p = i.next();
                    header.append(sep);
                    if (!i.hasNext() && method.isVarArgs()) {
                        header.append(unwrapArrayType(p.asType()));
                        header.append("...");

                    } else {
                        header.append(p.asType());
                    }
                    header.append(" ");
                    header.append(p.getSimpleName());
                    sep = ", ";
                }
                header.append(")");
                return header.toString();
           default:
                return el.toString();
        }
    }
    private TypeMirror unwrapArrayType(TypeMirror arrayType) {
        if (arrayType.getKind() == TypeKind.ARRAY) {
            return ((ArrayType)arrayType).getComponentType();
        }
        return arrayType;
    }
}
