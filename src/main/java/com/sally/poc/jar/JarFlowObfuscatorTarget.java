package com.sally.poc.jar;

import com.android.tools.r8.cf.code.CfArithmeticBinop;
import com.android.tools.r8.cf.code.CfConstNumber;
import com.android.tools.r8.cf.code.CfConstString;
import com.android.tools.r8.cf.code.CfFrame;
import com.android.tools.r8.cf.code.CfIf;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfLabel;
import com.android.tools.r8.cf.code.CfLoad;
import com.android.tools.r8.cf.code.CfNew;
import com.android.tools.r8.cf.code.CfReturn;
import com.android.tools.r8.cf.code.CfReturnVoid;
import com.android.tools.r8.cf.code.CfStackInstruction;
import com.android.tools.r8.cf.code.CfStaticFieldRead;
import com.android.tools.r8.cf.code.CfStaticFieldWrite;
import com.android.tools.r8.cf.code.CfStore;
import com.android.tools.r8.cf.code.CfThrow;
import com.android.tools.r8.cf.code.frame.FrameType;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.ir.code.IfType;
import com.android.tools.r8.ir.code.NumericType;
import com.android.tools.r8.ir.code.ValueType;
import com.google.common.collect.ImmutableList;
import com.sally.poc.FlowObfuscatorTarget;

import java.util.ArrayList;
import java.util.List;

import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;

// TODO: 중간에 flow 삽입시 반드시 직전 로직에 맞춰서 Frame (locals, stack) 을 만들어주어야 하기 때문에 일단 항상 맨 앞에 flow를 삽입합니다.
public class JarFlowObfuscatorTarget extends FlowObfuscatorTarget {
    public JarFlowObfuscatorTarget(DexItemFactory dexItemFactory, DexProgramClass clazz, DexEncodedMethod method) {
        super(dexItemFactory, clazz, method);
    }

    @Override
    public void addFlow() {
        Code code = this.method.getCode();
        if (code == null || !code.isCfCode()) {
            return;
        }

        CfCode cfCode = code.asCfCode();
        CfLabel label = new CfLabel();
        int index = cfCode.getMaxLocals();  // flow 에서 사용할 index 를 구함

        List<CfInstruction> instructions = new ArrayList<>(cfCode.getInstructions());
        instructions.addAll(0, getAddCalcFlowAndIf(index, label));
        instructions.addAll(instructions.size(), getReturnLabel(label, method));

        cfCode.setInstructions(instructions);
        cfCode.setMaxLocals(index + 1);
        cfCode.setMaxStack(cfCode.getMaxStack() + 2);
        cfCode.getStackMapStatus();
    }

    private List<CfInstruction> getAddCalcFlowAndIf(int index, CfLabel label) {
        int number = ((int) (Math.random() * 64)) * 2 + 1;

        RandomFlowFieldStruct random = new RandomFlowFieldStruct();
        return ImmutableList.of(
                new CfStaticFieldRead(random.fieldRead),
                new CfConstNumber(number, ValueType.INT),
                new CfArithmeticBinop(CfArithmeticBinop.Opcode.Add, NumericType.INT),
                new CfStore(ValueType.INT, index),
                new CfLoad(ValueType.INT, index),
                new CfConstNumber(128, ValueType.INT),
                new CfArithmeticBinop(CfArithmeticBinop.Opcode.Rem, NumericType.INT),
                new CfStaticFieldWrite(random.fieldWrite),
                new CfLoad(ValueType.INT, index),
                new CfConstNumber(2, ValueType.INT),
                new CfArithmeticBinop(CfArithmeticBinop.Opcode.Rem, NumericType.INT),
                new CfIf(random.ifType, ValueType.INT, label)
        );
    }

    private class RandomFlowFieldStruct {
        final DexField fieldRead;
        final DexField fieldWrite;
        final IfType ifType;

        RandomFlowFieldStruct() {
            int random = ((int) (Math.random() * 100));
            if ((random % 2) == 0) {
                fieldRead = getOddNumberDexField();
                fieldWrite = getEvenNumberDexField();
                ifType = IfType.NE;
            } else {
                fieldRead = getEvenNumberDexField();
                fieldWrite = getOddNumberDexField();
                ifType = IfType.EQ;
            }
        }
    }

    private List<CfInstruction> getReturnLabel(CfLabel label, DexEncodedMethod targetMethod) {
        List<CfInstruction> instructions = new ArrayList<>();

        instructions.addAll(
                0,
                ImmutableList.of(
                        label,
                        new CfFrame(
                                new Int2ObjectAVLTreeMap<>(
                                        new int[]{0, 1},
                                        new FrameType[]{
                                                FrameType.initializedNonNullReference(dexItemFactory.createType(descriptor)),
                                                FrameType.initializedNonNullReference(dexItemFactory.intType),
                                        }
                                )
//                                    // stack ...
//                                    new ArrayDeque<>(
//                                            Arrays.asList(
//                                                    FrameType.initializedNonNullReference(
//                                                            dexItemFactory.createType("I"))
//                                            )
//                                    )
                        )
                )
        );

        if (targetMethod.returnType().isVoidType()) {
            instructions.add(new CfReturnVoid());
        } else if (targetMethod.returnType().isIntType()) {
            instructions.addAll(
                    ImmutableList.of(
                            new CfConstNumber(0, ValueType.INT),
                            new CfReturn(ValueType.INT)
                    )
            );
        } else {
            instructions.addAll(
                    ImmutableList.of(
                            new CfNew(dexItemFactory.createType("Ljava/lang/RuntimeException;")),
                            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
                            new CfConstString(dexItemFactory.createString("error!")),
                            new CfInvoke(
                                    183,
                                    dexItemFactory.createMethod(
                                            dexItemFactory.createType("Ljava/lang/RuntimeException;"),
                                            dexItemFactory.createProto(dexItemFactory.voidType, dexItemFactory.stringType),
                                            dexItemFactory.createString("<init>")),
                                    false),
                            new CfThrow()
                    )
            );
        }

        return instructions;
    }
}
