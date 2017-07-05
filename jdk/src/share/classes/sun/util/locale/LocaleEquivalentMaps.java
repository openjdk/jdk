/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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

package sun.util.locale;

import java.util.HashMap;
import java.util.Map;

/**
 * Locale equivalent map for BCP47 Locale matching
 */
final class LocaleEquivalentMaps {

    static final Map<String, String> singleEquivMap;
    static final Map<String, String[]> multiEquivsMap;
    static final Map<String, String> regionVariantEquivMap;

    static {
        singleEquivMap = new HashMap<>();
        multiEquivsMap = new HashMap<>();
        regionVariantEquivMap = new HashMap<>();

        // This is an auto-generated file and should not be manually edited.
        //   LSR Revision: 2013-09-23
        singleEquivMap.put("acn", "xia");
        singleEquivMap.put("adx", "pcr");
        singleEquivMap.put("ami", "i-ami");
        singleEquivMap.put("art-lojban", "jbo");
        singleEquivMap.put("ase", "sgn-us");
        singleEquivMap.put("ayx", "nun");
        singleEquivMap.put("bfi", "sgn-gb");
        singleEquivMap.put("bjd", "drl");
        singleEquivMap.put("bnn", "i-bnn");
        singleEquivMap.put("bzs", "sgn-br");
        singleEquivMap.put("cir", "meg");
        singleEquivMap.put("cjr", "mom");
        singleEquivMap.put("cka", "cmr");
        singleEquivMap.put("cmk", "xch");
        singleEquivMap.put("cmn-hans", "zh-cmn-hans");
        singleEquivMap.put("cmn-hant", "zh-cmn-hant");
        singleEquivMap.put("cmr", "cka");
        singleEquivMap.put("csn", "sgn-co");
        singleEquivMap.put("dev", "gav");
        singleEquivMap.put("drh", "khk");
        singleEquivMap.put("drl", "bjd");
        singleEquivMap.put("dse", "sgn-nl");
        singleEquivMap.put("dsl", "sgn-dk");
        singleEquivMap.put("fsl", "sgn-fr");
        singleEquivMap.put("gal", "ilw");
        singleEquivMap.put("gan", "zh-gan");
        singleEquivMap.put("gav", "dev");
        singleEquivMap.put("gsg", "sgn-de");
        singleEquivMap.put("gss", "sgn-gr");
        singleEquivMap.put("he", "iw");
        singleEquivMap.put("hle", "sca");
        singleEquivMap.put("hrr", "jal");
        singleEquivMap.put("hsn", "zh-xiang");
        singleEquivMap.put("i-ami", "ami");
        singleEquivMap.put("i-bnn", "bnn");
        singleEquivMap.put("i-klingon", "tlh");
        singleEquivMap.put("i-lux", "lb");
        singleEquivMap.put("i-navajo", "nv");
        singleEquivMap.put("i-pwn", "pwn");
        singleEquivMap.put("i-tao", "tao");
        singleEquivMap.put("i-tay", "tay");
        singleEquivMap.put("i-tsu", "tsu");
        singleEquivMap.put("ibi", "opa");
        singleEquivMap.put("id", "in");
        singleEquivMap.put("ilw", "gal");
        singleEquivMap.put("in", "id");
        singleEquivMap.put("ise", "sgn-it");
        singleEquivMap.put("isg", "sgn-ie");
        singleEquivMap.put("iw", "he");
        singleEquivMap.put("jal", "hrr");
        singleEquivMap.put("jbo", "art-lojban");
        singleEquivMap.put("ji", "yi");
        singleEquivMap.put("jsl", "sgn-jp");
        singleEquivMap.put("jv", "jw");
        singleEquivMap.put("jw", "jv");
        singleEquivMap.put("kgh", "kml");
        singleEquivMap.put("khk", "drh");
        singleEquivMap.put("kml", "kgh");
        singleEquivMap.put("lb", "i-lux");
        singleEquivMap.put("lcq", "ppr");
        singleEquivMap.put("lrr", "yma");
        singleEquivMap.put("meg", "cir");
        singleEquivMap.put("mfs", "sgn-mx");
        singleEquivMap.put("mo", "ro");
        singleEquivMap.put("mom", "cjr");
        singleEquivMap.put("nan", "zh-min-nan");
        singleEquivMap.put("nb", "no-bok");
        singleEquivMap.put("ncs", "sgn-ni");
        singleEquivMap.put("nn", "no-nyn");
        singleEquivMap.put("no-bok", "nb");
        singleEquivMap.put("no-nyn", "nn");
        singleEquivMap.put("nsl", "sgn-no");
        singleEquivMap.put("nun", "ayx");
        singleEquivMap.put("nv", "i-navajo");
        singleEquivMap.put("opa", "ibi");
        singleEquivMap.put("pcr", "adx");
        singleEquivMap.put("ppr", "lcq");
        singleEquivMap.put("psr", "sgn-pt");
        singleEquivMap.put("pwn", "i-pwn");
        singleEquivMap.put("ras", "tie");
        singleEquivMap.put("ro", "mo");
        singleEquivMap.put("sca", "hle");
        singleEquivMap.put("sfb", "sgn-be-fr");
        singleEquivMap.put("sfs", "sgn-za");
        singleEquivMap.put("sgg", "sgn-ch-de");
        singleEquivMap.put("sgn-be-fr", "sfb");
        singleEquivMap.put("sgn-be-nl", "vgt");
        singleEquivMap.put("sgn-br", "bzs");
        singleEquivMap.put("sgn-ch-de", "sgg");
        singleEquivMap.put("sgn-co", "csn");
        singleEquivMap.put("sgn-de", "gsg");
        singleEquivMap.put("sgn-dk", "dsl");
        singleEquivMap.put("sgn-es", "ssp");
        singleEquivMap.put("sgn-fr", "fsl");
        singleEquivMap.put("sgn-gb", "bfi");
        singleEquivMap.put("sgn-gr", "gss");
        singleEquivMap.put("sgn-ie", "isg");
        singleEquivMap.put("sgn-it", "ise");
        singleEquivMap.put("sgn-jp", "jsl");
        singleEquivMap.put("sgn-mx", "mfs");
        singleEquivMap.put("sgn-ni", "ncs");
        singleEquivMap.put("sgn-nl", "dse");
        singleEquivMap.put("sgn-no", "nsl");
        singleEquivMap.put("sgn-pt", "psr");
        singleEquivMap.put("sgn-se", "swl");
        singleEquivMap.put("sgn-us", "ase");
        singleEquivMap.put("sgn-za", "sfs");
        singleEquivMap.put("ssp", "sgn-es");
        singleEquivMap.put("swl", "sgn-se");
        singleEquivMap.put("tao", "i-tao");
        singleEquivMap.put("tay", "i-tay");
        singleEquivMap.put("tie", "ras");
        singleEquivMap.put("tkk", "twm");
        singleEquivMap.put("tlh", "i-klingon");
        singleEquivMap.put("tlw", "weo");
        singleEquivMap.put("tsu", "i-tsu");
        singleEquivMap.put("twm", "tkk");
        singleEquivMap.put("vgt", "sgn-be-nl");
        singleEquivMap.put("weo", "tlw");
        singleEquivMap.put("wuu", "zh-wuu");
        singleEquivMap.put("xch", "cmk");
        singleEquivMap.put("xia", "acn");
        singleEquivMap.put("yi", "ji");
        singleEquivMap.put("yma", "lrr");
        singleEquivMap.put("yos", "zom");
        singleEquivMap.put("yue", "zh-yue");
        singleEquivMap.put("zh-cmn-hans", "cmn-hans");
        singleEquivMap.put("zh-cmn-hant", "cmn-hant");
        singleEquivMap.put("zh-gan", "gan");
        singleEquivMap.put("zh-min-nan", "nan");
        singleEquivMap.put("zh-wuu", "wuu");
        singleEquivMap.put("zh-xiang", "hsn");
        singleEquivMap.put("zh-yue", "yue");
        singleEquivMap.put("zom", "yos");

        multiEquivsMap.put("ccq", new String[] {"rki", "ybd"});
        multiEquivsMap.put("cmn", new String[] {"zh-guoyu", "zh-cmn"});
        multiEquivsMap.put("drw", new String[] {"prs", "tnf"});
        multiEquivsMap.put("hak", new String[] {"i-hak", "zh-hakka"});
        multiEquivsMap.put("i-hak", new String[] {"hak", "zh-hakka"});
        multiEquivsMap.put("mry", new String[] {"mst", "myt"});
        multiEquivsMap.put("mst", new String[] {"mry", "myt"});
        multiEquivsMap.put("myt", new String[] {"mry", "mst"});
        multiEquivsMap.put("prs", new String[] {"drw", "tnf"});
        multiEquivsMap.put("rki", new String[] {"ccq", "ybd"});
        multiEquivsMap.put("tnf", new String[] {"prs", "drw"});
        multiEquivsMap.put("ybd", new String[] {"rki", "ccq"});
        multiEquivsMap.put("zh-cmn", new String[] {"cmn", "zh-guoyu"});
        multiEquivsMap.put("zh-guoyu", new String[] {"cmn", "zh-cmn"});
        multiEquivsMap.put("zh-hakka", new String[] {"hak", "i-hak"});

        regionVariantEquivMap.put("-alalc97", "-heploc");
        regionVariantEquivMap.put("-bu", "-mm");
        regionVariantEquivMap.put("-cd", "-zr");
        regionVariantEquivMap.put("-dd", "-de");
        regionVariantEquivMap.put("-de", "-dd");
        regionVariantEquivMap.put("-fr", "-fx");
        regionVariantEquivMap.put("-fx", "-fr");
        regionVariantEquivMap.put("-heploc", "-alalc97");
        regionVariantEquivMap.put("-mm", "-bu");
        regionVariantEquivMap.put("-tl", "-tp");
        regionVariantEquivMap.put("-tp", "-tl");
        regionVariantEquivMap.put("-yd", "-ye");
        regionVariantEquivMap.put("-ye", "-yd");
        regionVariantEquivMap.put("-zr", "-cd");
    }

}
