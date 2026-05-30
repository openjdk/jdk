/*
 * Copyright (c) the original author(s).
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package jdk.internal.org.jline.utils;

/**
 * Text styling for terminal output with support for colors, fonts, and other attributes.
 *
 * <p>
 * The AttributedStyle class represents the styling attributes that can be applied to
 * text in a terminal. It supports various text attributes such as bold, italic, underline,
 * as well as foreground and background colors. The class uses a bit-packed long value to
 * efficiently store multiple attributes.
 * </p>
 *
 * <p>
 * This class provides a fluent API for building styles by chaining method calls. Styles
 * are immutable, so each method returns a new instance with the requested modifications.
 * </p>
 *
 * <p>
 * Color support includes:
 * </p>
 * <ul>
 *   <li>8 standard ANSI colors (black, red, green, yellow, blue, magenta, cyan, white)</li>
 *   <li>8 bright variants of the standard colors</li>
 *   <li>256-color indexed mode</li>
 *   <li>24-bit true color (RGB) mode</li>
 * </ul>
 *
 * <p>
 * Text attributes include bold, faint, italic, underline, blink, inverse, conceal, and crossed-out.
 * </p>
 *
 * <p>Example usage:</p>
 * <pre>
 * // Create a style with red foreground, bold, and underline
 * AttributedStyle style = AttributedStyle.DEFAULT
 *     .foreground(AttributedStyle.RED)
 *     .bold()
 *     .underline();
 *
 * // Create a string with this style
 * AttributedString str = new AttributedString("Error message", style);
 * </pre>
 *
 * @see AttributedString
 * @see AttributedStringBuilder
 */
// S1845: style constants are intentionally named after their factory methods (e.g., BOLD = DEFAULT.bold())
@SuppressWarnings("java:S1845")
public class AttributedStyle {

    public static final int BLACK = 0;
    public static final int RED = 1;
    public static final int GREEN = 2;
    public static final int YELLOW = 3;
    public static final int BLUE = 4;
    public static final int MAGENTA = 5;
    public static final int CYAN = 6;
    public static final int WHITE = 7;

    public static final int BRIGHT = 8;

    static final long F_BOLD = 0x00000001;
    static final long F_FAINT = 0x00000002;
    static final long F_ITALIC = 0x00000004;
    static final long F_UNDERLINE = 0x00000008;
    static final long F_BLINK = 0x00000010;
    static final long F_INVERSE = 0x00000020;
    static final long F_CONCEAL = 0x00000040;
    static final long F_CROSSED_OUT = 0x00000080;
    static final long F_FOREGROUND_IND = 0x00000100;
    static final long F_FOREGROUND_RGB = 0x00000200;
    static final long F_FOREGROUND = F_FOREGROUND_IND | F_FOREGROUND_RGB;
    static final long F_BACKGROUND_IND = 0x00000400;
    static final long F_BACKGROUND_RGB = 0x00000800;
    static final long F_BACKGROUND = F_BACKGROUND_IND | F_BACKGROUND_RGB;
    static final long F_HIDDEN = 0x00001000;

    static final long MASK = 0x00001FFF;

    static final int FG_COLOR_EXP = 15;
    static final int BG_COLOR_EXP = 39;
    static final long FG_COLOR = 0xFFFFFFL << FG_COLOR_EXP;
    static final long BG_COLOR = 0xFFFFFFL << BG_COLOR_EXP;

    /**
     * Default style with no attributes or colors set.
     */
    public static final AttributedStyle DEFAULT = new AttributedStyle();

    /**
     * Style with bold attribute enabled.
     */
    public static final AttributedStyle BOLD = DEFAULT.bold();

    /**
     * Style with bold attribute explicitly disabled.
     */
    public static final AttributedStyle BOLD_OFF = DEFAULT.boldOff();

    /**
     * Style with inverse (reverse video) attribute enabled.
     */
    public static final AttributedStyle INVERSE = DEFAULT.inverse();

    /**
     * Style with inverse (reverse video) attribute explicitly disabled.
     */
    public static final AttributedStyle INVERSE_OFF = DEFAULT.inverseOff();

    /**
     * Style with hidden attribute enabled.
     */
    public static final AttributedStyle HIDDEN = DEFAULT.hidden();

    /**
     * Style with hidden attribute explicitly disabled.
     */
    public static final AttributedStyle HIDDEN_OFF = DEFAULT.hiddenOff();

    final long style;
    final long mask;

    /**
     * Creates a new AttributedStyle with no attributes or colors set.
     *
     * <p>
     * This constructor creates a default style with no attributes or colors set.
     * It is equivalent to {@link #DEFAULT}.
     * </p>
     */
    public AttributedStyle() {
        this(0, 0);
    }

    /**
     * Creates a new AttributedStyle by copying another style.
     *
     * <p>
     * This constructor creates a new style with the same attributes and colors
     * as the specified style.
     * </p>
     *
     * @param s the style to copy
     */
    public AttributedStyle(AttributedStyle s) {
        this(s.style, s.mask);
    }

    /**
     * Creates a new AttributedStyle with the specified style and mask values.
     *
     * <p>
     * This constructor creates a new style with the specified style and mask values.
     * The style value contains the actual attributes and colors, while the mask value
     * indicates which attributes and colors are explicitly set (as opposed to being
     * inherited or default).
     * </p>
     *
     * <p>
     * This constructor is primarily for internal use and advanced scenarios.
     * </p>
     *
     * @param style the style value containing attributes and colors
     * @param mask the mask value indicating which attributes and colors are set
     */
    public AttributedStyle(long style, long mask) {
        this.style = style;
        this.mask = mask & MASK
                | ((style & F_FOREGROUND) != 0 ? FG_COLOR : 0)
                | ((style & F_BACKGROUND) != 0 ? BG_COLOR : 0);
    }

    /**
     * Returns a new style with the bold attribute enabled.
     *
     * <p>
     * This method returns a new style with the bold attribute enabled.
     * The bold attribute typically makes text appear with a heavier or thicker font.
     * </p>
     *
     * @return a new style with the bold attribute enabled
     * @see #boldOff()
     * @see #boldDefault()
     */
    public AttributedStyle bold() {
        return new AttributedStyle(style | F_BOLD, mask | F_BOLD);
    }

    /**
     * Returns a new style with the bold attribute explicitly disabled.
     *
     * <p>
     * This method returns a new style with the bold attribute explicitly disabled.
     * This is different from {@link #boldDefault()}, which removes any explicit
     * setting for the bold attribute.
     * </p>
     *
     * @return a new style with the bold attribute explicitly disabled
     * @see #bold()
     * @see #boldDefault()
     */
    public AttributedStyle boldOff() {
        return new AttributedStyle(style & ~F_BOLD, mask | F_BOLD);
    }

    /**
     * Returns a new style with the bold attribute set to its default state.
     *
     * <p>
     * This method returns a new style with no explicit setting for the bold attribute.
     * When this style is applied, the bold attribute will be inherited from the
     * parent style or use the terminal's default.
     * </p>
     *
     * @return a new style with the bold attribute set to its default state
     * @see #bold()
     * @see #boldOff()
     */
    public AttributedStyle boldDefault() {
        return new AttributedStyle(style & ~F_BOLD, mask & ~F_BOLD);
    }

    /**
     * Returns a new style with the faint attribute enabled.
     *
     * <p>
     * This method returns a new style with the faint attribute enabled.
     * The faint attribute typically makes text appear with a lighter or thinner font,
     * or with reduced intensity.
     * </p>
     *
     * @return a new style with the faint attribute enabled
     * @see #faintOff()
     * @see #faintDefault()
     */
    public AttributedStyle faint() {
        return new AttributedStyle(style | F_FAINT, mask | F_FAINT);
    }

    /**
     * Returns a new style with the faint attribute explicitly disabled.
     *
     * <p>
     * This method returns a new style with the faint attribute explicitly disabled.
     * This is different from {@link #faintDefault()}, which removes any explicit
     * setting for the faint attribute.
     * </p>
     *
     * @return a new style with the faint attribute explicitly disabled
     * @see #faint()
     * @see #faintDefault()
     */
    public AttributedStyle faintOff() {
        return new AttributedStyle(style & ~F_FAINT, mask | F_FAINT);
    }

    /**
     * Returns a new style with the faint attribute set to its default state.
     *
     * <p>
     * This method returns a new style with no explicit setting for the faint attribute.
     * When this style is applied, the faint attribute will be inherited from the
     * parent style or use the terminal's default.
     * </p>
     *
     * @return a new style with the faint attribute set to its default state
     * @see #faint()
     * @see #faintOff()
     */
    public AttributedStyle faintDefault() {
        return new AttributedStyle(style & ~F_FAINT, mask & ~F_FAINT);
    }

    /**
     * Returns a new style with the italic attribute enabled.
     *
     * <p>
     * This method returns a new style with the italic attribute enabled.
     * The italic attribute typically makes text appear slanted or cursive.
     * </p>
     *
     * @return a new style with the italic attribute enabled
     * @see #italicOff()
     * @see #italicDefault()
     */
    public AttributedStyle italic() {
        return new AttributedStyle(style | F_ITALIC, mask | F_ITALIC);
    }

    /**
     * Returns a new style with the italic attribute explicitly disabled.
     *
     * <p>
     * This method returns a new style with the italic attribute explicitly disabled.
     * This is different from {@link #italicDefault()}, which removes any explicit
     * setting for the italic attribute.
     * </p>
     *
     * @return a new style with the italic attribute explicitly disabled
     * @see #italic()
     * @see #italicDefault()
     */
    public AttributedStyle italicOff() {
        return new AttributedStyle(style & ~F_ITALIC, mask | F_ITALIC);
    }

    /**
     * Returns a new style with the italic attribute set to its default state.
     *
     * <p>
     * This method returns a new style with no explicit setting for the italic attribute.
     * When this style is applied, the italic attribute will be inherited from the
     * parent style or use the terminal's default.
     * </p>
     *
     * @return a new style with the italic attribute set to its default state
     * @see #italic()
     * @see #italicOff()
     */
    public AttributedStyle italicDefault() {
        return new AttributedStyle(style & ~F_ITALIC, mask & ~F_ITALIC);
    }

    /**
     * Returns a new style with the underline attribute enabled.
     *
     * <p>
     * This method returns a new style with the underline attribute enabled.
     * The underline attribute typically draws a line under the text.
     * </p>
     *
     * @return a new style with the underline attribute enabled
     * @see #underlineOff()
     * @see #underlineDefault()
     */
    public AttributedStyle underline() {
        return new AttributedStyle(style | F_UNDERLINE, mask | F_UNDERLINE);
    }

    /**
     * Returns a new style with the underline attribute explicitly disabled.
     *
     * <p>
     * This method returns a new style with the underline attribute explicitly disabled.
     * This is different from {@link #underlineDefault()}, which removes any explicit
     * setting for the underline attribute.
     * </p>
     *
     * @return a new style with the underline attribute explicitly disabled
     * @see #underline()
     * @see #underlineDefault()
     */
    public AttributedStyle underlineOff() {
        return new AttributedStyle(style & ~F_UNDERLINE, mask | F_UNDERLINE);
    }

    /**
     * Returns a new style with the underline attribute set to its default state.
     *
     * <p>
     * This method returns a new style with no explicit setting for the underline attribute.
     * When this style is applied, the underline attribute will be inherited from the
     * parent style or use the terminal's default.
     * </p>
     *
     * @return a new style with the underline attribute set to its default state
     * @see #underline()
     * @see #underlineOff()
     */
    public AttributedStyle underlineDefault() {
        return new AttributedStyle(style & ~F_UNDERLINE, mask & ~F_UNDERLINE);
    }

    /**
     * Returns a new style with the blink attribute enabled.
     *
     * <p>
     * This method returns a new style with the blink attribute enabled.
     * The blink attribute typically makes text flash on and off, though
     * support varies across terminals.
     * </p>
     *
     * @return a new style with the blink attribute enabled
     * @see #blinkOff()
     * @see #blinkDefault()
     */
    public AttributedStyle blink() {
        return new AttributedStyle(style | F_BLINK, mask | F_BLINK);
    }

    /**
     * Returns a new style with the blink attribute explicitly disabled.
     *
     * <p>
     * This method returns a new style with the blink attribute explicitly disabled.
     * This is different from {@link #blinkDefault()}, which removes any explicit
     * setting for the blink attribute.
     * </p>
     *
     * @return a new style with the blink attribute explicitly disabled
     * @see #blink()
     * @see #blinkDefault()
     */
    public AttributedStyle blinkOff() {
        return new AttributedStyle(style & ~F_BLINK, mask | F_BLINK);
    }

    /**
     * Returns a new style with the blink attribute set to its default state.
     *
     * <p>
     * This method returns a new style with no explicit setting for the blink attribute.
     * When this style is applied, the blink attribute will be inherited from the
     * parent style or use the terminal's default.
     * </p>
     *
     * @return a new style with the blink attribute set to its default state
     * @see #blink()
     * @see #blinkOff()
     */
    public AttributedStyle blinkDefault() {
        return new AttributedStyle(style & ~F_BLINK, mask & ~F_BLINK);
    }

    /**
     * Returns a new style with the inverse attribute enabled.
     *
     * <p>
     * This method returns a new style with the inverse attribute enabled.
     * The inverse attribute (also known as reverse video) typically swaps
     * the foreground and background colors of the text.
     * </p>
     *
     * @return a new style with the inverse attribute enabled
     * @see #inverseOff()
     * @see #inverseDefault()
     * @see #inverseNeg()
     */
    public AttributedStyle inverse() {
        return new AttributedStyle(style | F_INVERSE, mask | F_INVERSE);
    }

    /**
     * Returns a new style with the inverse attribute toggled.
     *
     * <p>
     * This method returns a new style with the inverse attribute toggled from its
     * current state. If the inverse attribute is currently enabled, it will be disabled,
     * and if it is currently disabled, it will be enabled.
     * </p>
     *
     * @return a new style with the inverse attribute toggled
     * @see #inverse()
     * @see #inverseOff()
     * @see #inverseDefault()
     */
    public AttributedStyle inverseNeg() {
        long s = (style & F_INVERSE) != 0 ? style & ~F_INVERSE : style | F_INVERSE;
        return new AttributedStyle(s, mask | F_INVERSE);
    }

    /**
     * Returns a new style with the inverse attribute explicitly disabled.
     *
     * <p>
     * This method returns a new style with the inverse attribute explicitly disabled.
     * This is different from {@link #inverseDefault()}, which removes any explicit
     * setting for the inverse attribute.
     * </p>
     *
     * @return a new style with the inverse attribute explicitly disabled
     * @see #inverse()
     * @see #inverseDefault()
     * @see #inverseNeg()
     */
    public AttributedStyle inverseOff() {
        return new AttributedStyle(style & ~F_INVERSE, mask | F_INVERSE);
    }

    /**
     * Returns a new style with the inverse attribute set to its default state.
     *
     * <p>
     * This method returns a new style with no explicit setting for the inverse attribute.
     * When this style is applied, the inverse attribute will be inherited from the
     * parent style or use the terminal's default.
     * </p>
     *
     * @return a new style with the inverse attribute set to its default state
     * @see #inverse()
     * @see #inverseOff()
     * @see #inverseNeg()
     */
    public AttributedStyle inverseDefault() {
        return new AttributedStyle(style & ~F_INVERSE, mask & ~F_INVERSE);
    }

    /**
     * Returns a new style with the conceal attribute enabled.
     *
     * <p>
     * This method returns a new style with the conceal attribute enabled.
     * The conceal attribute typically hides text from display, though
     * support varies across terminals.
     * </p>
     *
     * @return a new style with the conceal attribute enabled
     * @see #concealOff()
     * @see #concealDefault()
     */
    public AttributedStyle conceal() {
        return new AttributedStyle(style | F_CONCEAL, mask | F_CONCEAL);
    }

    /**
     * Returns a new style with the conceal attribute explicitly disabled.
     *
     * <p>
     * This method returns a new style with the conceal attribute explicitly disabled.
     * This is different from {@link #concealDefault()}, which removes any explicit
     * setting for the conceal attribute.
     * </p>
     *
     * @return a new style with the conceal attribute explicitly disabled
     * @see #conceal()
     * @see #concealDefault()
     */
    public AttributedStyle concealOff() {
        return new AttributedStyle(style & ~F_CONCEAL, mask | F_CONCEAL);
    }

    /**
     * Returns a new style with the conceal attribute set to its default state.
     *
     * <p>
     * This method returns a new style with no explicit setting for the conceal attribute.
     * When this style is applied, the conceal attribute will be inherited from the
     * parent style or use the terminal's default.
     * </p>
     *
     * @return a new style with the conceal attribute set to its default state
     * @see #conceal()
     * @see #concealOff()
     */
    public AttributedStyle concealDefault() {
        return new AttributedStyle(style & ~F_CONCEAL, mask & ~F_CONCEAL);
    }

    /**
     * Returns a new style with the crossed-out attribute enabled.
     *
     * <p>
     * This method returns a new style with the crossed-out attribute enabled.
     * The crossed-out attribute typically draws a line through the text,
     * though support varies across terminals.
     * </p>
     *
     * @return a new style with the crossed-out attribute enabled
     * @see #crossedOutOff()
     * @see #crossedOutDefault()
     */
    public AttributedStyle crossedOut() {
        return new AttributedStyle(style | F_CROSSED_OUT, mask | F_CROSSED_OUT);
    }

    /**
     * Returns a new style with the crossed-out attribute explicitly disabled.
     *
     * <p>
     * This method returns a new style with the crossed-out attribute explicitly disabled.
     * This is different from {@link #crossedOutDefault()}, which removes any explicit
     * setting for the crossed-out attribute.
     * </p>
     *
     * @return a new style with the crossed-out attribute explicitly disabled
     * @see #crossedOut()
     * @see #crossedOutDefault()
     */
    public AttributedStyle crossedOutOff() {
        return new AttributedStyle(style & ~F_CROSSED_OUT, mask | F_CROSSED_OUT);
    }

    /**
     * Returns a new style with the crossed-out attribute set to its default state.
     *
     * <p>
     * This method returns a new style with no explicit setting for the crossed-out attribute.
     * When this style is applied, the crossed-out attribute will be inherited from the
     * parent style or use the terminal's default.
     * </p>
     *
     * @return a new style with the crossed-out attribute set to its default state
     * @see #crossedOut()
     * @see #crossedOutOff()
     */
    public AttributedStyle crossedOutDefault() {
        return new AttributedStyle(style & ~F_CROSSED_OUT, mask & ~F_CROSSED_OUT);
    }

    /**
     * Returns a new style with the specified foreground color.
     *
     * <p>
     * This method returns a new style with the specified foreground color.
     * The color is specified as an index into the terminal's color palette.
     * Standard ANSI colors are defined as constants in this class (BLACK, RED, etc.).
     * </p>
     *
     * <p>
     * For 256-color support, use values from 0 to 255. For standard ANSI colors,
     * use values from 0 to 7, or add BRIGHT (8) for bright variants.
     * </p>
     *
     * @param color the foreground color index
     * @return a new style with the specified foreground color
     * @see #foregroundOff()
     * @see #foregroundDefault()
     * @see #foreground(int, int, int)
     * @see #foregroundRgb(int)
     */
    public AttributedStyle foreground(int color) {
        return new AttributedStyle(
                style & ~FG_COLOR | F_FOREGROUND_IND | (((long) color << FG_COLOR_EXP) & FG_COLOR),
                mask | F_FOREGROUND_IND);
    }

    /**
     * Returns a new style with the specified RGB foreground color.
     *
     * <p>
     * This method returns a new style with the specified RGB foreground color.
     * The color is specified as separate red, green, and blue components,
     * each with a value from 0 to 255.
     * </p>
     *
     * <p>
     * Note that true color support (24-bit RGB) may not be available in all terminals.
     * In terminals without true color support, the color will be approximated using
     * the closest available color in the terminal's palette.
     * </p>
     *
     * @param r the red component (0-255)
     * @param g the green component (0-255)
     * @param b the blue component (0-255)
     * @return a new style with the specified RGB foreground color
     * @see #foregroundRgb(int)
     * @see #foregroundOff()
     * @see #foregroundDefault()
     */
    public AttributedStyle foreground(int r, int g, int b) {
        return foregroundRgb(r << 16 | g << 8 | b);
    }

    /**
     * Returns a new style with the specified RGB foreground color.
     *
     * <p>
     * This method returns a new style with the specified RGB foreground color.
     * The color is specified as a single integer in the format 0xRRGGBB,
     * where RR, GG, and BB are the red, green, and blue components in hexadecimal.
     * </p>
     *
     * <p>
     * Note that true color support (24-bit RGB) may not be available in all terminals.
     * In terminals without true color support, the color will be approximated using
     * the closest available color in the terminal's palette.
     * </p>
     *
     * @param color the RGB color value (0xRRGGBB)
     * @return a new style with the specified RGB foreground color
     * @see #foreground(int, int, int)
     * @see #foregroundOff()
     * @see #foregroundDefault()
     */
    public AttributedStyle foregroundRgb(int color) {
        return new AttributedStyle(
                style & ~FG_COLOR | F_FOREGROUND_RGB | ((((long) color & 0xFFFFFF) << FG_COLOR_EXP) & FG_COLOR),
                mask | F_FOREGROUND_RGB);
    }

    /**
     * Returns a new style with the foreground color explicitly disabled.
     *
     * <p>
     * This method returns a new style with the foreground color explicitly disabled.
     * This is different from {@link #foregroundDefault()}, which removes any explicit
     * setting for the foreground color.
     * </p>
     *
     * <p>
     * When a style with the foreground color disabled is applied, the text will
     * typically be displayed using the terminal's default foreground color.
     * </p>
     *
     * @return a new style with the foreground color explicitly disabled
     * @see #foreground(int)
     * @see #foregroundDefault()
     */
    public AttributedStyle foregroundOff() {
        return new AttributedStyle(style & ~FG_COLOR & ~F_FOREGROUND, mask | F_FOREGROUND);
    }

    /**
     * Returns a new style with the foreground color set to its default state.
     *
     * <p>
     * This method returns a new style with no explicit setting for the foreground color.
     * When this style is applied, the foreground color will be inherited from the
     * parent style or use the terminal's default.
     * </p>
     *
     * @return a new style with the foreground color set to its default state
     * @see #foreground(int)
     * @see #foregroundOff()
     */
    public AttributedStyle foregroundDefault() {
        return new AttributedStyle(style & ~FG_COLOR & ~F_FOREGROUND, mask & ~(F_FOREGROUND | FG_COLOR));
    }

    /**
     * Returns a new style with the specified background color.
     *
     * <p>
     * This method returns a new style with the specified background color.
     * The color is specified as an index into the terminal's color palette.
     * Standard ANSI colors are defined as constants in this class (BLACK, RED, etc.).
     * </p>
     *
     * <p>
     * For 256-color support, use values from 0 to 255. For standard ANSI colors,
     * use values from 0 to 7, or add BRIGHT (8) for bright variants.
     * </p>
     *
     * @param color the background color index
     * @return a new style with the specified background color
     * @see #backgroundOff()
     * @see #backgroundDefault()
     * @see #background(int, int, int)
     * @see #backgroundRgb(int)
     */
    public AttributedStyle background(int color) {
        return new AttributedStyle(
                style & ~BG_COLOR | F_BACKGROUND_IND | (((long) color << BG_COLOR_EXP) & BG_COLOR),
                mask | F_BACKGROUND_IND);
    }

    /**
     * Returns a new style with the specified RGB background color.
     *
     * <p>
     * This method returns a new style with the specified RGB background color.
     * The color is specified as separate red, green, and blue components,
     * each with a value from 0 to 255.
     * </p>
     *
     * <p>
     * Note that true color support (24-bit RGB) may not be available in all terminals.
     * In terminals without true color support, the color will be approximated using
     * the closest available color in the terminal's palette.
     * </p>
     *
     * @param r the red component (0-255)
     * @param g the green component (0-255)
     * @param b the blue component (0-255)
     * @return a new style with the specified RGB background color
     * @see #backgroundRgb(int)
     * @see #backgroundOff()
     * @see #backgroundDefault()
     */
    public AttributedStyle background(int r, int g, int b) {
        return backgroundRgb(r << 16 | g << 8 | b);
    }

    /**
     * Returns a new style with the specified RGB background color.
     *
     * <p>
     * This method returns a new style with the specified RGB background color.
     * The color is specified as a single integer in the format 0xRRGGBB,
     * where RR, GG, and BB are the red, green, and blue components in hexadecimal.
     * </p>
     *
     * <p>
     * Note that true color support (24-bit RGB) may not be available in all terminals.
     * In terminals without true color support, the color will be approximated using
     * the closest available color in the terminal's palette.
     * </p>
     *
     * @param color the RGB color value (0xRRGGBB)
     * @return a new style with the specified RGB background color
     * @see #background(int, int, int)
     * @see #backgroundOff()
     * @see #backgroundDefault()
     */
    public AttributedStyle backgroundRgb(int color) {
        return new AttributedStyle(
                style & ~BG_COLOR | F_BACKGROUND_RGB | ((((long) color & 0xFFFFFF) << BG_COLOR_EXP) & BG_COLOR),
                mask | F_BACKGROUND_RGB);
    }

    /**
     * Returns a new style with the background color explicitly disabled.
     *
     * <p>
     * This method returns a new style with the background color explicitly disabled.
     * This is different from {@link #backgroundDefault()}, which removes any explicit
     * setting for the background color.
     * </p>
     *
     * <p>
     * When a style with the background color disabled is applied, the text will
     * typically be displayed using the terminal's default background color.
     * </p>
     *
     * @return a new style with the background color explicitly disabled
     * @see #background(int)
     * @see #backgroundDefault()
     */
    public AttributedStyle backgroundOff() {
        return new AttributedStyle(style & ~BG_COLOR & ~F_BACKGROUND, mask | F_BACKGROUND);
    }

    /**
     * Returns a new style with the background color set to its default state.
     *
     * <p>
     * This method returns a new style with no explicit setting for the background color.
     * When this style is applied, the background color will be inherited from the
     * parent style or use the terminal's default.
     * </p>
     *
     * @return a new style with the background color set to its default state
     * @see #background(int)
     * @see #backgroundOff()
     */
    public AttributedStyle backgroundDefault() {
        return new AttributedStyle(style & ~BG_COLOR & ~F_BACKGROUND, mask & ~(F_BACKGROUND | BG_COLOR));
    }

    /**
     * The hidden flag can be used to embed custom escape sequences.
     * The characters are considered being 0-column long and will be printed as-is.
     * The user is responsible for ensuring that those sequences do not move the cursor.
     *
     * @return the new style
     */
    public AttributedStyle hidden() {
        return new AttributedStyle(style | F_HIDDEN, mask | F_HIDDEN);
    }

    /**
     * Returns a new style with the hidden attribute explicitly disabled.
     *
     * <p>
     * This method returns a new style with the hidden attribute explicitly disabled.
     * This is different from {@link #hiddenDefault()}, which removes any explicit
     * setting for the hidden attribute.
     * </p>
     *
     * @return a new style with the hidden attribute explicitly disabled
     * @see #hidden()
     * @see #hiddenDefault()
     */
    public AttributedStyle hiddenOff() {
        return new AttributedStyle(style & ~F_HIDDEN, mask | F_HIDDEN);
    }

    /**
     * Returns a new style with the hidden attribute set to its default state.
     *
     * <p>
     * This method returns a new style with no explicit setting for the hidden attribute.
     * When this style is applied, the hidden attribute will be inherited from the
     * parent style or use the terminal's default.
     * </p>
     *
     * @return a new style with the hidden attribute set to its default state
     * @see #hidden()
     * @see #hiddenOff()
     */
    public AttributedStyle hiddenDefault() {
        return new AttributedStyle(style & ~F_HIDDEN, mask & ~F_HIDDEN);
    }

    /**
     * Returns the raw style value of this style.
     *
     * <p>
     * This method returns the raw style value, which contains all the attributes
     * and colors encoded as bit flags in a long value. This is primarily for
     * internal use and advanced scenarios.
     * </p>
     *
     * @return the raw style value
     * @see #getMask()
     */
    public long getStyle() {
        return style;
    }

    /**
     * Returns the raw mask value of this style.
     *
     * <p>
     * This method returns the raw mask value, which indicates which attributes
     * and colors are explicitly set (as opposed to being inherited or default).
     * This is primarily for internal use and advanced scenarios.
     * </p>
     *
     * @return the raw mask value
     * @see #getStyle()
     */
    public long getMask() {
        return mask;
    }

    /**
     * Compares this AttributedStyle with another object for equality.
     *
     * <p>
     * Two AttributedStyle objects are considered equal if they have the same
     * style and mask values.
     * </p>
     *
     * @param o the object to compare with
     * @return {@code true} if the objects are equal, {@code false} otherwise
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AttributedStyle that = (AttributedStyle) o;
        if (style != that.style) return false;
        return mask == that.mask;
    }

    /**
     * Returns a hash code for this AttributedStyle.
     *
     * <p>
     * The hash code is computed based on the style and mask values.
     * </p>
     *
     * @return a hash code value for this object
     */
    @Override
    public int hashCode() {
        return 31 * Long.hashCode(style) + Long.hashCode(mask);
    }

    /**
     * Returns an ANSI escape sequence string that represents this style.
     *
     * <p>
     * This method generates an ANSI escape sequence string that, when printed to
     * a terminal, would apply this style. This is useful for debugging or for
     * generating ANSI-colored output for terminals that support it.
     * </p>
     *
     * <p>
     * The method works by creating a temporary AttributedStringBuilder, applying
     * this style to a space character, and then extracting the ANSI escape sequences
     * from the resulting string.
     * </p>
     *
     * @return an ANSI escape sequence string representing this style
     */
    public String toAnsi() {
        AttributedStringBuilder sb = new AttributedStringBuilder();
        sb.styled(this, " ");
        String s = sb.toAnsi(AttributedCharSequence.TRUE_COLORS, AttributedCharSequence.ForceMode.None);
        return s.length() > 1 ? s.substring(2, s.indexOf('m')) : s;
    }

    /**
     * Returns a string representation of this AttributedStyle.
     *
     * <p>
     * This method returns a string representation of this AttributedStyle,
     * including the style value, mask value, and ANSI escape sequence.
     * This is primarily useful for debugging.
     * </p>
     *
     * @return a string representation of this AttributedStyle
     */
    @Override
    public String toString() {
        return "AttributedStyle{" + "style=" + style + ", mask=" + mask + ", ansi=" + toAnsi() + '}';
    }
}
