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
 * @bug 7021614 8273244 8352249
 * @summary extend com.sun.source API to support parsing javadoc comments
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.file
 *          jdk.compiler/com.sun.tools.javac.tree
 *          jdk.compiler/com.sun.tools.javac.util
 * @build DocCommentTester
 * @run main DocCommentTester SerialFieldTest.java
 */

class SerialFieldTest {

    /**
     * @serialField field String
     */
    String f1;
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: empty
  body: empty
  block tags: 1
    SerialField[SERIAL_FIELD, pos:0
      name:
        Identifier[IDENTIFIER, pos:13, field]
      type:
        Reference[REFERENCE, pos:19, String]
      description: empty
    ]
]
*/

    /**
     * @serialField field String f2 is a String
     */
    String f2;
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: empty
  body: empty
  block tags: 1
    SerialField[SERIAL_FIELD, pos:0
      name:
        Identifier[IDENTIFIER, pos:13, field]
      type:
        Reference[REFERENCE, pos:19, String]
      description: 1
        Text[TEXT, pos:26, f2_is_a_String]
    ]
]
*/

    /**
     * @serialField field String#member f3 is a String
     */
    String f3;
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: empty
  body: empty
  block tags: 1
    Erroneous[ERRONEOUS, pos:0, prefPos:25
      code: compiler.err.dc.ref.unexpected.input
      body: @serialField_field_String#member_f3_is_a_String
    ]
]
*/

    /**
     * @serialField field String##fragment f4 is a String
     */
    String f4;
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: empty
  body: empty
  block tags: 1
    Erroneous[ERRONEOUS, pos:0, prefPos:25
      code: compiler.err.dc.ref.unexpected.input
      body: @serialField_field_String##fragment_f4_is_a_String
    ]
]
*/

}

