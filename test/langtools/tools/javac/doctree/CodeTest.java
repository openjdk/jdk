/*
 * Copyright (c) 2012, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7021614 8241780 8273244 8284908 8352249 8352389
 * @summary extend com.sun.source API to support parsing javadoc comments
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.file
 *          jdk.compiler/com.sun.tools.javac.tree
 *          jdk.compiler/com.sun.tools.javac.util
 * @build DocCommentTester
 * @run main DocCommentTester CodeTest.java
 */

class CodeTest {
    /** {@code if (a < b) { }} */
    void minimal() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 1
    Literal[CODE, pos:0, if_(a_<_b)_{_}]
  body: empty
  block tags: empty
]
*/

    /** [{@code if (a < b) { }}] */
    void in_brackets() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 3
    Text[TEXT, pos:0, []
    Literal[CODE, pos:1, if_(a_<_b)_{_}]
    Text[TEXT, pos:23, ]]
  body: empty
  block tags: empty
]
*/

    /** [ {@code if (a < b) { }} ] */
    void in_brackets_with_whitespace() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 3
    Text[TEXT, pos:0, [_]
    Literal[CODE, pos:2, if_(a_<_b)_{_}]
    Text[TEXT, pos:24, _]]
  body: empty
  block tags: empty
]
*/

    /**
     * {@code {@code nested} }
     */
    void nested() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 1
    Literal[CODE, pos:0, {@code_nested}_]
  body: empty
  block tags: empty
]
*/

    /**
     * {@code if (a < b) {
     *        }
     * }
     */
    void embedded_newline() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 1
    Literal[CODE, pos:0, if_(a_<_b)_{|_______}|]
  body: empty
  block tags: empty
]
*/

    /**
     * {@code
     * @tag
     * }
     */
    void embedded_at() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 1
    Literal[CODE, pos:0, |@tag|]
  body: empty
  block tags: empty
]
*/

    /**
     * <pre>{@code
     *     @Override
     *     void m() { }
     * }</pre>
     */
    void pre_at_code() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 2
    StartElement[START_ELEMENT, pos:0
      name:pre
      attributes: empty
    ]
    Literal[CODE, pos:5, ____@Override|____void_m()_{_}|]
  body: 1
    EndElement[END_ELEMENT, pos:44, pre]
  block tags: empty
]
*/

    /** {@code if (a < b) { } */
    void unterminated_1() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 1
    Erroneous[ERRONEOUS, pos:0, prefPos:20
      code: compiler.err.dc.unterminated.inline.tag
      body: {@code_if_(a_<_b)_{_}
    ]
  body: empty
  block tags: empty
]
*/

    /**
     * {@code if (a < b) { }
     * @author jjg */
    void unterminated_2() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 1
    Erroneous[ERRONEOUS, pos:0, prefPos:32
      code: compiler.err.dc.unterminated.inline.tag
      body: {@code_if_(a_<_b)_{_}|@author_jjg
    ]
  body: empty
  block tags: empty
]
*/

}

