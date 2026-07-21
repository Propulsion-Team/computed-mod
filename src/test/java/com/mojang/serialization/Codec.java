package com.mojang.serialization;

import java.util.function.Function;
import com.mojang.serialization.codecs.PrimitiveCodec;

/** Minimal test signature used because the plain JUnit source set does not inherit ModDev's game classpath. */
public interface Codec<T> {
    PrimitiveCodec<String> STRING = new PrimitiveCodec<>() {};
    PrimitiveCodec<Double> DOUBLE = new PrimitiveCodec<>() {};
    PrimitiveCodec<Integer> INT = new PrimitiveCodec<>() {};
    PrimitiveCodec<Boolean> BOOL = new PrimitiveCodec<>() {};

    default <S> Codec<S> xmap(
            Function<? super T, ? extends S> decode,
            Function<? super S, ? extends T> encode) {
        return new Codec<>() {};
    }
}
