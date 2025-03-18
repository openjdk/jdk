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
 * @bug 7021614 8273244 8284908 8298405 8352249
 * @summary extend com.sun.source API to support parsing javadoc comments
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.file
 *          jdk.compiler/com.sun.tools.javac.tree
 *          jdk.compiler/com.sun.tools.javac.util
 * @build DocCommentTester
 * @run main DocCommentTester EntityTest.java
 */

class EntityTest {
    /**
     *  abc &lt; def
     */
    public void name() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 3
    Text[TEXT, pos:0, abc_]
    Entity[ENTITY, pos:4, lt]
    Text[TEXT, pos:8, _def]
  body: empty
  block tags: empty
]
*/

    /**
     *  abc &#160; def
     */
    public void decimal_value() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 3
    Text[TEXT, pos:0, abc_]
    Entity[ENTITY, pos:4, #160]
    Text[TEXT, pos:10, _def]
  body: empty
  block tags: empty
]
*/

    /**
     *  abc &#xa0; def
     */
    public void lower_hex_value() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 3
    Text[TEXT, pos:0, abc_]
    Entity[ENTITY, pos:4, #xa0]
    Text[TEXT, pos:10, _def]
  body: empty
  block tags: empty
]
*/

    /**
     *  abc &#XA0; def
     */
    public void upper_hex_value() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 3
    Text[TEXT, pos:0, abc_]
    Entity[ENTITY, pos:4, #XA0]
    Text[TEXT, pos:10, _def]
  body: empty
  block tags: empty
]
*/

    /**
     *  abc & def
     */
    public void bad_amp() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 3
    Text[TEXT, pos:0, abc_]
    Erroneous[ERRONEOUS, pos:4
      code: compiler.err.dc.bad.entity
      body: &
    ]
    Text[TEXT, pos:5, _def]
  body: empty
  block tags: empty
]
*/

    /**
     *  abc &1 def
     */
    public void bad_entity_name() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 3
    Text[TEXT, pos:0, abc_]
    Erroneous[ERRONEOUS, pos:4
      code: compiler.err.dc.bad.entity
      body: &
    ]
    Text[TEXT, pos:5, 1_def]
  body: empty
  block tags: empty
]
*/

    /**
     *  abc &#012.3; def
     */
    public void bad_entity_decimal_value() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 3
    Text[TEXT, pos:0, abc_]
    Erroneous[ERRONEOUS, pos:4, prefPos:8
      code: compiler.err.dc.missing.semicolon
      body: &#012
    ]
    Text[TEXT, pos:9, .3;_def]
  body: empty
  block tags: empty
]
*/

    /**
     *  abc &#x012azc; def
     */
    public void bad_entity_hex_value() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 3
    Text[TEXT, pos:0, abc_]
    Erroneous[ERRONEOUS, pos:4, prefPos:10
      code: compiler.err.dc.missing.semicolon
      body: &#x012a
    ]
    Text[TEXT, pos:11, zc;_def]
  body: empty
  block tags: empty
]
*/

    ///abc & def
    public void markdown() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 1
    RawText[MARKDOWN, pos:0, abc_&_def]
  body: empty
  block tags: empty
]
*/

}
