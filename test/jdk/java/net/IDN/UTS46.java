/*
 * @test
 * @bug 6988055
 * @summary java.net.IDN implements UTS#46 rather than IDNA2003
 * @compile -encoding UTF-8 UTS46.java
 * @run main UTS46
 */

import java.net.IDN;
import java.util.ArrayList;
import java.util.List;

public class UTS46 {

    private static final List<String> failures = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        roundTrips();
        deviationCharacters();
        caseAndNormalization();
        labelSeparators();
        std3AsciiRules();
        contextJ();
        contextJUnchecked();
        bidi();
        lengths();
        theRoot();
        obsoleteFlag();
        lenientToUnicode();
        reservedLdhLabels();
        unicodeVersionAgreement();

        if (!failures.isEmpty()) {
            failures.forEach(System.out::println);
            throw new Exception(failures.size() + " IDN failure(s)");
        }
    }

    /*
     * Basic sanity check before anything else: Names that any IDN
     * implementation has to get right, in both directions. Each pair
     * is a u-label form and its a-label form.
     */
    private static void roundTrips() {
        String[][] names = {
            { "grå.org",           "xn--gr-zia.org" },
            { "münchen.de",        "xn--mnchen-3ya.de" },
            { "orléans.fr",        "xn--orlans-dva.fr" },
            { "例子.中国",          "xn--fsqu00a.xn--fiqs8s" },
            { "慕田峪长城.网址",     "xn--uist2j67d64zv30b.xn--ses554g" },
            { "उदाहरण.भारत",         "xn--p1b6ci4b4b3a.xn--h2brj9c" },
            { "example.com",       "example.com" },
        };

        for (String[] pair : names) {
            check("toASCII " + pair[0], pair[1], IDN.toASCII(pair[0]));
            check("toUnicode " + pair[1], pair[0], IDN.toUnicode(pair[1]));
            check("toASCII " + pair[1], pair[1], IDN.toASCII(pair[1]));
        }
    }

    /*
     * Transitional processing maps the four deviation characters the
     * way RFC 3490 did, nontransitional leaves them alone. Both major
     * browsers and the registries use nontransitional, which is why
     * it is the default here.
     */
    private static void deviationCharacters() {
        String strasse = "straße.de";              // straße.de, with ess-zet
        check("nontransitional ess-zet", "xn--strae-oqa.de",
              IDN.toASCII(strasse, IDN.NONTRANSITIONAL_TO_ASCII));
        check("transitional ess-zet", "strasse.de",
              IDN.toASCII(strasse, 0));
        check("default ess-zet", "xn--strae-oqa.de", IDN.toASCII(strasse));

        // Final sigma differs from the medial one only in nontransitional
        // processing; transitional folds it to the medial form, so the two
        // a-labels differ.
        String solos = "σόλος.gr";  // σόλος.gr
        check("nontransitional final sigma", "xn--wxaijb9b.gr",
              IDN.toASCII(solos, IDN.NONTRANSITIONAL_TO_ASCII));
        check("transitional final sigma", "xn--wxaikc6b.gr",
              IDN.toASCII(solos, 0));

        // Going back to Unicode recovers the deviation character either way,
        // since by then it is inside Punycode.
        check("toUnicode ess-zet", strasse,
              IDN.toUnicode("xn--strae-oqa.de", IDN.NONTRANSITIONAL_TO_UNICODE));
        check("toUnicode ess-zet, transitional", strasse,
              IDN.toUnicode("xn--strae-oqa.de", 0));
    }

    /*
     * Mapping folds case, normalizes to NFC and maps the compatibility
     * variants, so all of these are the same name.
     */
    private static void caseAndNormalization() {
        String nfc = "grå.org";                    // grå.org, precomposed
        String nfd = "gra\u030A.org";              // gra + ring above
        // If some future reformatting normalizes the string literals above,
        // this test stops testing anything, so notice that first.
        if (nfd.length() != nfc.length() + 1) {
            failures.add("the NFD literal is no longer decomposed");
        }

        check("NFC", "xn--gr-zia.org", IDN.toASCII(nfc));
        check("NFD", "xn--gr-zia.org", IDN.toASCII(nfd));
        check("uppercase", "xn--gr-zia.org", IDN.toASCII("GRÅ.ORG"));
        check("uppercase a-label", "xn--gr-zia.org", IDN.toASCII("XN--GR-ZIA.ORG"));
        check("toUnicode of uppercase a-label", nfc, IDN.toUnicode("XN--GR-ZIA.ORG"));

        // Fullwidth Latin letters map to plain ASCII, so this needs no Punycode.
        check("fullwidth", "example.com",
              IDN.toASCII("ｅｘａｍｐｌｅ.com"));
    }

    /*
     * All four label separators are accepted, and the output uses U+002E.
     */
    private static void labelSeparators() {
        String[] separators = { ".", "。", "．", "｡" };
        for (String separator : separators) {
            check("separator U+" + Integer.toHexString(separator.charAt(0)),
                  "xn--fsqu00a.xn--fiqs8s",
                  IDN.toASCII("例子" + separator + "中国"));
        }
    }

    /*
     * Underscore is valid under UTS#46 alone and invalid under the STD 3
     * host name rules.
     */
    private static void std3AsciiRules() {
        String name = "under_score.example.com";
        check("underscore without STD3", name,
              IDN.toASCII(name, IDN.NONTRANSITIONAL_TO_ASCII));
        expectFailure("underscore with STD3", name,
                      IDN.NONTRANSITIONAL_TO_ASCII | IDN.USE_STD3_ASCII_RULES);
        // The one-argument form doesn't use the STD 3 rules.
        check("underscore by default", name, IDN.toASCII(name));
        check("wildcard by default", "*.xn--gr-zia.org",
              IDN.toASCII("*.grå.org"));
    }

    /*
     * CONTEXTJ allows the joiners where a script needs them and nowhere
     * else. Without the flag they are simply valid.
     */
    private static void contextJ() {
        int flags = IDN.NONTRANSITIONAL_TO_ASCII | IDN.CHECK_CONTEXTJ;
        String india = ".भारत";     // .भारत

        // U+200D ZERO WIDTH JOINER after U+094D DEVANAGARI SIGN VIRAMA
        check("joiner after virama", "xn--11b2ezcw70k.xn--h2brj9c",
              IDN.toASCII("क्‍ष" + india, flags));
        // U+200C ZERO WIDTH NON-JOINER in the same position
        check("non-joiner after virama", "xn--11b2ezcs70k.xn--h2brj9c",
              IDN.toASCII("क्‌ष" + india, flags));

        expectFailure("joiner between Latin letters",
                      "a‍b.example.com", flags);
        expectFailure("non-joiner between Latin letters",
                      "a‌b.example.com", flags);
        check("joiner between Latin letters, unchecked", "xn--ab-m1t.example.com",
              IDN.toASCII("a‍b.example.com", IDN.NONTRANSITIONAL_TO_ASCII));
    }

    /*
     * "If CONTEXTJ is not checked, ZWNJ etc. are accepted" holds in the sense
     * that nothing is rejected, but only nontransitional processing keeps the
     * character. ZWJ and ZWNJ are two of the four deviation characters, so
     * transitional processing maps them away, and the name is accepted with
     * the joiner silently gone. That is worth pinning down, because it is the
     * case where an unchecked joiner is most dangerous: a‌b and ab differ to
     * a reader and collapse to one a-label, which is the equal/inequal string
     * problem the flag exists to prevent.
     */
    private static void contextJUnchecked() {
        String zwnj = "a‌b.example.com";     // ZWNJ between two Latin letters
        String zwj = "a‍b.example.com";      // ZWJ in the same position

        // Nontransitional: accepted, and the joiner survives into the a-label.
        check("unchecked ZWNJ is kept", "xn--ab-j1t.example.com",
              IDN.toASCII(zwnj, IDN.NONTRANSITIONAL_TO_ASCII));
        check("unchecked ZWJ is kept", "xn--ab-m1t.example.com",
              IDN.toASCII(zwj, IDN.NONTRANSITIONAL_TO_ASCII));

        // Transitional: also accepted, but the joiner is mapped away, so both
        // names become the name without it.
        check("unchecked ZWNJ is dropped", "ab.example.com",
              IDN.toASCII(zwnj, 0));
        check("unchecked ZWJ is dropped", "ab.example.com",
              IDN.toASCII(zwj, 0));
        check("dropping collides with the plain name",
              IDN.toASCII("ab.example.com", 0), IDN.toASCII(zwnj, 0));

        // The loss is not confined to joiners nobody wants: transitional drops
        // the ZWNJ after a virama too, where CONTEXTJ would allow it.
        check("virama ZWNJ kept, nontransitional", "xn--11b2ezcs70k.example.com",
              IDN.toASCII("क्‌ष.example.com", IDN.NONTRANSITIONAL_TO_ASCII));
        check("virama ZWNJ dropped, transitional", "xn--11b2ezc.example.com",
              IDN.toASCII("क्‌ष.example.com", 0));

        // A label that is nothing but a joiner still converts.
        check("lone ZWNJ label", "xn--0ug.example.com",
              IDN.toASCII("‌.example.com", IDN.NONTRANSITIONAL_TO_ASCII));

        // Asking for CONTEXTJ does not help under transitional processing.
        check("CONTEXTJ is inert, transitional", "ab.example.com",
              IDN.toASCII(zwnj, IDN.CHECK_CONTEXTJ));
        check("CONTEXTJ is inert for ZWJ too", "ab.example.com",
              IDN.toASCII(zwj, IDN.CHECK_CONTEXTJ));
        check("CONTEXTJ changes nothing, transitional",
              IDN.toASCII(zwnj, 0), IDN.toASCII(zwnj, IDN.CHECK_CONTEXTJ));
        // If you don't panic yet, go back to the top and read all
        // this again, and maybe check CONTEXTJ.
    }

    /*
     * The BiDi rule is always applied, whatever the flags say. A label in a
     * right-to-left script may end in a digit but not in Latin letters.
     */
    private static void bidi() {
        check("Hebrew", "xn--6dbbec0c.xn--4dbrk0ce",
              IDN.toASCII("דוגמה.ישראל"));   // דוגמה.ישראל
        check("Hebrew with trailing digits", "xn--123-xpeem.example",
              IDN.toASCII("דוג" + "123.example"));
        expectFailure("Hebrew with trailing Latin", "דוג" + "abc.example");
        expectFailure("Arabic with trailing Latin", "مثال" + "abc.example");
    }

    private static void lengths() {
        check("63-octet label", "a".repeat(63) + ".example",
              IDN.toASCII("a".repeat(63) + ".example"));
        expectFailure("64-octet label", "a".repeat(64) + ".example");
    }

    /*
     * The root has no labels at all, which is outside the scope of
     * UTS#46. Java has accepted both "" and "." since 1.6.
     */
    private static void theRoot() {
        check("empty name", "", IDN.toASCII(""));
        check("root", ".", IDN.toASCII("."));
        check("trailing root dot", "xn--gr-zia.org.", IDN.toASCII("grå.org."));
        expectFailure("empty label", "example..com");
    }

    /*
     * ALLOW_UNASSIGNED was an IDNA2003 notion and is now ignored, so it must
     * not change any answer.
     */
    private static void obsoleteFlag() {
        String[] names = { "grå.org", "example.com", "xn--fsqu00a.xn--fiqs8s" };
        for (String name : names) {
            check("ALLOW_UNASSIGNED ignored for " + name,
                  IDN.toASCII(name, IDN.NONTRANSITIONAL_TO_ASCII),
                  IDN.toASCII(name, IDN.NONTRANSITIONAL_TO_ASCII | IDN.ALLOW_UNASSIGNED));
        }
    }

    /*
     * These cases exist to keep the behaviour Java has had since 1.6.
     *
     * UTS#46 ToUnicode substitutes U+FFFD for whatever it cannot
     * convert, which makes sense but IDN.toUnicode instead promises
     * never to fail, and passes a label it cannot convert through
     * untouched, converting the rest. A name that mixes good and bad
     * labels keeps the bad ones verbatim.
     *
     * Changing any of these would mean the JDK contract has been
     * dropped in favour of the UTS#46 one. While the UTS#46 contract
     * makes a lot of sense, changing the JDK contract is risky.
     */
    private static void lenientToUnicode() {
        // Punycode that does not decode
        check("undecodable a-label", "xn--a.example", IDN.toUnicode("xn--a.example"));
        check("undecodable a-label among good ones", "grå.xn--a.org",
              IDN.toUnicode("xn--gr-zia.xn--a.org"));

        // A character UTS#46 disallows outright
        check("space", "ex ample.com", IDN.toUnicode("ex ample.com"));
        check("space beside a good label", "ex ample.中国",
              IDN.toUnicode("ex ample.xn--fiqs8s"));

        // Names that ToASCII rejects but ToUnicode has to hand back
        String[] unconvertible = {
            "example..com",
            "-lead.example",
            "trail-.example",
            "ab--cd.example",
            "xn--0.example",
            "דוג" + "abc.example",
            "a".repeat(64) + ".example",
        };
        for (String name : unconvertible) {
            check("unmodified " + name, name, IDN.toUnicode(name));
        }

        // No flag combination may turn a failure into an exception
        for (int flag = 0; flag <= 0x3f; flag++) {
            for (String name : unconvertible) {
                try {
                    IDN.toUnicode(name, flag);
                } catch (RuntimeException e) {
                    failures.add("toUnicode(" + name + ", " + flag + ") threw " + e);
                }
            }
        }
    }

    /*
     * A label with hyphens in the third and fourth position is
     * reserved by RFC5891 4.2.3.1 for tagged forms. However, IDN let
     * non-xn labels through unchanged before UTS#46 and absent a
     * spefific reason to change, IDN ought to go on doing so.
     */
    private static void reservedLdhLabels() {
        String[] reserved = { "ab--cd.example.com",
                              "bq--abcdefg.example.com",
                              "ab--cd.grå.org" };
        for (String name : reserved) {
            String expected = name.equals("ab--cd.grå.org")
                              ? "ab--cd.xn--gr-zia.org" : name;
            check("reserved LDH, default flags", expected, IDN.toASCII(name));
            // The flags javax.net.ssl.SNIHostName passes.
            check("reserved LDH, STD3", expected,
                  IDN.toASCII(name, IDN.USE_STD3_ASCII_RULES));
            check("reserved LDH, no flags", expected, IDN.toASCII(name, 0));
        }

        check("reserved LDH survives toUnicode", "ab--cd.example.com",
              IDN.toUnicode("ab--cd.example.com"));
        check("reserved LDH round trip", "ab--cd.grå.org",
              IDN.toUnicode("ab--cd.xn--gr-zia.org"));

        // Relaxing HYPHEN_3_4 must not relax the other hyphen rules, which
        // STD 3 rejected long before UTS#46 arrived.
        expectFailure("leading hyphen", "-lead.example.com");
        expectFailure("trailing hyphen", "trail-.example.com");
        // Nor may it let a malformed a-label through as if it were valid.
        expectFailure("undecodable a-label", "xn--a.example.com");
    }

    /*
     * IDN and Character have to agree about which code points exist. IDN
     * answers from the ICU data files under jdk/internal/icu/impl/data, and
     * Character from the tables generated out of make/data/unicodedata, so
     * the two are only consistent as long as both are updated together. If
     * this fails, one of them has been moved to a different Unicode version:
     * a name is being accepted out of code points the rest of the JDK thinks
     * are unassigned. These three were added in Unicode 16.0.
     *
     * This test only really makes sense while this code  is a pull request.
     */
    private static void unicodeVersionAgreement() {
        int[] recent = { 0x10D4A,       // GARAY
                         0x11BC5,       // SUNUWAR
                         0x105C2 };     // TODHRI
        for (int cp : recent) {
            String label = new String(Character.toChars(cp)) + ".example";
            try {
                IDN.toASCII(label, IDN.NONTRANSITIONAL_TO_ASCII);
            } catch (IllegalArgumentException e) {
                failures.add(String.format("U+%04X: IDN rejects it", cp));
                continue;
            }
            if (!Character.isDefined(cp)) {
                failures.add(String.format(
                    "U+%04X: IDN accepts it, Character says it is unassigned", cp));
            }
        }
    }

    private static void check(String what, String expected, String actual) {
        if (!expected.equals(actual)) {
            failures.add(what + ": expected [" + expected + "], got [" + actual + "]");
        }
    }

    private static void expectFailure(String what, String name) {
        expectFailure(what, name, -1);
    }

    private static void expectFailure(String what, String name, int flags) {
        try {
            String result = flags == -1 ? IDN.toASCII(name) : IDN.toASCII(name, flags);
            failures.add(what + ": expected IllegalArgumentException for [" + name
                         + "], got [" + result + "]");
        } catch (IllegalArgumentException expected) {
            // happiness is here
        }
    }
}
