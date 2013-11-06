/*
 * Copyright (c) 2003, 2013, Oracle and/or its affiliates. All rights reserved.
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
package java.util.jar;

import java.util.SortedMap;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.File;
import java.io.IOException;
import java.beans.PropertyChangeListener;




/**
 * Transforms a JAR file to or from a packed stream in Pack200 format.
 * Please refer to Network Transfer Format JSR 200 Specification at
 * <a href=http://jcp.org/aboutJava/communityprocess/review/jsr200/index.html>http://jcp.org/aboutJava/communityprocess/review/jsr200/index.html</a>
 * <p>
 * Typically the packer engine is used by application developers
 * to deploy or host JAR files on a website.
 * The unpacker  engine is used by deployment applications to
 * transform the byte-stream back to JAR format.
 * <p>
 * Here is an example using  packer and unpacker:
 * <pre>{@code
 *    import java.util.jar.Pack200;
 *    import java.util.jar.Pack200.*;
 *    ...
 *    // Create the Packer object
 *    Packer packer = Pack200.newPacker();
 *
 *    // Initialize the state by setting the desired properties
 *    Map p = packer.properties();
 *    // take more time choosing codings for better compression
 *    p.put(Packer.EFFORT, "7");  // default is "5"
 *    // use largest-possible archive segments (>10% better compression).
 *    p.put(Packer.SEGMENT_LIMIT, "-1");
 *    // reorder files for better compression.
 *    p.put(Packer.KEEP_FILE_ORDER, Packer.FALSE);
 *    // smear modification times to a single value.
 *    p.put(Packer.MODIFICATION_TIME, Packer.LATEST);
 *    // ignore all JAR deflation requests,
 *    // transmitting a single request to use "store" mode.
 *    p.put(Packer.DEFLATE_HINT, Packer.FALSE);
 *    // discard debug attributes
 *    p.put(Packer.CODE_ATTRIBUTE_PFX+"LineNumberTable", Packer.STRIP);
 *    // throw an error if an attribute is unrecognized
 *    p.put(Packer.UNKNOWN_ATTRIBUTE, Packer.ERROR);
 *    // pass one class file uncompressed:
 *    p.put(Packer.PASS_FILE_PFX+0, "mutants/Rogue.class");
 *    try {
 *        JarFile jarFile = new JarFile("/tmp/testref.jar");
 *        FileOutputStream fos = new FileOutputStream("/tmp/test.pack");
 *        // Call the packer
 *        packer.pack(jarFile, fos);
 *        jarFile.close();
 *        fos.close();
 *
 *        File f = new File("/tmp/test.pack");
 *        FileOutputStream fostream = new FileOutputStream("/tmp/test.jar");
 *        JarOutputStream jostream = new JarOutputStream(fostream);
 *        Unpacker unpacker = Pack200.newUnpacker();
 *        // Call the unpacker
 *        unpacker.unpack(f, jostream);
 *        // Must explicitly close the output.
 *        jostream.close();
 *    } catch (IOException ioe) {
 *        ioe.printStackTrace();
 *    }
 * }</pre>
 * <p>
 * A Pack200 file compressed with gzip can be hosted on HTTP/1.1 web servers.
 * The deployment applications can use "Accept-Encoding=pack200-gzip". This
 * indicates to the server that the client application desires a version of
 * the file encoded with Pack200 and further compressed with gzip. Please
 * refer to  <a href="{@docRoot}/../technotes/guides/deployment/deployment-guide/pack200.html">Java Deployment Guide</a> for more details and
 * techniques.
 * <p>
 * Unless otherwise noted, passing a <tt>null</tt> argument to a constructor or
 * method in this class will cause a {@link NullPointerException} to be thrown.
 *
 * @author John Rose
 * @author Kumar Srinivasan
 * @since 1.5
 */
public abstract class Pack200 {
    private Pack200() {} //prevent instantiation

    // Static methods of the Pack200 class.
    /**
     * Obtain new instance of a class that implements Packer.
     * <ul>
     * <li><p>If the system property <tt>java.util.jar.Pack200.Packer</tt>
     * is defined, then the value is taken to be the fully-qualified name
     * of a concrete implementation class, which must implement Packer.
     * This class is loaded and instantiated.  If this process fails
     * then an unspecified error is thrown.</p></li>
     *
     * <li><p>If an implementation has not been specified with the system
     * property, then the system-default implementation class is instantiated,
     * and the result is returned.</p></li>
     * </ul>
     *
     * <p>Note:  The returned object is not guaranteed to operate
     * correctly if multiple threads use it at the same time.
     * A multi-threaded application should either allocate multiple
     * packer engines, or else serialize use of one engine with a lock.
     *
     * @return  A newly allocated Packer engine.
     */
    public synchronized static Packer newPacker() {
        return (Packer) newInstance(PACK_PROVIDER);
    }


    /**
     * Obtain new instance of a class that implements Unpacker.
     * <ul>
     * <li><p>If the system property <tt>java.util.jar.Pack200.Unpacker</tt>
     * is defined, then the value is taken to be the fully-qualified
     * name of a concrete implementation class, which must implement Unpacker.
     * The class is loaded and instantiated.  If this process fails
     * then an unspecified error is thrown.</p></li>
     *
     * <li><p>If an implementation has not been specified with the
     * system property, then the system-default implementation class
     * is instantiated, and the result is returned.</p></li>
     * </ul>
     *
     * <p>Note:  The returned object is not guaranteed to operate
     * correctly if multiple threads use it at the same time.
     * A multi-threaded application should either allocate multiple
     * unpacker engines, or else serialize use of one engine with a lock.
     *
     * @return  A newly allocated Unpacker engine.
     */

    public static Unpacker newUnpacker() {
        return (Unpacker) newInstance(UNPACK_PROVIDER);
    }

    // Interfaces
    /**
     * The packer engine applies various transformations to the input JAR file,
     * making the pack stream highly compressible by a compressor such as
     * gzip or zip. An instance of the engine can be obtained
     * using {@link #newPacker}.

     * The high degree of compression is achieved
     * by using a number of techniques described in the JSR 200 specification.
     * Some of the techniques are sorting, re-ordering and co-location of the
     * constant pool.
     * <p>
     * The pack engine is initialized to an initial state as described
     * by their properties below.
     * The initial state can be manipulated by getting the
     * engine properties (using {@link #properties}) and storing
     * the modified properties on the map.
     * The resource files will be passed through with no changes at all.
     * The class files will not contain identical bytes, since the unpacker
     * is free to change minor class file features such as constant pool order.
     * However, the class files will be semantically identical,
     * as specified in
     * <cite>The Java&trade; Virtual Machine Specification</cite>.
     * <p>
     * By default, the packer does not change the order of JAR elements.
     * Also, the modification time and deflation hint of each
     * JAR element is passed unchanged.
     * (Any other ZIP-archive information, such as extra attributes
     * giving Unix file permissions, are lost.)
     * <p>
     * Note that packing and unpacking a JAR will in general alter the
     * bytewise contents of classfiles in the JAR.  This means that packing
     * and unpacking will in general invalidate any digital signatures
     * which rely on bytewise images of JAR elements.  In order both to sign
     * and to pack a JAR, you must first pack and unpack the JAR to
     * "normalize" it, then compute signatures on the unpacked JAR elements,
     * and finally repack the signed JAR.
     * Both packing steps should
     * use precisely the same options, and the segment limit may also
     * need to be set to "-1", to prevent accidental variation of segment
     * boundaries as class file sizes change slightly.
     * <p>
     * (Here's why this works:  Any reordering the packer does
     * of any classfile structures is idempotent, so the second packing
     * does not change the orderings produced by the first packing.
     * Also, the unpacker is guaranteed by the JSR 200 specification
     * to produce a specific bytewise image for any given transmission
     * ordering of archive elements.)
     * <p>
     * In order to maintain backward compatibility, the pack file's version is
     * set to accommodate the class files present in the input JAR file. In
     * other words, the pack file version will be the latest, if the class files
     * are the latest and conversely the pack file version will be the oldest
     * if the class file versions are also the oldest. For intermediate class
     * file versions the corresponding pack file version will be used.
     * For example:
     *    If the input JAR-files are solely comprised of 1.5  (or  lesser)
     * class files, a 1.5 compatible pack file is  produced. This will also be
     * the case for archives that have no class files.
     *    If the input JAR-files contains a 1.6 class file, then the pack file
     * version will be set to 1.6.
     * <p>
     * Note: Unless otherwise noted, passing a <tt>null</tt> argument to a
     * constructor or method in this class will cause a {@link NullPointerException}
     * to be thrown.
     * <p>
     * @since 1.5
     */
    public interface Packer {
        /**
         * This property is a numeral giving the estimated target size N
         * (in bytes) of each archive segment.
         * If a single input file requires more than N bytes,
         * it will be given its own archive segment.
         * <p>
         * As a special case, a value of -1 will produce a single large
         * segment with all input files, while a value of 0 will
         * produce one segment for each class.
         * Larger archive segments result in less fragmentation and
         * better compression, but processing them requires more memory.
         * <p>
         * The size of each segment is estimated by counting the size of each
         * input file to be transmitted in the segment, along with the size
         * of its name and other transmitted properties.
         * <p>
         * The default is -1, which means the packer will always create a single
         * segment output file. In cases where extremely large output files are
         * generated, users are strongly encouraged to use segmenting or break
         * up the input file into smaller JARs.
         * <p>
         * A 10Mb JAR packed without this limit will
         * typically pack about 10% smaller, but the packer may require
         * a larger Java heap (about ten times the segment limit).
         */
        String SEGMENT_LIMIT    = "pack.segment.limit";

        /**
         * If this property is set to {@link #TRUE}, the packer will transmit
         * all elements in their original order within the source archive.
         * <p>
         * If it is set to {@link #FALSE}, the packer may reorder elements,
         * and also remove JAR directory entries, which carry no useful
         * information for Java applications.
         * (Typically this enables better compression.)
         * <p>
         * The default is {@link #TRUE}, which preserves the input information,
         * but may cause the transmitted archive to be larger than necessary.
         */
        String KEEP_FILE_ORDER = "pack.keep.file.order";


        /**
         * If this property is set to a single decimal digit, the packer will
         * use the indicated amount of effort in compressing the archive.
         * Level 1 may produce somewhat larger size and faster compression speed,
         * while level 9 will take much longer but may produce better compression.
         * <p>
         * The special value 0 instructs the packer to copy through the
         * original JAR file directly, with no compression.  The JSR 200
         * standard requires any unpacker to understand this special case
         * as a pass-through of the entire archive.
         * <p>
         * The default is 5, investing a modest amount of time to
         * produce reasonable compression.
         */
        String EFFORT           = "pack.effort";

        /**
         * If this property is set to {@link #TRUE} or {@link #FALSE}, the packer
         * will set the deflation hint accordingly in the output archive, and
         * will not transmit the individual deflation hints of archive elements.
         * <p>
         * If this property is set to the special string {@link #KEEP}, the packer
         * will attempt to determine an independent deflation hint for each
         * available element of the input archive, and transmit this hint separately.
         * <p>
         * The default is {@link #KEEP}, which preserves the input information,
         * but may cause the transmitted archive to be larger than necessary.
         * <p>
         * It is up to the unpacker implementation
         * to take action upon the hint to suitably compress the elements of
         * the resulting unpacked jar.
         * <p>
         * The deflation hint of a ZIP or JAR element indicates
         * whether the element was deflated or stored directly.
         */
        String DEFLATE_HINT     = "pack.deflate.hint";

        /**
         * If this property is set to the special string {@link #LATEST},
         * the packer will attempt to determine the latest modification time,
         * among all the available entries in the original archive or the latest
         * modification time of all the available entries in each segment.
         * This single value will be transmitted as part of the segment and applied
         * to all the entries in each segment, {@link #SEGMENT_LIMIT}.
         * <p>
         * This can marginally decrease the transmitted size of the
         * archive, at the expense of setting all installed files to a single
         * date.
         * <p>
         * If this property is set to the special string {@link #KEEP},
         * the packer transmits a separate modification time for each input
         * element.
         * <p>
         * The default is {@link #KEEP}, which preserves the input information,
         * but may cause the transmitted archive to be larger than necessary.
         * <p>
         * It is up to the unpacker implementation to take action to suitably
         * set the modification time of each element of its output file.
         * @see #SEGMENT_LIMIT
         */
        String MODIFICATION_TIME        = "pack.modification.time";

        /**
         * Indicates that a file should be passed through bytewise, with no
         * compression.  Multiple files may be specified by specifying
         * additional properties with distinct strings appended, to
         * make a family of properties with the common prefix.
         * <p>
         * There is no pathname transformation, except
         * that the system file separator is replaced by the JAR file
         * separator '/'.
         * <p>
         * The resulting file names must match exactly as strings with their
         * occurrences in the JAR file.
         * <p>
         * If a property value is a directory name, all files under that
         * directory will be passed also.
         * <p>
         * Examples:
         * <pre>{@code
         *     Map p = packer.properties();
         *     p.put(PASS_FILE_PFX+0, "mutants/Rogue.class");
         *     p.put(PASS_FILE_PFX+1, "mutants/Wolverine.class");
         *     p.put(PASS_FILE_PFX+2, "mutants/Storm.class");
         *     # Pass all files in an entire directory hierarchy:
         *     p.put(PASS_FILE_PFX+3, "police/");
         * }</pre>
         */
        String PASS_FILE_PFX            = "pack.pass.file.";

        /// Attribute control.

        /**
         * Indicates the action to take when a class-file containing an unknown
         * attribute is encountered.  Possible values are the strings {@link #ERROR},
         * {@link #STRIP}, and {@link #PASS}.
         * <p>
         * The string {@link #ERROR} means that the pack operation
         * as a whole will fail, with an exception of type <code>IOException</code>.
         * The string
         * {@link #STRIP} means that the attribute will be dropped.
         * The string
         * {@link #PASS} means that the whole class-file will be passed through
         * (as if it were a resource file) without compression, with  a suitable warning.
         * This is the default value for this property.
         * <p>
         * Examples:
         * <pre>{@code
         *     Map p = pack200.getProperties();
         *     p.put(UNKNOWN_ATTRIBUTE, ERROR);
         *     p.put(UNKNOWN_ATTRIBUTE, STRIP);
         *     p.put(UNKNOWN_ATTRIBUTE, PASS);
         * }</pre>
         */
        String UNKNOWN_ATTRIBUTE        = "pack.unknown.attribute";

        /**
         * When concatenated with a class attribute name,
         * indicates the format of that attribute,
         * using the layout language specified in the JSR 200 specification.
         * <p>
         * For example, the effect of this option is built in:
         * <code>pack.class.attribute.SourceFile=RUH</code>.
         * <p>
         * The special strings {@link #ERROR}, {@link #STRIP}, and {@link #PASS} are
         * also allowed, with the same meaning as {@link #UNKNOWN_ATTRIBUTE}.
         * This provides a way for users to request that specific attributes be
         * refused, stripped, or passed bitwise (with no class compression).
         * <p>
         * Code like this might be used to support attributes for JCOV:
         * <pre><code>
         *     Map p = packer.properties();
         *     p.put(CODE_ATTRIBUTE_PFX+"CoverageTable",       "NH[PHHII]");
         *     p.put(CODE_ATTRIBUTE_PFX+"CharacterRangeTable", "NH[PHPOHIIH]");
         *     p.put(CLASS_ATTRIBUTE_PFX+"SourceID",           "RUH");
         *     p.put(CLASS_ATTRIBUTE_PFX+"CompilationID",      "RUH");
         * </code></pre>
         * <p>
         * Code like this might be used to strip debugging attributes:
         * <pre><code>
         *     Map p = packer.properties();
         *     p.put(CODE_ATTRIBUTE_PFX+"LineNumberTable",    STRIP);
         *     p.put(CODE_ATTRIBUTE_PFX+"LocalVariableTable", STRIP);
         *     p.put(CLASS_ATTRIBUTE_PFX+"SourceFile",        STRIP);
         * </code></pre>
         */
        String CLASS_ATTRIBUTE_PFX      = "pack.class.attribute.";

        /**
         * When concatenated with a field attribute name,
         * indicates the format of that attribute.
         * For example, the effect of this option is built in:
         * <code>pack.field.attribute.Deprecated=</code>.
         * The special strings {@link #ERROR}, {@link #STRIP}, and
         * {@link #PASS} are also allowed.
         * @see #CLASS_ATTRIBUTE_PFX
         */
        String FIELD_ATTRIBUTE_PFX      = "pack.field.attribute.";

        /**
         * When concatenated with a method attribute name,
         * indicates the format of that attribute.
         * For example, the effect of this option is built in:
         * <code>pack.method.attribute.Exceptions=NH[RCH]</code>.
         * The special strings {@link #ERROR}, {@link #STRIP}, and {@link #PASS}
         * are also allowed.
         * @see #CLASS_ATTRIBUTE_PFX
         */
        String METHOD_ATTRIBUTE_PFX     = "pack.method.attribute.";

        /**
         * When concatenated with a code attribute name,
         * indicates the format of that attribute.
         * For example, the effect of this option is built in:
         * <code>pack.code.attribute.LocalVariableTable=NH[PHOHRUHRSHH]</code>.
         * The special strings {@link #ERROR}, {@link #STRIP}, and {@link #PASS}
         * are also allowed.
         * @see #CLASS_ATTRIBUTE_PFX
         */
        String CODE_ATTRIBUTE_PFX       = "pack.code.attribute.";

        /**
         * The unpacker's progress as a percentage, as periodically
         * updated by the unpacker.
         * Values of 0 - 100 are normal, and -1 indicates a stall.
         * Progress can be monitored by polling the value of this
         * property.
         * <p>
         * At a minimum, the unpacker must set progress to 0
         * at the beginning of a packing operation, and to 100
         * at the end.
         */
        String PROGRESS                 = "pack.progress";

        /** The string "keep", a possible value for certain properties.
         * @see #DEFLATE_HINT
         * @see #MODIFICATION_TIME
         */
        String KEEP  = "keep";

        /** The string "pass", a possible value for certain properties.
         * @see #UNKNOWN_ATTRIBUTE
         * @see #CLASS_ATTRIBUTE_PFX
         * @see #FIELD_ATTRIBUTE_PFX
         * @see #METHOD_ATTRIBUTE_PFX
         * @see #CODE_ATTRIBUTE_PFX
         */
        String PASS  = "pass";

        /** The string "strip", a possible value for certain properties.
         * @see #UNKNOWN_ATTRIBUTE
         * @see #CLASS_ATTRIBUTE_PFX
         * @see #FIELD_ATTRIBUTE_PFX
         * @see #METHOD_ATTRIBUTE_PFX
         * @see #CODE_ATTRIBUTE_PFX
         */
        String STRIP = "strip";

        /** The string "error", a possible value for certain properties.
         * @see #UNKNOWN_ATTRIBUTE
         * @see #CLASS_ATTRIBUTE_PFX
         * @see #FIELD_ATTRIBUTE_PFX
         * @see #METHOD_ATTRIBUTE_PFX
         * @see #CODE_ATTRIBUTE_PFX
         */
        String ERROR = "error";

        /** The string "true", a possible value for certain properties.
         * @see #KEEP_FILE_ORDER
         * @see #DEFLATE_HINT
         */
        String TRUE = "true";

        /** The string "false", a possible value for certain properties.
         * @see #KEEP_FILE_ORDER
         * @see #DEFLATE_HINT
         */
        String FALSE = "false";

        /** The string "latest", a possible value for certain properties.
         * @see #MODIFICATION_TIME
         */
        String LATEST = "latest";

        /**
         * Get the set of this engine's properties.
         * This set is a "live view", so that changing its
         * contents immediately affects the Packer engine, and
         * changes from the engine (such as progress indications)
         * are immediately visible in the map.
         *
         * <p>The property map may contain pre-defined implementation
         * specific and default properties.  Users are encouraged to
         * read the information and fully understand the implications,
         * before modifying pre-existing properties.
         * <p>
         * Implementation specific properties are prefixed with a
         * package name associated with the implementor, beginning
         * with <tt>com.</tt> or a similar prefix.
         * All property names beginning with <tt>pack.</tt> and
         * <tt>unpack.</tt> are reserved for use by this API.
         * <p>
         * Unknown properties may be ignored or rejected with an
         * unspecified error, and invalid entries may cause an
         * unspecified error to be thrown.
         *
         * <p>
         * The returned map implements all optional {@link SortedMap} operations
         * @return A sorted association of property key strings to property
         * values.
         */
        SortedMap<String,String> properties();

        /**
         * Takes a JarFile and converts it into a Pack200 archive.
         * <p>
         * Closes its input but not its output.  (Pack200 archives are appendable.)
         * @param in a JarFile
         * @param out an OutputStream
         * @exception IOException if an error is encountered.
         */
        void pack(JarFile in, OutputStream out) throws IOException ;

        /**
         * Takes a JarInputStream and converts it into a Pack200 archive.
         * <p>
         * Closes its input but not its output.  (Pack200 archives are appendable.)
         * <p>
         * The modification time and deflation hint attributes are not available,
         * for the JAR manifest file and its containing directory.
         *
         * @see #MODIFICATION_TIME
         * @see #DEFLATE_HINT
         * @param in a JarInputStream
         * @param out an OutputStream
         * @exception IOException if an error is encountered.
         */
        void pack(JarInputStream in, OutputStream out) throws IOException ;

        /**
         * Registers a listener for PropertyChange events on the properties map.
         * This is typically used by applications to update a progress bar.
         *
         * <p> The default implementation of this method does nothing and has
         * no side-effects.</p>
         *
         * <p><b>WARNING:</b> This method is omitted from the interface
         * declaration in all subset Profiles of Java SE that do not include
         * the {@code java.beans} package. </p>

         * @see #properties
         * @see #PROGRESS
         * @param listener  An object to be invoked when a property is changed.
         * @deprecated The dependency on {@code PropertyChangeListener} creates
         *             a significant impediment to future modularization of the
         *             Java platform. This method will be removed in a future
         *             release.
         *             Applications that need to monitor progress of the packer
         *             can poll the value of the {@link #PROGRESS PROGRESS}
         *             property instead.
         */
        @Deprecated
        default void addPropertyChangeListener(PropertyChangeListener listener) {
        }

        /**
         * Remove a listener for PropertyChange events, added by
         * the {@link #addPropertyChangeListener}.
         *
         * <p> The default implementation of this method does nothing and has
         * no side-effects.</p>
         *
         * <p><b>WARNING:</b> This method is omitted from the interface
         * declaration in all subset Profiles of Java SE that do not include
         * the {@code java.beans} package. </p>
         *
         * @see #addPropertyChangeListener
         * @param listener  The PropertyChange listener to be removed.
         * @deprecated The dependency on {@code PropertyChangeListener} creates
         *             a significant impediment to future modularization of the
         *             Java platform. This method will be removed in a future
         *             release.
         */
        @Deprecated
        default void removePropertyChangeListener(PropertyChangeListener listener) {
        }
    }

    /**
     * The unpacker engine converts the packed stream to a JAR file.
     * An instance of the engine can be obtained
     * using {@link #newUnpacker}.
     * <p>
     * Every JAR file produced by this engine will include the string
     * "<tt>PACK200</tt>" as a zip file comment.
     * This allows a deployer to detect if a JAR archive was packed and unpacked.
     * <p>
     * Note: Unless otherwise noted, passing a <tt>null</tt> argument to a
     * constructor or method in this class will cause a {@link NullPointerException}
     * to be thrown.
     * <p>
     * This version of the unpacker is compatible with all previous versions.
     * @since 1.5
     */
    public interface Unpacker {

        /** The string "keep", a possible value for certain properties.
         * @see #DEFLATE_HINT
         */
        String KEEP  = "keep";

        /** The string "true", a possible value for certain properties.
         * @see #DEFLATE_HINT
         */
        String TRUE = "true";

        /** The string "false", a possible value for certain properties.
         * @see #DEFLATE_HINT
         */
        String FALSE = "false";

        /**
         * Property indicating that the unpacker should
         * ignore all transmitted values for DEFLATE_HINT,
         * replacing them by the given value, {@link #TRUE} or {@link #FALSE}.
         * The default value is the special string {@link #KEEP},
         * which asks the unpacker to preserve all transmitted
         * deflation hints.
         */
        String DEFLATE_HINT      = "unpack.deflate.hint";



        /**
         * The unpacker's progress as a percentage, as periodically
         * updated by the unpacker.
         * Values of 0 - 100 are normal, and -1 indicates a stall.
         * Progress can be monitored by polling the value of this
         * property.
         * <p>
         * At a minimum, the unpacker must set progress to 0
         * at the beginning of a packing operation, and to 100
         * at the end.
         */
        String PROGRESS         = "unpack.progress";

        /**
         * Get the set of this engine's properties. This set is
         * a "live view", so that changing its
         * contents immediately affects the Packer engine, and
         * changes from the engine (such as progress indications)
         * are immediately visible in the map.
         *
         * <p>The property map may contain pre-defined implementation
         * specific and default properties.  Users are encouraged to
         * read the information and fully understand the implications,
         * before modifying pre-existing properties.
         * <p>
         * Implementation specific properties are prefixed with a
         * package name associated with the implementor, beginning
         * with <tt>com.</tt> or a similar prefix.
         * All property names beginning with <tt>pack.</tt> and
         * <tt>unpack.</tt> are reserved for use by this API.
         * <p>
         * Unknown properties may be ignored or rejected with an
         * unspecified error, and invalid entries may cause an
         * unspecified error to be thrown.
         *
         * @return A sorted association of option key strings to option values.
         */
        SortedMap<String,String> properties();

        /**
         * Read a Pack200 archive, and write the encoded JAR to
         * a JarOutputStream.
         * The entire contents of the input stream will be read.
         * It may be more efficient to read the Pack200 archive
         * to a file and pass the File object, using the alternate
         * method described below.
         * <p>
         * Closes its input but not its output.  (The output can accumulate more elements.)
         * @param in an InputStream.
         * @param out a JarOutputStream.
         * @exception IOException if an error is encountered.
         */
        void unpack(InputStream in, JarOutputStream out) throws IOException;

        /**
         * Read a Pack200 archive, and write the encoded JAR to
         * a JarOutputStream.
         * <p>
         * Does not close its output.  (The output can accumulate more elements.)
         * @param in a File.
         * @param out a JarOutputStream.
         * @exception IOException if an error is encountered.
         */
        void unpack(File in, JarOutputStream out) throws IOException;

        /**
         * Registers a listener for PropertyChange events on the properties map.
         * This is typically used by applications to update a progress bar.
         *
         * <p> The default implementation of this method does nothing and has
         * no side-effects.</p>
         *
         * <p><b>WARNING:</b> This method is omitted from the interface
         * declaration in all subset Profiles of Java SE that do not include
         * the {@code java.beans} package. </p>
         *
         * @see #properties
         * @see #PROGRESS
         * @param listener  An object to be invoked when a property is changed.
         * @deprecated The dependency on {@code PropertyChangeListener} creates
         *             a significant impediment to future modularization of the
         *             Java platform. This method will be removed in a future
         *             release.
         *             Applications that need to monitor progress of the
         *             unpacker can poll the value of the {@link #PROGRESS
         *             PROGRESS} property instead.
         */
        @Deprecated
        default void addPropertyChangeListener(PropertyChangeListener listener) {
        }

        /**
         * Remove a listener for PropertyChange events, added by
         * the {@link #addPropertyChangeListener}.
         *
         * <p> The default implementation of this method does nothing and has
         * no side-effects.</p>
         *
         * <p><b>WARNING:</b> This method is omitted from the interface
         * declaration in all subset Profiles of Java SE that do not include
         * the {@code java.beans} package. </p>
         *
         * @see #addPropertyChangeListener
         * @param listener  The PropertyChange listener to be removed.
         * @deprecated The dependency on {@code PropertyChangeListener} creates
         *             a significant impediment to future modularization of the
         *             Java platform. This method will be removed in a future
         *             release.
         */
        @Deprecated
        default void removePropertyChangeListener(PropertyChangeListener listener) {
        }
    }

    // Private stuff....

    private static final String PACK_PROVIDER = "java.util.jar.Pack200.Packer";
    private static final String UNPACK_PROVIDER = "java.util.jar.Pack200.Unpacker";

    private static Class<?> packerImpl;
    private static Class<?> unpackerImpl;

    private synchronized static Object newInstance(String prop) {
        String implName = "(unknown)";
        try {
            Class<?> impl = (PACK_PROVIDER.equals(prop))? packerImpl: unpackerImpl;
            if (impl == null) {
                // The first time, we must decide which class to use.
                implName = java.security.AccessController.doPrivileged(
                    new sun.security.action.GetPropertyAction(prop,""));
                if (implName != null && !implName.equals(""))
                    impl = Class.forName(implName);
                else if (PACK_PROVIDER.equals(prop))
                    impl = com.sun.java.util.jar.pack.PackerImpl.class;
                else
                    impl = com.sun.java.util.jar.pack.UnpackerImpl.class;
            }
            // We have a class.  Now instantiate it.
            return impl.newInstance();
        } catch (ClassNotFoundException e) {
            throw new Error("Class not found: " + implName +
                                ":\ncheck property " + prop +
                                " in your properties file.", e);
        } catch (InstantiationException e) {
            throw new Error("Could not instantiate: " + implName +
                                ":\ncheck property " + prop +
                                " in your properties file.", e);
        } catch (IllegalAccessException e) {
            throw new Error("Cannot access class: " + implName +
                                ":\ncheck property " + prop +
                                " in your properties file.", e);
        }
    }

}
