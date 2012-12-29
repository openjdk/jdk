/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;

import com.sun.xml.internal.dtdparser.DTDEventListener;


/**
 * @author Kohsuke Kawaguchi
 */
final class Occurence extends Term {
    final Term term;
    final boolean isOptional;
    final boolean isRepeated;

    Occurence(Term term, boolean optional, boolean repeated) {
        this.term = term;
        isOptional = optional;
        isRepeated = repeated;
    }

    static Term wrap( Term t, int occurence ) {
        switch(occurence) {
        case DTDEventListener.OCCURENCE_ONCE:
            return t;
        case DTDEventListener.OCCURENCE_ONE_OR_MORE:
            return new Occurence(t,false,true);
        case DTDEventListener.OCCURENCE_ZERO_OR_MORE:
            return new Occurence(t,true,true);
        case DTDEventListener.OCCURENCE_ZERO_OR_ONE:
            return new Occurence(t,true,false);
        default:
            throw new IllegalArgumentException();
        }
    }

    void normalize(List<Block> r, boolean optional) {
        if(isRepeated) {
            Block b = new Block(isOptional||optional,true);
            addAllElements(b);
            r.add(b);
        } else {
            term.normalize(r,optional||isOptional);
        }
    }

    void addAllElements(Block b) {
        term.addAllElements(b);
    }

    boolean isOptional() {
        return isOptional||term.isOptional();
    }

    boolean isRepeated() {
        return isRepeated||term.isRepeated();
    }
}
