/*
 * Copyright (c) 2022, Red Hat Inc. and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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


import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import jdk.jpackage.internal.regfile.*;
import jdk.jpackage.internal.regfile.parser.Parser;
import jdk.jpackage.test.Annotations.Test;

import static jdk.jpackage.test.TKit.assertEquals;
import static jdk.jpackage.test.TKit.assertThrows;

/*
 * @test
 * @summary Test case for .reg files values
 * @library ../../helpers
 * @build jdk.jpackage.test.*
 * @build WinRegFileParserTest
 * @requires (os.family == "windows")
 * @modules jdk.jpackage/jdk.jpackage.internal
 *          jdk.jpackage/jdk.jpackage.internal.regfile
 *          jdk.jpackage/jdk.jpackage.internal.regfile.parser
 * @run main jdk.jpackage.test.Main
 *  --jpt-run=WinRegFileParserTest
 */
public class WinRegFileParserTest {

    @Test
    public void testNoHeader() {
        assertThrows(RegFileParseException.class, () -> parseResource(
                "test_fail_no_header.reg"), "test_fail_no_header");
    }

    @Test
    public void testNoEol() {
        assertThrows(RegFileParseException.class, () -> parseResource(
                "test_fail_no_eol.reg"), "test_fail_no_eol");
    }

    @Test
    public void testNoKey() {
        assertThrows(RegFileParseException.class, () -> parseResource(
                "test_fail_no_key.reg"), "test_fail_no_key");
    }

    @Test
    public void testMalformedKey() {
        assertThrows(RegFileParseException.class, () -> parseResource(
                "test_fail_malformed_key.reg"), "test_fail_malformed_key");
    }

    @Test
    public void testInvalidRoot() {
        assertThrows(RegFileParseException.class, () -> parseResource(
                "test_fail_invalid_root.reg"), "test_fail_invalid_root");
    }

    @Test
    public void testMalformedValueName() {
        assertThrows(RegFileParseException.class, () -> parseResource(
                "test_fail_malformed_value_name.reg"), "test_fail_malformed_value_name");
    }

    @Test
    public void testEmpty() throws RegFileParseException {
        List<RegFileKey> keys = parseResource("test_success_empty.reg");
        assertEquals(0, keys.size(), "empty keys size");
    }

    @Test
    public void testSimple() throws RegFileParseException {
        List<RegFileKey> keys = parseResource("test_success_simple.reg");
        assertEquals(2, keys.size(), "simple keys size");

        assertEquals(RegFileRootKey.HKLM.name(), keys.get(0).getRoot().name(), "simple 0 root");
        assertEquals(2, keys.get(0).getPathParts().size(), "simple 0 parts size");
        assertEquals("SOFTWARE", keys.get(0).getPathParts().get(0), "simple 0 part 0");
        assertEquals("test", keys.get(0).getPathParts().get(1), "simple 0 part 1");
        assertEquals(2, keys.get(0).getValues().size(), "simple 0 values size");
        assertEquals("test_name1", keys.get(0).getValues().get(0).getName(), "simple 0 value 0");
        assertEquals(RegFileValueType.REG_SZ.name(), keys.get(0).getValues().get(0).getType().name(), "simple 0 value 0 type");
        assertEquals("test_value1", keys.get(0).getValues().get(0).getValue(), "simple 0 value 0");
        assertEquals("test_name2", keys.get(0).getValues().get(1).getName(), "simple 0 value 1 name");
        assertEquals(RegFileValueType.REG_SZ.name(), keys.get(0).getValues().get(1).getType().name(), "simple 0 value 1 type");
        assertEquals("test_value2", keys.get(0).getValues().get(1).getValue(), "simple 0 value 1");

        assertEquals(RegFileRootKey.HKLM.name(), keys.get(1).getRoot().name(), "simple 1 root");
        assertEquals(3, keys.get(1).getPathParts().size(), "simple 1 parts size");
        assertEquals("SOFTWARE", keys.get(1).getPathParts().get(0), "simple 1 part 0");
        assertEquals("test", keys.get(1).getPathParts().get(1), "simple 1 part 1");
        assertEquals("keys", keys.get(1).getPathParts().get(2), "simple 1 part 2");
        assertEquals(1, keys.get(1).getValues().size(), "simple 1 keys size");
        assertEquals("test_name3", keys.get(1).getValues().get(0).getName(), "simple 1 value 0 name");
        assertEquals(RegFileValueType.REG_SZ.name(), keys.get(1).getValues().get(0).getType().name(), "simple 1 value 0 type");
        assertEquals("test_value3", keys.get(1).getValues().get(0).getValue(), "simple 1 value 0");
    }

    @Test
    public void testRoots() throws RegFileParseException {
        List<RegFileKey> keys = parseResource("test_success_roots.reg");
        assertEquals(5, keys.size(), "roots key size");
        assertEquals(RegFileRootKey.HKMU.name(), keys.get(0).getRoot().name(), "roots 0");
        assertEquals(RegFileRootKey.HKCR.name(), keys.get(1).getRoot().name(), "roots 0");
        assertEquals(RegFileRootKey.HKCU.name(), keys.get(2).getRoot().name(), "roots 0");
        assertEquals(RegFileRootKey.HKLM.name(), keys.get(3).getRoot().name(), "roots 0");
        assertEquals(RegFileRootKey.HKU.name(), keys.get(4).getRoot().name(), "roots 0");
    }

    @Test
    public void testKeyNonascii() throws RegFileParseException {
        List<RegFileKey> keys = parseResource("test_success_key_nonascii.reg");
        assertEquals(1, keys.size(), "key nonascii keys size");
        assertEquals(2, keys.get(0).getPathParts().size(), "key nonascii parts size");
        assertEquals("\u0417\u0434\u0440\u0430\u0432\u0435\u0439\u0442\u0435",
                keys.get(0).getPathParts().get(1), "key nonascii");
        assertEquals(1, keys.get(0).getValues().size(), "key nonascii values size");
    }

    @Test
    public void testKeySpaces() throws RegFileParseException {
        List<RegFileKey> keys = parseResource("test_success_key_spaces.reg");
        assertEquals(1, keys.size(), "key spaces keys size");
        assertEquals(3, keys.get(0).getPathParts().size(), "key spaces parts size");
        assertEquals("foo bar", keys.get(0).getPathParts().get(1), "key spaces parts 1");
        assertEquals("baz", keys.get(0).getPathParts().get(2), "key spaces part 2");
        assertEquals(1, keys.get(0).getValues().size(), "key spaces values size");
    }

    @Test
    public void testKeyBrackets() throws RegFileParseException {
        List<RegFileKey> keys = parseResource("test_success_key_brackets.reg");
        assertEquals(1, keys.size(), "key brackets keys size");
        assertEquals(3, keys.get(0).getPathParts().size(), "key brackets parts size");
        assertEquals("foo]]bar[", keys.get(0).getPathParts().get(1), "key brackets parts 0");
        assertEquals("baz", keys.get(0).getPathParts().get(2), "key brackets part 1");
        assertEquals(1, keys.get(0).getValues().size(), "key brackets values size");
    }

    @Test
    public void testValueDefault() throws RegFileParseException {
        List<RegFileKey> keys = parseResource("test_success_value_default.reg");
        assertEquals(1, keys.size(), "value default keys size");
        assertEquals(1, keys.get(0).getValues().size(), "value default values size");
        assertEquals("", keys.get(0).getValues().get(0).getName(), "value default name");
    }

    @Test
    public void testValueNameNonascii() throws RegFileParseException {
        List<RegFileKey> keys = parseResource("test_success_value_name_nonascii.reg");
        assertEquals(1, keys.size(), "value nonascii keys size");
        assertEquals(1, keys.get(0).getValues().size(), "value nonascii values size");
        assertEquals("\u0417\u0434\u0440\u0430\u0432\u0435\u0439\u0442\u0435",
                keys.get(0).getValues().get(0).getName(), "value nonascii name");
    }

    @Test
    public void testValueNameQuotes() throws RegFileParseException {
        List<RegFileKey> keys = parseResource("test_success_value_name_quotes.reg");
        assertEquals(1, keys.size(), "value quotes keys size");
        assertEquals(1, keys.get(0).getValues().size(), "value quotes value size");
        assertEquals("test\\\"\\\"_name\\\"", keys.get(0).getValues().get(0).getName(), "value quotes");
    }

    @Test
    public void testValueSz() throws RegFileParseException {
        List<RegFileKey> keys = parseResource("test_success_value_sz.reg");
        assertEquals(1, keys.size(), "value sz keys");
        List<RegFileValue> values = keys.get(0).getValues();
        assertEquals(5, values.size(), "value sz values size");
        values.forEach(val ->
                assertEquals(RegFileValueType.REG_SZ.name(), val.getType().name(), "value sz type"));
        assertEquals("value_string_empty", values.get(0).getName(), "value sz empty name");
        assertEquals("", values.get(0).getValue(), "value sz emptyvalue");
        assertEquals("value_string_with_case", values.get(1).getName(), "value sz case name");
        assertEquals("withCase", values.get(1).getValue(), "value sz case");
        assertEquals("value_string_with_quotes", values.get(2).getName(), "value sz quotes name");
        assertEquals("with_\\\"quotes\\\"\\\"", values.get(2).getValue(), "value sz quotes");
        assertEquals("value_string_with_spaces", values.get(3).getName(), "value sz spaes name");
        assertEquals("with spaces", values.get(3).getValue(), "value sz spaces");
        assertEquals("value_string_nonascii", values.get(4).getName(), "value sz nonascii name");
        assertEquals("\u0417\u0434\u0440\u0430\u0432\u0435\u0439\u0442\u0435",
                values.get(4).getValue(), "value sz nonascii");
    }

    @Test
    public void testValueBinary() throws RegFileParseException {
        List<RegFileKey> keys = parseResource("test_success_value_binary.reg");
        assertEquals(1, keys.size(), "value binary keys size");
        List<RegFileValue> values = keys.get(0).getValues();
        assertEquals(3, values.size(), "value binary values size");
        values.forEach(val ->
                assertEquals(RegFileValueType.REG_BINARY.name(), val.getType().name(), "value binary type"));
        assertEquals("value_binary_empty", values.get(0).getName(), "value binary empty name");
        assertEquals("", values.get(0).getValue(), "value binary empty");
        assertEquals("value_binary", values.get(1).getName(), "value binary name");
        assertEquals("de,ad,be,ef", values.get(1).getValue(), "value binary");
        assertEquals("value_binary_long", values.get(2).getName(), "value binary long name");
        assertEquals(
                "01,23,45,67,89,ab,cd,ef,fe,dc,ba,98,76,54,32,10,12,34," +
                "56,78,90,98,76,54,32,12,34,56,78,90,98,76,54,32,12,34,56,78,90,98,76,54,32," +
                "12,34,56,78,90,98,76,54,32,12,34,56,78,90,98,76,54,32,10",
                values.get(2).getValue(), "value binary long");
    }

    @Test
    public void testValueDword() throws RegFileParseException {
        List<RegFileKey> keys = parseResource("test_success_value_dword.reg");
        assertEquals(1, keys.size(), "value dword keys size");
        List<RegFileValue> values = keys.get(0).getValues();
        assertEquals(2, values.size(), "value dword values size");
        values.forEach(val ->
                assertEquals(RegFileValueType.REG_DWORD.name(), val.getType().name(), "value dword type"));
        assertEquals("value_dword_zero", values.get(0).getName(), "value dword zero name");
        assertEquals("00000000", values.get(0).getValue(), "value dword zero");
        assertEquals("value_dword", values.get(1).getName(), "value dword name");
        assertEquals("0000002a", values.get(1).getValue(), "value dword");
    }

    @Test
    public void testValueQword() throws RegFileParseException {
        List<RegFileKey> keys = parseResource("test_success_value_qword.reg");
        assertEquals(1, keys.size(), "value qword keys size");
        List<RegFileValue> values = keys.get(0).getValues();
        assertEquals(2, values.size(), "value qword values size");
        values.forEach(val ->
                assertEquals(RegFileValueType.REG_QWORD.name(), val.getType().name(), "value qword type"));
        assertEquals("value_qword_zero", values.get(0).getName(), "value qword zero name");
        assertEquals("00,00,00,00,00,00,00,00", values.get(0).getValue(), "value qword zero");
        assertEquals("value_qword", values.get(1).getName(), "value qword name");
        assertEquals("b2,c9,06,2a,b6,2f,c1,4c", values.get(1).getValue(), "value qword");
    }

    @Test
    public void testValueMultiSz() throws RegFileParseException {
        List<RegFileKey> keys = parseResource("test_success_value_multi_sz.reg");
        assertEquals(1, keys.size(), "value multi_sz keys size");
        List<RegFileValue> values = keys.get(0).getValues();
        assertEquals(3, values.size(), "value multi_sz values size");
        values.forEach(val ->
                assertEquals(RegFileValueType.REG_MULTI_SZ.name(), val.getType().name(), "value multi_sz type"));
        assertEquals("value_multi_string_empty", values.get(0).getName(), "value multi_sz empty name");
        assertEquals("00,00", values.get(0).getValue(), "value multi_sz empty");
        assertEquals("value_multi_string", values.get(1).getName(), "value multi_sz name");
        assertEquals(
                "74,00,65,00,73,00,74,00,5f,00,6d,00,75,00,6c,00,74," +
                "00,69,00,5f,00,73,00,74,00,72,00,69,00,6e,00,67,00,00,00,00,00",
                values.get(1).getValue(), "value multi_sz");
        assertEquals("value_multi_string_long", values.get(2).getName(), "value multi_sz long name");
        assertEquals(
                "57,00,68,00,61,00,74,00,20,00,69,00,73,00,20," +
                "00,74,00,68,00,69,00,73,00,3f,00,00,00,54,00,68,00,65,00,20,00,70,00,6c,00," +
                "61,00,63,00,65,00,20,00,74,00,6f,00,20,00,63,00,6f,00,6c,00,6c,00,61,00,62," +
                "00,6f,00,72,00,61,00,74,00,65,00,20,00,6f,00,6e,00,20,00,61,00,6e,00,20,00," +
                "6f,00,70,00,65,00,6e,00,2d,00,73,00,6f,00,75,00,72,00,63,00,65,00,20,00,69," +
                "00,6d,00,70,00,6c,00,65,00,6d,00,65,00,6e,00,74,00,61,00,74,00,69,00,6f,00," +
                "6e,00,20,00,6f,00,66,00,20,00,74,00,68,00,65,00,20,00,4a,00,61,00,76,00,61," +
                "00,20,00,50,00,6c,00,61,00,74,00,66,00,6f,00,72,00,6d,00,2c,00,20,00,53,00," +
                "74,00,61,00,6e,00,64,00,61,00,72,00,64,00,20,00,45,00,64,00,69,00,74,00,69," +
                "00,6f,00,6e,00,2c,00,20,00,61,00,6e,00,64,00,20,00,72,00,65,00,6c,00,61,00," +
                "74,00,65,00,64,00,20,00,70,00,72,00,6f,00,6a,00,65,00,63,00,74,00,73,00,2e," +
                "00,00,00,00,00",
                values.get(2).getValue(), "value multi_sz long");
    }

    @Test
    public void testValueExpandSz() throws RegFileParseException {
        List<RegFileKey> keys = parseResource("test_success_value_expand_sz.reg");
        assertEquals(1, keys.size(), "value expand_sz keys size");
        List<RegFileValue> values = keys.get(0).getValues();
        assertEquals(2, values.size(), "value expand_sz values size");
        values.forEach(val ->
                assertEquals(RegFileValueType.REG_EXPAND_SZ.name(), val.getType().name(), "value expand_sz type"));
        assertEquals("value_expandable_string_empty", values.get(0).getName(), "value expand_sz empty name");
        assertEquals("00,00", values.get(0).getValue(), "value expand_sz empty");
        assertEquals("value_expandable_string", values.get(1).getName(), "value expand_sz name");
        assertEquals(
                "65,00,78,00,70,00,61,00,6e,00,64,00,61,00,62," +
                "00,6c,00,65,00,20,00,25,00,4a,00,41,00,56,00,41,00,5f,00,48,00,4f,00,4d,00," +
                "45,00,25,00,20,00,76,00,61,00,6c,00,75,00,65,00,00,00",
                values.get(1).getValue(), "value expand_sz");
    }

    @Test
    public void testAll() throws RegFileParseException {
        List<RegFileKey> keys = parseResource("test_success_all.reg");
        assertEquals(10, keys.size(), "all keys size");
        RegFileKey key = keys.get(keys.size() - 1);
        assertEquals(RegFileRootKey.HKLM.name(), key.getRoot().name(), "all key root");
        assertEquals(3, key.getPathParts().size(), "all parts size");
        assertEquals("values", key.getPathParts().get(key.getPathParts().size() - 1), "all part");
        assertEquals(23, key.getValues().size(), "all values size");
        assertEquals("", key.getValues().get(0).getName(), "all value name");
        assertEquals(RegFileValueType.REG_SZ.name(), key.getValues().get(0).getType().name(), "all value type");
        assertEquals("test1", key.getValues().get(0).getValue(), "all value");
    }

    private static List<RegFileKey> parseResource(String path) throws RegFileParseException {
        Path fullPath = Path.of(System.getProperty("test.src")).resolve(path);
        try (InputStream is = new FileInputStream(fullPath.toFile())) {
            Reader reader = new InputStreamReader(is, StandardCharsets.UTF_16);
            Parser parser = new Parser(reader);
            return parser.parse();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
