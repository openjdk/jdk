/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6251738 8226279
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
     * abc {@spec http://example.com label} def
     */
    void inline() {}
/*
DocComment[DOC_COMMENT, pos:1
  firstSentence: 3
    Text[TEXT, pos:1, abc_]
    Spec[SPEC, pos:5
      inline: true
      uri:
        Text[TEXT, pos:12, http://example.com]
      label: 1
        Text[TEXT, pos:31, label]
    ]
    Text[TEXT, pos:37, _def]
  body: empty
  block tags: empty
]
*/

    /**
     * abc
     * @spec http://example.com label
     */
    void block() {}
/*
DocComment[DOC_COMMENT, pos:1
  firstSentence: 1
    Text[TEXT, pos:1, abc]
  body: empty
  block tags: 1
    Spec[SPEC, pos:6
      inline: false
      uri:
        Text[TEXT, pos:12, http://example.com]
      label: 1
        Text[TEXT, pos:31, label]
    ]
]
*/

    /**
     * abc {@spec}
     */
    void bad_no_uri() {}
/*
DocComment[DOC_COMMENT, pos:1
  firstSentence: 3
    Text[TEXT, pos:1, abc_]
    Erroneous[ERRONEOUS, pos:5
      code: compiler.err.dc.no.uri
      body: {@spec
    ]
    Text[TEXT, pos:11, }]
  body: empty
  block tags: empty
]
*/

    /**
     * abc {@spec http://example.com}
     */
    void bad_no_label() {}
/*
DocComment[DOC_COMMENT, pos:1
  firstSentence: 2
    Text[TEXT, pos:1, abc_]
    Erroneous[ERRONEOUS, pos:5
      code: compiler.err.dc.no.label
      body: {@spec_http://example.com}
    ]
  body: empty
  block tags: empty
]
*/

}
