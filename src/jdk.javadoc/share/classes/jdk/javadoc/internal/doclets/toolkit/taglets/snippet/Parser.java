/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.doclets.toolkit.taglets.snippet;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import jdk.javadoc.internal.doclets.toolkit.Resources;
import jdk.javadoc.internal.doclets.toolkit.taglets.SnippetTaglet;

/*
 * Semantics of a EOL comment; plus
 * 1. This parser treats input as plain text. This may result in markup being
 * produced from unexpected places; for example, when parsing Java text blocks:
 *
 *     String text =
 *         """
 *             // @start x
 *         """;
 *
 * false positives are possible, but false negatives are not.
 * To remediate that, perhaps a no-op trailing // @start x @end x might be added.
 *
 * 2. To allow some preexisting constructs, unknown actions in a leading position are skipped;
 * for example, "// @formatter:on" marker in IntelliJ IDEA is ignored.
 *
 * 3. This match's value can be confused for a trailing markup.
 *
 *     String x; // comment // another comment // @formatter:on // @highlight match="// @"
 *
 * Do we need escapes?
 *
 * 4. Rules for EOL are very different among formats: compare Java's // with properties' #/!
 *
 * 5. A convenience `end` ends all the things started so far.
 */
/**
 * A parser of snippet content.
 */
public final class Parser {

    private static final Pattern JAVA_COMMENT = Pattern.compile(
            "^(?<payload>.*)//(?<markup>\\s*@\\s*\\w+.+?)$");
    private static final Pattern PROPERTIES_COMMENT = Pattern.compile(
            "^(?<payload>[ \t]*([#!].*)?)[#!](?<markup>\\s*@\\s*\\w+.+?)$");

    private final Resources resources;
    private final MarkupParser markupParser;

    // Incomplete actions waiting for their complementary @end
    private final Regions regions = new Regions();
    private final Queue<Tag> tags = new LinkedList<>();

    public Parser(Resources resources) {
        this.resources = resources;
        this.markupParser = new MarkupParser(resources);
    }

    public Result parse(SnippetTaglet.Diags diags, Optional<SnippetTaglet.Language> language, String source) throws ParseException {
        SnippetTaglet.Language lang = language.orElse(SnippetTaglet.Language.JAVA);
        var p = switch (lang) {
            case JAVA -> JAVA_COMMENT;
            case PROPERTIES -> PROPERTIES_COMMENT;
        };
        return parse(diags, p, source);
    }

    /*
     * Newline characters in the returned text are of the \n form.
     */
    private Result parse(SnippetTaglet.Diags diags, Pattern commentPattern, String source) throws ParseException {
        Objects.requireNonNull(commentPattern);
        Objects.requireNonNull(source);

        Matcher markedUpLine = commentPattern.matcher(""); // reusable matcher

        tags.clear();
        regions.clear();

        Queue<Action> actions = new LinkedList<>();

        StyledText text = new StyledText();
        boolean trailingNewline = source.endsWith("\r") || source.endsWith("\n");
        int lineStart = 0;
        List<Tag> previousLineTags = new ArrayList<>();
        List<Tag> thisLineTags = new ArrayList<>();
        List<Tag> tempList = new ArrayList<>();

        // while lines could be computed lazily, it would yield more complex code
        record OffsetAndLine(int offset, String line) { }
        var offsetAndLines = new LinkedList<OffsetAndLine>();
        forEachLine(source, (off, line) -> offsetAndLines.add(new OffsetAndLine(off, line)));
        Iterator<OffsetAndLine> iterator = offsetAndLines.iterator();

        while (iterator.hasNext()) {
            // There are 3 cases:
            //   1. The pattern that describes a marked-up line is not matched
            //   2. While the pattern is matched, the markup is not recognized
            //   3. Both the pattern is matched and the markup is recognized
            OffsetAndLine next = iterator.next();
            String rawLine = next.line();
            boolean addLineTerminator = iterator.hasNext() || trailingNewline;
            String line;
            boolean hasMarkup = false;
            markedUpLine.reset(rawLine);
            if (!markedUpLine.matches()) { // (1)
                line = rawLine + (addLineTerminator ? "\n" : "");
            } else {
                String maybeMarkup = rawLine.substring(markedUpLine.start("markup"));
                List<Tag> parsedTags;
                try {
                    parsedTags = markupParser.parse(maybeMarkup);
                } catch (ParseException e) {
                    // translate error position from markup to file line
                    throw new ParseException(e::getMessage, next.offset() + markedUpLine.start("markup") + e.getPosition());
                }
                for (Tag t : parsedTags) {
                    t.lineSourceOffset = next.offset();
                    t.markupLineOffset = markedUpLine.start("markup");
                }
                thisLineTags.addAll(parsedTags);
                for (var tagIterator = thisLineTags.iterator(); tagIterator.hasNext(); ) {
                    Tag t = tagIterator.next();
                    if (t.appliesToNextLine) {
                        tagIterator.remove();
                        t.appliesToNextLine = false; // clear the flag
                        tempList.add(t);
                    }
                }
                if (parsedTags.isEmpty()) { // (2)
                    diags.warn(resources.getText("doclet.snippet.markup.spurious"), next.offset() + markedUpLine.start("markup"));
                    line = rawLine + (addLineTerminator ? "\n" : "");
                } else { // (3)
                    hasMarkup = true;
                    String payload = rawLine.substring(0, markedUpLine.end("payload"));
                    line = payload + (addLineTerminator ? "\n" : "");
                }
            }

            thisLineTags.addAll(0, previousLineTags); // prepend!
            previousLineTags.clear();
            for (Tag t : thisLineTags) {
                t.start = lineStart;
                t.end = lineStart + line.length(); // this includes line terminator, if any
                processTag(t);
            }
            previousLineTags.addAll(tempList);
            tempList.clear();

            thisLineTags.clear();

            append(text, line.isBlank() && hasMarkup ? Set.of(new Style.Markup()) : Set.of(), line);
            // TODO: mark up trailing whitespace!
            lineStart += line.length();
        }

        if (!previousLineTags.isEmpty()) {
            Tag t = previousLineTags.iterator().next();
            String message = resources.getText("doclet.snippet.markup.tag.non.existent.lines");
            throw new ParseException(() -> message, t.lineSourceOffset
                    + t.markupLineOffset + t.nameLineOffset);
        }

        for (var t : tags) {

            // Translate a list of attributes into a more convenient form
            Attributes attributes = new Attributes(t.attributes());

            final var substring = attributes.get("substring", Attribute.Valued.class);
            final var regex = attributes.get("regex", Attribute.Valued.class);

            if (!t.name().equals("start") && substring.isPresent() && regex.isPresent()) {
                throw newParseException(t.lineSourceOffset + t.markupLineOffset
                                + substring.get().nameStartPosition(),
                        "doclet.snippet.markup.attribute.simultaneous.use",
                        "substring", "regex");
            }

            switch (t.name()) {
                case "link" -> {
                    var target = attributes.get("target", Attribute.Valued.class)
                            .orElseThrow(() -> newParseException(t.lineSourceOffset
                                    + t.markupLineOffset + t.nameLineOffset,
                                    "doclet.snippet.markup.attribute.absent", "target"));
                    // "type" is what HTML calls an enumerated attribute
                    var type = attributes.get("type", Attribute.Valued.class);
                    String typeValue = type.isPresent() ? type.get().value() : "link";
                    if (!typeValue.equals("link") && !typeValue.equals("linkplain")) {
                        throw newParseException(t.lineSourceOffset + t.markupLineOffset
                                + type.get().valueStartPosition(),
                                "doclet.snippet.markup.attribute.value.invalid", typeValue);
                    }
                    AddStyle a = new AddStyle(new Style.Link(target.value()),
                            // the default regex is different so as not to include newline
                            createRegexPattern(substring, regex, ".+",
                                    t.lineSourceOffset + t.markupLineOffset),
                            text.subText(t.start(), t.end()));
                    actions.add(a);
                }
                case "replace" -> {
                    var replacement = attributes.get("replacement", Attribute.Valued.class)
                            .orElseThrow(() -> newParseException(t.lineSourceOffset
                                    + t.markupLineOffset + t.nameLineOffset,
                                    "doclet.snippet.markup.attribute.absent", "replacement"));
                    Replace a = new Replace(replacement.value(),
                            createRegexPattern(substring, regex,
                                    t.lineSourceOffset + t.markupLineOffset),
                            text.subText(t.start(), t.end()));
                    actions.add(a);
                }
                case "highlight" -> {
                    var type = attributes.get("type", Attribute.Valued.class);

                    String typeValue = type.isPresent() ? type.get().value() : "bold";

                    AddStyle a = new AddStyle(new Style.Name(typeValue),
                            createRegexPattern(substring, regex,
                                    t.lineSourceOffset + t.markupLineOffset),
                            text.subText(t.start(), t.end()));
                    actions.add(a);
                }
                case "start" -> {
                    var region = attributes.get("region", Attribute.Valued.class)
                            .orElseThrow(() -> newParseException(t.lineSourceOffset
                                    + t.markupLineOffset + t.nameLineOffset,
                                    "doclet.snippet.markup.attribute.absent", "region"));
                    String regionValue = region.value();
                    if (regionValue.isBlank()) {
                        throw newParseException(t.lineSourceOffset + t.markupLineOffset
                                + region.valueStartPosition(), "doclet.snippet.markup.attribute.value.invalid");
                    }
                    for (Attribute a : t.attributes) {
                        if (!a.name().equals("region")) {
                            throw newParseException(t.lineSourceOffset +
                                            t.markupLineOffset + a.nameStartPosition(),
                                    "doclet.snippet.markup.attribute.unexpected");
                        }
                    }
                    actions.add(new Bookmark(region.value(), text.subText(t.start(), t.end() - 1)));
                }
            }
        }

        // also report on unpaired with corresponding `end` or unknown tags
        if (!regions.isEmpty()) {
            Optional<Tag> tag = regions.removeLast(); // any of these tags would do
            Tag t = tag.get();
            String message = resources.getText("doclet.snippet.markup.region.unpaired");
            throw new ParseException(() -> message, t.lineSourceOffset
                    + t.markupLineOffset + t.nameLineOffset);
        }

        return new Result(text, actions);
    }

    private ParseException newParseException(int pos, String key, Object... args) {
        String message = resources.getText(key, args);
        return new ParseException(() -> message, pos);
    }

    private Pattern createRegexPattern(Optional<Attribute.Valued> substring,
                                       Optional<Attribute.Valued> regex,
                                       int offset) throws ParseException {
        return createRegexPattern(substring, regex, "(?s).+", offset);
    }

    private Pattern createRegexPattern(Optional<Attribute.Valued> substring,
                                       Optional<Attribute.Valued> regex,
                                       String defaultRegex,
                                       int offset) throws ParseException {
        Pattern pattern;
        if (substring.isPresent()) {
            // this Pattern.compile *cannot* throw an exception
            pattern = Pattern.compile(Pattern.quote(substring.get().value()));
        } else if (regex.isEmpty()) {
            // this Pattern.compile *should not* throw an exception
            pattern = Pattern.compile(defaultRegex);
        } else {
            final String value = regex.get().value();
            try {
                pattern = Pattern.compile(value);
            } catch (PatternSyntaxException e) {
                // Unlike string literals in Java source, attribute values in
                // snippet markup do not use escape sequences. This is why
                // indices of characters in the regex pattern directly map to
                // their corresponding positions in snippet source. Refine
                // position using e.getIndex() only if that index is relevant to
                // the regex in the attribute value. Index might be irrelevant
                // because it refers to an internal representation of regex,
                // e.getPattern(), which might be a normalized or partial view
                // of the original pattern.
                int pos = offset + regex.get().valueStartPosition();
                if (e.getIndex() > -1 && value.equals(e.getPattern())) {
                    pos += e.getIndex();
                }
                // getLocalized cannot be used because it provides a localized
                // version of getMessage(), which in the case of this particular
                // exception is multi-line with the caret. If we used that,
                // it would duplicate the information we're trying to provide.
                String message = resources.getText("doclet.snippet.markup.regex.invalid");
                throw new ParseException(() -> message, pos);
            }
        }
        return pattern;
    }

    private void processTag(Tag t) throws ParseException {

        Attributes attributes = new Attributes(t.attributes()); // TODO: avoid creating attributes twice
        Optional<Attribute> region = attributes.get("region", Attribute.class);

        if (!t.name().equals("end")) {
            tags.add(t);
            if (region.isPresent()) {
                if (region.get() instanceof Attribute.Valued v) {
                    String name = v.value();
                    if (!regions.addNamed(name, t)) {
                        throw newParseException(t.lineSourceOffset + t.markupLineOffset
                                + v.valueStartPosition(), "doclet.snippet.markup.region.duplicated", name);
                    }
                } else {
                    // TODO: change to exhaustive switch after "Pattern Matching for switch" is implemented
                    assert region.get() instanceof Attribute.Valueless;
                    regions.addAnonymous(t);
                }
            }
        } else {
            if (region.isEmpty() || region.get() instanceof Attribute.Valueless) {
                Optional<Tag> tag = regions.removeLast();
                if (tag.isEmpty()) {
                    throw newParseException(t.lineSourceOffset + t.markupLineOffset
                            + t.nameLineOffset, "doclet.snippet.markup.region.none");
                }
                completeTag(tag.get(), t);
            } else {
                assert region.get() instanceof Attribute.Valued;
                String name = ((Attribute.Valued) region.get()).value();
                Optional<Tag> tag = regions.removeNamed(name);
                if (tag.isEmpty()) {
                    throw newParseException(t.lineSourceOffset + t.markupLineOffset
                            + region.get().nameStartPosition(), "doclet.snippet.markup.region.unpaired", name);
                }
                completeTag(tag.get(), t);
            }
        }
    }

    static final class Tag {

        String name;
        int lineSourceOffset;
        int markupLineOffset;
        int nameLineOffset;
        int start;
        int end;
        List<Attribute> attributes;
        boolean appliesToNextLine;

        String name() {
            return name;
        }

        List<Attribute> attributes() {
            return attributes;
        }

        int start() {
            return start;
        }

        int end() {
            return end;
        }

        @Override
        public String toString() {
            return "Tag{" +
                    "name='" + name + '\'' +
                    ", start=" + start +
                    ", end=" + end +
                    ", attributes=" + attributes +
                    '}';
        }
    }

    private void completeTag(Tag start, Tag end) {
        assert !start.name().equals("end") : start;
        assert end.name().equals("end") : end;
        start.end = end.end();
    }

    private void append(StyledText text, Set<Style> style, CharSequence s) {
        text.subText(text.length(), text.length()).replace(style, s.toString());
    }

    public record Result(StyledText text, Queue<Action> actions) { }

    /*
     * Encapsulates the data structure used to manage regions.
     *
     * boolean-returning commands return true if succeed and false if fail.
     */
    public static final class Regions {

        /*
         * LinkedHashMap does not fit here because of both the need for unique
         * keys for anonymous regions and inability to easily access the most
         * recently put entry.
         *
         * Since we expect only a few regions, a list will do.
         */
        private final ArrayList<Map.Entry<Optional<String>, Tag>> tags = new ArrayList<>();

        void addAnonymous(Tag i) {
            tags.add(Map.entry(Optional.empty(), i));
        }

        boolean addNamed(String name, Tag i) {
            boolean matches = tags.stream()
                    .anyMatch(entry -> entry.getKey().isPresent() && entry.getKey().get().equals(name));
            if (matches) {
                return false; // won't add a duplicate
            }
            tags.add(Map.entry(Optional.of(name), i));
            return true;
        }

        Optional<Tag> removeNamed(String name) {
            for (var iterator = tags.iterator(); iterator.hasNext(); ) {
                var entry = iterator.next();
                if (entry.getKey().isPresent() && entry.getKey().get().equals(name)) {
                    iterator.remove();
                    return Optional.of(entry.getValue());
                }
            }
            return Optional.empty();
        }

        Optional<Tag> removeLast() {
            if (tags.isEmpty()) {
                return Optional.empty();
            }
            Map.Entry<Optional<String>, Tag> e = tags.remove(tags.size() - 1);
            return Optional.of(e.getValue());
        }

        void clear() {
            tags.clear();
        }

        boolean isEmpty() {
            return tags.isEmpty();
        }
    }

    /*
     * The reason that the lines are split using a custom method as opposed to
     * String.split(String) or String.lines() is that along with the lines
     * themselves we also need their offsets in the originating input to supply
     * to diagnostic exceptions should they arise.
     *
     * The reason that "\n|(\r\n)|\r" is used instead of "\\R" is that the
     * latter is UNICODE-aware, which we must be not.
     */
    static void forEachLine(String s, LineConsumer consumer) {
        // the fact that the regex alternation is *ordered* is used here to try
        // to match \r\n before \r
        final Pattern NEWLINE = Pattern.compile("\n|(\r\n)|\r");
        Matcher matcher = NEWLINE.matcher(s);
        int pos = 0;
        while (matcher.find()) {
            consumer.accept(pos, s.substring(pos, matcher.start()));
            pos = matcher.end();
        }
        if (pos < s.length())
            consumer.accept(pos, s.substring(pos));
    }

    /*
     * This interface is introduced to encapsulate the matching mechanics so
     * that it wouldn't be obtrusive to the client code.
     */
    @FunctionalInterface
    interface LineConsumer {
        void accept(int offset, String line);
    }
}
