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
package com.sun.xml.internal.rngom.binary.visitor;

import com.sun.xml.internal.rngom.binary.Pattern;
import com.sun.xml.internal.rngom.nc.NameClass;

import java.util.HashSet;
import java.util.Set;

/**
 * Visits a pattern and creates a list of possible child elements.
 *
 * <p>
 * One can use a similar technique to introspect a pattern.
 *
 * @author Kohsuke Kawaguchi (kk@kohsuke.org)
 */
public class ChildElementFinder extends PatternWalker {

    private final Set children = new HashSet();

    /**
     * Represents a child element.
     */
    public static class Element {
        public final NameClass nc;
        public final Pattern content;

        public Element(NameClass nc, Pattern content) {
            this.nc = nc;
            this.content = content;
        }

        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Element)) return false;

            final Element element = (Element) o;

            if (content != null ? !content.equals(element.content) : element.content != null) return false;
            if (nc != null ? !nc.equals(element.nc) : element.nc != null) return false;

            return true;
        }

        public int hashCode() {
            int result;
            result = (nc != null ? nc.hashCode() : 0);
            result = 29 * result + (content != null ? content.hashCode() : 0);
            return result;
        }
    }

    /**
     * Returns a set of {@link Element}.
     */
    public Set getChildren() {
        return children;
    }

    public void visitElement(NameClass nc, Pattern content) {
        children.add(new Element(nc,content));
    }

    public void visitAttribute(NameClass ns, Pattern value) {
        // there will be no element inside attribute,
        // so don't go in there.
    }

    public void visitList(Pattern p) {
        // there will be no element inside a list,
        // so don't go in there.
    }
}
