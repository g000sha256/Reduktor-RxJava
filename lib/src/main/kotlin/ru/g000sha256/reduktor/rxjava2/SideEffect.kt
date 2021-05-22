package ru.g000sha256.reduktor.rxjava2

import io.reactivex.Flowable

fun interface SideEffect<ACTION, STATE> {

    fun invoke(snapshotsFlowable: Flowable<Snapshot<ACTION, STATE>>): Flowable<ACTION>

}