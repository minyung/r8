// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import com.android.tools.r8.ClassConflictResolver;
import com.android.tools.r8.dex.ApplicationReader.ProgramClassConflictResolver;
import com.android.tools.r8.errors.DuplicateTypesDiagnostic;
import com.android.tools.r8.graph.ClassKind;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.InternalGlobalSyntheticsProgramProvider.GlobalsEntryOrigin;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Represents a collection of library classes. */
public class ProgramClassCollection extends ClassMap<DexProgramClass> {

  private final ProgramClassConflictResolver conflictResolver;

  public static ProgramClassCollection create(
      List<DexProgramClass> classes, ProgramClassConflictResolver conflictResolver) {
    // We have all classes preloaded, but not necessarily without conflicts.
    ConcurrentHashMap<DexType, Supplier<DexProgramClass>> map = new ConcurrentHashMap<>();
    for (DexProgramClass clazz : classes) {
      map.merge(
          clazz.type, clazz, (a, b) -> conflictResolver.resolveClassConflict(a.get(), b.get()));
    }
    return new ProgramClassCollection(map, conflictResolver);
  }

  private ProgramClassCollection(
      ConcurrentHashMap<DexType, Supplier<DexProgramClass>> classes,
      ProgramClassConflictResolver conflictResolver) {
    super(classes, null);
    this.conflictResolver = conflictResolver;
  }

  @Override
  public String toString() {
    return "program classes: " + super.toString();
  }

  @Override
  DexProgramClass resolveClassConflict(DexProgramClass a, DexProgramClass b) {
    return conflictResolver.resolveClassConflict(a, b);
  }

  @Override
  Supplier<DexProgramClass> getTransparentSupplier(DexProgramClass clazz) {
    return clazz;
  }

  @Override
  ClassKind<DexProgramClass> getClassKind() {
    return ClassKind.PROGRAM;
  }

  public static ProgramClassConflictResolver defaultConflictResolver(Reporter reporter) {
    // The default conflict resolver only merges synthetic classes generated by D8 correctly.
    // All other conflicts are reported as a fatal error.
    return wrappedConflictResolver(null, reporter);
  }

  @SuppressWarnings("ReferenceEquality")
  public static ProgramClassConflictResolver wrappedConflictResolver(
      ClassConflictResolver clientResolver, Reporter reporter) {
    return (a, b) -> {
      DexProgramClass clazz = mergeClasses(a, b);
      if (clazz != null) {
        return clazz;
      }
      if (clientResolver != null) {
        List<Origin> origins = new ArrayList<>();
        origins.add(a.getOrigin());
        origins.add(b.getOrigin());
        Origin origin =
            clientResolver.resolveDuplicateClass(a.getClassReference(), origins, reporter);
        if (origin == a.getOrigin()) {
          return a;
        }
        if (origin == b.getOrigin()) {
          return b;
        }
      }
      throw reportDuplicateTypes(reporter, a, b);
    };
  }

  private static RuntimeException reportDuplicateTypes(
      Reporter reporter, DexProgramClass a, DexProgramClass b) {
    throw reporter.fatalError(
        new DuplicateTypesDiagnostic(
            Reference.classFromDescriptor(a.type.toDescriptorString()),
            ImmutableList.of(a.getOrigin(), b.getOrigin())));
  }

  @SuppressWarnings("ReferenceEquality")
  private static DexProgramClass mergeClasses(DexProgramClass a, DexProgramClass b) {
    assert a.type == b.type;
    boolean syntheticA = a.accessFlags.isSynthetic();
    boolean syntheticB = b.accessFlags.isSynthetic();
    if (syntheticA && syntheticB) {
      return mergeIfLegacySynthetics(a, b);
    } else if (syntheticA) {
      return mergeIfGlobalSynthetic(a, b);
    } else if (syntheticB) {
      return mergeIfGlobalSynthetic(b, a);
    }
    return null;
  }

  private static DexProgramClass mergeIfGlobalSynthetic(
      DexProgramClass synthetic, DexProgramClass nonSynthetic) {
    assert synthetic.accessFlags.isSynthetic();
    assert !nonSynthetic.accessFlags.isSynthetic();
    if (synthetic.getOrigin() instanceof GlobalsEntryOrigin) {
      return nonSynthetic;
    }
    return null;
  }

  private static DexProgramClass mergeIfLegacySynthetics(DexProgramClass a, DexProgramClass b) {
    if (a.type.isLegacySynthesizedTypeAllowedDuplication()) {
      assert assertEqualClasses(a, b);
      return a;
    }
    return null;
  }

  private static boolean assertEqualClasses(DexProgramClass a, DexProgramClass b) {
    assert a.getMethodCollection().numberOfDirectMethods()
        == b.getMethodCollection().numberOfDirectMethods();
    assert a.getMethodCollection().size() == b.getMethodCollection().size();
    return true;
  }
}
