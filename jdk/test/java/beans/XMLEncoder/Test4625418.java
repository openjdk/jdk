/*
 * Copyright (c) 2006, 2007, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4625418
 * @summary Tests XML <a href="http://download.java.net/jdk6/docs/technotes/guides/intl/encoding.doc.html">encoding</a>
 * @author Sergey Malenkov
 *
 * @run main Test4625418 ASCII
 * @run main Test4625418 Big5
 * ?run main Test4625418 Big5-HKSCS
 * ?run main Test4625418 Big5_HKSCS
 * @run main Test4625418 Big5_Solaris
 * ?run main Test4625418 Cp037
 * @run main Test4625418 Cp1006
 * ?run main Test4625418 Cp1025
 * -run main Test4625418 Cp1026
 * @run main Test4625418 Cp1046
 * @run main Test4625418 Cp1047
 * @run main Test4625418 Cp1097
 * @run main Test4625418 Cp1098
 * ?run main Test4625418 Cp1112
 * ?run main Test4625418 Cp1122
 * ?run main Test4625418 Cp1123
 * @run main Test4625418 Cp1124
 * ?run main Test4625418 Cp1140
 * ?run main Test4625418 Cp1141
 * ?run main Test4625418 Cp1142
 * ?run main Test4625418 Cp1143
 * ?run main Test4625418 Cp1144
 * ?run main Test4625418 Cp1145
 * ?run main Test4625418 Cp1146
 * ?run main Test4625418 Cp1147
 * ?run main Test4625418 Cp1148
 * ?run main Test4625418 Cp1149
 * @run main Test4625418 Cp1250
 * @run main Test4625418 Cp1251
 * @run main Test4625418 Cp1252
 * @run main Test4625418 Cp1253
 * @run main Test4625418 Cp1254
 * @run main Test4625418 Cp1255
 * @run main Test4625418 Cp1256
 * @run main Test4625418 Cp1257
 * @run main Test4625418 Cp1258
 * ?run main Test4625418 Cp1381
 * ?run main Test4625418 Cp1383
 * ?run main Test4625418 Cp273
 * ?run main Test4625418 Cp277
 * ?run main Test4625418 Cp278
 * ?run main Test4625418 Cp280
 * ?run main Test4625418 Cp284
 * ?run main Test4625418 Cp285
 * ?run main Test4625418 Cp297
 * ?run main Test4625418 Cp33722
 * ?run main Test4625418 Cp420
 * ?run main Test4625418 Cp424
 * @run main Test4625418 Cp437
 * ?run main Test4625418 Cp500
 * ?run main Test4625418 Cp50220
 * ?run main Test4625418 Cp50221
 * @run main Test4625418 Cp737
 * @run main Test4625418 Cp775
 * -run main Test4625418 Cp834
 * ?run main Test4625418 Cp838
 * @run main Test4625418 Cp850
 * @run main Test4625418 Cp852
 * @run main Test4625418 Cp855
 * @run main Test4625418 Cp856
 * @run main Test4625418 Cp857
 * @run main Test4625418 Cp858
 * @run main Test4625418 Cp860
 * @run main Test4625418 Cp861
 * @run main Test4625418 Cp862
 * @run main Test4625418 Cp863
 * @run main Test4625418 Cp864
 * @run main Test4625418 Cp865
 * @run main Test4625418 Cp866
 * @run main Test4625418 Cp868
 * @run main Test4625418 Cp869
 * ?run main Test4625418 Cp870
 * ?run main Test4625418 Cp871
 * @run main Test4625418 Cp874
 * ?run main Test4625418 Cp875
 * ?run main Test4625418 Cp918
 * @run main Test4625418 Cp921
 * @run main Test4625418 Cp922
 * -run main Test4625418 Cp930
 * @run main Test4625418 Cp933
 * ?run main Test4625418 Cp935
 * ?run main Test4625418 Cp937
 * ?run main Test4625418 Cp939
 * ?run main Test4625418 Cp942
 * ?run main Test4625418 Cp942C
 * @run main Test4625418 Cp943
 * ?run main Test4625418 Cp943C
 * @run main Test4625418 Cp948
 * @run main Test4625418 Cp949
 * ?run main Test4625418 Cp949C
 * @run main Test4625418 Cp950
 * @run main Test4625418 Cp964
 * ?run main Test4625418 Cp970
 * ?run main Test4625418 EUC-JP
 * @run main Test4625418 EUC-KR
 * @run main Test4625418 EUC_CN
 * ?run main Test4625418 EUC_JP
 * ?run main Test4625418 EUC_JP_LINUX
 * ?run main Test4625418 EUC_JP_Solaris
 * @run main Test4625418 EUC_KR
 * ?run main Test4625418 EUC_TW
 * @run main Test4625418 GB18030
 * @run main Test4625418 GB2312
 * @run main Test4625418 GBK
 * ?run main Test4625418 IBM-Thai
 * @run main Test4625418 IBM00858
 * ?run main Test4625418 IBM01140
 * ?run main Test4625418 IBM01141
 * ?run main Test4625418 IBM01142
 * ?run main Test4625418 IBM01143
 * ?run main Test4625418 IBM01144
 * ?run main Test4625418 IBM01145
 * ?run main Test4625418 IBM01146
 * ?run main Test4625418 IBM01147
 * ?run main Test4625418 IBM01148
 * ?run main Test4625418 IBM01149
 * ?run main Test4625418 IBM037
 * -run main Test4625418 IBM1026
 * @run main Test4625418 IBM1047
 * ?run main Test4625418 IBM273
 * ?run main Test4625418 IBM277
 * ?run main Test4625418 IBM278
 * ?run main Test4625418 IBM280
 * ?run main Test4625418 IBM284
 * ?run main Test4625418 IBM285
 * ?run main Test4625418 IBM297
 * ?run main Test4625418 IBM420
 * ?run main Test4625418 IBM424
 * @run main Test4625418 IBM437
 * ?run main Test4625418 IBM500
 * @run main Test4625418 IBM775
 * @run main Test4625418 IBM850
 * @run main Test4625418 IBM852
 * @run main Test4625418 IBM855
 * @run main Test4625418 IBM857
 * @run main Test4625418 IBM860
 * @run main Test4625418 IBM861
 * @run main Test4625418 IBM862
 * @run main Test4625418 IBM863
 * @run main Test4625418 IBM864
 * @run main Test4625418 IBM865
 * @run main Test4625418 IBM866
 * @run main Test4625418 IBM868
 * @run main Test4625418 IBM869
 * ?run main Test4625418 IBM870
 * ?run main Test4625418 IBM871
 * ?run main Test4625418 IBM918
 * ?run main Test4625418 ISCII91
 * -run main Test4625418 ISO-2022-CN
 * @run main Test4625418 ISO-2022-JP
 * @run main Test4625418 ISO-2022-KR
 * @run main Test4625418 ISO-8859-1
 * @run main Test4625418 ISO-8859-13
 * @run main Test4625418 ISO-8859-15
 * @run main Test4625418 ISO-8859-2
 * @run main Test4625418 ISO-8859-3
 * @run main Test4625418 ISO-8859-4
 * @run main Test4625418 ISO-8859-5
 * @run main Test4625418 ISO-8859-6
 * @run main Test4625418 ISO-8859-7
 * @run main Test4625418 ISO-8859-8
 * @run main Test4625418 ISO-8859-9
 * -run main Test4625418 ISO2022CN
 * @run main Test4625418 ISO2022JP
 * @run main Test4625418 ISO2022KR
 * -run main Test4625418 ISO2022_CN_CNS
 * -run main Test4625418 ISO2022_CN_GB
 * @run main Test4625418 ISO8859_1
 * @run main Test4625418 ISO8859_13
 * @run main Test4625418 ISO8859_15
 * @run main Test4625418 ISO8859_2
 * @run main Test4625418 ISO8859_3
 * @run main Test4625418 ISO8859_4
 * @run main Test4625418 ISO8859_5
 * @run main Test4625418 ISO8859_6
 * @run main Test4625418 ISO8859_7
 * @run main Test4625418 ISO8859_8
 * @run main Test4625418 ISO8859_9
 * -run main Test4625418 JISAutoDetect
 * ?run main Test4625418 JIS_X0201
 * -run main Test4625418 JIS_X0212-1990
 * @run main Test4625418 KOI8-R
 * @run main Test4625418 KOI8-U
 * @run main Test4625418 KOI8_R
 * @run main Test4625418 KOI8_U
 * @run main Test4625418 MS874
 * ?run main Test4625418 MS932
 * ?run main Test4625418 MS936
 * @run main Test4625418 MS949
 * @run main Test4625418 MS950
 * ?run main Test4625418 MS950_HKSCS
 * @run main Test4625418 MacArabic
 * @run main Test4625418 MacCentralEurope
 * @run main Test4625418 MacCroatian
 * @run main Test4625418 MacCyrillic
 * -run main Test4625418 MacDingbat
 * @run main Test4625418 MacGreek
 * @run main Test4625418 MacHebrew
 * @run main Test4625418 MacIceland
 * @run main Test4625418 MacRoman
 * @run main Test4625418 MacRomania
 * -run main Test4625418 MacSymbol
 * @run main Test4625418 MacThai
 * @run main Test4625418 MacTurkish
 * @run main Test4625418 MacUkraine
 * ?run main Test4625418 PCK
 * ?run main Test4625418 SJIS
 * ?run main Test4625418 Shift_JIS
 * @run main Test4625418 TIS-620
 * @run main Test4625418 TIS620
 * @run main Test4625418 US-ASCII
 * @run main Test4625418 UTF-16
 * @run main Test4625418 UTF-16BE
 * @run main Test4625418 UTF-16LE
 * @run main Test4625418 UTF-32
 * @run main Test4625418 UTF-32BE
 * @run main Test4625418 UTF-32LE
 * @run main Test4625418 UTF-8
 * @run main Test4625418 UTF8
 * @run main Test4625418 UTF_32
 * @run main Test4625418 UTF_32BE
 * -run main Test4625418 UTF_32BE_BOM
 * @run main Test4625418 UTF_32LE
 * -run main Test4625418 UTF_32LE_BOM
 * @run main Test4625418 UnicodeBig
 * @run main Test4625418 UnicodeBigUnmarked
 * @run main Test4625418 UnicodeLittle
 * @run main Test4625418 UnicodeLittleUnmarked
 * @run main Test4625418 windows-1250
 * @run main Test4625418 windows-1251
 * @run main Test4625418 windows-1252
 * @run main Test4625418 windows-1253
 * @run main Test4625418 windows-1254
 * @run main Test4625418 windows-1255
 * @run main Test4625418 windows-1256
 * @run main Test4625418 windows-1257
 * @run main Test4625418 windows-1258
 * ?run main Test4625418 windows-31j
 * -run main Test4625418 x-Big5_Solaris
 * ?run main Test4625418 x-EUC-TW
 * @run main Test4625418 x-IBM1006
 * ?run main Test4625418 x-IBM1025
 * @run main Test4625418 x-IBM1046
 * @run main Test4625418 x-IBM1097
 * @run main Test4625418 x-IBM1098
 * ?run main Test4625418 x-IBM1112
 * ?run main Test4625418 x-IBM1122
 * ?run main Test4625418 x-IBM1123
 * @run main Test4625418 x-IBM1124
 * ?run main Test4625418 x-IBM1381
 * ?run main Test4625418 x-IBM1383
 * ?run main Test4625418 x-IBM33722
 * @run main Test4625418 x-IBM737
 * -run main Test4625418 x-IBM834
 * @run main Test4625418 x-IBM856
 * @run main Test4625418 x-IBM874
 * ?run main Test4625418 x-IBM875
 * @run main Test4625418 x-IBM921
 * @run main Test4625418 x-IBM922
 * -run main Test4625418 x-IBM930
 * @run main Test4625418 x-IBM933
 * ?run main Test4625418 x-IBM935
 * ?run main Test4625418 x-IBM937
 * ?run main Test4625418 x-IBM939
 * ?run main Test4625418 x-IBM942
 * ?run main Test4625418 x-IBM942C
 * @run main Test4625418 x-IBM943
 * ?run main Test4625418 x-IBM943C
 * @run main Test4625418 x-IBM948
 * @run main Test4625418 x-IBM949
 * ?run main Test4625418 x-IBM949C
 * @run main Test4625418 x-IBM950
 * @run main Test4625418 x-IBM964
 * ?run main Test4625418 x-IBM970
 * ?run main Test4625418 x-ISCII91
 * -run main Test4625418 x-ISO2022-CN-CNS
 * -run main Test4625418 x-ISO2022-CN-GB
 * -run main Test4625418 x-JIS0208
 * -run main Test4625418 x-JISAutoDetect
 * @run main Test4625418 x-Johab
 * ?run main Test4625418 x-MS950-HKSCS
 * @run main Test4625418 x-MacArabic
 * @run main Test4625418 x-MacCentralEurope
 * @run main Test4625418 x-MacCroatian
 * @run main Test4625418 x-MacCyrillic
 * -run main Test4625418 x-MacDingbat
 * @run main Test4625418 x-MacGreek
 * @run main Test4625418 x-MacHebrew
 * @run main Test4625418 x-MacIceland
 * @run main Test4625418 x-MacRoman
 * @run main Test4625418 x-MacRomania
 * -run main Test4625418 x-MacSymbol
 * @run main Test4625418 x-MacThai
 * @run main Test4625418 x-MacTurkish
 * @run main Test4625418 x-MacUkraine
 * ?run main Test4625418 x-PCK
 * @run main Test4625418 x-UTF-16LE-BOM
 * -run main Test4625418 x-UTF-32BE-BOM
 * -run main Test4625418 x-UTF-32LE-BOM
 * ?run main Test4625418 x-euc-jp-linux
 * ?run main Test4625418 x-eucJP-Open
 * @run main Test4625418 x-iso-8859-11
 * @run main Test4625418 x-mswin-936
 * ?run main Test4625418 x-windows-50220
 * ?run main Test4625418 x-windows-50221
 * @run main Test4625418 x-windows-874
 * @run main Test4625418 x-windows-949
 * @run main Test4625418 x-windows-950
 * ?run main Test4625418 x-windows-iso2022jp
 */

import java.beans.ExceptionListener;
import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;

public final class Test4625418 implements ExceptionListener {
    public static void main(String[] args) {
        new Test4625418(args[0]).test(createString(0x10000));
        System.out.println("Test passed: " + args[0]);
    }

    private static String createString(int length) {
        StringBuilder sb = new StringBuilder(length);
        while (0 < length--)
            sb.append((char) length);

        return sb.toString();
    }

    private final String encoding;

    private Test4625418(String encoding) {
        this.encoding = encoding;
    }

    private void test(String string) {
        try {
            File file = new File("4625418." + this.encoding + ".xml");

            FileOutputStream output = new FileOutputStream(file);
            XMLEncoder encoder = new XMLEncoder(output, this.encoding, true, 0);
            encoder.setExceptionListener(this);
            encoder.writeObject(string);
            encoder.close();

            FileInputStream input = new FileInputStream(file);
            XMLDecoder decoder = new XMLDecoder(input);
            decoder.setExceptionListener(this);
            Object object = decoder.readObject();
            decoder.close();

            if (!string.equals(object))
                throw new Error(this.encoding + " - can't read properly");

            file.delete();
        }
        catch (FileNotFoundException exception) {
            throw new Error(this.encoding + " - file not found", exception);
        }
        catch (IllegalCharsetNameException exception) {
            throw new Error(this.encoding + " - illegal charset name", exception);
        }
        catch (UnsupportedCharsetException exception) {
            throw new Error(this.encoding + " - unsupported charset", exception);
        }
        catch (UnsupportedOperationException exception) {
            throw new Error(this.encoding + " - unsupported encoder", exception);
        }
    }

    public void exceptionThrown(Exception exception) {
        throw new Error(this.encoding + " - internal", exception);
    }
}
