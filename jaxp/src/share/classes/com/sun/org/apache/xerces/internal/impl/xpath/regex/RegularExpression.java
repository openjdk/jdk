/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Copyright 1999-2002,2004,2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sun.org.apache.xerces.internal.impl.xpath.regex;

import java.text.CharacterIterator;

/**
 * A regular expression matching engine using Non-deterministic Finite Automaton (NFA).
 * This engine does not conform to the POSIX regular expression.
 *
 * <hr width="50%">
 * <h3>How to use</h3>
 *
 * <dl>
 *   <dt>A. Standard way
 *   <dd>
 * <pre>
 * RegularExpression re = new RegularExpression(<var>regex</var>);
 * if (re.matches(text)) { ... }
 * </pre>
 *
 *   <dt>B. Capturing groups
 *   <dd>
 * <pre>
 * RegularExpression re = new RegularExpression(<var>regex</var>);
 * Match match = new Match();
 * if (re.matches(text, match)) {
 *     ... // You can refer captured texts with methods of the <code>Match</code> class.
 * }
 * </pre>
 *
 * </dl>
 *
 * <h4>Case-insensitive matching</h4>
 * <pre>
 * RegularExpression re = new RegularExpression(<var>regex</var>, "i");
 * if (re.matches(text) >= 0) { ...}
 * </pre>
 *
 * <h4>Options</h4>
 * <p>You can specify options to <a href="#RegularExpression(java.lang.String, java.lang.String)"><code>RegularExpression(</code><var>regex</var><code>, </code><var>options</var><code>)</code></a>
 *    or <a href="#setPattern(java.lang.String, java.lang.String)"><code>setPattern(</code><var>regex</var><code>, </code><var>options</var><code>)</code></a>.
 *    This <var>options</var> parameter consists of the following characters.
 * </p>
 * <dl>
 *   <dt><a name="I_OPTION"><code>"i"</code></a>
 *   <dd>This option indicates case-insensitive matching.
 *   <dt><a name="M_OPTION"><code>"m"</code></a>
 *   <dd class="REGEX"><kbd>^</kbd> and <kbd>$</kbd> consider the EOL characters within the text.
 *   <dt><a name="S_OPTION"><code>"s"</code></a>
 *   <dd class="REGEX"><kbd>.</kbd> matches any one character.
 *   <dt><a name="U_OPTION"><code>"u"</code></a>
 *   <dd class="REGEX">Redefines <Kbd>\d \D \w \W \s \S \b \B \&lt; \></kbd> as becoming to Unicode.
 *   <dt><a name="W_OPTION"><code>"w"</code></a>
 *   <dd class="REGEX">By this option, <kbd>\b \B \&lt; \></kbd> are processed with the method of
 *      'Unicode Regular Expression Guidelines' Revision 4.
 *      When "w" and "u" are specified at the same time,
 *      <kbd>\b \B \&lt; \></kbd> are processed for the "w" option.
 *   <dt><a name="COMMA_OPTION"><code>","</code></a>
 *   <dd>The parser treats a comma in a character class as a range separator.
 *      <kbd class="REGEX">[a,b]</kbd> matches <kbd>a</kbd> or <kbd>,</kbd> or <kbd>b</kbd> without this option.
 *      <kbd class="REGEX">[a,b]</kbd> matches <kbd>a</kbd> or <kbd>b</kbd> with this option.
 *
 *   <dt><a name="X_OPTION"><code>"X"</code></a>
 *   <dd class="REGEX">
 *       By this option, the engine confoms to <a href="http://www.w3.org/TR/2000/WD-xmlschema-2-20000407/#regexs">XML Schema: Regular Expression</a>.
 *       The <code>match()</code> method does not do subsring matching
 *       but entire string matching.
 *
 * </dl>
 *
 * <hr width="50%">
 * <h3>Syntax</h3>
 * <table border="1" bgcolor="#ddeeff">
 *   <tr>
 *    <td>
 *     <h4>Differences from the Perl 5 regular expression</h4>
 *     <ul>
 *      <li>There is 6-digit hexadecimal character representation  (<kbd>\u005cv</kbd><var>HHHHHH</var>.)
 *      <li>Supports subtraction, union, and intersection operations for character classes.
 *      <li>Not supported: <kbd>\</kbd><var>ooo</var> (Octal character representations),
 *          <Kbd>\G</kbd>, <kbd>\C</kbd>, <kbd>\l</kbd><var>c</var>,
 *          <kbd>\u005c u</kbd><var>c</var>, <kbd>\L</kbd>, <kbd>\U</kbd>,
 *          <kbd>\E</kbd>, <kbd>\Q</kbd>, <kbd>\N{</kbd><var>name</var><kbd>}</kbd>,
 *          <Kbd>(?{<kbd><var>code</var><kbd>})</kbd>, <Kbd>(??{<kbd><var>code</var><kbd>})</kbd>
 *     </ul>
 *    </td>
 *   </tr>
 * </table>
 *
 * <P>Meta characters are `<KBD>. * + ? { [ ( ) | \ ^ $</KBD>'.</P>
 * <ul>
 *   <li>Character
 *     <dl>
 *       <dt class="REGEX"><kbd>.</kbd> (A period)
 *       <dd>Matches any one character except the following characters.
 *       <dd>LINE FEED (U+000A), CARRIAGE RETURN (U+000D),
 *           PARAGRAPH SEPARATOR (U+2029), LINE SEPARATOR (U+2028)
 *       <dd>This expression matches one code point in Unicode. It can match a pair of surrogates.
 *       <dd>When <a href="#S_OPTION">the "s" option</a> is specified,
 *           it matches any character including the above four characters.
 *
 *       <dt class="REGEX"><Kbd>\e \f \n \r \t</kbd>
 *       <dd>Matches ESCAPE (U+001B), FORM FEED (U+000C), LINE FEED (U+000A),
 *           CARRIAGE RETURN (U+000D), HORIZONTAL TABULATION (U+0009)
 *
 *       <dt class="REGEX"><kbd>\c</kbd><var>C</var>
 *       <dd>Matches a control character.
 *           The <var>C</var> must be one of '<kbd>@</kbd>', '<kbd>A</kbd>'-'<kbd>Z</kbd>',
 *           '<kbd>[</kbd>', '<kbd>\u005c</kbd>', '<kbd>]</kbd>', '<kbd>^</kbd>', '<kbd>_</kbd>'.
 *           It matches a control character of which the character code is less than
 *           the character code of the <var>C</var> by 0x0040.
 *       <dd class="REGEX">For example, a <kbd>\cJ</kbd> matches a LINE FEED (U+000A),
 *           and a <kbd>\c[</kbd> matches an ESCAPE (U+001B).
 *
 *       <dt class="REGEX">a non-meta character
 *       <dd>Matches the character.
 *
 *       <dt class="REGEX"><KBD>\</KBD> + a meta character
 *       <dd>Matches the meta character.
 *
 *       <dt class="REGEX"><kbd>\u005cx</kbd><var>HH</var> <kbd>\u005cx{</kbd><var>HHHH</var><kbd>}</kbd>
 *       <dd>Matches a character of which code point is <var>HH</var> (Hexadecimal) in Unicode.
 *           You can write just 2 digits for <kbd>\u005cx</kbd><var>HH</var>, and
 *           variable length digits for <kbd>\u005cx{</kbd><var>HHHH</var><kbd>}</kbd>.
 *
 *       <!--
 *       <dt class="REGEX"><kbd>\u005c u</kbd><var>HHHH</var>
 *       <dd>Matches a character of which code point is <var>HHHH</var> (Hexadecimal) in Unicode.
 *       -->
 *
 *       <dt class="REGEX"><kbd>\u005cv</kbd><var>HHHHHH</var>
 *       <dd>Matches a character of which code point is <var>HHHHHH</var> (Hexadecimal) in Unicode.
 *
 *       <dt class="REGEX"><kbd>\g</kbd>
 *       <dd>Matches a grapheme.
 *       <dd class="REGEX">It is equivalent to <kbd>(?[\p{ASSIGNED}]-[\p{M}\p{C}])?(?:\p{M}|[\x{094D}\x{09CD}\x{0A4D}\x{0ACD}\x{0B3D}\x{0BCD}\x{0C4D}\x{0CCD}\x{0D4D}\x{0E3A}\x{0F84}]\p{L}|[\x{1160}-\x{11A7}]|[\x{11A8}-\x{11FF}]|[\x{FF9E}\x{FF9F}])*</kbd>
 *
 *       <dt class="REGEX"><kbd>\X</kbd>
 *       <dd class="REGEX">Matches a combining character sequence.
 *       It is equivalent to <kbd>(?:\PM\pM*)</kbd>
 *     </dl>
 *   </li>
 *
 *   <li>Character class
 *     <dl>
+ *       <dt class="REGEX"><kbd>[</kbd><var>R<sub>1</sub></var><var>R<sub>2</sub></var><var>...</var><var>R<sub>n</sub></var><kbd>]</kbd> (without <a href="#COMMA_OPTION">"," option</a>)
+ *       <dt class="REGEX"><kbd>[</kbd><var>R<sub>1</sub></var><kbd>,</kbd><var>R<sub>2</sub></var><kbd>,</kbd><var>...</var><kbd>,</kbd><var>R<sub>n</sub></var><kbd>]</kbd> (with <a href="#COMMA_OPTION">"," option</a>)
 *       <dd>Positive character class.  It matches a character in ranges.
 *       <dd><var>R<sub>n</sub></var>:
 *       <ul>
 *         <li class="REGEX">A character (including <Kbd>\e \f \n \r \t</kbd> <kbd>\u005cx</kbd><var>HH</var> <kbd>\u005cx{</kbd><var>HHHH</var><kbd>}</kbd> <!--kbd>\u005c u</kbd><var>HHHH</var--> <kbd>\u005cv</kbd><var>HHHHHH</var>)
 *             <p>This range matches the character.
 *         <li class="REGEX"><var>C<sub>1</sub></var><kbd>-</kbd><var>C<sub>2</sub></var>
 *             <p>This range matches a character which has a code point that is >= <var>C<sub>1</sub></var>'s code point and &lt;= <var>C<sub>2</sub></var>'s code point.
+ *         <li class="REGEX">A POSIX character class: <Kbd>[:alpha:] [:alnum:] [:ascii:] [:cntrl:] [:digit:] [:graph:] [:lower:] [:print:] [:punct:] [:space:] [:upper:] [:xdigit:]</kbd>,
+ *             and negative POSIX character classes in Perl like <kbd>[:^alpha:]</kbd>
 *             <p>...
 *         <li class="REGEX"><kbd>\d \D \s \S \w \W \p{</kbd><var>name</var><kbd>} \P{</kbd><var>name</var><kbd>}</kbd>
 *             <p>These expressions specifies the same ranges as the following expressions.
 *       </ul>
 *       <p class="REGEX">Enumerated ranges are merged (union operation).
 *          <kbd>[a-ec-z]</kbd> is equivalent to <kbd>[a-z]</kbd>
 *
 *       <dt class="REGEX"><kbd>[^</kbd><var>R<sub>1</sub></var><var>R<sub>2</sub></var><var>...</var><var>R<sub>n</sub></var><kbd>]</kbd> (without a <a href="#COMMA_OPTION">"," option</a>)
 *       <dt class="REGEX"><kbd>[^</kbd><var>R<sub>1</sub></var><kbd>,</kbd><var>R<sub>2</sub></var><kbd>,</kbd><var>...</var><kbd>,</kbd><var>R<sub>n</sub></var><kbd>]</kbd> (with a <a href="#COMMA_OPTION">"," option</a>)
 *       <dd>Negative character class.  It matches a character not in ranges.
 *
 *       <dt class="REGEX"><kbd>(?[</kbd><var>ranges</var><kbd>]</kbd><var>op</var><kbd>[</kbd><var>ranges</var><kbd>]</kbd><var>op</var><kbd>[</kbd><var>ranges</var><kbd>]</kbd> ... <Kbd>)</kbd>
 *       (<var>op</var> is <kbd>-</kbd> or <kbd>+</kbd> or <kbd>&</kbd>.)
 *       <dd>Subtraction or union or intersection for character classes.
 *       <dd class="REGEX">For exmaple, <kbd>(?[A-Z]-[CF])</kbd> is equivalent to <kbd>[A-BD-EG-Z]</kbd>, and <kbd>(?[0x00-0x7f]-[K]&[\p{Lu}])</kbd> is equivalent to <kbd>[A-JL-Z]</kbd>.
 *       <dd>The result of this operations is a <u>positive character class</u>
 *           even if an expression includes any negative character classes.
 *           You have to take care on this in case-insensitive matching.
 *           For instance, <kbd>(?[^b])</kbd> is equivalent to <kbd>[\x00-ac-\x{10ffff}]</kbd>,
 *           which is equivalent to <kbd>[^b]</kbd> in case-sensitive matching.
 *           But, in case-insensitive matching, <kbd>(?[^b])</kbd> matches any character because
 *           it includes '<kbd>B</kbd>' and '<kbd>B</kbd>' matches '<kbd>b</kbd>'
 *           though <kbd>[^b]</kbd> is processed as <kbd>[^Bb]</kbd>.
 *
 *       <dt class="REGEX"><kbd>[</kbd><var>R<sub>1</sub>R<sub>2</sub>...</var><kbd>-[</kbd><var>R<sub>n</sub>R<sub>n+1</sub>...</var><kbd>]]</kbd> (with an <a href="#X_OPTION">"X" option</a>)</dt>
 *       <dd>Character class subtraction for the XML Schema.
 *           You can use this syntax when you specify an <a href="#X_OPTION">"X" option</a>.
 *
 *       <dt class="REGEX"><kbd>\d</kbd>
 *       <dd class="REGEX">Equivalent to <kbd>[0-9]</kbd>.
 *       <dd>When <a href="#U_OPTION">a "u" option</a> is set, it is equivalent to
 *           <span class="REGEX"><kbd>\p{Nd}</kbd></span>.
 *
 *       <dt class="REGEX"><kbd>\D</kbd>
 *       <dd class="REGEX">Equivalent to <kbd>[^0-9]</kbd>
 *       <dd>When <a href="#U_OPTION">a "u" option</a> is set, it is equivalent to
 *           <span class="REGEX"><kbd>\P{Nd}</kbd></span>.
 *
 *       <dt class="REGEX"><kbd>\s</kbd>
 *       <dd class="REGEX">Equivalent to <kbd>[ \f\n\r\t]</kbd>
 *       <dd>When <a href="#U_OPTION">a "u" option</a> is set, it is equivalent to
 *           <span class="REGEX"><kbd>[ \f\n\r\t\p{Z}]</kbd></span>.
 *
 *       <dt class="REGEX"><kbd>\S</kbd>
 *       <dd class="REGEX">Equivalent to <kbd>[^ \f\n\r\t]</kbd>
 *       <dd>When <a href="#U_OPTION">a "u" option</a> is set, it is equivalent to
 *           <span class="REGEX"><kbd>[^ \f\n\r\t\p{Z}]</kbd></span>.
 *
 *       <dt class="REGEX"><kbd>\w</kbd>
 *       <dd class="REGEX">Equivalent to <kbd>[a-zA-Z0-9_]</kbd>
 *       <dd>When <a href="#U_OPTION">a "u" option</a> is set, it is equivalent to
 *           <span class="REGEX"><kbd>[\p{Lu}\p{Ll}\p{Lo}\p{Nd}_]</kbd></span>.
 *
 *       <dt class="REGEX"><kbd>\W</kbd>
 *       <dd class="REGEX">Equivalent to <kbd>[^a-zA-Z0-9_]</kbd>
 *       <dd>When <a href="#U_OPTION">a "u" option</a> is set, it is equivalent to
 *           <span class="REGEX"><kbd>[^\p{Lu}\p{Ll}\p{Lo}\p{Nd}_]</kbd></span>.
 *
 *       <dt class="REGEX"><kbd>\p{</kbd><var>name</var><kbd>}</kbd>
 *       <dd>Matches one character in the specified General Category (the second field in <a href="ftp://ftp.unicode.org/Public/UNIDATA/UnicodeData.txt"><kbd>UnicodeData.txt</kbd></a>) or the specified <a href="ftp://ftp.unicode.org/Public/UNIDATA/Blocks.txt">Block</a>.
 *       The following names are available:
 *       <dl>
 *         <dt>Unicode General Categories:
 *         <dd><kbd>
 *       L, M, N, Z, C, P, S, Lu, Ll, Lt, Lm, Lo, Mn, Me, Mc, Nd, Nl, No, Zs, Zl, Zp,
 *       Cc, Cf, Cn, Co, Cs, Pd, Ps, Pe, Pc, Po, Sm, Sc, Sk, So,
 *         </kbd>
 *         <dd>(Currently the Cn category includes U+10000-U+10FFFF characters)
 *         <dt>Unicode Blocks:
 *         <dd><kbd>
 *       Basic Latin, Latin-1 Supplement, Latin Extended-A, Latin Extended-B,
 *       IPA Extensions, Spacing Modifier Letters, Combining Diacritical Marks, Greek,
 *       Cyrillic, Armenian, Hebrew, Arabic, Devanagari, Bengali, Gurmukhi, Gujarati,
 *       Oriya, Tamil, Telugu, Kannada, Malayalam, Thai, Lao, Tibetan, Georgian,
 *       Hangul Jamo, Latin Extended Additional, Greek Extended, General Punctuation,
 *       Superscripts and Subscripts, Currency Symbols, Combining Marks for Symbols,
 *       Letterlike Symbols, Number Forms, Arrows, Mathematical Operators,
 *       Miscellaneous Technical, Control Pictures, Optical Character Recognition,
 *       Enclosed Alphanumerics, Box Drawing, Block Elements, Geometric Shapes,
 *       Miscellaneous Symbols, Dingbats, CJK Symbols and Punctuation, Hiragana,
 *       Katakana, Bopomofo, Hangul Compatibility Jamo, Kanbun,
 *       Enclosed CJK Letters and Months, CJK Compatibility, CJK Unified Ideographs,
 *       Hangul Syllables, High Surrogates, High Private Use Surrogates, Low Surrogates,
 *       Private Use, CJK Compatibility Ideographs, Alphabetic Presentation Forms,
 *       Arabic Presentation Forms-A, Combining Half Marks, CJK Compatibility Forms,
 *       Small Form Variants, Arabic Presentation Forms-B, Specials,
 *       Halfwidth and Fullwidth Forms
 *         </kbd>
 *         <dt>Others:
 *         <dd><kbd>ALL</kbd> (Equivalent to <kbd>[\u005cu0000-\u005cv10FFFF]</kbd>)
 *         <dd><kbd>ASSGINED</kbd> (<kbd>\p{ASSIGNED}</kbd> is equivalent to <kbd>\P{Cn}</kbd>)
 *         <dd><kbd>UNASSGINED</kbd>
 *             (<kbd>\p{UNASSIGNED}</kbd> is equivalent to <kbd>\p{Cn}</kbd>)
 *       </dl>
 *
 *       <dt class="REGEX"><kbd>\P{</kbd><var>name</var><kbd>}</kbd>
 *       <dd>Matches one character not in the specified General Category or the specified Block.
 *     </dl>
 *   </li>
 *
 *   <li>Selection and Quantifier
 *     <dl>
 *       <dt class="REGEX"><VAR>X</VAR><kbd>|</kbd><VAR>Y</VAR>
 *       <dd>...
 *
 *       <dt class="REGEX"><VAR>X</VAR><kbd>*</KBD>
 *       <dd>Matches 0 or more <var>X</var>.
 *
 *       <dt class="REGEX"><VAR>X</VAR><kbd>+</KBD>
 *       <dd>Matches 1 or more <var>X</var>.
 *
 *       <dt class="REGEX"><VAR>X</VAR><kbd>?</KBD>
 *       <dd>Matches 0 or 1 <var>X</var>.
 *
 *       <dt class="REGEX"><var>X</var><kbd>{</kbd><var>number</var><kbd>}</kbd>
 *       <dd>Matches <var>number</var> times.
 *
 *       <dt class="REGEX"><var>X</var><kbd>{</kbd><var>min</var><kbd>,}</kbd>
 *       <dd>...
 *
 *       <dt class="REGEX"><var>X</var><kbd>{</kbd><var>min</var><kbd>,</kbd><var>max</var><kbd>}</kbd>
 *       <dd>...
 *
 *       <dt class="REGEX"><VAR>X</VAR><kbd>*?</kbd>
 *       <dt class="REGEX"><VAR>X</VAR><kbd>+?</kbd>
 *       <dt class="REGEX"><VAR>X</VAR><kbd>??</kbd>
 *       <dt class="REGEX"><var>X</var><kbd>{</kbd><var>min</var><kbd>,}?</kbd>
 *       <dt class="REGEX"><var>X</var><kbd>{</kbd><var>min</var><kbd>,</kbd><var>max</var><kbd>}?</kbd>
 *       <dd>Non-greedy matching.
 *     </dl>
 *   </li>
 *
 *   <li>Grouping, Capturing, and Back-reference
 *     <dl>
 *       <dt class="REGEX"><KBD>(?:</kbd><VAR>X</VAR><kbd>)</KBD>
 *       <dd>Grouping. "<KBD>foo+</KBD>" matches "<KBD>foo</KBD>" or "<KBD>foooo</KBD>".
 *       If you want it matches "<KBD>foofoo</KBD>" or "<KBD>foofoofoo</KBD>",
 *       you have to write "<KBD>(?:foo)+</KBD>".
 *
 *       <dt class="REGEX"><KBD>(</kbd><VAR>X</VAR><kbd>)</KBD>
 *       <dd>Grouping with capturing.
 * It make a group and applications can know
 * where in target text a group matched with methods of a <code>Match</code> instance
 * after <code><a href="#matches(java.lang.String, com.sun.org.apache.xerces.internal.utils.regex.Match)">matches(String,Match)</a></code>.
 * The 0th group means whole of this regular expression.
 * The <VAR>N</VAR>th gorup is the inside of the <VAR>N</VAR>th left parenthesis.
 *
 *   <p>For instance, a regular expression is
 *   "<FONT color=blue><KBD> *([^&lt;:]*) +&lt;([^&gt;]*)&gt; *</KBD></FONT>"
 *   and target text is
 *   "<FONT color=red><KBD>From: TAMURA Kent &lt;kent@trl.ibm.co.jp&gt;</KBD></FONT>":
 *   <ul>
 *     <li><code>Match.getCapturedText(0)</code>:
 *     "<FONT color=red><KBD> TAMURA Kent &lt;kent@trl.ibm.co.jp&gt;</KBD></FONT>"
 *     <li><code>Match.getCapturedText(1)</code>: "<FONT color=red><KBD>TAMURA Kent</KBD></FONT>"
 *     <li><code>Match.getCapturedText(2)</code>: "<FONT color=red><KBD>kent@trl.ibm.co.jp</KBD></FONT>"
 *   </ul>
 *
 *       <dt class="REGEX"><kbd>\1 \2 \3 \4 \5 \6 \7 \8 \9</kbd>
 *       <dd>
 *
 *       <dt class="REGEX"><kbd>(?></kbd><var>X</var><kbd>)</kbd>
 *       <dd>Independent expression group. ................
 *
 *       <dt class="REGEX"><kbd>(?</kbd><var>options</var><kbd>:</kbd><var>X</var><kbd>)</kbd>
 *       <dt class="REGEX"><kbd>(?</kbd><var>options</var><kbd>-</kbd><var>options2</var><kbd>:</kbd><var>X</var><kbd>)</kbd>
 *       <dd>............................
 *       <dd>The <var>options</var> or the <var>options2</var> consists of 'i' 'm' 's' 'w'.
 *           Note that it can not contain 'u'.
 *
 *       <dt class="REGEX"><kbd>(?</kbd><var>options</var><kbd>)</kbd>
 *       <dt class="REGEX"><kbd>(?</kbd><var>options</var><kbd>-</kbd><var>options2</var><kbd>)</kbd>
 *       <dd>......
 *       <dd>These expressions must be at the beginning of a group.
 *     </dl>
 *   </li>
 *
 *   <li>Anchor
 *     <dl>
 *       <dt class="REGEX"><kbd>\A</kbd>
 *       <dd>Matches the beginnig of the text.
 *
 *       <dt class="REGEX"><kbd>\Z</kbd>
 *       <dd>Matches the end of the text, or before an EOL character at the end of the text,
 *           or CARRIAGE RETURN + LINE FEED at the end of the text.
 *
 *       <dt class="REGEX"><kbd>\z</kbd>
 *       <dd>Matches the end of the text.
 *
 *       <dt class="REGEX"><kbd>^</kbd>
 *       <dd>Matches the beginning of the text.  It is equivalent to <span class="REGEX"><Kbd>\A</kbd></span>.
 *       <dd>When <a href="#M_OPTION">a "m" option</a> is set,
 *           it matches the beginning of the text, or after one of EOL characters (
 *           LINE FEED (U+000A), CARRIAGE RETURN (U+000D), LINE SEPARATOR (U+2028),
 *           PARAGRAPH SEPARATOR (U+2029).)
 *
 *       <dt class="REGEX"><kbd>$</kbd>
 *       <dd>Matches the end of the text, or before an EOL character at the end of the text,
 *           or CARRIAGE RETURN + LINE FEED at the end of the text.
 *       <dd>When <a href="#M_OPTION">a "m" option</a> is set,
 *           it matches the end of the text, or before an EOL character.
 *
 *       <dt class="REGEX"><kbd>\b</kbd>
 *       <dd>Matches word boundary.
 *           (See <a href="#W_OPTION">a "w" option</a>)
 *
 *       <dt class="REGEX"><kbd>\B</kbd>
 *       <dd>Matches non word boundary.
 *           (See <a href="#W_OPTION">a "w" option</a>)
 *
 *       <dt class="REGEX"><kbd>\&lt;</kbd>
 *       <dd>Matches the beginning of a word.
 *           (See <a href="#W_OPTION">a "w" option</a>)
 *
 *       <dt class="REGEX"><kbd>\&gt;</kbd>
 *       <dd>Matches the end of a word.
 *           (See <a href="#W_OPTION">a "w" option</a>)
 *     </dl>
 *   </li>
 *   <li>Lookahead and lookbehind
 *     <dl>
 *       <dt class="REGEX"><kbd>(?=</kbd><var>X</var><kbd>)</kbd>
 *       <dd>Lookahead.
 *
 *       <dt class="REGEX"><kbd>(?!</kbd><var>X</var><kbd>)</kbd>
 *       <dd>Negative lookahead.
 *
 *       <dt class="REGEX"><kbd>(?&lt;=</kbd><var>X</var><kbd>)</kbd>
 *       <dd>Lookbehind.
 *       <dd>(Note for text capturing......)
 *
 *       <dt class="REGEX"><kbd>(?&lt;!</kbd><var>X</var><kbd>)</kbd>
 *       <dd>Negative lookbehind.
 *     </dl>
 *   </li>
 *
 *   <li>Misc.
 *     <dl>
 *       <dt class="REGEX"><kbd>(?(</Kbd><var>condition</var><Kbd>)</kbd><var>yes-pattern</var><kbd>|</kbd><var>no-pattern</var><kbd>)</kbd>,
 *       <dt class="REGEX"><kbd>(?(</kbd><var>condition</var><kbd>)</kbd><var>yes-pattern</var><kbd>)</kbd>
 *       <dd>......
 *       <dt class="REGEX"><kbd>(?#</kbd><var>comment</var><kbd>)</kbd>
 *       <dd>Comment.  A comment string consists of characters except '<kbd>)</kbd>'.
 *           You can not write comments in character classes and before quantifiers.
 *     </dl>
 *   </li>
 * </ul>
 *
 *
 * <hr width="50%">
 * <h3>BNF for the regular expression</h3>
 * <pre>
 * regex ::= ('(?' options ')')? term ('|' term)*
 * term ::= factor+
 * factor ::= anchors | atom (('*' | '+' | '?' | minmax ) '?'? )?
 *            | '(?#' [^)]* ')'
 * minmax ::= '{' ([0-9]+ | [0-9]+ ',' | ',' [0-9]+ | [0-9]+ ',' [0-9]+) '}'
 * atom ::= char | '.' | char-class | '(' regex ')' | '(?:' regex ')' | '\' [0-9]
 *          | '\w' | '\W' | '\d' | '\D' | '\s' | '\S' | category-block | '\X'
 *          | '(?>' regex ')' | '(?' options ':' regex ')'
 *          | '(?' ('(' [0-9] ')' | '(' anchors ')' | looks) term ('|' term)? ')'
 * options ::= [imsw]* ('-' [imsw]+)?
 * anchors ::= '^' | '$' | '\A' | '\Z' | '\z' | '\b' | '\B' | '\&lt;' | '\>'
 * looks ::= '(?=' regex ')'  | '(?!' regex ')'
 *           | '(?&lt;=' regex ')' | '(?&lt;!' regex ')'
 * char ::= '\\' | '\' [efnrtv] | '\c' [@-_] | code-point | character-1
 * category-block ::= '\' [pP] category-symbol-1
 *                    | ('\p{' | '\P{') (category-symbol | block-name
 *                                       | other-properties) '}'
 * category-symbol-1 ::= 'L' | 'M' | 'N' | 'Z' | 'C' | 'P' | 'S'
 * category-symbol ::= category-symbol-1 | 'Lu' | 'Ll' | 'Lt' | 'Lm' | Lo'
 *                     | 'Mn' | 'Me' | 'Mc' | 'Nd' | 'Nl' | 'No'
 *                     | 'Zs' | 'Zl' | 'Zp' | 'Cc' | 'Cf' | 'Cn' | 'Co' | 'Cs'
 *                     | 'Pd' | 'Ps' | 'Pe' | 'Pc' | 'Po'
 *                     | 'Sm' | 'Sc' | 'Sk' | 'So'
 * block-name ::= (See above)
 * other-properties ::= 'ALL' | 'ASSIGNED' | 'UNASSIGNED'
 * character-1 ::= (any character except meta-characters)
 *
 * char-class ::= '[' ranges ']'
 *                | '(?[' ranges ']' ([-+&] '[' ranges ']')? ')'
 * ranges ::= '^'? (range <a href="#COMMA_OPTION">','?</a>)+
 * range ::= '\d' | '\w' | '\s' | '\D' | '\W' | '\S' | category-block
 *           | range-char | range-char '-' range-char
 * range-char ::= '\[' | '\]' | '\\' | '\' [,-efnrtv] | code-point | character-2
 * code-point ::= '\x' hex-char hex-char
 *                | '\x{' hex-char+ '}'
 * <!--               | '\u005c u' hex-char hex-char hex-char hex-char
 * -->               | '\v' hex-char hex-char hex-char hex-char hex-char hex-char
 * hex-char ::= [0-9a-fA-F]
 * character-2 ::= (any character except \[]-,)
 * </pre>
 *
 * <hr width="50%">
 * <h3>TODO</h3>
 * <ul>
 *   <li><a href="http://www.unicode.org/unicode/reports/tr18/">Unicode Regular Expression Guidelines</a>
 *     <ul>
 *       <li>2.4 Canonical Equivalents
 *       <li>Level 3
 *     </ul>
 *   <li>Parsing performance
 * </ul>
 *
 * <hr width="50%">
 *
 * @xerces.internal
 *
 * @author TAMURA Kent &lt;kent@trl.ibm.co.jp&gt;
 */
public class RegularExpression implements java.io.Serializable {

    private static final long serialVersionUID = 3905241217112815923L;

    static final boolean DEBUG = false;

    /**
     * Compiles a token tree into an operation flow.
     */
    private synchronized void compile(Token tok) {
        if (this.operations != null)
            return;
        this.numberOfClosures = 0;
        this.operations = this.compile(tok, null, false);
    }

    /**
     * Converts a token to an operation.
     */
    private Op compile(Token tok, Op next, boolean reverse) {
        Op ret;
        switch (tok.type) {
        case Token.DOT:
            ret = Op.createDot();
            ret.next = next;
            break;

        case Token.CHAR:
            ret = Op.createChar(tok.getChar());
            ret.next = next;
            break;

        case Token.ANCHOR:
            ret = Op.createAnchor(tok.getChar());
            ret.next = next;
            break;

        case Token.RANGE:
        case Token.NRANGE:
            ret = Op.createRange(tok);
            ret.next = next;
            break;

        case Token.CONCAT:
            ret = next;
            if (!reverse) {
                for (int i = tok.size()-1;  i >= 0;  i --) {
                    ret = compile(tok.getChild(i), ret, false);
                }
            } else {
                for (int i = 0;  i < tok.size();  i ++) {
                    ret = compile(tok.getChild(i), ret, true);
                }
            }
            break;

        case Token.UNION:
            Op.UnionOp uni = Op.createUnion(tok.size());
            for (int i = 0;  i < tok.size();  i ++) {
                uni.addElement(compile(tok.getChild(i), next, reverse));
            }
            ret = uni;                          // ret.next is null.
            break;

        case Token.CLOSURE:
        case Token.NONGREEDYCLOSURE:
            Token child = tok.getChild(0);
            int min = tok.getMin();
            int max = tok.getMax();
            if (min >= 0 && min == max) { // {n}
                ret = next;
                for (int i = 0; i < min;  i ++) {
                    ret = compile(child, ret, reverse);
                }
                break;
            }
            if (min > 0 && max > 0)
                max -= min;
            if (max > 0) {
                // X{2,6} -> XX(X(X(XX?)?)?)?
                ret = next;
                for (int i = 0;  i < max;  i ++) {
                    Op.ChildOp q = Op.createQuestion(tok.type == Token.NONGREEDYCLOSURE);
                    q.next = next;
                    q.setChild(compile(child, ret, reverse));
                    ret = q;
                }
            } else {
                Op.ChildOp op;
                if (tok.type == Token.NONGREEDYCLOSURE) {
                    op = Op.createNonGreedyClosure();
                } else {                        // Token.CLOSURE
                    if (child.getMinLength() == 0)
                        op = Op.createClosure(this.numberOfClosures++);
                    else
                        op = Op.createClosure(-1);
                }
                op.next = next;
                op.setChild(compile(child, op, reverse));
                ret = op;
            }
            if (min > 0) {
                for (int i = 0;  i < min;  i ++) {
                    ret = compile(child, ret, reverse);
                }
            }
            break;

        case Token.EMPTY:
            ret = next;
            break;

        case Token.STRING:
            ret = Op.createString(tok.getString());
            ret.next = next;
            break;

        case Token.BACKREFERENCE:
            ret = Op.createBackReference(tok.getReferenceNumber());
            ret.next = next;
            break;

        case Token.PAREN:
            if (tok.getParenNumber() == 0) {
                ret = compile(tok.getChild(0), next, reverse);
            } else if (reverse) {
                next = Op.createCapture(tok.getParenNumber(), next);
                next = compile(tok.getChild(0), next, reverse);
                ret = Op.createCapture(-tok.getParenNumber(), next);
            } else {
                next = Op.createCapture(-tok.getParenNumber(), next);
                next = compile(tok.getChild(0), next, reverse);
                ret = Op.createCapture(tok.getParenNumber(), next);
            }
            break;

        case Token.LOOKAHEAD:
            ret = Op.createLook(Op.LOOKAHEAD, next, compile(tok.getChild(0), null, false));
            break;
        case Token.NEGATIVELOOKAHEAD:
            ret = Op.createLook(Op.NEGATIVELOOKAHEAD, next, compile(tok.getChild(0), null, false));
            break;
        case Token.LOOKBEHIND:
            ret = Op.createLook(Op.LOOKBEHIND, next, compile(tok.getChild(0), null, true));
            break;
        case Token.NEGATIVELOOKBEHIND:
            ret = Op.createLook(Op.NEGATIVELOOKBEHIND, next, compile(tok.getChild(0), null, true));
            break;

        case Token.INDEPENDENT:
            ret = Op.createIndependent(next, compile(tok.getChild(0), null, reverse));
            break;

        case Token.MODIFIERGROUP:
            ret = Op.createModifier(next, compile(tok.getChild(0), null, reverse),
                                    ((Token.ModifierToken)tok).getOptions(),
                                    ((Token.ModifierToken)tok).getOptionsMask());
            break;

        case Token.CONDITION:
            Token.ConditionToken ctok = (Token.ConditionToken)tok;
            int ref = ctok.refNumber;
            Op condition = ctok.condition == null ? null : compile(ctok.condition, null, reverse);
            Op yes = compile(ctok.yes, next, reverse);
            Op no = ctok.no == null ? null : compile(ctok.no, next, reverse);
            ret = Op.createCondition(next, ref, condition, yes, no);
            break;

        default:
            throw new RuntimeException("Unknown token type: "+tok.type);
        } // switch (tok.type)
        return ret;
    }


//Public

    /**
     * Checks whether the <var>target</var> text <strong>contains</strong> this pattern or not.
     *
     * @return true if the target is matched to this regular expression.
     */
    public boolean matches(char[]  target) {
        return this.matches(target, 0,  target .length , (Match)null);
    }

    /**
     * Checks whether the <var>target</var> text <strong>contains</strong> this pattern
     * in specified range or not.
     *
     * @param start Start offset of the range.
     * @param end  End offset +1 of the range.
     * @return true if the target is matched to this regular expression.
     */
    public boolean matches(char[]  target, int start, int end) {
        return this.matches(target, start, end, (Match)null);
    }

    /**
     * Checks whether the <var>target</var> text <strong>contains</strong> this pattern or not.
     *
     * @param match A Match instance for storing matching result.
     * @return Offset of the start position in <VAR>target</VAR>; or -1 if not match.
     */
    public boolean matches(char[]  target, Match match) {
        return this.matches(target, 0,  target .length , match);
    }


    /**
     * Checks whether the <var>target</var> text <strong>contains</strong> this pattern
     * in specified range or not.
     *
     * @param start Start offset of the range.
     * @param end  End offset +1 of the range.
     * @param match A Match instance for storing matching result.
     * @return Offset of the start position in <VAR>target</VAR>; or -1 if not match.
     */
    public boolean matches(char[]  target, int start, int end, Match match) {

        synchronized (this) {
            if (this.operations == null)
                this.prepare();
            if (this.context == null)
                this.context = new Context();
        }
        Context con = null;
        synchronized (this.context) {
            con = this.context.inuse ? new Context() : this.context;
            con.reset(target, start, end, this.numberOfClosures);
        }
        if (match != null) {
            match.setNumberOfGroups(this.nofparen);
            match.setSource(target);
        } else if (this.hasBackReferences) {
            match = new Match();
            match.setNumberOfGroups(this.nofparen);
            // Need not to call setSource() because
            // a caller can not access this match instance.
        }
        con.match = match;

        if (RegularExpression.isSet(this.options, XMLSCHEMA_MODE)) {
            int matchEnd = this. matchCharArray (con, this.operations, con.start, 1, this.options);
            //System.err.println("DEBUG: matchEnd="+matchEnd);
            if (matchEnd == con.limit) {
                if (con.match != null) {
                    con.match.setBeginning(0, con.start);
                    con.match.setEnd(0, matchEnd);
                }
                con.inuse = false;
                return true;
            }
            return false;
        }

        /*
         * The pattern has only fixed string.
         * The engine uses Boyer-Moore.
         */
        if (this.fixedStringOnly) {
            //System.err.println("DEBUG: fixed-only: "+this.fixedString);
            int o = this.fixedStringTable.matches(target, con.start, con.limit);
            if (o >= 0) {
                if (con.match != null) {
                    con.match.setBeginning(0, o);
                    con.match.setEnd(0, o+this.fixedString.length());
                }
                con.inuse = false;
                return true;
            }
            con.inuse = false;
            return false;
        }

        /*
         * The pattern contains a fixed string.
         * The engine checks with Boyer-Moore whether the text contains the fixed string or not.
         * If not, it return with false.
         */
        if (this.fixedString != null) {
            int o = this.fixedStringTable.matches(target, con.start, con.limit);
            if (o < 0) {
                //System.err.println("Non-match in fixed-string search.");
                con.inuse = false;
                return false;
            }
        }

        int limit = con.limit-this.minlength;
        int matchStart;
        int matchEnd = -1;

        /*
         * Checks whether the expression starts with ".*".
         */
        if (this.operations != null
            && this.operations.type == Op.CLOSURE && this.operations.getChild().type == Op.DOT) {
            if (isSet(this.options, SINGLE_LINE)) {
                matchStart = con.start;
                matchEnd = this. matchCharArray (con, this.operations, con.start, 1, this.options);
            } else {
                boolean previousIsEOL = true;
                for (matchStart = con.start;  matchStart <= limit;  matchStart ++) {
                    int ch =  target [  matchStart ] ;
                    if (isEOLChar(ch)) {
                        previousIsEOL = true;
                    } else {
                        if (previousIsEOL) {
                            if (0 <= (matchEnd = this. matchCharArray (con, this.operations,
                                                                       matchStart, 1, this.options)))
                                break;
                        }
                        previousIsEOL = false;
                    }
                }
            }
        }

        /*
         * Optimization against the first character.
         */
        else if (this.firstChar != null) {
            //System.err.println("DEBUG: with firstchar-matching: "+this.firstChar);
            RangeToken range = this.firstChar;
            if (RegularExpression.isSet(this.options, IGNORE_CASE)) {
                range = this.firstChar.getCaseInsensitiveToken();
                for (matchStart = con.start;  matchStart <= limit;  matchStart ++) {
                    int ch =  target [  matchStart ] ;
                    if (REUtil.isHighSurrogate(ch) && matchStart+1 < con.limit) {
                        ch = REUtil.composeFromSurrogates(ch,  target [  matchStart+1 ] );
                        if (!range.match(ch))  continue;
                    } else {
                        if (!range.match(ch)) {
                            char ch1 = Character.toUpperCase((char)ch);
                            if (!range.match(ch1))
                                if (!range.match(Character.toLowerCase(ch1)))
                                    continue;
                        }
                    }
                    if (0 <= (matchEnd = this. matchCharArray (con, this.operations,
                                                               matchStart, 1, this.options)))
                        break;
                }
            } else {
                for (matchStart = con.start;  matchStart <= limit;  matchStart ++) {
                    int ch =  target [  matchStart ] ;
                    if (REUtil.isHighSurrogate(ch) && matchStart+1 < con.limit)
                        ch = REUtil.composeFromSurrogates(ch,  target [  matchStart+1 ] );
                    if (!range.match(ch))  continue;
                    if (0 <= (matchEnd = this. matchCharArray (con, this.operations,
                                                               matchStart, 1, this.options)))
                        break;
                }
            }
        }

        /*
         * Straightforward matching.
         */
        else {
            for (matchStart = con.start;  matchStart <= limit;  matchStart ++) {
                if (0 <= (matchEnd = this. matchCharArray (con, this.operations, matchStart, 1, this.options)))
                    break;
            }
        }

        if (matchEnd >= 0) {
            if (con.match != null) {
                con.match.setBeginning(0, matchStart);
                con.match.setEnd(0, matchEnd);
            }
            con.inuse = false;
            return true;
        } else {
            con.inuse = false;
            return false;
        }
    }

/**
 * @return -1 when not match; offset of the end of matched string when match.
 */
    private int matchCharArray (Context con, Op op, int offset, int dx, int opts) {

        char[] target = con.charTarget;


        while (true) {
            if (op == null)
                return isSet(opts, XMLSCHEMA_MODE) && offset != con.limit ? -1 : offset;
            if (offset > con.limit || offset < con.start)
                return -1;
            switch (op.type) {
            case Op.CHAR:
                if (isSet(opts, IGNORE_CASE)) {
                    int ch = op.getData();
                    if (dx > 0) {
                        if (offset >= con.limit || !matchIgnoreCase(ch,  target [  offset ] ))
                            return -1;
                        offset ++;
                    } else {
                        int o1 = offset-1;
                        if (o1 >= con.limit || o1 < 0 || !matchIgnoreCase(ch,  target [  o1 ] ))
                            return -1;
                        offset = o1;
                    }
                } else {
                    int ch = op.getData();
                    if (dx > 0) {
                        if (offset >= con.limit || ch !=  target [  offset ] )
                            return -1;
                        offset ++;
                    } else {
                        int o1 = offset-1;
                        if (o1 >= con.limit || o1 < 0 || ch !=  target [  o1 ] )
                            return -1;
                        offset = o1;
                    }
                }
                op = op.next;
                break;

            case Op.DOT:
                if (dx > 0) {
                    if (offset >= con.limit)
                        return -1;
                    int ch =  target [  offset ] ;
                    if (isSet(opts, SINGLE_LINE)) {
                        if (REUtil.isHighSurrogate(ch) && offset+1 < con.limit)
                            offset ++;
                    } else {
                        if (REUtil.isHighSurrogate(ch) && offset+1 < con.limit)
                            ch = REUtil.composeFromSurrogates(ch,  target [  ++offset ] );
                        if (isEOLChar(ch))
                            return -1;
                    }
                    offset ++;
                } else {
                    int o1 = offset-1;
                    if (o1 >= con.limit || o1 < 0)
                        return -1;
                    int ch =  target [  o1 ] ;
                    if (isSet(opts, SINGLE_LINE)) {
                        if (REUtil.isLowSurrogate(ch) && o1-1 >= 0)
                            o1 --;
                    } else {
                        if (REUtil.isLowSurrogate(ch) && o1-1 >= 0)
                            ch = REUtil.composeFromSurrogates( target [  --o1 ] , ch);
                        if (!isEOLChar(ch))
                            return -1;
                    }
                    offset = o1;
                }
                op = op.next;
                break;

            case Op.RANGE:
            case Op.NRANGE:
                if (dx > 0) {
                    if (offset >= con.limit)
                        return -1;
                    int ch =  target [  offset ] ;
                    if (REUtil.isHighSurrogate(ch) && offset+1 < con.limit)
                        ch = REUtil.composeFromSurrogates(ch,  target [  ++offset ] );
                    RangeToken tok = op.getToken();
                    if (isSet(opts, IGNORE_CASE)) {
                        tok = tok.getCaseInsensitiveToken();
                        if (!tok.match(ch)) {
                            if (ch >= 0x10000)  return -1;
                            char uch;
                            if (!tok.match(uch = Character.toUpperCase((char)ch))
                                && !tok.match(Character.toLowerCase(uch)))
                                return -1;
                        }
                    } else {
                        if (!tok.match(ch))  return -1;
                    }
                    offset ++;
                } else {
                    int o1 = offset-1;
                    if (o1 >= con.limit || o1 < 0)
                        return -1;
                    int ch =  target [  o1 ] ;
                    if (REUtil.isLowSurrogate(ch) && o1-1 >= 0)
                        ch = REUtil.composeFromSurrogates( target [  --o1 ] , ch);
                    RangeToken tok = op.getToken();
                    if (isSet(opts, IGNORE_CASE)) {
                        tok = tok.getCaseInsensitiveToken();
                        if (!tok.match(ch)) {
                            if (ch >= 0x10000)  return -1;
                            char uch;
                            if (!tok.match(uch = Character.toUpperCase((char)ch))
                                && !tok.match(Character.toLowerCase(uch)))
                                return -1;
                        }
                    } else {
                        if (!tok.match(ch))  return -1;
                    }
                    offset = o1;
                }
                op = op.next;
                break;

            case Op.ANCHOR:
                boolean go = false;
                switch (op.getData()) {
                case '^':
                    if (isSet(opts, MULTIPLE_LINES)) {
                        if (!(offset == con.start
                              || offset > con.start && isEOLChar( target [  offset-1 ] )))
                            return -1;
                    } else {
                        if (offset != con.start)
                            return -1;
                    }
                    break;

                case '@':                         // Internal use only.
                    // The @ always matches line beginnings.
                    if (!(offset == con.start
                          || offset > con.start && isEOLChar( target [  offset-1 ] )))
                        return -1;
                    break;

                case '$':
                    if (isSet(opts, MULTIPLE_LINES)) {
                        if (!(offset == con.limit
                              || offset < con.limit && isEOLChar( target [  offset ] )))
                            return -1;
                    } else {
                        if (!(offset == con.limit
                              || offset+1 == con.limit && isEOLChar( target [  offset ] )
                              || offset+2 == con.limit &&  target [  offset ]  == CARRIAGE_RETURN
                              &&  target [  offset+1 ]  == LINE_FEED))
                            return -1;
                    }
                    break;

                case 'A':
                    if (offset != con.start)  return -1;
                    break;

                case 'Z':
                    if (!(offset == con.limit
                          || offset+1 == con.limit && isEOLChar( target [  offset ] )
                          || offset+2 == con.limit &&  target [  offset ]  == CARRIAGE_RETURN
                          &&  target [  offset+1 ]  == LINE_FEED))
                        return -1;
                    break;

                case 'z':
                    if (offset != con.limit)  return -1;
                    break;

                case 'b':
                    if (con.length == 0)  return -1;
                    {
                        int after = getWordType(target, con.start, con.limit, offset, opts);
                        if (after == WT_IGNORE)  return -1;
                        int before = getPreviousWordType(target, con.start, con.limit, offset, opts);
                        if (after == before)  return -1;
                    }
                    break;

                case 'B':
                    if (con.length == 0)
                        go = true;
                    else {
                        int after = getWordType(target, con.start, con.limit, offset, opts);
                        go = after == WT_IGNORE
                             || after == getPreviousWordType(target, con.start, con.limit, offset, opts);
                    }
                    if (!go)  return -1;
                    break;

                case '<':
                    if (con.length == 0 || offset == con.limit)  return -1;
                    if (getWordType(target, con.start, con.limit, offset, opts) != WT_LETTER
                        || getPreviousWordType(target, con.start, con.limit, offset, opts) != WT_OTHER)
                        return -1;
                    break;

                case '>':
                    if (con.length == 0 || offset == con.start)  return -1;
                    if (getWordType(target, con.start, con.limit, offset, opts) != WT_OTHER
                        || getPreviousWordType(target, con.start, con.limit, offset, opts) != WT_LETTER)
                        return -1;
                    break;
                } // switch anchor type
                op = op.next;
                break;

            case Op.BACKREFERENCE:
                {
                    int refno = op.getData();
                    if (refno <= 0 || refno >= this.nofparen)
                        throw new RuntimeException("Internal Error: Reference number must be more than zero: "+refno);
                    if (con.match.getBeginning(refno) < 0
                        || con.match.getEnd(refno) < 0)
                        return -1;                // ********
                    int o2 = con.match.getBeginning(refno);
                    int literallen = con.match.getEnd(refno)-o2;
                    if (!isSet(opts, IGNORE_CASE)) {
                        if (dx > 0) {
                            if (!regionMatches(target, offset, con.limit, o2, literallen))
                                return -1;
                            offset += literallen;
                        } else {
                            if (!regionMatches(target, offset-literallen, con.limit, o2, literallen))
                                return -1;
                            offset -= literallen;
                        }
                    } else {
                        if (dx > 0) {
                            if (!regionMatchesIgnoreCase(target, offset, con.limit, o2, literallen))
                                return -1;
                            offset += literallen;
                        } else {
                            if (!regionMatchesIgnoreCase(target, offset-literallen, con.limit,
                                                         o2, literallen))
                                return -1;
                            offset -= literallen;
                        }
                    }
                }
                op = op.next;
                break;
            case Op.STRING:
                {
                    String literal = op.getString();
                    int literallen = literal.length();
                    if (!isSet(opts, IGNORE_CASE)) {
                        if (dx > 0) {
                            if (!regionMatches(target, offset, con.limit, literal, literallen))
                                return -1;
                            offset += literallen;
                        } else {
                            if (!regionMatches(target, offset-literallen, con.limit, literal, literallen))
                                return -1;
                            offset -= literallen;
                        }
                    } else {
                        if (dx > 0) {
                            if (!regionMatchesIgnoreCase(target, offset, con.limit, literal, literallen))
                                return -1;
                            offset += literallen;
                        } else {
                            if (!regionMatchesIgnoreCase(target, offset-literallen, con.limit,
                                                         literal, literallen))
                                return -1;
                            offset -= literallen;
                        }
                    }
                }
                op = op.next;
                break;

            case Op.CLOSURE:
                {
                    /*
                     * Saves current position to avoid
                     * zero-width repeats.
                     */
                    int id = op.getData();
                    if (id >= 0) {
                        int previousOffset = con.offsets[id];
                        if (previousOffset < 0 || previousOffset != offset) {
                            con.offsets[id] = offset;
                        } else {
                            con.offsets[id] = -1;
                            op = op.next;
                            break;
                        }
                    }

                    int ret = this. matchCharArray (con, op.getChild(), offset, dx, opts);
                    if (id >= 0)  con.offsets[id] = -1;
                    if (ret >= 0)  return ret;
                    op = op.next;
                }
                break;

            case Op.QUESTION:
                {
                    int ret = this. matchCharArray (con, op.getChild(), offset, dx, opts);
                    if (ret >= 0)  return ret;
                    op = op.next;
                }
                break;

            case Op.NONGREEDYCLOSURE:
            case Op.NONGREEDYQUESTION:
                {
                    int ret = this. matchCharArray (con, op.next, offset, dx, opts);
                    if (ret >= 0)  return ret;
                    op = op.getChild();
                }
                break;

            case Op.UNION:
                for (int i = 0;  i < op.size();  i ++) {
                    int ret = this. matchCharArray (con, op.elementAt(i), offset, dx, opts);
                    if (DEBUG) {
                        System.err.println("UNION: "+i+", ret="+ret);
                    }
                    if (ret >= 0)  return ret;
                }
                return -1;

            case Op.CAPTURE:
                int refno = op.getData();
                if (con.match != null && refno > 0) {
                    int save = con.match.getBeginning(refno);
                    con.match.setBeginning(refno, offset);
                    int ret = this. matchCharArray (con, op.next, offset, dx, opts);
                    if (ret < 0)  con.match.setBeginning(refno, save);
                    return ret;
                } else if (con.match != null && refno < 0) {
                    int index = -refno;
                    int save = con.match.getEnd(index);
                    con.match.setEnd(index, offset);
                    int ret = this. matchCharArray (con, op.next, offset, dx, opts);
                    if (ret < 0)  con.match.setEnd(index, save);
                    return ret;
                }
                op = op.next;
                break;

            case Op.LOOKAHEAD:
                if (0 > this. matchCharArray (con, op.getChild(), offset, 1, opts))  return -1;
                op = op.next;
                break;
            case Op.NEGATIVELOOKAHEAD:
                if (0 <= this. matchCharArray (con, op.getChild(), offset, 1, opts))  return -1;
                op = op.next;
                break;
            case Op.LOOKBEHIND:
                if (0 > this. matchCharArray (con, op.getChild(), offset, -1, opts))  return -1;
                op = op.next;
                break;
            case Op.NEGATIVELOOKBEHIND:
                if (0 <= this. matchCharArray (con, op.getChild(), offset, -1, opts))  return -1;
                op = op.next;
                break;

            case Op.INDEPENDENT:
                {
                    int ret = this. matchCharArray (con, op.getChild(), offset, dx, opts);
                    if (ret < 0)  return ret;
                    offset = ret;
                    op = op.next;
                }
                break;

            case Op.MODIFIER:
                {
                    int localopts = opts;
                    localopts |= op.getData();
                    localopts &= ~op.getData2();
                    //System.err.println("MODIFIER: "+Integer.toString(opts, 16)+" -> "+Integer.toString(localopts, 16));
                    int ret = this. matchCharArray (con, op.getChild(), offset, dx, localopts);
                    if (ret < 0)  return ret;
                    offset = ret;
                    op = op.next;
                }
                break;

            case Op.CONDITION:
                {
                    Op.ConditionOp cop = (Op.ConditionOp)op;
                    boolean matchp = false;
                    if (cop.refNumber > 0) {
                        if (cop.refNumber >= this.nofparen)
                            throw new RuntimeException("Internal Error: Reference number must be more than zero: "+cop.refNumber);
                        matchp = con.match.getBeginning(cop.refNumber) >= 0
                                 && con.match.getEnd(cop.refNumber) >= 0;
                    } else {
                        matchp = 0 <= this. matchCharArray (con, cop.condition, offset, dx, opts);
                    }

                    if (matchp) {
                        op = cop.yes;
                    } else if (cop.no != null) {
                        op = cop.no;
                    } else {
                        op = cop.next;
                    }
                }
                break;

            default:
                throw new RuntimeException("Unknown operation type: "+op.type);
            } // switch (op.type)
        } // while
    }

    private static final int getPreviousWordType(char[]  target, int begin, int end,
                                                 int offset, int opts) {
        int ret = getWordType(target, begin, end, --offset, opts);
        while (ret == WT_IGNORE)
            ret = getWordType(target, begin, end, --offset, opts);
        return ret;
    }

    private static final int getWordType(char[]  target, int begin, int end,
                                         int offset, int opts) {
        if (offset < begin || offset >= end)  return WT_OTHER;
        return getWordType0( target [  offset ] , opts);
    }



    private static final boolean regionMatches(char[]  target, int offset, int limit,
                                               String part, int partlen) {
        if (offset < 0)  return false;
        if (limit-offset < partlen)
            return false;
        int i = 0;
        while (partlen-- > 0) {
            if ( target [  offset++ ]  != part.charAt(i++))
                return false;
        }
        return true;
    }

    private static final boolean regionMatches(char[]  target, int offset, int limit,
                                               int offset2, int partlen) {
        if (offset < 0)  return false;
        if (limit-offset < partlen)
            return false;
        int i = offset2;
        while (partlen-- > 0) {
            if ( target [  offset++ ]  !=  target [  i++ ] )
                return false;
        }
        return true;
    }

/**
 * @see java.lang.String#regionMatches
 */
    private static final boolean regionMatchesIgnoreCase(char[]  target, int offset, int limit,
                                                         String part, int partlen) {
        if (offset < 0)  return false;
        if (limit-offset < partlen)
            return false;
        int i = 0;
        while (partlen-- > 0) {
            char ch1 =  target [  offset++ ] ;
            char ch2 = part.charAt(i++);
            if (ch1 == ch2)
                continue;
            char uch1 = Character.toUpperCase(ch1);
            char uch2 = Character.toUpperCase(ch2);
            if (uch1 == uch2)
                continue;
            if (Character.toLowerCase(uch1) != Character.toLowerCase(uch2))
                return false;
        }
        return true;
    }

    private static final boolean regionMatchesIgnoreCase(char[]  target, int offset, int limit,
                                                         int offset2, int partlen) {
        if (offset < 0)  return false;
        if (limit-offset < partlen)
            return false;
        int i = offset2;
        while (partlen-- > 0) {
            char ch1 =  target [  offset++ ] ;
            char ch2 =  target [  i++ ] ;
            if (ch1 == ch2)
                continue;
            char uch1 = Character.toUpperCase(ch1);
            char uch2 = Character.toUpperCase(ch2);
            if (uch1 == uch2)
                continue;
            if (Character.toLowerCase(uch1) != Character.toLowerCase(uch2))
                return false;
        }
        return true;
    }




    /**
     * Checks whether the <var>target</var> text <strong>contains</strong> this pattern or not.
     *
     * @return true if the target is matched to this regular expression.
     */
    public boolean matches(String  target) {
        return this.matches(target, 0,  target .length() , (Match)null);
    }

    /**
     * Checks whether the <var>target</var> text <strong>contains</strong> this pattern
     * in specified range or not.
     *
     * @param start Start offset of the range.
     * @param end  End offset +1 of the range.
     * @return true if the target is matched to this regular expression.
     */
    public boolean matches(String  target, int start, int end) {
        return this.matches(target, start, end, (Match)null);
    }

    /**
     * Checks whether the <var>target</var> text <strong>contains</strong> this pattern or not.
     *
     * @param match A Match instance for storing matching result.
     * @return Offset of the start position in <VAR>target</VAR>; or -1 if not match.
     */
    public boolean matches(String  target, Match match) {
        return this.matches(target, 0,  target .length() , match);
    }

    /**
     * Checks whether the <var>target</var> text <strong>contains</strong> this pattern
     * in specified range or not.
     *
     * @param start Start offset of the range.
     * @param end  End offset +1 of the range.
     * @param match A Match instance for storing matching result.
     * @return Offset of the start position in <VAR>target</VAR>; or -1 if not match.
     */
    public boolean matches(String  target, int start, int end, Match match) {

        synchronized (this) {
            if (this.operations == null)
                this.prepare();
            if (this.context == null)
                this.context = new Context();
        }
        Context con = null;
        synchronized (this.context) {
            con = this.context.inuse ? new Context() : this.context;
            con.reset(target, start, end, this.numberOfClosures);
        }
        if (match != null) {
            match.setNumberOfGroups(this.nofparen);
            match.setSource(target);
        } else if (this.hasBackReferences) {
            match = new Match();
            match.setNumberOfGroups(this.nofparen);
            // Need not to call setSource() because
            // a caller can not access this match instance.
        }
        con.match = match;

        if (RegularExpression.isSet(this.options, XMLSCHEMA_MODE)) {
            if (DEBUG) {
                System.err.println("target string="+target);
            }
            int matchEnd = this. matchString (con, this.operations, con.start, 1, this.options);
            if (DEBUG) {
                System.err.println("matchEnd="+matchEnd);
                System.err.println("con.limit="+con.limit);
            }
            if (matchEnd == con.limit) {
                if (con.match != null) {
                    con.match.setBeginning(0, con.start);
                    con.match.setEnd(0, matchEnd);
                }
                con.inuse = false;
                return true;
            }
            return false;
        }

        /*
         * The pattern has only fixed string.
         * The engine uses Boyer-Moore.
         */
        if (this.fixedStringOnly) {
            //System.err.println("DEBUG: fixed-only: "+this.fixedString);
            int o = this.fixedStringTable.matches(target, con.start, con.limit);
            if (o >= 0) {
                if (con.match != null) {
                    con.match.setBeginning(0, o);
                    con.match.setEnd(0, o+this.fixedString.length());
                }
                con.inuse = false;
                return true;
            }
            con.inuse = false;
            return false;
        }

        /*
         * The pattern contains a fixed string.
         * The engine checks with Boyer-Moore whether the text contains the fixed string or not.
         * If not, it return with false.
         */
        if (this.fixedString != null) {
            int o = this.fixedStringTable.matches(target, con.start, con.limit);
            if (o < 0) {
                //System.err.println("Non-match in fixed-string search.");
                con.inuse = false;
                return false;
            }
        }

        int limit = con.limit-this.minlength;
        int matchStart;
        int matchEnd = -1;

        /*
         * Checks whether the expression starts with ".*".
         */
        if (this.operations != null
            && this.operations.type == Op.CLOSURE && this.operations.getChild().type == Op.DOT) {
            if (isSet(this.options, SINGLE_LINE)) {
                matchStart = con.start;
                matchEnd = this. matchString (con, this.operations, con.start, 1, this.options);
            } else {
                boolean previousIsEOL = true;
                for (matchStart = con.start;  matchStart <= limit;  matchStart ++) {
                    int ch =  target .charAt(  matchStart ) ;
                    if (isEOLChar(ch)) {
                        previousIsEOL = true;
                    } else {
                        if (previousIsEOL) {
                            if (0 <= (matchEnd = this. matchString (con, this.operations,
                                                                    matchStart, 1, this.options)))
                                break;
                        }
                        previousIsEOL = false;
                    }
                }
            }
        }

        /*
         * Optimization against the first character.
         */
        else if (this.firstChar != null) {
            //System.err.println("DEBUG: with firstchar-matching: "+this.firstChar);
            RangeToken range = this.firstChar;
            if (RegularExpression.isSet(this.options, IGNORE_CASE)) {
                range = this.firstChar.getCaseInsensitiveToken();
                for (matchStart = con.start;  matchStart <= limit;  matchStart ++) {
                    int ch =  target .charAt(  matchStart ) ;
                    if (REUtil.isHighSurrogate(ch) && matchStart+1 < con.limit) {
                        ch = REUtil.composeFromSurrogates(ch,  target .charAt(  matchStart+1 ) );
                        if (!range.match(ch))  continue;
                    } else {
                        if (!range.match(ch)) {
                            char ch1 = Character.toUpperCase((char)ch);
                            if (!range.match(ch1))
                                if (!range.match(Character.toLowerCase(ch1)))
                                    continue;
                        }
                    }
                    if (0 <= (matchEnd = this. matchString (con, this.operations,
                                                            matchStart, 1, this.options)))
                        break;
                }
            } else {
                for (matchStart = con.start;  matchStart <= limit;  matchStart ++) {
                    int ch =  target .charAt(  matchStart ) ;
                    if (REUtil.isHighSurrogate(ch) && matchStart+1 < con.limit)
                        ch = REUtil.composeFromSurrogates(ch,  target .charAt(  matchStart+1 ) );
                    if (!range.match(ch))  continue;
                    if (0 <= (matchEnd = this. matchString (con, this.operations,
                                                            matchStart, 1, this.options)))
                        break;
                }
            }
        }

        /*
         * Straightforward matching.
         */
        else {
            for (matchStart = con.start;  matchStart <= limit;  matchStart ++) {
                if (0 <= (matchEnd = this. matchString (con, this.operations, matchStart, 1, this.options)))
                    break;
            }
        }

        if (matchEnd >= 0) {
            if (con.match != null) {
                con.match.setBeginning(0, matchStart);
                con.match.setEnd(0, matchEnd);
            }
            con.inuse = false;
            return true;
        } else {
            con.inuse = false;
            return false;
        }
    }

    /**
     * @return -1 when not match; offset of the end of matched string when match.
     */
    private int matchString (Context con, Op op, int offset, int dx, int opts) {




        String target = con.strTarget;




        while (true) {
            if (op == null)
                return isSet(opts, XMLSCHEMA_MODE) && offset != con.limit ? -1 : offset;
            if (offset > con.limit || offset < con.start)
                return -1;
            switch (op.type) {
            case Op.CHAR:
                if (isSet(opts, IGNORE_CASE)) {
                    int ch = op.getData();
                    if (dx > 0) {
                        if (offset >= con.limit || !matchIgnoreCase(ch,  target .charAt(  offset ) ))
                            return -1;
                        offset ++;
                    } else {
                        int o1 = offset-1;
                        if (o1 >= con.limit || o1 < 0 || !matchIgnoreCase(ch,  target .charAt(  o1 ) ))
                            return -1;
                        offset = o1;
                    }
                } else {
                    int ch = op.getData();
                    if (dx > 0) {
                        if (offset >= con.limit || ch !=  target .charAt(  offset ) )
                            return -1;
                        offset ++;
                    } else {
                        int o1 = offset-1;
                        if (o1 >= con.limit || o1 < 0 || ch !=  target .charAt(  o1 ) )
                            return -1;
                        offset = o1;
                    }
                }
                op = op.next;
                break;

            case Op.DOT:
                if (dx > 0) {
                    if (offset >= con.limit)
                        return -1;
                    int ch =  target .charAt(  offset ) ;
                    if (isSet(opts, SINGLE_LINE)) {
                        if (REUtil.isHighSurrogate(ch) && offset+1 < con.limit)
                            offset ++;
                    } else {
                        if (REUtil.isHighSurrogate(ch) && offset+1 < con.limit)
                            ch = REUtil.composeFromSurrogates(ch,  target .charAt(  ++offset ) );
                        if (isEOLChar(ch))
                            return -1;
                    }
                    offset ++;
                } else {
                    int o1 = offset-1;
                    if (o1 >= con.limit || o1 < 0)
                        return -1;
                    int ch =  target .charAt(  o1 ) ;
                    if (isSet(opts, SINGLE_LINE)) {
                        if (REUtil.isLowSurrogate(ch) && o1-1 >= 0)
                            o1 --;
                    } else {
                        if (REUtil.isLowSurrogate(ch) && o1-1 >= 0)
                            ch = REUtil.composeFromSurrogates( target .charAt(  --o1 ) , ch);
                        if (!isEOLChar(ch))
                            return -1;
                    }
                    offset = o1;
                }
                op = op.next;
                break;

            case Op.RANGE:
            case Op.NRANGE:
                if (dx > 0) {
                    if (offset >= con.limit)
                        return -1;
                    int ch =  target .charAt(  offset ) ;
                    if (REUtil.isHighSurrogate(ch) && offset+1 < con.limit)
                        ch = REUtil.composeFromSurrogates(ch,  target .charAt(  ++offset ) );
                    RangeToken tok = op.getToken();
                    if (isSet(opts, IGNORE_CASE)) {
                        tok = tok.getCaseInsensitiveToken();
                        if (!tok.match(ch)) {
                            if (ch >= 0x10000)  return -1;
                            char uch;
                            if (!tok.match(uch = Character.toUpperCase((char)ch))
                                && !tok.match(Character.toLowerCase(uch)))
                                return -1;
                        }
                    } else {
                        if (!tok.match(ch))  return -1;
                    }
                    offset ++;
                } else {
                    int o1 = offset-1;
                    if (o1 >= con.limit || o1 < 0)
                        return -1;
                    int ch =  target .charAt(  o1 ) ;
                    if (REUtil.isLowSurrogate(ch) && o1-1 >= 0)
                        ch = REUtil.composeFromSurrogates( target .charAt(  --o1 ) , ch);
                    RangeToken tok = op.getToken();
                    if (isSet(opts, IGNORE_CASE)) {
                        tok = tok.getCaseInsensitiveToken();
                        if (!tok.match(ch)) {
                            if (ch >= 0x10000)  return -1;
                            char uch;
                            if (!tok.match(uch = Character.toUpperCase((char)ch))
                                && !tok.match(Character.toLowerCase(uch)))
                                return -1;
                        }
                    } else {
                        if (!tok.match(ch))  return -1;
                    }
                    offset = o1;
                }
                op = op.next;
                break;

            case Op.ANCHOR:
                boolean go = false;
                switch (op.getData()) {
                case '^':
                    if (isSet(opts, MULTIPLE_LINES)) {
                        if (!(offset == con.start
                              || offset > con.start && isEOLChar( target .charAt(  offset-1 ) )))
                            return -1;
                    } else {
                        if (offset != con.start)
                            return -1;
                    }
                    break;

                case '@':                         // Internal use only.
                    // The @ always matches line beginnings.
                    if (!(offset == con.start
                          || offset > con.start && isEOLChar( target .charAt(  offset-1 ) )))
                        return -1;
                    break;

                case '$':
                    if (isSet(opts, MULTIPLE_LINES)) {
                        if (!(offset == con.limit
                              || offset < con.limit && isEOLChar( target .charAt(  offset ) )))
                            return -1;
                    } else {
                        if (!(offset == con.limit
                              || offset+1 == con.limit && isEOLChar( target .charAt(  offset ) )
                              || offset+2 == con.limit &&  target .charAt(  offset )  == CARRIAGE_RETURN
                              &&  target .charAt(  offset+1 )  == LINE_FEED))
                            return -1;
                    }
                    break;

                case 'A':
                    if (offset != con.start)  return -1;
                    break;

                case 'Z':
                    if (!(offset == con.limit
                          || offset+1 == con.limit && isEOLChar( target .charAt(  offset ) )
                          || offset+2 == con.limit &&  target .charAt(  offset )  == CARRIAGE_RETURN
                          &&  target .charAt(  offset+1 )  == LINE_FEED))
                        return -1;
                    break;

                case 'z':
                    if (offset != con.limit)  return -1;
                    break;

                case 'b':
                    if (con.length == 0)  return -1;
                    {
                        int after = getWordType(target, con.start, con.limit, offset, opts);
                        if (after == WT_IGNORE)  return -1;
                        int before = getPreviousWordType(target, con.start, con.limit, offset, opts);
                        if (after == before)  return -1;
                    }
                    break;

                case 'B':
                    if (con.length == 0)
                        go = true;
                    else {
                        int after = getWordType(target, con.start, con.limit, offset, opts);
                        go = after == WT_IGNORE
                             || after == getPreviousWordType(target, con.start, con.limit, offset, opts);
                    }
                    if (!go)  return -1;
                    break;

                case '<':
                    if (con.length == 0 || offset == con.limit)  return -1;
                    if (getWordType(target, con.start, con.limit, offset, opts) != WT_LETTER
                        || getPreviousWordType(target, con.start, con.limit, offset, opts) != WT_OTHER)
                        return -1;
                    break;

                case '>':
                    if (con.length == 0 || offset == con.start)  return -1;
                    if (getWordType(target, con.start, con.limit, offset, opts) != WT_OTHER
                        || getPreviousWordType(target, con.start, con.limit, offset, opts) != WT_LETTER)
                        return -1;
                    break;
                } // switch anchor type
                op = op.next;
                break;

            case Op.BACKREFERENCE:
                {
                    int refno = op.getData();
                    if (refno <= 0 || refno >= this.nofparen)
                        throw new RuntimeException("Internal Error: Reference number must be more than zero: "+refno);
                    if (con.match.getBeginning(refno) < 0
                        || con.match.getEnd(refno) < 0)
                        return -1;                // ********
                    int o2 = con.match.getBeginning(refno);
                    int literallen = con.match.getEnd(refno)-o2;
                    if (!isSet(opts, IGNORE_CASE)) {
                        if (dx > 0) {
                            if (!regionMatches(target, offset, con.limit, o2, literallen))
                                return -1;
                            offset += literallen;
                        } else {
                            if (!regionMatches(target, offset-literallen, con.limit, o2, literallen))
                                return -1;
                            offset -= literallen;
                        }
                    } else {
                        if (dx > 0) {
                            if (!regionMatchesIgnoreCase(target, offset, con.limit, o2, literallen))
                                return -1;
                            offset += literallen;
                        } else {
                            if (!regionMatchesIgnoreCase(target, offset-literallen, con.limit,
                                                         o2, literallen))
                                return -1;
                            offset -= literallen;
                        }
                    }
                }
                op = op.next;
                break;
            case Op.STRING:
                {
                    String literal = op.getString();
                    int literallen = literal.length();
                    if (!isSet(opts, IGNORE_CASE)) {
                        if (dx > 0) {
                            if (!regionMatches(target, offset, con.limit, literal, literallen))
                                return -1;
                            offset += literallen;
                        } else {
                            if (!regionMatches(target, offset-literallen, con.limit, literal, literallen))
                                return -1;
                            offset -= literallen;
                        }
                    } else {
                        if (dx > 0) {
                            if (!regionMatchesIgnoreCase(target, offset, con.limit, literal, literallen))
                                return -1;
                            offset += literallen;
                        } else {
                            if (!regionMatchesIgnoreCase(target, offset-literallen, con.limit,
                                                         literal, literallen))
                                return -1;
                            offset -= literallen;
                        }
                    }
                }
                op = op.next;
                break;

            case Op.CLOSURE:
                {
                    /*
                     * Saves current position to avoid
                     * zero-width repeats.
                     */
                    int id = op.getData();
                    if (id >= 0) {
                        int previousOffset = con.offsets[id];
                        if (previousOffset < 0 || previousOffset != offset) {
                            con.offsets[id] = offset;
                        } else {
                            con.offsets[id] = -1;
                            op = op.next;
                            break;
                        }
                    }
                    int ret = this. matchString (con, op.getChild(), offset, dx, opts);
                    if (id >= 0)  con.offsets[id] = -1;
                    if (ret >= 0)  return ret;
                    op = op.next;
                }
                break;

            case Op.QUESTION:
                {
                    int ret = this. matchString (con, op.getChild(), offset, dx, opts);
                    if (ret >= 0)  return ret;
                    op = op.next;
                }
                break;

            case Op.NONGREEDYCLOSURE:
            case Op.NONGREEDYQUESTION:
                {
                    int ret = this. matchString (con, op.next, offset, dx, opts);
                    if (ret >= 0)  return ret;
                    op = op.getChild();
                }
                break;

            case Op.UNION:
                for (int i = 0;  i < op.size();  i ++) {
                    int ret = this. matchString (con, op.elementAt(i), offset, dx, opts);
                    if (DEBUG) {
                        System.err.println("UNION: "+i+", ret="+ret);
                    }
                    if (ret >= 0)  return ret;
                }
                return -1;

            case Op.CAPTURE:
                int refno = op.getData();
                if (con.match != null && refno > 0) {
                    int save = con.match.getBeginning(refno);
                    con.match.setBeginning(refno, offset);
                    int ret = this. matchString (con, op.next, offset, dx, opts);
                    if (ret < 0)  con.match.setBeginning(refno, save);
                    return ret;
                } else if (con.match != null && refno < 0) {
                    int index = -refno;
                    int save = con.match.getEnd(index);
                    con.match.setEnd(index, offset);
                    int ret = this. matchString (con, op.next, offset, dx, opts);
                    if (ret < 0)  con.match.setEnd(index, save);
                    return ret;
                }
                op = op.next;
                break;

            case Op.LOOKAHEAD:
                if (0 > this. matchString (con, op.getChild(), offset, 1, opts))  return -1;
                op = op.next;
                break;
            case Op.NEGATIVELOOKAHEAD:
                if (0 <= this. matchString (con, op.getChild(), offset, 1, opts))  return -1;
                op = op.next;
                break;
            case Op.LOOKBEHIND:
                if (0 > this. matchString (con, op.getChild(), offset, -1, opts))  return -1;
                op = op.next;
                break;
            case Op.NEGATIVELOOKBEHIND:
                if (0 <= this. matchString (con, op.getChild(), offset, -1, opts))  return -1;
                op = op.next;
                break;

            case Op.INDEPENDENT:
                {
                    int ret = this. matchString (con, op.getChild(), offset, dx, opts);
                    if (ret < 0)  return ret;
                    offset = ret;
                    op = op.next;
                }
                break;

            case Op.MODIFIER:
                {
                    int localopts = opts;
                    localopts |= op.getData();
                    localopts &= ~op.getData2();
                    //System.err.println("MODIFIER: "+Integer.toString(opts, 16)+" -> "+Integer.toString(localopts, 16));
                    int ret = this. matchString (con, op.getChild(), offset, dx, localopts);
                    if (ret < 0)  return ret;
                    offset = ret;
                    op = op.next;
                }
                break;

            case Op.CONDITION:
                {
                    Op.ConditionOp cop = (Op.ConditionOp)op;
                    boolean matchp = false;
                    if (cop.refNumber > 0) {
                        if (cop.refNumber >= this.nofparen)
                            throw new RuntimeException("Internal Error: Reference number must be more than zero: "+cop.refNumber);
                        matchp = con.match.getBeginning(cop.refNumber) >= 0
                                 && con.match.getEnd(cop.refNumber) >= 0;
                    } else {
                        matchp = 0 <= this. matchString (con, cop.condition, offset, dx, opts);
                    }

                    if (matchp) {
                        op = cop.yes;
                    } else if (cop.no != null) {
                        op = cop.no;
                    } else {
                        op = cop.next;
                    }
                }
                break;

            default:
                throw new RuntimeException("Unknown operation type: "+op.type);
            } // switch (op.type)
        } // while
    }

    private static final int getPreviousWordType(String  target, int begin, int end,
                                                 int offset, int opts) {
        int ret = getWordType(target, begin, end, --offset, opts);
        while (ret == WT_IGNORE)
            ret = getWordType(target, begin, end, --offset, opts);
        return ret;
    }

    private static final int getWordType(String  target, int begin, int end,
                                         int offset, int opts) {
        if (offset < begin || offset >= end)  return WT_OTHER;
        return getWordType0( target .charAt(  offset ) , opts);
    }


    private static final boolean regionMatches(String text, int offset, int limit,
                                               String part, int partlen) {
        if (limit-offset < partlen)  return false;
        return text.regionMatches(offset, part, 0, partlen);
    }

    private static final boolean regionMatches(String text, int offset, int limit,
                                               int offset2, int partlen) {
        if (limit-offset < partlen)  return false;
        return text.regionMatches(offset, text, offset2, partlen);
    }

    private static final boolean regionMatchesIgnoreCase(String text, int offset, int limit,
                                                         String part, int partlen) {
        return text.regionMatches(true, offset, part, 0, partlen);
    }

    private static final boolean regionMatchesIgnoreCase(String text, int offset, int limit,
                                                         int offset2, int partlen) {
        if (limit-offset < partlen)  return false;
        return text.regionMatches(true, offset, text, offset2, partlen);
    }







    /**
     * Checks whether the <var>target</var> text <strong>contains</strong> this pattern or not.
     *
     * @return true if the target is matched to this regular expression.
     */
    public boolean matches(CharacterIterator target) {
        return this.matches(target, (Match)null);
    }


    /**
     * Checks whether the <var>target</var> text <strong>contains</strong> this pattern or not.
     *
     * @param match A Match instance for storing matching result.
     * @return Offset of the start position in <VAR>target</VAR>; or -1 if not match.
     */
    public boolean matches(CharacterIterator  target, Match match) {
        int start = target.getBeginIndex();
        int end = target.getEndIndex();



        synchronized (this) {
            if (this.operations == null)
                this.prepare();
            if (this.context == null)
                this.context = new Context();
        }
        Context con = null;
        synchronized (this.context) {
            con = this.context.inuse ? new Context() : this.context;
            con.reset(target, start, end, this.numberOfClosures);
        }
        if (match != null) {
            match.setNumberOfGroups(this.nofparen);
            match.setSource(target);
        } else if (this.hasBackReferences) {
            match = new Match();
            match.setNumberOfGroups(this.nofparen);
            // Need not to call setSource() because
            // a caller can not access this match instance.
        }
        con.match = match;

        if (RegularExpression.isSet(this.options, XMLSCHEMA_MODE)) {
            int matchEnd = this. matchCharacterIterator (con, this.operations, con.start, 1, this.options);
            //System.err.println("DEBUG: matchEnd="+matchEnd);
            if (matchEnd == con.limit) {
                if (con.match != null) {
                    con.match.setBeginning(0, con.start);
                    con.match.setEnd(0, matchEnd);
                }
                con.inuse = false;
                return true;
            }
            return false;
        }

        /*
         * The pattern has only fixed string.
         * The engine uses Boyer-Moore.
         */
        if (this.fixedStringOnly) {
            //System.err.println("DEBUG: fixed-only: "+this.fixedString);
            int o = this.fixedStringTable.matches(target, con.start, con.limit);
            if (o >= 0) {
                if (con.match != null) {
                    con.match.setBeginning(0, o);
                    con.match.setEnd(0, o+this.fixedString.length());
                }
                con.inuse = false;
                return true;
            }
            con.inuse = false;
            return false;
        }

        /*
         * The pattern contains a fixed string.
         * The engine checks with Boyer-Moore whether the text contains the fixed string or not.
         * If not, it return with false.
         */
        if (this.fixedString != null) {
            int o = this.fixedStringTable.matches(target, con.start, con.limit);
            if (o < 0) {
                //System.err.println("Non-match in fixed-string search.");
                con.inuse = false;
                return false;
            }
        }

        int limit = con.limit-this.minlength;
        int matchStart;
        int matchEnd = -1;

        /*
         * Checks whether the expression starts with ".*".
         */
        if (this.operations != null
            && this.operations.type == Op.CLOSURE && this.operations.getChild().type == Op.DOT) {
            if (isSet(this.options, SINGLE_LINE)) {
                matchStart = con.start;
                matchEnd = this. matchCharacterIterator (con, this.operations, con.start, 1, this.options);
            } else {
                boolean previousIsEOL = true;
                for (matchStart = con.start;  matchStart <= limit;  matchStart ++) {
                    int ch =  target .setIndex(  matchStart ) ;
                    if (isEOLChar(ch)) {
                        previousIsEOL = true;
                    } else {
                        if (previousIsEOL) {
                            if (0 <= (matchEnd = this. matchCharacterIterator (con, this.operations,
                                                                               matchStart, 1, this.options)))
                                break;
                        }
                        previousIsEOL = false;
                    }
                }
            }
        }

        /*
         * Optimization against the first character.
         */
        else if (this.firstChar != null) {
            //System.err.println("DEBUG: with firstchar-matching: "+this.firstChar);
            RangeToken range = this.firstChar;
            if (RegularExpression.isSet(this.options, IGNORE_CASE)) {
                range = this.firstChar.getCaseInsensitiveToken();
                for (matchStart = con.start;  matchStart <= limit;  matchStart ++) {
                    int ch =  target .setIndex(  matchStart ) ;
                    if (REUtil.isHighSurrogate(ch) && matchStart+1 < con.limit) {
                        ch = REUtil.composeFromSurrogates(ch,  target .setIndex(  matchStart+1 ) );
                        if (!range.match(ch))  continue;
                    } else {
                        if (!range.match(ch)) {
                            char ch1 = Character.toUpperCase((char)ch);
                            if (!range.match(ch1))
                                if (!range.match(Character.toLowerCase(ch1)))
                                    continue;
                        }
                    }
                    if (0 <= (matchEnd = this. matchCharacterIterator (con, this.operations,
                                                                       matchStart, 1, this.options)))
                        break;
                }
            } else {
                for (matchStart = con.start;  matchStart <= limit;  matchStart ++) {
                    int ch =  target .setIndex(  matchStart ) ;
                    if (REUtil.isHighSurrogate(ch) && matchStart+1 < con.limit)
                        ch = REUtil.composeFromSurrogates(ch,  target .setIndex(  matchStart+1 ) );
                    if (!range.match(ch))  continue;
                    if (0 <= (matchEnd = this. matchCharacterIterator (con, this.operations,
                                                                       matchStart, 1, this.options)))
                        break;
                }
            }
        }

        /*
         * Straightforward matching.
         */
        else {
            for (matchStart = con.start;  matchStart <= limit;  matchStart ++) {
                if (0 <= (matchEnd = this. matchCharacterIterator (con, this.operations, matchStart, 1, this.options)))
                    break;
            }
        }

        if (matchEnd >= 0) {
            if (con.match != null) {
                con.match.setBeginning(0, matchStart);
                con.match.setEnd(0, matchEnd);
            }
            con.inuse = false;
            return true;
        } else {
            con.inuse = false;
            return false;
        }
    }

    /**
     * @return -1 when not match; offset of the end of matched string when match.
     */
    private int matchCharacterIterator (Context con, Op op, int offset, int dx, int opts) {


        CharacterIterator target = con.ciTarget;






        while (true) {
            if (op == null)
                return isSet(opts, XMLSCHEMA_MODE) && offset != con.limit ? -1 : offset;
            if (offset > con.limit || offset < con.start)
                return -1;
            switch (op.type) {
            case Op.CHAR:
                if (isSet(opts, IGNORE_CASE)) {
                    int ch = op.getData();
                    if (dx > 0) {
                        if (offset >= con.limit || !matchIgnoreCase(ch,  target .setIndex(  offset ) ))
                            return -1;
                        offset ++;
                    } else {
                        int o1 = offset-1;
                        if (o1 >= con.limit || o1 < 0 || !matchIgnoreCase(ch,  target .setIndex(  o1 ) ))
                            return -1;
                        offset = o1;
                    }
                } else {
                    int ch = op.getData();
                    if (dx > 0) {
                        if (offset >= con.limit || ch !=  target .setIndex(  offset ) )
                            return -1;
                        offset ++;
                    } else {
                        int o1 = offset-1;
                        if (o1 >= con.limit || o1 < 0 || ch !=  target .setIndex(  o1 ) )
                            return -1;
                        offset = o1;
                    }
                }
                op = op.next;
                break;

            case Op.DOT:
                if (dx > 0) {
                    if (offset >= con.limit)
                        return -1;
                    int ch =  target .setIndex(  offset ) ;
                    if (isSet(opts, SINGLE_LINE)) {
                        if (REUtil.isHighSurrogate(ch) && offset+1 < con.limit)
                            offset ++;
                    } else {
                        if (REUtil.isHighSurrogate(ch) && offset+1 < con.limit)
                            ch = REUtil.composeFromSurrogates(ch,  target .setIndex(  ++offset ) );
                        if (isEOLChar(ch))
                            return -1;
                    }
                    offset ++;
                } else {
                    int o1 = offset-1;
                    if (o1 >= con.limit || o1 < 0)
                        return -1;
                    int ch =  target .setIndex(  o1 ) ;
                    if (isSet(opts, SINGLE_LINE)) {
                        if (REUtil.isLowSurrogate(ch) && o1-1 >= 0)
                            o1 --;
                    } else {
                        if (REUtil.isLowSurrogate(ch) && o1-1 >= 0)
                            ch = REUtil.composeFromSurrogates( target .setIndex(  --o1 ) , ch);
                        if (!isEOLChar(ch))
                            return -1;
                    }
                    offset = o1;
                }
                op = op.next;
                break;

            case Op.RANGE:
            case Op.NRANGE:
                if (dx > 0) {
                    if (offset >= con.limit)
                        return -1;
                    int ch =  target .setIndex(  offset ) ;
                    if (REUtil.isHighSurrogate(ch) && offset+1 < con.limit)
                        ch = REUtil.composeFromSurrogates(ch,  target .setIndex(  ++offset ) );
                    RangeToken tok = op.getToken();
                    if (isSet(opts, IGNORE_CASE)) {
                        tok = tok.getCaseInsensitiveToken();
                        if (!tok.match(ch)) {
                            if (ch >= 0x10000)  return -1;
                            char uch;
                            if (!tok.match(uch = Character.toUpperCase((char)ch))
                                && !tok.match(Character.toLowerCase(uch)))
                                return -1;
                        }
                    } else {
                        if (!tok.match(ch))  return -1;
                    }
                    offset ++;
                } else {
                    int o1 = offset-1;
                    if (o1 >= con.limit || o1 < 0)
                        return -1;
                    int ch =  target .setIndex(  o1 ) ;
                    if (REUtil.isLowSurrogate(ch) && o1-1 >= 0)
                        ch = REUtil.composeFromSurrogates( target .setIndex(  --o1 ) , ch);
                    RangeToken tok = op.getToken();
                    if (isSet(opts, IGNORE_CASE)) {
                        tok = tok.getCaseInsensitiveToken();
                        if (!tok.match(ch)) {
                            if (ch >= 0x10000)  return -1;
                            char uch;
                            if (!tok.match(uch = Character.toUpperCase((char)ch))
                                && !tok.match(Character.toLowerCase(uch)))
                                return -1;
                        }
                    } else {
                        if (!tok.match(ch))  return -1;
                    }
                    offset = o1;
                }
                op = op.next;
                break;

            case Op.ANCHOR:
                boolean go = false;
                switch (op.getData()) {
                case '^':
                    if (isSet(opts, MULTIPLE_LINES)) {
                        if (!(offset == con.start
                              || offset > con.start && isEOLChar( target .setIndex(  offset-1 ) )))
                            return -1;
                    } else {
                        if (offset != con.start)
                            return -1;
                    }
                    break;

                case '@':                         // Internal use only.
                    // The @ always matches line beginnings.
                    if (!(offset == con.start
                          || offset > con.start && isEOLChar( target .setIndex(  offset-1 ) )))
                        return -1;
                    break;

                case '$':
                    if (isSet(opts, MULTIPLE_LINES)) {
                        if (!(offset == con.limit
                              || offset < con.limit && isEOLChar( target .setIndex(  offset ) )))
                            return -1;
                    } else {
                        if (!(offset == con.limit
                              || offset+1 == con.limit && isEOLChar( target .setIndex(  offset ) )
                              || offset+2 == con.limit &&  target .setIndex(  offset )  == CARRIAGE_RETURN
                              &&  target .setIndex(  offset+1 )  == LINE_FEED))
                            return -1;
                    }
                    break;

                case 'A':
                    if (offset != con.start)  return -1;
                    break;

                case 'Z':
                    if (!(offset == con.limit
                          || offset+1 == con.limit && isEOLChar( target .setIndex(  offset ) )
                          || offset+2 == con.limit &&  target .setIndex(  offset )  == CARRIAGE_RETURN
                          &&  target .setIndex(  offset+1 )  == LINE_FEED))
                        return -1;
                    break;

                case 'z':
                    if (offset != con.limit)  return -1;
                    break;

                case 'b':
                    if (con.length == 0)  return -1;
                    {
                        int after = getWordType(target, con.start, con.limit, offset, opts);
                        if (after == WT_IGNORE)  return -1;
                        int before = getPreviousWordType(target, con.start, con.limit, offset, opts);
                        if (after == before)  return -1;
                    }
                    break;

                case 'B':
                    if (con.length == 0)
                        go = true;
                    else {
                        int after = getWordType(target, con.start, con.limit, offset, opts);
                        go = after == WT_IGNORE
                             || after == getPreviousWordType(target, con.start, con.limit, offset, opts);
                    }
                    if (!go)  return -1;
                    break;

                case '<':
                    if (con.length == 0 || offset == con.limit)  return -1;
                    if (getWordType(target, con.start, con.limit, offset, opts) != WT_LETTER
                        || getPreviousWordType(target, con.start, con.limit, offset, opts) != WT_OTHER)
                        return -1;
                    break;

                case '>':
                    if (con.length == 0 || offset == con.start)  return -1;
                    if (getWordType(target, con.start, con.limit, offset, opts) != WT_OTHER
                        || getPreviousWordType(target, con.start, con.limit, offset, opts) != WT_LETTER)
                        return -1;
                    break;
                } // switch anchor type
                op = op.next;
                break;

            case Op.BACKREFERENCE:
                {
                    int refno = op.getData();
                    if (refno <= 0 || refno >= this.nofparen)
                        throw new RuntimeException("Internal Error: Reference number must be more than zero: "+refno);
                    if (con.match.getBeginning(refno) < 0
                        || con.match.getEnd(refno) < 0)
                        return -1;                // ********
                    int o2 = con.match.getBeginning(refno);
                    int literallen = con.match.getEnd(refno)-o2;
                    if (!isSet(opts, IGNORE_CASE)) {
                        if (dx > 0) {
                            if (!regionMatches(target, offset, con.limit, o2, literallen))
                                return -1;
                            offset += literallen;
                        } else {
                            if (!regionMatches(target, offset-literallen, con.limit, o2, literallen))
                                return -1;
                            offset -= literallen;
                        }
                    } else {
                        if (dx > 0) {
                            if (!regionMatchesIgnoreCase(target, offset, con.limit, o2, literallen))
                                return -1;
                            offset += literallen;
                        } else {
                            if (!regionMatchesIgnoreCase(target, offset-literallen, con.limit,
                                                         o2, literallen))
                                return -1;
                            offset -= literallen;
                        }
                    }
                }
                op = op.next;
                break;
            case Op.STRING:
                {
                    String literal = op.getString();
                    int literallen = literal.length();
                    if (!isSet(opts, IGNORE_CASE)) {
                        if (dx > 0) {
                            if (!regionMatches(target, offset, con.limit, literal, literallen))
                                return -1;
                            offset += literallen;
                        } else {
                            if (!regionMatches(target, offset-literallen, con.limit, literal, literallen))
                                return -1;
                            offset -= literallen;
                        }
                    } else {
                        if (dx > 0) {
                            if (!regionMatchesIgnoreCase(target, offset, con.limit, literal, literallen))
                                return -1;
                            offset += literallen;
                        } else {
                            if (!regionMatchesIgnoreCase(target, offset-literallen, con.limit,
                                                         literal, literallen))
                                return -1;
                            offset -= literallen;
                        }
                    }
                }
                op = op.next;
                break;

            case Op.CLOSURE:
                {
                    /*
                     * Saves current position to avoid
                     * zero-width repeats.
                     */
                    int id = op.getData();
                    if (id >= 0) {
                        int previousOffset = con.offsets[id];
                        if (previousOffset < 0 || previousOffset != offset) {
                            con.offsets[id] = offset;
                        } else {
                            con.offsets[id] = -1;
                            op = op.next;
                            break;
                        }
                    }

                    int ret = this. matchCharacterIterator (con, op.getChild(), offset, dx, opts);
                    if (id >= 0)  con.offsets[id] = -1;
                    if (ret >= 0)  return ret;
                    op = op.next;
                }
                break;

            case Op.QUESTION:
                {
                    int ret = this. matchCharacterIterator (con, op.getChild(), offset, dx, opts);
                    if (ret >= 0)  return ret;
                    op = op.next;
                }
                break;

            case Op.NONGREEDYCLOSURE:
            case Op.NONGREEDYQUESTION:
                {
                    int ret = this. matchCharacterIterator (con, op.next, offset, dx, opts);
                    if (ret >= 0)  return ret;
                    op = op.getChild();
                }
                break;

            case Op.UNION:
                for (int i = 0;  i < op.size();  i ++) {
                    int ret = this. matchCharacterIterator (con, op.elementAt(i), offset, dx, opts);
                    if (DEBUG) {
                        System.err.println("UNION: "+i+", ret="+ret);
                    }
                    if (ret >= 0)  return ret;
                }
                return -1;

            case Op.CAPTURE:
                int refno = op.getData();
                if (con.match != null && refno > 0) {
                    int save = con.match.getBeginning(refno);
                    con.match.setBeginning(refno, offset);
                    int ret = this. matchCharacterIterator (con, op.next, offset, dx, opts);
                    if (ret < 0)  con.match.setBeginning(refno, save);
                    return ret;
                } else if (con.match != null && refno < 0) {
                    int index = -refno;
                    int save = con.match.getEnd(index);
                    con.match.setEnd(index, offset);
                    int ret = this. matchCharacterIterator (con, op.next, offset, dx, opts);
                    if (ret < 0)  con.match.setEnd(index, save);
                    return ret;
                }
                op = op.next;
                break;

            case Op.LOOKAHEAD:
                if (0 > this. matchCharacterIterator (con, op.getChild(), offset, 1, opts))  return -1;
                op = op.next;
                break;
            case Op.NEGATIVELOOKAHEAD:
                if (0 <= this. matchCharacterIterator (con, op.getChild(), offset, 1, opts))  return -1;
                op = op.next;
                break;
            case Op.LOOKBEHIND:
                if (0 > this. matchCharacterIterator (con, op.getChild(), offset, -1, opts))  return -1;
                op = op.next;
                break;
            case Op.NEGATIVELOOKBEHIND:
                if (0 <= this. matchCharacterIterator (con, op.getChild(), offset, -1, opts))  return -1;
                op = op.next;
                break;

            case Op.INDEPENDENT:
                {
                    int ret = this. matchCharacterIterator (con, op.getChild(), offset, dx, opts);
                    if (ret < 0)  return ret;
                    offset = ret;
                    op = op.next;
                }
                break;

            case Op.MODIFIER:
                {
                    int localopts = opts;
                    localopts |= op.getData();
                    localopts &= ~op.getData2();
                    //System.err.println("MODIFIER: "+Integer.toString(opts, 16)+" -> "+Integer.toString(localopts, 16));
                    int ret = this. matchCharacterIterator (con, op.getChild(), offset, dx, localopts);
                    if (ret < 0)  return ret;
                    offset = ret;
                    op = op.next;
                }
                break;

            case Op.CONDITION:
                {
                    Op.ConditionOp cop = (Op.ConditionOp)op;
                    boolean matchp = false;
                    if (cop.refNumber > 0) {
                        if (cop.refNumber >= this.nofparen)
                            throw new RuntimeException("Internal Error: Reference number must be more than zero: "+cop.refNumber);
                        matchp = con.match.getBeginning(cop.refNumber) >= 0
                                 && con.match.getEnd(cop.refNumber) >= 0;
                    } else {
                        matchp = 0 <= this. matchCharacterIterator (con, cop.condition, offset, dx, opts);
                    }

                    if (matchp) {
                        op = cop.yes;
                    } else if (cop.no != null) {
                        op = cop.no;
                    } else {
                        op = cop.next;
                    }
                }
                break;

            default:
                throw new RuntimeException("Unknown operation type: "+op.type);
            } // switch (op.type)
        } // while
    }

    private static final int getPreviousWordType(CharacterIterator  target, int begin, int end,
                                                 int offset, int opts) {
        int ret = getWordType(target, begin, end, --offset, opts);
        while (ret == WT_IGNORE)
            ret = getWordType(target, begin, end, --offset, opts);
        return ret;
    }

    private static final int getWordType(CharacterIterator  target, int begin, int end,
                                         int offset, int opts) {
        if (offset < begin || offset >= end)  return WT_OTHER;
        return getWordType0( target .setIndex(  offset ) , opts);
    }



    private static final boolean regionMatches(CharacterIterator  target, int offset, int limit,
                                               String part, int partlen) {
        if (offset < 0)  return false;
        if (limit-offset < partlen)
            return false;
        int i = 0;
        while (partlen-- > 0) {
            if ( target .setIndex(  offset++ )  != part.charAt(i++))
                return false;
        }
        return true;
    }

    private static final boolean regionMatches(CharacterIterator  target, int offset, int limit,
                                               int offset2, int partlen) {
        if (offset < 0)  return false;
        if (limit-offset < partlen)
            return false;
        int i = offset2;
        while (partlen-- > 0) {
            if ( target .setIndex(  offset++ )  !=  target .setIndex(  i++ ) )
                return false;
        }
        return true;
    }

    /**
     * @see java.lang.String#regionMatches
     */
    private static final boolean regionMatchesIgnoreCase(CharacterIterator  target, int offset, int limit,
                                                         String part, int partlen) {
        if (offset < 0)  return false;
        if (limit-offset < partlen)
            return false;
        int i = 0;
        while (partlen-- > 0) {
            char ch1 =  target .setIndex(  offset++ ) ;
            char ch2 = part.charAt(i++);
            if (ch1 == ch2)
                continue;
            char uch1 = Character.toUpperCase(ch1);
            char uch2 = Character.toUpperCase(ch2);
            if (uch1 == uch2)
                continue;
            if (Character.toLowerCase(uch1) != Character.toLowerCase(uch2))
                return false;
        }
        return true;
    }

    private static final boolean regionMatchesIgnoreCase(CharacterIterator  target, int offset, int limit,
                                                         int offset2, int partlen) {
        if (offset < 0)  return false;
        if (limit-offset < partlen)
            return false;
        int i = offset2;
        while (partlen-- > 0) {
            char ch1 =  target .setIndex(  offset++ ) ;
            char ch2 =  target .setIndex(  i++ ) ;
            if (ch1 == ch2)
                continue;
            char uch1 = Character.toUpperCase(ch1);
            char uch2 = Character.toUpperCase(ch2);
            if (uch1 == uch2)
                continue;
            if (Character.toLowerCase(uch1) != Character.toLowerCase(uch2))
                return false;
        }
        return true;
    }




    // ================================================================

    /**
     * A regular expression.
     * @serial
     */
    String regex;
    /**
     * @serial
     */
    int options;

    /**
     * The number of parenthesis in the regular expression.
     * @serial
     */
    int nofparen;
    /**
     * Internal representation of the regular expression.
     * @serial
     */
    Token tokentree;

    boolean hasBackReferences = false;

    transient int minlength;
    transient Op operations = null;
    transient int numberOfClosures;
    transient Context context = null;
    transient RangeToken firstChar = null;

    transient String fixedString = null;
    transient int fixedStringOptions;
    transient BMPattern fixedStringTable = null;
    transient boolean fixedStringOnly = false;


    static final class Context {
        CharacterIterator ciTarget;
        String strTarget;
        char[] charTarget;
        int start;
        int limit;
        int length;
        Match match;
        boolean inuse = false;
        int[] offsets;

        Context() {
        }

        private void resetCommon(int nofclosures) {
            this.length = this.limit-this.start;
            this.inuse = true;
            this.match = null;
            if (this.offsets == null || this.offsets.length != nofclosures)
                this.offsets = new int[nofclosures];
            for (int i = 0;  i < nofclosures;  i ++)  this.offsets[i] = -1;
        }
        void reset(CharacterIterator target, int start, int limit, int nofclosures) {
            this.ciTarget = target;
            this.start = start;
            this.limit = limit;
            this.resetCommon(nofclosures);
        }
        void reset(String target, int start, int limit, int nofclosures) {
            this.strTarget = target;
            this.start = start;
            this.limit = limit;
            this.resetCommon(nofclosures);
        }
        void reset(char[] target, int start, int limit, int nofclosures) {
            this.charTarget = target;
            this.start = start;
            this.limit = limit;
            this.resetCommon(nofclosures);
        }
    }

    /**
     * Prepares for matching.  This method is called just before starting matching.
     */
    void prepare() {
        if (Op.COUNT)  Op.nofinstances = 0;
        this.compile(this.tokentree);
        /*
        if  (this.operations.type == Op.CLOSURE && this.operations.getChild().type == Op.DOT) { // .*
            Op anchor = Op.createAnchor(isSet(this.options, SINGLE_LINE) ? 'A' : '@');
            anchor.next = this.operations;
            this.operations = anchor;
        }
        */
        if (Op.COUNT)  System.err.println("DEBUG: The number of operations: "+Op.nofinstances);

        this.minlength = this.tokentree.getMinLength();

        this.firstChar = null;
        if (!isSet(this.options, PROHIBIT_HEAD_CHARACTER_OPTIMIZATION)
            && !isSet(this.options, XMLSCHEMA_MODE)) {
            RangeToken firstChar = Token.createRange();
            int fresult = this.tokentree.analyzeFirstCharacter(firstChar, this.options);
            if (fresult == Token.FC_TERMINAL) {
                firstChar.compactRanges();
                this.firstChar = firstChar;
                if (DEBUG)
                    System.err.println("DEBUG: Use the first character optimization: "+firstChar);
            }
        }

        if (this.operations != null
            && (this.operations.type == Op.STRING || this.operations.type == Op.CHAR)
            && this.operations.next == null) {
            if (DEBUG)
                System.err.print(" *** Only fixed string! *** ");
            this.fixedStringOnly = true;
            if (this.operations.type == Op.STRING)
                this.fixedString = this.operations.getString();
            else if (this.operations.getData() >= 0x10000) { // Op.CHAR
                this.fixedString = REUtil.decomposeToSurrogates(this.operations.getData());
            } else {
                char[] ac = new char[1];
                ac[0] = (char)this.operations.getData();
                this.fixedString = new String(ac);
            }
            this.fixedStringOptions = this.options;
            this.fixedStringTable = new BMPattern(this.fixedString, 256,
                                                  isSet(this.fixedStringOptions, IGNORE_CASE));
        } else if (!isSet(this.options, PROHIBIT_FIXED_STRING_OPTIMIZATION)
                   && !isSet(this.options, XMLSCHEMA_MODE)) {
            Token.FixedStringContainer container = new Token.FixedStringContainer();
            this.tokentree.findFixedString(container, this.options);
            this.fixedString = container.token == null ? null : container.token.getString();
            this.fixedStringOptions = container.options;
            if (this.fixedString != null && this.fixedString.length() < 2)
                this.fixedString = null;
            // This pattern has a fixed string of which length is more than one.
            if (this.fixedString != null) {
                this.fixedStringTable = new BMPattern(this.fixedString, 256,
                                                      isSet(this.fixedStringOptions, IGNORE_CASE));
                if (DEBUG) {
                    System.err.println("DEBUG: The longest fixed string: "+this.fixedString.length()
                                       +"/" //+this.fixedString
                                       +"/"+REUtil.createOptionString(this.fixedStringOptions));
                    System.err.print("String: ");
                    REUtil.dumpString(this.fixedString);
                }
            }
        }
    }

    /**
     * An option.
     * If you specify this option, <span class="REGEX"><kbd>(</kbd><var>X</var><kbd>)</kbd></span>
     * captures matched text, and <span class="REGEX"><kbd>(:?</kbd><var>X</var><kbd>)</kbd></span>
     * does not capture.
     *
     * @see #RegularExpression(java.lang.String,int)
     * @see #setPattern(java.lang.String,int)
    static final int MARK_PARENS = 1<<0;
     */

    /**
     * "i"
     */
    static final int IGNORE_CASE = 1<<1;

    /**
     * "s"
     */
    static final int SINGLE_LINE = 1<<2;

    /**
     * "m"
     */
    static final int MULTIPLE_LINES = 1<<3;

    /**
     * "x"
     */
    static final int EXTENDED_COMMENT = 1<<4;

    /**
     * This option redefines <span class="REGEX"><kbd>\d \D \w \W \s \S</kbd></span>.
     *
     * @see #RegularExpression(java.lang.String,int)
     * @see #setPattern(java.lang.String,int)
     * @see #UNICODE_WORD_BOUNDARY
     */
    static final int USE_UNICODE_CATEGORY = 1<<5; // "u"

    /**
     * An option.
     * This enables to process locale-independent word boundary for <span class="REGEX"><kbd>\b \B \&lt; \></kbd></span>.
     * <p>By default, the engine considers a position between a word character
     * (<span class="REGEX"><Kbd>\w</kbd></span>) and a non word character
     * is a word boundary.
     * <p>By this option, the engine checks word boundaries with the method of
     * 'Unicode Regular Expression Guidelines' Revision 4.
     *
     * @see #RegularExpression(java.lang.String,int)
     * @see #setPattern(java.lang.String,int)
     */
    static final int UNICODE_WORD_BOUNDARY = 1<<6; // "w"

    /**
     * "H"
     */
    static final int PROHIBIT_HEAD_CHARACTER_OPTIMIZATION = 1<<7;
    /**
     * "F"
     */
    static final int PROHIBIT_FIXED_STRING_OPTIMIZATION = 1<<8;
    /**
     * "X". XML Schema mode.
     */
    static final int XMLSCHEMA_MODE = 1<<9;
    /**
     * ",".
     */
    static final int SPECIAL_COMMA = 1<<10;


    private static final boolean isSet(int options, int flag) {
        return (options & flag) == flag;
    }

    /**
     * Creates a new RegularExpression instance.
     *
     * @param regex A regular expression
     * @exception com.sun.org.apache.xerces.internal.utils.regex.ParseException <VAR>regex</VAR> is not conforming to the syntax.
     */
    public RegularExpression(String regex) throws ParseException {
        this.setPattern(regex, null);
    }

    /**
     * Creates a new RegularExpression instance with options.
     *
     * @param regex A regular expression
     * @param options A String consisted of "i" "m" "s" "u" "w" "," "X"
     * @exception com.sun.org.apache.xerces.internal.utils.regex.ParseException <VAR>regex</VAR> is not conforming to the syntax.
     */
    public RegularExpression(String regex, String options) throws ParseException {
        this.setPattern(regex, options);
    }

    RegularExpression(String regex, Token tok, int parens, boolean hasBackReferences, int options) {
        this.regex = regex;
        this.tokentree = tok;
        this.nofparen = parens;
        this.options = options;
        this.hasBackReferences = hasBackReferences;
    }

    /**
     *
     */
    public void setPattern(String newPattern) throws ParseException {
        this.setPattern(newPattern, this.options);
    }

    private void setPattern(String newPattern, int options) throws ParseException {
        this.regex = newPattern;
        this.options = options;
        RegexParser rp = RegularExpression.isSet(this.options, RegularExpression.XMLSCHEMA_MODE)
                         ? new ParserForXMLSchema() : new RegexParser();
        this.tokentree = rp.parse(this.regex, this.options);
        this.nofparen = rp.parennumber;
        this.hasBackReferences = rp.hasBackReferences;

        this.operations = null;
        this.context = null;
    }
    /**
     *
     */
    public void setPattern(String newPattern, String options) throws ParseException {
        this.setPattern(newPattern, REUtil.parseOptions(options));
    }

    /**
     *
     */
    public String getPattern() {
        return this.regex;
    }

    /**
     * Represents this instence in String.
     */
    public String toString() {
        return this.tokentree.toString(this.options);
    }

    /**
     * Returns a option string.
     * The order of letters in it may be different from a string specified
     * in a constructor or <code>setPattern()</code>.
     *
     * @see #RegularExpression(java.lang.String,java.lang.String)
     * @see #setPattern(java.lang.String,java.lang.String)
     */
    public String getOptions() {
        return REUtil.createOptionString(this.options);
    }

    /**
     *  Return true if patterns are the same and the options are equivalent.
     */
    public boolean equals(Object obj) {
        if (obj == null)  return false;
        if (!(obj instanceof RegularExpression))
            return false;
        RegularExpression r = (RegularExpression)obj;
        return this.regex.equals(r.regex) && this.options == r.options;
    }

    boolean equals(String pattern, int options) {
        return this.regex.equals(pattern) && this.options == options;
    }

    /**
     *
     */
    public int hashCode() {
        return (this.regex+"/"+this.getOptions()).hashCode();
    }

    /**
     * Return the number of regular expression groups.
     * This method returns 1 when the regular expression has no capturing-parenthesis.
     *
     */
    public int getNumberOfGroups() {
        return this.nofparen;
    }

    // ================================================================

    private static final int WT_IGNORE = 0;
    private static final int WT_LETTER = 1;
    private static final int WT_OTHER = 2;
    private static final int getWordType0(char ch, int opts) {
        if (!isSet(opts, UNICODE_WORD_BOUNDARY)) {
            if (isSet(opts, USE_UNICODE_CATEGORY)) {
                return (Token.getRange("IsWord", true).match(ch)) ? WT_LETTER : WT_OTHER;
            }
            return isWordChar(ch) ? WT_LETTER : WT_OTHER;
        }

        switch (Character.getType(ch)) {
        case Character.UPPERCASE_LETTER:      // L
        case Character.LOWERCASE_LETTER:      // L
        case Character.TITLECASE_LETTER:      // L
        case Character.MODIFIER_LETTER:       // L
        case Character.OTHER_LETTER:          // L
        case Character.LETTER_NUMBER:         // N
        case Character.DECIMAL_DIGIT_NUMBER:  // N
        case Character.OTHER_NUMBER:          // N
        case Character.COMBINING_SPACING_MARK: // Mc
            return WT_LETTER;

        case Character.FORMAT:                // Cf
        case Character.NON_SPACING_MARK:      // Mn
        case Character.ENCLOSING_MARK:        // Mc
            return WT_IGNORE;

        case Character.CONTROL:               // Cc
            switch (ch) {
            case '\t':
            case '\n':
            case '\u000B':
            case '\f':
            case '\r':
                return WT_OTHER;
            default:
                return WT_IGNORE;
            }

        default:
            return WT_OTHER;
        }
    }

    // ================================================================

    static final int LINE_FEED = 0x000A;
    static final int CARRIAGE_RETURN = 0x000D;
    static final int LINE_SEPARATOR = 0x2028;
    static final int PARAGRAPH_SEPARATOR = 0x2029;

    private static final boolean isEOLChar(int ch) {
        return ch == LINE_FEED || ch == CARRIAGE_RETURN || ch == LINE_SEPARATOR
        || ch == PARAGRAPH_SEPARATOR;
    }

    private static final boolean isWordChar(int ch) { // Legacy word characters
        if (ch == '_')  return true;
        if (ch < '0')  return false;
        if (ch > 'z')  return false;
        if (ch <= '9')  return true;
        if (ch < 'A')  return false;
        if (ch <= 'Z')  return true;
        if (ch < 'a')  return false;
        return true;
    }

    private static final boolean matchIgnoreCase(int chardata, int ch) {
        if (chardata == ch)  return true;
        if (chardata > 0xffff || ch > 0xffff)  return false;
        char uch1 = Character.toUpperCase((char)chardata);
        char uch2 = Character.toUpperCase((char)ch);
        if (uch1 == uch2)  return true;
        return Character.toLowerCase(uch1) == Character.toLowerCase(uch2);
    }
}
