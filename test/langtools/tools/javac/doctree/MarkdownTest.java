/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
 */

class MarkdownTest {
    /**md
     * abc < def & ghi {@code 123} jkl {@unknown} mno.
     */
    void descriptionMix() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 5
    RawText[MARKDOWN, pos:4, abc_<_def_&_ghi_]
    Literal[CODE, pos:20, 123]
    RawText[MARKDOWN, pos:31, _jkl_]
    UnknownInlineTag[UNKNOWN_INLINE_TAG, pos:36
      tag:unknown
      content: 1
        Text[TEXT, pos:45]
    ]
    RawText[MARKDOWN, pos:46, _mno.]
  body: empty
  block tags: empty
]
*/

    /**md
     * @since abc < def & ghi {@code 123} jkl {@unknown} mno.
     */
    void blockTagMix() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: empty
  body: empty
  block tags: 1
    Since[SINCE, pos:4
      body: 5
        RawText[MARKDOWN, pos:11, abc_<_def_&_ghi_]
        Literal[CODE, pos:27, 123]
        RawText[MARKDOWN, pos:38, _jkl_]
        UnknownInlineTag[UNKNOWN_INLINE_TAG, pos:43
          tag:unknown
          content: 1
            Text[TEXT, pos:52]
        ]
        RawText[MARKDOWN, pos:53, _mno.]
    ]
]
*/

    /**md
     * 123 {@link Object abc < def & ghi {@code 123} jkl {@unknown} mno} 456.
     */
    void inlineTagMix() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 3
    RawText[MARKDOWN, pos:4, 123_]
    Link[LINK, pos:8
      reference:
        Reference[REFERENCE, pos:15, Object]
      body: 5
        RawText[MARKDOWN, pos:22, abc_<_def_&_ghi_]
        Literal[CODE, pos:38, 123]
        RawText[MARKDOWN, pos:49, _jkl_]
        UnknownInlineTag[UNKNOWN_INLINE_TAG, pos:54
          tag:unknown
          content: 1
            Text[TEXT, pos:63]
        ]
        RawText[MARKDOWN, pos:64, _mno]
    ]
    RawText[MARKDOWN, pos:69, _456.]
  body: empty
  block tags: empty
]
*/



}
