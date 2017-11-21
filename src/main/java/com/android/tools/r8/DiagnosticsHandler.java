// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

/**
 * A DiagnosticsHandler can be provided to customize handling of diagnostics information.
 *
 * <p>During compilation the warning and info methods will be called.
 */
public interface DiagnosticsHandler {

  /**
   * Handle error diagnostics.
   *
   * @param error Diagnostic containing error information.
   */
  default void error(Diagnostic error) {
    if (error.getLocation() != Location.UNKNOWN) {
      System.err.print("Error in " + error.getLocation() + ":\n  ");
    } else {
      System.err.print("Error: ");
    }
    System.err.println(error.getDiagnosticMessage());
  }

  /**
   * Handle warning diagnostics.
   *
   * @param warning Diagnostic containing warning information.
   */
  default void warning(Diagnostic warning) {
    if (warning.getLocation() != Location.UNKNOWN) {
      System.err.print("Warning in " + warning.getLocation() + ":\n  ");
    } else {
      System.err.print("Warning: ");
    }
    System.err.println(warning.getDiagnosticMessage());
  }

  /**
   * Handle info diagnostics.
   *
   * @param info Diagnostic containing the information.
   */
  default void info(Diagnostic info) {
    if (info.getLocation() != Location.UNKNOWN) {
      System.out.print("In " + info.getLocation() + ":\n  ");
    }
    System.out.println(info.getDiagnosticMessage());
  }
}
