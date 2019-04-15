// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.reflection;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.JvmTestRunResult;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.errors.InternalCompilerError;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InnerClassNameTestRunner extends TestBase {

  private static final Class<?> MAIN_CLASS = InnerClassNameTest.class;
  private static final String PACKAGE = "com/android/tools/r8/ir/optimize/reflection/";

  public enum TestNamingConfig {
    DEFAULT,
    DOLLAR2_SEPARATOR,
    EMTPY_SEPARATOR,
    UNDERBAR_SEPARATOR,
    NON_NESTED_INNER,
    OUTER_ENDS_WITH_DOLLAR,
    $_$_$;

    public String getOuterTypeRaw() {
      switch (this) {
        case DEFAULT:
        case DOLLAR2_SEPARATOR:
        case EMTPY_SEPARATOR:
        case UNDERBAR_SEPARATOR:
        case NON_NESTED_INNER:
          return "OuterClass";
        case OUTER_ENDS_WITH_DOLLAR:
          return "OuterClass$";
        case $_$_$:
          return "$";
      }
      throw new Unreachable();
    }

    public String getSeparator() {
      switch (this) {
        case DEFAULT:
        case OUTER_ENDS_WITH_DOLLAR:
        case $_$_$:
          return "$";
        case DOLLAR2_SEPARATOR:
          return "$$";
        case UNDERBAR_SEPARATOR:
          return "_";
        case NON_NESTED_INNER:
        case EMTPY_SEPARATOR:
          return "";
      }
      throw new Unreachable();
    }

    public String getMinifiedSeparator() {
      switch (this) {
        case DEFAULT:
        case OUTER_ENDS_WITH_DOLLAR:
        case $_$_$:
        case UNDERBAR_SEPARATOR:
        case NON_NESTED_INNER:
        case EMTPY_SEPARATOR:
          return "$";
        case DOLLAR2_SEPARATOR:
          return "$$";
      }
      throw new Unreachable();
    }

    public String getInnerClassName() {
      return this == $_$_$ ? "$" : "InnerClass";
    }

    public String getInnerTypeRaw() {
      switch (this) {
        case DEFAULT:
        case OUTER_ENDS_WITH_DOLLAR:
        case DOLLAR2_SEPARATOR:
        case EMTPY_SEPARATOR:
        case UNDERBAR_SEPARATOR:
        case $_$_$:
          return getOuterTypeRaw() + getSeparator() + getInnerClassName();
        case NON_NESTED_INNER:
          return getInnerClassName();
      }
      throw new Unreachable();
    }

    public String getInnerInternalType() {
      return PACKAGE + getInnerTypeRaw();
    }

    public String getOuterInternalType() {
      return PACKAGE + getOuterTypeRaw();
    }

    public String getInnerDescriptor() {
      return "L" + getInnerInternalType() + ";";
    }

    public String getOuterDescriptor() {
      return "L" + getOuterInternalType() + ";";
    }

    public static boolean isGetTypeNameSupported(TestParameters parameters) {
      return parameters.getRuntime().isCf()
          || parameters.getRuntime().asDex().getVm().isNewerThan(DexVm.ART_7_0_0_HOST);
    }
  }

  @Parameters(name = "{0} minify:{1} {2}")
  public static Collection<Object[]> parameters() {
    return buildParameters(
        getTestParameters().withAllRuntimes().build(),
        BooleanUtils.values(),
        TestNamingConfig.values());
  }

  private final TestParameters parameters;
  private final boolean minify;
  private final TestNamingConfig config;

  public InnerClassNameTestRunner(
      TestParameters parameters, boolean minify, TestNamingConfig config) {
    this.parameters = parameters;
    this.minify = minify;
    this.config = config;
  }

  @Test
  public void test() throws IOException, CompilationFailedException, ExecutionException {
    JvmTestRunResult runResult = null;
    if (parameters.isCfRuntime()) {
      runResult =
          testForJvm()
              .addProgramClassFileData(InnerClassNameTestDump.dump(config, parameters))
              .run(parameters.getRuntime(), MAIN_CLASS);
    }

    R8TestCompileResult r8CompileResult;
    try {
      r8CompileResult =
        testForR8(parameters.getBackend())
            .addKeepMainRule(MAIN_CLASS)
            .addKeepRules("-keep,allowobfuscation class * { *; }")
            .addKeepRules("-keepattributes InnerClasses,EnclosingMethod")
            .addProgramClassFileData(InnerClassNameTestDump.dump(config, parameters))
            .minification(minify)
            .setMinApi(parameters.getRuntime())
            .compile();
    } catch (InternalCompilerError e) {
      assertThat(e.getMessage(), containsString("Malformed class name"));
      assertThat(e.getMessage(), containsString(config.getInnerTypeRaw()));
      assertTrue(
          "b/120597515",
          config == TestNamingConfig.EMTPY_SEPARATOR
              || config == TestNamingConfig.UNDERBAR_SEPARATOR
              || config == TestNamingConfig.NON_NESTED_INNER);
      assertFalse("b/120597515", minify);
      return;
    }
    CodeInspector inspector = r8CompileResult.inspector();
    R8TestRunResult r8RunResult = r8CompileResult.run(parameters.getRuntime(), MAIN_CLASS);
    switch (config) {
      case DEFAULT:
      case OUTER_ENDS_WITH_DOLLAR:
      case $_$_$:
        if (parameters.isCfRuntime()) {
          runResult.assertSuccessWithOutput(getExpectedNonMinified(config.getInnerClassName()));
        }
        r8RunResult.assertSuccessWithOutput(getExpectedMinified(inspector));
        break;
      case DOLLAR2_SEPARATOR:
        if (parameters.isCfRuntime()
            && parameters.getRuntime().asCf().getVm().lessThanOrEqual(CfVm.JDK8)) {
          // $$ as separator and InnerClass as name, results in $InnerClass from getSimpleName...
          String expectedWithDollarOnInnerName =
              getExpectedNonMinified("$" + config.getInnerClassName());
          runResult.assertSuccessWithOutput(expectedWithDollarOnInnerName);
          // R8 map the inner name to 'a' while keeping $$ as a separator, whose inner name will be
          // "$a" in JDK under 8.
          r8RunResult.assertSuccessWithOutput(
              minify ? getExpectedMinified(inspector, true) : expectedWithDollarOnInnerName);
        } else {
          // $$ in DEX or JDK 9+ will not change the InnerName/getSimpleName.
          r8RunResult.assertSuccessWithOutput(getExpectedMinified(inspector));
        }
        break;
      case EMTPY_SEPARATOR:
      case UNDERBAR_SEPARATOR:
      case NON_NESTED_INNER:
        assertTrue("Expect to see compilation error", minify);
        r8RunResult.assertSuccessWithOutput(getExpectedMinified(inspector));
        break;
      default:
        throw new Unreachable("Unexpected test configuration: " + config);
    }
  }

  private String getExpected(String typeName, String canonicalName, String simpleName) {
    if (TestNamingConfig.isGetTypeNameSupported(parameters)) {
      return StringUtils.lines(
          "getName: " + typeName,
          "getTypeName: " + typeName,
          "getCanonicalName: " + canonicalName,
          "getSimpleName: " + simpleName);
    } else {
      return StringUtils.lines(
          "getName: " + typeName,
          "getCanonicalName: " + canonicalName,
          "getSimpleName: " + simpleName);
    }
  }

  private String getExpectedNonMinified(String innerName) {
    String outerClassType = DescriptorUtils.descriptorToJavaType(config.getOuterDescriptor());
    String innerClassType = DescriptorUtils.descriptorToJavaType(config.getInnerDescriptor());
    return getExpected(innerClassType, outerClassType + "." + innerName, innerName);
  }

  private String getExpectedMinified(CodeInspector inspector) {
    return getExpectedMinified(inspector, false);
  }

  private String getExpectedMinified(CodeInspector inspector, boolean withDollarOnInnerName) {
    String outerClassType = DescriptorUtils.descriptorToJavaType(config.getOuterDescriptor());
    String innerClassType = DescriptorUtils.descriptorToJavaType(config.getInnerDescriptor());
    String outerClassTypeFinal = inspector.clazz(outerClassType).getFinalName();
    String innerClassTypeFinal = inspector.clazz(innerClassType).getFinalName();
    String innerNameFinal =
        !minify
            ? config.getInnerClassName()
            : innerClassTypeFinal.substring(
                outerClassTypeFinal.length() + config.getMinifiedSeparator().length());
    innerNameFinal = withDollarOnInnerName ? "$" + innerNameFinal : innerNameFinal;
    return getExpected(
        innerClassTypeFinal, outerClassTypeFinal + "." + innerNameFinal, innerNameFinal);
  }
}
