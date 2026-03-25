/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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

package runtime.valhalla.inlinetypes.field_layout;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import javax.management.RuntimeErrorException;

import jdk.test.lib.Asserts;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class FieldLayoutAnalyzer {

  // Mutable wrapper around log output to manage the cursor while parsing
  public static class LogOutput {
    List<String> lines;
    int cursor;

    public LogOutput(List<String> lines) {
      this.lines = lines;
      cursor = 0;
    }

    String getCurrentLine() { return lines.get(cursor); }
    String get(int idx) { return lines.get(idx); }
    int size() { return lines.size(); }
    void moveToNextLine() { cursor = cursor + 1; }
    boolean hasMoreLines() { return cursor < lines.size(); }
  }

  static enum BlockType {
    RESERVED,
    INHERITED,
    EMPTY,
    REGULAR,
    PADDING,
    FLAT,
    NULL_MARKER;

    static BlockType parseType(String s) {
      switch(s) {
        case "RESERVED"    : return RESERVED;
        case "INHERITED"   : return INHERITED;
        case "EMPTY"       : return EMPTY;
        case "REGULAR"     : return REGULAR;
        case "PADDING"     : return PADDING;
        case "FLAT"        : return FLAT;
        case "NULL_MARKER" : return NULL_MARKER;
        default:
          throw new RuntimeException("Unknown block type: " + s);
      }
    }
  }

  public static enum LayoutKind {
    NON_FLAT(false),
    NULL_FREE_NON_ATOMIC_FLAT(false),
    NULL_FREE_ATOMIC_FLAT(false),
    NULLABLE_ATOMIC_FLAT(true),
    NULLABLE_NON_ATOMIC_FLAT(true);

    private final boolean hasNullMarker;

    private LayoutKind(boolean hasNullMarker) {
      this.hasNullMarker = hasNullMarker;
    }

    static LayoutKind parseLayoutKind(String s) {
      switch(s) {
        case ""                          : return NON_FLAT;
        case "NULL_FREE_NON_ATOMIC_FLAT" : return NULL_FREE_NON_ATOMIC_FLAT;
        case "NULL_FREE_ATOMIC_FLAT"     : return NULL_FREE_ATOMIC_FLAT;
        case "NULLABLE_ATOMIC_FLAT"      : return NULLABLE_ATOMIC_FLAT;
        case "NULLABLE_NON_ATOMIC_FLAT"  : return NULLABLE_NON_ATOMIC_FLAT;
        default:
          throw new RuntimeException("Unknown layout kind: " + s);
      }
    }

    boolean hasNullMarker() {
      return this.hasNullMarker;
    }
  }

  static public record FieldBlock (int offset,
                            BlockType type,
                            int size,
                            int alignment,
                            String name,
                            String signature,
                            String fieldClass,
                            LayoutKind layoutKind) {

    static FieldBlock createSpecialBlock(int offset, BlockType type, int size, int alignment) {
      return new FieldBlock(offset, type, size, alignment, null, null, null, LayoutKind.NON_FLAT);
    }

    static FieldBlock createJavaFieldBlock(int offset, BlockType type, int size, int alignment, String name, String signature, String fieldClass, LayoutKind layoutKind) {
      return new FieldBlock(offset, type, size, alignment, name, signature, fieldClass, layoutKind);
    }

    void print(PrintStream out) {
      out.println("Offset=" + offset+
                " type=" + type +
                " size=" + size +
                " alignment=" + alignment +
                " name=" + name +
                " signature=" + signature +
                " fieldClass=" + fieldClass);
    }

    boolean isFlat() { return type == BlockType.FLAT; } // Warning: always return false for inherited fields, even flat ones
    boolean hasNullMarker() { return layoutKind.hasNullMarker(); }

    static FieldBlock parseField(String line) {
      String[] fieldLine = line.split("\\s+");
      int offset = Integer.parseInt(fieldLine[1].substring(1, fieldLine[1].length()));
      BlockType type = BlockType.parseType(fieldLine[2]);
      String[] size_align = fieldLine[3].split("/");
      int size = Integer.parseInt(size_align[0]);
      int alignment = -1;
      if (type != BlockType.RESERVED) {
        alignment = Integer.parseInt(size_align[1]);
      } else {
        Asserts.assertTrue(size_align[1].equals("-"));
      }
      FieldBlock block = null;
      switch(type) {
        case BlockType.RESERVED:
        case BlockType.EMPTY:
        case BlockType.PADDING: {
            block = FieldBlock.createSpecialBlock(offset, type, size, alignment);
            break;
        }
        case BlockType.REGULAR:
        case BlockType.INHERITED:
        case BlockType.FLAT: {
            String name = fieldLine[4];
            String signature = fieldLine[5];
            String fieldClass = "";
            String layoutKind = "";
            int nullMarkerOffset = -1;
            if (type == BlockType.FLAT) {
              fieldClass = fieldLine[6];
              layoutKind = fieldLine[7];
            }
            block = FieldBlock.createJavaFieldBlock(offset, type, size, alignment, name, signature, fieldClass, LayoutKind.parseLayoutKind(layoutKind));
            break;
          }
        case BlockType.NULL_MARKER: {
          block = FieldBlock.createSpecialBlock(offset, type, size, alignment);
          break;
        }
      }
      Asserts.assertNotNull(block);
      return block;
    }

  }

  public static class ClassLayout {
    String name;
    String superName;
    boolean isValue;
    int instanceSize;
    int payloadSize;
    int payloadAlignment;
    int firstFieldOffset;
    int nullFreeNonAtomicLayoutSize;         // -1 if no null-free non-atomic layout
    int nullFreeNonAtomicLayoutAlignment;    // -1 if no null-free non-atomic layout
    int nullFreeAtomicLayoutSize;            // -1 if no null-free atomic layout
    int nullFreeAtomicLayoutAlignment;       // -1 if no null-free atomic layout
    int nullableAtomicLayoutSize;            // -1 if no nullable atomic layout
    int nullableAtomicLayoutAlignment;       // -1 if no nullable atomic layout
    int nullableNonAtomicLayoutSize;         // -1 if no nullable non-atomic layout
    int nullableNonAtomicLayoutAlignment;    // -1 if no nullable non-atomic layout
    int nullMarkerOffset;                    // -1 if no nullable layout
    String[] lines;
    ArrayList<FieldBlock> staticFields;
    ArrayList<FieldBlock> nonStaticFields;

    private ClassLayout() {
      staticFields = new ArrayList<FieldBlock>();
      nonStaticFields = new ArrayList<FieldBlock>();
    }

    boolean hasNullFreeNonAtomicLayout() { return nullFreeNonAtomicLayoutSize != -1; }
    boolean hasNullFreeAtomicLayout() { return nullFreeAtomicLayoutSize != -1; }
    boolean hasNullableAtomicLayout() { return nullableAtomicLayoutSize != -1; }
    boolean hasNullableNonAtomicLayout() { return nullableNonAtomicLayoutSize != -1; }
    boolean hasNullMarker() {return nullMarkerOffset != -1; }

    int getSize(LayoutKind layoutKind) {
      switch(layoutKind) {
        case NON_FLAT:
          throw new RuntimeException("Should not be called on non-flat fields");
        case NULL_FREE_NON_ATOMIC_FLAT:
          Asserts.assertTrue(nullFreeNonAtomicLayoutSize != -1);
          return nullFreeNonAtomicLayoutSize;
        case NULL_FREE_ATOMIC_FLAT:
          Asserts.assertTrue(nullFreeAtomicLayoutSize != -1);
          return nullFreeAtomicLayoutSize;
        case NULLABLE_ATOMIC_FLAT:
          Asserts.assertTrue(nullableAtomicLayoutSize != -1);
          return nullableAtomicLayoutSize;
        case NULLABLE_NON_ATOMIC_FLAT:
          Asserts.assertTrue(nullableNonAtomicLayoutSize != -1);
          return nullableNonAtomicLayoutSize;
        default:
          throw new RuntimeException("Unknown LayoutKind " + layoutKind);
      }
    }

    int getAlignment(LayoutKind layoutKind) {
      switch(layoutKind) {
        case NON_FLAT:
          throw new RuntimeException("Should not be called on non-flat fields");
        case NULL_FREE_NON_ATOMIC_FLAT:
          Asserts.assertTrue(nullFreeNonAtomicLayoutSize != -1);
          return nullFreeNonAtomicLayoutAlignment;
        case NULL_FREE_ATOMIC_FLAT:
          Asserts.assertTrue(nullFreeAtomicLayoutSize != -1);
          return nullFreeAtomicLayoutAlignment;
        case NULLABLE_ATOMIC_FLAT:
          Asserts.assertTrue(nullableAtomicLayoutSize != -1);
          return nullableAtomicLayoutAlignment;
        case NULLABLE_NON_ATOMIC_FLAT:
          Asserts.assertTrue(nullableNonAtomicLayoutSize != -1);
          return nullableNonAtomicLayoutAlignment;
        default:
          throw new RuntimeException("Unknown LayoutKind " + layoutKind);
      }
    }

    void processField(String line, boolean isStatic) {
      FieldBlock block = FieldBlock.parseField(line);
      if (isStatic) {
        Asserts.assertTrue(block.type != BlockType.INHERITED); // static fields cannot be inherited
        staticFields.add(block);
      } else {
        nonStaticFields.add(block);
      }
    }

    static ClassLayout parseClassLayout(LogOutput lo) {
      ClassLayout cl = new ClassLayout();
      // Parsing class name
      Asserts.assertTrue(lo.getCurrentLine().startsWith("Layout of class"), lo.getCurrentLine());
      String[] first = lo.getCurrentLine().split("\\s+");
      cl.name = first[3];
      if (first.length == 6) {
        Asserts.assertEquals(first[4], "extends");
        cl.superName = first[5];
      } else {
        cl.superName = null;
      }
      // System.out.println("Class name: " + cl.name);
      lo.moveToNextLine();
      Asserts.assertTrue(lo.getCurrentLine().startsWith("Instance fields:"), lo.getCurrentLine());
      lo.moveToNextLine();
      // Parsing instance fields
      while (lo.getCurrentLine().startsWith(" @")) {
        cl.processField(lo.getCurrentLine(), false);
        lo.moveToNextLine();
      }
      Asserts.assertTrue(lo.getCurrentLine().startsWith("Static fields:"), lo.getCurrentLine());
      lo.moveToNextLine();
      // Parsing static fields
      while (lo.getCurrentLine().startsWith(" @")) {
        cl.processField(lo.getCurrentLine(), true);
        lo.moveToNextLine();
      }
      Asserts.assertTrue(lo.getCurrentLine().startsWith("Instance size ="), lo.getCurrentLine());
      String[] sizeLine = lo.getCurrentLine().split("\\s+");
      cl.instanceSize = Integer.parseInt(sizeLine[3]);
      lo.moveToNextLine();
      if (lo.getCurrentLine().startsWith("First field offset =")) {
        // The class is a value class, more lines to parse
        cl.isValue = true;
        // First field offset = xx
        String[] firstOffsetLine = lo.getCurrentLine().split("\\s+");
        cl.firstFieldOffset = Integer.parseInt(firstOffsetLine[4]);
        lo.moveToNextLine();
        // BUFFERED layout: x/y
        Asserts.assertTrue(lo.getCurrentLine().startsWith("BUFFERED layout"));
        String[] payloadLayoutLine = lo.getCurrentLine().split("\\s+");
        String[] size_align = payloadLayoutLine[2].split("/");
        cl.payloadSize = Integer.parseInt(size_align[0]);
        cl.payloadAlignment = Integer.parseInt(size_align[1]);
        lo.moveToNextLine();
        // NULL_FREE_NON_ATOMIC_FLAT layout: x/y
        Asserts.assertTrue(lo.getCurrentLine().startsWith("NULL_FREE_NON_ATOMIC_FLAT layout"));
        String[] nonAtomicLayoutLine = lo.getCurrentLine().split("\\s+");
        size_align = nonAtomicLayoutLine[2].split("/");
        if (size_align[0].contentEquals("-")) {
          Asserts.assertTrue(size_align[1].contentEquals("-"), "Size/Alignment mismatch");
          cl.nullFreeNonAtomicLayoutSize = -1;
          cl.nullFreeNonAtomicLayoutAlignment = -1;
        } else {
          cl.nullFreeNonAtomicLayoutSize = Integer.parseInt(size_align[0]);
          cl.nullFreeNonAtomicLayoutAlignment = Integer.parseInt(size_align[1]);
        }
        lo.moveToNextLine();
        // NULL_FREE_ATOMIC_FLAT layout: x/y
        Asserts.assertTrue(lo.getCurrentLine().startsWith("NULL_FREE_ATOMIC_FLAT layout"));
        String[] atomicLayoutLine = lo.getCurrentLine().split("\\s+");
        size_align = atomicLayoutLine[2].split("/");
        if (size_align[0].contentEquals("-")) {
          Asserts.assertTrue(size_align[1].contentEquals("-"), "Size/Alignment mismatch");
          cl.nullFreeAtomicLayoutSize = -1;
          cl.nullFreeAtomicLayoutAlignment = -1;
        } else {
          cl.nullFreeAtomicLayoutSize = Integer.parseInt(size_align[0]);
          cl.nullFreeAtomicLayoutAlignment = Integer.parseInt(size_align[1]);
        }
        lo.moveToNextLine();
        // Nullable atomic flat layout: x/y
        Asserts.assertTrue(lo.getCurrentLine().startsWith("NULLABLE_ATOMIC_FLAT layout"));
        String[] nullableAtomicLayoutLine = lo.getCurrentLine().split("\\s+");
        size_align = nullableAtomicLayoutLine[2].split("/");
        if (size_align[0].contentEquals("-")) {
          Asserts.assertTrue(size_align[1].contentEquals("-"), "Size/Alignment mismatch");
          cl.nullableAtomicLayoutSize = -1;
          cl.nullableAtomicLayoutAlignment = -1;
        } else {
          cl.nullableAtomicLayoutSize = Integer.parseInt(size_align[0]);
          cl.nullableAtomicLayoutAlignment = Integer.parseInt(size_align[1]);
        }
        lo.moveToNextLine();
        // Nullable non-atomic flat layout: x/y
        Asserts.assertTrue(lo.getCurrentLine().startsWith("NULLABLE_NON_ATOMIC_FLAT layout"));
        String[] nullableNonAtomicLayoutLine = lo.getCurrentLine().split("\\s+");
        size_align = nullableNonAtomicLayoutLine[2].split("/");
        if (size_align[0].contentEquals("-")) {
          Asserts.assertTrue(size_align[1].contentEquals("-"), "Size/Alignment mismatch");
          cl.nullableNonAtomicLayoutSize = -1;
          cl.nullableNonAtomicLayoutAlignment = -1;
        } else {
          cl.nullableNonAtomicLayoutSize = Integer.parseInt(size_align[0]);
          cl.nullableNonAtomicLayoutAlignment = Integer.parseInt(size_align[1]);
        }
        lo.moveToNextLine();
        // Null marker offset = 15 (if class has a nullable flat layout)
        if (cl.nullableAtomicLayoutSize != -1 || cl.nullableNonAtomicLayoutSize != -1) {
          Asserts.assertTrue(lo.getCurrentLine().startsWith("Null marker offset"));
          String[] nullMarkerLine = lo.getCurrentLine().split("\\s+");
          cl.nullMarkerOffset = Integer.parseInt(nullMarkerLine[4]);
          lo.moveToNextLine();
        } else {
          cl.nullMarkerOffset = -1;
        }
        Asserts.assertTrue(lo.getCurrentLine().startsWith("Non-oop acmp map"), lo.getCurrentLine());
        lo.moveToNextLine();
        Asserts.assertTrue(lo.getCurrentLine().startsWith("oop acmp map"), lo.getCurrentLine());
        lo.moveToNextLine();
      } else {
        cl.isValue = false;
      }
      Asserts.assertTrue(lo.getCurrentLine().startsWith("---"), lo.getCurrentLine());
      lo.moveToNextLine();
      return cl;
    }

    FieldBlock getFieldAtOffset(int offset, boolean isStatic) {
      ArrayList<FieldBlock> fields = isStatic ? staticFields : nonStaticFields;
      for (FieldBlock block : fields) {
        if (block.offset == offset) return block;
      }
      throw new RuntimeException("No " + (isStatic ? "static" : "nonstatic") + " field found at offset "+ offset);
    }

    public FieldBlock getFieldFromName(String fieldName, boolean isStatic) {
      FieldBlock block = getFieldFromNameOrNull(fieldName, isStatic);
      if (block == null) {
        throw new RuntimeException("No " + (isStatic ? "static" : "nonstatic") + " field found with name "+ fieldName);
      }
      return block;
    }

    FieldBlock getFieldFromNameOrNull(String fieldName, boolean isStatic) {
      Asserts.assertTrue(fieldName != null);
      ArrayList<FieldBlock> fields = isStatic ? staticFields : nonStaticFields;
      for (FieldBlock block : fields) {
        if (block.name() == null) continue;
        String n = block.name().substring(1, block.name().length() - 1); // in the log, name is surrounded by double quotes
        if (fieldName.equals(n)) return block;
      }
      return null;
    }

  }

  ArrayList<ClassLayout> layouts;
  int oopSize;

  static String signatureToName(String sig) {
    Asserts.assertTrue((sig.charAt(0) == 'L'));
    Asserts.assertTrue((sig.charAt(sig.length() - 1) == ';'));
    return sig.substring(1, sig.length() - 1);
  }

  private FieldLayoutAnalyzer() {
    layouts = new ArrayList<ClassLayout>();
  }

  public static FieldLayoutAnalyzer createFieldLayoutAnalyzer(LogOutput lo) {
    FieldLayoutAnalyzer fla = new FieldLayoutAnalyzer();
    fla.generate(lo);
    return fla;
  }

  ClassLayout getClassLayout(String name) {
    for(ClassLayout layout : layouts) {
      if (layout.name.equals(name)) return layout;
    }
    return null;
  }

  public ClassLayout getClassLayoutFromName(String name) {
    System.out.println("We have the layouts");
    for(ClassLayout layout : layouts) {
      System.out.println("- " + layout.name);
      String sub = layout.name.substring(0, layout.name.indexOf('@'));
      System.out.println("-----> " + sub);
      if (name.equals(sub)) return layout;
    }
    return null;
  }

  void checkOffsetOnFields(ArrayList<FieldBlock> fields) {
    HashMap<Integer, FieldBlock> map = new HashMap<Integer, FieldBlock>();
    for (FieldBlock fb : fields) {
      Asserts.assertFalse(map.containsKey(fb.offset()), "Duplicate offset at " + fb.offset());
      map.put(fb.offset(), fb);
    }
  }

  void checkOffsets() {
    for (ClassLayout layout : layouts) {
      try {
        checkOffsetOnFields(layout.staticFields);
        checkOffsetOnFields(layout.nonStaticFields);
      } catch(Throwable t) {
        System.out.println("Unexpection exception when checking offsets in class " + layout.name);
        throw t;
      }
    }
  }

  void checkNoOverlapOnFields(ArrayList<FieldBlock> fields) {
    for (int i = 0; i < fields.size() - 1; i++) {
      FieldBlock f0 = fields.get(i);
      FieldBlock f1 = fields.get(i + 1);
      if (f0.offset + f0.size < f1.offset) {
        throw new RuntimeException("Hole issue found at offset " + f1.offset);
      } else if (f0.offset + f0.size > f1.offset) {
        throw new RuntimeException("Overlap issue found at offset " + f1.offset);
      }
    }
  }

  void checkNoOverlap() {
    for (ClassLayout layout : layouts) {
      try {
        checkNoOverlapOnFields(layout.staticFields);
        checkNoOverlapOnFields(layout.nonStaticFields);
      } catch(Throwable t) {
        System.out.println("Unexpected exception when checking for overlaps/holes in class " + layout.name);
        throw t;
      }
    }
  }

  void checkSizeAndAlignmentForField(FieldBlock block, ClassLayout layout) {
    Asserts.assertTrue(block.size() > 0);
    if (block.type == BlockType.RESERVED) {
      Asserts.assertTrue(block.alignment == -1);
      return;
    }
    if (block.type == BlockType.EMPTY || block.type == BlockType.PADDING
        || block.type == BlockType.NULL_MARKER) {
      Asserts.assertTrue(block.alignment == 1, "alignment = " + block.alignment);
      return;
    }

    switch(block.signature()) {
      case "Z" :
      case "B" : Asserts.assertTrue(block.size() == 1);
                 Asserts.assertTrue(block.alignment() == 1);
        break;
      case "S" :
      case "C" : Asserts.assertTrue(block.size() == 2);
                 Asserts.assertTrue(block.alignment() == 2);
        break;
      case "F" :
      case "I" : Asserts.assertTrue(block.size() == 4);
                 Asserts.assertTrue(block.alignment() == 4);
        break;
      case "J" :
      case "D" : Asserts.assertTrue(block.size() == 8);
                 Asserts.assertTrue(block.alignment() == 8);
        break;
      default: {
        if (block.signature().startsWith("[")) {
          Asserts.assertEquals(oopSize, block.size());
        } else if (block.signature().startsWith("L")) {
          if (block.type == BlockType.INHERITED) {
            // Skip for now, will be verified when checking the class declaring the field
          } else if (block.type == BlockType.REGULAR) {
            Asserts.assertEquals(oopSize, block.size());
          } else {
            Asserts.assertEquals(BlockType.FLAT, block.type);
            ClassLayout fcl = getClassLayout(block.fieldClass);
            Asserts.assertNotNull(fcl);
            Asserts.assertEquals(block.size(), fcl.getSize(block.layoutKind));
            Asserts.assertEquals(block.alignment(), fcl.getAlignment(block.layoutKind));
          }
        } else {
          throw new RuntimeException("Unknown signature type: " + block.signature);
        }
      }
      Asserts.assertTrue(block.offset % block.alignment == 0, "Alignment issue found at offset " + block.offset
                                                              + " of class " + layout.name
                                                              + " expected alignment: " + block.alignment);
    }
  }

  void checkSizeAndAlignment() {
    for (ClassLayout layout : layouts) {
      try {
        for (FieldBlock block : layout.staticFields) {
          checkSizeAndAlignmentForField(block, layout);
        }
      } catch(Throwable t) {
        System.out.println("Unexpected exception when checking size and alignment in static fields of class " + layout.name);
        throw t;
      }
      try {
        for (FieldBlock block : layout.nonStaticFields) {
          checkSizeAndAlignmentForField(block, layout);
        }
      } catch(Throwable t) {
        System.out.println("Unexpected exception when checking size and alignment in non-static fields of class " + layout.name);
        throw t;
      }
    }
  }

  // Verify that fields marked as INHERITED are declared in a super class
  void checkInheritedFields() {
    for (ClassLayout layout : layouts) {
      try {
        // Preparing the list of ClassLayout of super classes
        ArrayList<ClassLayout> supers = new ArrayList<ClassLayout>();
        String className = layout.superName;
        while (className != null) {
          ClassLayout cl = getClassLayout(className);
          supers.add(cl);
          className = cl.superName;
        }
        for (FieldBlock field : layout.nonStaticFields) {
          if (field.type == BlockType.INHERITED) {
            int i = 0;
            boolean found = false;
            FieldBlock b = null;
            while(i < supers.size() && !found) {
              b = supers.get(i).getFieldAtOffset(field.offset, false);
              if (b.type != BlockType.INHERITED) found = true;
              i++;
            }
            String location = new String(" at " + layout.name + " offset " + field.offset());
            Asserts.assertTrue(found, "No declaration found for an inherited field " + location);
            Asserts.assertNotEquals(field.type, BlockType.EMPTY, location);
            Asserts.assertEquals(field.size, b.size, location);
            Asserts.assertEquals(field.alignment, b.alignment, location );
            Asserts.assertEquals(field.name(), b.name(), location);
            Asserts.assertEquals(field.signature(), b.signature(), location);
          }
        }
      } catch(Throwable t) {
        System.out.println("Unexpected exception when checking inherited fields in class " + layout.name);
      }
    }
  }

  static class Node {
    ClassLayout classLayout;
    Node superClass;
    ArrayList<Node> subClasses = new ArrayList<Node>();
  }

  // Verify that all fields declared in a class are present in all subclass
  void checkSubClasses() {
    // Generating the class inheritance graph
    HashMap<String, Node> nodes = new HashMap<String, Node>();
    for (ClassLayout layout : layouts) {
      try {
        if (layout.name.contains("$$Lambda@0")) continue; // Skipping lambda classes
        Node current = nodes.get(layout.name);
        if (current == null) {
          current = new Node();
          nodes.put(layout.name, current);
        }
        if (current.classLayout == null) {
          current.classLayout = layout;
        } else {
          Asserts.assertEQ(current.classLayout, layout);
        }
        if (layout.superName != null) {
          Node superNode = nodes.get(layout.superName);
          if (superNode == null) {
            superNode = new Node();
            superNode.subClasses.add(current);
            nodes.put(layout.superName, superNode);
          }
          superNode.subClasses.add(current);
        }
      } catch(Throwable t) {
        System.out.println("Unexpected exception when generating list of sub-classes of class " + layout.name);
        throw t;
      }
    }
    // Field verification
    for (Node node : nodes.values()) {
      ClassLayout layout = node.classLayout;
      for (FieldBlock block : layout.nonStaticFields) {
        if (block.offset() == 0) continue; // Skip object header
        if (block.type() == BlockType.EMPTY) continue; // Empty spaces can be used by subclasses
        if (block.type() == BlockType.PADDING) continue; // PADDING should have a finer inspection, preserved for @Contended and other imperative padding, and relaxed for abstract value conservative padding
        if (block.type() == BlockType.NULL_MARKER) continue;
        // A special case for PADDING might be needed too => must NOT be used in subclasses
        for (Node subnode : node.subClasses) {
          try {
            checkFieldInClass(block, subnode);
          } catch(Throwable t) {
            System.out.println("Unexpected exception when checking subclass " + subnode.classLayout.name + " of class " + layout.name);
            throw t;
          }
        }
      }
    }
  }

  void checkFieldInClass(FieldBlock block, Node node) {
    FieldBlock b = node.classLayout.getFieldAtOffset(block.offset, false);
    Asserts.assertTrue(b.type == BlockType.INHERITED);
    Asserts.assertEquals(b.signature(), block.signature());
    Asserts.assertEquals(b.name(), block.name());
    Asserts.assertEquals(b.size(), block.size());
    Asserts.assertTrue(b.alignment() == block.alignment());
    for (Node subnode : node.subClasses) {
      checkFieldInClass(block, subnode);
    }
  }

  void checkNullMarkers() {
    for (ClassLayout layout : layouts) {
      try {
        BlockType last_type = BlockType.RESERVED;
        boolean has_empty_slot = false;
        for (FieldBlock block : layout.nonStaticFields) {
          last_type = block.type;
          if (block.type() == BlockType.NULL_MARKER) {
            Asserts.assertTrue(layout.hasNullMarker());
            Asserts.assertTrue(layout.hasNullableAtomicLayout() || layout.hasNullableNonAtomicLayout());
            Asserts.assertEQ(block.offset(), layout.nullMarkerOffset);
          }
          if (block.type() == BlockType.EMPTY && block.offset() >= layout.firstFieldOffset) has_empty_slot = true;
        }
        // null marker should not be added at the end of the layout if there's an empty slot
        Asserts.assertTrue(last_type != BlockType.NULL_MARKER || has_empty_slot == false,
                          "Problem detected in layout of class " + layout.name);
        // static layout => must not have NULL_MARKERS because static fields are never flat
        for (FieldBlock block : layout.staticFields) {
          Asserts.assertNotEquals(block.type(), BlockType.NULL_MARKER);
          Asserts.assertNotEquals(block.type(), BlockType.FLAT);
        }
      } catch(Throwable t) {
        System.out.println("Unexpected exception while checking null markers in class " + layout.name);
        throw t;
      }
    }
  }

  void checkBufferedLayout() {
    // The BUFFERED layout is supposed to be compatible with all other layouts,
    // which means its size must be equal or bigger than all other supported layouts
    // and its alignment must the alignment of the most constraining supported layout.
    for (ClassLayout layout : layouts) {
      try {
        if (layout.hasNullFreeNonAtomicLayout()) {
          Asserts.assertTrue(layout.payloadSize >= layout.nullFreeNonAtomicLayoutSize);
          Asserts.assertTrue(layout.payloadAlignment >= layout.nullFreeNonAtomicLayoutAlignment);
        }
        if (layout.hasNullFreeAtomicLayout()) {
          Asserts.assertTrue(layout.payloadSize >= layout.nullFreeAtomicLayoutSize);
          Asserts.assertTrue(layout.payloadAlignment >= layout.nullFreeAtomicLayoutAlignment);
        }
        if (layout.hasNullableAtomicLayout()) {
          Asserts.assertTrue(layout.payloadSize >= layout.nullableAtomicLayoutSize);
          Asserts.assertTrue(layout.payloadAlignment >= layout.nullableAtomicLayoutAlignment);
        }
        if (layout.hasNullableNonAtomicLayout()) {
          Asserts.assertTrue(layout.payloadSize >= layout.nullableNonAtomicLayoutSize);
          Asserts.assertTrue(layout.payloadAlignment >= layout.nullableNonAtomicLayoutAlignment);
        }
      } catch (Throwable t) {
        System.out.println("Exception while processing class " + layout.name);
        throw t;
      }
    }
  }

  public void check() {
    checkOffsets();
    checkNoOverlap();
    checkSizeAndAlignment();
    checkInheritedFields();
    checkSubClasses();
    checkNullMarkers();
    checkBufferedLayout();
  }

  private void generate(LogOutput lo) {
    try {
      while (lo.hasMoreLines()) {
        if (lo.getCurrentLine().startsWith("Heap oop size = ")) {
          String[] oopSizeLine = lo.getCurrentLine().split("\\s+");
          oopSize = Integer.parseInt(oopSizeLine[4]);
          Asserts.assertTrue(oopSize == 4 || oopSize == 8);
        }
        if (lo.getCurrentLine().startsWith("Layout of class")) {
          ClassLayout cl = ClassLayout.parseClassLayout(lo);
          layouts.add(cl);
        } else {
          lo.moveToNextLine(); // skipping line
        }
      }
      Asserts.assertTrue(oopSize != 0);
    } catch (Throwable t) {
      System.out.println("Error while processing line: " + lo.getCurrentLine());
      throw t;
    }
  }
 }
