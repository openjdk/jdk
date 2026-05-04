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
 * @bug 7021614 8076026 8273244 8321500 8352249
 * @summary extend com.sun.source API to support parsing javadoc comments
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.file
 *          jdk.compiler/com.sun.tools.javac.tree
 *          jdk.compiler/com.sun.tools.javac.util
 * @build DocCommentTester
 * @run main DocCommentTester AttrTest.java
 */

class AttrTest {
    /**
     * <a name=unquoted>foo</a>
     */
    void unquoted_attr() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 3
    StartElement[START_ELEMENT, pos:0
      name:a
      attributes: 1
        Attribute[ATTRIBUTE, pos:3
          name: name
          vkind: UNQUOTED
          value: 1
            Text[TEXT, pos:8, unquoted]
        ]
    ]
    Text[TEXT, pos:17, foo]
    EndElement[END_ELEMENT, pos:20, a]
  body: empty
  block tags: empty
]
*/

    /**
     * <a name-test=hyphened>foo</a>
     */
    void hyphened_attr() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 3
    StartElement[START_ELEMENT, pos:0
      name:a
      attributes: 1
        Attribute[ATTRIBUTE, pos:3
          name: name-test
          vkind: UNQUOTED
          value: 1
            Text[TEXT, pos:13, hyphened]
        ]
    ]
    Text[TEXT, pos:22, foo]
    EndElement[END_ELEMENT, pos:25, a]
  body: empty
  block tags: empty
]
*/

    /**
     * <a name="double_quoted">foo</a>
     */
    void double_quoted_attr() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 3
    StartElement[START_ELEMENT, pos:0
      name:a
      attributes: 1
        Attribute[ATTRIBUTE, pos:3
          name: name
          vkind: DOUBLE
          value: 1
            Text[TEXT, pos:9, double_quoted]
        ]
    ]
    Text[TEXT, pos:24, foo]
    EndElement[END_ELEMENT, pos:27, a]
  body: empty
  block tags: empty
]
*/

    /**
     * <a name='single_quoted'>foo</a>
     */
    void single_quoted_attr() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 3
    StartElement[START_ELEMENT, pos:0
      name:a
      attributes: 1
        Attribute[ATTRIBUTE, pos:3
          name: name
          vkind: SINGLE
          value: 1
            Text[TEXT, pos:9, single_quoted]
        ]
    ]
    Text[TEXT, pos:24, foo]
    EndElement[END_ELEMENT, pos:27, a]
  body: empty
  block tags: empty
]
*/

    /**
     * <hr size="3">
     */
    void numeric_attr() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 1
    StartElement[START_ELEMENT, pos:0
      name:hr
      attributes: 1
        Attribute[ATTRIBUTE, pos:4
          name: size
          vkind: DOUBLE
          value: 1
            Text[TEXT, pos:10, 3]
        ]
    ]
  body: empty
  block tags: empty
]
*/

    /**
     * <a href="{@docRoot}/index.html">
     */
    void docRoot_attr() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 1
    StartElement[START_ELEMENT, pos:0
      name:a
      attributes: 1
        Attribute[ATTRIBUTE, pos:3
          name: href
          vkind: DOUBLE
          value: 2
            DocRoot[DOC_ROOT, pos:9]
            Text[TEXT, pos:19, /index.html]
        ]
    ]
  body: empty
  block tags: empty
]
*/

    /**
     * <a name="abc&quot;def">
     */
    void entity_attr() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 1
    StartElement[START_ELEMENT, pos:0
      name:a
      attributes: 1
        Attribute[ATTRIBUTE, pos:3
          name: name
          vkind: DOUBLE
          value: 3
            Text[TEXT, pos:9, abc]
            Entity[ENTITY, pos:12, quot]
            Text[TEXT, pos:18, def]
        ]
    ]
  body: empty
  block tags: empty
]
*/

    /**
     * <hr noshade>
     */
    void no_value_attr() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 1
    StartElement[START_ELEMENT, pos:0
      name:hr
      attributes: 1
        Attribute[ATTRIBUTE, pos:4
          name: noshade
          vkind: EMPTY
          value: null
        ]
    ]
  body: empty
  block tags: empty
]
*/

    /**
     * abc <hr size='3'/>
     */
    void self_closing_attr_1() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 2
    Text[TEXT, pos:0, abc_]
    StartElement[START_ELEMENT, pos:4
      name:hr
      attributes: 1
        Attribute[ATTRIBUTE, pos:8
          name: size
          vkind: SINGLE
          value: 1
            Text[TEXT, pos:14, 3]
        ]
    ]
  body: empty
  block tags: empty
]
*/

    /**
     * abc <hr size=3 />
     */
    void self_closing_attr_2() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 2
    Text[TEXT, pos:0, abc_]
    StartElement[START_ELEMENT, pos:4
      name:hr
      attributes: 1
        Attribute[ATTRIBUTE, pos:8
          name: size
          vkind: UNQUOTED
          value: 1
            Text[TEXT, pos:13, 3]
        ]
    ]
  body: empty
  block tags: empty
]
*/

    /**
     * abc <hr size="3
     */
    void unterminated_attr_eoi() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 3
    Text[TEXT, pos:0, abc_]
    Erroneous[ERRONEOUS, pos:4
      code: compiler.err.dc.malformed.html
      body: <
    ]
    Text[TEXT, pos:5, hr_size="3]
  body: empty
  block tags: empty
]
*/

    /**
     * abc <hr size="3
     * @author jjg
     */
    void unterminated_attr_block() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 3
    Text[TEXT, pos:0, abc_]
    Erroneous[ERRONEOUS, pos:4
      code: compiler.err.dc.malformed.html
      body: <
    ]
    Text[TEXT, pos:5, hr_size="3]
  body: empty
  block tags: 1
    Author[AUTHOR, pos:16
      name: 1
        Text[TEXT, pos:24, jjg]
    ]
]
*/

    /**
     * <a name1="val1" name2='val2' name3=val3 name4>
     */
    void multiple_attr() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 1
    StartElement[START_ELEMENT, pos:0
      name:a
      attributes: 4
        Attribute[ATTRIBUTE, pos:3
          name: name1
          vkind: DOUBLE
          value: 1
            Text[TEXT, pos:10, val1]
        ]
        Attribute[ATTRIBUTE, pos:16
          name: name2
          vkind: SINGLE
          value: 1
            Text[TEXT, pos:23, val2]
        ]
        Attribute[ATTRIBUTE, pos:29
          name: name3
          vkind: UNQUOTED
          value: 1
            Text[TEXT, pos:35, val3]
        ]
        Attribute[ATTRIBUTE, pos:40
          name: name4
          vkind: EMPTY
          value: null
        ]
    ]
  body: empty
  block tags: empty
]
*/

    /**
     * <a name1="{@literal value}" name2='@foo' name3="abc
     *@notag &lt;Noref&gt; {@literal xyz}">
     */
    void tags_in_attr() { }
/*
DocComment[DOC_COMMENT, pos:1
  firstSentence: 1
    StartElement[START_ELEMENT, pos:1
      name:a
      attributes: 3
        Attribute[ATTRIBUTE, pos:4
          name: name1
          vkind: DOUBLE
          value: 1
            Literal[LITERAL, pos:11, value]
        ]
        Attribute[ATTRIBUTE, pos:29
          name: name2
          vkind: SINGLE
          value: 1
            Text[TEXT, pos:36, @foo]
        ]
        Attribute[ATTRIBUTE, pos:42
          name: name3
          vkind: DOUBLE
          value: 6
            Text[TEXT, pos:49, abc|@notag_]
            Entity[ENTITY, pos:60, lt]
            Text[TEXT, pos:64, Noref]
            Entity[ENTITY, pos:69, gt]
            Text[TEXT, pos:73, _]
            Literal[LITERAL, pos:74, xyz]
        ]
    ]
  body: empty
  block tags: empty
]
*/

    /**
     * <a name1="{@literal value}" name2='@foo' name3="abc
     * @see Ref {@literal xyz}
     */
    void unclosed_attr() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 4
    Erroneous[ERRONEOUS, pos:0
      code: compiler.err.dc.malformed.html
      body: <
    ]
    Text[TEXT, pos:1, a_name1="]
    Literal[LITERAL, pos:10, value]
    Text[TEXT, pos:26, "_name2='@foo'_name3="abc]
  body: empty
  block tags: 1
    See[SEE, pos:52
      reference: 2
        Reference[REFERENCE, pos:57, Ref]
        Literal[LITERAL, pos:61, xyz]
    ]
]
*/

}
