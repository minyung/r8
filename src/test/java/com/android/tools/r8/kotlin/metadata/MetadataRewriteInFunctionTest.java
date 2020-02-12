// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.metadata;

import static com.android.tools.r8.KotlinCompilerTool.KOTLINC;
import static com.android.tools.r8.utils.codeinspector.Matchers.isExtensionFunction;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isRenamed;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.KotlinTargetVersion;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.KmClassSubject;
import com.android.tools.r8.utils.codeinspector.KmFunctionSubject;
import com.android.tools.r8.utils.codeinspector.KmPackageSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MetadataRewriteInFunctionTest extends KotlinMetadataTestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0} target: {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withCfRuntimes().build(), KotlinTargetVersion.values());
  }

  public MetadataRewriteInFunctionTest(
      TestParameters parameters, KotlinTargetVersion targetVersion) {
    super(targetVersion);
    this.parameters = parameters;
  }

  private static final Map<KotlinTargetVersion, Path> funLibJarMap = new HashMap<>();

  @BeforeClass
  public static void createLibJar() throws Exception {
    String funLibFolder = PKG_PREFIX + "/function_lib";
    for (KotlinTargetVersion targetVersion : KotlinTargetVersion.values()) {
      Path funLibJar =
          kotlinc(KOTLINC, targetVersion)
              .addSourceFiles(getKotlinFileInTest(funLibFolder, "B"))
              .compile();
      funLibJarMap.put(targetVersion, funLibJar);
    }
  }

  @Test
  public void testMetadataInFunction_merged() throws Exception {
    Path libJar =
        testForR8(parameters.getBackend())
            .addProgramFiles(funLibJarMap.get(targetVersion))
            // Keep the B class and its interface (which has the doStuff method).
            .addKeepRules("-keep class **.B")
            .addKeepRules("-keep class **.I { <methods>; }")
            // Keep the BKt method, which will be called from other kotlin code.
            .addKeepRules("-keep class **.BKt { <methods>; }")
            .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
            .compile()
            .inspect(this::inspectMerged)
            .writeToZip();

    Path output =
        kotlinc(parameters.getRuntime().asCf(), KOTLINC, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(getKotlinFileInTest(PKG_PREFIX + "/function_app", "main"))
            .setOutputPath(temp.newFolder().toPath())
            .compile();

    testForJvm()
        .addRunClasspathFiles(ToolHelper.getKotlinStdlibJar(), libJar)
        .addClasspath(output)
        .run(parameters.getRuntime(), PKG + ".function_app.MainKt")
        .assertSuccessWithOutputLines("do stuff", "do stuff");
  }

  private void inspectMerged(CodeInspector inspector) {
    String superClassName = PKG + ".function_lib.Super";
    String bClassName = PKG + ".function_lib.B";
    String bKtClassName = PKG + ".function_lib.BKt";

    assertThat(inspector.clazz(superClassName), not(isPresent()));

    ClassSubject impl = inspector.clazz(bClassName);
    assertThat(impl, isPresent());
    assertThat(impl, not(isRenamed()));

    // API entry is kept, hence the presence of Metadata.
    KmClassSubject kmClass = impl.getKmClass();
    assertThat(kmClass, isPresent());
    List<ClassSubject> superTypes = kmClass.getSuperTypes();
    assertTrue(superTypes.stream().noneMatch(
        supertype -> supertype.getFinalDescriptor().contains("Super")));

    ClassSubject bKt = inspector.clazz(bKtClassName);
    assertThat(bKt, isPresent());
    assertThat(bKt, not(isRenamed()));

    // API entry is kept, hence the presence of Metadata.
    KmPackageSubject kmPackage = bKt.getKmPackage();
    assertThat(kmPackage, isPresent());

    KmFunctionSubject kmFunction = kmPackage.kmFunctionWithUniqueName("fun");
    assertThat(kmFunction, isPresent());
    assertThat(kmFunction, not(isExtensionFunction()));
  }

  @Test
  public void testMetadataInFunction_renamed() throws Exception {
    Path libJar =
        testForR8(parameters.getBackend())
            .addProgramFiles(funLibJarMap.get(targetVersion))
            // Keep the B class and its interface (which has the doStuff method).
            .addKeepRules("-keep class **.B")
            .addKeepRules("-keep class **.I { <methods>; }")
            // Keep Super, but allow minification.
            .addKeepRules("-keep,allowobfuscation class **.Super { <methods>; }")
            // Keep the BKt method, which will be called from other kotlin code.
            .addKeepRules("-keep class **.BKt { <methods>; }")
            .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
            .compile()
            .inspect(this::inspectRenamed)
            .writeToZip();

    Path output =
        kotlinc(parameters.getRuntime().asCf(), KOTLINC, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(getKotlinFileInTest(PKG_PREFIX + "/function_app", "main"))
            .setOutputPath(temp.newFolder().toPath())
            .compile();

    testForJvm()
        .addRunClasspathFiles(ToolHelper.getKotlinStdlibJar(), libJar)
        .addClasspath(output)
        .run(parameters.getRuntime(), PKG + ".function_app.MainKt")
        .assertSuccessWithOutputLines("do stuff", "do stuff");
  }

  private void inspectRenamed(CodeInspector inspector) {
    String superClassName = PKG + ".function_lib.Super";
    String bClassName = PKG + ".function_lib.B";
    String bKtClassName = PKG + ".function_lib.BKt";

    ClassSubject sup = inspector.clazz(superClassName);
    assertThat(sup, isRenamed());

    MethodSubject foo = sup.uniqueMethodWithName("foo");
    assertThat(foo, isRenamed());

    KmClassSubject kmClass = sup.getKmClass();
    assertThat(kmClass, isPresent());

    // TODO(b/70169921): would be better to look up function with the original name, "foo".
    KmFunctionSubject kmFunction = kmClass.kmFunctionWithUniqueName(foo.getFinalName());
    assertThat(kmFunction, isPresent());
    assertThat(kmFunction, not(isExtensionFunction()));
    assertEquals(foo.getJvmMethodSignatureAsString(), kmFunction.signature().asString());

    ClassSubject impl = inspector.clazz(bClassName);
    assertThat(impl, isPresent());
    assertThat(impl, not(isRenamed()));

    // API entry is kept, hence the presence of Metadata.
    kmClass = impl.getKmClass();
    assertThat(kmClass, isPresent());
    List<ClassSubject> superTypes = kmClass.getSuperTypes();
    assertTrue(superTypes.stream().noneMatch(
        supertype -> supertype.getFinalDescriptor().contains("Super")));
    assertTrue(superTypes.stream().anyMatch(
        supertype -> supertype.getFinalDescriptor().equals(sup.getFinalDescriptor())));

    ClassSubject bKt = inspector.clazz(bKtClassName);
    assertThat(bKt, isPresent());
    assertThat(bKt, not(isRenamed()));
    // API entry is kept, hence the presence of Metadata.
    KmPackageSubject kmPackage = bKt.getKmPackage();
    assertThat(kmPackage, isPresent());

    kmFunction = kmPackage.kmFunctionWithUniqueName("fun");
    assertThat(kmFunction, isPresent());
    assertThat(kmFunction, not(isExtensionFunction()));
  }
}
