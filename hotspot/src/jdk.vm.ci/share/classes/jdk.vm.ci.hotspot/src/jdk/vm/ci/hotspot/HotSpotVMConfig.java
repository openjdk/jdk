/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
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
package jdk.vm.ci.hotspot;

import static jdk.vm.ci.common.UnsafeUtil.readCString;
import static jdk.vm.ci.hotspot.HotSpotJVMCIRuntime.runtime;
import static jdk.vm.ci.hotspot.UnsafeAccess.UNSAFE;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Iterator;

import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.hotspotvmconfig.HotSpotVMAddress;
import jdk.vm.ci.hotspotvmconfig.HotSpotVMConstant;
import jdk.vm.ci.hotspotvmconfig.HotSpotVMData;
import jdk.vm.ci.hotspotvmconfig.HotSpotVMField;
import jdk.vm.ci.hotspotvmconfig.HotSpotVMFlag;
import jdk.vm.ci.hotspotvmconfig.HotSpotVMType;
import sun.misc.Unsafe;

//JaCoCo Exclude

/**
 * Used to access native configuration details.
 *
 * All non-static, public fields in this class are so that they can be compiled as constants.
 */
public class HotSpotVMConfig {

    /**
     * Gets the configuration associated with the singleton {@link HotSpotJVMCIRuntime}.
     */
    public static HotSpotVMConfig config() {
        return runtime().getConfig();
    }

    /**
     * Maximum allowed size of allocated area for a frame.
     */
    public final int maxFrameSize = 16 * 1024;

    public HotSpotVMConfig(CompilerToVM compilerToVm) {
        // Get raw pointer to the array that contains all gHotSpotVM values.
        final long gHotSpotVMData = compilerToVm.initializeConfiguration(this);
        assert gHotSpotVMData != 0;

        // Make FindBugs happy.
        jvmciHotSpotVMStructs = 0;
        jvmciHotSpotVMTypes = 0;
        jvmciHotSpotVMIntConstants = 0;
        jvmciHotSpotVMLongConstants = 0;
        jvmciHotSpotVMAddresses = 0;

        // Initialize the gHotSpotVM fields.
        for (Field f : HotSpotVMConfig.class.getDeclaredFields()) {
            if (f.isAnnotationPresent(HotSpotVMData.class)) {
                HotSpotVMData annotation = f.getAnnotation(HotSpotVMData.class);
                final int index = annotation.index();
                final long value = UNSAFE.getAddress(gHotSpotVMData + Unsafe.ADDRESS_SIZE * index);
                try {
                    f.setLong(this, value);
                } catch (IllegalAccessException e) {
                    throw new JVMCIError("index " + index, e);
                }
            }
        }

        // Quick sanity check.
        assert jvmciHotSpotVMStructs != 0;
        assert jvmciHotSpotVMTypes != 0;
        assert jvmciHotSpotVMIntConstants != 0;
        assert jvmciHotSpotVMLongConstants != 0;
        assert jvmciHotSpotVMAddresses != 0;

        initialize();

        oopEncoding = new CompressEncoding(narrowOopBase, narrowOopShift, logMinObjAlignment());
        klassEncoding = new CompressEncoding(narrowKlassBase, narrowKlassShift, logKlassAlignment);

        assert check();
        assert HotSpotVMConfigVerifier.check();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }

    /**
     * Initialize fields by reading their values from vmStructs.
     */
    private void initialize() {
        // Fill the VM fields hash map.
        HashMap<String, VMFields.Field> vmFields = new HashMap<>();
        for (VMFields.Field e : new VMFields(jvmciHotSpotVMStructs)) {
            vmFields.put(e.getName(), e);
        }

        // Fill the VM types hash map.
        HashMap<String, VMTypes.Type> vmTypes = new HashMap<>();
        for (VMTypes.Type e : new VMTypes(jvmciHotSpotVMTypes)) {
            vmTypes.put(e.getTypeName(), e);
        }

        // Fill the VM constants hash map.
        HashMap<String, AbstractConstant> vmConstants = new HashMap<>();
        for (AbstractConstant e : new VMIntConstants(jvmciHotSpotVMIntConstants)) {
            vmConstants.put(e.getName(), e);
        }
        for (AbstractConstant e : new VMLongConstants(jvmciHotSpotVMLongConstants)) {
            vmConstants.put(e.getName(), e);
        }

        // Fill the VM addresses hash map.
        HashMap<String, VMAddresses.Address> vmAddresses = new HashMap<>();
        for (VMAddresses.Address e : new VMAddresses(jvmciHotSpotVMAddresses)) {
            vmAddresses.put(e.getName(), e);
        }

        // Fill the flags hash map.
        HashMap<String, Flags.Flag> flags = new HashMap<>();
        for (Flags.Flag e : new Flags(vmFields, vmTypes)) {
            flags.put(e.getName(), e);
        }

        String osName = getHostOSName();
        String osArch = getHostArchitectureName();

        for (Field f : HotSpotVMConfig.class.getDeclaredFields()) {
            if (f.isAnnotationPresent(HotSpotVMField.class)) {
                HotSpotVMField annotation = f.getAnnotation(HotSpotVMField.class);
                String name = annotation.name();
                String type = annotation.type();
                VMFields.Field entry = vmFields.get(name);
                if (entry == null) {
                    if (!isRequired(osArch, annotation.archs())) {
                        continue;
                    }
                    throw new JVMCIError(f.getName() + ": expected VM field not found: " + name);
                }

                // Make sure the native type is still the type we expect.
                if (!type.isEmpty()) {
                    if (!type.equals(entry.getTypeString())) {
                        throw new JVMCIError(f.getName() + ": compiler expects type " + type + " but VM field " + name + " is of type " + entry.getTypeString());
                    }
                }

                switch (annotation.get()) {
                    case OFFSET:
                        setField(f, entry.getOffset());
                        break;
                    case ADDRESS:
                        setField(f, entry.getAddress());
                        break;
                    case VALUE:
                        setField(f, entry.getValue());
                        break;
                    default:
                        throw new JVMCIError(f.getName() + ": unknown kind: " + annotation.get());
                }
            } else if (f.isAnnotationPresent(HotSpotVMType.class)) {
                HotSpotVMType annotation = f.getAnnotation(HotSpotVMType.class);
                String name = annotation.name();
                VMTypes.Type entry = vmTypes.get(name);
                if (entry == null) {
                    throw new JVMCIError(f.getName() + ": expected VM type not found: " + name);
                }

                switch (annotation.get()) {
                    case SIZE:
                        setField(f, entry.getSize());
                        break;
                    default:
                        throw new JVMCIError(f.getName() + ": unknown kind: " + annotation.get());
                }
            } else if (f.isAnnotationPresent(HotSpotVMConstant.class)) {
                HotSpotVMConstant annotation = f.getAnnotation(HotSpotVMConstant.class);
                String name = annotation.name();
                AbstractConstant entry = vmConstants.get(name);
                if (entry == null) {
                    if (!isRequired(osArch, annotation.archs())) {
                        continue;
                    }
                    throw new JVMCIError(f.getName() + ": expected VM constant not found: " + name);
                }
                setField(f, entry.getValue());
            } else if (f.isAnnotationPresent(HotSpotVMAddress.class)) {
                HotSpotVMAddress annotation = f.getAnnotation(HotSpotVMAddress.class);
                String name = annotation.name();
                VMAddresses.Address entry = vmAddresses.get(name);
                if (entry == null) {
                    if (!isRequired(osName, annotation.os())) {
                        continue;
                    }
                    throw new JVMCIError(f.getName() + ": expected VM address not found: " + name);
                }
                setField(f, entry.getValue());
            } else if (f.isAnnotationPresent(HotSpotVMFlag.class)) {
                HotSpotVMFlag annotation = f.getAnnotation(HotSpotVMFlag.class);
                String name = annotation.name();
                Flags.Flag entry = flags.get(name);
                if (entry == null) {
                    if (annotation.optional() || !isRequired(osArch, annotation.archs())) {
                        continue;
                    }
                    throw new JVMCIError(f.getName() + ": expected VM flag not found: " + name);

                }
                setField(f, entry.getValue());
            }
        }
    }

    private final CompressEncoding oopEncoding;
    private final CompressEncoding klassEncoding;

    public CompressEncoding getOopEncoding() {
        return oopEncoding;
    }

    public CompressEncoding getKlassEncoding() {
        return klassEncoding;
    }

    private void setField(Field field, Object value) {
        try {
            Class<?> fieldType = field.getType();
            if (fieldType == boolean.class) {
                if (value instanceof String) {
                    field.setBoolean(this, Boolean.valueOf((String) value));
                } else if (value instanceof Boolean) {
                    field.setBoolean(this, (boolean) value);
                } else if (value instanceof Long) {
                    field.setBoolean(this, ((long) value) != 0);
                } else {
                    throw new JVMCIError(value.getClass().getSimpleName());
                }
            } else if (fieldType == byte.class) {
                if (value instanceof Long) {
                    field.setByte(this, (byte) (long) value);
                } else {
                    throw new JVMCIError(value.getClass().getSimpleName());
                }
            } else if (fieldType == int.class) {
                if (value instanceof Integer) {
                    field.setInt(this, (int) value);
                } else if (value instanceof Long) {
                    field.setInt(this, (int) (long) value);
                } else {
                    throw new JVMCIError(value.getClass().getSimpleName());
                }
            } else if (fieldType == long.class) {
                field.setLong(this, (long) value);
            } else {
                throw new JVMCIError(field.toString());
            }
        } catch (IllegalAccessException e) {
            throw new JVMCIError("%s: %s", field, e);
        }
    }

    /**
     * Gets the host operating system name.
     */
    private static String getHostOSName() {
        String osName = System.getProperty("os.name");
        switch (osName) {
            case "Linux":
                osName = "linux";
                break;
            case "SunOS":
                osName = "solaris";
                break;
            case "Mac OS X":
                osName = "bsd";
                break;
            default:
                // Of course Windows is different...
                if (osName.startsWith("Windows")) {
                    osName = "windows";
                } else {
                    throw new JVMCIError("Unexpected OS name: " + osName);
                }
        }
        return osName;
    }

    /**
     * Gets the host architecture name for the purpose of finding the corresponding
     * {@linkplain HotSpotJVMCIBackendFactory backend}.
     */
    public String getHostArchitectureName() {
        String arch = System.getProperty("os.arch");
        switch (arch) {
            case "x86_64":
                arch = "amd64";
                break;
            case "sparcv9":
                arch = "sparc";
                break;
        }
        return arch;
    }

    /**
     * Determines if the current specification is included in a given set of specifications.
     *
     * @param current
     * @param specification specifies a set of specifications, e.g. architectures or operating
     *            systems. A zero length value implies all.
     */
    private static boolean isRequired(String current, String[] specification) {
        if (specification.length == 0) {
            return true;
        }
        for (String arch : specification) {
            if (arch.equals(current)) {
                return true;
            }
        }
        return false;
    }

    /**
     * VMStructEntry (see {@code vmStructs.hpp}).
     */
    @HotSpotVMData(index = 0) @Stable private long jvmciHotSpotVMStructs;
    @HotSpotVMData(index = 1) @Stable private long jvmciHotSpotVMStructEntryTypeNameOffset;
    @HotSpotVMData(index = 2) @Stable private long jvmciHotSpotVMStructEntryFieldNameOffset;
    @HotSpotVMData(index = 3) @Stable private long jvmciHotSpotVMStructEntryTypeStringOffset;
    @HotSpotVMData(index = 4) @Stable private long jvmciHotSpotVMStructEntryIsStaticOffset;
    @HotSpotVMData(index = 5) @Stable private long jvmciHotSpotVMStructEntryOffsetOffset;
    @HotSpotVMData(index = 6) @Stable private long jvmciHotSpotVMStructEntryAddressOffset;
    @HotSpotVMData(index = 7) @Stable private long jvmciHotSpotVMStructEntryArrayStride;

    final class VMFields implements Iterable<VMFields.Field> {

        private final long address;

        VMFields(long address) {
            this.address = address;
        }

        public Iterator<VMFields.Field> iterator() {
            return new Iterator<VMFields.Field>() {

                private int index = 0;

                private Field current() {
                    return new Field(address + jvmciHotSpotVMStructEntryArrayStride * index);
                }

                /**
                 * The last entry is identified by a NULL fieldName.
                 */
                public boolean hasNext() {
                    Field entry = current();
                    return entry.getFieldName() != null;
                }

                public Field next() {
                    Field entry = current();
                    index++;
                    return entry;
                }
            };
        }

        final class Field {

            private final long entryAddress;

            Field(long address) {
                this.entryAddress = address;
            }

            public String getTypeName() {
                long typeNameAddress = UNSAFE.getAddress(entryAddress + jvmciHotSpotVMStructEntryTypeNameOffset);
                return readCString(UNSAFE, typeNameAddress);
            }

            public String getFieldName() {
                long fieldNameAddress = UNSAFE.getAddress(entryAddress + jvmciHotSpotVMStructEntryFieldNameOffset);
                return readCString(UNSAFE, fieldNameAddress);
            }

            public String getTypeString() {
                long typeStringAddress = UNSAFE.getAddress(entryAddress + jvmciHotSpotVMStructEntryTypeStringOffset);
                return readCString(UNSAFE, typeStringAddress);
            }

            public boolean isStatic() {
                return UNSAFE.getInt(entryAddress + jvmciHotSpotVMStructEntryIsStaticOffset) != 0;
            }

            public long getOffset() {
                return UNSAFE.getLong(entryAddress + jvmciHotSpotVMStructEntryOffsetOffset);
            }

            public long getAddress() {
                return UNSAFE.getAddress(entryAddress + jvmciHotSpotVMStructEntryAddressOffset);
            }

            public String getName() {
                String typeName = getTypeName();
                String fieldName = getFieldName();
                return typeName + "::" + fieldName;
            }

            public long getValue() {
                String type = getTypeString();
                switch (type) {
                    case "bool":
                        return UNSAFE.getByte(getAddress());
                    case "int":
                        return UNSAFE.getInt(getAddress());
                    case "uint64_t":
                        return UNSAFE.getLong(getAddress());
                    case "address":
                    case "intptr_t":
                    case "uintptr_t":
                    case "size_t":
                        return UNSAFE.getAddress(getAddress());
                    default:
                        // All foo* types are addresses.
                        if (type.endsWith("*")) {
                            return UNSAFE.getAddress(getAddress());
                        }
                        throw new JVMCIError(type);
                }
            }

            @Override
            public String toString() {
                return String.format("Field[typeName=%s, fieldName=%s, typeString=%s, isStatic=%b, offset=%d, address=0x%x]", getTypeName(), getFieldName(), getTypeString(), isStatic(), getOffset(),
                                getAddress());
            }
        }
    }

    /**
     * VMTypeEntry (see vmStructs.hpp).
     */
    @HotSpotVMData(index = 8) @Stable private long jvmciHotSpotVMTypes;
    @HotSpotVMData(index = 9) @Stable private long jvmciHotSpotVMTypeEntryTypeNameOffset;
    @HotSpotVMData(index = 10) @Stable private long jvmciHotSpotVMTypeEntrySuperclassNameOffset;
    @HotSpotVMData(index = 11) @Stable private long jvmciHotSpotVMTypeEntryIsOopTypeOffset;
    @HotSpotVMData(index = 12) @Stable private long jvmciHotSpotVMTypeEntryIsIntegerTypeOffset;
    @HotSpotVMData(index = 13) @Stable private long jvmciHotSpotVMTypeEntryIsUnsignedOffset;
    @HotSpotVMData(index = 14) @Stable private long jvmciHotSpotVMTypeEntrySizeOffset;
    @HotSpotVMData(index = 15) @Stable private long jvmciHotSpotVMTypeEntryArrayStride;

    final class VMTypes implements Iterable<VMTypes.Type> {

        private final long address;

        VMTypes(long address) {
            this.address = address;
        }

        public Iterator<VMTypes.Type> iterator() {
            return new Iterator<VMTypes.Type>() {

                private int index = 0;

                private Type current() {
                    return new Type(address + jvmciHotSpotVMTypeEntryArrayStride * index);
                }

                /**
                 * The last entry is identified by a NULL type name.
                 */
                public boolean hasNext() {
                    Type entry = current();
                    return entry.getTypeName() != null;
                }

                public Type next() {
                    Type entry = current();
                    index++;
                    return entry;
                }
            };
        }

        final class Type {

            private final long entryAddress;

            Type(long address) {
                this.entryAddress = address;
            }

            public String getTypeName() {
                long typeNameAddress = UNSAFE.getAddress(entryAddress + jvmciHotSpotVMTypeEntryTypeNameOffset);
                return readCString(UNSAFE, typeNameAddress);
            }

            public String getSuperclassName() {
                long superclassNameAddress = UNSAFE.getAddress(entryAddress + jvmciHotSpotVMTypeEntrySuperclassNameOffset);
                return readCString(UNSAFE, superclassNameAddress);
            }

            public boolean isOopType() {
                return UNSAFE.getInt(entryAddress + jvmciHotSpotVMTypeEntryIsOopTypeOffset) != 0;
            }

            public boolean isIntegerType() {
                return UNSAFE.getInt(entryAddress + jvmciHotSpotVMTypeEntryIsIntegerTypeOffset) != 0;
            }

            public boolean isUnsigned() {
                return UNSAFE.getInt(entryAddress + jvmciHotSpotVMTypeEntryIsUnsignedOffset) != 0;
            }

            public long getSize() {
                return UNSAFE.getLong(entryAddress + jvmciHotSpotVMTypeEntrySizeOffset);
            }

            @Override
            public String toString() {
                return String.format("Type[typeName=%s, superclassName=%s, isOopType=%b, isIntegerType=%b, isUnsigned=%b, size=%d]", getTypeName(), getSuperclassName(), isOopType(), isIntegerType(),
                                isUnsigned(), getSize());
            }
        }
    }

    public abstract class AbstractConstant {

        protected final long address;
        protected final long nameOffset;
        protected final long valueOffset;

        AbstractConstant(long address, long nameOffset, long valueOffset) {
            this.address = address;
            this.nameOffset = nameOffset;
            this.valueOffset = valueOffset;
        }

        public String getName() {
            long nameAddress = UNSAFE.getAddress(address + nameOffset);
            return readCString(UNSAFE, nameAddress);
        }

        public abstract long getValue();
    }

    /**
     * VMIntConstantEntry (see vmStructs.hpp).
     */
    @HotSpotVMData(index = 16) @Stable private long jvmciHotSpotVMIntConstants;
    @HotSpotVMData(index = 17) @Stable private long jvmciHotSpotVMIntConstantEntryNameOffset;
    @HotSpotVMData(index = 18) @Stable private long jvmciHotSpotVMIntConstantEntryValueOffset;
    @HotSpotVMData(index = 19) @Stable private long jvmciHotSpotVMIntConstantEntryArrayStride;

    final class VMIntConstants implements Iterable<VMIntConstants.Constant> {

        private final long address;

        VMIntConstants(long address) {
            this.address = address;
        }

        public Iterator<VMIntConstants.Constant> iterator() {
            return new Iterator<VMIntConstants.Constant>() {

                private int index = 0;

                private Constant current() {
                    return new Constant(address + jvmciHotSpotVMIntConstantEntryArrayStride * index);
                }

                /**
                 * The last entry is identified by a NULL name.
                 */
                public boolean hasNext() {
                    Constant entry = current();
                    return entry.getName() != null;
                }

                public Constant next() {
                    Constant entry = current();
                    index++;
                    return entry;
                }
            };
        }

        final class Constant extends AbstractConstant {

            Constant(long address) {
                super(address, jvmciHotSpotVMIntConstantEntryNameOffset, jvmciHotSpotVMIntConstantEntryValueOffset);
            }

            @Override
            public long getValue() {
                return UNSAFE.getInt(address + valueOffset);
            }

            @Override
            public String toString() {
                return String.format("IntConstant[name=%s, value=%d (0x%x)]", getName(), getValue(), getValue());
            }
        }
    }

    /**
     * VMLongConstantEntry (see vmStructs.hpp).
     */
    @HotSpotVMData(index = 20) @Stable private long jvmciHotSpotVMLongConstants;
    @HotSpotVMData(index = 21) @Stable private long jvmciHotSpotVMLongConstantEntryNameOffset;
    @HotSpotVMData(index = 22) @Stable private long jvmciHotSpotVMLongConstantEntryValueOffset;
    @HotSpotVMData(index = 23) @Stable private long jvmciHotSpotVMLongConstantEntryArrayStride;

    final class VMLongConstants implements Iterable<VMLongConstants.Constant> {

        private final long address;

        VMLongConstants(long address) {
            this.address = address;
        }

        public Iterator<VMLongConstants.Constant> iterator() {
            return new Iterator<VMLongConstants.Constant>() {

                private int index = 0;

                private Constant currentEntry() {
                    return new Constant(address + jvmciHotSpotVMLongConstantEntryArrayStride * index);
                }

                /**
                 * The last entry is identified by a NULL name.
                 */
                public boolean hasNext() {
                    Constant entry = currentEntry();
                    return entry.getName() != null;
                }

                public Constant next() {
                    Constant entry = currentEntry();
                    index++;
                    return entry;
                }
            };
        }

        final class Constant extends AbstractConstant {

            Constant(long address) {
                super(address, jvmciHotSpotVMLongConstantEntryNameOffset, jvmciHotSpotVMLongConstantEntryValueOffset);
            }

            @Override
            public long getValue() {
                return UNSAFE.getLong(address + valueOffset);
            }

            @Override
            public String toString() {
                return String.format("LongConstant[name=%s, value=%d (0x%x)]", getName(), getValue(), getValue());
            }
        }
    }

    /**
     * VMAddressEntry (see vmStructs.hpp).
     */
    @HotSpotVMData(index = 24) @Stable private long jvmciHotSpotVMAddresses;
    @HotSpotVMData(index = 25) @Stable private long jvmciHotSpotVMAddressEntryNameOffset;
    @HotSpotVMData(index = 26) @Stable private long jvmciHotSpotVMAddressEntryValueOffset;
    @HotSpotVMData(index = 27) @Stable private long jvmciHotSpotVMAddressEntryArrayStride;

    final class VMAddresses implements Iterable<VMAddresses.Address> {

        private final long address;

        VMAddresses(long address) {
            this.address = address;
        }

        public Iterator<VMAddresses.Address> iterator() {
            return new Iterator<VMAddresses.Address>() {

                private int index = 0;

                private Address currentEntry() {
                    return new Address(address + jvmciHotSpotVMAddressEntryArrayStride * index);
                }

                /**
                 * The last entry is identified by a NULL name.
                 */
                public boolean hasNext() {
                    Address entry = currentEntry();
                    return entry.getName() != null;
                }

                public Address next() {
                    Address entry = currentEntry();
                    index++;
                    return entry;
                }
            };
        }

        final class Address extends AbstractConstant {

            Address(long address) {
                super(address, jvmciHotSpotVMAddressEntryNameOffset, jvmciHotSpotVMAddressEntryValueOffset);
            }

            @Override
            public long getValue() {
                return UNSAFE.getLong(address + valueOffset);
            }

            @Override
            public String toString() {
                return String.format("Address[name=%s, value=%d (0x%x)]", getName(), getValue(), getValue());
            }
        }
    }

    final class Flags implements Iterable<Flags.Flag> {

        private final long address;
        private final long entrySize;
        private final long typeOffset;
        private final long nameOffset;
        private final long addrOffset;

        Flags(HashMap<String, VMFields.Field> vmStructs, HashMap<String, VMTypes.Type> vmTypes) {
            address = vmStructs.get("Flag::flags").getValue();
            entrySize = vmTypes.get("Flag").getSize();
            typeOffset = vmStructs.get("Flag::_type").getOffset();
            nameOffset = vmStructs.get("Flag::_name").getOffset();
            addrOffset = vmStructs.get("Flag::_addr").getOffset();

            assert vmTypes.get("bool").getSize() == Byte.BYTES;
            assert vmTypes.get("intx").getSize() == Long.BYTES;
            assert vmTypes.get("uintx").getSize() == Long.BYTES;
        }

        public Iterator<Flags.Flag> iterator() {
            return new Iterator<Flags.Flag>() {

                private int index = 0;

                private Flag current() {
                    return new Flag(address + entrySize * index);
                }

                /**
                 * The last entry is identified by a NULL name.
                 */
                public boolean hasNext() {
                    Flag entry = current();
                    return entry.getName() != null;
                }

                public Flag next() {
                    Flag entry = current();
                    index++;
                    return entry;
                }
            };
        }

        final class Flag {

            private final long entryAddress;

            Flag(long address) {
                this.entryAddress = address;
            }

            public String getType() {
                long typeAddress = UNSAFE.getAddress(entryAddress + typeOffset);
                return readCString(UNSAFE, typeAddress);
            }

            public String getName() {
                long nameAddress = UNSAFE.getAddress(entryAddress + nameOffset);
                return readCString(UNSAFE, nameAddress);
            }

            public long getAddr() {
                return UNSAFE.getAddress(entryAddress + addrOffset);
            }

            public Object getValue() {
                switch (getType()) {
                    case "bool":
                        return Boolean.valueOf(UNSAFE.getByte(getAddr()) != 0);
                    case "intx":
                    case "uintx":
                    case "uint64_t":
                        return Long.valueOf(UNSAFE.getLong(getAddr()));
                    case "double":
                        return Double.valueOf(UNSAFE.getDouble(getAddr()));
                    case "ccstr":
                    case "ccstrlist":
                        return readCString(UNSAFE, getAddr());
                    default:
                        throw new JVMCIError(getType());
                }
            }

            @Override
            public String toString() {
                return String.format("Flag[type=%s, name=%s, value=%s]", getType(), getName(), getValue());
            }
        }
    }

    @HotSpotVMConstant(name = "ASSERT") @Stable public boolean cAssertions;
    public final boolean windowsOs = System.getProperty("os.name", "").startsWith("Windows");
    public final boolean linuxOs = System.getProperty("os.name", "").startsWith("Linux");

    @HotSpotVMFlag(name = "CodeEntryAlignment") @Stable public int codeEntryAlignment;
    @HotSpotVMFlag(name = "VerifyOops") @Stable public boolean verifyOops;
    @HotSpotVMFlag(name = "CITime") @Stable public boolean ciTime;
    @HotSpotVMFlag(name = "CITimeEach") @Stable public boolean ciTimeEach;
    @HotSpotVMFlag(name = "CompileTheWorldStartAt", optional = true) @Stable public int compileTheWorldStartAt;
    @HotSpotVMFlag(name = "CompileTheWorldStopAt", optional = true) @Stable public int compileTheWorldStopAt;
    @HotSpotVMFlag(name = "DontCompileHugeMethods") @Stable public boolean dontCompileHugeMethods;
    @HotSpotVMFlag(name = "HugeMethodLimit") @Stable public int hugeMethodLimit;
    @HotSpotVMFlag(name = "PrintInlining") @Stable public boolean printInlining;
    @HotSpotVMFlag(name = "JVMCIUseFastLocking") @Stable public boolean useFastLocking;
    @HotSpotVMFlag(name = "ForceUnreachable") @Stable public boolean forceUnreachable;
    @HotSpotVMFlag(name = "CodeCacheSegmentSize") @Stable public int codeSegmentSize;
    @HotSpotVMFlag(name = "FoldStableValues") @Stable public boolean foldStableValues;

    @HotSpotVMFlag(name = "UseTLAB") @Stable public boolean useTLAB;
    @HotSpotVMFlag(name = "UseBiasedLocking") @Stable public boolean useBiasedLocking;
    @HotSpotVMFlag(name = "UsePopCountInstruction") @Stable public boolean usePopCountInstruction;
    @HotSpotVMFlag(name = "UseCountLeadingZerosInstruction", archs = {"amd64"}) @Stable public boolean useCountLeadingZerosInstruction;
    @HotSpotVMFlag(name = "UseCountTrailingZerosInstruction", archs = {"amd64"}) @Stable public boolean useCountTrailingZerosInstruction;
    @HotSpotVMFlag(name = "UseAESIntrinsics") @Stable public boolean useAESIntrinsics;
    @HotSpotVMFlag(name = "UseCRC32Intrinsics") @Stable public boolean useCRC32Intrinsics;
    @HotSpotVMFlag(name = "UseG1GC") @Stable public boolean useG1GC;
    @HotSpotVMFlag(name = "UseConcMarkSweepGC") @Stable public boolean useCMSGC;

    @HotSpotVMFlag(name = "AllocatePrefetchStyle") @Stable public int allocatePrefetchStyle;
    @HotSpotVMFlag(name = "AllocatePrefetchInstr") @Stable public int allocatePrefetchInstr;
    @HotSpotVMFlag(name = "AllocatePrefetchLines") @Stable public int allocatePrefetchLines;
    @HotSpotVMFlag(name = "AllocateInstancePrefetchLines") @Stable public int allocateInstancePrefetchLines;
    @HotSpotVMFlag(name = "AllocatePrefetchStepSize") @Stable public int allocatePrefetchStepSize;
    @HotSpotVMFlag(name = "AllocatePrefetchDistance") @Stable public int allocatePrefetchDistance;

    @HotSpotVMFlag(name = "FlightRecorder", optional = true) @Stable public boolean flightRecorder;

    @HotSpotVMField(name = "CompilerToVM::Data::Universe_collectedHeap", type = "CollectedHeap*", get = HotSpotVMField.Type.VALUE) @Stable private long universeCollectedHeap;
    @HotSpotVMField(name = "CollectedHeap::_total_collections", type = "unsigned int", get = HotSpotVMField.Type.OFFSET) @Stable private int collectedHeapTotalCollectionsOffset;

    public long gcTotalCollectionsAddress() {
        return universeCollectedHeap + collectedHeapTotalCollectionsOffset;
    }

    @HotSpotVMFlag(name = "ReduceInitialCardMarks") @Stable public boolean useDeferredInitBarriers;

    // Compressed Oops related values.
    @HotSpotVMFlag(name = "UseCompressedOops") @Stable public boolean useCompressedOops;
    @HotSpotVMFlag(name = "UseCompressedClassPointers") @Stable public boolean useCompressedClassPointers;

    @HotSpotVMField(name = "CompilerToVM::Data::Universe_narrow_oop_base", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long narrowOopBase;
    @HotSpotVMField(name = "CompilerToVM::Data::Universe_narrow_oop_shift", type = "int", get = HotSpotVMField.Type.VALUE) @Stable public int narrowOopShift;
    @HotSpotVMFlag(name = "ObjectAlignmentInBytes") @Stable public int objectAlignment;

    public final int minObjAlignment() {
        return objectAlignment / heapWordSize;
    }

    public final int logMinObjAlignment() {
        return (int) (Math.log(objectAlignment) / Math.log(2));
    }

    @HotSpotVMType(name = "narrowKlass", get = HotSpotVMType.Type.SIZE) @Stable public int narrowKlassSize;
    @HotSpotVMField(name = "CompilerToVM::Data::Universe_narrow_klass_base", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long narrowKlassBase;
    @HotSpotVMField(name = "CompilerToVM::Data::Universe_narrow_klass_shift", type = "int", get = HotSpotVMField.Type.VALUE) @Stable public int narrowKlassShift;
    @HotSpotVMConstant(name = "LogKlassAlignmentInBytes") @Stable public int logKlassAlignment;

    // CPU capabilities
    @HotSpotVMFlag(name = "UseSSE") @Stable public int useSSE;
    @HotSpotVMFlag(name = "UseAVX", archs = {"amd64"}) @Stable public int useAVX;

    @HotSpotVMField(name = "Abstract_VM_Version::_features", type = "uint64_t", get = HotSpotVMField.Type.VALUE) @Stable public long vmVersionFeatures;

    // AMD64 specific values
    @HotSpotVMConstant(name = "VM_Version::CPU_CX8", archs = {"amd64"}) @Stable public long amd64CX8;
    @HotSpotVMConstant(name = "VM_Version::CPU_CMOV", archs = {"amd64"}) @Stable public long amd64CMOV;
    @HotSpotVMConstant(name = "VM_Version::CPU_FXSR", archs = {"amd64"}) @Stable public long amd64FXSR;
    @HotSpotVMConstant(name = "VM_Version::CPU_HT", archs = {"amd64"}) @Stable public long amd64HT;
    @HotSpotVMConstant(name = "VM_Version::CPU_MMX", archs = {"amd64"}) @Stable public long amd64MMX;
    @HotSpotVMConstant(name = "VM_Version::CPU_3DNOW_PREFETCH", archs = {"amd64"}) @Stable public long amd643DNOWPREFETCH;
    @HotSpotVMConstant(name = "VM_Version::CPU_SSE", archs = {"amd64"}) @Stable public long amd64SSE;
    @HotSpotVMConstant(name = "VM_Version::CPU_SSE2", archs = {"amd64"}) @Stable public long amd64SSE2;
    @HotSpotVMConstant(name = "VM_Version::CPU_SSE3", archs = {"amd64"}) @Stable public long amd64SSE3;
    @HotSpotVMConstant(name = "VM_Version::CPU_SSSE3", archs = {"amd64"}) @Stable public long amd64SSSE3;
    @HotSpotVMConstant(name = "VM_Version::CPU_SSE4A", archs = {"amd64"}) @Stable public long amd64SSE4A;
    @HotSpotVMConstant(name = "VM_Version::CPU_SSE4_1", archs = {"amd64"}) @Stable public long amd64SSE41;
    @HotSpotVMConstant(name = "VM_Version::CPU_SSE4_2", archs = {"amd64"}) @Stable public long amd64SSE42;
    @HotSpotVMConstant(name = "VM_Version::CPU_POPCNT", archs = {"amd64"}) @Stable public long amd64POPCNT;
    @HotSpotVMConstant(name = "VM_Version::CPU_LZCNT", archs = {"amd64"}) @Stable public long amd64LZCNT;
    @HotSpotVMConstant(name = "VM_Version::CPU_TSC", archs = {"amd64"}) @Stable public long amd64TSC;
    @HotSpotVMConstant(name = "VM_Version::CPU_TSCINV", archs = {"amd64"}) @Stable public long amd64TSCINV;
    @HotSpotVMConstant(name = "VM_Version::CPU_AVX", archs = {"amd64"}) @Stable public long amd64AVX;
    @HotSpotVMConstant(name = "VM_Version::CPU_AVX2", archs = {"amd64"}) @Stable public long amd64AVX2;
    @HotSpotVMConstant(name = "VM_Version::CPU_AES", archs = {"amd64"}) @Stable public long amd64AES;
    @HotSpotVMConstant(name = "VM_Version::CPU_ERMS", archs = {"amd64"}) @Stable public long amd64ERMS;
    @HotSpotVMConstant(name = "VM_Version::CPU_CLMUL", archs = {"amd64"}) @Stable public long amd64CLMUL;
    @HotSpotVMConstant(name = "VM_Version::CPU_BMI1", archs = {"amd64"}) @Stable public long amd64BMI1;
    @HotSpotVMConstant(name = "VM_Version::CPU_BMI2", archs = {"amd64"}) @Stable public long amd64BMI2;
    @HotSpotVMConstant(name = "VM_Version::CPU_RTM", archs = {"amd64"}) @Stable public long amd64RTM;
    @HotSpotVMConstant(name = "VM_Version::CPU_ADX", archs = {"amd64"}) @Stable public long amd64ADX;
    @HotSpotVMConstant(name = "VM_Version::CPU_AVX512F", archs = {"amd64"}) @Stable public long amd64AVX512F;
    @HotSpotVMConstant(name = "VM_Version::CPU_AVX512DQ", archs = {"amd64"}) @Stable public long amd64AVX512DQ;
    @HotSpotVMConstant(name = "VM_Version::CPU_AVX512PF", archs = {"amd64"}) @Stable public long amd64AVX512PF;
    @HotSpotVMConstant(name = "VM_Version::CPU_AVX512ER", archs = {"amd64"}) @Stable public long amd64AVX512ER;
    @HotSpotVMConstant(name = "VM_Version::CPU_AVX512CD", archs = {"amd64"}) @Stable public long amd64AVX512CD;
    @HotSpotVMConstant(name = "VM_Version::CPU_AVX512BW", archs = {"amd64"}) @Stable public long amd64AVX512BW;
    @HotSpotVMConstant(name = "VM_Version::CPU_AVX512VL", archs = {"amd64"}) @Stable public long amd64AVX512VL;

    // SPARC specific values
    @HotSpotVMConstant(name = "VM_Version::vis3_instructions_m", archs = {"sparc"}) @Stable public int sparcVis3Instructions;
    @HotSpotVMConstant(name = "VM_Version::vis2_instructions_m", archs = {"sparc"}) @Stable public int sparcVis2Instructions;
    @HotSpotVMConstant(name = "VM_Version::vis1_instructions_m", archs = {"sparc"}) @Stable public int sparcVis1Instructions;
    @HotSpotVMConstant(name = "VM_Version::cbcond_instructions_m", archs = {"sparc"}) @Stable public int sparcCbcondInstructions;
    @HotSpotVMConstant(name = "VM_Version::v8_instructions_m", archs = {"sparc"}) @Stable public int sparcV8Instructions;
    @HotSpotVMConstant(name = "VM_Version::hardware_mul32_m", archs = {"sparc"}) @Stable public int sparcHardwareMul32;
    @HotSpotVMConstant(name = "VM_Version::hardware_div32_m", archs = {"sparc"}) @Stable public int sparcHardwareDiv32;
    @HotSpotVMConstant(name = "VM_Version::hardware_fsmuld_m", archs = {"sparc"}) @Stable public int sparcHardwareFsmuld;
    @HotSpotVMConstant(name = "VM_Version::hardware_popc_m", archs = {"sparc"}) @Stable public int sparcHardwarePopc;
    @HotSpotVMConstant(name = "VM_Version::v9_instructions_m", archs = {"sparc"}) @Stable public int sparcV9Instructions;
    @HotSpotVMConstant(name = "VM_Version::sun4v_m", archs = {"sparc"}) @Stable public int sparcSun4v;
    @HotSpotVMConstant(name = "VM_Version::blk_init_instructions_m", archs = {"sparc"}) @Stable public int sparcBlkInitInstructions;
    @HotSpotVMConstant(name = "VM_Version::fmaf_instructions_m", archs = {"sparc"}) @Stable public int sparcFmafInstructions;
    @HotSpotVMConstant(name = "VM_Version::fmau_instructions_m", archs = {"sparc"}) @Stable public int sparcFmauInstructions;
    @HotSpotVMConstant(name = "VM_Version::sparc64_family_m", archs = {"sparc"}) @Stable public int sparcSparc64Family;
    @HotSpotVMConstant(name = "VM_Version::M_family_m", archs = {"sparc"}) @Stable public int sparcMFamily;
    @HotSpotVMConstant(name = "VM_Version::T_family_m", archs = {"sparc"}) @Stable public int sparcTFamily;
    @HotSpotVMConstant(name = "VM_Version::T1_model_m", archs = {"sparc"}) @Stable public int sparcT1Model;
    @HotSpotVMConstant(name = "VM_Version::sparc5_instructions_m", archs = {"sparc"}) @Stable public int sparcSparc5Instructions;
    @HotSpotVMConstant(name = "VM_Version::aes_instructions_m", archs = {"sparc"}) @Stable public int sparcAesInstructions;
    @HotSpotVMConstant(name = "VM_Version::sha1_instruction_m", archs = {"sparc"}) @Stable public int sparcSha1Instruction;
    @HotSpotVMConstant(name = "VM_Version::sha256_instruction_m", archs = {"sparc"}) @Stable public int sparcSha256Instruction;
    @HotSpotVMConstant(name = "VM_Version::sha512_instruction_m", archs = {"sparc"}) @Stable public int sparcSha512Instruction;

    @HotSpotVMFlag(name = "UseBlockZeroing", archs = {"sparc"}) @Stable public boolean useBlockZeroing;
    @HotSpotVMFlag(name = "BlockZeroingLowLimit", archs = {"sparc"}) @Stable public int blockZeroingLowLimit;

    @HotSpotVMFlag(name = "StackShadowPages") @Stable public int stackShadowPages;
    @HotSpotVMFlag(name = "UseStackBanging") @Stable public boolean useStackBanging;
    @HotSpotVMConstant(name = "STACK_BIAS") @Stable public int stackBias;
    @HotSpotVMField(name = "CompilerToVM::Data::vm_page_size", type = "int", get = HotSpotVMField.Type.VALUE) @Stable public int vmPageSize;

    // offsets, ...
    @HotSpotVMField(name = "oopDesc::_mark", type = "markOop", get = HotSpotVMField.Type.OFFSET) @Stable public int markOffset;
    @HotSpotVMField(name = "oopDesc::_metadata._klass", type = "Klass*", get = HotSpotVMField.Type.OFFSET) @Stable public int hubOffset;

    @HotSpotVMField(name = "Klass::_prototype_header", type = "markOop", get = HotSpotVMField.Type.OFFSET) @Stable public int prototypeMarkWordOffset;
    @HotSpotVMField(name = "Klass::_subklass", type = "Klass*", get = HotSpotVMField.Type.OFFSET) @Stable public int subklassOffset;
    @HotSpotVMField(name = "Klass::_next_sibling", type = "Klass*", get = HotSpotVMField.Type.OFFSET) @Stable public int nextSiblingOffset;
    @HotSpotVMField(name = "Klass::_super_check_offset", type = "juint", get = HotSpotVMField.Type.OFFSET) @Stable public int superCheckOffsetOffset;
    @HotSpotVMField(name = "Klass::_secondary_super_cache", type = "Klass*", get = HotSpotVMField.Type.OFFSET) @Stable public int secondarySuperCacheOffset;
    @HotSpotVMField(name = "Klass::_secondary_supers", type = "Array<Klass*>*", get = HotSpotVMField.Type.OFFSET) @Stable public int secondarySupersOffset;

    /**
     * The offset of the _java_mirror field (of type {@link Class}) in a Klass.
     */
    @HotSpotVMField(name = "Klass::_java_mirror", type = "oop", get = HotSpotVMField.Type.OFFSET) @Stable public int classMirrorOffset;

    @HotSpotVMField(name = "Klass::_super", type = "Klass*", get = HotSpotVMField.Type.OFFSET) @Stable public int klassSuperKlassOffset;
    @HotSpotVMField(name = "Klass::_modifier_flags", type = "jint", get = HotSpotVMField.Type.OFFSET) @Stable public int klassModifierFlagsOffset;
    @HotSpotVMField(name = "Klass::_access_flags", type = "AccessFlags", get = HotSpotVMField.Type.OFFSET) @Stable public int klassAccessFlagsOffset;
    @HotSpotVMField(name = "Klass::_layout_helper", type = "jint", get = HotSpotVMField.Type.OFFSET) @Stable public int klassLayoutHelperOffset;

    @HotSpotVMConstant(name = "Klass::_lh_neutral_value") @Stable public int klassLayoutHelperNeutralValue;
    @HotSpotVMConstant(name = "Klass::_lh_instance_slow_path_bit") @Stable public int klassLayoutHelperInstanceSlowPathBit;
    @HotSpotVMConstant(name = "Klass::_lh_log2_element_size_shift") @Stable public int layoutHelperLog2ElementSizeShift;
    @HotSpotVMConstant(name = "Klass::_lh_log2_element_size_mask") @Stable public int layoutHelperLog2ElementSizeMask;
    @HotSpotVMConstant(name = "Klass::_lh_element_type_shift") @Stable public int layoutHelperElementTypeShift;
    @HotSpotVMConstant(name = "Klass::_lh_element_type_mask") @Stable public int layoutHelperElementTypeMask;
    @HotSpotVMConstant(name = "Klass::_lh_header_size_shift") @Stable public int layoutHelperHeaderSizeShift;
    @HotSpotVMConstant(name = "Klass::_lh_header_size_mask") @Stable public int layoutHelperHeaderSizeMask;
    @HotSpotVMConstant(name = "Klass::_lh_array_tag_shift") @Stable public int layoutHelperArrayTagShift;
    @HotSpotVMConstant(name = "Klass::_lh_array_tag_type_value") @Stable public int layoutHelperArrayTagTypeValue;
    @HotSpotVMConstant(name = "Klass::_lh_array_tag_obj_value") @Stable public int layoutHelperArrayTagObjectValue;

    /**
     * This filters out the bit that differentiates a type array from an object array.
     */
    public int layoutHelperElementTypePrimitiveInPlace() {
        return (layoutHelperArrayTagTypeValue & ~layoutHelperArrayTagObjectValue) << layoutHelperArrayTagShift;
    }

    /**
     * Bit pattern in the klass layout helper that can be used to identify arrays.
     */
    public final int arrayKlassLayoutHelperIdentifier = 0x80000000;

    @HotSpotVMType(name = "vtableEntry", get = HotSpotVMType.Type.SIZE) @Stable public int vtableEntrySize;
    @HotSpotVMField(name = "vtableEntry::_method", type = "Method*", get = HotSpotVMField.Type.OFFSET) @Stable public int vtableEntryMethodOffset;

    @HotSpotVMType(name = "InstanceKlass", get = HotSpotVMType.Type.SIZE) @Stable public int instanceKlassSize;
    @HotSpotVMField(name = "InstanceKlass::_source_file_name_index", type = "u2", get = HotSpotVMField.Type.OFFSET) @Stable public int instanceKlassSourceFileNameIndexOffset;
    @HotSpotVMField(name = "InstanceKlass::_init_state", type = "u1", get = HotSpotVMField.Type.OFFSET) @Stable public int instanceKlassInitStateOffset;
    @HotSpotVMField(name = "InstanceKlass::_constants", type = "ConstantPool*", get = HotSpotVMField.Type.OFFSET) @Stable public int instanceKlassConstantsOffset;
    @HotSpotVMField(name = "InstanceKlass::_fields", type = "Array<u2>*", get = HotSpotVMField.Type.OFFSET) @Stable public int instanceKlassFieldsOffset;
    @HotSpotVMField(name = "CompilerToVM::Data::InstanceKlass_vtable_start_offset", type = "int", get = HotSpotVMField.Type.VALUE) @Stable public int instanceKlassVtableStartOffset;
    @HotSpotVMField(name = "CompilerToVM::Data::InstanceKlass_vtable_length_offset", type = "int", get = HotSpotVMField.Type.VALUE) @Stable public int instanceKlassVtableLengthOffset;

    @HotSpotVMConstant(name = "InstanceKlass::linked") @Stable public int instanceKlassStateLinked;
    @HotSpotVMConstant(name = "InstanceKlass::fully_initialized") @Stable public int instanceKlassStateFullyInitialized;

    /**
     * See {@code InstanceKlass::vtable_start_offset()}.
     */
    public final int instanceKlassVtableStartOffset() {
        return instanceKlassVtableStartOffset * heapWordSize;
    }

    @HotSpotVMType(name = "arrayOopDesc", get = HotSpotVMType.Type.SIZE) @Stable public int arrayOopDescSize;

    /**
     * The offset of the array length word in an array object's header.
     *
     * See {@code arrayOopDesc::length_offset_in_bytes()}.
     */
    public final int arrayOopDescLengthOffset() {
        return useCompressedClassPointers ? hubOffset + narrowKlassSize : arrayOopDescSize;
    }

    @HotSpotVMField(name = "Array<int>::_length", type = "int", get = HotSpotVMField.Type.OFFSET) @Stable public int arrayU1LengthOffset;
    @HotSpotVMField(name = "Array<u1>::_data", type = "", get = HotSpotVMField.Type.OFFSET) @Stable public int arrayU1DataOffset;
    @HotSpotVMField(name = "Array<u2>::_data", type = "", get = HotSpotVMField.Type.OFFSET) @Stable public int arrayU2DataOffset;
    @HotSpotVMField(name = "Array<Klass*>::_length", type = "int", get = HotSpotVMField.Type.OFFSET) @Stable public int metaspaceArrayLengthOffset;
    @HotSpotVMField(name = "Array<Klass*>::_data[0]", type = "Klass*", get = HotSpotVMField.Type.OFFSET) @Stable public int metaspaceArrayBaseOffset;

    @HotSpotVMField(name = "ObjArrayKlass::_element_klass", type = "Klass*", get = HotSpotVMField.Type.OFFSET) @Stable public int arrayClassElementOffset;

    @HotSpotVMConstant(name = "FieldInfo::access_flags_offset") @Stable public int fieldInfoAccessFlagsOffset;
    @HotSpotVMConstant(name = "FieldInfo::name_index_offset") @Stable public int fieldInfoNameIndexOffset;
    @HotSpotVMConstant(name = "FieldInfo::signature_index_offset") @Stable public int fieldInfoSignatureIndexOffset;
    @HotSpotVMConstant(name = "FieldInfo::initval_index_offset") @Stable public int fieldInfoInitvalIndexOffset;
    @HotSpotVMConstant(name = "FieldInfo::low_packed_offset") @Stable public int fieldInfoLowPackedOffset;
    @HotSpotVMConstant(name = "FieldInfo::high_packed_offset") @Stable public int fieldInfoHighPackedOffset;
    @HotSpotVMConstant(name = "FieldInfo::field_slots") @Stable public int fieldInfoFieldSlots;

    @HotSpotVMConstant(name = "FIELDINFO_TAG_SIZE") @Stable public int fieldInfoTagSize;

    @HotSpotVMConstant(name = "JVM_ACC_MONITOR_MATCH") @Stable public int jvmAccMonitorMatch;
    @HotSpotVMConstant(name = "JVM_ACC_HAS_MONITOR_BYTECODES") @Stable public int jvmAccHasMonitorBytecodes;
    @HotSpotVMConstant(name = "JVM_ACC_HAS_FINALIZER") @Stable public int jvmAccHasFinalizer;
    @HotSpotVMConstant(name = "JVM_ACC_FIELD_INTERNAL") @Stable public int jvmAccFieldInternal;
    @HotSpotVMConstant(name = "JVM_ACC_FIELD_STABLE") @Stable public int jvmAccFieldStable;
    @HotSpotVMConstant(name = "JVM_ACC_FIELD_HAS_GENERIC_SIGNATURE") @Stable public int jvmAccFieldHasGenericSignature;
    @HotSpotVMConstant(name = "JVM_ACC_WRITTEN_FLAGS") @Stable public int jvmAccWrittenFlags;

    // Modifier.SYNTHETIC is not public so we get it via vmStructs.
    @HotSpotVMConstant(name = "JVM_ACC_SYNTHETIC") @Stable public int jvmAccSynthetic;

    /**
     * @see HotSpotResolvedObjectTypeImpl#createField
     */
    @HotSpotVMConstant(name = "JVM_RECOGNIZED_FIELD_MODIFIERS") @Stable public int recognizedFieldModifiers;

    @HotSpotVMField(name = "Thread::_tlab", type = "ThreadLocalAllocBuffer", get = HotSpotVMField.Type.OFFSET) @Stable public int threadTlabOffset;

    @HotSpotVMField(name = "JavaThread::_anchor", type = "JavaFrameAnchor", get = HotSpotVMField.Type.OFFSET) @Stable public int javaThreadAnchorOffset;
    @HotSpotVMField(name = "JavaThread::_threadObj", type = "oop", get = HotSpotVMField.Type.OFFSET) @Stable public int threadObjectOffset;
    @HotSpotVMField(name = "JavaThread::_osthread", type = "OSThread*", get = HotSpotVMField.Type.OFFSET) @Stable public int osThreadOffset;
    @HotSpotVMField(name = "JavaThread::_dirty_card_queue", type = "DirtyCardQueue", get = HotSpotVMField.Type.OFFSET) @Stable public int javaThreadDirtyCardQueueOffset;
    @HotSpotVMField(name = "JavaThread::_is_method_handle_return", type = "int", get = HotSpotVMField.Type.OFFSET) @Stable public int threadIsMethodHandleReturnOffset;
    @HotSpotVMField(name = "JavaThread::_satb_mark_queue", type = "SATBMarkQueue", get = HotSpotVMField.Type.OFFSET) @Stable public int javaThreadSatbMarkQueueOffset;
    @HotSpotVMField(name = "JavaThread::_vm_result", type = "oop", get = HotSpotVMField.Type.OFFSET) @Stable public int threadObjectResultOffset;
    @HotSpotVMField(name = "JavaThread::_jvmci_counters", type = "jlong*", get = HotSpotVMField.Type.OFFSET) @Stable public int jvmciCountersThreadOffset;

    /**
     * An invalid value for {@link #rtldDefault}.
     */
    public static final long INVALID_RTLD_DEFAULT_HANDLE = 0xDEADFACE;

    /**
     * Address of the library lookup routine. The C signature of this routine is:
     *
     * <pre>
     *     void* (const char *filename, char *ebuf, int ebuflen)
     * </pre>
     */
    @HotSpotVMAddress(name = "os::dll_load") @Stable public long dllLoad;

    /**
     * Address of the library lookup routine. The C signature of this routine is:
     *
     * <pre>
     *     void* (void* handle, const char* name)
     * </pre>
     */
    @HotSpotVMAddress(name = "os::dll_lookup") @Stable public long dllLookup;

    /**
     * A pseudo-handle which when used as the first argument to {@link #dllLookup} means lookup will
     * return the first occurrence of the desired symbol using the default library search order. If
     * this field is {@value #INVALID_RTLD_DEFAULT_HANDLE}, then this capability is not supported on
     * the current platform.
     */
    @HotSpotVMAddress(name = "RTLD_DEFAULT", os = {"bsd", "linux"}) @Stable public long rtldDefault = INVALID_RTLD_DEFAULT_HANDLE;

    /**
     * This field is used to pass exception objects into and out of the runtime system during
     * exception handling for compiled code.
     */
    @HotSpotVMField(name = "JavaThread::_exception_oop", type = "oop", get = HotSpotVMField.Type.OFFSET) @Stable public int threadExceptionOopOffset;
    @HotSpotVMField(name = "JavaThread::_exception_pc", type = "address", get = HotSpotVMField.Type.OFFSET) @Stable public int threadExceptionPcOffset;
    @HotSpotVMField(name = "ThreadShadow::_pending_exception", type = "oop", get = HotSpotVMField.Type.OFFSET) @Stable public int pendingExceptionOffset;

    @HotSpotVMField(name = "JavaThread::_pending_deoptimization", type = "int", get = HotSpotVMField.Type.OFFSET) @Stable public int pendingDeoptimizationOffset;
    @HotSpotVMField(name = "JavaThread::_pending_failed_speculation", type = "oop", get = HotSpotVMField.Type.OFFSET) @Stable public int pendingFailedSpeculationOffset;
    @HotSpotVMField(name = "JavaThread::_pending_transfer_to_interpreter", type = "bool", get = HotSpotVMField.Type.OFFSET) @Stable public int pendingTransferToInterpreterOffset;

    @HotSpotVMField(name = "JavaFrameAnchor::_last_Java_sp", type = "intptr_t*", get = HotSpotVMField.Type.OFFSET) @Stable private int javaFrameAnchorLastJavaSpOffset;
    @HotSpotVMField(name = "JavaFrameAnchor::_last_Java_pc", type = "address", get = HotSpotVMField.Type.OFFSET) @Stable private int javaFrameAnchorLastJavaPcOffset;
    @HotSpotVMField(name = "JavaFrameAnchor::_last_Java_fp", type = "intptr_t*", get = HotSpotVMField.Type.OFFSET, archs = {"amd64"}) @Stable private int javaFrameAnchorLastJavaFpOffset;
    @HotSpotVMField(name = "JavaFrameAnchor::_flags", type = "int", get = HotSpotVMField.Type.OFFSET, archs = {"sparc"}) @Stable private int javaFrameAnchorFlagsOffset;

    public int threadLastJavaSpOffset() {
        return javaThreadAnchorOffset + javaFrameAnchorLastJavaSpOffset;
    }

    public int threadLastJavaPcOffset() {
        return javaThreadAnchorOffset + javaFrameAnchorLastJavaPcOffset;
    }

    /**
     * This value is only valid on AMD64.
     */
    public int threadLastJavaFpOffset() {
        // TODO add an assert for AMD64
        return javaThreadAnchorOffset + javaFrameAnchorLastJavaFpOffset;
    }

    /**
     * This value is only valid on SPARC.
     */
    public int threadJavaFrameAnchorFlagsOffset() {
        // TODO add an assert for SPARC
        return javaThreadAnchorOffset + javaFrameAnchorFlagsOffset;
    }

    // These are only valid on AMD64.
    @HotSpotVMConstant(name = "frame::arg_reg_save_area_bytes", archs = {"amd64"}) @Stable public int runtimeCallStackSize;
    @HotSpotVMConstant(name = "frame::interpreter_frame_sender_sp_offset", archs = {"amd64"}) @Stable public int frameInterpreterFrameSenderSpOffset;
    @HotSpotVMConstant(name = "frame::interpreter_frame_last_sp_offset", archs = {"amd64"}) @Stable public int frameInterpreterFrameLastSpOffset;

    @HotSpotVMConstant(name = "dirtyCardQueueBufferOffset") @Stable private int dirtyCardQueueBufferOffset;
    @HotSpotVMConstant(name = "dirtyCardQueueIndexOffset") @Stable private int dirtyCardQueueIndexOffset;

    @HotSpotVMConstant(name = "satbMarkQueueBufferOffset") @Stable private int satbMarkQueueBufferOffset;
    @HotSpotVMConstant(name = "satbMarkQueueIndexOffset") @Stable private int satbMarkQueueIndexOffset;
    @HotSpotVMConstant(name = "satbMarkQueueActiveOffset") @Stable private int satbMarkQueueActiveOffset;

    @HotSpotVMField(name = "OSThread::_interrupted", type = "jint", get = HotSpotVMField.Type.OFFSET) @Stable public int osThreadInterruptedOffset;

    @HotSpotVMConstant(name = "markOopDesc::hash_shift") @Stable public long markOopDescHashShift;

    @HotSpotVMConstant(name = "markOopDesc::biased_lock_mask_in_place") @Stable public int biasedLockMaskInPlace;
    @HotSpotVMConstant(name = "markOopDesc::age_mask_in_place") @Stable public int ageMaskInPlace;
    @HotSpotVMConstant(name = "markOopDesc::epoch_mask_in_place") @Stable public int epochMaskInPlace;
    @HotSpotVMConstant(name = "markOopDesc::hash_mask") @Stable public long markOopDescHashMask;
    @HotSpotVMConstant(name = "markOopDesc::hash_mask_in_place") @Stable public long markOopDescHashMaskInPlace;

    @HotSpotVMConstant(name = "markOopDesc::unlocked_value") @Stable public int unlockedMask;
    @HotSpotVMConstant(name = "markOopDesc::biased_lock_pattern") @Stable public int biasedLockPattern;

    @HotSpotVMConstant(name = "markOopDesc::no_hash_in_place") @Stable public int markWordNoHashInPlace;
    @HotSpotVMConstant(name = "markOopDesc::no_lock_in_place") @Stable public int markWordNoLockInPlace;

    /**
     * See {@code markOopDesc::prototype()}.
     */
    public long arrayPrototypeMarkWord() {
        return markWordNoHashInPlace | markWordNoLockInPlace;
    }

    /**
     * See {@code markOopDesc::copy_set_hash()}.
     */
    public long tlabIntArrayMarkWord() {
        long tmp = arrayPrototypeMarkWord() & (~markOopDescHashMaskInPlace);
        tmp |= ((0x2 & markOopDescHashMask) << markOopDescHashShift);
        return tmp;
    }

    /**
     * Mark word right shift to get identity hash code.
     */
    @HotSpotVMConstant(name = "markOopDesc::hash_shift") @Stable public int identityHashCodeShift;

    /**
     * Identity hash code value when uninitialized.
     */
    @HotSpotVMConstant(name = "markOopDesc::no_hash") @Stable public int uninitializedIdentityHashCodeValue;

    @HotSpotVMField(name = "Method::_access_flags", type = "AccessFlags", get = HotSpotVMField.Type.OFFSET) @Stable public int methodAccessFlagsOffset;
    @HotSpotVMField(name = "Method::_constMethod", type = "ConstMethod*", get = HotSpotVMField.Type.OFFSET) @Stable public int methodConstMethodOffset;
    @HotSpotVMField(name = "Method::_intrinsic_id", type = "u2", get = HotSpotVMField.Type.OFFSET) @Stable public int methodIntrinsicIdOffset;
    @HotSpotVMField(name = "Method::_flags", type = "u2", get = HotSpotVMField.Type.OFFSET) @Stable public int methodFlagsOffset;
    @HotSpotVMField(name = "Method::_vtable_index", type = "int", get = HotSpotVMField.Type.OFFSET) @Stable public int methodVtableIndexOffset;

    @HotSpotVMField(name = "Method::_method_counters", type = "MethodCounters*", get = HotSpotVMField.Type.OFFSET) @Stable public int methodCountersOffset;
    @HotSpotVMField(name = "Method::_method_data", type = "MethodData*", get = HotSpotVMField.Type.OFFSET) @Stable public int methodDataOffset;
    @HotSpotVMField(name = "Method::_from_compiled_entry", type = "address", get = HotSpotVMField.Type.OFFSET) @Stable public int methodCompiledEntryOffset;
    @HotSpotVMField(name = "Method::_code", type = "nmethod*", get = HotSpotVMField.Type.OFFSET) @Stable public int methodCodeOffset;

    @HotSpotVMConstant(name = "Method::_jfr_towrite") @Stable public int methodFlagsJfrTowrite;
    @HotSpotVMConstant(name = "Method::_caller_sensitive") @Stable public int methodFlagsCallerSensitive;
    @HotSpotVMConstant(name = "Method::_force_inline") @Stable public int methodFlagsForceInline;
    @HotSpotVMConstant(name = "Method::_dont_inline") @Stable public int methodFlagsDontInline;
    @HotSpotVMConstant(name = "Method::_hidden") @Stable public int methodFlagsHidden;
    @HotSpotVMConstant(name = "Method::nonvirtual_vtable_index") @Stable public int nonvirtualVtableIndex;
    @HotSpotVMConstant(name = "Method::invalid_vtable_index") @Stable public int invalidVtableIndex;

    @HotSpotVMField(name = "MethodCounters::_invocation_counter", type = "InvocationCounter", get = HotSpotVMField.Type.OFFSET) @Stable public int invocationCounterOffset;
    @HotSpotVMField(name = "MethodCounters::_backedge_counter", type = "InvocationCounter", get = HotSpotVMField.Type.OFFSET) @Stable public int backedgeCounterOffset;
    @HotSpotVMConstant(name = "InvocationCounter::count_increment") @Stable public int invocationCounterIncrement;
    @HotSpotVMConstant(name = "InvocationCounter::count_shift") @Stable public int invocationCounterShift;

    @HotSpotVMField(name = "MethodData::_size", type = "int", get = HotSpotVMField.Type.OFFSET) @Stable public int methodDataSize;
    @HotSpotVMField(name = "MethodData::_data_size", type = "int", get = HotSpotVMField.Type.OFFSET) @Stable public int methodDataDataSize;
    @HotSpotVMField(name = "MethodData::_data[0]", type = "intptr_t", get = HotSpotVMField.Type.OFFSET) @Stable public int methodDataOopDataOffset;
    @HotSpotVMField(name = "MethodData::_trap_hist._array[0]", type = "u1", get = HotSpotVMField.Type.OFFSET) @Stable public int methodDataOopTrapHistoryOffset;
    @HotSpotVMField(name = "MethodData::_jvmci_ir_size", type = "int", get = HotSpotVMField.Type.OFFSET) @Stable public int methodDataIRSizeOffset;

    @HotSpotVMField(name = "nmethod::_verified_entry_point", type = "address", get = HotSpotVMField.Type.OFFSET) @Stable public int nmethodEntryOffset;
    @HotSpotVMField(name = "nmethod::_comp_level", type = "int", get = HotSpotVMField.Type.OFFSET) @Stable public int nmethodCompLevelOffset;

    @HotSpotVMConstant(name = "CompLevel_full_optimization") @Stable public int compilationLevelFullOptimization;

    @HotSpotVMConstant(name = "InvocationEntryBci") @Stable public int invocationEntryBci;

    @HotSpotVMField(name = "JVMCIEnv::_task", type = "CompileTask*", get = HotSpotVMField.Type.OFFSET) @Stable public int jvmciEnvTaskOffset;
    @HotSpotVMField(name = "JVMCIEnv::_jvmti_can_hotswap_or_post_breakpoint", type = "bool", get = HotSpotVMField.Type.OFFSET) @Stable public int jvmciEnvJvmtiCanHotswapOrPostBreakpointOffset;
    @HotSpotVMField(name = "CompileTask::_num_inlined_bytecodes", type = "int", get = HotSpotVMField.Type.OFFSET) @Stable public int compileTaskNumInlinedBytecodesOffset;

    @HotSpotVMField(name = "CompilerToVM::Data::Method_extra_stack_entries", type = "int", get = HotSpotVMField.Type.VALUE) @Stable public int extraStackEntries;

    @HotSpotVMField(name = "ConstMethod::_constants", type = "ConstantPool*", get = HotSpotVMField.Type.OFFSET) @Stable public int constMethodConstantsOffset;
    @HotSpotVMField(name = "ConstMethod::_flags", type = "u2", get = HotSpotVMField.Type.OFFSET) @Stable public int constMethodFlagsOffset;
    @HotSpotVMField(name = "ConstMethod::_code_size", type = "u2", get = HotSpotVMField.Type.OFFSET) @Stable public int constMethodCodeSizeOffset;
    @HotSpotVMField(name = "ConstMethod::_name_index", type = "u2", get = HotSpotVMField.Type.OFFSET) @Stable public int constMethodNameIndexOffset;
    @HotSpotVMField(name = "ConstMethod::_signature_index", type = "u2", get = HotSpotVMField.Type.OFFSET) @Stable public int constMethodSignatureIndexOffset;
    @HotSpotVMField(name = "ConstMethod::_max_stack", type = "u2", get = HotSpotVMField.Type.OFFSET) @Stable public int constMethodMaxStackOffset;
    @HotSpotVMField(name = "ConstMethod::_max_locals", type = "u2", get = HotSpotVMField.Type.OFFSET) @Stable public int methodMaxLocalsOffset;

    @HotSpotVMConstant(name = "ConstMethod::_has_linenumber_table") @Stable public int constMethodHasLineNumberTable;
    @HotSpotVMConstant(name = "ConstMethod::_has_localvariable_table") @Stable public int constMethodHasLocalVariableTable;
    @HotSpotVMConstant(name = "ConstMethod::_has_exception_table") @Stable public int constMethodHasExceptionTable;

    @HotSpotVMType(name = "ExceptionTableElement", get = HotSpotVMType.Type.SIZE) @Stable public int exceptionTableElementSize;
    @HotSpotVMField(name = "ExceptionTableElement::start_pc", type = "u2", get = HotSpotVMField.Type.OFFSET) @Stable public int exceptionTableElementStartPcOffset;
    @HotSpotVMField(name = "ExceptionTableElement::end_pc", type = "u2", get = HotSpotVMField.Type.OFFSET) @Stable public int exceptionTableElementEndPcOffset;
    @HotSpotVMField(name = "ExceptionTableElement::handler_pc", type = "u2", get = HotSpotVMField.Type.OFFSET) @Stable public int exceptionTableElementHandlerPcOffset;
    @HotSpotVMField(name = "ExceptionTableElement::catch_type_index", type = "u2", get = HotSpotVMField.Type.OFFSET) @Stable public int exceptionTableElementCatchTypeIndexOffset;

    @HotSpotVMType(name = "LocalVariableTableElement", get = HotSpotVMType.Type.SIZE) @Stable public int localVariableTableElementSize;
    @HotSpotVMField(name = "LocalVariableTableElement::start_bci", type = "u2", get = HotSpotVMField.Type.OFFSET) @Stable public int localVariableTableElementStartBciOffset;
    @HotSpotVMField(name = "LocalVariableTableElement::length", type = "u2", get = HotSpotVMField.Type.OFFSET) @Stable public int localVariableTableElementLengthOffset;
    @HotSpotVMField(name = "LocalVariableTableElement::name_cp_index", type = "u2", get = HotSpotVMField.Type.OFFSET) @Stable public int localVariableTableElementNameCpIndexOffset;
    @HotSpotVMField(name = "LocalVariableTableElement::descriptor_cp_index", type = "u2", get = HotSpotVMField.Type.OFFSET) @Stable public int localVariableTableElementDescriptorCpIndexOffset;
    @HotSpotVMField(name = "LocalVariableTableElement::signature_cp_index", type = "u2", get = HotSpotVMField.Type.OFFSET) @Stable public int localVariableTableElementSignatureCpIndexOffset;
    @HotSpotVMField(name = "LocalVariableTableElement::slot", type = "u2", get = HotSpotVMField.Type.OFFSET) @Stable public int localVariableTableElementSlotOffset;

    @HotSpotVMType(name = "ConstantPool", get = HotSpotVMType.Type.SIZE) @Stable public int constantPoolSize;
    @HotSpotVMField(name = "ConstantPool::_tags", type = "Array<u1>*", get = HotSpotVMField.Type.OFFSET) @Stable public int constantPoolTagsOffset;
    @HotSpotVMField(name = "ConstantPool::_pool_holder", type = "InstanceKlass*", get = HotSpotVMField.Type.OFFSET) @Stable public int constantPoolHolderOffset;
    @HotSpotVMField(name = "ConstantPool::_length", type = "int", get = HotSpotVMField.Type.OFFSET) @Stable public int constantPoolLengthOffset;

    @HotSpotVMConstant(name = "ConstantPool::CPCACHE_INDEX_TAG") @Stable public int constantPoolCpCacheIndexTag;

    @HotSpotVMConstant(name = "JVM_CONSTANT_Utf8") @Stable public int jvmConstantUtf8;
    @HotSpotVMConstant(name = "JVM_CONSTANT_Integer") @Stable public int jvmConstantInteger;
    @HotSpotVMConstant(name = "JVM_CONSTANT_Long") @Stable public int jvmConstantLong;
    @HotSpotVMConstant(name = "JVM_CONSTANT_Float") @Stable public int jvmConstantFloat;
    @HotSpotVMConstant(name = "JVM_CONSTANT_Double") @Stable public int jvmConstantDouble;
    @HotSpotVMConstant(name = "JVM_CONSTANT_Class") @Stable public int jvmConstantClass;
    @HotSpotVMConstant(name = "JVM_CONSTANT_UnresolvedClass") @Stable public int jvmConstantUnresolvedClass;
    @HotSpotVMConstant(name = "JVM_CONSTANT_UnresolvedClassInError") @Stable public int jvmConstantUnresolvedClassInError;
    @HotSpotVMConstant(name = "JVM_CONSTANT_String") @Stable public int jvmConstantString;
    @HotSpotVMConstant(name = "JVM_CONSTANT_Fieldref") @Stable public int jvmConstantFieldref;
    @HotSpotVMConstant(name = "JVM_CONSTANT_Methodref") @Stable public int jvmConstantMethodref;
    @HotSpotVMConstant(name = "JVM_CONSTANT_InterfaceMethodref") @Stable public int jvmConstantInterfaceMethodref;
    @HotSpotVMConstant(name = "JVM_CONSTANT_NameAndType") @Stable public int jvmConstantNameAndType;
    @HotSpotVMConstant(name = "JVM_CONSTANT_MethodHandle") @Stable public int jvmConstantMethodHandle;
    @HotSpotVMConstant(name = "JVM_CONSTANT_MethodHandleInError") @Stable public int jvmConstantMethodHandleInError;
    @HotSpotVMConstant(name = "JVM_CONSTANT_MethodType") @Stable public int jvmConstantMethodType;
    @HotSpotVMConstant(name = "JVM_CONSTANT_MethodTypeInError") @Stable public int jvmConstantMethodTypeInError;
    @HotSpotVMConstant(name = "JVM_CONSTANT_InvokeDynamic") @Stable public int jvmConstantInvokeDynamic;

    @HotSpotVMConstant(name = "JVM_CONSTANT_ExternalMax") @Stable public int jvmConstantExternalMax;
    @HotSpotVMConstant(name = "JVM_CONSTANT_InternalMin") @Stable public int jvmConstantInternalMin;
    @HotSpotVMConstant(name = "JVM_CONSTANT_InternalMax") @Stable public int jvmConstantInternalMax;

    @HotSpotVMConstant(name = "HeapWordSize") @Stable public int heapWordSize;

    @HotSpotVMType(name = "Symbol*", get = HotSpotVMType.Type.SIZE) @Stable public int symbolPointerSize;

    @HotSpotVMField(name = "vmSymbols::_symbols[0]", type = "Symbol*", get = HotSpotVMField.Type.ADDRESS) @Stable public long vmSymbolsSymbols;
    @HotSpotVMConstant(name = "vmSymbols::FIRST_SID") @Stable public int vmSymbolsFirstSID;
    @HotSpotVMConstant(name = "vmSymbols::SID_LIMIT") @Stable public int vmSymbolsSIDLimit;

    /**
     * Bit pattern that represents a non-oop. Neither the high bits nor the low bits of this value
     * are allowed to look like (respectively) the high or low bits of a real oop.
     */
    @HotSpotVMField(name = "CompilerToVM::Data::Universe_non_oop_bits", type = "void*", get = HotSpotVMField.Type.VALUE) @Stable public long nonOopBits;

    @HotSpotVMField(name = "StubRoutines::_verify_oop_count", type = "jint", get = HotSpotVMField.Type.ADDRESS) @Stable public long verifyOopCounterAddress;
    @HotSpotVMField(name = "CompilerToVM::Data::Universe_verify_oop_mask", type = "uintptr_t", get = HotSpotVMField.Type.VALUE) @Stable public long verifyOopMask;
    @HotSpotVMField(name = "CompilerToVM::Data::Universe_verify_oop_bits", type = "uintptr_t", get = HotSpotVMField.Type.VALUE) @Stable public long verifyOopBits;
    @HotSpotVMField(name = "CompilerToVM::Data::Universe_base_vtable_size", type = "int", get = HotSpotVMField.Type.VALUE) @Stable public int universeBaseVtableSize;

    public final int baseVtableLength() {
        return universeBaseVtableSize / vtableEntrySize;
    }

    @HotSpotVMField(name = "HeapRegion::LogOfHRGrainBytes", type = "int", get = HotSpotVMField.Type.VALUE) @Stable public int logOfHRGrainBytes;

    @HotSpotVMConstant(name = "CardTableModRefBS::dirty_card") @Stable public byte dirtyCardValue;
    @HotSpotVMConstant(name = "G1SATBCardTableModRefBS::g1_young_gen") @Stable public byte g1YoungCardValue;

    @HotSpotVMField(name = "CompilerToVM::Data::cardtable_start_address", type = "jbyte*", get = HotSpotVMField.Type.VALUE) @Stable private long cardtableStartAddress;
    @HotSpotVMField(name = "CompilerToVM::Data::cardtable_shift", type = "int", get = HotSpotVMField.Type.VALUE) @Stable private int cardtableShift;

    public long cardtableStartAddress() {
        return cardtableStartAddress;
    }

    public int cardtableShift() {
        return cardtableShift;
    }

    @HotSpotVMField(name = "os::_polling_page", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long safepointPollingAddress;

    // G1 Collector Related Values.

    public int g1CardQueueIndexOffset() {
        return javaThreadDirtyCardQueueOffset + dirtyCardQueueIndexOffset;
    }

    public int g1CardQueueBufferOffset() {
        return javaThreadDirtyCardQueueOffset + dirtyCardQueueBufferOffset;
    }

    public int g1SATBQueueMarkingOffset() {
        return javaThreadSatbMarkQueueOffset + satbMarkQueueActiveOffset;
    }

    public int g1SATBQueueIndexOffset() {
        return javaThreadSatbMarkQueueOffset + satbMarkQueueIndexOffset;
    }

    public int g1SATBQueueBufferOffset() {
        return javaThreadSatbMarkQueueOffset + satbMarkQueueBufferOffset;
    }

    @HotSpotVMField(name = "java_lang_Class::_klass_offset", type = "int", get = HotSpotVMField.Type.VALUE) @Stable public int klassOffset;
    @HotSpotVMField(name = "java_lang_Class::_array_klass_offset", type = "int", get = HotSpotVMField.Type.VALUE) @Stable public int arrayKlassOffset;

    @HotSpotVMType(name = "BasicLock", get = HotSpotVMType.Type.SIZE) @Stable public int basicLockSize;
    @HotSpotVMField(name = "BasicLock::_displaced_header", type = "markOop", get = HotSpotVMField.Type.OFFSET) @Stable public int basicLockDisplacedHeaderOffset;

    @HotSpotVMField(name = "Thread::_allocated_bytes", type = "jlong", get = HotSpotVMField.Type.OFFSET) @Stable public int threadAllocatedBytesOffset;

    @HotSpotVMFlag(name = "TLABWasteIncrement") @Stable public int tlabRefillWasteIncrement;

    @HotSpotVMField(name = "ThreadLocalAllocBuffer::_start", type = "HeapWord*", get = HotSpotVMField.Type.OFFSET) @Stable private int threadLocalAllocBufferStartOffset;
    @HotSpotVMField(name = "ThreadLocalAllocBuffer::_end", type = "HeapWord*", get = HotSpotVMField.Type.OFFSET) @Stable private int threadLocalAllocBufferEndOffset;
    @HotSpotVMField(name = "ThreadLocalAllocBuffer::_top", type = "HeapWord*", get = HotSpotVMField.Type.OFFSET) @Stable private int threadLocalAllocBufferTopOffset;
    @HotSpotVMField(name = "ThreadLocalAllocBuffer::_pf_top", type = "HeapWord*", get = HotSpotVMField.Type.OFFSET) @Stable private int threadLocalAllocBufferPfTopOffset;
    @HotSpotVMField(name = "ThreadLocalAllocBuffer::_slow_allocations", type = "unsigned", get = HotSpotVMField.Type.OFFSET) @Stable private int threadLocalAllocBufferSlowAllocationsOffset;
    @HotSpotVMField(name = "ThreadLocalAllocBuffer::_fast_refill_waste", type = "unsigned", get = HotSpotVMField.Type.OFFSET) @Stable private int threadLocalAllocBufferFastRefillWasteOffset;
    @HotSpotVMField(name = "ThreadLocalAllocBuffer::_number_of_refills", type = "unsigned", get = HotSpotVMField.Type.OFFSET) @Stable private int threadLocalAllocBufferNumberOfRefillsOffset;
    @HotSpotVMField(name = "ThreadLocalAllocBuffer::_refill_waste_limit", type = "size_t", get = HotSpotVMField.Type.OFFSET) @Stable private int threadLocalAllocBufferRefillWasteLimitOffset;
    @HotSpotVMField(name = "ThreadLocalAllocBuffer::_desired_size", type = "size_t", get = HotSpotVMField.Type.OFFSET) @Stable private int threadLocalAllocBufferDesiredSizeOffset;

    public int tlabSlowAllocationsOffset() {
        return threadTlabOffset + threadLocalAllocBufferSlowAllocationsOffset;
    }

    public int tlabFastRefillWasteOffset() {
        return threadTlabOffset + threadLocalAllocBufferFastRefillWasteOffset;
    }

    public int tlabNumberOfRefillsOffset() {
        return threadTlabOffset + threadLocalAllocBufferNumberOfRefillsOffset;
    }

    public int tlabRefillWasteLimitOffset() {
        return threadTlabOffset + threadLocalAllocBufferRefillWasteLimitOffset;
    }

    public int threadTlabSizeOffset() {
        return threadTlabOffset + threadLocalAllocBufferDesiredSizeOffset;
    }

    public int threadTlabStartOffset() {
        return threadTlabOffset + threadLocalAllocBufferStartOffset;
    }

    public int threadTlabEndOffset() {
        return threadTlabOffset + threadLocalAllocBufferEndOffset;
    }

    public int threadTlabTopOffset() {
        return threadTlabOffset + threadLocalAllocBufferTopOffset;
    }

    public int threadTlabPfTopOffset() {
        return threadTlabOffset + threadLocalAllocBufferPfTopOffset;
    }

    @HotSpotVMField(name = "CompilerToVM::Data::ThreadLocalAllocBuffer_alignment_reserve", type = "size_t", get = HotSpotVMField.Type.VALUE) @Stable public int tlabAlignmentReserve;

    @HotSpotVMFlag(name = "TLABStats") @Stable public boolean tlabStats;

    // FIXME This is only temporary until the GC code is changed.
    @HotSpotVMField(name = "CompilerToVM::Data::_supports_inline_contig_alloc", type = "bool", get = HotSpotVMField.Type.VALUE) @Stable public boolean inlineContiguousAllocationSupported;
    @HotSpotVMField(name = "CompilerToVM::Data::_heap_end_addr", type = "HeapWord**", get = HotSpotVMField.Type.VALUE) @Stable public long heapEndAddress;
    @HotSpotVMField(name = "CompilerToVM::Data::_heap_top_addr", type = "HeapWord**", get = HotSpotVMField.Type.VALUE) @Stable public long heapTopAddress;

    /**
     * The DataLayout header size is the same as the cell size.
     */
    @HotSpotVMConstant(name = "DataLayout::cell_size") @Stable public int dataLayoutHeaderSize;
    @HotSpotVMField(name = "DataLayout::_header._struct._tag", type = "u1", get = HotSpotVMField.Type.OFFSET) @Stable public int dataLayoutTagOffset;
    @HotSpotVMField(name = "DataLayout::_header._struct._flags", type = "u1", get = HotSpotVMField.Type.OFFSET) @Stable public int dataLayoutFlagsOffset;
    @HotSpotVMField(name = "DataLayout::_header._struct._bci", type = "u2", get = HotSpotVMField.Type.OFFSET) @Stable public int dataLayoutBCIOffset;
    @HotSpotVMField(name = "DataLayout::_cells[0]", type = "intptr_t", get = HotSpotVMField.Type.OFFSET) @Stable public int dataLayoutCellsOffset;
    @HotSpotVMConstant(name = "DataLayout::cell_size") @Stable public int dataLayoutCellSize;

    @HotSpotVMConstant(name = "DataLayout::no_tag") @Stable public int dataLayoutNoTag;
    @HotSpotVMConstant(name = "DataLayout::bit_data_tag") @Stable public int dataLayoutBitDataTag;
    @HotSpotVMConstant(name = "DataLayout::counter_data_tag") @Stable public int dataLayoutCounterDataTag;
    @HotSpotVMConstant(name = "DataLayout::jump_data_tag") @Stable public int dataLayoutJumpDataTag;
    @HotSpotVMConstant(name = "DataLayout::receiver_type_data_tag") @Stable public int dataLayoutReceiverTypeDataTag;
    @HotSpotVMConstant(name = "DataLayout::virtual_call_data_tag") @Stable public int dataLayoutVirtualCallDataTag;
    @HotSpotVMConstant(name = "DataLayout::ret_data_tag") @Stable public int dataLayoutRetDataTag;
    @HotSpotVMConstant(name = "DataLayout::branch_data_tag") @Stable public int dataLayoutBranchDataTag;
    @HotSpotVMConstant(name = "DataLayout::multi_branch_data_tag") @Stable public int dataLayoutMultiBranchDataTag;
    @HotSpotVMConstant(name = "DataLayout::arg_info_data_tag") @Stable public int dataLayoutArgInfoDataTag;
    @HotSpotVMConstant(name = "DataLayout::call_type_data_tag") @Stable public int dataLayoutCallTypeDataTag;
    @HotSpotVMConstant(name = "DataLayout::virtual_call_type_data_tag") @Stable public int dataLayoutVirtualCallTypeDataTag;
    @HotSpotVMConstant(name = "DataLayout::parameters_type_data_tag") @Stable public int dataLayoutParametersTypeDataTag;
    @HotSpotVMConstant(name = "DataLayout::speculative_trap_data_tag") @Stable public int dataLayoutSpeculativeTrapDataTag;

    @HotSpotVMFlag(name = "BciProfileWidth") @Stable public int bciProfileWidth;
    @HotSpotVMFlag(name = "TypeProfileWidth") @Stable public int typeProfileWidth;
    @HotSpotVMFlag(name = "MethodProfileWidth") @Stable public int methodProfileWidth;

    @HotSpotVMField(name = "CompilerToVM::Data::SharedRuntime_ic_miss_stub", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long inlineCacheMissStub;
    @HotSpotVMField(name = "CompilerToVM::Data::SharedRuntime_handle_wrong_method_stub", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long handleWrongMethodStub;

    @HotSpotVMField(name = "CompilerToVM::Data::SharedRuntime_deopt_blob_unpack", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long handleDeoptStub;
    @HotSpotVMField(name = "CompilerToVM::Data::SharedRuntime_deopt_blob_uncommon_trap", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long uncommonTrapStub;

    @HotSpotVMField(name = "CodeCache::_low_bound", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long codeCacheLowBound;
    @HotSpotVMField(name = "CodeCache::_high_bound", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long codeCacheHighBound;

    @HotSpotVMField(name = "StubRoutines::_aescrypt_encryptBlock", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long aescryptEncryptBlockStub;
    @HotSpotVMField(name = "StubRoutines::_aescrypt_decryptBlock", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long aescryptDecryptBlockStub;
    @HotSpotVMField(name = "StubRoutines::_cipherBlockChaining_encryptAESCrypt", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long cipherBlockChainingEncryptAESCryptStub;
    @HotSpotVMField(name = "StubRoutines::_cipherBlockChaining_decryptAESCrypt", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long cipherBlockChainingDecryptAESCryptStub;
    @HotSpotVMField(name = "StubRoutines::_updateBytesCRC32", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long updateBytesCRC32Stub;
    @HotSpotVMField(name = "StubRoutines::_crc_table_adr", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long crcTableAddress;

    @HotSpotVMField(name = "StubRoutines::_jbyte_arraycopy", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long jbyteArraycopy;
    @HotSpotVMField(name = "StubRoutines::_jshort_arraycopy", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long jshortArraycopy;
    @HotSpotVMField(name = "StubRoutines::_jint_arraycopy", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long jintArraycopy;
    @HotSpotVMField(name = "StubRoutines::_jlong_arraycopy", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long jlongArraycopy;
    @HotSpotVMField(name = "StubRoutines::_oop_arraycopy", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long oopArraycopy;
    @HotSpotVMField(name = "StubRoutines::_oop_arraycopy_uninit", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long oopArraycopyUninit;
    @HotSpotVMField(name = "StubRoutines::_jbyte_disjoint_arraycopy", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long jbyteDisjointArraycopy;
    @HotSpotVMField(name = "StubRoutines::_jshort_disjoint_arraycopy", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long jshortDisjointArraycopy;
    @HotSpotVMField(name = "StubRoutines::_jint_disjoint_arraycopy", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long jintDisjointArraycopy;
    @HotSpotVMField(name = "StubRoutines::_jlong_disjoint_arraycopy", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long jlongDisjointArraycopy;
    @HotSpotVMField(name = "StubRoutines::_oop_disjoint_arraycopy", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long oopDisjointArraycopy;
    @HotSpotVMField(name = "StubRoutines::_oop_disjoint_arraycopy_uninit", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long oopDisjointArraycopyUninit;
    @HotSpotVMField(name = "StubRoutines::_arrayof_jbyte_arraycopy", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long jbyteAlignedArraycopy;
    @HotSpotVMField(name = "StubRoutines::_arrayof_jshort_arraycopy", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long jshortAlignedArraycopy;
    @HotSpotVMField(name = "StubRoutines::_arrayof_jint_arraycopy", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long jintAlignedArraycopy;
    @HotSpotVMField(name = "StubRoutines::_arrayof_jlong_arraycopy", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long jlongAlignedArraycopy;
    @HotSpotVMField(name = "StubRoutines::_arrayof_oop_arraycopy", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long oopAlignedArraycopy;
    @HotSpotVMField(name = "StubRoutines::_arrayof_oop_arraycopy_uninit", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long oopAlignedArraycopyUninit;
    @HotSpotVMField(name = "StubRoutines::_arrayof_jbyte_disjoint_arraycopy", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long jbyteAlignedDisjointArraycopy;
    @HotSpotVMField(name = "StubRoutines::_arrayof_jshort_disjoint_arraycopy", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long jshortAlignedDisjointArraycopy;
    @HotSpotVMField(name = "StubRoutines::_arrayof_jint_disjoint_arraycopy", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long jintAlignedDisjointArraycopy;
    @HotSpotVMField(name = "StubRoutines::_arrayof_jlong_disjoint_arraycopy", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long jlongAlignedDisjointArraycopy;
    @HotSpotVMField(name = "StubRoutines::_arrayof_oop_disjoint_arraycopy", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long oopAlignedDisjointArraycopy;
    @HotSpotVMField(name = "StubRoutines::_arrayof_oop_disjoint_arraycopy_uninit", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long oopAlignedDisjointArraycopyUninit;
    @HotSpotVMField(name = "StubRoutines::_checkcast_arraycopy", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long checkcastArraycopy;
    @HotSpotVMField(name = "StubRoutines::_checkcast_arraycopy_uninit", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long checkcastArraycopyUninit;
    @HotSpotVMField(name = "StubRoutines::_unsafe_arraycopy", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long unsafeArraycopy;
    @HotSpotVMField(name = "StubRoutines::_generic_arraycopy", type = "address", get = HotSpotVMField.Type.VALUE) @Stable public long genericArraycopy;

    @HotSpotVMAddress(name = "JVMCIRuntime::new_instance") @Stable public long newInstanceAddress;
    @HotSpotVMAddress(name = "JVMCIRuntime::new_array") @Stable public long newArrayAddress;
    @HotSpotVMAddress(name = "JVMCIRuntime::new_multi_array") @Stable public long newMultiArrayAddress;
    @HotSpotVMAddress(name = "JVMCIRuntime::dynamic_new_array") @Stable public long dynamicNewArrayAddress;
    @HotSpotVMAddress(name = "JVMCIRuntime::dynamic_new_instance") @Stable public long dynamicNewInstanceAddress;

    @HotSpotVMAddress(name = "JVMCIRuntime::thread_is_interrupted") @Stable public long threadIsInterruptedAddress;
    @HotSpotVMAddress(name = "JVMCIRuntime::vm_message") @Stable public long vmMessageAddress;
    @HotSpotVMAddress(name = "JVMCIRuntime::identity_hash_code") @Stable public long identityHashCodeAddress;
    @HotSpotVMAddress(name = "JVMCIRuntime::exception_handler_for_pc") @Stable public long exceptionHandlerForPcAddress;
    @HotSpotVMAddress(name = "JVMCIRuntime::monitorenter") @Stable public long monitorenterAddress;
    @HotSpotVMAddress(name = "JVMCIRuntime::monitorexit") @Stable public long monitorexitAddress;
    @HotSpotVMAddress(name = "JVMCIRuntime::create_null_exception") @Stable public long createNullPointerExceptionAddress;
    @HotSpotVMAddress(name = "JVMCIRuntime::create_out_of_bounds_exception") @Stable public long createOutOfBoundsExceptionAddress;
    @HotSpotVMAddress(name = "JVMCIRuntime::log_primitive") @Stable public long logPrimitiveAddress;
    @HotSpotVMAddress(name = "JVMCIRuntime::log_object") @Stable public long logObjectAddress;
    @HotSpotVMAddress(name = "JVMCIRuntime::log_printf") @Stable public long logPrintfAddress;
    @HotSpotVMAddress(name = "JVMCIRuntime::vm_error") @Stable public long vmErrorAddress;
    @HotSpotVMAddress(name = "JVMCIRuntime::load_and_clear_exception") @Stable public long loadAndClearExceptionAddress;
    @HotSpotVMAddress(name = "JVMCIRuntime::write_barrier_pre") @Stable public long writeBarrierPreAddress;
    @HotSpotVMAddress(name = "JVMCIRuntime::write_barrier_post") @Stable public long writeBarrierPostAddress;
    @HotSpotVMAddress(name = "JVMCIRuntime::validate_object") @Stable public long validateObject;

    @HotSpotVMAddress(name = "JVMCIRuntime::test_deoptimize_call_int") @Stable public long testDeoptimizeCallInt;

    @HotSpotVMAddress(name = "SharedRuntime::register_finalizer") @Stable public long registerFinalizerAddress;
    @HotSpotVMAddress(name = "SharedRuntime::exception_handler_for_return_address") @Stable public long exceptionHandlerForReturnAddressAddress;
    @HotSpotVMAddress(name = "SharedRuntime::OSR_migration_end") @Stable public long osrMigrationEndAddress;

    @HotSpotVMAddress(name = "os::javaTimeMillis") @Stable public long javaTimeMillisAddress;
    @HotSpotVMAddress(name = "os::javaTimeNanos") @Stable public long javaTimeNanosAddress;
    @HotSpotVMAddress(name = "SharedRuntime::dsin") @Stable public long arithmeticSinAddress;
    @HotSpotVMAddress(name = "SharedRuntime::dcos") @Stable public long arithmeticCosAddress;
    @HotSpotVMAddress(name = "SharedRuntime::dtan") @Stable public long arithmeticTanAddress;
    @HotSpotVMAddress(name = "SharedRuntime::dexp") @Stable public long arithmeticExpAddress;
    @HotSpotVMAddress(name = "SharedRuntime::dlog") @Stable public long arithmeticLogAddress;
    @HotSpotVMAddress(name = "SharedRuntime::dlog10") @Stable public long arithmeticLog10Address;
    @HotSpotVMAddress(name = "SharedRuntime::dpow") @Stable public long arithmeticPowAddress;

    @HotSpotVMFlag(name = "JVMCICounterSize") @Stable public int jvmciCountersSize;

    @HotSpotVMAddress(name = "Deoptimization::fetch_unroll_info") @Stable public long deoptimizationFetchUnrollInfo;
    @HotSpotVMAddress(name = "Deoptimization::uncommon_trap") @Stable public long deoptimizationUncommonTrap;
    @HotSpotVMAddress(name = "Deoptimization::unpack_frames") @Stable public long deoptimizationUnpackFrames;

    @HotSpotVMConstant(name = "Deoptimization::Reason_none") @Stable public int deoptReasonNone;
    @HotSpotVMConstant(name = "Deoptimization::Reason_null_check") @Stable public int deoptReasonNullCheck;
    @HotSpotVMConstant(name = "Deoptimization::Reason_range_check") @Stable public int deoptReasonRangeCheck;
    @HotSpotVMConstant(name = "Deoptimization::Reason_class_check") @Stable public int deoptReasonClassCheck;
    @HotSpotVMConstant(name = "Deoptimization::Reason_array_check") @Stable public int deoptReasonArrayCheck;
    @HotSpotVMConstant(name = "Deoptimization::Reason_unreached0") @Stable public int deoptReasonUnreached0;
    @HotSpotVMConstant(name = "Deoptimization::Reason_type_checked_inlining") @Stable public int deoptReasonTypeCheckInlining;
    @HotSpotVMConstant(name = "Deoptimization::Reason_optimized_type_check") @Stable public int deoptReasonOptimizedTypeCheck;
    @HotSpotVMConstant(name = "Deoptimization::Reason_not_compiled_exception_handler") @Stable public int deoptReasonNotCompiledExceptionHandler;
    @HotSpotVMConstant(name = "Deoptimization::Reason_unresolved") @Stable public int deoptReasonUnresolved;
    @HotSpotVMConstant(name = "Deoptimization::Reason_jsr_mismatch") @Stable public int deoptReasonJsrMismatch;
    @HotSpotVMConstant(name = "Deoptimization::Reason_div0_check") @Stable public int deoptReasonDiv0Check;
    @HotSpotVMConstant(name = "Deoptimization::Reason_constraint") @Stable public int deoptReasonConstraint;
    @HotSpotVMConstant(name = "Deoptimization::Reason_loop_limit_check") @Stable public int deoptReasonLoopLimitCheck;
    @HotSpotVMConstant(name = "Deoptimization::Reason_aliasing") @Stable public int deoptReasonAliasing;
    @HotSpotVMConstant(name = "Deoptimization::Reason_transfer_to_interpreter") @Stable public int deoptReasonTransferToInterpreter;
    @HotSpotVMConstant(name = "Deoptimization::Reason_LIMIT") @Stable public int deoptReasonOSROffset;

    @HotSpotVMConstant(name = "Deoptimization::Action_none") @Stable public int deoptActionNone;
    @HotSpotVMConstant(name = "Deoptimization::Action_maybe_recompile") @Stable public int deoptActionMaybeRecompile;
    @HotSpotVMConstant(name = "Deoptimization::Action_reinterpret") @Stable public int deoptActionReinterpret;
    @HotSpotVMConstant(name = "Deoptimization::Action_make_not_entrant") @Stable public int deoptActionMakeNotEntrant;
    @HotSpotVMConstant(name = "Deoptimization::Action_make_not_compilable") @Stable public int deoptActionMakeNotCompilable;

    @HotSpotVMConstant(name = "Deoptimization::_action_bits") @Stable public int deoptimizationActionBits;
    @HotSpotVMConstant(name = "Deoptimization::_reason_bits") @Stable public int deoptimizationReasonBits;
    @HotSpotVMConstant(name = "Deoptimization::_debug_id_bits") @Stable public int deoptimizationDebugIdBits;
    @HotSpotVMConstant(name = "Deoptimization::_action_shift") @Stable public int deoptimizationActionShift;
    @HotSpotVMConstant(name = "Deoptimization::_reason_shift") @Stable public int deoptimizationReasonShift;
    @HotSpotVMConstant(name = "Deoptimization::_debug_id_shift") @Stable public int deoptimizationDebugIdShift;

    @HotSpotVMConstant(name = "Deoptimization::Unpack_deopt") @Stable public int deoptimizationUnpackDeopt;
    @HotSpotVMConstant(name = "Deoptimization::Unpack_exception") @Stable public int deoptimizationUnpackException;
    @HotSpotVMConstant(name = "Deoptimization::Unpack_uncommon_trap") @Stable public int deoptimizationUnpackUncommonTrap;
    @HotSpotVMConstant(name = "Deoptimization::Unpack_reexecute") @Stable public int deoptimizationUnpackReexecute;

    @HotSpotVMField(name = "Deoptimization::UnrollBlock::_size_of_deoptimized_frame", type = "int", get = HotSpotVMField.Type.OFFSET) @Stable public int deoptimizationUnrollBlockSizeOfDeoptimizedFrameOffset;
    @HotSpotVMField(name = "Deoptimization::UnrollBlock::_caller_adjustment", type = "int", get = HotSpotVMField.Type.OFFSET) @Stable public int deoptimizationUnrollBlockCallerAdjustmentOffset;
    @HotSpotVMField(name = "Deoptimization::UnrollBlock::_number_of_frames", type = "int", get = HotSpotVMField.Type.OFFSET) @Stable public int deoptimizationUnrollBlockNumberOfFramesOffset;
    @HotSpotVMField(name = "Deoptimization::UnrollBlock::_total_frame_sizes", type = "int", get = HotSpotVMField.Type.OFFSET) @Stable public int deoptimizationUnrollBlockTotalFrameSizesOffset;
    @HotSpotVMField(name = "Deoptimization::UnrollBlock::_unpack_kind", type = "int", get = HotSpotVMField.Type.OFFSET) @Stable public int deoptimizationUnrollBlockUnpackKindOffset;
    @HotSpotVMField(name = "Deoptimization::UnrollBlock::_frame_sizes", type = "intptr_t*", get = HotSpotVMField.Type.OFFSET) @Stable public int deoptimizationUnrollBlockFrameSizesOffset;
    @HotSpotVMField(name = "Deoptimization::UnrollBlock::_frame_pcs", type = "address*", get = HotSpotVMField.Type.OFFSET) @Stable public int deoptimizationUnrollBlockFramePcsOffset;
    @HotSpotVMField(name = "Deoptimization::UnrollBlock::_initial_info", type = "intptr_t", get = HotSpotVMField.Type.OFFSET) @Stable public int deoptimizationUnrollBlockInitialInfoOffset;

    @HotSpotVMConstant(name = "vmIntrinsics::_invokeBasic") @Stable public int vmIntrinsicInvokeBasic;
    @HotSpotVMConstant(name = "vmIntrinsics::_linkToVirtual") @Stable public int vmIntrinsicLinkToVirtual;
    @HotSpotVMConstant(name = "vmIntrinsics::_linkToStatic") @Stable public int vmIntrinsicLinkToStatic;
    @HotSpotVMConstant(name = "vmIntrinsics::_linkToSpecial") @Stable public int vmIntrinsicLinkToSpecial;
    @HotSpotVMConstant(name = "vmIntrinsics::_linkToInterface") @Stable public int vmIntrinsicLinkToInterface;

    @HotSpotVMConstant(name = "JVMCIEnv::ok") @Stable public int codeInstallResultOk;
    @HotSpotVMConstant(name = "JVMCIEnv::dependencies_failed") @Stable public int codeInstallResultDependenciesFailed;
    @HotSpotVMConstant(name = "JVMCIEnv::dependencies_invalid") @Stable public int codeInstallResultDependenciesInvalid;
    @HotSpotVMConstant(name = "JVMCIEnv::cache_full") @Stable public int codeInstallResultCacheFull;
    @HotSpotVMConstant(name = "JVMCIEnv::code_too_large") @Stable public int codeInstallResultCodeTooLarge;

    public String getCodeInstallResultDescription(int codeInstallResult) {
        if (codeInstallResult == codeInstallResultOk) {
            return "ok";
        }
        if (codeInstallResult == codeInstallResultDependenciesFailed) {
            return "dependencies failed";
        }
        if (codeInstallResult == codeInstallResultDependenciesInvalid) {
            return "dependencies invalid";
        }
        if (codeInstallResult == codeInstallResultCacheFull) {
            return "code cache is full";
        }
        if (codeInstallResult == codeInstallResultCodeTooLarge) {
            return "code is too large";
        }
        assert false : codeInstallResult;
        return "unknown";
    }

    // Checkstyle: stop
    @HotSpotVMConstant(name = "CodeInstaller::VERIFIED_ENTRY") @Stable public int MARKID_VERIFIED_ENTRY;
    @HotSpotVMConstant(name = "CodeInstaller::UNVERIFIED_ENTRY") @Stable public int MARKID_UNVERIFIED_ENTRY;
    @HotSpotVMConstant(name = "CodeInstaller::OSR_ENTRY") @Stable public int MARKID_OSR_ENTRY;
    @HotSpotVMConstant(name = "CodeInstaller::EXCEPTION_HANDLER_ENTRY") @Stable public int MARKID_EXCEPTION_HANDLER_ENTRY;
    @HotSpotVMConstant(name = "CodeInstaller::DEOPT_HANDLER_ENTRY") @Stable public int MARKID_DEOPT_HANDLER_ENTRY;
    @HotSpotVMConstant(name = "CodeInstaller::INVOKEINTERFACE") @Stable public int MARKID_INVOKEINTERFACE;
    @HotSpotVMConstant(name = "CodeInstaller::INVOKEVIRTUAL") @Stable public int MARKID_INVOKEVIRTUAL;
    @HotSpotVMConstant(name = "CodeInstaller::INVOKESTATIC") @Stable public int MARKID_INVOKESTATIC;
    @HotSpotVMConstant(name = "CodeInstaller::INVOKESPECIAL") @Stable public int MARKID_INVOKESPECIAL;
    @HotSpotVMConstant(name = "CodeInstaller::INLINE_INVOKE") @Stable public int MARKID_INLINE_INVOKE;
    @HotSpotVMConstant(name = "CodeInstaller::POLL_NEAR") @Stable public int MARKID_POLL_NEAR;
    @HotSpotVMConstant(name = "CodeInstaller::POLL_RETURN_NEAR") @Stable public int MARKID_POLL_RETURN_NEAR;
    @HotSpotVMConstant(name = "CodeInstaller::POLL_FAR") @Stable public int MARKID_POLL_FAR;
    @HotSpotVMConstant(name = "CodeInstaller::POLL_RETURN_FAR") @Stable public int MARKID_POLL_RETURN_FAR;
    @HotSpotVMConstant(name = "CodeInstaller::CARD_TABLE_SHIFT") @Stable public int MARKID_CARD_TABLE_SHIFT;
    @HotSpotVMConstant(name = "CodeInstaller::CARD_TABLE_ADDRESS") @Stable public int MARKID_CARD_TABLE_ADDRESS;
    @HotSpotVMConstant(name = "CodeInstaller::HEAP_TOP_ADDRESS") @Stable public int MARKID_HEAP_TOP_ADDRESS;
    @HotSpotVMConstant(name = "CodeInstaller::HEAP_END_ADDRESS") @Stable public int MARKID_HEAP_END_ADDRESS;
    @HotSpotVMConstant(name = "CodeInstaller::NARROW_KLASS_BASE_ADDRESS") @Stable public int MARKID_NARROW_KLASS_BASE_ADDRESS;
    @HotSpotVMConstant(name = "CodeInstaller::CRC_TABLE_ADDRESS") @Stable public int MARKID_CRC_TABLE_ADDRESS;
    @HotSpotVMConstant(name = "CodeInstaller::INVOKE_INVALID") @Stable public int MARKID_INVOKE_INVALID;

    @HotSpotVMConstant(name = "BitData::exception_seen_flag") @Stable public int bitDataExceptionSeenFlag;
    @HotSpotVMConstant(name = "BitData::null_seen_flag") @Stable public int bitDataNullSeenFlag;
    @HotSpotVMConstant(name = "CounterData::count_off") @Stable public int methodDataCountOffset;
    @HotSpotVMConstant(name = "JumpData::taken_off_set") @Stable public int jumpDataTakenOffset;
    @HotSpotVMConstant(name = "JumpData::displacement_off_set") @Stable public int jumpDataDisplacementOffset;
    @HotSpotVMConstant(name = "ReceiverTypeData::nonprofiled_count_off_set") @Stable public int receiverTypeDataNonprofiledCountOffset;
    @HotSpotVMConstant(name = "ReceiverTypeData::receiver_type_row_cell_count") @Stable public int receiverTypeDataReceiverTypeRowCellCount;
    @HotSpotVMConstant(name = "ReceiverTypeData::receiver0_offset") @Stable public int receiverTypeDataReceiver0Offset;
    @HotSpotVMConstant(name = "ReceiverTypeData::count0_offset") @Stable public int receiverTypeDataCount0Offset;
    @HotSpotVMConstant(name = "BranchData::not_taken_off_set") @Stable public int branchDataNotTakenOffset;
    @HotSpotVMConstant(name = "ArrayData::array_len_off_set") @Stable public int arrayDataArrayLenOffset;
    @HotSpotVMConstant(name = "ArrayData::array_start_off_set") @Stable public int arrayDataArrayStartOffset;
    @HotSpotVMConstant(name = "MultiBranchData::per_case_cell_count") @Stable public int multiBranchDataPerCaseCellCount;

    // Checkstyle: resume

    private boolean check() {
        for (Field f : getClass().getDeclaredFields()) {
            int modifiers = f.getModifiers();
            if (Modifier.isPublic(modifiers) && !Modifier.isStatic(modifiers)) {
                assert Modifier.isFinal(modifiers) || f.getAnnotation(Stable.class) != null : "field should either be final or @Stable: " + f;
            }
        }

        assert codeEntryAlignment > 0 : codeEntryAlignment;
        assert (layoutHelperArrayTagObjectValue & (1 << (Integer.SIZE - 1))) != 0 : "object array must have first bit set";
        assert (layoutHelperArrayTagTypeValue & (1 << (Integer.SIZE - 1))) != 0 : "type array must have first bit set";

        return true;
    }

    /**
     * A compact representation of the different encoding strategies for Objects and metadata.
     */
    public static class CompressEncoding {
        public final long base;
        public final int shift;
        public final int alignment;

        CompressEncoding(long base, int shift, int alignment) {
            this.base = base;
            this.shift = shift;
            this.alignment = alignment;
        }

        public int compress(long ptr) {
            if (ptr == 0L) {
                return 0;
            } else {
                return (int) ((ptr - base) >>> shift);
            }
        }

        public long uncompress(int ptr) {
            if (ptr == 0) {
                return 0L;
            } else {
                return ((ptr & 0xFFFFFFFFL) << shift) + base;
            }
        }

        @Override
        public String toString() {
            return "base: " + base + " shift: " + shift + " alignment: " + alignment;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + alignment;
            result = prime * result + (int) (base ^ (base >>> 32));
            result = prime * result + shift;
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof CompressEncoding) {
                CompressEncoding other = (CompressEncoding) obj;
                return alignment == other.alignment && base == other.base && shift == other.shift;
            } else {
                return false;
            }
        }
    }

}
