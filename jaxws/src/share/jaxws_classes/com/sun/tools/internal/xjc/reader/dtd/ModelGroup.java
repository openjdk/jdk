/*
 * Copyright (c) 1997, 2011, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.internal.xjc.reader.dtd;

import java.util.ArrayList;
import java.util.List;

import com.sun.xml.internal.dtdparser.DTDEventListener;


/**
 * @author Kohsuke Kawaguchi
 */
final class ModelGroup extends Term {
    enum Kind {
        CHOICE, SEQUENCE
    }

    Kind kind;

    private final List<Term> terms = new ArrayList<Term>();

    void normalize(List<Block> r, boolean optional) {
        switch(kind) {
        case SEQUENCE:
            for( Term t : terms )
                t.normalize(r,optional);
            return;
        case CHOICE:
            Block b = new Block(isOptional()||optional,isRepeated());
            addAllElements(b);
            r.add(b);
            return;
        }
    }

    void addAllElements(Block b) {
        for( Term t : terms )
            t.addAllElements(b);
    }

    boolean isOptional() {
        switch(kind) {
        case SEQUENCE:
            for( Term t : terms )
                if(!t.isOptional())
                    return false;
            return true;
        case CHOICE:
            for( Term t : terms )
                if(t.isOptional())
                    return true;
            return false;
        default:
            throw new IllegalArgumentException();
        }
    }

    boolean isRepeated() {
        switch(kind) {
        case SEQUENCE:
            return true;
        case CHOICE:
            for( Term t : terms )
                if(t.isRepeated())
                    return true;
            return false;
        default:
            throw new IllegalArgumentException();
        }
    }

    void setKind(short connectorType) {
        Kind k;
        switch(connectorType) {
        case DTDEventListener.SEQUENCE:
            k = Kind.SEQUENCE;
            break;
        case DTDEventListener.CHOICE:
            k = Kind.CHOICE;
            break;
        default:
            throw new IllegalArgumentException();
        }

        assert kind==null || k==kind;
        kind = k;
    }

    void addTerm(Term t) {
        if (t instanceof ModelGroup) {
            ModelGroup mg = (ModelGroup) t;
            if(mg.kind==this.kind) {
                terms.addAll(mg.terms);
                return;
            }
        }
        terms.add(t);
    }


    Term wrapUp() {
        switch(terms.size()) {
        case 0:
            return EMPTY;
        case 1:
            assert kind==null;
            return terms.get(0);
        default:
            assert kind!=null;
            return this;
        }
    }

}
