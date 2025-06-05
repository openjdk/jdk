/*
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

/*
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, a notice that is now available elsewhere in this distribution
 * accompanied the original version of this file, and, per its terms,
 * should not be removed.
 */

package jdk.internal.org.commonmark.internal;

import jdk.internal.org.commonmark.node.Text;
import jdk.internal.org.commonmark.parser.delimiter.DelimiterRun;

import java.util.List;

/**
 * Delimiter (emphasis, strong emphasis or custom emphasis).
 */
public class Delimiter implements DelimiterRun {

    public final List<Text> characters;
    public final char delimiterChar;
    private final int originalLength;

    // Can open emphasis, see spec.
    private final boolean canOpen;

    // Can close emphasis, see spec.
    private final boolean canClose;

    public Delimiter previous;
    public Delimiter next;

    public Delimiter(List<Text> characters, char delimiterChar, boolean canOpen, boolean canClose, Delimiter previous) {
        this.characters = characters;
        this.delimiterChar = delimiterChar;
        this.canOpen = canOpen;
        this.canClose = canClose;
        this.previous = previous;
        this.originalLength = characters.size();
    }

    @Override
    public boolean canOpen() {
        return canOpen;
    }

    @Override
    public boolean canClose() {
        return canClose;
    }

    @Override
    public int length() {
        return characters.size();
    }

    @Override
    public int originalLength() {
        return originalLength;
    }

    @Override
    public Text getOpener() {
        return characters.get(characters.size() - 1);
    }

    @Override
    public Text getCloser() {
        return characters.get(0);
    }

    @Override
    public Iterable<Text> getOpeners(int length) {
        if (!(length >= 1 && length <= length())) {
            throw new IllegalArgumentException("length must be between 1 and " + length() + ", was " + length);
        }

        return characters.subList(characters.size() - length, characters.size());
    }

    @Override
    public Iterable<Text> getClosers(int length) {
        if (!(length >= 1 && length <= length())) {
            throw new IllegalArgumentException("length must be between 1 and " + length() + ", was " + length);
        }

        return characters.subList(0, length);
    }
}
