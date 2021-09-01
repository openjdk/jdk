/*
 * Copyright (c) 2002-2021, the original author or authors.
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package jdk.internal.org.jline.utils;

/**
 * Text styling.
 *
 * @author <a href="mailto:gnodet@gmail.com">Guillaume Nodet</a>
 */
public class AttributedStyle {

    public static final int BLACK =     0;
    public static final int RED =       1;
    public static final int GREEN =     2;
    public static final int YELLOW =    3;
    public static final int BLUE =      4;
    public static final int MAGENTA =   5;
    public static final int CYAN =      6;
    public static final int WHITE =     7;

    public static final int BRIGHT =    8;

    static final long F_BOLD            = 0x00000001;
    static final long F_FAINT           = 0x00000002;
    static final long F_ITALIC          = 0x00000004;
    static final long F_UNDERLINE       = 0x00000008;
    static final long F_BLINK           = 0x00000010;
    static final long F_INVERSE         = 0x00000020;
    static final long F_CONCEAL         = 0x00000040;
    static final long F_CROSSED_OUT     = 0x00000080;
    static final long F_FOREGROUND_IND  = 0x00000100;
    static final long F_FOREGROUND_RGB  = 0x00000200;
    static final long F_FOREGROUND      = F_FOREGROUND_IND | F_FOREGROUND_RGB;
    static final long F_BACKGROUND_IND  = 0x00000400;
    static final long F_BACKGROUND_RGB  = 0x00000800;
    static final long F_BACKGROUND      = F_BACKGROUND_IND | F_BACKGROUND_RGB;
    static final long F_HIDDEN          = 0x00001000;

    static final long MASK           = 0x00001FFF;

    static final int FG_COLOR_EXP    = 15;
    static final int BG_COLOR_EXP    = 39;
    static final long FG_COLOR        = 0xFFFFFFL << FG_COLOR_EXP;
    static final long BG_COLOR        = 0xFFFFFFL << BG_COLOR_EXP;

    public static final AttributedStyle DEFAULT = new AttributedStyle();
    public static final AttributedStyle BOLD = DEFAULT.bold();
    public static final AttributedStyle BOLD_OFF = DEFAULT.boldOff();
    public static final AttributedStyle INVERSE = DEFAULT.inverse();
    public static final AttributedStyle INVERSE_OFF = DEFAULT.inverseOff();
    public static final AttributedStyle HIDDEN = DEFAULT.hidden();
    public static final AttributedStyle HIDDEN_OFF = DEFAULT.hiddenOff();

    final long style;
    final long mask;

    public AttributedStyle() {
        this(0, 0);
    }

    public AttributedStyle(AttributedStyle s) {
        this(s.style, s.mask);
    }

    public AttributedStyle(long style, long mask) {
        this.style = style;
        this.mask = mask & MASK | ((style & F_FOREGROUND) != 0 ? FG_COLOR : 0)
                                | ((style & F_BACKGROUND) != 0 ? BG_COLOR : 0);
    }

    public AttributedStyle bold() {
        return new AttributedStyle(style | F_BOLD, mask | F_BOLD);
    }

    public AttributedStyle boldOff() {
        return new AttributedStyle(style & ~F_BOLD, mask | F_BOLD);
    }

    public AttributedStyle boldDefault() {
        return new AttributedStyle(style & ~F_BOLD, mask & ~F_BOLD);
    }

    public AttributedStyle faint() {
        return new AttributedStyle(style | F_FAINT, mask | F_FAINT);
    }

    public AttributedStyle faintOff() {
        return new AttributedStyle(style & ~F_FAINT, mask | F_FAINT);
    }

    public AttributedStyle faintDefault() {
        return new AttributedStyle(style & ~F_FAINT, mask & ~F_FAINT);
    }

    public AttributedStyle italic() {
        return new AttributedStyle(style | F_ITALIC, mask | F_ITALIC);
    }

    public AttributedStyle italicOff() {
        return new AttributedStyle(style & ~F_ITALIC, mask | F_ITALIC);
    }

    public AttributedStyle italicDefault() {
        return new AttributedStyle(style & ~F_ITALIC, mask & ~F_ITALIC);
    }

    public AttributedStyle underline() {
        return new AttributedStyle(style | F_UNDERLINE, mask | F_UNDERLINE);
    }

    public AttributedStyle underlineOff() {
        return new AttributedStyle(style & ~F_UNDERLINE, mask | F_UNDERLINE);
    }

    public AttributedStyle underlineDefault() {
        return new AttributedStyle(style & ~F_UNDERLINE, mask & ~F_UNDERLINE);
    }

    public AttributedStyle blink() {
        return new AttributedStyle(style | F_BLINK, mask | F_BLINK);
    }

    public AttributedStyle blinkOff() {
        return new AttributedStyle(style & ~F_BLINK, mask | F_BLINK);
    }

    public AttributedStyle blinkDefault() {
        return new AttributedStyle(style & ~F_BLINK, mask & ~F_BLINK);
    }

    public AttributedStyle inverse() {
        return new AttributedStyle(style | F_INVERSE, mask | F_INVERSE);
    }

    public AttributedStyle inverseNeg() {
        long s = (style & F_INVERSE) != 0 ? style & ~F_INVERSE : style | F_INVERSE;
        return new AttributedStyle(s, mask | F_INVERSE);
    }

    public AttributedStyle inverseOff() {
        return new AttributedStyle(style & ~F_INVERSE, mask | F_INVERSE);
    }

    public AttributedStyle inverseDefault() {
        return new AttributedStyle(style & ~F_INVERSE, mask & ~F_INVERSE);
    }

    public AttributedStyle conceal() {
        return new AttributedStyle(style | F_CONCEAL, mask | F_CONCEAL);
    }

    public AttributedStyle concealOff() {
        return new AttributedStyle(style & ~F_CONCEAL, mask | F_CONCEAL);
    }

    public AttributedStyle concealDefault() {
        return new AttributedStyle(style & ~F_CONCEAL, mask & ~F_CONCEAL);
    }

    public AttributedStyle crossedOut() {
        return new AttributedStyle(style | F_CROSSED_OUT, mask | F_CROSSED_OUT);
    }

    public AttributedStyle crossedOutOff() {
        return new AttributedStyle(style & ~F_CROSSED_OUT, mask | F_CROSSED_OUT);
    }

    public AttributedStyle crossedOutDefault() {
        return new AttributedStyle(style & ~F_CROSSED_OUT, mask & ~F_CROSSED_OUT);
    }

    public AttributedStyle foreground(int color) {
        return new AttributedStyle(style & ~FG_COLOR | F_FOREGROUND_IND | (((long) color << FG_COLOR_EXP) & FG_COLOR), mask | F_FOREGROUND_IND);
    }

    public AttributedStyle foreground(int r, int g, int b) {
        return foregroundRgb(r << 16 | g << 8 | b);
    }

    public AttributedStyle foregroundRgb(int color) {
        return new AttributedStyle(style & ~FG_COLOR | F_FOREGROUND_RGB | ((((long) color & 0xFFFFFF) << FG_COLOR_EXP) & FG_COLOR), mask | F_FOREGROUND_RGB);
    }

    public AttributedStyle foregroundOff() {
        return new AttributedStyle(style & ~FG_COLOR & ~F_FOREGROUND, mask | F_FOREGROUND);
    }

    public AttributedStyle foregroundDefault() {
        return new AttributedStyle(style & ~FG_COLOR & ~F_FOREGROUND, mask & ~(F_FOREGROUND | FG_COLOR));
    }

    public AttributedStyle background(int color) {
        return new AttributedStyle(style & ~BG_COLOR | F_BACKGROUND_IND | (((long) color << BG_COLOR_EXP) & BG_COLOR), mask | F_BACKGROUND_IND);
    }

    public AttributedStyle background(int r, int g, int b) {
        return backgroundRgb(r << 16 | g << 8 | b);
    }

    public AttributedStyle backgroundRgb(int color) {
        return new AttributedStyle(style & ~BG_COLOR | F_BACKGROUND_RGB | ((((long) color & 0xFFFFFF) << BG_COLOR_EXP) & BG_COLOR), mask | F_BACKGROUND_RGB);
    }

    public AttributedStyle backgroundOff() {
        return new AttributedStyle(style & ~BG_COLOR & ~F_BACKGROUND, mask | F_BACKGROUND);
    }

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

    public AttributedStyle hiddenOff() {
        return new AttributedStyle(style & ~F_HIDDEN, mask | F_HIDDEN);
    }

    public AttributedStyle hiddenDefault() {
        return new AttributedStyle(style & ~F_HIDDEN, mask & ~F_HIDDEN);
    }

    public long getStyle() {
        return style;
    }

    public long getMask() {
        return mask;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AttributedStyle that = (AttributedStyle) o;
        if (style != that.style) return false;
        return mask == that.mask;

    }

    @Override
    public int hashCode() {
        return 31 * Long.hashCode(style) + Long.hashCode(mask);
    }

    public String toAnsi() {
        AttributedStringBuilder sb = new AttributedStringBuilder();
        sb.styled(this, " ");
        String s = sb.toAnsi(AttributedCharSequence.TRUE_COLORS, AttributedCharSequence.ForceMode.None);
        return s.length() > 1 ? s.substring(2, s.indexOf('m')) : s;
    }

    @Override
    public String toString() {
        return "AttributedStyle{" +
                "style=" + style +
                ", mask=" + mask +
                ", ansi=" + toAnsi() +
                '}';
    }
}
