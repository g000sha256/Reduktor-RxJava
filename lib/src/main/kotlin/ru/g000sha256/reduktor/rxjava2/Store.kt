package ru.g000sha256.reduktor.rxjava2

import io.reactivex.Flowable
import io.reactivex.Scheduler
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.functions.Consumer
import io.reactivex.processors.BehaviorProcessor
import io.reactivex.processors.FlowableProcessor
import io.reactivex.processors.PublishProcessor

fun <ACTION, STATE> Flowable<ACTION>.toStoreFlowable(
    state: STATE,
    reducer: Reducer<ACTION, STATE>,
    sideEffectsIterable: Iterable<SideEffect<ACTION, STATE>>,
    scheduler: Scheduler,
    tag: String?
): Flowable<STATE> {
    val store = Store(this, sideEffectsIterable, reducer, scheduler, tag, state)
    return store.createFlowable()
}

private class Store<ACTION, STATE>(
    private val actionsFlowable: Flowable<ACTION>,
    private val sideEffectsIterable: Iterable<SideEffect<ACTION, STATE>>,
    private val reducer: Reducer<ACTION, STATE>,
    private val scheduler: Scheduler,
    private val tag: String?,
    private var state: STATE
) {

    private val actionHandlerLock: Any = Any()
    private val disposableLock: Any = Any()
    private val compositeDisposable: CompositeDisposable = CompositeDisposable()
    private val statesFlowableProcessor: FlowableProcessor<STATE> = BehaviorProcessor.createDefault(state)
    private val snapshotsFlowableProcessor: FlowableProcessor<Snapshot<ACTION, STATE>> = PublishProcessor.create()

    private var counter = 0

    fun createFlowable(): Flowable<STATE> {
        return statesFlowableProcessor
            .doOnCancel { synchronized(disposableLock, ::stopIfNeeded) }
            .doOnSubscribe { synchronized(disposableLock, ::startIfNeeded) }
    }

    private fun handleAction(action: ACTION) {
        log("ACTION --> $action")
        val oldState = state
        log("STATE  --> $oldState")
        val newState = reducer.invoke(action, oldState)
        if (newState == oldState) {
            log("STATE      NOT CHANGED")
        } else {
            state = newState
            log("STATE  <-- $newState")
            statesFlowableProcessor.onNext(newState)
        }
        val snapshot = Snapshot(action, newState)
        snapshotsFlowableProcessor.onNext(snapshot)
    }

    private fun log(message: String) {
        tag?.apply { println("REDUKTOR($this): $message") }
    }

    private fun startIfNeeded() {
        counter++
        if (counter > 1) return
        val sideEffectsFlowableList = sideEffectsIterable.map { it.invoke(snapshotsFlowableProcessor) }
        val nextConsumer = Consumer<ACTION> { /* nothing */ }
        val disposable = Flowable
            .merge(sideEffectsFlowableList)
            .mergeWith(actionsFlowable)
            .observeOn(scheduler)
            .doOnNext { synchronized(actionHandlerLock) { handleAction(it) } }
            .subscribe(nextConsumer, statesFlowableProcessor::onError, statesFlowableProcessor::onComplete)
        compositeDisposable.add(disposable)
    }

    private fun stopIfNeeded() {
        counter--
        if (counter > 0) return
        compositeDisposable.clear()
    }

}