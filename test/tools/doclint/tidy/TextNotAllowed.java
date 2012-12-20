/*
 * @test /nodynamiccopyright/
 * @bug 8004832
 * @summary Add new doclint package
 * @library ..
 * @build DocLintTester
 * @run main DocLintTester -ref TextNotAllowed.out TextNotAllowed.java
 */

// tidy: Warning: plain text isn't allowed in <.*> elements

/**
 * <table summary=description> abc </table>
 * <table summary=description> <tbody> abc </tbody> </table>
 * <table summary=description> <tr> abc </tr> </table>
 *
 * <dl> abc </dl>
 * <ol> abc </ol>
 * <ul> abc </ul>
 *
 * <ul>
 *     <li> item
 *     <li> item
 * </ul>
 */
public class TextNotAllowed { }
