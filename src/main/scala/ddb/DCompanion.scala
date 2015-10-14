//
// DDB - for great syncing of data between server and clients

package ddb

trait DCompanion[+E <: DEntity] {

  def entityName :String

  def create (id :Long) :E
}
