/*
 * Copyright (c) 2000, 2005, Oracle and/or its affiliates. All rights reserved.
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

package javax.imageio.metadata;

import org.w3c.dom.Node;
import java.lang.reflect.Method;

/**
 * An abstract class to be extended by objects that represent metadata
 * (non-image data) associated with images and streams.  Plug-ins
 * represent metadata using opaque, plug-in specific objects.  These
 * objects, however, provide the ability to access their internal
 * information as a tree of <code>IIOMetadataNode</code> objects that
 * support the XML DOM interfaces as well as additional interfaces for
 * storing non-textual data and retrieving information about legal
 * data values.  The format of such trees is plug-in dependent, but
 * plug-ins may choose to support a plug-in neutral format described
 * below.  A single plug-in may support multiple metadata formats,
 * whose names maybe determined by calling
 * <code>getMetadataFormatNames</code>.  The plug-in may also support
 * a single special format, referred to as the "native" format, which
 * is designed to encode its metadata losslessly.  This format will
 * typically be designed specifically to work with a specific file
 * format, so that images may be loaded and saved in the same format
 * with no loss of metadata, but may be less useful for transfering
 * metadata between an <code>ImageReader</code> and an
 * <code>ImageWriter</code> for different image formats.  To convert
 * between two native formats as losslessly as the image file formats
 * will allow, an <code>ImageTranscoder</code> object must be used.
 *
 * @see javax.imageio.ImageReader#getImageMetadata
 * @see javax.imageio.ImageReader#getStreamMetadata
 * @see javax.imageio.ImageReader#readAll
 * @see javax.imageio.ImageWriter#getDefaultStreamMetadata
 * @see javax.imageio.ImageWriter#getDefaultImageMetadata
 * @see javax.imageio.ImageWriter#write
 * @see javax.imageio.ImageWriter#convertImageMetadata
 * @see javax.imageio.ImageWriter#convertStreamMetadata
 * @see javax.imageio.IIOImage
 * @see javax.imageio.ImageTranscoder
 *
 */
public abstract class IIOMetadata {

    /**
     * A boolean indicating whether the concrete subclass supports the
     * standard metadata format, set via the constructor.
     */
    protected boolean standardFormatSupported;

    /**
     * The name of the native metadata format for this object,
     * initialized to <code>null</code> and set via the constructor.
     */
    protected String nativeMetadataFormatName = null;

    /**
     * The name of the class implementing <code>IIOMetadataFormat</code>
     * and representing the native metadata format, initialized to
     * <code>null</code> and set via the constructor.
     */
    protected String nativeMetadataFormatClassName = null;

    /**
     * An array of names of formats, other than the standard and
     * native formats, that are supported by this plug-in,
     * initialized to <code>null</code> and set via the constructor.
     */
    protected String[] extraMetadataFormatNames = null;

    /**
     * An array of names of classes implementing <code>IIOMetadataFormat</code>
     * and representing the metadata formats, other than the standard and
     * native formats, that are supported by this plug-in,
     * initialized to <code>null</code> and set via the constructor.
     */
    protected String[] extraMetadataFormatClassNames = null;

    /**
     * An <code>IIOMetadataController</code> that is suggested for use
     * as the controller for this <code>IIOMetadata</code> object.  It
     * may be retrieved via <code>getDefaultController</code>.  To
     * install the default controller, call
     * <code>setController(getDefaultController())</code>.  This
     * instance variable should be set by subclasses that choose to
     * provide their own default controller, usually a GUI, for
     * setting parameters.
     *
     * @see IIOMetadataController
     * @see #getDefaultController
     */
    protected IIOMetadataController defaultController = null;

    /**
     * The <code>IIOMetadataController</code> that will be
     * used to provide settings for this <code>IIOMetadata</code>
     * object when the <code>activateController</code> method
     * is called.  This value overrides any default controller,
     * even when <code>null</code>.
     *
     * @see IIOMetadataController
     * @see #setController(IIOMetadataController)
     * @see #hasController()
     * @see #activateController()
     */
    protected IIOMetadataController controller = null;

    /**
     * Constructs an empty <code>IIOMetadata</code> object.  The
     * subclass is responsible for suppying values for all protected
     * instance variables that will allow any non-overridden default
     * implemtations of methods to satisfy their contracts.  For example,
     * <code>extraMetadataFormatNames</code> should not have length 0.
     */
    protected IIOMetadata() {}

    /**
     * Constructs an <code>IIOMetadata</code> object with the given
     * format names and format class names, as well as a boolean
     * indicating whether the standard format is supported.
     *
     * <p> This constructor does not attempt to check the class names
     * for validity.  Invalid class names may cause exceptions in
     * subsequent calls to <code>getMetadataFormat</code>.
     *
     * @param standardMetadataFormatSupported <code>true</code> if
     * this object can return or accept a DOM tree using the standard
     * metadata format.
     * @param nativeMetadataFormatName the name of the native metadata
     * format, as a <code>String</code>, or <code>null</code> if there
     * is no native format.
     * @param nativeMetadataFormatClassName the name of the class of
     * the native metadata format, or <code>null</code> if there is
     * no native format.
     * @param extraMetadataFormatNames an array of <code>String</code>s
     * indicating additional formats supported by this object, or
     * <code>null</code> if there are none.
     * @param extraMetadataFormatClassNames an array of <code>String</code>s
     * indicating the class names of any additional formats supported by
     * this object, or <code>null</code> if there are none.
     *
     * @exception IllegalArgumentException if
     * <code>extraMetadataFormatNames</code> has length 0.
     * @exception IllegalArgumentException if
     * <code>extraMetadataFormatNames</code> and
     * <code>extraMetadataFormatClassNames</code> are neither both
     * <code>null</code>, nor of the same length.
     */
    protected IIOMetadata(boolean standardMetadataFormatSupported,
                          String nativeMetadataFormatName,
                          String nativeMetadataFormatClassName,
                          String[] extraMetadataFormatNames,
                          String[] extraMetadataFormatClassNames) {
        this.standardFormatSupported = standardMetadataFormatSupported;
        this.nativeMetadataFormatName = nativeMetadataFormatName;
        this.nativeMetadataFormatClassName = nativeMetadataFormatClassName;
        if (extraMetadataFormatNames != null) {
            if (extraMetadataFormatNames.length == 0) {
                throw new IllegalArgumentException
                    ("extraMetadataFormatNames.length == 0!");
            }
            if (extraMetadataFormatClassNames == null) {
                throw new IllegalArgumentException
                    ("extraMetadataFormatNames != null && extraMetadataFormatClassNames == null!");
            }
            if (extraMetadataFormatClassNames.length !=
                extraMetadataFormatNames.length) {
                throw new IllegalArgumentException
                    ("extraMetadataFormatClassNames.length != extraMetadataFormatNames.length!");
            }
            this.extraMetadataFormatNames =
                (String[]) extraMetadataFormatNames.clone();
            this.extraMetadataFormatClassNames =
                (String[]) extraMetadataFormatClassNames.clone();
        } else {
            if (extraMetadataFormatClassNames != null) {
                throw new IllegalArgumentException
                    ("extraMetadataFormatNames == null && extraMetadataFormatClassNames != null!");
            }
        }
    }

    /**
     * Returns <code>true</code> if the standard metadata format is
     * supported by <code>getMetadataFormat</code>,
     * <code>getAsTree</code>, <code>setFromTree</code>, and
     * <code>mergeTree</code>.
     *
     * <p> The default implementation returns the value of the
     * <code>standardFormatSupported</code> instance variable.
     *
     * @return <code>true</code> if the standard metadata format
     * is supported.
     *
     * @see #getAsTree
     * @see #setFromTree
     * @see #mergeTree
     * @see #getMetadataFormat
     */
    public boolean isStandardMetadataFormatSupported() {
        return standardFormatSupported;
    }

    /**
     * Returns <code>true</code> if this object does not support the
     * <code>mergeTree</code>, <code>setFromTree</code>, and
     * <code>reset</code> methods.
     *
     * @return true if this <code>IIOMetadata</code> object cannot be
     * modified.
     */
    public abstract boolean isReadOnly();

    /**
     * Returns the name of the "native" metadata format for this
     * plug-in, which typically allows for lossless encoding and
     * transmission of the metadata stored in the format handled by
     * this plug-in.  If no such format is supported,
     * <code>null</code>will be returned.
     *
     * <p> The structure and contents of the "native" metadata format
     * are defined by the plug-in that created this
     * <code>IIOMetadata</code> object.  Plug-ins for simple formats
     * will usually create a dummy node for the root, and then a
     * series of child nodes representing individual tags, chunks, or
     * keyword/value pairs.  A plug-in may choose whether or not to
     * document its native format.
     *
     * <p> The default implementation returns the value of the
     * <code>nativeMetadataFormatName</code> instance variable.
     *
     * @return the name of the native format, or <code>null</code>.
     *
     * @see #getExtraMetadataFormatNames
     * @see #getMetadataFormatNames
     */
    public String getNativeMetadataFormatName() {
        return nativeMetadataFormatName;
    }

    /**
     * Returns an array of <code>String</code>s containing the names
     * of additional metadata formats, other than the native and standard
     * formats, recognized by this plug-in's
     * <code>getAsTree</code>, <code>setFromTree</code>, and
     * <code>mergeTree</code> methods.  If there are no such additional
     * formats, <code>null</code> is returned.
     *
     * <p> The default implementation returns a clone of the
     * <code>extraMetadataFormatNames</code> instance variable.
     *
     * @return an array of <code>String</code>s with length at least
     * 1, or <code>null</code>.
     *
     * @see #getAsTree
     * @see #setFromTree
     * @see #mergeTree
     * @see #getNativeMetadataFormatName
     * @see #getMetadataFormatNames
     */
    public String[] getExtraMetadataFormatNames() {
        if (extraMetadataFormatNames == null) {
            return null;
        }
        return (String[])extraMetadataFormatNames.clone();
    }

    /**
     * Returns an array of <code>String</code>s containing the names
     * of all metadata formats, including the native and standard
     * formats, recognized by this plug-in's <code>getAsTree</code>,
     * <code>setFromTree</code>, and <code>mergeTree</code> methods.
     * If there are no such formats, <code>null</code> is returned.
     *
     * <p> The default implementation calls
     * <code>getNativeMetadataFormatName</code>,
     * <code>isStandardMetadataFormatSupported</code>, and
     * <code>getExtraMetadataFormatNames</code> and returns the
     * combined results.
     *
     * @return an array of <code>String</code>s.
     *
     * @see #getNativeMetadataFormatName
     * @see #isStandardMetadataFormatSupported
     * @see #getExtraMetadataFormatNames
     */
    public String[] getMetadataFormatNames() {
        String nativeName = getNativeMetadataFormatName();
        String standardName = isStandardMetadataFormatSupported() ?
            IIOMetadataFormatImpl.standardMetadataFormatName : null;
        String[] extraNames = getExtraMetadataFormatNames();

        int numFormats = 0;
        if (nativeName != null) {
            ++numFormats;
        }
        if (standardName != null) {
            ++numFormats;
        }
        if (extraNames != null) {
            numFormats += extraNames.length;
        }
        if (numFormats == 0) {
            return null;
        }

        String[] formats = new String[numFormats];
        int index = 0;
        if (nativeName != null) {
            formats[index++] = nativeName;
        }
        if (standardName != null) {
            formats[index++] = standardName;
        }
        if (extraNames != null) {
            for (int i = 0; i < extraNames.length; i++) {
                formats[index++] = extraNames[i];
            }
        }

        return formats;
    }

    /**
     * Returns an <code>IIOMetadataFormat</code> object describing the
     * given metadata format, or <code>null</code> if no description
     * is available.  The supplied name must be one of those returned
     * by <code>getMetadataFormatNames</code> (<i>i.e.</i>, either the
     * native format name, the standard format name, or one of those
     * returned by <code>getExtraMetadataFormatNames</code>).
     *
     * <p> The default implementation checks the name against the
     * global standard metadata format name, and returns that format
     * if it is supported.  Otherwise, it checks against the native
     * format names followed by any additional format names.  If a
     * match is found, it retrieves the name of the
     * <code>IIOMetadataFormat</code> class from
     * <code>nativeMetadataFormatClassName</code> or
     * <code>extraMetadataFormatClassNames</code> as appropriate, and
     * constructs an instance of that class using its
     * <code>getInstance</code> method.
     *
     * @param formatName the desired metadata format.
     *
     * @return an <code>IIOMetadataFormat</code> object.
     *
     * @exception IllegalArgumentException if <code>formatName</code>
     * is <code>null</code> or is not one of the names recognized by
     * the plug-in.
     * @exception IllegalStateException if the class corresponding to
     * the format name cannot be loaded.
     */
    public IIOMetadataFormat getMetadataFormat(String formatName) {
        if (formatName == null) {
            throw new IllegalArgumentException("formatName == null!");
        }
        if (standardFormatSupported
            && formatName.equals
                (IIOMetadataFormatImpl.standardMetadataFormatName)) {
            return IIOMetadataFormatImpl.getStandardFormatInstance();
        }
        String formatClassName = null;
        if (formatName.equals(nativeMetadataFormatName)) {
            formatClassName = nativeMetadataFormatClassName;
        } else if (extraMetadataFormatNames != null) {
            for (int i = 0; i < extraMetadataFormatNames.length; i++) {
                if (formatName.equals(extraMetadataFormatNames[i])) {
                    formatClassName = extraMetadataFormatClassNames[i];
                    break;  // out of for
                }
            }
        }
        if (formatClassName == null) {
            throw new IllegalArgumentException("Unsupported format name");
        }
        try {
            Class cls = null;
            final Object o = this;

            // firstly we try to use classloader used for loading
            // the IIOMetadata implemantation for this plugin.
            ClassLoader loader = (ClassLoader)
                java.security.AccessController.doPrivileged(
                    new java.security.PrivilegedAction() {
                            public Object run() {
                                return o.getClass().getClassLoader();
                            }
                        });

            try {
                cls = Class.forName(formatClassName, true,
                                    loader);
            } catch (ClassNotFoundException e) {
                // we failed to load IIOMetadataFormat class by
                // using IIOMetadata classloader.Next try is to
                // use thread context classloader.
                loader = (ClassLoader)
                    java.security.AccessController.doPrivileged(
                        new java.security.PrivilegedAction() {
                                public Object run() {
                                    return Thread.currentThread().getContextClassLoader();
                                }
                        });
                try {
                    cls = Class.forName(formatClassName, true,
                                        loader);
                } catch (ClassNotFoundException e1) {
                    // finally we try to use system classloader in case
                    // if we failed to load IIOMetadataFormat implementation
                    // class above.
                    cls = Class.forName(formatClassName, true,
                                        ClassLoader.getSystemClassLoader());
                }
            }

            Method meth = cls.getMethod("getInstance");
            return (IIOMetadataFormat) meth.invoke(null);
        } catch (Exception e) {
            RuntimeException ex =
                new IllegalStateException ("Can't obtain format");
            ex.initCause(e);
            throw ex;
        }

    }

    /**
     * Returns an XML DOM <code>Node</code> object that represents the
     * root of a tree of metadata contained within this object
     * according to the conventions defined by a given metadata
     * format.
     *
     * <p> The names of the available metadata formats may be queried
     * using the <code>getMetadataFormatNames</code> method.
     *
     * @param formatName the desired metadata format.
     *
     * @return an XML DOM <code>Node</code> object forming the
     * root of a tree.
     *
     * @exception IllegalArgumentException if <code>formatName</code>
     * is <code>null</code> or is not one of the names returned by
     * <code>getMetadataFormatNames</code>.
     *
     * @see #getMetadataFormatNames
     * @see #setFromTree
     * @see #mergeTree
     */
    public abstract Node getAsTree(String formatName);

    /**
     * Alters the internal state of this <code>IIOMetadata</code>
     * object from a tree of XML DOM <code>Node</code>s whose syntax
     * is defined by the given metadata format.  The previous state is
     * altered only as necessary to accomodate the nodes that are
     * present in the given tree.  If the tree structure or contents
     * are invalid, an <code>IIOInvalidTreeException</code> will be
     * thrown.
     *
     * <p> As the semantics of how a tree or subtree may be merged with
     * another tree are completely format-specific, plug-in authors may
     * implement this method in whatever manner is most appropriate for
     * the format, including simply replacing all existing state with the
     * contents of the given tree.
     *
     * @param formatName the desired metadata format.
     * @param root an XML DOM <code>Node</code> object forming the
     * root of a tree.
     *
     * @exception IllegalStateException if this object is read-only.
     * @exception IllegalArgumentException if <code>formatName</code>
     * is <code>null</code> or is not one of the names returned by
     * <code>getMetadataFormatNames</code>.
     * @exception IllegalArgumentException if <code>root</code> is
     * <code>null</code>.
     * @exception IIOInvalidTreeException if the tree cannot be parsed
     * successfully using the rules of the given format.
     *
     * @see #getMetadataFormatNames
     * @see #getAsTree
     * @see #setFromTree
     */
    public abstract void mergeTree(String formatName, Node root)
        throws IIOInvalidTreeException;

    /**
     * Returns an <code>IIOMetadataNode</code> representing the chroma
     * information of the standard <code>javax_imageio_1.0</code>
     * metadata format, or <code>null</code> if no such information is
     * available.  This method is intended to be called by the utility
     * routine <code>getStandardTree</code>.
     *
     * <p> The default implementation returns <code>null</code>.
     *
     * <p> Subclasses should override this method to produce an
     * appropriate subtree if they wish to support the standard
     * metadata format.
     *
     * @return an <code>IIOMetadataNode</code>, or <code>null</code>.
     *
     * @see #getStandardTree
     */
    protected IIOMetadataNode getStandardChromaNode() {
        return null;
    }

    /**
     * Returns an <code>IIOMetadataNode</code> representing the
     * compression information of the standard
     * <code>javax_imageio_1.0</code> metadata format, or
     * <code>null</code> if no such information is available.  This
     * method is intended to be called by the utility routine
     * <code>getStandardTree</code>.
     *
     * <p> The default implementation returns <code>null</code>.
     *
     * <p> Subclasses should override this method to produce an
     * appropriate subtree if they wish to support the standard
     * metadata format.
     *
     * @return an <code>IIOMetadataNode</code>, or <code>null</code>.
     *
     * @see #getStandardTree
     */
    protected IIOMetadataNode getStandardCompressionNode() {
        return null;
    }

    /**
     * Returns an <code>IIOMetadataNode</code> representing the data
     * format information of the standard
     * <code>javax_imageio_1.0</code> metadata format, or
     * <code>null</code> if no such information is available.  This
     * method is intended to be called by the utility routine
     * <code>getStandardTree</code>.
     *
     * <p> The default implementation returns <code>null</code>.
     *
     * <p> Subclasses should override this method to produce an
     * appropriate subtree if they wish to support the standard
     * metadata format.
     *
     * @return an <code>IIOMetadataNode</code>, or <code>null</code>.
     *
     * @see #getStandardTree
     */
    protected IIOMetadataNode getStandardDataNode() {
        return null;
    }

    /**
     * Returns an <code>IIOMetadataNode</code> representing the
     * dimension information of the standard
     * <code>javax_imageio_1.0</code> metadata format, or
     * <code>null</code> if no such information is available.  This
     * method is intended to be called by the utility routine
     * <code>getStandardTree</code>.
     *
     * <p> The default implementation returns <code>null</code>.
     *
     * <p> Subclasses should override this method to produce an
     * appropriate subtree if they wish to support the standard
     * metadata format.
     *
     * @return an <code>IIOMetadataNode</code>, or <code>null</code>.
     *
     * @see #getStandardTree
     */
    protected IIOMetadataNode getStandardDimensionNode() {
        return null;
    }

    /**
     * Returns an <code>IIOMetadataNode</code> representing the document
     * information of the standard <code>javax_imageio_1.0</code>
     * metadata format, or <code>null</code> if no such information is
     * available.  This method is intended to be called by the utility
     * routine <code>getStandardTree</code>.
     *
     * <p> The default implementation returns <code>null</code>.
     *
     * <p> Subclasses should override this method to produce an
     * appropriate subtree if they wish to support the standard
     * metadata format.
     *
     * @return an <code>IIOMetadataNode</code>, or <code>null</code>.
     *
     * @see #getStandardTree
     */
    protected IIOMetadataNode getStandardDocumentNode() {
        return null;
    }

    /**
     * Returns an <code>IIOMetadataNode</code> representing the textual
     * information of the standard <code>javax_imageio_1.0</code>
     * metadata format, or <code>null</code> if no such information is
     * available.  This method is intended to be called by the utility
     * routine <code>getStandardTree</code>.
     *
     * <p> The default implementation returns <code>null</code>.
     *
     * <p> Subclasses should override this method to produce an
     * appropriate subtree if they wish to support the standard
     * metadata format.
     *
     * @return an <code>IIOMetadataNode</code>, or <code>null</code>.
     *
     * @see #getStandardTree
     */
    protected IIOMetadataNode getStandardTextNode() {
        return null;
    }

    /**
     * Returns an <code>IIOMetadataNode</code> representing the tiling
     * information of the standard <code>javax_imageio_1.0</code>
     * metadata format, or <code>null</code> if no such information is
     * available.  This method is intended to be called by the utility
     * routine <code>getStandardTree</code>.
     *
     * <p> The default implementation returns <code>null</code>.
     *
     * <p> Subclasses should override this method to produce an
     * appropriate subtree if they wish to support the standard
     * metadata format.
     *
     * @return an <code>IIOMetadataNode</code>, or <code>null</code>.
     *
     * @see #getStandardTree
     */
    protected IIOMetadataNode getStandardTileNode() {
        return null;
    }

    /**
     * Returns an <code>IIOMetadataNode</code> representing the
     * transparency information of the standard
     * <code>javax_imageio_1.0</code> metadata format, or
     * <code>null</code> if no such information is available.  This
     * method is intended to be called by the utility routine
     * <code>getStandardTree</code>.
     *
     * <p> The default implementation returns <code>null</code>.
     *
     * <p> Subclasses should override this method to produce an
     * appropriate subtree if they wish to support the standard
     * metadata format.
     *
     * @return an <code>IIOMetadataNode</code>, or <code>null</code>.
     */
    protected IIOMetadataNode getStandardTransparencyNode() {
        return null;
    }

    /**
     * Appends a new node to an existing node, if the new node is
     * non-<code>null</code>.
     */
    private void append(IIOMetadataNode root, IIOMetadataNode node) {
        if (node != null) {
            root.appendChild(node);
        }
    }

    /**
     * A utility method to return a tree of
     * <code>IIOMetadataNode</code>s representing the metadata
     * contained within this object according to the conventions of
     * the standard <code>javax_imageio_1.0</code> metadata format.
     *
     * <p> This method calls the various <code>getStandard*Node</code>
     * methods to supply each of the subtrees rooted at the children
     * of the root node.  If any of those methods returns
     * <code>null</code>, the corresponding subtree will be omitted.
     * If all of them return <code>null</code>, a tree consisting of a
     * single root node will be returned.
     *
     * @return an <code>IIOMetadataNode</code> representing the root
     * of a metadata tree in the <code>javax_imageio_1.0</code>
     * format.
     *
     * @see #getStandardChromaNode
     * @see #getStandardCompressionNode
     * @see #getStandardDataNode
     * @see #getStandardDimensionNode
     * @see #getStandardDocumentNode
     * @see #getStandardTextNode
     * @see #getStandardTileNode
     * @see #getStandardTransparencyNode
     */
    protected final IIOMetadataNode getStandardTree() {
        IIOMetadataNode root = new IIOMetadataNode
                (IIOMetadataFormatImpl.standardMetadataFormatName);
        append(root, getStandardChromaNode());
        append(root, getStandardCompressionNode());
        append(root, getStandardDataNode());
        append(root, getStandardDimensionNode());
        append(root, getStandardDocumentNode());
        append(root, getStandardTextNode());
        append(root, getStandardTileNode());
        append(root, getStandardTransparencyNode());
        return root;
    }

    /**
     * Sets the internal state of this <code>IIOMetadata</code> object
     * from a tree of XML DOM <code>Node</code>s whose syntax is
     * defined by the given metadata format.  The previous state is
     * discarded.  If the tree's structure or contents are invalid, an
     * <code>IIOInvalidTreeException</code> will be thrown.
     *
     * <p> The default implementation calls <code>reset</code>
     * followed by <code>mergeTree(formatName, root)</code>.
     *
     * @param formatName the desired metadata format.
     * @param root an XML DOM <code>Node</code> object forming the
     * root of a tree.
     *
     * @exception IllegalStateException if this object is read-only.
     * @exception IllegalArgumentException if <code>formatName</code>
     * is <code>null</code> or is not one of the names returned by
     * <code>getMetadataFormatNames</code>.
     * @exception IllegalArgumentException if <code>root</code> is
     * <code>null</code>.
     * @exception IIOInvalidTreeException if the tree cannot be parsed
     * successfully using the rules of the given format.
     *
     * @see #getMetadataFormatNames
     * @see #getAsTree
     * @see #mergeTree
     */
    public void setFromTree(String formatName, Node root)
        throws IIOInvalidTreeException {
        reset();
        mergeTree(formatName, root);
    }

    /**
     * Resets all the data stored in this object to default values,
     * usually to the state this object was in immediately after
     * construction, though the precise semantics are plug-in specific.
     * Note that there are many possible default values, depending on
     * how the object was created.
     *
     * @exception IllegalStateException if this object is read-only.
     *
     * @see javax.imageio.ImageReader#getStreamMetadata
     * @see javax.imageio.ImageReader#getImageMetadata
     * @see javax.imageio.ImageWriter#getDefaultStreamMetadata
     * @see javax.imageio.ImageWriter#getDefaultImageMetadata
     */
    public abstract void reset();

    /**
     * Sets the <code>IIOMetadataController</code> to be used
     * to provide settings for this <code>IIOMetadata</code>
     * object when the <code>activateController</code> method
     * is called, overriding any default controller.  If the
     * argument is <code>null</code>, no controller will be
     * used, including any default.  To restore the default, use
     * <code>setController(getDefaultController())</code>.
     *
     * <p> The default implementation sets the <code>controller</code>
     * instance variable to the supplied value.
     *
     * @param controller An appropriate
     * <code>IIOMetadataController</code>, or <code>null</code>.
     *
     * @see IIOMetadataController
     * @see #getController
     * @see #getDefaultController
     * @see #hasController
     * @see #activateController()
     */
    public void setController(IIOMetadataController controller) {
        this.controller = controller;
    }

    /**
     * Returns whatever <code>IIOMetadataController</code> is currently
     * installed.  This could be the default if there is one,
     * <code>null</code>, or the argument of the most recent call
     * to <code>setController</code>.
     *
     * <p> The default implementation returns the value of the
     * <code>controller</code> instance variable.
     *
     * @return the currently installed
     * <code>IIOMetadataController</code>, or <code>null</code>.
     *
     * @see IIOMetadataController
     * @see #setController
     * @see #getDefaultController
     * @see #hasController
     * @see #activateController()
     */
    public IIOMetadataController getController() {
        return controller;
    }

    /**
     * Returns the default <code>IIOMetadataController</code>, if there
     * is one, regardless of the currently installed controller.  If
     * there is no default controller, returns <code>null</code>.
     *
     * <p> The default implementation returns the value of the
     * <code>defaultController</code> instance variable.
     *
     * @return the default <code>IIOMetadataController</code>, or
     * <code>null</code>.
     *
     * @see IIOMetadataController
     * @see #setController(IIOMetadataController)
     * @see #getController
     * @see #hasController
     * @see #activateController()
     */
    public IIOMetadataController getDefaultController() {
        return defaultController;
    }

    /**
     * Returns <code>true</code> if there is a controller installed
     * for this <code>IIOMetadata</code> object.
     *
     * <p> The default implementation returns <code>true</code> if the
     * <code>getController</code> method returns a
     * non-<code>null</code> value.
     *
     * @return <code>true</code> if a controller is installed.
     *
     * @see IIOMetadataController
     * @see #setController(IIOMetadataController)
     * @see #getController
     * @see #getDefaultController
     * @see #activateController()
     */
    public boolean hasController() {
        return (getController() != null);
    }

    /**
     * Activates the installed <code>IIOMetadataController</code> for
     * this <code>IIOMetadata</code> object and returns the resulting
     * value.  When this method returns <code>true</code>, all values for this
     * <code>IIOMetadata</code> object will be ready for the next write
     * operation.  If <code>false</code> is
     * returned, no settings in this object will have been disturbed
     * (<i>i.e.</i>, the user canceled the operation).
     *
     * <p> Ordinarily, the controller will be a GUI providing a user
     * interface for a subclass of <code>IIOMetadata</code> for a
     * particular plug-in.  Controllers need not be GUIs, however.
     *
     * <p> The default implementation calls <code>getController</code>
     * and the calls <code>activate</code> on the returned object if
     * <code>hasController</code> returns <code>true</code>.
     *
     * @return <code>true</code> if the controller completed normally.
     *
     * @exception IllegalStateException if there is no controller
     * currently installed.
     *
     * @see IIOMetadataController
     * @see #setController(IIOMetadataController)
     * @see #getController
     * @see #getDefaultController
     * @see #hasController
     */
    public boolean activateController() {
        if (!hasController()) {
            throw new IllegalStateException("hasController() == false!");
        }
        return getController().activate(this);
    }
}
