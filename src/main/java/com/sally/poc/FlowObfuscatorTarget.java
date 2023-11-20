package com.sally.poc;

import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;

public abstract class FlowObfuscatorTarget {
    protected final DexItemFactory dexItemFactory;
    private final DexProgramClass clazz;
    protected final DexEncodedMethod method;
    protected final String descriptor;

    public FlowObfuscatorTarget(DexItemFactory dexItemFactory, DexProgramClass clazz, DexEncodedMethod method) {
        this.dexItemFactory = dexItemFactory;
        this.clazz = clazz;
        this.method = method;
        this.descriptor = "L" + clazz.getTypeName().replace('.', '/') + ";";
    }

    private DexField getDexField(DexType type, String name) {
        return dexItemFactory.createField(
                dexItemFactory.createType(descriptor),
                type,
                dexItemFactory.createString(name)
        );
    }

    protected DexField getOddNumberDexField() {
        return getDexField(dexItemFactory.intType, "oddNumber");
    }

    protected DexField getEvenNumberDexField() {
        return getDexField(dexItemFactory.intType, "evenNumber");
    }

    public abstract void addFlow();
}
