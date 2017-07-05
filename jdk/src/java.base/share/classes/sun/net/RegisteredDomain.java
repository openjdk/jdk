/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package sun.net;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/*
 * The naming tables listed below were gathered from publicly available data such as
 * the subdomain registration websites listed for each top-level domain by the Internet
 * Assigned Numbers Authority and the website of the Internet Corporation for Assigned Names
 * and Numbers as well as Wikipedia.
 */

public class RegisteredDomain {

// XX.AA
private static Set<String> top1Set = new HashSet<String>(Arrays.asList("asia", "biz", "cat", "coop",
        "edu", "info", "gov", "jobs", "travel", "am", "aq", "ax", "cc", "cf", "cg", "ch", "cv", "cz",
        "de", "dj", "dk", "fm", "fo", "ga", "gd", "gf", "gl", "gm", "gq", "gs", "gw", "hm",
        "li", "lu", "md", "mh", "mil", "mobi", "mq", "ms", "ms", "ne", "nl", "nu", "si",
        "sm", "sr", "su", "tc", "td", "tf", "tg", "tk", "tm", "tv", "va", "vg",
        /* ae */ "xn--mgbaam7a8h",      /* cn s */      "xn--fiqs8s",        /* cn t */ "xn--fiqz9s",
        /* eg */ "xn--wgbh1c",          /* hk */        "xn--j6w193g",       /* jo */   "xn--mgbayh7gpa",
        /* lk */ "xn--fzc2c9e2c",       /* ps */        "xn--ygbi2ammx",     /* ru */   "xn--p1ai",
        /* qa */ "xn--wgbl6a",          /* sa */        "xn--mgberp4a5d4ar", /* sg */   "xn--yfro4i67o",
        /* th */ "xn--o3cw4h",          /* tn */        "xn--pgbs0dh",       /* tw s */ "xn--kpry57d",
        /* tw */ "xn--kprw13d",         /* sg tamil */  "xn--clchc0ea0b2g2a9gcd"));

// common pattern: XX.AA or XX.GOV.AA
private static Set<String> top2Set = new HashSet<String>(Arrays.asList("as", "bf", "cd", "cx",
        "ie", "lt", "mr", "tl"));

// common pattern: XX.AA or XX.COM.AA or XX.EDU.AA or XX.NET.AA or XX.ORG.AA or XX.GOV.AA
private static Set<String> top4Set = new HashSet<String>(Arrays.asList("af", "bm", "bs", "bt",
        "bz", "dm", "ky", "lb", "lr", "mo", "sc", "sl", "ws"));

// AA or less than 3 other XX.BB.AA possible matches
private static Set<String> top3Set = new HashSet<String>(Arrays.asList("ad", "aw", "be", "bw",
        "cl", "fi", "int", "io", "mc"));

// AA.UK exceptions
private static Set<String> ukSet = new HashSet<String>(Arrays.asList( "bl", "british-library",
        "jet", "nhs", "nls", "parliament", "mod", "police"));

// AA.AR exceptions
private static Set<String> arSet = new HashSet<String>(Arrays.asList( "argentina", "educ",
        "gobiernoelectronico", "nic", "promocion", "retina", "uba"));

// AA.OM exceptions
private static Set<String> omSet = new HashSet<String>(Arrays.asList("mediaphone", "nawrastelecom",
        "nawras", "omanmobile", "omanpost", "omantel", "rakpetroleum", "siemens", "songfest",
        "statecouncil", "shura", "peie", "omran", "omnic", "omanet", "oman", "muriya", "kom"));

// any XX.BB.AA
private static Set<String> top5Set = new HashSet<String>(Arrays.asList("au", "arpa", "bd", "bn", "ck",
         "cy", "er", "et", "fj", "fk", "gt", "gu", "il", "jm", "ke", "kh", "kw",
         "mm", "mt", "mz", "ni", "np", "nz", "pg", "sb", "sv", "tz", "uy", "ve", "ye",
         "za", "zm", "zw"));

// XX.CC.BB.JP
private static Set<String> jpSet = new HashSet<String>(Arrays.asList("aichi", "akita", "aomori",
        "chiba", "ehime", "fukui", "fukuoka", "fukushima", "gifu", "gunma", "hiroshima", "hokkaido",
        "hyogo", "ibaraki", "ishikawa", "iwate", "kagawa", "kagoshima", "kanagawa", "kawasaki",
        "kitakyushu", "kobe", "kochi", "kumamoto", "kyoto", "mie", "miyagi", "miyazaki", "nagano",
        "nagasaki", "nagoya", "nara", "niigata", "oita", "okayama", "okinawa", "osaka", "saga",
        "saitama", "sapporo", "sendai", "shiga", "shimane", "shizuoka", "tochigi", "tokushima",
        "tokyo", "tottori", "toyama", "wakayama", "yamagata", "yamaguchi", "yamanashi", "yokohama"));

// CC.BB.JP exceptions
private static Set<String> jp2Set = new HashSet<String>(Arrays.asList("metro.tokyo.jp",
        "pref.aichi.jp", "pref.akita.jp", "pref.aomori.jp", "pref.chiba.jp", "pref.ehime.jp",
        "pref.fukui.jp", "pref.fukuoka.jp", "pref.fukushima.jp", "pref.gifu.jp", "pref.gunma.jp",
        "pref.hiroshima.jp", "pref.hokkaido.jp", "pref.hyogo.jp", "pref.ibaraki.jp", "pref.ishikawa.jp",
        "pref.iwate.jp", "pref.kagawa.jp", "pref.kagoshima.jp", "pref.kanagawa.jp", "pref.kochi.jp",
        "pref.kumamoto.jp", "pref.kyoto.jp", "pref.mie.jp", "pref.miyagi.jp", "pref.miyazaki.jp",
        "pref.nagano.jp", "pref.nagasaki.jp", "pref.nara.jp", "pref.niigata.jp", "pref.oita.jp",
        "pref.okayama.jp", "pref.okinawa.jp", "pref.osaka.jp", "pref.saga.jp", "pref.saitama.jp",
        "pref.shiga.jp", "pref.shimane.jp", "pref.shizuoka.jp", "pref.tochigi.jp", "pref.tokushima.jp",
        "pref.tottori.jp", "pref.toyama.jp", "pref.wakayama.jp", "pref.yamagata.jp", "pref.yamaguchi.jp",
        "pref.yamanashi.jp", "city.chiba.jp", "city.fukuoka.jp", "city.hamamatsu.jp", "city.hiroshima.jp", "city.kawasaki.jp",
        "city.kitakyushu.jp", "city.kobe.jp", "city.kyoto.jp", "city.nagoya.jp", "city.niigata.jp",
        "city.okayama.jp", "city.osaka.jp", "city.sagamihara.jp", "city.saitama.jp", "city.sapporo.jp", "city.sendai.jp",
        "city.shizuoka.jp", "city.yokohama.jp"));

private static Set<String>  usStateSet = new HashSet<String>(Arrays.asList("ak",
                "al", "ar", "as", "az", "ca", "co", "ct", "dc", "de", "fl", "ga", "gu", "hi", "ia",
                "id", "il", "in", "ks", "ky", "la", "ma", "md", "me", "mi", "mn", "mo", "ms", "mt",
                "nc", "nd", "ne", "nh", "nj", "nm", "nv", "ny", "oh", "ok", "or", "pa", "pr", "ri",
                "sc", "sd", "tn", "tx", "ut", "vi", "vt", "va", "wa", "wi", "wv", "wy"));

private static Set<String>  usSubStateSet = new HashSet<String>(Arrays.asList("state",
                "lib", "k12", "cc", "tec", "gen", "cog", "mus", "dst"));

private static Map<String,Set<String>> topMap = new HashMap<>();
private static Map<String,Set<String>> top3Map = new HashMap<>();

static {
    /*
     * XX.AA or XX.BB.AA
     */
    topMap.put("ac", new HashSet<String>(Arrays.asList("com", "co", "edu", "gov", "net", "mil", "org")));
    topMap.put("ae", new HashSet<String>(Arrays.asList("co", "net", "org", "sch", "ac", "gov", "mil")));
    topMap.put("aero", new HashSet<String>(Arrays.asList("accident-investigation",
                "accident-prevention", "aerobatic", "aeroclub", "aerodrome", "agents", "aircraft",
                "airline", "airport", "air-surveillance", "airtraffic", "air-traffic-control",
                "ambulance", "amusement", "association", "author", "ballooning", "broker", "caa",
                "cargo", "catering", "certification", "championship", "charter", "civilaviation",
                "club", "conference", "consultant", "consulting", "control", "council", "crew",
                "design", "dgca", "educator", "emergency", "engine", "engineer", "entertainment",
                "equipment", "exchange", "express", "federation", "flight", "freight", "fuel",
                "gliding", "government", "groundhandling", "group", "hanggliding", "homebuilt",
                "insurance", "journal", "journalist", "leasing", "logistics", "magazine",
                "maintenance", "marketplace", "media", "microlight", "modelling", "navigation",
                "parachuting", "paragliding", "passenger-association", "pilot", "press", "production",
                "recreation", "repbody", "res", "research", "rotorcraft", "safety", "scientist",
                "services", "show", "skydiving", "software", "student", "taxi", "trader", "trading",
                "trainer", "union", "workinggroup", "works" )));
    topMap.put( "ag", new HashSet<String>(Arrays.asList("com", "org", "net", "co", "nom")));
    topMap.put( "ai", new HashSet<String>(Arrays.asList("off", "com", "net", "org")));
    topMap.put( "al", new HashSet<String>(Arrays.asList("com", "edu", "gov", "mil", "net", "org")));
    topMap.put( "an", new HashSet<String>(Arrays.asList("com")));
    topMap.put( "ao", new HashSet<String>(Arrays.asList("ed", "gv", "og", "co", "pb", "it")));
    topMap.put( "at", new HashSet<String>(Arrays.asList("ac", "co", "gv", "or", "biz", "info", "priv")));
    topMap.put( "az", new HashSet<String>(Arrays.asList("com", "net", "int", "gov", "org", "edu", "info",
                "pp", "mil", "name", "biz")));
    topMap.put( "ba", new HashSet<String>(Arrays.asList("org", "net", "edu", "gov", "mil", "unbi",
                "unmo", "unsa", "untz", "unze", "co", "com", "rs")));
    topMap.put( "bb", new HashSet<String>(Arrays.asList("biz", "com", "edu", "gov", "info", "net", "org",
                "store")));
    topMap.put( "bg", new HashSet<String>(Arrays.asList("a", "b", "c", "d", "e", "f", "g", "h", "i", "j",
                "k", "l", "m", "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z", "0", "1",
                "2", "3", "4", "5", "6", "7", "8", "9")));
    topMap.put( "bh", new HashSet<String>(Arrays.asList("com", "info", "cc", "edu", "biz", "net",
                "org", "gov")));
    topMap.put( "bi", new HashSet<String>(Arrays.asList("co", "com", "edu", "gov", "info", "or", "org")));
    topMap.put( "bj", new HashSet<String>(Arrays.asList("asso", "barreau", "com", "edu", "gouv", "gov", "mil")));
    topMap.put( "bo", new HashSet<String>(Arrays.asList("com", "edu", "gov", "gob", "int", "org", "net",
                 "mil", "tv")));
    topMap.put( "br", new HashSet<String>(Arrays.asList("adm", "adv", "agr", "am", "arq", "art", "ato",
                "b", "bio", "blog", "bmd", "cim", "cng", "cnt", "com", "coop", "ecn", "edu", "emp", "eng",
                "esp", "etc", "eti", "far", "flog", "fm", "fnd", "fot", "fst", "g12", "ggf", "gov",
                "imb", "ind", "inf", "jor", "jus", "lel", "mat", "med", "mil", "mus", "net", "nom",
                "not", "ntr", "odo", "org", "ppg", "pro", "psc", "psi", "qsl", "radio", "rec", "slg",
                "srv", "taxi", "teo", "tmp", "trd", "tur", "tv", "vet", "vlog", "wiki", "zlg")));
    topMap.put( "bw", new HashSet<String>(Arrays.asList("co", "gov", "org")));
    topMap.put( "by", new HashSet<String>(Arrays.asList("gov", "mil", "com", "of")));
    topMap.put( "ca", new HashSet<String>(Arrays.asList("ab", "bc", "mb", "nb", "nf",
                "nl", "ns", "nt", "nu", "on", "pe", "qc", "sk", "yk", "gc")));
    topMap.put( "ci", new HashSet<String>(Arrays.asList("org", "or", "com", "co", "edu",
                "ed", "ac", "net", "go", "asso", "xn--aroport-bya", "int",
                "presse", "md", "gouv")));
    topMap.put( "com", new HashSet<String>(Arrays.asList("ad", "ar", "br", "cn", "de", "eu", "gb",
                "gr", "hu", "jpn", "kr", "no", "qc", "ru", "sa", "se", "uk", "us", "uy", "za")));
    topMap.put( "cm", new HashSet<String>(Arrays.asList("co", "com", "gov", "net")));
    topMap.put( "cn", new HashSet<String>(Arrays.asList("ac", "com", "edu", "gov", "net",
                "org", "mil", "xn--55qx5d", "xn--io0a7i",
                "ah", "bj", "cq", "fj", "gd", "gs", "gz", "gx",
                "ha", "hb", "he", "hi", "hl", "hn", "jl", "js", "jx", "ln", "nm", "nx", "qh",
                "sc", "sd", "sh", "sn", "sx", "tj", "xj", "xz", "yn", "zj", "hk", "mo", "tw")));
    topMap.put( "co", new HashSet<String>(Arrays.asList("arts", "com", "edu", "firm", "gov", "info",
                "int", "mil", "net", "nom", "org", "rec", "web")));
    topMap.put( "cr", new HashSet<String>(Arrays.asList("ac", "co", "ed", "fi", "go", "or", "sa")));
    topMap.put( "cu", new HashSet<String>(Arrays.asList("com", "edu", "org", "net", "gov", "inf")));
    topMap.put( "do", new HashSet<String>(Arrays.asList("com", "edu", "org", "net", "gov", "gob",
                "web", "art", "sld", "mil")));
    topMap.put( "dz", new HashSet<String>(Arrays.asList("com", "org", "net", "gov", "edu", "asso",
                 "pol", "art")));
    topMap.put( "ec", new HashSet<String>(Arrays.asList("com", "info", "net", "fin", "k12", "med",
                "pro", "org", "edu", "gov", "gob", "mil")));
    topMap.put( "ee", new HashSet<String>(Arrays.asList("edu", "gov", "riik", "lib", "med", "com",
                "pri", "aip", "org", "fie")));
    topMap.put( "eg", new HashSet<String>(Arrays.asList("com", "edu", "eun", "gov", "mil", "name",
                "net", "org", "sci")));
    topMap.put( "es", new HashSet<String>(Arrays.asList("com", "nom", "org", "gob", "edu")));
    topMap.put( "eu", new HashSet<String>(Arrays.asList("europa")));
    topMap.put( "fr", new HashSet<String>(Arrays.asList("com", "asso", "nom", "prd", "presse",
                "tm", "aeroport", "assedic", "avocat", "avoues", "cci", "chambagri",
                "chirurgiens-dentistes", "experts-comptables", "geometre-expert", "gouv", "greta",
                "huissier-justice", "medecin", "notaires", "pharmacien", "port", "veterinaire")));
    topMap.put( "ge", new HashSet<String>(Arrays.asList("com", "edu", "gov", "org", "mil", "net", "pvt")));
    topMap.put( "gg", new HashSet<String>(Arrays.asList("co", "org", "net", "sch", "gov")));
    topMap.put( "gh", new HashSet<String>(Arrays.asList("com", "edu", "gov", "org", "mil")));
    topMap.put( "gi", new HashSet<String>(Arrays.asList("com", "ltd", "gov", "mod", "edu", "org")));
    topMap.put( "gn", new HashSet<String>(Arrays.asList("ac", "com", "edu", "gov", "org", "net")));
    topMap.put( "gp", new HashSet<String>(Arrays.asList("com", "net", "mobi", "edu", "org", "asso")));
    topMap.put( "gr", new HashSet<String>(Arrays.asList("com", "co", "net", "edu", "org", "gov",
                "mil", "mod", "sch")));
    topMap.put( "gy", new HashSet<String>(Arrays.asList("co", "com", "net", "org", "edu", "gov")));
    topMap.put( "hk", new HashSet<String>(Arrays.asList("com", "edu", "gov", "idv", "net", "org",
                /* com */ "xn--55qx5d", /* edu */ "xn--wcvs22d", /* gov */"xn--mxtq1m",
                /* idv */ "xn--gmqw5a", /* net */ "xn--od0alg", /*org*/ "xn--uc0atv")));
    topMap.put( /* hk */  "xn--j6w193g", new HashSet<String>(Arrays.asList(
                /* com */ "xn--55qx5d", /* edu */ "xn--wcvs22d", /* gov */"xn--mxtq1m",
                /* idv */ "xn--gmqw5a", /* net */ "xn--od0alg", /*org*/ "xn--uc0atv")));
    topMap.put( "hn", new HashSet<String>(Arrays.asList("com", "edu", "org", "net", "mil", "gob")));
    topMap.put( "hr", new HashSet<String>(Arrays.asList("iz.hr", "from.hr", "name.hr", "com.hr")));
    topMap.put( "ht", new HashSet<String>(Arrays.asList("com", "shop", "firm", "info", "adult",
                "net", "pro", "org", "med", "art", "coop", "pol", "asso", "edu", "rel", "gouv", "perso")));
    topMap.put( "hu", new HashSet<String>(Arrays.asList("co", "info", "org", "priv", "sport", "tm",
                "2000", "agrar", "bolt", "casino", "city", "erotica", "erotika", "film", "forum",
                "games", "hotel", "ingatlan", "jogasz", "konyvelo", "lakas", "media", "news", "reklam",
                "sex", "shop", "suli", "szex", "tozsde", "utazas", "video")));
    topMap.put( "id", new HashSet<String>(Arrays.asList("ac", "co", "go", "mil", "net", "or", "sch",
                "web")));
    topMap.put( "im", new HashSet<String>(Arrays.asList("co.im", "com", "net.im", "gov.im", "org.im",
                "ac.im")));
    topMap.put( "in", new HashSet<String>(Arrays.asList("co", "firm", "ernet", "net", "org", "gen", "ind",
                "nic", "ac", "edu", "res", "gov", "mil")));
    topMap.put( "iq", new HashSet<String>(Arrays.asList("gov", "edu", "mil", "com", "org", "net" )));
    topMap.put( "ir", new HashSet<String>(Arrays.asList("ac", "co", "gov", "id", "net", "org", "sch"
                )));
    topMap.put( "is", new HashSet<String>(Arrays.asList("net", "com", "edu", "gov", "org", "int")));
    topMap.put( "it", new HashSet<String>(Arrays.asList("gov", "edu", "agrigento", "ag", "alessandria",
                "al", "ancona", "an", "aosta", "aoste", "ao", "arezzo", "ar", "ascoli-piceno",
                "ascolipiceno", "ap", "asti", "at", "avellino", "av", "bari", "ba",
                "andria-barletta-trani", "andriabarlettatrani", "trani-barletta-andria",
                "tranibarlettaandria", "barletta-trani-andria", "barlettatraniandria",
                "andria-trani-barletta", "andriatranibarletta", "trani-andria-barletta",
                "traniandriabarletta", "bt", "belluno", "bl", "benevento", "bn", "bergamo", "bg",
                "biella", "bi", "bologna", "bo", "bolzano", "bozen", "balsan", "alto-adige",
                "altoadige", "suedtirol", "bz", "brescia", "bs", "brindisi", "br", "cagliari",
                "ca", "caltanissetta", "cl", "campobasso", "cb", "carboniaiglesias", "carbonia-iglesias",
                "iglesias-carbonia", "iglesiascarbonia", "ci", "caserta", "ce", "catania", "ct",
                "catanzaro", "cz", "chieti", "ch", "como", "co", "cosenza", "cs", "cremona", "cr",
                "crotone", "kr", "cuneo", "cn", "dell-ogliastra", "dellogliastra", "ogliastra", "og",
                "enna", "en", "ferrara", "fe", "fermo", "fm", "firenze", "florence", "fi", "foggia",
                "fg", "forli-cesena", "forlicesena", "cesena-forli", "cesenaforli", "fc", "frosinone",
                "fr", "genova", "genoa", "ge", "gorizia", "go", "grosseto", "gr", "imperia", "im",
                "isernia", "is", "laquila", "aquila", "aq", "la-spezia", "laspezia", "sp", "latina",
                "lt", "lecce", "le", "lecco", "lc", "livorno", "li", "lodi", "lo", "lucca", "lu",
                "macerata", "mc", "mantova", "mn", "massa-carrara", "massacarrara", "carrara-massa",
                "carraramassa", "ms", "matera", "mt", "medio-campidano", "mediocampidano",
                "campidano-medio", "campidanomedio", "vs", "messina", "me", "milano", "milan",
                "mi", "modena", "mo", "monza", "monza-brianza", "monzabrianza", "monzaebrianza",
                "monzaedellabrianza", "monza-e-della-brianza", "mb", "napoli", "naples", "na",
                "novara", "no", "nuoro", "nu", "oristano", "or", "padova", "padua", "pd", "palermo",
                "pa", "parma", "pr", "pavia", "pv", "perugia", "pg", "pescara", "pe", "pesaro-urbino",
                "pesarourbino", "urbino-pesaro", "urbinopesaro", "pu", "piacenza", "pc", "pisa",
                "pi", "pistoia", "pt", "pordenone", "pn", "potenza", "pz", "prato", "po", "ragusa",
                "rg", "ravenna", "ra", "reggio-calabria", "reggiocalabria", "rc", "reggio-emilia",
                "reggioemilia", "re", "rieti", "ri", "rimini", "rn", "roma", "rome", "rm", "rovigo",
                "ro", "salerno", "sa", "sassari", "ss", "savona", "sv", "siena", "si", "siracusa",
                "sr", "sondrio", "so", "taranto", "ta", "tempio-olbia", "tempioolbia", "olbia-tempio",
                "olbiatempio", "ot", "teramo", "te", "terni", "tr", "torino", "turin", "to",
                "trapani", "tp", "trento", "trentino", "tn", "treviso", "tv", "trieste", "ts",
                "udine", "ud", "varese", "va", "venezia", "venice", "ve", "verbania", "vb",
                "vercelli", "vc", "verona", "vr", "vibo-valentia", "vibovalentia", "vv", "vicenza",
                "vi", "viterbo", "vt")));
    topMap.put( "je", new HashSet<String>(Arrays.asList("co", "org", "net", "sch", "gov")));
    topMap.put( "jo", new HashSet<String>(Arrays.asList("com", "org", "net", "edu", "sch",
                "gov", "mil", "name")));
    topMap.put( "jp", new HashSet<String>(Arrays.asList("ac", "ad", "co", "ed", "go", "gr", "lg",
                "ne", "or")));
    topMap.put( "kg", new HashSet<String>(Arrays.asList("org", "net", "com", "edu", "gov", "mil")));
    topMap.put( "ki", new HashSet<String>(Arrays.asList("edu", "biz", "net", "org", "gov",
                 "info", "com")));
    topMap.put( "km", new HashSet<String>(Arrays.asList("org", "nom", "gov", "prd", "tm", "edu",
                "mil", "ass", "com", "coop", "asso", "presse", "medecin", "notaires", "pharmaciens",
                "veterinaire", "gouv")));
    topMap.put( "kn", new HashSet<String>(Arrays.asList("net", "org", "edu", "gov")));
    topMap.put( "kp", new HashSet<String>(Arrays.asList("com", "edu", "gov", "org", "rep", "tra")));
    topMap.put( "kr", new HashSet<String>(Arrays.asList("ac", "co", "es", "go", "hs", "kg", "mil",
                "ms", "ne", "or", "pe", "re", "sc", "busan", "chungbuk", "chungnam", "daegu",
                "daejeon", "gangwon", "gwangju", "gyeongbuk", "gyeonggi", "gyeongnam", "incheon",
                "jeju", "jeonbuk", "jeonnam", "seoul", "ulsan")));
    topMap.put( "kz", new HashSet<String>(Arrays.asList("org", "edu", "net", "gov", "mil", "com")));
    topMap.put( "la", new HashSet<String>(Arrays.asList("int", "net", "info", "edu", "gov", "per",
                "com", "org", "c")));
    topMap.put( "lc", new HashSet<String>(Arrays.asList("com", "net", "co", "org", "edu", "gov",
                "l.lc", "p.lc")));
    topMap.put( "lk", new HashSet<String>(Arrays.asList("gov", "sch", "net", "int", "com", "org",
                "edu", "ngo", "soc", "web", "ltd", "assn", "grp", "hotel")));
    topMap.put( "ls", new HashSet<String>(Arrays.asList("co", "gov", "ac", "org")));
    topMap.put( "lv", new HashSet<String>(Arrays.asList("com", "edu", "gov", "org", "mil",
                "id", "net", "asn", "conf")));
    topMap.put( "ly", new HashSet<String>(Arrays.asList("com", "net", "gov", "plc", "edu", "sch",
                "med", "org", "id")));
    topMap.put( "ma", new HashSet<String>(Arrays.asList("co", "net", "gov", "org", "ac", "press")));
    topMap.put( "me", new HashSet<String>(Arrays.asList("co", "net", "org", "edu", "ac", "gov",
                "its", "priv")));
    topMap.put( "mg", new HashSet<String>(Arrays.asList("org", "nom", "gov", "prd", "tm",
                "edu", "mil", "com")));
    topMap.put( "mk", new HashSet<String>(Arrays.asList("com", "org", "net", "edu", "gov", "inf",
                "name", "pro")));
    topMap.put( "ml", new HashSet<String>(Arrays.asList("com", "edu", "gouv", "gov", "net",
                "org", "presse")));
    topMap.put( "mn", new HashSet<String>(Arrays.asList("gov", "edu", "org")));
    topMap.put( "mp", new HashSet<String>(Arrays.asList("gov", "co", "org")));
    topMap.put( "mu", new HashSet<String>(Arrays.asList("com", "net", "org", "gov", "ac",
                "co", "or")));
    topMap.put( "museum", new HashSet<String>(Arrays.asList("academy", "agriculture", "air",
                "airguard", "alabama", "alaska", "amber", "ambulance", "american", "americana",
                "americanantiques", "americanart", "amsterdam", "and", "annefrank", "anthro",
                "anthropology", "antiques", "aquarium", "arboretum", "archaeological", "archaeology",
                "architecture", "art", "artanddesign", "artcenter", "artdeco", "arteducation",
                "artgallery", "arts", "artsandcrafts", "asmatart", "assassination", "assisi",
                "association", "astronomy", "atlanta", "austin", "australia", "automotive", "aviation",
                "axis", "badajoz", "baghdad", "bahn", "bale", "baltimore", "barcelona", "baseball",
                "basel", "baths", "bauern", "beauxarts", "beeldengeluid", "bellevue", "bergbau",
                "berkeley", "berlin", "bern", "bible", "bilbao", "bill", "birdart", "birthplace",
                "bonn", "boston", "botanical", "botanicalgarden", "botanicgarden", "botany",
                "brandywinevalley", "brasil", "bristol", "british", "britishcolumbia", "broadcast",
                "brunel", "brussel", "brussels", "bruxelles", "building", "burghof", "bus", "bushey",
                "cadaques", "california", "cambridge", "can", "canada", "capebreton", "carrier",
                "cartoonart", "casadelamoneda", "castle", "castres", "celtic", "center", "chattanooga",
                "cheltenham", "chesapeakebay", "chicago", "children", "childrens", "childrensgarden",
                "chiropractic", "chocolate", "christiansburg", "cincinnati", "cinema", "circus",
                "civilisation", "civilization", "civilwar", "clinton", "clock", "coal", "coastaldefence",
                "cody", "coldwar", "collection", "colonialwilliamsburg", "coloradoplateau", "columbia",
                "columbus", "communication", "communications", "community", "computer",
                "computerhistory", "xn--comunicaes-v6a2o", "contemporary", "contemporaryart",
                "convent", "copenhagen", "corporation", "xn--correios-e-telecomunicaes-ghc29a",
                "corvette", "costume", "countryestate", "county", "crafts", "cranbrook", "creation",
                "cultural", "culturalcenter", "culture", "cyber", "cymru", "dali", "dallas", "database",
                "ddr", "decorativearts", "delaware", "delmenhorst", "denmark", "depot", "design",
                "detroit", "dinosaur", "discovery", "dolls", "donostia", "durham", "eastafrica",
                "eastcoast", "education", "educational", "egyptian", "eisenbahn", "elburg",
                "elvendrell", "embroidery", "encyclopedic", "england", "entomology", "environment",
                "environmentalconservation", "epilepsy", "essex", "estate", "ethnology", "exeter",
                "exhibition", "family", "farm", "farmequipment", "farmers", "farmstead", "field",
                "figueres", "filatelia", "film", "fineart", "finearts", "finland", "flanders", "florida",
                "force", "fortmissoula", "fortworth", "foundation", "francaise", "frankfurt",
                "franziskaner", "freemasonry", "freiburg", "fribourg", "frog", "fundacio", "furniture",
                "gallery", "garden", "gateway", "geelvinck", "gemological", "geology", "georgia",
                "giessen", "glas", "glass", "gorge", "grandrapids", "graz", "guernsey", "halloffame",
                "hamburg", "handson", "harvestcelebration", "hawaii", "health", "heimatunduhren",
                "hellas", "helsinki", "hembygdsforbund", "heritage", "histoire", "historical",
                "historicalsociety", "historichouses", "historisch", "historisches", "history",
                "historyofscience", "horology", "house", "humanities", "illustration", "imageandsound",
                "indian", "indiana", "indianapolis", "indianmarket", "intelligence", "interactive",
                "iraq", "iron", "isleofman", "jamison", "jefferson", "jerusalem", "jewelry",
                "jewish", "jewishart", "jfk", "journalism", "judaica", "judygarland", "juedisches",
                "juif", "karate", "karikatur", "kids", "koebenhavn", "koeln", "kunst", "kunstsammlung",
                "kunstunddesign", "labor", "labour", "lajolla", "lancashire", "landes", "lans",
                "xn--lns-qla", "larsson", "lewismiller", "lincoln", "linz", "living", "livinghistory",
                "localhistory", "london", "losangeles", "louvre", "loyalist", "lucerne", "luxembourg",
                "luzern", "mad", "madrid", "mallorca", "manchester", "mansion", "mansions", "manx",
                "marburg", "maritime", "maritimo", "maryland", "marylhurst", "media", "medical",
                "medizinhistorisches", "meeres", "memorial", "mesaverde", "michigan", "midatlantic",
                "military", "mill", "miners", "mining", "minnesota", "missile", "missoula", "modern",
                "moma", "money", "monmouth", "monticello", "montreal", "moscow", "motorcycle", "muenchen",
                "muenster", "mulhouse", "muncie", "museet", "museumcenter", "museumvereniging", "music",
                "national", "nationalfirearms", "nationalheritage", "nativeamerican", "naturalhistory",
                "naturalhistorymuseum", "naturalsciences", "nature", "naturhistorisches",
                "natuurwetenschappen", "naumburg", "naval", "nebraska", "neues", "newhampshire",
                "newjersey", "newmexico", "newport", "newspaper", "newyork", "niepce", "norfolk",
                "north", "nrw", "nuernberg", "nuremberg", "nyc", "nyny", "oceanographic",
                "oceanographique", "omaha", "online", "ontario", "openair", "oregon", "oregontrail",
                "otago", "oxford", "pacific", "paderborn", "palace", "paleo", "palmsprings", "panama",
                "paris", "pasadena", "pharmacy", "philadelphia", "philadelphiaarea", "philately",
                "phoenix", "photography", "pilots", "pittsburgh", "planetarium", "plantation",
                "plants", "plaza", "portal", "portland", "portlligat", "posts-and-telecommunications",
                "preservation", "presidio", "press", "project", "public", "pubol", "quebec",
                "railroad", "railway", "research", "resistance", "riodejaneiro", "rochester", "rockart",
                "roma", "russia", "saintlouis", "salem", "salvadordali", "salzburg", "sandiego",
                "sanfrancisco", "santabarbara", "santacruz", "santafe", "saskatchewan", "satx",
                "savannahga", "schlesisches", "schoenbrunn", "schokoladen", "school", "schweiz",
                "science", "scienceandhistory", "scienceandindustry", "sciencecenter", "sciencecenters",
                "science-fiction", "sciencehistory", "sciences", "sciencesnaturelles", "scotland",
                "seaport", "settlement", "settlers", "shell", "sherbrooke", "sibenik", "silk", "ski",
                "skole", "society", "sologne", "soundandvision", "southcarolina", "southwest", "space",
                "spy", "square", "stadt", "stalbans", "starnberg", "state", "stateofdelaware",
                "station", "steam", "steiermark", "stjohn", "stockholm", "stpetersburg", "stuttgart",
                "suisse", "surgeonshall", "surrey", "svizzera", "sweden", "sydney", "tank", "tcm",
                "technology", "telekommunikation", "television", "texas", "textile", "theater",
                "time", "timekeeping", "topology", "torino", "touch", "town", "transport", "tree",
                "trolley", "trust", "trustee", "uhren", "ulm", "undersea", "university", "usa",
                "usantiques", "usarts", "uscountryestate", "usculture", "usdecorativearts", "usgarden",
                "ushistory", "ushuaia", "uslivinghistory", "utah", "uvic", "valley", "vantaa",
                "versailles", "viking", "village", "virginia", "virtual", "virtuel", "vlaanderen",
                "volkenkunde", "wales", "wallonie", "war", "washingtondc", "watchandclock",
                "watch-and-clock", "western", "westfalen", "whaling", "wildlife", "williamsburg",
                "windmill", "workshop", "york", "yorkshire", "yosemite", "youth", "zoological",
                "zoology", "xn--9dbhblg6di", "xn--h1aegh")));
    topMap.put( "mv", new HashSet<String>(Arrays.asList("aero", "biz", "com", "coop", "edu", "gov",
                "info", "int", "mil", "museum", "name", "net", "org", "pro")));
    topMap.put( "mw", new HashSet<String>(Arrays.asList("ac", "biz", "co", "com", "coop", "edu",
                "gov", "int", "museum", "net", "org")));
    topMap.put( "mx", new HashSet<String>(Arrays.asList("com", "org", "gob", "edu", "net")));
    topMap.put( "my", new HashSet<String>(Arrays.asList("com", "net", "org", "gov", "edu",
                 "mil", "name", "sch")));
    topMap.put( "na", new HashSet<String>(Arrays.asList("co", "com", "org", "edu", "edunet", "net",
                "alt", "biz", "info")));
    topMap.put( "nc", new HashSet<String>(Arrays.asList("asso", "nom")));
    topMap.put( "net", new HashSet<String>(Arrays.asList("gb", "se", "uk", "za")));
    topMap.put( "ng", new HashSet<String>(Arrays.asList("name", "sch", "mil", "mobi", "com",
                "edu", "gov", "net", "org")));
    topMap.put( "nf", new HashSet<String>(Arrays.asList("com", "net", "per", "rec", "web",
                "arts", "firm", "info", "other", "store")));
    topMap.put( "no", new HashSet<String>(Arrays.asList("fhs", "vgs", "fylkesbibl", "folkebibl",
                "museum", "idrett", "priv", "mil", "stat", "dep", "kommune", "herad", "aa",
                "ah", "bu", "fm", "hl", "hm", "jan-mayen", "mr", "nl", "nt", "of", "ol", "oslo",
                "rl", "sf", "st", "svalbard", "tm", "tr", "va", "vf", "akrehamn",
                "xn--krehamn-dxa", "algard", "xn--lgrd-poac", "arna", "brumunddal",
                "bryne", "bronnoysund", "xn--brnnysund-m8ac", "drobak",
                "xn--drbak-wua", "egersund", "fetsund", "floro", "xn--flor-jra",
                "fredrikstad", "hokksund", "honefoss", "xn--hnefoss-q1a",
                "jessheim", "jorpeland", "xn--jrpeland-54a", "kirkenes", "kopervik",
                "krokstadelva", "langevag", "xn--langevg-jxa", "leirvik", "mjondalen",
                "xn--mjndalen-64a", "mo-i-rana", "mosjoen", "xn--mosjen-eya",
                "nesoddtangen", "orkanger", "osoyro", "xn--osyro-wua",
                "raholt", "xn--rholt-mra", "sandnessjoen", "xn--sandnessjen-ogb",
                "skedsmokorset", "slattum", "spjelkavik", "stathelle", "stavern", "stjordalshalsen",
                "xn--stjrdalshalsen-sqb", "tananger", "tranby", "vossevangen", "tranby",
                "vossevangen", "afjord", "xn--fjord-lra", "agdenes", "al",
                "xn--l-1fa", "alesund", "xn--lesund-hua",
                "alstahaug", "alta", "xn--lt-liac", "alaheadju",
                "xn--laheadju-7ya", "alvdal", "amli", "xn--mli-tla",
                "amot", "xn--mot-tla", "andebu", "andoy", "xn--andy-ira",
                "andasuolo", "ardal", "xn--rdal-poa", "aremark", "arendal",
                "xn--s-1fa", "aseral", "xn--seral-lra",
                "asker", "askim", "askvoll", "askoy", "xn--asky-ira",
                "asnes", "xn--snes-poa", "audnedaln", "aukra", "aure", "aurland",
                "aurskog-holand", "xn--aurskog-hland-jnb",
                "austevoll", "austrheim", "averoy", "xn--avery-yua",
                "balestrand", "ballangen", "balat", "xn--blt-elab",
                "balsfjord", "bahccavuotna", "xn--bhccavuotna-k7a",
                "bamble", "bardu", "beardu", "beiarn", "bajddar", "xn--bjddar-pta",
                "baidar", "xn--bidr-5nac", "berg", "bergen", "berlevag", "xn--berlevg-jxa",
                "bearalvahki", "xn--bearalvhki-y4a", "bindal", "birkenes", "bjarkoy",
                "xn--bjarky-fya", "bjerkreim", "bjugn", "bodo", "xn--bod-2na",
                "badaddja", "xn--bdddj-mrabd", "budejju", "bokn",
                "bremanger", "bronnoy", "xn--brnny-wuac", "bygland",
                "bykle", "barum", "xn--brum-voa", "bievat", "xn--bievt-0qa",
                "bomlo", "xn--bmlo-gra", "batsfjord", "xn--btsfjord-9za", "bahcavuotna",
                "xn--bhcavuotna-s4a", "dovre", "drammen", "drangedal", "dyroy",
                "xn--dyry-ira", "donna", "xn--dnna-gra",
                "eid", "eidfjord", "eidsberg", "eidskog", "eidsvoll", "eigersund", "elverum",
                "enebakk", "engerdal", "etne", "etnedal", "evenes", "evenassi",
                "xn--eveni-0qa01ga", "evje-og-hornnes", "farsund", "fauske",
                "fuossko", "fuoisku", "fedje", "fet", "finnoy", "xn--finny-yua",
                "fitjar", "fjaler", "fjell", "flakstad", "flatanger", "flekkefjord", "flesberg",
                "flora", "fla", "xn--fl-zia", "folldal", "forsand", "fosnes", "frei",
                "frogn", "froland", "frosta", "frana", "xn--frna-woa",
                "froya", "xn--frya-hra", "fusa", "fyresdal", "forde",
                "xn--frde-gra", "gamvik", "gangaviika", "xn--ggaviika-8ya47h",
                "gaular", "gausdal", "gildeskal", "xn--gildeskl-g0a",
                "giske", "gjemnes", "gjerdrum", "gjerstad", "gjesdal", "gjovik",
                "xn--gjvik-wua", "gloppen", "gol", "gran", "grane", "granvin",
                "gratangen", "grimstad", "grong", "kraanghke", "xn--kranghke-b0a",
                "grue", "gulen", "hadsel", "halden", "halsa", "hamar", "hamaroy", "habmer",
                "xn--hbmer-xqa",  "hapmir", "xn--hpmir-xqa",
                "hammerfest", "hammarfeasta", "xn--hmmrfeasta-s4ac",
                "haram", "hareid", "harstad", "hasvik", "aknoluokta", "xn--koluokta-7ya57h",
                "hattfjelldal", "aarborte", "haugesund", "hemne", "hemnes", "hemsedal",
                "hitra", "hjartdal", "hjelmeland",
                "hobol", "xn--hobl-ira", "hof", "hol", "hole", "holmestrand", "holtalen",
                "xn--holtlen-hxa", "hornindal", "horten", "hurdal", "hurum", "hvaler",
                "hyllestad", "hagebostad", "xn--hgebostad-g3a",  "hoyanger",
                "xn--hyanger-q1a", "hoylandet", "xn--hylandet-54a",
                "ha", "xn--h-2fa", "ibestad", "inderoy", "xn--indery-fya",
                "iveland", "jevnaker", "jondal", "jolster", "xn--jlster-bya",
                "karasjok", "karasjohka", "xn--krjohka-hwab49j",
                "karlsoy", "galsa", "xn--gls-elac", "karmoy",
                "xn--karmy-yua", "kautokeino", "guovdageaidnu", "klepp", "klabu",
                "xn--klbu-woa", "kongsberg", "kongsvinger", "kragero", "xn--krager-gya",
                "kristiansand", "kristiansund", "krodsherad", "xn--krdsherad-m8a",
                "kvalsund", "rahkkeravju", "xn--rhkkervju-01af",
                "kvam", "kvinesdal", "kvinnherad", "kviteseid", "kvitsoy", "xn--kvitsy-fya",
                "kvafjord", "xn--kvfjord-nxa", "giehtavuoatna", "kvanangen",
                "xn--kvnangen-k0a", "navuotna", "xn--nvuotna-hwa",
                "kafjord", "xn--kfjord-iua", "gaivuotna", "xn--givuotna-8ya",
                "larvik", "lavangen", "lavagis", "loabat", "xn--loabt-0qa",
                "lebesby", "davvesiida", "leikanger", "leirfjord", "leka", "leksvik", "lenvik",
                "leangaviika", "xn--leagaviika-52b", "lesja", "levanger", "lier", "lierne",
                "lillehammer", "lillesand", "lindesnes", "lindas", "xn--linds-pra",
                "lom", "loppa", "lahppi", "xn--lhppi-xqa", "lund", "lunner", "luroy",
                "xn--lury-ira", "luster", "lyngdal", "lyngen", "ivgu", "lardal", "lerdal",
                "xn--lrdal-sra", "lodingen", "xn--ldingen-q1a", "lorenskog",
                "xn--lrenskog-54a", "loten", "xn--lten-gra",  "malvik",
                "masoy", "xn--msy-ula0h", "muosat", "xn--muost-0qa",
                "mandal", "marker", "marnardal", "masfjorden", "meland", "meldal", "melhus",
                "meloy", "xn--mely-ira", "meraker", "xn--merker-kua", "moareke",
                "xn--moreke-jua", "midsund", "midtre-gauldal", "modalen", "modum",
                "molde", "moskenes", "moss", "mosvik", "malselv", "xn--mlselv-iua",
                "malatvuopmi", "xn--mlatvuopmi-s4a", "namdalseid", "aejrie", "namsos",
                "namsskogan", "naamesjevuemie", "xn--nmesjevuemie-tcba",
                "laakesvuemie", "nannestad", "narvik", "narviika", "naustdal", "nedre-eiker",
                "nesna", "nesodden", "nesseby", "unjarga", "xn--unjrga-rta", "nesset",
                "nissedal", "nittedal", "nord-aurdal", "nord-fron", "nord-odal", "norddal",
                "nordkapp", "davvenjarga", "xn--davvenjrga-y4a", "nordre-land",
                "nordreisa", "raisa", "xn--risa-5na", "nore-og-uvdal", "notodden", "naroy",
                "xn--nry-yla5g", "notteroy", "xn--nttery-byae",
                "odda", "oksnes", "xn--ksnes-uua", "oppdal", "oppegard",
                "xn--oppegrd-ixa", "orkdal", "orland", "xn--rland-uua",
                "orskog", "xn--rskog-uua", "orsta", "xn--rsta-fra",
                "os.hedmark", "os.hordaland", "osen", "osteroy", "xn--ostery-fya",
                "ostre-toten", "xn--stre-toten-zcb", "overhalla", "ovre-eiker",
                "xn--vre-eiker-k8a", "oyer", "xn--yer-zna",
                "oygarden", "xn--ygarden-p1a",  "oystre-slidre", "xn--ystre-slidre-ujb",
                "porsanger", "porsangu", "xn--porsgu-sta26f", "porsgrunn",
                "radoy", "xn--rady-ira", "rakkestad", "rana", "ruovat", "randaberg",
                "rauma", "rendalen", "rennebu", "rennesoy", "xn--rennesy-v1a",
                "rindal", "ringebu", "ringerike", "ringsaker", "rissa", "risor",
                "xn--risr-ira", "roan", "rollag", "rygge", "ralingen", "xn--rlingen-mxa",
                "rodoy", "xn--rdy-0nab", "romskog", "xn--rmskog-bya",
                "roros", "xn--rros-gra", "rost", "xn--rst-0na",
                "royken", "xn--ryken-vua", "royrvik", "xn--ryrvik-bya",
                "rade", "xn--rde-ula", "salangen", "siellak", "saltdal", "salat",
                "xn--slt-elab", "xn--slat-5na", "samnanger",
                "sandefjord", "sandnes", "sandoy", "xn--sandy-yua", "sarpsborg",
                "sauda", "sauherad", "sel", "selbu", "selje", "seljord", "sigdal", "siljan",
                "sirdal", "skaun", "skedsmo", "ski", "skien", "skiptvet", "skjervoy",
                "xn--skjervy-v1a", "skierva", "xn--skierv-uta",
                "skjak", "xn--skjk-soa",  "skodje", "skanland", "xn--sknland-fxa",
                "skanit", "xn--sknit-yqa",  "smola", "xn--smla-hra",
                "snillfjord", "snasa", "xn--snsa-roa",  "snoasa", "snaase",
                "xn--snase-nra", "sogndal", "sokndal", "sola", "solund", "songdalen",
                "sortland", "spydeberg", "stange", "stavanger", "steigen", "steinkjer",
                "stjordal", "xn--stjrdal-s1a", "stokke", "stor-elvdal", "stord", "stordal",
                "storfjord", "omasvuotna", "strand", "stranda", "stryn", "sula", "suldal",
                "sund", "sunndal", "surnadal", "sveio", "svelvik", "sykkylven", "sogne",
                "xn--sgne-gra", "somna", "xn--smna-gra", "sondre-land",
                "xn--sndre-land-0cb", "sor-aurdal", "xn--sr-aurdal-l8a",
                "sor-fron", "xn--sr-fron-q1a", "sor-odal", "xn--sr-odal-q1a",
                "sor-varanger", "xn--sr-varanger-ggb",  "matta-varjjat",
                "xn--mtta-vrjjat-k7af", "sorfold", "xn--srfold-bya",
                "sorreisa", "xn--srreisa-q1a", "sorum", "xn--srum-gra",
                "tana", "deatnu", "time", "tingvoll", "tinn", "tjeldsund", "dielddanuorri",
                "tjome", "xn--tjme-hra", "tokke", "tolga", "torsken", "tranoy",
                "xn--trany-yua",  "tromso", "xn--troms-zua",  "tromsa", "romsa",
                "trondheim", "troandin", "trysil", "trana", "xn--trna-woa",
                "trogstad", "xn--trgstad-r1a",  "tvedestrand", "tydal", "tynset",
                "tysfjord", "divtasvuodna", "divttasvuotna", "tysnes", "tysvar",
                "xn--tysvr-vra",  "tonsberg", "xn--tnsberg-q1a",
                "ullensaker", "ullensvang", "ulvik", "utsira", "vadso", "xn--vads-jra",
                "cahcesuolo", "xn--hcesuolo-7ya35b",  "vaksdal", "valle", "vang",
                "vanylven", "vardo", "xn--vard-jra",  "varggat", "xn--vrggt-xqad",
                "vefsn", "vaapste", "vega", "vegarshei", "xn--vegrshei-c0a", "vennesla",
                "verdal", "verran", "vestby", "vestnes", "vestre-slidre", "vestre-toten",
                "vestvagoy", "xn--vestvgy-ixa6o", "vevelstad", "vik", "vikna",
                "vindafjord", "volda", "voss", "varoy", "xn--vry-yla5g",
                "vagan", "xn--vgan-qoa", "voagat", "vagsoy", "xn--vgsy-qoa0j",
                "vaga", "xn--vg-yiab")));

    topMap.put( "nr", new HashSet<String>(Arrays.asList("biz", "info", "gov", "edu", "org",
                 "net", "com", "co")));
    topMap.put( "pa", new HashSet<String>(Arrays.asList("ac", "gob", "com", "org",
                "sld", "edu", "net", "ing", "abo", "med", "nom")));
    topMap.put( "pe", new HashSet<String>(Arrays.asList("edu", "gob", "nom", "mil", "org", "com",
                "net", "sld")));
    topMap.put( "pf", new HashSet<String>(Arrays.asList( "com")));
    topMap.put( "ph", new HashSet<String>(Arrays.asList("com", "net", "org", "gov", "edu", "ngo", "mil")));
    topMap.put( "pk", new HashSet<String>(Arrays.asList("com", "net", "edu", "org", "fam", "biz",
                "web", "gov", "gob", "gok", "gon", "gop", "gos", "gog", "gkp", "info")));
    topMap.put( "pl", new HashSet<String>(Arrays.asList("aid", "agro", "atm", "auto", "biz", "com",
                "edu", "gmina", "gsm", "info", "mail", "miasta", "media", "mil", "net", "nieruchomosci",
                "nom", "org", "pc", "powiat", "priv", "realestate", "rel", "sex", "shop", "sklep",
                "sos", "szkola", "targi", "tm", "tourism", "travel", "turystyka", "art",
                "gov", "ngo", "augustow", "babia-gora", "bedzin", "beskidy",
                "bialowieza", "bialystok", "bielawa", "bieszczady", "boleslawiec", "bydgoszcz",
                "bytom", "cieszyn", "czeladz", "czest", "dlugoleka", "elblag", "elk", "glogow",
                "gniezno", "gorlice", "grajewo", "ilawa", "jaworzno", "jelenia-gora", "jgora",
                "kalisz", "kazimierz-dolny", "karpacz", "kartuzy", "kaszuby", "katowice", "kepno",
                "ketrzyn", "klodzko", "kobierzyce", "kolobrzeg", "konin", "konskowola", "kutno",
                "lapy", "lebork", "legnica", "lezajsk", "limanowa", "lomza", "lowicz", "lubin",
                "lukow", "malbork", "malopolska", "mazowsze", "mazury", "mielec", "mielno", "mragowo",
                "naklo", "nowaruda", "nysa", "olawa", "olecko", "olkusz", "olsztyn", "opoczno",
                "opole", "ostroda", "ostroleka", "ostrowiec", "ostrowwlkp", "pila", "pisz", "podhale",
                "podlasie", "polkowice", "pomorze", "pomorskie", "prochowice", "pruszkow", "przeworsk",
                "pulawy", "radom", "rawa-maz", "rybnik", "rzeszow", "sanok", "sejny", "siedlce",
                "slask", "slupsk", "sosnowiec", "stalowa-wola", "skoczow", "starachowice", "stargard",
                "suwalki", "swidnica", "swiebodzin", "swinoujscie", "szczecin", "szczytno", "tarnobrzeg",
                "tgory", "turek", "tychy", "ustka", "walbrzych", "warmia", "warszawa", "waw",
                "wegrow", "wielun", "wlocl", "wloclawek", "wodzislaw", "wolomin", "wroclaw",
                "zachpomor", "zagan", "zarow", "zgora", "zgorzelec", "gda", "gdansk",
                "krakow", "poznan", "wroc", "co",
                "lodz", "lublin", "torun")));
    topMap.put( "pn", new HashSet<String>(Arrays.asList("gov", "co", "org", "edu", "net")));
    topMap.put( "pr", new HashSet<String>(Arrays.asList("com", "net", "org", "gov", "edu", "isla",
                "pro", "biz", "info", "name", "est", "prof", "ac", "gobierno")));
    topMap.put( "pro", new HashSet<String>(Arrays.asList("aca", "bar", "cpa", "jur", "law",
                 "med", "eng")));
    topMap.put( "ps", new HashSet<String>(Arrays.asList("edu", "gov", "sec", "plo", "com", "org", "net")));
    topMap.put( "pt", new HashSet<String>(Arrays.asList("net", "gov", "org", "edu", "int", "publ",
                 "com", "nome")));
    topMap.put( "pw", new HashSet<String>(Arrays.asList("co", "ne", "or", "ed", "go", "belau")));
    topMap.put( "qa", new HashSet<String>(Arrays.asList("com", "net", "org", "gov", "edu", "mil")));
    topMap.put( "re", new HashSet<String>(Arrays.asList("com", "asso", "nom")));
    topMap.put( "ro", new HashSet<String>(Arrays.asList("com", "org", "tm", "nt", "nom", "info",
                "rec", "arts", "firm", "store", "www")));
    topMap.put( "rs", new HashSet<String>(Arrays.asList("co", "org", "edu", "ac", "gov", "in")));
    topMap.put( "ru", new HashSet<String>(Arrays.asList("ac", "com", "edu", "int", "net", "org",
                "pp", "adygeya", "altai", "amur", "arkhangelsk", "astrakhan", "bashkiria",
                "belgorod", "bir", "bryansk", "buryatia", "cap", "cbg", "chel", "chelyabinsk", "chita",
                "chukotka", "dagestan", "e-burg", "grozny", "irkutsk",
                "ivanovo", "izhevsk", "jar", "joshkar-ola", "kalmykia", "kaluga", "kamchatka",
                "karelia", "kazan", "kchr", "kemerovo", "khabarovsk", "khakassia", "khv", "kirov",
                "koenig", "komi", "kostroma", "krasnoyarsk", "kuban", "kurgan", "kursk", "lipetsk",
                "magadan", "mari", "mari-el", "marine", "mordovia", "mosreg", "msk", "murmansk",
                "nalchik", "nnov", "nov", "novosibirsk", "nsk", "omsk", "orenburg", "oryol",
                "palana", "penza", "perm", "pskov", "ptz", "rnd", "ryazan", "sakhalin", "samara",
                "saratov", "simbirsk", "smolensk", "spb", "stavropol", "stv", "surgut", "tambov",
                "tatarstan", "tom", "tomsk", "tsaritsyn", "tsk", "tula", "tuva", "tver", "tyumen",
                "udm", "udmurtia", "ulan-ude", "vladikavkaz", "vladimir", "vladivostok", "volgograd",
                "vologda", "voronezh", "vrn", "vyatka", "yakutia", "yamal", "yaroslavl",
                "yekaterinburg", "yuzhno-sakhalinsk", "amursk", "baikal", "cmw", "fareast",
                "jamal", "kms", "k-uralsk", "kustanai", "kuzbass", "magnitka", "mytis",
                "nakhodka", "nkz", "norilsk", "oskol", "pyatigorsk", "rubtsovsk", "snz", "syzran",
                "vdonsk", "zgrad", "gov", "mil", "test")));
    topMap.put( "rw", new HashSet<String>(Arrays.asList("gov", "net", "edu", "ac", "com", "co",
                "int", "mil", "gouv")));
    topMap.put( "sa", new HashSet<String>(Arrays.asList("com", "net", "org", "gov", "med", "pub",
                "edu", "sch")));
    topMap.put( "sd", new HashSet<String>(Arrays.asList("com", "net", "org", "edu", "med", "gov",
                "info", "tv")));
    topMap.put( "se", new HashSet<String>(Arrays.asList("a", "ac", "b", "bd", "brand", "c", "d",
                "e", "f", "fh", "fhsk", "fhv", "g", "h", "i", "k", "komforb", "kommunalforbund",
                "komvux", "l", "lanarb", "lanbib", "m", "n", "naturbruksgymn", "o", "org", "p", "parti",
                "pp", "press", "r", "s", "sshn", "t", "tm", "u", "w", "x", "y", "z")));
    topMap.put( "sg", new HashSet<String>(Arrays.asList("com", "net", "org", "gov", "edu", "per")));
    topMap.put( "sh", new HashSet<String>(Arrays.asList("co", "com", "net", "org", "gov", "edu", "nom")));
    topMap.put( "sk", new HashSet<String>(Arrays.asList("gov", "edu")));
    topMap.put( "sn", new HashSet<String>(Arrays.asList("art", "com", "edu", "gouv", "org", "perso",
                "univ")));
    topMap.put( "so", new HashSet<String>(Arrays.asList("com", "net", "org")));
    topMap.put( "sr", new HashSet<String>(Arrays.asList("co", "com", "consulado", "edu", "embaixada",
                "gov", "mil", "net", "org", "principe", "saotome", "store")));
    topMap.put( "sy", new HashSet<String>(Arrays.asList("edu", "gov", "net", "mil", "com", "org", "news")));
    topMap.put( "sz", new HashSet<String>(Arrays.asList("co", "ac", "org")));
    topMap.put( "th", new HashSet<String>(Arrays.asList("ac", "co", "go", "in", "mi", "net", "or")));
    topMap.put( "tj", new HashSet<String>(Arrays.asList("ac", "biz", "co", "com", "edu", "go", "gov",
                "int", "mil", "name", "net", "nic", "org", "test", "web")));
    topMap.put( "tn", new HashSet<String>(Arrays.asList("com", "ens", "fin", "gov", "ind", "intl",
                "nat", "net", "org", "info", "perso", "tourism", "edunet", "rnrt", "rns", "rnu",
                "mincom", "agrinet", "defense", "turen")));
    topMap.put( "to", new HashSet<String>(Arrays.asList("gov")));
    topMap.put( "tt", new HashSet<String>(Arrays.asList("co", "com", "org", "net", "biz", "info",
                "pro", "int", "coop", "jobs", "mobi", "travel", "museum", "aero", "name", "gov",
                "edu", "cat", "tel", "mil")));
    topMap.put( "tw", new HashSet<String>(Arrays.asList("edu", "gov", "mil", "com", "net", "org",
                "idv", "game", "ebiz", "club", "xn--zf0ao64a", "xn--uc0atv", "xn--czrw28b")));
    topMap.put( "ua", new HashSet<String>(Arrays.asList("com", "edu", "gov", "in", "net", "org",
                "cherkassy", "chernigov", "chernovtsy", "ck", "cn", "crimea", "cv", "dn",
                "dnepropetrovsk", "donetsk", "dp", "if", "ivano-frankivsk", "kh", "kharkov",
                "kherson", "kiev", "kirovograd", "km", "kr", "ks", "lg",
                "lugansk", "lutsk", "lviv", "mk", "nikolaev", "od", "odessa", "pl", "poltava",
                "rovno", "rv", "sebastopol", "sumy", "te", "ternopil", "uzhgorod", "vinnica", "vn",
                "zaporizhzhe", "zp", "zhitomir", "zt", "cr", "lt", "lv", "sb", "sm", "tr",
                "co", "biz", "in", "ne", "pp", "uz", "dominic")));
    topMap.put( "ug", new HashSet<String>(Arrays.asList("co", "ac", "sc", "go", "ne", "or", "org", "com")));
    topMap.put( "us", new HashSet<String>(Arrays.asList("dni", "fed", "isa", "kids", "nsn", "kyschools")));
    topMap.put( "uz", new HashSet<String>(Arrays.asList("co", "com", "org", "gov", "ac", "edu", "int", "pp", "net")));
    topMap.put( "vc", new HashSet<String>(Arrays.asList("com", "net", "org", "gov")));
    topMap.put( "vi", new HashSet<String>(Arrays.asList("co", "com", "k12", "net", "org")));
    topMap.put( "vn", new HashSet<String>(Arrays.asList( "com", "net", "org", "edu", "gov", "int",
                "ac", "biz", "info", "name", "pro", "health")));
    topMap.put( "vu", new HashSet<String>(Arrays.asList("co", "com", "net", "org", "edu", "gov", "de")));
    topMap.put("org", new HashSet<String>(Arrays.asList("ae", "za")));
    topMap.put("pro", new HashSet<String>(Arrays.asList("aca", "bar", "cpa", "jur", "law", "med", "eng")));

    top3Map.put("au", new HashSet<String>(Arrays.asList("act.edu.au", "eq.edu.au",
                "nsw.edu.au", "nt.edu.au", "qld.edu.au", "sa.edu.au", "tas.edu.au", "vic.edu.au",
                 "wa.edu.au", "act.gov.au", "nsw.gov.au", "nt.gov.au", "qld.gov.au", "sa.gov.au",
                 "tas.gov.au", "vic.gov.au", "wa.gov.au")));
    top3Map.put("im", new HashSet<String>(Arrays.asList("ltd.co.im", "plc.co.im")));
    top3Map.put("no", new HashSet<String>(Arrays.asList("gs.aa.no", "gs.ah.no", "gs.bu.no",
                "gs.fm.no", "gs.hl.no", "gs.hm.no", "gs.jan-mayen.no", "gs.mr.no", "gs.nl.no",
                "gs.nt.no", "gs.of.no", "gs.ol.no", "gs.oslo.no", "gs.rl.no", "gs.sf.no",
                "gs.st.no", "gs.svalbard.no", "gs.tm.no", "gs.tr.no", "gs.va.no", "gs.vf.no",
                "bo.telemark.no", "xn--b-5ga.telemark.no", "bo.nordland.no",
                "xn--b-5ga.nordland.no", "heroy.more-og-romsdal.no",
                "xn--hery-ira.xn--mre-og-romsdal-qqb.no", "heroy.nordland.no",
                "xn--hery-ira.nordland.no", "nes.akershus.no", "nes.buskerud.no",
                "os.hedmark.no", "os.hordaland.no",
                "sande.more-og-romsdal.no", "sande.xn--mre-og-romsdal-qqb.no",
                "sande.vestfold.no", "valer.ostfold.no", "xn--vler-qoa.xn--stfold-9xa.no",
                "valer.hedmark.no", "xn--vler-qoa.hedmark.no")));
    top3Map.put("tr", new HashSet<String>(Arrays.asList("gov.nc.tr")));
}


    /*
     * Return the registered part of a qualified domain
     * name or the original if no match is found.
     */
    public static String getRegisteredDomain(String cname) {
        int dot;

        /*
         * If one dot or less than just return.
         */
        dot = cname.lastIndexOf('.');
        if (dot == -1)
            return cname;
        if (dot == 0)
            return "";
        if (dot == cname.length() - 1) {
            cname = cname.substring(0, cname.length() -1);
            dot = cname.lastIndexOf('.');
            if (dot == -1)
                return cname;
            if (dot == 0)
                return "";
        }
        if (dot == cname.length() - 1)
            return "";

        /*
         * Break it up into seperate labels.
         */
        int second = cname.lastIndexOf('.', dot - 1);
        if (second == -1)
            return cname;
        if (second == 0)
            return "";
        int third = cname.lastIndexOf('.', second - 1);
        int fourth = -1;
        if (third > 0) {
            fourth = cname.lastIndexOf('.', third - 1);
        }
        int fifth = -1;
        if (fourth > 0) {
            fifth = cname.lastIndexOf('.', fourth - 1);
        }
        String s = cname.substring(dot + 1);
        String s2 = cname.substring(second + 1, dot);

        /*
         * Look for longest matches first.
         * XX.PVT.K12.MA.US etc.
         */
        if (fourth != -1 && s.equals("us") && usStateSet.contains(s2)) {
            String s3 = cname.substring(third + 1, second);
            String s4 = cname.substring(fourth + 1, third);
            if (s3.equals("k12")) {
                if (s2.equals("ma") && (s4.equals("chtr") || s4.equals("paroch"))) {
                    return cname.substring(fifth + 1);
                } else if (s4.equals("pvt")) {
                    return cname.substring(fifth + 1);
                }
            }
        }

        /*
         * XX.K12.MA.US.
         */
        String str = cname.substring(third + 1);
        if (third != -1) {
            Set<String> set = top3Map.get(s);
            if (set != null) {
                if (set.contains(str)) {
                    return cname.substring(fourth + 1);
                }
            } else if (s.equals("us") && usStateSet.contains(s2)) {
                // check for known third level labels
                String s3 = cname.substring(third + 1, second);
                if (usSubStateSet.contains(s3)) {
                    return fourth != -1? cname.substring(fourth + 1): cname;
                } else {
                    return cname.substring(third + 1);
                }
            } else if (s.equals("uk")) {
                if (s2.equals("sch")) {
                    return cname.substring(fourth + 1);
                }
            } else if (s.equals("jp")) {
                if (jpSet.contains(s2)) {
                    if (jp2Set.contains(str)) {
                        return cname.substring(third + 1);
                    }
                    return cname.substring(fourth + 1);
                }
            }
        }

        /*
         * PREF.AKITA.JP etc.
         */
        if (jp2Set.contains(str)) {
            return cname.substring(third + 1);
        }

        /*
         * XX.MA.US.
         */
        Set<String> topSet = topMap.get(s);
        if (topSet != null) {
            if (topSet.contains(s2)) {
                return cname.substring(third + 1);
            }
            if (!((s.equals("us") && usStateSet.contains(s2)) || (s.equals("jp") && jpSet.contains(s2)))) {
                return cname.substring(second + 1);
            }
        } else if (top2Set.contains(s)) {
            if (s2.equals("gov")) {
                return cname.substring(third + 1);
            }
            return cname.substring(second + 1);
        } else if (top3Set.contains(s)) {
            if (s.equals("ad") && s2.equals("nom") ||
                s.equals("aw") && s2.equals("com") ||
                s.equals("be") && s2.equals("ac") ||
                s.equals("cl") && s2.equals("gov") ||
                s.equals("cl") && s2.equals("gob") ||
                s.equals("fi") && s2.equals("aland") ||
                s.equals("int") && s2.equals("eu") ||
                s.equals("io") && s2.equals("com") ||
                s.equals("mc") && s2.equals("tm") ||
                s.equals("mc") && s2.equals("asso") ||
                s.equals("vc") && s2.equals("com")) {
                return cname.substring(third + 1);
            }
            return cname.substring(second + 1);
        } else if (top4Set.contains(s)) {
            if (s2.equals("com") || s2.equals("edu") || s2.equals("gov") ||
                s2.equals("net") || s2.equals("org")) {
                return cname.substring(third + 1);
            }
            return cname.substring(second + 1);
        } else if (top5Set.contains(s)) {
            return cname.substring(third + 1);
        }

        /*
         * BB.AA exception cases.
         */
        if (s.equals("tr")) {
            if (!s2.equals("nic") && !s2.equals("tsk")) {
                return cname.substring(third + 1);
            }
            return cname.substring(second + 1);
        } else if (s.equals("uk")) {
            if (!ukSet.contains(s2)) {
                return cname.substring(third + 1);
            }
            return cname.substring(second + 1);
        } else if (s.equals("ar")) {
            if (!arSet.contains(s2)) {
                return cname.substring(third + 1);
            }
            return cname.substring(second + 1);
        } else if (s.equals("om")) {
            if (!omSet.contains(s2)) {
                return cname.substring(third + 1);
            }
            return cname.substring(second + 1);
        }

        /*
         * XX.AA
         */
        if (top1Set.contains(s)) {
            return cname.substring(second + 1);
        }

        /*
         * Nothing matched so we can't shorten the string.
         */
        return cname;
     }
}
