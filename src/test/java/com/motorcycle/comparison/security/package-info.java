/**
 * Deliberate test-only grouping: a security regression suite that cuts across every controller and filter instead
 * of mirroring the {@code main} package layout. It complements, and does not repeat, the unit/slice coverage already
 * in {@code controller}, {@code service}, {@code config} and {@code exception} — see each class's own javadoc for
 * exactly which layer it exercises and why. Findings that would need a change under {@code src/main} to turn green
 * are not represented here at all; they live in {@code SECURITY-AUDIT.md} at the repository root instead.
 */
package com.motorcycle.comparison.security;
