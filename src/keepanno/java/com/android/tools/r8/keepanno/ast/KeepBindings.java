// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.ast;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

public class KeepBindings {

  public static Builder builder() {
    return new Builder();
  }

  private static final KeepBindings NONE_INSTANCE = new KeepBindings(Collections.emptyMap());

  private final Map<KeepBindingSymbol, Binding> bindings;

  private KeepBindings(Map<KeepBindingSymbol, Binding> bindings) {
    assert bindings != null;
    this.bindings = bindings;
  }

  public static KeepBindings none() {
    return NONE_INSTANCE;
  }

  public Binding get(KeepBindingReference bindingReference) {
    return bindings.get(bindingReference.getName());
  }

  public Binding get(KeepBindingSymbol bindingReference) {
    return bindings.get(bindingReference);
  }

  public int size() {
    return bindings.size();
  }

  public boolean isEmpty() {
    return bindings.isEmpty();
  }

  public void forEach(BiConsumer<KeepBindingSymbol, KeepItemPattern> fn) {
    bindings.forEach((name, binding) -> fn.accept(name, binding.getItem()));
  }

  @Override
  public String toString() {
    return "{"
        + bindings.entrySet().stream()
            .map(e -> e.getKey() + "=" + e.getValue())
            .collect(Collectors.joining(", "));
  }

  /**
   * A unique binding.
   *
   * <p>The uniqueness / identity of a binding is critical as a binding denotes both the static
   * structural constraint on an item and potentially the identity of a concrete match in the
   * precondition of a rule.
   *
   * <p>In terms of proguard keep rules it provides the difference of structural constraints
   * illustrated by:
   *
   * <pre>
   *   -keepclasswithmembers class *Foo { void bar(); void baz(); }
   * </pre>
   *
   * and
   *
   * <pre>
   *   -keepclasswithmembers class *Foo { void bar(); }
   *   -keepclasswithmembers class *Foo { void baz(); }
   * </pre>
   *
   * The former which only matches classes with the Foo suffix that have both bar and baz can be
   * expressed with a binding of *Foo and the two method items expressed by referencing the shared
   * class. Without the binding the targets will give rise to the latter rules which are independent
   * of each other.
   *
   * <p>In terms of proguard keep rules it also provides the difference in back-referencing into
   * preconditions:
   *
   * <pre>
   *   -if class *Foo -keep class *Foo { void <init>(...); }
   * </pre>
   *
   * and
   *
   * <pre>
   *   -if class *Foo -keep class <1>Foo { void <init>(...); }
   * </pre>
   *
   * The first case will keep all classes matching *Foo and there default constructors if any single
   * live class matches. The second will keep the default constructors of the live classes that
   * match.
   *
   * <p>This wrapper ensures that pattern equality does not imply binding equality.
   */
  public static class Binding {
    private final KeepItemPattern item;

    public Binding(KeepItemPattern item) {
      this.item = item;
    }

    public KeepItemPattern getItem() {
      return item;
    }

    @Override
    public boolean equals(Object obj) {
      return this == obj;
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(this);
    }

    @Override
    public String toString() {
      return item.toString();
    }
  }

  public static class KeepBindingSymbol {
    private final String hint;
    private String suffix = "";

    public KeepBindingSymbol(String hint) {
      this.hint = hint;
    }

    private void setSuffix(String suffix) {
      this.suffix = suffix;
    }

    @Override
    public String toString() {
      return hint + suffix;
    }

    @Override
    public boolean equals(Object obj) {
      return this == obj;
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(this);
    }
  }

  public static class Builder {

    private final Map<String, KeepBindingSymbol> reserved = new HashMap<>();
    private final Map<KeepBindingSymbol, KeepItemPattern> bindings = new IdentityHashMap<>();

    public KeepBindingSymbol generateFreshSymbol(String hint) {
      // Allocate a fresh non-forgeable symbol. The actual name is chosen at build time.
      return new KeepBindingSymbol(hint);
    }

    public KeepBindingSymbol create(String name) {
      KeepBindingSymbol symbol = new KeepBindingSymbol(name);
      KeepBindingSymbol old = reserved.put(name, symbol);
      if (old != null) {
        throw new KeepEdgeException("Multiple bindings with name '" + name + "'");
      }
      return symbol;
    }

    public Builder addBinding(KeepBindingSymbol symbol, KeepItemPattern itemPattern) {
      if (symbol == null || itemPattern == null) {
        throw new KeepEdgeException("Invalid binding of '" + symbol + "'");
      }
      KeepItemPattern old = bindings.put(symbol, itemPattern);
      if (old != null) {
        throw new KeepEdgeException("Multiple definitions for binding '" + symbol + "'");
      }
      return this;
    }

    @SuppressWarnings("ReferenceEquality")
    public KeepBindings build() {
      if (bindings.isEmpty()) {
        return NONE_INSTANCE;
      }
      Map<KeepBindingSymbol, Binding> definitions = new HashMap<>(bindings.size());
      for (KeepBindingSymbol symbol : bindings.keySet()) {
        // The reserved symbols are a subset of all symbols. Those that are not yet reserved denote
        // symbols that must be "unique" in the set of symbols, but that do not have a specific
        // name. Now that all symbols are known we can give each of these a unique name.
        KeepBindingSymbol defined = reserved.get(symbol.toString());
        if (defined != symbol) {
          // For each undefined symbol we try to use the "hint" as its name, if the name is already
          // reserved for another symbol then we search for the first non-reserved name with an
          // integer suffix.
          int i = 0;
          while (defined != null) {
            symbol.setSuffix(Integer.toString(++i));
            defined = reserved.get(symbol.toString());
          }
          reserved.put(symbol.toString(), symbol);
        }
        definitions.put(symbol, verifyAndCreateBinding(symbol));
      }
      return new KeepBindings(definitions);
    }

    private Binding verifyAndCreateBinding(KeepBindingSymbol bindingDefinitionSymbol) {
      KeepItemPattern pattern = bindings.get(bindingDefinitionSymbol);
      for (KeepBindingReference bindingReference : pattern.getBindingReferences()) {
        // Currently, it is not possible to define mutually recursive items, so we only need
        // to check against self.
        if (bindingReference.getName().equals(bindingDefinitionSymbol)) {
          throw new KeepEdgeException("Recursive binding for name '" + bindingReference + "'");
        }
        if (!bindings.containsKey(bindingReference.getName())) {
          throw new KeepEdgeException(
              "Undefined binding for binding '"
                  + bindingReference.getName()
                  + "' or type '"
                  + (bindingReference.isClassType() ? "class" : "member")
                  + "' referenced in binding of '"
                  + bindingDefinitionSymbol
                  + "'");
        }
      }
      return new Binding(pattern);
    }
  }
}
