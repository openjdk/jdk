/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac.tree;

import com.sun.source.doctree.AttributeTree.ValueKind;
import com.sun.source.doctree.DocTree.Kind;

import com.sun.tools.javac.parser.Tokens.Comment;
import com.sun.tools.javac.tree.DCTree.*;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.DiagnosticSource;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Position;

/**
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class DocTreeMaker {

    /** The context key for the tree factory. */
    protected static final Context.Key<DocTreeMaker> treeMakerKey =
        new Context.Key<DocTreeMaker>();

    /** Get the TreeMaker instance. */
    public static DocTreeMaker instance(Context context) {
        DocTreeMaker instance = context.get(treeMakerKey);
        if (instance == null)
            instance = new DocTreeMaker(context);
        return instance;
    }

    /** The position at which subsequent trees will be created.
     */
    public int pos = Position.NOPOS;

    /** Access to diag factory for ErroneousTrees. */
    private final JCDiagnostic.Factory diags;

    /** Create a tree maker with NOPOS as initial position.
     */
    protected DocTreeMaker(Context context) {
        context.put(treeMakerKey, this);
        diags = JCDiagnostic.Factory.instance(context);
        this.pos = Position.NOPOS;
    }

    /** Reassign current position.
     */
    public DocTreeMaker at(int pos) {
        this.pos = pos;
        return this;
    }

    /** Reassign current position.
     */
    public DocTreeMaker at(DiagnosticPosition pos) {
        this.pos = (pos == null ? Position.NOPOS : pos.getStartPosition());
        return this;
    }

    public DCAttribute Attribute(Name name, ValueKind vkind, List<DCTree> value) {
        DCAttribute tree = new DCAttribute(name, vkind, value);
        tree.pos = pos;
        return tree;
    }

    public DCAuthor Author(List<DCTree> name) {
        DCAuthor tree = new DCAuthor(name);
        tree.pos = pos;
        return tree;
    }

    public DCLiteral Code(DCText text) {
        DCLiteral tree = new DCLiteral(Kind.CODE, text);
        tree.pos = pos;
        return tree;
    }

    public DCComment Comment(String text) {
        DCComment tree = new DCComment(text);
        tree.pos = pos;
        return tree;
    }

    public DCDeprecated Deprecated(List<DCTree> text) {
        DCDeprecated tree = new DCDeprecated(text);
        tree.pos = pos;
        return tree;
    }

    public DCDocComment DocComment(Comment comment, List<DCTree> firstSentence, List<DCTree> body, List<DCTree> tags) {
        DCDocComment tree = new DCDocComment(comment, firstSentence, body, tags);
        tree.pos = pos;
        return tree;
    }

    public DCDocRoot DocRoot() {
        DCDocRoot tree = new DCDocRoot();
        tree.pos = pos;
        return tree;
    }

    public DCEndElement EndElement(Name name) {
        DCEndElement tree = new DCEndElement(name);
        tree.pos = pos;
        return tree;
    }

    public DCEntity Entity(Name name) {
        DCEntity tree = new DCEntity(name);
        tree.pos = pos;
        return tree;
    }

    public DCErroneous Erroneous(String text, DiagnosticSource diagSource, String code, Object... args) {
        DCErroneous tree = new DCErroneous(text, diags, diagSource, code, args);
        tree.pos = pos;
        return tree;
    }

    public DCThrows Exception(DCReference name, List<DCTree> description) {
        DCThrows tree = new DCThrows(Kind.EXCEPTION, name, description);
        tree.pos = pos;
        return tree;
    }

    public DCIdentifier Identifier(Name name) {
        DCIdentifier tree = new DCIdentifier(name);
        tree.pos = pos;
        return tree;
    }

    public DCInheritDoc InheritDoc() {
        DCInheritDoc tree = new DCInheritDoc();
        tree.pos = pos;
        return tree;
    }

    public DCLink Link(DCReference ref, List<DCTree> label) {
        DCLink tree = new DCLink(Kind.LINK, ref, label);
        tree.pos = pos;
        return tree;
    }

    public DCLink LinkPlain(DCReference ref, List<DCTree> label) {
        DCLink tree = new DCLink(Kind.LINK_PLAIN, ref, label);
        tree.pos = pos;
        return tree;
    }

    public DCLiteral Literal(DCText text) {
        DCLiteral tree = new DCLiteral(Kind.LITERAL, text);
        tree.pos = pos;
        return tree;
    }

    public DCParam Param(boolean isTypeParameter, DCIdentifier name, List<DCTree> description) {
        DCParam tree = new DCParam(isTypeParameter, name, description);
        tree.pos = pos;
        return tree;
    }

    public DCReference Reference(String signature,
            JCTree qualExpr, Name member, List<JCTree> paramTypes) {
        DCReference tree = new DCReference(signature, qualExpr, member, paramTypes);
        tree.pos = pos;
        return tree;
    }

    public DCReturn Return(List<DCTree> description) {
        DCReturn tree = new DCReturn(description);
        tree.pos = pos;
        return tree;
    }

    public DCSee See(List<DCTree> reference) {
        DCSee tree = new DCSee(reference);
        tree.pos = pos;
        return tree;
    }

    public DCSerial Serial(List<DCTree> description) {
        DCSerial tree = new DCSerial(description);
        tree.pos = pos;
        return tree;
    }

    public DCSerialData SerialData(List<DCTree> description) {
        DCSerialData tree = new DCSerialData(description);
        tree.pos = pos;
        return tree;
    }

    public DCSerialField SerialField(DCIdentifier name, DCReference type, List<DCTree> description) {
        DCSerialField tree = new DCSerialField(name, type, description);
        tree.pos = pos;
        return tree;
    }

    public DCSince Since(List<DCTree> text) {
        DCSince tree = new DCSince(text);
        tree.pos = pos;
        return tree;
    }

    public DCStartElement StartElement(Name name, List<DCTree> attrs, boolean selfClosing) {
        DCStartElement tree = new DCStartElement(name, attrs, selfClosing);
        tree.pos = pos;
        return tree;
    }

    public DCText Text(String text) {
        DCText tree = new DCText(text);
        tree.pos = pos;
        return tree;
    }

    public DCThrows Throws(DCReference name, List<DCTree> description) {
        DCThrows tree = new DCThrows(Kind.THROWS, name, description);
        tree.pos = pos;
        return tree;
    }

    public DCUnknownBlockTag UnknownBlockTag(Name name, List<DCTree> content) {
        DCUnknownBlockTag tree = new DCUnknownBlockTag(name, content);
        tree.pos = pos;
        return tree;
    }

    public DCUnknownInlineTag UnknownInlineTag(Name name, List<DCTree> content) {
        DCUnknownInlineTag tree = new DCUnknownInlineTag(name, content);
        tree.pos = pos;
        return tree;
    }

    public DCValue Value(DCReference ref) {
        DCValue tree = new DCValue(ref);
        tree.pos = pos;
        return tree;
    }

    public DCVersion Version(List<DCTree> text) {
        DCVersion tree = new DCVersion(text);
        tree.pos = pos;
        return tree;
    }
}
