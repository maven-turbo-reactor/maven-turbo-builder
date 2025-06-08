package com.github.seregamorph.maven.turbo;

import java.util.concurrent.FutureTask;

/**
 * @author Sergey Chernov
 */
class OrderedFutureTask<T> extends FutureTask<T> implements Comparable<OrderedFutureTask<T>> {

    private final OrderedCallable<T> callable;

    OrderedFutureTask(OrderedCallable<T> callable) {
        super(callable);
        this.callable = callable;
    }

    @Override
    public int compareTo(OrderedFutureTask<T> that) {
        return callable.compareTo(that.callable);
    }
}
