/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @bug 8261847
 * @summary test the output of the toString method of records with a large number of components
 * @run testng BigRecordsToStringTest
 */

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.List;
import java.util.function.Supplier;

import org.testng.annotations.*;
import static org.testng.Assert.*;

@Test
public class BigRecordsToStringTest {
    record BigInt(
            int i1,int i2,int i3,int i4,int i5,int i6,int i7,int i8,int i9,int i10,
            int i11,int i12,int i13,int i14,int i15,int i16,int i17,int i18,int i19,int i20,
            int i21,int i22,int i23,int i24,int i25,int i26,int i27,int i28,int i29,int i30,
            int i31,int i32,int i33,int i34,int i35,int i36,int i37,int i38,int i39,int i40,
            int i41,int i42,int i43,int i44,int i45,int i46,int i47,int i48,int i49,int i50,
            int i51,int i52,int i53,int i54,int i55,int i56,int i57,int i58,int i59,int i60,
            int i61,int i62,int i63,int i64,int i65,int i66,int i67,int i68,int i69,int i70,
            int i71,int i72,int i73,int i74,int i75,int i76,int i77,int i78,int i79,int i80,
            int i81,int i82,int i83,int i84,int i85,int i86,int i87,int i88,int i89,int i90,
            int i91,int i92,int i93,int i94,int i95,int i96,int i97,int i98,int i99,int i100,
            int i101,int i102,int i103,int i104,int i105,int i106,int i107,int i108,int i109,int i110,
            int i111,int i112,int i113,int i114,int i115,int i116,int i117,int i118,int i119,int i120,
            int i121,int i122,int i123,int i124,int i125,int i126,int i127,int i128,int i129,int i130,
            int i131,int i132,int i133,int i134,int i135,int i136,int i137,int i138,int i139,int i140,
            int i141,int i142,int i143,int i144,int i145,int i146,int i147,int i148,int i149,int i150,
            int i151,int i152,int i153,int i154,int i155,int i156,int i157,int i158,int i159,int i160,
            int i161,int i162,int i163,int i164,int i165,int i166,int i167,int i168,int i169,int i170,
            int i171,int i172,int i173,int i174,int i175,int i176,int i177,int i178,int i179,int i180,
            int i181,int i182,int i183,int i184,int i185,int i186,int i187,int i188,int i189,int i190,
            int i191,int i192,int i193,int i194,int i195,int i196,int i197,int i198,int i199, int i200,
            int i201,int i202,int i203,int i204,int i205,int i206,int i207,int i208,int i209,int i210,
            int i211,int i212,int i213,int i214,int i215,int i216,int i217,int i218,int i219,int i220,
            int i221,int i222,int i223,int i224,int i225,int i226,int i227,int i228,int i229,int i230,
            int i231,int i232,int i233,int i234,int i235,int i236,int i237,int i238,int i239,int i240,
            int i241,int i242,int i243,int i244,int i245,int i246,int i247,int i248,int i249,int i250,
            int i251,int i252,int i253,int i254
    ) {}

    BigInt bigInt= new BigInt(
            1,2,3,4,5,6,7,8,9,10,
            11,12,13,14,15,16,17,18,19,20,
            21,22,23,24,25,26,27,28,29,30,
            31,32,33,34,35,36,37,38,39,40,
            41,42,43,44,45,46,47,48,49,50,
            51,52,53,54,55,56,57,58,59,60,
            61,62,63,64,65,66,67,68,69,70,
            71,72,73,74,75,76,77,78,79,80,
            81,82,83,84,85,86,87,88,89,90,
            91,92,93,94,95,96,97,98,99,100,
            101,102,103,104,105,106,107,108,109,110,
            111,112,113,114,115,116,117,118,119,120,
            121,122,123,124,125,126,127,128,129,130,
            131,132,133,134,135,136,137,138,139,140,
            141,142,143,144,145,146,147,148,149,150,
            151,152,153,154,155,156,157,158,159,160,
            161,162,163,164,165,166,167,168,169,170,
            171,172,173,174,175,176,177,178,179,180,
            181,182,183,184,185,186,187,188,189,190,
            191,192,193,194,195,196,197,198,199, 200,
            201,202,203,204,205,206,207,208,209,210,
            211,212,213,214,215,216,217,218,219,220,
            221,222,223,224,225,226,227,228,229,230,
            231,232,233,234,235,236,237,238,239,240,
            241,242,243,244,245,246,247,248,249,250,
            251,252,253,254
    );

    record BigLong(
            long i1,long i2,long i3,long i4,long i5,long i6,long i7,long i8,long i9,long i10,
            long i11,long i12,long i13,long i14,long i15,long i16,long i17,long i18,long i19,long i20,
            long i21,long i22,long i23,long i24,long i25,long i26,long i27,long i28,long i29,long i30,
            long i31,long i32,long i33,long i34,long i35,long i36,long i37,long i38,long i39,long i40,
            long i41,long i42,long i43,long i44,long i45,long i46,long i47,long i48,long i49,long i50,
            long i51,long i52,long i53,long i54,long i55,long i56,long i57,long i58,long i59,long i60,
            long i61,long i62,long i63,long i64,long i65,long i66,long i67,long i68,long i69,long i70,
            long i71,long i72,long i73,long i74,long i75,long i76,long i77,long i78,long i79,long i80,
            long i81,long i82,long i83,long i84,long i85,long i86,long i87,long i88,long i89,long i90,
            long i91,long i92,long i93,long i94,long i95,long i96,long i97,long i98,long i99,long i100,
            long i101,long i102,long i103,long i104,long i105,long i106,long i107,long i108,long i109,long i110,
            long i111,long i112,long i113,long i114,long i115,long i116,long i117,long i118,long i119,long i120,
            long i121,long i122,long i123,long i124,long i125,long i126,long i127
    ) {}

    BigLong bigLong = new BigLong(
            1,2,3,4,5,6,7,8,9,10,
            11,12,13,14,15,16,17,18,19,20,
            21,22,23,24,25,26,27,28,29,30,
            31,32,33,34,35,36,37,38,39,40,
            41,42,43,44,45,46,47,48,49,50,
            51,52,53,54,55,56,57,58,59,60,
            61,62,63,64,65,66,67,68,69,70,
            71,72,73,74,75,76,77,78,79,80,
            81,82,83,84,85,86,87,88,89,90,
            91,92,93,94,95,96,97,98,99,100,
            101,102,103,104,105,106,107,108,109,110,
            111,112,113,114,115,116,117,118,119,120,
            121,122,123,124,125,126,127
    );

    private static final String BIG_INT_TO_STRING_OUTPUT =
        "BigInt[i1=1, i2=2, i3=3, i4=4, i5=5, i6=6, i7=7, i8=8, i9=9, i10=10, i11=11, i12=12, i13=13, i14=14, i15=15, i16=16, " +
            "i17=17, i18=18, i19=19, i20=20, i21=21, i22=22, i23=23, i24=24, i25=25, i26=26, i27=27, i28=28, i29=29, i30=30, " +
            "i31=31, i32=32, i33=33, i34=34, i35=35, i36=36, i37=37, i38=38, i39=39, i40=40, i41=41, i42=42, i43=43, i44=44, " +
            "i45=45, i46=46, i47=47, i48=48, i49=49, i50=50, i51=51, i52=52, i53=53, i54=54, i55=55, i56=56, i57=57, i58=58, " +
            "i59=59, i60=60, i61=61, i62=62, i63=63, i64=64, i65=65, i66=66, i67=67, i68=68, i69=69, i70=70, i71=71, i72=72, " +
            "i73=73, i74=74, i75=75, i76=76, i77=77, i78=78, i79=79, i80=80, i81=81, i82=82, i83=83, i84=84, i85=85, i86=86, " +
            "i87=87, i88=88, i89=89, i90=90, i91=91, i92=92, i93=93, i94=94, i95=95, i96=96, i97=97, i98=98, i99=99, i100=100, " +
            "i101=101, i102=102, i103=103, i104=104, i105=105, i106=106, i107=107, i108=108, i109=109, i110=110, i111=111, i112=112, " +
            "i113=113, i114=114, i115=115, i116=116, i117=117, i118=118, i119=119, i120=120, i121=121, i122=122, i123=123, i124=124, " +
            "i125=125, i126=126, i127=127, i128=128, i129=129, i130=130, i131=131, i132=132, i133=133, i134=134, i135=135, i136=136, " +
            "i137=137, i138=138, i139=139, i140=140, i141=141, i142=142, i143=143, i144=144, i145=145, i146=146, i147=147, i148=148, " +
            "i149=149, i150=150, i151=151, i152=152, i153=153, i154=154, i155=155, i156=156, i157=157, i158=158, i159=159, i160=160, " +
            "i161=161, i162=162, i163=163, i164=164, i165=165, i166=166, i167=167, i168=168, i169=169, i170=170, i171=171, i172=172, " +
            "i173=173, i174=174, i175=175, i176=176, i177=177, i178=178, i179=179, i180=180, i181=181, i182=182, i183=183, i184=184, " +
            "i185=185, i186=186, i187=187, i188=188, i189=189, i190=190, i191=191, i192=192, i193=193, i194=194, i195=195, i196=196, " +
            "i197=197, i198=198, i199=199, i200=200, i201=201, i202=202, i203=203, i204=204, i205=205, i206=206, i207=207, i208=208, " +
            "i209=209, i210=210, i211=211, i212=212, i213=213, i214=214, i215=215, i216=216, i217=217, i218=218, i219=219, i220=220, " +
            "i221=221, i222=222, i223=223, i224=224, i225=225, i226=226, i227=227, i228=228, i229=229, i230=230, i231=231, i232=232, " +
            "i233=233, i234=234, i235=235, i236=236, i237=237, i238=238, i239=239, i240=240, i241=241, i242=242, i243=243, i244=244, " +
            "i245=245, i246=246, i247=247, i248=248, i249=249, i250=250, i251=251, i252=252, i253=253, i254=254]";

    private static final String BIG_LONG_TO_STRING_OUTPUT =
        "BigLong[i1=1, i2=2, i3=3, i4=4, i5=5, i6=6, i7=7, i8=8, i9=9, i10=10, i11=11, i12=12, i13=13, i14=14, i15=15, i16=16, i17=17, " +
            "i18=18, i19=19, i20=20, i21=21, i22=22, i23=23, i24=24, i25=25, i26=26, i27=27, i28=28, i29=29, i30=30, i31=31, i32=32, i33=33, " +
            "i34=34, i35=35, i36=36, i37=37, i38=38, i39=39, i40=40, i41=41, i42=42, i43=43, i44=44, i45=45, i46=46, i47=47, i48=48, i49=49, " +
            "i50=50, i51=51, i52=52, i53=53, i54=54, i55=55, i56=56, i57=57, i58=58, i59=59, i60=60, i61=61, i62=62, i63=63, i64=64, i65=65, " +
            "i66=66, i67=67, i68=68, i69=69, i70=70, i71=71, i72=72, i73=73, i74=74, i75=75, i76=76, i77=77, i78=78, i79=79, i80=80, i81=81, " +
            "i82=82, i83=83, i84=84, i85=85, i86=86, i87=87, i88=88, i89=89, i90=90, i91=91, i92=92, i93=93, i94=94, i95=95, i96=96, i97=97, " +
            "i98=98, i99=99, i100=100, i101=101, i102=102, i103=103, i104=104, i105=105, i106=106, i107=107, i108=108, i109=109, i110=110, " +
            "i111=111, i112=112, i113=113, i114=114, i115=115, i116=116, i117=117, i118=118, i119=119, i120=120, i121=121, i122=122, i123=123, " +
            "i124=124, i125=125, i126=126, i127=127]";

    public void testToStringOutput() {
        assertTrue(bigInt.toString().equals(BIG_INT_TO_STRING_OUTPUT));
        assertTrue(bigLong.toString().equals(BIG_LONG_TO_STRING_OUTPUT));
    }
}
