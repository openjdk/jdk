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

package jdk.internal.org.commonmark.parser;

import jdk.internal.org.commonmark.node.SourceSpan;

import java.util.ArrayList;
import java.util.List;

/**
 * A set of lines ({@link SourceLine}) from the input source.
 *
 * @since 0.16.0
 */
public class SourceLines {

    private final List<SourceLine> lines = new ArrayList<>();

    public static SourceLines empty() {
        return new SourceLines();
    }

    public static SourceLines of(SourceLine sourceLine) {
        SourceLines sourceLines = new SourceLines();
        sourceLines.addLine(sourceLine);
        return sourceLines;
    }

    public static SourceLines of(List<SourceLine> sourceLines) {
        SourceLines result = new SourceLines();
        result.lines.addAll(sourceLines);
        return result;
    }

    public void addLine(SourceLine sourceLine) {
        lines.add(sourceLine);
    }

    public List<SourceLine> getLines() {
        return lines;
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }

    public String getContent() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i != 0) {
                sb.append('\n');
            }
            sb.append(lines.get(i).getContent());
        }
        return sb.toString();
    }

    public List<SourceSpan> getSourceSpans() {
        List<SourceSpan> sourceSpans = new ArrayList<>();
        for (SourceLine line : lines) {
            SourceSpan sourceSpan = line.getSourceSpan();
            if (sourceSpan != null) {
                sourceSpans.add(sourceSpan);
            }
        }
        return sourceSpans;
    }
}
