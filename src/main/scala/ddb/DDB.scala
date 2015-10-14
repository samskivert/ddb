//
// DDB - for great syncing of data between server and clients

package ddb

class DDB {

  def get[E <: DEntity] (ecomp :DCompanion[E])(id :Long) :E = ???

  def create[E <: DEntity] (ecomp :DCompanion[E]) :E = ???

  def destroy (entity :DEntity) :Unit = ???
}
