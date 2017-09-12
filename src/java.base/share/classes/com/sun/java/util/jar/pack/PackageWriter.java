/*
 * Copyright (c) 2001, 2013, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.java.util.jar.pack.ConstantPool.*;
import com.sun.java.util.jar.pack.Package.Class;
import com.sun.java.util.jar.pack.Package.File;
import com.sun.java.util.jar.pack.Package.InnerClass;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static com.sun.java.util.jar.pack.Constants.*;

/**
 * Writer for a package file.
 * @author John Rose
 */
class PackageWriter extends BandStructure {
    Package pkg;
    OutputStream finalOut;
    Package.Version packageVersion;

    PackageWriter(Package pkg, OutputStream out) throws IOException {
        this.pkg = pkg;
        this.finalOut = out;
        // Caller has specified maximum class file version in the package:
        initHighestClassVersion(pkg.getHighestClassVersion());
    }

    void write() throws IOException {
        boolean ok = false;
        try {
            if (verbose > 0) {
                Utils.log.info("Setting up constant pool...");
            }
            setup();

            if (verbose > 0) {
                Utils.log.info("Packing...");
            }

            // writeFileHeader() is done last, since it has ultimate counts
            // writeBandHeaders() is called after all other bands are done
            writeConstantPool();
            writeFiles();
            writeAttrDefs();
            writeInnerClasses();
            writeClassesAndByteCodes();
            writeAttrCounts();

            if (verbose > 1)  printCodeHist();

            // choose codings (fill band_headers if needed)
            if (verbose > 0) {
                Utils.log.info("Coding...");
            }
            all_bands.chooseBandCodings();

            // now we can write the headers:
            writeFileHeader();

            writeAllBandsTo(finalOut);

            ok = true;
        } catch (Exception ee) {
            Utils.log.warning("Error on output: "+ee, ee);
            //if (verbose > 0)  ee.printStackTrace();
            // Write partial output only if we are verbose.
            if (verbose > 0)  finalOut.close();
            if (ee instanceof IOException)  throw (IOException)ee;
            if (ee instanceof RuntimeException)  throw (RuntimeException)ee;
            throw new Error("error packing", ee);
        }
    }

    Set<Entry>                       requiredEntries;  // for the CP
    Map<Attribute.Layout, int[]>     backCountTable;   // for layout callables
    int[][]     attrCounts;       // count attr. occurrences

    void setup() {
        requiredEntries = new HashSet<>();
        setArchiveOptions();
        trimClassAttributes();
        collectAttributeLayouts();
        pkg.buildGlobalConstantPool(requiredEntries);
        setBandIndexes();
        makeNewAttributeBands();
        collectInnerClasses();
    }

    /*
     * Convenience function to choose an archive version based
     * on the class file versions observed within the archive
     * or set the user defined version preset via properties.
     */
    void chooseDefaultPackageVersion() throws IOException {
        if (pkg.packageVersion != null) {
            packageVersion = pkg.packageVersion;
            if (verbose > 0) {
                Utils.log.info("package version overridden with: "
                                + packageVersion);
            }
            return;
        }

        Package.Version highV = getHighestClassVersion();
        // set the package version now
        if (highV.lessThan(JAVA6_MAX_CLASS_VERSION)) {
            // There are only old classfiles in this segment or resources
            packageVersion = JAVA5_PACKAGE_VERSION;
        } else if (highV.equals(JAVA6_MAX_CLASS_VERSION) ||
                (highV.equals(JAVA7_MAX_CLASS_VERSION) && !pkg.cp.haveExtraTags())) {
            // force down the package version if we have jdk7 classes without
            // any Indy references, this is because jdk7 class file (51.0) without
            // Indy is identical to jdk6 class file (50.0).
            packageVersion = JAVA6_PACKAGE_VERSION;
        } else if (highV.equals(JAVA7_MAX_CLASS_VERSION)) {
            packageVersion = JAVA7_PACKAGE_VERSION;
        } else {
            // Normal case.  Use the newest archive format, when available
            packageVersion = JAVA8_PACKAGE_VERSION;
        }

        if (verbose > 0) {
            Utils.log.info("Highest version class file: " + highV
                    + " package version: " + packageVersion);
        }
    }

    void checkVersion() throws IOException {
        assert(packageVersion != null);

        if (packageVersion.lessThan(JAVA7_PACKAGE_VERSION)) {
            // this bit was reserved for future use in previous versions
            if (testBit(archiveOptions, AO_HAVE_CP_EXTRAS)) {
                throw new IOException("Format bits for Java 7 must be zero in previous releases");
            }
        }
        if (testBit(archiveOptions, AO_UNUSED_MBZ)) {
            throw new IOException("High archive option bits are reserved and must be zero: " + Integer.toHexString(archiveOptions));
        }
    }

    void setArchiveOptions() {
        // Decide on some archive options early.
        // Does not decide on: AO_HAVE_SPECIAL_FORMATS,
        // AO_HAVE_CP_NUMBERS, AO_HAVE_FILE_HEADERS.
        // Also, AO_HAVE_FILE_OPTIONS may be forced on later.
        int minModtime = pkg.default_modtime;
        int maxModtime = pkg.default_modtime;
        int minOptions = -1;
        int maxOptions = 0;

        // Import defaults from package (deflate hint, etc.).
        archiveOptions |= pkg.default_options;

        for (File file : pkg.files) {
            int modtime = file.modtime;
            int options = file.options;

            if (minModtime == NO_MODTIME) {
                minModtime = maxModtime = modtime;
            } else {
                if (minModtime > modtime)  minModtime = modtime;
                if (maxModtime < modtime)  maxModtime = modtime;
            }
            minOptions &= options;
            maxOptions |= options;
        }
        if (pkg.default_modtime == NO_MODTIME) {
            // Make everything else be a positive offset from here.
            pkg.default_modtime = minModtime;
        }
        if (minModtime != NO_MODTIME && minModtime != maxModtime) {
            // Put them into a band.
            archiveOptions |= AO_HAVE_FILE_MODTIME;
        }
        // If the archive deflation is set do not bother with each file.
        if (!testBit(archiveOptions,AO_DEFLATE_HINT) && minOptions != -1) {
            if (testBit(minOptions, FO_DEFLATE_HINT)) {
                // Every file has the deflate_hint set.
                // Set it for the whole archive, and omit options.
                archiveOptions |= AO_DEFLATE_HINT;
                minOptions -= FO_DEFLATE_HINT;
                maxOptions -= FO_DEFLATE_HINT;
            }
            pkg.default_options |= minOptions;
            if (minOptions != maxOptions
                || minOptions != pkg.default_options) {
                archiveOptions |= AO_HAVE_FILE_OPTIONS;
            }
        }
        // Decide on default version number (majority rule).
        Map<Package.Version, int[]> verCounts = new HashMap<>();
        int bestCount = 0;
        Package.Version bestVersion = null;
        for (Class cls : pkg.classes) {
            Package.Version version = cls.getVersion();
            int[] var = verCounts.get(version);
            if (var == null) {
                var = new int[1];
                verCounts.put(version, var);
            }
            int count = (var[0] += 1);
            //System.out.println("version="+version+" count="+count);
            if (bestCount < count) {
                bestCount = count;
                bestVersion = version;
            }
        }
        verCounts.clear();
        if (bestVersion == null)  bestVersion = JAVA_MIN_CLASS_VERSION;  // degenerate case
        pkg.defaultClassVersion = bestVersion;
        if (verbose > 0)
           Utils.log.info("Consensus version number in segment is " + bestVersion);
        if (verbose > 0)
            Utils.log.info("Highest version number in segment is "
                            + pkg.getHighestClassVersion());

        // Now add explicit pseudo-attrs. to classes with odd versions.
        for (Class cls : pkg.classes) {
            if (!cls.getVersion().equals(bestVersion)) {
                Attribute a = makeClassFileVersionAttr(cls.getVersion());
                if (verbose > 1) {
                    Utils.log.fine("Version "+cls.getVersion() + " of " + cls
                                     + " doesn't match package version "
                                     + bestVersion);
                }
                // Note:  Does not add in "natural" order.  (Who cares?)
                cls.addAttribute(a);
            }
        }

        // Decide if we are transmitting a huge resource file:
        for (File file : pkg.files) {
            long len = file.getFileLength();
            if (len != (int)len) {
                archiveOptions |= AO_HAVE_FILE_SIZE_HI;
                if (verbose > 0)
                   Utils.log.info("Note: Huge resource file "+file.getFileName()+" forces 64-bit sizing");
                break;
            }
        }

        // Decide if code attributes typically have sub-attributes.
        // In that case, to preserve compact 1-byte code headers,
        // we must declare unconditional presence of code flags.
        int cost0 = 0;
        int cost1 = 0;
        for (Class cls : pkg.classes) {
            for (Class.Method m : cls.getMethods()) {
                if (m.code != null) {
                    if (m.code.attributeSize() == 0) {
                        // cost of a useless unconditional flags byte
                        cost1 += 1;
                    } else if (shortCodeHeader(m.code) != LONG_CODE_HEADER) {
                        // cost of inflating a short header
                        cost0 += 3;
                    }
                }
            }
        }
        if (cost0 > cost1) {
            archiveOptions |= AO_HAVE_ALL_CODE_FLAGS;
        }
        if (verbose > 0)
            Utils.log.info("archiveOptions = "
                             +"0b"+Integer.toBinaryString(archiveOptions));
    }

    void writeFileHeader() throws IOException {
        chooseDefaultPackageVersion();
        writeArchiveMagic();
        writeArchiveHeader();
    }

    // Local routine used to format fixed-format scalars
    // in the file_header:
    private void putMagicInt32(int val) throws IOException {
        int res = val;
        for (int i = 0; i < 4; i++) {
            archive_magic.putByte(0xFF & (res >>> 24));
            res <<= 8;
        }
    }

    void writeArchiveMagic() throws IOException {
        putMagicInt32(pkg.magic);
    }

    void writeArchiveHeader() throws IOException {
        // for debug only:  number of words optimized away
        int headerSizeForDebug = AH_LENGTH_MIN;

        // AO_HAVE_SPECIAL_FORMATS is set if non-default
        // coding techniques are used, or if there are
        // compressor-defined attributes transmitted.
        boolean haveSpecial = testBit(archiveOptions, AO_HAVE_SPECIAL_FORMATS);
        if (!haveSpecial) {
            haveSpecial |= (band_headers.length() != 0);
            haveSpecial |= (attrDefsWritten.length != 0);
            if (haveSpecial)
                archiveOptions |= AO_HAVE_SPECIAL_FORMATS;
        }
        if (haveSpecial)
            headerSizeForDebug += AH_SPECIAL_FORMAT_LEN;

        // AO_HAVE_FILE_HEADERS is set if there is any
        // file or segment envelope information present.
        boolean haveFiles = testBit(archiveOptions, AO_HAVE_FILE_HEADERS);
        if (!haveFiles) {
            haveFiles |= (archiveNextCount > 0);
            haveFiles |= (pkg.default_modtime != NO_MODTIME);
            if (haveFiles)
                archiveOptions |= AO_HAVE_FILE_HEADERS;
        }
        if (haveFiles)
            headerSizeForDebug += AH_FILE_HEADER_LEN;

        // AO_HAVE_CP_NUMBERS is set if there are any numbers
        // in the global constant pool.  (Numbers are in 15% of classes.)
        boolean haveNumbers = testBit(archiveOptions, AO_HAVE_CP_NUMBERS);
        if (!haveNumbers) {
            haveNumbers |= pkg.cp.haveNumbers();
            if (haveNumbers)
                archiveOptions |= AO_HAVE_CP_NUMBERS;
        }
        if (haveNumbers)
            headerSizeForDebug += AH_CP_NUMBER_LEN;

        // AO_HAVE_CP_EXTRAS is set if there are constant pool entries
        // beyond the Java 6 version of the class file format.
        boolean haveCPExtra = testBit(archiveOptions, AO_HAVE_CP_EXTRAS);
        if (!haveCPExtra) {
            haveCPExtra |= pkg.cp.haveExtraTags();
            if (haveCPExtra)
                archiveOptions |= AO_HAVE_CP_EXTRAS;
        }
        if (haveCPExtra)
            headerSizeForDebug += AH_CP_EXTRA_LEN;

        // the archiveOptions are all initialized, sanity check now!.
        checkVersion();

        archive_header_0.putInt(packageVersion.minor);
        archive_header_0.putInt(packageVersion.major);
        if (verbose > 0)
            Utils.log.info("Package Version for this segment:" + packageVersion);
        archive_header_0.putInt(archiveOptions); // controls header format
        assert(archive_header_0.length() == AH_LENGTH_0);

        final int DUMMY = 0;
        if (haveFiles) {
            assert(archive_header_S.length() == AH_ARCHIVE_SIZE_HI);
            archive_header_S.putInt(DUMMY); // (archiveSize1 >>> 32)
            assert(archive_header_S.length() == AH_ARCHIVE_SIZE_LO);
            archive_header_S.putInt(DUMMY); // (archiveSize1 >>> 0)
            assert(archive_header_S.length() == AH_LENGTH_S);
        }

        // Done with unsized part of header....

        if (haveFiles) {
            archive_header_1.putInt(archiveNextCount);  // usually zero
            archive_header_1.putInt(pkg.default_modtime);
            archive_header_1.putInt(pkg.files.size());
        } else {
            assert(pkg.files.isEmpty());
        }

        if (haveSpecial) {
            archive_header_1.putInt(band_headers.length());
            archive_header_1.putInt(attrDefsWritten.length);
        } else {
            assert(band_headers.length() == 0);
            assert(attrDefsWritten.length == 0);
        }

        writeConstantPoolCounts(haveNumbers, haveCPExtra);

        archive_header_1.putInt(pkg.getAllInnerClasses().size());
        archive_header_1.putInt(pkg.defaultClassVersion.minor);
        archive_header_1.putInt(pkg.defaultClassVersion.major);
        archive_header_1.putInt(pkg.classes.size());

        // Sanity:  Make sure we came out to 29 (less optional fields):
        assert(archive_header_0.length() +
               archive_header_S.length() +
               archive_header_1.length()
               == headerSizeForDebug);

        // Figure out all the sizes now, first cut:
        archiveSize0 = 0;
        archiveSize1 = all_bands.outputSize();
        // Second cut:
        archiveSize0 += archive_magic.outputSize();
        archiveSize0 += archive_header_0.outputSize();
        archiveSize0 += archive_header_S.outputSize();
        // Make the adjustments:
        archiveSize1 -= archiveSize0;

        // Patch the header:
        if (haveFiles) {
            int archiveSizeHi = (int)(archiveSize1 >>> 32);
            int archiveSizeLo = (int)(archiveSize1 >>> 0);
            archive_header_S.patchValue(AH_ARCHIVE_SIZE_HI, archiveSizeHi);
            archive_header_S.patchValue(AH_ARCHIVE_SIZE_LO, archiveSizeLo);
            int zeroLen = UNSIGNED5.getLength(DUMMY);
            archiveSize0 += UNSIGNED5.getLength(archiveSizeHi) - zeroLen;
            archiveSize0 += UNSIGNED5.getLength(archiveSizeLo) - zeroLen;
        }
        if (verbose > 1)
            Utils.log.fine("archive sizes: "+
                             archiveSize0+"+"+archiveSize1);
        assert(all_bands.outputSize() == archiveSize0+archiveSize1);
    }

    void writeConstantPoolCounts(boolean haveNumbers, boolean haveCPExtra) throws IOException {
        for (byte tag : ConstantPool.TAGS_IN_ORDER) {
            int count = pkg.cp.getIndexByTag(tag).size();
            switch (tag) {
            case CONSTANT_Utf8:
                // The null string is always first.
                if (count > 0)
                    assert(pkg.cp.getIndexByTag(tag).get(0)
                           == ConstantPool.getUtf8Entry(""));
                break;

            case CONSTANT_Integer:
            case CONSTANT_Float:
            case CONSTANT_Long:
            case CONSTANT_Double:
                // Omit counts for numbers if possible.
                if (!haveNumbers) {
                    assert(count == 0);
                    continue;
                }
                break;

            case CONSTANT_MethodHandle:
            case CONSTANT_MethodType:
            case CONSTANT_InvokeDynamic:
            case CONSTANT_BootstrapMethod:
                // Omit counts for newer entities if possible.
                if (!haveCPExtra) {
                    assert(count == 0);
                    continue;
                }
                break;
            }
            archive_header_1.putInt(count);
        }
    }

    protected Index getCPIndex(byte tag) {
        return pkg.cp.getIndexByTag(tag);
    }

// (The following observations are out of date; they apply only to
// "banding" the constant pool itself.  Later revisions of this algorithm
// applied the banding technique to every part of the package file,
// applying the benefits more broadly.)

// Note:  Keeping the data separate in passes (or "bands") allows the
// compressor to issue significantly shorter indexes for repeated data.
// The difference in zipped size is 4%, which is remarkable since the
// unzipped sizes are the same (only the byte order differs).

// After moving similar data into bands, it becomes natural to delta-encode
// each band.  (This is especially useful if we sort the constant pool first.)
// Delta encoding saves an extra 5% in the output size (13% of the CP itself).
// Because a typical delta usees much less data than a byte, the savings after
// zipping is even better:  A zipped delta-encoded package is 8% smaller than
// a zipped non-delta-encoded package.  Thus, in the zipped file, a banded,
// delta-encoded constant pool saves over 11% (of the total file size) compared
// with a zipped unbanded file.

    void writeConstantPool() throws IOException {
        IndexGroup cp = pkg.cp;

        if (verbose > 0)  Utils.log.info("Writing CP");

        for (byte tag : ConstantPool.TAGS_IN_ORDER) {
            Index index = cp.getIndexByTag(tag);

            Entry[] cpMap = index.cpMap;
            if (verbose > 0)
                Utils.log.info("Writing "+cpMap.length+" "+ConstantPool.tagName(tag)+" entries...");

            if (optDumpBands) {
                try (PrintStream ps = new PrintStream(getDumpStream(index, ".idx"))) {
                    printArrayTo(ps, cpMap, 0, cpMap.length);
                }
            }

            switch (tag) {
            case CONSTANT_Utf8:
                writeUtf8Bands(cpMap);
                break;
            case CONSTANT_Integer:
                for (int i = 0; i < cpMap.length; i++) {
                    NumberEntry e = (NumberEntry) cpMap[i];
                    int x = ((Integer)e.numberValue()).intValue();
                    cp_Int.putInt(x);
                }
                break;
            case CONSTANT_Float:
                for (int i = 0; i < cpMap.length; i++) {
                    NumberEntry e = (NumberEntry) cpMap[i];
                    float fx = ((Float)e.numberValue()).floatValue();
                    int x = Float.floatToIntBits(fx);
                    cp_Float.putInt(x);
                }
                break;
            case CONSTANT_Long:
                for (int i = 0; i < cpMap.length; i++) {
                    NumberEntry e = (NumberEntry) cpMap[i];
                    long x = ((Long)e.numberValue()).longValue();
                    cp_Long_hi.putInt((int)(x >>> 32));
                    cp_Long_lo.putInt((int)(x >>> 0));
                }
                break;
            case CONSTANT_Double:
                for (int i = 0; i < cpMap.length; i++) {
                    NumberEntry e = (NumberEntry) cpMap[i];
                    double dx = ((Double)e.numberValue()).doubleValue();
                    long x = Double.doubleToLongBits(dx);
                    cp_Double_hi.putInt((int)(x >>> 32));
                    cp_Double_lo.putInt((int)(x >>> 0));
                }
                break;
            case CONSTANT_String:
                for (int i = 0; i < cpMap.length; i++) {
                    StringEntry e = (StringEntry) cpMap[i];
                    cp_String.putRef(e.ref);
                }
                break;
            case CONSTANT_Class:
                for (int i = 0; i < cpMap.length; i++) {
                    ClassEntry e = (ClassEntry) cpMap[i];
                    cp_Class.putRef(e.ref);
                }
                break;
            case CONSTANT_Signature:
                writeSignatureBands(cpMap);
                break;
            case CONSTANT_NameandType:
                for (int i = 0; i < cpMap.length; i++) {
                    DescriptorEntry e = (DescriptorEntry) cpMap[i];
                    cp_Descr_name.putRef(e.nameRef);
                    cp_Descr_type.putRef(e.typeRef);
                }
                break;
            case CONSTANT_Fieldref:
                writeMemberRefs(tag, cpMap, cp_Field_class, cp_Field_desc);
                break;
            case CONSTANT_Methodref:
                writeMemberRefs(tag, cpMap, cp_Method_class, cp_Method_desc);
                break;
            case CONSTANT_InterfaceMethodref:
                writeMemberRefs(tag, cpMap, cp_Imethod_class, cp_Imethod_desc);
                break;
            case CONSTANT_MethodHandle:
                for (int i = 0; i < cpMap.length; i++) {
                    MethodHandleEntry e = (MethodHandleEntry) cpMap[i];
                    cp_MethodHandle_refkind.putInt(e.refKind);
                    cp_MethodHandle_member.putRef(e.memRef);
                }
                break;
            case CONSTANT_MethodType:
                for (int i = 0; i < cpMap.length; i++) {
                    MethodTypeEntry e = (MethodTypeEntry) cpMap[i];
                    cp_MethodType.putRef(e.typeRef);
                }
                break;
            case CONSTANT_InvokeDynamic:
                for (int i = 0; i < cpMap.length; i++) {
                    InvokeDynamicEntry e = (InvokeDynamicEntry) cpMap[i];
                    cp_InvokeDynamic_spec.putRef(e.bssRef);
                    cp_InvokeDynamic_desc.putRef(e.descRef);
                }
                break;
            case CONSTANT_BootstrapMethod:
                for (int i = 0; i < cpMap.length; i++) {
                    BootstrapMethodEntry e = (BootstrapMethodEntry) cpMap[i];
                    cp_BootstrapMethod_ref.putRef(e.bsmRef);
                    cp_BootstrapMethod_arg_count.putInt(e.argRefs.length);
                    for (Entry argRef : e.argRefs) {
                        cp_BootstrapMethod_arg.putRef(argRef);
                    }
                }
                break;
            default:
                throw new AssertionError("unexpected CP tag in package");
            }
        }
        if (optDumpBands || verbose > 1) {
            for (byte tag = CONSTANT_GroupFirst; tag < CONSTANT_GroupLimit; tag++) {
                Index index = cp.getIndexByTag(tag);
                if (index == null || index.isEmpty())  continue;
                Entry[] cpMap = index.cpMap;
                if (verbose > 1)
                    Utils.log.info("Index group "+ConstantPool.tagName(tag)+" contains "+cpMap.length+" entries.");
                if (optDumpBands) {
                    try (PrintStream ps = new PrintStream(getDumpStream(index.debugName, tag, ".gidx", index))) {
                        printArrayTo(ps, cpMap, 0, cpMap.length, true);
                    }
                }
            }
        }
    }

    void writeUtf8Bands(Entry[] cpMap) throws IOException {
        if (cpMap.length == 0)
            return;  // nothing to write

        // The first element must always be the empty string.
        assert(cpMap[0].stringValue().equals(""));
        final int SUFFIX_SKIP_1 = 1;
        final int PREFIX_SKIP_2 = 2;

        // Fetch the char arrays, first of all.
        char[][] chars = new char[cpMap.length][];
        for (int i = 0; i < chars.length; i++) {
            chars[i] = cpMap[i].stringValue().toCharArray();
        }

        // First band:  Write lengths of shared prefixes.
        int[] prefixes = new int[cpMap.length];  // includes 2 skipped zeroes
        char[] prevChars = {};
        for (int i = 0; i < chars.length; i++) {
            int prefix = 0;
            char[] curChars = chars[i];
            int limit = Math.min(curChars.length, prevChars.length);
            while (prefix < limit && curChars[prefix] == prevChars[prefix])
                prefix++;
            prefixes[i] = prefix;
            if (i >= PREFIX_SKIP_2)
                cp_Utf8_prefix.putInt(prefix);
            else
                assert(prefix == 0);
            prevChars = curChars;
        }

        // Second band:  Write lengths of unshared suffixes.
        // Third band:  Write the char values in the unshared suffixes.
        for (int i = 0; i < chars.length; i++) {
            char[] str = chars[i];
            int prefix = prefixes[i];
            int suffix = str.length - prefixes[i];
            boolean isPacked = false;
            if (suffix == 0) {
                // Zero suffix length is special flag to indicate
                // separate treatment in cp_Utf8_big bands.
                // This suffix length never occurs naturally,
                // except in the one case of a zero-length string.
                // (If it occurs, it is the first, due to sorting.)
                // The zero length string must, paradoxically, be
                // encoded as a zero-length cp_Utf8_big band.
                // This wastes exactly (& tolerably) one null byte.
                isPacked = (i >= SUFFIX_SKIP_1);
                // Do not bother to add an empty "(Utf8_big_0)" band.
                // Also, the initial empty string does not require a band.
            } else if (optBigStrings && effort > 1 && suffix > 100) {
                int numWide = 0;
                for (int n = 0; n < suffix; n++) {
                    if (str[prefix+n] > 127) {
                        numWide++;
                    }
                }
                if (numWide > 100) {
                    // Try packing the chars with an alternate encoding.
                    isPacked = tryAlternateEncoding(i, numWide, str, prefix);
                }
            }
            if (i < SUFFIX_SKIP_1) {
                // No output.
                assert(!isPacked);
                assert(suffix == 0);
            } else if (isPacked) {
                // Mark packed string with zero-length suffix count.
                // This tells the unpacker to go elsewhere for the suffix bits.
                // Fourth band:  Write unshared suffix with alternate coding.
                cp_Utf8_suffix.putInt(0);
                cp_Utf8_big_suffix.putInt(suffix);
            } else {
                assert(suffix != 0);  // would be ambiguous
                // Normal string.  Save suffix in third and fourth bands.
                cp_Utf8_suffix.putInt(suffix);
                for (int n = 0; n < suffix; n++) {
                    int ch = str[prefix+n];
                    cp_Utf8_chars.putInt(ch);
                }
            }
        }
        if (verbose > 0) {
            int normCharCount = cp_Utf8_chars.length();
            int packCharCount = cp_Utf8_big_chars.length();
            int charCount = normCharCount + packCharCount;
            Utils.log.info("Utf8string #CHARS="+charCount+" #PACKEDCHARS="+packCharCount);
        }
    }

    private boolean tryAlternateEncoding(int i, int numWide,
                                         char[] str, int prefix) {
        int suffix = str.length - prefix;
        int[] cvals = new int[suffix];
        for (int n = 0; n < suffix; n++) {
            cvals[n] = str[prefix+n];
        }
        CodingChooser cc = getCodingChooser();
        Coding bigRegular = cp_Utf8_big_chars.regularCoding;
        String bandName = "(Utf8_big_"+i+")";
        int[] sizes = { 0, 0 };
        final int BYTE_SIZE = CodingChooser.BYTE_SIZE;
        final int ZIP_SIZE = CodingChooser.ZIP_SIZE;
        if (verbose > 1 || cc.verbose > 1) {
            Utils.log.fine("--- chooseCoding "+bandName);
        }
        CodingMethod special = cc.choose(cvals, bigRegular, sizes);
        Coding charRegular = cp_Utf8_chars.regularCoding;
        if (verbose > 1)
            Utils.log.fine("big string["+i+"] len="+suffix+" #wide="+numWide+" size="+sizes[BYTE_SIZE]+"/z="+sizes[ZIP_SIZE]+" coding "+special);
        if (special != charRegular) {
            int specialZipSize = sizes[ZIP_SIZE];
            int[] normalSizes = cc.computeSize(charRegular, cvals);
            int normalZipSize = normalSizes[ZIP_SIZE];
            int minWin = Math.max(5, normalZipSize/1000);
            if (verbose > 1)
                Utils.log.fine("big string["+i+"] normalSize="+normalSizes[BYTE_SIZE]+"/z="+normalSizes[ZIP_SIZE]+" win="+(specialZipSize<normalZipSize-minWin));
            if (specialZipSize < normalZipSize-minWin) {
                IntBand big = cp_Utf8_big_chars.newIntBand(bandName);
                big.initializeValues(cvals);
                return true;
            }
        }
        return false;
    }

    void writeSignatureBands(Entry[] cpMap) throws IOException {
        for (int i = 0; i < cpMap.length; i++) {
            SignatureEntry e = (SignatureEntry) cpMap[i];
            cp_Signature_form.putRef(e.formRef);
            for (int j = 0; j < e.classRefs.length; j++) {
                cp_Signature_classes.putRef(e.classRefs[j]);
            }
        }
    }

    void writeMemberRefs(byte tag, Entry[] cpMap, CPRefBand cp_class, CPRefBand cp_desc) throws IOException {
        for (int i = 0; i < cpMap.length; i++) {
            MemberEntry e = (MemberEntry) cpMap[i];
            cp_class.putRef(e.classRef);
            cp_desc.putRef(e.descRef);
        }
    }

    void writeFiles() throws IOException {
        int numFiles = pkg.files.size();
        if (numFiles == 0)  return;
        int options = archiveOptions;
        boolean haveSizeHi  = testBit(options, AO_HAVE_FILE_SIZE_HI);
        boolean haveModtime = testBit(options, AO_HAVE_FILE_MODTIME);
        boolean haveOptions = testBit(options, AO_HAVE_FILE_OPTIONS);
        if (!haveOptions) {
            for (File file : pkg.files) {
                if (file.isClassStub()) {
                    haveOptions = true;
                    options |= AO_HAVE_FILE_OPTIONS;
                    archiveOptions = options;
                    break;
                }
            }
        }
        if (haveSizeHi || haveModtime || haveOptions || !pkg.files.isEmpty()) {
            options |= AO_HAVE_FILE_HEADERS;
            archiveOptions = options;
        }
        for (File file : pkg.files) {
            file_name.putRef(file.name);
            long len = file.getFileLength();
            file_size_lo.putInt((int)len);
            if (haveSizeHi)
                file_size_hi.putInt((int)(len >>> 32));
            if (haveModtime)
                file_modtime.putInt(file.modtime - pkg.default_modtime);
            if (haveOptions)
                file_options.putInt(file.options);
            file.writeTo(file_bits.collectorStream());
            if (verbose > 1)
                Utils.log.fine("Wrote "+len+" bytes of "+file.name.stringValue());
        }
        if (verbose > 0)
            Utils.log.info("Wrote "+numFiles+" resource files");
    }

    void collectAttributeLayouts() {
        maxFlags = new int[ATTR_CONTEXT_LIMIT];
        allLayouts = new FixedList<>(ATTR_CONTEXT_LIMIT);
        for (int i = 0; i < ATTR_CONTEXT_LIMIT; i++) {
            allLayouts.set(i, new HashMap<>());
        }
        // Collect maxFlags and allLayouts.
        for (Class cls : pkg.classes) {
            visitAttributeLayoutsIn(ATTR_CONTEXT_CLASS, cls);
            for (Class.Field f : cls.getFields()) {
                visitAttributeLayoutsIn(ATTR_CONTEXT_FIELD, f);
            }
            for (Class.Method m : cls.getMethods()) {
                visitAttributeLayoutsIn(ATTR_CONTEXT_METHOD, m);
                if (m.code != null) {
                    visitAttributeLayoutsIn(ATTR_CONTEXT_CODE, m.code);
                }
            }
        }
        // If there are many species of attributes, use 63-bit flags.
        for (int i = 0; i < ATTR_CONTEXT_LIMIT; i++) {
            int nl = allLayouts.get(i).size();
            boolean haveLongFlags = haveFlagsHi(i);
            final int TOO_MANY_ATTRS = 32 /*int flag size*/
                - 12 /*typical flag bits in use*/
                + 4  /*typical number of OK overflows*/;
            if (nl >= TOO_MANY_ATTRS) {  // heuristic
                int mask = 1<<(LG_AO_HAVE_XXX_FLAGS_HI+i);
                archiveOptions |= mask;
                haveLongFlags = true;
                if (verbose > 0)
                   Utils.log.info("Note: Many "+Attribute.contextName(i)+" attributes forces 63-bit flags");
            }
            if (verbose > 1) {
                Utils.log.fine(Attribute.contextName(i)+".maxFlags = 0x"+Integer.toHexString(maxFlags[i]));
                Utils.log.fine(Attribute.contextName(i)+".#layouts = "+nl);
            }
            assert(haveFlagsHi(i) == haveLongFlags);
        }
        initAttrIndexLimit();

        // Standard indexes can never conflict with flag bits.  Assert it.
        for (int i = 0; i < ATTR_CONTEXT_LIMIT; i++) {
            assert((attrFlagMask[i] & maxFlags[i]) == 0);
        }
        // Collect counts for both predefs. and custom defs.
        // Decide on custom, local attribute definitions.
        backCountTable = new HashMap<>();
        attrCounts = new int[ATTR_CONTEXT_LIMIT][];
        for (int i = 0; i < ATTR_CONTEXT_LIMIT; i++) {
            // Now the remaining defs in allLayouts[i] need attr. indexes.
            // Fill up unused flag bits with new defs.
            // Unused bits are those which are not used by predefined attrs,
            // and which are always clear in the classfiles.
            long avHiBits = ~(maxFlags[i] | attrFlagMask[i]);
            assert(attrIndexLimit[i] > 0);
            assert(attrIndexLimit[i] < 64);  // all bits fit into a Java long
            avHiBits &= (1L<<attrIndexLimit[i])-1;
            int nextLoBit = 0;
            Map<Attribute.Layout, int[]> defMap = allLayouts.get(i);
            @SuppressWarnings({"unchecked", "rawtypes"})
            Map.Entry<Attribute.Layout, int[]>[] layoutsAndCounts =
                    new Map.Entry[defMap.size()];
            defMap.entrySet().toArray(layoutsAndCounts);
            // Sort by count, most frequent first.
            // Predefs. participate in this sort, though it does not matter.
            Arrays.sort(layoutsAndCounts,
                        new Comparator<>() {
                public int compare(Map.Entry<Attribute.Layout, int[]> e0,
                                   Map.Entry<Attribute.Layout, int[]> e1) {
                    // Primary sort key is count, reversed.
                    int r = -(e0.getValue()[0] - e1.getValue()[0]);
                    if (r != 0)  return r;
                    return e0.getKey().compareTo(e1.getKey());
                }
            });
            attrCounts[i] = new int[attrIndexLimit[i]+layoutsAndCounts.length];
            for (int j = 0; j < layoutsAndCounts.length; j++) {
                Map.Entry<Attribute.Layout, int[]> e = layoutsAndCounts[j];
                Attribute.Layout def = e.getKey();
                int count = e.getValue()[0];
                int index;
                Integer predefIndex = attrIndexTable.get(def);
                if (predefIndex != null) {
                    // The index is already set.
                    index = predefIndex.intValue();
                } else if (avHiBits != 0) {
                    while ((avHiBits & 1) == 0) {
                        avHiBits >>>= 1;
                        nextLoBit += 1;
                    }
                    avHiBits -= 1;  // clear low bit; we are using it now
                    // Update attrIndexTable:
                    index = setAttributeLayoutIndex(def, nextLoBit);
                } else {
                    // Update attrIndexTable:
                    index = setAttributeLayoutIndex(def, ATTR_INDEX_OVERFLOW);
                }

                // Now that we know the index, record the count of this def.
                attrCounts[i][index] = count;

                // For all callables in the def, keep a tally of back-calls.
                Attribute.Layout.Element[] cbles = def.getCallables();
                final int[] bc = new int[cbles.length];
                for (int k = 0; k < cbles.length; k++) {
                    assert(cbles[k].kind == Attribute.EK_CBLE);
                    if (!cbles[k].flagTest(Attribute.EF_BACK)) {
                        bc[k] = -1;  // no count to accumulate here
                    }
                }
                backCountTable.put(def, bc);

                if (predefIndex == null) {
                    // Make sure the package CP can name the local attribute.
                    Entry ne = ConstantPool.getUtf8Entry(def.name());
                    String layout = def.layoutForClassVersion(getHighestClassVersion());
                    Entry le = ConstantPool.getUtf8Entry(layout);
                    requiredEntries.add(ne);
                    requiredEntries.add(le);
                    if (verbose > 0) {
                        if (index < attrIndexLimit[i])
                           Utils.log.info("Using free flag bit 1<<"+index+" for "+count+" occurrences of "+def);
                        else
                            Utils.log.info("Using overflow index "+index+" for "+count+" occurrences of "+def);
                    }
                }
            }
        }
        // Later, when emitting attr_definition_bands, we will look at
        // attrDefSeen and attrDefs at position 32/63 and beyond.
        // The attrIndexTable will provide elements of xxx_attr_indexes bands.

        // Done with scratch variables:
        maxFlags = null;
        allLayouts = null;
    }

    // Scratch variables for processing attributes and flags.
    int[] maxFlags;
    List<Map<Attribute.Layout, int[]>> allLayouts;

    void visitAttributeLayoutsIn(int ctype, Attribute.Holder h) {
        // Make note of which flags appear in the class file.
        // Set them in maxFlags.
        maxFlags[ctype] |= h.flags;
        for (Attribute a : h.getAttributes()) {
            Attribute.Layout def = a.layout();
            Map<Attribute.Layout, int[]> defMap = allLayouts.get(ctype);
            int[] count = defMap.get(def);
            if (count == null) {
                defMap.put(def, count = new int[1]);
            }
            if (count[0] < Integer.MAX_VALUE) {
                count[0] += 1;
            }
        }
    }

    Attribute.Layout[] attrDefsWritten;

    void writeAttrDefs() throws IOException {
        List<Object[]> defList = new ArrayList<>();
        for (int i = 0; i < ATTR_CONTEXT_LIMIT; i++) {
            int limit = attrDefs.get(i).size();
            for (int j = 0; j < limit; j++) {
                int header = i;  // ctype
                if (j < attrIndexLimit[i]) {
                    header |= ((j + ADH_BIT_IS_LSB) << ADH_BIT_SHIFT);
                    assert(header < 0x100);  // must fit into a byte
                    // (...else header is simply ctype, with zero high bits.)
                    if (!testBit(attrDefSeen[i], 1L<<j)) {
                        // either undefined or predefined; nothing to write
                        continue;
                    }
                }
                Attribute.Layout def = attrDefs.get(i).get(j);
                defList.add(new Object[]{ Integer.valueOf(header), def });
                assert(Integer.valueOf(j).equals(attrIndexTable.get(def)));
            }
        }
        // Sort the new attr defs into some "natural" order.
        int numAttrDefs = defList.size();
        Object[][] defs = new Object[numAttrDefs][];
        defList.toArray(defs);
        Arrays.sort(defs, new Comparator<>() {
            public int compare(Object[] a0, Object[] a1) {
                // Primary sort key is attr def header.
                @SuppressWarnings("unchecked")
                int r = ((Comparable)a0[0]).compareTo(a1[0]);
                if (r != 0)  return r;
                Integer ind0 = attrIndexTable.get(a0[1]);
                Integer ind1 = attrIndexTable.get(a1[1]);
                // Secondary sort key is attribute index.
                // (This must be so, in order to keep overflow attr order.)
                assert(ind0 != null);
                assert(ind1 != null);
                return ind0.compareTo(ind1);
            }
        });
        attrDefsWritten = new Attribute.Layout[numAttrDefs];
        try (PrintStream dump = !optDumpBands ? null
                 : new PrintStream(getDumpStream(attr_definition_headers, ".def")))
        {
            int[] indexForDebug = Arrays.copyOf(attrIndexLimit, ATTR_CONTEXT_LIMIT);
            for (int i = 0; i < defs.length; i++) {
                int header = ((Integer)defs[i][0]).intValue();
                Attribute.Layout def = (Attribute.Layout) defs[i][1];
                attrDefsWritten[i] = def;
                assert((header & ADH_CONTEXT_MASK) == def.ctype());
                attr_definition_headers.putByte(header);
                attr_definition_name.putRef(ConstantPool.getUtf8Entry(def.name()));
                String layout = def.layoutForClassVersion(getHighestClassVersion());
                attr_definition_layout.putRef(ConstantPool.getUtf8Entry(layout));
                // Check that we are transmitting that correct attribute index:
                boolean debug = false;
                assert(debug = true);
                if (debug) {
                    int hdrIndex = (header >> ADH_BIT_SHIFT) - ADH_BIT_IS_LSB;
                    if (hdrIndex < 0)  hdrIndex = indexForDebug[def.ctype()]++;
                    int realIndex = (attrIndexTable.get(def)).intValue();
                    assert(hdrIndex == realIndex);
                }
                if (dump != null) {
                    int index = (header >> ADH_BIT_SHIFT) - ADH_BIT_IS_LSB;
                    dump.println(index+" "+def);
                }
            }
        }
    }

    void writeAttrCounts() throws IOException {
        // Write the four xxx_attr_calls bands.
        for (int ctype = 0; ctype < ATTR_CONTEXT_LIMIT; ctype++) {
            MultiBand xxx_attr_bands = attrBands[ctype];
            IntBand xxx_attr_calls = getAttrBand(xxx_attr_bands, AB_ATTR_CALLS);
            Attribute.Layout[] defs = new Attribute.Layout[attrDefs.get(ctype).size()];
            attrDefs.get(ctype).toArray(defs);
            for (boolean predef = true; ; predef = false) {
                for (int ai = 0; ai < defs.length; ai++) {
                    Attribute.Layout def = defs[ai];
                    if (def == null)  continue;  // unused index
                    if (predef != isPredefinedAttr(ctype, ai))
                        continue;  // wrong pass
                    int totalCount = attrCounts[ctype][ai];
                    if (totalCount == 0)
                        continue;  // irrelevant
                    int[] bc = backCountTable.get(def);
                    for (int j = 0; j < bc.length; j++) {
                        if (bc[j] >= 0) {
                            int backCount = bc[j];
                            bc[j] = -1;  // close out; do not collect further counts
                            xxx_attr_calls.putInt(backCount);
                            assert(def.getCallables()[j].flagTest(Attribute.EF_BACK));
                        } else {
                            assert(!def.getCallables()[j].flagTest(Attribute.EF_BACK));
                        }
                    }
                }
                if (!predef)  break;
            }
        }
    }

    void trimClassAttributes() {
        for (Class cls : pkg.classes) {
            // Replace "obvious" SourceFile attrs by null.
            cls.minimizeSourceFile();
            // BootstrapMethods should never have been inserted.
            assert(cls.getAttribute(Package.attrBootstrapMethodsEmpty) == null);
        }
    }

    void collectInnerClasses() {
        // Capture inner classes, removing them from individual classes.
        // Irregular inner classes must stay local, though.
        Map<ClassEntry, InnerClass> allICMap = new HashMap<>();
        // First, collect a consistent global set.
        for (Class cls : pkg.classes) {
            if (!cls.hasInnerClasses())  continue;
            for (InnerClass ic : cls.getInnerClasses()) {
                InnerClass pic = allICMap.put(ic.thisClass, ic);
                if (pic != null && !pic.equals(ic) && pic.predictable) {
                    // Different ICs.  Choose the better to make global.
                    allICMap.put(pic.thisClass, pic);
                }
            }
        }

        InnerClass[] allICs = new InnerClass[allICMap.size()];
        allICMap.values().toArray(allICs);
        allICMap = null;  // done with it

        // Note: The InnerClasses attribute must be in a valid order,
        // so that A$B always occurs earlier than A$B$C.  This is an
        // important side-effect of sorting lexically by class name.
        Arrays.sort(allICs);  // put in canonical order
        pkg.setAllInnerClasses(Arrays.asList(allICs));

        // Next, empty out of every local set the consistent entries.
        // Calculate whether there is any remaining need to have a local
        // set, and whether it needs to be locked.
        for (Class cls : pkg.classes) {
            cls.minimizeLocalICs();
        }
    }

    void writeInnerClasses() throws IOException {
        for (InnerClass ic : pkg.getAllInnerClasses()) {
            int flags = ic.flags;
            assert((flags & ACC_IC_LONG_FORM) == 0);
            if (!ic.predictable) {
                flags |= ACC_IC_LONG_FORM;
            }
            ic_this_class.putRef(ic.thisClass);
            ic_flags.putInt(flags);
            if (!ic.predictable) {
                ic_outer_class.putRef(ic.outerClass);
                ic_name.putRef(ic.name);
            }
        }
    }

    /** If there are any extra InnerClasses entries to write which are
     *  not already implied by the global table, put them into a
     *  local attribute.  This is expected to be rare.
     */
    void writeLocalInnerClasses(Class cls) throws IOException {
        List<InnerClass> localICs = cls.getInnerClasses();
        class_InnerClasses_N.putInt(localICs.size());
        for(InnerClass ic : localICs) {
            class_InnerClasses_RC.putRef(ic.thisClass);
            // Is it redundant with the global version?
            if (ic.equals(pkg.getGlobalInnerClass(ic.thisClass))) {
                // A zero flag means copy a global IC here.
                class_InnerClasses_F.putInt(0);
            } else {
                int flags = ic.flags;
                if (flags == 0)
                    flags = ACC_IC_LONG_FORM;  // force it to be non-zero
                class_InnerClasses_F.putInt(flags);
                class_InnerClasses_outer_RCN.putRef(ic.outerClass);
                class_InnerClasses_name_RUN.putRef(ic.name);
            }
        }
    }

    void writeClassesAndByteCodes() throws IOException {
        Class[] classes = new Class[pkg.classes.size()];
        pkg.classes.toArray(classes);
        // Note:  This code respects the order in which caller put classes.
        if (verbose > 0)
            Utils.log.info("  ...scanning "+classes.length+" classes...");

        int nwritten = 0;
        for (int i = 0; i < classes.length; i++) {
            // Collect the class body, sans bytecodes.
            Class cls = classes[i];
            if (verbose > 1)
                Utils.log.fine("Scanning "+cls);

            ClassEntry   thisClass  = cls.thisClass;
            ClassEntry   superClass = cls.superClass;
            ClassEntry[] interfaces = cls.interfaces;
            // Encode rare case of null superClass as thisClass:
            assert(superClass != thisClass);  // bad class file!?
            if (superClass == null)  superClass = thisClass;
            class_this.putRef(thisClass);
            class_super.putRef(superClass);
            class_interface_count.putInt(cls.interfaces.length);
            for (int j = 0; j < interfaces.length; j++) {
                class_interface.putRef(interfaces[j]);
            }

            writeMembers(cls);
            writeAttrs(ATTR_CONTEXT_CLASS, cls, cls);

            nwritten++;
            if (verbose > 0 && (nwritten % 1000) == 0)
                Utils.log.info("Have scanned "+nwritten+" classes...");
        }
    }

    void writeMembers(Class cls) throws IOException {
        List<Class.Field> fields = cls.getFields();
        class_field_count.putInt(fields.size());
        for (Class.Field f : fields) {
            field_descr.putRef(f.getDescriptor());
            writeAttrs(ATTR_CONTEXT_FIELD, f, cls);
        }

        List<Class.Method> methods = cls.getMethods();
        class_method_count.putInt(methods.size());
        for (Class.Method m : methods) {
            method_descr.putRef(m.getDescriptor());
            writeAttrs(ATTR_CONTEXT_METHOD, m, cls);
            assert((m.code != null) == (m.getAttribute(attrCodeEmpty) != null));
            if (m.code != null) {
                writeCodeHeader(m.code);
                writeByteCodes(m.code);
            }
        }
    }

    void writeCodeHeader(Code c) throws IOException {
        boolean attrsOK = testBit(archiveOptions, AO_HAVE_ALL_CODE_FLAGS);
        int na = c.attributeSize();
        int sc = shortCodeHeader(c);
        if (!attrsOK && na > 0)
            // We must write flags, and can only do so for long headers.
            sc = LONG_CODE_HEADER;
        if (verbose > 2) {
            int siglen = c.getMethod().getArgumentSize();
            Utils.log.fine("Code sizes info "+c.max_stack+" "+c.max_locals+" "+c.getHandlerCount()+" "+siglen+" "+na+(sc > 0 ? " SHORT="+sc : ""));
        }
        code_headers.putByte(sc);
        if (sc == LONG_CODE_HEADER) {
            code_max_stack.putInt(c.getMaxStack());
            code_max_na_locals.putInt(c.getMaxNALocals());
            code_handler_count.putInt(c.getHandlerCount());
        } else {
            assert(attrsOK || na == 0);
            assert(c.getHandlerCount() < shortCodeHeader_h_limit);
        }
        writeCodeHandlers(c);
        if (sc == LONG_CODE_HEADER || attrsOK)
            writeAttrs(ATTR_CONTEXT_CODE, c, c.thisClass());
    }

    void writeCodeHandlers(Code c) throws IOException {
        int sum, del;
        for (int j = 0, jmax = c.getHandlerCount(); j < jmax; j++) {
            code_handler_class_RCN.putRef(c.handler_class[j]); // null OK
            // Encode end as offset from start, and catch as offset from end,
            // because they are strongly correlated.
            sum = c.encodeBCI(c.handler_start[j]);
            code_handler_start_P.putInt(sum);
            del = c.encodeBCI(c.handler_end[j]) - sum;
            code_handler_end_PO.putInt(del);
            sum += del;
            del = c.encodeBCI(c.handler_catch[j]) - sum;
            code_handler_catch_PO.putInt(del);
        }
    }

    // Generic routines for writing attributes and flags of
    // classes, fields, methods, and codes.
    void writeAttrs(int ctype,
                    final Attribute.Holder h,
                    Class cls) throws IOException {
        MultiBand xxx_attr_bands = attrBands[ctype];
        IntBand xxx_flags_hi = getAttrBand(xxx_attr_bands, AB_FLAGS_HI);
        IntBand xxx_flags_lo = getAttrBand(xxx_attr_bands, AB_FLAGS_LO);
        boolean haveLongFlags = haveFlagsHi(ctype);
        assert(attrIndexLimit[ctype] == (haveLongFlags? 63: 32));
        if (h.attributes == null) {
            xxx_flags_lo.putInt(h.flags);  // no extra bits to set here
            if (haveLongFlags)
                xxx_flags_hi.putInt(0);
            return;
        }
        if (verbose > 3)
            Utils.log.fine("Transmitting attrs for "+h+" flags="+Integer.toHexString(h.flags));

        long flagMask = attrFlagMask[ctype];  // which flags are attr bits?
        long flagsToAdd = 0;
        int overflowCount = 0;
        for (Attribute a : h.attributes) {
            Attribute.Layout def = a.layout();
            int index = (attrIndexTable.get(def)).intValue();
            assert(attrDefs.get(ctype).get(index) == def);
            if (verbose > 3)
                Utils.log.fine("add attr @"+index+" "+a+" in "+h);
            if (index < attrIndexLimit[ctype] && testBit(flagMask, 1L<<index)) {
                if (verbose > 3)
                    Utils.log.fine("Adding flag bit 1<<"+index+" in "+Long.toHexString(flagMask));
                assert(!testBit(h.flags, 1L<<index));
                flagsToAdd |= (1L<<index);
                flagMask -= (1L<<index);  // do not use this bit twice here
            } else {
                // an overflow attr.
                flagsToAdd |= (1L<<X_ATTR_OVERFLOW);
                overflowCount += 1;
                if (verbose > 3)
                    Utils.log.fine("Adding overflow attr #"+overflowCount);
                IntBand xxx_attr_indexes = getAttrBand(xxx_attr_bands, AB_ATTR_INDEXES);
                xxx_attr_indexes.putInt(index);
                // System.out.println("overflow @"+index);
            }
            if (def.bandCount == 0) {
                if (def == attrInnerClassesEmpty) {
                    // Special logic to write this attr.
                    writeLocalInnerClasses((Class) h);
                    continue;
                }
                // Empty attr; nothing more to write here.
                continue;
            }
            assert(a.fixups == null);
            final Band[] ab = attrBandTable.get(def);
            assert(ab != null);
            assert(ab.length == def.bandCount);
            final int[] bc = backCountTable.get(def);
            assert(bc != null);
            assert(bc.length == def.getCallables().length);
            // Write one attribute of type def into ab.
            if (verbose > 2)  Utils.log.fine("writing "+a+" in "+h);
            boolean isCV = (ctype == ATTR_CONTEXT_FIELD && def == attrConstantValue);
            if (isCV)  setConstantValueIndex((Class.Field)h);
            a.parse(cls, a.bytes(), 0, a.size(),
                      new Attribute.ValueStream() {
                public void putInt(int bandIndex, int value) {
                    ((IntBand) ab[bandIndex]).putInt(value);
                }
                public void putRef(int bandIndex, Entry ref) {
                    ((CPRefBand) ab[bandIndex]).putRef(ref);
                }
                public int encodeBCI(int bci) {
                    Code code = (Code) h;
                    return code.encodeBCI(bci);
                }
                public void noteBackCall(int whichCallable) {
                    assert(bc[whichCallable] >= 0);
                    bc[whichCallable] += 1;
                }
            });
            if (isCV)  setConstantValueIndex(null);  // clean up
        }

        if (overflowCount > 0) {
            IntBand xxx_attr_count = getAttrBand(xxx_attr_bands, AB_ATTR_COUNT);
            xxx_attr_count.putInt(overflowCount);
        }

        xxx_flags_lo.putInt(h.flags | (int)flagsToAdd);
        if (haveLongFlags)
            xxx_flags_hi.putInt((int)(flagsToAdd >>> 32));
        else
            assert((flagsToAdd >>> 32) == 0);
        assert((h.flags & flagsToAdd) == 0)
            : (h+".flags="
                +Integer.toHexString(h.flags)+"^"
                +Long.toHexString(flagsToAdd));
    }

    // temporary scratch variables for processing code blocks
    private Code                 curCode;
    private Class                curClass;
    private Entry[] curCPMap;
    private void beginCode(Code c) {
        assert(curCode == null);
        curCode = c;
        curClass = c.m.thisClass();
        curCPMap = c.getCPMap();
    }
    private void endCode() {
        curCode = null;
        curClass = null;
        curCPMap = null;
    }

    // Return an _invokeinit_op variant, if the instruction matches one,
    // else -1.
    private int initOpVariant(Instruction i, Entry newClass) {
        if (i.getBC() != _invokespecial)  return -1;
        MemberEntry ref = (MemberEntry) i.getCPRef(curCPMap);
        if ("<init>".equals(ref.descRef.nameRef.stringValue()) == false)
            return -1;
        ClassEntry refClass = ref.classRef;
        if (refClass == curClass.thisClass)
            return _invokeinit_op+_invokeinit_self_option;
        if (refClass == curClass.superClass)
            return _invokeinit_op+_invokeinit_super_option;
        if (refClass == newClass)
            return _invokeinit_op+_invokeinit_new_option;
        return -1;
    }

    // Return a _self_linker_op variant, if the instruction matches one,
    // else -1.
    private int selfOpVariant(Instruction i) {
        int bc = i.getBC();
        if (!(bc >= _first_linker_op && bc <= _last_linker_op))  return -1;
        MemberEntry ref = (MemberEntry) i.getCPRef(curCPMap);
        // do not optimize this case, simply fall back to regular coding
        if ((bc == _invokespecial || bc == _invokestatic) &&
                ref.tagEquals(CONSTANT_InterfaceMethodref))
            return -1;
        ClassEntry refClass = ref.classRef;
        int self_bc = _self_linker_op + (bc - _first_linker_op);
        if (refClass == curClass.thisClass)
            return self_bc;
        if (refClass == curClass.superClass)
            return self_bc + _self_linker_super_flag;
        return -1;
    }

    void writeByteCodes(Code code) throws IOException {
        beginCode(code);
        IndexGroup cp = pkg.cp;

        // true if the previous instruction is an aload to absorb
        boolean prevAload = false;

        // class of most recent new; helps compress <init> calls
        Entry newClass = null;

        for (Instruction i = code.instructionAt(0); i != null; i = i.next()) {
            // %%% Add a stress mode which issues _ref/_byte_escape.
            if (verbose > 3)  Utils.log.fine(i.toString());

            if (i.isNonstandard()) {
                // Crash and burn with a complaint if there are funny
                // bytecodes in this class file.
                String complaint = code.getMethod()
                    +" contains an unrecognized bytecode "+i
                    +"; please use the pass-file option on this class.";
                Utils.log.warning(complaint);
                throw new IOException(complaint);
            }

            if (i.isWide()) {
                if (verbose > 1) {
                    Utils.log.fine("_wide opcode in "+code);
                    Utils.log.fine(i.toString());
                }
                bc_codes.putByte(_wide);
                codeHist[_wide]++;
            }

            int bc = i.getBC();

            // Begin "bc_linker" compression.
            if (bc == _aload_0) {
                // Try to group aload_0 with a following operation.
                Instruction ni = code.instructionAt(i.getNextPC());
                if (selfOpVariant(ni) >= 0) {
                    prevAload = true;
                    continue;
                }
            }

            // Test for <init> invocations:
            int init_bc = initOpVariant(i, newClass);
            if (init_bc >= 0) {
                if (prevAload) {
                    // get rid of it
                    bc_codes.putByte(_aload_0);
                    codeHist[_aload_0]++;
                    prevAload = false;  //used up
                }
                // Write special bytecode.
                bc_codes.putByte(init_bc);
                codeHist[init_bc]++;
                MemberEntry ref = (MemberEntry) i.getCPRef(curCPMap);
                // Write operand to a separate band.
                int coding = cp.getOverloadingIndex(ref);
                bc_initref.putInt(coding);
                continue;
            }

            int self_bc = selfOpVariant(i);
            if (self_bc >= 0) {
                boolean isField = Instruction.isFieldOp(bc);
                boolean isSuper = (self_bc >= _self_linker_op+_self_linker_super_flag);
                boolean isAload = prevAload;
                prevAload = false;  //used up
                if (isAload)
                    self_bc += _self_linker_aload_flag;
                // Write special bytecode.
                bc_codes.putByte(self_bc);
                codeHist[self_bc]++;
                // Write field or method ref to a separate band.
                MemberEntry ref = (MemberEntry) i.getCPRef(curCPMap);
                CPRefBand bc_which = selfOpRefBand(self_bc);
                Index which_ix = cp.getMemberIndex(ref.tag, ref.classRef);
                bc_which.putRef(ref, which_ix);
                continue;
            }
            assert(!prevAload);
            // End "bc_linker" compression.

            // Normal bytecode.
            codeHist[bc]++;
            switch (bc) {
            case _tableswitch: // apc:  (df, lo, hi, (hi-lo+1)*(label))
            case _lookupswitch: // apc:  (df, nc, nc*(case, label))
                bc_codes.putByte(bc);
                Instruction.Switch isw = (Instruction.Switch) i;
                // Note that we do not write the alignment bytes.
                int apc = isw.getAlignedPC();
                int npc = isw.getNextPC();
                // write a length specification into the bytecode stream
                int caseCount = isw.getCaseCount();
                bc_case_count.putInt(caseCount);
                putLabel(bc_label, code, i.getPC(), isw.getDefaultLabel());
                for (int j = 0; j < caseCount; j++) {
                    putLabel(bc_label, code, i.getPC(), isw.getCaseLabel(j));
                }
                // Transmit case values in their own band.
                if (bc == _tableswitch) {
                    bc_case_value.putInt(isw.getCaseValue(0));
                } else {
                    for (int j = 0; j < caseCount; j++) {
                        bc_case_value.putInt(isw.getCaseValue(j));
                    }
                }
                // Done with the switch.
                continue;
            }

            int branch = i.getBranchLabel();
            if (branch >= 0) {
                bc_codes.putByte(bc);
                putLabel(bc_label, code, i.getPC(), branch);
                continue;
            }
            Entry ref = i.getCPRef(curCPMap);
            if (ref != null) {
                if (bc == _new)  newClass = ref;
                if (bc == _ldc)  ldcHist[ref.tag]++;
                CPRefBand bc_which;
                int vbc = bc;
                switch (i.getCPTag()) {
                case CONSTANT_LoadableValue:
                    switch (ref.tag) {
                    case CONSTANT_Integer:
                        bc_which = bc_intref;
                        switch (bc) {
                        case _ldc:    vbc = _ildc; break;
                        case _ldc_w:  vbc = _ildc_w; break;
                        default:      assert(false);
                        }
                        break;
                    case CONSTANT_Float:
                        bc_which = bc_floatref;
                        switch (bc) {
                        case _ldc:    vbc = _fldc; break;
                        case _ldc_w:  vbc = _fldc_w; break;
                        default:      assert(false);
                        }
                        break;
                    case CONSTANT_Long:
                        bc_which = bc_longref;
                        assert(bc == _ldc2_w);
                        vbc = _lldc2_w;
                        break;
                    case CONSTANT_Double:
                        bc_which = bc_doubleref;
                        assert(bc == _ldc2_w);
                        vbc = _dldc2_w;
                        break;
                    case CONSTANT_String:
                        bc_which = bc_stringref;
                        switch (bc) {
                        case _ldc:    vbc = _sldc; break;
                        case _ldc_w:  vbc = _sldc_w; break;
                        default:      assert(false);
                        }
                        break;
                    case CONSTANT_Class:
                        bc_which = bc_classref;
                        switch (bc) {
                        case _ldc:    vbc = _cldc; break;
                        case _ldc_w:  vbc = _cldc_w; break;
                        default:      assert(false);
                        }
                        break;
                    default:
                        // CONSTANT_MethodHandle, etc.
                        if (getHighestClassVersion().lessThan(JAVA7_MAX_CLASS_VERSION)) {
                            throw new IOException("bad class file major version for Java 7 ldc");
                        }
                        bc_which = bc_loadablevalueref;
                        switch (bc) {
                        case _ldc:    vbc = _qldc; break;
                        case _ldc_w:  vbc = _qldc_w; break;
                        default:      assert(false);
                        }
                    }
                    break;
                case CONSTANT_Class:
                    // Use a special shorthand for the current class:
                    if (ref == curClass.thisClass)  ref = null;
                    bc_which = bc_classref; break;
                case CONSTANT_Fieldref:
                    bc_which = bc_fieldref; break;
                case CONSTANT_Methodref:
                    if (ref.tagEquals(CONSTANT_InterfaceMethodref)) {
                        if (bc == _invokespecial)
                            vbc = _invokespecial_int;
                        if (bc == _invokestatic)
                            vbc = _invokestatic_int;
                        bc_which = bc_imethodref;
                    } else {
                        bc_which = bc_methodref;
                    }
                    break;
                case CONSTANT_InterfaceMethodref:
                    bc_which = bc_imethodref; break;
                case CONSTANT_InvokeDynamic:
                    bc_which = bc_indyref; break;
                default:
                    bc_which = null;
                    assert(false);
                }
                if (ref != null && bc_which.index != null && !bc_which.index.contains(ref)) {
                    // Crash and burn with a complaint if there are funny
                    // references for this bytecode instruction.
                    // Example:  invokestatic of a CONSTANT_InterfaceMethodref.
                    String complaint = code.getMethod() +
                        " contains a bytecode " + i +
                        " with an unsupported constant reference; please use the pass-file option on this class.";
                    Utils.log.warning(complaint);
                    throw new IOException(complaint);
                }
                bc_codes.putByte(vbc);
                bc_which.putRef(ref);
                // handle trailing junk
                if (bc == _multianewarray) {
                    assert(i.getConstant() == code.getByte(i.getPC()+3));
                    // Just dump the byte into the bipush pile
                    bc_byte.putByte(0xFF & i.getConstant());
                } else if (bc == _invokeinterface) {
                    assert(i.getLength() == 5);
                    // Make sure the discarded bytes are sane:
                    assert(i.getConstant() == (1+((MemberEntry)ref).descRef.typeRef.computeSize(true)) << 8);
                } else if (bc == _invokedynamic) {
                    if (getHighestClassVersion().lessThan(JAVA7_MAX_CLASS_VERSION)) {
                        throw new IOException("bad class major version for Java 7 invokedynamic");
                    }
                    assert(i.getLength() == 5);
                    assert(i.getConstant() == 0);  // last 2 bytes MBZ
                } else {
                    // Make sure there is nothing else to write.
                    assert(i.getLength() == ((bc == _ldc)?2:3));
                }
                continue;
            }
            int slot = i.getLocalSlot();
            if (slot >= 0) {
                bc_codes.putByte(bc);
                bc_local.putInt(slot);
                int con = i.getConstant();
                if (bc == _iinc) {
                    if (!i.isWide()) {
                        bc_byte.putByte(0xFF & con);
                    } else {
                        bc_short.putInt(0xFFFF & con);
                    }
                } else {
                    assert(con == 0);
                }
                continue;
            }
            // Generic instruction.  Copy the body.
            bc_codes.putByte(bc);
            int pc = i.getPC()+1;
            int npc = i.getNextPC();
            if (pc < npc) {
                // Do a few remaining multi-byte instructions.
                switch (bc) {
                case _sipush:
                    bc_short.putInt(0xFFFF & i.getConstant());
                    break;
                case _bipush:
                    bc_byte.putByte(0xFF & i.getConstant());
                    break;
                case _newarray:
                    bc_byte.putByte(0xFF & i.getConstant());
                    break;
                default:
                    assert(false);  // that's it
                }
            }
        }
        bc_codes.putByte(_end_marker);
        bc_codes.elementCountForDebug++;
        codeHist[_end_marker]++;
        endCode();
    }

    int[] codeHist = new int[1<<8];
    int[] ldcHist  = new int[20];
    void printCodeHist() {
        assert(verbose > 0);
        String[] hist = new String[codeHist.length];
        int totalBytes = 0;
        for (int bc = 0; bc < codeHist.length; bc++) {
            totalBytes += codeHist[bc];
        }
        for (int bc = 0; bc < codeHist.length; bc++) {
            if (codeHist[bc] == 0) { hist[bc] = ""; continue; }
            String iname = Instruction.byteName(bc);
            String count = "" + codeHist[bc];
            count = "         ".substring(count.length()) + count;
            String pct = "" + (codeHist[bc] * 10000 / totalBytes);
            while (pct.length() < 4) {
                pct = "0" + pct;
            }
            pct = pct.substring(0, pct.length()-2) + "." + pct.substring(pct.length()-2);
            hist[bc] = count + "  " + pct + "%  " + iname;
        }
        Arrays.sort(hist);
        System.out.println("Bytecode histogram ["+totalBytes+"]");
        for (int i = hist.length; --i >= 0; ) {
            if ("".equals(hist[i]))  continue;
            System.out.println(hist[i]);
        }
        for (int tag = 0; tag < ldcHist.length; tag++) {
            int count = ldcHist[tag];
            if (count == 0)  continue;
            System.out.println("ldc "+ConstantPool.tagName(tag)+" "+count);
        }
    }
}
