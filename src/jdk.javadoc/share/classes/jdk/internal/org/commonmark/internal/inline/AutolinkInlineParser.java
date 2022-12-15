package jdk.internal.org.commonmark.internal.inline;

import jdk.internal.org.commonmark.node.Link;
import jdk.internal.org.commonmark.node.Text;
import jdk.internal.org.commonmark.parser.SourceLines;

import java.util.regex.Pattern;

/**
 * Attempt to parse an autolink (URL or email in pointy brackets).
 */
public class AutolinkInlineParser implements InlineContentParser {

    private static final Pattern URI = Pattern
            .compile("^[a-zA-Z][a-zA-Z0-9.+-]{1,31}:[^<>\u0000-\u0020]*$");

    private static final Pattern EMAIL = Pattern
            .compile("^([a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*)$");

    @Override
    public ParsedInline tryParse(InlineParserState inlineParserState) {
        Scanner scanner = inlineParserState.scanner();
        scanner.next();
        Position textStart = scanner.position();
        if (scanner.find('>') > 0) {
            SourceLines textSource = scanner.getSource(textStart, scanner.position());
            String content = textSource.getContent();
            scanner.next();

            String destination = null;
            if (URI.matcher(content).matches()) {
                destination = content;
            } else if (EMAIL.matcher(content).matches()) {
                destination = "mailto:" + content;
            }

            if (destination != null) {
                Link link = new Link(destination, null);
                Text text = new Text(content);
                text.setSourceSpans(textSource.getSourceSpans());
                link.appendChild(text);
                return ParsedInline.of(link, scanner.position());
            }
        }
        return ParsedInline.none();
    }
}
