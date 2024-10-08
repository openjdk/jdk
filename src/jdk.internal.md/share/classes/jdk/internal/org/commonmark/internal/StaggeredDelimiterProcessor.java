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

import jdk.internal.org.commonmark.parser.delimiter.DelimiterProcessor;
import jdk.internal.org.commonmark.parser.delimiter.DelimiterRun;

import java.util.LinkedList;
import java.util.ListIterator;

/**
 * An implementation of DelimiterProcessor that dispatches all calls to two or more other DelimiterProcessors
 * depending on the length of the delimiter run. All child DelimiterProcessors must have different minimum
 * lengths. A given delimiter run is dispatched to the child with the largest acceptable minimum length. If no
 * child is applicable, the one with the largest minimum length is chosen.
 */
class StaggeredDelimiterProcessor implements DelimiterProcessor {

    private final char delim;
    private int minLength = 0;
    private LinkedList<DelimiterProcessor> processors = new LinkedList<>(); // in reverse getMinLength order

    StaggeredDelimiterProcessor(char delim) {
        this.delim = delim;
    }


    @Override
    public char getOpeningCharacter() {
        return delim;
    }

    @Override
    public char getClosingCharacter() {
        return delim;
    }

    @Override
    public int getMinLength() {
        return minLength;
    }

    void add(DelimiterProcessor dp) {
        final int len = dp.getMinLength();
        ListIterator<DelimiterProcessor> it = processors.listIterator();
        boolean added = false;
        while (it.hasNext()) {
            DelimiterProcessor p = it.next();
            int pLen = p.getMinLength();
            if (len > pLen) {
                it.previous();
                it.add(dp);
                added = true;
                break;
            } else if (len == pLen) {
                throw new IllegalArgumentException("Cannot add two delimiter processors for char '" + delim + "' and minimum length " + len + "; conflicting processors: " + p + ", " + dp);
            }
        }
        if (!added) {
            processors.add(dp);
            this.minLength = len;
        }
    }

    private DelimiterProcessor findProcessor(int len) {
        for (DelimiterProcessor p : processors) {
            if (p.getMinLength() <= len) {
                return p;
            }
        }
        return processors.getFirst();
    }

    @Override
    public int process(DelimiterRun openingRun, DelimiterRun closingRun) {
        return findProcessor(openingRun.length()).process(openingRun, closingRun);
    }
}
