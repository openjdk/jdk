/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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


package java.awt;

import java.awt.image.BufferedImage;
import java.security.AccessController;
import java.util.Locale;

import sun.font.FontManager;
import sun.font.FontManagerFactory;
import sun.java2d.HeadlessGraphicsEnvironment;
import sun.java2d.SunGraphicsEnvironment;
import sun.security.action.GetPropertyAction;

/**
 *
 * The <code>GraphicsEnvironment</code> class describes the collection
 * of {@link GraphicsDevice} objects and {@link java.awt.Font} objects
 * available to a Java(tm) application on a particular platform.
 * The resources in this <code>GraphicsEnvironment</code> might be local
 * or on a remote machine.  <code>GraphicsDevice</code> objects can be
 * screens, printers or image buffers and are the destination of
 * {@link Graphics2D} drawing methods.  Each <code>GraphicsDevice</code>
 * has a number of {@link GraphicsConfiguration} objects associated with
 * it.  These objects specify the different configurations in which the
 * <code>GraphicsDevice</code> can be used.
 * @see GraphicsDevice
 * @see GraphicsConfiguration
 */

public abstract class GraphicsEnvironment {
    private static GraphicsEnvironment localEnv;

    /**
     * The headless state of the Toolkit and GraphicsEnvironment
     */
    private static Boolean headless;

    /**
     * The headless state assumed by default
     */
    private static Boolean defaultHeadless;

    /**
     * This is an abstract class and cannot be instantiated directly.
     * Instances must be obtained from a suitable factory or query method.
     */
    protected GraphicsEnvironment() {
    }

    /**
     * Returns the local <code>GraphicsEnvironment</code>.
     * @return the local <code>GraphicsEnvironment</code>
     */
    public static synchronized GraphicsEnvironment getLocalGraphicsEnvironment() {
        if (localEnv == null) {
            localEnv = createGE();
        }

        return localEnv;
    }

    /**
     * Creates and returns the GraphicsEnvironment, according to the
     * system property 'java.awt.graphicsenv'.
     *
     * @return the graphics environment
     */
    private static GraphicsEnvironment createGE() {
        GraphicsEnvironment ge;
        String nm = AccessController.doPrivileged(new GetPropertyAction("java.awt.graphicsenv", null));
        try {
//          long t0 = System.currentTimeMillis();
            Class<GraphicsEnvironment> geCls;
            try {
                // First we try if the bootclassloader finds the requested
                // class. This way we can avoid to run in a privileged block.
                geCls = (Class<GraphicsEnvironment>)Class.forName(nm);
            } catch (ClassNotFoundException ex) {
                // If the bootclassloader fails, we try again with the
                // application classloader.
                ClassLoader cl = ClassLoader.getSystemClassLoader();
                geCls = (Class<GraphicsEnvironment>)Class.forName(nm, true, cl);
            }
            ge = geCls.newInstance();
//          long t1 = System.currentTimeMillis();
//          System.out.println("GE creation took " + (t1-t0)+ "ms.");
            if (isHeadless()) {
                ge = new HeadlessGraphicsEnvironment(ge);
            }
        } catch (ClassNotFoundException e) {
            throw new Error("Could not find class: "+nm);
        } catch (InstantiationException e) {
            throw new Error("Could not instantiate Graphics Environment: "
                            + nm);
        } catch (IllegalAccessException e) {
            throw new Error ("Could not access Graphics Environment: "
                             + nm);
        }
        return ge;
    }

    /**
     * Tests whether or not a display, keyboard, and mouse can be
     * supported in this environment.  If this method returns true,
     * a HeadlessException is thrown from areas of the Toolkit
     * and GraphicsEnvironment that are dependent on a display,
     * keyboard, or mouse.
     * @return <code>true</code> if this environment cannot support
     * a display, keyboard, and mouse; <code>false</code>
     * otherwise
     * @see java.awt.HeadlessException
     * @since 1.4
     */
    public static boolean isHeadless() {
        return getHeadlessProperty();
    }

    /**
     * @return warning message if headless state is assumed by default;
     * null otherwise
     * @since 1.5
     */
    static String getHeadlessMessage() {
        if (headless == null) {
            getHeadlessProperty(); // initialize the values
        }
        return defaultHeadless != Boolean.TRUE ? null :
            "\nNo X11 DISPLAY variable was set, " +
            "but this program performed an operation which requires it.";
    }

    /**
     * @return the value of the property "java.awt.headless"
     * @since 1.4
     */
    private static boolean getHeadlessProperty() {
        if (headless == null) {
            java.security.AccessController.doPrivileged(
            new java.security.PrivilegedAction<Object>() {
                public Object run() {
                    String nm = System.getProperty("java.awt.headless");

                    if (nm == null) {
                        /* No need to ask for DISPLAY when run in a browser */
                        if (System.getProperty("javaplugin.version") != null) {
                            headless = defaultHeadless = Boolean.FALSE;
                        } else {
                            String osName = System.getProperty("os.name");
                            if (osName.contains("OS X") && "sun.awt.HToolkit".equals(
                                    System.getProperty("awt.toolkit")))
                            {
                                headless = defaultHeadless = Boolean.TRUE;
                            } else {
                                headless = defaultHeadless =
                                    Boolean.valueOf(("Linux".equals(osName) ||
                                                     "SunOS".equals(osName) ||
                                                     "FreeBSD".equals(osName) ||
                                                     "NetBSD".equals(osName) ||
                                                     "OpenBSD".equals(osName)) &&
                                                     (System.getenv("DISPLAY") == null));
                            }
                        }
                    } else if (nm.equals("true")) {
                        headless = Boolean.TRUE;
                    } else {
                        headless = Boolean.FALSE;
                    }
                    return null;
                }
                }
            );
        }
        return headless.booleanValue();
    }

    /**
     * Check for headless state and throw HeadlessException if headless
     * @since 1.4
     */
    static void checkHeadless() throws HeadlessException {
        if (isHeadless()) {
            throw new HeadlessException();
        }
    }

    /**
     * Returns whether or not a display, keyboard, and mouse can be
     * supported in this graphics environment.  If this returns true,
     * <code>HeadlessException</code> will be thrown from areas of the
     * graphics environment that are dependent on a display, keyboard, or
     * mouse.
     * @return <code>true</code> if a display, keyboard, and mouse
     * can be supported in this environment; <code>false</code>
     * otherwise
     * @see java.awt.HeadlessException
     * @see #isHeadless
     * @since 1.4
     */
    public boolean isHeadlessInstance() {
        // By default (local graphics environment), simply check the
        // headless property.
        return getHeadlessProperty();
    }

    /**
     * Returns an array of all of the screen <code>GraphicsDevice</code>
     * objects.
     * @return an array containing all the <code>GraphicsDevice</code>
     * objects that represent screen devices
     * @exception HeadlessException if isHeadless() returns true
     * @see #isHeadless()
     */
    public abstract GraphicsDevice[] getScreenDevices()
        throws HeadlessException;

    /**
     * Returns the default screen <code>GraphicsDevice</code>.
     * @return the <code>GraphicsDevice</code> that represents the
     * default screen device
     * @exception HeadlessException if isHeadless() returns true
     * @see #isHeadless()
     */
    public abstract GraphicsDevice getDefaultScreenDevice()
        throws HeadlessException;

    /**
     * Returns a <code>Graphics2D</code> object for rendering into the
     * specified {@link BufferedImage}.
     * @param img the specified <code>BufferedImage</code>
     * @return a <code>Graphics2D</code> to be used for rendering into
     * the specified <code>BufferedImage</code>
     * @throws NullPointerException if <code>img</code> is null
     */
    public abstract Graphics2D createGraphics(BufferedImage img);

    /**
     * Returns an array containing a one-point size instance of all fonts
     * available in this <code>GraphicsEnvironment</code>.  Typical usage
     * would be to allow a user to select a particular font.  Then, the
     * application can size the font and set various font attributes by
     * calling the <code>deriveFont</code> method on the chosen instance.
     * <p>
     * This method provides for the application the most precise control
     * over which <code>Font</code> instance is used to render text.
     * If a font in this <code>GraphicsEnvironment</code> has multiple
     * programmable variations, only one
     * instance of that <code>Font</code> is returned in the array, and
     * other variations must be derived by the application.
     * <p>
     * If a font in this environment has multiple programmable variations,
     * such as Multiple-Master fonts, only one instance of that font is
     * returned in the <code>Font</code> array.  The other variations
     * must be derived by the application.
     *
     * @return an array of <code>Font</code> objects
     * @see #getAvailableFontFamilyNames
     * @see java.awt.Font
     * @see java.awt.Font#deriveFont
     * @see java.awt.Font#getFontName
     * @since 1.2
     */
    public abstract Font[] getAllFonts();

    /**
     * Returns an array containing the names of all font families in this
     * <code>GraphicsEnvironment</code> localized for the default locale,
     * as returned by <code>Locale.getDefault()</code>.
     * <p>
     * Typical usage would be for presentation to a user for selection of
     * a particular family name. An application can then specify this name
     * when creating a font, in conjunction with a style, such as bold or
     * italic, giving the font system flexibility in choosing its own best
     * match among multiple fonts in the same font family.
     *
     * @return an array of <code>String</code> containing font family names
     * localized for the default locale, or a suitable alternative
     * name if no name exists for this locale.
     * @see #getAllFonts
     * @see java.awt.Font
     * @see java.awt.Font#getFamily
     * @since 1.2
     */
    public abstract String[] getAvailableFontFamilyNames();

    /**
     * Returns an array containing the names of all font families in this
     * <code>GraphicsEnvironment</code> localized for the specified locale.
     * <p>
     * Typical usage would be for presentation to a user for selection of
     * a particular family name. An application can then specify this name
     * when creating a font, in conjunction with a style, such as bold or
     * italic, giving the font system flexibility in choosing its own best
     * match among multiple fonts in the same font family.
     *
     * @param l a {@link Locale} object that represents a
     * particular geographical, political, or cultural region.
     * Specifying <code>null</code> is equivalent to
     * specifying <code>Locale.getDefault()</code>.
     * @return an array of <code>String</code> containing font family names
     * localized for the specified <code>Locale</code>, or a
     * suitable alternative name if no name exists for the specified locale.
     * @see #getAllFonts
     * @see java.awt.Font
     * @see java.awt.Font#getFamily
     * @since 1.2
     */
    public abstract String[] getAvailableFontFamilyNames(Locale l);

    /**
     * Registers a <i>created</i> <code>Font</code>in this
     * <code>GraphicsEnvironment</code>.
     * A created font is one that was returned from calling
     * {@link Font#createFont}, or derived from a created font by
     * calling {@link Font#deriveFont}.
     * After calling this method for such a font, it is available to
     * be used in constructing new <code>Font</code>s by name or family name,
     * and is enumerated by {@link #getAvailableFontFamilyNames} and
     * {@link #getAllFonts} within the execution context of this
     * application or applet. This means applets cannot register fonts in
     * a way that they are visible to other applets.
     * <p>
     * Reasons that this method might not register the font and therefore
     * return <code>false</code> are:
     * <ul>
     * <li>The font is not a <i>created</i> <code>Font</code>.
     * <li>The font conflicts with a non-created <code>Font</code> already
     * in this <code>GraphicsEnvironment</code>. For example if the name
     * is that of a system font, or a logical font as described in the
     * documentation of the {@link Font} class. It is implementation dependent
     * whether a font may also conflict if it has the same family name
     * as a system font.
     * <p>Notice that an application can supersede the registration
     * of an earlier created font with a new one.
     * </ul>
     * @return true if the <code>font</code> is successfully
     * registered in this <code>GraphicsEnvironment</code>.
     * @throws NullPointerException if <code>font</code> is null
     * @since 1.6
     */
    public boolean registerFont(Font font) {
        if (font == null) {
            throw new NullPointerException("font cannot be null.");
        }
        FontManager fm = FontManagerFactory.getInstance();
        return fm.registerFont(font);
    }

    /**
     * Indicates a preference for locale-specific fonts in the mapping of
     * logical fonts to physical fonts. Calling this method indicates that font
     * rendering should primarily use fonts specific to the primary writing
     * system (the one indicated by the default encoding and the initial
     * default locale). For example, if the primary writing system is
     * Japanese, then characters should be rendered using a Japanese font
     * if possible, and other fonts should only be used for characters for
     * which the Japanese font doesn't have glyphs.
     * <p>
     * The actual change in font rendering behavior resulting from a call
     * to this method is implementation dependent; it may have no effect at
     * all, or the requested behavior may already match the default behavior.
     * The behavior may differ between font rendering in lightweight
     * and peered components.  Since calling this method requests a
     * different font, clients should expect different metrics, and may need
     * to recalculate window sizes and layout. Therefore this method should
     * be called before user interface initialisation.
     * @since 1.5
     */
    public void preferLocaleFonts() {
        FontManager fm = FontManagerFactory.getInstance();
        fm.preferLocaleFonts();
    }

    /**
     * Indicates a preference for proportional over non-proportional (e.g.
     * dual-spaced CJK fonts) fonts in the mapping of logical fonts to
     * physical fonts. If the default mapping contains fonts for which
     * proportional and non-proportional variants exist, then calling
     * this method indicates the mapping should use a proportional variant.
     * <p>
     * The actual change in font rendering behavior resulting from a call to
     * this method is implementation dependent; it may have no effect at all.
     * The behavior may differ between font rendering in lightweight and
     * peered components. Since calling this method requests a
     * different font, clients should expect different metrics, and may need
     * to recalculate window sizes and layout. Therefore this method should
     * be called before user interface initialisation.
     * @since 1.5
     */
    public void preferProportionalFonts() {
        FontManager fm = FontManagerFactory.getInstance();
        fm.preferProportionalFonts();
    }

    /**
     * Returns the Point where Windows should be centered.
     * It is recommended that centered Windows be checked to ensure they fit
     * within the available display area using getMaximumWindowBounds().
     * @return the point where Windows should be centered
     *
     * @exception HeadlessException if isHeadless() returns true
     * @see #getMaximumWindowBounds
     * @since 1.4
     */
    public Point getCenterPoint() throws HeadlessException {
    // Default implementation: return the center of the usable bounds of the
    // default screen device.
        Rectangle usableBounds =
         SunGraphicsEnvironment.getUsableBounds(getDefaultScreenDevice());
        return new Point((usableBounds.width / 2) + usableBounds.x,
                         (usableBounds.height / 2) + usableBounds.y);
    }

    /**
     * Returns the maximum bounds for centered Windows.
     * These bounds account for objects in the native windowing system such as
     * task bars and menu bars.  The returned bounds will reside on a single
     * display with one exception: on multi-screen systems where Windows should
     * be centered across all displays, this method returns the bounds of the
     * entire display area.
     * <p>
     * To get the usable bounds of a single display, use
     * <code>GraphicsConfiguration.getBounds()</code> and
     * <code>Toolkit.getScreenInsets()</code>.
     * @return  the maximum bounds for centered Windows
     *
     * @exception HeadlessException if isHeadless() returns true
     * @see #getCenterPoint
     * @see GraphicsConfiguration#getBounds
     * @see Toolkit#getScreenInsets
     * @since 1.4
     */
    public Rectangle getMaximumWindowBounds() throws HeadlessException {
    // Default implementation: return the usable bounds of the default screen
    // device.  This is correct for Microsoft Windows and non-Xinerama X11.
        return SunGraphicsEnvironment.getUsableBounds(getDefaultScreenDevice());
    }
}
