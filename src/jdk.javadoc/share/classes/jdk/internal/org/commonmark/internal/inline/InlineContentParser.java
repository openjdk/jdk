package jdk.internal.org.commonmark.internal.inline;

public interface InlineContentParser {

    ParsedInline tryParse(InlineParserState inlineParserState);
}
