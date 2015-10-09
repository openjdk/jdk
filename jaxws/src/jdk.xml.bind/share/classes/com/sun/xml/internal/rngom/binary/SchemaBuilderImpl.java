/*
 * Copyright (c) 2005, 2015, Oracle and/or its affiliates. All rights reserved.
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
/*
 * Copyright (C) 2004-2012
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.sun.xml.internal.rngom.binary;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;

import com.sun.xml.internal.rngom.ast.builder.Annotations;
import com.sun.xml.internal.rngom.ast.builder.BuildException;
import com.sun.xml.internal.rngom.ast.builder.CommentList;
import com.sun.xml.internal.rngom.ast.builder.DataPatternBuilder;
import com.sun.xml.internal.rngom.ast.builder.Div;
import com.sun.xml.internal.rngom.ast.builder.ElementAnnotationBuilder;
import com.sun.xml.internal.rngom.ast.builder.Grammar;
import com.sun.xml.internal.rngom.ast.builder.GrammarSection;
import com.sun.xml.internal.rngom.ast.builder.Include;
import com.sun.xml.internal.rngom.ast.builder.IncludedGrammar;
import com.sun.xml.internal.rngom.ast.builder.NameClassBuilder;
import com.sun.xml.internal.rngom.ast.builder.SchemaBuilder;
import com.sun.xml.internal.rngom.ast.builder.Scope;
import com.sun.xml.internal.rngom.ast.om.Location;
import com.sun.xml.internal.rngom.ast.om.ParsedElementAnnotation;
import com.sun.xml.internal.rngom.ast.om.ParsedNameClass;
import com.sun.xml.internal.rngom.ast.om.ParsedPattern;
import com.sun.xml.internal.rngom.ast.util.LocatorImpl;
import com.sun.xml.internal.rngom.dt.builtin.BuiltinDatatypeLibraryFactory;
import com.sun.xml.internal.rngom.dt.CascadingDatatypeLibraryFactory;
import com.sun.xml.internal.rngom.nc.NameClass;
import com.sun.xml.internal.rngom.nc.NameClassBuilderImpl;
import com.sun.xml.internal.rngom.parse.Context;
import com.sun.xml.internal.rngom.parse.IllegalSchemaException;
import com.sun.xml.internal.rngom.parse.Parseable;
import com.sun.xml.internal.rngom.util.Localizer;
import com.sun.xml.internal.org.relaxng.datatype.Datatype;
import com.sun.xml.internal.org.relaxng.datatype.DatatypeBuilder;
import com.sun.xml.internal.org.relaxng.datatype.DatatypeException;
import com.sun.xml.internal.org.relaxng.datatype.DatatypeLibrary;
import com.sun.xml.internal.org.relaxng.datatype.DatatypeLibraryFactory;
import com.sun.xml.internal.org.relaxng.datatype.ValidationContext;
import com.sun.xml.internal.org.relaxng.datatype.helpers.DatatypeLibraryLoader;
import org.xml.sax.ErrorHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

public class SchemaBuilderImpl implements SchemaBuilder, ElementAnnotationBuilder, CommentList {

    private final SchemaBuilderImpl parent;
    private boolean hadError = false;
    private final SchemaPatternBuilder pb;
    private final DatatypeLibraryFactory datatypeLibraryFactory;
    private final String inheritNs;
    private final ErrorHandler eh;
    private final OpenIncludes openIncludes;
    private final NameClassBuilder ncb = new NameClassBuilderImpl();
    static final Localizer localizer = new Localizer(SchemaBuilderImpl.class);

    static class OpenIncludes {

        final String uri;
        final OpenIncludes parent;

        OpenIncludes(String uri, OpenIncludes parent) {
            this.uri = uri;
            this.parent = parent;
        }
    }

    public ParsedPattern expandPattern(ParsedPattern _pattern)
            throws BuildException, IllegalSchemaException {
        Pattern pattern = (Pattern) _pattern;
        if (!hadError) {
            try {
                pattern.checkRecursion(0);
                pattern = pattern.expand(pb);
                pattern.checkRestrictions(Pattern.START_CONTEXT, null, null);
                if (!hadError) {
                    return pattern;
                }
            } catch (SAXParseException e) {
                error(e);
            } catch (SAXException e) {
                throw new BuildException(e);
            } catch (RestrictionViolationException e) {
                if (e.getName() != null) {
                    error(e.getMessageId(), e.getName().toString(), e
                            .getLocator());
                } else {
                    error(e.getMessageId(), e.getLocator());
                }
            }
        }
        throw new IllegalSchemaException();
    }

    /**
     *
     * @param eh Error handler to receive errors while building the schema.
     */
    public SchemaBuilderImpl(ErrorHandler eh) {
        this(eh,
                new CascadingDatatypeLibraryFactory(new DatatypeLibraryLoader(),
                new BuiltinDatatypeLibraryFactory(new DatatypeLibraryLoader())),
                new SchemaPatternBuilder());
    }

    /**
     *
     * @param eh Error handler to receive errors while building the schema.
     * @param datatypeLibraryFactory This is consulted to locate datatype
     * libraries.
     * @param pb Used to build patterns.
     */
    public SchemaBuilderImpl(ErrorHandler eh,
            DatatypeLibraryFactory datatypeLibraryFactory,
            SchemaPatternBuilder pb) {
        this.parent = null;
        this.eh = eh;
        this.datatypeLibraryFactory = datatypeLibraryFactory;
        this.pb = pb;
        this.inheritNs = "";
        this.openIncludes = null;
    }

    private SchemaBuilderImpl(String inheritNs,
            String uri,
            SchemaBuilderImpl parent) {
        this.parent = parent;
        this.eh = parent.eh;
        this.datatypeLibraryFactory = parent.datatypeLibraryFactory;
        this.pb = parent.pb;
        this.inheritNs = inheritNs;
        this.openIncludes = new OpenIncludes(uri, parent.openIncludes);
    }

    public NameClassBuilder getNameClassBuilder() {
        return ncb;
    }

    public ParsedPattern makeChoice(List patterns, Location loc, Annotations anno)
            throws BuildException {
        if (patterns.isEmpty()) {
            throw new IllegalArgumentException();
        }
        Pattern result = (Pattern) patterns.get(0);
        for (int i = 1; i < patterns.size(); i++) {
            result = pb.makeChoice(result, (Pattern) patterns.get(i));
        }
        return result;
    }

    public ParsedPattern makeInterleave(List patterns, Location loc, Annotations anno)
            throws BuildException {
        if (patterns.isEmpty()) {
            throw new IllegalArgumentException();
        }
        Pattern result = (Pattern) patterns.get(0);
        for (int i = 1; i < patterns.size(); i++) {
            result = pb.makeInterleave(result, (Pattern) patterns.get(i));
        }
        return result;
    }

    public ParsedPattern makeGroup(List patterns, Location loc, Annotations anno)
            throws BuildException {
        if (patterns.isEmpty()) {
            throw new IllegalArgumentException();
        }
        Pattern result = (Pattern) patterns.get(0);
        for (int i = 1; i < patterns.size(); i++) {
            result = pb.makeGroup(result, (Pattern) patterns.get(i));
        }
        return result;
    }

    public ParsedPattern makeOneOrMore(ParsedPattern p, Location loc, Annotations anno)
            throws BuildException {
        return pb.makeOneOrMore((Pattern) p);
    }

    public ParsedPattern makeZeroOrMore(ParsedPattern p, Location loc, Annotations anno)
            throws BuildException {
        return pb.makeZeroOrMore((Pattern) p);
    }

    public ParsedPattern makeOptional(ParsedPattern p, Location loc, Annotations anno)
            throws BuildException {
        return pb.makeOptional((Pattern) p);
    }

    public ParsedPattern makeList(ParsedPattern p, Location loc, Annotations anno)
            throws BuildException {
        return pb.makeList((Pattern) p, (Locator) loc);
    }

    public ParsedPattern makeMixed(ParsedPattern p, Location loc, Annotations anno)
            throws BuildException {
        return pb.makeMixed((Pattern) p);
    }

    public ParsedPattern makeEmpty(Location loc, Annotations anno) {
        return pb.makeEmpty();
    }

    public ParsedPattern makeNotAllowed(Location loc, Annotations anno) {
        return pb.makeUnexpandedNotAllowed();
    }

    public ParsedPattern makeText(Location loc, Annotations anno) {
        return pb.makeText();
    }

    public ParsedPattern makeErrorPattern() {
        return pb.makeError();
    }

//  public ParsedNameClass makeErrorNameClass() {
//    return new ErrorNameClass();
//  }
    public ParsedPattern makeAttribute(ParsedNameClass nc, ParsedPattern p, Location loc, Annotations anno)
            throws BuildException {
        return pb.makeAttribute((NameClass) nc, (Pattern) p, (Locator) loc);
    }

    public ParsedPattern makeElement(ParsedNameClass nc, ParsedPattern p, Location loc, Annotations anno)
            throws BuildException {
        return pb.makeElement((NameClass) nc, (Pattern) p, (Locator) loc);
    }

    private class DummyDataPatternBuilder implements DataPatternBuilder {

        public void addParam(String name, String value, Context context, String ns, Location loc, Annotations anno)
                throws BuildException {
        }

        public ParsedPattern makePattern(Location loc, Annotations anno)
                throws BuildException {
            return pb.makeError();
        }

        public ParsedPattern makePattern(ParsedPattern except, Location loc, Annotations anno)
                throws BuildException {
            return pb.makeError();
        }

        public void annotation(ParsedElementAnnotation ea) {
        }
    }

    private static class ValidationContextImpl implements ValidationContext {

        private ValidationContext vc;
        private String ns;

        ValidationContextImpl(ValidationContext vc, String ns) {
            this.vc = vc;
            this.ns = ns.length() == 0 ? null : ns;
        }

        public String resolveNamespacePrefix(String prefix) {
            return prefix.length() == 0 ? ns : vc.resolveNamespacePrefix(prefix);
        }

        public String getBaseUri() {
            return vc.getBaseUri();
        }

        public boolean isUnparsedEntity(String entityName) {
            return vc.isUnparsedEntity(entityName);
        }

        public boolean isNotation(String notationName) {
            return vc.isNotation(notationName);
        }
    }

    private class DataPatternBuilderImpl implements DataPatternBuilder {

        private DatatypeBuilder dtb;

        DataPatternBuilderImpl(DatatypeBuilder dtb) {
            this.dtb = dtb;
        }

        public void addParam(String name, String value, Context context, String ns, Location loc, Annotations anno)
                throws BuildException {
            try {
                dtb.addParameter(name, value, new ValidationContextImpl(context, ns));
            } catch (DatatypeException e) {
                String detail = e.getMessage();
                int pos = e.getIndex();
                String displayedParam;
                if (pos == DatatypeException.UNKNOWN) {
                    displayedParam = null;
                } else {
                    displayedParam = displayParam(value, pos);
                }
                if (displayedParam != null) {
                    if (detail != null) {
                        error("invalid_param_detail_display", detail, displayedParam, (Locator) loc);
                    } else {
                        error("invalid_param_display", displayedParam, (Locator) loc);
                    }
                } else if (detail != null) {
                    error("invalid_param_detail", detail, (Locator) loc);
                } else {
                    error("invalid_param", (Locator) loc);
                }
            }
        }

        String displayParam(String value, int pos) {
            if (pos < 0) {
                pos = 0;
            } else if (pos > value.length()) {
                pos = value.length();
            }
            return localizer.message("display_param", value.substring(0, pos), value.substring(pos));
        }

        public ParsedPattern makePattern(Location loc, Annotations anno)
                throws BuildException {
            try {
                return pb.makeData(dtb.createDatatype());
            } catch (DatatypeException e) {
                String detail = e.getMessage();
                if (detail != null) {
                    error("invalid_params_detail", detail, (Locator) loc);
                } else {
                    error("invalid_params", (Locator) loc);
                }
                return pb.makeError();
            }
        }

        public ParsedPattern makePattern(ParsedPattern except, Location loc, Annotations anno)
                throws BuildException {
            try {
                return pb.makeDataExcept(dtb.createDatatype(), (Pattern) except, (Locator) loc);
            } catch (DatatypeException e) {
                String detail = e.getMessage();
                if (detail != null) {
                    error("invalid_params_detail", detail, (Locator) loc);
                } else {
                    error("invalid_params", (Locator) loc);
                }
                return pb.makeError();
            }
        }

        public void annotation(ParsedElementAnnotation ea) {
        }
    }

    public DataPatternBuilder makeDataPatternBuilder(String datatypeLibrary, String type, Location loc)
            throws BuildException {
        DatatypeLibrary dl = datatypeLibraryFactory.createDatatypeLibrary(datatypeLibrary);
        if (dl == null) {
            error("unrecognized_datatype_library", datatypeLibrary, (Locator) loc);
        } else {
            try {
                return new DataPatternBuilderImpl(dl.createDatatypeBuilder(type));
            } catch (DatatypeException e) {
                String detail = e.getMessage();
                if (detail != null) {
                    error("unsupported_datatype_detail", datatypeLibrary, type, detail, (Locator) loc);
                } else {
                    error("unrecognized_datatype", datatypeLibrary, type, (Locator) loc);
                }
            }
        }
        return new DummyDataPatternBuilder();
    }

    public ParsedPattern makeValue(String datatypeLibrary, String type, String value, Context context, String ns,
            Location loc, Annotations anno) throws BuildException {
        DatatypeLibrary dl = datatypeLibraryFactory.createDatatypeLibrary(datatypeLibrary);
        if (dl == null) {
            error("unrecognized_datatype_library", datatypeLibrary, (Locator) loc);
        } else {
            try {
                DatatypeBuilder dtb = dl.createDatatypeBuilder(type);
                try {
                    Datatype dt = dtb.createDatatype();
                    Object obj = dt.createValue(value, new ValidationContextImpl(context, ns));
                    if (obj != null) {
                        return pb.makeValue(dt, obj);
                    }
                    error("invalid_value", value, (Locator) loc);
                } catch (DatatypeException e) {
                    String detail = e.getMessage();
                    if (detail != null) {
                        error("datatype_requires_param_detail", detail, (Locator) loc);
                    } else {
                        error("datatype_requires_param", (Locator) loc);
                    }
                }
            } catch (DatatypeException e) {
                error("unrecognized_datatype", datatypeLibrary, type, (Locator) loc);
            }
        }
        return pb.makeError();
    }

    static class GrammarImpl implements Grammar, Div, IncludedGrammar {

        private final SchemaBuilderImpl sb;
        private final Hashtable defines;
        private final RefPattern startRef;
        private final Scope parent;

        private GrammarImpl(SchemaBuilderImpl sb, Scope parent) {
            this.sb = sb;
            this.parent = parent;
            this.defines = new Hashtable();
            this.startRef = new RefPattern(null);
        }

        protected GrammarImpl(SchemaBuilderImpl sb, GrammarImpl g) {
            this.sb = sb;
            parent = g.parent;
            startRef = g.startRef;
            defines = g.defines;
        }

        public ParsedPattern endGrammar(Location loc, Annotations anno) throws BuildException {
            for (Enumeration e = defines.keys();
                    e.hasMoreElements();) {
                String name = (String) e.nextElement();
                RefPattern rp = (RefPattern) defines.get(name);
                if (rp.getPattern() == null) {
                    sb.error("reference_to_undefined", name, rp.getRefLocator());
                    rp.setPattern(sb.pb.makeError());
                }
            }
            Pattern start = startRef.getPattern();
            if (start == null) {
                sb.error("missing_start_element", (Locator) loc);
                start = sb.pb.makeError();
            }
            return start;
        }

        public void endDiv(Location loc, Annotations anno) throws BuildException {
            // nothing to do
        }

        public ParsedPattern endIncludedGrammar(Location loc, Annotations anno) throws BuildException {
            return null;
        }

        public void define(String name, GrammarSection.Combine combine, ParsedPattern pattern, Location loc, Annotations anno)
                throws BuildException {
            define(lookup(name), combine, pattern, loc);
        }

        private void define(RefPattern rp, GrammarSection.Combine combine, ParsedPattern pattern, Location loc)
                throws BuildException {
            switch (rp.getReplacementStatus()) {
                case RefPattern.REPLACEMENT_KEEP:
                    if (combine == null) {
                        if (rp.isCombineImplicit()) {
                            if (rp.getName() == null) {
                                sb.error("duplicate_start", (Locator) loc);
                            } else {
                                sb.error("duplicate_define", rp.getName(), (Locator) loc);
                            }
                        } else {
                            rp.setCombineImplicit();
                        }
                    } else {
                        byte combineType = (combine == COMBINE_CHOICE ? RefPattern.COMBINE_CHOICE : RefPattern.COMBINE_INTERLEAVE);
                        if (rp.getCombineType() != RefPattern.COMBINE_NONE
                                && rp.getCombineType() != combineType) {
                            if (rp.getName() == null) {
                                sb.error("conflict_combine_start", (Locator) loc);
                            } else {
                                sb.error("conflict_combine_define", rp.getName(), (Locator) loc);
                            }
                        }
                        rp.setCombineType(combineType);
                    }
                    Pattern p = (Pattern) pattern;
                    if (rp.getPattern() == null) {
                        rp.setPattern(p);
                    } else if (rp.getCombineType() == RefPattern.COMBINE_INTERLEAVE) {
                        rp.setPattern(sb.pb.makeInterleave(rp.getPattern(), p));
                    } else {
                        rp.setPattern(sb.pb.makeChoice(rp.getPattern(), p));
                    }
                    break;
                case RefPattern.REPLACEMENT_REQUIRE:
                    rp.setReplacementStatus(RefPattern.REPLACEMENT_IGNORE);
                    break;
                case RefPattern.REPLACEMENT_IGNORE:
                    break;
            }
        }

        public void topLevelAnnotation(ParsedElementAnnotation ea) throws BuildException {
        }

        public void topLevelComment(CommentList comments) throws BuildException {
        }

        private RefPattern lookup(String name) {
            if (name == START) {
                return startRef;
            }
            return lookup1(name);
        }

        private RefPattern lookup1(String name) {
            RefPattern p = (RefPattern) defines.get(name);
            if (p == null) {
                p = new RefPattern(name);
                defines.put(name, p);
            }
            return p;
        }

        public ParsedPattern makeRef(String name, Location loc, Annotations anno) throws BuildException {
            RefPattern p = lookup1(name);
            if (p.getRefLocator() == null && loc != null) {
                p.setRefLocator((Locator) loc);
            }
            return p;
        }

        public ParsedPattern makeParentRef(String name, Location loc, Annotations anno) throws BuildException {
            // TODO: do this check by the caller
            if (parent == null) {
                sb.error("parent_ref_outside_grammar", (Locator) loc);
                return sb.makeErrorPattern();
            }
            return parent.makeRef(name, loc, anno);
        }

        public Div makeDiv() {
            return this;
        }

        public Include makeInclude() {
            return new IncludeImpl(sb, this);
        }
    }

    static class Override {

        Override(RefPattern prp, Override next) {
            this.prp = prp;
            this.next = next;
        }
        RefPattern prp;
        Override next;
        byte replacementStatus;
    }

    private static class IncludeImpl implements Include, Div {

        private SchemaBuilderImpl sb;
        private Override overrides;
        private GrammarImpl grammar;

        private IncludeImpl(SchemaBuilderImpl sb, GrammarImpl grammar) {
            this.sb = sb;
            this.grammar = grammar;
        }

        public void define(String name, GrammarSection.Combine combine, ParsedPattern pattern, Location loc, Annotations anno)
                throws BuildException {
            RefPattern rp = grammar.lookup(name);
            overrides = new Override(rp, overrides);
            grammar.define(rp, combine, pattern, loc);
        }

        public void endDiv(Location loc, Annotations anno) throws BuildException {
            // nothing to do
        }

        public void topLevelAnnotation(ParsedElementAnnotation ea) throws BuildException {
            // nothing to do
        }

        public void topLevelComment(CommentList comments) throws BuildException {
        }

        public Div makeDiv() {
            return this;
        }

        public void endInclude(Parseable current, String uri, String ns,
                Location loc, Annotations anno) throws BuildException {
            for (OpenIncludes inc = sb.openIncludes;
                    inc != null;
                    inc = inc.parent) {
                if (inc.uri.equals(uri)) {
                    sb.error("recursive_include", uri, (Locator) loc);
                    return;
                }
            }

            for (Override o = overrides; o != null; o = o.next) {
                o.replacementStatus = o.prp.getReplacementStatus();
                o.prp.setReplacementStatus(RefPattern.REPLACEMENT_REQUIRE);
            }
            try {
                SchemaBuilderImpl isb = new SchemaBuilderImpl(ns, uri, sb);
                current.parseInclude(uri, isb, new GrammarImpl(isb, grammar), ns);
                for (Override o = overrides; o != null; o = o.next) {
                    if (o.prp.getReplacementStatus() == RefPattern.REPLACEMENT_REQUIRE) {
                        if (o.prp.getName() == null) {
                            sb.error("missing_start_replacement", (Locator) loc);
                        } else {
                            sb.error("missing_define_replacement", o.prp.getName(), (Locator) loc);
                        }
                    }
                }
            } catch (IllegalSchemaException e) {
                sb.noteError();
            } finally {
                for (Override o = overrides; o != null; o = o.next) {
                    o.prp.setReplacementStatus(o.replacementStatus);
                }
            }
        }

        public Include makeInclude() {
            return null;
        }
    }

    public Grammar makeGrammar(Scope parent) {
        return new GrammarImpl(this, parent);
    }

    public ParsedPattern annotate(ParsedPattern p, Annotations anno) throws BuildException {
        return p;
    }

    public ParsedPattern annotateAfter(ParsedPattern p, ParsedElementAnnotation e) throws BuildException {
        return p;
    }

    public ParsedPattern commentAfter(ParsedPattern p, CommentList comments) throws BuildException {
        return p;
    }

    public ParsedPattern makeExternalRef(Parseable current, String uri, String ns, Scope scope,
            Location loc, Annotations anno)
            throws BuildException {
        for (OpenIncludes inc = openIncludes;
                inc != null;
                inc = inc.parent) {
            if (inc.uri.equals(uri)) {
                error("recursive_include", uri, (Locator) loc);
                return pb.makeError();
            }
        }
        try {
            return current.parseExternal(uri, new SchemaBuilderImpl(ns, uri, this), scope, ns);
        } catch (IllegalSchemaException e) {
            noteError();
            return pb.makeError();
        }
    }

    public Location makeLocation(String systemId, int lineNumber, int columnNumber) {
        return new LocatorImpl(systemId, lineNumber, columnNumber);
    }

    public Annotations makeAnnotations(CommentList comments, Context context) {
        return this;
    }

    public ElementAnnotationBuilder makeElementAnnotationBuilder(String ns, String localName, String prefix,
            Location loc, CommentList comments, Context context) {
        return this;
    }

    public CommentList makeCommentList() {
        return this;
    }

    public void addComment(String value, Location loc) throws BuildException {
    }

    public void addAttribute(String ns, String localName, String prefix, String value, Location loc) {
        // nothing needed
    }

    public void addElement(ParsedElementAnnotation ea) {
        // nothing needed
    }

    public void addComment(CommentList comments) throws BuildException {
        // nothing needed
    }

    public void addLeadingComment(CommentList comments) throws BuildException {
        // nothing needed
    }

    public ParsedElementAnnotation makeElementAnnotation() {
        return null;
    }

    public void addText(String value, Location loc, CommentList comments) throws BuildException {
    }

    public boolean usesComments() {
        return false;
    }

    private void error(SAXParseException message) throws BuildException {
        noteError();
        try {
            if (eh != null) {
                eh.error(message);
            }
        } catch (SAXException e) {
            throw new BuildException(e);
        }
    }

    private void error(String key, Locator loc) throws BuildException {
        error(new SAXParseException(localizer.message(key), loc));
    }

    private void error(String key, String arg, Locator loc) throws BuildException {
        error(new SAXParseException(localizer.message(key, arg), loc));
    }

    private void error(String key, String arg1, String arg2, Locator loc) throws BuildException {
        error(new SAXParseException(localizer.message(key, arg1, arg2), loc));
    }

    private void error(String key, String arg1, String arg2, String arg3, Locator loc) throws BuildException {
        error(new SAXParseException(localizer.message(key, new Object[]{arg1, arg2, arg3}), loc));
    }

    private void noteError() {
        if (!hadError && parent != null) {
            parent.noteError();
        }
        hadError = true;
    }
}
