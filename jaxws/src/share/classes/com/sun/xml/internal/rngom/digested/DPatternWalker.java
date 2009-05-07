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



/**
 * @author Kohsuke Kawaguchi (kk@kohsuke.org)
 */
public class DPatternWalker implements DPatternVisitor<Void> {
    public Void onAttribute(DAttributePattern p) {
        return onXmlToken(p);
    }

    protected Void onXmlToken(DXmlTokenPattern p) {
        return onUnary(p);
    }

    public Void onChoice(DChoicePattern p) {
        return onContainer(p);
    }

    protected Void onContainer(DContainerPattern p) {
        for( DPattern c=p.firstChild(); c!=null; c=c.next )
            c.accept(this);
        return null;
    }

    public Void onData(DDataPattern p) {
        return null;
    }

    public Void onElement(DElementPattern p) {
        return onXmlToken(p);
    }

    public Void onEmpty(DEmptyPattern p) {
        return null;
    }

    public Void onGrammar(DGrammarPattern p) {
        return p.getStart().accept(this);
    }

    public Void onGroup(DGroupPattern p) {
        return onContainer(p);
    }

    public Void onInterleave(DInterleavePattern p) {
        return onContainer(p);
    }

    public Void onList(DListPattern p) {
        return onUnary(p);
    }

    public Void onMixed(DMixedPattern p) {
        return onUnary(p);
    }

    public Void onNotAllowed(DNotAllowedPattern p) {
        return null;
    }

    public Void onOneOrMore(DOneOrMorePattern p) {
        return onUnary(p);
    }

    public Void onOptional(DOptionalPattern p) {
        return onUnary(p);
    }

    public Void onRef(DRefPattern p) {
        return p.getTarget().getPattern().accept(this);
    }

    public Void onText(DTextPattern p) {
        return null;
    }

    public Void onValue(DValuePattern p) {
        return null;
    }

    public Void onZeroOrMore(DZeroOrMorePattern p) {
        return onUnary(p);
    }

    protected Void onUnary(DUnaryPattern p) {
        return p.getChild().accept(this);
    }
}
