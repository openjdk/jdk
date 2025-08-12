/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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

package snippets;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.QualifiedNameable;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleElementVisitor14;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.DiagnosticListener;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import com.sun.source.doctree.AttributeTree;
import com.sun.source.doctree.DocCommentTree;
import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.SnippetTree;
import com.sun.source.doctree.TextTree;
import com.sun.source.util.DocTreeScanner;
import com.sun.source.util.DocTrees;
import com.sun.source.util.JavacTask;

/**
 * Utilities for analyzing snippets.
 *
 * Support is provided for the following:
 * <ul>
 * <li>creating an instance of {@link JavacTask} suitable for looking up
 *     elements by name, in order to access any corresponding documentation comment,
 * <li>scanning elements to find all associated snippets,
 * <li>locating instances of snippets by their {@code id},
 * <li>parsing snippets, and
 * <li>accessing the body of snippets, for any additional analysis.
 * </ul>
 *
 * @apiNote
 * The utilities do not provide support for compiling and running snippets,
 * because in general, this requires too much additional context. However,
 * the utilities do provide support for locating snippets in various ways,
 * and accessing the body of those snippets, to simplify the task of writing
 * code to compile and run snippets, where that is appropriate.
 */
public class SnippetUtils {
    /**
     * Exception used to report a configuration issue that prevents
     * the test from executing as expected.
     */
    public static class ConfigurationException extends Exception {
        public ConfigurationException(String message) {
            super(message);
        }
    }

    /**
     * Exception used to report that a snippet could not be found.
     */
    public static class SnippetNotFoundException extends Exception {
        public SnippetNotFoundException(String message) {
            super(message);
        }
    }

    /**
     * Exception used to report that a doc comment could not be found.
     */
    public static class DocCommentNotFoundException extends Exception {
        public DocCommentNotFoundException(String message) {
            super(message);
        }
    }

    private static final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

    private final StandardJavaFileManager fileManager;
    private final Path srcDir;
    private final JavacTask javacTask;
    private final Elements elements;
    private final DocTrees docTrees;

    /**
     * Creates an instance for analysing snippets in one or more JDK modules.
     *
     * The source for the modules is derived from the value of the
     * {@code test.src} system property.
     *
     * Any messages, including error messages, will be written to {@code System.err}.
     *
     * @param modules the modules
     *
     * @throws IllegalArgumentException if no modules are specified
     * @throws ConfigurationException if the main source directory cannot be found
     *                                or if a module's source directory cannot be found
     */
    public SnippetUtils(String... modules) throws ConfigurationException {
        this(findSourceDir(), null, null, Set.of(modules));
    }

    /**
     * Creates an instance for analysing snippets in one or more modules.
     *
     * @param srcDir the location for the source of the modules;
     *               the location for the source of a specific module should be
     *               in <em>srcDir</em>{@code /}<em>module</em>{@code /share/module}
     *
     * @param pw     a writer for any text messages that may be generated;
     *               if null, messages will be written to {@code System.err}
     *
     * @param dl     a diagnostic listener for any diagnostic messages that may be generated;
     *               if null, messages will be written to {@code System.err}
     *
     * @param modules the modules
     *
     * @throws IllegalArgumentException if no modules are specified
     * @throws ConfigurationException if {@code srcDir} does not exist
     *                                or if a module's source directory cannot be found
     */
    public SnippetUtils(Path srcDir, PrintWriter pw, DiagnosticListener<JavaFileObject> dl, Set<String> modules)
            throws ConfigurationException {
        if (modules.isEmpty()) {
            throw new IllegalArgumentException("no modules specified");
        }

        if (!Files.exists(srcDir)) {
            throw new ConfigurationException("directory not found: " + srcDir);
        }

        this.srcDir = srcDir;

        for (var m : modules) {
            var moduleSourceDir = getModuleSourceDir(m);
            if (!Files.exists(moduleSourceDir)) {
                throw new ConfigurationException(("cannot find source directory for " + m
                        + ": " + moduleSourceDir));
            }
        }

        fileManager = compiler.getStandardFileManager(dl, null, null);

        List<String> opts = new ArrayList<>();
        opts.addAll(List.of("--add-modules", String.join(",", modules)));  // could use CompilationTask.addModules
        modules.forEach(m -> opts.addAll(List.of("--patch-module", m + "=" + getModuleSourceDir(m))));
        opts.add("-proc:only");

        javacTask = (JavacTask) compiler.getTask(pw, fileManager, dl, opts, null, null);
        elements = javacTask.getElements();
        elements.getModuleElement("java.base"); // forces module graph to be instantiated, etc

        docTrees = DocTrees.instance(javacTask);
    }

    /**
     * {@return the source directory for the task used to access snippets}
     */
    public Path getSourceDir() {
        return srcDir;
    }

    /**
     * {@return the file manager for the task used to access snippets}
     */
    public StandardJavaFileManager  getFileManager() {
        return fileManager;
    }

    /**
     * {@return the instance of {@code Elements} for the task used to access snippets}
     */
    public Elements getElements() {
        return elements;
    }

    /**
     * {@return the instance of {@code DocTrees} for the task used to access snippets}
     */
    public DocTrees getDocTrees() {
        return docTrees;
    }

    /**
     * {@return the doc comment tree for an element}
     *
     * @param element the element
     */
    public DocCommentTree getDocCommentTree(Element element) {
        return docTrees.getDocCommentTree(element);
    }

    /**
     * {@return the snippet with a given id in a doc comment tree}
     *
     * @param tree the doc comment tree
     * @param id   the id
     *
     * @throws SnippetNotFoundException if the snippet cannot be found
     */
    public SnippetTree getSnippetById(DocCommentTree tree, String id) throws SnippetNotFoundException {
        SnippetTree result = new SnippetFinder().scan(tree, id);
        if (result == null) {
            throw new SnippetNotFoundException(id);
        }
        return result;
    }

    /**
     * {@return the snippet with a given id in the doc comment tree for an element}
     *
     * @param element the element
     * @param id      the id
     *
     * @throws DocCommentNotFoundException if the doc comment for the element cannot be found
     * @throws SnippetNotFoundException if the snippet cannot be found
     */
    public SnippetTree getSnippetById(Element element, String id)
            throws DocCommentNotFoundException, SnippetNotFoundException {
        DocCommentTree docCommentTree = getDocCommentTree(element);
        if (docCommentTree == null) {
            var name = (element instanceof QualifiedNameable q) ? q.getQualifiedName() : element.getSimpleName();
            throw new DocCommentNotFoundException(element.getKind() + " " + name);
        }
        return getSnippetById(docCommentTree, id);
    }

    /**
     * A scanner to locate the tree for a snippet with a given id.
     * Note: the scanner is use-once.
     */
    private static class SnippetFinder extends DocTreeScanner<SnippetTree,String> {
        private SnippetTree result;
        private SnippetTree inSnippet;

        @Override
        public SnippetTree scan(DocTree tree, String id) {
            // stop scanning once the result has been found
            return result != null ? result : super.scan(tree, id);
        }

        @Override
        public SnippetTree visitSnippet(SnippetTree tree, String id) {
            inSnippet = tree;
            try {
                return super.visitSnippet(tree, id);
            } finally {
                inSnippet = null;
            }
        }

        @Override
        public SnippetTree visitAttribute(AttributeTree tree, String id) {
            if (tree.getName().contentEquals("id")
                    && tree.getValue().toString().equals(id)) {
                result = inSnippet;
                return result;
            } else {
                return null;
            }
        }
    }

    /**
     * Scans an element and appropriate enclosed elements for doc comments,
     * and call a handler to handle any snippet trees in those doc comments.
     *
     * Only the public and protected members of type elements are scanned.
     * The enclosed elements of modules and packages are <em>not</em> scanned.
     *
     * @param element the element
     * @param handler the handler
     * @throws IllegalArgumentException if any inappropriate element is scanned
     */
    public void scan(Element element, BiConsumer<Element, SnippetTree> handler) {
        new ElementScanner(docTrees).scan(element, handler);
    }

    private static class ElementScanner extends SimpleElementVisitor14<Void, DocTreeScanner<Void, Element>> {
        private final DocTrees trees;

        public ElementScanner(DocTrees trees) {
            this.trees = trees;
        }

        public void scan(Element e, BiConsumer<Element, SnippetTree> snippetHandler) {
            visit(e, new DocTreeScanner<>() {
                @Override
                public Void visitSnippet(SnippetTree tree, Element e) {
                    snippetHandler.accept(e, tree);
                    return null;
                }
            });
        }

        @Override
        public Void visitModule(ModuleElement me, DocTreeScanner<Void, Element> treeScanner) {
            scanDocComment(me, treeScanner);
            return null;
        }

        @Override
        public Void visitPackage(PackageElement pe, DocTreeScanner<Void, Element> treeScanner) {
            scanDocComment(pe, treeScanner);
            return null;
        }

        @Override
        public Void visitType(TypeElement te, DocTreeScanner<Void, Element> treeScanner) {
            scanDocComment(te, treeScanner);
            for (Element e : te.getEnclosedElements()) {
                Set<Modifier> mods = e.getModifiers();
                if (mods.contains(Modifier.PUBLIC) || mods.contains(Modifier.PROTECTED)) {
                    e.accept(this, treeScanner);
                }
            }
            return null;
        }

        @Override
        public Void visitExecutable(ExecutableElement ee, DocTreeScanner<Void, Element> treeScanner) {
            scanDocComment(ee, treeScanner);
            return null;
        }

        @Override
        public Void visitVariable(VariableElement ve, DocTreeScanner<Void, Element> treeScanner) {
            switch (ve.getKind()) {
                case ENUM_CONSTANT, FIELD -> scanDocComment(ve, treeScanner);
                default -> defaultAction(ve, treeScanner);
            }
            return null;
        }

        @Override
        public Void defaultAction(Element e, DocTreeScanner<Void, Element> treeScanner) {
            throw new IllegalArgumentException(e.getKind() + " " + e.getSimpleName());
        }

        private void scanDocComment(Element e, DocTreeScanner<Void, Element> treeScanner) {
            DocCommentTree dc = trees.getDocCommentTree(e);
            if (dc != null) {
                treeScanner.scan(dc, e);
            }
        }
    }

    /**
     * {@return the string content of an inline or hybrid snippet, or {@code null} for an external snippet}
     *
     * @param tree the snippet
     */
    public String getBody(SnippetTree tree) {
        TextTree body = tree.getBody();
        return body == null ? null : body.getBody();
    }

    /**
     * {@return the string content of an external or inline snippet}
     *
     * @param element the element whose documentation contains the snippet
     * @param tree    the snippet
     */
    public String getBody(Element element, SnippetTree tree) throws IOException {
        Path externalSnippetPath = getExternalSnippetPath(element, tree);
        return externalSnippetPath == null ? getBody(tree) : Files.readString(externalSnippetPath);
    }

    /**
     * {@return the path for the {@code snippet-files} directory for an element}
     *
     * @param element the element
     *
     * @return the path
     */
    public Path getSnippetFilesDir(Element element) {
        var moduleElem = elements.getModuleOf(element);
        var modulePath = getModuleSourceDir(moduleElem);

        var packageElem = elements.getPackageOf(element); // null for a module
        var packagePath = packageElem == null
                ? modulePath
                : modulePath.resolve(packageElem.getQualifiedName().toString().replace(".", File.separator));

        return packagePath.resolve("snippet-files");
    }

    /**
     * {@return the path for an external snippet, or {@code null} if the snippet is inline}
     *
     * @param element the element whose documentation contains the snippet
     * @param tree    the snippet
     */
    public Path getExternalSnippetPath(Element element, SnippetTree tree) {
        var classAttr = getAttr(tree, "class");
        String file = (classAttr != null)
            ? classAttr.replace(".", "/") + ".java"
            : getAttr(tree, "file");
        return file == null ? null : getSnippetFilesDir(element).resolve(file.replace("/", File.separator));
    }

    /**
     * {@return the value of an attribute defined by a snippet}
     *
     * @param tree the snippet
     * @param name the name of the attribute
     */
    public String getAttr(SnippetTree tree, String name) {
        for (DocTree t : tree.getAttributes()) {
            if (t instanceof AttributeTree at && at.getName().contentEquals(name)) {
                return at.getValue().toString();
            }
        }
        return null;
    }

    /**
     * {@return the primary source directory for a module}
     *
     * The directory is <em>srcDir</em>/<em>module-name</em>/share/classes.
     *
     * @param e the module
     */
    public Path getModuleSourceDir(ModuleElement e) {
        return getModuleSourceDir(e.getQualifiedName().toString());
    }

    /**
     * {@return the primary source directory for a module}
     *
     * The directory is <em>srcDir</em>/<em>moduleName</em>/share/classes.
     *
     * @param moduleName the module name
     */
    public Path getModuleSourceDir(String moduleName) {
        return srcDir.resolve(moduleName).resolve("share").resolve("classes");
    }

    /**
     * Kinds of fragments of source code.
     */
    public enum SourceKind {
        /** A module declaration. */
        MODULE_INFO,
        /** A package declaration. */
        PACKAGE_INFO,
        /** A class or interface declaration. */
        TYPE_DECL,
        /** A member declaration for a class or interface. */
        MEMBER_DECL,
        /** A statement, expression or other kind of fragment. */
        OTHER
    }

    /**
     * Parses a fragment of source code, after trying to infer the kind of the fragment.
     *
     * @param body      the string to be parsed
     * @param showDiag  a function to handle any diagnostics that may be generated
     * @return          {@code true} if the parse succeeded, and {@code false} otherwise
     *
     * @throws IOException if an IO exception occurs
     */
    public boolean parse(String body, Consumer<? super Diagnostic<? extends JavaFileObject>> showDiag) throws IOException {
        DiagnosticCollector<JavaFileObject> collector = new DiagnosticCollector<>();
        parse(body, null, collector);
        var diags = collector.getDiagnostics();
        diags.forEach(showDiag);
        return diags.isEmpty();
    }

    /**
     * Parses a fragment of source code, after trying to infer the kind of the fragment.
     *
     * @param body the string to be parsed
     * @param pw   a stream for diagnostics, or {@code null} to use {@code System.err}
     * @param dl   a diagnostic listener, or {@code null} to report diagnostics to {@code pw} or {@code System.err}
     * @throws IOException if an IO exception occurs
     */
    public void parse(String body, PrintWriter pw, DiagnosticListener<JavaFileObject> dl)
            throws IOException {
        parse(inferSourceKind(body), body, pw, dl);
    }

    /**
     * Parses a fragment of source code of a given kind.
     *
     * @param kind the kind of code to be parsed
     * @param body the string to be parsed
     * @param pw   a stream for diagnostics, or {@code null} to use {@code System.err}
     * @param dl   a diagnostic listener, or {@code null} to report diagnostics to {@code pw} or {@code System.err}.
     * @throws IOException if an IO exception occurs
     */
    public void parse(SourceKind kind, String body, PrintWriter pw, DiagnosticListener<JavaFileObject> dl)
            throws IOException {
        String fileBase = switch (kind) {
            case MODULE_INFO -> "module-info";
            case PACKAGE_INFO -> "package-info";
            default -> "C";  // the exact name doesn't matter if just parsing (the filename check for public types comes later on)
        };
        URI uri = URI.create("mem://%s.java".formatted(fileBase));

        String compUnit = switch (kind) {
            case MODULE_INFO, PACKAGE_INFO, TYPE_DECL -> body;
            case MEMBER_DECL -> """
                    class C {
                    %s
                    }""".formatted(body);
            case OTHER -> """
                    class C {
                        void m() {
                        %s
                        ;
                        }
                    }""".formatted(body);
        };
        JavaFileObject fo = SimpleJavaFileObject.forSource(uri, compUnit);

        JavaFileManager fm = compiler.getStandardFileManager(dl, null, null);

        List<String> opts = new ArrayList<>();
        JavacTask javacTask = (JavacTask) compiler.getTask(pw, fm, dl, opts, null, List.of(fo));

        javacTask.parse();
    }

    public SourceKind inferSourceKind(String s) {
        Pattern typeDecl = Pattern.compile("(?s)(^|\\R)([A-Za-z0-9_$ ])*\\b(?<kw>module|package|class|interface|record|enum)\\s+(?<name>[A-Za-z0-9_$]+)");
        Matcher m1 = typeDecl.matcher(s);
        if (m1.find()) {
            return switch (m1.group("kw")) {
                case "module" -> SourceKind.MODULE_INFO;
                case "package" -> m1.find() ? SourceKind.TYPE_DECL : SourceKind.PACKAGE_INFO;
                default -> SourceKind.TYPE_DECL;
            };
        }

        Pattern methodDecl = Pattern.compile("(?s)(^|\\R)([A-Za-z0-9<>,]+ )+\\b(?<name>[A-Za-z0-9_$]+)([(;]| +=)");
        Matcher m2 = methodDecl.matcher(s);
        if (m2.find()) {
            return SourceKind.MEMBER_DECL;
        }

        return SourceKind.OTHER;
    }

    private static Path findSourceDir() throws ConfigurationException {
        String testSrc = System.getProperty("test.src");
        Path p = Path.of(testSrc).toAbsolutePath();
        while (p.getParent() != null) {
            Path srcDir = p.resolve("src");
            if (Files.exists(srcDir.resolve("java.base"))) {
                return srcDir;
            }
            p = p.getParent();
        }
        throw new ConfigurationException("Cannot find src/ from " + testSrc);
    }
}