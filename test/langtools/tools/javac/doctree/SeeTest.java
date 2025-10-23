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
 * @bug 7021614 8031212 8273244 8284908 8200337 8288619 8352249
 * @summary extend com.sun.source API to support parsing javadoc comments
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.file
 *          jdk.compiler/com.sun.tools.javac.tree
 *          jdk.compiler/com.sun.tools.javac.util
 * @build DocCommentTester
 * @run main DocCommentTester SeeTest.java
 */

class SeeTest {
    /**
     * abc.
     * @see "String"
     */
    void quoted_text() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 1
    Text[TEXT, pos:0, abc.]
  body: empty
  block tags: 1
    See[SEE, pos:5
      reference: 1
        Text[TEXT, pos:10, "String"]
    ]
]
*/

    /**
     * Test '@' in quoted string.
     * @see "{@code}"
     */
    void at_sign_in_quoted_string() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 1
    Text[TEXT, pos:0, Test_'@'_in_quoted_string.]
  body: empty
  block tags: 1
    See[SEE, pos:27
      reference: 1
        Text[TEXT, pos:32, "{@code}"]
    ]
]
*/

    /**
     * Test new line before quoted string.
     * @see
     *    "{@code}"
     */
    @PrettyCheck(false)
    void new_line_before_quoted_string() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 1
    Text[TEXT, pos:0, Test_new_line_before_quoted_string.]
  body: empty
  block tags: 1
    See[SEE, pos:36
      reference: 1
        Text[TEXT, pos:44, "{@code}"]
    ]
]
*/

    /**
     * abc.
     * @see <a href="url">url</a>
     */
    void url() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 1
    Text[TEXT, pos:0, abc.]
  body: empty
  block tags: 1
    See[SEE, pos:5
      reference: 3
        StartElement[START_ELEMENT, pos:10
          name:a
          attributes: 1
            Attribute[ATTRIBUTE, pos:13
              name: href
              vkind: DOUBLE
              value: 1
                Text[TEXT, pos:19, url]
            ]
        ]
        Text[TEXT, pos:24, url]
        EndElement[END_ELEMENT, pos:27, a]
    ]
]
*/

    /**
     * abc.
     * @see String text
     */
    void string() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 1
    Text[TEXT, pos:0, abc.]
  body: empty
  block tags: 1
    See[SEE, pos:5
      reference: 2
        Reference[REFERENCE, pos:10, String]
        Text[TEXT, pos:17, text]
    ]
]
*/

    /**
     * abc.
     * @see java.lang.String text
     */
    void j_l_string() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 1
    Text[TEXT, pos:0, abc.]
  body: empty
  block tags: 1
    See[SEE, pos:5
      reference: 2
        Reference[REFERENCE, pos:10, java.lang.String]
        Text[TEXT, pos:27, text]
    ]
]
*/

    /**
     * abc.
     * @see java.lang.String#length text
     */
    void j_l_string_length() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 1
    Text[TEXT, pos:0, abc.]
  body: empty
  block tags: 1
    See[SEE, pos:5
      reference: 2
        Reference[REFERENCE, pos:10, java.lang.String#length]
        Text[TEXT, pos:34, text]
    ]
]
*/

    /**
     * abc.
     * @see java.lang.String#matches(String regex) text
     */
    void j_l_string_matches() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 1
    Text[TEXT, pos:0, abc.]
  body: empty
  block tags: 1
    See[SEE, pos:5
      reference: 2
        Reference[REFERENCE, pos:10, java.lang.String#matches(String_regex)]
        Text[TEXT, pos:49, text]
    ]
]
*/

    /**
     * abc.
     * @see java.lang.String##fragment text
     */
    void j_l_string_anchor() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 1
    Text[TEXT, pos:0, abc.]
  body: empty
  block tags: 1
    See[SEE, pos:5
      reference: 2
        Reference[REFERENCE, pos:10, java.lang.String##fragment]
        Text[TEXT, pos:37, text]
    ]
]
*/

    /**
     * abc.
     * @see 123 text
     */
    void bad_numeric() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 1
    Text[TEXT, pos:0, abc.]
  body: empty
  block tags: 1
    Erroneous[ERRONEOUS, pos:5, prefPos:17
      code: compiler.err.dc.unexpected.content
      body: @see_123_text
    ]
]
*/

}
