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
