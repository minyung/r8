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
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.IfType;

public class FlowObfuscator {

    public static class DexTarget {
        private final DexItemFactory dexItemFactory;
        private final DexProgramClass clazz;

        private final DexEncodedMethod method;

        private final String descriptor;

        public DexTarget(DexItemFactory dexItemFactory, DexProgramClass clazz, DexEncodedMethod method) {
            this.dexItemFactory = dexItemFactory;
            this.clazz = clazz;
            this.descriptor = "L" + clazz.getTypeName().replace('.', '/') + ";";
            this.method = method;
        }

        public void addFlow() {
            Code code = method.getCode();
            if (code == null || !code.isDexCode()) {
                return;
            }

            DexCode dexCode = code.asDexCode();
            System.out.println(dexCode.toString());

            int newRegisterIndex = dexCode.registerSize - (method.getParameters().size() + 1);
            System.out.println("dexCode.registerSize: " + dexCode.registerSize + " params: " + method.getParameters().size() + " index: " + newRegisterIndex);
            System.out.println(dexCode.toString());

            int index = getRandom(dexCode.instructions);

            DexInstruction[] newInstructions = getAddCalcFlowAndIf(newRegisterIndex);
            DexInstruction[] originInstructions = updateParameterIndex(dexCode.instructions, newRegisterIndex);
            DexInstruction[] resultInstructions = insertInstructions(originInstructions, newInstructions, index, getInstructionsSize(newInstructions));

            updateOffset(resultInstructions);

            DexCode resultDexCode = new DexCode(
                    dexCode.registerSize + 2,
                    dexCode.incomingRegisterSize,
                    dexCode.outgoingRegisterSize,
                    resultInstructions,
                    dexCode.tries,
                    dexCode.handlers,
                    dexCode.getDebugInfo(),
                    dexCode.getMetadata()
            );

            System.out.println(resultDexCode.toString());
            method.setCode(resultDexCode.asCode(), method.getParameterInfo());
        }

        private int getInstructionsSize(DexInstruction[] instructions) {
            int size = 0;
            for (DexInstruction instruction : instructions) {
                size += instruction.getSize();
            }
            return size;
        }

        private int getRandom(DexInstruction[] instructions) {
            int number = ((int) (Math.random() * instructions.length));

            if (instructions[number] instanceof DexMoveResultObject ||
                    instructions[number] instanceof DexMoveResult ||
                    instructions[number] instanceof DexMoveResultWide) {
                return getRandom(instructions);
            }

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

        private DexInstruction[] insertInstructions(DexInstruction[] origin, DexInstruction[] target, int index, int newInstructionsSize) {
            DexInstruction[] resultInstructions = new DexInstruction[origin.length + target.length];
            for (int i = 0; i < index; i++) {
                // TODO: goto offset 를 사용하는 instructions 을 전부 구현해야함.
                if (origin[i] instanceof DexIfEqz) {
                    origin[i] = new DexIfEqz(
                            ((DexIfEqz) origin[i]).AA,
                            ((DexIfEqz) origin[i]).BBBB + newInstructionsSize
                    );
                }
            }
            System.arraycopy(origin, 0, resultInstructions, 0, index);
            System.arraycopy(target, 0, resultInstructions, index, target.length);
            System.arraycopy(origin, index, resultInstructions, index + target.length, origin.length - index);
            return resultInstructions;
        }

        private int getRegisterIndex(int index, int originParameterIndex) {
            return (originParameterIndex <= index ? (index + 2) : index);
        }

        private DexInstruction[] updateParameterIndex(DexInstruction[] instructions, int originParamIndex) {
            // TODO: parameter 를 사용하는 instructions 을 전부 구현해야함.
            for (int i = 0; i < instructions.length; i++) {
                if (instructions[i] instanceof DexConst4) {
                    instructions[i] = new DexConst4(
                            getRegisterIndex(((DexConst4) instructions[i]).A, originParamIndex),
                            getRegisterIndex(((DexConst4) instructions[i]).B, originParamIndex)
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
                    ifType = IfType.NE;
                } else {
                    fieldRead = getEvenNumberDexField();
                    fieldWrite = getOddNumberDexField();
                    ifType = IfType.EQ;
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

        private DexField getOddNumberDexField() {
            return getDexField(dexItemFactory.intType, "oddNumber");
        }

        private DexField getEvenNumberDexField() {
            return getDexField(dexItemFactory.intType, "evenNumber");
        }

        private DexField getDexField(DexType type, String name) {
            return dexItemFactory.createField(
                    dexItemFactory.createType(descriptor),
                    type,
                    dexItemFactory.createString(name)
            );
        }
    }
}
