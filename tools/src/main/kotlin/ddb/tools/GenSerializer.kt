//
// DDB - for great syncing of data between server and clients

package ddb.tools

import com.samskivert.mustache.Mustache
import com.samskivert.mustache.Template
import java.io.*
import java.nio.CharBuffer
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.ArrayList
import java.util.HashMap
import java.util.jar.JarFile
import org.jetbrains.kotlin.serialization.ClassData
import org.jetbrains.kotlin.serialization.jvm.JvmProtoBufUtil
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
      writeSerializer(metas, out)
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

enum class Kind { IGNORE, DATA, ENTITY, SERVICE }

class PropMeta (val propName :String, val type :TypeN, val isDelegate :Boolean,
                private val metas :Map<String,ClassMeta>) {

  val typeName = type.toKotlin()
  val isEnum :Boolean
    get () = metas[typeName]?.isEnum ?: false
  val metaName :String
    get () = metaName(propName)

  fun getter () :String = type.getter()
  fun setter () :String = type.setter(propName)

  override fun toString () = "$propName :$typeName"
}

class ArgMeta (val type :TypeN, val name :String)

class MethodMeta (val methName :String, types :List<TypeN>, val names :List<String>) {
  val args :List<ArgMeta> = types.subList(0, types.size-1).zip(names) { t, n -> ArgMeta(t, n) }
  val returnType :TypeN
  init {
    val last = types.last()
    if (!(last is NamedTypeN) || last.name != "react.RFuture") throw IllegalArgumentException(
      "DService methods must return react.RFuture<T> for some T.")
    returnType = last.params[0]
  }
}

data class ClassMeta (val typeName :String, val superName :String, val ifaceNames :List<String>,
                      val directKind :Kind, val directProps :List<PropMeta>,
                      val methods :List<MethodMeta>,
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
  val isService :Boolean
    get () = kind == Kind.SERVICE

  val needsSzer :Boolean
    get () = ((isData && !isAbstract) || isEntity || isService)

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
        if (iface.kind == Kind.DATA) return iface.kind
      }
      return Kind.IGNORE
    }
  }
}

enum class Bound (val token :String) {
  EXTENDS("+"), SUPER("-")
}
abstract class TypeN {
  open fun toBound () :TypeN = this
  open fun rawType () :String = toString()
  open fun kind () :String = toKotlin()

  open fun getter () :String = "buf.get${kind()}()"
  open fun setter (name :String) :String = "buf.put${kind()}(obj.$name)"

  abstract fun toKotlin () :String
  override fun toString () = toKotlin()
}

class PrimTypeN (val name :String) : TypeN() {
  override fun toKotlin () = name
}

class ArrayTypeN (val comp :TypeN) : TypeN() {
  override fun toKotlin () = if (comp is PrimTypeN) "${comp}Array" else "Array<$comp>"
  // TODO: have special kind for non-primitive type arrays and corresponding array get/put
  // methods in BufferExt (i.e. getArray(Foo::class.java))?
}

class NamedTypeN (val name :String, val params :List<TypeN>) : TypeN() {
  override fun rawType () = toKotlinType(name)

  override fun kind () :String {
    val rtype = rawType()
    return if (rtype != name) rtype else "Value"
  }

  override fun getter () = "buf.get${kind()}(pcol${paramArgs()})"
  override fun setter (name :String) = "buf.put${kind()}(pcol${paramArgs()}, obj.$name)"

  override fun toKotlin () = rawType() +
    if (params.isEmpty()) "" else params.joinToString(",", "<", ">")

  private fun paramArgs () :String {
    if (name == "java.lang.Object") return "" // getAny/putAny take no args
    val ptypes = if (params.isEmpty()) listOf(this) else params
    return ptypes.map { "${it.rawType()}::class.java" }.joinToString(", ", ", ")
  }
}

class TypeParamTypeN (val name :String) : TypeN() {
  override fun toKotlin () = name
}

class BoundTypeN (val bound :Bound, val type :TypeN) : TypeN() {
  override fun rawType () = type.rawType()
  override fun kind () = type.kind()
  override fun toKotlin () = type.toKotlin() // "${bound.token}$type"
  override fun toBound () = type
}

// NOTE
// Kotlin as of 1.0-M15 translates get(index) into charAt(index) which is NOT CORRECT; so don't use
// get(index) unless you want to blow an hour trying to figure out what the fuck you can't even
fun CharBuffer.peek () :Char = charAt(0)

// parses a Java type signature into a tree
fun parseType (sig :CharBuffer) :TypeN = when (sig.get()) {
  'V' -> PrimTypeN("Unit")
  'Z' -> PrimTypeN("Boolean")
  'B' -> PrimTypeN("Byte")
  'S' -> PrimTypeN("Short")
  'C' -> PrimTypeN("Char")
  'I' -> PrimTypeN("Int")
  'J' -> PrimTypeN("Long")
  'F' -> PrimTypeN("Float")
  'D' -> PrimTypeN("Double")
  'L' -> {
    val nbuf = StringBuilder()
    var haveParams = false
    while (true) {
      val c = sig.get()
      if (c == ';') break
      if (c == '<') {
        haveParams = true
        break
      }
      if (c == '/') nbuf.append('.')
      else nbuf.append(c)
    }
    val params = arrayListOf<TypeN>()
    if (haveParams) {
      while (sig.peek() != '>') params.add(parseType(sig))
      sig.get() // skip the '>'
      require(sig.get() == ';') { val pos = sig.position()-1 ; "Expected ; at $pos in $sig" }
    }
    val name = nbuf.toString()
    // special hackery here because we treat String like a primitive/built-in type
    if (name == "java.lang.String") PrimTypeN("String") else NamedTypeN(name, params)
  }
  'T' -> {
    val name = StringBuilder()
    while (true) {
      val c = sig.get()
      if (c == ';') break
      else name.append(c)
    }
    TypeParamTypeN(name.toString())
  }
  '+' -> BoundTypeN(Bound.EXTENDS, parseType(sig))
  '-' -> BoundTypeN(Bound.SUPER, parseType(sig))
  '[' -> ArrayTypeN(parseType(sig))
  '*' -> NamedTypeN("java.lang.Object", listOf())
  else -> {
    val pos = sig.position()-1 ; sig.rewind() ; val errc = sig.get(pos)
    throw IllegalArgumentException("Unexpected sig token '$errc' at $pos in $sig")
  }
}

// parses bytecode method type signature '(T*)R' into a list of trees (last elem is return type)
fun parseMethod (sig :CharBuffer) :List<TypeN> {
  val oc = sig.get()
  require(oc == '(') { "Buffer does not contain method signature? Open: $oc" }

  val types = arrayListOf<TypeN>()
  var nc = sig.peek()
  while (nc != ')') {
    types.add(parseType(sig))
    nc = sig.peek()
  }
  sig.get() // skip the closing ')'
  types.add(parseType(sig))
  return types
}

private fun stripPost (typeName :String, postfix :String) =
  if (typeName.endsWith(postfix)) typeName.substring(0, typeName.length-postfix.length)
  else typeName

private fun toKotlinType (javaType :String) :String = when (javaType) {
  "java.lang.Boolean"    -> "Boolean"
  "java.lang.Byte"       -> "Byte"
  "java.lang.Character"  -> "Char"
  "java.lang.Short"      -> "Short"
  "java.lang.Integer"    -> "Int"
  "java.lang.Long"       -> "Long"
  "java.lang.Float"      -> "Float"
  "java.lang.Double"     -> "Double"
  "java.lang.Class"      -> "Class"
  "java.lang.Object"     -> "Any"
  "java.util.List"       -> "List"
  "java.util.Set"        -> "Set"
  "java.util.Map"        -> "Map"
  "java.util.Collection" -> "Collection"
  else -> javaType
}

class Visitor (val metas :HashMap<String,ClassMeta>) : ClassVisitor(Opcodes.ASM5) {
  protected var typeName :String = ""
  protected var superName :String = ""
  protected val ifaces = arrayListOf<String>()
  protected val props = arrayListOf<PropMeta>()
  protected val methods = arrayListOf<MethodMeta>()
  // TODO: this is going to choke on overloaded method names... blah
  protected val methodParamNames = hashMapOf<String,List<String>>()

  protected var kind = Kind.IGNORE
  protected var isAbstract = false
  protected var isObject = false
  protected var ignore = false
  protected var hasCustom = false

  class StringsCollector (val into :ArrayList<String>) : AnnotationVisitor(Opcodes.ASM5) {
    override fun visit (name :String?, value :Any) {
      into += value.toString()
    }
  }

  override fun visitAnnotation (desc :String, visible :Boolean) :AnnotationVisitor? =
    if (kind != Kind.SERVICE || desc != "Lkotlin/jvm/internal/KotlinClass;") null
    else object : AnnotationVisitor(Opcodes.ASM5) {
      private val data = arrayListOf<String>()
      private val strings = arrayListOf<String>()
      override fun visitArray (name :String) = when (name) {
        "data" -> StringsCollector(data)
        "strings" -> StringsCollector(strings)
        else -> null
      }
      override fun visitEnd () {
        val classData = JvmProtoBufUtil.readClassDataFrom(
          data.toTypedArray(), strings.toTypedArray())
        val nr = classData.nameResolver
        for (fn in classData.classProto.functionList) {
          val fnName = nr.getString(fn.name)
          methodParamNames[fnName] = fn.valueParameterList.map { nr.getString(it.name) }
        }
      }
    }

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
        if (ifcName == "ddb.DService" && access and Opcodes.ACC_INTERFACE != 0) kind = Kind.SERVICE
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
      val type = parseType(CharBuffer.wrap(tdesc))
      // println("$name $sig / $desc -> $type")

      // if this is a delegated prop, we have something like: foo$delegate :Delegate<actualtype>
      // so we clean all that up here before stuffing it into a PropMeta
      if (name.endsWith("\$delegate") && type is NamedTypeN) {
        props += PropMeta(stripPost(name, "\$delegate"), type.params[0], true, metas)
      } else {
        // println("$tdesc -> $typeName -> ${toKotlinType(typeName)}")
        props += PropMeta(name, type, false, metas)
      }
    } else {
      if (name == "INSTANCE$") isObject = true
      else if (name == "serializer") hasCustom = true
    }
    return null
  }

  override fun visitMethod (access :Int, name :String, desc :String, sig :String?,
                            exns :Array<String>?) :MethodVisitor? {
    if (kind == Kind.SERVICE) {
      val paramNames = requireNotNull(methodParamNames[name]) {
        "Missing param names for service method: $name"
      }
      methods += MethodMeta(name, parseMethod(CharBuffer.wrap(sig)), paramNames)
      // TODO: complain if method declares thrown exceptions?
    }
    return null
  }

  override fun visitEnd () {
    if (!ignore) metas.put(typeName, ClassMeta(typeName, superName, ifaces, kind, props, methods,
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
}
