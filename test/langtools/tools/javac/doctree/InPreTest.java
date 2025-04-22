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
 * @bug 8078320 8273244 8284908 8352249 8352389
 * @summary extend com.sun.source API to support parsing javadoc comments
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.file
 *          jdk.compiler/com.sun.tools.javac.tree
 *          jdk.compiler/com.sun.tools.javac.util
 * @build DocCommentTester
 * @run main DocCommentTester InPreTest.java
 */

class InPreTest {
    /**
     * xyz<pre> pqr </pre> abc{@code  def  }ghi
     */
    public void after_pre() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 1
    Text[TEXT, pos:0, xyz]
  body: 6
    StartElement[START_ELEMENT, pos:3
      name:pre
      attributes: empty
    ]
    Text[TEXT, pos:8, _pqr_]
    EndElement[END_ELEMENT, pos:13, pre]
    Text[TEXT, pos:19, _abc]
    Literal[CODE, pos:23, _def__]
    Text[TEXT, pos:37, ghi]
  block tags: empty
]
*/
    /**
     * abc{@code def}ghi
     */
    public void no_pre() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 3
    Text[TEXT, pos:0, abc]
    Literal[CODE, pos:3, def]
    Text[TEXT, pos:14, ghi]
  body: empty
  block tags: empty
]
*/
    /**
     * xyz<pre> abc{@code  def  }ghi</pre>
     */
    public void pre_after_text() {}
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 1
    Text[TEXT, pos:0, xyz]
  body: 5
    StartElement[START_ELEMENT, pos:3
      name:pre
      attributes: empty
    ]
    Text[TEXT, pos:8, _abc]
    Literal[CODE, pos:12, _def__]
    Text[TEXT, pos:26, ghi]
    EndElement[END_ELEMENT, pos:29, pre]
  block tags: empty
]
*/

    /**
     * abc{@code  def  }ghi
     */
    public void no_pre_extra_whitespace() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 3
    Text[TEXT, pos:0, abc]
    Literal[CODE, pos:3, _def__]
    Text[TEXT, pos:17, ghi]
  body: empty
  block tags: empty
]
*/
    /**
     * <pre> abc{@code  def  }ghi</pre>
     */
    public void in_pre() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 4
    StartElement[START_ELEMENT, pos:0
      name:pre
      attributes: empty
    ]
    Text[TEXT, pos:5, _abc]
    Literal[CODE, pos:9, _def__]
    Text[TEXT, pos:23, ghi]
  body: 1
    EndElement[END_ELEMENT, pos:26, pre]
  block tags: empty
]
*/
    /**
     * <pre> abc{@code
     * def  }ghi</pre>
     */
    public void in_pre_with_space_nl() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 4
    StartElement[START_ELEMENT, pos:0
      name:pre
      attributes: empty
    ]
    Text[TEXT, pos:5, _abc]
    Literal[CODE, pos:9, |def__]
    Text[TEXT, pos:22, ghi]
  body: 1
    EndElement[END_ELEMENT, pos:25, pre]
  block tags: empty
]
*/

    /**
     * <pre> abc{@code
     *def  }ghi</pre>
     */
    public void in_pre_with_nl() { }
/*
DocComment[DOC_COMMENT, pos:1
  firstSentence: 4
    StartElement[START_ELEMENT, pos:1
      name:pre
      attributes: empty
    ]
    Text[TEXT, pos:6, _abc]
    Literal[CODE, pos:10, |def__]
    Text[TEXT, pos:23, ghi]
  body: 1
    EndElement[END_ELEMENT, pos:26, pre]
  block tags: empty
]
*/
    /**
     * <pre> {@code
     * abc  }
     * def</pre>
     */
    public void in_pre_with_space_at_code_nl() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 3
    StartElement[START_ELEMENT, pos:0
      name:pre
      attributes: empty
    ]
    Literal[CODE, pos:6, abc__]
    Text[TEXT, pos:19, |def]
  body: 1
    EndElement[END_ELEMENT, pos:23, pre]
  block tags: empty
]
*/
    /**
     * <pre> <code>
     *   abc
     * </code></pre>
     */
    public void in_pre_with_space_code_nl() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 4
    StartElement[START_ELEMENT, pos:0
      name:pre
      attributes: empty
    ]
    StartElement[START_ELEMENT, pos:6
      name:code
      attributes: empty
    ]
    Text[TEXT, pos:13, __abc|]
    EndElement[END_ELEMENT, pos:19, code]
  body: 1
    EndElement[END_ELEMENT, pos:26, pre]
  block tags: empty
]
*/
    /**
     * abc {@code
     */
    public void bad_code_no_content() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 2
    Text[TEXT, pos:0, abc_]
    Erroneous[ERRONEOUS, pos:4, prefPos:9
      code: compiler.err.dc.unterminated.inline.tag
      body: {@code
    ]
  body: empty
  block tags: empty
]
*/
    /**
     * abc {@code abc
     */
    public void bad_code_content() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 2
    Text[TEXT, pos:0, abc_]
    Erroneous[ERRONEOUS, pos:4, prefPos:13
      code: compiler.err.dc.unterminated.inline.tag
      body: {@code_abc
    ]
  body: empty
  block tags: empty
]
*/
}
