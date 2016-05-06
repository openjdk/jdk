/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8154556
 * @run testng/othervm -Diters=20000 -XX:TieredStopAtLevel=1 VarHandleTestByteArrayAsInt
 * @run testng/othervm -Diters=20000                         VarHandleTestByteArrayAsInt
 * @run testng/othervm -Diters=20000 -XX:-TieredCompilation  VarHandleTestByteArrayAsInt
 */

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

import static org.testng.Assert.*;

public class VarHandleTestByteArrayAsInt extends VarHandleBaseByteArrayTest {
    static final int SIZE = Integer.BYTES;

    static final int VALUE_1 = 0x01020304;

    static final int VALUE_2 = 0x11121314;

    static final int VALUE_3 = 0x21222324;


    @Override
    public void setupVarHandleSources() {
        // Combinations of VarHandle byte[] or ByteBuffer
        vhss = new ArrayList<>();
        for (MemoryMode endianess : Arrays.asList(MemoryMode.BIG_ENDIAN, MemoryMode.LITTLE_ENDIAN)) {

            ByteOrder bo = endianess == MemoryMode.BIG_ENDIAN
                    ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;
            VarHandleSource aeh = new VarHandleSource(
                    MethodHandles.byteArrayViewVarHandle(int[].class, bo),
                    endianess, MemoryMode.READ_WRITE);
            vhss.add(aeh);

            VarHandleSource bbh = new VarHandleSource(
                    MethodHandles.byteBufferViewVarHandle(int[].class, bo),
                    endianess, MemoryMode.READ_WRITE);
            vhss.add(bbh);
        }
    }


    @Test(dataProvider = "varHandlesProvider")
    public void testIsAccessModeSupported(VarHandleSource vhs) {
        VarHandle vh = vhs.s;

        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.GET));
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.SET));

        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.GET_VOLATILE));
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.SET_VOLATILE));
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.GET_ACQUIRE));
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.SET_RELEASE));
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.GET_OPAQUE));
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.SET_OPAQUE));

        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.COMPARE_AND_SET));
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.COMPARE_AND_EXCHANGE_VOLATILE));
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.COMPARE_AND_EXCHANGE_ACQUIRE));
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.COMPARE_AND_EXCHANGE_RELEASE));
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.WEAK_COMPARE_AND_SET));
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.WEAK_COMPARE_AND_SET_VOLATILE));
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.WEAK_COMPARE_AND_SET_ACQUIRE));
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.WEAK_COMPARE_AND_SET_RELEASE));
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.GET_AND_SET));

        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.GET_AND_ADD));
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.ADD_AND_GET));
    }

    @Test(dataProvider = "typesProvider")
    public void testTypes(VarHandle vh, List<java.lang.Class<?>> pts) {
        assertEquals(vh.varType(), int.class);

        assertEquals(vh.coordinateTypes(), pts);

        testTypes(vh);
    }


    @DataProvider
    public Object[][] accessTestCaseProvider() throws Exception {
        List<AccessTestCase<?>> cases = new ArrayList<>();

        for (ByteArrayViewSource<?> bav : bavss) {
            for (VarHandleSource vh : vhss) {
                if (vh.matches(bav)) {
                    if (bav instanceof ByteArraySource) {
                        ByteArraySource bas = (ByteArraySource) bav;

                        cases.add(new VarHandleSourceAccessTestCase(
                                "read write", bav, vh, h -> testArrayReadWrite(bas, h),
                                true));
                        cases.add(new VarHandleSourceAccessTestCase(
                                "unsupported", bav, vh, h -> testArrayUnsupported(bas, h),
                                false));
                        cases.add(new VarHandleSourceAccessTestCase(
                                "index out of bounds", bav, vh, h -> testArrayIndexOutOfBounds(bas, h),
                                false));
                        cases.add(new VarHandleSourceAccessTestCase(
                                "misaligned access", bav, vh, h -> testArrayMisalignedAccess(bas, h),
                                false));
                    }
                    else {
                        ByteBufferSource bbs = (ByteBufferSource) bav;

                        if (MemoryMode.READ_WRITE.isSet(bav.memoryModes)) {
                            cases.add(new VarHandleSourceAccessTestCase(
                                    "read write", bav, vh, h -> testArrayReadWrite(bbs, h),
                                    true));
                        }
                        else {
                            cases.add(new VarHandleSourceAccessTestCase(
                                    "read only", bav, vh, h -> testArrayReadOnly(bbs, h),
                                    true));
                        }

                        cases.add(new VarHandleSourceAccessTestCase(
                                "unsupported", bav, vh, h -> testArrayUnsupported(bbs, h),
                                false));
                        cases.add(new VarHandleSourceAccessTestCase(
                                "index out of bounds", bav, vh, h -> testArrayIndexOutOfBounds(bbs, h),
                                false));
                        cases.add(new VarHandleSourceAccessTestCase(
                                "misaligned access", bav, vh, h -> testArrayMisalignedAccess(bbs, h),
                                false));
                    }
                }
            }
        }

        // Work around issue with jtreg summary reporting which truncates
        // the String result of Object.toString to 30 characters, hence
        // the first dummy argument
        return cases.stream().map(tc -> new Object[]{tc.toString(), tc}).toArray(Object[][]::new);
    }

    @Test(dataProvider = "accessTestCaseProvider")
    public <T> void testAccess(String desc, AccessTestCase<T> atc) throws Throwable {
        T t = atc.get();
        int iters = atc.requiresLoop() ? ITERS : 1;
        for (int c = 0; c < iters; c++) {
            atc.testAccess(t);
        }
    }


    static void testArrayUnsupported(ByteArraySource bs, VarHandleSource vhs) {
        VarHandle vh = vhs.s;
        byte[] array = bs.s;
        int ci = 1;


    }

    static void testArrayUnsupported(ByteBufferSource bs, VarHandleSource vhs) {
        VarHandle vh = vhs.s;
        ByteBuffer array = bs.s;
        int ci = 0;
        boolean readOnly = MemoryMode.READ_ONLY.isSet(bs.memoryModes);

        if (readOnly) {
            checkROBE(() -> {
                vh.set(array, ci, VALUE_1);
            });
        }

        if (readOnly) {
            checkROBE(() -> {
                vh.setVolatile(array, ci, VALUE_1);
            });

            checkROBE(() -> {
                vh.setRelease(array, ci, VALUE_1);
            });

            checkROBE(() -> {
                vh.setOpaque(array, ci, VALUE_1);
            });

            checkROBE(() -> {
                boolean r = vh.compareAndSet(array, ci, VALUE_1, VALUE_2);
            });

            checkROBE(() -> {
                int r = (int) vh.compareAndExchangeVolatile(array, ci, VALUE_2, VALUE_1);
            });

            checkROBE(() -> {
                int r = (int) vh.compareAndExchangeAcquire(array, ci, VALUE_2, VALUE_1);
            });

            checkROBE(() -> {
                int r = (int) vh.compareAndExchangeRelease(array, ci, VALUE_2, VALUE_1);
            });

            checkROBE(() -> {
                boolean r = vh.weakCompareAndSet(array, ci, VALUE_1, VALUE_2);
            });

            checkROBE(() -> {
                boolean r = vh.weakCompareAndSetVolatile(array, ci, VALUE_1, VALUE_2);
            });

            checkROBE(() -> {
                boolean r = vh.weakCompareAndSetAcquire(array, ci, VALUE_1, VALUE_2);
            });

            checkROBE(() -> {
                boolean r = vh.weakCompareAndSetRelease(array, ci, VALUE_1, VALUE_2);
            });

            checkROBE(() -> {
                int o = (int) vh.getAndSet(array, ci, VALUE_1);
            });
            checkUOE(() -> {
                boolean r = vh.compareAndSet(array, ci, VALUE_1, VALUE_2);
            });

            checkROBE(() -> {
                int o = (int) vh.getAndAdd(array, ci, VALUE_1);
            });

            checkROBE(() -> {
                int o = (int) vh.addAndGet(array, ci, VALUE_1);
            });
        }
        else {
        }
    }


    static void testArrayIndexOutOfBounds(ByteArraySource bs, VarHandleSource vhs) throws Throwable {
        VarHandle vh = vhs.s;
        byte[] array = bs.s;

        int length = array.length - SIZE + 1;
        for (int i : new int[]{-1, Integer.MIN_VALUE, length, length + 1, Integer.MAX_VALUE}) {
            final int ci = i;

            checkIOOBE(() -> {
                int x = (int) vh.get(array, ci);
            });

            checkIOOBE(() -> {
                vh.set(array, ci, VALUE_1);
            });

            checkIOOBE(() -> {
                int x = (int) vh.getVolatile(array, ci);
            });

            checkIOOBE(() -> {
                int x = (int) vh.getAcquire(array, ci);
            });

            checkIOOBE(() -> {
                int x = (int) vh.getOpaque(array, ci);
            });

            checkIOOBE(() -> {
                vh.setVolatile(array, ci, VALUE_1);
            });

            checkIOOBE(() -> {
                vh.setRelease(array, ci, VALUE_1);
            });

            checkIOOBE(() -> {
                vh.setOpaque(array, ci, VALUE_1);
            });

            checkIOOBE(() -> {
                boolean r = vh.compareAndSet(array, ci, VALUE_1, VALUE_2);
            });

            checkIOOBE(() -> {
                int r = (int) vh.compareAndExchangeVolatile(array, ci, VALUE_2, VALUE_1);
            });

            checkIOOBE(() -> {
                int r = (int) vh.compareAndExchangeAcquire(array, ci, VALUE_2, VALUE_1);
            });

            checkIOOBE(() -> {
                int r = (int) vh.compareAndExchangeRelease(array, ci, VALUE_2, VALUE_1);
            });

            checkIOOBE(() -> {
                boolean r = vh.weakCompareAndSet(array, ci, VALUE_1, VALUE_2);
            });

            checkIOOBE(() -> {
                boolean r = vh.weakCompareAndSetVolatile(array, ci, VALUE_1, VALUE_2);
            });

            checkIOOBE(() -> {
                boolean r = vh.weakCompareAndSetAcquire(array, ci, VALUE_1, VALUE_2);
            });

            checkIOOBE(() -> {
                boolean r = vh.weakCompareAndSetRelease(array, ci, VALUE_1, VALUE_2);
            });

            checkIOOBE(() -> {
                int o = (int) vh.getAndSet(array, ci, VALUE_1);
            });

            checkIOOBE(() -> {
                int o = (int) vh.getAndAdd(array, ci, VALUE_1);
            });

            checkIOOBE(() -> {
                int o = (int) vh.addAndGet(array, ci, VALUE_1);
            });

        }
    }

    static void testArrayIndexOutOfBounds(ByteBufferSource bs, VarHandleSource vhs) throws Throwable {
        VarHandle vh = vhs.s;
        ByteBuffer array = bs.s;

        boolean readOnly = MemoryMode.READ_ONLY.isSet(bs.memoryModes);

        int length = array.limit() - SIZE + 1;
        for (int i : new int[]{-1, Integer.MIN_VALUE, length, length + 1, Integer.MAX_VALUE}) {
            final int ci = i;

            checkIOOBE(() -> {
                int x = (int) vh.get(array, ci);
            });

            if (!readOnly) {
                checkIOOBE(() -> {
                    vh.set(array, ci, VALUE_1);
                });
            }

            checkIOOBE(() -> {
                int x = (int) vh.getVolatile(array, ci);
            });

            checkIOOBE(() -> {
                int x = (int) vh.getAcquire(array, ci);
            });

            checkIOOBE(() -> {
                int x = (int) vh.getOpaque(array, ci);
            });

            if (!readOnly) {
                checkIOOBE(() -> {
                    vh.setVolatile(array, ci, VALUE_1);
                });

                checkIOOBE(() -> {
                    vh.setRelease(array, ci, VALUE_1);
                });

                checkIOOBE(() -> {
                    vh.setOpaque(array, ci, VALUE_1);
                });

                checkIOOBE(() -> {
                    boolean r = vh.compareAndSet(array, ci, VALUE_1, VALUE_2);
                });

                checkIOOBE(() -> {
                    int r = (int) vh.compareAndExchangeVolatile(array, ci, VALUE_2, VALUE_1);
                });

                checkIOOBE(() -> {
                    int r = (int) vh.compareAndExchangeAcquire(array, ci, VALUE_2, VALUE_1);
                });

                checkIOOBE(() -> {
                    int r = (int) vh.compareAndExchangeRelease(array, ci, VALUE_2, VALUE_1);
                });

                checkIOOBE(() -> {
                    boolean r = vh.weakCompareAndSet(array, ci, VALUE_1, VALUE_2);
                });

                checkIOOBE(() -> {
                    boolean r = vh.weakCompareAndSetVolatile(array, ci, VALUE_1, VALUE_2);
                });

                checkIOOBE(() -> {
                    boolean r = vh.weakCompareAndSetAcquire(array, ci, VALUE_1, VALUE_2);
                });

                checkIOOBE(() -> {
                    boolean r = vh.weakCompareAndSetRelease(array, ci, VALUE_1, VALUE_2);
                });

                checkIOOBE(() -> {
                    int o = (int) vh.getAndSet(array, ci, VALUE_1);
                });

                checkIOOBE(() -> {
                    int o = (int) vh.getAndAdd(array, ci, VALUE_1);
                });

                checkIOOBE(() -> {
                    int o = (int) vh.addAndGet(array, ci, VALUE_1);
                });
            }
        }
    }

    static void testArrayMisalignedAccess(ByteArraySource bs, VarHandleSource vhs) throws Throwable {
        VarHandle vh = vhs.s;
        byte[] array = bs.s;

        int misalignmentAtZero = ByteBuffer.wrap(array).alignmentOffset(0, SIZE);

        int length = array.length - SIZE + 1;
        for (int i = 0; i < length; i++) {
            boolean iAligned = ((i + misalignmentAtZero) & (SIZE - 1)) == 0;
            final int ci = i;

            if (!iAligned) {
                checkISE(() -> {
                    int x = (int) vh.getVolatile(array, ci);
                });

                checkISE(() -> {
                    int x = (int) vh.getAcquire(array, ci);
                });

                checkISE(() -> {
                    int x = (int) vh.getOpaque(array, ci);
                });

                checkISE(() -> {
                    vh.setVolatile(array, ci, VALUE_1);
                });

                checkISE(() -> {
                    vh.setRelease(array, ci, VALUE_1);
                });

                checkISE(() -> {
                    vh.setOpaque(array, ci, VALUE_1);
                });

                checkISE(() -> {
                    boolean r = vh.compareAndSet(array, ci, VALUE_1, VALUE_2);
                });

                checkISE(() -> {
                    int r = (int) vh.compareAndExchangeVolatile(array, ci, VALUE_2, VALUE_1);
                });

                checkISE(() -> {
                    int r = (int) vh.compareAndExchangeAcquire(array, ci, VALUE_2, VALUE_1);
                });

                checkISE(() -> {
                    int r = (int) vh.compareAndExchangeRelease(array, ci, VALUE_2, VALUE_1);
                });

                checkISE(() -> {
                    boolean r = vh.weakCompareAndSet(array, ci, VALUE_1, VALUE_2);
                });

                checkISE(() -> {
                    boolean r = vh.weakCompareAndSetVolatile(array, ci, VALUE_1, VALUE_2);
                });

                checkISE(() -> {
                    boolean r = vh.weakCompareAndSetAcquire(array, ci, VALUE_1, VALUE_2);
                });

                checkISE(() -> {
                    boolean r = vh.weakCompareAndSetRelease(array, ci, VALUE_1, VALUE_2);
                });

                checkISE(() -> {
                    int o = (int) vh.getAndSet(array, ci, VALUE_1);
                });

                checkISE(() -> {
                    int o = (int) vh.getAndAdd(array, ci, VALUE_1);
                });

                checkISE(() -> {
                    int o = (int) vh.addAndGet(array, ci, VALUE_1);
                });

            }
        }
    }

    static void testArrayMisalignedAccess(ByteBufferSource bs, VarHandleSource vhs) throws Throwable {
        VarHandle vh = vhs.s;
        ByteBuffer array = bs.s;

        boolean readOnly = MemoryMode.READ_ONLY.isSet(bs.memoryModes);
        int misalignmentAtZero = array.alignmentOffset(0, SIZE);

        int length = array.limit() - SIZE + 1;
        for (int i = 0; i < length; i++) {
            boolean iAligned = ((i + misalignmentAtZero) & (SIZE - 1)) == 0;
            final int ci = i;

            if (!iAligned) {
                checkISE(() -> {
                    int x = (int) vh.getVolatile(array, ci);
                });

                checkISE(() -> {
                    int x = (int) vh.getAcquire(array, ci);
                });

                checkISE(() -> {
                    int x = (int) vh.getOpaque(array, ci);
                });

                if (!readOnly) {
                    checkISE(() -> {
                        vh.setVolatile(array, ci, VALUE_1);
                    });

                    checkISE(() -> {
                        vh.setRelease(array, ci, VALUE_1);
                    });

                    checkISE(() -> {
                        vh.setOpaque(array, ci, VALUE_1);
                    });

                    checkISE(() -> {
                        boolean r = vh.compareAndSet(array, ci, VALUE_1, VALUE_2);
                    });

                    checkISE(() -> {
                        int r = (int) vh.compareAndExchangeVolatile(array, ci, VALUE_2, VALUE_1);
                    });

                    checkISE(() -> {
                        int r = (int) vh.compareAndExchangeAcquire(array, ci, VALUE_2, VALUE_1);
                    });

                    checkISE(() -> {
                        int r = (int) vh.compareAndExchangeRelease(array, ci, VALUE_2, VALUE_1);
                    });

                    checkISE(() -> {
                        boolean r = vh.weakCompareAndSet(array, ci, VALUE_1, VALUE_2);
                    });

                    checkISE(() -> {
                        boolean r = vh.weakCompareAndSetVolatile(array, ci, VALUE_1, VALUE_2);
                    });

                    checkISE(() -> {
                        boolean r = vh.weakCompareAndSetAcquire(array, ci, VALUE_1, VALUE_2);
                    });

                    checkISE(() -> {
                        boolean r = vh.weakCompareAndSetRelease(array, ci, VALUE_1, VALUE_2);
                    });

                    checkISE(() -> {
                        int o = (int) vh.getAndSet(array, ci, VALUE_1);
                    });

                    checkISE(() -> {
                        int o = (int) vh.getAndAdd(array, ci, VALUE_1);
                    });

                    checkISE(() -> {
                        int o = (int) vh.addAndGet(array, ci, VALUE_1);
                    });
                }
            }
        }
    }

    static void testArrayReadWrite(ByteArraySource bs, VarHandleSource vhs) {
        VarHandle vh = vhs.s;
        byte[] array = bs.s;

        int misalignmentAtZero = ByteBuffer.wrap(array).alignmentOffset(0, SIZE);

        bs.fill((byte) 0xff);
        int length = array.length - SIZE + 1;
        for (int i = 0; i < length; i++) {
            boolean iAligned = ((i + misalignmentAtZero) & (SIZE - 1)) == 0;

            // Plain
            {
                vh.set(array, i, VALUE_1);
                int x = (int) vh.get(array, i);
                assertEquals(x, VALUE_1, "get int value");
            }


            if (iAligned) {
                // Volatile
                {
                    vh.setVolatile(array, i, VALUE_2);
                    int x = (int) vh.getVolatile(array, i);
                    assertEquals(x, VALUE_2, "setVolatile int value");
                }

                // Lazy
                {
                    vh.setRelease(array, i, VALUE_1);
                    int x = (int) vh.getAcquire(array, i);
                    assertEquals(x, VALUE_1, "setRelease int value");
                }

                // Opaque
                {
                    vh.setOpaque(array, i, VALUE_2);
                    int x = (int) vh.getOpaque(array, i);
                    assertEquals(x, VALUE_2, "setOpaque int value");
                }

                vh.set(array, i, VALUE_1);

                // Compare
                {
                    boolean r = vh.compareAndSet(array, i, VALUE_1, VALUE_2);
                    assertEquals(r, true, "success compareAndSet int");
                    int x = (int) vh.get(array, i);
                    assertEquals(x, VALUE_2, "success compareAndSet int value");
                }

                {
                    boolean r = vh.compareAndSet(array, i, VALUE_1, VALUE_3);
                    assertEquals(r, false, "failing compareAndSet int");
                    int x = (int) vh.get(array, i);
                    assertEquals(x, VALUE_2, "failing compareAndSet int value");
                }

                {
                    int r = (int) vh.compareAndExchangeVolatile(array, i, VALUE_2, VALUE_1);
                    assertEquals(r, VALUE_2, "success compareAndExchangeVolatile int");
                    int x = (int) vh.get(array, i);
                    assertEquals(x, VALUE_1, "success compareAndExchangeVolatile int value");
                }

                {
                    int r = (int) vh.compareAndExchangeVolatile(array, i, VALUE_2, VALUE_3);
                    assertEquals(r, VALUE_1, "failing compareAndExchangeVolatile int");
                    int x = (int) vh.get(array, i);
                    assertEquals(x, VALUE_1, "failing compareAndExchangeVolatile int value");
                }

                {
                    int r = (int) vh.compareAndExchangeAcquire(array, i, VALUE_1, VALUE_2);
                    assertEquals(r, VALUE_1, "success compareAndExchangeAcquire int");
                    int x = (int) vh.get(array, i);
                    assertEquals(x, VALUE_2, "success compareAndExchangeAcquire int value");
                }

                {
                    int r = (int) vh.compareAndExchangeAcquire(array, i, VALUE_1, VALUE_3);
                    assertEquals(r, VALUE_2, "failing compareAndExchangeAcquire int");
                    int x = (int) vh.get(array, i);
                    assertEquals(x, VALUE_2, "failing compareAndExchangeAcquire int value");
                }

                {
                    int r = (int) vh.compareAndExchangeRelease(array, i, VALUE_2, VALUE_1);
                    assertEquals(r, VALUE_2, "success compareAndExchangeRelease int");
                    int x = (int) vh.get(array, i);
                    assertEquals(x, VALUE_1, "success compareAndExchangeRelease int value");
                }

                {
                    int r = (int) vh.compareAndExchangeRelease(array, i, VALUE_2, VALUE_3);
                    assertEquals(r, VALUE_1, "failing compareAndExchangeRelease int");
                    int x = (int) vh.get(array, i);
                    assertEquals(x, VALUE_1, "failing compareAndExchangeRelease int value");
                }

                {
                    boolean r = vh.weakCompareAndSet(array, i, VALUE_1, VALUE_2);
                    assertEquals(r, true, "weakCompareAndSet int");
                    int x = (int) vh.get(array, i);
                    assertEquals(x, VALUE_2, "weakCompareAndSet int value");
                }

                {
                    boolean r = vh.weakCompareAndSetAcquire(array, i, VALUE_2, VALUE_1);
                    assertEquals(r, true, "weakCompareAndSetAcquire int");
                    int x = (int) vh.get(array, i);
                    assertEquals(x, VALUE_1, "weakCompareAndSetAcquire int");
                }

                {
                    boolean r = vh.weakCompareAndSetRelease(array, i, VALUE_1, VALUE_2);
                    assertEquals(r, true, "weakCompareAndSetRelease int");
                    int x = (int) vh.get(array, i);
                    assertEquals(x, VALUE_2, "weakCompareAndSetRelease int");
                }

                {
                    boolean r = vh.weakCompareAndSetVolatile(array, i, VALUE_2, VALUE_1);
                    assertEquals(r, true, "weakCompareAndSetVolatile int");
                    int x = (int) vh.get(array, i);
                    assertEquals(x, VALUE_1, "weakCompareAndSetVolatile int value");
                }

                // Compare set and get
                {
                    int o = (int) vh.getAndSet(array, i, VALUE_2);
                    assertEquals(o, VALUE_1, "getAndSet int");
                    int x = (int) vh.get(array, i);
                    assertEquals(x, VALUE_2, "getAndSet int value");
                }

                vh.set(array, i, VALUE_1);

                // get and add, add and get
                {
                    int o = (int) vh.getAndAdd(array, i, VALUE_3);
                    assertEquals(o, VALUE_1, "getAndAdd int");
                    int c = (int) vh.addAndGet(array, i, VALUE_3);
                    assertEquals(c, VALUE_1 + VALUE_3 + VALUE_3, "getAndAdd int value");
                }
            }
        }
    }


    static void testArrayReadWrite(ByteBufferSource bs, VarHandleSource vhs) {
        VarHandle vh = vhs.s;
        ByteBuffer array = bs.s;

        int misalignmentAtZero = array.alignmentOffset(0, SIZE);

        bs.fill((byte) 0xff);
        int length = array.limit() - SIZE + 1;
        for (int i = 0; i < length; i++) {
            boolean iAligned = ((i + misalignmentAtZero) & (SIZE - 1)) == 0;

            // Plain
            {
                vh.set(array, i, VALUE_1);
                int x = (int) vh.get(array, i);
                assertEquals(x, VALUE_1, "get int value");
            }

            if (iAligned) {
                // Volatile
                {
                    vh.setVolatile(array, i, VALUE_2);
                    int x = (int) vh.getVolatile(array, i);
                    assertEquals(x, VALUE_2, "setVolatile int value");
                }

                // Lazy
                {
                    vh.setRelease(array, i, VALUE_1);
                    int x = (int) vh.getAcquire(array, i);
                    assertEquals(x, VALUE_1, "setRelease int value");
                }

                // Opaque
                {
                    vh.setOpaque(array, i, VALUE_2);
                    int x = (int) vh.getOpaque(array, i);
                    assertEquals(x, VALUE_2, "setOpaque int value");
                }

                vh.set(array, i, VALUE_1);

                // Compare
                {
                    boolean r = vh.compareAndSet(array, i, VALUE_1, VALUE_2);
                    assertEquals(r, true, "success compareAndSet int");
                    int x = (int) vh.get(array, i);
                    assertEquals(x, VALUE_2, "success compareAndSet int value");
                }

                {
                    boolean r = vh.compareAndSet(array, i, VALUE_1, VALUE_3);
                    assertEquals(r, false, "failing compareAndSet int");
                    int x = (int) vh.get(array, i);
                    assertEquals(x, VALUE_2, "failing compareAndSet int value");
                }

                {
                    int r = (int) vh.compareAndExchangeVolatile(array, i, VALUE_2, VALUE_1);
                    assertEquals(r, VALUE_2, "success compareAndExchangeVolatile int");
                    int x = (int) vh.get(array, i);
                    assertEquals(x, VALUE_1, "success compareAndExchangeVolatile int value");
                }

                {
                    int r = (int) vh.compareAndExchangeVolatile(array, i, VALUE_2, VALUE_3);
                    assertEquals(r, VALUE_1, "failing compareAndExchangeVolatile int");
                    int x = (int) vh.get(array, i);
                    assertEquals(x, VALUE_1, "failing compareAndExchangeVolatile int value");
                }

                {
                    int r = (int) vh.compareAndExchangeAcquire(array, i, VALUE_1, VALUE_2);
                    assertEquals(r, VALUE_1, "success compareAndExchangeAcquire int");
                    int x = (int) vh.get(array, i);
                    assertEquals(x, VALUE_2, "success compareAndExchangeAcquire int value");
                }

                {
                    int r = (int) vh.compareAndExchangeAcquire(array, i, VALUE_1, VALUE_3);
                    assertEquals(r, VALUE_2, "failing compareAndExchangeAcquire int");
                    int x = (int) vh.get(array, i);
                    assertEquals(x, VALUE_2, "failing compareAndExchangeAcquire int value");
                }

                {
                    int r = (int) vh.compareAndExchangeRelease(array, i, VALUE_2, VALUE_1);
                    assertEquals(r, VALUE_2, "success compareAndExchangeRelease int");
                    int x = (int) vh.get(array, i);
                    assertEquals(x, VALUE_1, "success compareAndExchangeRelease int value");
                }

                {
                    int r = (int) vh.compareAndExchangeRelease(array, i, VALUE_2, VALUE_3);
                    assertEquals(r, VALUE_1, "failing compareAndExchangeRelease int");
                    int x = (int) vh.get(array, i);
                    assertEquals(x, VALUE_1, "failing compareAndExchangeRelease int value");
                }

                {
                    boolean r = vh.weakCompareAndSet(array, i, VALUE_1, VALUE_2);
                    assertEquals(r, true, "weakCompareAndSet int");
                    int x = (int) vh.get(array, i);
                    assertEquals(x, VALUE_2, "weakCompareAndSet int value");
                }

                {
                    boolean r = vh.weakCompareAndSetAcquire(array, i, VALUE_2, VALUE_1);
                    assertEquals(r, true, "weakCompareAndSetAcquire int");
                    int x = (int) vh.get(array, i);
                    assertEquals(x, VALUE_1, "weakCompareAndSetAcquire int");
                }

                {
                    boolean r = vh.weakCompareAndSetRelease(array, i, VALUE_1, VALUE_2);
                    assertEquals(r, true, "weakCompareAndSetRelease int");
                    int x = (int) vh.get(array, i);
                    assertEquals(x, VALUE_2, "weakCompareAndSetRelease int");
                }

                {
                    boolean r = vh.weakCompareAndSetVolatile(array, i, VALUE_2, VALUE_1);
                    assertEquals(r, true, "weakCompareAndSetVolatile int");
                    int x = (int) vh.get(array, i);
                    assertEquals(x, VALUE_1, "weakCompareAndSetVolatile int value");
                }

                // Compare set and get
                {
                    int o = (int) vh.getAndSet(array, i, VALUE_2);
                    assertEquals(o, VALUE_1, "getAndSet int");
                    int x = (int) vh.get(array, i);
                    assertEquals(x, VALUE_2, "getAndSet int value");
                }

                vh.set(array, i, VALUE_1);

                // get and add, add and get
                {
                    int o = (int) vh.getAndAdd(array, i, VALUE_3);
                    assertEquals(o, VALUE_1, "getAndAdd int");
                    int c = (int) vh.addAndGet(array, i, VALUE_3);
                    assertEquals(c, VALUE_1 + VALUE_3 + VALUE_3, "getAndAdd int value");
                }
            }
        }
    }

    static void testArrayReadOnly(ByteBufferSource bs, VarHandleSource vhs) {
        VarHandle vh = vhs.s;
        ByteBuffer array = bs.s;

        int misalignmentAtZero = array.alignmentOffset(0, SIZE);

        ByteBuffer bb = ByteBuffer.allocate(SIZE);
        bb.order(MemoryMode.BIG_ENDIAN.isSet(vhs.memoryModes) ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
        bs.fill(bb.putInt(0, VALUE_2).array());

        int length = array.limit() - SIZE + 1;
        for (int i = 0; i < length; i++) {
            boolean iAligned = ((i + misalignmentAtZero) & (SIZE - 1)) == 0;

            int v = MemoryMode.BIG_ENDIAN.isSet(vhs.memoryModes)
                    ? rotateLeft(VALUE_2, (i % SIZE) << 3)
                    : rotateRight(VALUE_2, (i % SIZE) << 3);
            // Plain
            {
                int x = (int) vh.get(array, i);
                assertEquals(x, v, "get int value");
            }

            if (iAligned) {
                // Volatile
                {
                    int x = (int) vh.getVolatile(array, i);
                    assertEquals(x, v, "getVolatile int value");
                }

                // Lazy
                {
                    int x = (int) vh.getAcquire(array, i);
                    assertEquals(x, v, "getRelease int value");
                }

                // Opaque
                {
                    int x = (int) vh.getOpaque(array, i);
                    assertEquals(x, v, "getOpaque int value");
                }
            }
        }
    }

}

