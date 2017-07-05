/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.apple.internal.jobjc.generator.model;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.apple.internal.jobjc.generator.model.types.Type;
import com.apple.internal.jobjc.generator.utils.ObjectInspector;

/**
 * Subclasses must implement ctor(Node, P)
 */
public class Element <P extends Element<?>> implements Comparable<Element<?>>{
    public final String name;
    public final P parent;

    public Element(final String name, final P parent) {
        this.name = Type.cleanName(name);
        this.parent = parent;
    }

    public Element(final Node node, final P parent) {
        this(getAttr(node, "name"), parent);
    }

    public static String getAttr(final Node node, final String key) {
        final NamedNodeMap attrs = node.getAttributes();
        if (attrs == null) return null;
        final Node name = attrs.getNamedItem(key);
        if (name == null) return null;
        return name.getNodeValue();
    }

    static <P extends Element<?>, T extends Element<P>> List<T> getNodesFor(final Node parentNode, final String selection, final Class<T> clazz, final P parent) {
        Constructor<T> ctor;
        try {
            ctor = clazz.getConstructor(new Class[] { Node.class, parent.getClass() });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        final NodeList childNodes = parentNode.getChildNodes();
        final List<T> nodes = new ArrayList<T>();
        for (int i = 0; i < childNodes.getLength(); i++) {
            final Node node = childNodes.item(i);
            if (!selection.equals(node.getLocalName())) continue;

            T obj;
            try {
                obj = ctor.newInstance(new Object[] { node, parent });
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e.getCause());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            nodes.add(obj);
        }

        return nodes;
    }

    @Override public String toString() {
        return name;
    }

    public String reflectOnMySelf() {
        return ObjectInspector.inspect(this);
    }

    public int compareTo(Element<?> o) {
        return name.compareTo(o.name);
    }
}
