// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.backports;

import static com.android.tools.r8.utils.FileUtils.JAR_EXTENSION;
import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class ObjectsBackportJava9Test extends AbstractBackportTest {
  @Parameters(name = "{0}")
  public static Iterable<?> data() {
    return getTestParameters()
        .withDexRuntimes()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK9)
        .withAllApiLevelsAlsoForCf()
        .build();
  }

  private static final Path TEST_JAR =
      Paths.get(ToolHelper.EXAMPLES_JAVA9_BUILD_DIR).resolve("backport" + JAR_EXTENSION);
  private static final String TEST_CLASS = "backport.ObjectsBackportJava9Main";

  public ObjectsBackportJava9Test(TestParameters parameters) {
    super(parameters, Objects.class, TEST_JAR, TEST_CLASS);
    // Objects.checkFromIndexSize, Objects.checkFromToIndex, Objects.checkIndex,
    // Objects.requireNonNullElse and Objects.requireNonNullElseGet added in API 30.
    registerTarget(AndroidApiLevel.R, 28);
    registerTarget(AndroidApiLevel.N, 0);
    // Objects.requireNonNullElseGet is not desugared if Supplier is absent.
    registerTarget(AndroidApiLevel.B, 4);
  }

  @Test
  public void desugaringApiLevelR() throws Exception {
    // TODO(b/154759404): This test should start to fail when testing on an Android R VM.
    // This has now been checked with S, when R testing is added chck and remove this.
    if (parameters.getRuntime().isDex() && parameters.getApiLevel().isEqualTo(AndroidApiLevel.R)) {
      testForD8()
          .setMinApi(AndroidApiLevel.R)
          .addProgramClasses(MiniAssert.class, IgnoreInvokes.class)
          .addProgramFiles(TEST_JAR)
          .setIncludeClassesChecksum(true)
          .compile()
          .run(parameters.getRuntime(), TEST_CLASS)
          .assertFailureWithErrorThatMatches(containsString("java.lang.NoSuchMethodError"));
    }
  }
}
