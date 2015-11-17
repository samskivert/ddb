//
// DDB - for great syncing of data between server and clients

package ddb

import java.io.IOException
import java.nio.ByteBuffer
import org.eclipse.jetty.websocket.api.*
import org.eclipse.jetty.websocket.servlet.*

class DJettyServlet (val server :DServer) : WebSocketServlet() {

  override fun configure (factory :WebSocketServletFactory) {
    factory.policy.setIdleTimeout(10000)
    factory.creator = object : WebSocketCreator {
      override fun createWebSocket (req :ServletUpgradeRequest, rsp :ServletUpgradeResponse) =
        WSSession(server)
    }
  }

  // TODO: need DSession for tracking session local state
  // TODO: also use DSession for listening for session failure/termination?
  // TODO: need to route messages to appropriate DDB (can just do that right here? or maybe in
  // DServer?)
  // TODO: need to route responses back to this websocket when appropriate
  // TODO: need subscription mechanism (hear all events for a DDB)

  class WSSession (server :DServer) : DSession(server), WebSocketListener {

    private var ipaddr = "<unknown>"
    private var websess :Session? = null
    private var endpoint :RemoteEndpoint? = null

    private val writeCB = object : WriteCallback {
      override fun writeSuccess () {}
      override fun writeFailed (cause :Throwable) = onError("write", cause)
    }

    // from DSession
    override fun send (msg :ByteBuffer) {
      if (websess!!.isOpen()) endpoint!!.sendBytes(msg, writeCB)
      else server.onErr.report("Dropping message to closed WebSocket [$this, msg=$msg]", null);
    }

    // from WebSocketListener
    override fun onWebSocketConnect (websess :Session) {
      websess.setIdleTimeout(5*60*1000L) // TODO: make configurable
      endpoint = websess.remote
      ipaddr = websess.remoteAddress.toString()
      this.websess = websess
    }

    // from WebSocketListener
    override fun onWebSocketClose (statusCode :Int, reason :String) {
      // TODO: interpret statusCode?
      onClose.emit(this)
    }

    // from WebSocketListener
    override fun onWebSocketError (cause :Throwable) {
      onError("read", cause)
    }

    // from WebSocketListener
    override fun onWebSocketText (text :String) {
      server.onErr.report("Got text message? [$this, text=$text]", null)
    }

    // from WebSocketListener
    override fun onWebSocketBinary (payload :ByteArray, offset :Int, len :Int) {
      process(ByteBuffer.wrap(payload, offset, len))
    }

    override fun toString () = "addr=$ipaddr"

    private fun onError (mode :String, cause :Throwable) {
      server.onErr.report("Session failure [$this, mode=$mode]", cause)
      onError.emit(cause)
      onClose.emit(this)
      try {
        websess!!.disconnect()
      } catch (ioe :IOException) {
        server.onErr.report("Session.disconnect() failure [$this]", ioe)
      }
    }
  }
}
