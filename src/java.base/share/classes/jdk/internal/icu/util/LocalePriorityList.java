// Copyright 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 *******************************************************************************
 * Copyright (C) 2010-2016, Google, Inc.; International Business Machines      *
 * Corporation and others. All Rights Reserved.                                *
 *******************************************************************************
 */

package jdk.internal.icu.util;

import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Provides an immutable list of languages/locales in priority order.
 * The string format is based on the Accept-Language format
 * (<a href="https://tools.ietf.org/html/rfc2616#section-14.4">RFC 2616 Section 14.4</a>), such as
 * "af, en, fr;q=0.9". Syntactically it is slightly
 * more lenient, in allowing extra whitespace between elements, extra commas,
 * and more than 3 decimals (on input). The qvalues must be between 0 and 1.
 *
 * <p>In theory, Accept-Language indicates the relative 'quality' of each item,
 * but in practice, all of the browsers just take an ordered list, like
 * "en, fr, de", and synthesize arbitrary quality values that put these in the
 * right order, like: "en, fr;q=0.7, de;q=0.3". The quality values in these de facto
 * semantics thus have <b>nothing</b> to do with the relative qualities of the
 * original. Accept-Language also doesn't
 * specify the interpretation of multiple instances, eg what "en, fr, en;q=.5"
 * means.
 * <p>There are various ways to build a LocalePriorityList, such
 * as using the following equivalent patterns:
 *
 * <pre>
 * list = LocalePriorityList.add(&quot;af, en, fr;q=0.9&quot;).build();
 *
 * list2 = LocalePriorityList
 *  .add(ULocale.forString(&quot;af&quot;))
 *  .add(ULocale.ENGLISH)
 *  .add(ULocale.FRENCH, 0.9d)
 *  .build();
 * </pre>
 * When the list is built, the internal values are sorted in descending order by weight,
 * and then by input order.
 * That is, if two languages/locales have the same weight, the first one in the original order comes first.
 * If exactly the same language tag appears multiple times, the last one wins.
 *
 * <p>There are two options when building.
 * If preserveWeights are on, then "de;q=0.3, ja;q=0.3, en, fr;q=0.7, de " would result in the following:
 * <pre> en;q=1.0
 * de;q=1.0
 * fr;q=0.7
 * ja;q=0.3</pre>
 * If it is off (the default), then all weights are reset to 1.0 after reordering.
 * This is to match the effect of the Accept-Language semantics as used in browsers, and results in the following:
 * <pre> en;q=1.0
 * de;q=1.0
 * fr;q=1.0
 * ja;q=1.0</pre>
 * @author markdavis@google.com
 * @stable ICU 4.4
 */
public class LocalePriorityList implements Iterable<ULocale> {
    private static final Double D1 = 1.0d;

    private static final Pattern languageSplitter = Pattern.compile("\\s*,\\s*");
    private static final Pattern weightSplitter = Pattern
            .compile("\\s*(\\S*)\\s*;\\s*q\\s*=\\s*(\\S*)");
    private final Map<ULocale, Double> languagesAndWeights;

    /**
     * Creates a Builder and adds locales, each with weight 1.0.
     *
     * @param locales locales/languages to be added
     * @return a new builder with these locales, for chaining
     * @stable ICU 4.4
     */
    public static Builder add(ULocale... locales) {
        return new Builder().add(locales);
    }

    /**
     * Creates a Builder and adds a locale with a specified weight.
     * A zero or negative weight leads to removing the locale.
     * A weight greater than 1 is pinned to 1.
     *
     * @param locale locale/language to be added
     * @param weight value from 0.0 to 1.0
     * @return a new builder with this locale, for chaining
     * @stable ICU 4.4
     */
    public static Builder add(ULocale locale, final double weight) {
        return new Builder().add(locale, weight);
    }

    /**
     * Creates a Builder and adds locales with weights.
     *
     * @param list list of locales with weights
     * @return a new builder with these locales, for chaining
     * @stable ICU 4.4
     */
    public static Builder add(LocalePriorityList list) {
        return new Builder(list);
    }

    /**
     * Creates a Builder, parses the RFC 2616 string, and adds locales with weights accordingly.
     *
     * @param acceptLanguageString String in RFC 2616 format (leniently parsed)
     * @return a new builder with these locales, for chaining
     * @stable ICU 4.4
     */
    public static Builder add(String acceptLanguageString) {
        return new Builder().add(acceptLanguageString);
    }

    /**
     * Returns the weight for a given language/locale, or null if there is none.
     * Note that the weights may be adjusted from those used to build the list.
     *
     * @param locale to get weight of
     * @return weight
     * @stable ICU 4.4
     */
    public Double getWeight(ULocale locale) {
        return languagesAndWeights.get(locale);
    }

    /**
     * Returns the locales as an immutable Set view.
     * The set has the same iteration order as this object itself.
     *
     * @return the locales
     * @stable ICU 65
     */
    public Set<ULocale> getULocales() {
        return languagesAndWeights.keySet();
    }

    /**
     * {@inheritDoc}
     * @stable ICU 4.4
     */
    @Override
    public String toString() {
        final StringBuilder result = new StringBuilder();
        for (Entry<ULocale, Double> entry : languagesAndWeights.entrySet()) {
            ULocale language = entry.getKey();
            double weight = entry.getValue();
            if (result.length() != 0) {
                result.append(", ");
            }
            result.append(language);
            if (weight != 1.0) {
                result.append(";q=").append(weight);
            }
        }
        return result.toString();
    }

    /**
     * {@inheritDoc}
     * @stable ICU 4.4
     */
    @Override
    public Iterator<ULocale> iterator() {
        return languagesAndWeights.keySet().iterator();
    }

    /**
     * {@inheritDoc}
     * @stable ICU 4.4
     */
    @Override
    public boolean equals(final Object o) {
        if (o == null) {
            return false;
        }
        if (this == o) {
            return true;
        }
        try {
            final LocalePriorityList that = (LocalePriorityList) o;
            return languagesAndWeights.equals(that.languagesAndWeights);
        } catch (final RuntimeException e) {
            return false;
        }
    }

    /**
     * {@inheritDoc}
     * @stable ICU 4.4
     */
    @Override
    public int hashCode() {
        return languagesAndWeights.hashCode();
    }

    // ==================== Privates ====================


    private LocalePriorityList(final Map<ULocale, Double> languageToWeight) {
        this.languagesAndWeights = languageToWeight;
    }

    /**
     * Class used for building LocalePriorityLists.
     * @stable ICU 4.4
     */
    public static class Builder {
        /**
         * These store the input languages and weights, in chronological order,
         * where later additions override previous ones.
         */
        private Map<ULocale, Double> languageToWeight;
        /**
         * The builder is reusable but rarely reused. Avoid cloning the map when not needed.
         * Exactly one of languageToWeight and built is null.
         */
        private LocalePriorityList built;
        private boolean hasWeights = false;  // other than 1.0

        /**
         * Private constructor, only used by LocalePriorityList
         */
        private Builder() {
            languageToWeight = new LinkedHashMap<>();
        }

        private Builder(LocalePriorityList list) {
            built = list;
            for (Double value : list.languagesAndWeights.values()) {
                double weight = value;
                assert 0.0 < weight && weight <= 1.0;
                if (weight != 1.0) {
                    hasWeights = true;
                    break;
                }
            }
        }

        /**
         * Creates a LocalePriorityList.  This is equivalent to
         * {@link Builder#build(boolean) Builder.build(false)}.
         *
         * @return A LocalePriorityList
         * @stable ICU 4.4
         */
        public LocalePriorityList build() {
            return build(false);
        }

        /**
         * Creates a LocalePriorityList.
         *
         * @param preserveWeights when true, each locale's given weight is preserved.
         * @return A LocalePriorityList
         * @stable ICU 4.4
         */
        public LocalePriorityList build(boolean preserveWeights) {
            if (built != null) {
                // Calling build() again without changing anything in between.
                // Just return the same immutable list.
                return built;
            }
            Map<ULocale, Double> temp;
            if (hasWeights) {
                // Walk through the input list, collecting the items with the same weights.
                final TreeMap<Double, List<ULocale>> weightToLanguages =
                        new TreeMap<>(myDescendingDouble);
                for (Entry<ULocale, Double> entry : languageToWeight.entrySet()) {
                    ULocale lang = entry.getKey();
                    Double weight = entry.getValue();
                    List<ULocale> s = weightToLanguages.get(weight);
                    if (s == null) {
                        weightToLanguages.put(weight, s = new LinkedList<>());
                    }
                    s.add(lang);
                }
                // We now have a bunch of items sorted by weight, then chronologically.
                // We can now create a list in the right order.
                if (weightToLanguages.size() <= 1) {
                    // There is at most one weight.
                    temp = languageToWeight;
                    if (weightToLanguages.isEmpty() || weightToLanguages.firstKey() == 1.0) {
                        hasWeights = false;
                    }
                } else {
                    temp = new LinkedHashMap<>();
                    for (Entry<Double, List<ULocale>> langEntry : weightToLanguages.entrySet()) {
                        final Double weight = preserveWeights ? langEntry.getKey() : D1;
                        for (final ULocale lang : langEntry.getValue()) {
                            temp.put(lang, weight);
                        }
                    }
                }
            } else {
                // Nothing to sort.
                temp = languageToWeight;
            }
            languageToWeight = null;
            return built = new LocalePriorityList(Collections.unmodifiableMap(temp));
        }

        /**
         * Adds locales with weights.
         *
         * @param list list of locales with weights
         * @return this, for chaining
         * @stable ICU 4.4
         */
        public Builder add(final LocalePriorityList list) {
            for (Entry<ULocale, Double> entry : list.languagesAndWeights.entrySet()) {
                add(entry.getKey(), entry.getValue());
            }
            return this;
        }

        /**
         * Adds a locale with weight 1.0.
         *
         * @param locale to add with weight 1.0
         * @return this, for chaining
         * @stable ICU 4.4
         */
        public Builder add(final ULocale locale) {
            return add(locale, 1.0);
        }

        /**
         * Adds locales, each with weight 1.0.
         *
         * @param locales locales/languages to be added
         * @return this, for chaining.
         * @stable ICU 4.4
         */
        public Builder add(ULocale... locales) {
            for (final ULocale languageCode : locales) {
                add(languageCode, 1.0);
            }
            return this;
        }

        /**
         * Adds a locale with a specified weight.
         * Overrides any previous weight for the locale.
         * A zero or negative weight leads to removing the locale.
         * A weight greater than 1 is pinned to 1.
         *
         * @param locale language/locale to add
         * @param weight value between 0.0 and 1.1
         * @return this, for chaining.
         * @stable ICU 4.4
         */
        public Builder add(final ULocale locale, double weight) {
            if (languageToWeight == null) {
                // Builder reuse after build().
                languageToWeight = new LinkedHashMap<>(built.languagesAndWeights);
                built = null;
            }
            if (languageToWeight.containsKey(locale)) {
                languageToWeight.remove(locale);
            }
            Double value;
            if (weight <= 0.0) {
                return this; // skip zeros
            } else if (weight >= 1.0) {
                value = D1;
            } else {
                value = weight;
                hasWeights = true;
            }
            languageToWeight.put(locale, value);
            return this;
        }

        /**
         * Parses the RFC 2616 string, and adds locales with weights accordingly.
         *
         * @param acceptLanguageList in RFC 2616 format (leniently parsed)
         * @return this, for chaining.
         * @stable ICU 4.4
         */
        public Builder add(final String acceptLanguageList) {
            final String[] items = languageSplitter.split(acceptLanguageList.trim());
            final Matcher itemMatcher = weightSplitter.matcher("");
            for (final String item : items) {
                if (itemMatcher.reset(item).matches()) {
                    final ULocale language = new ULocale(itemMatcher.group(1));
                    final double weight = Double.parseDouble(itemMatcher.group(2));
                    if (!(0.0 <= weight && weight <= 1.0)) { // do ! for NaN
                        throw new IllegalArgumentException(
                                "Illegal weight, must be 0..1: " + weight);
                    }
                    add(language, weight);
                } else if (item.length() != 0) {
                    add(new ULocale(item));
                }
            }
            return this;
        }
    }

    private static Comparator<Double> myDescendingDouble = new Comparator<Double>() {
        @Override
        public int compare(Double o1, Double o2) {
            int result = o1.compareTo(o2);
            return result > 0 ? -1 : result < 0 ? 1 : 0; // Reverse the order.
        }
    };
}
