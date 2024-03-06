/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, Alibaba Group Holding Limited. All rights reserved.
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
 * @bug 0000000
 * @summary Exercise format builder.
 * @enablePreview true
 */

import java.time.*;
import java.util.FormatProcessor;
import java.util.Objects;
import java.util.Date;
import java.util.Locale;
import java.util.MissingFormatArgumentException;
import java.util.UnknownFormatConversionException;

import static java.util.FormatProcessor.FMT;

public class FormatterBuilder {
    public static void main(String... args) {
        Locale.setDefault(Locale.US);
        suite(FMT);
        suiteDateTimes(FMT);
        Locale thai = Locale.forLanguageTag("th-TH-u-nu-thai");
        FormatProcessor thaiFormat = FormatProcessor.create(thai);
        Locale.setDefault(thai);
        suite(thaiFormat);
        suiteDateTimes(thaiFormat);
    }

    static void test(String a, String b) {
        if (!Objects.equals(a, b)) {
            throw new RuntimeException("format and FMT do not match: " + a + " : " + b);
        }
    }

    public interface Executable {
        void execute() throws Throwable;
    }

    static <T extends Throwable> void assertThrows(Class<T> expectedType, Executable executable, String message) {
        Throwable actualException = null;
        try {
            executable.execute();
        } catch (Throwable e) {
            actualException = e;
        }
        if (actualException == null) {
            throw new RuntimeException("Expected " + expectedType + " to be thrown, but nothing was thrown.");
        }
        if (!expectedType.isInstance(actualException)) {
            throw new RuntimeException("Expected " + expectedType + " to be thrown, but was thrown " + actualException.getClass());
        }
        if (message != null && !message.equals(actualException.getMessage())) {
            throw new RuntimeException("Expected " + message + " to be thrown, but was thrown " + actualException.getMessage());
        }
    }

    static void suite(FormatProcessor fmt) {
        Object nullObject = null;
        test(String.format("%b", false), fmt."%b\{false}");
        test(String.format("%b", true), fmt."%b\{true}");
        test(String.format("%10b", false), fmt."%10b\{false}");
        test(String.format("%10b", true), fmt."%10b\{true}");
        test(String.format("%-10b", false), fmt."%-10b\{false}");
        test(String.format("%-10b", true), fmt."%-10b\{true}");
        test(String.format("%B", false), fmt."%B\{false}");
        test(String.format("%B", true), fmt."%B\{true}");
        test(String.format("%10B", false), fmt."%10B\{false}");
        test(String.format("%10B", true), fmt."%10B\{true}");
        test(String.format("%-10B", false), fmt."%-10B\{false}");
        test(String.format("%-10B", true), fmt."%-10B\{true}");

        test(String.format("%h", 12345), fmt."%h\{12345}");
        test(String.format("%h", 0xABCDE), fmt."%h\{0xABCDE}");
        test(String.format("%10h", 12345), fmt."%10h\{12345}");
        test(String.format("%10h", 0xABCDE), fmt."%10h\{0xABCDE}");
        test(String.format("%-10h", 12345), fmt."%-10h\{12345}");
        test(String.format("%-10h", 0xABCDE), fmt."%-10h\{0xABCDE}");
        test(String.format("%H", 12345), fmt."%H\{12345}");
        test(String.format("%H", 0xABCDE), fmt."%H\{0xABCDE}");
        test(String.format("%10H", 12345), fmt."%10H\{12345}");
        test(String.format("%10H", 0xABCDE), fmt."%10H\{0xABCDE}");
        test(String.format("%-10H", 12345), fmt."%-10H\{12345}");
        test(String.format("%-10H", 0xABCDE), fmt."%-10H\{0xABCDE}");

        test(String.format("%s", (byte)0xFF), fmt."%s\{(byte)0xFF}");
        test(String.format("%s", (short)0xFFFF), fmt."%s\{(short)0xFFFF}");
        test(String.format("%s", 12345), fmt."%s\{12345}");
        test(String.format("%s", 12345L), fmt."%s\{12345L}");
        test(String.format("%s", 1.33f), fmt."%s\{1.33f}");
        test(String.format("%s", 1.33), fmt."%s\{1.33}");
        test(String.format("%s", "abcde"), fmt."%s\{"abcde"}");
        test(String.format("%s", nullObject), fmt."%s\{nullObject}");
        test(String.format("%10s", (byte)0xFF), fmt."%10s\{(byte)0xFF}");
        test(String.format("%10s", (short)0xFFFF), fmt."%10s\{(short)0xFFFF}");
        test(String.format("%10s", 12345), fmt."%10s\{12345}");
        test(String.format("%10s", 12345L), fmt."%10s\{12345L}");
        test(String.format("%10s", 1.33f), fmt."%10s\{1.33f}");
        test(String.format("%10s", 1.33), fmt."%10s\{1.33}");
        test(String.format("%10s", "abcde"), fmt."%10s\{"abcde"}");
        test(String.format("%10s", nullObject), fmt."%10s\{nullObject}");
        test(String.format("%-10s", (byte)0xFF), fmt."%-10s\{(byte)0xFF}");
        test(String.format("%-10s", (short)0xFFFF), fmt."%-10s\{(short)0xFFFF}");
        test(String.format("%-10s", 12345), fmt."%-10s\{12345}");
        test(String.format("%-10s", 12345L), fmt."%-10s\{12345L}");
        test(String.format("%-10s", 1.33f), fmt."%-10s\{1.33f}");
        test(String.format("%-10s", 1.33), fmt."%-10s\{1.33}");
        test(String.format("%-10s", "abcde"), fmt."%-10s\{"abcde"}");
        test(String.format("%-10s", nullObject), fmt."%-10s\{nullObject}");
        test(String.format("%S", (byte)0xFF), fmt."%S\{(byte)0xFF}");
        test(String.format("%S", (short)0xFFFF), fmt."%S\{(short)0xFFFF}");
        test(String.format("%S", 12345), fmt."%S\{12345}");
        test(String.format("%S", 12345L), fmt."%S\{12345L}");
        test(String.format("%S", 1.33f), fmt."%S\{1.33f}");
        test(String.format("%S", 1.33), fmt."%S\{1.33}");
        test(String.format("%S", "abcde"), fmt."%S\{"abcde"}");
        test(String.format("%S", nullObject), fmt."%S\{nullObject}");
        test(String.format("%10S", (byte)0xFF), fmt."%10S\{(byte)0xFF}");
        test(String.format("%10S", (short)0xFFFF), fmt."%10S\{(short)0xFFFF}");
        test(String.format("%10S", 12345), fmt."%10S\{12345}");
        test(String.format("%10S", 12345L), fmt."%10S\{12345L}");
        test(String.format("%10S", 1.33f), fmt."%10S\{1.33f}");
        test(String.format("%10S", 1.33), fmt."%10S\{1.33}");
        test(String.format("%10S", "abcde"), fmt."%10S\{"abcde"}");
        test(String.format("%10S", nullObject), fmt."%10S\{nullObject}");
        test(String.format("%-10S", (byte)0xFF), fmt."%-10S\{(byte)0xFF}");
        test(String.format("%-10S", (short)0xFFFF), fmt."%-10S\{(short)0xFFFF}");
        test(String.format("%-10S", 12345), fmt."%-10S\{12345}");
        test(String.format("%-10S", 12345L), fmt."%-10S\{12345L}");
        test(String.format("%-10S", 1.33f), fmt."%-10S\{1.33f}");
        test(String.format("%-10S", 1.33), fmt."%-10S\{1.33}");
        test(String.format("%-10S", "abcde"), fmt."%-10S\{"abcde"}");
        test(String.format("%-10S", nullObject), fmt."%-10S\{nullObject}");

        test(String.format("%c", 'a'), fmt."%c\{'a'}");
        test(String.format("%10c", 'a'), fmt."%10c\{'a'}");
        test(String.format("%-10c", 'a'), fmt."%-10c\{'a'}");
        test(String.format("%C", 'a'), fmt."%C\{'a'}");
        test(String.format("%10C", 'a'), fmt."%10C\{'a'}");
        test(String.format("%-10C", 'a'), fmt."%-10C\{'a'}");

        test(String.format("%d", -12345), fmt."%d\{-12345}");
        test(String.format("%d", 0), fmt."%d\{0}");
        test(String.format("%d", 12345), fmt."%d\{12345}");
        test(String.format("%10d", -12345), fmt."%10d\{-12345}");
        test(String.format("%10d", 0), fmt."%10d\{0}");
        test(String.format("%10d", 12345), fmt."%10d\{12345}");
        test(String.format("%-10d", -12345), fmt."%-10d\{-12345}");
        test(String.format("%-10d", 0), fmt."%-10d\{0}");
        test(String.format("%-10d", 12345), fmt."%-10d\{12345}");
        test(String.format("%,d", -12345), fmt."%,d\{-12345}");
        test(String.format("%,d", 0), fmt."%,d\{0}");
        test(String.format("%,d", 12345), fmt."%,d\{12345}");
        test(String.format("%,10d", -12345), fmt."%,10d\{-12345}");
        test(String.format("%,10d", 0), fmt."%,10d\{0}");
        test(String.format("%,10d", 12345), fmt."%,10d\{12345}");
        test(String.format("%,-10d", -12345), fmt."%,-10d\{-12345}");
        test(String.format("%,-10d", 0), fmt."%,-10d\{0}");
        test(String.format("%,-10d", 12345), fmt."%,-10d\{12345}");
        test(String.format("%010d", -12345), fmt."%010d\{-12345}");
        test(String.format("%010d", 0), fmt."%010d\{0}");
        test(String.format("%010d", 12345), fmt."%010d\{12345}");
        test(String.format("%,010d", -12345), fmt."%,010d\{-12345}");
        test(String.format("%,010d", 0), fmt."%,010d\{0}");
        test(String.format("%,010d", 12345), fmt."%,010d\{12345}");

        test(String.format("%d", -12345), fmt."%d\{-12345}");
        test(String.format("%d", 0), fmt."%d\{0}");
        test(String.format("%d", 12345), fmt."%d\{12345}");
        test(String.format("%10d", -12345), fmt."%10d\{-12345}");
        test(String.format("%10d", 0), fmt."%10d\{0}");
        test(String.format("%10d", 12345), fmt."%10d\{12345}");
        test(String.format("%-10d", -12345), fmt."%-10d\{-12345}");
        test(String.format("%-10d", 0), fmt."%-10d\{0}");
        test(String.format("%-10d", 12345), fmt."%-10d\{12345}");
        test(String.format("%,d", -12345), fmt."%,d\{-12345}");
        test(String.format("%,d", 0), fmt."%,d\{0}");
        test(String.format("%,d", 12345), fmt."%,d\{12345}");
        test(String.format("%,10d", -12345), fmt."%,10d\{-12345}");
        test(String.format("%,10d", 0), fmt."%,10d\{0}");
        test(String.format("%,10d", 12345), fmt."%,10d\{12345}");
        test(String.format("%,-10d", -12345), fmt."%,-10d\{-12345}");
        test(String.format("%,-10d", 0), fmt."%,-10d\{0}");
        test(String.format("%,-10d", 12345), fmt."%,-10d\{12345}");
        test(String.format("% d", -12345), fmt."% d\{-12345}");
        test(String.format("% d", 0), fmt."% d\{0}");
        test(String.format("% d", 12345), fmt."% d\{12345}");
        test(String.format("% 10d", -12345), fmt."% 10d\{-12345}");
        test(String.format("% 10d", 0), fmt."% 10d\{0}");
        test(String.format("% 10d", 12345), fmt."% 10d\{12345}");
        test(String.format("% -10d", -12345), fmt."% -10d\{-12345}");
        test(String.format("% -10d", 0), fmt."% -10d\{0}");
        test(String.format("% -10d", 12345), fmt."% -10d\{12345}");
        test(String.format("%, d", -12345), fmt."%, d\{-12345}");
        test(String.format("%, d", 0), fmt."%, d\{0}");
        test(String.format("%, d", 12345), fmt."%, d\{12345}");
        test(String.format("%, 10d", -12345), fmt."%, 10d\{-12345}");
        test(String.format("%, 10d", 0), fmt."%, 10d\{0}");
        test(String.format("%, 10d", 12345), fmt."%, 10d\{12345}");
        test(String.format("%, -10d", -12345), fmt."%, -10d\{-12345}");
        test(String.format("%, -10d", 0), fmt."%, -10d\{0}");
        test(String.format("%, -10d", 12345), fmt."%, -10d\{12345}");
        test(String.format("%010d", -12345), fmt."%010d\{-12345}");
        test(String.format("%010d", 0), fmt."%010d\{0}");
        test(String.format("%010d", 12345), fmt."%010d\{12345}");
        test(String.format("%,010d", -12345), fmt."%,010d\{-12345}");
        test(String.format("%,010d", 0), fmt."%,010d\{0}");
        test(String.format("%,010d", 12345), fmt."%,010d\{12345}");
        test(String.format("% 010d", -12345), fmt."% 010d\{-12345}");
        test(String.format("% 010d", 0), fmt."% 010d\{0}");
        test(String.format("% 010d", 12345), fmt."% 010d\{12345}");
        test(String.format("%, 010d", -12345), fmt."%, 010d\{-12345}");
        test(String.format("%, 010d", 0), fmt."%, 010d\{0}");
        test(String.format("%, 010d", 12345), fmt."%, 010d\{12345}");

        test(String.format("%d", -12345), fmt."%d\{-12345}");
        test(String.format("%d", 0), fmt."%d\{0}");
        test(String.format("%d", 12345), fmt."%d\{12345}");
        test(String.format("%10d", -12345), fmt."%10d\{-12345}");
        test(String.format("%10d", 0), fmt."%10d\{0}");
        test(String.format("%10d", 12345), fmt."%10d\{12345}");
        test(String.format("%-10d", -12345), fmt."%-10d\{-12345}");
        test(String.format("%-10d", 0), fmt."%-10d\{0}");
        test(String.format("%-10d", 12345), fmt."%-10d\{12345}");
        test(String.format("%,d", -12345), fmt."%,d\{-12345}");
        test(String.format("%,d", 0), fmt."%,d\{0}");
        test(String.format("%,d", 12345), fmt."%,d\{12345}");
        test(String.format("%,10d", -12345), fmt."%,10d\{-12345}");
        test(String.format("%,10d", 0), fmt."%,10d\{0}");
        test(String.format("%,10d", 12345), fmt."%,10d\{12345}");
        test(String.format("%,-10d", -12345), fmt."%,-10d\{-12345}");
        test(String.format("%,-10d", 0), fmt."%,-10d\{0}");
        test(String.format("%,-10d", 12345), fmt."%,-10d\{12345}");
        test(String.format("%+d", -12345), fmt."%+d\{-12345}");
        test(String.format("%+d", 0), fmt."%+d\{0}");
        test(String.format("%+d", 12345), fmt."%+d\{12345}");
        test(String.format("%+10d", -12345), fmt."%+10d\{-12345}");
        test(String.format("%+10d", 0), fmt."%+10d\{0}");
        test(String.format("%+10d", 12345), fmt."%+10d\{12345}");
        test(String.format("%+-10d", -12345), fmt."%+-10d\{-12345}");
        test(String.format("%+-10d", 0), fmt."%+-10d\{0}");
        test(String.format("%+-10d", 12345), fmt."%+-10d\{12345}");
        test(String.format("%,+d", -12345), fmt."%,+d\{-12345}");
        test(String.format("%,+d", 0), fmt."%,+d\{0}");
        test(String.format("%,+d", 12345), fmt."%,+d\{12345}");
        test(String.format("%,+10d", -12345), fmt."%,+10d\{-12345}");
        test(String.format("%,+10d", 0), fmt."%,+10d\{0}");
        test(String.format("%,+10d", 12345), fmt."%,+10d\{12345}");
        test(String.format("%,+-10d", -12345), fmt."%,+-10d\{-12345}");
        test(String.format("%,+-10d", 0), fmt."%,+-10d\{0}");
        test(String.format("%,+-10d", 12345), fmt."%,+-10d\{12345}");
        test(String.format("%010d", -12345), fmt."%010d\{-12345}");
        test(String.format("%010d", 0), fmt."%010d\{0}");
        test(String.format("%010d", 12345), fmt."%010d\{12345}");
        test(String.format("%,010d", -12345), fmt."%,010d\{-12345}");
        test(String.format("%,010d", 0), fmt."%,010d\{0}");
        test(String.format("%,010d", 12345), fmt."%,010d\{12345}");
        test(String.format("%+010d", -12345), fmt."%+010d\{-12345}");
        test(String.format("%+010d", 0), fmt."%+010d\{0}");
        test(String.format("%+010d", 12345), fmt."%+010d\{12345}");
        test(String.format("%,+010d", -12345), fmt."%,+010d\{-12345}");
        test(String.format("%,+010d", 0), fmt."%,+010d\{0}");
        test(String.format("%,+010d", 12345), fmt."%,+010d\{12345}");

        test(String.format("%d", -12345), fmt."%d\{-12345}");
        test(String.format("%d", 0), fmt."%d\{0}");
        test(String.format("%d", 12345), fmt."%d\{12345}");
        test(String.format("%10d", -12345), fmt."%10d\{-12345}");
        test(String.format("%10d", 0), fmt."%10d\{0}");
        test(String.format("%10d", 12345), fmt."%10d\{12345}");
        test(String.format("%-10d", -12345), fmt."%-10d\{-12345}");
        test(String.format("%-10d", 0), fmt."%-10d\{0}");
        test(String.format("%-10d", 12345), fmt."%-10d\{12345}");
        test(String.format("%,d", -12345), fmt."%,d\{-12345}");
        test(String.format("%,d", 0), fmt."%,d\{0}");
        test(String.format("%,d", 12345), fmt."%,d\{12345}");
        test(String.format("%,10d", -12345), fmt."%,10d\{-12345}");
        test(String.format("%,10d", 0), fmt."%,10d\{0}");
        test(String.format("%,10d", 12345), fmt."%,10d\{12345}");
        test(String.format("%,-10d", -12345), fmt."%,-10d\{-12345}");
        test(String.format("%,-10d", 0), fmt."%,-10d\{0}");
        test(String.format("%,-10d", 12345), fmt."%,-10d\{12345}");
        test(String.format("%(d", -12345), fmt."%(d\{-12345}");
        test(String.format("%(d", 0), fmt."%(d\{0}");
        test(String.format("%(d", 12345), fmt."%(d\{12345}");
        test(String.format("%(10d", -12345), fmt."%(10d\{-12345}");
        test(String.format("%(10d", 0), fmt."%(10d\{0}");
        test(String.format("%(10d", 12345), fmt."%(10d\{12345}");
        test(String.format("%(-10d", -12345), fmt."%(-10d\{-12345}");
        test(String.format("%(-10d", 0), fmt."%(-10d\{0}");
        test(String.format("%(-10d", 12345), fmt."%(-10d\{12345}");
        test(String.format("%,(d", -12345), fmt."%,(d\{-12345}");
        test(String.format("%,(d", 0), fmt."%,(d\{0}");
        test(String.format("%,(d", 12345), fmt."%,(d\{12345}");
        test(String.format("%,(10d", -12345), fmt."%,(10d\{-12345}");
        test(String.format("%,(10d", 0), fmt."%,(10d\{0}");
        test(String.format("%,(10d", 12345), fmt."%,(10d\{12345}");
        test(String.format("%,(-10d", -12345), fmt."%,(-10d\{-12345}");
        test(String.format("%,(-10d", 0), fmt."%,(-10d\{0}");
        test(String.format("%,(-10d", 12345), fmt."%,(-10d\{12345}");
        test(String.format("%010d", -12345), fmt."%010d\{-12345}");
        test(String.format("%010d", 0), fmt."%010d\{0}");
        test(String.format("%010d", 12345), fmt."%010d\{12345}");
        test(String.format("%,010d", -12345), fmt."%,010d\{-12345}");
        test(String.format("%,010d", 0), fmt."%,010d\{0}");
        test(String.format("%,010d", 12345), fmt."%,010d\{12345}");
        test(String.format("%(010d", -12345), fmt."%(010d\{-12345}");
        test(String.format("%(010d", 0), fmt."%(010d\{0}");
        test(String.format("%(010d", 12345), fmt."%(010d\{12345}");
        test(String.format("%,(010d", -12345), fmt."%,(010d\{-12345}");
        test(String.format("%,(010d", 0), fmt."%,(010d\{0}");
        test(String.format("%,(010d", 12345), fmt."%,(010d\{12345}");

        test(String.format("%o", -12345), fmt."%o\{-12345}");
        test(String.format("%o", 0), fmt."%o\{0}");
        test(String.format("%o", 12345), fmt."%o\{12345}");
        test(String.format("%10o", -12345), fmt."%10o\{-12345}");
        test(String.format("%10o", 0), fmt."%10o\{0}");
        test(String.format("%10o", 12345), fmt."%10o\{12345}");
        test(String.format("%-10o", -12345), fmt."%-10o\{-12345}");
        test(String.format("%-10o", 0), fmt."%-10o\{0}");
        test(String.format("%-10o", 12345), fmt."%-10o\{12345}");
        test(String.format("%#o", -12345), fmt."%#o\{-12345}");
        test(String.format("%#o", 0), fmt."%#o\{0}");
        test(String.format("%#o", 12345), fmt."%#o\{12345}");
        test(String.format("%#10o", -12345), fmt."%#10o\{-12345}");
        test(String.format("%#10o", 0), fmt."%#10o\{0}");
        test(String.format("%#10o", 12345), fmt."%#10o\{12345}");
        test(String.format("%#-10o", -12345), fmt."%#-10o\{-12345}");
        test(String.format("%#-10o", 0), fmt."%#-10o\{0}");
        test(String.format("%#-10o", 12345), fmt."%#-10o\{12345}");
        test(String.format("%010o", -12345), fmt."%010o\{-12345}");
        test(String.format("%010o", 0), fmt."%010o\{0}");
        test(String.format("%010o", 12345), fmt."%010o\{12345}");
        test(String.format("%#010o", -12345), fmt."%#010o\{-12345}");
        test(String.format("%#010o", 0), fmt."%#010o\{0}");
        test(String.format("%#010o", 12345), fmt."%#010o\{12345}");

        test(String.format("%x", -12345), fmt."%x\{-12345}");
        test(String.format("%x", 0), fmt."%x\{0}");
        test(String.format("%x", 12345), fmt."%x\{12345}");
        test(String.format("%10x", -12345), fmt."%10x\{-12345}");
        test(String.format("%10x", 0), fmt."%10x\{0}");
        test(String.format("%10x", 12345), fmt."%10x\{12345}");
        test(String.format("%-10x", -12345), fmt."%-10x\{-12345}");
        test(String.format("%-10x", 0), fmt."%-10x\{0}");
        test(String.format("%-10x", 12345), fmt."%-10x\{12345}");
        test(String.format("%X", -12345), fmt."%X\{-12345}");
        test(String.format("%X", 0), fmt."%X\{0}");
        test(String.format("%X", 12345), fmt."%X\{12345}");
        test(String.format("%10X", -12345), fmt."%10X\{-12345}");
        test(String.format("%10X", 0), fmt."%10X\{0}");
        test(String.format("%10X", 12345), fmt."%10X\{12345}");
        test(String.format("%-10X", -12345), fmt."%-10X\{-12345}");
        test(String.format("%-10X", 0), fmt."%-10X\{0}");
        test(String.format("%-10X", 12345), fmt."%-10X\{12345}");
        test(String.format("%#x", -12345), fmt."%#x\{-12345}");
        test(String.format("%#x", 0), fmt."%#x\{0}");
        test(String.format("%#x", 12345), fmt."%#x\{12345}");
        test(String.format("%#10x", -12345), fmt."%#10x\{-12345}");
        test(String.format("%#10x", 0), fmt."%#10x\{0}");
        test(String.format("%#10x", 12345), fmt."%#10x\{12345}");
        test(String.format("%#-10x", -12345), fmt."%#-10x\{-12345}");
        test(String.format("%#-10x", 0), fmt."%#-10x\{0}");
        test(String.format("%#-10x", 12345), fmt."%#-10x\{12345}");
        test(String.format("%#X", -12345), fmt."%#X\{-12345}");
        test(String.format("%#X", 0), fmt."%#X\{0}");
        test(String.format("%#X", 12345), fmt."%#X\{12345}");
        test(String.format("%#10X", -12345), fmt."%#10X\{-12345}");
        test(String.format("%#10X", 0), fmt."%#10X\{0}");
        test(String.format("%#10X", 12345), fmt."%#10X\{12345}");
        test(String.format("%#-10X", -12345), fmt."%#-10X\{-12345}");
        test(String.format("%#-10X", 0), fmt."%#-10X\{0}");
        test(String.format("%#-10X", 12345), fmt."%#-10X\{12345}");
        test(String.format("%010x", -12345), fmt."%010x\{-12345}");
        test(String.format("%010x", 0), fmt."%010x\{0}");
        test(String.format("%010x", 12345), fmt."%010x\{12345}");
        test(String.format("%010X", -12345), fmt."%010X\{-12345}");
        test(String.format("%010X", 0), fmt."%010X\{0}");
        test(String.format("%010X", 12345), fmt."%010X\{12345}");
        test(String.format("%#010x", -12345), fmt."%#010x\{-12345}");
        test(String.format("%#010x", 0), fmt."%#010x\{0}");
        test(String.format("%#010x", 12345), fmt."%#010x\{12345}");
        test(String.format("%#010X", -12345), fmt."%#010X\{-12345}");
        test(String.format("%#010X", 0), fmt."%#010X\{0}");
        test(String.format("%#010X", 12345), fmt."%#010X\{12345}");

        test(String.format("%f", -12345.6), fmt."%f\{-12345.6}");
        test(String.format("%f", 0.0), fmt."%f\{0.0}");
        test(String.format("%f", 12345.6), fmt."%f\{12345.6}");
        test(String.format("%10f", -12345.6), fmt."%10f\{-12345.6}");
        test(String.format("%10f", 0.0), fmt."%10f\{0.0}");
        test(String.format("%10f", 12345.6), fmt."%10f\{12345.6}");
        test(String.format("%-10f", -12345.6), fmt."%-10f\{-12345.6}");
        test(String.format("%-10f", 0.0), fmt."%-10f\{0.0}");
        test(String.format("%-10f", 12345.6), fmt."%-10f\{12345.6}");
        test(String.format("%,f", -12345.6), fmt."%,f\{-12345.6}");
        test(String.format("%,f", 0.0), fmt."%,f\{0.0}");
        test(String.format("%,f", 12345.6), fmt."%,f\{12345.6}");
        test(String.format("%,10f", -12345.6), fmt."%,10f\{-12345.6}");
        test(String.format("%,10f", 0.0), fmt."%,10f\{0.0}");
        test(String.format("%,10f", 12345.6), fmt."%,10f\{12345.6}");
        test(String.format("%,-10f", -12345.6), fmt."%,-10f\{-12345.6}");
        test(String.format("%,-10f", 0.0), fmt."%,-10f\{0.0}");
        test(String.format("%,-10f", 12345.6), fmt."%,-10f\{12345.6}");

        test(String.format("%f", -12345.6), fmt."%f\{-12345.6}");
        test(String.format("%f", 0.0), fmt."%f\{0.0}");
        test(String.format("%f", 12345.6), fmt."%f\{12345.6}");
        test(String.format("%10f", -12345.6), fmt."%10f\{-12345.6}");
        test(String.format("%10f", 0.0), fmt."%10f\{0.0}");
        test(String.format("%10f", 12345.6), fmt."%10f\{12345.6}");
        test(String.format("%-10f", -12345.6), fmt."%-10f\{-12345.6}");
        test(String.format("%-10f", 0.0), fmt."%-10f\{0.0}");
        test(String.format("%-10f", 12345.6), fmt."%-10f\{12345.6}");
        test(String.format("%,f", -12345.6), fmt."%,f\{-12345.6}");
        test(String.format("%,f", 0.0), fmt."%,f\{0.0}");
        test(String.format("%,f", 12345.6), fmt."%,f\{12345.6}");
        test(String.format("%,10f", -12345.6), fmt."%,10f\{-12345.6}");
        test(String.format("%,10f", 0.0), fmt."%,10f\{0.0}");
        test(String.format("%,10f", 12345.6), fmt."%,10f\{12345.6}");
        test(String.format("%,-10f", -12345.6), fmt."%,-10f\{-12345.6}");
        test(String.format("%,-10f", 0.0), fmt."%,-10f\{0.0}");
        test(String.format("%,-10f", 12345.6), fmt."%,-10f\{12345.6}");
        test(String.format("% f", -12345.6), fmt."% f\{-12345.6}");
        test(String.format("% f", 0.0), fmt."% f\{0.0}");
        test(String.format("% f", 12345.6), fmt."% f\{12345.6}");
        test(String.format("% 10f", -12345.6), fmt."% 10f\{-12345.6}");
        test(String.format("% 10f", 0.0), fmt."% 10f\{0.0}");
        test(String.format("% 10f", 12345.6), fmt."% 10f\{12345.6}");
        test(String.format("% -10f", -12345.6), fmt."% -10f\{-12345.6}");
        test(String.format("% -10f", 0.0), fmt."% -10f\{0.0}");
        test(String.format("% -10f", 12345.6), fmt."% -10f\{12345.6}");
        test(String.format("%, f", -12345.6), fmt."%, f\{-12345.6}");
        test(String.format("%, f", 0.0), fmt."%, f\{0.0}");
        test(String.format("%, f", 12345.6), fmt."%, f\{12345.6}");
        test(String.format("%, 10f", -12345.6), fmt."%, 10f\{-12345.6}");
        test(String.format("%, 10f", 0.0), fmt."%, 10f\{0.0}");
        test(String.format("%, 10f", 12345.6), fmt."%, 10f\{12345.6}");
        test(String.format("%, -10f", -12345.6), fmt."%, -10f\{-12345.6}");
        test(String.format("%, -10f", 0.0), fmt."%, -10f\{0.0}");
        test(String.format("%, -10f", 12345.6), fmt."%, -10f\{12345.6}");

        test(String.format("%f", -12345.6), fmt."%f\{-12345.6}");
        test(String.format("%f", 0.0), fmt."%f\{0.0}");
        test(String.format("%f", 12345.6), fmt."%f\{12345.6}");
        test(String.format("%10f", -12345.6), fmt."%10f\{-12345.6}");
        test(String.format("%10f", 0.0), fmt."%10f\{0.0}");
        test(String.format("%10f", 12345.6), fmt."%10f\{12345.6}");
        test(String.format("%-10f", -12345.6), fmt."%-10f\{-12345.6}");
        test(String.format("%-10f", 0.0), fmt."%-10f\{0.0}");
        test(String.format("%-10f", 12345.6), fmt."%-10f\{12345.6}");
        test(String.format("%,f", -12345.6), fmt."%,f\{-12345.6}");
        test(String.format("%,f", 0.0), fmt."%,f\{0.0}");
        test(String.format("%,f", 12345.6), fmt."%,f\{12345.6}");
        test(String.format("%,10f", -12345.6), fmt."%,10f\{-12345.6}");
        test(String.format("%,10f", 0.0), fmt."%,10f\{0.0}");
        test(String.format("%,10f", 12345.6), fmt."%,10f\{12345.6}");
        test(String.format("%,-10f", -12345.6), fmt."%,-10f\{-12345.6}");
        test(String.format("%,-10f", 0.0), fmt."%,-10f\{0.0}");
        test(String.format("%,-10f", 12345.6), fmt."%,-10f\{12345.6}");
        test(String.format("%+f", -12345.6), fmt."%+f\{-12345.6}");
        test(String.format("%+f", 0.0), fmt."%+f\{0.0}");
        test(String.format("%+f", 12345.6), fmt."%+f\{12345.6}");
        test(String.format("%+10f", -12345.6), fmt."%+10f\{-12345.6}");
        test(String.format("%+10f", 0.0), fmt."%+10f\{0.0}");
        test(String.format("%+10f", 12345.6), fmt."%+10f\{12345.6}");
        test(String.format("%+-10f", -12345.6), fmt."%+-10f\{-12345.6}");
        test(String.format("%+-10f", 0.0), fmt."%+-10f\{0.0}");
        test(String.format("%+-10f", 12345.6), fmt."%+-10f\{12345.6}");
        test(String.format("%,+f", -12345.6), fmt."%,+f\{-12345.6}");
        test(String.format("%,+f", 0.0), fmt."%,+f\{0.0}");
        test(String.format("%,+f", 12345.6), fmt."%,+f\{12345.6}");
        test(String.format("%,+10f", -12345.6), fmt."%,+10f\{-12345.6}");
        test(String.format("%,+10f", 0.0), fmt."%,+10f\{0.0}");
        test(String.format("%,+10f", 12345.6), fmt."%,+10f\{12345.6}");
        test(String.format("%,+-10f", -12345.6), fmt."%,+-10f\{-12345.6}");
        test(String.format("%,+-10f", 0.0), fmt."%,+-10f\{0.0}");
        test(String.format("%,+-10f", 12345.6), fmt."%,+-10f\{12345.6}");

        test(String.format("%f", -12345.6), fmt."%f\{-12345.6}");
        test(String.format("%f", 0.0), fmt."%f\{0.0}");
        test(String.format("%f", 12345.6), fmt."%f\{12345.6}");
        test(String.format("%10f", -12345.6), fmt."%10f\{-12345.6}");
        test(String.format("%10f", 0.0), fmt."%10f\{0.0}");
        test(String.format("%10f", 12345.6), fmt."%10f\{12345.6}");
        test(String.format("%-10f", -12345.6), fmt."%-10f\{-12345.6}");
        test(String.format("%-10f", 0.0), fmt."%-10f\{0.0}");
        test(String.format("%-10f", 12345.6), fmt."%-10f\{12345.6}");
        test(String.format("%,f", -12345.6), fmt."%,f\{-12345.6}");
        test(String.format("%,f", 0.0), fmt."%,f\{0.0}");
        test(String.format("%,f", 12345.6), fmt."%,f\{12345.6}");
        test(String.format("%,10f", -12345.6), fmt."%,10f\{-12345.6}");
        test(String.format("%,10f", 0.0), fmt."%,10f\{0.0}");
        test(String.format("%,10f", 12345.6), fmt."%,10f\{12345.6}");
        test(String.format("%,-10f", -12345.6), fmt."%,-10f\{-12345.6}");
        test(String.format("%,-10f", 0.0), fmt."%,-10f\{0.0}");
        test(String.format("%,-10f", 12345.6), fmt."%,-10f\{12345.6}");
        test(String.format("%(f", -12345.6), fmt."%(f\{-12345.6}");
        test(String.format("%(f", 0.0), fmt."%(f\{0.0}");
        test(String.format("%(f", 12345.6), fmt."%(f\{12345.6}");
        test(String.format("%(10f", -12345.6), fmt."%(10f\{-12345.6}");
        test(String.format("%(10f", 0.0), fmt."%(10f\{0.0}");
        test(String.format("%(10f", 12345.6), fmt."%(10f\{12345.6}");
        test(String.format("%(-10f", -12345.6), fmt."%(-10f\{-12345.6}");
        test(String.format("%(-10f", 0.0), fmt."%(-10f\{0.0}");
        test(String.format("%(-10f", 12345.6), fmt."%(-10f\{12345.6}");
        test(String.format("%,(f", -12345.6), fmt."%,(f\{-12345.6}");
        test(String.format("%,(f", 0.0), fmt."%,(f\{0.0}");
        test(String.format("%,(f", 12345.6), fmt."%,(f\{12345.6}");
        test(String.format("%,(10f", -12345.6), fmt."%,(10f\{-12345.6}");
        test(String.format("%,(10f", 0.0), fmt."%,(10f\{0.0}");
        test(String.format("%,(10f", 12345.6), fmt."%,(10f\{12345.6}");
        test(String.format("%,(-10f", -12345.6), fmt."%,(-10f\{-12345.6}");
        test(String.format("%,(-10f", 0.0), fmt."%,(-10f\{0.0}");
        test(String.format("%,(-10f", 12345.6), fmt."%,(-10f\{12345.6}");
        test(String.format("%+f", -12345.6), fmt."%+f\{-12345.6}");
        test(String.format("%+f", 0.0), fmt."%+f\{0.0}");
        test(String.format("%+f", 12345.6), fmt."%+f\{12345.6}");
        test(String.format("%+10f", -12345.6), fmt."%+10f\{-12345.6}");
        test(String.format("%+10f", 0.0), fmt."%+10f\{0.0}");
        test(String.format("%+10f", 12345.6), fmt."%+10f\{12345.6}");
        test(String.format("%+-10f", -12345.6), fmt."%+-10f\{-12345.6}");
        test(String.format("%+-10f", 0.0), fmt."%+-10f\{0.0}");
        test(String.format("%+-10f", 12345.6), fmt."%+-10f\{12345.6}");
        test(String.format("%,+f", -12345.6), fmt."%,+f\{-12345.6}");
        test(String.format("%,+f", 0.0), fmt."%,+f\{0.0}");
        test(String.format("%,+f", 12345.6), fmt."%,+f\{12345.6}");
        test(String.format("%,+10f", -12345.6), fmt."%,+10f\{-12345.6}");
        test(String.format("%,+10f", 0.0), fmt."%,+10f\{0.0}");
        test(String.format("%,+10f", 12345.6), fmt."%,+10f\{12345.6}");
        test(String.format("%,+-10f", -12345.6), fmt."%,+-10f\{-12345.6}");
        test(String.format("%,+-10f", 0.0), fmt."%,+-10f\{0.0}");
        test(String.format("%,+-10f", 12345.6), fmt."%,+-10f\{12345.6}");
        test(String.format("%(+f", -12345.6), fmt."%(+f\{-12345.6}");
        test(String.format("%(+f", 0.0), fmt."%(+f\{0.0}");
        test(String.format("%(+f", 12345.6), fmt."%(+f\{12345.6}");
        test(String.format("%(+10f", -12345.6), fmt."%(+10f\{-12345.6}");
        test(String.format("%(+10f", 0.0), fmt."%(+10f\{0.0}");
        test(String.format("%(+10f", 12345.6), fmt."%(+10f\{12345.6}");
        test(String.format("%(+-10f", -12345.6), fmt."%(+-10f\{-12345.6}");
        test(String.format("%(+-10f", 0.0), fmt."%(+-10f\{0.0}");
        test(String.format("%(+-10f", 12345.6), fmt."%(+-10f\{12345.6}");
        test(String.format("%,(+f", -12345.6), fmt."%,(+f\{-12345.6}");
        test(String.format("%,(+f", 0.0), fmt."%,(+f\{0.0}");
        test(String.format("%,(+f", 12345.6), fmt."%,(+f\{12345.6}");
        test(String.format("%,(+10f", -12345.6), fmt."%,(+10f\{-12345.6}");
        test(String.format("%,(+10f", 0.0), fmt."%,(+10f\{0.0}");
        test(String.format("%,(+10f", 12345.6), fmt."%,(+10f\{12345.6}");
        test(String.format("%,(+-10f", -12345.6), fmt."%,(+-10f\{-12345.6}");
        test(String.format("%,(+-10f", 0.0), fmt."%,(+-10f\{0.0}");
        test(String.format("%,(+-10f", 12345.6), fmt."%,(+-10f\{12345.6}");

        test(String.format("%e", -12345.6), fmt."%e\{-12345.6}");
        test(String.format("%e", 0.0), fmt."%e\{0.0}");
        test(String.format("%e", 12345.6), fmt."%e\{12345.6}");
        test(String.format("%10e", -12345.6), fmt."%10e\{-12345.6}");
        test(String.format("%10e", 0.0), fmt."%10e\{0.0}");
        test(String.format("%10e", 12345.6), fmt."%10e\{12345.6}");
        test(String.format("%-10e", -12345.6), fmt."%-10e\{-12345.6}");
        test(String.format("%-10e", 0.0), fmt."%-10e\{0.0}");
        test(String.format("%-10e", 12345.6), fmt."%-10e\{12345.6}");
        test(String.format("%E", -12345.6), fmt."%E\{-12345.6}");
        test(String.format("%E", 0.0), fmt."%E\{0.0}");
        test(String.format("%E", 12345.6), fmt."%E\{12345.6}");
        test(String.format("%10E", -12345.6), fmt."%10E\{-12345.6}");
        test(String.format("%10E", 0.0), fmt."%10E\{0.0}");
        test(String.format("%10E", 12345.6), fmt."%10E\{12345.6}");
        test(String.format("%-10E", -12345.6), fmt."%-10E\{-12345.6}");
        test(String.format("%-10E", 0.0), fmt."%-10E\{0.0}");
        test(String.format("%-10E", 12345.6), fmt."%-10E\{12345.6}");

        test(String.format("%g", -12345.6), fmt."%g\{-12345.6}");
        test(String.format("%g", 0.0), fmt."%g\{0.0}");
        test(String.format("%g", 12345.6), fmt."%g\{12345.6}");
        test(String.format("%10g", -12345.6), fmt."%10g\{-12345.6}");
        test(String.format("%10g", 0.0), fmt."%10g\{0.0}");
        test(String.format("%10g", 12345.6), fmt."%10g\{12345.6}");
        test(String.format("%-10g", -12345.6), fmt."%-10g\{-12345.6}");
        test(String.format("%-10g", 0.0), fmt."%-10g\{0.0}");
        test(String.format("%-10g", 12345.6), fmt."%-10g\{12345.6}");
        test(String.format("%G", -12345.6), fmt."%G\{-12345.6}");
        test(String.format("%G", 0.0), fmt."%G\{0.0}");
        test(String.format("%G", 12345.6), fmt."%G\{12345.6}");
        test(String.format("%10G", -12345.6), fmt."%10G\{-12345.6}");
        test(String.format("%10G", 0.0), fmt."%10G\{0.0}");
        test(String.format("%10G", 12345.6), fmt."%10G\{12345.6}");
        test(String.format("%-10G", -12345.6), fmt."%-10G\{-12345.6}");
        test(String.format("%-10G", 0.0), fmt."%-10G\{0.0}");
        test(String.format("%-10G", 12345.6), fmt."%-10G\{12345.6}");
        test(String.format("%,g", -12345.6), fmt."%,g\{-12345.6}");
        test(String.format("%,g", 0.0), fmt."%,g\{0.0}");
        test(String.format("%,g", 12345.6), fmt."%,g\{12345.6}");
        test(String.format("%,10g", -12345.6), fmt."%,10g\{-12345.6}");
        test(String.format("%,10g", 0.0), fmt."%,10g\{0.0}");
        test(String.format("%,10g", 12345.6), fmt."%,10g\{12345.6}");
        test(String.format("%,-10g", -12345.6), fmt."%,-10g\{-12345.6}");
        test(String.format("%,-10g", 0.0), fmt."%,-10g\{0.0}");
        test(String.format("%,-10g", 12345.6), fmt."%,-10g\{12345.6}");
        test(String.format("%,G", -12345.6), fmt."%,G\{-12345.6}");
        test(String.format("%,G", 0.0), fmt."%,G\{0.0}");
        test(String.format("%,G", 12345.6), fmt."%,G\{12345.6}");
        test(String.format("%,10G", -12345.6), fmt."%,10G\{-12345.6}");
        test(String.format("%,10G", 0.0), fmt."%,10G\{0.0}");
        test(String.format("%,10G", 12345.6), fmt."%,10G\{12345.6}");
        test(String.format("%,-10G", -12345.6), fmt."%,-10G\{-12345.6}");
        test(String.format("%,-10G", 0.0), fmt."%,-10G\{0.0}");
        test(String.format("%,-10G", 12345.6), fmt."%,-10G\{12345.6}");

        test(String.format("%g", -12345.6), fmt."%g\{-12345.6}");
        test(String.format("%g", 0.0), fmt."%g\{0.0}");
        test(String.format("%g", 12345.6), fmt."%g\{12345.6}");
        test(String.format("%10g", -12345.6), fmt."%10g\{-12345.6}");
        test(String.format("%10g", 0.0), fmt."%10g\{0.0}");
        test(String.format("%10g", 12345.6), fmt."%10g\{12345.6}");
        test(String.format("%-10g", -12345.6), fmt."%-10g\{-12345.6}");
        test(String.format("%-10g", 0.0), fmt."%-10g\{0.0}");
        test(String.format("%-10g", 12345.6), fmt."%-10g\{12345.6}");
        test(String.format("%G", -12345.6), fmt."%G\{-12345.6}");
        test(String.format("%G", 0.0), fmt."%G\{0.0}");
        test(String.format("%G", 12345.6), fmt."%G\{12345.6}");
        test(String.format("%10G", -12345.6), fmt."%10G\{-12345.6}");
        test(String.format("%10G", 0.0), fmt."%10G\{0.0}");
        test(String.format("%10G", 12345.6), fmt."%10G\{12345.6}");
        test(String.format("%-10G", -12345.6), fmt."%-10G\{-12345.6}");
        test(String.format("%-10G", 0.0), fmt."%-10G\{0.0}");
        test(String.format("%-10G", 12345.6), fmt."%-10G\{12345.6}");
        test(String.format("%,g", -12345.6), fmt."%,g\{-12345.6}");
        test(String.format("%,g", 0.0), fmt."%,g\{0.0}");
        test(String.format("%,g", 12345.6), fmt."%,g\{12345.6}");
        test(String.format("%,10g", -12345.6), fmt."%,10g\{-12345.6}");
        test(String.format("%,10g", 0.0), fmt."%,10g\{0.0}");
        test(String.format("%,10g", 12345.6), fmt."%,10g\{12345.6}");
        test(String.format("%,-10g", -12345.6), fmt."%,-10g\{-12345.6}");
        test(String.format("%,-10g", 0.0), fmt."%,-10g\{0.0}");
        test(String.format("%,-10g", 12345.6), fmt."%,-10g\{12345.6}");
        test(String.format("%,G", -12345.6), fmt."%,G\{-12345.6}");
        test(String.format("%,G", 0.0), fmt."%,G\{0.0}");
        test(String.format("%,G", 12345.6), fmt."%,G\{12345.6}");
        test(String.format("%,10G", -12345.6), fmt."%,10G\{-12345.6}");
        test(String.format("%,10G", 0.0), fmt."%,10G\{0.0}");
        test(String.format("%,10G", 12345.6), fmt."%,10G\{12345.6}");
        test(String.format("%,-10G", -12345.6), fmt."%,-10G\{-12345.6}");
        test(String.format("%,-10G", 0.0), fmt."%,-10G\{0.0}");
        test(String.format("%,-10G", 12345.6), fmt."%,-10G\{12345.6}");
        test(String.format("% g", -12345.6), fmt."% g\{-12345.6}");
        test(String.format("% g", 0.0), fmt."% g\{0.0}");
        test(String.format("% g", 12345.6), fmt."% g\{12345.6}");
        test(String.format("% 10g", -12345.6), fmt."% 10g\{-12345.6}");
        test(String.format("% 10g", 0.0), fmt."% 10g\{0.0}");
        test(String.format("% 10g", 12345.6), fmt."% 10g\{12345.6}");
        test(String.format("% -10g", -12345.6), fmt."% -10g\{-12345.6}");
        test(String.format("% -10g", 0.0), fmt."% -10g\{0.0}");
        test(String.format("% -10g", 12345.6), fmt."% -10g\{12345.6}");
        test(String.format("% G", -12345.6), fmt."% G\{-12345.6}");
        test(String.format("% G", 0.0), fmt."% G\{0.0}");
        test(String.format("% G", 12345.6), fmt."% G\{12345.6}");
        test(String.format("% 10G", -12345.6), fmt."% 10G\{-12345.6}");
        test(String.format("% 10G", 0.0), fmt."% 10G\{0.0}");
        test(String.format("% 10G", 12345.6), fmt."% 10G\{12345.6}");
        test(String.format("% -10G", -12345.6), fmt."% -10G\{-12345.6}");
        test(String.format("% -10G", 0.0), fmt."% -10G\{0.0}");
        test(String.format("% -10G", 12345.6), fmt."% -10G\{12345.6}");
        test(String.format("%, g", -12345.6), fmt."%, g\{-12345.6}");
        test(String.format("%, g", 0.0), fmt."%, g\{0.0}");
        test(String.format("%, g", 12345.6), fmt."%, g\{12345.6}");
        test(String.format("%, 10g", -12345.6), fmt."%, 10g\{-12345.6}");
        test(String.format("%, 10g", 0.0), fmt."%, 10g\{0.0}");
        test(String.format("%, 10g", 12345.6), fmt."%, 10g\{12345.6}");
        test(String.format("%, -10g", -12345.6), fmt."%, -10g\{-12345.6}");
        test(String.format("%, -10g", 0.0), fmt."%, -10g\{0.0}");
        test(String.format("%, -10g", 12345.6), fmt."%, -10g\{12345.6}");
        test(String.format("%, G", -12345.6), fmt."%, G\{-12345.6}");
        test(String.format("%, G", 0.0), fmt."%, G\{0.0}");
        test(String.format("%, G", 12345.6), fmt."%, G\{12345.6}");
        test(String.format("%, 10G", -12345.6), fmt."%, 10G\{-12345.6}");
        test(String.format("%, 10G", 0.0), fmt."%, 10G\{0.0}");
        test(String.format("%, 10G", 12345.6), fmt."%, 10G\{12345.6}");
        test(String.format("%, -10G", -12345.6), fmt."%, -10G\{-12345.6}");
        test(String.format("%, -10G", 0.0), fmt."%, -10G\{0.0}");
        test(String.format("%, -10G", 12345.6), fmt."%, -10G\{12345.6}");

        test(String.format("%g", -12345.6), fmt."%g\{-12345.6}");
        test(String.format("%g", 0.0), fmt."%g\{0.0}");
        test(String.format("%g", 12345.6), fmt."%g\{12345.6}");
        test(String.format("%10g", -12345.6), fmt."%10g\{-12345.6}");
        test(String.format("%10g", 0.0), fmt."%10g\{0.0}");
        test(String.format("%10g", 12345.6), fmt."%10g\{12345.6}");
        test(String.format("%-10g", -12345.6), fmt."%-10g\{-12345.6}");
        test(String.format("%-10g", 0.0), fmt."%-10g\{0.0}");
        test(String.format("%-10g", 12345.6), fmt."%-10g\{12345.6}");
        test(String.format("%G", -12345.6), fmt."%G\{-12345.6}");
        test(String.format("%G", 0.0), fmt."%G\{0.0}");
        test(String.format("%G", 12345.6), fmt."%G\{12345.6}");
        test(String.format("%10G", -12345.6), fmt."%10G\{-12345.6}");
        test(String.format("%10G", 0.0), fmt."%10G\{0.0}");
        test(String.format("%10G", 12345.6), fmt."%10G\{12345.6}");
        test(String.format("%-10G", -12345.6), fmt."%-10G\{-12345.6}");
        test(String.format("%-10G", 0.0), fmt."%-10G\{0.0}");
        test(String.format("%-10G", 12345.6), fmt."%-10G\{12345.6}");
        test(String.format("%,g", -12345.6), fmt."%,g\{-12345.6}");
        test(String.format("%,g", 0.0), fmt."%,g\{0.0}");
        test(String.format("%,g", 12345.6), fmt."%,g\{12345.6}");
        test(String.format("%,10g", -12345.6), fmt."%,10g\{-12345.6}");
        test(String.format("%,10g", 0.0), fmt."%,10g\{0.0}");
        test(String.format("%,10g", 12345.6), fmt."%,10g\{12345.6}");
        test(String.format("%,-10g", -12345.6), fmt."%,-10g\{-12345.6}");
        test(String.format("%,-10g", 0.0), fmt."%,-10g\{0.0}");
        test(String.format("%,-10g", 12345.6), fmt."%,-10g\{12345.6}");
        test(String.format("%,G", -12345.6), fmt."%,G\{-12345.6}");
        test(String.format("%,G", 0.0), fmt."%,G\{0.0}");
        test(String.format("%,G", 12345.6), fmt."%,G\{12345.6}");
        test(String.format("%,10G", -12345.6), fmt."%,10G\{-12345.6}");
        test(String.format("%,10G", 0.0), fmt."%,10G\{0.0}");
        test(String.format("%,10G", 12345.6), fmt."%,10G\{12345.6}");
        test(String.format("%,-10G", -12345.6), fmt."%,-10G\{-12345.6}");
        test(String.format("%,-10G", 0.0), fmt."%,-10G\{0.0}");
        test(String.format("%,-10G", 12345.6), fmt."%,-10G\{12345.6}");
        test(String.format("%+g", -12345.6), fmt."%+g\{-12345.6}");
        test(String.format("%+g", 0.0), fmt."%+g\{0.0}");
        test(String.format("%+g", 12345.6), fmt."%+g\{12345.6}");
        test(String.format("%+10g", -12345.6), fmt."%+10g\{-12345.6}");
        test(String.format("%+10g", 0.0), fmt."%+10g\{0.0}");
        test(String.format("%+10g", 12345.6), fmt."%+10g\{12345.6}");
        test(String.format("%+-10g", -12345.6), fmt."%+-10g\{-12345.6}");
        test(String.format("%+-10g", 0.0), fmt."%+-10g\{0.0}");
        test(String.format("%+-10g", 12345.6), fmt."%+-10g\{12345.6}");
        test(String.format("%+G", -12345.6), fmt."%+G\{-12345.6}");
        test(String.format("%+G", 0.0), fmt."%+G\{0.0}");
        test(String.format("%+G", 12345.6), fmt."%+G\{12345.6}");
        test(String.format("%+10G", -12345.6), fmt."%+10G\{-12345.6}");
        test(String.format("%+10G", 0.0), fmt."%+10G\{0.0}");
        test(String.format("%+10G", 12345.6), fmt."%+10G\{12345.6}");
        test(String.format("%+-10G", -12345.6), fmt."%+-10G\{-12345.6}");
        test(String.format("%+-10G", 0.0), fmt."%+-10G\{0.0}");
        test(String.format("%+-10G", 12345.6), fmt."%+-10G\{12345.6}");
        test(String.format("%,+g", -12345.6), fmt."%,+g\{-12345.6}");
        test(String.format("%,+g", 0.0), fmt."%,+g\{0.0}");
        test(String.format("%,+g", 12345.6), fmt."%,+g\{12345.6}");
        test(String.format("%,+10g", -12345.6), fmt."%,+10g\{-12345.6}");
        test(String.format("%,+10g", 0.0), fmt."%,+10g\{0.0}");
        test(String.format("%,+10g", 12345.6), fmt."%,+10g\{12345.6}");
        test(String.format("%,+-10g", -12345.6), fmt."%,+-10g\{-12345.6}");
        test(String.format("%,+-10g", 0.0), fmt."%,+-10g\{0.0}");
        test(String.format("%,+-10g", 12345.6), fmt."%,+-10g\{12345.6}");
        test(String.format("%,+G", -12345.6), fmt."%,+G\{-12345.6}");
        test(String.format("%,+G", 0.0), fmt."%,+G\{0.0}");
        test(String.format("%,+G", 12345.6), fmt."%,+G\{12345.6}");
        test(String.format("%,+10G", -12345.6), fmt."%,+10G\{-12345.6}");
        test(String.format("%,+10G", 0.0), fmt."%,+10G\{0.0}");
        test(String.format("%,+10G", 12345.6), fmt."%,+10G\{12345.6}");
        test(String.format("%,+-10G", -12345.6), fmt."%,+-10G\{-12345.6}");
        test(String.format("%,+-10G", 0.0), fmt."%,+-10G\{0.0}");
        test(String.format("%,+-10G", 12345.6), fmt."%,+-10G\{12345.6}");

        test(String.format("%g", -12345.6), fmt."%g\{-12345.6}");
        test(String.format("%g", 0.0), fmt."%g\{0.0}");
        test(String.format("%g", 12345.6), fmt."%g\{12345.6}");
        test(String.format("%10g", -12345.6), fmt."%10g\{-12345.6}");
        test(String.format("%10g", 0.0), fmt."%10g\{0.0}");
        test(String.format("%10g", 12345.6), fmt."%10g\{12345.6}");
        test(String.format("%-10g", -12345.6), fmt."%-10g\{-12345.6}");
        test(String.format("%-10g", 0.0), fmt."%-10g\{0.0}");
        test(String.format("%-10g", 12345.6), fmt."%-10g\{12345.6}");
        test(String.format("%G", -12345.6), fmt."%G\{-12345.6}");
        test(String.format("%G", 0.0), fmt."%G\{0.0}");
        test(String.format("%G", 12345.6), fmt."%G\{12345.6}");
        test(String.format("%10G", -12345.6), fmt."%10G\{-12345.6}");
        test(String.format("%10G", 0.0), fmt."%10G\{0.0}");
        test(String.format("%10G", 12345.6), fmt."%10G\{12345.6}");
        test(String.format("%-10G", -12345.6), fmt."%-10G\{-12345.6}");
        test(String.format("%-10G", 0.0), fmt."%-10G\{0.0}");
        test(String.format("%-10G", 12345.6), fmt."%-10G\{12345.6}");
        test(String.format("%,g", -12345.6), fmt."%,g\{-12345.6}");
        test(String.format("%,g", 0.0), fmt."%,g\{0.0}");
        test(String.format("%,g", 12345.6), fmt."%,g\{12345.6}");
        test(String.format("%,10g", -12345.6), fmt."%,10g\{-12345.6}");
        test(String.format("%,10g", 0.0), fmt."%,10g\{0.0}");
        test(String.format("%,10g", 12345.6), fmt."%,10g\{12345.6}");
        test(String.format("%,-10g", -12345.6), fmt."%,-10g\{-12345.6}");
        test(String.format("%,-10g", 0.0), fmt."%,-10g\{0.0}");
        test(String.format("%,-10g", 12345.6), fmt."%,-10g\{12345.6}");
        test(String.format("%,G", -12345.6), fmt."%,G\{-12345.6}");
        test(String.format("%,G", 0.0), fmt."%,G\{0.0}");
        test(String.format("%,G", 12345.6), fmt."%,G\{12345.6}");
        test(String.format("%,10G", -12345.6), fmt."%,10G\{-12345.6}");
        test(String.format("%,10G", 0.0), fmt."%,10G\{0.0}");
        test(String.format("%,10G", 12345.6), fmt."%,10G\{12345.6}");
        test(String.format("%,-10G", -12345.6), fmt."%,-10G\{-12345.6}");
        test(String.format("%,-10G", 0.0), fmt."%,-10G\{0.0}");
        test(String.format("%,-10G", 12345.6), fmt."%,-10G\{12345.6}");
        test(String.format("%(g", -12345.6), fmt."%(g\{-12345.6}");
        test(String.format("%(g", 0.0), fmt."%(g\{0.0}");
        test(String.format("%(g", 12345.6), fmt."%(g\{12345.6}");
        test(String.format("%(10g", -12345.6), fmt."%(10g\{-12345.6}");
        test(String.format("%(10g", 0.0), fmt."%(10g\{0.0}");
        test(String.format("%(10g", 12345.6), fmt."%(10g\{12345.6}");
        test(String.format("%(-10g", -12345.6), fmt."%(-10g\{-12345.6}");
        test(String.format("%(-10g", 0.0), fmt."%(-10g\{0.0}");
        test(String.format("%(-10g", 12345.6), fmt."%(-10g\{12345.6}");
        test(String.format("%(G", -12345.6), fmt."%(G\{-12345.6}");
        test(String.format("%(G", 0.0), fmt."%(G\{0.0}");
        test(String.format("%(G", 12345.6), fmt."%(G\{12345.6}");
        test(String.format("%(10G", -12345.6), fmt."%(10G\{-12345.6}");
        test(String.format("%(10G", 0.0), fmt."%(10G\{0.0}");
        test(String.format("%(10G", 12345.6), fmt."%(10G\{12345.6}");
        test(String.format("%(-10G", -12345.6), fmt."%(-10G\{-12345.6}");
        test(String.format("%(-10G", 0.0), fmt."%(-10G\{0.0}");
        test(String.format("%(-10G", 12345.6), fmt."%(-10G\{12345.6}");
        test(String.format("%,(g", -12345.6), fmt."%,(g\{-12345.6}");
        test(String.format("%,(g", 0.0), fmt."%,(g\{0.0}");
        test(String.format("%,(g", 12345.6), fmt."%,(g\{12345.6}");
        test(String.format("%,(10g", -12345.6), fmt."%,(10g\{-12345.6}");
        test(String.format("%,(10g", 0.0), fmt."%,(10g\{0.0}");
        test(String.format("%,(10g", 12345.6), fmt."%,(10g\{12345.6}");
        test(String.format("%,(-10g", -12345.6), fmt."%,(-10g\{-12345.6}");
        test(String.format("%,(-10g", 0.0), fmt."%,(-10g\{0.0}");
        test(String.format("%,(-10g", 12345.6), fmt."%,(-10g\{12345.6}");
        test(String.format("%,(G", -12345.6), fmt."%,(G\{-12345.6}");
        test(String.format("%,(G", 0.0), fmt."%,(G\{0.0}");
        test(String.format("%,(G", 12345.6), fmt."%,(G\{12345.6}");
        test(String.format("%,(10G", -12345.6), fmt."%,(10G\{-12345.6}");
        test(String.format("%,(10G", 0.0), fmt."%,(10G\{0.0}");
        test(String.format("%,(10G", 12345.6), fmt."%,(10G\{12345.6}");
        test(String.format("%,(-10G", -12345.6), fmt."%,(-10G\{-12345.6}");
        test(String.format("%,(-10G", 0.0), fmt."%,(-10G\{0.0}");
        test(String.format("%,(-10G", 12345.6), fmt."%,(-10G\{12345.6}");
        test(String.format("%+g", -12345.6), fmt."%+g\{-12345.6}");
        test(String.format("%+g", 0.0), fmt."%+g\{0.0}");
        test(String.format("%+g", 12345.6), fmt."%+g\{12345.6}");
        test(String.format("%+10g", -12345.6), fmt."%+10g\{-12345.6}");
        test(String.format("%+10g", 0.0), fmt."%+10g\{0.0}");
        test(String.format("%+10g", 12345.6), fmt."%+10g\{12345.6}");
        test(String.format("%+-10g", -12345.6), fmt."%+-10g\{-12345.6}");
        test(String.format("%+-10g", 0.0), fmt."%+-10g\{0.0}");
        test(String.format("%+-10g", 12345.6), fmt."%+-10g\{12345.6}");
        test(String.format("%+G", -12345.6), fmt."%+G\{-12345.6}");
        test(String.format("%+G", 0.0), fmt."%+G\{0.0}");
        test(String.format("%+G", 12345.6), fmt."%+G\{12345.6}");
        test(String.format("%+10G", -12345.6), fmt."%+10G\{-12345.6}");
        test(String.format("%+10G", 0.0), fmt."%+10G\{0.0}");
        test(String.format("%+10G", 12345.6), fmt."%+10G\{12345.6}");
        test(String.format("%+-10G", -12345.6), fmt."%+-10G\{-12345.6}");
        test(String.format("%+-10G", 0.0), fmt."%+-10G\{0.0}");
        test(String.format("%+-10G", 12345.6), fmt."%+-10G\{12345.6}");
        test(String.format("%,+g", -12345.6), fmt."%,+g\{-12345.6}");
        test(String.format("%,+g", 0.0), fmt."%,+g\{0.0}");
        test(String.format("%,+g", 12345.6), fmt."%,+g\{12345.6}");
        test(String.format("%,+10g", -12345.6), fmt."%,+10g\{-12345.6}");
        test(String.format("%,+10g", 0.0), fmt."%,+10g\{0.0}");
        test(String.format("%,+10g", 12345.6), fmt."%,+10g\{12345.6}");
        test(String.format("%,+-10g", -12345.6), fmt."%,+-10g\{-12345.6}");
        test(String.format("%,+-10g", 0.0), fmt."%,+-10g\{0.0}");
        test(String.format("%,+-10g", 12345.6), fmt."%,+-10g\{12345.6}");
        test(String.format("%,+G", -12345.6), fmt."%,+G\{-12345.6}");
        test(String.format("%,+G", 0.0), fmt."%,+G\{0.0}");
        test(String.format("%,+G", 12345.6), fmt."%,+G\{12345.6}");
        test(String.format("%,+10G", -12345.6), fmt."%,+10G\{-12345.6}");
        test(String.format("%,+10G", 0.0), fmt."%,+10G\{0.0}");
        test(String.format("%,+10G", 12345.6), fmt."%,+10G\{12345.6}");
        test(String.format("%,+-10G", -12345.6), fmt."%,+-10G\{-12345.6}");
        test(String.format("%,+-10G", 0.0), fmt."%,+-10G\{0.0}");
        test(String.format("%,+-10G", 12345.6), fmt."%,+-10G\{12345.6}");
        test(String.format("%(+g", -12345.6), fmt."%(+g\{-12345.6}");
        test(String.format("%(+g", 0.0), fmt."%(+g\{0.0}");
        test(String.format("%(+g", 12345.6), fmt."%(+g\{12345.6}");
        test(String.format("%(+10g", -12345.6), fmt."%(+10g\{-12345.6}");
        test(String.format("%(+10g", 0.0), fmt."%(+10g\{0.0}");
        test(String.format("%(+10g", 12345.6), fmt."%(+10g\{12345.6}");
        test(String.format("%(+-10g", -12345.6), fmt."%(+-10g\{-12345.6}");
        test(String.format("%(+-10g", 0.0), fmt."%(+-10g\{0.0}");
        test(String.format("%(+-10g", 12345.6), fmt."%(+-10g\{12345.6}");
        test(String.format("%(+G", -12345.6), fmt."%(+G\{-12345.6}");
        test(String.format("%(+G", 0.0), fmt."%(+G\{0.0}");
        test(String.format("%(+G", 12345.6), fmt."%(+G\{12345.6}");
        test(String.format("%(+10G", -12345.6), fmt."%(+10G\{-12345.6}");
        test(String.format("%(+10G", 0.0), fmt."%(+10G\{0.0}");
        test(String.format("%(+10G", 12345.6), fmt."%(+10G\{12345.6}");
        test(String.format("%(+-10G", -12345.6), fmt."%(+-10G\{-12345.6}");
        test(String.format("%(+-10G", 0.0), fmt."%(+-10G\{0.0}");
        test(String.format("%(+-10G", 12345.6), fmt."%(+-10G\{12345.6}");
        test(String.format("%,(+g", -12345.6), fmt."%,(+g\{-12345.6}");
        test(String.format("%,(+g", 0.0), fmt."%,(+g\{0.0}");
        test(String.format("%,(+g", 12345.6), fmt."%,(+g\{12345.6}");
        test(String.format("%,(+10g", -12345.6), fmt."%,(+10g\{-12345.6}");
        test(String.format("%,(+10g", 0.0), fmt."%,(+10g\{0.0}");
        test(String.format("%,(+10g", 12345.6), fmt."%,(+10g\{12345.6}");
        test(String.format("%,(+-10g", -12345.6), fmt."%,(+-10g\{-12345.6}");
        test(String.format("%,(+-10g", 0.0), fmt."%,(+-10g\{0.0}");
        test(String.format("%,(+-10g", 12345.6), fmt."%,(+-10g\{12345.6}");
        test(String.format("%,(+G", -12345.6), fmt."%,(+G\{-12345.6}");
        test(String.format("%,(+G", 0.0), fmt."%,(+G\{0.0}");
        test(String.format("%,(+G", 12345.6), fmt."%,(+G\{12345.6}");
        test(String.format("%,(+10G", -12345.6), fmt."%,(+10G\{-12345.6}");
        test(String.format("%,(+10G", 0.0), fmt."%,(+10G\{0.0}");
        test(String.format("%,(+10G", 12345.6), fmt."%,(+10G\{12345.6}");
        test(String.format("%,(+-10G", -12345.6), fmt."%,(+-10G\{-12345.6}");
        test(String.format("%,(+-10G", 0.0), fmt."%,(+-10G\{0.0}");
        test(String.format("%,(+-10G", 12345.6), fmt."%,(+-10G\{12345.6}");

        test(String.format("%a", -12345.6), fmt."%a\{-12345.6}");
        test(String.format("%a", 0.0), fmt."%a\{0.0}");
        test(String.format("%a", 12345.6), fmt."%a\{12345.6}");
        test(String.format("%10a", -12345.6), fmt."%10a\{-12345.6}");
        test(String.format("%10a", 0.0), fmt."%10a\{0.0}");
        test(String.format("%10a", 12345.6), fmt."%10a\{12345.6}");
        test(String.format("%-10a", -12345.6), fmt."%-10a\{-12345.6}");
        test(String.format("%-10a", 0.0), fmt."%-10a\{0.0}");
        test(String.format("%-10a", 12345.6), fmt."%-10a\{12345.6}");
        test(String.format("%A", -12345.6), fmt."%A\{-12345.6}");
        test(String.format("%A", 0.0), fmt."%A\{0.0}");
        test(String.format("%A", 12345.6), fmt."%A\{12345.6}");
        test(String.format("%10A", -12345.6), fmt."%10A\{-12345.6}");
        test(String.format("%10A", 0.0), fmt."%10A\{0.0}");
        test(String.format("%10A", 12345.6), fmt."%10A\{12345.6}");
        test(String.format("%-10A", -12345.6), fmt."%-10A\{-12345.6}");
        test(String.format("%-10A", 0.0), fmt."%-10A\{0.0}");
        test(String.format("%-10A", 12345.6), fmt."%-10A\{12345.6}");

        test("aaa%false", fmt."aaa%%%b\{false}");
        test("aaa" + System.lineSeparator() + "false", fmt."aaa%n%b\{false}");

        assertThrows(
                MissingFormatArgumentException.class,
                () -> fmt. "%10ba\{ false }",
                "Format specifier '%10b is not immediately followed by an embedded expression'");

        assertThrows(
                MissingFormatArgumentException.class,
                () ->fmt. "%ba\{ false }",
                "Format specifier '%b is not immediately followed by an embedded expression'");

        assertThrows(
                MissingFormatArgumentException.class,
                () ->fmt. "%b",
                "Format specifier '%b is not immediately followed by an embedded expression'");
        assertThrows(
                UnknownFormatConversionException.class,
                () ->fmt. "%0",
                "Conversion = '0'");
    }

    static void suiteDateTimes(FormatProcessor fmt) {
        ZoneOffset zoneOffset = ZoneOffset.ofHours(8);
        ZoneId zoneId = ZoneId.systemDefault();

        int[] years = {-99999, -9999, -999, -99, -9, 0, 9, 99, 999, 1999, 2999, 9999, 99999};
        for (int year : years) {
            for (int month = 1; month <= 12; month++) {
                for (int dayOfMonth = 1; dayOfMonth <= 28; dayOfMonth++) {
                    LocalDate localDate = LocalDate.of(year, month, dayOfMonth);
                    LocalDateTime ldt = LocalDateTime.of(localDate, LocalTime.MIN);
                    OffsetDateTime odt = OffsetDateTime.of(ldt, zoneOffset);
                    ZonedDateTime zdt = ZonedDateTime.of(ldt, zoneId);
                    Instant instant = Instant.from(zdt);
                    Date date = Date.from(instant);

                    // %tF latin1
                    test("time %tF".formatted(ldt), fmt."time %tF\{ldt}");
                    test("time %tF".formatted(zdt), fmt."time %tF\{zdt}");
                    test("time %tF".formatted(odt), fmt."time %tF\{odt}");
                    test("time %tF".formatted(date), fmt."time %tF\{date}");

                    // %tF utf16
                    test("\u65f6\u95f4 %tF".formatted(ldt), fmt."\u65f6\u95f4 %tF\{ldt}");
                    test("\u65f6\u95f4 %tF".formatted(zdt), fmt."\u65f6\u95f4 %tF\{zdt}");
                    test("\u65f6\u95f4 %tF".formatted(odt), fmt."\u65f6\u95f4 %tF\{odt}");
                    test("\u65f6\u95f4 %tF".formatted(date), fmt."\u65f6\u95f4 %tF\{date}");

                    // %tD latin1
                    test("time %tD".formatted(ldt), fmt."time %tD\{ldt}");
                    test("time %tD".formatted(zdt), fmt."time %tD\{zdt}");
                    test("time %tD".formatted(odt), fmt."time %tD\{odt}");
                    test("time %tD".formatted(date), fmt."time %tD\{date}");

                    // %tD utf16
                    test("\u65f6\u95f4 %tD".formatted(ldt), fmt."\u65f6\u95f4 %tD\{ldt}");
                    test("\u65f6\u95f4 %tD".formatted(zdt), fmt."\u65f6\u95f4 %tD\{zdt}");
                    test("\u65f6\u95f4 %tD".formatted(odt), fmt."\u65f6\u95f4 %tD\{odt}");
                    test("\u65f6\u95f4 %tD".formatted(date), fmt."\u65f6\u95f4 %tD\{date}");

                    // %s latin1
                    test("date %s".formatted(ldt), fmt."date %s\{ldt}");
                    test("date %s".formatted(zdt), fmt."date %s\{zdt}");
                    test("date %s".formatted(odt), fmt."date %s\{odt}");
                    test("date %s".formatted(localDate), fmt."date %s\{localDate}");
                    test("date %s".formatted(date), fmt."date %s\{date}");
                    test("date %s".formatted(instant), fmt."date %s\{instant}");

                    // %s utf16
                    test("\u65e5\u671f %s".formatted(ldt), fmt."\u65e5\u671f %s\{ldt}");
                    test("\u65e5\u671f %s".formatted(zdt), fmt."\u65e5\u671f %s\{zdt}");
                    test("\u65e5\u671f %s".formatted(odt), fmt."\u65e5\u671f %s\{odt}");
                    test("\u65e5\u671f %s".formatted(localDate), fmt."\u65e5\u671f %s\{localDate}");
                    test("\u65e5\u671f %s".formatted(date), fmt."\u65e5\u671f %s\{date}");
                    test("\u65e5\u671f %s".formatted(instant), fmt."\u65e5\u671f %s\{instant}");

                    test("%tc".formatted(date), fmt."%tc\{date}");
                    test("\u65f6\u95f4%tc".formatted(date), fmt."\u65f6\u95f4%tc\{date}");
                }
            }
        }

        LocalDate localDate = LocalDate.of(2023, 10, 3);
        int[] nanos = {
                0, 1, 10, 12, 100, 123,
                123000, 123010, 123100, 123120, 123123,
                100000000, 120000000,
                123000000, 123000001, 123000010, 123000100, 123001000, 123010000, 123100000,
                123120000, 123120010, 123120100, 123123000, 123123100, 123123120, 123123123,
                999999999
        };
        int[] minutes = {0, 1, 9, 10, 59};
        int[] seconds = {0, 1, 9, 10, 59};
        for (int hour = 0; hour < 23; hour++) {
            for (int minute : minutes) {
                for (int nano : nanos) {
                    for (int second : seconds) {
                        LocalTime localTime = LocalTime.of(hour, minute, second, nano);
                        OffsetTime offsetTime = OffsetTime.of(localTime, zoneOffset);

                        LocalDateTime ldt = LocalDateTime.of(localDate, localTime);
                        OffsetDateTime odt = OffsetDateTime.of(ldt, zoneOffset);
                        ZonedDateTime zdt = ZonedDateTime.of(ldt, zoneId);
                        Instant instant = Instant.from(zdt);
                        Date date = Date.from(instant);

                        // %tr latin1
                        test("time %tr".formatted(localTime), fmt. "time %tr\{ localTime }" );
                        test("time %tr".formatted(offsetTime), fmt. "time %tr\{ offsetTime }" );
                        test("time %tr".formatted(ldt), fmt. "time %tr\{ ldt }" );
                        test("time %tr".formatted(odt), fmt. "time %tr\{ odt }" );
                        test("time %tr".formatted(zdt), fmt. "time %tr\{ zdt }" );

                        // %tr utf16
                        test("\u65f6\u95f4 %tr".formatted(localTime), fmt. "\u65f6\u95f4 %tr\{ localTime }" );
                        test("\u65f6\u95f4 %tr".formatted(offsetTime), fmt. "\u65f6\u95f4 %tr\{ offsetTime }" );
                        test("\u65f6\u95f4 %tr".formatted(ldt), fmt. "\u65f6\u95f4 %tr\{ ldt }" );
                        test("\u65f6\u95f4 %tr".formatted(odt), fmt. "\u65f6\u95f4 %tr\{ odt }" );
                        test("\u65f6\u95f4 %tr".formatted(zdt), fmt. "\u65f6\u95f4 %tr\{ zdt }" );

                        // %tR latin1
                        test("time %tR".formatted(localTime), fmt. "time %tR\{ localTime }" );
                        test("time %tR".formatted(offsetTime), fmt. "time %tR\{ offsetTime }" );
                        test("time %tR".formatted(ldt), fmt. "time %tR\{ ldt }" );
                        test("time %tR".formatted(odt), fmt. "time %tR\{ odt }" );
                        test("time %tR".formatted(zdt), fmt. "time %tR\{ zdt }" );

                        // %tR utf16
                        test("\u65f6\u95f4 %tR".formatted(localTime), fmt. "\u65f6\u95f4 %tR\{ localTime }" );
                        test("\u65f6\u95f4 %tR".formatted(offsetTime), fmt. "\u65f6\u95f4 %tR\{ offsetTime }" );
                        test("\u65f6\u95f4 %tR".formatted(ldt), fmt. "\u65f6\u95f4 %tR\{ ldt }" );
                        test("\u65f6\u95f4 %tR".formatted(odt), fmt. "\u65f6\u95f4 %tR\{ odt }" );
                        test("\u65f6\u95f4 %tR".formatted(zdt), fmt. "\u65f6\u95f4 %tR\{ zdt }" );

                        // %tT latin1
                        test("time %tT".formatted(localTime), fmt."time %tT\{localTime}");
                        test("time %tT".formatted(offsetTime), fmt."time %tT\{offsetTime}");
                        test("time %tT".formatted(ldt), fmt."time %tT\{ldt}");
                        test("time %tT".formatted(zdt), fmt."time %tT\{zdt}");
                        test("time %tT".formatted(odt), fmt."time %tT\{odt}");
                        test("time %tT".formatted(date), fmt."time %tT\{date}");

                        // %tT utf16
                        test("\u65f6\u95f4 %tT".formatted(localTime), fmt."\u65f6\u95f4 %tT\{localTime}");
                        test("\u65f6\u95f4 %tT".formatted(offsetTime), fmt."\u65f6\u95f4 %tT\{offsetTime}");
                        test("\u65f6\u95f4 %tT".formatted(ldt), fmt."\u65f6\u95f4 %tT\{ldt}");
                        test("\u65f6\u95f4 %tT".formatted(zdt), fmt."\u65f6\u95f4 %tT\{zdt}");
                        test("\u65f6\u95f4 %tT".formatted(odt), fmt."\u65f6\u95f4 %tT\{odt}");
                        test("\u65f6\u95f4 %tT".formatted(date), fmt."\u65f6\u95f4 %tT\{date}");

                        // %s latin1
                        test("time %s".formatted(localTime), fmt."time %s\{localTime}");
                        test("time %s".formatted(offsetTime), fmt."time %s\{offsetTime}");
                        test("time %s".formatted(ldt), fmt."time %s\{ldt}");
                        test("time %s".formatted(odt), fmt."time %s\{odt}");
                        test("time %s".formatted(zdt), fmt."time %s\{zdt}");
                        test("time %s".formatted(date), fmt."time %s\{date}");
                        test("time %s".formatted(instant), fmt."time %s\{instant}");

                        // %s utf16
                        test("\u65f6\u95f4 %s".formatted(localTime), fmt."\u65f6\u95f4 %s\{localTime}");
                        test("\u65f6\u95f4 %s".formatted(offsetTime), fmt."\u65f6\u95f4 %s\{offsetTime}");
                        test("\u65f6\u95f4 %s".formatted(ldt), fmt."\u65f6\u95f4 %s\{ldt}");
                        test("\u65f6\u95f4 %s".formatted(odt), fmt."\u65f6\u95f4 %s\{odt}");
                        test("\u65f6\u95f4 %s".formatted(zdt), fmt."\u65f6\u95f4 %s\{zdt}");
                        test("\u65f6\u95f4 %s".formatted(date), fmt."\u65f6\u95f4 %s\{date}");
                        test("\u65f6\u95f4 %s".formatted(instant), fmt."\u65f6\u95f4 %s\{instant}");

                        // %tc
                        test("time %tc".formatted(date), fmt."time %tc\{date}");
                        test("\u65f6\u95f4%tc".formatted(date), fmt."\u65f6\u95f4%tc\{date}");
                    }
                }
            }
        }
    }
}
