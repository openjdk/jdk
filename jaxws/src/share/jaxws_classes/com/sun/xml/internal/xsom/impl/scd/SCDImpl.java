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

package com.sun.xml.internal.xsom.impl.scd;

import com.sun.xml.internal.xsom.SCD;
import com.sun.xml.internal.xsom.XSComponent;

import java.util.Iterator;

/**
 * Schema component designator.
 *
 * @author Kohsuke Kawaguchi
 */
public final class SCDImpl extends SCD {
    /**
     * SCD is fundamentally a list of steps.
     */
    private final Step[] steps;

    /**
     * The original textual SCD representation.
     */
    private final String text;

    public SCDImpl(String text, Step[] steps) {
        this.text = text;
        this.steps = steps;
    }

    public Iterator<XSComponent> select(Iterator<? extends XSComponent> contextNode) {
        Iterator<XSComponent> nodeSet = (Iterator)contextNode;

        int len = steps.length;
        for( int i=0; i<len; i++ ) {
            if(i!=0 && i!=len-1 && !steps[i-1].axis.isModelGroup() && steps[i].axis.isModelGroup()) {
                // expand the current nodeset by adding abbreviatable complex type and model groups.
                // note that such expansion is not allowed to occure in in between model group axes.

                // TODO: this step is not needed if the next step is known not to react to
                // complex type nor model groups, such as, say Axis.FACET
                nodeSet = new Iterators.Unique<XSComponent>(
                    new Iterators.Map<XSComponent,XSComponent>(nodeSet) {
                        protected Iterator<XSComponent> apply(XSComponent u) {
                            return new Iterators.Union<XSComponent>(
                                Iterators.singleton(u),
                                Axis.INTERMEDIATE_SKIP.iterator(u) );
                        }
                    }
                );
            }
            nodeSet = steps[i].evaluate(nodeSet);
        }

        return nodeSet;
    }

    public String toString() {
        return text;
    }
}
