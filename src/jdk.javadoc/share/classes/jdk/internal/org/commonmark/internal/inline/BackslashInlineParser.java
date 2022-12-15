package jdk.internal.org.commonmark.internal.inline;

import jdk.internal.org.commonmark.internal.util.Escaping;
import jdk.internal.org.commonmark.node.HardLineBreak;
import jdk.internal.org.commonmark.node.Node;
import jdk.internal.org.commonmark.node.Text;

import java.util.regex.Pattern;

/**
 * Parse a backslash-escaped special character, adding either the escaped  character, a hard line break
 * (if the backslash is followed by a newline), or a literal backslash to the block's children.
 */
public class BackslashInlineParser implements InlineContentParser {

    private static final Pattern ESCAPABLE = Pattern.compile('^' + Escaping.ESCAPABLE);

    @Override
    public ParsedInline tryParse(InlineParserState inlineParserState) {
        Scanner scanner = inlineParserState.scanner();
        // Backslash
        scanner.next();

        char next = scanner.peek();
        if (next == '\n') {
            scanner.next();
            return ParsedInline.of(new HardLineBreak(), scanner.position());
        } else if (ESCAPABLE.matcher(String.valueOf(next)).matches()) {
            scanner.next();
            return ParsedInline.of(new Text(String.valueOf(next)), scanner.position());
        } else {
            return ParsedInline.of(new Text("\\"), scanner.position());
        }
    }
}
