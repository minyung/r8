// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package invokecustom2;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class TestGenerator {

  private final Path classNamePath;
  private final Path outputClassNamePath;

  public static void main(String[] args) throws IOException {
    assert args.length == 2;
    String fileName = invokecustom.InvokeCustom.class.getSimpleName() + ".class";
    Path inputFile = Paths.get(args[0], TestGenerator.class.getPackage().getName(), fileName);
    Path outputFile = Paths.get(args[1], fileName);
    TestGenerator testGenerator = new TestGenerator(inputFile, outputFile);
    testGenerator.generateTests();
  }

  public TestGenerator(Path classNamePath, Path outputClassNamePath) {
    this.classNamePath = classNamePath;
    this.outputClassNamePath = outputClassNamePath;
  }

  private void generateTests() throws IOException {
    Files.createDirectories(outputClassNamePath.getParent());
    try (InputStream input = Files.newInputStream(classNamePath)) {
      ClassReader cr = new ClassReader(input);
      ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
      cr.accept(
          new ClassVisitor(Opcodes.ASM7, cw) {
            @Override
            public void visitEnd() {
              generateMethodTest1(cw);
              generateMethodTest2(cw);
              generateMethodTest3(cw);
              generateMethodTest4(cw);
              generateMethodTest5(cw);
              generateMethodTest6(cw);
              generateMethodTest7(cw);
              generateMethodTest8(cw);
              generateMethodTest9(cw);
              generateMethodMain(cw);
              super.visitEnd();
            }
          }, 0);
      try (OutputStream output =
          Files.newOutputStream(outputClassNamePath, StandardOpenOption.CREATE)) {
        output.write(cw.toByteArray());
      }
    }
  }

  /* generate main method that only call all test methods. */
  private void generateMethodMain(ClassVisitor cv) {
    MethodVisitor mv = cv.visitMethod(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC,
        "main", "([Ljava/lang/String;)V", null, null);
    String internalName = Type.getInternalName(InvokeCustom.class);
    mv.visitMethodInsn(Opcodes.INVOKESTATIC, internalName, "test1", "()V", false);
    mv.visitMethodInsn(Opcodes.INVOKESTATIC, internalName, "test2", "()V", false);
    mv.visitMethodInsn(Opcodes.INVOKESTATIC, internalName, "test3", "()V", false);
    mv.visitMethodInsn(Opcodes.INVOKESTATIC, internalName, "test4", "()V", false);
    mv.visitMethodInsn(Opcodes.INVOKESTATIC, internalName, "test5", "()V", false);
    mv.visitMethodInsn(Opcodes.INVOKESTATIC, internalName, "test6", "()V", false);
    mv.visitMethodInsn(Opcodes.INVOKESTATIC, internalName, "test7", "()V", false);
    mv.visitMethodInsn(Opcodes.INVOKESTATIC, internalName, "test8", "()V", false);
    mv.visitMethodInsn(Opcodes.INVOKESTATIC, internalName, "test9", "()V", false);
    mv.visitInsn(Opcodes.RETURN);
    mv.visitMaxs(-1, -1);
  }

  /**
   *  Generate test with an invokedynamic, a static bootstrap method without extra args and no arg
   *  to the target method.
   */
  private void generateMethodTest1(ClassVisitor cv) {
    MethodVisitor mv = cv.visitMethod(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, "test1", "()V",
        null, null);
    MethodType mt = MethodType.methodType(CallSite.class, MethodHandles.Lookup.class, String.class,
        MethodType.class);
    Handle bootstrap = new Handle(Opcodes.H_INVOKESTATIC, Type.getInternalName(InvokeCustom.class),
        "bsmLookupStatic", mt.toMethodDescriptorString(), false);
    mv.visitInvokeDynamicInsn("targetMethodTest1", "()V", bootstrap);
    mv.visitInsn(Opcodes.RETURN);
    mv.visitMaxs(-1, -1);
  }

  /**
   *  Generate test with an invokedynamic, a static bootstrap method without extra args and
   *  args to the target method.
   */
  private void generateMethodTest2(ClassVisitor cv) {
    MethodVisitor mv = cv.visitMethod(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, "test2", "()V",
        null, null);
    MethodType mt = MethodType.methodType(CallSite.class, MethodHandles.Lookup.class, String.class,
        MethodType.class);
    Handle bootstrap = new Handle(Opcodes.H_INVOKESTATIC, Type.getInternalName(InvokeCustom.class),
        "bsmLookupStatic", mt.toMethodDescriptorString(), false);
    mv.visitLdcInsn(new Boolean(true));
    mv.visitLdcInsn(new Byte((byte) 127));
    mv.visitLdcInsn(new Character('c'));
    mv.visitLdcInsn(new Short((short) 1024));
    mv.visitLdcInsn(new Integer(123456));
    mv.visitLdcInsn(new Float(1.2f));
    mv.visitLdcInsn(new Long(123456789));
    mv.visitLdcInsn(new Double(3.5123456789));
    mv.visitLdcInsn("String");
    mv.visitInvokeDynamicInsn("targetMethodTest2", "(ZBCSIFJDLjava/lang/String;)V", bootstrap);
    mv.visitInsn(Opcodes.RETURN);
    mv.visitMaxs(-1, -1);
  }

  /**
   *  Generate test with an invokedynamic, a static bootstrap method with extra args and no arg
   *  to the target method.
   */
  private void generateMethodTest3(ClassVisitor cv) {
    MethodVisitor mv = cv.visitMethod(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, "test3", "()V",
        null, null);
    MethodType mt = MethodType.methodType(CallSite.class, MethodHandles.Lookup.class, String.class,
        MethodType.class, int.class,
        long.class, float.class, double.class);
    Handle bootstrap = new Handle( Opcodes.H_INVOKESTATIC, Type.getInternalName(InvokeCustom.class),
        "bsmLookupStaticWithExtraArgs", mt.toMethodDescriptorString(), false);
    mv.visitInvokeDynamicInsn("targetMethodTest3", "()V", bootstrap, new Integer(1),
        new Long(123456789), new Float(123.456), new Double(123456.789123));
    mv.visitInsn(Opcodes.RETURN);
    mv.visitMaxs(-1, -1);
  }

  /**
   *  Generate test with an invokedynamic, a static bootstrap method with an extra arg that is a
   *  MethodHandle of kind invokespecial.
   */
  private void generateMethodTest4(ClassVisitor cv) {
    MethodVisitor mv = cv.visitMethod(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, "test4", "()V",
        null, null);
    MethodType mt =
        MethodType.methodType(
            CallSite.class,
            MethodHandles.Lookup.class,
            String.class,
            MethodType.class,
            MethodHandle.class);
    Handle bootstrap = new Handle( Opcodes.H_INVOKESTATIC, Type.getInternalName(InvokeCustom.class),
        "bsmCreateCallSite", mt.toMethodDescriptorString(), false);
    mv.visitTypeInsn(Opcodes.NEW, Type.getInternalName(InvokeCustom.class));
    mv.visitInsn(Opcodes.DUP);
    mv.visitMethodInsn(Opcodes.INVOKESPECIAL, Type.getInternalName(InvokeCustom.class),
        "<init>", "()V", false);
    mv.visitInvokeDynamicInsn("targetMethodTest4", "(Linvokecustom2/InvokeCustom;)V", bootstrap,
        new Handle(Opcodes.H_INVOKESPECIAL, Type.getInternalName(Super.class),
            "targetMethodTest4", "()V", false));
    mv.visitInsn(Opcodes.RETURN);
    mv.visitMaxs(-1, -1);
  }

  /**
   * Generate a test with an invokedynamic where the target generates
   * a result that the call site prints out.
   */
  private void generateMethodTest5(ClassVisitor cv) {
    MethodVisitor mv = cv.visitMethod(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, "test5", "()V",
        null, null);
    MethodType mt = MethodType.methodType(
        CallSite.class, MethodHandles.Lookup.class, String.class, MethodType.class);
    Handle bootstrap = new Handle(Opcodes.H_INVOKESTATIC, Type.getInternalName(InvokeCustom.class),
        "bsmLookupStatic", mt.toMethodDescriptorString(), false);
    mv.visitIntInsn(Opcodes.SIPUSH, 1000);
    mv.visitIntInsn(Opcodes.SIPUSH, -923);
    mv.visitIntInsn(Opcodes.SIPUSH, 77);
    mv.visitInvokeDynamicInsn("targetMethodTest5", "(III)I", bootstrap);
    mv.visitVarInsn(Opcodes.ISTORE, 0);
    mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
    mv.visitTypeInsn(Opcodes.NEW, "java/lang/StringBuilder");
    mv.visitInsn(Opcodes.DUP);
    mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
    mv.visitLdcInsn("targetMethodTest5 returned: ");
    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
        "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
    mv.visitVarInsn(Opcodes.ILOAD, 0);
    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
        "(I)Ljava/lang/StringBuilder;", false);
    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "toString",
        "()Ljava/lang/String;", false);
    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println",
        "(Ljava/lang/String;)V", false);
    mv.visitInsn(Opcodes.RETURN);
    mv.visitMaxs(-1, -1);
  }

  /**
   * Generate a test with an invokedynamic where the call site invocation tests the summation of
   * two long values and returns a long.
   */
  private void generateMethodTest6(ClassVisitor cv) {
    MethodVisitor mv = cv.visitMethod(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, "test6", "()V",
        null, null);
    MethodType mt = MethodType.methodType(CallSite.class, MethodHandles.Lookup.class, String.class,
        MethodType.class);
    Handle bootstrap = new Handle(Opcodes.H_INVOKESTATIC, Type.getInternalName(InvokeCustom.class),
        "bsmLookupStatic", mt.toMethodDescriptorString(), false);
    mv.visitLdcInsn(0x77777777777l);
    mv.visitLdcInsn(-0x11111111111l);
    mv.visitLdcInsn(0x66666666666l);
    mv.visitInvokeDynamicInsn("targetMethodTest6", "(JJJ)J", bootstrap);
    mv.visitVarInsn(Opcodes.LSTORE, 0);
    mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
    mv.visitTypeInsn(Opcodes.NEW, "java/lang/StringBuilder");
    mv.visitInsn(Opcodes.DUP);
    mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
    mv.visitLdcInsn("targetMethodTest6 returned: ");
    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
        "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
    mv.visitVarInsn(Opcodes.LLOAD, 0);
    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
        "(J)Ljava/lang/StringBuilder;", false);
    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "toString",
        "()Ljava/lang/String;", false);
    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println",
        "(Ljava/lang/String;)V", false);
    mv.visitInsn(Opcodes.RETURN);
    mv.visitMaxs(-1, -1);
  }

  /**
   * Generate a test with an invokedynamic where the call site invocation tests the product of
   * two float values and returns a double.
   */
  private void generateMethodTest7(ClassVisitor cv) {
    MethodVisitor mv = cv.visitMethod(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, "test7", "()V",
        null, null);
    MethodType mt = MethodType.methodType(CallSite.class, MethodHandles.Lookup.class, String.class,
        MethodType.class);
    Handle bootstrap = new Handle(Opcodes.H_INVOKESTATIC, Type.getInternalName(InvokeCustom.class),
        "bsmLookupStatic", mt.toMethodDescriptorString(), false);
    double x = 0.5009765625;
    double y = -x;
    mv.visitLdcInsn((float) x);
    mv.visitLdcInsn((float) y);
    mv.visitLdcInsn(x * y);
    mv.visitInvokeDynamicInsn("targetMethodTest7", "(FFD)D", bootstrap);
    mv.visitVarInsn(Opcodes.DSTORE, 0);
    mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
    mv.visitTypeInsn(Opcodes.NEW, "java/lang/StringBuilder");
    mv.visitInsn(Opcodes.DUP);
    mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
    mv.visitLdcInsn("targetMethodTest6 returned: ");
    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
        "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
    mv.visitVarInsn(Opcodes.DLOAD, 0);
    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
        "(D)Ljava/lang/StringBuilder;", false);
    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "toString",
        "()Ljava/lang/String;", false);
    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println",
        "(Ljava/lang/String;)V", false);
    mv.visitInsn(Opcodes.RETURN);
    mv.visitMaxs(-1, -1);
  }

  /**
   * Generate a test with multiple invokedynamic bytecodes operating on the same parameters.
   * These invocations should each produce invoke-custom bytecodes with unique call site ids.
   */
  private void generateMethodTest8(ClassVisitor cv) {
    MethodVisitor mv = cv.visitMethod(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, "test8", "()V",
        null, null);
    MethodType mt = MethodType.methodType(CallSite.class, MethodHandles.Lookup.class, String.class,
        MethodType.class);
    // These should be two distinct call sites and both invoke the
    // bootstrap method. An erroneous implementation might treat them
    // as the same call site because the handle arguments are the same.
    Handle bootstrap1 = new Handle(Opcodes.H_INVOKESTATIC, Type.getInternalName(InvokeCustom.class),
        "bsmLookupStatic", mt.toMethodDescriptorString(), false);
    mv.visitLdcInsn("First invokedynamic invocation");
    mv.visitInvokeDynamicInsn("targetMethodTest8", "(Ljava/lang/String;)V", bootstrap1);

    Handle bootstrap2 = new Handle(Opcodes.H_INVOKESTATIC, Type.getInternalName(InvokeCustom.class),
        "bsmLookupStatic", mt.toMethodDescriptorString(), false);
    mv.visitLdcInsn("Second invokedynamic invocation");
    mv.visitInvokeDynamicInsn("targetMethodTest8", "(Ljava/lang/String;)V", bootstrap2);

    // Using same handle again creates a new call site so invokes the bootstrap method.
    mv.visitLdcInsn("Dupe first invokedynamic invocation");
    mv.visitInvokeDynamicInsn("targetMethodTest8", "(Ljava/lang/String;)V", bootstrap1);
    mv.visitInsn(Opcodes.RETURN);
    mv.visitMaxs(-1, -1);
  }

  /**
   * Generate a test with different kinds of constant method handles.
   */
  private void generateMethodTest9(ClassVisitor cv) {
    MethodVisitor mv = cv.visitMethod(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, "test9", "()V",
        null, null);
    MethodType mt =
        MethodType.methodType(CallSite.class,
            MethodHandles.Lookup.class, String.class, MethodType.class,
            MethodHandle.class, MethodHandle.class,
            MethodHandle.class, MethodHandle.class,
            MethodHandle.class, MethodHandle.class,
            MethodHandle.class, MethodHandle.class);
    String internalName = Type.getInternalName(InvokeCustom.class);
    Handle bootstrap = new Handle(Opcodes.H_INVOKESTATIC, internalName, "bsmLookupTest9",
        mt.toMethodDescriptorString(), false);
    Handle staticSetter =
        new Handle(Opcodes.H_GETSTATIC, internalName, "staticFieldTest9", "I", false);
    Handle staticGetter =
        new Handle(Opcodes.H_PUTSTATIC, internalName, "staticFieldTest9", "I", false);
    Handle setter =
        new Handle(Opcodes.H_GETFIELD, internalName, "fieldTest9", "F", false);
    Handle getter =
        new Handle(Opcodes.H_PUTFIELD, internalName, "fieldTest9", "F", false);
    Handle instanceInvoke =
        new Handle(Opcodes.H_INVOKEVIRTUAL, internalName, "helperMethodTest9", "()V", false);
    Handle constructor =
        new Handle(Opcodes.H_NEWINVOKESPECIAL, internalName, "<init>", "(I)V", false);
    Handle interfaceInvoke =
        new Handle(Opcodes.H_INVOKEINTERFACE,
            Type.getInternalName(Runnable.class),
            "run", "()V", true);
    // test4 covers invokespecial for a super method. This covers invokespecial of a private method.
    Handle privateInvoke =
        new Handle(Opcodes.H_INVOKESPECIAL, internalName, "privateMethodTest9", "()V", false);

    mv.visitInvokeDynamicInsn("targetMethodTest9", "()V", bootstrap,
        staticSetter, staticGetter,
        setter, getter,
        instanceInvoke, constructor,
        interfaceInvoke, privateInvoke);
    mv.visitInsn(Opcodes.RETURN);
    mv.visitMaxs(-1, -1);
  }
}