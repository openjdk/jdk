// Copyright 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 ****************************************************************************************
 * Copyright (C) 2009-2016, Google, Inc.; International Business Machines Corporation
 * and others. All Rights Reserved.
 ****************************************************************************************
 */
package jdk.internal.icu.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import jdk.internal.icu.impl.locale.LSR;
import jdk.internal.icu.impl.locale.LocaleDistance;
import jdk.internal.icu.impl.locale.XLikelySubtags;

/**
 * Immutable class that picks the best match between a user's desired locales and
 * an application's supported locales.
 *
 * <p>Example:
 * <pre>
 * LocaleMatcher matcher = LocaleMatcher.builder().setSupportedLocales("fr, en-GB, en").build();
 * Locale bestSupported = matcher.getBestLocale(Locale.US);  // "en"
 * </pre>
 *
 * <p>A matcher takes into account when languages are close to one another,
 * such as Danish and Norwegian,
 * and when regional variants are close, like en-GB and en-AU as opposed to en-US.
 *
 * <p>If there are multiple supported locales with the same (language, script, region)
 * likely subtags, then the current implementation returns the first of those locales.
 * It ignores variant subtags (except for pseudolocale variants) and extensions.
 * This may change in future versions.
 *
 * <p>For example, the current implementation does not distinguish between
 * de, de-DE, de-Latn, de-1901, de-u-co-phonebk.
 *
 * <p>If you prefer one equivalent locale over another, then provide only the preferred one,
 * or place it earlier in the list of supported locales.
 *
 * <p>Otherwise, the order of supported locales may have no effect on the best-match results.
 * The current implementation compares each desired locale with supported locales
 * in the following order:
 * 1. Default locale, if supported;
 * 2. CLDR "paradigm locales" like en-GB and es-419;
 * 3. other supported locales.
 * This may change in future versions.
 *
 * <p>Often a product will just need one matcher instance, built with the languages
 * that it supports. However, it may want multiple instances with different
 * default languages based on additional information, such as the domain.
 *
 * <p>This class is not intended for public subclassing.
 *
 * @author markdavis@google.com
 * @stable ICU 4.4
 */
@SuppressWarnings("deprecation")
public final class LocaleMatcher {
    private static final LSR UND_LSR = new LSR("und","","", LSR.EXPLICIT_LSR);
    // In ULocale, "und" and "" make the same object.
    private static final ULocale UND_ULOCALE = new ULocale("und");
    // In Locale, "und" and "" make different objects.
    private static final Locale UND_LOCALE = new Locale("und");
    private static final Locale EMPTY_LOCALE = new Locale("");

    // Activates debugging output to stderr with details of GetBestMatch.
    private static final boolean TRACE_MATCHER = false;

    private static abstract class LsrIterator implements Iterator<LSR> {
        int bestDesiredIndex = -1;

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

        public abstract void rememberCurrent(int desiredIndex);
    }

    /**
     * Builder option for whether the language subtag or the script subtag is most important.
     *
     * @see LocaleMatcher.Builder#setFavorSubtag(LocaleMatcher.FavorSubtag)
     * @stable ICU 65
     */
    public enum FavorSubtag {
        /**
         * Language differences are most important, then script differences, then region differences.
         * (This is the default behavior.)
         *
         * @stable ICU 65
         */
        LANGUAGE,
        /**
         * Makes script differences matter relatively more than language differences.
         *
         * @stable ICU 65
         */
        SCRIPT
    }

    /**
     * Builder option for whether all desired locales are treated equally or
     * earlier ones are preferred.
     *
     * @see LocaleMatcher.Builder#setDemotionPerDesiredLocale(LocaleMatcher.Demotion)
     * @stable ICU 65
     */
    public enum Demotion {
        /**
         * All desired locales are treated equally.
         *
         * @stable ICU 65
         */
        NONE,
        /**
         * Earlier desired locales are preferred.
         *
         * <p>From each desired locale to the next,
         * the distance to any supported locale is increased by an additional amount
         * which is at least as large as most region mismatches.
         * A later desired locale has to have a better match with some supported locale
         * due to more than merely having the same region subtag.
         *
         * <p>For example: <code>Supported={en, sv}  desired=[en-GB, sv]</code>
         * yields <code>Result(en-GB, en)</code> because
         * with the demotion of sv its perfect match is no better than
         * the region distance between the earlier desired locale en-GB and en=en-US.
         *
         * <p>Notes:
         * <ul>
         *   <li>In some cases, language and/or script differences can be as small as
         *       the typical region difference. (Example: sr-Latn vs. sr-Cyrl)
         *   <li>It is possible for certain region differences to be larger than usual,
         *       and larger than the demotion.
         *       (As of CLDR 35 there is no such case, but
         *        this is possible in future versions of the data.)
         * </ul>
         *
         * @stable ICU 65
         */
        REGION
    }

    /**
     * Builder option for whether to include or ignore one-way (fallback) match data.
     * The LocaleMatcher uses CLDR languageMatch data which includes fallback (oneway=true) entries.
     * Sometimes it is desirable to ignore those.
     *
     * <p>For example, consider a web application with the UI in a given language,
     * with a link to another, related web app.
     * The link should include the UI language, and the target server may also use
     * the client\u2019s Accept-Language header data.
     * The target server has its own list of supported languages.
     * One may want to favor UI language consistency, that is,
     * if there is a decent match for the original UI language, we want to use it,
     * but not if it is merely a fallback.
     *
     * @see LocaleMatcher.Builder#setDirection(LocaleMatcher.Direction)
     * @stable ICU 67
     */
    public enum Direction {
        /**
         * Locale matching includes one-way matches such as Breton\u2192French. (default)
         *
         * @stable ICU 67
         */
        WITH_ONE_WAY,
        /**
         * Locale matching limited to two-way matches including e.g. Danish\u2194Norwegian
         * but ignoring one-way matches.
         *
         * @stable ICU 67
         */
        ONLY_TWO_WAY
    }

    /**
     * Data for the best-matching pair of a desired and a supported locale.
     *
     * @stable ICU 65
     */
    public static final class Result {
        private final ULocale desiredULocale;
        private final ULocale supportedULocale;
        private final Locale desiredLocale;
        private final Locale supportedLocale;
        private final int desiredIndex;
        private final int supportedIndex;

        private Result(ULocale udesired, ULocale usupported,
                Locale desired, Locale supported,
                int desIndex, int suppIndex) {
            desiredULocale = udesired;
            supportedULocale = usupported;
            desiredLocale = desired;
            supportedLocale = supported;
            desiredIndex = desIndex;
            supportedIndex = suppIndex;
        }

        /**
         * Returns the best-matching desired locale.
         * null if the list of desired locales is empty or if none matched well enough.
         *
         * @return the best-matching desired locale, or null.
         * @stable ICU 65
         */
        public ULocale getDesiredULocale() {
            return desiredULocale == null && desiredLocale != null ?
                    ULocale.forLocale(desiredLocale) : desiredULocale;
        }
        /**
         * Returns the best-matching desired locale.
         * null if the list of desired locales is empty or if none matched well enough.
         *
         * @return the best-matching desired locale, or null.
         * @stable ICU 65
         */
        public Locale getDesiredLocale() {
            return desiredLocale == null && desiredULocale != null ?
                    desiredULocale.toLocale() : desiredLocale;
        }

        /**
         * Returns the best-matching supported locale.
         * If none matched well enough, this is the default locale.
         * The default locale is null if {@link Builder#setNoDefaultLocale()} was called,
         * or if the list of supported locales is empty and no explicit default locale is set.
         *
         * @return the best-matching supported locale, or null.
         * @stable ICU 65
         */
        public ULocale getSupportedULocale() { return supportedULocale; }
        /**
         * Returns the best-matching supported locale.
         * If none matched well enough, this is the default locale.
         * The default locale is null if {@link Builder#setNoDefaultLocale()} was called,
         * or if the list of supported locales is empty and no explicit default locale is set.
         *
         * @return the best-matching supported locale, or null.
         * @stable ICU 65
         */
        public Locale getSupportedLocale() { return supportedLocale; }

        /**
         * Returns the index of the best-matching desired locale in the input Iterable order.
         * -1 if the list of desired locales is empty or if none matched well enough.
         *
         * @return the index of the best-matching desired locale, or -1.
         * @stable ICU 65
         */
        public int getDesiredIndex() { return desiredIndex; }

        /**
         * Returns the index of the best-matching supported locale in the
         * constructor\u2019s or builder\u2019s input order (\u201cset" Collection plus "added\u201d locales).
         * If the matcher was built from a locale list string, then the iteration order is that
         * of a LocalePriorityList built from the same string.
         * -1 if the list of supported locales is empty or if none matched well enough.
         *
         * @return the index of the best-matching supported locale, or -1.
         * @stable ICU 65
         */
        public int getSupportedIndex() { return supportedIndex; }

        /**
         * Takes the best-matching supported locale and adds relevant fields of the
         * best-matching desired locale, such as the -t- and -u- extensions.
         * May replace some fields of the supported locale.
         * The result is the locale that should be used for date and number formatting, collation, etc.
         * Returns null if getSupportedLocale() returns null.
         *
         * <p>Example: desired=ar-SA-u-nu-latn, supported=ar-EG, resolved locale=ar-SA-u-nu-latn
         *
         * @return a locale combining the best-matching desired and supported locales.
         * @stable ICU 65
         */
        public ULocale makeResolvedULocale() {
            ULocale bestDesired = getDesiredULocale();
            if (supportedULocale == null || bestDesired == null ||
                    supportedULocale.equals(bestDesired)) {
                return supportedULocale;
            }
            ULocale.Builder b = new ULocale.Builder().setLocale(supportedULocale);

            // Copy the region from bestDesired, if there is one.
            String region = bestDesired.getCountry();
            if (!region.isEmpty()) {
                b.setRegion(region);
            }

            // Copy the variants from bestDesired, if there are any.
            // Note that this will override any supportedULocale variants.
            // For example, "sco-ulster-fonipa" + "...-fonupa" => "sco-fonupa" (replacing ulster).
            String variants = bestDesired.getVariant();
            if (!variants.isEmpty()) {
                b.setVariant(variants);
            }

            // Copy the extensions from bestDesired, if there are any.
            // Note that this will override any supportedULocale extensions.
            // For example, "th-u-nu-latn-ca-buddhist" + "...-u-nu-native" => "th-u-nu-native"
            // (replacing calendar).
            for (char extensionKey : bestDesired.getExtensionKeys()) {
                b.setExtension(extensionKey, bestDesired.getExtension(extensionKey));
            }
            return b.build();
        }

        /**
         * Takes the best-matching supported locale and adds relevant fields of the
         * best-matching desired locale, such as the -t- and -u- extensions.
         * May replace some fields of the supported locale.
         * The result is the locale that should be used for
         * date and number formatting, collation, etc.
         * Returns null if getSupportedLocale() returns null.
         *
         * <p>Example: desired=ar-SA-u-nu-latn, supported=ar-EG, resolved locale=ar-SA-u-nu-latn
         *
         * @return a locale combining the best-matching desired and supported locales.
         * @stable ICU 65
         */
        public Locale makeResolvedLocale() {
            ULocale resolved = makeResolvedULocale();
            return resolved != null ? resolved.toLocale() : null;
        }
    }

    private final int thresholdDistance;
    private final int demotionPerDesiredLocale;
    private final FavorSubtag favorSubtag;
    private final Direction direction;

    // These are in input order.
    private final ULocale[] supportedULocales;
    private final Locale[] supportedLocales;
    // These are in preference order: 1. Default locale 2. paradigm locales 3. others.
    private final Map<LSR, Integer> supportedLsrToIndex;
    // Array versions of the supportedLsrToIndex keys and values.
    // The distance lookup loops over the supportedLSRs and returns the index of the best match.
    private final LSR[] supportedLSRs;
    private final int[] supportedIndexes;
    private final int supportedLSRsLength;
    private final ULocale defaultULocale;
    private final Locale defaultLocale;

    /**
     * LocaleMatcher Builder.
     *
     * @see LocaleMatcher#builder()
     * @stable ICU 65
     */
    public static final class Builder {
        private List<ULocale> supportedLocales;
        private int thresholdDistance = -1;
        private Demotion demotion;
        private ULocale defaultLocale;
        private boolean withDefault = true;
        private FavorSubtag favor;
        private Direction direction;
        private ULocale maxDistanceDesired;
        private ULocale maxDistanceSupported;

        private Builder() {}

        /**
         * Parses the string like {@link LocalePriorityList} does and
         * sets the supported locales accordingly.
         * Clears any previously set/added supported locales first.
         *
         * @param locales the string of locales to set, to be parsed like LocalePriorityList does
         * @return this Builder object
         * @stable ICU 65
         */
        public Builder setSupportedLocales(String locales) {
            return setSupportedULocales(LocalePriorityList.add(locales).build().getULocales());
        }

        /**
         * Copies the supported locales, preserving iteration order.
         * Clears any previously set/added supported locales first.
         * Duplicates are allowed, and are not removed.
         *
         * @param locales the list of locales
         * @return this Builder object
         * @stable ICU 65
         */
        public Builder setSupportedULocales(Collection<ULocale> locales) {
            supportedLocales = new ArrayList<>(locales);
            return this;
        }

        /**
         * Copies the supported locales, preserving iteration order.
         * Clears any previously set/added supported locales first.
         * Duplicates are allowed, and are not removed.
         *
         * @param locales the list of locale
         * @return this Builder object
         * @stable ICU 65
         */
        public Builder setSupportedLocales(Collection<Locale> locales) {
            supportedLocales = new ArrayList<>(locales.size());
            for (Locale locale : locales) {
                supportedLocales.add(ULocale.forLocale(locale));
            }
            return this;
        }

        /**
         * Adds another supported locale.
         * Duplicates are allowed, and are not removed.
         *
         * @param locale another locale
         * @return this Builder object
         * @stable ICU 65
         */
        public Builder addSupportedULocale(ULocale locale) {
            if (supportedLocales == null) {
                supportedLocales = new ArrayList<>();
            }
            supportedLocales.add(locale);
            return this;
        }

        /**
         * Adds another supported locale.
         * Duplicates are allowed, and are not removed.
         *
         * @param locale another locale
         * @return this Builder object
         * @stable ICU 65
         */
        public Builder addSupportedLocale(Locale locale) {
            return addSupportedULocale(ULocale.forLocale(locale));
        }

        /**
         * Sets no default locale.
         * There will be no explicit or implicit default locale.
         * If there is no good match, then the matcher will return null for the
         * best supported locale.
         *
         * @stable ICU 68
         */
        public Builder setNoDefaultLocale() {
            this.defaultLocale = null;
            withDefault = false;
            return this;
        }

        /**
         * Sets the default locale; if null, or if it is not set explicitly,
         * then the first supported locale is used as the default locale.
         * There is no default locale at all (null will be returned instead)
         * if {@link #setNoDefaultLocale()} is called.
         *
         * @param defaultLocale the default locale
         * @return this Builder object
         * @stable ICU 65
         */
        public Builder setDefaultULocale(ULocale defaultLocale) {
            this.defaultLocale = defaultLocale;
            withDefault = true;
            return this;
        }

        /**
         * Sets the default locale; if null, or if it is not set explicitly,
         * then the first supported locale is used as the default locale.
         * There is no default locale at all (null will be returned instead)
         * if {@link #setNoDefaultLocale()} is called.
         *
         * @param defaultLocale the default locale
         * @return this Builder object
         * @stable ICU 65
         */
        public Builder setDefaultLocale(Locale defaultLocale) {
            this.defaultLocale = ULocale.forLocale(defaultLocale);
            withDefault = true;
            return this;
        }

        /**
         * If SCRIPT, then the language differences are smaller than script differences.
         * This is used in situations (such as maps) where
         * it is better to fall back to the same script than a similar language.
         *
         * @param subtag the subtag to favor
         * @return this Builder object
         * @stable ICU 65
         */
        public Builder setFavorSubtag(FavorSubtag subtag) {
            this.favor = subtag;
            return this;
        }

        /**
         * Option for whether all desired locales are treated equally or
         * earlier ones are preferred (this is the default).
         *
         * @param demotion the demotion per desired locale to set.
         * @return this Builder object
         * @stable ICU 65
         */
        public Builder setDemotionPerDesiredLocale(Demotion demotion) {
            this.demotion = demotion;
            return this;
        }

        /**
         * Option for whether to include or ignore one-way (fallback) match data.
         * By default, they are included.
         *
         * @param direction the match direction to set.
         * @return this Builder object
         * @stable ICU 67
         */
        public Builder setDirection(Direction direction) {
            this.direction = direction;
            return this;
        }

        /**
         * Sets the maximum distance for an acceptable match.
         * The matcher will return a match for a pair of locales only if
         * they match at least as well as the pair given here.
         *
         * <p>For example, setMaxDistance(en-US, en-GB) limits matches to ones where the
         * (desired, support) locales have a distance no greater than a region subtag difference.
         * This is much stricter than the CLDR default.
         *
         * <p>The details of locale matching are subject to changes in
         * CLDR data and in the algorithm.
         * Specifying a maximum distance in relative terms via a sample pair of locales
         * insulates from changes that affect all distance metrics similarly,
         * but some changes will necessarily affect relative distances between
         * different pairs of locales.
         *
         * @param desired the desired locale for distance comparison.
         * @param supported the supported locale for distance comparison.
         * @return this Builder object
         * @stable ICU 68
         */
        public Builder setMaxDistance(Locale desired, Locale supported) {
            if (desired == null || supported == null) {
                throw new IllegalArgumentException("desired/supported locales must not be null");
            }
            return setMaxDistance(ULocale.forLocale(desired), ULocale.forLocale(supported));
        }

        /**
         * Sets the maximum distance for an acceptable match.
         * The matcher will return a match for a pair of locales only if
         * they match at least as well as the pair given here.
         *
         * <p>For example, setMaxDistance(en-US, en-GB) limits matches to ones where the
         * (desired, support) locales have a distance no greater than a region subtag difference.
         * This is much stricter than the CLDR default.
         *
         * <p>The details of locale matching are subject to changes in
         * CLDR data and in the algorithm.
         * Specifying a maximum distance in relative terms via a sample pair of locales
         * insulates from changes that affect all distance metrics similarly,
         * but some changes will necessarily affect relative distances between
         * different pairs of locales.
         *
         * @param desired the desired locale for distance comparison.
         * @param supported the supported locale for distance comparison.
         * @return this Builder object
         * @stable ICU 68
         */
        public Builder setMaxDistance(ULocale desired, ULocale supported) {
            if (desired == null || supported == null) {
                throw new IllegalArgumentException("desired/supported locales must not be null");
            }
            maxDistanceDesired = desired;
            maxDistanceSupported = supported;
            return this;
        }

        /**
         * <i>Internal only!</i>
         *
         * @param thresholdDistance the thresholdDistance to set, with -1 = default
         * @return this Builder object
         * @internal
         * @deprecated This API is ICU internal only.
         */
        @Deprecated
        public Builder internalSetThresholdDistance(int thresholdDistance) {
            if (thresholdDistance > 100) {
                thresholdDistance = 100;
            }
            this.thresholdDistance = thresholdDistance;
            return this;
        }

        /**
         * Builds and returns a new locale matcher.
         * This builder can continue to be used.
         *
         * @return new LocaleMatcher.
         * @stable ICU 65
         */
        public LocaleMatcher build() {
            return new LocaleMatcher(this);
        }

        /**
         * {@inheritDoc}
         * @stable ICU 65
         */
        @Override
        public String toString() {
            StringBuilder s = new StringBuilder().append("{LocaleMatcher.Builder");
            if (supportedLocales != null && !supportedLocales.isEmpty()) {
                s.append(" supported={").append(supportedLocales).append('}');
            }
            if (defaultLocale != null) {
                s.append(" default=").append(defaultLocale);
            }
            if (favor != null) {
                s.append(" distance=").append(favor);
            }
            if (thresholdDistance >= 0) {
                s.append(String.format(" threshold=%d", thresholdDistance));
            }
            if (demotion != null) {
                s.append(" demotion=").append(demotion);
            }
            return s.append('}').toString();
        }
    }

    /**
     * Returns a builder used in chaining parameters for building a LocaleMatcher.
     *
     * @return a new Builder object
     * @stable ICU 65
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Copies the supported locales, preserving iteration order, and constructs a LocaleMatcher.
     * The first locale is used as the default locale for when there is no good match.
     *
     * @param supportedLocales list of locales
     * @stable ICU 4.4
     */
    public LocaleMatcher(LocalePriorityList supportedLocales) {
        this(builder().setSupportedULocales(supportedLocales.getULocales()));
    }

    /**
     * Parses the string like {@link LocalePriorityList} does and
     * constructs a LocaleMatcher for the supported locales parsed from the string.
     * The first one (in LocalePriorityList iteration order) is used as the default locale for
     * when there is no good match.
     *
     * @param supportedLocales the string of locales to set,
     *          to be parsed like LocalePriorityList does
     * @stable ICU 4.4
     */
    public LocaleMatcher(String supportedLocales) {
        this(builder().setSupportedLocales(supportedLocales));
    }

    private LocaleMatcher(Builder builder) {
        ULocale udef = builder.defaultLocale;
        Locale def = null;
        LSR defLSR = null;
        if (udef != null) {
            def = udef.toLocale();
            defLSR = getMaximalLsrOrUnd(udef);
        }
        // Store the supported locales in input order,
        // so that when different types are used (e.g., java.util.Locale)
        // we can return those by parallel index.
        int supportedLocalesLength = builder.supportedLocales != null ?
                builder.supportedLocales.size() : 0;
        supportedULocales = new ULocale[supportedLocalesLength];
        supportedLocales = new Locale[supportedLocalesLength];
        // Supported LRSs in input order.
        LSR lsrs[] = new LSR[supportedLocalesLength];
        int i = 0;
        if (supportedLocalesLength > 0) {
            for (ULocale locale : builder.supportedLocales) {
                supportedULocales[i] = locale;
                supportedLocales[i] = locale.toLocale();
                lsrs[i] = getMaximalLsrOrUnd(locale);
                ++i;
            }
        }

        // We need an unordered map from LSR to first supported locale with that LSR,
        // and an ordered list of (LSR, supported index) for
        // the supported locales in the following order:
        // 1. Default locale, if it is supported.
        // 2. Priority locales (aka "paradigm locales") in builder order.
        // 3. Remaining locales in builder order.
        supportedLsrToIndex = new HashMap<>(supportedLocalesLength);
        supportedLSRs = new LSR[supportedLocalesLength];
        supportedIndexes = new int[supportedLocalesLength];
        int suppLength = 0;
        // Determine insertion order.
        // Add locales immediately that are equivalent to the default.
        byte[] order = new byte[supportedLocalesLength];
        int numParadigms = 0;
        i = 0;
        for (ULocale locale : supportedULocales) {
            LSR lsr = lsrs[i];
            if (defLSR == null && builder.withDefault) {
                // Implicit default locale = first supported locale, if not turned off.
                assert i == 0;
                udef = locale;
                def = supportedLocales[0];
                defLSR = lsr;
                suppLength = putIfAbsent(lsr, 0, suppLength);
            } else if (defLSR != null && lsr.isEquivalentTo(defLSR)) {
                suppLength = putIfAbsent(lsr, i, suppLength);
            } else if (LocaleDistance.INSTANCE.isParadigmLSR(lsr)) {
                order[i] = 2;
                ++numParadigms;
            } else {
                order[i] = 3;
            }
            ++i;
        }
        // Add supported paradigm locales.
        int paradigmLimit = suppLength + numParadigms;
        for (i = 0; i < supportedLocalesLength && suppLength < paradigmLimit; ++i) {
            if (order[i] == 2) {
                suppLength = putIfAbsent(lsrs[i], i, suppLength);
            }
        }
        // Add remaining supported locales.
        for (i = 0; i < supportedLocalesLength; ++i) {
            if (order[i] == 3) {
                suppLength = putIfAbsent(lsrs[i], i, suppLength);
            }
        }
        supportedLSRsLength = suppLength;
        // If supportedLSRsLength < supportedLocalesLength then
        // we waste as many array slots as there are duplicate supported LSRs,
        // but the amount of wasted space is small as long as there are few duplicates.

        defaultULocale = udef;
        defaultLocale = def;
        demotionPerDesiredLocale =
                builder.demotion == Demotion.NONE ? 0 :
                    LocaleDistance.INSTANCE.getDefaultDemotionPerDesiredLocale();  // null or REGION
        favorSubtag = builder.favor;
        direction = builder.direction;

        int threshold;
        if (builder.thresholdDistance >= 0) {
            threshold = builder.thresholdDistance;
        } else if (builder.maxDistanceDesired != null) {
            int indexAndDistance = LocaleDistance.INSTANCE.getBestIndexAndDistance(
                    getMaximalLsrOrUnd(builder.maxDistanceDesired),
                    new LSR[] { getMaximalLsrOrUnd(builder.maxDistanceSupported) }, 1,
                    LocaleDistance.shiftDistance(100), favorSubtag, direction);
            // +1 for an exclusive threshold from an inclusive max.
            threshold = LocaleDistance.getDistanceFloor(indexAndDistance) + 1;
        } else {
            threshold = LocaleDistance.INSTANCE.getDefaultScriptDistance();
        }
        thresholdDistance = threshold;

        if (TRACE_MATCHER) {
            System.err.printf("new LocaleMatcher: %s\n", toString());
        }
    }

    private final int putIfAbsent(LSR lsr, int i, int suppLength) {
        if (!supportedLsrToIndex.containsKey(lsr)) {
            supportedLsrToIndex.put(lsr, i);
            supportedLSRs[suppLength] = lsr;
            supportedIndexes[suppLength++] = i;
        }
        return suppLength;
    }

    private static final LSR getMaximalLsrOrUnd(ULocale locale) {
        if (locale.equals(UND_ULOCALE)) {
            return UND_LSR;
        } else {
            return XLikelySubtags.INSTANCE.makeMaximizedLsrFrom(locale);
        }
    }

    private static final LSR getMaximalLsrOrUnd(Locale locale) {
        if (locale.equals(UND_LOCALE) || locale.equals(EMPTY_LOCALE)) {
            return UND_LSR;
        } else {
            return XLikelySubtags.INSTANCE.makeMaximizedLsrFrom(locale);
        }
    }

    private static final class ULocaleLsrIterator extends LsrIterator {
        private Iterator<ULocale> locales;
        private ULocale current, remembered;

        ULocaleLsrIterator(Iterator<ULocale> locales) {
            this.locales = locales;
        }

        @Override
        public boolean hasNext() {
            return locales.hasNext();
        }

        @Override
        public LSR next() {
            current = locales.next();
            return getMaximalLsrOrUnd(current);
        }

        @Override
        public void rememberCurrent(int desiredIndex) {
            bestDesiredIndex = desiredIndex;
            remembered = current;
        }
    }

    private static final class LocaleLsrIterator extends LsrIterator {
        private Iterator<Locale> locales;
        private Locale current, remembered;

        LocaleLsrIterator(Iterator<Locale> locales) {
            this.locales = locales;
        }

        @Override
        public boolean hasNext() {
            return locales.hasNext();
        }

        @Override
        public LSR next() {
            current = locales.next();
            return getMaximalLsrOrUnd(current);
        }

        @Override
        public void rememberCurrent(int desiredIndex) {
            bestDesiredIndex = desiredIndex;
            remembered = current;
        }
    }

    /**
     * Returns the supported locale which best matches the desired locale.
     *
     * @param desiredLocale Typically a user's language.
     * @return the best-matching supported locale.
     * @stable ICU 4.4
     */
    public ULocale getBestMatch(ULocale desiredLocale) {
        LSR desiredLSR = getMaximalLsrOrUnd(desiredLocale);
        int suppIndex = getBestSuppIndex(desiredLSR, null);
        return suppIndex >= 0 ? supportedULocales[suppIndex] : defaultULocale;
    }

    /**
     * Returns the supported locale which best matches one of the desired locales.
     *
     * @param desiredLocales Typically a user's languages, in order of preference (descending).
     *          (In ICU 4.4..63 this parameter had type LocalePriorityList.)
     * @return the best-matching supported locale.
     * @stable ICU 4.4
     */
    public ULocale getBestMatch(Iterable<ULocale> desiredLocales) {
        Iterator<ULocale> desiredIter = desiredLocales.iterator();
        if (!desiredIter.hasNext()) {
            return defaultULocale;
        }
        ULocaleLsrIterator lsrIter = new ULocaleLsrIterator(desiredIter);
        LSR desiredLSR = lsrIter.next();
        int suppIndex = getBestSuppIndex(desiredLSR, lsrIter);
        return suppIndex >= 0 ? supportedULocales[suppIndex] : defaultULocale;
    }

    /**
     * Parses the string like {@link LocalePriorityList} does and
     * returns the supported locale which best matches one of the desired locales.
     *
     * @param desiredLocaleList Typically a user's languages,
     *          as a string which is to be parsed like LocalePriorityList does.
     * @return the best-matching supported locale.
     * @stable ICU 4.4
     */
    public ULocale getBestMatch(String desiredLocaleList) {
        return getBestMatch(LocalePriorityList.add(desiredLocaleList).build());
    }

    /**
     * Returns the supported locale which best matches the desired locale.
     *
     * @param desiredLocale Typically a user's language.
     * @return the best-matching supported locale.
     * @stable ICU 65
     */
    public Locale getBestLocale(Locale desiredLocale) {
        LSR desiredLSR = getMaximalLsrOrUnd(desiredLocale);
        int suppIndex = getBestSuppIndex(desiredLSR, null);
        return suppIndex >= 0 ? supportedLocales[suppIndex] : defaultLocale;
    }

    /**
     * Returns the supported locale which best matches one of the desired locales.
     *
     * @param desiredLocales Typically a user's languages, in order of preference (descending).
     * @return the best-matching supported locale.
     * @stable ICU 65
     */
    public Locale getBestLocale(Iterable<Locale> desiredLocales) {
        Iterator<Locale> desiredIter = desiredLocales.iterator();
        if (!desiredIter.hasNext()) {
            return defaultLocale;
        }
        LocaleLsrIterator lsrIter = new LocaleLsrIterator(desiredIter);
        LSR desiredLSR = lsrIter.next();
        int suppIndex = getBestSuppIndex(desiredLSR, lsrIter);
        return suppIndex >= 0 ? supportedLocales[suppIndex] : defaultLocale;
    }

    private Result defaultResult() {
        return new Result(null, defaultULocale, null, defaultLocale, -1, -1);
    }

    private Result makeResult(ULocale desiredLocale, ULocaleLsrIterator lsrIter, int suppIndex) {
        if (suppIndex < 0) {
            return defaultResult();
        } else if (desiredLocale != null) {
            return new Result(desiredLocale, supportedULocales[suppIndex],
                    null, supportedLocales[suppIndex], 0, suppIndex);
        } else {
            return new Result(lsrIter.remembered, supportedULocales[suppIndex],
                    null, supportedLocales[suppIndex], lsrIter.bestDesiredIndex, suppIndex);
        }
    }

    private Result makeResult(Locale desiredLocale, LocaleLsrIterator lsrIter, int suppIndex) {
        if (suppIndex < 0) {
            return defaultResult();
        } else if (desiredLocale != null) {
            return new Result(null, supportedULocales[suppIndex],
                    desiredLocale, supportedLocales[suppIndex], 0, suppIndex);
        } else {
            return new Result(null, supportedULocales[suppIndex],
                    lsrIter.remembered, supportedLocales[suppIndex],
                    lsrIter.bestDesiredIndex, suppIndex);
        }
    }

    /**
     * Returns the best match between the desired locale and the supported locales.
     *
     * @param desiredLocale Typically a user's language.
     * @return the best-matching pair of the desired and a supported locale.
     * @stable ICU 65
     */
    public Result getBestMatchResult(ULocale desiredLocale) {
        LSR desiredLSR = getMaximalLsrOrUnd(desiredLocale);
        int suppIndex = getBestSuppIndex(desiredLSR, null);
        return makeResult(desiredLocale, null, suppIndex);
    }

    /**
     * Returns the best match between the desired and supported locales.
     *
     * @param desiredLocales Typically a user's languages, in order of preference (descending).
     * @return the best-matching pair of a desired and a supported locale.
     * @stable ICU 65
     */
    public Result getBestMatchResult(Iterable<ULocale> desiredLocales) {
        Iterator<ULocale> desiredIter = desiredLocales.iterator();
        if (!desiredIter.hasNext()) {
            return defaultResult();
        }
        ULocaleLsrIterator lsrIter = new ULocaleLsrIterator(desiredIter);
        LSR desiredLSR = lsrIter.next();
        int suppIndex = getBestSuppIndex(desiredLSR, lsrIter);
        return makeResult(null, lsrIter, suppIndex);
    }

    /**
     * Returns the best match between the desired locale and the supported locales.
     *
     * @param desiredLocale Typically a user's language.
     * @return the best-matching pair of the desired and a supported locale.
     * @stable ICU 65
     */
    public Result getBestLocaleResult(Locale desiredLocale) {
        LSR desiredLSR = getMaximalLsrOrUnd(desiredLocale);
        int suppIndex = getBestSuppIndex(desiredLSR, null);
        return makeResult(desiredLocale, null, suppIndex);
    }

    /**
     * Returns the best match between the desired and supported locales.
     *
     * @param desiredLocales Typically a user's languages, in order of preference (descending).
     * @return the best-matching pair of a desired and a supported locale.
     * @stable ICU 65
     */
    public Result getBestLocaleResult(Iterable<Locale> desiredLocales) {
        Iterator<Locale> desiredIter = desiredLocales.iterator();
        if (!desiredIter.hasNext()) {
            return defaultResult();
        }
        LocaleLsrIterator lsrIter = new LocaleLsrIterator(desiredIter);
        LSR desiredLSR = lsrIter.next();
        int suppIndex = getBestSuppIndex(desiredLSR, lsrIter);
        return makeResult(null, lsrIter, suppIndex);
    }

    /**
     * @param desiredLSR The first desired locale's LSR.
     * @param remainingIter Remaining desired LSRs, null or empty if none.
     * @return the index of the best-matching supported locale, or -1 if there is no good match.
     */
    private int getBestSuppIndex(LSR desiredLSR, LsrIterator remainingIter) {
        int desiredIndex = 0;
        int bestSupportedLsrIndex = -1;
        StringBuilder sb = null;
        if (TRACE_MATCHER) {
            sb = new StringBuilder("LocaleMatcher desired:");
        }
        for (int bestShiftedDistance = LocaleDistance.shiftDistance(thresholdDistance);;) {
            if (TRACE_MATCHER) {
                sb.append(' ').append(desiredLSR);
            }
            // Quick check for exact maximized LSR.
            Integer index = supportedLsrToIndex.get(desiredLSR);
            if (index != null) {
                int suppIndex = index;
                if (TRACE_MATCHER) {
                    System.err.printf("%s --> best=%s: desiredLSR=supportedLSR\n",
                            sb, supportedULocales[suppIndex]);
                }
                if (remainingIter != null) { remainingIter.rememberCurrent(desiredIndex); }
                return suppIndex;
            }
            int bestIndexAndDistance = LocaleDistance.INSTANCE.getBestIndexAndDistance(
                    desiredLSR, supportedLSRs, supportedLSRsLength,
                    bestShiftedDistance, favorSubtag, direction);
            if (bestIndexAndDistance >= 0) {
                bestShiftedDistance = LocaleDistance.getShiftedDistance(bestIndexAndDistance);
                if (remainingIter != null) { remainingIter.rememberCurrent(desiredIndex); }
                bestSupportedLsrIndex = LocaleDistance.getIndex(bestIndexAndDistance);
            }
            if ((bestShiftedDistance -= LocaleDistance.shiftDistance(demotionPerDesiredLocale))
                    <= 0) {
                break;
            }
            if (remainingIter == null || !remainingIter.hasNext()) {
                break;
            }
            desiredLSR = remainingIter.next();
            ++desiredIndex;
        }
        if (bestSupportedLsrIndex < 0) {
            if (TRACE_MATCHER) {
                System.err.printf("%s --> best=default %s: no good match\n", sb, defaultULocale);
            }
            return -1;
        }
        int suppIndex = supportedIndexes[bestSupportedLsrIndex];
        if (TRACE_MATCHER) {
            System.err.printf("%s --> best=%s: best matching supported locale\n",
                    sb, supportedULocales[suppIndex]);
        }
        return suppIndex;
    }

    /**
     * Returns true if the pair of locales matches acceptably.
     * This is influenced by Builder options such as setDirection(), setFavorSubtag(),
     * and setMaxDistance().
     *
     * @param desired The desired locale.
     * @param supported The supported locale.
     * @return true if the pair of locales matches acceptably.
     * @stable ICU 68
     */
    public boolean isMatch(Locale desired, Locale supported) {
        int indexAndDistance = LocaleDistance.INSTANCE.getBestIndexAndDistance(
                getMaximalLsrOrUnd(desired),
                new LSR[] { getMaximalLsrOrUnd(supported) }, 1,
                LocaleDistance.shiftDistance(thresholdDistance), favorSubtag, direction);
        return indexAndDistance >= 0;
    }

    /**
     * Returns true if the pair of locales matches acceptably.
     * This is influenced by Builder options such as setDirection(), setFavorSubtag(),
     * and setMaxDistance().
     *
     * @param desired The desired locale.
     * @param supported The supported locale.
     * @return true if the pair of locales matches acceptably.
     * @stable ICU 68
     */
    public boolean isMatch(ULocale desired, ULocale supported) {
        int indexAndDistance = LocaleDistance.INSTANCE.getBestIndexAndDistance(
                getMaximalLsrOrUnd(desired),
                new LSR[] { getMaximalLsrOrUnd(supported) }, 1,
                LocaleDistance.shiftDistance(thresholdDistance), favorSubtag, direction);
        return indexAndDistance >= 0;
    }

    /**
     * Returns a fraction between 0 and 1, where 1 means that the languages are a
     * perfect match, and 0 means that they are completely different.
     *
     * <p>This is mostly an implementation detail, and the precise values may change over time.
     * The implementation may use either the maximized forms or the others ones, or both.
     * The implementation may or may not rely on the forms to be consistent with each other.
     *
     * <p>Callers should construct and use a matcher rather than match pairs of locales directly.
     *
     * @param desired Desired locale.
     * @param desiredMax Maximized locale (using likely subtags).
     * @param supported Supported locale.
     * @param supportedMax Maximized locale (using likely subtags).
     * @return value between 0 and 1, inclusive.
     * @deprecated ICU 65 Build and use a matcher rather than comparing pairs of locales.
     */
    @Deprecated
    public double match(ULocale desired, ULocale desiredMax, ULocale supported, ULocale supportedMax) {
        // Returns the inverse of the distance: That is, 1-distance(desired, supported).
        int indexAndDistance = LocaleDistance.INSTANCE.getBestIndexAndDistance(
                getMaximalLsrOrUnd(desired),
                new LSR[] { getMaximalLsrOrUnd(supported) }, 1,
                LocaleDistance.shiftDistance(thresholdDistance), favorSubtag, direction);
        double distance = LocaleDistance.getDistanceDouble(indexAndDistance);
        if (TRACE_MATCHER) {
            System.err.printf("LocaleMatcher distance(desired=%s, supported=%s)=%g\n",
                String.valueOf(desired), String.valueOf(supported), distance);
        }
        return (100.0 - distance) / 100.0;
    }

    /**
     * Partially canonicalizes a locale (language). Note that for now, it is canonicalizing
     * according to CLDR conventions (he vs iw, etc), since that is what is needed
     * for likelySubtags.
     *
     * <p>Currently, this is a much simpler canonicalization than what the ULocale class does:
     * The language/script/region subtags are each mapped separately, ignoring the other subtags.
     * If none of these change, then the input locale is returned.
     * Otherwise a new ULocale with only those subtags is returned, removing variants and extensions.
     *
     * @param locale language/locale code
     * @return ULocale with remapped subtags.
     * @stable ICU 4.4
     */
    public ULocale canonicalize(ULocale locale) {
        return XLikelySubtags.INSTANCE.canonicalize(locale);
    }

    /**
     * {@inheritDoc}
     * @stable ICU 4.4
     */
    @Override
    public String toString() {
        StringBuilder s = new StringBuilder().append("{LocaleMatcher");
        // Supported languages in the order that we try to match them.
        if (supportedLSRsLength > 0) {
            s.append(" supportedLSRs={").append(supportedLSRs[0]);
            for (int i = 1; i < supportedLSRsLength; ++i) {
                s.append(", ").append(supportedLSRs[i]);
            }
            s.append('}');
        }
        s.append(" default=").append(defaultULocale);
        if (favorSubtag != null) {
            s.append(" favor=").append(favorSubtag);
        }
        if (direction != null) {
            s.append(" direction=").append(direction);
        }
        if (thresholdDistance >= 0) {
            s.append(String.format(" threshold=%d", thresholdDistance));
        }
        s.append(String.format(" demotion=%d", demotionPerDesiredLocale));
        return s.append('}').toString();
    }
}
