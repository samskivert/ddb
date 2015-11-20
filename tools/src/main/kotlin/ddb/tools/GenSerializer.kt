//
// DDB - for great syncing of data between server and clients

package ddb.tools

import com.samskivert.mustache.Mustache
import com.samskivert.mustache.Template
import java.io.*
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.ArrayList
import java.util.HashMap
import java.util.jar.JarFile
import org.objectweb.asm.*
import org.objectweb.asm.signature.*
import org.objectweb.asm.util.TraceSignatureVisitor

fun main (argv :Array<String>) {
  if (argv.size < 2) {
    println("Usage: GenSerializerKt [jars or classes directories...] YourProtocol.tmpl")
    System.exit(-1)
  }
  val args = listOf(*argv)
  val tmplP = Paths.get(args.last())
  val destP = Paths.get(stripPost(args.last(), ".tmpl") + ".kt")

  val comp = Mustache.compiler().escapeHTML(false)
  val tmpl = comp.compile(Files.newBufferedReader(tmplP))

  val sources = args.subList(0, args.size-1).map { Paths.get(it) }
  val metas = extractMetas(sources)

  val out = Files.newBufferedWriter(destP)
  val initLam = object : Mustache.Lambda {
    override fun execute (frag :Template.Fragment, out :Writer){
      process(sources, out)
    }
  }
  tmpl.execute(mapOf("count" to metas.size, "init" to initLam), out)
  out.close()
}

fun process (sources :List<Path>, out :Writer) {
  writeSerializer(extractMetas(sources), out)
}

fun writeSerializer (metas :List<ClassMeta>, dest :Writer) {
  val comp = Mustache.compiler().escapeHTML(false)
  val tmplIn = ClassMeta::class.javaClass.getClassLoader().getResourceAsStream("serializer.tmpl")
  val tmpl = comp.compile(BufferedReader(InputStreamReader(tmplIn)))
  tmpl.execute(mapOf("metas" to metas), dest)
}

fun extractMetas (sources :List<Path>) :List<ClassMeta> {
  val metas = hashMapOf<String,ClassMeta>()
  for (path in sources) {
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

  val szerMetas = arrayListOf<ClassMeta>()
  // first initialize all metas so they have their parents
  for (meta in metas.values) meta.init(metas)
  // then go through and check which need serializers
  for (meta in metas.values) if (meta.needsSzer) szerMetas += meta
  // else println("NOSZER: ${kind(metas, meta)} ${meta.typeName}")

  // for (meta in szerMetas) {
  //   println("${meta.kind} ${meta.typeName}")
  //   for (prop in meta.props) println("  $prop")
  // }

  // finally "topologically sort" the metas so that parents always precede children in the list
  val sortedMetas = ArrayList<ClassMeta>(szerMetas.size)
  val seen = hashSetOf<String>()
  while (!szerMetas.isEmpty()) {
    val iter = szerMetas.iterator() ; while (iter.hasNext()) {
      val meta = iter.next()
      if (!meta.isEntity || !meta.isEntityChild || seen.contains(meta.superName)) {
        sortedMetas.add(meta)
        seen.add(meta.typeName)
        iter.remove()
      }
    }
  }

  // for (meta in szerMetas) {
  //   println("${kind(metas, meta)} ${meta.typeName}")
  //   for (prop in meta.props) println("  $prop")
  // }

  return sortedMetas
}

fun extractMetaFromJar (metas :HashMap<String,ClassMeta>, jarPath :Path) {
  val jar = JarFile(jarPath.toFile())
  val iter = jar.entries() ; while (iter.hasMoreElements()) {
    val entry = iter.nextElement()
    if (entry.getName().endsWith(".class")) {
      extractMeta(metas, entry.getName(), ClassReader(jar.getInputStream(entry)))
    }
  }
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

fun extractMeta (metas :HashMap<String,ClassMeta>, name :String, reader :ClassReader) {
  try {
    val mode = ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES
    reader.accept(Visitor(metas), mode)
  } catch (e :Exception) {
    println("Error parsing: $name")
    e.printStackTrace(System.out)
  }
}

fun metaName (name :String) :String {
  val propFmt = System.getProperty("ddb.prop_format", "%C")
  val out = StringBuilder()
  var isCmd = false
  for (char in propFmt) {
    if (isCmd) {
      when (char) {
        'C' -> out.append(name[0].toUpperCase()).append(name.substring(1))
        'U' -> {
          var pastFirst = false
          for (nchar in name) {
            if (!nchar.isUpperCase()) out.append(nchar.toUpperCase())
            else {
              if (pastFirst) out.append('_')
              out.append(nchar)
            }
            pastFirst = true
          }
        }
      }
      isCmd = false
    }
    else if (char == '%') isCmd = true
    else out.append(char)
  }
  return out.toString()
}

class PropMeta (val propName :String, val typeName :String, val isDelegate :Boolean,
                private val metas :Map<String,ClassMeta>) {

  val isBuiltIn :Boolean
    get () = !typeName.contains('.')
  val needsProtocol :Boolean
    get () = !isBuiltIn || typeName == "Any"

  val rawType :String
    get () = rawType(typeName)

  val paramTypes :List<String>
    get () = if (typeKind() == "Value") listOf(typeName) else paramTypes(typeName)

  val isEnum :Boolean
    get () = metas[typeName]?.isEnum ?: false

  val metaName :String
    get () = metaName(propName)

  fun typeKind () :String = when(rawType) {
    "java.util.List"       -> "List"
    "java.util.Set"        -> "Set"
    "java.util.Map"        -> "Map"
    "java.util.Collection" -> "Collection"
    "java.util.String"     -> "String"
    else                   -> if (isBuiltIn) typeName else "Value"
  }

  override fun toString () = "$propName :$typeName"
}

data class ClassMeta (val typeName :String, val superName :String, val ifaceNames :List<String>,
                      val directProps :List<PropMeta>, val directKind :Kind,
                      val isAbstract :Boolean, val isObject :Boolean, val hasCustom :Boolean) {

  var parent :ClassMeta? = null
  var ifaces = listOf<ClassMeta>()

  fun init (metas :Map<String,ClassMeta>){
    parent = metas[superName]
    val ifaces = arrayListOf<ClassMeta>()
    for (name in ifaceNames) {
      val iface = metas[name]
      if (iface != null) ifaces += iface
    }
    this.ifaces = ifaces
  }

  val props :List<PropMeta>
    get () = (parent?.props ?: emptyList()) + directProps

  val kind :Kind by lazy { kind(this) }

  val isEnum :Boolean
    get () = this.superName == "java.lang.Enum"
  val isData :Boolean
    get () = kind == Kind.DATA
  val isEntity :Boolean
    get () = kind == Kind.ENTITY
  val isEntityChild :Boolean
    get () = directKind == Kind.IGNORE

  val needsSzer :Boolean
    get () = ((isData && !isAbstract) || isEntity)

  val entityTypeName :String // needed to disambig in template
    get () = typeName

  fun realProps () = props.filter { !it.isDelegate }
  fun delegateProps () = directProps.filter { it.isDelegate }

  companion object {
    fun kind (meta :ClassMeta) :Kind {
      if (meta.directKind != Kind.IGNORE) return meta.directKind
      val smeta = meta.parent
      if (smeta != null) {
        // if our super class is an enum then we're a specialized enum subtype and we don't want a
        // serializer, so remain IGNORE
        if (smeta.isEnum) return Kind.IGNORE
        if (smeta.kind != Kind.IGNORE) return smeta.kind
      }
      for (iface in meta.ifaces) {
        if (iface.kind != Kind.IGNORE) return iface.kind
      }
      return Kind.IGNORE
    }
  }
}

enum class Kind { IGNORE, DATA, ENTITY }

private fun stripPre (prefix :String, typeName :String) =
  if (typeName.startsWith(prefix)) typeName.substring(prefix.length) else typeName
private fun stripPost (typeName :String, postfix :String) =
  if (typeName.endsWith(postfix)) typeName.substring(0, typeName.length-postfix.length)
  else typeName

private fun rawType (typeName :String) :String {
  val braceIdx = typeName.indexOf('<')
  return if (braceIdx == -1) typeName else typeName.substring(0, braceIdx)
}
private fun paramTypes (typeName :String) :List<String> {
  fun stripBounds (name :String) :String = stripPre("? super ", stripPre("? extends ", name))
  val braceIdx = typeName.indexOf('<')
  return if (braceIdx == -1) listOf()
  else splitParams(typeName.substring(braceIdx+1, typeName.length-1)).map {
    pt -> toKotlinType(stripBounds(pt.trim())) }
}

private fun splitParams (params :String) :List<String> {
  val split = arrayListOf<String>()
  var depth = 0 ; var start = 0
  var ii = 0 ; while (ii < params.length) {
    when(params[ii]) {
      '<' -> depth += 1
      '>' -> depth -= 1
      ',' -> if (depth == 0) {
        split += params.substring(start, ii).trim()
        start = ii+1
      }
    }
    ii += 1
  }
  if (ii > start) split += params.substring(start, ii).trim()
  return split
}

private fun toKotlinType (javaType :String) :String = when(javaType) {
  "boolean" -> "Boolean"
  "byte"    -> "Byte"
  "char"    -> "Char"
  "short"   -> "Short"
  "int"     -> "Int"
  "long"    -> "Long"
  "float"   -> "Float"
  "double"  -> "Double"
  "java.lang.Boolean"   -> "Boolean"
  "java.lang.Byte"      -> "Byte"
  "java.lang.Character" -> "Char"
  "java.lang.Short"     -> "Short"
  "java.lang.Integer"   -> "Int"
  "java.lang.Long"      -> "Long"
  "java.lang.Float"     -> "Float"
  "java.lang.Double"    -> "Double"
  "java.lang.String"    -> "String"
  "java.lang.Class<?>"  -> "Class"
  "java.lang.Object"    -> "Any"
  else -> {
    if (javaType.endsWith("[]")) toKotlinType(javaType.substring(0, javaType.length-2)) + "Array"
    else javaType
  }
}

class Visitor (val metas :HashMap<String,ClassMeta>) : ClassVisitor(Opcodes.ASM5) {
  protected var typeName :String = ""
  protected var superName :String = ""
  protected val ifaces = arrayListOf<String>()
  protected val props = arrayListOf<PropMeta>()
  protected var kind = Kind.IGNORE
  protected var isAbstract = false
  protected var isObject = false
  protected var ignore = false
  protected var hasCustom = false

  override fun visit (version :Int, access :Int, typeName :String, sig :String?, superName :String,
                      ifcs :Array<String>) {
    if ((access and Opcodes.ACC_PRIVATE != 0) ||
        (access and Opcodes.ACC_PUBLIC == 0) ||
        (access and Opcodes.ACC_SYNTHETIC != 0)) ignore = true
    else {
      // println("CLASS: $typeName ${accessToString(access)}")
      this.typeName = jvmToClass(typeName)
      this.superName = jvmToClass(superName)
      for (ifc in ifcs) {
        val ifcName = jvmToClass(ifc)
        if (ifcName == "ddb.DData") kind = Kind.DATA
        ifaces.add(ifcName)
      }
      if (this.superName == "ddb.DEntity") kind = Kind.ENTITY
      this.isAbstract = (access and Opcodes.ACC_ABSTRACT != 0)
    }
  }

  override fun visitField (access :Int, name :String, desc :String, sig :String?,
                           value :Any?) :FieldVisitor? {
    if (ignore) return null
    if (access and Opcodes.ACC_STATIC == 0) {
      val tdesc = if (sig != null) sig else desc
      val typeName = jvmToType(tdesc, access)
      // println("$name $sig / $desc -> $typeName")

      // if this is a delegated prop, we have something like: foo$delegate :Delegate<actualtype>
      // so we clean all that up here before stuffing it into a PropMeta
      if (name.endsWith("\$delegate")) {
        props += PropMeta(stripPost(name, "\$delegate"), paramTypes(typeName)[0], true, metas)
      } else {
        // println("$tdesc -> $typeName -> ${toKotlinType(typeName)}")
        props += PropMeta(name, toKotlinType(typeName), false, metas)
      }
    } else {
      if (name == "INSTANCE$") isObject = true
      else if (name == "serializer") hasCustom = true
    }
    return null
  }

  override fun visitEnd () {
    if (!ignore) metas.put(typeName, ClassMeta(typeName, superName, ifaces, props, kind,
                                               isAbstract, isObject, hasCustom))
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
    if (decl == "") return "java.lang.Object" // special hackery!
    // TraceSignatureVisitor calls visitSuperclass for some reason which tacks an ' extends ' onto
    // the start of everything, fuck knows why
    val cruft = " extends "
    val uncrufted = if (decl.startsWith(cruft)) decl.substring(cruft.length) else decl
    return uncrufted.replace('$', '.')
  }
}
