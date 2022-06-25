package com.zimatars.async.runtime;

import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;

/**
 * Continuation
 */
public interface Continuation {
    int getNext();
    Mono resume(Object sent);

}
