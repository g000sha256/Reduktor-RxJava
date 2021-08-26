package ru.g000sha256.reduktor.rxjava2

import io.reactivex.Flowable
import io.reactivex.Scheduler
import io.reactivex.disposables.Disposable
import io.reactivex.processors.BehaviorProcessor
import io.reactivex.processors.PublishProcessor
import org.reactivestreams.Subscriber

internal class StoreFlowable<ACTION, STATE>(
    private val actions: Flowable<ACTION>,
    private val sideEffects: Iterable<SideEffect<ACTION, STATE>>,
    private val reducer: Reducer<ACTION, STATE>,
    private val logger: Logger?,
    private val scheduler: Scheduler?,
    private var state: STATE
) : Flowable<STATE>() {

    private val actionHandlerLock = Any()
    private val subscriptionLock = Any()
    private val states = BehaviorProcessor.createDefault(state)
    private val snapshots = PublishProcessor.create<Snapshot<ACTION, STATE>>()

    private var counter = 0
    private var disposable: Disposable? = null

    override fun subscribeActual(s: Subscriber<in STATE>?) {
        states
            .doOnCancel { synchronized(subscriptionLock, ::doOnUnsubscribe) }
            .doOnSubscribe { synchronized(subscriptionLock, ::doOnSubscribe) }
            .subscribe(s)
    }

    private fun doOnSubscribe() {
        counter++
        if (disposable != null) return
        disposable = mutableListOf(actions)
            .apply { this += sideEffects.map { it.invoke(snapshots) } }
            .let { merge(it) }
            .run { scheduler?.let { observeOn(it) } ?: this }
            .subscribe(::onNext, states::onError, states::onComplete)
    }

    private fun doOnUnsubscribe() {
        counter--
        if (counter > 0) return
        disposable?.dispose()
        disposable = null
    }

    private fun onNext(action: ACTION) {
        synchronized(actionHandlerLock) {
            val logger = logger
            logger?.invoke("ACTION --> $action")
            val oldState = state
            logger?.invoke("STATE  --> $oldState")
            val newState = reducer.invoke(action, oldState)
            if (newState == oldState) {
                logger?.invoke("STATE      NOT CHANGED")
            } else {
                state = newState
                logger?.invoke("STATE  <-- $newState")
                states.onNext(newState)
            }
            val snapshot = Snapshot(action, newState)
            snapshots.onNext(snapshot)
        }
    }

}