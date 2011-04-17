/*
 * Copyright (c) 2000, 2010, Oracle and/or its affiliates. All rights reserved.
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

package java.util.logging;
import java.util.ResourceBundle;

/**
 * The Level class defines a set of standard logging levels that
 * can be used to control logging output.  The logging Level objects
 * are ordered and are specified by ordered integers.  Enabling logging
 * at a given level also enables logging at all higher levels.
 * <p>
 * Clients should normally use the predefined Level constants such
 * as Level.SEVERE.
 * <p>
 * The levels in descending order are:
 * <ul>
 * <li>SEVERE (highest value)
 * <li>WARNING
 * <li>INFO
 * <li>CONFIG
 * <li>FINE
 * <li>FINER
 * <li>FINEST  (lowest value)
 * </ul>
 * In addition there is a level OFF that can be used to turn
 * off logging, and a level ALL that can be used to enable
 * logging of all messages.
 * <p>
 * It is possible for third parties to define additional logging
 * levels by subclassing Level.  In such cases subclasses should
 * take care to chose unique integer level values and to ensure that
 * they maintain the Object uniqueness property across serialization
 * by defining a suitable readResolve method.
 *
 * @since 1.4
 */

public class Level implements java.io.Serializable {
    private static java.util.ArrayList<Level> known = new java.util.ArrayList<>();
    private static String defaultBundle = "sun.util.logging.resources.logging";

    /**
     * @serial  The non-localized name of the level.
     */
    private final String name;

    /**
     * @serial  The integer value of the level.
     */
    private final int value;

    /**
     * @serial The resource bundle name to be used in localizing the level name.
     */
    private final String resourceBundleName;

    /**
     * OFF is a special level that can be used to turn off logging.
     * This level is initialized to <CODE>Integer.MAX_VALUE</CODE>.
     */
    public static final Level OFF = new Level("OFF",Integer.MAX_VALUE, defaultBundle);

    /**
     * SEVERE is a message level indicating a serious failure.
     * <p>
     * In general SEVERE messages should describe events that are
     * of considerable importance and which will prevent normal
     * program execution.   They should be reasonably intelligible
     * to end users and to system administrators.
     * This level is initialized to <CODE>1000</CODE>.
     */
    public static final Level SEVERE = new Level("SEVERE",1000, defaultBundle);

    /**
     * WARNING is a message level indicating a potential problem.
     * <p>
     * In general WARNING messages should describe events that will
     * be of interest to end users or system managers, or which
     * indicate potential problems.
     * This level is initialized to <CODE>900</CODE>.
     */
    public static final Level WARNING = new Level("WARNING", 900, defaultBundle);

    /**
     * INFO is a message level for informational messages.
     * <p>
     * Typically INFO messages will be written to the console
     * or its equivalent.  So the INFO level should only be
     * used for reasonably significant messages that will
     * make sense to end users and system administrators.
     * This level is initialized to <CODE>800</CODE>.
     */
    public static final Level INFO = new Level("INFO", 800, defaultBundle);

    /**
     * CONFIG is a message level for static configuration messages.
     * <p>
     * CONFIG messages are intended to provide a variety of static
     * configuration information, to assist in debugging problems
     * that may be associated with particular configurations.
     * For example, CONFIG message might include the CPU type,
     * the graphics depth, the GUI look-and-feel, etc.
     * This level is initialized to <CODE>700</CODE>.
     */
    public static final Level CONFIG = new Level("CONFIG", 700, defaultBundle);

    /**
     * FINE is a message level providing tracing information.
     * <p>
     * All of FINE, FINER, and FINEST are intended for relatively
     * detailed tracing.  The exact meaning of the three levels will
     * vary between subsystems, but in general, FINEST should be used
     * for the most voluminous detailed output, FINER for somewhat
     * less detailed output, and FINE for the  lowest volume (and
     * most important) messages.
     * <p>
     * In general the FINE level should be used for information
     * that will be broadly interesting to developers who do not have
     * a specialized interest in the specific subsystem.
     * <p>
     * FINE messages might include things like minor (recoverable)
     * failures.  Issues indicating potential performance problems
     * are also worth logging as FINE.
     * This level is initialized to <CODE>500</CODE>.
     */
    public static final Level FINE = new Level("FINE", 500, defaultBundle);

    /**
     * FINER indicates a fairly detailed tracing message.
     * By default logging calls for entering, returning, or throwing
     * an exception are traced at this level.
     * This level is initialized to <CODE>400</CODE>.
     */
    public static final Level FINER = new Level("FINER", 400, defaultBundle);

    /**
     * FINEST indicates a highly detailed tracing message.
     * This level is initialized to <CODE>300</CODE>.
     */
    public static final Level FINEST = new Level("FINEST", 300, defaultBundle);

    /**
     * ALL indicates that all messages should be logged.
     * This level is initialized to <CODE>Integer.MIN_VALUE</CODE>.
     */
    public static final Level ALL = new Level("ALL", Integer.MIN_VALUE, defaultBundle);

    /**
     * Create a named Level with a given integer value.
     * <p>
     * Note that this constructor is "protected" to allow subclassing.
     * In general clients of logging should use one of the constant Level
     * objects such as SEVERE or FINEST.  However, if clients need to
     * add new logging levels, they may subclass Level and define new
     * constants.
     * @param name  the name of the Level, for example "SEVERE".
     * @param value an integer value for the level.
     * @throws NullPointerException if the name is null
     */
    protected Level(String name, int value) {
        this(name, value, null);
    }

    /**
     * Create a named Level with a given integer value and a
     * given localization resource name.
     * <p>
     * @param name  the name of the Level, for example "SEVERE".
     * @param value an integer value for the level.
     * @param resourceBundleName name of a resource bundle to use in
     *    localizing the given name. If the resourceBundleName is null
     *    or an empty string, it is ignored.
     * @throws NullPointerException if the name is null
     */
    protected Level(String name, int value, String resourceBundleName) {
        if (name == null) {
            throw new NullPointerException();
        }
        this.name = name;
        this.value = value;
        this.resourceBundleName = resourceBundleName;
        synchronized (Level.class) {
            known.add(this);
        }
    }

    /**
     * Return the level's localization resource bundle name, or
     * null if no localization bundle is defined.
     *
     * @return localization resource bundle name
     */
    public String getResourceBundleName() {
        return resourceBundleName;
    }

    /**
     * Return the non-localized string name of the Level.
     *
     * @return non-localized name
     */
    public String getName() {
        return name;
    }

    /**
     * Return the localized string name of the Level, for
     * the current default locale.
     * <p>
     * If no localization information is available, the
     * non-localized name is returned.
     *
     * @return localized name
     */
    public String getLocalizedName() {
        try {
            ResourceBundle rb = ResourceBundle.getBundle(resourceBundleName);
            return rb.getString(name);
        } catch (Exception ex) {
            return name;
        }
    }

    /**
     * Returns a string representation of this Level.
     *
     * @return the non-localized name of the Level, for example "INFO".
     */
    public final String toString() {
        return name;
    }

    /**
     * Get the integer value for this level.  This integer value
     * can be used for efficient ordering comparisons between
     * Level objects.
     * @return the integer value for this level.
     */
    public final int intValue() {
        return value;
    }

    private static final long serialVersionUID = -8176160795706313070L;

    // Serialization magic to prevent "doppelgangers".
    // This is a performance optimization.
    private Object readResolve() {
        synchronized (Level.class) {
            for (int i = 0; i < known.size(); i++) {
                Level other = known.get(i);
                if (this.name.equals(other.name) && this.value == other.value
                        && (this.resourceBundleName == other.resourceBundleName ||
                            (this.resourceBundleName != null &&
                            this.resourceBundleName.equals(other.resourceBundleName)))) {
                    return other;
                }
            }
            // Woops.  Whoever sent us this object knows
            // about a new log level.  Add it to our list.
            known.add(this);
            return this;
        }
    }

    /**
     * Parse a level name string into a Level.
     * <p>
     * The argument string may consist of either a level name
     * or an integer value.
     * <p>
     * For example:
     * <ul>
     * <li>     "SEVERE"
     * <li>     "1000"
     * </ul>
     * @param  name   string to be parsed
     * @throws NullPointerException if the name is null
     * @throws IllegalArgumentException if the value is not valid.
     * Valid values are integers between <CODE>Integer.MIN_VALUE</CODE>
     * and <CODE>Integer.MAX_VALUE</CODE>, and all known level names.
     * Known names are the levels defined by this class (e.g., <CODE>FINE</CODE>,
     * <CODE>FINER</CODE>, <CODE>FINEST</CODE>), or created by this class with
     * appropriate package access, or new levels defined or created
     * by subclasses.
     *
     * @return The parsed value. Passing an integer that corresponds to a known name
     * (e.g., 700) will return the associated name (e.g., <CODE>CONFIG</CODE>).
     * Passing an integer that does not (e.g., 1) will return a new level name
     * initialized to that value.
     */
    public static synchronized Level parse(String name) throws IllegalArgumentException {
        // Check that name is not null.
        name.length();

        // Look for a known Level with the given non-localized name.
        for (int i = 0; i < known.size(); i++) {
            Level l = known.get(i);
            if (name.equals(l.name)) {
                return l;
            }
        }

        // Now, check if the given name is an integer.  If so,
        // first look for a Level with the given value and then
        // if necessary create one.
        try {
            int x = Integer.parseInt(name);
            for (int i = 0; i < known.size(); i++) {
                Level l = known.get(i);
                if (l.value == x) {
                    return l;
                }
            }
            // Create a new Level.
            return new Level(name, x);
        } catch (NumberFormatException ex) {
            // Not an integer.
            // Drop through.
        }

        // Finally, look for a known level with the given localized name,
        // in the current default locale.
        // This is relatively expensive, but not excessively so.
        for (int i = 0; i < known.size(); i++) {
            Level l =  known.get(i);
            if (name.equals(l.getLocalizedName())) {
                return l;
            }
        }

        // OK, we've tried everything and failed
        throw new IllegalArgumentException("Bad level \"" + name + "\"");
    }

    /**
     * Compare two objects for value equality.
     * @return true if and only if the two objects have the same level value.
     */
    public boolean equals(Object ox) {
        try {
            Level lx = (Level)ox;
            return (lx.value == this.value);
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * Generate a hashcode.
     * @return a hashcode based on the level value
     */
    public int hashCode() {
        return this.value;
    }
}
