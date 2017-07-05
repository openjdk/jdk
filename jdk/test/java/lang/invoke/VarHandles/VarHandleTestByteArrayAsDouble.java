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
 * @run testng/othervm -Diters=20000 -XX:TieredStopAtLevel=1 VarHandleTestByteArrayAsDouble
 * @run testng/othervm -Diters=20000                         VarHandleTestByteArrayAsDouble
 * @run testng/othervm -Diters=20000 -XX:-TieredCompilation  VarHandleTestByteArrayAsDouble
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

public class VarHandleTestByteArrayAsDouble extends VarHandleBaseByteArrayTest {
    static final int SIZE = Double.BYTES;

    static final double VALUE_1 = 0x0102030405060708L;

    static final double VALUE_2 = 0x1112131415161718L;

    static final double VALUE_3 = 0x2122232425262728L;


    @Override
    public void setupVarHandleSources() {
        // Combinations of VarHandle byte[] or ByteBuffer
        vhss = new ArrayList<>();
        for (MemoryMode endianess : Arrays.asList(MemoryMode.BIG_ENDIAN, MemoryMode.LITTLE_ENDIAN)) {
            VarHandleSource aeh = new VarHandleSource(
                    MethodHandles.byteArrayViewVarHandle(double[].class,
                                                         endianess == MemoryMode.BIG_ENDIAN),
                    endianess, MemoryMode.READ_WRITE);
            vhss.add(aeh);

            VarHandleSource bbh = new VarHandleSource(
                    MethodHandles.byteBufferViewVarHandle(double[].class,
                                                          endianess == MemoryMode.BIG_ENDIAN),
                    endianess, MemoryMode.READ_WRITE);
            vhss.add(bbh);
        }
    }


    @Test(dataProvider = "varHandlesProvider")
    public void testIsAccessModeSupported(VarHandleSource vhs) {
        VarHandle vh = vhs.s;

        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.get));
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.set));

        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.getVolatile));
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.setVolatile));
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.getAcquire));
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.setRelease));
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.getOpaque));
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.setOpaque));

        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.compareAndSet));
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.compareAndExchangeVolatile));
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.compareAndExchangeAcquire));
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.compareAndExchangeRelease));
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.weakCompareAndSet));
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.weakCompareAndSetAcquire));
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.weakCompareAndSetRelease));
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.weakCompareAndSetRelease));
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.getAndSet));

        assertFalse(vh.isAccessModeSupported(VarHandle.AccessMode.getAndAdd));
        assertFalse(vh.isAccessModeSupported(VarHandle.AccessMode.addAndGet));
    }

    @Test(dataProvider = "typesProvider")
    public void testTypes(VarHandle vh, List<java.lang.Class<?>> pts) {
        assertEquals(vh.varType(), double.class);

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


        checkUOE(() -> {
            double o = (double) vh.getAndAdd(array, ci, VALUE_1);
        });

        checkUOE(() -> {
            double o = (double) vh.addAndGet(array, ci, VALUE_1);
        });
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
                double r = (double) vh.compareAndExchangeVolatile(array, ci, VALUE_2, VALUE_1);
            });

            checkROBE(() -> {
                double r = (double) vh.compareAndExchangeAcquire(array, ci, VALUE_2, VALUE_1);
            });

            checkROBE(() -> {
                double r = (double) vh.compareAndExchangeRelease(array, ci, VALUE_2, VALUE_1);
            });

            checkROBE(() -> {
                boolean r = vh.weakCompareAndSet(array, ci, VALUE_1, VALUE_2);
            });

            checkROBE(() -> {
                boolean r = vh.weakCompareAndSetAcquire(array, ci, VALUE_1, VALUE_2);
            });

            checkROBE(() -> {
                boolean r = vh.weakCompareAndSetRelease(array, ci, VALUE_1, VALUE_2);
            });

            checkROBE(() -> {
                double o = (double) vh.getAndSet(array, ci, VALUE_1);
            });
            checkUOE(() -> {
                boolean r = vh.compareAndSet(array, ci, VALUE_1, VALUE_2);
            });

            checkUOE(() -> {
                double o = (double) vh.getAndAdd(array, ci, VALUE_1);
            });

            checkUOE(() -> {
                double o = (double) vh.addAndGet(array, ci, VALUE_1);
            });
        }
        else {
            checkUOE(() -> {
                double o = (double) vh.getAndAdd(array, ci, VALUE_1);
            });

            checkUOE(() -> {
                double o = (double) vh.addAndGet(array, ci, VALUE_1);
            });
        }
    }


    static void testArrayIndexOutOfBounds(ByteArraySource bs, VarHandleSource vhs) throws Throwable {
        VarHandle vh = vhs.s;
        byte[] array = bs.s;

        int length = array.length - SIZE + 1;
        for (int i : new int[]{-1, Integer.MIN_VALUE, length, length + 1, Integer.MAX_VALUE}) {
            final int ci = i;

            checkIOOBE(() -> {
                double x = (double) vh.get(array, ci);
            });

            checkIOOBE(() -> {
                vh.set(array, ci, VALUE_1);
            });

            checkIOOBE(() -> {
                double x = (double) vh.getVolatile(array, ci);
            });

            checkIOOBE(() -> {
                double x = (double) vh.getAcquire(array, ci);
            });

            checkIOOBE(() -> {
                double x = (double) vh.getOpaque(array, ci);
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
                double r = (double) vh.compareAndExchangeVolatile(array, ci, VALUE_2, VALUE_1);
            });

            checkIOOBE(() -> {
                double r = (double) vh.compareAndExchangeAcquire(array, ci, VALUE_2, VALUE_1);
            });

            checkIOOBE(() -> {
                double r = (double) vh.compareAndExchangeRelease(array, ci, VALUE_2, VALUE_1);
            });

            checkIOOBE(() -> {
                boolean r = vh.weakCompareAndSet(array, ci, VALUE_1, VALUE_2);
            });

            checkIOOBE(() -> {
                boolean r = vh.weakCompareAndSetAcquire(array, ci, VALUE_1, VALUE_2);
            });

            checkIOOBE(() -> {
                boolean r = vh.weakCompareAndSetRelease(array, ci, VALUE_1, VALUE_2);
            });

            checkIOOBE(() -> {
                double o = (double) vh.getAndSet(array, ci, VALUE_1);
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
                double x = (double) vh.get(array, ci);
            });

            if (!readOnly) {
                checkIOOBE(() -> {
                    vh.set(array, ci, VALUE_1);
                });
            }

            checkIOOBE(() -> {
                double x = (double) vh.getVolatile(array, ci);
            });

            checkIOOBE(() -> {
                double x = (double) vh.getAcquire(array, ci);
            });

            checkIOOBE(() -> {
                double x = (double) vh.getOpaque(array, ci);
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
                    double r = (double) vh.compareAndExchangeVolatile(array, ci, VALUE_2, VALUE_1);
                });

                checkIOOBE(() -> {
                    double r = (double) vh.compareAndExchangeAcquire(array, ci, VALUE_2, VALUE_1);
                });

                checkIOOBE(() -> {
                    double r = (double) vh.compareAndExchangeRelease(array, ci, VALUE_2, VALUE_1);
                });

                checkIOOBE(() -> {
                    boolean r = vh.weakCompareAndSet(array, ci, VALUE_1, VALUE_2);
                });

                checkIOOBE(() -> {
                    boolean r = vh.weakCompareAndSetAcquire(array, ci, VALUE_1, VALUE_2);
                });

                checkIOOBE(() -> {
                    boolean r = vh.weakCompareAndSetRelease(array, ci, VALUE_1, VALUE_2);
                });

                checkIOOBE(() -> {
                    double o = (double) vh.getAndSet(array, ci, VALUE_1);
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
                    double x = (double) vh.getVolatile(array, ci);
                });

                checkISE(() -> {
                    double x = (double) vh.getAcquire(array, ci);
                });

                checkISE(() -> {
                    double x = (double) vh.getOpaque(array, ci);
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
                    double r = (double) vh.compareAndExchangeVolatile(array, ci, VALUE_2, VALUE_1);
                });

                checkISE(() -> {
                    double r = (double) vh.compareAndExchangeAcquire(array, ci, VALUE_2, VALUE_1);
                });

                checkISE(() -> {
                    double r = (double) vh.compareAndExchangeRelease(array, ci, VALUE_2, VALUE_1);
                });

                checkISE(() -> {
                    boolean r = vh.weakCompareAndSet(array, ci, VALUE_1, VALUE_2);
                });

                checkISE(() -> {
                    boolean r = vh.weakCompareAndSetAcquire(array, ci, VALUE_1, VALUE_2);
                });

                checkISE(() -> {
                    boolean r = vh.weakCompareAndSetRelease(array, ci, VALUE_1, VALUE_2);
                });

                checkISE(() -> {
                    double o = (double) vh.getAndSet(array, ci, VALUE_1);
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
                    double x = (double) vh.getVolatile(array, ci);
                });

                checkISE(() -> {
                    double x = (double) vh.getAcquire(array, ci);
                });

                checkISE(() -> {
                    double x = (double) vh.getOpaque(array, ci);
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
                        double r = (double) vh.compareAndExchangeVolatile(array, ci, VALUE_2, VALUE_1);
                    });

                    checkISE(() -> {
                        double r = (double) vh.compareAndExchangeAcquire(array, ci, VALUE_2, VALUE_1);
                    });

                    checkISE(() -> {
                        double r = (double) vh.compareAndExchangeRelease(array, ci, VALUE_2, VALUE_1);
                    });

                    checkISE(() -> {
                        boolean r = vh.weakCompareAndSet(array, ci, VALUE_1, VALUE_2);
                    });

                    checkISE(() -> {
                        boolean r = vh.weakCompareAndSetAcquire(array, ci, VALUE_1, VALUE_2);
                    });

                    checkISE(() -> {
                        boolean r = vh.weakCompareAndSetRelease(array, ci, VALUE_1, VALUE_2);
                    });

                    checkISE(() -> {
                        double o = (double) vh.getAndSet(array, ci, VALUE_1);
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
                double x = (double) vh.get(array, i);
                assertEquals(x, VALUE_1, "get double value");
            }


            if (iAligned) {
                // Volatile
                {
                    vh.setVolatile(array, i, VALUE_2);
                    double x = (double) vh.getVolatile(array, i);
                    assertEquals(x, VALUE_2, "setVolatile double value");
                }

                // Lazy
                {
                    vh.setRelease(array, i, VALUE_1);
                    double x = (double) vh.getAcquire(array, i);
                    assertEquals(x, VALUE_1, "setRelease double value");
                }

                // Opaque
                {
                    vh.setOpaque(array, i, VALUE_2);
                    double x = (double) vh.getOpaque(array, i);
                    assertEquals(x, VALUE_2, "setOpaque double value");
                }

                vh.set(array, i, VALUE_1);

                // Compare
                {
                    boolean r = vh.compareAndSet(array, i, VALUE_1, VALUE_2);
                    assertEquals(r, true, "success compareAndSet double");
                    double x = (double) vh.get(array, i);
                    assertEquals(x, VALUE_2, "success compareAndSet double value");
                }

                {
                    boolean r = vh.compareAndSet(array, i, VALUE_1, VALUE_3);
                    assertEquals(r, false, "failing compareAndSet double");
                    double x = (double) vh.get(array, i);
                    assertEquals(x, VALUE_2, "failing compareAndSet double value");
                }

                {
                    double r = (double) vh.compareAndExchangeVolatile(array, i, VALUE_2, VALUE_1);
                    assertEquals(r, VALUE_2, "success compareAndExchangeVolatile double");
                    double x = (double) vh.get(array, i);
                    assertEquals(x, VALUE_1, "success compareAndExchangeVolatile double value");
                }

                {
                    double r = (double) vh.compareAndExchangeVolatile(array, i, VALUE_2, VALUE_3);
                    assertEquals(r, VALUE_1, "failing compareAndExchangeVolatile double");
                    double x = (double) vh.get(array, i);
                    assertEquals(x, VALUE_1, "failing compareAndExchangeVolatile double value");
                }

                {
                    double r = (double) vh.compareAndExchangeAcquire(array, i, VALUE_1, VALUE_2);
                    assertEquals(r, VALUE_1, "success compareAndExchangeAcquire double");
                    double x = (double) vh.get(array, i);
                    assertEquals(x, VALUE_2, "success compareAndExchangeAcquire double value");
                }

                {
                    double r = (double) vh.compareAndExchangeAcquire(array, i, VALUE_1, VALUE_3);
                    assertEquals(r, VALUE_2, "failing compareAndExchangeAcquire double");
                    double x = (double) vh.get(array, i);
                    assertEquals(x, VALUE_2, "failing compareAndExchangeAcquire double value");
                }

                {
                    double r = (double) vh.compareAndExchangeRelease(array, i, VALUE_2, VALUE_1);
                    assertEquals(r, VALUE_2, "success compareAndExchangeRelease double");
                    double x = (double) vh.get(array, i);
                    assertEquals(x, VALUE_1, "success compareAndExchangeRelease double value");
                }

                {
                    double r = (double) vh.compareAndExchangeRelease(array, i, VALUE_2, VALUE_3);
                    assertEquals(r, VALUE_1, "failing compareAndExchangeRelease double");
                    double x = (double) vh.get(array, i);
                    assertEquals(x, VALUE_1, "failing compareAndExchangeRelease double value");
                }

                {
                    boolean r = vh.weakCompareAndSet(array, i, VALUE_1, VALUE_2);
                    assertEquals(r, true, "weakCompareAndSet double");
                    double x = (double) vh.get(array, i);
                    assertEquals(x, VALUE_2, "weakCompareAndSet double value");
                }

                {
                    boolean r = vh.weakCompareAndSetAcquire(array, i, VALUE_2, VALUE_1);
                    assertEquals(r, true, "weakCompareAndSetAcquire double");
                    double x = (double) vh.get(array, i);
                    assertEquals(x, VALUE_1, "weakCompareAndSetAcquire double");
                }

                {
                    boolean r = vh.weakCompareAndSetRelease(array, i, VALUE_1, VALUE_2);
                    assertEquals(r, true, "weakCompareAndSetRelease double");
                    double x = (double) vh.get(array, i);
                    assertEquals(x, VALUE_2, "weakCompareAndSetRelease double");
                }

                // Compare set and get
                {
                    double o = (double) vh.getAndSet(array, i, VALUE_1);
                    assertEquals(o, VALUE_2, "getAndSet double");
                    double x = (double) vh.get(array, i);
                    assertEquals(x, VALUE_1, "getAndSet double value");
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
                double x = (double) vh.get(array, i);
                assertEquals(x, VALUE_1, "get double value");
            }

            if (iAligned) {
                // Volatile
                {
                    vh.setVolatile(array, i, VALUE_2);
                    double x = (double) vh.getVolatile(array, i);
                    assertEquals(x, VALUE_2, "setVolatile double value");
                }

                // Lazy
                {
                    vh.setRelease(array, i, VALUE_1);
                    double x = (double) vh.getAcquire(array, i);
                    assertEquals(x, VALUE_1, "setRelease double value");
                }

                // Opaque
                {
                    vh.setOpaque(array, i, VALUE_2);
                    double x = (double) vh.getOpaque(array, i);
                    assertEquals(x, VALUE_2, "setOpaque double value");
                }

                vh.set(array, i, VALUE_1);

                // Compare
                {
                    boolean r = vh.compareAndSet(array, i, VALUE_1, VALUE_2);
                    assertEquals(r, true, "success compareAndSet double");
                    double x = (double) vh.get(array, i);
                    assertEquals(x, VALUE_2, "success compareAndSet double value");
                }

                {
                    boolean r = vh.compareAndSet(array, i, VALUE_1, VALUE_3);
                    assertEquals(r, false, "failing compareAndSet double");
                    double x = (double) vh.get(array, i);
                    assertEquals(x, VALUE_2, "failing compareAndSet double value");
                }

                {
                    double r = (double) vh.compareAndExchangeVolatile(array, i, VALUE_2, VALUE_1);
                    assertEquals(r, VALUE_2, "success compareAndExchangeVolatile double");
                    double x = (double) vh.get(array, i);
                    assertEquals(x, VALUE_1, "success compareAndExchangeVolatile double value");
                }

                {
                    double r = (double) vh.compareAndExchangeVolatile(array, i, VALUE_2, VALUE_3);
                    assertEquals(r, VALUE_1, "failing compareAndExchangeVolatile double");
                    double x = (double) vh.get(array, i);
                    assertEquals(x, VALUE_1, "failing compareAndExchangeVolatile double value");
                }

                {
                    double r = (double) vh.compareAndExchangeAcquire(array, i, VALUE_1, VALUE_2);
                    assertEquals(r, VALUE_1, "success compareAndExchangeAcquire double");
                    double x = (double) vh.get(array, i);
                    assertEquals(x, VALUE_2, "success compareAndExchangeAcquire double value");
                }

                {
                    double r = (double) vh.compareAndExchangeAcquire(array, i, VALUE_1, VALUE_3);
                    assertEquals(r, VALUE_2, "failing compareAndExchangeAcquire double");
                    double x = (double) vh.get(array, i);
                    assertEquals(x, VALUE_2, "failing compareAndExchangeAcquire double value");
                }

                {
                    double r = (double) vh.compareAndExchangeRelease(array, i, VALUE_2, VALUE_1);
                    assertEquals(r, VALUE_2, "success compareAndExchangeRelease double");
                    double x = (double) vh.get(array, i);
                    assertEquals(x, VALUE_1, "success compareAndExchangeRelease double value");
                }

                {
                    double r = (double) vh.compareAndExchangeRelease(array, i, VALUE_2, VALUE_3);
                    assertEquals(r, VALUE_1, "failing compareAndExchangeRelease double");
                    double x = (double) vh.get(array, i);
                    assertEquals(x, VALUE_1, "failing compareAndExchangeRelease double value");
                }

                {
                    boolean r = vh.weakCompareAndSet(array, i, VALUE_1, VALUE_2);
                    assertEquals(r, true, "weakCompareAndSet double");
                    double x = (double) vh.get(array, i);
                    assertEquals(x, VALUE_2, "weakCompareAndSet double value");
                }

                {
                    boolean r = vh.weakCompareAndSetAcquire(array, i, VALUE_2, VALUE_1);
                    assertEquals(r, true, "weakCompareAndSetAcquire double");
                    double x = (double) vh.get(array, i);
                    assertEquals(x, VALUE_1, "weakCompareAndSetAcquire double");
                }

                {
                    boolean r = vh.weakCompareAndSetRelease(array, i, VALUE_1, VALUE_2);
                    assertEquals(r, true, "weakCompareAndSetRelease double");
                    double x = (double) vh.get(array, i);
                    assertEquals(x, VALUE_2, "weakCompareAndSetRelease double");
                }

                // Compare set and get
                {
                    double o = (double) vh.getAndSet(array, i, VALUE_1);
                    assertEquals(o, VALUE_2, "getAndSet double");
                    double x = (double) vh.get(array, i);
                    assertEquals(x, VALUE_1, "getAndSet double value");
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
        bs.fill(bb.putDouble(0, VALUE_2).array());

        int length = array.limit() - SIZE + 1;
        for (int i = 0; i < length; i++) {
            boolean iAligned = ((i + misalignmentAtZero) & (SIZE - 1)) == 0;

            double v = MemoryMode.BIG_ENDIAN.isSet(vhs.memoryModes)
                    ? rotateLeft(VALUE_2, (i % SIZE) << 3)
                    : rotateRight(VALUE_2, (i % SIZE) << 3);
            // Plain
            {
                double x = (double) vh.get(array, i);
                assertEquals(x, v, "get double value");
            }

            if (iAligned) {
                // Volatile
                {
                    double x = (double) vh.getVolatile(array, i);
                    assertEquals(x, v, "getVolatile double value");
                }

                // Lazy
                {
                    double x = (double) vh.getAcquire(array, i);
                    assertEquals(x, v, "getRelease double value");
                }

                // Opaque
                {
                    double x = (double) vh.getOpaque(array, i);
                    assertEquals(x, v, "getOpaque double value");
                }
            }
        }
    }

}

