/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.doclets.toolkit.taglets.snippet;

import java.util.Objects;

/*
 * 1. The hierarchy of attributes is modelled as
 *
 *     Attribute
 *     |
 *     +- Valueless
 *     |
 *     +- Valued
 *
 * not as
 *
 *     Attribute (Valueless)
 *     |
 *     +- Valued
 *
 * because in conjunction with query operations of `Attributes`, `Valued` and
 * `Valueless` should be more useful if neither is a subtype of the other.
 *
 * 2. `Attribute` is abstract because its sole purpose is to be a category.
 *
 * 3. This attribute abstraction is simpler than that of com.sun.source.doctree.AttributeTree.
 * There's no need to have recursive structure similar to that of allowed by AttributeTree.
 */
/**
 * A markup attribute.
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own risk.
 * This code and its internal interfaces are subject to change or
 * deletion without notice.</b>
 */
public abstract class Attribute {

    private final String name;

    private final int nameStartPosition;

    private Attribute(String name, int nameStartPosition) {
        this.name = Objects.requireNonNull(name);
        this.nameStartPosition = nameStartPosition;
    }

    String name() {
        return name;
    }

    int nameStartPosition() {
        return nameStartPosition;
    }

    /*
     * `Valued` can be later extended by classes such as DoublyQuoted,
     * SinglyQuoted or Unquoted to form a (sealed) hierarchy. In that case,
     * `Valued` should become abstract similarly to `Attribute`.
     */
    static final class Valued extends Attribute {

        private final String value;

        private final int valueStartPosition;

        Valued(String name, String value, int namePosition, int valueStartPosition) {
            super(name, namePosition);
            this.value = Objects.requireNonNull(value);
            this.valueStartPosition = valueStartPosition;
        }

        String value() {
            return value;
        }

        public int valueStartPosition() {
            return valueStartPosition;
        }
    }

    static final class Valueless extends Attribute {

        Valueless(String name, int nameStartPosition) {
            super(name, nameStartPosition);
        }
    }
}
