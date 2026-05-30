/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8358754
 * @summary Implement Rich Notes in Java API Documentation
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.file
 *          jdk.compiler/com.sun.tools.javac.tree
 *          jdk.compiler/com.sun.tools.javac.util
 * @build DocCommentTester
 * @run main DocCommentTester NoteTest.java
 */

class NoteTest {

    /**
     * abc.
     * @note note body
     */
    void simple_block_note() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 1
    Text[TEXT, pos:0, abc.]
  body: empty
  block tags: 1
    Note[NOTE, pos:5
      tagName: note
      inline: false
      attributes: empty
      body: 1
        Text[TEXT, pos:11, note_body]
    ]
]
*/

    /**
     * abc.
     * @note [attr="value"] note body
     */
    void attr_block_note() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 1
    Text[TEXT, pos:0, abc.]
  body: empty
  block tags: 1
    Note[NOTE, pos:5
      tagName: note
      inline: false
      attributes: 1
        Attribute[ATTRIBUTE, pos:12
          name: attr
          vkind: DOUBLE
          value: 1
            Text[TEXT, pos:18, value]
        ]
      body: 1
        Text[TEXT, pos:26, note_body]
    ]
]
*/

    /**
     * abc {@note note body} def.
     */
    void simple_inline_note() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 3
    Text[TEXT, pos:0, abc_]
    Note[NOTE, pos:4
      tagName: note
      inline: true
      attributes: empty
      body: 1
        Text[TEXT, pos:11, note_body]
    ]
    Text[TEXT, pos:21, _def.]
  body: empty
  block tags: empty
]
*/

    /**
     * abc {@note [attr="value"] note body} def.
     */
    void attr_inline_note() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 3
    Text[TEXT, pos:0, abc_]
    Note[NOTE, pos:4
      tagName: note
      inline: true
      attributes: 1
        Attribute[ATTRIBUTE, pos:12
          name: attr
          vkind: DOUBLE
          value: 1
            Text[TEXT, pos:18, value]
        ]
      body: 1
        Text[TEXT, pos:26, note_body]
    ]
    Text[TEXT, pos:36, _def.]
  body: empty
  block tags: empty
]
*/

    /**
     * abc
     * @note
     */
    void empty_block_note() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 1
    Text[TEXT, pos:0, abc]
  body: empty
  block tags: 1
    Note[NOTE, pos:4
      tagName: note
      inline: false
      attributes: empty
      body: empty
    ]
]
*/

    /**
     * abc
     * @note [attr=val]
     */
    void empty_body_block_note() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 1
    Text[TEXT, pos:0, abc]
  body: empty
  block tags: 1
    Note[NOTE, pos:4
      tagName: note
      inline: false
      attributes: 1
        Attribute[ATTRIBUTE, pos:11
          name: attr
          vkind: UNQUOTED
          value: 1
            Text[TEXT, pos:16, val]
        ]
      body: empty
    ]
]
*/

    /**
     * abc {@note} def.
     */
    void empty_inline_note() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 3
    Text[TEXT, pos:0, abc_]
    Note[NOTE, pos:4
      tagName: note
      inline: true
      attributes: empty
      body: empty
    ]
    Text[TEXT, pos:11, _def.]
  body: empty
  block tags: empty
]
*/

    /**
     * abc {@note [attr="value"]} def.
     */
    void empty_body_inline_note() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 3
    Text[TEXT, pos:0, abc_]
    Note[NOTE, pos:4
      tagName: note
      inline: true
      attributes: 1
        Attribute[ATTRIBUTE, pos:12
          name: attr
          vkind: DOUBLE
          value: 1
            Text[TEXT, pos:18, value]
        ]
      body: empty
    ]
    Text[TEXT, pos:26, _def.]
  body: empty
  block tags: empty
]
*/

    /**
     * abc
     * @note {@code code} <b>bold</b>} def
     */
    void nested_tags_block_note() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 1
    Text[TEXT, pos:0, abc]
  body: empty
  block tags: 1
    Note[NOTE, pos:4
      tagName: note
      inline: false
      attributes: empty
      body: 6
        Literal[CODE, pos:10, code]
        Text[TEXT, pos:22, _]
        StartElement[START_ELEMENT, pos:23
          name:b
          attributes: empty
        ]
        Text[TEXT, pos:26, bold]
        EndElement[END_ELEMENT, pos:30, b]
        Text[TEXT, pos:34, }_def]
    ]
]
*/

    /**
     * abc {@note {@code code} <b>bold</b>} def.
     */
    void nested_tags_inline_note() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 3
    Text[TEXT, pos:0, abc_]
    Note[NOTE, pos:4
      tagName: note
      inline: true
      attributes: empty
      body: 5
        Literal[CODE, pos:11, code]
        Text[TEXT, pos:23, _]
        StartElement[START_ELEMENT, pos:24
          name:b
          attributes: empty
        ]
        Text[TEXT, pos:27, bold]
        EndElement[END_ELEMENT, pos:31, b]
    ]
    Text[TEXT, pos:36, _def.]
  body: empty
  block tags: empty
]
*/
}
