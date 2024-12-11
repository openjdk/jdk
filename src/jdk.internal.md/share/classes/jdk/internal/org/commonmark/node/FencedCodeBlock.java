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

public class FencedCodeBlock extends Block {

    private String fenceCharacter;
    private Integer openingFenceLength;
    private Integer closingFenceLength;
    private int fenceIndent;

    private String info;
    private String literal;

    @Override
    public void accept(Visitor visitor) {
        visitor.visit(this);
    }

    /**
     * @return the fence character that was used, e.g. {@code `} or {@code ~}, if available, or null otherwise
     */
    public String getFenceCharacter() {
        return fenceCharacter;
    }

    public void setFenceCharacter(String fenceCharacter) {
        this.fenceCharacter = fenceCharacter;
    }

    /**
     * @return the length of the opening fence (how many of {{@link #getFenceCharacter()}} were used to start the code
     * block) if available, or null otherwise
     */
    public Integer getOpeningFenceLength() {
        return openingFenceLength;
    }

    public void setOpeningFenceLength(Integer openingFenceLength) {
        if (openingFenceLength != null && openingFenceLength < 3) {
            throw new IllegalArgumentException("openingFenceLength needs to be >= 3");
        }
        checkFenceLengths(openingFenceLength, closingFenceLength);
        this.openingFenceLength = openingFenceLength;
    }

    /**
     * @return the length of the closing fence (how many of {@link #getFenceCharacter()} were used to end the code
     * block) if available, or null otherwise
     */
    public Integer getClosingFenceLength() {
        return closingFenceLength;
    }

    public void setClosingFenceLength(Integer closingFenceLength) {
        if (closingFenceLength != null && closingFenceLength < 3) {
            throw new IllegalArgumentException("closingFenceLength needs to be >= 3");
        }
        checkFenceLengths(openingFenceLength, closingFenceLength);
        this.closingFenceLength = closingFenceLength;
    }

    public int getFenceIndent() {
        return fenceIndent;
    }

    public void setFenceIndent(int fenceIndent) {
        this.fenceIndent = fenceIndent;
    }

    /**
     * @see <a href="http://spec.commonmark.org/0.18/#info-string">CommonMark spec</a>
     */
    public String getInfo() {
        return info;
    }

    public void setInfo(String info) {
        this.info = info;
    }

    public String getLiteral() {
        return literal;
    }

    public void setLiteral(String literal) {
        this.literal = literal;
    }

    /**
     * @deprecated use {@link #getFenceCharacter()} instead
     */
    @Deprecated
    public char getFenceChar() {
        return fenceCharacter != null && !fenceCharacter.isEmpty() ? fenceCharacter.charAt(0) : '\0';
    }

    /**
     * @deprecated use {@link #setFenceCharacter} instead
     */
    @Deprecated
    public void setFenceChar(char fenceChar) {
        this.fenceCharacter = fenceChar != '\0' ? String.valueOf(fenceChar) : null;
    }

    /**
     * @deprecated use {@link #getOpeningFenceLength} instead
     */
    @Deprecated
    public int getFenceLength() {
        return openingFenceLength != null ? openingFenceLength : 0;
    }

    /**
     * @deprecated use {@link #setOpeningFenceLength} instead
     */
    @Deprecated
    public void setFenceLength(int fenceLength) {
        this.openingFenceLength = fenceLength != 0 ? fenceLength : null;
    }

    private static void checkFenceLengths(Integer openingFenceLength, Integer closingFenceLength) {
        if (openingFenceLength != null && closingFenceLength != null) {
            if (closingFenceLength < openingFenceLength) {
                throw new IllegalArgumentException("fence lengths required to be: closingFenceLength >= openingFenceLength");
            }
        }
    }
}
