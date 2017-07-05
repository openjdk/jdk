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

package com.sun.xml.internal.xsom;

import com.sun.xml.internal.xsom.impl.scd.Iterators;
import com.sun.xml.internal.xsom.impl.scd.ParseException;
import com.sun.xml.internal.xsom.impl.scd.SCDImpl;
import com.sun.xml.internal.xsom.impl.scd.SCDParser;
import com.sun.xml.internal.xsom.impl.scd.Step;
import com.sun.xml.internal.xsom.impl.scd.TokenMgrError;
import com.sun.xml.internal.xsom.util.DeferedCollection;

import javax.xml.namespace.NamespaceContext;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * Schema Component Designator (SCD).
 *
 * <p>
 * SCD for schema is what XPath is for XML. SCD allows you to select a schema component(s)
 * from a schema component(s).
 *
 * <p>
 * See <a href="http://www.w3.org/TR/2005/WD-xmlschema-ref-20050329/">XML Schema: Component Designators</a>.
 * This implementation is based on 03/29/2005 working draft.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class SCD {

    /**
     * Parses the string representation of SCD.
     *
     * <p>
     * This method involves parsing the path expression and preparing the in-memory
     * structure, so this is useful when you plan to use the same SCD against
     * different context node multiple times.
     *
     * <p>
     * If you want to evaluate SCD just once, use {@link XSComponent#select} methods.
     *
     * @param path
     *      the string representation of SCD, such as "/foo/bar".
     * @param nsContext
     *      Its {@link NamespaceContext#getNamespaceURI(String)} is used
     *      to resolve prefixes in the SCD to the namespace URI.
     */
    public static SCD create(String path, NamespaceContext nsContext) throws java.text.ParseException {
        try {
            SCDParser p = new SCDParser(path,nsContext);
            List<?> list = p.RelativeSchemaComponentPath();
            return new SCDImpl(path,list.toArray(new Step[list.size()]));
        } catch (TokenMgrError e) {
            throw setCause(new java.text.ParseException(e.getMessage(), -1 ),e);
        } catch (ParseException e) {
            throw setCause(new java.text.ParseException(e.getMessage(), e.currentToken.beginColumn ),e);
        }
    }

    private static java.text.ParseException setCause(java.text.ParseException e, Throwable x) {
        e.initCause(x);
        return e;
    }

    /**
     * Evaluates the SCD against the given context node and
     * returns the matched nodes.
     *
     * @return
     *      could be empty but never be null.
     */
    public final Collection<XSComponent> select(XSComponent contextNode) {
        return new DeferedCollection<XSComponent>(select(Iterators.singleton(contextNode)));
    }

    /**
     * Evaluates the SCD against the whole schema and
     * returns the matched nodes.
     *
     * <p>
     * This method is here because {@link XSSchemaSet}
     * doesn't implement {@link XSComponent}.
     *
     * @return
     *      could be empty but never be null.
     */
    public final Collection<XSComponent> select(XSSchemaSet contextNode) {
        return select(contextNode.getSchemas());
    }

    /**
     * Evaluates the SCD against the given context node and
     * returns the matched node.
     *
     * @return
     *      null if the SCD didn't match anything. If the SCD matched more than one node,
     *      the first one will be returned.
     */
    public final XSComponent selectSingle(XSComponent contextNode) {
        Iterator<XSComponent> r = select(Iterators.singleton(contextNode));
        if(r.hasNext())     return r.next();
        return null;
    }

    /**
     * Evaluates the SCD against the whole schema set and
     * returns the matched node.
     *
     * @return
     *      null if the SCD didn't match anything. If the SCD matched more than one node,
     *      the first one will be returned.
     */
    public final XSComponent selectSingle(XSSchemaSet contextNode) {
        Iterator<XSComponent> r = select(contextNode.iterateSchema());
        if(r.hasNext())     return r.next();
        return null;
    }

    /**
     * Evaluates the SCD against the given set of context nodes and
     * returns the matched nodes.
     *
     * @param contextNodes
     *      {@link XSComponent}s that represent the context node against
     *      which {@link SCD} is evaluated.
     *
     * @return
     *      could be empty but never be null.
     */
    public abstract Iterator<XSComponent> select(Iterator<? extends XSComponent> contextNodes);

    /**
     * Evaluates the SCD against the given set of context nodes and
     * returns the matched nodes.
     *
     * @param contextNodes
     *      {@link XSComponent}s that represent the context node against
     *      which {@link SCD} is evaluated.
     *
     * @return
     *      could be empty but never be null.
     */
    public final Collection<XSComponent> select(Collection<? extends XSComponent> contextNodes) {
        return new DeferedCollection<XSComponent>(select(contextNodes.iterator()));
    }

    /**
     * Returns the textual SCD representation as given to {@link SCD#create(String, NamespaceContext)}.
     */
    public abstract String toString();
}
