/*
 * Copyright (c) 2000, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4159554
 * @summary Problem with UUDecoder
 *
 */

import sun.misc.*;
import java.io.*;

public class DecodeBuffer {

    public static void main(String[] args) throws Exception {
        String encoded;
        // text to encode and decode
        String originalText = "Hi There, please encode and decode me";
        UUDecoder uuD = new UUDecoder();

        encoded = "begin 644 encoder.buf\r\n" +
          "E2&D@5&AE<F4L('!L96%S92!E;F-O9&4@86YD(&1E8V]D92!M90$!\r\n"+
          " \r\nend\r\n";
        check (uuD, encoded, originalText);

        encoded = "begin 644 encoder.buf\n" +
          "E2&D@5&AE<F4L('!L96%S92!E;F-O9&4@86YD(&1E8V]D92!M90$!\n"+
          " \nend\n";
        check (uuD, encoded, originalText);

        encoded = "begin 644 encoder.buf\r" +
          "E2&D@5&AE<F4L('!L96%S92!E;F-O9&4@86YD(&1E8V]D92!M90$!\r"+
          " \rend\r";
        check (uuD, encoded, originalText);

        // Multi-line Unix text file

        String s1 = "begin 644 f\n"+
        "M3W)I9VYL(\"I(:2!4:&5R92P@<&QE87-E(&5N8V]D92!A;F0@9&5C;V1E(&UE\n"+
        "M*@IA;F0@;64@06YD($UE(&%N1\"!M92!!;F0@344@04Y$($U%(%I80U8@,3(S\n"+
        "-97)T\"E5)3U @45=%\"DUE\n"+
        " \nend\n";

        String s2 = "Orignl *Hi There, please encode and decode me*\n"+
        "and me And Me anD me And ME AND ME ZXCV 123ert\n"+
        "UIOP QWE\n";
        check (uuD, s1, s2);

        // Multi-line Windows text file

        s1 = "begin 644 f\n"+
        "M2&5L;&\\@22!A;2!A(&UU;'1I;&EN92!F:6QE#0IC<F5A=&5D(&]N(%=I;F1O\r\n"+
        "M=W,L('1O('1E<W0@=&AE(%5516YC;V1E<@T*86YD(%551&5C;V1E<B!C;&%S\r\n"+
        "$<V5S+G1O\r\n"+ " \r\nend\r\n";
        s2="Hello I am a multiline file\r\n"+
        "created on Windows, to test the UUEncoder\r\n"+
        "and UUDecoder classes.";
        check (uuD, s1, s2);
    }

    public static void check (UUDecoder uuD, String s, String original) throws Exception {
        String decoded;
        // do UU stuff
        decoded = new String(uuD.decodeBuffer(s));
        if (!decoded.equals (original)) {
            throw new Exception ("decoded text not same as original");
        }
    }
}
