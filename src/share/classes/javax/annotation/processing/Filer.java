/*
 * Copyright (c) 2005, 2006, Oracle and/or its affiliates. All rights reserved.
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

package javax.annotation.processing;

import javax.tools.JavaFileManager;
import javax.tools.*;
import javax.lang.model.element.Element;
import java.io.IOException;

/**
 * This interface supports the creation of new files by an annotation
 * processor.  Files created in this way will be known to the
 * annotation processing tool implementing this interface, better
 * enabling the tool to manage them.  Source and class files so
 * created will be {@linkplain RoundEnvironment#getRootElements
 * considered for processing} by the tool in a subsequent {@linkplain
 * RoundEnvironment round of processing} after the {@code close}
 * method has been called on the {@code Writer} or {@code
 * OutputStream} used to write the contents of the file.
 *
 * Three kinds of files are distinguished: source files, class files,
 * and auxiliary resource files.
 *
 * <p> There are two distinguished supported locations (subtrees
 * within the logical file system) where newly created files are
 * placed: one for {@linkplain
 * javax.tools.StandardLocation#SOURCE_OUTPUT new source files}, and
 * one for {@linkplain javax.tools.StandardLocation#CLASS_OUTPUT new
 * class files}.  (These might be specified on a tool's command line,
 * for example, using flags such as {@code -s} and {@code -d}.)  The
 * actual locations for new source files and new class files may or
 * may not be distinct on a particular run of the tool.  Resource
 * files may be created in either location.  The methods for reading
 * and writing resources take a relative name argument.  A relative
 * name is a non-null, non-empty sequence of path segments separated
 * by {@code '/'}; {@code '.'} and {@code '..'} are invalid path
 * segments.  A valid relative name must match the
 * &quot;path-rootless&quot; rule of <a
 * href="http://www.ietf.org/rfc/rfc3986.txt">RFC 3986</a>, section
 * 3.3.
 *
 * <p>The file creation methods take a variable number of arguments to
 * allow the <em>originating elements</em> to be provided as hints to
 * the tool infrastructure to better manage dependencies.  The
 * originating elements are the types or packages (representing {@code
 * package-info} files) which caused an annotation processor to
 * attempt to create a new file.  For example, if an annotation
 * processor tries to create a source file, {@code
 * GeneratedFromUserSource}, in response to processing
 *
 * <blockquote><pre>
 *  &#64;Generate
 *  public class UserSource {}
 * </pre></blockquote>
 *
 * the type element for {@code UserSource} should be passed as part of
 * the creation method call as in:
 *
 * <blockquote><pre>
 *      filer.createSourceFile("GeneratedFromUserSource",
 *                             eltUtils.getTypeElement("UserSource"));
 * </pre></blockquote>
 *
 * If there are no originating elements, none need to be passed.  This
 * information may be used in an incremental environment to determine
 * the need to rerun processors or remove generated files.
 * Non-incremental environments may ignore the originating element
 * information.
 *
 * <p> During each run of an annotation processing tool, a file with a
 * given pathname may be created only once.  If that file already
 * exists before the first attempt to create it, the old contents will
 * be deleted.  Any subsequent attempt to create the same file during
 * a run will throw a {@link FilerException}, as will attempting to
 * create both a class file and source file for the same type name or
 * same package name.  The {@linkplain Processor initial inputs} to
 * the tool are considered to be created by the zeroth round;
 * therefore, attempting to create a source or class file
 * corresponding to one of those inputs will result in a {@link
 * FilerException}.
 *
 * <p> In general, processors must not knowingly attempt to overwrite
 * existing files that were not generated by some processor.  A {@code
 * Filer} may reject attempts to open a file corresponding to an
 * existing type, like {@code java.lang.Object}.  Likewise, the
 * invoker of the annotation processing tool must not knowingly
 * configure the tool such that the discovered processors will attempt
 * to overwrite existing files that were not generated.
 *
 * <p> Processors can indicate a source or class file is generated by
 * including an {@link javax.annotation.Generated @Generated}
 * annotation.
 *
 * <p> Note that some of the effect of overwriting a file can be
 * achieved by using a <i>decorator</i>-style pattern.  Instead of
 * modifying a class directly, the class is designed so that either
 * its superclass is generated by annotation processing or subclasses
 * of the class are generated by annotation processing.  If the
 * subclasses are generated, the parent class may be designed to use
 * factories instead of public constructors so that only subclass
 * instances would be presented to clients of the parent class.
 *
 * @author Joseph D. Darcy
 * @author Scott Seligman
 * @author Peter von der Ah&eacute;
 * @since 1.6
 */
public interface Filer {
    /**
     * Creates a new source file and returns an object to allow
     * writing to it.  The file's name and path (relative to the
     * {@linkplain StandardLocation#SOURCE_OUTPUT root output location
     * for source files}) are based on the type to be declared in that
     * file.  If more than one type is being declared, the name of the
     * principal top-level type (the public one, for example) should
     * be used.  A source file can also be created to hold information
     * about a package, including package annotations.  To create a
     * source file for a named package, have {@code name} be the
     * package's name followed by {@code ".package-info"}; to create a
     * source file for an unnamed package, use {@code "package-info"}.
     *
     * <p> Note that to use a particular {@linkplain
     * java.nio.charset.Charset charset} to encode the contents of the
     * file, an {@code OutputStreamWriter} with the chosen charset can
     * be created from the {@code OutputStream} from the returned
     * object. If the {@code Writer} from the returned object is
     * directly used for writing, its charset is determined by the
     * implementation.  An annotation processing tool may have an
     * {@code -encoding} flag or analogous option for specifying this;
     * otherwise, it will typically be the platform's default
     * encoding.
     *
     * <p>To avoid subsequent errors, the contents of the source file
     * should be compatible with the {@linkplain
     * ProcessingEnvironment#getSourceVersion source version} being used
     * for this run.
     *
     * @param name  canonical (fully qualified) name of the principal type
     *          being declared in this file or a package name followed by
     *          {@code ".package-info"} for a package information file
     * @param originatingElements type or package elements causally
     * associated with the creation of this file, may be elided or
     * {@code null}
     * @return a {@code JavaFileObject} to write the new source file
     * @throws FilerException if the same pathname has already been
     * created, the same type has already been created, or the name is
     * not valid for a type
     * @throws IOException if the file cannot be created
     */
    JavaFileObject createSourceFile(CharSequence name,
                                    Element... originatingElements) throws IOException;

    /**
     * Creates a new class file, and returns an object to allow
     * writing to it.  The file's name and path (relative to the
     * {@linkplain StandardLocation#CLASS_OUTPUT root output location
     * for class files}) are based on the name of the type being
     * written.  A class file can also be created to hold information
     * about a package, including package annotations.  To create a
     * class file for a named package, have {@code name} be the
     * package's name followed by {@code ".package-info"}; creating a
     * class file for an unnamed package is not supported.
     *
     * <p>To avoid subsequent errors, the contents of the class file
     * should be compatible with the {@linkplain
     * ProcessingEnvironment#getSourceVersion source version} being used
     * for this run.
     *
     * @param name binary name of the type being written or a package name followed by
     *          {@code ".package-info"} for a package information file
     * @param originatingElements type or package elements causally
     * associated with the creation of this file, may be elided or
     * {@code null}
     * @return a {@code JavaFileObject} to write the new class file
     * @throws FilerException if the same pathname has already been
     * created, the same type has already been created, or the name is
     * not valid for a type
     * @throws IOException if the file cannot be created
     */
    JavaFileObject createClassFile(CharSequence name,
                                   Element... originatingElements) throws IOException;

    /**
     * Creates a new auxiliary resource file for writing and returns a
     * file object for it.  The file may be located along with the
     * newly created source files, newly created binary files, or
     * other supported location.  The locations {@link
     * StandardLocation#CLASS_OUTPUT CLASS_OUTPUT} and {@link
     * StandardLocation#SOURCE_OUTPUT SOURCE_OUTPUT} must be
     * supported.  The resource may be named relative to some package
     * (as are source and class files), and from there by a relative
     * pathname.  In a loose sense, the full pathname of the new file
     * will be the concatenation of {@code location}, {@code pkg}, and
     * {@code relativeName}.
     *
     * <p>Files created via this method are not registered for
     * annotation processing, even if the full pathname of the file
     * would correspond to the full pathname of a new source file
     * or new class file.
     *
     * @param location location of the new file
     * @param pkg package relative to which the file should be named,
     *          or the empty string if none
     * @param relativeName final pathname components of the file
     * @param originatingElements type or package elements causally
     * associated with the creation of this file, may be elided or
     * {@code null}
     * @return a {@code FileObject} to write the new resource
     * @throws IOException if the file cannot be created
     * @throws FilerException if the same pathname has already been
     * created
     * @throws IllegalArgumentException for an unsupported location
     * @throws IllegalArgumentException if {@code relativeName} is not relative
     */
   FileObject createResource(JavaFileManager.Location location,
                             CharSequence pkg,
                             CharSequence relativeName,
                             Element... originatingElements) throws IOException;

    /**
     * Returns an object for reading an existing resource.  The
     * locations {@link StandardLocation#CLASS_OUTPUT CLASS_OUTPUT}
     * and {@link StandardLocation#SOURCE_OUTPUT SOURCE_OUTPUT} must
     * be supported.
     *
     * @param location location of the file
     * @param pkg package relative to which the file should be searched,
     *          or the empty string if none
     * @param relativeName final pathname components of the file
     * @return an object to read the file
     * @throws FilerException if the same pathname has already been
     * opened for writing
     * @throws IOException if the file cannot be opened
     * @throws IllegalArgumentException for an unsupported location
     * @throws IllegalArgumentException if {@code relativeName} is not relative
     */
    FileObject getResource(JavaFileManager.Location location,
                           CharSequence pkg,
                           CharSequence relativeName) throws IOException;
}
