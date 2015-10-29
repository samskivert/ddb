//
// DDB - for great syncing of data between server and clients

package ddb

/**
 * Provides a mechanism to cancel a slot or listener registration, or to perform post-registration
 * adjustment like making the registration single-shot.
 */
abstract class DConnection : AutoCloseable {

    companion object {
        /**
         * Returns a single connection which aggregates all of the supplied connections. When the
         * aggregated connection is closed, the underlying connections are closed. When its priority is
         * changed the underlying connections' priorities are changed. Etc.
         */
        fun join (vararg conns :DConnection) :DConnection = object : DConnection() {
            override fun close () {
                for (c in conns) c.close()
            }

            override fun once () :DConnection {
                for (c in conns) c.once()
                return this
            }

            override fun atPrio (priority: Int) :DConnection {
                for (c in conns) c.atPrio(priority)
                return this
            }
        }
    }

    /**
     * Disconnects this registration. Subsequent events will not be dispatched to the associated
     * slot or listener.
     */
    override abstract fun close () :Unit

    /**
     * Converts this connection into a one-shot connection. After the first time the slot or
     * listener is notified, it will automatically be disconnected.
     *
     * <p><em>NOTE:</em> if you are dispatching signals in a multithreaded environment, it is
     * possible for your connected listener to be notified before this call has a chance to mark it
     * as one-shot. Thus you could receive multiple notifications. If you require this to be
     * avoided, you must synchronize on the signal/value/etc. on which you are adding a
     * listener:</p>
     *
     * <pre>{@code
     * Signal<Foo> signal = ...;
     * Connection conn;
     * synchronized (signal) {
     *   conn = signal.connect(slot).once();
     * }
     * }</pre>
     *
     * @return this connection instance for convenient chaining.
     */
    abstract fun once () :DConnection

    /**
     * Changes the priority of this connection to the specified value. Connections are notified from
     * highest priority to lowest priority. The default priority is zero.
     *
     * <p>This should generally be done simultaneously with creating a connection. For example:</p>
     *
     * <pre>{@code
     * Signal<Foo> signal = ...;
     * Connection conn = signal.connect(new Slot<Foo>() { ... }).atPrio(5);
     * }</pre>
     *
     * <p><em>NOTE:</em> if you are dispatching signals in a multithreaded environment, it is
     * possible for your connected listener to be notified at priority zero before this call has a
     * chance to update its priority. If you require this to be avoided, you must synchronize on
     * the signal/value/etc. on which you are adding a listener:</p>
     *
     * <pre>{@code
     * Signal<Foo> signal = ...;
     * Connection conn;
     * synchronized (signal) {
     *   conn = signal.connect(slot).atPrio(5);
     * }
     * }</pre>
     *
     * @return this connection instance for convenient chaining.
     */
    abstract fun atPrio (priority :Int) :DConnection
}
