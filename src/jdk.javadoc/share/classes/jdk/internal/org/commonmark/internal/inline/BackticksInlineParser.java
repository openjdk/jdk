package jdk.internal.org.commonmark.internal.inline;

import jdk.internal.org.commonmark.internal.util.Parsing;
import jdk.internal.org.commonmark.node.Code;
import jdk.internal.org.commonmark.node.Text;
import jdk.internal.org.commonmark.parser.SourceLines;

/**
 * Attempt to parse backticks, returning either a backtick code span or a literal sequence of backticks.
 */
public class BackticksInlineParser implements InlineContentParser {

    @Override
    public ParsedInline tryParse(InlineParserState inlineParserState) {
        Scanner scanner = inlineParserState.scanner();
        Position start = scanner.position();
        int openingTicks = scanner.matchMultiple('`');
        Position afterOpening = scanner.position();

        while (scanner.find('`') > 0) {
            Position beforeClosing = scanner.position();
            int count = scanner.matchMultiple('`');
            if (count == openingTicks) {
                Code node = new Code();

                String content = scanner.getSource(afterOpening, beforeClosing).getContent();
                content = content.replace('\n', ' ');

                // spec: If the resulting string both begins and ends with a space character, but does not consist
                // entirely of space characters, a single space character is removed from the front and back.
                if (content.length() >= 3 &&
                        content.charAt(0) == ' ' &&
                        content.charAt(content.length() - 1) == ' ' &&
                        Parsing.hasNonSpace(content)) {
                    content = content.substring(1, content.length() - 1);
                }

                node.setLiteral(content);
                return ParsedInline.of(node, scanner.position());
            }
        }

        // If we got here, we didn't find a matching closing backtick sequence.
        SourceLines source = scanner.getSource(start, afterOpening);
        Text text = new Text(source.getContent());
        return ParsedInline.of(text, afterOpening);
    }
}
