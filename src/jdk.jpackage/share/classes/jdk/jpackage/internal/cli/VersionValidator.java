/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.math.BigInteger;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import jdk.jpackage.internal.model.AppImageBundleType;
import jdk.jpackage.internal.model.BundleType;
import jdk.jpackage.internal.model.BundleVersion;
import jdk.jpackage.internal.model.DottedVersion;
import jdk.jpackage.internal.model.PackageType;
import jdk.jpackage.internal.model.StandardPackageType;

/**
 * Validator for bundle version.
 */
@FunctionalInterface
interface VersionValidator {

    Optional<CannedException> validate(BundleVersion ver);

    static VersionValidator create(BundleType type) {
        return switch (type) {
            case AppImageBundleType appImageType -> {
                yield switch (appImageType) {
                    case LINUX_APP_IMAGE -> {
                        yield validateNotEmpty(Optional.empty());
                    }
                    case MAC_APP_IMAGE -> {
                        yield create(VersionValidator::validateCFBundleVersion, Internal.MAC_ADVICE);
                    }
                    case WIN_APP_IMAGE -> {
                        yield create(VersionValidator::validateWindowsVersion, Internal.WIN_ADVICE);
                    }
                };
            }
            case StandardPackageType pkgType -> {
                yield switch (pkgType) {
                    case WIN_EXE, WIN_MSI -> {
                        yield create(VersionValidator::validateMsiVersion, Internal.MSI_ADVICE);
                    }
                    case MAC_DMG, MAC_PKG -> {
                        yield create(VersionValidator::validateCFBundleVersion, Internal.MAC_ADVICE);
                    }
                    case LINUX_DEB -> {
                        yield VersionValidator::validateDebVersion;
                    }
                    case LINUX_RPM -> {
                        yield VersionValidator::validateRpmVersion;
                    }
                };
            }
            case PackageType pkgType -> {
                yield validateNotEmpty(Optional.empty());
            }
        };
    }

    static final class Internal {

        private Internal() {}

        record IntegerValidator(Optional<Integer> min, Optional<Integer> max, String key, int componentIdx) {

            IntegerValidator {
                Objects.requireNonNull(min);
                Objects.requireNonNull(max);
                Objects.requireNonNull(key);
            }

            boolean validate(BigInteger component) {

                if (min.isPresent() && BigInteger.valueOf(min.orElseThrow()).compareTo(component) > 0) {
                    return false;
                }

                if (max.isPresent() && BigInteger.valueOf(max.orElseThrow()).compareTo(component) < 0) {
                    return false;
                }

                return true;
            }
        }

        private static final CannedFormattedMessage INVALID_VERSION =
                CannedFormattedMessage.build("error.parameter-not-version").optionValue().optionName().bundleTypeName().create();

        private static final CannedFormattedMessage RPM_ADVICE =
                CannedFormattedMessage.build("error.parameter-not-rpm-version.advice").bundleTypeName().create();

        private static final CannedFormattedMessage DEB_ADVICE =
                CannedFormattedMessage.build("error.parameter-not-deb-version.advice").bundleTypeName().create();

        private static final CannedFormattedMessage MSI_ADVICE =
                CannedFormattedMessage.build("error.parameter-not-msi-version.advice").bundleTypeName().create();

        private static final CannedFormattedMessage WIN_ADVICE =
                CannedFormattedMessage.build("error.parameter-not-win-version.advice").bundleTypeName().create();

        private static final CannedFormattedMessage MAC_ADVICE =
                CannedFormattedMessage.build("error.parameter-not-mac-version.advice").bundleTypeName().create();

        // https://rpm.org/docs/6.0.x/man/rpm-version.7#Version
        // "Version" tag in the https://rpm.org/docs/6.0.x/manual/spec.html#preamble
        private static final Predicate<String> IS_LINUX_RPM_VERSION = Pattern.compile("(?=.*\\p{Alnum})[\\p{Alnum}._+~^]+").asMatchPredicate();

        // https://man7.org/linux/man-pages/man7/deb-version.7.html
        // Predicate for "[epoch:]upstream-version" version value (exclude optional "-debian-revision")
        private static final Predicate<String> IS_LINUX_DEB_VERSION = Pattern.compile("(?:\\d+:\\d[\\p{Alnum}.+:~]*|\\d[\\p{Alnum}.+~]*)").asMatchPredicate();

    }

    private static VersionValidator create(Function<DottedVersion, Optional<CannedException>> func, CannedFormattedMessage advice) {
        Objects.requireNonNull(func);
        Objects.requireNonNull(advice);

        VersionValidator validator = ver -> {
            var dottedVer = ver.asDottedVersion();
            if (dottedVer.isEmpty()) {
                return Optional.of(new CannedException(Internal.INVALID_VERSION, advice));
            } else {
                return func.apply(dottedVer.orElseThrow());
            }
        };

        var notEmptyValidator = validateNotEmpty(Optional.of(advice));

        return ver -> {
            return notEmptyValidator.validate(ver).or(() -> {
                return validator.validate(ver);
            });
        };
    }

    private static VersionValidator validateNotEmpty(Optional<CannedFormattedMessage> advice) {
        Objects.requireNonNull(advice);

        return ver -> {
            if (ver.toString().isEmpty()) {
                return Optional.of(new CannedException(Internal.INVALID_VERSION, advice));
            } else {
                return Optional.empty();
            }
        };
    }

    private static Optional<CannedException> validateCFBundleVersion(DottedVersion ver) {

        //
        // Validates the given version as a value of the CFBundleVersion property.
        //
        // It should be a string comprised of non-negative, period-separated integers.
        // macOS will ignore anything after 3 components, but it is acceptable to have more then 3 components.
        //
        // See https://developer.apple.com/documentation/bundleresources/information-property-list/cfbundleversion
        //
        // There is another, more detailed specification for the value of this property at
        // https://developer.apple.com/library/archive/documentation/General/Reference/InfoPlistKeyReference/Articles/CoreFoundationKeys.html#//apple_ref/doc/uid/20001431-102364
        // It suggests the first component must be at most four digits.
        // But this contradicts an empirical observation reported at https://medium.com/@ranhiru/maximum-value-for-cfbundleversion-f4eeede4cf62
        // that suggests the maximum length of the value should be 18 characters.
        // So it can be "999999999999999999" or "9999999999999999.9".
        //
        // Because of this ambiguity, we don't want to impose tight limitations;
        // instead, we simply restrict it to a numeric version string.
        //

        if (!ver.getUnprocessedSuffix().isEmpty()) {
            return Optional.of(new CannedException(Internal.INVALID_VERSION, Internal.MAC_ADVICE));
        } else {
            return Optional.empty();
        }
    }

    private static Optional<CannedException> validateWindowsVersion(DottedVersion ver) {

        //
        // Validates the given version as a value of the FILEVERSION component of VERSIONINFO resource.
        //
        // See https://learn.microsoft.com/en-us/windows/win32/menurc/versioninfo-resource
        //

        if (!ver.getUnprocessedSuffix().isEmpty()) {
            return Optional.of(new CannedException(Internal.INVALID_VERSION, Internal.WIN_ADVICE));
        }

        if (ver.getComponentsCount() > 4) {
            return Optional.of(new CannedException(Internal.INVALID_VERSION, Internal.WIN_ADVICE));
        }

        String[] keys = new String[] {
                "error.parameter-not-version.major-out-of-range",
                "error.parameter-not-version.minor-out-of-range",
        };

        var err = validateComponents(ver, Stream.concat(
                buildAndRevisionValidators(),
                IntStream.range(0, keys.length).mapToObj(idx -> {
                    return new Internal.IntegerValidator(Optional.empty(), Optional.of(65535), keys[idx], idx);
                })).toArray(Internal.IntegerValidator[]::new));

        return err.map(v -> {
            return new CannedException(v, Internal.WIN_ADVICE);
        });
    }

    private static Optional<CannedException> validateMsiVersion(DottedVersion ver) {

        //
        // Validates the given version as a value of ProductVersion MSI property.
        //
        // See https://learn.microsoft.com/en-us/windows/win32/msi/productversion
        //
        // jpackage supports an extended format for this property with an additional fourth version component.
        //
        // This component has no intrinsic value limit. However, we apply the same
        // restriction as for the Windows application image version for consistency.
        // When an MSI is wrapped in an EXE, its version is used as the EXE version and
        // must therefore be valid for the FILEVERSION field of the VERSIONINFO resource.
        //
        // In general, a valid MSI version must also be valid for a Windows application
        // image, but a valid Windows application image version may be an invalid MSI version.
        //

        if (!ver.getUnprocessedSuffix().isEmpty()) {
            return Optional.of(new CannedException(Internal.INVALID_VERSION, Internal.MSI_ADVICE));
        }

        if (ver.getComponentsCount() < 2 || ver.getComponentsCount() > 4) {
            return Optional.of(new CannedException(Internal.INVALID_VERSION, Internal.MSI_ADVICE));
        }

        String[] keys = new String[] {
                "error.parameter-not-version.major-out-of-range",
                "error.parameter-not-version.minor-out-of-range",
        };

        var err = validateComponents(ver, Stream.concat(
                buildAndRevisionValidators(),
                IntStream.range(0, keys.length).mapToObj(idx -> {
                    return new Internal.IntegerValidator(Optional.empty(), Optional.of(255), keys[idx], idx);
                })).toArray(Internal.IntegerValidator[]::new));

        return err.map(v -> {
            return new CannedException(v, Internal.MSI_ADVICE);
        });
    }

    private static Stream<Internal.IntegerValidator> buildAndRevisionValidators() {
        return Stream.of(
                new Internal.IntegerValidator(Optional.empty(), Optional.of(65535), "error.parameter-not-version.build-out-of-range", 2),
                new Internal.IntegerValidator(Optional.empty(), Optional.of(65535), "error.parameter-not-version.revision-out-of-range", 3)
        );
    }

    private static Optional<CannedFormattedMessage> validateComponents(
            DottedVersion ver, Internal.IntegerValidator... componentValidators) {

        Objects.requireNonNull(ver);

        var components = ver.getComponents();

        return Stream.of(componentValidators).sorted(Comparator.comparing(Internal.IntegerValidator::componentIdx)).filter(validator -> {
            return validator.componentIdx() < components.length;
        }).map(validator -> {
            var component = components[validator.componentIdx()];
            var valid = validator.validate(component);
            if (!valid) {
                return CannedFormattedMessage.build(validator.key())
                        .optionValue()
                        .optionName()
                        .bundleTypeName()
                        .str(component.toString())
                        .create();
            } else {
                return (CannedFormattedMessage)null;
            }
        }).filter(Objects::nonNull).findFirst();
    }

    private static Optional<CannedException> validateRpmVersion(BundleVersion ver) {
        if (!Internal.IS_LINUX_RPM_VERSION.test(ver.toString())) {
            return Optional.of(new CannedException(Internal.INVALID_VERSION, Internal.RPM_ADVICE));
        } else {
            return Optional.empty();
        }
    }

    private static Optional<CannedException> validateDebVersion(BundleVersion ver) {
        if (!Internal.IS_LINUX_DEB_VERSION.test(ver.toString())) {
            return Optional.of(new CannedException(Internal.INVALID_VERSION, Internal.DEB_ADVICE));
        } else {
            return Optional.empty();
        }
    }

}
