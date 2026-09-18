package eu.kanade.tachiyomi.source

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import rx.Observable
import rx.Subscriber

fun <T> Observable<T>.asFlow(): Flow<T> = callbackFlow {
    val subscription = this@asFlow.subscribe(object : Subscriber<T>() {
        override fun onNext(t: T) {
            trySend(t)
        }
        override fun onError(e: Throwable) {
            close(e)
        }
        override fun onCompleted() {
            close()
        }
    })
    awaitClose { subscription.unsubscribe() }
}
