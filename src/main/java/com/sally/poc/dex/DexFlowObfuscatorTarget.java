package com.sally.poc.dex;

import com.android.tools.r8.dex.code.DexAddIntLit8;
import com.android.tools.r8.dex.code.DexConst4;
import com.android.tools.r8.dex.code.DexConstString;
import com.android.tools.r8.dex.code.DexIfEqz;
import com.android.tools.r8.dex.code.DexIfNez;
import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.dex.code.DexInvokeDirect;
import com.android.tools.r8.dex.code.DexInvokeStatic;
import com.android.tools.r8.dex.code.DexMoveObject;
import com.android.tools.r8.dex.code.DexMoveResult;
import com.android.tools.r8.dex.code.DexMoveResultObject;
import com.android.tools.r8.dex.code.DexMoveResultWide;
import com.android.tools.r8.dex.code.DexRemInt2Addr;
import com.android.tools.r8.dex.code.DexRemIntLit16;
import com.android.tools.r8.dex.code.DexReturnObject;
import com.android.tools.r8.dex.code.DexSget;
import com.android.tools.r8.dex.code.DexSput;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.ir.code.IfType;
import com.sally.poc.FlowObfuscatorTarget;

public class DexFlowObfuscatorTarget extends FlowObfuscatorTarget {
    private final int useRegisterSize = 2;

    public DexFlowObfuscatorTarget(DexItemFactory dexItemFactory, DexProgramClass clazz, DexEncodedMethod method) {
        super(dexItemFactory, clazz, method);
    }

    @Override
    public void addFlow() {
        Code code = method.getCode();
        if (code == null || !code.isDexCode()) {
            return;
        }

        DexCode dexCode = code.asDexCode();

        int newRegisterIndex = dexCode.registerSize - (method.getParameters().size() + 1);
        int index = getRandom(dexCode.instructions);

        DexInstruction[] newInstructions = getAddCalcFlowAndIf(newRegisterIndex);
        DexInstruction[] originInstructions = updateParameterIndex(
                dexCode.instructions,
                newRegisterIndex
        );
        DexInstruction[] resultInstructions = insertInstructions(
                originInstructions,
                newInstructions,
                index
        );

        updateOffset(resultInstructions);

        DexCode resultDexCode = new DexCode(
                dexCode.registerSize + useRegisterSize,
                dexCode.incomingRegisterSize,
                dexCode.outgoingRegisterSize,
                resultInstructions,
                dexCode.tries,
                dexCode.handlers,
                dexCode.getDebugInfo(),
                dexCode.getMetadata()
        );

        method.setCode(resultDexCode.asCode(), method.getParameterInfo());
    }

    private int getInstructionsSize(DexInstruction[] instructions, int start, int end) {
        int size = 0;
        for (int i = start; i < end; i++) {
            size += instructions[i].getSize();
        }
        return size;
    }

    private int getRandom(DexInstruction[] instructions) {
        // TODO: void 함수 호출인 경우에는 상관 없지 않을까? 확인 필요

        int number = ((int) (Math.random() * instructions.length));

        // 이전 함수 실행 결과값을 어떤 register 에 저장할 지 정하는 것이기 때문에
        // 해당 offset 에 로직을 추가하게 되면 함수 리턴 값 저장이 제대로 안됩니다.
        if (instructions[number] instanceof DexMoveResultObject ||
                instructions[number] instanceof DexMoveResult ||
                instructions[number] instanceof DexMoveResultWide) {
            return getRandom(instructions);
        }

        // 함수 실행 결과값은 다음 instructions 에서 처리되기 때문에
        // 함수 실행 후 로직을 추가하게 되면 함수 리턴 값 저장이 제대로 안됩니다.
        if ((0 < number) &&
                (instructions[number - 1] instanceof DexInvokeDirect ||
                        instructions[number - 1] instanceof DexInvokeStatic)) {
            return getRandom(instructions);
        }
        return number;
    }

    private void updateOffset(DexInstruction[] instructions) {
        int offset = 0;
        for (DexInstruction instruction : instructions) {
            instruction.setOffset(offset);
            offset += instruction.getSize();
        }
    }

    private DexInstruction[] insertInstructions(DexInstruction[] origin, DexInstruction[] target, int index) {
        DexInstruction[] resultInstructions = new DexInstruction[origin.length + target.length];
        for (int i = 0; i < index; i++) {
            // TODO: goto offset 를 사용하는 instructions 을 전부 구현해야함.
            if (origin[i] instanceof DexIfEqz) {
                int jumpOffset = getInstructionsSize(origin, 0, i) + ((DexIfEqz) origin[i]).BBBB;
                if (getInstructionsSize(origin, 0, index) < jumpOffset) {
                    origin[i] = new DexIfEqz(
                            ((DexIfEqz) origin[i]).AA,
                            ((DexIfEqz) origin[i]).BBBB + getInstructionsSize(target, 0, target.length)
                    );
                }
            }
        }
        System.arraycopy(origin, 0, resultInstructions, 0, index);
        System.arraycopy(target, 0, resultInstructions, index, target.length);
        System.arraycopy(origin, index, resultInstructions, index + target.length, origin.length - index);
        return resultInstructions;
    }

    private int getRegisterIndex(int index, int originParameterIndex) {
        return (originParameterIndex <= index ? (index + useRegisterSize) : index);
    }

    private DexInstruction[] updateParameterIndex(DexInstruction[] instructions, int originParamIndex) {
        // TODO: parameter 를 사용하는 instructions 을 전부 구현해야함.
        for (int i = 0; i < instructions.length; i++) {
            if (instructions[i] instanceof DexConst4) {
                instructions[i] = new DexConst4(
                        getRegisterIndex(((DexConst4) instructions[i]).A, originParamIndex),
                        getRegisterIndex(((DexConst4) instructions[i]).B, originParamIndex)
                );
            } else if (instructions[i] instanceof DexConstString) {
                instructions[i] = new DexConstString(
                        getRegisterIndex(((DexConstString) instructions[i]).AA, originParamIndex),
                        ((DexConstString) instructions[i]).BBBB
                );
            } else if (instructions[i] instanceof DexInvokeStatic) {
                instructions[i] = new DexInvokeStatic(
                        ((DexInvokeStatic) instructions[i]).A,
                        ((DexInvokeStatic) instructions[i]).BBBB,
                        getRegisterIndex(((DexInvokeStatic) instructions[i]).C, originParamIndex),
                        getRegisterIndex(((DexInvokeStatic) instructions[i]).D, originParamIndex),
                        getRegisterIndex(((DexInvokeStatic) instructions[i]).E, originParamIndex),
                        getRegisterIndex(((DexInvokeStatic) instructions[i]).F, originParamIndex),
                        getRegisterIndex(((DexInvokeStatic) instructions[i]).G, originParamIndex)
                );
            } else if (instructions[i] instanceof DexInvokeDirect) {
                instructions[i] = new DexInvokeDirect(
                        ((DexInvokeDirect) instructions[i]).A,
                        ((DexInvokeDirect) instructions[i]).BBBB,
                        getRegisterIndex(((DexInvokeDirect) instructions[i]).C, originParamIndex),
                        getRegisterIndex(((DexInvokeDirect) instructions[i]).D, originParamIndex),
                        getRegisterIndex(((DexInvokeDirect) instructions[i]).E, originParamIndex),
                        getRegisterIndex(((DexInvokeDirect) instructions[i]).F, originParamIndex),
                        getRegisterIndex(((DexInvokeDirect) instructions[i]).G, originParamIndex)
                );
            } else if (instructions[i] instanceof DexMoveObject) {
                instructions[i] = new DexMoveObject(
                        getRegisterIndex(((DexMoveObject) instructions[i]).A, originParamIndex),
                        getRegisterIndex(((DexMoveObject) instructions[i]).B, originParamIndex)
                );
            } else if (instructions[i] instanceof DexMoveResultObject) {
                instructions[i] = new DexMoveResultObject(
                        getRegisterIndex(((DexMoveResultObject) instructions[i]).AA, originParamIndex)
                );
            } else if (instructions[i] instanceof DexMoveResult) {
                instructions[i] = new DexMoveResult(
                        getRegisterIndex(((DexMoveResult) instructions[i]).AA, originParamIndex)
                );
            } else if (instructions[i] instanceof DexReturnObject) {
                instructions[i] = new DexReturnObject(
                        getRegisterIndex(((DexReturnObject) instructions[i]).AA, originParamIndex)
                );
            }
        }
        return instructions;
    }

    private DexInstruction[] getAddCalcFlowAndIf(int registerIndex) {
        int number = ((int) (Math.random() * 64)) * 2 + 1;

        RandomFlowFieldStruct random = new RandomFlowFieldStruct();

        return new DexInstruction[]{
                new DexSget(registerIndex, random.fieldRead),
                new DexAddIntLit8(registerIndex, registerIndex, number),
                new DexRemIntLit16(registerIndex + 1, registerIndex, 128),
                new DexSput(registerIndex + 1, random.fieldWrite),
                new DexConst4(registerIndex + 1, 2),
                new DexRemInt2Addr(registerIndex, registerIndex + 1),
                random.getDexIf(registerIndex, DexIfEqz.SIZE + DexConstString.SIZE + DexReturnObject.SIZE),
                // TODO: 현재는 String 함수만 지원중
                new DexConstString(registerIndex, dexItemFactory.createString("error!")),
                new DexReturnObject(registerIndex)
        };
    }

    private class RandomFlowFieldStruct {
        final DexField fieldRead;
        final DexField fieldWrite;
        private final IfType ifType;

        RandomFlowFieldStruct() {
            int random = ((int) (Math.random() * 100));
            if ((random % 2) == 0) {
                fieldRead = getOddNumberDexField();
                fieldWrite = getEvenNumberDexField();
                ifType = IfType.EQ;
            } else {
                fieldRead = getEvenNumberDexField();
                fieldWrite = getOddNumberDexField();
                ifType = IfType.NE;
            }
        }

        public DexInstruction getDexIf(int registerIndex, int relativeOffset) {
            if (ifType == IfType.NE) {
                return new DexIfNez(registerIndex, relativeOffset);
            } else {
                return new DexIfEqz(registerIndex, relativeOffset);
            }
        }
    }
}
