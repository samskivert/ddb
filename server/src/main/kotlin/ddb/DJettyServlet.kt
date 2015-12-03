//
// DDB - for great syncing of data between server and clients

package ddb

import java.io.IOException
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import org.eclipse.jetty.websocket.api.*
import org.eclipse.jetty.websocket.servlet.*

class DJettyServlet (val server :DServer) : WebSocketServlet() {

  override fun configure (factory :WebSocketServletFactory) {
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
    override fun address () = ipaddr

    // from DSession
    override fun send (msg :ByteBuffer) {
      if (websess!!.isOpen()) endpoint!!.sendBytes(msg, writeCB)
      else server.log.error("Dropping message to closed WebSocket [$this, msg=$msg]", null);
    }

    // from WebSocketListener
    override fun onWebSocketConnect (websess :Session) {
      websess.setIdleTimeout(5*60*1000L) // TODO: make configurable
      endpoint = websess.remote
      ipaddr = websess.remoteAddress.toString()
      this.websess = websess
      onOpen()
    }

    // from WebSocketListener
    override fun onWebSocketClose (statusCode :Int, reason :String) {
      onClose("status=$statusCode, reason=$reason")
    }

    // from WebSocketListener
    override fun onWebSocketError (cause :Throwable) {
      // if the session just timed out, no need to report error, just let it be closed
      if (cause !is SocketTimeoutException) {
        onError("read", cause)
      }
    }

    // from WebSocketListener
    override fun onWebSocketText (text :String) {
      server.log.error("Got text message? [$this, text=$text]", null)
    }

    // from WebSocketListener
    override fun onWebSocketBinary (payload :ByteArray, offset :Int, len :Int) {
      process(ByteBuffer.wrap(payload, offset, len))
    }

    override fun toString () = "addr=$ipaddr"

    override protected fun onError (mode :String, cause :Throwable) {
      super.onError(mode, cause)
      try {
        websess!!.disconnect()
      } catch (ioe :IOException) {
        server.log.error("Session.disconnect() failure [$this]", ioe)
      }
    }
  }
}
