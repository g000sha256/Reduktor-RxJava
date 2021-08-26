package ru.g000sha256.reduktor.rxjava2

import io.reactivex.Flowable

fun interface SideEffect<ACTION, STATE> {

    fun invoke(snapshots: Flowable<Snapshot<ACTION, STATE>>): Flowable<ACTION>

}