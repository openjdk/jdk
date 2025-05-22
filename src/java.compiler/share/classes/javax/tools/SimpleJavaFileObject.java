/*
 * Copyright (c) 2005, 2024, Oracle and/or its affiliates. All rights reserved.
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

package javax.tools;

import java.io.*;
import java.net.URI;
import java.nio.CharBuffer;
import java.util.Objects;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;

/**
 * Provides simple implementations for most methods in JavaFileObject.
 * This class is designed to be subclassed and used as a basis for
 * JavaFileObject implementations.  Subclasses can override the
 * implementation and specification of any method of this class as
 * long as the general contract of JavaFileObject is obeyed.
 *
 * @since 1.6
 */
public class SimpleJavaFileObject implements JavaFileObject {
    /**
     * A URI for this file object.
     */
    protected final URI uri;

    /**
     * The kind of this file object.
     */
    protected final Kind kind;

    /**
     * Construct a SimpleJavaFileObject of the given kind and with the
     * given URI.
     *
     * @param uri  the URI for this file object
     * @param kind the kind of this file object
     */
    protected SimpleJavaFileObject(URI uri, Kind kind) {
        Objects.requireNonNull(uri);
        Objects.requireNonNull(kind);
        if (uri.getPath() == null)
            throw new IllegalArgumentException("URI must have a path: " + uri);
        this.uri = uri;
        this.kind = kind;
    }

    @Override
    public URI toUri() {
        return uri;
    }

    @Override
    public String getName() {
        return toUri().getPath();
    }

    /**
     * {@inheritDoc FileObject}
     * @implSpec
     * This implementation always throws {@linkplain
     * UnsupportedOperationException}.
     */
    @Override
    public InputStream openInputStream() throws IOException {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc FileObject}
     * @implSpec
     * This implementation always throws {@linkplain
     * UnsupportedOperationException}.
     */
    @Override
    public OutputStream openOutputStream() throws IOException {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc FileObject}
     * @implSpec
     * This implementation wraps the result of {@link #getCharContent}
     * in a {@link Reader}.
     *
     * @param  ignoreEncodingErrors {@inheritDoc}
     * @return a Reader wrapping the result of getCharContent
     * @throws IllegalStateException {@inheritDoc}
     * @throws UnsupportedOperationException {@inheritDoc}
     * @throws IOException {@inheritDoc}
     */
    @Override
    public Reader openReader(boolean ignoreEncodingErrors) throws IOException {
        CharSequence charContent = getCharContent(ignoreEncodingErrors);
        if (charContent == null)
            throw new UnsupportedOperationException();
        if (charContent instanceof CharBuffer buffer && buffer.hasArray()) {
            return new CharArrayReader(buffer.array());
        }
        return new StringReader(charContent.toString());
    }

    /**
     * {@inheritDoc FileObject}
     * @implSpec
     * This implementation always throws {@linkplain
     * UnsupportedOperationException}.
     */
    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc FileObject}
     * @implSpec
     * This implementation wraps the result of {@link
     * #openOutputStream} in a {@link Writer}.
     *
     * @return a Writer wrapping the result of openOutputStream
     * @throws IllegalStateException {@inheritDoc}
     * @throws UnsupportedOperationException {@inheritDoc}
     * @throws IOException {@inheritDoc}
     */
    @Override
    public Writer openWriter() throws IOException {
        return new OutputStreamWriter(openOutputStream());
    }

    /**
     * {@inheritDoc FileObject}
     * @implSpec
     * This implementation returns {@code 0L}.
     *
     * @return {@code 0L}
     */
    @Override
    public long getLastModified() {
        return 0L;
    }

    /**
     * {@inheritDoc FileObject}
     * @implSpec
     * This implementation does nothing.
     *
     * @return {@code false}
     */
    @Override
    public boolean delete() {
        return false;
    }

    /**
     * @return {@code this.kind}
     */
    @Override
    public Kind getKind() {
        return kind;
    }

    /**
     * {@inheritDoc JavaFileObject}
     * @implSpec
     * This implementation compares the path of its URI to the given
     * simple name.  This method returns true if the given kind is
     * equal to the kind of this object, and if the path is equal to
     * {@code simpleName + kind.extension} or if it ends with {@code
     * "/" + simpleName + kind.extension}.
     *
     * <p>This method calls {@link #getKind} and {@link #toUri} and
     * does not access the fields {@link #uri} and {@link #kind}
     * directly.
     */
    @Override
    public boolean isNameCompatible(String simpleName, Kind kind) {
        String baseName = simpleName + kind.extension;
        return kind.equals(getKind())
            && (baseName.equals(toUri().getPath())
                || toUri().getPath().endsWith("/" + baseName));
    }

    /**
     * {@inheritDoc JavaFileObject}
     * @implSpec
     * This implementation returns {@code null}.
     */
    @Override
    public NestingKind getNestingKind() { return null; }

    /**
     * {@inheritDoc JavaFileObject}
     * @implSpec
     * This implementation returns {@code null}.
     */
    @Override
    public Modifier getAccessLevel()  { return null; }

    @Override
    public String toString() {
        return getClass().getName() + "[" + toUri() + "]";
    }

    /**
     * Creates a {@link JavaFileObject} which represents the given source content.
     *
     * <p>The provided {@code uri} will be returned from {@link #toUri()}.
     * The provided {@code content} will be returned from {@link #getCharContent(boolean)}.
     * The {@link #getKind()} method will return {@link Kind#SOURCE}.
     *
     * <p>All other methods will behave as described in the documentation in this class,
     * as if the constructor is called with {@code uri} and {@code Kind.SOURCE}.
     *
     * <p>This method can be, for example, used to compile an in-memory String
     * to a set of classfile in a target directory:
     * {@snippet lang="java":
     *      var code = """
     *                 public class CompiledCode {}
     *                 """;
     *      var compiler = ToolProvider.getSystemJavaCompiler();
     *      var targetDirectory = "...";
     *      var task = compiler.getTask(null,
     *                                  null,
     *                                  null,
     *                                  List.of("-d", targetDirectory),
     *                                  null,
     *                                  List.of(SimpleJavaFileObject.forSource(URI.create("CompiledCode.java"), code)));
     *      if (!task.call()) {
     *          throw new IllegalStateException("Compilation failed!");
     *      }
     * }
     *
     * @param uri that should be used for the resulting {@code JavaFileObject}
     * @param content the content of the {@code JavaFileObject}
     * @return a {@code JavaFileObject} representing the given source content.
     * @since 23
     */
    public static JavaFileObject forSource(URI uri, String content) {
        return new SimpleJavaFileObject(uri, Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return content;
            }
        };
    }

}
