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
package com.sun.tools.internal.xjc.reader.relaxng;

import java.util.HashSet;
import java.util.Set;

import com.sun.xml.internal.rngom.digested.DDefine;
import com.sun.xml.internal.rngom.digested.DGrammarPattern;
import com.sun.xml.internal.rngom.digested.DPatternWalker;
import com.sun.xml.internal.rngom.digested.DRefPattern;

/**
 * Recursively find all {@link DDefine}s in the grammar.
 *
 * @author Kohsuke Kawaguchi
 */
final class DefineFinder extends DPatternWalker {

    public final Set<DDefine> defs = new HashSet<DDefine>();

    public Void onGrammar(DGrammarPattern p) {
        for( DDefine def : p ) {
            defs.add(def);
            def.getPattern().accept(this);
        }

        return p.getStart().accept(this);
    }

    /**
     * We visit all {@link DDefine}s from {@link DGrammarPattern},
     * so no point in resolving refs.
     */
    public Void onRef(DRefPattern p) {
        return null;
    }
}
