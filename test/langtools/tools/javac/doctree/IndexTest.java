/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8144287 8273244 8284908 8305620 8352249
 * @summary extend com.sun.source API to support parsing javadoc comments
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.file
 *          jdk.compiler/com.sun.tools.javac.tree
 *          jdk.compiler/com.sun.tools.javac.util
 * @build DocCommentTester
 * @run main DocCommentTester IndexTest.java
 */

class IndexTest {
    /**
     * abc {@index xyz} def
     */
    void simple_term() {}
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 3
    Text[TEXT, pos:0, abc_]
    Index[INDEX, pos:4
      term:
        Text[TEXT, pos:12, xyz]
      description: empty
    ]
    Text[TEXT, pos:16, _def]
  body: empty
  block tags: empty
]
*/

    /**
     * abc {@index lmn pqr stu} def
     */
    void simple_term_with_description() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 3
    Text[TEXT, pos:0, abc_]
    Index[INDEX, pos:4
      term:
        Text[TEXT, pos:12, lmn]
      description: 1
        Text[TEXT, pos:16, pqr_stu]
    ]
    Text[TEXT, pos:24, _def]
  body: empty
  block tags: empty
]
*/

    /**
     * abc {@index ijk {lmn opq} } def
     */
    void phrase_with_curly_description() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 3
    Text[TEXT, pos:0, abc_]
    Index[INDEX, pos:4
      term:
        Text[TEXT, pos:12, ijk]
      description: 1
        Text[TEXT, pos:16, {lmn_opq}_]
    ]
    Text[TEXT, pos:27, _def]
  body: empty
  block tags: empty
]
*/
    /**
     * abc {@index ijk lmn
     * opq
     * rst
     * } def
     */
    void phrase_with_nl_description() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 3
    Text[TEXT, pos:0, abc_]
    Index[INDEX, pos:4
      term:
        Text[TEXT, pos:12, ijk]
      description: 1
        Text[TEXT, pos:16, lmn|opq|rst|]
    ]
    Text[TEXT, pos:29, _def]
  body: empty
  block tags: empty
]
*/

    /**
     * abc {@index "lmn pqr"} def
     */
    void phrase_no_description() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 3
    Text[TEXT, pos:0, abc_]
    Index[INDEX, pos:4
      term:
        Text[TEXT, pos:12, "lmn_pqr"]
      description: empty
    ]
    Text[TEXT, pos:22, _def]
  body: empty
  block tags: empty
]
*/

    /**
     * abc {@index "ijk lmn" pqr xyz} def
     */
    void phrase_with_description() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 3
    Text[TEXT, pos:0, abc_]
    Index[INDEX, pos:4
      term:
        Text[TEXT, pos:12, "ijk_lmn"]
      description: 1
        Text[TEXT, pos:22, pqr_xyz]
    ]
    Text[TEXT, pos:30, _def]
  body: empty
  block tags: empty
]
*/

    /**
     * abc {@index {@xyz} "{@see xyz}"} def
     */
    void term_and_description_with_nested_tag() {}
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 3
    Text[TEXT, pos:0, abc_]
    Index[INDEX, pos:4
      term:
        Text[TEXT, pos:12, {@xyz}]
      description: 3
        Text[TEXT, pos:19, "]
        UnknownInlineTag[UNKNOWN_INLINE_TAG, pos:20
          tag:see
          content: 1
            Text[TEXT, pos:26, xyz]
        ]
        Text[TEXT, pos:30, "]
    ]
    Text[TEXT, pos:32, _def]
  body: empty
  block tags: empty
]
*/
    /**
     * abc {@index @def lmn } xyz
     */
    void term_with_at() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 3
    Text[TEXT, pos:0, abc_]
    Index[INDEX, pos:4
      term:
        Text[TEXT, pos:12, @def]
      description: 1
        Text[TEXT, pos:17, lmn_]
    ]
    Text[TEXT, pos:22, _xyz]
  body: empty
  block tags: empty
]
*/

    /**
     * abc {@index ijk@lmn pqr } xyz
     */
    void term_with_text_at() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 3
    Text[TEXT, pos:0, abc_]
    Index[INDEX, pos:4
      term:
        Text[TEXT, pos:12, ijk@lmn]
      description: 1
        Text[TEXT, pos:20, pqr_]
    ]
    Text[TEXT, pos:25, _xyz]
  body: empty
  block tags: empty
]
*/

    /**
     * abc {@index ""} def
     */
    void empty_index_in_quotes() {}
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 3
    Text[TEXT, pos:0, abc_]
    Index[INDEX, pos:4
      term:
        Text[TEXT, pos:12, ""]
      description: empty
    ]
    Text[TEXT, pos:15, _def]
  body: empty
  block tags: empty
]
*/

    /**
     * abc {@index
     * @return def} xyz
     */
    void bad_nl_at_in_term() {}
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 2
    Text[TEXT, pos:0, abc_]
    Erroneous[ERRONEOUS, pos:4, prefPos:10
      code: compiler.err.dc.no.content
      body: {@index
    ]
  body: empty
  block tags: 1
    Return[RETURN, pos:12
      description: 1
        Text[TEXT, pos:20, def}_xyz]
    ]
]
*/
    /**
     * abc {@index "xyz } def
     */
    void bad_unbalanced_quote() {}
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 2
    Text[TEXT, pos:0, abc_]
    Erroneous[ERRONEOUS, pos:4, prefPos:21
      code: compiler.err.dc.no.content
      body: {@index_"xyz_}_def
    ]
  body: empty
  block tags: empty
]
*/
    /**
     * abc {@index} def
     */
    void bad_no_index() {}
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 3
    Text[TEXT, pos:0, abc_]
    Erroneous[ERRONEOUS, pos:4, prefPos:10
      code: compiler.err.dc.no.content
      body: {@index
    ]
    Text[TEXT, pos:11, }_def]
  body: empty
  block tags: empty
]
*/

}
