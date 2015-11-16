//
// DDB - for great syncing of data between server and clients

package ddb

import kotlin.reflect.KProperty

abstract class DReactor {

    /** Returns true if this reactor has at least one connection. */
    val hasConnections :Boolean
        get () = _listeners != null

    /**
     * Clears all connections from this reactor. This is not used in normal circumstances, but is
     * made available for libraries which build on react and need a way to forcibly disconnect all
     * connections to reactive state.
     *
     * @throws IllegalStateException if this reactor is in the middle of dispatching an event.
     */
    @Synchronized fun clearConnections () {
        if (isDispatching) throw IllegalStateException(
            "Cannot clear connections while dispatching.");
        assert(_pendingRuns == null)
        _listeners = null
    }

    @Synchronized internal fun addCons (cons :Cons) :Cons {
        if (isDispatching) {
            _pendingRuns = append(_pendingRuns, object : Runs() {
                override fun run () {
                    _listeners = Cons.insert(_listeners, cons)
                    connectionAdded()
                }
            })
        } else {
            _listeners = Cons.insert(_listeners, cons)
            connectionAdded()
        }
        return cons
    }

    @Synchronized internal fun disconnect (cons :Cons) {
        if (isDispatching) {
            _pendingRuns = append(_pendingRuns, object : Runs() {
                override fun run () {
                    _listeners = Cons.remove(_listeners, cons)
                    connectionRemoved()
                }
            })
        } else {
            _listeners = Cons.remove(_listeners, cons)
            connectionRemoved()
        }
    }

    /** Called when a connection has been added to this reactor. */
    protected fun connectionAdded () {} // noop

    /** Called when a connection may have been removed from this reactor. */
    protected fun connectionRemoved () {} // noop

    /**
     * Emits the supplied event to all connected slots. We omit a bunch of generic type shenanigans
     * here and force the caller to just cast things, because this is all under the hood where
     * there's zero chance of fucking up and this results in simpler, easier to read code.
     */
    protected fun notify (p :KProperty<*>, a1 :Any, a2 :Any) {
        var lners :Cons? = null
        synchronized (this) {
            // if we're currently dispatching, defer this notification until we're done
            if (_listeners == DISPATCHING) {
                _pendingRuns = append(_pendingRuns, object : Runs() {
                    override fun run () { notify(p, a1, a2) }
                })
            } else {
                lners = _listeners
                val sentinel = DISPATCHING
                _listeners = sentinel
            }
        }

        var exn :RuntimeException? = null
        try {
            // perform this dispatch, catching and accumulating any errors
            var cons = lners ; while (cons != null) {
                try {
                    cons.notify(p, a1, a2)
                } catch (ex :RuntimeException) {
                    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
                    if (exn != null) (exn as java.lang.Throwable).addSuppressed(ex)
                    else exn = ex
                }
                if (cons.oneShot) cons.close()
                cons = cons.next
            }

        } finally {
            // note that we're no longer dispatching
            synchronized (this) { _listeners = lners }

            // perform any operations that were deferred while we were dispatching
            var run :Runs? = nextRun()
            while (run != null) {
                try {
                    run.run()
                } catch (ex :RuntimeException) {
                    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
                    if (exn != null) (exn as java.lang.Throwable).addSuppressed(ex)
                    else exn = ex
                }
                run = nextRun()
            }
        }

        // finally throw any exception(s) that occurred during dispatch
        if (exn != null) throw exn
    }

    @Synchronized private fun nextRun () :Runs? {
        val run = _pendingRuns
        if (run != null) _pendingRuns = run.next
        return run
    }

    // always called while lock is held on this reactor
    private val isDispatching :Boolean
        get () = _listeners == DISPATCHING

    protected var _listeners :Cons? = null
    protected var _pendingRuns :Runs? = null

    protected abstract class Runs : Runnable {
        var next :Runs? = null
    }

    companion object {
        /**
         * Returns true if both values are null, reference the same instance, or are
         * {@link Object#equals}.
         */
        fun <T> areEqual (o1 :T, o2 :T) :Boolean =
            (o1 == o2 || (o1 != null && o1.equals(o2)))

        fun append (head :Runs?, action :Runs) :Runs {
            if (head == null) return action
            head.next = append(head.next, action)
            return head
        }

        val DISPATCHING = object : Cons(null) {
            override fun notify (p :KProperty<*>, a1 :Any, a2: Any) {}
        }
    }
}
