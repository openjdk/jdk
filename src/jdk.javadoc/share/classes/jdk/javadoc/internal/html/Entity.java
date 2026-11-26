/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.html;

import java.io.IOException;

/**
 * A representation of HTML entities.
 */
public class Entity extends Content {
    public static final Entity LESS_THAN = new Entity("&lt;", "<");
    public static final Entity GREATER_THAN = new Entity("&gt;", ">");
    public static final Entity AMPERSAND = new Entity("&amp;", "&");
    public static final Entity DOUBLE_QUOTE = new Entity("&quot;", "\"");
    public static final Entity NO_BREAK_SPACE = new Entity("&nbsp;", "\u00A0");
    public static final Entity LEFT_ARROW = new Entity("&leftarrow;", "\u2190");
    public static final Entity UP_ARROW = new Entity("&uparrow;", "\u2191");
    public static final Entity RIGHT_ARROW = new Entity("&rightarrow;", "\u2192");
    public static final Entity DOWN_ARROW = new Entity("&downarrow;", "\u2193");

    public final String text;
    private final String characters;

    /**
     * Creates an entity with a given name or numeric value.
     *
     * @param name the name, or numeric value
     * @return the entity
     */
    public static Entity of(String name) {
        String reference = "&" + name + ";";
        String characters = com.sun.tools.javac.api.Entity.getCharacters(name);
        return new Entity(reference, characters != null ? characters : reference);
    }

    private Entity(String text, String characters) {
        this.text = text;
        this.characters = characters;
    }

    @Override
    public boolean write(ContentWriter writer, boolean atNewline) throws IOException {
        return writer.writeRaw(text);
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public boolean isPhrasingContent() {
        return true;
    }

    @Override
    public int charCount() {
        return characters.length();
    }

    @Override
    public String toPlainText() {
        return characters;
    }
}
