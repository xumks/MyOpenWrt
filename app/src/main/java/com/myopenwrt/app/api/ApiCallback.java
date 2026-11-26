package com.myopenwrt.app.api;

/**
 * 通用异步回调接口，所有回调在主线程执行。
 */
public interface ApiCallback<T> {
    void onSuccess(T result);

    void onFailure(String message, Exception e);
}
