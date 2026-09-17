package com.xuanji.repeater

import android.app.Application

/**
 * 这个类只有一个用途：在 App 起来的时候把 applicationContext 交给 [Pronounce]。
 *
 * 为什么需要它：发音要往本地存音频缓存（cacheDir/voice/），而 `Pronounce` 是个 object，
 * 手里没有 Context，拿不到 cacheDir。
 *
 * ⚠️ 别往这儿塞业务逻辑 —— 它就是个传参的。真正的初始化都在各自的页面/Service 里。
 */
class XuanjiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Pronounce.init(this)
    }
}
