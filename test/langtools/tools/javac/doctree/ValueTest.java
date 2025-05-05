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
 * @bug 7021614 8273244 8284908 8352249
 * @summary extend com.sun.source API to support parsing javadoc comments
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.file
 *          jdk.compiler/com.sun.tools.javac.tree
 *          jdk.compiler/com.sun.tools.javac.util
 * @build DocCommentTester
 * @run main DocCommentTester ValueTest.java
 */

class ValueTest {
    /**
     * abc {@value}
     */
    int no_ref() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 2
    Text[TEXT, pos:0, abc_]
    Value[VALUE, pos:4
      format: null
      reference: null
    ]
  body: empty
  block tags: empty
]
*/

    /**
     * abc {@value java.awt.Color#RED}
     */
    int typical() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 2
    Text[TEXT, pos:0, abc_]
    Value[VALUE, pos:4
      format: null
      reference:
        Reference[REFERENCE, pos:12, java.awt.Color#RED]
    ]
  body: empty
  block tags: empty
]
*/

    /**
     * abc {@value java.awt.Color#RED }
     */
    int trailing_ws() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 2
    Text[TEXT, pos:0, abc_]
    Value[VALUE, pos:4
      format: null
      reference:
        Reference[REFERENCE, pos:12, java.awt.Color#RED]
    ]
  body: empty
  block tags: empty
]
*/

    /**
     * abc {@value java.awt.Color#RED junk}
     */
    int trailing_junk() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 3
    Text[TEXT, pos:0, abc_]
    Erroneous[ERRONEOUS, pos:4, prefPos:31
      code: compiler.err.dc.unexpected.content
      body: {@value_java.awt.Color#RED_j
    ]
    Text[TEXT, pos:32, unk}]
  body: empty
  block tags: empty
]
*/

    /**
     * abc {@value %d java.awt.Color#RED}
     */
    int format_plain() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 2
    Text[TEXT, pos:0, abc_]
    Value[VALUE, pos:4
      format:
        Text[TEXT, pos:12, %d]
      reference:
        Reference[REFERENCE, pos:15, java.awt.Color#RED]
    ]
  body: empty
  block tags: empty
]
*/

    /**
     * abc {@value "%d" java.awt.Color#RED}
     */
    int format_quoted() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 2
    Text[TEXT, pos:0, abc_]
    Value[VALUE, pos:4
      format:
        Text[TEXT, pos:12, "%d"]
      reference:
        Reference[REFERENCE, pos:17, java.awt.Color#RED]
    ]
  body: empty
  block tags: empty
]
*/

    /**
     * abc {@value 0x%x4 java.awt.Color#RED}
     */
    int format_invalid() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 3
    Text[TEXT, pos:0, abc_]
    Erroneous[ERRONEOUS, pos:4, prefPos:12
      code: compiler.err.dc.ref.unexpected.input
      body: {@value_0x%x4
    ]
    Text[TEXT, pos:17, _java.awt.Color#RED}]
  body: empty
  block tags: empty
]
*/

    /**
     * abc {@value "%d" java.awt.Color#RED junk}
     */
    int format_trailing_junk() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 3
    Text[TEXT, pos:0, abc_]
    Erroneous[ERRONEOUS, pos:4, prefPos:36
      code: compiler.err.dc.unexpected.content
      body: {@value_"%d"_java.awt.Color#RED_j
    ]
    Text[TEXT, pos:37, unk}]
  body: empty
  block tags: empty
]
*/

    /**
     * abc {@value java.awt.Color}
     */
    int type_reference() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 3
    Text[TEXT, pos:0, abc_]
    Erroneous[ERRONEOUS, pos:4, prefPos:16
      code: compiler.err.dc.ref.unexpected.input
      body: {@value_java.awt.Color
    ]
    Text[TEXT, pos:26, }]
  body: empty
  block tags: empty
]
*/

    /**
     * abc {@value java.awt.Color##fragment}
     */
    int anchor_reference() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 3
    Text[TEXT, pos:0, abc_]
    Erroneous[ERRONEOUS, pos:4, prefPos:27
      code: compiler.err.dc.ref.unexpected.input
      body: {@value_java.awt.Color##fragment
    ]
    Text[TEXT, pos:36, }]
  body: empty
  block tags: empty
]
*/
}


