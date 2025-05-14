/*
 * Copyright (c) 2007, 2022, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

package com.bar;

import java.util.*;
import java.util.spi.*;

import com.foobar.Utils;

public class TimeZoneNameProviderImpl extends TimeZoneNameProvider {
    static Locale[] avail = {Locale.of("ja", "JP", "osaka"),
                        Locale.of("ja", "JP", "kyoto"),
                        Locale.of("xx"),
                        Locale.JAPAN};

    static String[][] zoneOsaka = {
        {"GMT",
         "グ_リ_ニ_ッ_ジ_標_準_時_や_。",
         "G_M_T_や_。",
         "グ_リ_ニ_ッ_ジ_標_準_時_や_。",
         "G_M_T_や_。"},
        {"JST",
         "や_ま_と_標_準_時_や_。",
         "J_S_T_や_。",
         "や_ま_と_標_準_時_や_。",
         "J_S_T_や_。"},
        {"America/Los_Angeles",
         "太_平_洋_標_準_時_や_。",
         "P_S_T_や_。",
         "太_平_洋_夏_時_間_や_。",
         "P_D_T_や_。"},
        {"SystemV/PST8",
         "太_平_洋_標_準_時_や_。",
         "P_S_T_や_。",
         "太_平_洋_夏_時_間_や_。",
         "P_D_T_や_。"},
        {"SystemV/PST8PDT",
         "太_平_洋_標_準_時_や_。",
         "P_S_T_や_。",
         "太_平_洋_夏_時_間_や_。",
         "P_D_T_や_。"},
        {"PST8PDT",
         "太_平_洋_標_準_時_や_。",
         "P_S_T_や_。",
         "太_平_洋_夏_時_間_や_。",
         "P_D_T_や_。"},
    };

    static String[][] zoneKyoto = {
        {"GMT",
         "グ_リ_ニ_ッ_ジ_標_準_時_ど_す_。",
         "G_M_T_ど_す_。",
         "グ_リ_ニ_ッ_ジ_標_準_時_ど_す_。",
         "G_M_T_ど_す_。"},
        {"America/Los_Angeles",
         "太_平_洋_標_準_時_ど_す_。",
         "P_S_T_ど_す_。",
         "太_平_洋_夏_時_間_ど_す_。",
         "P_D_T_ど_す_。"},
        {"SystemV/PST8",
         "太_平_洋_標_準_時_ど_す_。",
         "P_S_T_ど_す_。",
         "太_平_洋_夏_時_間_ど_す_。",
         "P_D_T_ど_す_。"},
        {"SystemV/PST8PDT",
         "太_平_洋_標_準_時_ど_す_。",
         "P_S_T_ど_す_。",
         "太_平_洋_夏_時_間_ど_す_。",
         "P_D_T_ど_す_。"},
        {"PST8PDT",
         "太_平_洋_標_準_時_ど_す_。",
         "P_S_T_ど_す_。",
         "太_平_洋_夏_時_間_ど_す_。",
         "P_D_T_ど_す_。"},
    };

    static String[][] zoneXX = {
        {"GMT",
         "グ_リ_ニ_ッ_ジ_標_準_時ばつばつ。",
         "G_M_T_ばつばつ。",
         "グ_リ_ニ_ッ_ジ_標_準_時ばつばつ。",
         "G_M_T_ばつばつ。"},
        {"America/Los_Angeles",
         "太_平_洋_標_準_時_ばつばつ。",
         "P_S_T_ばつばつ。",
         "太_平_洋_夏_時_間_ばつばつ。",
         "P_D_T_ばつばつ。"}};

    static String[][] zoneJaJP = {
        {"GMT",
         "グ_リ_ニ_ッ_ジ_標_準_時_で_す_。",
         "G_M_T_で_す_。",
         "グ_リ_ニ_ッ_ジ_標_準_時_で_す_。",
         "G_M_T_で_す_。"},
        {"America/Los_Angeles",
         "グ_リ_ニ_ッ_ジ_標_準_時_で_す_。",
         "P_S_T_で_す_。",
         "太_平_洋_夏_時_間_で_す_。",
         "P_D_T_で_す_。"}};

    static String[][][] names = {zoneOsaka, zoneKyoto, zoneXX, zoneJaJP};

    public Locale[] getAvailableLocales() {
        return avail;
    }

    public String getDisplayName(String id, boolean dst, int style, Locale language) {
        if (!Utils.supportsLocale(Arrays.asList(avail), language)) {
            throw new IllegalArgumentException("locale is not one of available locales: "+language);
        }

        for (int i = 0; i < avail.length; i ++) {
            if (Utils.supportsLocale(avail[i], language)) {
                String[][] namesForALocale = names[i];
                for (int j = 0; j < namesForALocale.length; j++) {
                    String[] array = namesForALocale[j];
                    if (id.equals(array[0])) {
                        String ret = array[(style==TimeZone.LONG?0:1)+(dst?2:0)+1];
                        return ret;
                    }
                }
            }
        }
        return null;
    }
}
