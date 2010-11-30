/*
 * Copyright (c) 2002, 2005, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.java.util.jar.pack;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * Heuristic chooser of basic encodings.
 * Runs "zip" to measure the apparent information content after coding.
 * @author John Rose
 */
class CodingChooser implements Constants {
    int verbose;
    int effort;
    boolean optUseHistogram = true;
    boolean optUsePopulationCoding = true;
    boolean optUseAdaptiveCoding = true;
    boolean disablePopCoding;
    boolean disableRunCoding;
    boolean topLevel = true;

    // Derived from effort; >1 (<1) means try more (less) experiments
    // when looking to beat a best score.
    double fuzz;

    Coding[] allCodingChoices;
    Choice[] choices;
    ByteArrayOutputStream context;
    CodingChooser popHelper;
    CodingChooser runHelper;

    Random stress;  // If not null, stress mode oracle.

    // Element in sorted set of coding choices:
    static
    class Choice {
        final Coding coding;
        final int index;       // index in choices
        final int[] distance;  // cache of distance
        Choice(Coding coding, int index, int[] distance) {
            this.coding   = coding;
            this.index    = index;
            this.distance = distance;
        }
        // These variables are reset and reused:
        int searchOrder; // order in which it is checked
        int minDistance; // min distance from already-checked choices
        int zipSize;     // size of encoding in sample, zipped output
        int byteSize;    // size of encoding in sample (debug only)
        int histSize;    // size of encoding, according to histogram

        void reset() {
            searchOrder = Integer.MAX_VALUE;
            minDistance = Integer.MAX_VALUE;
            zipSize = byteSize = histSize = -1;
        }

        boolean isExtra() {
            return index < 0;
        }

        public String toString() {
            return stringForDebug();
        }

        private String stringForDebug() {
            String s = "";
            if (searchOrder < Integer.MAX_VALUE)
                s += " so: "+searchOrder;
            if (minDistance < Integer.MAX_VALUE)
                s += " md: "+minDistance;
            if (zipSize > 0)
                s += " zs: "+zipSize;
            if (byteSize > 0)
                s += " bs: "+byteSize;
            if (histSize > 0)
                s += " hs: "+histSize;
            return "Choice["+index+"] "+s+" "+coding;
        }
    }

    CodingChooser(int effort, Coding[] allCodingChoices) {
        PropMap p200 = Utils.currentPropMap();
        if (p200 != null) {
            this.verbose
                = Math.max(p200.getInteger(Utils.DEBUG_VERBOSE),
                           p200.getInteger(Utils.COM_PREFIX+"verbose.coding"));
            this.optUseHistogram
                = !p200.getBoolean(Utils.COM_PREFIX+"no.histogram");
            this.optUsePopulationCoding
                = !p200.getBoolean(Utils.COM_PREFIX+"no.population.coding");
            this.optUseAdaptiveCoding
                = !p200.getBoolean(Utils.COM_PREFIX+"no.adaptive.coding");
            int stress
                = p200.getInteger(Utils.COM_PREFIX+"stress.coding");
            if (stress != 0)
                this.stress = new Random(stress);
        }

        this.effort = effort;
        // The following line "makes sense" but is too much
        // work for a simple heuristic.
        //if (effort > 5)  zipDef.setLevel(effort);

        this.allCodingChoices = allCodingChoices;

        // If effort = 9, look carefully at any solution
        // whose initial metrics are within 1% of the best
        // so far.  If effort = 1, look carefully only at
        // solutions whose initial metrics promise a 1% win.
        this.fuzz = 1 + (0.0025 * (effort-MID_EFFORT));

        int nc = 0;
        for (int i = 0; i < allCodingChoices.length; i++) {
            if (allCodingChoices[i] == null)  continue;
            nc++;
        }
        choices = new Choice[nc];
        nc = 0;
        for (int i = 0; i < allCodingChoices.length; i++) {
            if (allCodingChoices[i] == null)  continue;
            int[] distance = new int[choices.length];
            choices[nc++] = new Choice(allCodingChoices[i], i, distance);
        }
        for (int i = 0; i < choices.length; i++) {
            Coding ci = choices[i].coding;
            assert(ci.distanceFrom(ci) == 0);
            for (int j = 0; j < i; j++) {
                Coding cj = choices[j].coding;
                int dij = ci.distanceFrom(cj);
                assert(dij > 0);
                assert(dij == cj.distanceFrom(ci));
                choices[i].distance[j] = dij;
                choices[j].distance[i] = dij;
            }
        }
    }

    Choice makeExtraChoice(Coding coding) {
        int[] distance = new int[choices.length];
        for (int i = 0; i < distance.length; i++) {
            Coding ci = choices[i].coding;
            int dij = coding.distanceFrom(ci);
            assert(dij > 0);
            assert(dij == ci.distanceFrom(coding));
            distance[i] = dij;
        }
        Choice c = new Choice(coding, -1, distance);
        c.reset();
        return c;
    }

    ByteArrayOutputStream getContext() {
        if (context == null)
            context = new ByteArrayOutputStream(1 << 16);
        return context;
    }

    // These variables are reset and reused:
    private int[] values;
    private int start, end;  // slice of values
    private int[] deltas;
    private int min, max;
    private Histogram vHist;
    private Histogram dHist;
    private int searchOrder;
    private Choice regularChoice;
    private Choice bestChoice;
    private CodingMethod bestMethod;
    private int bestByteSize;
    private int bestZipSize;
    private int targetSize;   // fuzzed target byte size

    private void reset(int[] values, int start, int end) {
        this.values = values;
        this.start = start;
        this.end = end;
        this.deltas = null;
        this.min = Integer.MAX_VALUE;
        this.max = Integer.MIN_VALUE;
        this.vHist = null;
        this.dHist = null;
        this.searchOrder = 0;
        this.regularChoice = null;
        this.bestChoice = null;
        this.bestMethod = null;
        this.bestZipSize = Integer.MAX_VALUE;
        this.bestByteSize = Integer.MAX_VALUE;
        this.targetSize = Integer.MAX_VALUE;
    }

    public static final int MIN_EFFORT = 1;
    public static final int MID_EFFORT = 5;
    public static final int MAX_EFFORT = 9;

    public static final int POP_EFFORT = MID_EFFORT-1;
    public static final int RUN_EFFORT = MID_EFFORT-2;

    public static final int BYTE_SIZE = 0;
    public static final int ZIP_SIZE = 1;

    CodingMethod choose(int[] values, int start, int end, Coding regular, int[] sizes) {
        // Save the value array
        reset(values, start, end);

        if (effort <= MIN_EFFORT || start >= end) {
            if (sizes != null) {
                int[] computed = computeSizePrivate(regular);
                sizes[BYTE_SIZE] = computed[BYTE_SIZE];
                sizes[ZIP_SIZE]  = computed[ZIP_SIZE];
            }
            return regular;
        }

        if (optUseHistogram) {
            getValueHistogram();
            getDeltaHistogram();
        }

        for (int i = start; i < end; i++) {
            int val = values[i];
            if (min > val)  min = val;
            if (max < val)  max = val;
        }

        // Find all the preset choices that might be worth looking at:
        int numChoices = markUsableChoices(regular);

        if (stress != null) {
            // Make a random choice.
            int rand = stress.nextInt(numChoices*2 + 4);
            CodingMethod coding = null;
            for (int i = 0; i < choices.length; i++) {
                Choice c = choices[i];
                if (c.searchOrder >= 0 && rand-- == 0) {
                    coding = c.coding;
                    break;
                }
            }
            if (coding == null) {
                if ((rand & 7) != 0) {
                    coding = regular;
                } else {
                    // Pick a totally random coding 6% of the time.
                    coding = stressCoding(min, max);
                }
            }
            if (!disablePopCoding
                && optUsePopulationCoding
                && effort >= POP_EFFORT) {
                coding = stressPopCoding(coding);
            }
            if (!disableRunCoding
                && optUseAdaptiveCoding
                && effort >= RUN_EFFORT) {
                coding = stressAdaptiveCoding(coding);
            }
            return coding;
        }

        double searchScale = 1.0;
        for (int x = effort; x < MAX_EFFORT; x++) {
            searchScale /= 1.414;  // every 2 effort points doubles work
        }
        int searchOrderLimit = (int)Math.ceil( numChoices * searchScale );

        // Start by evaluating the "regular" choice.
        bestChoice = regularChoice;
        evaluate(regularChoice);
        int maxd = updateDistances(regularChoice);

        // save these first-cut numbers for later
        int zipSize1 = bestZipSize;
        int byteSize1 = bestByteSize;

        if (regularChoice.coding == regular && topLevel) {
            // Give credit for being the default; no band header is needed.
            // Rather than increasing every other size value by the band
            // header amount, we decrement this one metric, to give it an edge.
            // Decreasing zipSize by a byte length is conservatively correct,
            // especially considering that the escape byte is not likely to
            // zip well with other bytes in the band.
            int X = BandStructure.encodeEscapeValue(_meta_canon_max, regular);
            if (regular.canRepresentSigned(X)) {
                int Xlen = regular.getLength(X);  // band coding header
                //regularChoice.histSize -= Xlen; // keep exact byteSize
                //regularChoice.byteSize -= Xlen; // keep exact byteSize
                regularChoice.zipSize -= Xlen;
                bestByteSize = regularChoice.byteSize;
                bestZipSize = regularChoice.zipSize;
            }
        }

        int dscale = 1;
        // Continually select a new choice to evaluate.
        while (searchOrder < searchOrderLimit) {
            Choice nextChoice;
            if (dscale > maxd)  dscale = 1;  // cycle dscale values!
            int dhi = maxd / dscale;
            int dlo = maxd / (dscale *= 2) + 1;
            nextChoice = findChoiceNear(bestChoice, dhi, dlo);
            if (nextChoice == null)  continue;
            assert(nextChoice.coding.canRepresent(min, max));
            evaluate(nextChoice);
            int nextMaxd = updateDistances(nextChoice);
            if (nextChoice == bestChoice) {
                maxd = nextMaxd;
                if (verbose > 5)  Utils.log.info("maxd = "+maxd);
            }
        }

        // Record best "plain coding" choice.
        Coding plainBest = bestChoice.coding;
        assert(plainBest == bestMethod);

        if (verbose > 2) {
            Utils.log.info("chooser: plain result="+bestChoice+" after "+bestChoice.searchOrder+" rounds, "+(regularChoice.zipSize-bestZipSize)+" fewer bytes than regular "+regular);
        }
        bestChoice = null;

        if (!disablePopCoding
            && optUsePopulationCoding
            && effort >= POP_EFFORT
            && bestMethod instanceof Coding) {
            tryPopulationCoding(plainBest);
        }

        if (!disableRunCoding
            && optUseAdaptiveCoding
            && effort >= RUN_EFFORT
            && bestMethod instanceof Coding) {
            tryAdaptiveCoding(plainBest);
        }

        // Pass back the requested information:
        if (sizes != null) {
            sizes[BYTE_SIZE] = bestByteSize;
            sizes[ZIP_SIZE]  = bestZipSize;
        }
        if (verbose > 1) {
            Utils.log.info("chooser: result="+bestMethod+" "+
                             (zipSize1-bestZipSize)+
                             " fewer bytes than regular "+regular+
                             "; win="+pct(zipSize1-bestZipSize, zipSize1));
        }
        CodingMethod bestMethod = this.bestMethod;
        reset(null, 0, 0);  // for GC
        return bestMethod;
    }
    CodingMethod choose(int[] values, int start, int end, Coding regular) {
        return choose(values, start, end, regular, null);
    }
    CodingMethod choose(int[] values, Coding regular, int[] sizes) {
        return choose(values, 0, values.length, regular, sizes);
    }
    CodingMethod choose(int[] values, Coding regular) {
        return choose(values, 0, values.length, regular, null);
    }

    private int markUsableChoices(Coding regular) {
        int numChoices = 0;
        for (int i = 0; i < choices.length; i++) {
            Choice c = choices[i];
            c.reset();
            if (!c.coding.canRepresent(min, max)) {
                // Mark as already visited:
                c.searchOrder = -1;
                if (verbose > 1 && c.coding == regular) {
                    Utils.log.info("regular coding cannot represent ["+min+".."+max+"]: "+regular);
                }
                continue;
            }
            if (c.coding == regular)
                regularChoice = c;
            numChoices++;
        }
        if (regularChoice == null && regular.canRepresent(min, max)) {
            regularChoice = makeExtraChoice(regular);
            if (verbose > 1) {
                Utils.log.info("*** regular choice is extra: "+regularChoice.coding);
            }
        }
        if (regularChoice == null) {
            for (int i = 0; i < choices.length; i++) {
                Choice c = choices[i];
                if (c.searchOrder != -1) {
                    regularChoice = c;  // arbitrary pick
                    break;
                }
            }
            if (verbose > 1) {
                Utils.log.info("*** regular choice does not apply "+regular);
                Utils.log.info("    using instead "+regularChoice.coding);
            }
        }
        if (verbose > 2) {
            Utils.log.info("chooser: #choices="+numChoices+" ["+min+".."+max+"]");
            if (verbose > 4) {
                for (int i = 0; i < choices.length; i++) {
                    Choice c = choices[i];
                    if (c.searchOrder >= 0)
                        Utils.log.info("  "+c);
                }
            }
        }
        return numChoices;
    }

    // Find an arbitrary choice at least dlo away from a previously
    // evaluated choices, and at most dhi.  Try also to regulate its
    // min distance to all previously evaluated choices, in this range.
    private Choice findChoiceNear(Choice near, int dhi, int dlo) {
        if (verbose > 5)
            Utils.log.info("findChoice "+dhi+".."+dlo+" near: "+near);
        int[] distance = near.distance;
        Choice found = null;
        for (int i = 0; i < choices.length; i++) {
            Choice c = choices[i];
            if (c.searchOrder < searchOrder)
                continue;  // already searched
            // Distance from "near" guy must be in bounds:
            if (distance[i] >= dlo && distance[i] <= dhi) {
                // Try also to keep min-distance from other guys in bounds:
                if (c.minDistance >= dlo && c.minDistance <= dhi) {
                    if (verbose > 5)
                        Utils.log.info("findChoice => good "+c);
                    return c;
                }
                found = c;
            }
        }
        if (verbose > 5)
            Utils.log.info("findChoice => found "+found);
        return found;
    }

    private void evaluate(Choice c) {
        assert(c.searchOrder == Integer.MAX_VALUE);
        c.searchOrder = searchOrder++;
        boolean mustComputeSize;
        if (c == bestChoice || c.isExtra()) {
            mustComputeSize = true;
        } else if (optUseHistogram) {
            Histogram hist = getHistogram(c.coding.isDelta());
            c.histSize = (int)Math.ceil(hist.getBitLength(c.coding) / 8);
            c.byteSize = c.histSize;
            mustComputeSize = (c.byteSize <= targetSize);
        } else {
            mustComputeSize = true;
        }
        if (mustComputeSize) {
            int[] sizes = computeSizePrivate(c.coding);
            c.byteSize = sizes[BYTE_SIZE];
            c.zipSize  = sizes[ZIP_SIZE];
            if (noteSizes(c.coding, c.byteSize, c.zipSize))
                bestChoice = c;
        }
        if (c.histSize >= 0) {
            assert(c.byteSize == c.histSize);  // models should agree
        }
        if (verbose > 4) {
            Utils.log.info("evaluated "+c);
        }
    }

    private boolean noteSizes(CodingMethod c, int byteSize, int zipSize) {
        assert(zipSize > 0 && byteSize > 0);
        boolean better = (zipSize < bestZipSize);
        if (verbose > 3)
            Utils.log.info("computed size "+c+" "+byteSize+"/zs="+zipSize+
                             ((better && bestMethod != null)?
                              (" better by "+
                               pct(bestZipSize - zipSize, zipSize)): ""));
        if (better) {
            bestMethod = c;
            bestZipSize = zipSize;
            bestByteSize = byteSize;
            targetSize = (int)(byteSize * fuzz);
            return true;
        } else {
            return false;
        }
    }


    private int updateDistances(Choice c) {
        // update all minDistance values in still unevaluated choices
        int[] distance = c.distance;
        int maxd = 0;  // how far is c from everybody else?
        for (int i = 0; i < choices.length; i++) {
            Choice c2 = choices[i];
            if (c2.searchOrder < searchOrder)
                continue;
            int d = distance[i];
            if (verbose > 5)
                Utils.log.info("evaluate dist "+d+" to "+c2);
            int mind = c2.minDistance;
            if (mind > d)
                c2.minDistance = mind = d;
            if (maxd < d)
                maxd = d;
        }
        // Now maxd has the distance of the farthest outlier
        // from all evaluated choices.
        if (verbose > 5)
            Utils.log.info("evaluate maxd => "+maxd);
        return maxd;
    }

    // Compute the coded size of a sequence of values.
    // The first int is the size in uncompressed bytes.
    // The second is an estimate of the compressed size of these bytes.
    public void computeSize(CodingMethod c, int[] values, int start, int end, int[] sizes) {
        if (end <= start) {
            sizes[BYTE_SIZE] = sizes[ZIP_SIZE] = 0;
            return;
        }
        try {
            resetData();
            c.writeArrayTo(byteSizer, values, start, end);
            sizes[BYTE_SIZE] = getByteSize();
            sizes[ZIP_SIZE] = getZipSize();
        } catch (IOException ee) {
            throw new RuntimeException(ee); // cannot happen
        }
    }
    public void computeSize(CodingMethod c, int[] values, int[] sizes) {
        computeSize(c, values, 0, values.length, sizes);
    }
    public int[] computeSize(CodingMethod c, int[] values, int start, int end) {
        int[] sizes = { 0, 0 };
        computeSize(c, values, start, end, sizes);
        return sizes;
    }
    public int[] computeSize(CodingMethod c, int[] values) {
        return computeSize(c, values, 0, values.length);
    }
    // This version uses the implicit local arguments
    private int[] computeSizePrivate(CodingMethod c) {
        int[] sizes = { 0, 0 };
        computeSize(c, values, start, end, sizes);
        return sizes;
    }
    public int computeByteSize(CodingMethod cm, int[] values, int start, int end) {
        int len = end-start;
        if (len < 0) {
            return 0;
        }
        if (cm instanceof Coding) {
            Coding c = (Coding) cm;
            int size = c.getLength(values, start, end);
            int size2;
            assert(size == (size2=countBytesToSizer(cm, values, start, end)))
                : (cm+" : "+size+" != "+size2);
            return size;
        }
        return countBytesToSizer(cm, values, start, end);
    }
    private int countBytesToSizer(CodingMethod cm, int[] values, int start, int end) {
        try {
            byteOnlySizer.reset();
            cm.writeArrayTo(byteOnlySizer, values, start, end);
            return byteOnlySizer.getSize();
        } catch (IOException ee) {
            throw new RuntimeException(ee); // cannot happen
        }
    }

    int[] getDeltas(int min, int max) {
        if ((min|max) != 0)
            return Coding.makeDeltas(values, start, end, min, max);
        if (deltas == null) {
            deltas = Coding.makeDeltas(values, start, end, 0, 0);
        }
        return deltas;
    }
    Histogram getValueHistogram() {
        if (vHist == null) {
            vHist = new Histogram(values, start, end);
            if (verbose > 3) {
                vHist.print("vHist", System.out);
            } else if (verbose > 1) {
                vHist.print("vHist", null, System.out);
            }
        }
        return vHist;
    }
    Histogram getDeltaHistogram() {
        if (dHist == null) {
            dHist = new Histogram(getDeltas(0, 0));
            if (verbose > 3) {
                dHist.print("dHist", System.out);
            } else if (verbose > 1) {
                dHist.print("dHist", null, System.out);
            }
        }
        return dHist;
    }
    Histogram getHistogram(boolean isDelta) {
        return isDelta ? getDeltaHistogram(): getValueHistogram();
    }

    private void tryPopulationCoding(Coding plainCoding) {
        // assert(plainCoding.canRepresent(min, max));
        Histogram hist = getValueHistogram();
        // Start with "reasonable" default codings.
        final int approxL = 64;
        Coding favoredCoding = plainCoding.getValueCoding();
        Coding tokenCoding = BandStructure.UNSIGNED5.setL(approxL);
        Coding unfavoredCoding = plainCoding.getValueCoding();
        // There's going to be a band header.  Estimate conservatively large.
        final int BAND_HEADER = 4;
        // Keep a running model of the predicted sizes of the F/T/U sequences.
        int currentFSize;
        int currentTSize;
        int currentUSize;
        // Start by assuming a degenerate favored-value length of 0,
        // which looks like a bunch of zero tokens followed by the
        // original sequence.
        // The {F} list ends with a repeated F value; find worst case:
        currentFSize =
            BAND_HEADER + Math.max(favoredCoding.getLength(min),
                                   favoredCoding.getLength(max));
        // The {T} list starts out a bunch of zeros, each of length 1.
        final int ZERO_LEN = tokenCoding.getLength(0);
        currentTSize = ZERO_LEN * (end-start);
        // The {U} list starts out a copy of the plainCoding:
        currentUSize = (int) Math.ceil(hist.getBitLength(unfavoredCoding) / 8);

        int bestPopSize = (currentFSize + currentTSize + currentUSize);
        int bestPopFVC  = 0;

        // Record all the values, in decreasing order of favor.
        int[] allFavoredValues = new int[1+hist.getTotalLength()];
        //int[] allPopSizes    = new int[1+hist.getTotalLength()];

        // What sizes are "interesting"?
        int targetLowFVC = -1;
        int targetHighFVC = -1;

        // For each length, adjust the currentXSize model, and look for a win.
        int[][] matrix = hist.getMatrix();
        int mrow = -1;
        int mcol = 1;
        int mrowFreq = 0;
        for (int fvcount = 1; fvcount <= hist.getTotalLength(); fvcount++) {
            // The {F} list gets an additional member.
            // Take it from the end of the current matrix row.
            // (It's the end, so that we get larger favored values first.)
            if (mcol == 1) {
                mrow += 1;
                mrowFreq = matrix[mrow][0];
                mcol = matrix[mrow].length;
            }
            int thisValue = matrix[mrow][--mcol];
            allFavoredValues[fvcount] = thisValue;
            int thisVLen = favoredCoding.getLength(thisValue);
            currentFSize += thisVLen;
            // The token list replaces occurrences of zero with a new token:
            int thisVCount = mrowFreq;
            int thisToken = fvcount;
            currentTSize += (tokenCoding.getLength(thisToken)
                             - ZERO_LEN) * thisVCount;
            // The unfavored list loses occurrences of the newly favored value.
            // (This is the whole point of the exercise!)
            currentUSize -= thisVLen * thisVCount;
            int currentSize = (currentFSize + currentTSize + currentUSize);
            //allPopSizes[fvcount] = currentSize;
            if (bestPopSize > currentSize) {
                if (currentSize <= targetSize) {
                    targetHighFVC = fvcount;
                    if (targetLowFVC < 0)
                        targetLowFVC = fvcount;
                    if (verbose > 4)
                        Utils.log.info("better pop-size at fvc="+fvcount+
                                         " by "+pct(bestPopSize-currentSize,
                                                    bestPopSize));
                }
                bestPopSize = currentSize;
                bestPopFVC = fvcount;
            }
        }
        if (targetLowFVC < 0) {
            if (verbose > 1) {
                // Complete loss.
                if (verbose > 1)
                    Utils.log.info("no good pop-size; best was "+
                                     bestPopSize+" at "+bestPopFVC+
                                     " worse by "+
                                     pct(bestPopSize-bestByteSize,
                                         bestByteSize));
            }
            return;
        }
        if (verbose > 1)
            Utils.log.info("initial best pop-size at fvc="+bestPopFVC+
                             " in ["+targetLowFVC+".."+targetHighFVC+"]"+
                             " by "+pct(bestByteSize-bestPopSize,
                                        bestByteSize));
        int oldZipSize = bestZipSize;
        // Now close onto a specific coding, testing more rigorously
        // with the zipSize metric.
        // Questions to decide:
        //   1. How many favored values?
        //   2. What token coding (TC)?
        //   3. Sort favored values by value within length brackets?
        //   4. What favored coding?
        //   5. What unfavored coding?
        // Steps 1/2/3 are interdependent, and may be iterated.
        // Steps 4 and 5 may be decided independently afterward.
        int[] LValuesCoded = PopulationCoding.LValuesCoded;
        ArrayList bestFits = new ArrayList();
        ArrayList fullFits = new ArrayList();
        ArrayList longFits = new ArrayList();
        final int PACK_TO_MAX_S = 1;
        if (bestPopFVC <= 255) {
            bestFits.add(BandStructure.BYTE1);
        } else {
            int bestB = Coding.B_MAX;
            boolean doFullAlso = (effort > POP_EFFORT);
            if (doFullAlso)
                fullFits.add(BandStructure.BYTE1.setS(PACK_TO_MAX_S));
            for (int i = LValuesCoded.length-1; i >= 1; i--) {
                int L = LValuesCoded[i];
                Coding c0 = PopulationCoding.fitTokenCoding(targetLowFVC,  L);
                Coding c1 = PopulationCoding.fitTokenCoding(bestPopFVC,    L);
                Coding c3 = PopulationCoding.fitTokenCoding(targetHighFVC, L);
                if (c1 != null) {
                    if (!bestFits.contains(c1))
                        bestFits.add(c1);
                    if (bestB > c1.B())
                        bestB = c1.B();
                }
                if (doFullAlso) {
                    if (c3 == null)  c3 = c1;
                    for (int B = c0.B(); B <= c3.B(); B++) {
                        if (B == c1.B())  continue;
                        if (B == 1)  continue;
                        Coding c2 = c3.setB(B).setS(PACK_TO_MAX_S);
                        if (!fullFits.contains(c2))
                            fullFits.add(c2);
                    }
                }
            }
            // interleave all B greater than bestB with best and full fits
            for (Iterator i = bestFits.iterator(); i.hasNext(); ) {
                Coding c = (Coding) i.next();
                if (c.B() > bestB) {
                    i.remove();
                    longFits.add(0, c);
                }
            }
        }
        ArrayList allFits = new ArrayList();
        for (Iterator i = bestFits.iterator(),
                      j = fullFits.iterator(),
                      k = longFits.iterator();
             i.hasNext() || j.hasNext() || k.hasNext(); ) {
            if (i.hasNext())  allFits.add(i.next());
            if (j.hasNext())  allFits.add(j.next());
            if (k.hasNext())  allFits.add(k.next());
        }
        bestFits.clear();
        fullFits.clear();
        longFits.clear();
        int maxFits = allFits.size();
        if (effort == POP_EFFORT)
            maxFits = 2;
        else if (maxFits > 4) {
            maxFits -= 4;
            maxFits = (maxFits * (effort-POP_EFFORT)
                       ) / (MAX_EFFORT-POP_EFFORT);
            maxFits += 4;
        }
        if (allFits.size() > maxFits) {
            if (verbose > 4)
                Utils.log.info("allFits before clip: "+allFits);
            allFits.subList(maxFits, allFits.size()).clear();
        }
        if (verbose > 3)
            Utils.log.info("allFits: "+allFits);
        for (Iterator i = allFits.iterator(); i.hasNext(); ) {
            Coding tc = (Coding) i.next();
            boolean packToMax = false;
            if (tc.S() == PACK_TO_MAX_S) {
                // Kludge:  setS(PACK_TO_MAX_S) means packToMax here.
                packToMax = true;
                tc = tc.setS(0);
            }
            int fVlen;
            if (!packToMax) {
                fVlen = bestPopFVC;
                assert(tc.umax() >= fVlen);
                assert(tc.B() == 1 || tc.setB(tc.B()-1).umax() < fVlen);
            } else {
                fVlen = Math.min(tc.umax(), targetHighFVC);
                if (fVlen < targetLowFVC)
                    continue;
                if (fVlen == bestPopFVC)
                    continue;  // redundant test
            }
            PopulationCoding pop = new PopulationCoding();
            pop.setHistogram(hist);
            pop.setL(tc.L());
            pop.setFavoredValues(allFavoredValues, fVlen);
            assert(pop.tokenCoding == tc);  // predict correctly
            pop.resortFavoredValues();
            int[] tcsizes =
                computePopSizePrivate(pop,
                                      favoredCoding, unfavoredCoding);
            noteSizes(pop, tcsizes[BYTE_SIZE], BAND_HEADER+tcsizes[ZIP_SIZE]);
        }
        if (verbose > 3) {
            Utils.log.info("measured best pop, size="+bestByteSize+
                             "/zs="+bestZipSize+
                             " better by "+
                             pct(oldZipSize-bestZipSize, oldZipSize));
            if (bestZipSize < oldZipSize) {
                Utils.log.info(">>> POP WINS BY "+
                                 (oldZipSize - bestZipSize));
            }
        }
    }

    private
    int[] computePopSizePrivate(PopulationCoding pop,
                                Coding favoredCoding,
                                Coding unfavoredCoding) {
        if (popHelper == null) {
            popHelper = new CodingChooser(effort, allCodingChoices);
            if (stress != null)
                popHelper.addStressSeed(stress.nextInt());
            popHelper.topLevel = false;
            popHelper.verbose -= 1;
            popHelper.disablePopCoding = true;
            popHelper.disableRunCoding = this.disableRunCoding;
            if (effort < MID_EFFORT)
                // No nested run codings.
                popHelper.disableRunCoding = true;
        }
        int fVlen = pop.fVlen;
        if (verbose > 2) {
            Utils.log.info("computePopSizePrivate fvlen="+fVlen+
                             " tc="+pop.tokenCoding);
            Utils.log.info("{ //BEGIN");
        }

        // Find good coding choices for the token and unfavored sequences.
        int[] favoredValues = pop.fValues;
        int[][] vals = pop.encodeValues(values, start, end);
        int[] tokens = vals[0];
        int[] unfavoredValues = vals[1];
        if (verbose > 2)
            Utils.log.info("-- refine on fv["+fVlen+"] fc="+favoredCoding);
        pop.setFavoredCoding(popHelper.choose(favoredValues, 1, 1+fVlen, favoredCoding));
        if (pop.tokenCoding instanceof Coding &&
            (stress == null || stress.nextBoolean())) {
            if (verbose > 2)
                Utils.log.info("-- refine on tv["+tokens.length+"] tc="+pop.tokenCoding);
            CodingMethod tc = popHelper.choose(tokens, (Coding) pop.tokenCoding);
            if (tc != pop.tokenCoding) {
                if (verbose > 2)
                    Utils.log.info(">>> refined tc="+tc);
                pop.setTokenCoding(tc);
            }
        }
        if (unfavoredValues.length == 0)
            pop.setUnfavoredCoding(null);
        else {
            if (verbose > 2)
                Utils.log.info("-- refine on uv["+unfavoredValues.length+"] uc="+pop.unfavoredCoding);
            pop.setUnfavoredCoding(popHelper.choose(unfavoredValues, unfavoredCoding));
        }
        if (verbose > 3) {
            Utils.log.info("finish computePopSizePrivate fvlen="+fVlen+
                             " fc="+pop.favoredCoding+
                             " tc="+pop.tokenCoding+
                             " uc="+pop.unfavoredCoding);
            //pop.hist.print("pop-hist", null, System.out);
            StringBuffer sb = new StringBuffer();
            sb.append("fv = {");
            for (int i = 1; i <= fVlen; i++) {
                if ((i % 10) == 0)
                    sb.append('\n');
                sb.append(" ").append(favoredValues[i]);
            }
            sb.append('\n');
            sb.append("}");
            Utils.log.info(sb.toString());
        }
        if (verbose > 2) {
            Utils.log.info("} //END");
        }
        if (stress != null) {
            return null;  // do not bother with size computation
        }
        int[] sizes;
        try {
            resetData();
            // Write the array of favored values.
            pop.writeSequencesTo(byteSizer, tokens, unfavoredValues);
            sizes = new int[] { getByteSize(), getZipSize() };
        } catch (IOException ee) {
            throw new RuntimeException(ee); // cannot happen
        }
        int[] checkSizes = null;
        assert((checkSizes = computeSizePrivate(pop)) != null);
        assert(checkSizes[BYTE_SIZE] == sizes[BYTE_SIZE])
            : (checkSizes[BYTE_SIZE]+" != "+sizes[BYTE_SIZE]);
        return sizes;
    }

    private void tryAdaptiveCoding(Coding plainCoding) {
        int oldZipSize = bestZipSize;
        // Scan the value sequence, determining whether an interesting
        // run occupies too much space.  ("Too much" means, say 5% more
        // than the average integer size of the band as a whole.)
        // Try to find a better coding for those segments.
        int   start  = this.start;
        int   end    = this.end;
        int[] values = this.values;
        int len = end-start;
        if (plainCoding.isDelta()) {
            values = getDeltas(0,0); //%%% not quite right!
            start = 0;
            end = values.length;
        }
        int[] sizes = new int[len+1];
        int fillp = 0;
        int totalSize = 0;
        for (int i = start; i < end; i++) {
            int val = values[i];
            sizes[fillp++] = totalSize;
            int size = plainCoding.getLength(val);
            assert(size < Integer.MAX_VALUE);
            //System.out.println("len "+val+" = "+size);
            totalSize += size;
        }
        sizes[fillp++] = totalSize;
        assert(fillp == sizes.length);
        double avgSize = (double)totalSize / len;
        double sizeFuzz;
        double sizeFuzz2;
        double sizeFuzz3;
        if (effort >= MID_EFFORT) {
            if (effort > MID_EFFORT+1)
                sizeFuzz = 1.001;
            else
                sizeFuzz = 1.003;
        } else {
            if (effort > RUN_EFFORT)
                sizeFuzz = 1.01;
            else
                sizeFuzz = 1.03;
        }
        // for now:
        sizeFuzz *= sizeFuzz; // double the thresh
        sizeFuzz2 = (sizeFuzz*sizeFuzz);
        sizeFuzz3 = (sizeFuzz*sizeFuzz*sizeFuzz);
        // Find some mesh scales we like.
        double[] dmeshes = new double[1 + (effort-RUN_EFFORT)];
        double logLen = Math.log(len);
        for (int i = 0; i < dmeshes.length; i++) {
            dmeshes[i] = Math.exp(logLen*(i+1)/(dmeshes.length+1));
        }
        int[] meshes = new int[dmeshes.length];
        int mfillp = 0;
        for (int i = 0; i < dmeshes.length; i++) {
            int m = (int)Math.round(dmeshes[i]);
            m = AdaptiveCoding.getNextK(m-1);
            if (m <= 0 || m >= len)  continue;
            if (mfillp > 0 && m == meshes[mfillp-1])  continue;
            meshes[mfillp++] = m;
        }
        meshes = BandStructure.realloc(meshes, mfillp);
        // There's going to be a band header.  Estimate conservatively large.
        final int BAND_HEADER = 4; // op, KB, A, B
        // Threshold values for a "too big" mesh.
        int[]    threshes = new int[meshes.length];
        double[] fuzzes   = new double[meshes.length];
        for (int i = 0; i < meshes.length; i++) {
            int mesh = meshes[i];
            double fuzz;
            if (mesh < 10)
                fuzz = sizeFuzz3;
            else if (mesh < 100)
                fuzz = sizeFuzz2;
            else
                fuzz = sizeFuzz;
            fuzzes[i] = fuzz;
            threshes[i] = BAND_HEADER + (int)Math.ceil(mesh * avgSize * fuzz);
        }
        if (verbose > 1) {
            System.out.print("tryAdaptiveCoding ["+len+"]"+
                             " avgS="+avgSize+" fuzz="+sizeFuzz+
                             " meshes: {");
            for (int i = 0; i < meshes.length; i++)
                System.out.print(" "+meshes[i]+"("+threshes[i]+")");
            Utils.log.info(" }");
        }
        if (runHelper == null) {
            runHelper = new CodingChooser(effort, allCodingChoices);
            if (stress != null)
                runHelper.addStressSeed(stress.nextInt());
            runHelper.topLevel = false;
            runHelper.verbose -= 1;
            runHelper.disableRunCoding = true;
            runHelper.disablePopCoding = this.disablePopCoding;
            if (effort < MID_EFFORT)
                // No nested pop codings.
                runHelper.disablePopCoding = true;
        }
        for (int i = 0; i < len; i++) {
            i = AdaptiveCoding.getNextK(i-1);
            if (i > len)  i = len;
            for (int j = meshes.length-1; j >= 0; j--) {
                int mesh   = meshes[j];
                int thresh = threshes[j];
                if (i+mesh > len)  continue;
                int size = sizes[i+mesh] - sizes[i];
                if (size >= thresh) {
                    // Found a size bulge.
                    int bend  = i+mesh;
                    int bsize = size;
                    double bigSize = avgSize * fuzzes[j];
                    while (bend < len && (bend-i) <= len/2) {
                        int bend0 = bend;
                        int bsize0 = bsize;
                        bend += mesh;
                        bend = i+AdaptiveCoding.getNextK(bend-i-1);
                        if (bend < 0 || bend > len)
                            bend = len;
                        bsize = sizes[bend]-sizes[i];
                        if (bsize < BAND_HEADER + (bend-i) * bigSize) {
                            bsize = bsize0;
                            bend = bend0;
                            break;
                        }
                    }
                    int nexti = bend;
                    if (verbose > 2) {
                        Utils.log.info("bulge at "+i+"["+(bend-i)+"] of "+
                                         pct(bsize - avgSize*(bend-i),
                                             avgSize*(bend-i)));
                        Utils.log.info("{ //BEGIN");
                    }
                    CodingMethod begcm, midcm, endcm;
                    midcm = runHelper.choose(this.values,
                                             this.start+i,
                                             this.start+bend,
                                             plainCoding);
                    if (midcm == plainCoding) {
                        // No use working further.
                        begcm = plainCoding;
                        endcm = plainCoding;
                    } else {
                        begcm = runHelper.choose(this.values,
                                                 this.start,
                                                 this.start+i,
                                                 plainCoding);
                        endcm = runHelper.choose(this.values,
                                                 this.start+bend,
                                                 this.start+len,
                                                 plainCoding);
                    }
                    if (verbose > 2)
                        Utils.log.info("} //END");
                    if (begcm == midcm && i > 0 &&
                        AdaptiveCoding.isCodableLength(bend)) {
                        i = 0;
                    }
                    if (midcm == endcm && bend < len) {
                        bend = len;
                    }
                    if (begcm != plainCoding ||
                        midcm != plainCoding ||
                        endcm != plainCoding) {
                        CodingMethod chain;
                        int hlen = 0;
                        if (bend == len) {
                            chain = midcm;
                        } else {
                            chain = new AdaptiveCoding(bend-i, midcm, endcm);
                            hlen += BAND_HEADER;
                        }
                        if (i > 0) {
                            chain = new AdaptiveCoding(i, begcm, chain);
                            hlen += BAND_HEADER;
                        }
                        int[] chainSize = computeSizePrivate(chain);
                        noteSizes(chain,
                                  chainSize[BYTE_SIZE],
                                  chainSize[ZIP_SIZE]+hlen);
                    }
                    i = nexti;
                    break;
                }
            }
        }
        if (verbose > 3) {
            if (bestZipSize < oldZipSize) {
                Utils.log.info(">>> RUN WINS BY "+
                                 (oldZipSize - bestZipSize));
            }
        }
    }

    static private
    String pct(double num, double den) {
        return (Math.round((num / den)*10000)/100.0)+"%";
    }

    static
    class Sizer extends OutputStream {
        final OutputStream out;  // if non-null, copy output here also
        Sizer(OutputStream out) {
            this.out = out;
        }
        Sizer() {
            this(null);
        }
        private int count;
        public void write(int b) throws IOException {
            count++;
            if (out != null)  out.write(b);
        }
        public void write(byte b[], int off, int len) throws IOException {
            count += len;
            if (out != null)  out.write(b, off, len);
        }
        public void reset() {
            count = 0;
        }
        public int getSize() { return count; }

        public String toString() {
            String str = super.toString();
            // If -ea, print out more informative strings!
            assert((str = stringForDebug()) != null);
            return str;
        }
        String stringForDebug() {
            return "<Sizer "+getSize()+">";
        }
    }

    private Sizer zipSizer  = new Sizer();
    private Deflater zipDef = new Deflater();
    private DeflaterOutputStream zipOut = new DeflaterOutputStream(zipSizer, zipDef);
    private Sizer byteSizer = new Sizer(zipOut);
    private Sizer byteOnlySizer = new Sizer();

    private void resetData() {
        flushData();
        zipDef.reset();
        if (context != null) {
            // Prepend given salt to the test output.
            try {
                context.writeTo(byteSizer);
            } catch (IOException ee) {
                throw new RuntimeException(ee); // cannot happen
            }
        }
        zipSizer.reset();
        byteSizer.reset();
    }
    private void flushData() {
        try {
            zipOut.finish();
        } catch (IOException ee) {
            throw new RuntimeException(ee); // cannot happen
        }
    }
    private int getByteSize() {
        return byteSizer.getSize();
    }
    private int getZipSize() {
        flushData();
        return zipSizer.getSize();
    }


    /// Stress-test helpers.

    void addStressSeed(int x) {
        if (stress == null)  return;
        stress.setSeed(x + ((long)stress.nextInt() << 32));
    }

    // Pick a random pop-coding.
    private CodingMethod stressPopCoding(CodingMethod coding) {
        assert(stress != null);  // this method is only for testing
        // Don't turn it into a pop coding if it's already something special.
        if (!(coding instanceof Coding))  return coding;
        Coding valueCoding = ((Coding)coding).getValueCoding();
        Histogram hist = getValueHistogram();
        int fVlen = stressLen(hist.getTotalLength());
        if (fVlen == 0)  return coding;
        List popvals = new ArrayList();
        if (stress.nextBoolean()) {
            // Build the population from the value list.
            HashSet popset = new HashSet();
            for (int i = start; i < end; i++) {
                Integer val = new Integer(values[i]);
                if (popset.add(val))  popvals.add(val);
            }
        } else {
            int[][] matrix = hist.getMatrix();
            for (int mrow = 0; mrow < matrix.length; mrow++) {
                int[] row = matrix[mrow];
                for (int mcol = 1; mcol < row.length; mcol++) {
                    popvals.add(new Integer(row[mcol]));
                }
            }
        }
        int reorder = stress.nextInt();
        if ((reorder & 7) <= 2) {
            // Lose the order.
            Collections.shuffle(popvals, stress);
        } else {
            // Keep the order, mostly.
            if (((reorder >>>= 3) & 7) <= 2)  Collections.sort(popvals);
            if (((reorder >>>= 3) & 7) <= 2)  Collections.reverse(popvals);
            if (((reorder >>>= 3) & 7) <= 2)  Collections.rotate(popvals, stressLen(popvals.size()));
        }
        if (popvals.size() > fVlen) {
            // Cut the list down.
            if (((reorder >>>= 3) & 7) <= 2) {
                // Cut at end.
                popvals.subList(fVlen,   popvals.size()).clear();
            } else {
                // Cut at start.
                popvals.subList(0, popvals.size()-fVlen).clear();
            }
        }
        fVlen = popvals.size();
        int[] fvals = new int[1+fVlen];
        for (int i = 0; i < fVlen; i++) {
            fvals[1+i] = ((Integer)popvals.get(i)).intValue();
        }
        PopulationCoding pop = new PopulationCoding();
        pop.setFavoredValues(fvals, fVlen);
        int[] lvals = PopulationCoding.LValuesCoded;
        for (int i = 0; i < lvals.length / 2; i++) {
            int popl = lvals[stress.nextInt(lvals.length)];
            if (popl < 0)  continue;
            if (PopulationCoding.fitTokenCoding(fVlen, popl) != null) {
                pop.setL(popl);
                break;
            }
        }
        if (pop.tokenCoding == null) {
            int min = fvals[1], max = min;
            for (int i = 2; i <= fVlen; i++) {
                int val = fvals[i];
                if (min > val)  min = val;
                if (max < val)  max = val;
            }
            pop.tokenCoding = stressCoding(min, max);
        }

        computePopSizePrivate(pop, valueCoding, valueCoding);
        return pop;
    }

    // Pick a random adaptive coding.
    private CodingMethod stressAdaptiveCoding(CodingMethod coding) {
        assert(stress != null);  // this method is only for testing
        // Don't turn it into a run coding if it's already something special.
        if (!(coding instanceof Coding))  return coding;
        Coding plainCoding = (Coding)coding;
        int len = end-start;
        if (len < 2)  return coding;
        // Decide how many spans we'll create.
        int spanlen = stressLen(len-1)+1;
        if (spanlen == len)  return coding;
        try {
            assert(!disableRunCoding);
            disableRunCoding = true;  // temporary, while I decide spans
            int[] allValues = (int[]) values.clone();
            CodingMethod result = null;
            int scan  = this.end;
            int start = this.start;
            for (int split; scan > start; scan = split) {
                int thisspan;
                int rand = (scan - start < 100)? -1: stress.nextInt();
                if ((rand & 7) != 0) {
                    thisspan = (spanlen==1? spanlen: stressLen(spanlen-1)+1);
                } else {
                    // Every so often generate a value based on KX/KB format.
                    int KX = (rand >>>= 3) & AdaptiveCoding.KX_MAX;
                    int KB = (rand >>>= 3) & AdaptiveCoding.KB_MAX;
                    for (;;) {
                        thisspan = AdaptiveCoding.decodeK(KX, KB);
                        if (thisspan <= scan - start)  break;
                        // Try smaller and smaller codings:
                        if (KB != AdaptiveCoding.KB_DEFAULT)
                            KB = AdaptiveCoding.KB_DEFAULT;
                        else
                            KX -= 1;
                    }
                    //System.out.println("KX="+KX+" KB="+KB+" K="+thisspan);
                    assert(AdaptiveCoding.isCodableLength(thisspan));
                }
                if (thisspan > scan - start)  thisspan = scan - start;
                while (!AdaptiveCoding.isCodableLength(thisspan))  --thisspan;
                split = scan - thisspan;
                assert(split < scan);
                assert(split >= start);
                // Choose a coding for the span [split..scan).
                CodingMethod sc = choose(allValues, split, scan, plainCoding);
                if (result == null) {
                    result = sc;  // the caboose
                } else {
                    result = new AdaptiveCoding(scan-split, sc, result);
                }
            }
            return result;
        } finally {
            disableRunCoding = false; // return to normal value
        }
    }

    // Return a random value in [0..len], gently biased toward extremes.
    private Coding stressCoding(int min, int max) {
        assert(stress != null);  // this method is only for testing
        for (int i = 0; i < 100; i++) {
            Coding c = Coding.of(stress.nextInt(Coding.B_MAX)+1,
                                 stress.nextInt(Coding.H_MAX)+1,
                                 stress.nextInt(Coding.S_MAX+1));
            if (c.B() == 1)  c = c.setH(256);
            if (c.H() == 256 && c.B() >= 5)  c = c.setB(4);
            if (stress.nextBoolean()) {
                Coding dc = c.setD(1);
                if (dc.canRepresent(min, max))  return dc;
            }
            if (c.canRepresent(min, max))  return c;
        }
        return BandStructure.UNSIGNED5;
    }

    // Return a random value in [0..len], gently biased toward extremes.
    private int stressLen(int len) {
        assert(stress != null);  // this method is only for testing
        assert(len >= 0);
        int rand = stress.nextInt(100);
        if (rand < 20)
            return Math.min(len/5, rand);
        else if (rand < 40)
            return len;
        else
            return stress.nextInt(len);
    }

    // For debug only.
/*
    public static
    int[] readValuesFrom(InputStream instr) {
        return readValuesFrom(new InputStreamReader(instr));
    }
    public static
    int[] readValuesFrom(Reader inrdr) {
        inrdr = new BufferedReader(inrdr);
        final StreamTokenizer in = new StreamTokenizer(inrdr);
        final int TT_NOTHING = -99;
        in.commentChar('#');
        return readValuesFrom(new Iterator() {
            int token = TT_NOTHING;
            private int getToken() {
                if (token == TT_NOTHING) {
                    try {
                        token = in.nextToken();
                        assert(token != TT_NOTHING);
                    } catch (IOException ee) {
                        throw new RuntimeException(ee);
                    }
                }
                return token;
            }
            public boolean hasNext() {
                return getToken() != StreamTokenizer.TT_EOF;
            }
            public Object next() {
                int ntok = getToken();
                token = TT_NOTHING;
                switch (ntok) {
                case StreamTokenizer.TT_EOF:
                    throw new NoSuchElementException();
                case StreamTokenizer.TT_NUMBER:
                    return new Integer((int) in.nval);
                default:
                    assert(false);
                    return null;
                }
            }
            public void remove() {
                throw new UnsupportedOperationException();
            }
        });
    }
    public static
    int[] readValuesFrom(Iterator iter) {
        return readValuesFrom(iter, 0);
    }
    public static
    int[] readValuesFrom(Iterator iter, int initSize) {
        int[] na = new int[Math.max(10, initSize)];
        int np = 0;
        while (iter.hasNext()) {
            Integer val = (Integer) iter.next();
            if (np == na.length) {
                na = BandStructure.realloc(na);
            }
            na[np++] = val.intValue();
        }
        if (np != na.length) {
            na = BandStructure.realloc(na, np);
        }
        return na;
    }

    public static
    void main(String[] av) throws IOException {
        int effort = MID_EFFORT;
        int ap = 0;
        if (ap < av.length && av[ap].equals("-e")) {
            ap++;
            effort = Integer.parseInt(av[ap++]);
        }
        int verbose = 1;
        if (ap < av.length && av[ap].equals("-v")) {
            ap++;
            verbose = Integer.parseInt(av[ap++]);
        }
        Coding[] bcs = BandStructure.getBasicCodings();
        CodingChooser cc = new CodingChooser(effort, bcs);
        if (ap < av.length && av[ap].equals("-p")) {
            ap++;
            cc.optUsePopulationCoding = false;
        }
        if (ap < av.length && av[ap].equals("-a")) {
            ap++;
            cc.optUseAdaptiveCoding = false;
        }
        cc.verbose = verbose;
        int[] values = readValuesFrom(System.in);
        int[] sizes = {0,0};
        CodingMethod cm = cc.choose(values, BandStructure.UNSIGNED5, sizes);
        System.out.println("size: "+sizes[BYTE_SIZE]+"/zs="+sizes[ZIP_SIZE]);
        System.out.println(cm);
    }
//*/

}
