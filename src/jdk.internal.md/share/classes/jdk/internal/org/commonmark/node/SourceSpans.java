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

package jdk.internal.org.commonmark.node;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A list of source spans that can be added to. Takes care of merging adjacent source spans.
 *
 * @since 0.16.0
 */
public class SourceSpans {

    private List<SourceSpan> sourceSpans;

    public static SourceSpans empty() {
        return new SourceSpans();
    }

    public List<SourceSpan> getSourceSpans() {
        return sourceSpans != null ? sourceSpans : Collections.<SourceSpan>emptyList();
    }

    public void addAllFrom(Iterable<? extends Node> nodes) {
        for (Node node : nodes) {
            addAll(node.getSourceSpans());
        }
    }

    public void addAll(List<SourceSpan> other) {
        if (other.isEmpty()) {
            return;
        }

        if (sourceSpans == null) {
            sourceSpans = new ArrayList<>();
        }

        if (sourceSpans.isEmpty()) {
            sourceSpans.addAll(other);
        } else {
            int lastIndex = sourceSpans.size() - 1;
            SourceSpan a = sourceSpans.get(lastIndex);
            SourceSpan b = other.get(0);
            if (a.getLineIndex() == b.getLineIndex() && a.getColumnIndex() + a.getLength() == b.getColumnIndex()) {
                sourceSpans.set(lastIndex, SourceSpan.of(a.getLineIndex(), a.getColumnIndex(), a.getLength() + b.getLength()));
                sourceSpans.addAll(other.subList(1, other.size()));
            } else {
                sourceSpans.addAll(other);
            }
        }
    }
}
