# LiveDataBus

![](https://jitpack.io/v/AndroidBBQ/LiveDataBus.svg)

LiveDataBus是一款Android消息总线，基于LiveData，具有生命周期感知能力，支持Sticky，支持AndroidX

和其他的消息总线框架相比，🔥体积是最小的、🔥性能是最高的、🔥使用是最简单的、🔥而且由于是基于LiveData本身带有生命周期感知能力，不用担心泄漏问题。


## 快速开始
```groovy
 repositories {
        maven { url 'https://jitpack.io' }
  }
```
引用依赖
```groovy
 implementation 'com.github.AndroidBBQ:LiveDataBus:1.0.0'
```

## 使用

监听非Stick事件
```kotlin
//监听非粘性事件
LiveDataBus.with<String>("test").observe(this, {
       Log.i(TAG, "LiveDataBus  onCreate: $it")
})
```
监听Stick事件
```kotlin
//监听粘性事件
LiveDataBus.with<String>("test").observeStick(this, {
    Log.i("MainActivity", "LiveDataBus  onCreate: $it")
}
```
发送消息
```kotlin
 LiveDataBus.with<String>("test").value = "todo"
```
如果在子线程
```kotlin
LiveDataBus.with<String>("haha").postValue("todo")
```


## 原理
原理也是非常简单，主要需要解决两个地方
1. 解决数据丢失问题，livedata的原来判断组件是否活跃是判断当前的生命周期是否大于 STARTED(onStart->onPause),所以当界面的生命周期在onCreate或者onStop是收不到消息的。
所以需要调大生命周期：
```java
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
        //判断是否是活跃状态，判断的标准是当前的被观察者生命周期在 start 之上
        //修改为在CREATED之上
        return mOwner.getLifecycle().getCurrentState().isAtLeast(CREATED);
    }
}
```
2. 在组件从非活跃状态变成活跃状态时，会将observe之前的value发送过来。这个的话如果我们是需要粘性事件的话还好，如果不是粘性事件就会有问题。

所以为了解决这个问题，是否是粘性事件交给用户自己判断。我提供一个粘性事件和非粘性事件方法
```java
     @MainThread
    public void observe(@NonNull LifecycleOwner owner, @NonNull Observer<? super T> observer) {
        realObserve(owner, observer, false);
    }

    @MainThread
    public void observeStick(@NonNull LifecycleOwner owner, @NonNull Observer<? super T> observer) {
        realObserve(owner, observer, true);
    }
```

然后在状态发生变化的时候，都会走到这个方法中，在这个方法，如果是粘性事件就分发，如果不是就不分发了。
```java
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
```






