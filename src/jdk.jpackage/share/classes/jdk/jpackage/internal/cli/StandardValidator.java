/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.internal.cli;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import jdk.jpackage.internal.cli.Validator.ValidatingConsumerException;

final class StandardValidator {

    private StandardValidator() {
    }

    static final Predicate<Path> IS_DIRECTORY = Files::isDirectory;

    static final Predicate<Path> IS_EXISTENT_NOT_DIRECTORY = path -> {
        return Files.exists(path) && !Files.isDirectory(path);
    };

    static final Predicate<Path> IS_DIRECTORY_OR_NON_EXISTENT = path -> {
        return !Files.exists(path) || Files.isDirectory(path);
    };

    static final Consumer<Path> IS_DIRECTORY_EMPTY_OR_NON_EXISTENT = path -> {
        if (Files.exists(path)) {
            if (Files.isDirectory(path)) {
                try (var dirContents = Files.list(path)) {
                    if (dirContents.findAny().isPresent()) {
                        throw new ValidatingConsumerException(new DirectoryNotEmptyException(path.toString()));
                    }
                } catch (IOException ex) {
                    throw new ValidatingConsumerException(new DirectoryListingIOException(ex));
                }
            } else {
                throw new ValidatingConsumerException(new NotDirectoryException(path.toString()));
            }
        }
    };

    static final Consumer<String> IS_URL = url -> {
        try {
            new URI(url);
        } catch (URISyntaxException ex) {
            throw new ValidatingConsumerException(ex);
        }
    };

    // Copied from DeployParams.validateName()
    static final Predicate<String> IS_NAME_VALID = s -> {
        if (s.length() == 0 || s.indexOf('\\') != -1 || s.indexOf('/') != -1 || s.isBlank() || !s.equals(s.trim())) {
            return false;
        }

        try {
            // name must be valid path element for this file system
            Path p = Path.of(s);
            // and it must be a single name element in a path
            if (p.getNameCount() != 1) {
                return false;
            }
        } catch (InvalidPathException ipe) {
            return false;
        }

        for (int i = 0; i < s.length(); i++) {
            char a = s.charAt(i);
            // We check for ASCII codes first which we accept. If check fails,
            // check if it is acceptable extended ASCII or unicode character.
            if (a < ' ' || a > '~') {
                // Accept anything else including special chars like copyright
                // symbols. Note: space will be included by ASCII check above,
                // but other whitespace like tabs or new line will be rejected.
                if (Character.isISOControl(a)|| Character.isWhitespace(a)) {
                    return false;
                }
            } else if (a == '"' || a == '%') {
                return false;
            }
        }

        return true;
    };


    static final class DirectoryListingIOException extends RuntimeException{

        DirectoryListingIOException(IOException cause) {
            super(Objects.requireNonNull(cause));
        }

        private static final long serialVersionUID = 1L;
    }
}
