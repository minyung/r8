package com.android.tools.r8.ir.optimize.staticizer;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CompanionClassWithNewInstanceUserTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public CompanionClassWithNewInstanceUserTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(CompanionClassWithNewInstanceUserTest.class)
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  private void inspect(CodeInspector inspector) {
    // The companion class has not been removed.
    assertThat(inspector.clazz(Companion.class), isPresent());
  }

  static class TestClass {

    public static void main(String[] args) {
      Companion companion = CompanionHost.COMPANION;
    }
  }

  static class CompanionHost {

    static final Companion COMPANION;

    static {
      Companion companion = new Companion();
      COMPANION = companion;
      companion.method();
    }
  }

  static class Companion {

    @NeverInline
    public void method() {
      System.out.println("Hello world!");
    }
  }
}
