package com.jiaopeng.jpble

/**
 * 描述：实现可传参数的单例模式
 *
 * @author JiaoPeng by 2020/10/10
 */
open class SingletonHolder<out T : Any, in A>(private val creator: (A) -> T) {

    private var instance: T? = null

    fun getInstance(arg: A): T =
        instance ?: synchronized(this) {
            instance ?: creator(arg).apply {
                instance = this
            }
        }
}