/**
 * The console the permit office actually uses when the screen is down.
 *
 * <p>{@link io.github.rkaushikdev.jeepdolly.cli.Main} is the entry point. Every command goes at the
 * database directly through the same unit of work the http service uses, so it works when the web
 * process is down, and prints plain lines: one record to a line, the fields separated by two
 * spaces.
 */
package io.github.rkaushikdev.jeepdolly.cli;
