/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6928542
 * @summary Verify RTFEditorKit.read() with fcharset
 */

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.rtf.RTFEditorKit;

import static java.nio.charset.StandardCharsets.ISO_8859_1;

public class RTFReadFontCharsetTest {
    public static void main(String[] args) throws Exception {
        String s =
            "{\\rtf1\\fbidis\\ansi\\ansicpg932\\deff0\\nouicomp" +
            "at\\deflang1033\\deflangfe1041{\\fonttbl{\\f0\\fni" +
            "l\\fcharset0 Segoe UI;}{\\f1\\fnil\\fcharset128 Yu" +
            " Gothic UI;}{\\f2\\fswiss\\fprq2\\fcharset129 Malg" +
            "un Gothic;}{\\f3\\fnil\\fcharset134 Microsoft YaHe" +
            "i;}{\\f4\\fnil\\fcharset136 Microsoft JhengHei;}{\\" +
            "f5\\fnil\\fcharset161 Segoe UI;}{\\f6\\fnil\\fcha" +
            "rset162 Segoe UI;}{\\f7\\fnil\\fcharset163 Segoe U" +
            "I;}{\\f8\\fnil\\fcharset177 Segoe UI;}{\\f9\\fnil\\" +
            "fcharset178 Segoe UI;}{\\f10\\fnil\\fcharset186 S" +
            "egoe UI;}{\\f11\\fnil\\fcharset204 Segoe UI;}{\\f1" +
            "2\\fnil\\fcharset222 Leelawadee UI;}{\\f13\\fnil\\" +
            "fcharset0 Leelawadee UI;}{\\f14\\fnil\\fcharset238" +
            " Segoe UI;}}\r\n{\\*\\generator Riched20 10.0.1904" +
            "1}\\viewkind4\\uc1 \r\n\\pard\\ltrpar\\nowidctlpar" +
            "\\sa200\\sl276\\slmult1\\f0\\fs22\\lang1041 Gr\\'f" +
            "cezi -  Switzerland 0\\line\\f1\\'82\\'b1\\'82\\'f" +
            "1\\'82\\'c9\\'82\\'bf\\'82\\'cd - Japanese 128\\li" +
            "ne\\f2\\lang17\\'be\\'c8\\'b3\\'e7\\'c7\\'cf\\'bc\\" +
            "'bc\\'bf\\'e4\\lang1041  - Korean 129\\line\\kern" +
            "ing2\\f3\\lang1033\\'c4\\'e3\\'ba\\'c3 - China 134" +
            "\\line\\f4\\'bb\\'4f\\'c6\\'57 - Traditional Chine" +
            "se - Taiwan 136\\line\\kerning0\\f5\\lang17\\'e3\\" +
            "'e5\\'e9\\'e1 \\'f3\\'ef\\'f5 - Greek\\f0\\lang104" +
            "1  161\\line\\f6\\lang17 A\\'f0a\\'e7 - \\f0 Turki" +
            "sh (Tree) 162\\line\\f7\\'fe\\f0\\lang1041  \\lang" +
            "1033 - \\lang17 Vietnam currency\\lang1041  163\\l" +
            "ine\\f8\\rtlch\\lang17\\'f9\\'c8\\'d1\\'ec\\'e5\\'" +
            "c9\\'ed\\f0\\ltrch  - Hebrew 177\\line\\f9\\rtlch\\" +
            "lang1025\\'e3\\'d1\\'cd\\'c8\\'c7\\f0\\ltrch\\lan" +
            "g17  - Arabic 178\\line\\kerning2\\f10\\lang1033 A" +
            "\\'e8i\\'fb - Lithuanian (Thank you) 186\\kerning0" +
            "\\f0\\lang1041\\line\\kerning2\\f11\\lang1049\\'c7" +
            "\\'e4\\'f0\\'e0\\'e2\\'f1\\'f2\\'e2\\'f3\\'e9\\'f2" +
            "\\'e5\\f0\\lang1033  - Russian 204\\line\\kerning0" +
            "\\f12\\lang1054\\'ca\\'c7\\'d1\\'ca\\'b4\\'d5 \\f1" +
            "3\\lang1033 - Thailand 222\\line\\kerning2\\f14 cz" +
            "e\\'9c\\'e6 - Polish 238\\par\r\n}\r\n\u0000";
        String expected =
            "Grüezi -  Switzerland 0\n" +
            "こんにちは - Japanese 128\n" +
            "안녕하세요 - Korean 129\n" +
            "你好 - China 134\n" +
            "臺灣 - Traditional Chinese - Taiwan 136\n" +
            "γεια σου - Greek 161\n" +
            "Ağaç - Turkish (Tree) 162\n" +
            "₫ - Vietnam currency 163\n" +
            "שָׁלוֹם - Hebrew 177\n" +
            "مرحبا - Arabic 178\n" +
            "Ačiū - Lithuanian (Thank you) 186\n" +
            "Здравст" +
            "вуйте - Russian 204\n" +
            "สวัสดี - Thailand 222\n" +
            "cześć - Polish 238\n" +
            "\n";
        ByteArrayInputStream bais = new ByteArrayInputStream(
            s.getBytes(ISO_8859_1));
        InputStreamReader isr = new InputStreamReader(bais, ISO_8859_1);
        RTFEditorKit kit = new RTFEditorKit();
        Document doc = kit.createDefaultDocument();
        kit.read(isr, doc, 0);
        Element elem = doc.getDefaultRootElement();
        int elemStart = elem.getStartOffset();
        int elemEnd = elem.getEndOffset();
        String text = doc.getText(elemStart, elemEnd - elemStart);
        if (!expected.equals(text)) {
            System.err.println("Read data");
            System.err.println("=========");
            dump(text, System.err);
            System.err.println("Expected data");
            System.err.println("=============");
            dump(expected, System.err);
            throw new RuntimeException("Test failed");
        }
    }

    private static void dump(String s, PrintStream ps) {
        for(char ch : s.toCharArray()) {
            if (ch == '\\')
                ps.print("\\\\");
            else if (ch >= 0x20 && ch <= 0x7e)
                ps.print(ch);
            else if (ch == '\n')
                ps.println();
            else
                ps.printf("\\u%04x", (int)ch);
        }
    }

}
