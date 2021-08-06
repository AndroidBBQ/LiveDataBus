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


