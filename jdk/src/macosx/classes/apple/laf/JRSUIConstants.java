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

package apple.laf;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;

public final class JRSUIConstants {
    private static native long getPtrForConstant(final int constant);

    static class Key {
        protected static final int _value = 20;
        public static final Key VALUE = new Key(_value);

        protected static final int _thumbProportion = 24;
        public static final Key THUMB_PROPORTION = new Key(_thumbProportion);

        protected static final int _thumbStart = 25;
        public static final Key THUMB_START = new Key(_thumbStart);

        protected static final int _windowTitleBarHeight = 28;
        public static final Key WINDOW_TITLE_BAR_HEIGHT = new Key(_windowTitleBarHeight);

        protected static final int _animationFrame = 23;
        public static final Key ANIMATION_FRAME = new Key(_animationFrame);

        final int constant;
        private long ptr;

        private Key(final int constant) {
            this.constant = constant;
        }

        long getConstantPtr() {
            if (ptr != 0) return ptr;
            ptr = getPtrForConstant(constant);
            if (ptr != 0) return ptr;
            throw new RuntimeException("Constant not implemented in native: " + this);
        }

        public String toString() {
            return getConstantName(this) + (ptr == 0 ? "(unlinked)" : "");
        }
    }

    static class DoubleValue {
        protected static final byte TYPE_CODE = 1;

        final double doubleValue;

        DoubleValue(final double doubleValue) {
            this.doubleValue = doubleValue;
        }

        public byte getTypeCode() {
            return TYPE_CODE;
        }

        public void putValueInBuffer(final ByteBuffer buffer) {
            buffer.putDouble(doubleValue);
        }

        public boolean equals(final Object obj) {
            return (obj instanceof DoubleValue) && (((DoubleValue)obj).doubleValue == doubleValue);
        }

        public int hashCode() {
            final long bits = Double.doubleToLongBits(doubleValue);
            return (int)(bits ^ (bits >>> 32));
        }

        public String toString() {
            return Double.toString(doubleValue);
        }
    }


    static class PropertyEncoding {
        final long mask;
        final byte shift;

        PropertyEncoding(final long mask, final byte shift) {
            this.mask = mask;
            this.shift = shift;
        }
    }

    static class Property {
        final PropertyEncoding encoding;
        final long value;
        final byte ordinal;

        Property(final PropertyEncoding encoding, final byte ordinal) {
            this.encoding = encoding;
            this.value = ((long)ordinal) << encoding.shift;
            this.ordinal = ordinal;
        }

        /**
         * Applies this property value to the provided state
         * @param encodedState the incoming JRSUI encoded state
         * @return the composite of the provided JRSUI encoded state and this value
         */
        public long apply(final long encodedState) {
            return (encodedState & ~encoding.mask) | value;
        }

        public String toString() {
            return getConstantName(this);
        }
    }

    public static class Size extends Property {
        private static final byte SHIFT = 0;
        private static final byte SIZE = 3;
        private static final long MASK = (long)0x7 << SHIFT;
        private static final PropertyEncoding size = new PropertyEncoding(MASK, SHIFT);

        Size(final byte value) {
            super(size, value);
        }

        private static final byte _mini = 1;
        public static final Size MINI = new Size(_mini);
        private static final byte _small = 2;
        public static final Size SMALL = new Size(_small);
        private static final byte _regular = 3;
        public static final Size REGULAR = new Size(_regular);
        private static final byte _large = 4;
        public static final Size LARGE = new Size(_large);
    }

    public static class State extends Property {
        private static final byte SHIFT = Size.SHIFT + Size.SIZE;
        private static final byte SIZE = 4;
        private static final long MASK = (long)0xF << SHIFT;
        private static final PropertyEncoding state = new PropertyEncoding(MASK, SHIFT);

        State(final byte value) {
            super(state, value);
        }

        private static final byte _active = 1;
        public static final State ACTIVE = new State(_active);
        private static final byte _inactive = 2;
        public static final State INACTIVE = new State(_inactive);
        private static final byte _disabled = 3;
        public static final State DISABLED = new State(_disabled);
        private static final byte _pressed = 4;
        public static final State PRESSED = new State(_pressed);
        private static final byte _pulsed = 5;
        public static final State PULSED = new State(_pulsed);
        private static final byte _rollover = 6;
        public static final State ROLLOVER = new State(_rollover);
        private static final byte _drag = 7;
        public static final State DRAG = new State(_drag);
    }

    public static class Direction extends Property {
        private static final byte SHIFT = State.SHIFT + State.SIZE;
        private static final byte SIZE = 4;
        private static final long MASK = (long)0xF << SHIFT;
        private static final PropertyEncoding direction = new PropertyEncoding(MASK, SHIFT);

        Direction(final byte value) {
            super(direction, value);
        }

        private static final byte _none = 1;
        public static final Direction NONE = new Direction(_none);
        private static final byte _up = 2;
        public static final Direction UP = new Direction(_up);
        private static final byte _down = 3;
        public static final Direction DOWN = new Direction(_down);
        private static final byte _left = 4;
        public static final Direction LEFT = new Direction(_left);
        private static final byte _right = 5;
        public static final Direction RIGHT = new Direction(_right);
        private static final byte _north = 6;
        public static final Direction NORTH = new Direction(_north);
        private static final byte _south = 7;
        public static final Direction SOUTH = new Direction(_south);
        private static final byte _east = 8;
        public static final Direction EAST = new Direction(_east);
        private static final byte _west = 9;
        public static final Direction WEST = new Direction(_west);
    }

    public static class Orientation extends Property {
        private static final byte SHIFT = Direction.SHIFT + Direction.SIZE;
        private static final byte SIZE = 2;
        private static final long MASK = (long)0x3 << SHIFT;
        private static final PropertyEncoding orientation = new PropertyEncoding(MASK, SHIFT);

        Orientation(final byte value) {
            super(orientation, value);
        }

        private static final byte _horizontal = 1;
        public static final Orientation HORIZONTAL = new Orientation(_horizontal);
        private static final byte _vertical = 2;
        public static final Orientation VERTICAL = new Orientation(_vertical);
    }

    public static class AlignmentVertical extends Property {
        private static final byte SHIFT = Orientation.SHIFT + Orientation.SIZE;
        private static final byte SIZE = 2;
        private static final long MASK = (long)0x3 << SHIFT;
        private static final PropertyEncoding alignmentVertical = new PropertyEncoding(MASK, SHIFT);

        AlignmentVertical(final byte value){
            super(alignmentVertical, value);
        }

        private static final byte _top = 1;
        public static final AlignmentVertical TOP = new AlignmentVertical(_top);
        private static final byte _center = 2;
        public static final AlignmentVertical CENTER = new AlignmentVertical(_center);
        private static final byte _bottom = 3;
        public static final AlignmentVertical BOTTOM = new AlignmentVertical(_bottom);
    }

    public static class AlignmentHorizontal extends Property {
        private static final byte SHIFT = AlignmentVertical.SHIFT + AlignmentVertical.SIZE;
        private static final byte SIZE = 2;
        private static final long MASK = (long)0x3 << SHIFT;
        private static final PropertyEncoding alignmentHorizontal = new PropertyEncoding(MASK, SHIFT);

        AlignmentHorizontal(final byte value){
            super(alignmentHorizontal, value);
        }

        private static final byte _left = 1;
        public static final AlignmentHorizontal LEFT = new AlignmentHorizontal(_left);
        private static final byte _center =  2;
        public static final AlignmentHorizontal CENTER = new AlignmentHorizontal(_center);
        private static final byte _right = 3;
        public static final AlignmentHorizontal RIGHT = new AlignmentHorizontal(_right);
    }

    public static class SegmentPosition extends Property {
        private static final byte SHIFT = AlignmentHorizontal.SHIFT + AlignmentHorizontal.SIZE;
        private static final byte SIZE = 3;
        private static final long MASK = (long)0x7 << SHIFT;
        private static final PropertyEncoding segmentPosition = new PropertyEncoding(MASK, SHIFT);

        SegmentPosition(final byte value) {
            super(segmentPosition, value);
        }

        private static final byte _first = 1;
        public static final SegmentPosition FIRST = new SegmentPosition(_first);
        private static final byte _middle = 2;
        public static final SegmentPosition MIDDLE = new SegmentPosition(_middle);
        private static final byte _last = 3;
        public static final SegmentPosition LAST = new SegmentPosition(_last);
        private static final byte _only = 4;
        public static final SegmentPosition ONLY = new SegmentPosition(_only);
    }

    public static class ScrollBarPart extends Property {
        private static final byte SHIFT = SegmentPosition.SHIFT + SegmentPosition.SIZE;
        private static final byte SIZE = 4;
        private static final long MASK = (long)0xF << SHIFT;
        private static final PropertyEncoding scrollBarPart = new PropertyEncoding(MASK, SHIFT);

        ScrollBarPart(final byte value) {
            super(scrollBarPart, value);
        }

        private static final byte _none = 1;
        public static final ScrollBarPart NONE = new ScrollBarPart(_none);
        private static final byte _thumb = 2;
        public static final ScrollBarPart THUMB = new ScrollBarPart(_thumb);
        private static final byte _arrowMin = 3;
        public static final ScrollBarPart ARROW_MIN = new ScrollBarPart(_arrowMin);
        private static final byte _arrowMax = 4;
        public static final ScrollBarPart ARROW_MAX = new ScrollBarPart(_arrowMax);
        private static final byte _arrowMaxInside = 5;
        public static final ScrollBarPart ARROW_MAX_INSIDE = new ScrollBarPart(_arrowMaxInside);
        private static final byte _arrowMinInside = 6;
        public static final ScrollBarPart ARROW_MIN_INSIDE = new ScrollBarPart(_arrowMinInside);
        private static final byte _trackMin = 7;
        public static final ScrollBarPart TRACK_MIN = new ScrollBarPart(_trackMin);
        private static final byte _trackMax = 8;
        public static final ScrollBarPart TRACK_MAX = new ScrollBarPart(_trackMax);
    }

    public static class Variant extends Property {
        private static final byte SHIFT = ScrollBarPart.SHIFT + ScrollBarPart.SIZE;
        private static final byte SIZE = 4;
        private static final long MASK = (long)0xF << SHIFT;
        private static final PropertyEncoding variant = new PropertyEncoding(MASK, SHIFT);

        Variant(final byte value) {
            super(variant, value);
        }

        private static final byte _menuGlyph = 1;
        public static final Variant MENU_GLYPH = new Variant(_menuGlyph);
        private static final byte _menuPopup = Variant._menuGlyph + 1;
        public static final Variant MENU_POPUP = new Variant(_menuPopup);
        private static final byte _menuPulldown = Variant._menuPopup + 1;
        public static final Variant MENU_PULLDOWN = new Variant(_menuPulldown);
        private static final byte _menuHierarchical = Variant._menuPulldown + 1;
        public static final Variant MENU_HIERARCHICAL = new Variant(_menuHierarchical);

        private static final byte _gradientListBackgroundEven = Variant._menuHierarchical + 1;
        public static final Variant GRADIENT_LIST_BACKGROUND_EVEN = new Variant(_gradientListBackgroundEven);
        private static final byte _gradientListBackgroundOdd = Variant._gradientListBackgroundEven + 1;
        public static final Variant GRADIENT_LIST_BACKGROUND_ODD = new Variant(_gradientListBackgroundOdd);
        private static final byte _gradientSideBar = Variant._gradientListBackgroundOdd + 1;
        public static final Variant GRADIENT_SIDE_BAR = new Variant(_gradientSideBar);
        private static final byte _gradientSideBarSelection = Variant._gradientSideBar + 1;
        public static final Variant GRADIENT_SIDE_BAR_SELECTION = new Variant(_gradientSideBarSelection);
        private static final byte _gradientSideBarFocusedSelection = Variant._gradientSideBarSelection + 1;
        public static final Variant GRADIENT_SIDE_BAR_FOCUSED_SELECTION = new Variant(_gradientSideBarFocusedSelection);
    }

    public static class WindowType extends Property {
        private static final byte SHIFT = Variant.SHIFT + Variant.SIZE;
        private static final byte SIZE = 2;
        private static final long MASK = (long)0x3 << SHIFT;
        private static final PropertyEncoding windowType = new PropertyEncoding(MASK, SHIFT);

        WindowType(final byte value){
            super(windowType, value);
        }

        private static final byte _document = 1;
        public static final WindowType DOCUMENT = new WindowType(_document);
        private static final byte _utility = 2;
        public static final WindowType UTILITY = new WindowType(_utility);
        private static final byte _titlelessUtility = 3;
        public static final WindowType TITLELESS_UTILITY = new WindowType(_titlelessUtility);
    }

    public static class Focused extends Property {
        private static final byte SHIFT = WindowType.SHIFT + WindowType.SIZE;
        private static final byte SIZE = 1;
        private static final long MASK = (long)0x1 << SHIFT;
        private static final PropertyEncoding focused = new PropertyEncoding(MASK, SHIFT);

        Focused(final byte value) {
            super(focused, value);
        }

        private static final byte _no = 0;
        public static final Focused NO = new Focused(_no);
        private static final byte _yes = 1;
        public static final Focused YES = new Focused(_yes);
    }

    public static class IndicatorOnly extends Property {
        private static final byte SHIFT = Focused.SHIFT + Focused.SIZE;
        private static final byte SIZE = 1;
        private static final long MASK = (long)0x1 << SHIFT;
        private static final PropertyEncoding indicatorOnly = new PropertyEncoding(MASK, SHIFT);

        IndicatorOnly(final byte value) {
            super(indicatorOnly, value);
        }

        private static final byte _no = 0;
        public static final IndicatorOnly NO = new IndicatorOnly(_no);
        private static final byte _yes = 1;
        public static final IndicatorOnly YES = new IndicatorOnly(_yes);
    }

    public static class NoIndicator extends Property {
        private static final byte SHIFT = IndicatorOnly.SHIFT + IndicatorOnly.SIZE;
        private static final byte SIZE = 1;
        private static final long MASK = (long)0x1 << SHIFT;
        private static final PropertyEncoding noIndicator = new PropertyEncoding(MASK, SHIFT);

        NoIndicator(final byte value) {
            super(noIndicator, value);
        }

        private static final byte _no = 0;
        public static final NoIndicator NO = new NoIndicator(_no);
        private static final byte _yes = 1;
        public static final NoIndicator YES = new NoIndicator(_yes);
    }

    public static class ArrowsOnly extends Property {
        private static final byte SHIFT = NoIndicator.SHIFT + NoIndicator.SIZE;
        private static final byte SIZE = 1;
        private static final long MASK = (long)0x1 << SHIFT;
        private static final PropertyEncoding focused = new PropertyEncoding(MASK, SHIFT);

        ArrowsOnly(final byte value) {
            super(focused, value);
        }

        private static final byte _no = 0;
        public static final ArrowsOnly NO = new ArrowsOnly(_no);
        private static final byte _yes = 1;
        public static final ArrowsOnly YES = new ArrowsOnly(_yes);
    }

    public static class FrameOnly extends Property {
        private static final byte SHIFT = ArrowsOnly.SHIFT + ArrowsOnly.SIZE;
        private static final byte SIZE = 1;
        private static final long MASK = (long)0x1 << SHIFT;
        private static final PropertyEncoding focused = new PropertyEncoding(MASK, SHIFT);

        FrameOnly(final byte value) {
            super(focused, value);
        }

        private static final byte _no = 0;
        public static final FrameOnly NO = new FrameOnly(_no);
        private static final byte _yes = 1;
        public static final FrameOnly YES = new FrameOnly(_yes);
    }

    public static class SegmentTrailingSeparator extends Property {
        private static final byte SHIFT = FrameOnly.SHIFT + FrameOnly.SIZE;
        private static final byte SIZE = 1;
        private static final long MASK = (long)0x1 << SHIFT;
        private static final PropertyEncoding focused = new PropertyEncoding(MASK, SHIFT);

        SegmentTrailingSeparator(final byte value) {
            super(focused, value);
        }

        private static final byte _no = 0;
        public static final SegmentTrailingSeparator NO = new SegmentTrailingSeparator(_no);
        private static final byte _yes = 1;
        public static final SegmentTrailingSeparator YES = new SegmentTrailingSeparator(_yes);
    }

    public static class SegmentLeadingSeparator extends Property {
        private static final byte SHIFT = SegmentTrailingSeparator.SHIFT + SegmentTrailingSeparator.SIZE;
        private static final byte SIZE = 1;
        private static final long MASK = (long)0x1 << SHIFT;
        private static final PropertyEncoding leadingSeparator = new PropertyEncoding(MASK, SHIFT);

        SegmentLeadingSeparator(final byte value) {
            super(leadingSeparator, value);
        }

        private static final byte _no = 0;
        public static final SegmentLeadingSeparator NO = new SegmentLeadingSeparator(_no);
        private static final byte _yes = 1;
        public static final SegmentLeadingSeparator YES = new SegmentLeadingSeparator(_yes);
    }

    public static class NothingToScroll extends Property {
        private static final byte SHIFT = SegmentLeadingSeparator.SHIFT + SegmentLeadingSeparator.SIZE;
        private static final byte SIZE = 1;
        private static final long MASK = (long)0x1 << SHIFT;
        private static final PropertyEncoding focused = new PropertyEncoding(MASK, SHIFT);

        NothingToScroll(final byte value) {
            super(focused, value);
        }

        private static final byte _no = 0;
        public static final NothingToScroll NO = new NothingToScroll(_no);
        private static final byte _yes = 1;
        public static final NothingToScroll YES = new NothingToScroll(_yes);
    }

    public static class WindowTitleBarSeparator extends Property {
        private static final byte SHIFT = NothingToScroll.SHIFT + NothingToScroll.SIZE;
        private static final byte SIZE = 1;
        private static final long MASK = (long)0x1 << SHIFT;
        private static final PropertyEncoding focused = new PropertyEncoding(MASK, SHIFT);

        WindowTitleBarSeparator(final byte value) {
            super(focused, value);
        }

        private static final byte _no = 0;
        public static final WindowTitleBarSeparator NO = new WindowTitleBarSeparator(_no);
        private static final byte _yes = 1;
        public static final WindowTitleBarSeparator YES = new WindowTitleBarSeparator(_yes);
    }

    public static class WindowClipCorners extends Property {
        private static final byte SHIFT = WindowTitleBarSeparator.SHIFT + WindowTitleBarSeparator.SIZE;
        private static final byte SIZE = 1;
        private static final long MASK = (long)0x1 << SHIFT;
        private static final PropertyEncoding focused = new PropertyEncoding(MASK, SHIFT);

        WindowClipCorners(final byte value) {
            super(focused, value);
        }

        private static final byte _no = 0;
        public static final WindowClipCorners NO = new WindowClipCorners(_no);
        private static final byte _yes = 1;
        public static final WindowClipCorners YES = new WindowClipCorners(_yes);
    }

    public static class ShowArrows extends Property {
        private static final byte SHIFT = WindowClipCorners.SHIFT + WindowClipCorners.SIZE;
        private static final byte SIZE = 1;
        private static final long MASK = (long)0x1 << SHIFT;
        private static final PropertyEncoding showArrows = new PropertyEncoding(MASK, SHIFT);

        ShowArrows(final byte value) {
            super(showArrows, value);
        }

        private static final byte _no = 0;
        public static final ShowArrows NO = new ShowArrows(_no);
        private static final byte _yes = 1;
        public static final ShowArrows YES = new ShowArrows(_yes);
    }

    public static class BooleanValue extends Property {
        private static final byte SHIFT = ShowArrows.SHIFT + ShowArrows.SIZE;
        private static final byte SIZE = 1;
        private static final long MASK = (long)0x1 << SHIFT;
        private static final PropertyEncoding booleanValue = new PropertyEncoding(MASK, SHIFT);

        BooleanValue(final byte value) {
            super(booleanValue, value);
        }

        private static final byte _no = 0;
        public static final BooleanValue NO = new BooleanValue(_no);
        private static final byte _yes = 1;
        public static final BooleanValue YES = new BooleanValue(_yes);
    }

    public static class Animating extends Property {
        private static final byte SHIFT = BooleanValue.SHIFT + BooleanValue.SIZE;
        private static final byte SIZE = 1;
        private static final long MASK = (long)0x1 << SHIFT;
        private static final PropertyEncoding animating = new PropertyEncoding(MASK, SHIFT);

        Animating(final byte value) {
            super(animating, value);
        }

        private static final byte _no = 0;
        public static final Animating NO = new Animating(_no);
        private static final byte _yes = 1;
        public static final Animating YES = new Animating(_yes);
    }

    public static class Widget extends Property {
        private static final byte SHIFT = Animating.SHIFT + Animating.SIZE;
        private static final byte SIZE = 7;
        private static final long MASK = (long)0x7F << SHIFT;
        private static final PropertyEncoding widget = new PropertyEncoding(MASK, SHIFT);

        Widget(final byte constant) {
            super(widget, constant);
        }

        private static final byte _background = 1;
        public static final Widget BACKGROUND = new Widget(_background);

        private static final byte _buttonBevel = _background + 1;
        public static final Widget BUTTON_BEVEL = new Widget(_buttonBevel);
        private static final byte _buttonBevelInset = _buttonBevel + 1;
        public static final Widget BUTTON_BEVEL_INSET = new Widget(_buttonBevelInset);
        private static final byte _buttonBevelRound = _buttonBevelInset + 1;
        public static final Widget BUTTON_BEVEL_ROUND = new Widget(_buttonBevelRound);

        private static final byte _buttonCheckBox = _buttonBevelRound + 1;
        public static final Widget BUTTON_CHECK_BOX = new Widget(_buttonCheckBox);

        private static final byte _buttonComboBox = _buttonCheckBox + 1;
        public static final Widget BUTTON_COMBO_BOX = new Widget(_buttonComboBox);
        private static final byte _buttonComboBoxInset = _buttonComboBox + 1;
        public static final Widget BUTTON_COMBO_BOX_INSET = new Widget(_buttonComboBoxInset); // not hooked up in JRSUIConstants.m

        private static final byte _buttonDisclosure = _buttonComboBoxInset + 1;
        public static final Widget BUTTON_DISCLOSURE = new Widget(_buttonDisclosure);

        private static final byte _buttonListHeader = _buttonDisclosure + 1;
        public static final Widget BUTTON_LIST_HEADER = new Widget(_buttonListHeader);

        private static final byte _buttonLittleArrows = _buttonListHeader + 1;
        public static final Widget BUTTON_LITTLE_ARROWS = new Widget(_buttonLittleArrows);

        private static final byte _buttonPopDown = _buttonLittleArrows + 1;
        public static final Widget BUTTON_POP_DOWN = new Widget(_buttonPopDown);
        private static final byte _buttonPopDownInset = _buttonPopDown + 1;
        public static final Widget BUTTON_POP_DOWN_INSET = new Widget(_buttonPopDownInset);
        private static final byte _buttonPopDownSquare = _buttonPopDownInset + 1;
        public static final Widget BUTTON_POP_DOWN_SQUARE = new Widget(_buttonPopDownSquare);

        private static final byte _buttonPopUp = _buttonPopDownSquare + 1;
        public static final Widget BUTTON_POP_UP = new Widget(_buttonPopUp);
        private static final byte _buttonPopUpInset = _buttonPopUp + 1;
        public static final Widget BUTTON_POP_UP_INSET = new Widget(_buttonPopUpInset);
        private static final byte _buttonPopUpSquare = _buttonPopUpInset + 1;
        public static final Widget BUTTON_POP_UP_SQUARE = new Widget(_buttonPopUpSquare);

        private static final byte _buttonPush = _buttonPopUpSquare + 1;
        public static final Widget BUTTON_PUSH = new Widget(_buttonPush);
        private static final byte _buttonPushScope = _buttonPush + 1;
        public static final Widget BUTTON_PUSH_SCOPE = new Widget(_buttonPushScope);
        private static final byte _buttonPushScope2 = _buttonPushScope + 1;
        public static final Widget BUTTON_PUSH_SCOPE2 = new Widget(_buttonPushScope2);
        private static final byte _buttonPushTextured = _buttonPushScope2 + 1;
        public static final Widget BUTTON_PUSH_TEXTURED = new Widget(_buttonPushTextured);
        private static final byte _buttonPushInset = _buttonPushTextured + 1;
        public static final Widget BUTTON_PUSH_INSET = new Widget(_buttonPushInset);
        private static final byte _buttonPushInset2 = _buttonPushInset + 1;
        public static final Widget BUTTON_PUSH_INSET2 = new Widget(_buttonPushInset2);

        private static final byte _buttonRadio = _buttonPushInset2 + 1;
        public static final Widget BUTTON_RADIO = new Widget(_buttonRadio);

        private static final byte _buttonRound = _buttonRadio + 1;
        public static final Widget BUTTON_ROUND = new Widget(_buttonRound);
        private static final byte _buttonRoundHelp = _buttonRound + 1;
        public static final Widget BUTTON_ROUND_HELP = new Widget(_buttonRoundHelp);
        private static final byte _buttonRoundInset = _buttonRoundHelp + 1;
        public static final Widget BUTTON_ROUND_INSET = new Widget(_buttonRoundInset);
        private static final byte _buttonRoundInset2 =_buttonRoundInset + 1;
        public static final Widget BUTTON_ROUND_INSET2 = new Widget(_buttonRoundInset2);

        private static final byte _buttonSearchFieldCancel = _buttonRoundInset2 + 1;
        public static final Widget BUTTON_SEARCH_FIELD_CANCEL = new Widget(_buttonSearchFieldCancel);
        private static final byte _buttonSearchFieldFind = _buttonSearchFieldCancel + 1;
        public static final Widget BUTTON_SEARCH_FIELD_FIND = new Widget(_buttonSearchFieldFind);

        private static final byte _buttonSegmented = _buttonSearchFieldFind + 1;
        public static final Widget BUTTON_SEGMENTED = new Widget(_buttonSegmented);
        private static final byte _buttonSegmentedInset = _buttonSegmented + 1;
        public static final Widget BUTTON_SEGMENTED_INSET = new Widget(_buttonSegmentedInset);
        private static final byte _buttonSegmentedInset2 = _buttonSegmentedInset + 1;
        public static final Widget BUTTON_SEGMENTED_INSET2 = new Widget(_buttonSegmentedInset2);
        private static final byte _buttonSegmentedSCurve = _buttonSegmentedInset2 + 1;
        public static final Widget BUTTON_SEGMENTED_SCURVE = new Widget(_buttonSegmentedSCurve);
        private static final byte _buttonSegmentedTextured = _buttonSegmentedSCurve + 1;
        public static final Widget BUTTON_SEGMENTED_TEXTURED = new Widget(_buttonSegmentedTextured);
        private static final byte _buttonSegmentedToolbar = _buttonSegmentedTextured + 1;
        public static final Widget BUTTON_SEGMENTED_TOOLBAR = new Widget(_buttonSegmentedToolbar);

        private static final byte _dial = _buttonSegmentedToolbar + 1;
        public static final Widget DIAL = new Widget(_dial);

        private static final byte _disclosureTriangle = _dial + 1;
        public static final Widget DISCLOSURE_TRIANGLE = new Widget(_disclosureTriangle);

        private static final byte _dividerGrabber = _disclosureTriangle + 1;
        public static final Widget DIVIDER_GRABBER = new Widget(_dividerGrabber);
        private static final byte _dividerSeparatorBar = _dividerGrabber + 1;
        public static final Widget DIVIDER_SEPARATOR_BAR = new Widget(_dividerSeparatorBar);
        private static final byte _dividerSplitter = _dividerSeparatorBar + 1;
        public static final Widget DIVIDER_SPLITTER = new Widget(_dividerSplitter);

        private static final byte _focus = _dividerSplitter + 1;
        public static final Widget FOCUS = new Widget(_focus);

        private static final byte _frameGroupBox = _focus + 1;
        public static final Widget FRAME_GROUP_BOX = new Widget(_frameGroupBox);
        private static final byte _frameGroupBoxSecondary = _frameGroupBox + 1;
        public static final Widget FRAME_GROUP_BOX_SECONDARY = new Widget(_frameGroupBoxSecondary);

        private static final byte _frameListBox = _frameGroupBoxSecondary + 1;
        public static final Widget FRAME_LIST_BOX = new Widget(_frameListBox);

        private static final byte _framePlacard = _frameListBox + 1;
        public static final Widget FRAME_PLACARD = new Widget(_framePlacard);

        private static final byte _frameTextField = _framePlacard + 1;
        public static final Widget FRAME_TEXT_FIELD = new Widget(_frameTextField);
        private static final byte _frameTextFieldRound = _frameTextField + 1;
        public static final Widget FRAME_TEXT_FIELD_ROUND = new Widget(_frameTextFieldRound);

        private static final byte _frameWell = _frameTextFieldRound + 1;
        public static final Widget FRAME_WELL = new Widget(_frameWell);

        private static final byte _growBox = _frameWell + 1;
        public static final Widget GROW_BOX = new Widget(_growBox);
        private static final byte _growBoxTextured = _growBox + 1;
        public static final Widget GROW_BOX_TEXTURED = new Widget(_growBoxTextured);

        private static final byte _gradient = _growBoxTextured + 1;
        public static final Widget GRADIENT = new Widget(_gradient);

        private static final byte _menu = _gradient + 1;
        public static final Widget MENU = new Widget(_menu);
        private static final byte _menuItem = _menu + 1;
        public static final Widget MENU_ITEM = new Widget(_menuItem);
        private static final byte _menuBar = _menuItem + 1;
        public static final Widget MENU_BAR = new Widget(_menuBar);
        private static final byte _menuTitle = _menuBar + 1;
        public static final Widget MENU_TITLE = new Widget(_menuTitle);

        private static final byte _progressBar = _menuTitle + 1;
        public static final Widget PROGRESS_BAR = new Widget(_progressBar);
        private static final byte _progressIndeterminateBar = _progressBar + 1;
        public static final Widget PROGRESS_INDETERMINATE_BAR = new Widget(_progressIndeterminateBar);
        private static final byte _progressRelevance = _progressIndeterminateBar + 1;
        public static final Widget PROGRESS_RELEVANCE = new Widget(_progressRelevance);
        private static final byte _progressSpinner = _progressRelevance + 1;
        public static final Widget PROGRESS_SPINNER = new Widget(_progressSpinner);

        private static final byte _scrollBar = _progressSpinner + 1;
        public static final Widget SCROLL_BAR = new Widget(_scrollBar);

        private static final byte _scrollColumnSizer = _scrollBar + 1;
        public static final Widget SCROLL_COLUMN_SIZER = new Widget(_scrollColumnSizer);

        private static final byte _slider = _scrollColumnSizer + 1;
        public static final Widget SLIDER = new Widget(_slider);
        private static final byte _sliderThumb = _slider + 1;
        public static final Widget SLIDER_THUMB = new Widget(_sliderThumb);

        private static final byte _synchronization = _sliderThumb + 1;
        public static final Widget SYNCHRONIZATION = new Widget(_synchronization);

        private static final byte _tab = _synchronization + 1;
        public static final Widget TAB = new Widget(_tab);

        private static final byte _titleBarCloseBox = _tab + 1;
        public static final Widget TITLE_BAR_CLOSE_BOX = new Widget(_titleBarCloseBox);
        private static final byte _titleBarCollapseBox = _titleBarCloseBox + 1;
        public static final Widget TITLE_BAR_COLLAPSE_BOX = new Widget(_titleBarCollapseBox);
        private static final byte _titleBarZoomBox = _titleBarCollapseBox + 1;
        public static final Widget TITLE_BAR_ZOOM_BOX = new Widget(_titleBarZoomBox);

        private static final byte _titleBarToolbarButton = _titleBarZoomBox + 1;
        public static final Widget TITLE_BAR_TOOLBAR_BUTTON = new Widget(_titleBarToolbarButton);

        private static final byte _toolbarItemWell = _titleBarToolbarButton + 1;
        public static final Widget TOOLBAR_ITEM_WELL = new Widget(_toolbarItemWell);

        private static final byte _windowFrame = _toolbarItemWell + 1;
        public static final Widget WINDOW_FRAME = new Widget(_windowFrame);
    }

    public static class Hit {
        private static final int _unknown = -1;
        public static final Hit UNKNOWN = new Hit(_unknown);
        private static final int _none = 0;
        public static final Hit NONE = new Hit(_none);
        private static final int _hit = 1;
        public static final Hit HIT = new Hit(_hit);

        final int hit;
        Hit(final int hit) { this.hit = hit; }

        public boolean isHit() {
            return hit > 0;
        }

        public String toString() {
            return getConstantName(this);
        }
    }

    public static class ScrollBarHit extends Hit {
        private static final int _thumb = 2;
        public static final ScrollBarHit THUMB = new ScrollBarHit(_thumb);

        private static final int _trackMin = 3;
        public static final ScrollBarHit TRACK_MIN = new ScrollBarHit(_trackMin);
        private static final int _trackMax = 4;
        public static final ScrollBarHit TRACK_MAX = new ScrollBarHit(_trackMax);

        private static final int _arrowMin = 5;
        public static final ScrollBarHit ARROW_MIN = new ScrollBarHit(_arrowMin);
        private static final int _arrowMax = 6;
        public static final ScrollBarHit ARROW_MAX = new ScrollBarHit(_arrowMax);
        private static final int _arrowMaxInside = 7;
        public static final ScrollBarHit ARROW_MAX_INSIDE = new ScrollBarHit(_arrowMaxInside);
        private static final int _arrowMinInside = 8;
        public static final ScrollBarHit ARROW_MIN_INSIDE = new ScrollBarHit(_arrowMinInside);

        ScrollBarHit(final int hit) { super(hit); }
    }

    static Hit getHit(final int hit) {
        switch (hit) {
            case Hit._none:
                return Hit.NONE;
            case Hit._hit:
                return Hit.HIT;

            case ScrollBarHit._thumb:
                return ScrollBarHit.THUMB;
            case ScrollBarHit._trackMin:
                return ScrollBarHit.TRACK_MIN;
            case ScrollBarHit._trackMax:
                return ScrollBarHit.TRACK_MAX;
            case ScrollBarHit._arrowMin:
                return ScrollBarHit.ARROW_MIN;
            case ScrollBarHit._arrowMax:
                return ScrollBarHit.ARROW_MAX;
            case ScrollBarHit._arrowMaxInside:
                return ScrollBarHit.ARROW_MAX_INSIDE;
            case ScrollBarHit._arrowMinInside:
                return ScrollBarHit.ARROW_MIN_INSIDE;
        }
        return Hit.UNKNOWN;
    }

    static String getConstantName(final Object object) {
        final Class<? extends Object> clazz = object.getClass();
        try {
            for (final Field field : clazz.getFields()) {
                if (field.get(null) == object) {
                    return field.getName();
                }
            }
        } catch (final Exception e) {}
        return clazz.getSimpleName();
    }
}
