/*
 * Copyright (c) 1998, 2014, Oracle and/or its affiliates. All rights reserved.
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

package java.awt.image.renderable;
import java.awt.image.RenderedImage;
import java.io.Serializable;
import java.util.Vector;

/**
 * A <code>ParameterBlock</code> encapsulates all the information about sources and
 * parameters (Objects) required by a RenderableImageOp, or other
 * classes that process images.
 *
 * <p> Although it is possible to place arbitrary objects in the
 * source Vector, users of this class may impose semantic constraints
 * such as requiring all sources to be RenderedImages or
 * RenderableImage.  <code>ParameterBlock</code> itself is merely a container and
 * performs no checking on source or parameter types.
 *
 * <p> All parameters in a <code>ParameterBlock</code> are objects; convenience
 * add and set methods are available that take arguments of base type and
 * construct the appropriate subclass of Number (such as
 * Integer or Float).  Corresponding get methods perform a
 * downward cast and have return values of base type; an exception
 * will be thrown if the stored values do not have the correct type.
 * There is no way to distinguish between the results of
 * "short s; add(s)" and "add(new Short(s))".
 *
 * <p> Note that the get and set methods operate on references.
 * Therefore, one must be careful not to share references between
 * <code>ParameterBlock</code>s when this is inappropriate.  For example, to create
 * a new <code>ParameterBlock</code> that is equal to an old one except for an
 * added source, one might be tempted to write:
 *
 * <pre>
 * ParameterBlock addSource(ParameterBlock pb, RenderableImage im) {
 *     ParameterBlock pb1 = new ParameterBlock(pb.getSources());
 *     pb1.addSource(im);
 *     return pb1;
 * }
 * </pre>
 *
 * <p> This code will have the side effect of altering the original
 * <code>ParameterBlock</code>, since the getSources operation returned a reference
 * to its source Vector.  Both pb and pb1 share their source Vector,
 * and a change in either is visible to both.
 *
 * <p> A correct way to write the addSource function is to clone
 * the source Vector:
 *
 * <pre>
 * ParameterBlock addSource (ParameterBlock pb, RenderableImage im) {
 *     ParameterBlock pb1 = new ParameterBlock(pb.getSources().clone());
 *     pb1.addSource(im);
 *     return pb1;
 * }
 * </pre>
 *
 * <p> The clone method of <code>ParameterBlock</code> has been defined to
 * perform a clone of both the source and parameter Vectors for
 * this reason.  A standard, shallow clone is available as
 * shallowClone.
 *
 * <p> The addSource, setSource, add, and set methods are
 * defined to return 'this' after adding their argument.  This allows
 * use of syntax like:
 *
 * <pre>
 * ParameterBlock pb = new ParameterBlock();
 * op = new RenderableImageOp("operation", pb.add(arg1).add(arg2));
 * </pre>
 * */
public class ParameterBlock implements Cloneable, Serializable {
    private static final long serialVersionUID = -7577115551785240750L;

    /** A Vector of sources, stored as arbitrary Objects. */
    protected Vector<Object> sources = new Vector<Object>();

    /** A Vector of non-source parameters, stored as arbitrary Objects. */
    protected Vector<Object> parameters = new Vector<Object>();

    /** A dummy constructor. */
    public ParameterBlock() {}

    /**
     * Constructs a <code>ParameterBlock</code> with a given Vector
     * of sources.
     * @param sources a <code>Vector</code> of source images
     */
    public ParameterBlock(Vector<Object> sources) {
        setSources(sources);
    }

    /**
     * Constructs a <code>ParameterBlock</code> with a given Vector of sources and
     * Vector of parameters.
     * @param sources a <code>Vector</code> of source images
     * @param parameters a <code>Vector</code> of parameters to be used in the
     *        rendering operation
     */
    public ParameterBlock(Vector<Object> sources,
                          Vector<Object> parameters)
    {
        setSources(sources);
        setParameters(parameters);
    }

    /**
     * Creates a shallow copy of a <code>ParameterBlock</code>.  The source and
     * parameter Vectors are copied by reference -- additions or
     * changes will be visible to both versions.
     *
     * @return an Object clone of the <code>ParameterBlock</code>.
     */
    public Object shallowClone() {
        try {
            return super.clone();
        } catch (Exception e) {
            // We can't be here since we implement Cloneable.
            return null;
        }
    }

    /**
     * Creates a copy of a <code>ParameterBlock</code>.  The source and parameter
     * Vectors are cloned, but the actual sources and parameters are
     * copied by reference.  This allows modifications to the order
     * and number of sources and parameters in the clone to be invisible
     * to the original <code>ParameterBlock</code>.  Changes to the shared sources or
     * parameters themselves will still be visible.
     *
     * @return an Object clone of the <code>ParameterBlock</code>.
     */
    @SuppressWarnings("unchecked") // casts from clone
    public Object clone() {
        ParameterBlock theClone;

        try {
            theClone = (ParameterBlock) super.clone();
        } catch (Exception e) {
            // We can't be here since we implement Cloneable.
            return null;
        }

        if (sources != null) {
            theClone.setSources((Vector<Object>)sources.clone());
        }
        if (parameters != null) {
            theClone.setParameters((Vector<Object>)parameters.clone());
        }
        return (Object) theClone;
    }

    /**
     * Adds an image to end of the list of sources.  The image is
     * stored as an object in order to allow new node types in the
     * future.
     *
     * @param source an image object to be stored in the source list.
     * @return a new <code>ParameterBlock</code> containing the specified
     *         <code>source</code>.
     */
    public ParameterBlock addSource(Object source) {
        sources.addElement(source);
        return this;
    }

    /**
     * Returns a source as a general Object.  The caller must cast it into
     * an appropriate type.
     *
     * @param index the index of the source to be returned.
     * @return an <code>Object</code> that represents the source located
     *         at the specified index in the <code>sources</code>
     *         <code>Vector</code>.
     * @see #setSource(Object, int)
     */
    public Object getSource(int index) {
        return sources.elementAt(index);
    }

    /**
     * Replaces an entry in the list of source with a new source.
     * If the index lies beyond the current source list,
     * the list is extended with nulls as needed.
     * @param source the specified source image
     * @param index the index into the <code>sources</code>
     *              <code>Vector</code> at which to
     *              insert the specified <code>source</code>
     * @return a new <code>ParameterBlock</code> that contains the
     *         specified <code>source</code> at the specified
     *         <code>index</code>.
     * @see #getSource(int)
     */
    public ParameterBlock setSource(Object source, int index) {
        int oldSize = sources.size();
        int newSize = index + 1;
        if (oldSize < newSize) {
            sources.setSize(newSize);
        }
        sources.setElementAt(source, index);
        return this;
    }

    /**
     * Returns a source as a <code>RenderedImage</code>.  This method is
     * a convenience method.
     * An exception will be thrown if the source is not a RenderedImage.
     *
     * @param index the index of the source to be returned
     * @return a <code>RenderedImage</code> that represents the source
     *         image that is at the specified index in the
     *         <code>sources</code> <code>Vector</code>.
     */
    public RenderedImage getRenderedSource(int index) {
        return (RenderedImage) sources.elementAt(index);
    }

    /**
     * Returns a source as a RenderableImage.  This method is a
     * convenience method.
     * An exception will be thrown if the sources is not a RenderableImage.
     *
     * @param index the index of the source to be returned
     * @return a <code>RenderableImage</code> that represents the source
     *         image that is at the specified index in the
     *         <code>sources</code> <code>Vector</code>.
     */
    public RenderableImage getRenderableSource(int index) {
        return (RenderableImage) sources.elementAt(index);
    }

    /**
     * Returns the number of source images.
     * @return the number of source images in the <code>sources</code>
     *         <code>Vector</code>.
     */
    public int getNumSources() {
        return sources.size();
    }

    /**
     * Returns the entire Vector of sources.
     * @return the <code>sources</code> <code>Vector</code>.
     * @see #setSources(Vector)
     */
    public Vector<Object> getSources() {
        return sources;
    }

    /**
     * Sets the entire Vector of sources to a given Vector.
     * @param sources the <code>Vector</code> of source images
     * @see #getSources
     */
    public void setSources(Vector<Object> sources) {
        this.sources = sources;
    }

    /** Clears the list of source images. */
    public void removeSources() {
        sources = new Vector<>();
    }

    /**
     * Returns the number of parameters (not including source images).
     * @return the number of parameters in the <code>parameters</code>
     *         <code>Vector</code>.
     */
    public int getNumParameters() {
        return parameters.size();
    }

    /**
     * Returns the entire Vector of parameters.
     * @return the <code>parameters</code> <code>Vector</code>.
     * @see #setParameters(Vector)
     */
    public Vector<Object> getParameters() {
        return parameters;
    }

    /**
     * Sets the entire Vector of parameters to a given Vector.
     * @param parameters the specified <code>Vector</code> of
     *        parameters
     * @see #getParameters
     */
    public void setParameters(Vector<Object> parameters) {
        this.parameters = parameters;
    }

    /** Clears the list of parameters. */
    public void removeParameters() {
        parameters = new Vector<>();
    }

    /**
     * Adds an object to the list of parameters.
     * @param obj the <code>Object</code> to add to the
     *            <code>parameters</code> <code>Vector</code>
     * @return a new <code>ParameterBlock</code> containing
     *         the specified parameter.
     */
    public ParameterBlock add(Object obj) {
        parameters.addElement(obj);
        return this;
    }

    /**
     * Adds a Byte to the list of parameters.
     * @param b the byte to add to the
     *            <code>parameters</code> <code>Vector</code>
     * @return a new <code>ParameterBlock</code> containing
     *         the specified parameter.
     */
    public ParameterBlock add(byte b) {
        return add(Byte.valueOf(b));
    }

    /**
     * Adds a Character to the list of parameters.
     * @param c the char to add to the
     *            <code>parameters</code> <code>Vector</code>
     * @return a new <code>ParameterBlock</code> containing
     *         the specified parameter.
     */
    public ParameterBlock add(char c) {
        return add(Character.valueOf(c));
    }

    /**
     * Adds a Short to the list of parameters.
     * @param s the short to add to the
     *            <code>parameters</code> <code>Vector</code>
     * @return a new <code>ParameterBlock</code> containing
     *         the specified parameter.
     */
    public ParameterBlock add(short s) {
        return add(Short.valueOf(s));
    }

    /**
     * Adds a Integer to the list of parameters.
     * @param i the int to add to the
     *            <code>parameters</code> <code>Vector</code>
     * @return a new <code>ParameterBlock</code> containing
     *         the specified parameter.
     */
    public ParameterBlock add(int i) {
        return add(Integer.valueOf(i));
    }

    /**
     * Adds a Long to the list of parameters.
     * @param l the long to add to the
     *            <code>parameters</code> <code>Vector</code>
     * @return a new <code>ParameterBlock</code> containing
     *         the specified parameter.
     */
    public ParameterBlock add(long l) {
        return add(Long.valueOf(l));
    }

    /**
     * Adds a Float to the list of parameters.
     * @param f the float to add to the
     *            <code>parameters</code> <code>Vector</code>
     * @return a new <code>ParameterBlock</code> containing
     *         the specified parameter.
     */
    public ParameterBlock add(float f) {
        return add(new Float(f));
    }

    /**
     * Adds a Double to the list of parameters.
     * @param d the double to add to the
     *            <code>parameters</code> <code>Vector</code>
     * @return a new <code>ParameterBlock</code> containing
     *         the specified parameter.
     */
    public ParameterBlock add(double d) {
        return add(new Double(d));
    }

    /**
     * Replaces an Object in the list of parameters.
     * If the index lies beyond the current source list,
     * the list is extended with nulls as needed.
     * @param obj the parameter that replaces the
     *        parameter at the specified index in the
     *        <code>parameters</code> <code>Vector</code>
     * @param index the index of the parameter to be
     *        replaced with the specified parameter
     * @return a new <code>ParameterBlock</code> containing
     *        the specified parameter.
     */
    public ParameterBlock set(Object obj, int index) {
        int oldSize = parameters.size();
        int newSize = index + 1;
        if (oldSize < newSize) {
            parameters.setSize(newSize);
        }
        parameters.setElementAt(obj, index);
        return this;
    }

    /**
     * Replaces an Object in the list of parameters with a Byte.
     * If the index lies beyond the current source list,
     * the list is extended with nulls as needed.
     * @param b the parameter that replaces the
     *        parameter at the specified index in the
     *        <code>parameters</code> <code>Vector</code>
     * @param index the index of the parameter to be
     *        replaced with the specified parameter
     * @return a new <code>ParameterBlock</code> containing
     *        the specified parameter.
     */
    public ParameterBlock set(byte b, int index) {
        return set(Byte.valueOf(b), index);
    }

    /**
     * Replaces an Object in the list of parameters with a Character.
     * If the index lies beyond the current source list,
     * the list is extended with nulls as needed.
     * @param c the parameter that replaces the
     *        parameter at the specified index in the
     *        <code>parameters</code> <code>Vector</code>
     * @param index the index of the parameter to be
     *        replaced with the specified parameter
     * @return a new <code>ParameterBlock</code> containing
     *        the specified parameter.
     */
    public ParameterBlock set(char c, int index) {
        return set(Character.valueOf(c), index);
    }

    /**
     * Replaces an Object in the list of parameters with a Short.
     * If the index lies beyond the current source list,
     * the list is extended with nulls as needed.
     * @param s the parameter that replaces the
     *        parameter at the specified index in the
     *        <code>parameters</code> <code>Vector</code>
     * @param index the index of the parameter to be
     *        replaced with the specified parameter
     * @return a new <code>ParameterBlock</code> containing
     *        the specified parameter.
     */
    public ParameterBlock set(short s, int index) {
        return set(Short.valueOf(s), index);
    }

    /**
     * Replaces an Object in the list of parameters with an Integer.
     * If the index lies beyond the current source list,
     * the list is extended with nulls as needed.
     * @param i the parameter that replaces the
     *        parameter at the specified index in the
     *        <code>parameters</code> <code>Vector</code>
     * @param index the index of the parameter to be
     *        replaced with the specified parameter
     * @return a new <code>ParameterBlock</code> containing
     *        the specified parameter.
     */
    public ParameterBlock set(int i, int index) {
        return set(Integer.valueOf(i), index);
    }

    /**
     * Replaces an Object in the list of parameters with a Long.
     * If the index lies beyond the current source list,
     * the list is extended with nulls as needed.
     * @param l the parameter that replaces the
     *        parameter at the specified index in the
     *        <code>parameters</code> <code>Vector</code>
     * @param index the index of the parameter to be
     *        replaced with the specified parameter
     * @return a new <code>ParameterBlock</code> containing
     *        the specified parameter.
     */
    public ParameterBlock set(long l, int index) {
        return set(Long.valueOf(l), index);
    }

    /**
     * Replaces an Object in the list of parameters with a Float.
     * If the index lies beyond the current source list,
     * the list is extended with nulls as needed.
     * @param f the parameter that replaces the
     *        parameter at the specified index in the
     *        <code>parameters</code> <code>Vector</code>
     * @param index the index of the parameter to be
     *        replaced with the specified parameter
     * @return a new <code>ParameterBlock</code> containing
     *        the specified parameter.
     */
    public ParameterBlock set(float f, int index) {
        return set(new Float(f), index);
    }

    /**
     * Replaces an Object in the list of parameters with a Double.
     * If the index lies beyond the current source list,
     * the list is extended with nulls as needed.
     * @param d the parameter that replaces the
     *        parameter at the specified index in the
     *        <code>parameters</code> <code>Vector</code>
     * @param index the index of the parameter to be
     *        replaced with the specified parameter
     * @return a new <code>ParameterBlock</code> containing
     *        the specified parameter.
     */
    public ParameterBlock set(double d, int index) {
        return set(new Double(d), index);
    }

    /**
     * Gets a parameter as an object.
     * @param index the index of the parameter to get
     * @return an <code>Object</code> representing the
     *         the parameter at the specified index
     *         into the <code>parameters</code>
     *         <code>Vector</code>.
     */
    public Object getObjectParameter(int index) {
        return parameters.elementAt(index);
    }

    /**
     * A convenience method to return a parameter as a byte.  An
     * exception is thrown if the parameter is
     * <code>null</code> or not a <code>Byte</code>.
     *
     * @param index the index of the parameter to be returned.
     * @return the parameter at the specified index
     *         as a <code>byte</code> value.
     * @throws ClassCastException if the parameter at the
     *         specified index is not a <code>Byte</code>
     * @throws NullPointerException if the parameter at the specified
     *         index is <code>null</code>
     * @throws ArrayIndexOutOfBoundsException if <code>index</code>
     *         is negative or not less than the current size of this
     *         <code>ParameterBlock</code> object
     */
    public byte getByteParameter(int index) {
        return ((Byte)parameters.elementAt(index)).byteValue();
    }

    /**
     * A convenience method to return a parameter as a char.  An
     * exception is thrown if the parameter is
     * <code>null</code> or not a <code>Character</code>.
     *
     * @param index the index of the parameter to be returned.
     * @return the parameter at the specified index
     *         as a <code>char</code> value.
     * @throws ClassCastException if the parameter at the
     *         specified index is not a <code>Character</code>
     * @throws NullPointerException if the parameter at the specified
     *         index is <code>null</code>
     * @throws ArrayIndexOutOfBoundsException if <code>index</code>
     *         is negative or not less than the current size of this
     *         <code>ParameterBlock</code> object
     */
    public char getCharParameter(int index) {
        return ((Character)parameters.elementAt(index)).charValue();
    }

    /**
     * A convenience method to return a parameter as a short.  An
     * exception is thrown if the parameter is
     * <code>null</code> or not a <code>Short</code>.
     *
     * @param index the index of the parameter to be returned.
     * @return the parameter at the specified index
     *         as a <code>short</code> value.
     * @throws ClassCastException if the parameter at the
     *         specified index is not a <code>Short</code>
     * @throws NullPointerException if the parameter at the specified
     *         index is <code>null</code>
     * @throws ArrayIndexOutOfBoundsException if <code>index</code>
     *         is negative or not less than the current size of this
     *         <code>ParameterBlock</code> object
     */
    public short getShortParameter(int index) {
        return ((Short)parameters.elementAt(index)).shortValue();
    }

    /**
     * A convenience method to return a parameter as an int.  An
     * exception is thrown if the parameter is
     * <code>null</code> or not an <code>Integer</code>.
     *
     * @param index the index of the parameter to be returned.
     * @return the parameter at the specified index
     *         as a <code>int</code> value.
     * @throws ClassCastException if the parameter at the
     *         specified index is not a <code>Integer</code>
     * @throws NullPointerException if the parameter at the specified
     *         index is <code>null</code>
     * @throws ArrayIndexOutOfBoundsException if <code>index</code>
     *         is negative or not less than the current size of this
     *         <code>ParameterBlock</code> object
     */
    public int getIntParameter(int index) {
        return ((Integer)parameters.elementAt(index)).intValue();
    }

    /**
     * A convenience method to return a parameter as a long.  An
     * exception is thrown if the parameter is
     * <code>null</code> or not a <code>Long</code>.
     *
     * @param index the index of the parameter to be returned.
     * @return the parameter at the specified index
     *         as a <code>long</code> value.
     * @throws ClassCastException if the parameter at the
     *         specified index is not a <code>Long</code>
     * @throws NullPointerException if the parameter at the specified
     *         index is <code>null</code>
     * @throws ArrayIndexOutOfBoundsException if <code>index</code>
     *         is negative or not less than the current size of this
     *         <code>ParameterBlock</code> object
     */
    public long getLongParameter(int index) {
        return ((Long)parameters.elementAt(index)).longValue();
    }

    /**
     * A convenience method to return a parameter as a float.  An
     * exception is thrown if the parameter is
     * <code>null</code> or not a <code>Float</code>.
     *
     * @param index the index of the parameter to be returned.
     * @return the parameter at the specified index
     *         as a <code>float</code> value.
     * @throws ClassCastException if the parameter at the
     *         specified index is not a <code>Float</code>
     * @throws NullPointerException if the parameter at the specified
     *         index is <code>null</code>
     * @throws ArrayIndexOutOfBoundsException if <code>index</code>
     *         is negative or not less than the current size of this
     *         <code>ParameterBlock</code> object
     */
    public float getFloatParameter(int index) {
        return ((Float)parameters.elementAt(index)).floatValue();
    }

    /**
     * A convenience method to return a parameter as a double.  An
     * exception is thrown if the parameter is
     * <code>null</code> or not a <code>Double</code>.
     *
     * @param index the index of the parameter to be returned.
     * @return the parameter at the specified index
     *         as a <code>double</code> value.
     * @throws ClassCastException if the parameter at the
     *         specified index is not a <code>Double</code>
     * @throws NullPointerException if the parameter at the specified
     *         index is <code>null</code>
     * @throws ArrayIndexOutOfBoundsException if <code>index</code>
     *         is negative or not less than the current size of this
     *         <code>ParameterBlock</code> object
     */
    public double getDoubleParameter(int index) {
        return ((Double)parameters.elementAt(index)).doubleValue();
    }

    /**
     * Returns an array of Class objects describing the types
     * of the parameters.
     * @return an array of <code>Class</code> objects.
     */
    public Class<?>[] getParamClasses() {
        int numParams = getNumParameters();
        Class<?>[] classes = new Class<?>[numParams];
        int i;

        for (i = 0; i < numParams; i++) {
            Object obj = getObjectParameter(i);
            if (obj instanceof Byte) {
              classes[i] = byte.class;
            } else if (obj instanceof Character) {
              classes[i] = char.class;
            } else if (obj instanceof Short) {
              classes[i] = short.class;
            } else if (obj instanceof Integer) {
              classes[i] = int.class;
            } else if (obj instanceof Long) {
              classes[i] = long.class;
            } else if (obj instanceof Float) {
              classes[i] = float.class;
            } else if (obj instanceof Double) {
              classes[i] = double.class;
            } else {
              classes[i] = obj.getClass();
            }
        }

        return classes;
    }
}
