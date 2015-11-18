//
// DDB - for great syncing of data between server and clients

package ddb

import react.Connection

/**
 * Implements {@link Connection} and a linked-list style listener list for {@link Reactor}s.
 */
abstract class Cons (owner :DReactor?): Connection()  {

    private var _owner :DReactor? = owner
    private var _oneShot = false
    private var _priority = 0

    /** The next connection in our chain. */
    var next :Cons? = null

    /** Indicates whether this connection is one-shot or persistent. */
    val oneShot :Boolean
        get () = _oneShot

    abstract fun notify (key :Any, a1 :Any, a2 :Any) :Unit

    override fun close () {
        // multiple disconnects are OK, we just NOOP after the first one
        _owner?.disconnect(this)
        _owner = null
    }

    override fun once () :Connection {
        _oneShot = true
        return this
    }

    override fun holdWeakly () :Connection = throw UnsupportedOperationException()

    override fun atPrio (priority :Int) :Connection {
        val owner = _owner ?: throw IllegalStateException(
            "Cannot change priority of disconnected connection.")
        owner.disconnect(this)
        next = null
        _priority = priority
        owner.addCons(this)
        return this
    }

    override fun toString () =
        "[owner=$_owner, pri=$_priority, hasNext=${next != null}, oneShot=$oneShot]"

    companion object {
        fun insert(head: Cons?, cons: Cons): Cons {
            if (head == null) {
                return cons
            } else if (cons._priority > head._priority) {
                cons.next = head
                return cons
            } else {
                head.next = insert(head.next, cons)
                return head
            }
        }

        fun remove(head: Cons?, cons: Cons): Cons? {
            if (head == null) return head
            if (head == cons) return head.next
            head.next = remove(head.next, cons)
            return head
        }
    }
}
