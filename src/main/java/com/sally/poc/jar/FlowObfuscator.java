package com.sally.poc.jar;

import com.android.tools.r8.androidapi.ComputedApiLevel;
import com.android.tools.r8.cf.code.CfArithmeticBinop;
import com.android.tools.r8.cf.code.CfConstNumber;
import com.android.tools.r8.cf.code.CfFrame;
import com.android.tools.r8.cf.code.CfIf;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfLabel;
import com.android.tools.r8.cf.code.CfLoad;
import com.android.tools.r8.cf.code.CfReturn;
import com.android.tools.r8.cf.code.CfReturnVoid;
import com.android.tools.r8.cf.code.CfStaticFieldRead;
import com.android.tools.r8.cf.code.CfStaticFieldWrite;
import com.android.tools.r8.cf.code.CfStore;
import com.android.tools.r8.cf.code.frame.FrameType;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.graph.FieldAccessFlags;
import com.android.tools.r8.ir.code.IfType;
import com.android.tools.r8.ir.code.NumericType;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;

public class FlowObfuscator {
    private final AppView<AppInfoWithLiveness> appView;
    private final DexItemFactory dexItemFactory;

    public FlowObfuscator(AppView<AppInfoWithLiveness> appView) {
        this.appView = appView;
        this.dexItemFactory = appView.dexItemFactory();
    }

    public void execute(ExecutorService executorService, Timing timing, List<String> target) throws ExecutionException {
        timing.begin("flow obfuscator");
        execute(executorService, target);
        timing.end();
    }

    private void execute(ExecutorService executorService, List<String> targetClassNameList) throws ExecutionException {
        ThreadUtils.processItems(
                appView.appInfo().classes(),
                clazz -> {
                    String className = clazz.getTypeName();
                    if (!targetClassNameList.contains(className)) {
                        return;
                    }

                    Target target = new Target(dexItemFactory, clazz);
                    target.execute();
                },
                executorService
        );
    }

    private static class Target {
        private final DexItemFactory dexItemFactory;
        private final DexProgramClass clazz;
        private final String descriptor;

        public Target(DexItemFactory dexItemFactory, DexProgramClass clazz) {
            this.dexItemFactory = dexItemFactory;
            this.clazz = clazz;
            this.descriptor = "L" + clazz.getTypeName() + ";";
        }

        public void execute() {
            addFields();
            addFlow();
        }

        private void addFields() {
            DexEncodedField oddNumber = getDexEncodedField(
                    Constants.ACC_PRIVATE | Constants.ACC_STATIC,
                    "oddNumber",
                    DexValue.DexValueInt.create(1)
            );
            DexEncodedField evenNumber = getDexEncodedField(
                    Constants.ACC_PRIVATE | Constants.ACC_STATIC,
                    "evenNumber",
                    null
            );

            clazz.appendStaticField(oddNumber);
            clazz.appendStaticField(evenNumber);
        }

        private DexEncodedField getDexEncodedField(int flags, String name, DexValue defaultValue) {
            DexEncodedField.Builder builder = DexEncodedField.syntheticBuilder()
                    .setField(getDexField(dexItemFactory.intType, name))
                    .setAccessFlags(
                            FieldAccessFlags.fromCfAccessFlags(flags)
                    )
                    .setApiLevel(ComputedApiLevel.unknown());

            if (defaultValue != null) {
                builder.setStaticValue(defaultValue);
            }

            return builder.build();
        }

        private DexField getDexField(DexType type, String name) {
            return dexItemFactory.createField(
                    dexItemFactory.createType(descriptor),
                    type,
                    dexItemFactory.createString(name)
            );
        }

        private void addFlow() {
            for (DexEncodedMethod method : clazz.methods()) {
                if (method.isClassInitializer() || method.isInitializer()) {
                    continue;
                }

                Code code = method.getCode();
                if (code == null || !code.isCfCode()) {
                    continue;
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
            }
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

        private DexField getOddNumberDexField() {
            return getDexField(dexItemFactory.intType, "oddNumber");
        }

        private DexField getEvenNumberDexField() {
            return getDexField(dexItemFactory.intType, "evenNumber");
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
                throw new RuntimeException("not supported " + targetMethod.returnType());
            }

            return instructions;
        }
    }
}
