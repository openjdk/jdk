/*
 * Copyright (c) 2002-2020, the original author or authors.
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package jdk.internal.org.jline.utils;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jdk.internal.org.jline.terminal.Terminal;

/**
 * Color palette
 */
public class ColorPalette {

    public static final String XTERM_INITC = "\\E]4;%p1%d;rgb\\:%p2%{255}%*%{1000}%/%2.2X/%p3%{255}%*%{1000}%/%2.2X/%p4%{255}%*%{1000}%/%2.2X\\E\\\\";

    public static final ColorPalette DEFAULT = new ColorPalette();

    private final Terminal terminal;
    private String distanceName;
    private Colors.Distance distance;
    private boolean osc4;
    private int[] palette;

    public ColorPalette() {
        this.terminal = null;
        this.distanceName = null;
        this.palette = Colors.DEFAULT_COLORS_256;
    }

    public ColorPalette(Terminal terminal) throws IOException {
        this(terminal, null);
    }

    public ColorPalette(Terminal terminal, String distance) throws IOException {
        this.terminal = terminal;
        this.distanceName = distance;
        loadPalette(false);
    }

    /**
     * Get the name of the distance to use for rounding colors.
     * @return the name of the color distance
     */
    public String getDistanceName() {
        return distanceName;
    }

    /**
     * Set the name of the color distance to use when rounding RGB colors to the palette.
     * @param name the name of the color distance
     */
    public void setDistance(String name) {
        this.distanceName = name;
    }

    /**
     * Check if the terminal has the capability to change colors.
     * @return <code>true</code> if the terminal can change colors
     */
    public boolean canChange() {
        return terminal != null && terminal.getBooleanCapability(InfoCmp.Capability.can_change);
    }

    /**
     * Load the palette from the terminal.
     * If the palette has already been loaded, subsequent calls will simply return <code>true</code>.
     *
     * @return <code>true</code> if the palette has been successfully loaded.
     * @throws IOException
     */
    public boolean loadPalette() throws IOException {
        if (!osc4) {
            loadPalette(true);
        }
        return osc4;
    }

    protected void loadPalette(boolean doLoad) throws IOException {
        if (terminal != null) {
            int[] pal = doLoad ? doLoad(terminal) : null;
            if (pal != null) {
                this.palette = pal;
                this.osc4 = true;
            } else {
                Integer cols = terminal.getNumericCapability(InfoCmp.Capability.max_colors);
                if (cols != null) {
                    if (cols == Colors.DEFAULT_COLORS_88.length) {
                        this.palette = Colors.DEFAULT_COLORS_88;
                    } else {
                        this.palette = Arrays.copyOf(Colors.DEFAULT_COLORS_256, Math.min(cols, 256));
                    }
                } else {
                    this.palette = Arrays.copyOf(Colors.DEFAULT_COLORS_256, 256);
                }
                this.osc4 = false;
            }
        } else {
            this.palette = Colors.DEFAULT_COLORS_256;
            this.osc4 = false;
        }
    }

    /**
     * Get the palette length
     * @return the palette length
     */
    public int getLength() {
        return this.palette.length;
    }

    /**
     * Get a specific color in the palette
     * @param index the index of the color
     * @return the color at the given index
     */
    public int getColor(int index) {
        return palette[index];
    }

    /**
     * Change the color of the palette
     * @param index the index of the color
     * @param color the new color value
     */
    public void setColor(int index, int color) {
        palette[index] = color;
        if (canChange()) {
            String initc = terminal.getStringCapability(InfoCmp.Capability.initialize_color);
            if (initc != null || osc4) {
                // initc expects color in 0..1000 range
                int r = (((color >> 16) & 0xFF) * 1000) / 255 + 1;
                int g = (((color >> 8) & 0xFF) * 1000) / 255 + 1;
                int b = ((color & 0xFF) * 1000) / 255 + 1;
                if (initc == null) {
                    // This is the xterm version
                    initc = XTERM_INITC;
                }
                Curses.tputs(terminal.writer(), initc, index, r, g, b);
                terminal.writer().flush();
            }
        }
    }

    public boolean isReal() {
        return osc4;
    }

    public int round(int r, int g, int b) {
        return Colors.roundColor((r << 16) + (g << 8) + b, palette, palette.length, getDist());
    }

    public int round(int col) {
        if (col >= palette.length) {
            col = Colors.roundColor(DEFAULT.getColor(col), palette, palette.length, getDist());
        }
        return col;
    }

    protected Colors.Distance getDist() {
        if (distance == null) {
            distance = Colors.getDistance(distanceName);
        }
        return distance;
    }

    private static int[] doLoad(Terminal terminal) throws IOException {
        PrintWriter writer = terminal.writer();
        NonBlockingReader reader = terminal.reader();

        int[] palette = new int[256];
        for (int i = 0; i < 16; i++) {
            StringBuilder req = new StringBuilder(1024);
            req.append("\033]4");
            for (int j = 0; j < 16; j++) {
                req.append(';').append(i * 16 + j).append(";?");
            }
            req.append("\033\\");
            writer.write(req.toString());
            writer.flush();

            boolean black = true;
            for (int j = 0; j < 16; j++) {
                if (reader.peek(50) < 0) {
                    break;
                }
                if (reader.read(10) != '\033'
                        || reader.read(10) != ']'
                        || reader.read(10) != '4'
                        || reader.read(10) != ';') {
                    return null;
                }
                int idx = 0;
                int c;
                while (true) {
                    c = reader.read(10);
                    if (c >= '0' && c <= '9') {
                        idx = idx * 10 + (c - '0');
                    } else if (c == ';') {
                        break;
                    } else {
                        return null;
                    }
                }
                if (idx > 255) {
                    return null;
                }
                if (reader.read(10) != 'r'
                        || reader.read(10) != 'g'
                        || reader.read(10) != 'b'
                        || reader.read(10) != ':') {
                    return null;
                }
                StringBuilder sb = new StringBuilder(16);
                List<String> rgb = new ArrayList<>();
                while (true) {
                    c = reader.read(10);
                    if (c == '\007') {
                        rgb.add(sb.toString());
                        break;
                    } else if (c == '\033') {
                        c = reader.read(10);
                        if (c == '\\') {
                            rgb.add(sb.toString());
                            break;
                        } else {
                            return null;
                        }
                    } else if (c >= '0' && c <= '9' || c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z') {
                        sb.append((char) c);
                    } else if (c == '/') {
                        rgb.add(sb.toString());
                        sb.setLength(0);
                    }
                }
                if (rgb.size() != 3) {
                    return null;
                }
                double r = Integer.parseInt(rgb.get(0), 16) / ((1 << (4 * rgb.get(0).length())) - 1.0);
                double g = Integer.parseInt(rgb.get(1), 16) / ((1 << (4 * rgb.get(1).length())) - 1.0);
                double b = Integer.parseInt(rgb.get(2), 16) / ((1 << (4 * rgb.get(2).length())) - 1.0);
                palette[idx] = (int)((Math.round(r * 255) << 16) + (Math.round(g * 255) << 8) + Math.round(b * 255));
                black &= palette[idx] == 0;
            }
            if (black) {
                break;
            }
        }
        int max = 256;
        while (max > 0 && palette[--max] == 0);
        return Arrays.copyOfRange(palette, 0, max + 1);
    }
}
