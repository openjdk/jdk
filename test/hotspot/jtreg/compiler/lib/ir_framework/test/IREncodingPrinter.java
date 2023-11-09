/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package compiler.lib.ir_framework.test;

import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.IRNode;
import compiler.lib.ir_framework.TestFramework;
import compiler.lib.ir_framework.shared.*;
import jdk.test.lib.Platform;
import jdk.test.whitebox.WhiteBox;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Prints an encoding to the dedicated test framework socket whether @IR rules of @Test methods should be applied or not.
 * This is done during the execution of the test VM by checking the active VM flags. This encoding is eventually parsed
 * and checked by the IRMatcher class in the driver VM after the termination of the test VM. IR rule indices start at 1.
 */
public class IREncodingPrinter {
    public static final String START = "##### IRMatchRulesEncoding - used by TestFramework #####";
    public static final String END = "----- END -----";
    public static final int NO_RULE_APPLIED = -1;

    private static final WhiteBox WHITE_BOX = WhiteBox.getWhiteBox();
    private static final List<Function<String, Object>> LONG_GETTERS = Arrays.asList(
            WHITE_BOX::getIntVMFlag, WHITE_BOX::getUintVMFlag, WHITE_BOX::getIntxVMFlag,
            WHITE_BOX::getUintxVMFlag, WHITE_BOX::getUint64VMFlag, WHITE_BOX::getSizeTVMFlag);

    private final StringBuilder output = new StringBuilder();
    private Method method;
    private int ruleIndex;

    // Platforms for use in IR preconditions. Please verify that e.g. there is
    // a corresponding use in a jtreg @requires annotation before adding new platforms,
    // as adding non-existent platforms can lead to skipped tests.
    private static final List<String> irTestingPlatforms = new ArrayList<String>(Arrays.asList(
        // os.family
        "linux",
        "mac",
        "windows",
        // vm.simpleArch
        "aarch64",
        "arm",
        "ppc",
        "riscv64",
        "s390",
        "x64",
        "x86",
        // corresponds to vm.bits
        "32-bit",
        "64-bit"
    ));

    // Please verify new CPU features before adding them. If we allow non-existent features
    // on this list, we will ignore tests and never execute them. Consult CPU_FEATURE_FLAGS
    // in corresponding vm_version_.hpp file to find correct cpu feature's name.
    private static final List<String> verifiedCPUFeatures = new ArrayList<String>( Arrays.asList(
        // x86
        "fma",
        "f16c",
        // Intel SSE
        "sse",
        "sse2",
        "sse3",
        "ssse3",
        "sse4.1",
        // Intel AVX
        "avx",
        "avx2",
        "avx512",
        "avx512bw",
        "avx512dq",
        "avx512vl",
        "avx512f",
        // AArch64
        "sha3",
        "asimd",
        "sve"
    ));

    public IREncodingPrinter() {
        output.append(START).append(System.lineSeparator());
        output.append("<method>,{comma separated applied @IR rule ids}").append(System.lineSeparator());
    }

    /**
     * Emits "<method>,{ids}" where {ids} is either:
     * - indices of all @IR rules that should be applied, separated by a comma
     * - "-1" if no @IR rule should not be applied
     */
    public void emitRuleEncoding(Method m, boolean skipped) {
        method = m;
        int i = 0;
        ArrayList<Integer> validRules = new ArrayList<>();
        IR[] irAnnos = m.getAnnotationsByType(IR.class);
        if (!skipped) {
            for (IR irAnno : irAnnos) {
                ruleIndex = i + 1;
                try {
                    if (shouldApplyIrRule(irAnno, m.getName(), ruleIndex, irAnnos.length)) {
                        validRules.add(ruleIndex);
                    }
                } catch (TestFormatException e) {
                    // Catch logged failure and continue to check other IR annotations.
                }
                i++;
            }
        }
        if (irAnnos.length != 0) {
            output.append(m.getName());
            if (validRules.isEmpty()) {
                output.append("," + NO_RULE_APPLIED);
            } else {
                for (i = 0; i < validRules.size(); i++) {
                    output.append(",").append(validRules.get(i));
                }
            }
            output.append(System.lineSeparator());
        }
    }

    private void printDisableReason(String method, String reason, String[] apply, int ruleIndex, int ruleMax) {
        TestFrameworkSocket.write("Disabling IR matching for rule " + ruleIndex + " of " + ruleMax + " in " +
                                  method + ": " + reason + ": " + String.join(", ", apply),
                                  "[IREncodingPrinter]", true);
    }

    private boolean shouldApplyIrRule(IR irAnno, String m, int ruleIndex, int ruleMax) {
        checkIRAnnotations(irAnno);
        if (isIRNodeUnsupported(irAnno)) {
            return false;
        } else if (irAnno.applyIfPlatform().length != 0 && !hasAllRequiredPlatform(irAnno.applyIfPlatform())) {
            printDisableReason(m, "Constraint not met (applyIfPlatform)", irAnno.applyIfPlatform(), ruleIndex, ruleMax);
            return false;
        } else if (irAnno.applyIfPlatformAnd().length != 0 && !hasAllRequiredPlatform(irAnno.applyIfPlatformAnd())) {
            printDisableReason(m, "Not all constraints are met (applyIfPlatformAnd)", irAnno.applyIfPlatformAnd(), ruleIndex, ruleMax);
            return false;
        } else if (irAnno.applyIfPlatformOr().length != 0 && !hasAnyRequiredPlatform(irAnno.applyIfPlatformOr())) {
            printDisableReason(m, "None of the constraints are met (applyIfPlatformOr)", irAnno.applyIfPlatformOr(), ruleIndex, ruleMax);
            return false;
        } else if (irAnno.applyIfCPUFeature().length != 0 && !hasAllRequiredCPUFeature(irAnno.applyIfCPUFeature())) {
            printDisableReason(m, "Feature constraint not met (applyIfCPUFeature)", irAnno.applyIfCPUFeature(), ruleIndex, ruleMax);
            return false;
        } else if (irAnno.applyIfCPUFeatureAnd().length != 0 && !hasAllRequiredCPUFeature(irAnno.applyIfCPUFeatureAnd())) {
            printDisableReason(m, "Not all feature constraints are met (applyIfCPUFeatureAnd)", irAnno.applyIfCPUFeatureAnd(), ruleIndex, ruleMax);
            return false;
        } else if (irAnno.applyIfCPUFeatureOr().length != 0 && !hasAnyRequiredCPUFeature(irAnno.applyIfCPUFeatureOr())) {
            printDisableReason(m, "None of the feature constraints met (applyIfCPUFeatureOr)", irAnno.applyIfCPUFeatureOr(), ruleIndex, ruleMax);
            return false;
        } else if (irAnno.applyIf().length != 0 && !hasAllRequiredFlags(irAnno.applyIf(), "applyIf")) {
            printDisableReason(m, "Flag constraint not met (applyIf)", irAnno.applyIf(), ruleIndex, ruleMax);
            return false;
        } else if (irAnno.applyIfNot().length != 0 && !hasNoRequiredFlags(irAnno.applyIfNot(), "applyIfNot")) {
            printDisableReason(m, "Flag constraint not met (applyIfNot)", irAnno.applyIfNot(), ruleIndex, ruleMax);
            return false;
        } else if (irAnno.applyIfAnd().length != 0 && !hasAllRequiredFlags(irAnno.applyIfAnd(), "applyIfAnd")) {
            printDisableReason(m, "Not all flag constraints are met (applyIfAnd)", irAnno.applyIfAnd(), ruleIndex, ruleMax);
            return false;
        } else if (irAnno.applyIfOr().length != 0 && hasNoRequiredFlags(irAnno.applyIfOr(), "applyIfOr")) {
            printDisableReason(m, "None of the flag constraints met (applyIfOr)", irAnno.applyIfOr(), ruleIndex, ruleMax);
            return false;
        } else {
            // All preconditions satisfied: apply rule.
            return true;
        }
    }

    private void checkIRAnnotations(IR irAnno) {
        TestFormat.checkNoThrow(irAnno.counts().length != 0 || irAnno.failOn().length != 0,
                                "Must specify either counts or failOn constraint" + failAt());
        int flagConstraints = 0;
        int platformConstraints = 0;
        int cpuFeatureConstraints = 0;
        if (irAnno.applyIfAnd().length != 0) {
            flagConstraints++;
            TestFormat.checkNoThrow(irAnno.applyIfAnd().length > 2,
                                    "Use applyIf or applyIfNot or at least 2 conditions for applyIfAnd" + failAt());
        }
        if (irAnno.applyIfOr().length != 0) {
            flagConstraints++;
            TestFormat.checkNoThrow(irAnno.applyIfOr().length > 2,
                                    "Use applyIf or applyIfNot or at least 2 conditions for applyIfOr" + failAt());
        }
        if (irAnno.applyIf().length != 0) {
            flagConstraints++;
            TestFormat.checkNoThrow(irAnno.applyIf().length <= 2,
                                    "Use applyIfAnd or applyIfOr or only 1 condition for applyIf" + failAt());
        }
        if (irAnno.applyIfPlatform().length != 0) {
            platformConstraints++;
            TestFormat.checkNoThrow(irAnno.applyIfPlatform().length == 2,
                                    "applyIfPlatform expects single platform pair" + failAt());
        }
        if (irAnno.applyIfPlatformAnd().length != 0) {
            platformConstraints++;
            TestFormat.checkNoThrow(irAnno.applyIfPlatformAnd().length % 2 == 0,
                                    "applyIfPlatformAnd expects more than one platform pair" + failAt());
        }
        if (irAnno.applyIfPlatformOr().length != 0) {
            platformConstraints++;
            TestFormat.checkNoThrow(irAnno.applyIfPlatformOr().length % 2 == 0,
                                    "applyIfPlatformOr expects more than one platform pair" + failAt());
        }
        if (irAnno.applyIfCPUFeature().length != 0) {
            cpuFeatureConstraints++;
            TestFormat.checkNoThrow(irAnno.applyIfCPUFeature().length == 2,
                                    "applyIfCPUFeature expects single CPU feature pair" + failAt());
        }
        if (irAnno.applyIfCPUFeatureAnd().length != 0) {
            cpuFeatureConstraints++;
            TestFormat.checkNoThrow(irAnno.applyIfCPUFeatureAnd().length % 2 == 0,
                                    "applyIfCPUFeatureAnd expects more than one CPU feature pair" + failAt());
        }
        if (irAnno.applyIfCPUFeatureOr().length != 0) {
            cpuFeatureConstraints++;
            TestFormat.checkNoThrow(irAnno.applyIfCPUFeatureOr().length % 2 == 0,
                                    "applyIfCPUFeatureOr expects more than one CPU feature pair" + failAt());
        }
        if (irAnno.applyIfNot().length != 0) {
            flagConstraints++;
            TestFormat.checkNoThrow(irAnno.applyIfNot().length <= 2,
                                    "Use applyIfAnd or applyIfOr or only 1 condition for applyIfNot" + failAt());
        }
        TestFormat.checkNoThrow(flagConstraints <= 1, "Can only specify one flag constraint" + failAt());
        TestFormat.checkNoThrow(platformConstraints <= 1, "Can only specify one platform constraint" + failAt());
        TestFormat.checkNoThrow(cpuFeatureConstraints <= 1, "Can only specify one CPU feature constraint" + failAt());
    }

    private boolean isIRNodeUnsupported(IR irAnno) {
        try {
            for (String s : irAnno.failOn()) {
                IRNode.checkIRNodeSupported(s);
            }
            for (String s : irAnno.counts()) {
                IRNode.checkIRNodeSupported(s);
            }
        } catch (CheckedTestFrameworkException e) {
            TestFrameworkSocket.write("Skip Rule " + ruleIndex + ": " + e.getMessage(), TestFrameworkSocket.DEFAULT_REGEX_TAG, true);
            return true;
        }
        return false;
    }

    private boolean hasAllRequiredFlags(String[] andRules, String ruleType) {
        boolean returnValue = true;
        for (int i = 0; i < andRules.length; i++) {
            String flag = andRules[i].trim();
            i++;
            TestFormat.check(i < andRules.length, "Missing value for flag " + flag + " in " + ruleType + failAt());
            String value = andRules[i].trim();
            if (!check(flag, value) && returnValue) {
                // Rule will not be applied but keep processing the other flags to verify that they are sane.
                returnValue = false;
            }
        }
        return returnValue;
    }

    private boolean hasAllRequiredPlatform(String[] andRules) {
        boolean returnValue = true;
        for (int i = 0; i < andRules.length; i++) {
            String platform = andRules[i].trim();
            i++;
            String value = andRules[i].trim();
            returnValue &= checkPlatform(platform, value);
        }
        return returnValue;
    }

    private boolean hasAnyRequiredPlatform(String[] orRules) {
        boolean returnValue = false;
        for (int i = 0; i < orRules.length; i++) {
            String platform = orRules[i].trim();
            i++;
            String value = orRules[i].trim();
            returnValue |= checkPlatform(platform, value);
        }
        return returnValue;
    }

    private boolean checkPlatform(String platform, String value) {
        if (platform.isEmpty()) {
            TestFormat.failNoThrow("Provided empty platform" + failAt());
            return false;
        }
        if (value.isEmpty()) {
            TestFormat.failNoThrow("Provided empty value for platform " + platform + failAt());
            return false;
        }

        if (!irTestingPlatforms.contains(platform)) {
            TestFormat.failNoThrow("Provided platform is not in verified list: " + platform + failAt());
            return false;
        }

        boolean trueValue = value.contains("true");
        boolean falseValue = value.contains("false");

        if (!trueValue && !falseValue) {
            TestFormat.failNoThrow("Provided incorrect value for platform " + platform + failAt());
            return false;
        }

        String os = "";
        if (Platform.isLinux()) {
            os = "linux";
        } else if (Platform.isOSX()) {
            os = "mac";
        } else if (Platform.isWindows()) {
            os = "windows";
        }

        String arch = "";
        if (Platform.isAArch64()) {
            arch = "aarch64";
        } else if (Platform.isARM()) {
            arch = "arm";
        } else if (Platform.isPPC()) {
            arch = "ppc";
        } else if (Platform.isRISCV64()) {
            arch = "riscv64";
        } else if (Platform.isS390x()) {
            arch = "s390";
        } else if (Platform.isX64()) {
            arch = "x64";
        } else if (Platform.isX86()) {
            arch = "x86";
        }

        String currentPlatform = os + " " + arch + " " + (Platform.is32bit() ? "32-bit" : "64-bit");

        return (trueValue && currentPlatform.contains(platform)) || (falseValue && !currentPlatform.contains(platform));
    }

    private boolean hasAllRequiredCPUFeature(String[] andRules) {
        boolean returnValue = true;
        for (int i = 0; i < andRules.length; i++) {
            String feature = andRules[i].trim();
            i++;
            String value = andRules[i].trim();
            returnValue &= checkCPUFeature(feature, value);
        }
        return returnValue;
    }

    private boolean hasAnyRequiredCPUFeature(String[] orRules) {
        boolean returnValue = false;
        for (int i = 0; i < orRules.length; i++) {
            String feature = orRules[i].trim();
            i++;
            String value = orRules[i].trim();
            returnValue |= checkCPUFeature(feature, value);
        }
        return returnValue;
    }

    private boolean checkCPUFeature(String feature, String value) {
        if (feature.isEmpty()) {
            TestFormat.failNoThrow("Provided empty feature" + failAt());
            return false;
        }
        if (value.isEmpty()) {
            TestFormat.failNoThrow("Provided empty value for feature " + feature + failAt());
            return false;
        }

        if (!verifiedCPUFeatures.contains(feature)) {
            TestFormat.failNoThrow("Provided CPU feature is not in verified list: " + feature + failAt());
            return false;
        }

        boolean trueValue = value.contains("true");
        boolean falseValue = value.contains("false");

        if (!trueValue && !falseValue) {
            TestFormat.failNoThrow("Provided incorrect value for feature " + feature + failAt());
            return false;
        }
        String cpuFeatures = WHITE_BOX.getCPUFeatures();
        return (trueValue && cpuFeatures.contains(feature)) || (falseValue && !cpuFeatures.contains(feature));
    }

    private boolean hasNoRequiredFlags(String[] orRules, String ruleType) {
        boolean returnValue = true;
        for (int i = 0; i < orRules.length; i++) {
            String flag = orRules[i];
            i++;
            TestFormat.check(i < orRules.length, "Missing value for flag " + flag + " in " + ruleType + failAt());
            String value = orRules[i];
            if (check(flag, value) && returnValue) {
                // Rule will not be applied but keep processing the other flags to verify that they are sane.
                returnValue = false;
            }
        }
        return returnValue;
    }

    private boolean check(String flag, String value) {
        if (flag.isEmpty()) {
            TestFormat.failNoThrow("Provided empty flag" + failAt());
            return false;
        }
        if (value.isEmpty()) {
            TestFormat.failNoThrow("Provided empty value for flag " + flag + failAt());
            return false;
        }
        Object actualFlagValue = WHITE_BOX.getBooleanVMFlag(flag);
        if (actualFlagValue != null) {
            return checkBooleanFlag(flag, value, (Boolean) actualFlagValue);
        }
        actualFlagValue = LONG_GETTERS.stream().map(f -> f.apply(flag)).filter(Objects::nonNull).findAny().orElse(null);
        if (actualFlagValue != null) {
            return checkFlag(Long::parseLong, "integer", flag, value, (Long) actualFlagValue);
        }
        actualFlagValue = WHITE_BOX.getDoubleVMFlag(flag);
        if (actualFlagValue != null) {
            return checkFlag(Double::parseDouble, "floating point", flag, value, (Double) actualFlagValue);
        }
        actualFlagValue = WHITE_BOX.getStringVMFlag(flag);
        if (actualFlagValue != null) {
            return value.equals(actualFlagValue);
        }

        // This could be improved if the Whitebox offers a "isVMFlag" function. For now, just check if we can actually set
        // a value for a string flag. If we find this value, it's a string flag. If null is returned, the flag is unknown.
        WHITE_BOX.setStringVMFlag(flag, "test");
        String stringFlagValue = WHITE_BOX.getStringVMFlag(flag);
        if (stringFlagValue == null) {
            TestFormat.failNoThrow("Could not find VM flag \"" + flag + "\"" + failAt());
            return false;
        }
        TestFramework.check(stringFlagValue.equals("test"),
                         "Must find newly set flag value \"test\" but found " + failAt());
        WHITE_BOX.setStringVMFlag(flag, null); // reset flag to NULL
        return false;
    }

    private boolean checkBooleanFlag(String flag, String value, boolean actualFlagValue) {
        boolean booleanValue = false;
        if ("true".equalsIgnoreCase(value)) {
            booleanValue = true;
        } else if (!"false".equalsIgnoreCase(value)) {
            TestFormat.failNoThrow("Invalid value \"" + value + "\" for boolean flag " + flag + failAt());
            return false;
        }
        return booleanValue == actualFlagValue;
    }

    private <T extends Comparable<T>> boolean checkFlag(Function<String, T> parseFunction, String kind, String flag,
                                                        String value, T actualFlagValue) {
        try {
            Comparison<T> comparison = ComparisonConstraintParser.parse(value, parseFunction);
            return comparison.compare(actualFlagValue);
        } catch (TestFormatException e) {
            // Format exception, do not apply rule.
            String postFixErrorMsg = " for " + kind + " based flag \"" + flag + "\"" + failAt();
            TestFormat.failNoThrow(e.getMessage() + postFixErrorMsg);
            return false;
        }
    }

    private String failAt() {
        return " in @IR rule " + ruleIndex + " at " + method;
    }

    public void emit() {
        output.append(END);
        TestFrameworkSocket.write(output.toString(), "IR rule application encoding");
    }
}


