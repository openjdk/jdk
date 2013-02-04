/*
 * @test /nodynamiccopyright/
 * @bug 8004832
 * @summary Add new doclint package
 * @library ..
 * @build DocLintTester
 * @run main DocLintTester -ref TextNotAllowed.out TextNotAllowed.java
 */

/**
 * <dl> abc <dt> term </dt> def <dd> description </dd> ghi </dl>
 * <ol> abc <li> item </li> def <li> item </li> ghi </ol>
 * <ul> abc <li> item </li> def <li> item </li> ghi </ul>
 *
 * <table summary=description> abc </table>
 * <table summary=description> <thead> abc </thead> </table>
 * <table summary=description> <tbody> abc </tbody> </table>
 * <table summary=description> <tfoot> abc </tfoot> </table>
 * <table summary=description> <tr> abc </tr> </table>
 *
 * <dl> &amp; <dt> term </dt> &lt; <dd> description </dd> &gt; </dl>
 * <ol> &amp; <li> item </li> &lt; <li> item </li> &gt; </ol>
 * <ul> &amp; <li> item </li> &lt; <li> item </li> &gt; </ul>
 *
 * <table summary=description> &amp; </table>
 * <table summary=description> <thead> &amp; </thead> </table>
 * <table summary=description> <tbody> &amp; </tbody> </table>
 * <table summary=description> <tfoot> &amp; </tfoot> </table>
 * <table summary=description> <tr> &amp; </tr> </table>
 *
 */
public class TextNotAllowed { }
