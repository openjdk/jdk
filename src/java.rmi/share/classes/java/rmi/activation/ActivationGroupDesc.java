/*
 * Copyright (c) 1997, 2020, Oracle and/or its affiliates. All rights reserved.
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

package java.rmi.activation;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.rmi.MarshalledObject;
import java.util.Arrays;
import java.util.Properties;

/**
 * An activation group descriptor contains the information necessary to
 * create/recreate an activation group in which to activate objects.
 * Such a descriptor contains: <ul>
 * <li> the group's class name,
 * <li> the group's code location (the location of the group's class), and
 * <li> a "marshalled" object that can contain group specific
 * initialization data. </ul> <p>
 *
 * The group's class must be a concrete subclass of
 * <code>ActivationGroup</code>. A subclass of
 * <code>ActivationGroup</code> is created/recreated via the
 * <code>ActivationGroup.createGroup</code> static method that invokes
 * a special constructor that takes two arguments: <ul>
 *
 * <li> the group's <code>ActivationGroupID</code>, and
 * <li> the group's initialization data (in a
 * <code>java.rmi.MarshalledObject</code>)</ul>
 *
 * @author      Ann Wollrath
 * @since       1.2
 * @see         ActivationGroup
 * @see         ActivationGroupID
 * @deprecated
 * See the <a href="{@docRoot}/java.rmi/java/rmi/activation/package-summary.html">
 * {@code java.rmi.activation}</a> package specification for further information.
 */
@Deprecated(forRemoval=true, since="15")
public final class ActivationGroupDesc implements Serializable {

    /**
     * @serial The group's fully package qualified class name.
     */
    private String className;

    /**
     * @serial The location from where to load the group's class.
     */
    private String location;

    /**
     * @serial The group's initialization data.
     */
    private MarshalledObject<?> data;

    /**
     * @serial The controlling options for executing the VM in
     * another process.
     */
    private CommandEnvironment env;

    /**
     * @serial A properties map which will override those set
     * by default in the subprocess environment.
     */
    private Properties props;

    /** indicate compatibility with the Java 2 SDK v1.2 version of class */
    private static final long serialVersionUID = -4936225423168276595L;

    /**
     * Constructs a group descriptor that uses the system defaults for group
     * implementation and code location.  Properties specify Java
     * environment overrides (which will override system properties in
     * the group implementation's VM).  The command
     * environment can control the exact command/options used in
     * starting the child VM, or can be <code>null</code> to accept
     * rmid's default.
     *
     * <p>This constructor will create an <code>ActivationGroupDesc</code>
     * with a <code>null</code> group class name, which indicates the system's
     * default <code>ActivationGroup</code> implementation.
     *
     * @param overrides the set of properties to set when the group is
     * recreated.
     * @param cmd the controlling options for executing the VM in
     * another process (or <code>null</code>).
     * @since 1.2
     */
    public ActivationGroupDesc(Properties overrides,
                               CommandEnvironment cmd)
    {
        this(null, null, null, overrides, cmd);
    }

    /**
     * Specifies an alternate group implementation and execution
     * environment to be used for the group.
     *
     * @param className the group's package qualified class name or
     * <code>null</code>. A <code>null</code> group class name indicates
     * the system's default <code>ActivationGroup</code> implementation.
     * @param location the location from where to load the group's
     * class
     * @param data the group's initialization data contained in
     * marshalled form (could contain properties, for example)
     * @param overrides a properties map which will override those set
     * by default in the subprocess environment (will be translated
     * into <code>-D</code> options), or <code>null</code>.
     * @param cmd the controlling options for executing the VM in
     * another process (or <code>null</code>).
     * @since 1.2
     */
    public ActivationGroupDesc(String className,
                               String location,
                               MarshalledObject<?> data,
                               Properties overrides,
                               CommandEnvironment cmd)
    {
        this.props = overrides;
        this.env = cmd;
        this.data = data;
        this.location = location;
        this.className = className;
    }

    /**
     * Returns the group's class name (possibly <code>null</code>).  A
     * <code>null</code> group class name indicates the system's default
     * <code>ActivationGroup</code> implementation.
     * @return the group's class name
     * @since 1.2
     */
    public String getClassName() {
        return className;
    }

    /**
     * Returns the group's code location.
     * @return the group's code location
     * @since 1.2
     */
    public String getLocation() {
        return location;
    }

    /**
     * Returns the group's initialization data.
     * @return the group's initialization data
     * @since 1.2
     */
    public MarshalledObject<?> getData() {
        return data;
    }

    /**
     * Returns the group's property-override list.
     * @return the property-override list, or <code>null</code>
     * @since 1.2
     */
    public Properties getPropertyOverrides() {
        return (props != null) ? (Properties) props.clone() : null;
    }

    /**
     * Returns the group's command-environment control object.
     * @return the command-environment object, or <code>null</code>
     * @since 1.2
     */
    public CommandEnvironment getCommandEnvironment() {
        return this.env;
    }


    /**
     * Startup options for ActivationGroup implementations.
     *
     * This class allows overriding default system properties and
     * specifying implementation-defined options for ActivationGroups.
     * @since 1.2
     */
    public static class CommandEnvironment implements Serializable {
        private static final long serialVersionUID = 6165754737887770191L;

        /**
         * @serial
         */
        private String command;

        /**
         * @serial
         */
        private String[] options;

        /**
         * Create a CommandEnvironment with all the necessary
         * information.
         *
         * @param cmdpath the name of the java executable, including
         * the full path, or <code>null</code>, meaning "use rmid's default".
         * The named program <em>must</em> be able to accept multiple
         * <code>-Dpropname=value</code> options (as documented for the
         * "java" tool)
         *
         * @param argv extra options which will be used in creating the
         * ActivationGroup.  Null has the same effect as an empty
         * list.
         * @since 1.2
         */
        public CommandEnvironment(String cmdpath,
                                  String[] argv)
        {
            this.command = cmdpath;     // might be null

            // Hold a safe copy of argv in this.options
            if (argv == null) {
                this.options = new String[0];
            } else {
                this.options = new String[argv.length];
                System.arraycopy(argv, 0, this.options, 0, argv.length);
            }
        }

        /**
         * Fetch the configured path-qualified java command name.
         *
         * @return the configured name, or <code>null</code> if configured to
         * accept the default
         * @since 1.2
         */
        public String getCommandPath() {
            return (this.command);
        }

        /**
         * Fetch the configured java command options.
         *
         * @return An array of the command options which will be passed
         * to the new child command by rmid.
         * Note that rmid may add other options before or after these
         * options, or both.
         * Never returns <code>null</code>.
         * @since 1.2
         */
        public String[] getCommandOptions() {
            return options.clone();
        }

        /**
         * Compares two command environments for content equality.
         *
         * @param       obj     the Object to compare with
         * @return      true if these Objects are equal; false otherwise.
         * @see         java.util.Hashtable
         * @since 1.2
         */
        public boolean equals(Object obj) {

            if (obj instanceof CommandEnvironment) {
                CommandEnvironment env = (CommandEnvironment) obj;
                return
                    ((command == null ? env.command == null :
                      command.equals(env.command)) &&
                     Arrays.equals(options, env.options));
            } else {
                return false;
            }
        }

        /**
         * Return identical values for similar
         * <code>CommandEnvironment</code>s.
         * @return an integer
         * @see java.util.Hashtable
         */
        public int hashCode()
        {
            // hash command and ignore possibly expensive options
            return (command == null ? 0 : command.hashCode());
        }

        /**
         * <code>readObject</code> for custom serialization.
         *
         * <p>This method reads this object's serialized form for this
         * class as follows:
         *
         * <p>This method first invokes <code>defaultReadObject</code> on
         * the specified object input stream, and if <code>options</code>
         * is <code>null</code>, then <code>options</code> is set to a
         * zero-length array of <code>String</code>.
         */
        private void readObject(ObjectInputStream in)
            throws IOException, ClassNotFoundException
        {
            in.defaultReadObject();
            if (options == null) {
                options = new String[0];
            }
        }
    }

    /**
     * Compares two activation group descriptors for content equality.
     *
     * @param   obj     the Object to compare with
     * @return  true if these Objects are equal; false otherwise.
     * @see             java.util.Hashtable
     * @since 1.2
     */
    public boolean equals(Object obj) {

        if (obj instanceof ActivationGroupDesc) {
            ActivationGroupDesc desc = (ActivationGroupDesc) obj;
            return
                ((className == null ? desc.className == null :
                  className.equals(desc.className)) &&
                 (location == null ? desc.location == null :
                  location.equals(desc.location)) &&
                 (data == null ? desc.data == null : data.equals(desc.data)) &&
                 (env == null ? desc.env == null : env.equals(desc.env)) &&
                 (props == null ? desc.props == null :
                  props.equals(desc.props)));
        } else {
            return false;
        }
    }

    /**
     * Produce identical numbers for similar <code>ActivationGroupDesc</code>s.
     * @return an integer
     * @see java.util.Hashtable
     */
    public int hashCode() {
        // hash location, className, data, and env
        // but omit props (may be expensive)
        return ((location == null
                    ? 0
                    : location.hashCode() << 24) ^
                (env == null
                    ? 0
                    : env.hashCode() << 16) ^
                (className == null
                    ? 0
                    : className.hashCode() << 8) ^
                (data == null
                    ? 0
                    : data.hashCode()));
    }
}
