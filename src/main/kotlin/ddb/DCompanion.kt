//
// DDB - for great syncing of data between server and clients

package ddb

interface DCompanion<out E : DEntity> {

  fun entityName () :String

  fun create (id :Long) :E
}
