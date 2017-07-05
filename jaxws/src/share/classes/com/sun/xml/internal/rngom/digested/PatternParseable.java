/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */
package com.sun.xml.internal.rngom.digested;

import com.sun.xml.internal.rngom.ast.builder.Annotations;
import com.sun.xml.internal.rngom.ast.builder.BuildException;
import com.sun.xml.internal.rngom.ast.builder.IncludedGrammar;
import com.sun.xml.internal.rngom.ast.builder.SchemaBuilder;
import com.sun.xml.internal.rngom.ast.builder.Scope;
import com.sun.xml.internal.rngom.ast.om.Location;
import com.sun.xml.internal.rngom.ast.om.ParsedNameClass;
import com.sun.xml.internal.rngom.ast.om.ParsedPattern;
import com.sun.xml.internal.rngom.nc.NameClass;
import com.sun.xml.internal.rngom.parse.IllegalSchemaException;
import com.sun.xml.internal.rngom.parse.Parseable;
import org.xml.sax.Locator;

import java.util.List;
import java.util.ArrayList;

/**
 * @author Kohsuke Kawaguchi (kk@kohsuke.org)
 */
final class PatternParseable implements Parseable {
    private final DPattern pattern;

    public PatternParseable(DPattern p) {
        this.pattern = p;
    }

    public ParsedPattern parse(SchemaBuilder sb) throws BuildException {
        return (ParsedPattern)pattern.accept(new Parser(sb));
    }

    public ParsedPattern parseInclude(String uri, SchemaBuilder f, IncludedGrammar g, String inheritedNs) throws BuildException {
        throw new UnsupportedOperationException();
    }

    public ParsedPattern parseExternal(String uri, SchemaBuilder f, Scope s, String inheritedNs) throws BuildException {
        throw new UnsupportedOperationException();
    }


    private static class Parser implements DPatternVisitor<ParsedPattern> {
        private final SchemaBuilder sb;

        public Parser(SchemaBuilder sb) {
            this.sb = sb;
        }

        private Annotations parseAnnotation(DPattern p) {
            // TODO
            return null;
        }

        private Location parseLocation(DPattern p) {
            Locator l = p.getLocation();
            return sb.makeLocation(l.getSystemId(),l.getLineNumber(),l.getColumnNumber());
        }

        private ParsedNameClass parseNameClass(NameClass name) {
            // TODO: reparse the name class
            return name;
        }



        public ParsedPattern onAttribute(DAttributePattern p) {
            return sb.makeAttribute(
                parseNameClass(p.getName()),
                (ParsedPattern)p.getChild().accept(this),
                parseLocation(p),
                parseAnnotation(p) );
        }

        public ParsedPattern onChoice(DChoicePattern p) {
            List<ParsedPattern> kids = new ArrayList<ParsedPattern>();
            for( DPattern c=p.firstChild(); c!=null; c=c.next )
                kids.add( (ParsedPattern)c.accept(this) );
            return sb.makeChoice(kids,parseLocation(p),null);
        }

        public ParsedPattern onData(DDataPattern p) {
            // TODO
            return null;
        }

        public ParsedPattern onElement(DElementPattern p) {
            return sb.makeElement(
                parseNameClass(p.getName()),
                (ParsedPattern)p.getChild().accept(this),
                parseLocation(p),
                parseAnnotation(p) );
        }

        public ParsedPattern onEmpty(DEmptyPattern p) {
            return sb.makeEmpty(
                parseLocation(p),
                parseAnnotation(p) );
        }

        public ParsedPattern onGrammar(DGrammarPattern p) {
            // TODO
            return null;
        }

        public ParsedPattern onGroup(DGroupPattern p) {
            List<ParsedPattern> kids = new ArrayList<ParsedPattern>();
            for( DPattern c=p.firstChild(); c!=null; c=c.next )
                kids.add( (ParsedPattern)c.accept(this) );
            return sb.makeGroup(kids,parseLocation(p),null);
        }

        public ParsedPattern onInterleave(DInterleavePattern p) {
            List<ParsedPattern> kids = new ArrayList<ParsedPattern>();
            for( DPattern c=p.firstChild(); c!=null; c=c.next )
                kids.add( (ParsedPattern)c.accept(this) );
            return sb.makeInterleave(kids,parseLocation(p),null);
        }

        public ParsedPattern onList(DListPattern p) {
            return sb.makeList(
                (ParsedPattern)p.getChild().accept(this),
                parseLocation(p),
                parseAnnotation(p) );
        }

        public ParsedPattern onMixed(DMixedPattern p) {
            return sb.makeMixed(
                (ParsedPattern)p.getChild().accept(this),
                parseLocation(p),
                parseAnnotation(p) );
        }

        public ParsedPattern onNotAllowed(DNotAllowedPattern p) {
            return sb.makeNotAllowed(
                parseLocation(p),
                parseAnnotation(p) );
        }

        public ParsedPattern onOneOrMore(DOneOrMorePattern p) {
            return sb.makeOneOrMore(
                (ParsedPattern)p.getChild().accept(this),
                parseLocation(p),
                parseAnnotation(p) );
        }

        public ParsedPattern onOptional(DOptionalPattern p) {
            return sb.makeOptional(
                (ParsedPattern)p.getChild().accept(this),
                parseLocation(p),
                parseAnnotation(p) );
        }

        public ParsedPattern onRef(DRefPattern p) {
            // TODO
            return null;
        }

        public ParsedPattern onText(DTextPattern p) {
            return sb.makeText(
                parseLocation(p),
                parseAnnotation(p) );
        }

        public ParsedPattern onValue(DValuePattern p) {
            return sb.makeValue(
                p.getDatatypeLibrary(),
                p.getType(),
                p.getValue(),
                p.getContext(),
                p.getNs(),
                parseLocation(p),
                parseAnnotation(p) );
        }

        public ParsedPattern onZeroOrMore(DZeroOrMorePattern p) {
            return sb.makeZeroOrMore(
                (ParsedPattern)p.getChild().accept(this),
                parseLocation(p),
                parseAnnotation(p) );
        }
    }
}
