package ru.g000sha256.reduktor.rxjava2

import io.reactivex.Flowable
import io.reactivex.Scheduler
import io.reactivex.plugins.RxJavaPlugins

fun <ACTION, STATE> Flowable<ACTION>.toStoreFlowable(
    state: STATE,
    reducer: Reducer<ACTION, STATE>,
    sideEffect: SideEffect<ACTION, STATE>,
    logger: Logger? = null,
    scheduler: Scheduler? = null
): Flowable<STATE> {
    return toStoreFlowable(
        state = state,
        reducer = reducer,
        sideEffects = listOf(sideEffect),
        logger = logger,
        scheduler = scheduler
    )
}

fun <ACTION, STATE> Flowable<ACTION>.toStoreFlowable(
    state: STATE,
    reducer: Reducer<ACTION, STATE>,
    logger: Logger? = null,
    scheduler: Scheduler? = null,
    vararg sideEffects: SideEffect<ACTION, STATE>
): Flowable<STATE> {
    return toStoreFlowable(
        state = state,
        reducer = reducer,
        sideEffects = sideEffects.toList(),
        logger = logger,
        scheduler = scheduler
    )
}

fun <ACTION, STATE> Flowable<ACTION>.toStoreFlowable(
    state: STATE,
    reducer: Reducer<ACTION, STATE>,
    sideEffects: Iterable<SideEffect<ACTION, STATE>>,
    logger: Logger? = null,
    scheduler: Scheduler? = null
): Flowable<STATE> {
    val storeFlowable = StoreFlowable(this, sideEffects, reducer, logger, scheduler, state)
    return RxJavaPlugins.onAssembly(storeFlowable)
}