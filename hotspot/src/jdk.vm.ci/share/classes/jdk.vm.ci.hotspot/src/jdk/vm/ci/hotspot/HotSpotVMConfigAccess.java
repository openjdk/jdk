/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

import jdk.vm.ci.common.JVMCIError;

/**
 * Access to VM configuration data.
 */
public class HotSpotVMConfigAccess {

    /**
     * Gets the available configuration data.
     */
    public HotSpotVMConfigStore getStore() {
        return store;
    }

    /**
     * Gets the address of a C++ symbol.
     *
     * @param name name of C++ symbol
     * @param notPresent if non-null and the symbol is not present then this value is returned
     * @return the address of the symbol
     * @throws JVMCIError if the symbol is not present and {@code notPresent == null}
     */
    public long getAddress(String name, Long notPresent) {
        Long entry = store.vmAddresses.get(name);
        if (entry == null) {
            if (notPresent != null) {
                return notPresent;
            }
            throw new JVMCIError("expected VM symbol not found: " + name);
        }
        return entry;
    }

    /**
     * Gets the address of a C++ symbol.
     *
     * @param name name of C++ symbol
     * @return the address of the symbol
     * @throws JVMCIError if the symbol is not present
     */
    public long getAddress(String name) {
        return getAddress(name, null);
    }

    /**
     * Gets the value of a C++ constant.
     *
     * @param name name of the constant (e.g., {@code "frame::arg_reg_save_area_bytes"})
     * @param type the boxed type to which the constant value will be converted
     * @param notPresent if non-null and the constant is not present then this value is returned
     * @return the constant value converted to {@code type}
     * @throws JVMCIError if the constant is not present and {@code notPresent == null}
     */
    public <T> T getConstant(String name, Class<T> type, T notPresent) {
        Long c = store.vmConstants.get(name);
        if (c == null) {
            if (notPresent != null) {
                return notPresent;
            }
            throw new JVMCIError("expected VM constant not found: " + name);
        }
        return type.cast(convertValue(name, type, c, null));
    }

    /**
     * Gets the value of a C++ constant.
     *
     * @param name name of the constant (e.g., {@code "frame::arg_reg_save_area_bytes"})
     * @param type the boxed type to which the constant value will be converted
     * @return the constant value converted to {@code type}
     * @throws JVMCIError if the constant is not present
     */
    public <T> T getConstant(String name, Class<T> type) {
        return getConstant(name, type, null);
    }

    /**
     * Gets the offset of a non-static C++ field.
     *
     * @param name fully qualified name of the field
     * @param type the boxed type to which the offset value will be converted (must be
     *            {@link Integer} or {@link Long})
     * @param cppType if non-null, the expected C++ type of the field (e.g., {@code "HeapWord*"})
     * @param notPresent if non-null and the field is not present then this value is returned
     * @return the offset in bytes of the requested field
     * @throws JVMCIError if the field is static or not present and {@code notPresent} is null
     */
    public <T> T getFieldOffset(String name, Class<T> type, String cppType, T notPresent) {
        assert type == Integer.class || type == Long.class;
        VMField entry = getField(name, cppType, notPresent == null);
        if (entry == null) {
            return notPresent;
        }
        if (entry.address != 0) {
            throw new JVMCIError("cannot get offset of static field " + name);
        }
        return type.cast(convertValue(name, type, entry.offset, cppType));
    }

    /**
     * Gets the offset of a non-static C++ field.
     *
     * @param name fully qualified name of the field
     * @param type the boxed type to which the offset value will be converted (must be
     *            {@link Integer} or {@link Long})
     * @param cppType if non-null, the expected C++ type of the field (e.g., {@code "HeapWord*"})
     * @return the offset in bytes of the requested field
     * @throws JVMCIError if the field is static or not present
     */
    public <T> T getFieldOffset(String name, Class<T> type, String cppType) {
        return getFieldOffset(name, type, cppType, null);
    }

    /**
     * Gets the offset of a non-static C++ field.
     *
     * @param name fully qualified name of the field
     * @param type the boxed type to which the offset value will be converted (must be
     *            {@link Integer} or {@link Long})
     * @return the offset in bytes of the requested field
     * @throws JVMCIError if the field is static or not present
     */
    public <T> T getFieldOffset(String name, Class<T> type) {
        return getFieldOffset(name, type, null, null);
    }

    /**
     * Gets the address of a static C++ field.
     *
     * @param name fully qualified name of the field
     * @param cppType if non-null, the expected C++ type of the field (e.g., {@code "HeapWord*"})
     * @param notPresent if non-null and the field is not present then this value is returned
     * @return the address of the requested field
     * @throws JVMCIError if the field is not static or not present and {@code notPresent} is null
     */
    public long getFieldAddress(String name, String cppType, Long notPresent) {
        VMField entry = getField(name, cppType, notPresent == null);
        if (entry == null) {
            return notPresent;
        }
        if (entry.address == 0) {
            throw new JVMCIError(name + " is not a static field");
        }
        return entry.address;
    }

    /**
     * Gets the address of a static C++ field.
     *
     * @param name fully qualified name of the field
     * @param cppType if non-null, the expected C++ type of the field (e.g., {@code "HeapWord*"})
     * @return the address of the requested field
     * @throws JVMCIError if the field is not static or not present
     */
    public long getFieldAddress(String name, String cppType) {
        return getFieldAddress(name, cppType, null);
    }

    /**
     * Gets the value of a static C++ field.
     *
     * @param name fully qualified name of the field
     * @param type the boxed type to which the constant value will be converted
     * @param cppType if non-null, the expected C++ type of the field (e.g., {@code "HeapWord*"})
     * @param notPresent if non-null and the field is not present then this value is returned
     * @return the value of the requested field
     * @throws JVMCIError if the field is not static or not present and {@code notPresent} is null
     */
    public <T> T getFieldValue(String name, Class<T> type, String cppType, T notPresent) {
        VMField entry = getField(name, cppType, notPresent == null);
        if (entry == null) {
            return notPresent;
        }
        if (entry.value == null) {
            throw new JVMCIError(name + " is not a static field");
        }
        return type.cast(convertValue(name, type, entry.value, cppType));
    }

    /**
     * Gets the value of a static C++ field.
     *
     * @param name fully qualified name of the field
     * @param type the boxed type to which the constant value will be converted
     * @param cppType if non-null, the expected C++ type of the field (e.g., {@code "HeapWord*"})
     * @return the value of the requested field
     * @throws JVMCIError if the field is not static or not present
     */
    public <T> T getFieldValue(String name, Class<T> type, String cppType) {
        return getFieldValue(name, type, cppType, null);
    }

    /**
     * Gets the value of a static C++ field.
     *
     * @param name fully qualified name of the field
     * @param type the boxed type to which the constant value will be converted
     * @return the value of the requested field
     * @throws JVMCIError if the field is not static or not present
     */
    public <T> T getFieldValue(String name, Class<T> type) {
        return getFieldValue(name, type, null, null);
    }

    /**
     * Gets a C++ field.
     *
     * @param name fully qualified name of the field
     * @param cppType if non-null, the expected C++ type of the field (e.g., {@code "HeapWord*"})
     * @param required specifies if the field must be present
     * @return the field
     * @throws JVMCIError if the field is not present and {@code required == true}
     */
    private VMField getField(String name, String cppType, boolean required) {
        VMField entry = store.vmFields.get(name);
        if (entry == null) {
            if (!required) {
                return null;
            }
            throw new JVMCIError("expected VM field not found: " + name);
        }

        // Make sure the native type is still the type we expect.
        if (cppType != null && !cppType.equals(entry.type)) {
            throw new JVMCIError("expected type " + cppType + " but VM field " + name + " is of type " + entry.type);
        }
        return entry;
    }

    /**
     * Gets a VM flag value.
     *
     * @param name name of the flag (e.g., {@code "CompileTheWorldStartAt"})
     * @param type the boxed type to which the flag's value will be converted
     * @return the flag's value converted to {@code type} or {@code notPresent} if the flag is not
     *         present
     * @throws JVMCIError if the flag is not present
     */
    public <T> T getFlag(String name, Class<T> type) {
        return getFlag(name, type, null);
    }

    /**
     * Gets a VM flag value.
     *
     * @param name name of the flag (e.g., {@code "CompileTheWorldStartAt"})
     * @param type the boxed type to which the flag's value will be converted
     * @param notPresent if non-null and the flag is not present then this value is returned
     * @return the flag's value converted to {@code type} or {@code notPresent} if the flag is not
     *         present
     * @throws JVMCIError if the flag is not present and {@code notPresent == null}
     */
    public <T> T getFlag(String name, Class<T> type, T notPresent) {
        VMFlag entry = store.vmFlags.get(name);
        Object value;
        String cppType;
        if (entry == null) {
            // Fall back to VM call
            value = store.compilerToVm.getFlagValue(name);
            if (value == store.compilerToVm) {
                if (notPresent != null) {
                    return notPresent;
                }
                throw new JVMCIError("expected VM flag not found: " + name);
            } else {
                cppType = null;
            }
        } else {
            value = entry.value;
            cppType = entry.type;
        }
        return type.cast(convertValue(name, type, value, cppType));
    }

    private static <T> Object convertValue(String name, Class<T> toType, Object value, String cppType) throws JVMCIError {
        if (toType == Boolean.class) {
            if (value instanceof String) {
                return Boolean.valueOf((String) value);
            } else if (value instanceof Boolean) {
                return value;
            } else if (value instanceof Long) {
                return ((long) value) != 0;
            }
        } else if (toType == Byte.class) {
            if (value instanceof Long) {
                return (byte) (long) value;
            }
        } else if (toType == Integer.class) {
            if (value instanceof Integer) {
                return value;
            } else if (value instanceof Long) {
                return (int) (long) value;
            }
        } else if (toType == String.class) {
            if (value == null || value instanceof String) {
                return value;
            }
        } else if (toType == Long.class) {
            return value;
        }

        throw new JVMCIError("cannot convert " + name + " of type " + value.getClass().getSimpleName() + (cppType == null ? "" : " [" + cppType + "]") + " to " + toType.getSimpleName());
    }

    private final HotSpotVMConfigStore store;

    public HotSpotVMConfigAccess(HotSpotVMConfigStore store) {
        this.store = store;
    }
}
