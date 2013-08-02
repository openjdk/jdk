/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.doclint;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
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
import com.sun.source.doctree.IdentifierTree;
import com.sun.source.doctree.InheritDocTree;
import com.sun.source.doctree.LinkTree;
import com.sun.source.doctree.LiteralTree;
import com.sun.source.doctree.ParamTree;
import com.sun.source.doctree.ReferenceTree;
import com.sun.source.doctree.ReturnTree;
import com.sun.source.doctree.SerialDataTree;
import com.sun.source.doctree.SerialFieldTree;
import com.sun.source.doctree.SinceTree;
import com.sun.source.doctree.StartElementTree;
import com.sun.source.doctree.TextTree;
import com.sun.source.doctree.ThrowsTree;
import com.sun.source.doctree.ValueTree;
import com.sun.source.doctree.VersionTree;
import com.sun.source.util.DocTreePath;
import com.sun.source.util.DocTreePathScanner;
import com.sun.source.util.TreePath;
import com.sun.tools.doclint.HtmlTag.AttrKind;
import com.sun.tools.javac.tree.DocPretty;
import static com.sun.tools.doclint.Messages.Group.*;


/**
 * Validate a doc comment.
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own
 * risk.  This code and its internal interfaces are subject to change
 * or deletion without notice.</b></p>
 */
public class Checker extends DocTreePathScanner<Void, Void> {
    final Env env;

    Set<Element> foundParams = new HashSet<>();
    Set<TypeMirror> foundThrows = new HashSet<>();
    Map<JavaFileObject, Set<String>> foundAnchors = new HashMap<>();
    boolean foundInheritDoc = false;
    boolean foundReturn = false;

    public enum Flag {
        TABLE_HAS_CAPTION,
        HAS_ELEMENT,
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

    private Deque<TagStackItem> tagStack; // TODO: maybe want to record starting tree as well
    private HtmlTag currHeaderTag;

    private final int implicitHeaderLevel;

    // <editor-fold defaultstate="collapsed" desc="Top level">

    Checker(Env env) {
        env.getClass();
        this.env = env;
        tagStack = new LinkedList<>();
        implicitHeaderLevel = env.implicitHeaderLevel;
    }

    public Void scan(DocCommentTree tree, TreePath p) {
        env.setCurrent(p, tree);

        boolean isOverridingMethod = !env.currOverriddenMethods.isEmpty();

        if (p.getLeaf() == p.getCompilationUnit()) {
            // If p points to a compilation unit, the implied declaration is the
            // package declaration (if any) for the compilation unit.
            // Handle this case specially, because doc comments are only
            // expected in package-info files.
            JavaFileObject fo = p.getCompilationUnit().getSourceFile();
            boolean isPkgInfo = fo.isNameCompatible("package-info", JavaFileObject.Kind.SOURCE);
            if (tree == null) {
                if (isPkgInfo)
                    reportMissing("dc.missing.comment");
                return null;
            } else {
                if (!isPkgInfo)
                    reportReference("dc.unexpected.comment");
            }
        } else {
            if (tree == null) {
                if (!isSynthetic() && !isOverridingMethod)
                    reportMissing("dc.missing.comment");
                return null;
            }
        }

        tagStack.clear();
        currHeaderTag = null;

        foundParams.clear();
        foundThrows.clear();
        foundInheritDoc = false;
        foundReturn = false;

        scan(new DocTreePath(p, tree), null);

        if (!isOverridingMethod) {
            switch (env.currElement.getKind()) {
                case METHOD:
                case CONSTRUCTOR: {
                    ExecutableElement ee = (ExecutableElement) env.currElement;
                    checkParamsDocumented(ee.getTypeParameters());
                    checkParamsDocumented(ee.getParameters());
                    switch (ee.getReturnType().getKind()) {
                        case VOID:
                        case NONE:
                            break;
                        default:
                            if (!foundReturn
                                    && !foundInheritDoc
                                    && !env.types.isSameType(ee.getReturnType(), env.java_lang_Void)) {
                                reportMissing("dc.missing.return");
                            }
                    }
                    checkThrowsDocumented(ee.getThrownTypes());
                }
            }
        }

        return null;
    }

    private void reportMissing(String code, Object... args) {
        env.messages.report(MISSING, Kind.WARNING, env.currPath.getLeaf(), code, args);
    }

    private void reportReference(String code, Object... args) {
        env.messages.report(REFERENCE, Kind.WARNING, env.currPath.getLeaf(), code, args);
    }

    @Override
    public Void visitDocComment(DocCommentTree tree, Void ignore) {
        super.visitDocComment(tree, ignore);
        for (TagStackItem tsi: tagStack) {
            if (tsi.tree.getKind() == DocTree.Kind.START_ELEMENT
                    && tsi.tag.endKind == HtmlTag.EndKind.REQUIRED) {
                StartElementTree t = (StartElementTree) tsi.tree;
                env.messages.error(HTML, t, "dc.tag.not.closed", t.getName());
            }
        }
        return null;
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Text and entities.">

    @Override
    public Void visitText(TextTree tree, Void ignore) {
        if (hasNonWhitespace(tree)) {
            checkAllowsText(tree);
            markEnclosingTag(Flag.HAS_TEXT);
        }
        return null;
    }

    @Override
    public Void visitEntity(EntityTree tree, Void ignore) {
        checkAllowsText(tree);
        markEnclosingTag(Flag.HAS_TEXT);
        String name = tree.getName().toString();
        if (name.startsWith("#")) {
            int v = name.toLowerCase().startsWith("#x")
                    ? Integer.parseInt(name.substring(2), 16)
                    : Integer.parseInt(name.substring(1), 10);
            if (!Entity.isValid(v)) {
                env.messages.error(HTML, tree, "dc.entity.invalid", name);
            }
        } else if (!Entity.isValid(name)) {
            env.messages.error(HTML, tree, "dc.entity.invalid", name);
        }
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

    @Override
    public Void visitStartElement(StartElementTree tree, Void ignore) {
        markEnclosingTag(Flag.HAS_ELEMENT);
        final Name treeName = tree.getName();
        final HtmlTag t = HtmlTag.get(treeName);
        if (t == null) {
            env.messages.error(HTML, tree, "dc.tag.unknown", treeName);
        } else {
            boolean done = false;
            for (TagStackItem tsi: tagStack) {
                if (tsi.tag.accepts(t)) {
                    while (tagStack.peek() != tsi) tagStack.pop();
                    done = true;
                    break;
                } else if (tsi.tag.endKind != HtmlTag.EndKind.OPTIONAL) {
                    done = true;
                    break;
                }
            }
            if (!done && HtmlTag.BODY.accepts(t)) {
                tagStack.clear();
            }

            checkStructure(tree, t);

            // tag specific checks
            switch (t) {
                // check for out of sequence headers, such as <h1>...</h1>  <h3>...</h3>
                case H1: case H2: case H3: case H4: case H5: case H6:
                    checkHeader(tree, t);
                    break;
            }

            if (t.flags.contains(HtmlTag.Flag.NO_NEST)) {
                for (TagStackItem i: tagStack) {
                    if (t == i.tag) {
                        env.messages.warning(HTML, tree, "dc.tag.nested.not.allowed", treeName);
                        break;
                    }
                }
            }
        }

        // check for self closing tags, such as <a id="name"/>
        if (tree.isSelfClosing()) {
            env.messages.error(HTML, tree, "dc.tag.self.closing", treeName);
        }

        try {
            TagStackItem parent = tagStack.peek();
            TagStackItem top = new TagStackItem(tree, t);
            tagStack.push(top);

            super.visitStartElement(tree, ignore);

            // handle attributes that may or may not have been found in start element
            if (t != null) {
                switch (t) {
                    case CAPTION:
                        if (parent != null && parent.tag == HtmlTag.TABLE)
                            parent.flags.add(Flag.TABLE_HAS_CAPTION);
                        break;

                    case IMG:
                        if (!top.attrs.contains(HtmlTag.Attr.ALT))
                            env.messages.error(ACCESSIBILITY, tree, "dc.no.alt.attr.for.image");
                        break;
                }
            }

            return null;
        } finally {

            if (t == null || t.endKind == HtmlTag.EndKind.NONE)
                tagStack.pop();
        }
    }

    private void checkStructure(StartElementTree tree, HtmlTag t) {
        Name treeName = tree.getName();
        TagStackItem top = tagStack.peek();
        switch (t.blockType) {
            case BLOCK:
                if (top == null || top.tag.accepts(t))
                    return;

                switch (top.tree.getKind()) {
                    case START_ELEMENT: {
                        if (top.tag.blockType == HtmlTag.BlockType.INLINE) {
                            Name name = ((StartElementTree) top.tree).getName();
                            env.messages.error(HTML, tree, "dc.tag.not.allowed.inline.element",
                                    treeName, name);
                            return;
                        }
                    }
                    break;

                    case LINK:
                    case LINK_PLAIN: {
                        String name = top.tree.getKind().tagName;
                        env.messages.error(HTML, tree, "dc.tag.not.allowed.inline.tag",
                                treeName, name);
                        return;
                    }
                }
                break;

            case INLINE:
                if (top == null || top.tag.accepts(t))
                    return;
                break;

            case LIST_ITEM:
            case TABLE_ITEM:
                if (top != null) {
                    // reset this flag so subsequent bad inline content gets reported
                    top.flags.remove(Flag.REPORTED_BAD_INLINE);
                    if (top.tag.accepts(t))
                        return;
                }
                break;

            case OTHER:
                env.messages.error(HTML, tree, "dc.tag.not.allowed", treeName);
                return;
        }

        env.messages.error(HTML, tree, "dc.tag.not.allowed.here", treeName);
    }

    private void checkHeader(StartElementTree tree, HtmlTag tag) {
        // verify the new tag
        if (getHeaderLevel(tag) > getHeaderLevel(currHeaderTag) + 1) {
            if (currHeaderTag == null) {
                env.messages.error(ACCESSIBILITY, tree, "dc.tag.header.sequence.1", tag);
            } else {
                env.messages.error(ACCESSIBILITY, tree, "dc.tag.header.sequence.2",
                    tag, currHeaderTag);
            }
        }

        currHeaderTag = tag;
    }

    private int getHeaderLevel(HtmlTag tag) {
        if (tag == null)
            return implicitHeaderLevel;
        switch (tag) {
            case H1: return 1;
            case H2: return 2;
            case H3: return 3;
            case H4: return 4;
            case H5: return 5;
            case H6: return 6;
            default: throw new IllegalArgumentException();
        }
    }

    @Override
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
                        case TABLE:
                            if (!top.attrs.contains(HtmlTag.Attr.SUMMARY)
                                    && !top.flags.contains(Flag.TABLE_HAS_CAPTION)) {
                                env.messages.error(ACCESSIBILITY, tree,
                                        "dc.no.summary.or.caption.for.table");
                            }
                    }
                    if (t.flags.contains(HtmlTag.Flag.EXPECT_CONTENT)
                            && !top.flags.contains(Flag.HAS_TEXT)
                            && !top.flags.contains(Flag.HAS_ELEMENT)
                            && !top.flags.contains(Flag.HAS_INLINE_TAG)) {
                        env.messages.warning(HTML, tree, "dc.tag.empty", treeName);
                    }
                    tagStack.pop();
                    done = true;
                    break;
                } else if (top.tag == null || top.tag.endKind != HtmlTag.EndKind.REQUIRED) {
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
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="HTML attributes">

    @Override @SuppressWarnings("fallthrough")
    public Void visitAttribute(AttributeTree tree, Void ignore) {
        HtmlTag currTag = tagStack.peek().tag;
        if (currTag != null) {
            Name name = tree.getName();
            HtmlTag.Attr attr = currTag.getAttr(name);
            if (attr != null) {
                boolean first = tagStack.peek().attrs.add(attr);
                if (!first)
                    env.messages.error(HTML, tree, "dc.attr.repeated", name);
            }
            AttrKind k = currTag.getAttrKind(name);
            switch (k) {
                case OK:
                    break;

                case INVALID:
                    env.messages.error(HTML, tree, "dc.attr.unknown", name);
                    break;

                case OBSOLETE:
                    env.messages.warning(ACCESSIBILITY, tree, "dc.attr.obsolete", name);
                    break;

                case USE_CSS:
                    env.messages.warning(ACCESSIBILITY, tree, "dc.attr.obsolete.use.css", name);
                    break;
            }

            if (attr != null) {
                switch (attr) {
                    case NAME:
                        if (currTag != HtmlTag.A) {
                            break;
                        }
                        // fallthrough
                    case ID:
                        String value = getAttrValue(tree);
                        if (value == null) {
                            env.messages.error(HTML, tree, "dc.anchor.value.missing");
                        } else {
                            if (!validName.matcher(value).matches()) {
                                env.messages.error(HTML, tree, "dc.invalid.anchor", value);
                            }
                            if (!checkAnchor(value)) {
                                env.messages.error(HTML, tree, "dc.anchor.already.defined", value);
                            }
                        }
                        break;

                    case HREF:
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
                        break;

                    case VALUE:
                        if (currTag == HtmlTag.LI) {
                            String v = getAttrValue(tree);
                            if (v == null || v.isEmpty()) {
                                env.messages.error(HTML, tree, "dc.attr.lacks.value");
                            } else if (!validNumber.matcher(v).matches()) {
                                env.messages.error(HTML, tree, "dc.attr.not.number");
                            }
                        }
                        break;
                }
            }
        }

        // TODO: basic check on value

        return super.visitAttribute(tree, ignore);
    }

    private boolean checkAnchor(String name) {
        JavaFileObject fo = env.currPath.getCompilationUnit().getSourceFile();
        Set<String> set = foundAnchors.get(fo);
        if (set == null)
            foundAnchors.put(fo, set = new HashSet<>());
        return set.add(name);
    }

    // http://www.w3.org/TR/html401/types.html#type-name
    private static final Pattern validName = Pattern.compile("[A-Za-z][A-Za-z0-9-_:.]*");

    private static final Pattern validNumber = Pattern.compile("-?[0-9]+");

    // pattern to remove leading {@docRoot}/?
    private static final Pattern docRoot = Pattern.compile("(?i)(\\{@docRoot *\\}/?)?(.*)");

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
        try {
            URI u = new URI(uri);
        } catch (URISyntaxException e) {
            env.messages.error(HTML, tree, "dc.invalid.uri", uri);
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="javadoc tags">

    @Override
    public Void visitAuthor(AuthorTree tree, Void ignore) {
        warnIfEmpty(tree, tree.getName());
        return super.visitAuthor(tree, ignore);
    }

    @Override
    public Void visitDocRoot(DocRootTree tree, Void ignore) {
        markEnclosingTag(Flag.HAS_INLINE_TAG);
        return super.visitDocRoot(tree, ignore);
    }

    @Override
    public Void visitInheritDoc(InheritDocTree tree, Void ignore) {
        markEnclosingTag(Flag.HAS_INLINE_TAG);
        // TODO: verify on overridden method
        foundInheritDoc = true;
        return super.visitInheritDoc(tree, ignore);
    }

    @Override
    public Void visitLink(LinkTree tree, Void ignore) {
        markEnclosingTag(Flag.HAS_INLINE_TAG);
        // simulate inline context on tag stack
        HtmlTag t = (tree.getKind() == DocTree.Kind.LINK)
                ? HtmlTag.CODE : HtmlTag.SPAN;
        tagStack.push(new TagStackItem(tree, t));
        try {
            return super.visitLink(tree, ignore);
        } finally {
            tagStack.pop();
        }
    }

    @Override
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

    @Override
    @SuppressWarnings("fallthrough")
    public Void visitParam(ParamTree tree, Void ignore) {
        boolean typaram = tree.isTypeParameter();
        IdentifierTree nameTree = tree.getName();
        Element paramElement = nameTree != null ? env.trees.getElement(new DocTreePath(getCurrentPath(), nameTree)) : null;

        if (paramElement == null) {
            switch (env.currElement.getKind()) {
                case CLASS: case INTERFACE: {
                    if (!typaram) {
                        env.messages.error(REFERENCE, tree, "dc.invalid.param");
                        break;
                    }
                }
                case METHOD: case CONSTRUCTOR: {
                    env.messages.error(REFERENCE, nameTree, "dc.param.name.not.found");
                    break;
                }

                default:
                    env.messages.error(REFERENCE, tree, "dc.invalid.param");
                    break;
            }
        } else {
            foundParams.add(paramElement);
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

    @Override
    public Void visitReference(ReferenceTree tree, Void ignore) {
        Element e = env.trees.getElement(getCurrentPath());
        if (e == null)
            env.messages.error(REFERENCE, tree, "dc.ref.not.found");
        return super.visitReference(tree, ignore);
    }

    @Override
    public Void visitReturn(ReturnTree tree, Void ignore) {
        Element e = env.trees.getElement(env.currPath);
        if (e.getKind() != ElementKind.METHOD
                || ((ExecutableElement) e).getReturnType().getKind() == TypeKind.VOID)
            env.messages.error(REFERENCE, tree, "dc.invalid.return");
        foundReturn = true;
        warnIfEmpty(tree, tree.getDescription());
        return super.visitReturn(tree, ignore);
    }

    @Override
    public Void visitSerialData(SerialDataTree tree, Void ignore) {
        warnIfEmpty(tree, tree.getDescription());
        return super.visitSerialData(tree, ignore);
    }

    @Override
    public Void visitSerialField(SerialFieldTree tree, Void ignore) {
        warnIfEmpty(tree, tree.getDescription());
        return super.visitSerialField(tree, ignore);
    }

    @Override
    public Void visitSince(SinceTree tree, Void ignore) {
        warnIfEmpty(tree, tree.getBody());
        return super.visitSince(tree, ignore);
    }

    @Override
    public Void visitThrows(ThrowsTree tree, Void ignore) {
        ReferenceTree exName = tree.getExceptionName();
        Element ex = env.trees.getElement(new DocTreePath(getCurrentPath(), exName));
        if (ex == null) {
            env.messages.error(REFERENCE, tree, "dc.ref.not.found");
        } else if (isThrowable(ex.asType())) {
            switch (env.currElement.getKind()) {
                case CONSTRUCTOR:
                case METHOD:
                    if (isCheckedException(ex.asType())) {
                        ExecutableElement ee = (ExecutableElement) env.currElement;
                        checkThrowsDeclared(exName, ex.asType(), ee.getThrownTypes());
                    }
                    break;
                default:
                    env.messages.error(REFERENCE, tree, "dc.invalid.throws");
            }
        } else {
            env.messages.error(REFERENCE, tree, "dc.invalid.throws");
        }
        warnIfEmpty(tree, tree.getDescription());
        return scan(tree.getDescription(), ignore);
    }

    private boolean isThrowable(TypeMirror tm) {
        switch (tm.getKind()) {
            case DECLARED:
            case TYPEVAR:
                return env.types.isAssignable(tm, env.java_lang_Throwable);
        }
        return false;
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

    @Override
    public Void visitValue(ValueTree tree, Void ignore) {
        markEnclosingTag(Flag.HAS_INLINE_TAG);
        return super.visitValue(tree, ignore);
    }

    @Override
    public Void visitVersion(VersionTree tree, Void ignore) {
        warnIfEmpty(tree, tree.getBody());
        return super.visitVersion(tree, ignore);
    }

    @Override
    public Void visitErroneous(ErroneousTree tree, Void ignore) {
        env.messages.error(SYNTAX, tree, null, tree.getDiagnostic().getMessage(null));
        return null;
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Utility methods">

    private boolean isCheckedException(TypeMirror t) {
        return !(env.types.isAssignable(t, env.java_lang_Error)
                || env.types.isAssignable(t, env.java_lang_RuntimeException));
    }

    private boolean isSynthetic() {
        switch (env.currElement.getKind()) {
            case CONSTRUCTOR:
                // A synthetic default constructor has the same pos as the
                // enclosing class
                TreePath p = env.currPath;
                return env.getPos(p) == env.getPos(p.getParentPath());
        }
        return false;
    }

    void markEnclosingTag(Flag flag) {
        TagStackItem top = tagStack.peek();
        if (top != null)
            top.flags.add(flag);
    }

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
            switch (d.getKind()) {
                case TEXT:
                    if (hasNonWhitespace((TextTree) d))
                        return;
                    break;
                default:
                    return;
            }
        }
        env.messages.warning(SYNTAX, tree, "dc.empty", tree.getKind().tagName);
    }

    boolean hasNonWhitespace(TextTree tree) {
        String s = tree.getBody();
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isWhitespace(s.charAt(i)))
                return true;
        }
        return false;
    }

    // </editor-fold>

}
