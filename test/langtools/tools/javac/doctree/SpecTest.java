/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6251738 8226279 8352249
 * @summary javadoc should support a new at-spec tag
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.file
 *          jdk.compiler/com.sun.tools.javac.tree
 *          jdk.compiler/com.sun.tools.javac.util
 * @build DocCommentTester
 * @run main DocCommentTester SpecTest.java
 */

class SpecTest {

    /**
     * abc.
     * @spec http://example.com title
     */
    void block() {}
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 1
    Text[TEXT, pos:0, abc.]
  body: empty
  block tags: 1
    Spec[SPEC, pos:5
      url:
        Text[TEXT, pos:11, http://example.com]
      title: 1
        Text[TEXT, pos:30, title]
    ]
]
*/

    /**
     * abc.
     * @spec
     */
    void bad_no_url() {}
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 1
    Text[TEXT, pos:0, abc.]
  body: empty
  block tags: 1
    Erroneous[ERRONEOUS, pos:5, prefPos:9
      code: compiler.err.dc.no.url
      body: @spec
    ]
]
*/

    /**
     * abc.
     * @spec http://example.com
     */
    void bad_no_label() {}
/*
DocComment[DOC_COMMENT, pos:0
  firstSentence: 1
    Text[TEXT, pos:0, abc.]
  body: empty
  block tags: 1
    Erroneous[ERRONEOUS, pos:5, prefPos:28
      code: compiler.err.dc.no.title
      body: @spec_http://example.com
    ]
]
*/

}
