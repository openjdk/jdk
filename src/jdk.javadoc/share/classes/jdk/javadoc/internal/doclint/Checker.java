/*
 * Copyright (c) 2012, 2023, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.doclint;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic.Kind;
import javax.tools.JavaFileObject;

import com.sun.source.doctree.AttributeTree;
import com.sun.source.doctree.AuthorTree;
import com.sun.source.doctree.DocCommentTree;
import com.sun.source.doctree.DocRootTree;
import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.EndElementTree;
import com.sun.source.doctree.EntityTree;
import com.sun.source.doctree.ErroneousTree;
import com.sun.source.doctree.EscapeTree;
import com.sun.source.doctree.IdentifierTree;
import com.sun.source.doctree.IndexTree;
import com.sun.source.doctree.InheritDocTree;
import com.sun.source.doctree.LinkTree;
import com.sun.source.doctree.LiteralTree;
import com.sun.source.doctree.ParamTree;
import com.sun.source.doctree.ProvidesTree;
import com.sun.source.doctree.ReferenceTree;
import com.sun.source.doctree.ReturnTree;
import com.sun.source.doctree.SerialDataTree;
import com.sun.source.doctree.SerialFieldTree;
import com.sun.source.doctree.SinceTree;
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
import com.sun.source.tree.Tree;
import com.sun.source.util.DocTreePath;
import com.sun.source.util.DocTreePathScanner;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.tree.DocPretty;
import com.sun.tools.javac.util.Assert;
import com.sun.tools.javac.util.DefinedBy;
import com.sun.tools.javac.util.DefinedBy.Api;

import jdk.javadoc.internal.doclint.HtmlTag.AttrKind;
import jdk.javadoc.internal.doclint.HtmlTag.ElemKind;
import static jdk.javadoc.internal.doclint.Messages.Group.*;


/**
 * Validate a doc comment.
 */
public class Checker extends DocTreePathScanner<Void, Void> {
    final Env env;

    Set<Element> foundParams = new HashSet<>();
    Set<TypeMirror> foundThrows = new HashSet<>();
    Map<Element, Set<String>> foundAnchors = new HashMap<>();
    boolean foundInheritDoc = false;
    boolean foundReturn = false;
    boolean hasNonWhitespaceText = false;

    public enum Flag {
        TABLE_HAS_CAPTION,
        TABLE_IS_PRESENTATION,
        HAS_ELEMENT,
        HAS_HEADING,
        HAS_INLINE_TAG,
        HAS_TEXT,
        REPORTED_BAD_INLINE
    }

    static class TagStackItem {
        final DocTree tree; // typically, but not always, StartElementTree
        final HtmlTag tag;
        final Set<HtmlTag.Attr> attrs;
        final Set<Flag> flags;
        TagStackItem(DocTree tree, HtmlTag tag) {
            this.tree = tree;
            this.tag = tag;
            attrs = EnumSet.noneOf(HtmlTag.Attr.class);
            flags = EnumSet.noneOf(Flag.class);
        }
        @Override
        public String toString() {
            return String.valueOf(tag);
        }
    }

    private final Deque<TagStackItem> tagStack; // TODO: maybe want to record starting tree as well
    private HtmlTag currHeadingTag;

    private int implicitHeadingRank;
    private boolean inIndex;
    private boolean inLink;
    private boolean inSummary;

    // <editor-fold defaultstate="collapsed" desc="Top level">

    Checker(Env env) {
        this.env = Assert.checkNonNull(env);
        tagStack = new LinkedList<>();
    }

    public Void scan(DocCommentTree tree, TreePath p) {
        env.initTypes();
        env.setCurrent(p, tree);

        boolean isOverridingMethod = !env.currOverriddenMethods.isEmpty();
        JavaFileObject fo = p.getCompilationUnit().getSourceFile();

        if (p.getLeaf().getKind() == Tree.Kind.PACKAGE) {
            // If p points to a package, the implied declaration is the
            // package declaration (if any) for the compilation unit.
            // Handle this case specially, because doc comments are only
            // expected in package-info files.
            boolean isPkgInfo = fo.isNameCompatible("package-info", JavaFileObject.Kind.SOURCE);
            if (tree == null) {
                if (isPkgInfo)
                    reportMissing("dc.missing.comment");
                return null;
            } else {
                if (!isPkgInfo)
                    reportReference("dc.unexpected.comment");
            }
        } else if (tree != null && fo.isNameCompatible("package", JavaFileObject.Kind.HTML)) {
            // a package.html file with a DocCommentTree
            if (tree.getFullBody().isEmpty()) {
                reportMissing("dc.missing.comment");
                return null;
            }
        } else {
            if (tree == null) {
                if (isDefaultConstructor()) {
                    if (isNormalClass(p.getParentPath())) {
                        reportMissing("dc.default.constructor");
                    }
                } else if (!isOverridingMethod && !isSynthetic() && !isAnonymous() && !isRecordComponentOrField()
                        && !isImplicitlyDeclaredClass(env.currPath.getLeaf())) {
                    reportMissing("dc.missing.comment");
                }
                return null;
            } else if (tree.getFirstSentence().isEmpty() && !isOverridingMethod && !pseudoElement(p)) {
                if (tree.getBlockTags().isEmpty()) {
                    reportMissing("dc.empty.comment");
                    return null;
                } else {
                    // Don't report an empty description if the comment contains @deprecated,
                    // because javadoc will use the content of that tag in summary tables.
                    if (tree.getBlockTags().stream().allMatch(t -> t.getKind() != DocTree.Kind.DEPRECATED)) {
                        env.messages.report(MISSING, Kind.WARNING, tree, "dc.empty.main.description");
                    }
                }
            }
        }

        tagStack.clear();
        currHeadingTag = null;

        foundParams.clear();
        foundThrows.clear();
        foundInheritDoc = false;
        foundReturn = false;
        hasNonWhitespaceText = false;

        implicitHeadingRank = switch (p.getLeaf().getKind()) {
            // the following are for declarations that have their own top-level page,
            // and so the doc comment comes after the <h1> page title.
            case MODULE, PACKAGE, CLASS, INTERFACE, ENUM, ANNOTATION_TYPE, RECORD -> 1;


            // this is for html files
            // ... if it is a legacy package.html, the doc comment comes after the <h1> page title
            // ... otherwise, (e.g. overview file and doc-files/**/*.html files) no additional headings are inserted
            case COMPILATION_UNIT -> fo.isNameCompatible("package", JavaFileObject.Kind.HTML) ? 1 : 0;


            // the following are for member declarations, which appear in the page
            // for the enclosing type, and so appear after the <h2> "Members"
            // aggregate heading and the specific <h3> "Member signature" heading.
            case METHOD, VARIABLE ->  3;

            default -> throw new AssertionError("unexpected tree kind: " + p.getLeaf().getKind() + " " + fo);
        };

        scan(new DocTreePath(p, tree), null);

        // the following checks are made after the scan, which will record @param tags
        if (isDeclaredType()) {
            TypeElement te = (TypeElement) env.currElement;
            checkParamsDocumented(te.getTypeParameters());
            checkParamsDocumented(te.getRecordComponents());
        } else if (isExecutable()) {
            if (!isOverridingMethod) {
                ExecutableElement ee = (ExecutableElement) env.currElement;
                if (!isCanonicalRecordConstructor(ee)) {
                    checkParamsDocumented(ee.getTypeParameters());
                    checkParamsDocumented(ee.getParameters());
                }
                switch (ee.getReturnType().getKind()) {
                    case VOID, NONE -> { }
                    default -> {
                        if (!foundReturn
                                && !foundInheritDoc
                                && !env.types.isSameType(ee.getReturnType(), env.java_lang_Void)) {
                            reportMissing("dc.missing.return");
                        }
                    }
                }
                checkThrowsDocumented(ee.getThrownTypes());
            }
        }

        return null;
    }

    private boolean isCanonicalRecordConstructor(ExecutableElement ee) {
        TypeElement te = (TypeElement) ee.getEnclosingElement();
        if (te.getKind() != ElementKind.RECORD) {
            return false;
        }
        List<? extends RecordComponentElement> stateComps = te.getRecordComponents();
        List<? extends VariableElement> params = ee.getParameters();
        if (stateComps.size() != params.size()) {
            return false;
        }

        Iterator<? extends RecordComponentElement> stateIter = stateComps.iterator();
        Iterator<? extends VariableElement> paramIter = params.iterator();
        while (paramIter.hasNext() && stateIter.hasNext()) {
            VariableElement param = paramIter.next();
            RecordComponentElement comp = stateIter.next();
            if (!Objects.equals(param.getSimpleName(), comp.getSimpleName())
                    || !env.types.isSameType(param.asType(), comp.asType())) {
                return false;
            }
        }

        return true;
    }

    // Checks if the passed tree path corresponds to an entity, such as
    // the overview file and doc-files/**/*.html files.
    private boolean pseudoElement(TreePath p) {
        return p.getLeaf().getKind() == Tree.Kind.COMPILATION_UNIT
                && p.getCompilationUnit().getSourceFile().getKind() == JavaFileObject.Kind.HTML;
    }

    private void reportMissing(String code, Object... args) {
        env.messages.report(MISSING, Kind.WARNING, env.currPath.getLeaf(), code, args);
    }

    private void reportReference(String code, Object... args) {
        env.messages.report(REFERENCE, Kind.WARNING, env.currPath.getLeaf(), code, args);
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public Void visitDocComment(DocCommentTree tree, Void ignore) {
        scan(tree.getFirstSentence(), ignore);
        scan(tree.getBody(), ignore);
        checkTagStack();

        for (DocTree blockTag : tree.getBlockTags()) {
            tagStack.clear();
            scan(blockTag, ignore);
            checkTagStack();
        }

        return null;
    }

    private void checkTagStack() {
        for (TagStackItem tsi: tagStack) {
            warnIfEmpty(tsi, null);
            if (tsi.tree.getKind() == DocTree.Kind.START_ELEMENT
                    && tsi.tag.endKind == HtmlTag.EndKind.REQUIRED) {
                StartElementTree t = (StartElementTree) tsi.tree;
                env.messages.error(HTML, t, "dc.tag.not.closed", t.getName());
            }
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Text and entities.">

    @Override @DefinedBy(Api.COMPILER_TREE)
    public Void visitText(TextTree tree, Void ignore) {
        hasNonWhitespaceText = hasNonWhitespace(tree);
        if (hasNonWhitespaceText) {
            checkAllowsText(tree);
            markEnclosingTag(Flag.HAS_TEXT);
        }
        return null;
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public Void visitEntity(EntityTree tree, Void ignore) {
        hasNonWhitespaceText = true;
        checkAllowsText(tree);
        markEnclosingTag(Flag.HAS_TEXT);
        String s = env.trees.getCharacters(tree);
        if (s == null) {
            env.messages.error(HTML, tree, "dc.entity.invalid", tree.getName());
        }
        return null;

    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public Void visitEscape(EscapeTree tree, Void ignore) {
        hasNonWhitespaceText = true;
        checkAllowsText(tree);
        markEnclosingTag(Flag.HAS_TEXT);
        return null;
    }

    void checkAllowsText(DocTree tree) {
        TagStackItem top = tagStack.peek();
        if (top != null
                && top.tree.getKind() == DocTree.Kind.START_ELEMENT
                && !top.tag.acceptsText()) {
            if (top.flags.add(Flag.REPORTED_BAD_INLINE)) {
                env.messages.error(HTML, tree, "dc.text.not.allowed",
                        ((StartElementTree) top.tree).getName());
            }
        }
    }

    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="HTML elements">

    @Override @DefinedBy(Api.COMPILER_TREE)
    public Void visitStartElement(StartElementTree tree, Void ignore) {
        final Name treeName = tree.getName();
        final HtmlTag t = HtmlTag.get(treeName);
        if (t == null) {
            env.messages.error(HTML, tree, "dc.tag.unknown", treeName);
        } else if (t.elemKind == ElemKind.HTML4) {
            env.messages.error(HTML, tree, "dc.tag.not.supported.html5", treeName);
        } else {
            boolean done = false;
            for (TagStackItem tsi: tagStack) {
                if (tsi.tag.accepts(t)) {
                    while (tagStack.peek() != tsi) {
                        warnIfEmpty(tagStack.peek(), null);
                        tagStack.pop();
                    }
                    done = true;
                    break;
                } else if (tsi.tag.endKind != HtmlTag.EndKind.OPTIONAL) {
                    done = true;
                    break;
                }
            }
            if (!done && HtmlTag.BODY.accepts(t)) {
                while (!tagStack.isEmpty()) {
                    warnIfEmpty(tagStack.peek(), null);
                    tagStack.pop();
                }
            }

            markEnclosingTag(Flag.HAS_ELEMENT);
            checkStructure(tree, t);

            // tag specific checks
            switch (t) {
                // check for out of sequence headings, such as <h1>...</h1>  <h3>...</h3>
                case H1, H2, H3, H4, H5, H6 -> checkHeading(tree, t);
            }

            if (t.flags.contains(HtmlTag.Flag.NO_NEST)) {
                for (TagStackItem i: tagStack) {
                    if (t == i.tag) {
                        env.messages.warning(HTML, tree, "dc.tag.nested.not.allowed", treeName);
                        break;
                    }
                }
            }

            // check for self-closing tags, such as <a id="name"/>
            if (tree.isSelfClosing() && !isSelfClosingAllowed(t)) {
                env.messages.error(HTML, tree, "dc.tag.self.closing", treeName);
            }
        }

        try {
            TagStackItem parent = tagStack.peek();
            TagStackItem top = new TagStackItem(tree, t);
            tagStack.push(top);

            super.visitStartElement(tree, ignore);

            // handle attributes that may or may not have been found in start element
            if (t != null) {
                switch (t) {
                    case CAPTION -> {
                        if (parent != null && parent.tag == HtmlTag.TABLE)
                            parent.flags.add(Flag.TABLE_HAS_CAPTION);
                    }

                    case H1, H2, H3, H4, H5, H6 -> {
                        if (parent != null && (parent.tag == HtmlTag.SECTION || parent.tag == HtmlTag.ARTICLE)) {
                            parent.flags.add(Flag.HAS_HEADING);
                        }
                    }

                    case IMG -> {
                        if (!top.attrs.contains(HtmlTag.Attr.ALT))
                            env.messages.error(ACCESSIBILITY, tree, "dc.no.alt.attr.for.image");
                    }
                }
            }

            return null;
        } finally {

            if (t == null || t.endKind == HtmlTag.EndKind.NONE)
                tagStack.pop();
        }
    }

    // so-called "self-closing" tags are only permitted in HTML 5, for void elements
    // https://html.spec.whatwg.org/multipage/syntax.html#start-tags
    private boolean isSelfClosingAllowed(HtmlTag tag) {
        return tag.endKind == HtmlTag.EndKind.NONE;
    }

    private void checkStructure(StartElementTree tree, HtmlTag t) {
        Name treeName = tree.getName();
        TagStackItem top = tagStack.peek();
        switch (t.blockType) {
            case BLOCK -> {
                if (top == null || top.tag.accepts(t))
                    return;

                switch (top.tree.getKind()) {
                    case START_ELEMENT -> {
                        if (top.tag.blockType == HtmlTag.BlockType.INLINE) {
                            Name name = ((StartElementTree) top.tree).getName();
                            // Links may use block display style so issue warning instead of error
                            if ("a".equalsIgnoreCase(name.toString())) {
                                env.messages.warning(HTML, tree, "dc.tag.not.allowed.element.default.style",
                                        treeName, name);
                            } else {
                                env.messages.error(HTML, tree, "dc.tag.not.allowed.inline.element",
                                        treeName, name);
                            }
                            return;
                        }
                    }

                    case LINK, LINK_PLAIN -> {
                        String name = top.tree.getKind().tagName;
                        env.messages.warning(HTML, tree, "dc.tag.not.allowed.tag.default.style",
                                treeName, name);
                        return;
                    }
                }
            }

            case INLINE -> {
                if (top == null || top.tag.accepts(t))
                    return;
            }

            case LIST_ITEM, TABLE_ITEM -> {
                if (top != null) {
                    // reset this flag so subsequent bad inline content gets reported
                    top.flags.remove(Flag.REPORTED_BAD_INLINE);
                    if (top.tag.accepts(t))
                        return;
                }
            }

            case OTHER -> {
                switch (t) {
                    case SCRIPT -> {
                        // <script> may or may not be allowed, depending on --allow-script-in-comments,
                        // but we allow it here, and rely on a separate scanner to detect all uses
                        // of JavaScript, including <script> tags, and use in attributes, etc.
                    }

                    default ->
                        env.messages.error(HTML, tree, "dc.tag.not.allowed", treeName);
                }
                return;
            }
        }

        env.messages.error(HTML, tree, "dc.tag.not.allowed.here", treeName);
    }

    private void checkHeading(StartElementTree tree, HtmlTag tag) {
        // verify the new tag
        if (getHeadingRank(tag) > getHeadingRank(currHeadingTag) + 1) {
            if (currHeadingTag == null) {
                env.messages.error(ACCESSIBILITY, tree, "dc.tag.heading.sequence.1",
                        tag, implicitHeadingRank);
            } else {
                env.messages.error(ACCESSIBILITY, tree, "dc.tag.heading.sequence.2",
                    tag, currHeadingTag);
            }
        } else if (getHeadingRank(tag) <= implicitHeadingRank) {
            env.messages.error(ACCESSIBILITY, tree, "dc.tag.heading.sequence.3",
                    tag, implicitHeadingRank);
        }

        currHeadingTag = tag;
    }

    private int getHeadingRank(HtmlTag tag) {
        return (tag == null)
                ? implicitHeadingRank
                : switch (tag) {
                        case H1 -> 1;
                        case H2 -> 2;
                        case H3 -> 3;
                        case H4 -> 4;
                        case H5 -> 5;
                        case H6 -> 6;
                        default -> throw new IllegalArgumentException();
                    };
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public Void visitEndElement(EndElementTree tree, Void ignore) {
        final Name treeName = tree.getName();
        final HtmlTag t = HtmlTag.get(treeName);
        if (t == null) {
            env.messages.error(HTML, tree, "dc.tag.unknown", treeName);
        } else if (t.endKind == HtmlTag.EndKind.NONE) {
            env.messages.error(HTML, tree, "dc.tag.end.not.permitted", treeName);
        } else {
            boolean done = false;
            while (!tagStack.isEmpty()) {
                TagStackItem top = tagStack.peek();
                if (t == top.tag) {
                    switch (t) {
                        case TABLE -> {
                            if (!top.flags.contains(Flag.TABLE_IS_PRESENTATION)
                                    && !top.attrs.contains(HtmlTag.Attr.SUMMARY)
                                    && !top.flags.contains(Flag.TABLE_HAS_CAPTION)) {
                                env.messages.error(ACCESSIBILITY, tree,
                                        "dc.no.summary.or.caption.for.table");
                            }
                        }

                        case SECTION, ARTICLE -> {
                            if (!top.flags.contains(Flag.HAS_HEADING)) {
                                env.messages.error(HTML, tree, "dc.tag.requires.heading", treeName);
                            }
                        }
                    }
                    warnIfEmpty(top, tree);
                    tagStack.pop();
                    done = true;
                    break;
                } else if (top.tag == null || top.tag.endKind != HtmlTag.EndKind.REQUIRED) {
                    warnIfEmpty(top, null);
                    tagStack.pop();
                } else {
                    boolean found = false;
                    for (TagStackItem si: tagStack) {
                        if (si.tag == t) {
                            found = true;
                            break;
                        }
                    }
                    if (found && top.tree.getKind() == DocTree.Kind.START_ELEMENT) {
                        env.messages.error(HTML, top.tree, "dc.tag.start.unmatched",
                                ((StartElementTree) top.tree).getName());
                        tagStack.pop();
                    } else {
                        env.messages.error(HTML, tree, "dc.tag.end.unexpected", treeName);
                        done = true;
                        break;
                    }
                }
            }

            if (!done && tagStack.isEmpty()) {
                env.messages.error(HTML, tree, "dc.tag.end.unexpected", treeName);
            }
        }

        return super.visitEndElement(tree, ignore);
    }

    void warnIfEmpty(TagStackItem tsi, DocTree endTree) {
        if (tsi.tag != null && tsi.tree instanceof StartElementTree startTree) {
            if (tsi.tag.flags.contains(HtmlTag.Flag.EXPECT_CONTENT)
                    && !tsi.flags.contains(Flag.HAS_TEXT)
                    && !tsi.flags.contains(Flag.HAS_ELEMENT)
                    && !tsi.flags.contains(Flag.HAS_INLINE_TAG)
                    && !(tsi.tag.elemKind == ElemKind.HTML4)) {
                DocTree tree = (endTree != null) ? endTree : startTree;
                Name treeName = startTree.getName();
                env.messages.warning(HTML, tree, "dc.tag.empty", treeName);
            }
        }
    }

    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="HTML attributes">

    @Override @DefinedBy(Api.COMPILER_TREE) @SuppressWarnings("fallthrough")
    public Void visitAttribute(AttributeTree tree, Void ignore) {
        // for now, ensure we're in an HTML StartElementTree;
        // in time, we might check uses of attributes in other tree nodes
        if (getParentKind() != DocTree.Kind.START_ELEMENT) {
            return null;
        }

        HtmlTag currTag = tagStack.peek().tag;
        if (currTag != null && currTag.elemKind != ElemKind.HTML4) {
            Name name = tree.getName();
            HtmlTag.Attr attr = currTag.getAttr(name);
            if (attr != null) {
                boolean first = tagStack.peek().attrs.add(attr);
                if (!first)
                    env.messages.error(HTML, tree, "dc.attr.repeated", name);
            }
            // for now, doclint allows all attribute names beginning with "on" as event handler names,
            // without checking the validity or applicability of the name
            if (!name.toString().startsWith("on")) {
                AttrKind k = currTag.getAttrKind(name);
                switch (k) {
                    case OK -> { }

                    case OBSOLETE ->
                        env.messages.warning(HTML, tree, "dc.attr.obsolete", name);

                    case HTML4 ->
                        env.messages.error(HTML, tree, "dc.attr.not.supported.html5", name);

                    case INVALID ->
                        env.messages.error(HTML, tree, "dc.attr.unknown", name);
                }
            }

            if (attr != null) {
                switch (attr) {
                    case ID -> {
                        String value = getAttrValue(tree);
                        if (value == null) {
                            env.messages.error(HTML, tree, "dc.anchor.value.missing");
                        } else {
                            if (!validId.matcher(value).matches()) {
                                env.messages.error(HTML, tree, "dc.invalid.anchor", value);
                            }
                            if (!checkAnchor(value)) {
                                env.messages.error(HTML, tree, "dc.anchor.already.defined", value);
                            }
                        }
                    }

                    case HREF -> {
                        if (currTag == HtmlTag.A) {
                            String v = getAttrValue(tree);
                            if (v == null || v.isEmpty()) {
                                env.messages.error(HTML, tree, "dc.attr.lacks.value");
                            } else {
                                Matcher m = docRoot.matcher(v);
                                if (m.matches()) {
                                    String rest = m.group(2);
                                    if (!rest.isEmpty())
                                        checkURI(tree, rest);
                                } else {
                                    checkURI(tree, v);
                                }
                            }
                        }
                    }

                    case VALUE -> {
                        if (currTag == HtmlTag.LI) {
                            String v = getAttrValue(tree);
                            if (v == null || v.isEmpty()) {
                                env.messages.error(HTML, tree, "dc.attr.lacks.value");
                            } else if (!validNumber.matcher(v).matches()) {
                                env.messages.error(HTML, tree, "dc.attr.not.number");
                            }
                        }
                    }

                    case BORDER -> {
                        if (currTag == HtmlTag.TABLE) {
                            String v = getAttrValue(tree);
                            try {
                                if (v == null || (!v.isEmpty() && Integer.parseInt(v) != 1)) {
                                    env.messages.error(HTML, tree, "dc.attr.table.border.not.valid", attr);
                                }
                            } catch (NumberFormatException ex) {
                                env.messages.error(HTML, tree, "dc.attr.table.border.not.number", attr);
                            }
                        } else if (currTag == HtmlTag.IMG) {
                            String v = getAttrValue(tree);
                            try {
                                if (v == null || (!v.isEmpty() && Integer.parseInt(v) != 0)) {
                                    env.messages.error(HTML, tree, "dc.attr.img.border.not.valid", attr);
                                }
                            } catch (NumberFormatException ex) {
                                env.messages.error(HTML, tree, "dc.attr.img.border.not.number", attr);
                            }
                        }
                    }

                    case ROLE -> {
                        if (currTag == HtmlTag.TABLE) {
                            String v = getAttrValue(tree);
                            if (Objects.equals(v, "presentation")) {
                                tagStack.peek().flags.add(Flag.TABLE_IS_PRESENTATION);
                            }
                        }
                    }
                }
            }
        }

        // TODO: basic check on value

        return null;
    }


    private boolean checkAnchor(String name) {
        var e = getEnclosingPackageOrClass(env.currElement);
        return e == null || foundAnchors.computeIfAbsent(e, k -> new HashSet<>()).add(name);
    }

    private Element getEnclosingPackageOrClass(Element e) {
        while (e != null) {
            if (e.getKind().isDeclaredType() || e.getKind() == ElementKind.PACKAGE) {
                return e;
            }

            e = e.getEnclosingElement();
        }
        return e;
    }

    // https://html.spec.whatwg.org/#the-id-attribute
    private static final Pattern validId = Pattern.compile("[^\\s]+");

    private static final Pattern validNumber = Pattern.compile("-?[0-9]+");

    // pattern to remove leading {@docRoot}/?
    private static final Pattern docRoot = Pattern.compile("(?i)(\\{@docRoot *}/?)?(.*)");

    private String getAttrValue(AttributeTree tree) {
        if (tree.getValue() == null)
            return null;

        StringWriter sw = new StringWriter();
        try {
            new DocPretty(sw).print(tree.getValue());
        } catch (IOException e) {
            // cannot happen
        }
        // ignore potential use of entities for now
        return sw.toString();
    }

    private void checkURI(AttributeTree tree, String uri) {
        // allow URIs beginning with javascript:, which would otherwise be rejected by the URI API.
        if (uri.startsWith("javascript:"))
            return;
        try {
            new URI(uri);
        } catch (URISyntaxException e) {
            env.messages.error(HTML, tree, "dc.invalid.uri", uri);
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="javadoc tags">

    @Override @DefinedBy(Api.COMPILER_TREE)
    public Void visitAuthor(AuthorTree tree, Void ignore) {
        warnIfEmpty(tree, tree.getName());
        return super.visitAuthor(tree, ignore);
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public Void visitDocRoot(DocRootTree tree, Void ignore) {
        markEnclosingTag(Flag.HAS_INLINE_TAG);
        return super.visitDocRoot(tree, ignore);
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public Void visitIndex(IndexTree tree, Void ignore) {
        markEnclosingTag(Flag.HAS_INLINE_TAG);
        if (inIndex) {
            env.messages.warning(HTML, tree, "dc.tag.nested.tag", "@" + tree.getTagName());
        }
        for (TagStackItem tsi : tagStack) {
            if (tsi.tag == HtmlTag.A) {
                env.messages.warning(HTML, tree, "dc.tag.a.within.a",
                        "{@" + tree.getTagName() + "}");
                break;
            }
        }
        boolean prevInIndex = inIndex;
        try {
            inIndex = true;
            return super.visitIndex(tree, ignore);
        } finally {
            inIndex = prevInIndex;
        }
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public Void visitInheritDoc(InheritDocTree tree, Void ignore) {
        markEnclosingTag(Flag.HAS_INLINE_TAG);
        // TODO: verify on overridden method
        foundInheritDoc = true;
        return super.visitInheritDoc(tree, ignore);
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public Void visitLink(LinkTree tree, Void ignore) {
        markEnclosingTag(Flag.HAS_INLINE_TAG);
        if (inLink) {
            env.messages.warning(HTML, tree, "dc.tag.nested.tag", "@" + tree.getTagName());
        }
        boolean prevInLink = inLink;
        // simulate inline context on tag stack
        HtmlTag t = (tree.getKind() == DocTree.Kind.LINK)
                ? HtmlTag.CODE : HtmlTag.SPAN;
        tagStack.push(new TagStackItem(tree, t));
        try {
            inLink = true;
            return super.visitLink(tree, ignore);
        } finally {
            tagStack.pop();
            inLink = prevInLink;
        }
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public Void visitLiteral(LiteralTree tree, Void ignore) {
        markEnclosingTag(Flag.HAS_INLINE_TAG);
        if (tree.getKind() == DocTree.Kind.CODE) {
            for (TagStackItem tsi: tagStack) {
                if (tsi.tag == HtmlTag.CODE) {
                    env.messages.warning(HTML, tree, "dc.tag.code.within.code");
                    break;
                }
            }
        }
        return super.visitLiteral(tree, ignore);
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public Void visitParam(ParamTree tree, Void ignore) {
        boolean typaram = tree.isTypeParameter();
        IdentifierTree nameTree = tree.getName();
        Element paramElement = nameTree != null ? env.trees.getElement(new DocTreePath(getCurrentPath(), nameTree)) : null;

        if (paramElement == null) {
            switch (env.currElement.getKind()) {
                case CLASS, INTERFACE -> {
                    if (typaram) {
                        env.messages.error(REFERENCE, nameTree, "dc.param.name.not.found");
                    } else {
                        env.messages.error(REFERENCE, tree, "dc.invalid.param");
                    }
                }

                case METHOD, CONSTRUCTOR, RECORD ->
                    env.messages.error(REFERENCE, nameTree, "dc.param.name.not.found");

                default ->
                    env.messages.error(REFERENCE, tree, "dc.invalid.param");
            }
        } else {
            boolean unique = foundParams.add(paramElement);

            if (!unique) {
                env.messages.warning(REFERENCE, tree, "dc.exists.param", nameTree);
            }
        }

        warnIfEmpty(tree, tree.getDescription());
        return super.visitParam(tree, ignore);
    }

    private void checkParamsDocumented(List<? extends Element> list) {
        if (foundInheritDoc)
            return;

        for (Element e: list) {
            if (!foundParams.contains(e)) {
                CharSequence paramName = (e.getKind() == ElementKind.TYPE_PARAMETER)
                        ? "<" + e.getSimpleName() + ">"
                        : e.getSimpleName();
                reportMissing("dc.missing.param", paramName);
            }
        }
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public Void visitProvides(ProvidesTree tree, Void ignore) {
        Element e = env.trees.getElement(env.currPath);
        if (e.getKind() != ElementKind.MODULE) {
            env.messages.error(REFERENCE, tree, "dc.invalid.provides");
        }
        ReferenceTree serviceType = tree.getServiceType();
        Element se = env.trees.getElement(new DocTreePath(getCurrentPath(), serviceType));
        if (se == null) {
            env.messages.error(REFERENCE, tree, "dc.service.not.found");
        }
        return super.visitProvides(tree, ignore);
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public Void visitReference(ReferenceTree tree, Void ignore) {
        Element e = env.trees.getElement(getCurrentPath());
        if (e == null) {
            reportBadReference(tree);
        }
        return super.visitReference(tree, ignore);
    }

    private void reportBadReference(ReferenceTree tree) {
        if (!env.strictReferenceChecks) {
            String refSig = tree.getSignature();
            int sep = refSig.indexOf("/");
            if (sep > 0) {
                String moduleName = refSig.substring(0, sep);
                if (SourceVersion.isName(moduleName)) {
                    Element m = env.elements.getModuleElement(moduleName);
                    if (m == null) {
                        env.messages.warning(REFERENCE, tree, "dc.ref.in.missing.module", moduleName);
                        return;
                    }
                }
            }
        }

        env.messages.error(REFERENCE, tree, "dc.ref.not.found");
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public Void visitReturn(ReturnTree tree, Void ignore) {
        if (foundReturn) {
            env.messages.warning(REFERENCE, tree, "dc.exists.return");
        }
        if (tree.isInline()) {
            DocCommentTree dct = getCurrentPath().getDocComment();
            if (dct.getFirstSentence().isEmpty() || tree != dct.getFirstSentence().get(0)) {
                env.messages.warning(SYNTAX, tree, "dc.return.not.first");
            }
        }

        Element e = env.trees.getElement(env.currPath);
        if (e.getKind() != ElementKind.METHOD
                || ((ExecutableElement) e).getReturnType().getKind() == TypeKind.VOID)
            env.messages.error(REFERENCE, tree, "dc.invalid.return");
        foundReturn = true;
        warnIfEmpty(tree, tree.getDescription());
        return super.visitReturn(tree, ignore);
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public Void visitSerialData(SerialDataTree tree, Void ignore) {
        warnIfEmpty(tree, tree.getDescription());
        return super.visitSerialData(tree, ignore);
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public Void visitSerialField(SerialFieldTree tree, Void ignore) {
        warnIfEmpty(tree, tree.getDescription());
        return super.visitSerialField(tree, ignore);
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public Void visitSince(SinceTree tree, Void ignore) {
        warnIfEmpty(tree, tree.getBody());
        return super.visitSince(tree, ignore);
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public Void visitSummary(SummaryTree tree, Void aVoid) {
        markEnclosingTag(Flag.HAS_INLINE_TAG);
        if (inSummary) {
            env.messages.warning(HTML, tree, "dc.tag.nested.tag", "@" + tree.getTagName());
        }
        int idx = env.currDocComment.getFullBody().indexOf(tree);
        // Warn if the node is preceded by non-whitespace characters,
        // or other non-text nodes.
        if ((idx == 1 && hasNonWhitespaceText) || idx > 1) {
            env.messages.warning(SYNTAX, tree, "dc.invalid.summary", tree.getTagName());
        }
        boolean prevInSummary = inSummary;
        try {
            inSummary = true;
            return super.visitSummary(tree, aVoid);
        } finally {
            inSummary = prevInSummary;
        }
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public Void visitSystemProperty(SystemPropertyTree tree, Void ignore) {
        markEnclosingTag(Flag.HAS_INLINE_TAG);
        for (TagStackItem tsi : tagStack) {
            if (tsi.tag == HtmlTag.A) {
                env.messages.warning(HTML, tree, "dc.tag.a.within.a",
                        "{@" + tree.getTagName() + "}");
                break;
            }
        }
        return super.visitSystemProperty(tree, ignore);
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public Void visitThrows(ThrowsTree tree, Void ignore) {
        ReferenceTree exName = tree.getExceptionName();
        Element ex = env.trees.getElement(new DocTreePath(getCurrentPath(), exName));
        if (ex == null) {
            env.messages.error(REFERENCE, tree, "dc.ref.not.found");
        } else if (isThrowable(ex.asType())) {
            switch (env.currElement.getKind()) {
                case CONSTRUCTOR, METHOD -> {
                    if (isCheckedException(ex.asType())) {
                        ExecutableElement ee = (ExecutableElement) env.currElement;
                        checkThrowsDeclared(exName, ex.asType(), ee.getThrownTypes());
                    }
                }

                default ->
                    env.messages.error(REFERENCE, tree, "dc.invalid.throws");
            }
        } else {
            env.messages.error(REFERENCE, tree, "dc.invalid.throws");
        }
        warnIfEmpty(tree, tree.getDescription());
        return scan(tree.getDescription(), ignore);
    }

    private boolean isThrowable(TypeMirror tm) {
        return switch (tm.getKind()) {
            case DECLARED, TYPEVAR -> env.types.isAssignable(tm, env.java_lang_Throwable);
            default -> false;
        };
    }

    private void checkThrowsDeclared(ReferenceTree tree, TypeMirror t, List<? extends TypeMirror> list) {
        boolean found = false;
        for (TypeMirror tl : list) {
            if (env.types.isAssignable(t, tl)) {
                foundThrows.add(tl);
                found = true;
            }
        }
        if (!found)
            env.messages.error(REFERENCE, tree, "dc.exception.not.thrown", t);
    }

    private void checkThrowsDocumented(List<? extends TypeMirror> list) {
        if (foundInheritDoc)
            return;

        for (TypeMirror tl: list) {
            if (isCheckedException(tl) && !foundThrows.contains(tl))
                reportMissing("dc.missing.throws", tl);
        }
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public Void visitUnknownBlockTag(UnknownBlockTagTree tree, Void ignore) {
        checkUnknownTag(tree, tree.getTagName());
        return super.visitUnknownBlockTag(tree, ignore);
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public Void visitUnknownInlineTag(UnknownInlineTagTree tree, Void ignore) {
        markEnclosingTag(Flag.HAS_INLINE_TAG);
        checkUnknownTag(tree, tree.getTagName());
        return super.visitUnknownInlineTag(tree, ignore);
    }

    private void checkUnknownTag(DocTree tree, String tagName) {
        // if it were a standard tag, this method wouldn't be called:
        // a standard tag is never represented by Unknown{Block,Inline}TagTree
        var k = tree.getKind();
        assert k == DocTree.Kind.UNKNOWN_BLOCK_TAG
                || k == DocTree.Kind.UNKNOWN_INLINE_TAG;
        assert !getStandardTags().contains(tagName);
        // report an unknown tag only if custom tags are set, see 8314213
        if (env.customTags != null && !env.customTags.contains(tagName)) {
            var suggestions = DocLint.suggestSimilar(env.customTags, tagName);
            if (suggestions.isEmpty()) {
                env.messages.error(SYNTAX, tree, "dc.unknown.javadoc.tag");
            } else {
                env.messages.error(SYNTAX, tree, "dc.unknown.javadoc.tag.with.hint",
                        String.join(", ", suggestions)); // TODO: revisit after 8041488
            }
        }
    }

    private Set<String> getStandardTags() {
        return Stream.of(DocTree.Kind.values())
                .filter(k -> k.tagName != null) // not all DocTree represent tags
                .map(k -> k.tagName)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public Void visitUses(UsesTree tree, Void ignore) {
        Element e = env.trees.getElement(env.currPath);
        if (e.getKind() != ElementKind.MODULE) {
            env.messages.error(REFERENCE, tree, "dc.invalid.uses");
        }
        ReferenceTree serviceType = tree.getServiceType();
        Element se = env.trees.getElement(new DocTreePath(getCurrentPath(), serviceType));
        if (se == null) {
            env.messages.error(REFERENCE, tree, "dc.service.not.found");
        }
        return super.visitUses(tree, ignore);
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public Void visitValue(ValueTree tree, Void ignore) {
        ReferenceTree ref = tree.getReference();
        if (ref == null || ref.getSignature().isEmpty()) {
            if (!isConstant(env.currElement))
                env.messages.error(REFERENCE, tree, "dc.value.not.allowed.here");
        } else {
            Element e = env.trees.getElement(new DocTreePath(getCurrentPath(), ref));
            if (!isConstant(e))
                env.messages.error(REFERENCE, tree, "dc.value.not.a.constant");
        }
        TextTree format = tree.getFormat();
        if (format != null) {
            String f = format.getBody().toString();
            long count = format.getBody().toString().chars()
                    .filter(ch -> ch == '%')
                    .count();
            if (count != 1) {
                env.messages.error(REFERENCE, format, "dc.value.bad.format", f);
            }
        }

        markEnclosingTag(Flag.HAS_INLINE_TAG);
        return super.visitValue(tree, ignore);
    }

    private boolean isConstant(Element e) {
        if (e != null && e.getKind() == ElementKind.FIELD) {
            Object value = ((VariableElement) e).getConstantValue();
            return (value != null); // can't distinguish "not a constant" from "constant is null"
        } else {
            return false;
        }
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public Void visitVersion(VersionTree tree, Void ignore) {
        warnIfEmpty(tree, tree.getBody());
        return super.visitVersion(tree, ignore);
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public Void visitErroneous(ErroneousTree tree, Void ignore) {
        env.messages.error(SYNTAX, tree, null, tree.getDiagnostic().getMessage(null));
        return null;
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Utility methods">

    private DocTree.Kind getParentKind() {
        return getCurrentPath().getParentPath().getLeaf().getKind();
    }

    private boolean isCheckedException(TypeMirror t) {
        return !(env.types.isAssignable(t, env.java_lang_Error)
                || env.types.isAssignable(t, env.java_lang_RuntimeException));
    }

    private boolean isSynthetic() {
        return env.elements.getOrigin(env.currElement) == Elements.Origin.SYNTHETIC;
    }

    private boolean isAnonymous() {
        return (env.currElement instanceof TypeElement te)
                && te.getNestingKind() == NestingKind.ANONYMOUS;
    }

    private boolean isDefaultConstructor() {
        return env.currElement.getKind() == ElementKind.CONSTRUCTOR
                && env.elements.getOrigin(env.currElement) == Elements.Origin.MANDATED;
    }

    private boolean isDeclaredType() {
        ElementKind ek = env.currElement.getKind();
        return ek.isClass() || ek.isInterface();
    }

    private boolean isExecutable() {
        ElementKind ek = env.currElement.getKind();
        return switch (ek) {
            case CONSTRUCTOR, METHOD -> true;
            default -> false;
        };
    }

    private boolean isRecordComponentOrField() {
        return env.currElement.getKind() == ElementKind.RECORD_COMPONENT
            || env.currElement.getEnclosingElement() != null
                && env.currElement.getEnclosingElement().getKind() == ElementKind.RECORD
                && env.currElement.getKind() == ElementKind.FIELD;
    }

    private boolean isNormalClass(TreePath p) {
        return switch (p.getLeaf().getKind()) {
            case ENUM, RECORD -> false;
            case CLASS -> !isImplicitlyDeclaredClass(p.getLeaf());
            default -> throw new IllegalArgumentException(p.getLeaf().getKind().name());
        };
    }

    /*
     * If a similar query is ever added to com.sun.source.tree, use that instead.
     */
    private boolean isImplicitlyDeclaredClass(Tree t) {
        return t.getKind() == Tree.Kind.CLASS
                && t instanceof com.sun.tools.javac.tree.JCTree.JCClassDecl classDecl
                && (classDecl.mods.flags & com.sun.tools.javac.code.Flags.IMPLICIT_CLASS) != 0;
    }

    void markEnclosingTag(Flag flag) {
        TagStackItem top = tagStack.peek();
        if (top != null)
            top.flags.add(flag);
    }

    // for debug use
    String toString(TreePath p) {
        StringBuilder sb = new StringBuilder("TreePath[");
        toString(p, sb);
        sb.append("]");
        return sb.toString();
    }

    void toString(TreePath p, StringBuilder sb) {
        TreePath parent = p.getParentPath();
        if (parent != null) {
            toString(parent, sb);
            sb.append(",");
        }
       sb.append(p.getLeaf().getKind()).append(":").append(env.getPos(p)).append(":S").append(env.getStartPos(p));
    }

    void warnIfEmpty(DocTree tree, List<? extends DocTree> list) {
        for (DocTree d: list) {
            if (d.getKind() != DocTree.Kind.TEXT
                    || hasNonWhitespace((TextTree) d)) {
                return;
            }
        }
        env.messages.warning(MISSING, tree, "dc.empty", tree.getKind().tagName);
    }

    boolean hasNonWhitespace(TextTree tree) {
        return !tree.getBody().isBlank();
    }

    // </editor-fold>

}
