/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

/**
 * JDK-8074687:  Add tests for JSON parsing of numeric keys
 *
 * @test
 * @run
 */

Assert.assertEquals(JSON.stringify(JSON.parse('{ "0": {} }')),                   '{"0":{}}');
Assert.assertEquals(JSON.stringify(JSON.parse('{ "0": 1 }')),                    '{"0":1}');

Assert.assertEquals(JSON.stringify(JSON.parse('{ "65503": {} }')),               '{"65503":{}}');
Assert.assertEquals(JSON.stringify(JSON.parse('{ "65503": 1 }')),                '{"65503":1}');
Assert.assertEquals(JSON.stringify(JSON.parse('{ "0": {}, "65503": {} }')),      '{"0":{},"65503":{}}');
Assert.assertEquals(JSON.stringify(JSON.parse('{ "0": 1, "65503": 1 }')),        '{"0":1,"65503":1}');
Assert.assertEquals(JSON.stringify(JSON.parse('{ "65503": {}, "0": {} }')),      '{"0":{},"65503":{}}');
Assert.assertEquals(JSON.stringify(JSON.parse('{ "65503": 1, "0": 1 }')),        '{"0":1,"65503":1}');

Assert.assertEquals(JSON.stringify(JSON.parse('{ "4294967295": {} }')),          '{"4294967295":{}}');
Assert.assertEquals(JSON.stringify(JSON.parse('{ "4294967295": 1 }')),           '{"4294967295":1}');
Assert.assertEquals(JSON.stringify(JSON.parse('{ "0": {}, "4294967295": {} }')), '{"0":{},"4294967295":{}}');
Assert.assertEquals(JSON.stringify(JSON.parse('{ "0": 1, "4294967295": 1 }')),   '{"0":1,"4294967295":1}');
Assert.assertEquals(JSON.stringify(JSON.parse('{ "4294967295": {}, "0": {} }')), '{"0":{},"4294967295":{}}');
Assert.assertEquals(JSON.stringify(JSON.parse('{ "4294967295": 1, "0": 1 }')),   '{"0":1,"4294967295":1}');

Assert.assertEquals(JSON.stringify(JSON.parse('{ "100": {} }')),                 '{"100":{}}');
Assert.assertEquals(JSON.stringify(JSON.parse('{ "100": 1 }')),                  '{"100":1}');
Assert.assertEquals(JSON.stringify(JSON.parse('{ "0": {}, "100": {} }')),        '{"0":{},"100":{}}');
Assert.assertEquals(JSON.stringify(JSON.parse('{ "0": 1, "100": 1 }')),          '{"0":1,"100":1}');
Assert.assertEquals(JSON.stringify(JSON.parse('{ "100": {}, "0": {} }')),        '{"0":{},"100":{}}');
Assert.assertEquals(JSON.stringify(JSON.parse('{ "100": 1, "0": 1 }')),          '{"0":1,"100":1}');

Assert.assertEquals(JSON.stringify(JSON.parse('{ "-100": {} }')),                '{"-100":{}}');
Assert.assertEquals(JSON.stringify(JSON.parse('{ "-100": 1 }')),                 '{"-100":1}');
Assert.assertEquals(JSON.stringify(JSON.parse('{ "0": {}, "-100": {} }')),       '{"0":{},"-100":{}}');
Assert.assertEquals(JSON.stringify(JSON.parse('{ "0": 1, "-100": 1 }')),         '{"0":1,"-100":1}');
Assert.assertEquals(JSON.stringify(JSON.parse('{ "-100": {}, "0": {} }')),       '{"0":{},"-100":{}}');
Assert.assertEquals(JSON.stringify(JSON.parse('{ "-100": 1, "0": 1 }')),         '{"0":1,"-100":1}');
