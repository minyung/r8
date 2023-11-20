package com.sally.poc;

import com.android.tools.r8.androidapi.ComputedApiLevel;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.graph.FieldAccessFlags;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class FlowObfuscator {
    private final AppView<AppInfoWithLiveness> appView;
    private final DexItemFactory dexItemFactory;

    public FlowObfuscator(AppView<AppInfoWithLiveness> appView) {
        this.appView = appView;
        this.dexItemFactory = appView.dexItemFactory();
    }

    public void execute(ExecutorService executorService, Timing timing, List<String> target) throws ExecutionException {
        timing.begin("flow obfuscator");
        System.out.println("sally.sim");
        execute(executorService, target);
        timing.end();
    }

    private void execute(ExecutorService executorService, List<String> targetClassNameList) throws ExecutionException {
        ThreadUtils.processItems(
                appView.appInfo().classes(),
                clazz -> {
                    String className = clazz.getTypeName();
                    System.out.println(className);
                    if (!targetClassNameList.contains(className)) {
                        return;
                    }
                    System.out.println("start");
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
            this.descriptor = "L" + clazz.getTypeName().replace('.', '/') + ";";
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
                if (code == null) {
                    continue;
                }

                if (code.isCfCode()) {
                    new com.sally.poc.jar.FlowObfuscator.JarTarget(dexItemFactory, clazz).addFlow();
                } else if (code.isDexCode()) {
                    new com.sally.poc.dex.FlowObfuscator.DexTarget(dexItemFactory, clazz, method).addFlow();
                }
            }
        }
    }
}
