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

package jdk.internal.org.commonmark.internal.inline;

import jdk.internal.org.commonmark.node.*;
import jdk.internal.org.commonmark.parser.delimiter.DelimiterProcessor;
import jdk.internal.org.commonmark.parser.delimiter.DelimiterRun;

public abstract class EmphasisDelimiterProcessor implements DelimiterProcessor {

    private final char delimiterChar;

    protected EmphasisDelimiterProcessor(char delimiterChar) {
        this.delimiterChar = delimiterChar;
    }

    @Override
    public char getOpeningCharacter() {
        return delimiterChar;
    }

    @Override
    public char getClosingCharacter() {
        return delimiterChar;
    }

    @Override
    public int getMinLength() {
        return 1;
    }

    @Override
    public int process(DelimiterRun openingRun, DelimiterRun closingRun) {
        // "multiple of 3" rule for internal delimiter runs
        if ((openingRun.canClose() || closingRun.canOpen()) &&
                closingRun.originalLength() % 3 != 0 &&
                (openingRun.originalLength() + closingRun.originalLength()) % 3 == 0) {
            return 0;
        }

        int usedDelimiters;
        Node emphasis;
        // calculate actual number of delimiters used from this closer
        if (openingRun.length() >= 2 && closingRun.length() >= 2) {
            usedDelimiters = 2;
            emphasis = new StrongEmphasis(String.valueOf(delimiterChar) + delimiterChar);
        } else {
            usedDelimiters = 1;
            emphasis = new Emphasis(String.valueOf(delimiterChar));
        }

        SourceSpans sourceSpans = SourceSpans.empty();
        sourceSpans.addAllFrom(openingRun.getOpeners(usedDelimiters));

        Text opener = openingRun.getOpener();
        for (Node node : Nodes.between(opener, closingRun.getCloser())) {
            emphasis.appendChild(node);
            sourceSpans.addAll(node.getSourceSpans());
        }

        sourceSpans.addAllFrom(closingRun.getClosers(usedDelimiters));

        emphasis.setSourceSpans(sourceSpans.getSourceSpans());
        opener.insertAfter(emphasis);

        return usedDelimiters;
    }
}
