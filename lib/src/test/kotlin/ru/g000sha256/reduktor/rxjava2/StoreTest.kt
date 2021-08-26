package ru.g000sha256.reduktor.rxjava2

import io.reactivex.Flowable
import io.reactivex.Scheduler
import io.reactivex.processors.PublishProcessor
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.Executors
import org.junit.Assert
import org.junit.Test

class StoreTest {

    private val currentThread: Thread
        get() = Thread.currentThread()

    @Test
    fun `subscribe - returns initial state`() {
        // GIVEN
        val statesFlowable = PublishProcessor
            .create<String>()
            .createDefaultStoreFlowable()

        // WHEN
        val testSubscriber = statesFlowable.test()

        // THEN
        testSubscriber.assertNotComplete()
        testSubscriber.assertNoErrors()
        testSubscriber.assertValue("State")
    }

    @Test
    fun `actions stream was complete - states stream also completed`() {
        // GIVEN
        val actionsPublishProcessor = PublishProcessor.create<String>()
        val testSubscriber = actionsPublishProcessor
            .createDefaultStoreFlowable()
            .test()

        // WHEN
        actionsPublishProcessor.onComplete()

        // THEN
        testSubscriber.assertComplete()
        testSubscriber.assertNoErrors()
        testSubscriber.assertValue("State")
    }

    @Test
    fun `side effect stream was complete - states stream not completed`() {
        // GIVEN
        val sideEffectPublishProcessor = PublishProcessor.create<String>()
        val sideEffect = SideEffect<String, String> { sideEffectPublishProcessor }
        val testSubscriber = PublishProcessor
            .create<String>()
            .createDefaultStoreFlowable(sideEffect = sideEffect)
            .test()

        // WHEN
        sideEffectPublishProcessor.onComplete()

        // THEN
        testSubscriber.assertNotComplete()
        testSubscriber.assertNoErrors()
        testSubscriber.assertValue("State")
    }

    @Test
    fun `subscribe - states stream not completed - side effect stream returns empty`() {
        // GIVEN
        val sideEffect = SideEffect<String, String> { Flowable.empty() }
        val statesFlowable = PublishProcessor
            .create<String>()
            .createDefaultStoreFlowable(sideEffect = sideEffect)

        // WHEN
        val testSubscriber = statesFlowable.test()

        // THEN
        testSubscriber.assertNotComplete()
        testSubscriber.assertNoErrors()
        testSubscriber.assertValue("State")
    }

    @Test
    fun `subscribe - states stream not completed - side effect stream returns never`() {
        // GIVEN
        val sideEffect = SideEffect<String, String> { Flowable.never() }
        val statesFlowable = PublishProcessor
            .create<String>()
            .createDefaultStoreFlowable(sideEffect = sideEffect)

        // WHEN
        val testSubscriber = statesFlowable.test()

        // THEN
        testSubscriber.assertNotComplete()
        testSubscriber.assertNoErrors()
        testSubscriber.assertValue("State")
    }

    @Test
    fun `actions stream has error - states stream also has error`() {
        // GIVEN
        val actionsPublishProcessor = PublishProcessor.create<String>()
        val testSubscriber = actionsPublishProcessor
            .createDefaultStoreFlowable()
            .test()

        // WHEN
        val throwable = Throwable()
        actionsPublishProcessor.onError(throwable)

        // THEN
        testSubscriber.assertNotComplete()
        testSubscriber.assertError(throwable)
        testSubscriber.assertValue("State")
    }

    @Test
    fun `side effect stream has error - states stream also has error`() {
        // GIVEN
        val sideEffectPublishProcessor = PublishProcessor.create<String>()
        val sideEffect = SideEffect<String, String> { sideEffectPublishProcessor }
        val testSubscriber = PublishProcessor
            .create<String>()
            .createDefaultStoreFlowable(sideEffect = sideEffect)
            .test()

        // WHEN
        val throwable = Throwable()
        sideEffectPublishProcessor.onError(throwable)

        // THEN
        testSubscriber.assertNotComplete()
        testSubscriber.assertError(throwable)
        testSubscriber.assertValue("State")
    }

    @Test
    fun `post action - returns changed states - side effects is empty`() {
        // GIVEN
        val actionsPublishProcessor = PublishProcessor.create<String>()
        val testSubscriber = actionsPublishProcessor
            .createDefaultStoreFlowable()
            .test()

        // WHEN
        actionsPublishProcessor.onNext("Action")

        // THEN
        testSubscriber.assertNotComplete()
        testSubscriber.assertNoErrors()
        testSubscriber.assertValues("State", "ActionState")
    }

    @Test
    fun `post action - returns changed states - side effect has empty flowable`() {
        // GIVEN
        val actionsPublishProcessor = PublishProcessor.create<String>()
        val sideEffect = SideEffect<String, String> { Flowable.empty() }
        val testSubscriber = actionsPublishProcessor
            .createDefaultStoreFlowable(sideEffect = sideEffect)
            .test()

        // WHEN
        actionsPublishProcessor.onNext("Action")

        // THEN
        testSubscriber.assertNotComplete()
        testSubscriber.assertNoErrors()
        testSubscriber.assertValues("State", "ActionState")
    }

    @Test
    fun `post action - returns changed states - side effect handles action`() {
        // GIVEN
        val actionsPublishProcessor = PublishProcessor.create<String>()
        val sideEffect = SideEffect<String, String> { snapshotsFlowable ->
            snapshotsFlowable.flatMap {
                if (it.action == "Action1") return@flatMap Flowable.just("Action2")
                return@flatMap Flowable.empty()
            }
        }
        val testSubscriber = actionsPublishProcessor
            .createDefaultStoreFlowable(sideEffect = sideEffect)
            .test()

        // WHEN
        actionsPublishProcessor.onNext("Action1")

        // THEN
        testSubscriber.assertNotComplete()
        testSubscriber.assertNoErrors()
        testSubscriber.assertValues("State", "Action1State", "Action2Action1State")
    }

    @Test
    fun `post action - side effect calls after reducer`() {
        // GIVEN
        val actionsPublishProcessor = PublishProcessor.create<String>()
        val actualList = mutableListOf<String>()
        val sideEffect = SideEffect<String, String> { snapshotsFlowable ->
            snapshotsFlowable.flatMap {
                actualList.add("SideEffect")
                return@flatMap Flowable.empty()
            }
        }
        val testSubscriber = actionsPublishProcessor
            .createDefaultStoreFlowable(
                reducer = createReducer { actualList.add("Reducer") },
                sideEffect = sideEffect
            )
            .test()

        // WHEN
        actionsPublishProcessor.onNext("Action")

        // THEN
        testSubscriber.assertNotComplete()
        testSubscriber.assertNoErrors()
        testSubscriber.assertValues("State", "ActionState")
        val expectedList = listOf("Reducer", "SideEffect")
        Assert.assertEquals(expectedList, actualList)
    }

    @Test
    fun `wait terminal event - reducer and side effect runs in custom scheduler`() {
        // GIVEN
        val actionsScheduler = createScheduler("ActionsThread")
        val sideEffectScheduler = createScheduler("SideEffectThread")
        val storeScheduler = createScheduler("StoreThread")
        val actualList = mutableListOf<String>()
        val throwable = Throwable()
        val sideEffect = SideEffect<String, String> { snapshotsFlowable ->
            snapshotsFlowable.flatMap {
                actualList.add(currentThread.name)
                when (it.action) {
                    "Action1" -> return@flatMap Flowable
                        .just("Action2")
                        .observeOn(sideEffectScheduler)
                    else -> return@flatMap Flowable.error(throwable)
                }
            }
        }
        val testSubscriber = Flowable
            .just("Action1")
            .observeOn(actionsScheduler)
            .createDefaultStoreFlowable(
                reducer = createReducer { actualList.add(currentThread.name) },
                sideEffect = sideEffect,
                scheduler = storeScheduler
            )
            .test()

        // WHEN
        Thread.sleep(100L)

        // THEN
        testSubscriber.assertNotComplete()
        testSubscriber.assertError(throwable)
        testSubscriber.assertValues("State", "Action1State", "Action2Action1State")
        val expectedList = listOf("StoreThread", "StoreThread", "StoreThread", "StoreThread")
        Assert.assertEquals(expectedList, actualList)
    }

    @Test
    fun `multiple subscription - returns the same state`() {
        // GIVEN
        val actionsPublishProcessor = PublishProcessor.create<String>()
        val statesFlowable = actionsPublishProcessor.createDefaultStoreFlowable()
        val testSubscriber1 = statesFlowable.test()
        val testSubscriber2 = statesFlowable.test()

        // WHEN
        actionsPublishProcessor.onNext("Action")

        // THEN
        testSubscriber1.assertNotComplete()
        testSubscriber1.assertNoErrors()
        testSubscriber1.assertValues("State", "ActionState")
        testSubscriber2.assertNotComplete()
        testSubscriber2.assertNoErrors()
        testSubscriber2.assertValues("State", "ActionState")
    }

    @Test
    fun `multiple subscription - first subscriber was canceled`() {
        // GIVEN
        val actionsPublishProcessor = PublishProcessor.create<String>()
        val statesFlowable = actionsPublishProcessor.createDefaultStoreFlowable()
        val testSubscriber1 = statesFlowable.test()
        val testSubscriber2 = statesFlowable.test()
        testSubscriber1.cancel()

        // WHEN
        actionsPublishProcessor.onNext("Action")

        // THEN
        testSubscriber1.assertNotComplete()
        testSubscriber1.assertNoErrors()
        testSubscriber1.assertValues("State")
        testSubscriber2.assertNotComplete()
        testSubscriber2.assertNoErrors()
        testSubscriber2.assertValues("State", "ActionState")
    }

    @Test
    fun `multiple subscription - all subscribers were canceled`() {
        // GIVEN
        val actionsPublishProcessor = PublishProcessor.create<String>()
        val statesFlowable = actionsPublishProcessor.createDefaultStoreFlowable()
        val testSubscriber1 = statesFlowable.test()
        val testSubscriber2 = statesFlowable.test()
        testSubscriber1.cancel()
        testSubscriber2.cancel()

        // WHEN
        actionsPublishProcessor.onNext("Action")

        // THEN
        testSubscriber1.assertNotComplete()
        testSubscriber1.assertNoErrors()
        testSubscriber1.assertValues("State")
        testSubscriber2.assertNotComplete()
        testSubscriber2.assertNoErrors()
        testSubscriber2.assertValues("State")
    }

    @Test
    fun `post action - side effect does not get action`() {
        // GIVEN
        val actualList = mutableListOf<String>()
        val sideEffect = SideEffect<String, String> { snapshotsFlowable ->
            snapshotsFlowable.flatMap {
                actualList.add(currentThread.name)
                return@flatMap Flowable.empty()
            }
        }
        val actionsPublishProcessor = PublishProcessor.create<String>()
        val testSubscriber = actionsPublishProcessor
            .createDefaultStoreFlowable(sideEffect = sideEffect)
            .test()
        testSubscriber.cancel()

        // WHEN
        actionsPublishProcessor.onNext("Action")

        // THEN
        testSubscriber.assertNotComplete()
        testSubscriber.assertNoErrors()
        testSubscriber.assertValues("State")
    }

    private fun createReducer(callback: () -> Unit): Reducer<String, String> {
        return Reducer { action, state ->
            callback()
            action + state
        }
    }

    private fun createScheduler(name: String): Scheduler {
        val executor = Executors.newSingleThreadExecutor { Thread(it, name) }
        return Schedulers.from(executor)
    }

    private fun Flowable<String>.createDefaultStoreFlowable(
        state: String = "State",
        reducer: Reducer<String, String> = createReducer { },
        sideEffect: SideEffect<String, String>? = null,
        logger: Logger? = null,
        scheduler: Scheduler? = null
    ): Flowable<String> {
        val sideEffects = sideEffect?.let(::listOf) ?: emptyList()
        return toStoreFlowable(state, reducer, sideEffects, logger, scheduler)
    }

}