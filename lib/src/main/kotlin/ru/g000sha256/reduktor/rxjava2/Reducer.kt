package ru.g000sha256.reduktor.rxjava2

fun interface Reducer<ACTION, STATE> {

    fun invoke(action: ACTION, state: STATE): STATE

}