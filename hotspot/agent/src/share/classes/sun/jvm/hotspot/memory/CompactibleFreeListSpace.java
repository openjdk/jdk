/*
 * Copyright 2003-2007 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

package sun.jvm.hotspot.memory;

import java.io.*;
import java.util.*;
import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.oops.*;
import sun.jvm.hotspot.runtime.*;
import sun.jvm.hotspot.types.*;
import sun.jvm.hotspot.utilities.*;

public class CompactibleFreeListSpace extends CompactibleSpace {
   private static AddressField collectorField;

   static {
      VM.registerVMInitializedObserver(new Observer() {
         public void update(Observable o, Object data) {
            initialize(VM.getVM().getTypeDataBase());
         }
      });
   }

   private static synchronized void initialize(TypeDataBase db) {
      long sizeofFreeChunk = db.lookupType("FreeChunk").getSize();
      VM vm = VM.getVM();
      MinChunkSizeInBytes = numQuanta(sizeofFreeChunk, vm.getMinObjAlignmentInBytes()) *
                     vm.getMinObjAlignmentInBytes();

     Type type = db.lookupType("CompactibleFreeListSpace");
     collectorField = type.getAddressField("_collector");
   }

   public CompactibleFreeListSpace(Address addr) {
      super(addr);
   }

   // Accessing block offset table
   public CMSCollector collector() {
    return (CMSCollector) VMObjectFactory.newObject(
                                 CMSCollector.class,
                                 collectorField.getValue(addr));
  }

   public long used() {
      List regions = getLiveRegions();
      long usedSize = 0L;
      for (Iterator itr = regions.iterator(); itr.hasNext();) {
         MemRegion mr = (MemRegion) itr.next();
         usedSize += mr.byteSize();
      }
      return usedSize;
   }

   public long free() {
      return capacity() - used();
   }

   public void printOn(PrintStream tty) {
      tty.print("free-list-space");
   }

   public Address skipBlockSizeUsingPrintezisBits(Address pos) {
       CMSCollector collector = collector();
       long size = 0;
       Address addr = null;

       if (collector != null) {
         size = collector.blockSizeUsingPrintezisBits(pos);
         if (size >= 3) {
           addr = pos.addOffsetTo(adjustObjectSizeInBytes(size));
         }
       }
       return addr;
   }

   public List/*<MemRegion>*/ getLiveRegions() {
      List res = new ArrayList(); // List<MemRegion>
      VM vm = VM.getVM();
      Debugger dbg = vm.getDebugger();
      ObjectHeap heap = vm.getObjectHeap();
      Address cur = bottom();
      Address regionStart = cur;
      Address limit = end();
      final long addressSize = vm.getAddressSize();

      for (; cur.lessThan(limit);) {
         Address klassOop = cur.getAddressAt(addressSize);
         // FIXME: need to do a better job here.
         // can I use bitMap here?
         if (klassOop == null) {
            //Find the object size using Printezis bits and skip over
            System.err.println("Finding object size using Printezis bits and skipping over...");
            long size = collector().blockSizeUsingPrintezisBits(cur);
            if (size == -1) {
              System.err.println("Printezis bits not set...");
              break;
            }
            cur = cur.addOffsetTo(adjustObjectSizeInBytes(size));
         }

         if (FreeChunk.indicatesFreeChunk(cur)) {
            if (! cur.equals(regionStart)) {
               res.add(new MemRegion(regionStart, cur));
            }
            FreeChunk fc = (FreeChunk) VMObjectFactory.newObject(FreeChunk.class, cur);
            long chunkSize = fc.size();
            if (Assert.ASSERTS_ENABLED) {
               Assert.that(chunkSize > 0, "invalid FreeChunk size");
            }
            // note that fc.size() gives chunk size in heap words
            cur = cur.addOffsetTo(chunkSize * addressSize);
            System.err.println("Free chunk in CMS heap, size="+chunkSize * addressSize);
            regionStart = cur;
         } else if (klassOop != null) {
            Oop obj = heap.newOop(cur.addOffsetToAsOopHandle(0));
            long objectSize = obj.getObjectSize();
            cur = cur.addOffsetTo(adjustObjectSizeInBytes(objectSize));
         }
      }
      return res;
   }

   //-- Internals only below this point

   // Unlike corresponding VM code, we operate on byte size rather than
   // HeapWord size for convenience.

   private static long numQuanta(long x, long y) {
      return  ((x+y-1)/y);
   }

   public static long adjustObjectSizeInBytes(long sizeInBytes) {
      return Oop.alignObjectSize(Math.max(sizeInBytes, MinChunkSizeInBytes));
   }

   // FIXME: should I read this directly from VM?
   private static long MinChunkSizeInBytes;
}
