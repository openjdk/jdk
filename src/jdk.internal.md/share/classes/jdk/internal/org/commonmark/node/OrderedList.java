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

public class OrderedList extends ListBlock {

    private String markerDelimiter;
    private Integer markerStartNumber;

    @Override
    public void accept(Visitor visitor) {
        visitor.visit(this);
    }

    /**
     * @return the start number used in the marker, e.g. {@code 1}, if available, or null otherwise
     */
    public Integer getMarkerStartNumber() {
        return markerStartNumber;
    }

    public void setMarkerStartNumber(Integer markerStartNumber) {
        this.markerStartNumber = markerStartNumber;
    }

    /**
     * @return the delimiter used in the marker, e.g. {@code .} or {@code )}, if available, or null otherwise
     */
    public String getMarkerDelimiter() {
        return markerDelimiter;
    }

    public void setMarkerDelimiter(String markerDelimiter) {
        this.markerDelimiter = markerDelimiter;
    }

    /**
     * @deprecated use {@link #getMarkerStartNumber()} instead
     */
    @Deprecated
    public int getStartNumber() {
        return markerStartNumber != null ? markerStartNumber : 0;
    }

    /**
     * @deprecated use {@link #setMarkerStartNumber} instead
     */
    @Deprecated
    public void setStartNumber(int startNumber) {
        this.markerStartNumber = startNumber != 0 ? startNumber : null;
    }

    /**
     * @deprecated use {@link #getMarkerDelimiter()} instead
     */
    @Deprecated
    public char getDelimiter() {
        return markerDelimiter != null && !markerDelimiter.isEmpty() ? markerDelimiter.charAt(0) : '\0';
    }

    /**
     * @deprecated use {@link #setMarkerDelimiter} instead
     */
    @Deprecated
    public void setDelimiter(char delimiter) {
        this.markerDelimiter = delimiter != '\0' ? String.valueOf(delimiter) : null;
    }
}
