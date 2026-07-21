package com.mojang.serialization.codecs;

import com.mojang.serialization.Codec;

/** Minimal test signature used because the plain JUnit source set does not inherit ModDev's game classpath. */
public interface PrimitiveCodec<T> extends Codec<T> {}
