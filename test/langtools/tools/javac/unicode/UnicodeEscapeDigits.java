/*
 * @test  /nodynamiccopyright/
 * @bug 8394557
 * @summary Unicode escapes must use only ASCII hex digits
 *
 * @compile/fail/ref=UnicodeEscapeDigits.out -XDrawDiagnostics UnicodeEscapeDigits.java
 */
class UnicodeEscapeDigits {

    // The Unicode escape below contains '\', 'u' and then four 'Ａ' (U+FF21) characters.
    // Although accepted by Character.digit(), they are not allowed in Java Unicode escapes.
    String x = "\uＡＡＡＡ";
}
