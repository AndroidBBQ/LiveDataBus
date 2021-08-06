/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.androidbbq.livedatabus;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;

import java.util.Iterator;
import java.util.Map;

import static androidx.lifecycle.Lifecycle.State.CREATED;
import static androidx.lifecycle.Lifecycle.State.DESTROYED;

public class LiveBusData<T> {

    final Object mDataLock = new Object();
    static final int START_VERSION = -1;
    static final Object NOT_SET = new Object();

    private SafeIterableMap<Observer<? super T>, ObserverWrapper> mObservers =
            new SafeIterableMap<>();
    //volatile修饰
    private volatile Object mData;
    volatile Object mPendingData = NOT_SET;
    //当前值的version
    private int mVersion;

    private boolean mDispatchingValue;
    private boolean mDispatchInvalidated;
    private final Runnable mPostValueRunnable = () -> {
        Object newValue;
        synchronized (mDataLock) {
            //从mPendingData取出
            newValue = mPendingData;
            //重置标志
            mPendingData = NOT_SET;
        }
        //还是调用了setValue
        setValue((T) newValue);
    };

    /**
     * Creates a LiveData initialized with the given {@code value}.
     *
     * @param value initial value
     */
    public LiveBusData(T value) {
        mData = value;
        mVersion = START_VERSION + 1;
    }

    /**
     * Creates a LiveData with no value assigned to it.
     */
    public LiveBusData() {
        mData = NOT_SET;
        mVersion = START_VERSION;
    }

    //consider考虑
    private void considerNotify(ObserverWrapper observer) {
        //如果观察者不是活跃的状态，不管
        if (!observer.mActive) {
            return;
        }
        //判断当前的被观察者是否是活跃状态（start周期之后）
        if (!observer.shouldBeActive()) {
            //如果不是，将mActive设置为false
            observer.activeStateChanged(false);
            return;
        }
        //1。这里 mVersion 默认为-1，看空参构造函数   比如activity第一次到 onStart的时候，调用到这里前面也没有给调用过setValue,
        // 所以也说明mData其实是没有值的，mLastVersion对应为-1，所以 这个会return，不分发值
        //2。当调用了setValue之后，mVersion会+1 变成了 0 ，再走到这里，mLastVersion为-1，所以条件不成立，会分发值
        if (observer.mLastVersion >= mVersion) {
            return;
        }
        observer.mLastVersion = mVersion;
        //调用 mObserver 的 onChange 方法，改变
        observer.mObserver.onChanged((T) mData);
    }

    void dispatchingValue(@Nullable ObserverWrapper initiator) {
        //如果前面的正在调度，后面的会调度无效，会造成数据丢失
        if (mDispatchingValue) {
            //调度无效
            mDispatchInvalidated = true;
            return;
        }
        //调度标志
        mDispatchingValue = true;
        do {
            //调度无效 false
            mDispatchInvalidated = false;
            if (initiator != null) {
                //observer方法调用这个后
                considerNotify(initiator);
                //将initiator置空
                initiator = null;
            } else {
                //setValue postValue 走这里，会遍历mObservers中所有的 ObserverWrapper  调用它的 观察者的 onChange 方法
                for (Iterator<Map.Entry<Observer<? super T>, ObserverWrapper>> iterator =
                     mObservers.iteratorWithAdditions(); iterator.hasNext(); ) {
                    //iterator.next().getValue():LifecycleBoundObserver
                    considerNotify(iterator.next().getValue());
                    if (mDispatchInvalidated) {
                        break;
                    }
                }
            }
        } while (mDispatchInvalidated);
        mDispatchingValue = false;
    }


    @MainThread
    public void observe(@NonNull LifecycleOwner owner, @NonNull Observer<? super T> observer) {
        realObserve(owner, observer, false);
    }

    @MainThread
    public void observeStick(@NonNull LifecycleOwner owner, @NonNull Observer<? super T> observer) {
        realObserve(owner, observer, true);
    }

    private void realObserve(LifecycleOwner owner, Observer<? super T> observer, boolean isStick) {
        //必须是在主线程
        assertMainThread("observe");
        //1。当组件生命周期已经Destroy了，也就没有必要再继续走下去，则直接return。
        // 在这里，LiveData对生命周期的感知也就慢慢显现出来了。
        if (owner.getLifecycle().getCurrentState() == DESTROYED) {
            //如果被观察者的生命已经是被销毁的状态,忽略
            return;
        }
        // 2。 首先以LifecycleOwner和Observer作为参数创建了一个LifecycleBoundObserver对象，
        LifecycleBoundObserver wrapper = new LifecycleBoundObserver(owner, observer, isStick);
        // 接着以Observer为key，新创建的LifecycleBoundObserver为value，存储到mObservers这个map中。
        // 在后面LiveData postValue中会遍历出该map的value值ObserverWrapper，获取组件生命周期的状态，以此状态来决定分不分发通知。
        ObserverWrapper existing = mObservers.putIfAbsent(observer, wrapper);
        if (existing != null && !existing.isAttachedTo(owner)) {
            throw new IllegalArgumentException("Cannot add the same observer"
                    + " with different lifecycles");
        }
        if (existing != null) {
            return;
        }
        //3. 将新创建的LifecycleBoundObserver添加到Lifecycle中，也就是说这个时候观察者注册成功，
        // 当LifecycleOwner也就是组件的状态发生改变时，也会通知到所匹配的observer。
        owner.getLifecycle().addObserver(wrapper);
    }


    @MainThread
    public void removeObserver(@NonNull final Observer<? super T> observer) {
        assertMainThread("removeObserver");
        //将观察者从哪个观察者列表中移除，观察者列表中是以观察者作文key的
        ObserverWrapper removed = mObservers.remove(observer);
        if (removed == null) {
            return;
        }
        removed.detachObserver();
        //调用状态改变，将 wrapper的mActive 设置为false
        removed.activeStateChanged(false);
    }

    @SuppressWarnings("WeakerAccess")
    @MainThread
    public void removeObservers(@NonNull final LifecycleOwner owner) {
        assertMainThread("removeObservers");
        for (Map.Entry<Observer<? super T>, ObserverWrapper> entry : mObservers) {
            if (entry.getValue().isAttachedTo(owner)) {
                removeObserver(entry.getKey());
            }
        }
    }


    public void postValue(T value) {
        boolean postTask;
        synchronized (mDataLock) {
            //判断mPendingData是否是没有设置数据状态
            postTask = mPendingData == NOT_SET;
            //将值赋值给了mPendingData
            mPendingData = value;
        }
        //如果已经设置数据了，将新的丢弃，为了放置一次发送太多数据
        if (!postTask) {
            return;
        }
        //将runnable放到主线程handler中执行
        DefaultTaskExecutor.getInstance().postToMainThread(mPostValueRunnable);
    }


    @MainThread
    public void setValue(T value) {
//      必须得是主线程
        assertMainThread("setValue");
        //值的版本加1
        mVersion++;
        //新的value
        mData = value;
        //通知改变
        dispatchingValue(null);
    }

    /**
     * Returns the current value.
     * Note that calling this method on a background thread does not guarantee that the latest
     * value set will be received.
     *
     * @return the current value
     */
    @SuppressWarnings("unchecked")
    @Nullable
    public T getValue() {
        Object data = mData;
        if (data != NOT_SET) {
            return (T) data;
        }
        return null;
    }

    int getVersion() {
        return mVersion;
    }

    /**
     * Returns true if this LiveData has observers.
     *
     * @return true if this LiveData has observers
     */
    @SuppressWarnings("WeakerAccess")
    public boolean hasObservers() {
        return mObservers.size() > 0;
    }


    //    LifecycleEventObserver是监听组件生命周期更改并将其分派给接收方的一个接口，
    //    而在LifecycleBoundObserver的构造函数中将observer传给了父类ObserverWrapper。
    class LifecycleBoundObserver extends ObserverWrapper implements LifecycleEventObserver {
        //被观察者一般是 activity 或 fragment
        @NonNull
        final LifecycleOwner mOwner;

        LifecycleBoundObserver(@NonNull LifecycleOwner owner, Observer<? super T> observer, boolean isStick) {
            super(observer, isStick);
            mOwner = owner;
        }

        @Override
        boolean shouldBeActive() {
            //判断是否是活跃状态，判断的标准是当前的被观察者生命周期在 start之上
            //修改为在CREATED之上
            return mOwner.getLifecycle().getCurrentState().isAtLeast(CREATED);
        }

        //当被观察者的状态发生变化的时候就会调用这个方法
        @Override
        public void onStateChanged(@NonNull LifecycleOwner source,
                                   @NonNull Lifecycle.Event event) {
            //当owner的生命周期发生改变时会调用
            Lifecycle.State currentState = mOwner.getLifecycle().getCurrentState();
            //如果被观察者是 DESTROYED 状态
            if (currentState == DESTROYED) {
                //将观察者移除
                removeObserver(mObserver);
                return;
            }
            Lifecycle.State prevState = null;
            while (prevState != currentState) {
                prevState = currentState;
                activeStateChanged(shouldBeActive());
                currentState = mOwner.getLifecycle().getCurrentState();
            }
        }

        @Override
        boolean isAttachedTo(LifecycleOwner owner) {
            return mOwner == owner;
        }

        @Override
        void detachObserver() {
            mOwner.getLifecycle().removeObserver(this);
        }
    }

    private abstract class ObserverWrapper {
        //观察者
        final Observer<? super T> mObserver;
        boolean mActive;
        int mLastVersion = START_VERSION;
        boolean mIsStick;

        ObserverWrapper(Observer<? super T> observer, boolean isStick) {
            mObserver = observer;
            mIsStick = isStick;
        }

        abstract boolean shouldBeActive();

        boolean isAttachedTo(LifecycleOwner owner) {
            return false;
        }

        void detachObserver() {
        }

        //活动状态改变
        void activeStateChanged(boolean newActive) {
            //新状态和旧状态相同,return
            //这个意思就是 mActive默认为false  Activity在从生命周期走到了 onCreate 后，回调到这里是不相等,后面代码会将mActive置为true
            // 当activity生命周期发生改变走到 onResume 的时候，由于也是在 shouldBeActive 状态，所以会传入true，因为已经是true了，所以后面不会再处理了。
            if (newActive == mActive) {
                return;
            }
            //立即 改变 mActive 状态，我们不会分发任何事件到不活跃的被观察者
            mActive = newActive;
            //当activity的生命周期发生改变的时候，只有当是粘性事件的时候才会分发值
            if (mActive && mIsStick) {
                //如果是是活跃状态，分发value
                dispatchingValue(this);
            }
        }
    }

    static void assertMainThread(String methodName) {
        if (!DefaultTaskExecutor.getInstance().isMainThread()) {
            throw new IllegalStateException("Cannot invoke " + methodName + " on a background"
                    + " thread");
        }
    }
}
