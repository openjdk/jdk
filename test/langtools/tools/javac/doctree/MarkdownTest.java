/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/*
 * @test
 * @bug 8298405
 * @summary Markdown support in the standard doclet
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.file
 *          jdk.compiler/com.sun.tools.javac.tree
 *          jdk.compiler/com.sun.tools.javac.util
 * @build DocCommentTester
 * @run main DocCommentTester MarkdownTest.java
 * @run main DocCommentTester -useStandardTransformer MarkdownTest.java
 */

/*
 * Test for handling Markdown content.
 *
 * In the tests for code spans and code blocks, "@dummy" is used as a dummy inline
 * or block tag to verify that it is skipped as part of the code span or code block.
 * In other words, "@dummy" should appear as a literal part of the Markdown content.
 * Conversely, standard tags are used to verify that a fragment of text is not being
 * skipped as a code span or code block. In other words, they should be recognized as tags
 * and not skipped as part of any Markdown content.
 *
 * "@dummy" is also known to DocCommentTester and will not have any preceding whitespace
 * removed during normalization.
 */

class MarkdownTest {
    ///abc < def & ghi {@code 123} jkl {@unknown} mno.
    void descriptionMix() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 5
    RawText[MARKDOWN, pos:0, abc_<_def_&_ghi_]
    Literal[CODE, pos:16, 123]
    RawText[MARKDOWN, pos:27, _jkl_]
    UnknownInlineTag[UNKNOWN_INLINE_TAG, pos:32
      tag:unknown
      content: 1
        Text[TEXT, pos:41]
    ]
    RawText[MARKDOWN, pos:42, _mno.]
  body: empty
  block tags: empty
]
*/

    ///@since abc < def & ghi {@code 123} jkl {@unknown} mno.
    void blockTagMix() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: empty
  body: empty
  block tags: 1
    Since[SINCE, pos:0
      body: 5
        RawText[MARKDOWN, pos:7, abc_<_def_&_ghi_]
        Literal[CODE, pos:23, 123]
        RawText[MARKDOWN, pos:34, _jkl_]
        UnknownInlineTag[UNKNOWN_INLINE_TAG, pos:39
          tag:unknown
          content: 1
            Text[TEXT, pos:48]
        ]
        RawText[MARKDOWN, pos:49, _mno.]
    ]
]
*/

    ///123 {@link Object abc < def & ghi {@code 123} jkl {@unknown} mno} 456.
    void inlineTagMix() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 3
    RawText[MARKDOWN, pos:0, 123_]
    Link[LINK, pos:4
      reference:
        Reference[REFERENCE, pos:11, Object]
      body: 5
        RawText[MARKDOWN, pos:18, abc_<_def_&_ghi_]
        Literal[CODE, pos:34, 123]
        RawText[MARKDOWN, pos:45, _jkl_]
        UnknownInlineTag[UNKNOWN_INLINE_TAG, pos:50
          tag:unknown
          content: 1
            Text[TEXT, pos:59]
        ]
        RawText[MARKDOWN, pos:60, _mno]
    ]
    RawText[MARKDOWN, pos:65, _456.]
  body: empty
  block tags: empty
]
*/

    ///123 `abc` 456.
    void simpleCodeSpan() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 1
    RawText[MARKDOWN, pos:0, 123_`abc`_456.]
  body: empty
  block tags: empty
]
*/

    ///123 `abc`
    void simpleCodeSpanAtEndOfInput() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 1
    RawText[MARKDOWN, pos:0, 123_`abc`]
  body: empty
  block tags: empty
]
*/

    ///123 ```abc``` 456.
    void mediumCodeSpan() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 1
    RawText[MARKDOWN, pos:0, 123_```abc```_456.]
  body: empty
  block tags: empty
]
*/

    ///123 ```abc`def``` 456.
    void mediumCodeSpanWithBackTicks() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 1
    RawText[MARKDOWN, pos:0, 123_```abc`def```_456.]
  body: empty
  block tags: empty
]
*/

    ///123 ```abc{@dummy ...}def``` 456.
    void mediumCodeSpanWithNotInlineTag() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 1
    RawText[MARKDOWN, pos:0, 123_```abc{@dummy_...}def```_456.]
  body: empty
  block tags: empty
]
*/

    ///123 ```abc
    ///@dummy def``` 456.
    void mediumCodeSpanWithNotBlockTag() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 1
    RawText[MARKDOWN, pos:0, 123_```abc|@dummy_def```_456.]
  body: empty
  block tags: empty
]
*/

    ///123.
    ///```
    ///abc
    ///```
    ///456.
    void simpleFencedCodeBlock_backtick() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 1
    RawText[MARKDOWN, pos:0, 123.]
  body: 1
    RawText[MARKDOWN, pos:5, ```|abc|```|456.]
  block tags: empty
]
*/

    ///123.
    ///~~~
    ///abc
    ///{@dummy ...}
    ///~~~
    ///456.
    void simpleFencedCodeBlock_tilde() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 1
    RawText[MARKDOWN, pos:0, 123.]
  body: 1
    RawText[MARKDOWN, pos:5, ~~~|abc|{@dummy_...}|~~~|456.]
  block tags: empty
]
*/

    ///123.
    ///```
    ///abc
    ///```
    void simpleFencedCodeBlock_atEndOfInput() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 1
    RawText[MARKDOWN, pos:0, 123.]
  body: 1
    RawText[MARKDOWN, pos:5, ```|abc|```]
  block tags: empty
]
*/

    ///123.
    ///```
    ///abc {@dummy def} ghi
    ///```
    ///456.
    void fencedCodeBlockWithInlineTag_backtick() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 1
    RawText[MARKDOWN, pos:0, 123.]
  body: 1
    RawText[MARKDOWN, pos:5, ```|abc_{@dummy_def}_ghi|```|456.]
  block tags: empty
]
*/

    ///123.
    ///```
    ///abc ``` ghi
    ///{@dummy ...}
    ///```
    ///456.
    void fencedCodeBlockWithBackTicks_backtick() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 1
    RawText[MARKDOWN, pos:0, 123.]
  body: 1
    RawText[MARKDOWN, pos:5, ```|abc_```_ghi|{@dummy_...}|```|456.]
  block tags: empty
]
*/

    ///123.
    ///```abc`def``` 456.
    void codeSpanNotCodeBlock() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 1
    RawText[MARKDOWN, pos:0, 123.]
  body: 1
    RawText[MARKDOWN, pos:5, ```abc`def```_456.]
  block tags: empty
]
*/

    ///123.
    ///```
    ///{@code ...}
    ///~~~
    ///456.
    void mismatchedFences() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 1
    RawText[MARKDOWN, pos:0, 123.]
  body: 1
    RawText[MARKDOWN, pos:5, ```|{@code_...}|~~~|456.]
  block tags: empty
]
*/

    ///123.
    ///`````
    ///``` ghi
    ///{@dummy ...}
    ///`````
    ///456.
    void fencedCodeBlockWithShortFence_backtick() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 1
    RawText[MARKDOWN, pos:0, 123.]
  body: 1
    RawText[MARKDOWN, pos:5, `````|```_ghi|{@dummy_...}|`````|456.]
  block tags: empty
]
*/

    ///123.
    ///
    ///    abc {@dummy ...}
    ///    @dummy
    ///    def
    ///
    ///456 {@code ...}.
    void indentedCodeBlock_afterBlank() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 1
    RawText[MARKDOWN, pos:0, 123.]
  body: 3
    RawText[MARKDOWN, pos:10, abc_{@dummy_...}|____@dummy|____def||456_]
    Literal[CODE, pos:51, ...]
    RawText[MARKDOWN, pos:62, .]
  block tags: empty
]
*/

    ///123.
    ///### heading
    ///    abc {@dummy ...}
    ///    @dummy
    ///    def
    ///456 {@code ...}.
    void indentedCodeBlock_afterATX() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 1
    RawText[MARKDOWN, pos:0, 123.]
  body: 3
    RawText[MARKDOWN, pos:5, ###_heading|____abc_{@dummy_...}|____@dummy|____def|456_]
    Literal[CODE, pos:61, ...]
    RawText[MARKDOWN, pos:72, .]
  block tags: empty
]
*/

    ///123.
    ///Heading
    ///-------
    ///    abc {@dummy ...}
    ///    @dummy
    ///    def
    ///456 {@code ...}.
    void indentedCodeBlock_afterSetext() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 1
    RawText[MARKDOWN, pos:0, 123.]
  body: 3
    RawText[MARKDOWN, pos:5, Heading|-------|____abc_{@dummy_...}|____@dummy|____def|456_]
    Literal[CODE, pos:65, ...]
    RawText[MARKDOWN, pos:76, .]
  block tags: empty
]
*/

    ///123.
    ///- - - - -
    ///    abc {@dummy ...}
    ///    @dummy
    ///    def
    ///456 {@code ...}.
    void indentedCodeBlock_afterThematicBreak() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 1
    RawText[MARKDOWN, pos:0, 123.]
  body: 3
    RawText[MARKDOWN, pos:5, -_-_-_-_-|____abc_{@dummy_...}|____@dummy|____def|456_]
    Literal[CODE, pos:59, ...]
    RawText[MARKDOWN, pos:70, .]
  block tags: empty
]
*/

    ///123.
    ///```
    ///abc
    ///{@dummy}
    ///def
    ///```
    ///    abc {@dummy ...}
    ///    @dummy
    ///    def
    ///456 {@code ...}.
    void indentedCodeBlock_afterFencedCodeBlock() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 1
    RawText[MARKDOWN, pos:0, 123.]
  body: 3
    RawText[MARKDOWN, pos:5, ```|abc|{@dummy}|def|```|____abc...mmy_...}|____@dummy|____def|456_]
    Literal[CODE, pos:74, ...]
    RawText[MARKDOWN, pos:85, .]
  block tags: empty
]
*/

    ///123.
    ///
    ///```
    ///public class HelloWorld {
    ///    @dummy
    ///    public static void main(String... args) {
    ///        System.out.println("Hello World");
    ///    }
    ///}
    ///```
    ///456 {@code ...}.
    void fencedHelloWorld() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 1
    RawText[MARKDOWN, pos:0, 123.]
  body: 3
    RawText[MARKDOWN, pos:6, ```|public_class_HelloWorld_{|__..."Hello_World");|____}|}|```|456_]
    Literal[CODE, pos:152, ...]
    RawText[MARKDOWN, pos:163, .]
  block tags: empty
]
*/

    ///123.
    ///
    ///    public class HelloWorld {
    ///        @dummy
    ///        public static void main(String... args) {
    ///            System.out.println("Hello World");
    ///        }
    ///    }
    ///
    ///456 {@code ...}.
    void indentedHelloWorld() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 1
    RawText[MARKDOWN, pos:0, 123.]
  body: 3
    RawText[MARKDOWN, pos:10, public_class_HelloWorld_{|______...o_World");|________}|____}||456_]
    Literal[CODE, pos:169, ...]
    RawText[MARKDOWN, pos:180, .]
  block tags: empty
]
*/

    ///{@summary abc ``code-span {@dummy ...}`` def {@code ...} }
    ///rest.
    void codeSpanInInlineTag() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 1
    Summary[SUMMARY, pos:0
      summary: 3
        RawText[MARKDOWN, pos:10, abc_``code-span_{@dummy_...}``_def_]
        Literal[CODE, pos:45, ...]
        RawText[MARKDOWN, pos:56, _]
    ]
  body: 1
    RawText[MARKDOWN, pos:58, |rest.]
  block tags: empty
]
*/

    ///{@summary abc
    ///```code-block
    ///  {@dummy ...}
    ///```
    ///def {@code ...} }
    ///rest.
    void codeBlockInInlineTag() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 1
    Summary[SUMMARY, pos:0
      summary: 3
        RawText[MARKDOWN, pos:10, abc|```code-block|__{@dummy_...}|```|def_]
        Literal[CODE, pos:51, ...]
        RawText[MARKDOWN, pos:62, _]
    ]
  body: 1
    RawText[MARKDOWN, pos:64, |rest.]
  block tags: empty
]
*/

    ///abc `
    ///def
    void unmatchedBackTick() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 1
    RawText[MARKDOWN, pos:0, abc_`|def]
  body: empty
  block tags: empty
]
*/

    ///{@summary abc `
    ///def}
    ///rest
    void unmatchedBackTickInInline() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 1
    Summary[SUMMARY, pos:0
      summary: 1
        RawText[MARKDOWN, pos:10, abc_`|def]
    ]
  body: 1
    RawText[MARKDOWN, pos:20, |rest]
  block tags: empty
]
*/

// While this is an important test case, it is also a negative one
// (that is, the AST contains an Erroneous node).
// Note how the backticks "match" across the end of the inline tag.
// That's unfortunate, but cannot reasonably be detected without
// examining the contents of a code span.
// Not surprisingly, some of the checks fail for this (bad) test case.
// * PrettyChecker fails because it does not handle an unterminated inline tag.
// * StartEndPosChecker fails because it does not handle an unterminated inline tag.
//
// Disabled until we can either enhance the checkers or select which
// checkers to use.

//    ///{@summary abc `
//    ///def}
//    ///rest `more`
//    ///
//    void unmatchedBackTickInInline2() { }
///*
//DocComment[DOC_COMMENT, pos:0
//  firstSentence: 1
//    Summary[SUMMARY, pos:0
//      summary: 1
//        Erroneous[ERRONEOUS, pos:10, prefPos:31
//          code: compiler.err.dc.unterminated.inline.tag
//          body: abc_`|def}|rest_`more`
//        ]
//    ]
//  body: empty
//  block tags: empty
//]
//*/

    ///Indented by 0.
    ///
    ///   * list
    ///
    ///    code block
    ///
    ///done.
    void indent0() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 1
    RawText[MARKDOWN, pos:0, Indented_by_0.]
  body: 1
    RawText[MARKDOWN, pos:19, *_list||____code_block||done.]
  block tags: empty
]
*/

    /// Indented by 1.
    ///
    ///    * list
    ///
    ///     code block
    ///
    /// done.
    void indent1() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 1
    RawText[MARKDOWN, pos:0, Indented_by_1.]
  body: 1
    RawText[MARKDOWN, pos:19, *_list||____code_block||done.]
  block tags: empty
]
*/

    ///        Indented by 8.
    ///
    ///           * list
    ///
    ///            code block
    ///
    ///        done.
    void indent8() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 1
    RawText[MARKDOWN, pos:0, Indented_by_8.]
  body: 1
    RawText[MARKDOWN, pos:19, *_list||____code_block||done.]
  block tags: empty
]
*/


}
