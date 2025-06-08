package com.github.seregamorph.maven.turbo;

import java.util.concurrent.Callable;

/**
 * @author Sergey Chernov
 */
public class OrderedCallable<T> implements Callable<T>, Comparable<OrderedCallable<T>> {

    private final int order;
    private final Callable<T> callable;

    public OrderedCallable(int order, Callable<T> callable) {
        this.order = order;
        this.callable = callable;
    }

    @Override
    public int compareTo(OrderedCallable that) {
        return Integer.compare(order, that.order);
    }

    @Override
    public T call() throws Exception {
        return callable.call();
    }
}
