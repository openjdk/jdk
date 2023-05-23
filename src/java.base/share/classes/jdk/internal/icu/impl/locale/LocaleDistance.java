// Copyright 2017 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
package jdk.internal.icu.impl.locale;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Set;
import java.util.TreeMap;

import jdk.internal.icu.impl.ICUData;
import jdk.internal.icu.impl.ICUResourceBundle;
import jdk.internal.icu.impl.UResource;
import jdk.internal.icu.util.BytesTrie;
import jdk.internal.icu.util.LocaleMatcher;
import jdk.internal.icu.util.LocaleMatcher.FavorSubtag;
import jdk.internal.icu.util.ULocale;

/**
 * Offline-built data for LocaleMatcher.
 * Mostly but not only the data for mapping locales to their maximized forms.
 */
public class LocaleDistance {
    /**
     * Bit flag used on the last character of a subtag in the trie.
     * Must be set consistently by the builder and the lookup code.
     */
    public static final int END_OF_SUBTAG = 0x80;
    /** Distance value bit flag, set by the builder. */
    public static final int DISTANCE_SKIP_SCRIPT = 0x80;
    /** Distance value bit flag, set by trieNext(). */
    private static final int DISTANCE_IS_FINAL = 0x100;
    private static final int DISTANCE_IS_FINAL_OR_SKIP_SCRIPT =
            DISTANCE_IS_FINAL | DISTANCE_SKIP_SCRIPT;

    // The distance is shifted left to gain some fraction bits.
    private static final int DISTANCE_SHIFT = 3;
    private static final int DISTANCE_FRACTION_MASK = 7;
    // 7 bits for 0..100
    private static final int DISTANCE_INT_SHIFT = 7;
    private static final int INDEX_SHIFT = DISTANCE_INT_SHIFT + DISTANCE_SHIFT;
    private static final int DISTANCE_MASK = 0x3ff;
    // vate static final int MAX_INDEX = 0x1fffff;  // avoids sign bit
    private static final int INDEX_NEG_1 = 0xfffffc00;

    // Indexes into array of distances.
    public static final int IX_DEF_LANG_DISTANCE = 0;
    public static final int IX_DEF_SCRIPT_DISTANCE = 1;
    public static final int IX_DEF_REGION_DISTANCE = 2;
    public static final int IX_MIN_REGION_DISTANCE = 3;
    public static final int IX_LIMIT = 4;
    private static final int ABOVE_THRESHOLD = 100;

    private static final boolean DEBUG_OUTPUT = LSR.DEBUG_OUTPUT;

    // The trie maps each dlang+slang+dscript+sscript+dregion+sregion
    // (encoded in ASCII with bit 7 set on the last character of each subtag) to a distance.
    // There is also a trie value for each subsequence of whole subtags.
    // One '*' is used for a (desired, supported) pair of "und", "Zzzz"/"", or "ZZ"/"".
    private final BytesTrie trie;

    /**
     * Maps each region to zero or more single-character partitions.
     */
    private final byte[] regionToPartitionsIndex;
    private final String[] partitionArrays;

    /**
     * Used to get the paradigm region for a cluster, if there is one.
     */
    private final Set<LSR> paradigmLSRs;

    private final int defaultLanguageDistance;
    private final int defaultScriptDistance;
    private final int defaultRegionDistance;
    private final int minRegionDistance;
    private final int defaultDemotionPerDesiredLocale;

    public static final int shiftDistance(int distance) {
        return distance << DISTANCE_SHIFT;
    }

    public static final int getShiftedDistance(int indexAndDistance) {
        return indexAndDistance & DISTANCE_MASK;
    }

    public static final double getDistanceDouble(int indexAndDistance) {
        double shiftedDistance = getShiftedDistance(indexAndDistance);
        return shiftedDistance / (1 << DISTANCE_SHIFT);
    }

    public static final int getDistanceFloor(int indexAndDistance) {
        return (indexAndDistance & DISTANCE_MASK) >> DISTANCE_SHIFT;
    }

    public static final int getIndex(int indexAndDistance) {
        assert indexAndDistance >= 0;
        return indexAndDistance >> INDEX_SHIFT;
    }

    // VisibleForTesting
    public static final class Data {
        public byte[] trie;
        public byte[] regionToPartitionsIndex;
        public String[] partitionArrays;
        public Set<LSR> paradigmLSRs;
        public int[] distances;

        public Data(byte[] trie,
                byte[] regionToPartitionsIndex, String[] partitionArrays,
                Set<LSR> paradigmLSRs, int[] distances) {
            this.trie = trie;
            this.regionToPartitionsIndex = regionToPartitionsIndex;
            this.partitionArrays = partitionArrays;
            this.paradigmLSRs = paradigmLSRs;
            this.distances = distances;
        }

        private static UResource.Value getValue(UResource.Table table,
                String key, UResource.Value value) {
            if (!table.findValue(key, value)) {
                throw new MissingResourceException(
                        "langInfo.res missing data", "", "match/" + key);
            }
            return value;
        }

        // VisibleForTesting
        public static Data load() throws MissingResourceException {
            ICUResourceBundle langInfo = ICUResourceBundle.getBundleInstance(
                    ICUData.ICU_BASE_NAME, "langInfo",
                    ICUResourceBundle.ICU_DATA_CLASS_LOADER, ICUResourceBundle.OpenType.DIRECT);
            UResource.Value value = langInfo.getValueWithFallback("match");
            UResource.Table matchTable = value.getTable();

            ByteBuffer buffer = getValue(matchTable, "trie", value).getBinary();
            byte[] trie = new byte[buffer.remaining()];
            buffer.get(trie);

            buffer = getValue(matchTable, "regionToPartitions", value).getBinary();
            byte[] regionToPartitions = new byte[buffer.remaining()];
            buffer.get(regionToPartitions);
            if (regionToPartitions.length < LSR.REGION_INDEX_LIMIT) {
                throw new MissingResourceException(
                        "langInfo.res binary data too short", "", "match/regionToPartitions");
            }

            String[] partitions = getValue(matchTable, "partitions", value).getStringArray();

            Set<LSR> paradigmLSRs;
            if (matchTable.findValue("paradigms", value)) {
                String[] paradigms = value.getStringArray();
                // LinkedHashSet for stable order; otherwise a unit test is flaky.
                paradigmLSRs = new LinkedHashSet<>(paradigms.length / 3);
                for (int i = 0; i < paradigms.length; i += 3) {
                    paradigmLSRs.add(new LSR(paradigms[i], paradigms[i + 1], paradigms[i + 2],
                            LSR.DONT_CARE_FLAGS));
                }
            } else {
                paradigmLSRs = Collections.emptySet();
            }

            int[] distances = getValue(matchTable, "distances", value).getIntVector();
            if (distances.length < IX_LIMIT) {
                throw new MissingResourceException(
                        "langInfo.res intvector too short", "", "match/distances");
            }

            return new Data(trie, regionToPartitions, partitions, paradigmLSRs, distances);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) { return true; }
            if (other == null || !getClass().equals(other.getClass())) { return false; }
            Data od = (Data)other;
            return Arrays.equals(trie, od.trie) &&
                    Arrays.equals(regionToPartitionsIndex, od.regionToPartitionsIndex) &&
                    Arrays.equals(partitionArrays, od.partitionArrays) &&
                    paradigmLSRs.equals(od.paradigmLSRs) &&
                    Arrays.equals(distances, od.distances);
        }

        @Override
        public int hashCode() {  // unused; silence ErrorProne
            return 1;
        }
    }

    // VisibleForTesting
    public static final LocaleDistance INSTANCE = new LocaleDistance(Data.load());

    private LocaleDistance(Data data) {
        trie = new BytesTrie(data.trie, 0);
        regionToPartitionsIndex = data.regionToPartitionsIndex;
        partitionArrays = data.partitionArrays;
        paradigmLSRs = data.paradigmLSRs;
        defaultLanguageDistance = data.distances[IX_DEF_LANG_DISTANCE];
        defaultScriptDistance = data.distances[IX_DEF_SCRIPT_DISTANCE];
        defaultRegionDistance = data.distances[IX_DEF_REGION_DISTANCE];
        minRegionDistance = data.distances[IX_MIN_REGION_DISTANCE];

        // For the default demotion value, use the
        // default region distance between unrelated Englishes.
        // Thus, unless demotion is turned off,
        // a mere region difference for one desired locale
        // is as good as a perfect match for the next following desired locale.
        // As of CLDR 36, we have <languageMatch desired="en_*_*" supported="en_*_*" distance="5"/>.
        LSR en = new LSR("en", "Latn", "US", LSR.EXPLICIT_LSR);
        LSR enGB = new LSR("en", "Latn", "GB", LSR.EXPLICIT_LSR);
        int indexAndDistance = getBestIndexAndDistance(en, new LSR[] { enGB }, 1,
                shiftDistance(50), FavorSubtag.LANGUAGE, LocaleMatcher.Direction.WITH_ONE_WAY);
        defaultDemotionPerDesiredLocale  = getDistanceFloor(indexAndDistance);

        if (DEBUG_OUTPUT) {
            System.out.println("*** locale distance");
            System.out.println("defaultLanguageDistance=" + defaultLanguageDistance);
            System.out.println("defaultScriptDistance=" + defaultScriptDistance);
            System.out.println("defaultRegionDistance=" + defaultRegionDistance);
            testOnlyPrintDistanceTable();
        }
    }

    // VisibleForTesting
    public int testOnlyDistance(ULocale desired, ULocale supported,
            int threshold, FavorSubtag favorSubtag) {
        LSR supportedLSR = XLikelySubtags.INSTANCE.makeMaximizedLsrFrom(supported);
        LSR desiredLSR = XLikelySubtags.INSTANCE.makeMaximizedLsrFrom(desired);
        int indexAndDistance = getBestIndexAndDistance(desiredLSR, new LSR[] { supportedLSR }, 1,
                shiftDistance(threshold), favorSubtag, LocaleMatcher.Direction.WITH_ONE_WAY);
        return getDistanceFloor(indexAndDistance);
    }

    /**
     * Finds the supported LSR with the smallest distance from the desired one.
     * Equivalent LSR subtags must be normalized into a canonical form.
     *
     * <p>Returns the index of the lowest-distance supported LSR in the high bits
     * (negative if none has a distance below the threshold),
     * and its distance (0..ABOVE_THRESHOLD) in the low bits.
     */
    public int getBestIndexAndDistance(LSR desired, LSR[] supportedLSRs, int supportedLSRsLength,
            int shiftedThreshold, FavorSubtag favorSubtag, LocaleMatcher.Direction direction) {
        BytesTrie iter = new BytesTrie(trie);
        // Look up the desired language only once for all supported LSRs.
        // Its "distance" is either a match point value of 0, or a non-match negative value.
        // Note: The data builder verifies that there are no <*, supported> or <desired, *> rules.
        int desLangDistance = trieNext(iter, desired.language, false);
        long desLangState = desLangDistance >= 0 && supportedLSRsLength > 1 ? iter.getState64() : 0;
        // Index of the supported LSR with the lowest distance.
        int bestIndex = -1;
        // Cached lookup info from XLikelySubtags.compareLikely().
        int bestLikelyInfo = -1;
        for (int slIndex = 0; slIndex < supportedLSRsLength; ++slIndex) {
            LSR supported = supportedLSRs[slIndex];
            boolean star = false;
            int distance = desLangDistance;
            if (distance >= 0) {
                assert (distance & DISTANCE_IS_FINAL) == 0;
                if (slIndex != 0) {
                    iter.resetToState64(desLangState);
                }
                distance = trieNext(iter, supported.language, true);
            }
            // Note: The data builder verifies that there are no rules with "any" (*) language and
            // real (non *) script or region subtags.
            // This means that if the lookup for either language fails we can use
            // the default distances without further lookups.
            int flags;
            if (distance >= 0) {
                flags = distance & DISTANCE_IS_FINAL_OR_SKIP_SCRIPT;
                distance &= ~DISTANCE_IS_FINAL_OR_SKIP_SCRIPT;
            } else {  // <*, *>
                if (desired.language.equals(supported.language)) {
                    distance = 0;
                } else {
                    distance = defaultLanguageDistance;
                }
                flags = 0;
                star = true;
            }
            assert 0 <= distance && distance <= 100;
            // Round up the shifted threshold (if fraction bits are not 0)
            // for comparison with un-shifted distances until we need fraction bits.
            // (If we simply shifted non-zero fraction bits away, then we might ignore a language
            // when it's really still a micro distance below the threshold.)
            int roundedThreshold = (shiftedThreshold + DISTANCE_FRACTION_MASK) >> DISTANCE_SHIFT;
            // We implement "favor subtag" by reducing the language subtag distance
            // (unscientifically reducing it to a quarter of the normal value),
            // so that the script distance is relatively more important.
            // For example, given a default language distance of 80, we reduce it to 20,
            // which is below the default threshold of 50, which is the default script distance.
            if (favorSubtag == FavorSubtag.SCRIPT) {
                distance >>= 2;
            }
            // Let distance == roundedThreshold pass until the tie-breaker logic
            // at the end of the loop.
            if (distance > roundedThreshold) {
                continue;
            }

            int scriptDistance;
            if (star || flags != 0) {
                if (desired.script.equals(supported.script)) {
                    scriptDistance = 0;
                } else {
                    scriptDistance = defaultScriptDistance;
                }
            } else {
                scriptDistance = getDesSuppScriptDistance(iter, iter.getState64(),
                        desired.script, supported.script);
                flags = scriptDistance & DISTANCE_IS_FINAL;
                scriptDistance &= ~DISTANCE_IS_FINAL;
            }
            distance += scriptDistance;
            if (distance > roundedThreshold) {
                continue;
            }

            if (desired.region.equals(supported.region)) {
                // regionDistance = 0
            } else if (star || (flags & DISTANCE_IS_FINAL) != 0) {
                distance += defaultRegionDistance;
            } else {
                int remainingThreshold = roundedThreshold - distance;
                if (minRegionDistance > remainingThreshold) {
                    continue;
                }

                // From here on we know the regions are not equal.
                // Map each region to zero or more partitions. (zero = one non-matching string)
                // (Each array of single-character partition strings is encoded as one string.)
                // If either side has more than one, then we find the maximum distance.
                // This could be optimized by adding some more structure, but probably not worth it.
                distance += getRegionPartitionsDistance(
                        iter, iter.getState64(),
                        partitionsForRegion(desired),
                        partitionsForRegion(supported),
                        remainingThreshold);
            }
            int shiftedDistance = shiftDistance(distance);
            if (shiftedDistance == 0) {
                // Distinguish between equivalent but originally unequal locales via an
                // additional micro distance.
                shiftedDistance |= (desired.flags ^ supported.flags);
                if (shiftedDistance < shiftedThreshold) {
                    if (direction != LocaleMatcher.Direction.ONLY_TWO_WAY ||
                            // Is there also a match when we swap desired/supported?
                            isMatch(supported, desired, shiftedThreshold, favorSubtag)) {
                        if (shiftedDistance == 0) {
                            return slIndex << INDEX_SHIFT;
                        }
                        bestIndex = slIndex;
                        shiftedThreshold = shiftedDistance;
                        bestLikelyInfo = -1;
                    }
                }
            } else {
                if (shiftedDistance < shiftedThreshold) {
                    if (direction != LocaleMatcher.Direction.ONLY_TWO_WAY ||
                            // Is there also a match when we swap desired/supported?
                            isMatch(supported, desired, shiftedThreshold, favorSubtag)) {
                        bestIndex = slIndex;
                        shiftedThreshold = shiftedDistance;
                        bestLikelyInfo = -1;
                    }
                } else if (shiftedDistance == shiftedThreshold && bestIndex >= 0) {
                    if (direction != LocaleMatcher.Direction.ONLY_TWO_WAY ||
                            // Is there also a match when we swap desired/supported?
                            isMatch(supported, desired, shiftedThreshold, favorSubtag)) {
                        bestLikelyInfo = XLikelySubtags.INSTANCE.compareLikely(
                                supported, supportedLSRs[bestIndex], bestLikelyInfo);
                        if ((bestLikelyInfo & 1) != 0) {
                            // This supported locale matches as well as the previous best match,
                            // and neither matches perfectly,
                            // but this one is "more likely" (has more-default subtags).
                            bestIndex = slIndex;
                        }
                    }
                }
            }
        }
        return bestIndex >= 0 ?
                (bestIndex << INDEX_SHIFT) | shiftedThreshold :
                INDEX_NEG_1 | shiftDistance(ABOVE_THRESHOLD);
    }

    private boolean isMatch(LSR desired, LSR supported,
            int shiftedThreshold, FavorSubtag favorSubtag) {
        return getBestIndexAndDistance(
                desired, new LSR[] { supported }, 1,
                shiftedThreshold, favorSubtag, null) >= 0;
    }

    private static final int getDesSuppScriptDistance(BytesTrie iter, long startState,
            String desired, String supported) {
        // Note: The data builder verifies that there are no <*, supported> or <desired, *> rules.
        int distance = trieNext(iter, desired, false);
        if (distance >= 0) {
            distance = trieNext(iter, supported, true);
        }
        if (distance < 0) {
            BytesTrie.Result result = iter.resetToState64(startState).next('*');  // <*, *>
            assert result.hasValue();
            if (desired.equals(supported)) {
                distance = 0;  // same script
            } else {
                distance = iter.getValue();
                assert distance >= 0;
            }
            if (result == BytesTrie.Result.FINAL_VALUE) {
                distance |= DISTANCE_IS_FINAL;
            }
        }
        return distance;
    }

    private static final int getRegionPartitionsDistance(BytesTrie iter, long startState,
            String desiredPartitions, String supportedPartitions, int threshold) {
        int desLength = desiredPartitions.length();
        int suppLength = supportedPartitions.length();
        if (desLength == 1 && suppLength == 1) {
            // Fastpath for single desired/supported partitions.
            BytesTrie.Result result = iter.next(desiredPartitions.charAt(0) | END_OF_SUBTAG);
            if (result.hasNext()) {
                result = iter.next(supportedPartitions.charAt(0) | END_OF_SUBTAG);
                if (result.hasValue()) {
                    return iter.getValue();
                }
            }
            return getFallbackRegionDistance(iter, startState);
        }

        int regionDistance = 0;
        // Fall back to * only once, not for each pair of partition strings.
        boolean star = false;
        for (int di = 0;;) {
            // Look up each desired-partition string only once,
            // not for each (desired, supported) pair.
            BytesTrie.Result result = iter.next(desiredPartitions.charAt(di++) | END_OF_SUBTAG);
            if (result.hasNext()) {
                long desState = suppLength > 1 ? iter.getState64() : 0;
                for (int si = 0;;) {
                    result = iter.next(supportedPartitions.charAt(si++) | END_OF_SUBTAG);
                    int d;
                    if (result.hasValue()) {
                        d = iter.getValue();
                    } else if (star) {
                        d = 0;
                    } else {
                        d = getFallbackRegionDistance(iter, startState);
                        star = true;
                    }
                    if (d > threshold) {
                        return d;
                    } else if (regionDistance < d) {
                        regionDistance = d;
                    }
                    if (si < suppLength) {
                        iter.resetToState64(desState);
                    } else {
                        break;
                    }
                }
            } else if (!star) {
                int d = getFallbackRegionDistance(iter, startState);
                if (d > threshold) {
                    return d;
                } else if (regionDistance < d) {
                    regionDistance = d;
                }
                star = true;
            }
            if (di < desLength) {
                iter.resetToState64(startState);
            } else {
                break;
            }
        }
        return regionDistance;
    }

    private static final int getFallbackRegionDistance(BytesTrie iter, long startState) {
        BytesTrie.Result result = iter.resetToState64(startState).next('*');  // <*, *>
        assert result.hasValue();
        int distance = iter.getValue();
        assert distance >= 0;
        return distance;
    }

    private static final int trieNext(BytesTrie iter, String s, boolean wantValue) {
        if (s.isEmpty()) {
            return -1;  // no empty subtags in the distance data
        }
        for (int i = 0, end = s.length() - 1;; ++i) {
            int c = s.charAt(i);
            if (i < end) {
                if (!iter.next(c).hasNext()) {
                    return -1;
                }
            } else {
                // last character of this subtag
                BytesTrie.Result result = iter.next(c | END_OF_SUBTAG);
                if (wantValue) {
                    if (result.hasValue()) {
                        int value = iter.getValue();
                        if (result == BytesTrie.Result.FINAL_VALUE) {
                            value |= DISTANCE_IS_FINAL;
                        }
                        return value;
                    }
                } else {
                    if (result.hasNext()) {
                        return 0;
                    }
                }
                return -1;
            }
        }
    }

    @Override
    public String toString() {
        return testOnlyGetDistanceTable().toString();
    }

    private String partitionsForRegion(LSR lsr) {
        // ill-formed region -> one non-matching string
        int pIndex = regionToPartitionsIndex[lsr.regionIndex];
        return partitionArrays[pIndex];
    }

    public boolean isParadigmLSR(LSR lsr) {
        // Linear search for a very short list (length 6 as of 2019),
        // because we look for equivalence not equality, and
        // HashSet does not support customizing equality.
        // If there are many paradigm LSRs we should revisit this.
        assert paradigmLSRs.size() <= 15;
        for (LSR plsr : paradigmLSRs) {
            if (lsr.isEquivalentTo(plsr)) {
                return true;
            }
        }
        return false;
    }

    // VisibleForTesting
    public int getDefaultScriptDistance() {
        return defaultScriptDistance;
    }

    int getDefaultRegionDistance() {
        return defaultRegionDistance;
    }

    public int getDefaultDemotionPerDesiredLocale() {
        return defaultDemotionPerDesiredLocale;
    }

    // VisibleForTesting
    public Map<String, Integer> testOnlyGetDistanceTable() {
        Map<String, Integer> map = new TreeMap<>();
        StringBuilder sb = new StringBuilder();
        for (BytesTrie.Entry entry : trie) {
            sb.setLength(0);
            int length = entry.bytesLength();
            for (int i = 0; i < length; ++i) {
                byte b = entry.byteAt(i);
                if (b == '*') {
                    // One * represents a (desired, supported) = (ANY, ANY) pair.
                    sb.append("*-*-");
                } else {
                    if (b >= 0) {
                        sb.append((char) b);
                    } else {  // end of subtag
                        sb.append((char) (b & 0x7f)).append('-');
                    }
                }
            }
            assert sb.length() > 0 && sb.charAt(sb.length() - 1) == '-';
            sb.setLength(sb.length() - 1);
            map.put(sb.toString(), entry.value);
        }
        return map;
    }

    // VisibleForTesting
    public void testOnlyPrintDistanceTable() {
        for (Map.Entry<String, Integer> mapping : testOnlyGetDistanceTable().entrySet()) {
            String suffix = "";
            int value = mapping.getValue();
            if ((value & DISTANCE_SKIP_SCRIPT) != 0) {
                value &= ~DISTANCE_SKIP_SCRIPT;
                suffix = " skip script";
            }
            System.out.println(mapping.getKey() + '=' + value + suffix);
        }
    }
}
