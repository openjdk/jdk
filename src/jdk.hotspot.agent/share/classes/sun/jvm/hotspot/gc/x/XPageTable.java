/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

package sun.jvm.hotspot.gc.x;

import java.util.Iterator;

import sun.jvm.hotspot.debugger.Address;
import sun.jvm.hotspot.runtime.VM;
import sun.jvm.hotspot.runtime.VMObject;
import sun.jvm.hotspot.runtime.VMObjectFactory;
import sun.jvm.hotspot.types.Type;
import sun.jvm.hotspot.types.TypeDataBase;

public class XPageTable extends VMObject {
    private static long mapFieldOffset;

    static {
        VM.registerVMInitializedObserver((o, d) -> initialize(VM.getVM().getTypeDataBase()));
    }

    private static synchronized void initialize(TypeDataBase db) {
        Type type = db.lookupType("XPageTable");

        mapFieldOffset = type.getAddressField("_map").getOffset();
    }

    public XPageTable(Address addr) {
        super(addr);
    }

    private XGranuleMapForPageTable map() {
        return VMObjectFactory.newObject(XGranuleMapForPageTable.class, addr.addOffsetTo(mapFieldOffset));
    }

    private XPageTableEntry getEntry(Address o) {
        return new XPageTableEntry(map().get(o));
    }

    XPage get(Address o) {
        return VMObjectFactory.newObject(XPage.class, map().get(VM.getVM().getDebugger().newAddress(XAddress.offset(o))));
    }

    boolean is_relocating(Address o) {
        return getEntry(o).relocating();
    }

    private class XPagesIterator implements Iterator<XPage> {
        private XGranuleMapForPageTable.Iterator mapIter;
        private XPage next;

        XPagesIterator() {
            mapIter = map().new Iterator();
            positionToNext();
        }

        private XPage positionToNext() {
            XPage current = next;

            // Find next
            XPage found = null;
            while (mapIter.hasNext()) {
                XPageTableEntry entry = new XPageTableEntry(mapIter.next());
                if (!entry.isEmpty()) {
                    XPage page = entry.page();
                    // Medium pages have repeated entries for all covered slots,
                    // therefore we need to compare against the current page.
                    if (page != null && !page.equals(current)) {
                        found = page;
                        break;
                    }
                }
            }

            next = found;

            return current;
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        @Override
        public XPage next() {
            return positionToNext();
        }

        @Override
        public void remove() {
            /* not supported */
        }
    }

    abstract class XPageFilter {
        public abstract boolean accept(XPage page);
    }

    class XPagesFilteredIterator implements Iterator<XPage> {
        private XPage next;
        private XPagesIterator iter = new XPagesIterator();
        private XPageFilter filter;

        XPagesFilteredIterator(XPageFilter filter) {
            this.filter = filter;
            positionToNext();
        }

        public XPage positionToNext() {
            XPage current = next;

            // Find next
            XPage found = null;
            while (iter.hasNext()) {
                XPage page = iter.next();
                if (filter.accept(page)) {
                    found = page;
                    break;
                }
            }

            next = found;

            return current;
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        @Override
        public XPage next() {
            return positionToNext();
        }

        @Override
        public void remove() {
            /* not supported */
        }
    }

    public Iterator<XPage> iterator() {
        return new XPagesIterator();
    }

    public Iterator<XPage> activePagesIterator() {
        return new XPagesFilteredIterator(new XPageFilter() {
            public boolean accept(XPage page) {
                return page != null;
            }
        });
    }
}
