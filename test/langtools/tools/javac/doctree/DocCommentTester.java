/*
 * Copyright (c) 2012, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.lang.model.element.Name;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;

import com.sun.source.doctree.AttributeTree;
import com.sun.source.doctree.AuthorTree;
import com.sun.source.doctree.CommentTree;
import com.sun.source.doctree.DeprecatedTree;
import com.sun.source.doctree.DocCommentTree;
import com.sun.source.doctree.DocRootTree;
import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.DocTreeVisitor;
import com.sun.source.doctree.DocTypeTree;
import com.sun.source.doctree.EndElementTree;
import com.sun.source.doctree.EntityTree;
import com.sun.source.doctree.ErroneousTree;
import com.sun.source.doctree.EscapeTree;
import com.sun.source.doctree.HiddenTree;
import com.sun.source.doctree.IdentifierTree;
import com.sun.source.doctree.IndexTree;
import com.sun.source.doctree.InheritDocTree;
import com.sun.source.doctree.LinkTree;
import com.sun.source.doctree.LiteralTree;
import com.sun.source.doctree.ParamTree;
import com.sun.source.doctree.ProvidesTree;
import com.sun.source.doctree.RawTextTree;
import com.sun.source.doctree.ReferenceTree;
import com.sun.source.doctree.ReturnTree;
import com.sun.source.doctree.SeeTree;
import com.sun.source.doctree.SerialDataTree;
import com.sun.source.doctree.SerialFieldTree;
import com.sun.source.doctree.SerialTree;
import com.sun.source.doctree.SinceTree;
import com.sun.source.doctree.SnippetTree;
import com.sun.source.doctree.SpecTree;
import com.sun.source.doctree.StartElementTree;
import com.sun.source.doctree.SummaryTree;
import com.sun.source.doctree.SystemPropertyTree;
import com.sun.source.doctree.TextTree;
import com.sun.source.doctree.ThrowsTree;
import com.sun.source.doctree.UnknownBlockTagTree;
import com.sun.source.doctree.UnknownInlineTagTree;
import com.sun.source.doctree.UsesTree;
import com.sun.source.doctree.ValueTree;
import com.sun.source.doctree.VersionTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.DocTreeScanner;
import com.sun.source.util.DocTrees;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.tools.javac.api.JavacTool;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.tree.DCTree;
import com.sun.tools.javac.tree.DCTree.DCDocComment;
import com.sun.tools.javac.tree.DCTree.DCErroneous;
import com.sun.tools.javac.tree.DocPretty;

/**
 * A class to test doc comment trees.
 * It is normally executed by calling {@code main}, providing a source file to be analyzed.
 * The file is scanned for top-level declarations, and the comment for any such declarations
 * is analyzed with a series of "checkers".
 *
 * @see DocCommentTester.ASTChecker#main(String... args)
 */
public class DocCommentTester {

    public static void main(String... args) throws Exception {
        ArrayList<String> list = new ArrayList<>(Arrays.asList(args));
        if (!list.isEmpty() && "-useBreakIterator".equals(list.get(0))) {
            list.remove(0);
            new DocCommentTester(true, true).run(list);
        } else if (!list.isEmpty() && "-useStandardTransformer".equals(list.get(0))) {
            list.remove(0);
            new DocCommentTester(false, false).run(list);
        } else {
            new DocCommentTester(false, true).run(list);
        }
    }

    public static final String BI_MARKER = "BREAK_ITERATOR";
    public final boolean useBreakIterator;

    public final boolean useIdentityTransformer;

    public DocCommentTester(boolean useBreakIterator, boolean useIdentityTtransformer) {
        this.useBreakIterator = useBreakIterator;
        this.useIdentityTransformer = useIdentityTtransformer;
    }

    public void run(List<String> args) throws Exception {
        String testSrc = System.getProperty("test.src");

        List<Path> files = args.stream()
                .map(arg -> Path.of(testSrc, arg))
                .collect(Collectors.toList());

        JavacTool javac = JavacTool.create();
        StandardJavaFileManager fm = javac.getStandardFileManager(null, null, null);

        Iterable<? extends JavaFileObject> fos = fm.getJavaFileObjectsFromPaths(files);

        JavacTask t = javac.getTask(null, fm, null, null, null, fos);
        final JavacTrees trees = (JavacTrees) DocTrees.instance(t);

        if (useIdentityTransformer) {
            // disable default use of the "standard" transformer, so that we can examine
            // the trees as created by DocCommentParser.
            trees.setDocCommentTreeTransformer(new JavacTrees.IdentityTransformer());
        }

        if (useBreakIterator) {
            // BreakIterators are locale dependent wrt. behavior
            trees.setBreakIterator(BreakIterator.getSentenceInstance(Locale.ENGLISH));
        }

        final Checker[] checkers = {
            new ASTChecker(this, trees),
            new PosChecker(this, trees),
            new PrettyChecker(this, trees),
            new RangeChecker(this, trees),
            new StartEndPosChecker(this, trees)
        };

        DeclScanner d = new DeclScanner() {
            @Override
            public Void visitCompilationUnit(CompilationUnitTree tree, Void ignore) {
                for (Checker c: checkers)
                    c.visitCompilationUnit(tree);
                return super.visitCompilationUnit(tree, ignore);
            }

            @Override
            void visitDecl(Tree tree, Name name) {
                TreePath path = getCurrentPath();
                String dc = trees.getDocComment(path);
                if (dc != null) {
                    for (Checker c : checkers) {
                        try {
                            System.err.println(path.getLeaf().getKind()
                                    + " " + name
                                    + " " + c.getClass().getSimpleName());

                            c.check(path, name);

                            System.err.println();
                        } catch (Exception e) {
                            error("Exception " + e);
                            e.printStackTrace(System.err);
                        }
                    }
                }
            }
        };

        Iterable<? extends CompilationUnitTree> units = t.parse();
        for (CompilationUnitTree unit: units) {
            d.scan(unit, null);
        }

        if (errors > 0)
            throw new Exception(errors + " errors occurred");
    }

    static abstract class DeclScanner extends TreePathScanner<Void, Void> {
        abstract void visitDecl(Tree tree, Name name);

        @Override
        public Void visitClass(ClassTree tree, Void ignore) {
            super.visitClass(tree, ignore);
            visitDecl(tree, tree.getSimpleName());
            return null;
        }

        @Override
        public Void visitMethod(MethodTree tree, Void ignore) {
            super.visitMethod(tree, ignore);
            visitDecl(tree, tree.getName());
            return null;
        }

        @Override
        public Void visitVariable(VariableTree tree, Void ignore) {
            super.visitVariable(tree, ignore);
            visitDecl(tree, tree.getName());
            return null;
        }
    }

    /**
     * Base class for checkers to check the doc comment on a declaration
     * (when present.)
     */
    abstract class Checker {
        final DocTrees trees;

        Checker(DocTrees trees) {
            this.trees = trees;
        }

        void visitCompilationUnit(CompilationUnitTree tree) { }

        abstract void check(TreePath tree, Name name) throws Exception;

        void error(String msg) {
            DocCommentTester.this.error(msg);
        }
    }

    void error(String msg) {
        System.err.println("Error: " + msg);
        errors++;
    }

    int errors;

    /**
     * Verifies the structure of the DocTree AST by comparing it against golden text.
     */
    static class ASTChecker extends Checker {
        static final String NEWLINE = System.getProperty("line.separator");
        Printer printer = new Printer();
        String source;
        DocCommentTester test;

        ASTChecker(DocCommentTester test, DocTrees t) {
            test.super(t);
            this.test = test;
        }

        @Override
        void visitCompilationUnit(CompilationUnitTree tree) {
            try {
                source = tree.getSourceFile().getCharContent(true).toString();
            } catch (IOException e) {
                source = "";
            }
        }

        void check(TreePath path, Name name) {
            StringWriter out = new StringWriter();
            DocCommentTree dc = trees.getDocCommentTree(path);
            printer.print(dc, out);
            out.flush();
            String found = out.toString().replace(NEWLINE, "\n");

            /*
             * Look for the first block comment after the first occurrence
             * of name, noting that, block comments with BI_MARKER may
             * very well be present.
             */
            int start = test.useBreakIterator
                    ? source.indexOf("\n/*\n" + BI_MARKER + "\n", findName(source, name))
                    : source.indexOf("\n/*\n", findName(source, name));
            assert start >= 0 : "start of AST comment not found";
            int end = source.indexOf("\n*/\n", start);
            assert end >= 0 : "end of AST comment not found";
            int startlen = start + (test.useBreakIterator ? BI_MARKER.length() + 1 : 0) + 4;
            String expect = source.substring(startlen, end + 1);
            if (!found.equals(expect)) {
                if (test.useBreakIterator) {
                    System.err.println("Using BreakIterator");
                }
                System.err.println("Expect:\n" + expect);
                System.err.println("Found:\n" + found);
                error("AST mismatch for " + name);
            }
        }

        /**
         * This main program is to set up the golden comments used by this
         * checker.
         * Usage:
         *     java DocCommentTester$ASTChecker -o dir file...
         * The given files are written to the output directory with their
         * golden comments updated. The intent is that the files should
         * then be compared with the originals, e.g. with meld, and if the
         * changes are approved, the new files can be used to replace the old.
         */
        public static void main(String... args) throws Exception {
            List<Path> files = new ArrayList<>();
            Path o = null;
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                if (arg.equals("-o"))
                    o = Path.of(args[++i]);
                else if (arg.startsWith("-"))
                    throw new IllegalArgumentException(arg);
                else {
                    files.add(Path.of(arg));
                }
            }

            if (o == null)
                throw new IllegalArgumentException("no output dir specified");
            final Path outDir = o;

            JavacTool javac = JavacTool.create();
            StandardJavaFileManager fm = javac.getStandardFileManager(null, null, null);
            Iterable<? extends JavaFileObject> fos = fm.getJavaFileObjectsFromPaths(files);

            JavacTask t = javac.getTask(null, fm, null, null, null, fos);
            final DocTrees trees = DocTrees.instance(t);

            DeclScanner d = new DeclScanner() {
                final Printer p = new Printer();
                String source;

                @Override
                public Void visitCompilationUnit(CompilationUnitTree tree, Void ignore) {
                    System.err.println("processing " + tree.getSourceFile().getName());
                    try {
                        source = tree.getSourceFile().getCharContent(true).toString();
                    } catch (IOException e) {
                        source = "";
                    }
                    // remove existing gold by removing all block comments after the first '{'.
                    int start = source.indexOf("{");
                    assert start >= 0 : "cannot find initial '{'";
                    while ((start = source.indexOf("\n/*\n", start)) != -1) {
                        int end = source.indexOf("\n*/\n");
                        assert end >= 0 : "cannot find end of comment";
                        source = source.substring(0, start + 1) + source.substring(end + 4);
                    }

                    // process decls in compilation unit
                    super.visitCompilationUnit(tree, ignore);

                    // write the modified source
                    var treeSourceFileName = tree.getSourceFile().getName();
                    var outFile = outDir.resolve(treeSourceFileName);
                    try {
                        Files.writeString(outFile, source);
                    } catch (IOException e) {
                        System.err.println("Can't write " + treeSourceFileName
                                + " to " + outFile + ": " + e);
                    }
                    return null;
                }

                @Override
                void visitDecl(Tree tree, Name name) {
                    DocTree dc = trees.getDocCommentTree(getCurrentPath());
                    if (dc != null) {
                        StringWriter out = new StringWriter();
                        p.print(dc, out);
                        String found = out.toString();

                        // Look for the empty line after the first occurrence of name
                        int pos = source.indexOf("\n\n", findName(source, name));

                        // Insert the golden comment
                        source = source.substring(0, pos)
                                + "\n/*\n"
                                + found
                                + "*/"
                                + source.substring(pos);
                    }
                }

            };

            Iterable<? extends CompilationUnitTree> units = t.parse();
            for (CompilationUnitTree unit: units) {
                d.scan(unit, null);
            }
        }

        static int findName(String source, Name name) {
            Pattern p = Pattern.compile("\\s" + name + "[(;]");
            Matcher m = p.matcher(source);
            if (!m.find())
                throw new Error("cannot find " + name);
            return m.start();
        }

        static class Printer implements DocTreeVisitor<Void, Void> {
            PrintWriter out;

            void print(DocTree tree, Writer out) {
                this.out = (out instanceof PrintWriter)
                        ? (PrintWriter) out : new PrintWriter(out);
                tree.accept(this, null);
                this.out.flush();
            }

            public Void visitAttribute(AttributeTree node, Void p) {
                header(node);
                indent(+1);
                print("name", node.getName().toString());
                print("vkind", node.getValueKind().toString());
                print("value", node.getValue());
                indent(-1);
                indent();
                out.println("]");
                return null;
            }

            public Void visitAuthor(AuthorTree node, Void p) {
                header(node);
                indent(+1);
                print("name", node.getName());
                indent(-1);
                indent();
                out.println("]");
                return null;
            }

            public Void visitComment(CommentTree node, Void p) {
                header(node, compress(node.getBody()));
                return null;
            }

            public Void visitDeprecated(DeprecatedTree node, Void p) {
                header(node);
                indent(+1);
                print("body", node.getBody());
                indent(-1);
                indent();
                out.println("]");
                return null;
            }

            public Void visitDocComment(DocCommentTree node, Void p) {
                header(node);
                indent(+1);
                // Applicable only to html files, print iff non-empty
                if (!node.getPreamble().isEmpty())
                    print("preamble", node.getPreamble());

                print("firstSentence", node.getFirstSentence());
                print("body", node.getBody());
                print("block tags", node.getBlockTags());

                // Applicable only to html files, print iff non-empty
                if (!node.getPostamble().isEmpty())
                    print("postamble", node.getPostamble());

                indent(-1);
                indent();
                out.println("]");
                return null;
            }

            public Void visitDocRoot(DocRootTree node, Void p) {
                header(node, "");
                return null;
            }

            public Void visitDocType(DocTypeTree node, Void p) {
                header(node, compress(node.getText()));
                return null;
            }

            public Void visitEndElement(EndElementTree node, Void p) {
                header(node, node.getName().toString());
                return null;
            }

            public Void visitEntity(EntityTree node, Void p) {
                header(node, node.getName().toString());
                return null;
            }

            public Void visitErroneous(ErroneousTree node, Void p) {
                header(node);
                indent(+1);
                print("code", ((DCErroneous) node).diag.getCode());
                print("body", compress(node.getBody()));
                indent(-1);
                indent();
                out.println("]");
                return null;
            }

            public Void visitEscape(EscapeTree node, Void p) {
                header(node, node.getBody());
                return null;
            }

            public Void visitHidden(HiddenTree node, Void p) {
                header(node);
                indent(+1);
                print("body", node.getBody());
                indent(-1);
                indent();
                out.println("]");
                return null;
            }

            public Void visitIdentifier(IdentifierTree node, Void p) {
                header(node, compress(node.getName().toString()));
                return null;
            }

            @Override
            public Void visitIndex(IndexTree node, Void p) {
                header(node);
                indent(+1);
                print("term", node.getSearchTerm());
                print("description", node.getDescription());
                indent(-1);
                indent();
                out.println("]");
                return null;
            }

            public Void visitInheritDoc(InheritDocTree node, Void p) {
                header(node);
                indent(+1);
                print("supertype", node.getSupertype());
                indent(-1);
                indent();
                out.println("]");
                return null;
            }

            public Void visitLink(LinkTree node, Void p) {
                header(node);
                indent(+1);
                print("reference", node.getReference());
                print("body", node.getLabel());
                indent(-1);
                indent();
                out.println("]");
                return null;
            }

            public Void visitLiteral(LiteralTree node, Void p) {
                header(node, compress(node.getBody().getBody()));
                return null;
            }

            public Void visitParam(ParamTree node, Void p) {
                header(node);
                indent(+1);
                print("name", node.getName());
                print("description", node.getDescription());
                indent(-1);
                indent();
                out.println("]");
                return null;
            }

            public Void visitProvides(ProvidesTree node, Void p) {
                header(node);
                indent(+1);
                print("serviceName", node.getServiceType());
                print("description", node.getDescription());
                indent(-1);
                indent();
                out.println("]");
                return null;
            }

            public Void visitRawText(RawTextTree node, Void p) {
                header(node, compress(node.getContent()));
                return null;
            }

            public Void visitReference(ReferenceTree node, Void p) {
                header(node, compress(node.getSignature()));
                return null;
            }

            public Void visitReturn(ReturnTree node, Void p) {
                header(node);
                indent(+1);
                print("description", node.getDescription());
                indent(-1);
                indent();
                out.println("]");
                return null;
            }

            public Void visitSee(SeeTree node, Void p) {
                header(node);
                indent(+1);
                print("reference", node.getReference());
                indent(-1);
                indent();
                out.println("]");
                return null;
            }

            public Void visitSerial(SerialTree node, Void p) {
                header(node);
                indent(+1);
                print("description", node.getDescription());
                indent(-1);
                indent();
                out.println("]");
                return null;
            }

            public Void visitSerialData(SerialDataTree node, Void p) {
                header(node);
                indent(+1);
                print("description", node.getDescription());
                indent(-1);
                indent();
                out.println("]");
                return null;
            }

            public Void visitSerialField(SerialFieldTree node, Void p) {
                header(node);
                indent(+1);
                print("name", node.getName());
                print("type", node.getType());
                print("description", node.getDescription());
                indent(-1);
                indent();
                out.println("]");
                return null;
            }

            public Void visitSince(SinceTree node, Void p) {
                header(node);
                indent(+1);
                print("body", node.getBody());
                indent(-1);
                indent();
                out.println("]");
                return null;
            }

            @Override
            public Void visitSpec(SpecTree node, Void p) {
                header(node);
                indent(+1);
                print("url", node.getURL());
                print("title", node.getTitle());
                indent(-1);
                indent();
                out.println("]");
                return null;
            }

            @Override
            public Void visitSnippet(SnippetTree node, Void p) {
                header(node);
                indent(+1);
                print("attributes", node.getAttributes());
                print("body", node.getBody());
                indent(-1);
                indent();
                out.println("]");
                return null;
            }

            public Void visitStartElement(StartElementTree node, Void p) {
                header(node);
                indent(+1);
                indent();
                out.println("name:" + node.getName());
                print("attributes", node.getAttributes());
                indent(-1);
                indent();
                out.println("]");
                return null;
            }

            @Override
            public Void visitSummary(SummaryTree node, Void p) {
                header(node);
                indent(+1);
                print("summary", node.getSummary());
                indent(-1);
                indent();
                out.println("]");
                return null;
            }

            @Override
            public Void visitSystemProperty(SystemPropertyTree node, Void p) {
                header(node);
                indent(+1);
                print("property name", node.getPropertyName().toString());
                indent(-1);
                indent();
                out.println("]");
                return null;
            }

            public Void visitText(TextTree node, Void p) {
                header(node, compress(node.getBody()));
                return null;
            }

            public Void visitThrows(ThrowsTree node, Void p) {
                header(node);
                indent(+1);
                print("exceptionName", node.getExceptionName());
                print("description", node.getDescription());
                indent(-1);
                indent();
                out.println("]");
                return null;
            }

            public Void visitUnknownBlockTag(UnknownBlockTagTree node, Void p) {
                header(node);
                indent(+1);
                indent();
                out.println("tag:" + node.getTagName());
                print("content", node.getContent());
                indent(-1);
                indent();
                out.println("]");
                return null;
            }

            public Void visitUnknownInlineTag(UnknownInlineTagTree node, Void p) {
                header(node);
                indent(+1);
                indent();
                out.println("tag:" + node.getTagName());
                print("content", node.getContent());
                indent(-1);
                indent();
                out.println("]");
                return null;
            }

            public Void visitUses(UsesTree node, Void p) {
                header(node);
                indent(+1);
                print("serviceName", node.getServiceType());
                print("description", node.getDescription());
                indent(-1);
                indent();
                out.println("]");
                return null;
            }

            public Void visitValue(ValueTree node, Void p) {
                header(node);
                indent(+1);
                print("format", node.getFormat());
                print("reference", node.getReference());
                indent(-1);
                indent();
                out.println("]");
                return null;
            }

            public Void visitVersion(VersionTree node, Void p) {
                header(node);
                indent(+1);
                print("body", node.getBody());
                indent(-1);
                indent();
                out.println("]");
                return null;
            }

            public Void visitOther(DocTree node, Void p) {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            /*
             * Use this method to start printing a multi-line representation of a
             * DocTree node. The representation should be terminated by calling
             * out.println("]").
             */
            void header(DocTree node) {
                indent();
                var n = (DCTree) node;
                out.println(simpleClassName(node) + "[" + node.getKind() + ", pos:" + n.pos +
                        (n.getPreferredPosition() != n.pos ? ", prefPos:" + n.getPreferredPosition() : ""));
            }

            /*
             * Use this method to print a single-line representation of a DocTree node.
             */
            void header(DocTree node, String rest) {
                indent();
                out.println(simpleClassName(node) + "[" + node.getKind() + ", pos:" + ((DCTree) node).pos
                        + (rest.isEmpty() ? "" : ", " + rest)
                        + "]");
            }

            String simpleClassName(DocTree node) {
                return node.getClass().getSimpleName().replaceAll("DC(.*)", "$1");
            }

            void print(String name, DocTree item) {
                indent();
                if (item == null)
                    out.println(name + ": null");
                else {
                    out.println(name + ":");
                    indent(+1);
                    item.accept(this, null);
                    indent(-1);
                }
            }

            void print(String name, String s) {
                indent();
                out.println(name + ": " + s);
            }

            void print(String name, List<? extends DocTree> list) {
                indent();
                if (list == null)
                    out.println(name + ": null");
                else if (list.isEmpty())
                    out.println(name + ": empty");
                else {
                    out.println(name + ": " + list.size());
                    indent(+1);
                    for (DocTree tree: list) {
                        tree.accept(this, null);
                    }
                    indent(-1);
                }
            }

            int indent = 0;

            void indent() {
                for (int i = 0; i < indent; i++) {
                    out.print("  ");
                }
            }

            void indent(int n) {
                indent += n;
            }

            private static final int BEGIN = 32;
            private static final String ELLIPSIS = "...";
            private static final int END = 32;

            String compress(String s) {
                s = s.replace("\n", "|").replace(" ", "_");
                return (s.length() < BEGIN + ELLIPSIS.length() + END)
                        ? s
                        : s.substring(0, BEGIN) + ELLIPSIS + s.substring(s.length() - END);
            }

            String quote(String s) {
                if (s.contains("\""))
                    return "'" + s + "'";
                else if (s.contains("'") || s.contains(" "))
                    return '"' + s + '"';
                else
                    return s;
            }
        }
    }

    /**
     * Verifies the reported tree positions by comparing the characters found
     * at and after the reported position with the beginning of the pretty-
     * printed text.
     */
    static class PosChecker extends Checker {
        PosChecker(DocCommentTester test, DocTrees t) {
            test.super(t);
        }

        @Override
        void check(TreePath path, Name name) throws Exception {
            JavaFileObject fo = path.getCompilationUnit().getSourceFile();
            final CharSequence cs = fo.getCharContent(true);

            final DCDocComment dc = (DCDocComment) trees.getDocCommentTree(path);

            DocTreeScanner<Void, Void> scanner = new DocTreeScanner<>() {
                @Override
                public Void scan(DocTree node, Void ignore) {
                    if (node != null) {
                        try {
                            DCTree dcTree = (DCTree) node;
                            String expect = getExpectText(node);
                            long startPos = dc.getSourcePosition(dcTree.getStartPosition());
                            String found = getFoundText(cs, (int) startPos, expect.length());
                            if (!found.equals(expect)) {
                                System.err.println("node: " + node.getKind());
                                System.err.println("startPos: " + startPos + " " + showPos(cs, (int) startPos));
                                System.err.println("expect: " + expect);
                                System.err.println("found:  " + found);
                                error("mismatch");
                            }

                        } catch (StringIndexOutOfBoundsException e) {
                            error(node.getClass() + ": " + e);
                                e.printStackTrace();
                        }
                    }
                    return super.scan(node, ignore);
                }
            };

            scanner.scan(dc, null);
        }

        String getExpectText(DocTree t) {
            StringWriter sw = new StringWriter();
            DocPretty p = new DocPretty(sw);
            try { p.print(t); } catch (IOException never) { }
            String s = sw.toString();
            if (s.length() <= 1)
                return s;
            int ws = s.replaceAll("\\s+", " ").indexOf(" ");
            if (ws != -1) s = s.substring(0, ws);
            return (s.length() < 5) ? s : s.substring(0, 5);
        }

        String getFoundText(CharSequence cs, int pos, int len) {
            return (pos == -1) ? "" : cs.subSequence(pos, Math.min(pos + len, cs.length())).toString();
        }

        String showPos(CharSequence cs, int pos) {
            String s = cs.toString();
            return (s.substring(Math.max(0, pos - 10), pos)
                    + "["
                    + s.charAt(pos)
                    + "]"
                    + s.substring(pos + 1, Math.min(s.length(), pos + 10)))
                    .replace('\n', '|')
                    .replace(' ', '_');
        }
    }

    /**
     * Verifies the pretty printed text against a normalized form of the
     * original doc comment.
     */
    static class PrettyChecker extends Checker {

        PrettyChecker(DocCommentTester test, DocTrees t) {
            test.super(t);
        }

        @Override
        void check(TreePath path, Name name) throws Exception {
            var annos = (path.getLeaf() instanceof MethodTree m)
                    ? m.getModifiers().getAnnotations().toString()
                    : "";
            if (annos.contains("@PrettyCheck(false)")) {
                return;
            }

            Elements.DocCommentKind ck = trees.getDocCommentKind(path);
            boolean isLineComment = ck == Elements.DocCommentKind.END_OF_LINE;
            String raw = trees.getDocComment(path).stripTrailing();
            String normRaw = normalize(raw, isLineComment);

            StringWriter out = new StringWriter();
            DocPretty dp = new DocPretty(out);
            dp.print(trees.getDocCommentTree(path));
            String pretty = out.toString();

            if (!pretty.equals(normRaw)) {
                error("mismatch");
                System.err.println("*** raw: (" + raw.length() + ")");
                System.err.println(raw.replace(" ", "_"));
                System.err.println("*** expected: (" + normRaw.length() + ")");
                System.err.println(normRaw.replace(" ", "_"));
                System.err.println("*** found: (" + pretty.length() + ")");
                System.err.println(pretty.replace(" ", "_"));
            }
        }

        /**
         * Normalize whitespace in places where the tree does not preserve it.
         *
         * @param s the comment text to be normalized
         * @return the normalized content
         */
        String normalize(String s, boolean isLineComment) {
            // See comment in MarkdownTest for explanation of dummy and Override
            return (isLineComment ? s : s.stripIndent().trim())
                    .replaceFirst("\\.\\s*\\n *@(?![@*])", ".\n@")  // Between block tags
                    .replaceAll("\n[ \t]+@(?!([@*]|(dummy|Override)))", "\n@")
                    .replaceAll("(?i)\\{@([a-z][a-z0-9.:-]*)\\s+}", "{@$1}")
                    .replaceAll("(\\{@value\\s+[^}]+)\\s+(})", "$1$2")
                    .replaceAll("<pre> *\\{@code\\n", "<pre>{@code ")
                    .replaceAll("<pre> *<code>\\n", "<pre><code>");
        }
    }

    /**
     * Verifies the general "left to right" constraints for the positions of
     * nodes in the DocTree AST.
     */
    static class RangeChecker extends Checker {
        int cursor = 0;

        RangeChecker(DocCommentTester test, DocTrees docTrees) {
            test.super(docTrees);
        }

        @Override
        void check(TreePath path, Name name) throws Exception {
            final DCDocComment dc = (DCDocComment) trees.getDocCommentTree(path);

            DocTreeScanner<Void, Void> scanner = new DocTreeScanner<>() {
                @Override
                public Void scan(DocTree node, Void ignore) {
                    if (node instanceof DCTree dcTree) {
                        int start = dcTree.getStartPosition();
                        int pref = dcTree.getPreferredPosition();
                        int end = dcTree.getEndPosition();

                        // check within the node, start <= pref <= end
                        check("start:pref", dcTree, start, pref);
                        check("pref:end", dcTree, pref, end);

                        // check cursor <= start
                        check("cursor:start", dcTree, cursor, start);
                        cursor = start;

                        // recursively scan any children, updating the cursor
                        super.scan(node, ignore);

                        // check cursor <= end
                        check("cursor:end", dcTree, cursor, end);
                        cursor = end;
                    }
                    return null;
                }
            };

            cursor = 0;
            scanner.scan(dc, null);

        }

        void check(String name, DCTree tree, int first, int second) {
            if (!(first <= second)) {
                error(name, tree, first, second);
            }
        }

        private void error(String name, DCTree tree, int first, int second) {
            String t = tree.toString().replaceAll("\\s+", " ");
            if (t.length() > 32) {
                t = t.substring(0, 15) + "..." + t.substring(t.length() - 15);
            }
            error("Checking " + name + " for " + tree.getKind() + " `" + t + "`;  first:" + first + ", second:" + second);

        }
    }

    /**
     * Verifies that the start and end positions of all nodes in a DocCommentTree point to the
     * expected characters in the source code.
     *
     * The expected characters are derived from the beginning and end of the DocPretty output
     * for each node. Note that while the whitespace within the DocPretty output may not exactly
     * match the original source code, the first and last characters should match.
     */
    static class StartEndPosChecker extends Checker {

        StartEndPosChecker(DocCommentTester test, DocTrees docTrees) {
            test.super(docTrees);
        }

        @Override
        void check(TreePath path, Name name) throws Exception {
            final DCDocComment dc = (DCDocComment) trees.getDocCommentTree(path);
            JavaFileObject jfo = path.getCompilationUnit().getSourceFile();
            CharSequence content = jfo.getCharContent(true);

            DocTreeScanner<Void, Void> scanner = new DocTreeScanner<>() {
                @Override
                public Void scan(DocTree node, Void ignore) {
                    if (node instanceof DCTree dcTree) {
                        int start = dc.getSourcePosition(dc.getStartPosition());
                        int end = dc.getSourcePosition(dcTree.getEndPosition());

                        try {
                            StringWriter out = new StringWriter();
                            DocPretty dp = new DocPretty(out);
                            dp.print(trees.getDocCommentTree(path));
                            String pretty = out.toString();

                            if (pretty.isEmpty()) {
                                if (start != end) {
                                    error("Error: expected content is empty, but actual content is not: "
                                            + dcTree.getKind() + " [" + start + "," + end + ")"
                                            + ": \"" + content.subSequence(start, end) + "\"" );
                                }
                            } else {
                                check(dcTree, "start", content, start, pretty, 0);
                                check(dcTree, "end", content, end - 1, pretty, pretty.length() - 1);
                            }

                        } catch (IOException e) {
                            error("Error generating DocPretty for tree at position " + start + "; " + e);
                        }
                    }
                    return null;
                }
            };

            scanner.scan(dc, null);
        }

        void check(DCTree tree, String label, CharSequence content, int contentIndex, String pretty, int prettyIndex) {
            if (contentIndex == Diagnostic.NOPOS) {
                error("NOPOS for content " + label + ": " + tree.getKind() + " >>" + abbrev(pretty, MAX) + "<<");
            }

            char contentChar = content.charAt(contentIndex);
            char prettyChar = pretty.charAt(prettyIndex);
            if (contentChar != prettyChar) {
                error ("Mismatch for content " + label + ": "
                        + "expect: '" + prettyChar + "', found: '" + contentChar + "' at position " + contentIndex + ": "
                        + tree.getKind() + " >>" + abbrev(pretty, MAX) + "<<");
            }
        }

        static final int MAX = 64;

        static String abbrev(String s, int max) {
            s = s.replaceAll("\\s+", " ");
            if (s.length() > max) {
                s = s.substring(0, max / 2 - 2) + " ... " + s.substring(max / 2 + 2);
            }
            return s;
        }

    }
}

