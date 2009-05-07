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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * &lt;grammar> pattern, which is a collection of named patterns.
 *
 * @author Kohsuke Kawaguchi (kk@kohsuke.org)
 */
public class DGrammarPattern extends DPattern implements Iterable<DDefine> {
    private final Map<String,DDefine> patterns = new HashMap<String,DDefine>();

    DPattern start;

    /**
     * Gets the start pattern.
     */
    public DPattern getStart() {
        return start;
    }

    /**
     * Gets the named pattern by its name.
     *
     * @return
     *      null if not found.
     */
    public DDefine get( String name ) {
        return patterns.get(name);
    }

    DDefine getOrAdd( String name ) {
        if(patterns.containsKey(name)) {
            return get(name);
        } else {
            DDefine d = new DDefine(name);
            patterns.put(name,d);
            return d;
        }
    }

    /**
     * Iterates all the {@link DDefine}s in this grammar.
     */
    public Iterator<DDefine> iterator() {
        return patterns.values().iterator();
    }

    public boolean isNullable() {
        return start.isNullable();
    }

    public <V> V accept( DPatternVisitor<V> visitor ) {
        return visitor.onGrammar(this);
    }
}
