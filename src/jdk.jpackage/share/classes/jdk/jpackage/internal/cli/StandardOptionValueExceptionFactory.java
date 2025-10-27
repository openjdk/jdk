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

import java.nio.file.Path;
import java.util.function.UnaryOperator;
import jdk.jpackage.internal.cli.OptionValueExceptionFactory.ArgumentsMapper;
import jdk.jpackage.internal.cli.OptionValueExceptionFactory.StandardArgumentsMapper;
import jdk.jpackage.internal.model.JPackageException;

final class StandardOptionValueExceptionFactory {

    static OptionValueExceptionFactory<JPackageException> forMessageWithOptionValue(Path propertyFile) {
        return forMessageWithOptionValue(appendPath(propertyFile)).printOptionPrefix(false).create();
    }

    static OptionValueExceptionFactory<JPackageException> forMessageWithOptionValueAndName(Path propertyFile) {
        return forMessageWithOptionValueAndName(appendPath(propertyFile)).printOptionPrefix(false).create();
    }

    static OptionValueExceptionFactory<JPackageException> forFixedMessage(Path propertyFile) {
        return forFixedMessage(appendPath(propertyFile)).printOptionPrefix(false).create();
    }

    private StandardOptionValueExceptionFactory() {
    }

    private static UnaryOperator<ArgumentsMapper> appendPath(Path path) {
        return argumentsMapper -> {
            return ArgumentsMapper.appendArguments(argumentsMapper, path);
        };
    }

    private static OptionValueExceptionFactory.Builder<JPackageException> forMessageWithOptionValue(UnaryOperator<ArgumentsMapper> mapper) {
        return OptionValueExceptionFactory.build(JPackageException::new)
                .formatArgumentsTransformer(mapper.apply(StandardArgumentsMapper.VALUE));
    }

    private static OptionValueExceptionFactory.Builder<JPackageException> forMessageWithOptionValueAndName(UnaryOperator<ArgumentsMapper> mapper) {
        return OptionValueExceptionFactory.build(JPackageException::new)
                .formatArgumentsTransformer(mapper.apply(StandardArgumentsMapper.VALUE_AND_NAME));
    }

    private static OptionValueExceptionFactory.Builder<JPackageException> forFixedMessage(UnaryOperator<ArgumentsMapper> mapper) {
        return OptionValueExceptionFactory.build(JPackageException::new)
                .formatArgumentsTransformer(mapper.apply(StandardArgumentsMapper.NONE));
    }

    static final OptionValueExceptionFactory<JPackageException> ERROR_WITH_VALUE =
            forMessageWithOptionValue(UnaryOperator.identity()).create();

    static final OptionValueExceptionFactory<JPackageException> ERROR_WITHOUT_CONTEXT =
            forFixedMessage(UnaryOperator.identity()).create();

    static final OptionValueExceptionFactory<JPackageException> ERROR_WITH_VALUE_AND_OPTION_NAME =
            forMessageWithOptionValueAndName(UnaryOperator.identity()).create();
}
