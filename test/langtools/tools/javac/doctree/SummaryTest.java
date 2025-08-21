/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8173425 8273244 8352249
 * @summary extend com.sun.source API to support parsing javadoc comments
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.file
 *          jdk.compiler/com.sun.tools.javac.tree
 *          jdk.compiler/com.sun.tools.javac.util
 * @build DocCommentTester
 * @run main DocCommentTester SummaryTest.java
 */

class SummaryTest {
    /**
     * {@summary} abc.
     */
    void empty() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 1
    Summary[SUMMARY, pos:0
      summary: empty
    ]
  body: 1
    Text[TEXT, pos:10, _abc.]
  block tags: empty
]
*/
    /**
     * {@summary abc} def.
     */
    void simple() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 1
    Summary[SUMMARY, pos:0
      summary: 1
        Text[TEXT, pos:10, abc]
    ]
  body: 1
    Text[TEXT, pos:14, _def.]
  block tags: empty
]
*/

    /**
     *    {@summary abc} def
     */
    void leading_space() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 1
    Summary[SUMMARY, pos:0
      summary: 1
        Text[TEXT, pos:10, abc]
    ]
  body: 1
    Text[TEXT, pos:14, _def]
  block tags: empty
]
*/

    /**
     * <p> {@summary abc} def
     */
    void leading_html() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 3
    StartElement[START_ELEMENT, pos:0
      name:p
      attributes: empty
    ]
    Text[TEXT, pos:3, _]
    Summary[SUMMARY, pos:4
      summary: 1
        Text[TEXT, pos:14, abc]
    ]
  body: 1
    Text[TEXT, pos:18, _def]
  block tags: empty
]
*/

    /**
     * abc {@summary def} ghi
     */
    void leading_text() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 2
    Text[TEXT, pos:0, abc_]
    Summary[SUMMARY, pos:4
      summary: 1
        Text[TEXT, pos:14, def]
    ]
  body: 1
    Text[TEXT, pos:18, _ghi]
  block tags: empty
]
*/

    /**
     * abc. {@summary def} ghi
     */
    void leading_text_with_break() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 1
    Text[TEXT, pos:0, abc.]
  body: 2
    Summary[SUMMARY, pos:5
      summary: 1
        Text[TEXT, pos:15, def]
    ]
    Text[TEXT, pos:19, _ghi]
  block tags: empty
]
*/

    /**
     * {@summary def} <p> ghi
     */
    void trailing_html() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 1
    Summary[SUMMARY, pos:0
      summary: 1
        Text[TEXT, pos:10, def]
    ]
  body: 3
    Text[TEXT, pos:14, _]
    StartElement[START_ELEMENT, pos:15
      name:p
      attributes: empty
    ]
    Text[TEXT, pos:18, _ghi]
  block tags: empty
]
*/

    /**
     * {@summary abc &lt;def&gt; ghi} jkl
     */
    void with_html() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 1
    Summary[SUMMARY, pos:0
      summary: 5
        Text[TEXT, pos:10, abc_]
        Entity[ENTITY, pos:14, lt]
        Text[TEXT, pos:18, def]
        Entity[ENTITY, pos:21, gt]
        Text[TEXT, pos:25, _ghi]
    ]
  body: 1
    Text[TEXT, pos:30, _jkl]
  block tags: empty
]
*/
}
