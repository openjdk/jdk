package jdk.internal.org.commonmark.parser.block;

import jdk.internal.org.commonmark.parser.SourceLines;

/**
 * Open block parser that was last matched during the continue phase. This is different from the currently active
 * block parser, as an unmatched block is only closed when a new block is started.
 * <p><em>This interface is not intended to be implemented by clients.</em></p>
 */
public interface MatchedBlockParser {

    BlockParser getMatchedBlockParser();

    /**
     * Returns the current paragraph lines if the matched block is a paragraph.
     *
     * @return paragraph content or an empty list
     */
    SourceLines getParagraphLines();

}
