//
// DDB - for great syncing of data between server and clients

package ddb.tools

import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.HashMap
import org.objectweb.asm.*
import org.objectweb.asm.signature.*
import org.objectweb.asm.util.TraceSignatureVisitor

fun main (argv :Array<String>) {
  val args = listOf(*argv)
  val dest = Paths.get(args.last())
  // TODO: validate dest before we process classes?

  val metas = hashMapOf<String,ClassMeta>()
  for (arg in args.subList(0, args.size-1)) {
    val path = Paths.get(arg)
    if (!Files.exists(path)) {
      println("Not a file or directory: $path")
    } else if (Files.isDirectory(path)) {
      extractMetaFromDir(metas, path)
    } else if (Files.isReadable(path)) {
      extractMetaFromJar(metas, path)
    } else {
      println("Not a readable file or directory: $path")
    }
  }

  val iter = metas.values.iterator() ; while (iter.hasNext()) {
    if (kind(metas, iter.next()) == Kind.IGNORE) iter.remove()
  }
  for (meta in metas.values) {
    println("${kind(metas, meta)} ${meta.typeName}")
    for (prop in meta.props) println("  $prop")
  }

  writeSerializer(metas, dest)
}

interface Meta {}
data class PropMeta (val propName :String, val typeName :String, val isPrim :Boolean) : Meta
data class ClassMeta (val typeName :String, val superName :String, val ifaces :List<String>,
                      val props :List<PropMeta>, val isData :Boolean, val isEntity :Boolean) : Meta

enum class Kind { IGNORE, DATA, ENTITY }

fun kind (metas :Map<String,ClassMeta>, meta :ClassMeta?) :Kind {
  if (meta == null) return Kind.IGNORE
  if (meta.isData) return Kind.DATA
  if (meta.isEntity) return Kind.ENTITY
  val skind = kind(metas, metas[meta.superName])
  if (skind != Kind.IGNORE) return skind
  for (iface in meta.ifaces) {
    val ikind = kind(metas, metas[iface])
    if (ikind != Kind.IGNORE) return ikind
  }
  return Kind.IGNORE
}

fun extractMetaFromJar (metas :HashMap<String,ClassMeta>, jarPath :Path) {
  // TODO
}

fun extractMetaFromDir (metas :HashMap<String,ClassMeta>, dirPath :Path) {
  println("Processing classes in $dirPath...")
  Files.walkFileTree(dirPath, object : SimpleFileVisitor<Path>() {
    override fun visitFile (file :Path, attrs :BasicFileAttributes) :FileVisitResult {
      if (attrs.isRegularFile() && file.getFileName().toString().endsWith(".class")) {
        extractMeta(metas, file.toString(), ClassReader(Files.readAllBytes(file)))
      }
      return FileVisitResult.CONTINUE
    }
  })
}

class Visitor (val metas :HashMap<String,ClassMeta>) : ClassVisitor(Opcodes.ASM5) {
  protected var typeName :String = ""
  protected var superName :String = ""
  protected val ifaces = arrayListOf<String>()
  protected val props = arrayListOf<PropMeta>()
  protected var isData = false
  protected var isEntity = false
  protected var ignore = false

  override fun visit (version :Int, access :Int, typeName :String, sig :String?, superName :String,
                      ifcs :Array<String>) {
    if ((access and Opcodes.ACC_PRIVATE != 0) ||
        (access and Opcodes.ACC_PUBLIC == 0) ||
        (access and Opcodes.ACC_SYNTHETIC != 0)) ignore = true
    else {
      // println("CLASS: $typeName ${accessToString(access)}")
      this.typeName = jvmToClass(typeName)
      this.superName = jvmToClass(superName)
      this.isEntity = (this.superName == "ddb.DEntity.Keyed" ||
                       this.superName == "ddb.DEntity.Singleton")
      for (ifc in ifcs) {
        val ifcName = jvmToClass(ifc)
        if (ifcName == "ddb.DData") isData = true
        ifaces.add(ifcName)
      }
    }
  }

  override fun visitField (access :Int, name :String, desc :String, sig :String?,
                           value :Any?) :FieldVisitor? {
    if (ignore) return null
    if (access and Opcodes.ACC_STATIC == 0) {
      val typeName = jvmToType(if (sig != null) sig else desc, access)
      // println("$name $sig / $desc -> $typeName")
      props += PropMeta(name, typeName, false) // TODO
    }
    return null
  }

  override fun visitEnd () {
    if (!ignore) {
      val meta = ClassMeta(typeName, superName, ifaces, props, isData, isEntity)
      // println(meta)
      metas.put(typeName, meta)
    }
  }

  private fun accessToString (access :Int) :String {
    val sb = StringBuilder()
    fun add (id :String) {
      if (sb.length > 0) sb.append("|")
      sb.append(id)
    }
    if (access and Opcodes.ACC_PUBLIC != 0) add("public")
    if (access and Opcodes.ACC_PRIVATE != 0) add("private")
    if (access and Opcodes.ACC_PROTECTED != 0) add("protected")
    // if (access and Opcodes.ACC_STATIC != 0) add("static")
    if (access and Opcodes.ACC_FINAL != 0) add("final")
    if (access and Opcodes.ACC_SUPER != 0) add("super")
    // if (access and Opcodes.ACC_SYNCHRONIZED != 0) add("synchronized")
    // if (access and Opcodes.ACC_VOLATILE != 0) add("volatile")
    // if (access and Opcodes.ACC_BRIDGE != 0) add("bridge")
    // if (access and Opcodes.ACC_VARARGS != 0) add("varargs")
    // if (access and Opcodes.ACC_TRANSIENT != 0) add("transient")
    // if (access and Opcodes.ACC_NATIVE != 0) add("native")
    if (access and Opcodes.ACC_INTERFACE != 0) add("interface")
    if (access and Opcodes.ACC_ABSTRACT != 0) add("abstract")
    // if (access and Opcodes.ACC_STRICT != 0) add("strict")
    if (access and Opcodes.ACC_SYNTHETIC != 0) add("synthetic")
    if (access and Opcodes.ACC_ANNOTATION != 0) add("annotation")
    if (access and Opcodes.ACC_ENUM != 0) add("enum")
    // if (access and Opcodes.ACC_MANDATED != 0) add("mandated")
    return sb.toString()
  }

  private fun jvmToClass (typeName :String) = typeName.replace('/', '.').replace('$', '.')

  private fun jvmToType (sig :String, access :Int) :String {
    val viz = TraceSignatureVisitor(access)
    SignatureReader(sig).accept(viz)
    val decl = viz.getDeclaration()
    // TraceSignatureVisitor calls visitSuperclass for some reason which tacks an ' extends ' onto
    // the start of everything, fuck knows why
    val cruft = " extends "
    return if (decl.startsWith(cruft)) decl.substring(cruft.length) else decl
  }
}

fun extractMeta (metas :HashMap<String,ClassMeta>, name :String, reader :ClassReader) {
  try {
    val mode = ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES
    reader.accept(Visitor(metas), mode)
  } catch (e :Exception) {
    println("Error parsing: $name")
    e.printStackTrace(System.out)
  }
}

fun writeSerializer (metas :Map<String,ClassMeta>, dest :Path) {

}
