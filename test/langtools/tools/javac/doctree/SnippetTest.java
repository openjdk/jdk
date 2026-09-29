/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8266666 8352249
 * @summary Implementation for snippets
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.file
 *          jdk.compiler/com.sun.tools.javac.tree
 *          jdk.compiler/com.sun.tools.javac.util
 * @build DocCommentTester
 * @run main DocCommentTester SnippetTest.java
 */

class SnippetTest {
    /**
     * {@snippet attr1="val1" :
     *     Hello, Snippet!
     * }
     */
    void inline() { }
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 1
    Snippet[SNIPPET, pos:0
      attributes: 1
        Attribute[ATTRIBUTE, pos:10
          name: attr1
          vkind: DOUBLE
          value: 1
            Text[TEXT, pos:17, val1]
        ]
      body:
        Text[TEXT, pos:25, ____Hello,_Snippet!|]
    ]
  body: empty
  block tags: empty
]
*/
}
