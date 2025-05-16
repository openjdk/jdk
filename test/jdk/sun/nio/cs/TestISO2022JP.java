/*
 * Copyright (c) 2008, 2018, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 4626545 4879522 4913711 4119445 8042125 8211382
 * @summary Check full coverage encode/decode for ISO-2022-JP
 * @modules jdk.charsets
 */

/*
 * Tests the NIO converter for J2RE >= 1.4.1
 * since the default converter used by String
 * API is the NIO converter sun.nio.cs.ext.ISO2022_JP
 */

import java.io.*;
import java.util.Arrays;

public class TestISO2022JP {

    private final static String US_ASCII =
        "\u0000\u0001\u0002\u0003\u0004\u0005\u0006\u0007" +
        "\b\t\n\u000B\f\r" +
        "\u0010\u0011\u0012\u0013\u0014\u0015\u0016\u0017" +
        "\u0018\u0019\u001A\u001C\u001D\u001E\u001F" +
        " !\"#$%&\'" +
        "()*+,-./" +
        "01234567" +
        "89:;<=>?" +
        "@ABCDEFG" +
        "HIJKLMNO" +
        "PQRSTUVW" +
        "XYZ[\\]^_" +
        "`abcdefg" +
        "hijklmno" +
        "pqrstuvw" +
        "xyz{|}~¥‾";

     // Subset of chars sourced from JISX0208:1983

     private final static String JISX0208SUBSET =
        "u3000、。，．・：" +
        "；？！゛゜´｀¨" +
        "＾￣＿ヽヾゝゞ〃" +
        "仝々〆〇ー—‐／" +
        "＼〜‖｜…‥‘’" +
        "尅將專對尓尠尢尨" +
        "尸尹屁屆屎屓屐屏" +
        "孱屬屮乢屶屹岌岑" +
        "岔妛岫岻岶岼岷峅" +
        "岾峇峙峩峽峺峭嶌" +
        "峪崋崕崗嵜崟崛崑" +
        "崔崢崚崙崘嵌嵒嵎" +
        "嵋嵬嵳嵶嶇嶄嶂嶢" +
        "嶝嶬嶮嶽嶐嶷嶼巉" +
        "巍巓巒巖巛巫已巵" +
        "帋帚帙帑帛帶帷幄" +
        "幃幀幎幗幔幟幢幤" +
        "幇幵并幺麼广庠廁" +
        "廂廈廐廏廖廣廝廚" +
        "廛廢廡廨廩廬廱廳" +
        "廰廴廸廾弃弉彝彜" +
        "弋弑弖弩弭弸彁彈" +
        "彌彎弯彑彖彗彙彡" +
        "彭彳彷徃徂彿徊很" +
        "徑徇從徙徘徠徨徭" +
        "徼忖忻忤忸忱忝悳" +
        "忿怡恠怙怐怩怎怱" +
        "拮拱挧挂挈拯拵捐" +
        "挾捍搜捏掖掎掀掫" +
        "捶掣掏掉掟掵捫捩" +
        "掾揩揀揆揣揉插揶" +
        "揄搖搴搆搓搦搶攝" +
        "搗搨搏摧摯摶摎攪" +
        "撕撓撥撩撈撼據擒" +
        "擅擇撻擘擂擱擧舉" +
        "擠擡抬擣擯攬擶擴" +
        "擲擺攀擽攘攜攅攤" +
        "攣攫攴攵攷收攸畋" +
        "杁朸朷杆杞杠杙杣" +
        "杤枉杰枩杼杪枌枋" +
        "枦枡枅枷柯枴柬枳" +
        "柩枸柤柞柝柢柮枹" +
        "柎柆柧檜栞框栩桀" +
        "桍栲桎梳栫桙档桷" +
        "桿梟梏梭梔條梛梃" +
        "檮梹桴梵梠梺椏梍" +
        "桾椁棊椈棘椢椦棡" +
        "椌棍棔棧棕椶椒椄" +
        "棗棣椥棹棠棯椨椪" +
        "椚椣椡棆楹楷楜楸" +
        "泗泅泝沮沱沾沺泛" +
        "泯泙泪洟衍洶洫洽" +
        "洸洙洵洳洒洌浣涓" +
        "浤浚浹浙涎涕濤涅" +
        "淹渕渊涵淇淦涸淆" +
        "淬淞淌淨淒淅淺淙" +
        "牋牘牴牾犂犁犇犒" +
        "犖犢犧犹犲狃狆狄" +
        "鵙鵲鶉鶇鶫鵯鵺鶚" +
        "鶤鶩鶲鷄鷁鶻鶸鶺" +
        "鷆鷏鷂鷙鷓鷸鷦鷭" +
        "鷯鷽鸚鸛鸞鹵鹹鹽" +
        "麁麈麋麌麒麕麑麝" +
        "麥麩麸麪麭靡黌黎" +
        "黏黐黔黜點黝黠黥" +
        "黨黯黴黶黷黹黻黼" +
        "黽鼇鼈皷鼕鼡鼬鼾" +
        "齊齒齔齣齟齠齡齦" +
        "齧齬齪齷齲齶龕龜" +
        "龠堯槇遙瑤凜熙";

    final static String JISX0202KATAKANA =
        "｡｢｣､" +
        "･ｦｧｨｩｪｫｬ" +
        "ｭｮｯｰｱｲｳｴ" +
        "ｵｶｷｸｹｺｻｼ" +
        "ｽｾｿﾀﾁﾂﾃﾄ" +
        "ﾅﾆﾇﾈﾉﾊﾋﾌ" +
        "ﾍﾎﾏﾐﾑﾒﾓﾔ" +
        "ﾕﾖﾗﾘﾙﾚﾛﾜ" +
        "ﾝﾞﾟ";


    final static byte[] expectedBytes1 = {
        (byte) 0x0, (byte) 0x1, (byte) 0x2, (byte) 0x3,
        (byte) 0x4, (byte) 0x5, (byte) 0x6, (byte) 0x7,
        (byte) 0x8, (byte) 0x9, (byte) 0xa, (byte) 0xb,
        (byte) 0xc, (byte) 0xd,
        (byte) 0x10, (byte) 0x11, (byte) 0x12, (byte) 0x13,
        (byte) 0x14, (byte) 0x15, (byte) 0x16, (byte) 0x17,
        (byte) 0x18, (byte) 0x19, (byte) 0x1a,
        (byte) 0x1c, (byte) 0x1d, (byte) 0x1e, (byte) 0x1f,
        (byte) 0x20, (byte) 0x21, (byte) 0x22, (byte) 0x23,
        (byte) 0x24, (byte) 0x25, (byte) 0x26, (byte) 0x27,
        (byte) 0x28, (byte) 0x29, (byte) 0x2a, (byte) 0x2b,
        (byte) 0x2c, (byte) 0x2d, (byte) 0x2e, (byte) 0x2f,
        (byte) 0x30, (byte) 0x31, (byte) 0x32, (byte) 0x33,
        (byte) 0x34, (byte) 0x35, (byte) 0x36, (byte) 0x37,
        (byte) 0x38, (byte) 0x39, (byte) 0x3a, (byte) 0x3b,
        (byte) 0x3c, (byte) 0x3d, (byte) 0x3e, (byte) 0x3f,
        (byte) 0x40, (byte) 0x41, (byte) 0x42, (byte) 0x43,
        (byte) 0x44, (byte) 0x45, (byte) 0x46, (byte) 0x47,
        (byte) 0x48, (byte) 0x49, (byte) 0x4a, (byte) 0x4b,
        (byte) 0x4c, (byte) 0x4d, (byte) 0x4e, (byte) 0x4f,
        (byte) 0x50, (byte) 0x51, (byte) 0x52, (byte) 0x53,
        (byte) 0x54, (byte) 0x55, (byte) 0x56, (byte) 0x57,
        (byte) 0x58, (byte) 0x59, (byte) 0x5a, (byte) 0x5b,
        (byte) 0x5c, (byte) 0x5d, (byte) 0x5e, (byte) 0x5f,
        (byte) 0x60, (byte) 0x61, (byte) 0x62, (byte) 0x63,
        (byte) 0x64, (byte) 0x65, (byte) 0x66, (byte) 0x67,
        (byte) 0x68, (byte) 0x69, (byte) 0x6a, (byte) 0x6b,
        (byte) 0x6c, (byte) 0x6d, (byte) 0x6e, (byte) 0x6f,
        (byte) 0x70, (byte) 0x71, (byte) 0x72, (byte) 0x73,
        (byte) 0x74, (byte) 0x75, (byte) 0x76, (byte) 0x77,
        (byte) 0x78, (byte) 0x79, (byte) 0x7a, (byte) 0x7b,
        (byte) 0x7c, (byte) 0x7d, (byte) 0x7e, (byte) 0x1b,
        (byte) 0x28, (byte) 0x4a, (byte) 0x5c, (byte) 0x7e,
        (byte) 0x1b, (byte) 0x28, (byte) 0x42, (byte) 0x75,
        (byte) 0x33, (byte) 0x30, (byte) 0x30, (byte) 0x30,
        (byte) 0x1b, (byte) 0x24, (byte) 0x42, (byte) 0x21,
        (byte) 0x22, (byte) 0x21, (byte) 0x23, (byte) 0x21,
        (byte) 0x24, (byte) 0x21, (byte) 0x25, (byte) 0x21,
        (byte) 0x26, (byte) 0x21, (byte) 0x27, (byte) 0x21,
        (byte) 0x28, (byte) 0x21, (byte) 0x29, (byte) 0x21,
        (byte) 0x2a, (byte) 0x21, (byte) 0x2b, (byte) 0x21,
        (byte) 0x2c, (byte) 0x21, (byte) 0x2d, (byte) 0x21,
        (byte) 0x2e, (byte) 0x21, (byte) 0x2f, (byte) 0x21,
        (byte) 0x30, (byte) 0x21, (byte) 0x31, (byte) 0x21,
        (byte) 0x32, (byte) 0x21, (byte) 0x33, (byte) 0x21,
        (byte) 0x34, (byte) 0x21, (byte) 0x35, (byte) 0x21,
        (byte) 0x36, (byte) 0x21, (byte) 0x37, (byte) 0x21,
        (byte) 0x38, (byte) 0x21, (byte) 0x39, (byte) 0x21,
        (byte) 0x3a, (byte) 0x21, (byte) 0x3b, (byte) 0x21,
        (byte) 0x3c, (byte) 0x21, (byte) 0x3d, (byte) 0x21,
        (byte) 0x3e, (byte) 0x21, (byte) 0x3f, (byte) 0x21,
        (byte) 0x40, (byte) 0x21, (byte) 0x41, (byte) 0x21,
        (byte) 0x42, (byte) 0x21, (byte) 0x43, (byte) 0x21,
        (byte) 0x44, (byte) 0x21, (byte) 0x45, (byte) 0x21,
        (byte) 0x46, (byte) 0x21, (byte) 0x47, (byte) 0x55,
        (byte) 0x71, (byte) 0x55, (byte) 0x72, (byte) 0x55,
        (byte) 0x73, (byte) 0x55, (byte) 0x74, (byte) 0x55,
        (byte) 0x75, (byte) 0x55, (byte) 0x76, (byte) 0x55,
        (byte) 0x77, (byte) 0x55, (byte) 0x78, (byte) 0x55,
        (byte) 0x79, (byte) 0x55, (byte) 0x7a, (byte) 0x55,
        (byte) 0x7b, (byte) 0x55, (byte) 0x7c, (byte) 0x55,
        (byte) 0x7d, (byte) 0x55, (byte) 0x7e, (byte) 0x56,
        (byte) 0x21, (byte) 0x56, (byte) 0x22, (byte) 0x56,
        (byte) 0x23, (byte) 0x56, (byte) 0x24, (byte) 0x56,
        (byte) 0x25, (byte) 0x56, (byte) 0x26, (byte) 0x56,
        (byte) 0x27, (byte) 0x56, (byte) 0x28, (byte) 0x56,
        (byte) 0x29, (byte) 0x56, (byte) 0x2a, (byte) 0x56,
        (byte) 0x2b, (byte) 0x56, (byte) 0x2c, (byte) 0x56,
        (byte) 0x2d, (byte) 0x56, (byte) 0x2e, (byte) 0x56,
        (byte) 0x2f, (byte) 0x56, (byte) 0x30, (byte) 0x56,
        (byte) 0x31, (byte) 0x56, (byte) 0x32, (byte) 0x56,
        (byte) 0x33, (byte) 0x56, (byte) 0x34, (byte) 0x56,
        (byte) 0x35, (byte) 0x56, (byte) 0x36, (byte) 0x56,
        (byte) 0x37, (byte) 0x56, (byte) 0x38, (byte) 0x56,
        (byte) 0x39, (byte) 0x56, (byte) 0x3a, (byte) 0x56,
        (byte) 0x3b, (byte) 0x56, (byte) 0x3c, (byte) 0x56,
        (byte) 0x3d, (byte) 0x56, (byte) 0x3e, (byte) 0x56,
        (byte) 0x3f, (byte) 0x56, (byte) 0x40, (byte) 0x56,
        (byte) 0x41, (byte) 0x56, (byte) 0x42, (byte) 0x56,
        (byte) 0x43, (byte) 0x56, (byte) 0x44, (byte) 0x56,
        (byte) 0x45, (byte) 0x56, (byte) 0x46, (byte) 0x56,
        (byte) 0x47, (byte) 0x56, (byte) 0x48, (byte) 0x56,
        (byte) 0x49, (byte) 0x56, (byte) 0x4a, (byte) 0x56,
        (byte) 0x4b, (byte) 0x56, (byte) 0x4c, (byte) 0x56,
        (byte) 0x4d, (byte) 0x56, (byte) 0x4e, (byte) 0x56,
        (byte) 0x4f, (byte) 0x56, (byte) 0x50, (byte) 0x56,
        (byte) 0x51, (byte) 0x56, (byte) 0x52, (byte) 0x56,
        (byte) 0x53, (byte) 0x56, (byte) 0x54, (byte) 0x56,
        (byte) 0x55, (byte) 0x56, (byte) 0x56, (byte) 0x56,
        (byte) 0x57, (byte) 0x56, (byte) 0x58, (byte) 0x56,
        (byte) 0x59, (byte) 0x56, (byte) 0x5a, (byte) 0x56,
        (byte) 0x5b, (byte) 0x56, (byte) 0x5c, (byte) 0x56,
        (byte) 0x5d, (byte) 0x56, (byte) 0x5e, (byte) 0x56,
        (byte) 0x5f, (byte) 0x56, (byte) 0x60, (byte) 0x56,
        (byte) 0x61, (byte) 0x56, (byte) 0x62, (byte) 0x56,
        (byte) 0x63, (byte) 0x56, (byte) 0x64, (byte) 0x56,
        (byte) 0x65, (byte) 0x56, (byte) 0x66, (byte) 0x56,
        (byte) 0x67, (byte) 0x56, (byte) 0x68, (byte) 0x56,
        (byte) 0x69, (byte) 0x56, (byte) 0x6a, (byte) 0x56,
        (byte) 0x6b, (byte) 0x56, (byte) 0x6c, (byte) 0x56,
        (byte) 0x6d, (byte) 0x56, (byte) 0x6e, (byte) 0x56,
        (byte) 0x6f, (byte) 0x56, (byte) 0x70, (byte) 0x56,
        (byte) 0x71, (byte) 0x56, (byte) 0x72, (byte) 0x56,
        (byte) 0x73, (byte) 0x56, (byte) 0x74, (byte) 0x56,
        (byte) 0x75, (byte) 0x56, (byte) 0x76, (byte) 0x56,
        (byte) 0x77, (byte) 0x56, (byte) 0x78, (byte) 0x56,
        (byte) 0x79, (byte) 0x56, (byte) 0x7a, (byte) 0x56,
        (byte) 0x7b, (byte) 0x56, (byte) 0x7c, (byte) 0x56,
        (byte) 0x7d, (byte) 0x56, (byte) 0x7e, (byte) 0x57,
        (byte) 0x21, (byte) 0x57, (byte) 0x22, (byte) 0x57,
        (byte) 0x23, (byte) 0x57, (byte) 0x24, (byte) 0x57,
        (byte) 0x25, (byte) 0x57, (byte) 0x26, (byte) 0x57,
        (byte) 0x27, (byte) 0x57, (byte) 0x28, (byte) 0x57,
        (byte) 0x29, (byte) 0x57, (byte) 0x2a, (byte) 0x57,
        (byte) 0x2b, (byte) 0x57, (byte) 0x2c, (byte) 0x57,
        (byte) 0x2d, (byte) 0x57, (byte) 0x2e, (byte) 0x57,
        (byte) 0x2f, (byte) 0x57, (byte) 0x30, (byte) 0x57,
        (byte) 0x31, (byte) 0x57, (byte) 0x32, (byte) 0x57,
        (byte) 0x33, (byte) 0x57, (byte) 0x34, (byte) 0x57,
        (byte) 0x35, (byte) 0x57, (byte) 0x36, (byte) 0x57,
        (byte) 0x37, (byte) 0x57, (byte) 0x38, (byte) 0x57,
        (byte) 0x39, (byte) 0x57, (byte) 0x3a, (byte) 0x57,
        (byte) 0x3b, (byte) 0x57, (byte) 0x3c, (byte) 0x57,
        (byte) 0x3d, (byte) 0x57, (byte) 0x3e, (byte) 0x57,
        (byte) 0x3f, (byte) 0x57, (byte) 0x40, (byte) 0x57,
        (byte) 0x41, (byte) 0x57, (byte) 0x42, (byte) 0x57,
        (byte) 0x43, (byte) 0x57, (byte) 0x44, (byte) 0x57,
        (byte) 0x45, (byte) 0x57, (byte) 0x46, (byte) 0x57,
        (byte) 0x47, (byte) 0x57, (byte) 0x48, (byte) 0x57,
        (byte) 0x49, (byte) 0x57, (byte) 0x4a, (byte) 0x57,
        (byte) 0x4b, (byte) 0x57, (byte) 0x4c, (byte) 0x57,
        (byte) 0x4d, (byte) 0x57, (byte) 0x4e, (byte) 0x57,
        (byte) 0x4f, (byte) 0x57, (byte) 0x50, (byte) 0x57,
        (byte) 0x51, (byte) 0x57, (byte) 0x52, (byte) 0x57,
        (byte) 0x53, (byte) 0x57, (byte) 0x54, (byte) 0x57,
        (byte) 0x55, (byte) 0x57, (byte) 0x56, (byte) 0x57,
        (byte) 0x57, (byte) 0x57, (byte) 0x58, (byte) 0x57,
        (byte) 0x59, (byte) 0x57, (byte) 0x5a, (byte) 0x57,
        (byte) 0x5b, (byte) 0x57, (byte) 0x5c, (byte) 0x57,
        (byte) 0x5d, (byte) 0x57, (byte) 0x5e, (byte) 0x57,
        (byte) 0x5f, (byte) 0x57, (byte) 0x60, (byte) 0x57,
        (byte) 0x61, (byte) 0x57, (byte) 0x62, (byte) 0x57,
        (byte) 0x63, (byte) 0x57, (byte) 0x64, (byte) 0x59,
        (byte) 0x49, (byte) 0x59, (byte) 0x4a, (byte) 0x59,
        (byte) 0x4b, (byte) 0x59, (byte) 0x4c, (byte) 0x59,
        (byte) 0x4d, (byte) 0x59, (byte) 0x4e, (byte) 0x59,
        (byte) 0x4f, (byte) 0x59, (byte) 0x50, (byte) 0x59,
        (byte) 0x51, (byte) 0x59, (byte) 0x52, (byte) 0x59,
        (byte) 0x53, (byte) 0x59, (byte) 0x54, (byte) 0x59,
        (byte) 0x55, (byte) 0x59, (byte) 0x56, (byte) 0x59,
        (byte) 0x57, (byte) 0x59, (byte) 0x58, (byte) 0x59,
        (byte) 0x59, (byte) 0x59, (byte) 0x5a, (byte) 0x59,
        (byte) 0x5b, (byte) 0x59, (byte) 0x5c, (byte) 0x59,
        (byte) 0x5d, (byte) 0x59, (byte) 0x5e, (byte) 0x59,
        (byte) 0x5f, (byte) 0x59, (byte) 0x60, (byte) 0x59,
        (byte) 0x61, (byte) 0x59, (byte) 0x62, (byte) 0x59,
        (byte) 0x63, (byte) 0x59, (byte) 0x64, (byte) 0x59,
        (byte) 0x65, (byte) 0x59, (byte) 0x66, (byte) 0x59,
        (byte) 0x67, (byte) 0x59, (byte) 0x68, (byte) 0x59,
        (byte) 0x69, (byte) 0x59, (byte) 0x6a, (byte) 0x59,
        (byte) 0x6b, (byte) 0x59, (byte) 0x6c, (byte) 0x59,
        (byte) 0x6d, (byte) 0x59, (byte) 0x6e, (byte) 0x59,
        (byte) 0x6f, (byte) 0x59, (byte) 0x70, (byte) 0x59,
        (byte) 0x71, (byte) 0x59, (byte) 0x72, (byte) 0x59,
        (byte) 0x73, (byte) 0x59, (byte) 0x74, (byte) 0x59,
        (byte) 0x75, (byte) 0x59, (byte) 0x76, (byte) 0x59,
        (byte) 0x77, (byte) 0x59, (byte) 0x78, (byte) 0x59,
        (byte) 0x79, (byte) 0x59, (byte) 0x7a, (byte) 0x59,
        (byte) 0x7b, (byte) 0x59, (byte) 0x7c, (byte) 0x59,
        (byte) 0x7d, (byte) 0x59, (byte) 0x7e, (byte) 0x5a,
        (byte) 0x21, (byte) 0x5a, (byte) 0x22, (byte) 0x5a,
        (byte) 0x23, (byte) 0x5a, (byte) 0x24, (byte) 0x5a,
        (byte) 0x25, (byte) 0x5a, (byte) 0x26, (byte) 0x5a,
        (byte) 0x27, (byte) 0x5a, (byte) 0x28, (byte) 0x5a,
        (byte) 0x29, (byte) 0x5a, (byte) 0x2a, (byte) 0x5a,
        (byte) 0x2b, (byte) 0x5a, (byte) 0x2c, (byte) 0x5a,
        (byte) 0x2d, (byte) 0x5a, (byte) 0x2e, (byte) 0x5a,
        (byte) 0x2f, (byte) 0x5a, (byte) 0x30, (byte) 0x5a,
        (byte) 0x31, (byte) 0x5a, (byte) 0x32, (byte) 0x5a,
        (byte) 0x33, (byte) 0x5a, (byte) 0x34, (byte) 0x5a,
        (byte) 0x35, (byte) 0x5a, (byte) 0x36, (byte) 0x5a,
        (byte) 0x37, (byte) 0x5a, (byte) 0x38, (byte) 0x5a,
        (byte) 0x39, (byte) 0x5a, (byte) 0x3a, (byte) 0x5a,
        (byte) 0x3b, (byte) 0x5a, (byte) 0x3c, (byte) 0x5a,
        (byte) 0x3d, (byte) 0x5a, (byte) 0x3e, (byte) 0x5a,
        (byte) 0x3f, (byte) 0x5a, (byte) 0x40, (byte) 0x5a,
        (byte) 0x41, (byte) 0x5a, (byte) 0x42, (byte) 0x5b,
        (byte) 0x35, (byte) 0x5b, (byte) 0x36, (byte) 0x5b,
        (byte) 0x37, (byte) 0x5b, (byte) 0x38, (byte) 0x5b,
        (byte) 0x39, (byte) 0x5b, (byte) 0x3a, (byte) 0x5b,
        (byte) 0x3b, (byte) 0x5b, (byte) 0x3c, (byte) 0x5b,
        (byte) 0x3d, (byte) 0x5b, (byte) 0x3e, (byte) 0x5b,
        (byte) 0x3f, (byte) 0x5b, (byte) 0x40, (byte) 0x5b,
        (byte) 0x41, (byte) 0x5b, (byte) 0x42, (byte) 0x5b,
        (byte) 0x43, (byte) 0x5b, (byte) 0x44, (byte) 0x5b,
        (byte) 0x45, (byte) 0x5b, (byte) 0x46, (byte) 0x5b,
        (byte) 0x47, (byte) 0x5b, (byte) 0x48, (byte) 0x5b,
        (byte) 0x49, (byte) 0x5b, (byte) 0x4a, (byte) 0x5b,
        (byte) 0x4b, (byte) 0x5b, (byte) 0x4c, (byte) 0x5b,
        (byte) 0x4d, (byte) 0x5b, (byte) 0x4e, (byte) 0x5b,
        (byte) 0x4f, (byte) 0x5b, (byte) 0x50, (byte) 0x5b,
        (byte) 0x51, (byte) 0x5b, (byte) 0x52, (byte) 0x5b,
        (byte) 0x53, (byte) 0x5b, (byte) 0x54, (byte) 0x5b,
        (byte) 0x55, (byte) 0x5b, (byte) 0x56, (byte) 0x5b,
        (byte) 0x57, (byte) 0x5b, (byte) 0x58, (byte) 0x5b,
        (byte) 0x59, (byte) 0x5b, (byte) 0x5a, (byte) 0x5b,
        (byte) 0x5b, (byte) 0x5b, (byte) 0x5c, (byte) 0x5b,
        (byte) 0x5d, (byte) 0x5b, (byte) 0x5e, (byte) 0x5b,
        (byte) 0x5f, (byte) 0x5b, (byte) 0x60, (byte) 0x5b,
        (byte) 0x61, (byte) 0x5b, (byte) 0x62, (byte) 0x5b,
        (byte) 0x63, (byte) 0x5b, (byte) 0x64, (byte) 0x5b,
        (byte) 0x65, (byte) 0x5b, (byte) 0x66, (byte) 0x5b,
        (byte) 0x67, (byte) 0x5b, (byte) 0x68, (byte) 0x5b,
        (byte) 0x69, (byte) 0x5b, (byte) 0x6a, (byte) 0x5b,
        (byte) 0x6b, (byte) 0x5b, (byte) 0x6c, (byte) 0x5b,
        (byte) 0x6d, (byte) 0x5b, (byte) 0x6e, (byte) 0x5b,
        (byte) 0x6f, (byte) 0x5b, (byte) 0x70, (byte) 0x5b,
        (byte) 0x71, (byte) 0x5b, (byte) 0x72, (byte) 0x5b,
        (byte) 0x73, (byte) 0x5b, (byte) 0x74, (byte) 0x5b,
        (byte) 0x75, (byte) 0x5b, (byte) 0x76, (byte) 0x5b,
        (byte) 0x77, (byte) 0x5b, (byte) 0x78, (byte) 0x5b,
        (byte) 0x79, (byte) 0x5b, (byte) 0x7a, (byte) 0x5b,
        (byte) 0x7b, (byte) 0x5b, (byte) 0x7c, (byte) 0x5b,
        (byte) 0x7d, (byte) 0x5b, (byte) 0x7e, (byte) 0x5c,
        (byte) 0x21, (byte) 0x5c, (byte) 0x22, (byte) 0x5c,
        (byte) 0x23, (byte) 0x5c, (byte) 0x24, (byte) 0x5c,
        (byte) 0x25, (byte) 0x5c, (byte) 0x26, (byte) 0x5c,
        (byte) 0x27, (byte) 0x5c, (byte) 0x28, (byte) 0x5c,
        (byte) 0x29, (byte) 0x5c, (byte) 0x2a, (byte) 0x5c,
        (byte) 0x2b, (byte) 0x5c, (byte) 0x2c, (byte) 0x5c,
        (byte) 0x2d, (byte) 0x5c, (byte) 0x2e, (byte) 0x5c,
        (byte) 0x2f, (byte) 0x5c, (byte) 0x30, (byte) 0x5c,
        (byte) 0x31, (byte) 0x5c, (byte) 0x32, (byte) 0x5c,
        (byte) 0x33, (byte) 0x5c, (byte) 0x34, (byte) 0x5c,
        (byte) 0x35, (byte) 0x5c, (byte) 0x36, (byte) 0x5d,
        (byte) 0x79, (byte) 0x5d, (byte) 0x7a, (byte) 0x5d,
        (byte) 0x7b, (byte) 0x5d, (byte) 0x7c, (byte) 0x5d,
        (byte) 0x7d, (byte) 0x5d, (byte) 0x7e, (byte) 0x5e,
        (byte) 0x21, (byte) 0x5e, (byte) 0x22, (byte) 0x5e,
        (byte) 0x23, (byte) 0x5e, (byte) 0x24, (byte) 0x5e,
        (byte) 0x25, (byte) 0x5e, (byte) 0x26, (byte) 0x5e,
        (byte) 0x27, (byte) 0x5e, (byte) 0x28, (byte) 0x5e,
        (byte) 0x29, (byte) 0x5e, (byte) 0x2a, (byte) 0x5e,
        (byte) 0x2b, (byte) 0x5e, (byte) 0x2c, (byte) 0x5e,
        (byte) 0x2d, (byte) 0x5e, (byte) 0x2e, (byte) 0x5e,
        (byte) 0x2f, (byte) 0x5e, (byte) 0x30, (byte) 0x5e,
        (byte) 0x31, (byte) 0x5e, (byte) 0x32, (byte) 0x5e,
        (byte) 0x33, (byte) 0x5e, (byte) 0x34, (byte) 0x5e,
        (byte) 0x35, (byte) 0x5e, (byte) 0x36, (byte) 0x5e,
        (byte) 0x37, (byte) 0x5e, (byte) 0x38, (byte) 0x5e,
        (byte) 0x39, (byte) 0x5e, (byte) 0x3a, (byte) 0x5e,
        (byte) 0x3b, (byte) 0x5e, (byte) 0x3c, (byte) 0x5e,
        (byte) 0x3d, (byte) 0x5e, (byte) 0x3e, (byte) 0x5e,
        (byte) 0x3f, (byte) 0x5e, (byte) 0x40, (byte) 0x5e,
        (byte) 0x41, (byte) 0x5e, (byte) 0x42, (byte) 0x5e,
        (byte) 0x43, (byte) 0x5e, (byte) 0x44, (byte) 0x5e,
        (byte) 0x45, (byte) 0x5e, (byte) 0x46, (byte) 0x5e,
        (byte) 0x47, (byte) 0x5e, (byte) 0x48, (byte) 0x5e,
        (byte) 0x49, (byte) 0x5e, (byte) 0x4a, (byte) 0x60,
        (byte) 0x30, (byte) 0x60, (byte) 0x31, (byte) 0x60,
        (byte) 0x32, (byte) 0x60, (byte) 0x33, (byte) 0x60,
        (byte) 0x34, (byte) 0x60, (byte) 0x35, (byte) 0x60,
        (byte) 0x36, (byte) 0x60, (byte) 0x37, (byte) 0x60,
        (byte) 0x38, (byte) 0x60, (byte) 0x39, (byte) 0x60,
        (byte) 0x3a, (byte) 0x60, (byte) 0x3b, (byte) 0x60,
        (byte) 0x3c, (byte) 0x60, (byte) 0x3d, (byte) 0x60,
        (byte) 0x3e, (byte) 0x60, (byte) 0x3f, (byte) 0x73,
        (byte) 0x26, (byte) 0x73, (byte) 0x27, (byte) 0x73,
        (byte) 0x28, (byte) 0x73, (byte) 0x29, (byte) 0x73,
        (byte) 0x2a, (byte) 0x73, (byte) 0x2b, (byte) 0x73,
        (byte) 0x2c, (byte) 0x73, (byte) 0x2d, (byte) 0x73,
        (byte) 0x2e, (byte) 0x73, (byte) 0x2f, (byte) 0x73,
        (byte) 0x30, (byte) 0x73, (byte) 0x31, (byte) 0x73,
        (byte) 0x32, (byte) 0x73, (byte) 0x33, (byte) 0x73,
        (byte) 0x34, (byte) 0x73, (byte) 0x35, (byte) 0x73,
        (byte) 0x36, (byte) 0x73, (byte) 0x37, (byte) 0x73,
        (byte) 0x38, (byte) 0x73, (byte) 0x39, (byte) 0x73,
        (byte) 0x3a, (byte) 0x73, (byte) 0x3b, (byte) 0x73,
        (byte) 0x3c, (byte) 0x73, (byte) 0x3d, (byte) 0x73,
        (byte) 0x3e, (byte) 0x73, (byte) 0x3f, (byte) 0x73,
        (byte) 0x40, (byte) 0x73, (byte) 0x41, (byte) 0x73,
        (byte) 0x42, (byte) 0x73, (byte) 0x43, (byte) 0x73,
        (byte) 0x44, (byte) 0x73, (byte) 0x45, (byte) 0x73,
        (byte) 0x46, (byte) 0x73, (byte) 0x47, (byte) 0x73,
        (byte) 0x48, (byte) 0x73, (byte) 0x49, (byte) 0x73,
        (byte) 0x4a, (byte) 0x73, (byte) 0x4b, (byte) 0x73,
        (byte) 0x4c, (byte) 0x73, (byte) 0x4d, (byte) 0x73,
        (byte) 0x4e, (byte) 0x73, (byte) 0x4f, (byte) 0x73,
        (byte) 0x50, (byte) 0x73, (byte) 0x51, (byte) 0x73,
        (byte) 0x52, (byte) 0x73, (byte) 0x53, (byte) 0x73,
        (byte) 0x54, (byte) 0x73, (byte) 0x55, (byte) 0x73,
        (byte) 0x56, (byte) 0x73, (byte) 0x57, (byte) 0x73,
        (byte) 0x58, (byte) 0x73, (byte) 0x59, (byte) 0x73,
        (byte) 0x5a, (byte) 0x73, (byte) 0x5b, (byte) 0x73,
        (byte) 0x5c, (byte) 0x73, (byte) 0x5d, (byte) 0x73,
        (byte) 0x5e, (byte) 0x73, (byte) 0x5f, (byte) 0x73,
        (byte) 0x60, (byte) 0x73, (byte) 0x61, (byte) 0x73,
        (byte) 0x62, (byte) 0x73, (byte) 0x63, (byte) 0x73,
        (byte) 0x64, (byte) 0x73, (byte) 0x65, (byte) 0x73,
        (byte) 0x66, (byte) 0x73, (byte) 0x67, (byte) 0x73,
        (byte) 0x68, (byte) 0x73, (byte) 0x69, (byte) 0x73,
        (byte) 0x6a, (byte) 0x73, (byte) 0x6b, (byte) 0x73,
        (byte) 0x6c, (byte) 0x73, (byte) 0x6d, (byte) 0x73,
        (byte) 0x6e, (byte) 0x73, (byte) 0x6f, (byte) 0x73,
        (byte) 0x70, (byte) 0x73, (byte) 0x71, (byte) 0x73,
        (byte) 0x72, (byte) 0x73, (byte) 0x73, (byte) 0x73,
        (byte) 0x74, (byte) 0x73, (byte) 0x75, (byte) 0x73,
        (byte) 0x76, (byte) 0x73, (byte) 0x77, (byte) 0x73,
        (byte) 0x78, (byte) 0x73, (byte) 0x79, (byte) 0x73,
        (byte) 0x7a, (byte) 0x73, (byte) 0x7b, (byte) 0x73,
        (byte) 0x7c, (byte) 0x73, (byte) 0x7d, (byte) 0x73,
        (byte) 0x7e, (byte) 0x74, (byte) 0x21, (byte) 0x74,
        (byte) 0x22, (byte) 0x74, (byte) 0x23, (byte) 0x74,
        (byte) 0x24, (byte) 0x74, (byte) 0x25, (byte) 0x74,
        (byte) 0x26, (byte) 0x1b, (byte) 0x28, (byte) 0x49,
        (byte) 0x21, (byte) 0x22, (byte) 0x23, (byte) 0x24,
        (byte) 0x25, (byte) 0x26, (byte) 0x27, (byte) 0x28,
        (byte) 0x29, (byte) 0x2a, (byte) 0x2b, (byte) 0x2c,
        (byte) 0x2d, (byte) 0x2e, (byte) 0x2f, (byte) 0x30,
        (byte) 0x31, (byte) 0x32, (byte) 0x33, (byte) 0x34,
        (byte) 0x35, (byte) 0x36, (byte) 0x37, (byte) 0x38,
        (byte) 0x39, (byte) 0x3a, (byte) 0x3b, (byte) 0x3c,
        (byte) 0x3d, (byte) 0x3e, (byte) 0x3f, (byte) 0x40,
        (byte) 0x41, (byte) 0x42, (byte) 0x43, (byte) 0x44,
        (byte) 0x45, (byte) 0x46, (byte) 0x47, (byte) 0x48,
        (byte) 0x49, (byte) 0x4a, (byte) 0x4b, (byte) 0x4c,
        (byte) 0x4d, (byte) 0x4e, (byte) 0x4f, (byte) 0x50,
        (byte) 0x51, (byte) 0x52, (byte) 0x53, (byte) 0x54,
        (byte) 0x55, (byte) 0x56, (byte) 0x57, (byte) 0x58,
        (byte) 0x59, (byte) 0x5a, (byte) 0x5b, (byte) 0x5c,
        (byte) 0x5d, (byte) 0x5e, (byte) 0x5f, (byte) 0x1b,
        (byte) 0x28, (byte) 0x42 };

    private final static String MIXEDCONTENT =
        "JA\u3000\u3002\u0062\uFF64PAN" +
        "\uFF0C\uFF0E\u00A5\uFF65\uFF66X\u203E" +
        "\u30FB\uFF67\u203E";

    static byte[] mixedBytesExpected = {
        (byte) 0x4a, (byte) 0x41, (byte) 0x1b, (byte) 0x24,
        (byte) 0x42, (byte) 0x21, (byte) 0x21, (byte) 0x21,
        (byte) 0x23, (byte) 0x1b, (byte) 0x28, (byte) 0x42,
        (byte) 0x62, (byte) 0x1b, (byte) 0x28, (byte) 0x49,
        (byte) 0x24, (byte) 0x1b, (byte) 0x28, (byte) 0x42,
        (byte) 0x50, (byte) 0x41, (byte) 0x4e, (byte) 0x1b,
        (byte) 0x24, (byte) 0x42, (byte) 0x21, (byte) 0x24,
        (byte) 0x21, (byte) 0x25, (byte) 0x1b, (byte) 0x28,
        (byte) 0x4a, (byte) 0x5c, (byte) 0x1b, (byte) 0x28,
        (byte) 0x49, (byte) 0x25, (byte) 0x26, (byte) 0x1b,
        (byte) 0x28, (byte) 0x42, (byte) 0x58, (byte) 0x1b,
        (byte) 0x28, (byte) 0x4a, (byte) 0x7e, (byte) 0x1b,
        (byte) 0x24, (byte) 0x42, (byte) 0x21, (byte) 0x26,
        (byte) 0x1b, (byte) 0x28, (byte) 0x49, (byte) 0x27,
        (byte) 0x1b, (byte) 0x28, (byte) 0x4a, (byte) 0x7e,
        (byte) 0x1b, (byte) 0x28, (byte) 0x42  };

    static byte[] repeatingEscapes = {
        (byte) 0x4a, (byte) 0x41, (byte) 0x1b, (byte) 0x24,
        (byte) 0x42, (byte)0x1b, (byte)0x24, (byte)0x42,
        (byte) 0x21, (byte) 0x21, (byte) 0x21,
        (byte) 0x23, (byte) 0x1b, (byte) 0x28, (byte) 0x42,
        // embedded repeated iso-2022 escapes (see bugID 4879522)
        (byte)0x1b, (byte)0x28, (byte)0x42,
        (byte) 0x62, (byte) 0x1b, (byte) 0x28, (byte) 0x49,
        (byte)0x0f, (byte)0x0e, (byte)0x0f,
        (byte)0x1b, (byte)0x28, (byte)0x49,
        (byte) 0x24, (byte) 0x1b, (byte) 0x28, (byte) 0x42,
        (byte) 0x50, (byte) 0x41, (byte) 0x4e,
        // embedded shift chars (see bugID 4879522)
        (byte)0x0e, (byte)0x0f,
        (byte) 0x1b,
        (byte) 0x24, (byte) 0x42, (byte) 0x21, (byte) 0x24,
        (byte) 0x21, (byte) 0x25, (byte) 0x1b, (byte) 0x28,
        (byte) 0x4a, (byte) 0x5c, (byte) 0x1b, (byte) 0x28,
        (byte) 0x49, (byte) 0x25, (byte) 0x26, (byte) 0x1b,
        (byte) 0x28, (byte) 0x42, (byte) 0x58, (byte) 0x1b,
        (byte) 0x28, (byte) 0x4a, (byte) 0x7e, (byte) 0x1b,
        (byte) 0x24, (byte) 0x42, (byte) 0x21, (byte) 0x26,
        (byte) 0x1b, (byte) 0x28, (byte) 0x49, (byte) 0x27,
        (byte) 0x1b, (byte) 0x28, (byte) 0x4a, (byte) 0x7e,
        (byte) 0x1b, (byte) 0x28, (byte) 0x42  };


    private static String JISX0212 =
        "˘梖龥";

    private static byte[] expectedBytes_JISX0212 = {
        (byte)0x1b, (byte)0x24, (byte)0x28, (byte)0x44,
        (byte)0x22, (byte)0x2f, (byte)0x43, (byte)0x6f,
        (byte)0x6d, (byte)0x63,
        (byte)0x1b, (byte)0x28, (byte)0x42
    };

    /*
     * Tests the roundtrip integrity and expected encoding
     * correctness for a String containing a substantial
     * subset of ISO-2022-JP/ISO-2022-JP-2 encodeable chars
     */

    private static void roundTrip(String testStr, byte[] expectBytes,
                                  String csName)
    throws Exception {
        byte[] encodedBytes = testStr.getBytes(csName);

        if (encodedBytes.length != expectBytes.length) {
            throw new Exception(csName + " Encoder error");
        }

        for (int i = 0; i < expectBytes.length; i++) {
            if (encodedBytes[i] != expectBytes[i])  {
                throw new Exception(csName + " Encoder error");
            }
        }
        String decoded = new String(encodedBytes, csName);

        if (!decoded.equals(testStr)) {
            throw new Exception(csName + " Decoder error");
        }
        String decoded2 = new String(repeatingEscapes, csName);
        if (!decoded2.equals(MIXEDCONTENT)) {
            throw new Exception(csName + " Decoder error");
        }
     }

    public static void main(String[] args) throws Exception {

        // Long String containing sequential chars
        // ASCII/yen/tilde/jisx0208 chars/katakana chars

        String testStr1 = US_ASCII +
                        JISX0208SUBSET + JISX0202KATAKANA;
        roundTrip(testStr1, expectedBytes1, "ISO-2022-JP");
        roundTrip(testStr1, expectedBytes1, "ISO-2022-JP-2");
        roundTrip(JISX0212, expectedBytes_JISX0212, "ISO-2022-JP-2");

        // mixed chars which encode to the supported codesets
        // of ISO-2022-JP/ISO-2022-JP-2

        String testStr2 = MIXEDCONTENT;
        roundTrip(testStr2 , mixedBytesExpected, "ISO-2022-JP");
        roundTrip(testStr2 , mixedBytesExpected, "ISO-2022-JP-2");

        String decoded2 = new String(repeatingEscapes, "ISO-2022-JP");
        if (!decoded2.equals(MIXEDCONTENT)) {
            throw new Exception("ISO-2022-JP Decoder error");
        }

        decoded2 = new String(repeatingEscapes, "ISO-2022-JP-2");
        if (!decoded2.equals(MIXEDCONTENT)) {
            throw new Exception("ISO-2022-JP-2 Decoder error");
        }

        // Test for bugID 4913711
        // ISO-2022-JP encoding of a single input char yields
        // 8 output bytes. Prior to fix for 4913711 the
        // max bytes per char value was underspecified as 5.0
        // and the code below would have thrown a BufferOverflow
        // exception. This test validates the fix for 4913711

        String testStr3 = "\u3042";
        byte[] expected = { (byte)0x1b, (byte)0x24, (byte)0x42,
                            (byte)0x24, (byte)0x22, (byte)0x1b,
                            (byte)0x28, (byte)0x42 };
        byte[] encoded = testStr3.getBytes("ISO-2022-JP");
        for (int i = 0; i < expected.length; i++) {
            if (encoded[i] != expected[i])
               throw new Exception("ISO-2022-JP Decoder error");
        }

        // Test for 7 c2b codepoints in ms932 iso2022jp
        String testStr4 = "\u00b8\u00b7\u00af\u00ab\u00bb\u3094\u00b5";
        expected = new byte[] {
                     (byte)0x1b, (byte)0x24, (byte)0x42,
                     (byte)0x21, (byte)0x24,
                     (byte)0x21, (byte)0x26,
                     (byte)0x21, (byte)0x31,
                     (byte)0x22, (byte)0x63,
                     (byte)0x22, (byte)0x64,
                     (byte)0x25, (byte)0x74,
                     (byte)0x26, (byte)0x4c,
                     (byte)0x1b, (byte)0x28, (byte)0x42 };
        encoded = testStr4.getBytes("x-windows-iso2022jp");
        if (!Arrays.equals(encoded, expected)) {
               throw new Exception("MSISO2022JP Encoder error");
        }
        // Test for 10 non-roundtrip characters in ms932 iso2022jp
        encoded = new byte[] {
            (byte)0x1B, (byte)0x24, (byte)0x42,
            (byte)0x22, (byte)0x4C,
            (byte)0x22, (byte)0x5D,
            (byte)0x22, (byte)0x65,
            (byte)0x22, (byte)0x69,
            (byte)0x2D, (byte)0x70,
            (byte)0x2D, (byte)0x71,
            (byte)0x2D, (byte)0x77,
            (byte)0x2D, (byte)0x7A,
            (byte)0x2D, (byte)0x7B,
            (byte)0x2D, (byte)0x7C,
            (byte)0x1B, (byte)0x28, (byte)0x42,
        };
        String expectedStr = "\uffe2\u22a5\u221a\u222b\u2252\u2261\u2220\u2235\u2229\u222a";
        if (!new String(encoded, "x-windows-iso2022jp").equals(expectedStr)) {
               throw new Exception("MSISO2022JP Decoder error");
        }
        // Test for 11 iso2022jp decoder
        encoded = new byte[] {
            (byte)0x1B, (byte)0x28, (byte)0x49, (byte)0x60,
            (byte)0x1B, (byte)0x28, (byte)0x42,
        };
        String unexpectedStr = "\uffa0";
        expectedStr = "\ufffd";
        if (new String(encoded, "ISO2022JP").equals(unexpectedStr)) {
               throw new Exception("ISO2022JP Decoder error: \\uFFA0");
        } else if (!new String(encoded, "ISO2022JP").equals(expectedStr)) {
               throw new Exception("ISO2022JP Decoder error: \\uFFFD");
        }
    }
}
