package com.hmdp.utils;

/**
 * @Author: IIE
 * @name: ILock
 * @Date: 2024/5/6
 */
public interface ILock {
    /**
     * 获取锁
     * @param timeoutSec
     * @return
     */
    boolean tryLock(long timeoutSec);

    void unLock();
}
