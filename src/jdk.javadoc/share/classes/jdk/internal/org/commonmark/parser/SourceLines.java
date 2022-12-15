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
