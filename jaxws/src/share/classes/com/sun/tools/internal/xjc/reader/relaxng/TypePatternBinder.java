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
import java.util.Stack;

import com.sun.xml.internal.rngom.digested.DAttributePattern;
import com.sun.xml.internal.rngom.digested.DChoicePattern;
import com.sun.xml.internal.rngom.digested.DDefine;
import com.sun.xml.internal.rngom.digested.DListPattern;
import com.sun.xml.internal.rngom.digested.DMixedPattern;
import com.sun.xml.internal.rngom.digested.DOneOrMorePattern;
import com.sun.xml.internal.rngom.digested.DOptionalPattern;
import com.sun.xml.internal.rngom.digested.DPatternWalker;
import com.sun.xml.internal.rngom.digested.DRefPattern;
import com.sun.xml.internal.rngom.digested.DZeroOrMorePattern;

/**
 * Fumigate the named patterns that can be bound to inheritance.
 *
 * @author Kohsuke Kawaguchi
 */
final class TypePatternBinder extends DPatternWalker {
    private boolean canInherit;
    private final Stack<Boolean> stack = new Stack<Boolean>();

    /**
     * Patterns that are determined not to be bindable to inheritance.
     */
    private final Set<DDefine> cannotBeInherited = new HashSet<DDefine>();


    void reset() {
        canInherit = true;
        stack.clear();
    }

    public Void onRef(DRefPattern p) {
        if(!canInherit) {
            cannotBeInherited.add(p.getTarget());
        } else {
            // if the whole pattern is like "A,B", we can only inherit from
            // either A or B. For now, always derive from A.
            // it might be worthwhile to have a smarter binding logic where
            // we pick A and B based on their 'usefulness' --- by taking into
            // account how many other paterns are derived from those.
            canInherit = false;
        }
        return null;
    }

    /*
        Set the flag to false if we hit a pattern that cannot include
        a <ref> to be bound as an inheritance.

        All the following code are the same
    */
    public Void onChoice(DChoicePattern p) {
        push(false);
        super.onChoice(p);
        pop();
        return null;
    }

    public Void onAttribute(DAttributePattern p) {
        push(false);
        super.onAttribute(p);
        pop();
        return null;
    }

    public Void onList(DListPattern p) {
        push(false);
        super.onList(p);
        pop();
        return null;
    }

    public Void onMixed(DMixedPattern p) {
        push(false);
        super.onMixed(p);
        pop();
        return null;
    }

    public Void onOneOrMore(DOneOrMorePattern p) {
        push(false);
        super.onOneOrMore(p);
        pop();
        return null;
    }

    public Void onZeroOrMore(DZeroOrMorePattern p) {
        push(false);
        super.onZeroOrMore(p);
        pop();
        return null;
    }

    public Void onOptional(DOptionalPattern p) {
        push(false);
        super.onOptional(p);
        pop();
        return null;
    }

    private void push(boolean v) {
        stack.push(canInherit);
        canInherit = v;
    }

    private void pop() {
        canInherit = stack.pop();
    }
}
