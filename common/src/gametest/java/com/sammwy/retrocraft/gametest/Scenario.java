package com.sammwy.retrocraft.gametest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/** Loader-independent test declaration consumed by generateGameTestAdapters. */
@Target(ElementType.METHOD)
public @interface Scenario {}
