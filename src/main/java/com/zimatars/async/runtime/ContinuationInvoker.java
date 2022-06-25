package com.zimatars.async.runtime;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;

public class ContinuationInvoker {
    public static void invoke(MonoSink sink, Continuation continuation, Object sent) {
        Mono next = continuation.resume(sent);
        next.subscribe(new ContinuationSubscriber(sink, continuation));
    }

    static final class ContinuationSubscriber implements Subscriber {
        private Subscription subscription;
        private boolean seenValue;
        private MonoSink sink;
        private Continuation continuation;

        public ContinuationSubscriber(MonoSink sink, Continuation continuation) {
            this.sink = sink;
            this.continuation = continuation;
        }

        @Override
        public void onSubscribe(Subscription s) {
            subscription = s;
            s.request(1);
        }

        @Override
        public void onNext(Object o) {
            seenValue = true;
            handleNext(o);
        }

        @Override
        public void onError(Throwable t) {
        }

        @Override
        public void onComplete() {
            if (!seenValue) handleNext(null);
        }

        private void handleNext(Object o) {
            if (continuation.getNext() == -1) {
                sink.success(o);
            } else {
                invoke(sink, continuation, o);
            }
        }
    }
}
