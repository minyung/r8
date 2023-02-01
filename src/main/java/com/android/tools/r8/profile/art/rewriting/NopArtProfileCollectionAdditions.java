// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art.rewriting;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.profile.art.rewriting.ArtProfileAdditions.ArtProfileAdditionsBuilder;
import java.util.function.Consumer;

public class NopArtProfileCollectionAdditions extends ArtProfileCollectionAdditions {

  private static final NopArtProfileCollectionAdditions INSTANCE =
      new NopArtProfileCollectionAdditions();

  private NopArtProfileCollectionAdditions() {}

  public static NopArtProfileCollectionAdditions getInstance() {
    return INSTANCE;
  }

  @Override
  public void applyIfContextIsInProfile(
      DexMethod context, Consumer<ArtProfileAdditionsBuilder> builderConsumer) {
    // Intentionally empty.
  }

  @Override
  public void commit(AppView<?> appView) {
    // Intentionally empty.
  }

  @Override
  public boolean isNop() {
    return true;
  }
}
