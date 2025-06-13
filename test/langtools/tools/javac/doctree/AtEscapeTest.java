/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8300914 8352249
 * @summary Allow `@` as an escape in documentation comments
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.file
 *          jdk.compiler/com.sun.tools.javac.tree
 *          jdk.compiler/com.sun.tools.javac.util
 * @build DocCommentTester
 * @run main DocCommentTester AtEscapeTest.java
 */

class AtEscapeTest {
    /**
     * abc
     * @@tag
     * def
     */
    void escape_block_tag() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 3
    Text[TEXT, pos:0, abc|]
    Escape[ESCAPE, pos:4, @]
    Text[TEXT, pos:6, tag|def]
  body: empty
  block tags: empty
]
*/

    /**
     * abc {@@tag} def
     */
    void escape_inline_tag() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 3
    Text[TEXT, pos:0, abc_{]
    Escape[ESCAPE, pos:5, @]
    Text[TEXT, pos:7, tag}_def]
  body: empty
  block tags: empty
]
*/

    /**
     * abc /* def *@/ ghi
     */
    void escape_end_comment() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 3
    Text[TEXT, pos:0, abc_/*_def_*]
    Escape[ESCAPE, pos:12, /]
    Text[TEXT, pos:14, _ghi]
  body: empty
  block tags: empty
]
*/
    /**
     abc
     @* def
     ghi
     */
    void escape_asterisk() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 3
    Text[TEXT, pos:0, abc|]
    Escape[ESCAPE, pos:4, *]
    Text[TEXT, pos:6, _def|ghi]
  body: empty
  block tags: empty
]
*/

    /**
     * abc.
     * not an escaped tag @@tag;
     * xyz.
     */
    void not_escaped_tag() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 1
    Text[TEXT, pos:0, abc.]
  body: 1
    Text[TEXT, pos:5, not_an_escaped_tag_@@tag;|xyz.]
  block tags: empty
]
*/

    /**
     * abc.
     * not an escaped asterisk @*;
     * xyz.
     */
    void not_escaped_asterisk() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 1
    Text[TEXT, pos:0, abc.]
  body: 1
    Text[TEXT, pos:5, not_an_escaped_asterisk_@*;|xyz.]
  block tags: empty
]
*/

    /**
     * abc.
     * not an escaped solidus @/.
     * xyz.
     */
    void not_escaped_solidus() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 1
    Text[TEXT, pos:0, abc.]
  body: 1
    Text[TEXT, pos:5, not_an_escaped_solidus_@/.|xyz.]
  block tags: empty
]
*/

}
