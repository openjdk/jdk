/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights
 * reserved.  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE
 * HEADER.
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

package com.sun.tools.javac.code;

import com.sun.tools.javac.util.Assert;
import com.sun.tools.javac.util.List;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A super-interface for all type metadata elements.  Metadata classes
 * can be created for any metadata on types with the following
 * properties:
 *
 * <ul>
 * <li>They have a default value (preferably empty)</li>
 * <li>The field is usually the default value</li>
 * <li>Different values of the field are visible, and denote distinct
 *     types</li>
 * </ul>
 */
public class TypeMetadata {

    public static final TypeMetadata empty = new TypeMetadata();
    private final EnumMap<TypeMetadata.Element.Kind, TypeMetadata.Element> contents;

    private TypeMetadata() {
        contents = new EnumMap<Element.Kind, Element>(Element.Kind.class);
    }

    public TypeMetadata(final Element elem) {
        this();
        contents.put(elem.kind(), elem);
    }

    public TypeMetadata(final TypeMetadata other) {
        contents = other.contents.clone();
    }

    public TypeMetadata copy() {
        return new TypeMetadata(this);
    }

    public TypeMetadata combine(final Element elem) {
        final TypeMetadata out = new TypeMetadata(this);
        final Element.Kind key = elem.kind();
        if (contents.containsKey(key)) {
            out.add(key, this.contents.get(key).combine(elem));
        } else {
            out.add(key, elem);
        }
        return out;
    }

    public TypeMetadata combine(final TypeMetadata other) {
        final TypeMetadata out = new TypeMetadata();
        final Set<Element.Kind> keys = new HashSet<>(this.contents.keySet());
        keys.addAll(other.contents.keySet());

        for(final Element.Kind key : keys) {
            if (this.contents.containsKey(key)) {
                if (other.contents.containsKey(key)) {
                    out.add(key, this.contents.get(key).combine(other.contents.get(key)));
                } else {
                    out.add(key, this.contents.get(key));
                }
            } else if (other.contents.containsKey(key)) {
                out.add(key, other.contents.get(key));
            }
        }
        return out;
    }

    public Element get(final Element.Kind kind) {
        return contents.get(kind);
    }

    public boolean isEmpty() {
        return contents.isEmpty();
    }

    private void add(final Element.Kind kind, final Element elem) {
        contents.put(kind, elem);
    }

    private void addAll(final Map<? extends Element.Kind,? extends Element> m) {
        contents.putAll(m);
    }

    public interface Element {

        public enum Kind {
            ANNOTATIONS;
        }

        /**
         * Get the kind of metadata this object represents
         */
        public Kind kind();

        /**
         * Combine this type metadata with another metadata of the
         * same kind.
         *
         * @param other The metadata with which to combine this one.
         * @return The combined metadata.
         */
        public Element combine(Element other);
    }

    /**
     * A type metadata object holding type annotations.
     */
    public static class Annotations implements Element {
        private final List<Attribute.TypeCompound> annos;

        public Annotations(final List<Attribute.TypeCompound> annos) {
            this.annos = annos;
        }

        /**
         * Get the type annotations contained in this metadata.
         *
         * @return The annotations.
         */
        public List<Attribute.TypeCompound> getAnnotations() {
            return annos;
        }

        @Override
        public Annotations combine(final Element other) {
            // Temporary: we should append the lists, but that won't
            // work with type annotations today.  Instead, we replace
            // the list.
            return new Annotations(((Annotations) other).annos);
        }

        @Override
        public Kind kind() { return Kind.ANNOTATIONS; }

        @Override
        public String toString() { return "ANNOTATIONS { " + annos + " }"; }
    }

}
