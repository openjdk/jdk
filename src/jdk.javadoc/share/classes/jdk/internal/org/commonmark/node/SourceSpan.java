package jdk.internal.org.commonmark.node;

import java.util.Objects;

/**
 * A source span references a snippet of text from the source input.
 * <p>
 * It has a starting position (line and column index) and a length of how many characters it spans.
 * <p>
 * For example, this CommonMark source text:
 * <pre><code>
 * &gt; foo
 * </code></pre>
 * The {@link BlockQuote} node would have this source span: line 0, column 0, length 5.
 * <p>
 * The {@link Paragraph} node inside it would have: line 0, column 2, length 3.
 * <p>
 * If a block has multiple lines, it will have a source span for each line.
 * <p>
 * Note that the column index and length are measured in Java characters (UTF-16 code units). If you're outputting them
 * to be consumed by another programming language, e.g. one that uses UTF-8 strings, you will need to translate them,
 * otherwise characters such as emojis will result in incorrect positions.
 *
 * @since 0.16.0
 */
public class SourceSpan {

    private final int lineIndex;
    private final int columnIndex;
    private final int length;

    public static SourceSpan of(int lineIndex, int columnIndex, int length) {
        return new SourceSpan(lineIndex, columnIndex, length);
    }

    private SourceSpan(int lineIndex, int columnIndex, int length) {
        this.lineIndex = lineIndex;
        this.columnIndex = columnIndex;
        this.length = length;
    }

    /**
     * @return 0-based index of line in source
     */
    public int getLineIndex() {
        return lineIndex;
    }

    /**
     * @return 0-based index of column (character on line) in source
     */
    public int getColumnIndex() {
        return columnIndex;
    }

    /**
     * @return length of the span in characters
     */
    public int getLength() {
        return length;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SourceSpan that = (SourceSpan) o;
        return lineIndex == that.lineIndex &&
                columnIndex == that.columnIndex &&
                length == that.length;
    }

    @Override
    public int hashCode() {
        return Objects.hash(lineIndex, columnIndex, length);
    }

    @Override
    public String toString() {
        return "SourceSpan{" +
                "line=" + lineIndex +
                ", column=" + columnIndex +
                ", length=" + length +
                "}";
    }
}
