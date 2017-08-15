package com.getkeepsafe.dexcount

import org.gradle.api.DefaultTask
import org.gradle.api.logging.LogLevel
import java.io.PrintWriter
import java.io.Writer
import java.lang.reflect.Method


enum class Color {
    DEFAULT,
    GREEN,
    YELLOW,
    RED
}

private fun Color.toStyle() = when (this) {
    Color.DEFAULT -> Style.Normal
    Color.GREEN   -> Style.Identifier
    Color.YELLOW  -> Style.Info
    Color.RED     -> Style.Error
}

/**
 * Various styles that can be applied to text output.
 *
 * These names match the Gradle StyledTextOutput.Style enums exactly.
 */
private enum class Style {
    Normal,
    Header,
    UserInput,
    Identifier,
    Description,
    ProgressStatus,
    Success,
    SuccessHeader,
    Failure,
    FailureHeader,
    Info,
    Error
}

fun DefaultTask.withStyledOutput(color: Color = Color.DEFAULT, level: LogLevel? = null, fn: (PrintWriter) -> Unit) {
    val style = color.toStyle()
    val factory = this.createStyledOutputFactory()
    val output = factory.create("dexcount", level)
    val styledOutput = output.withStyle(style)
    val printWriter = styledOutput.asPrintWriter()

    fn(printWriter)
}

private fun DefaultTask.createStyledOutputFactory(): StyledTextOutputFactoryWrapper {
    val registry = DefaultTask_getServices(this)
    val factory = ServiceRegistry_get(registry, styledTextOutputFactoryClass)
    return StyledTextOutputFactoryWrapper(factory)
}

private class StyledTextOutputFactoryWrapper(val factory: Any) {
    fun create(label: String, level: LogLevel? = null): StyledTextOutputWrapper {
        val sto = if (level == null) {
            StyledTextOutputFactory_create(factory, label)
        } else {
            StyledTextOutputFactory_createWithLevel(factory, label, level)
        }
        return StyledTextOutputWrapper(sto)
    }
}

private class StyledTextOutputWrapper(private var sto: Any) {
    fun withStyle(style: Style): StyledTextOutputWrapper {
        val platformStyle = style.toPlatformStyle()
        this.sto = StyledTextOutput_withStyle(sto, platformStyle)
        return this
    }

    fun asPrintWriter(): PrintWriter {
        val appendable = sto as Appendable
        val writer = AppendablePrintWriterAdapter(appendable)
        return PrintWriter(writer)
    }
}

private fun Style.toPlatformStyle(): Any {
    val name = this.name
    return styleClass.enumConstants.single { it.name == name }
}

private class AppendablePrintWriterAdapter(private val appendable: Appendable) : Writer() {
    override fun append(csq: CharSequence?): Writer {
        appendable.append(csq)
        return this
    }

    override fun append(csq: CharSequence?, start: Int, end: Int): Writer {
        appendable.append(csq, start, end)
        return this
    }

    override fun append(c: Char): Writer {
        appendable.append(c)
        return this
    }

    override fun write(c: Int) {
        super.write(c)
    }

    override fun write(cbuf: CharArray?) {

    }

    override fun write(cbuf: CharArray?, off: Int, len: Int) {
        val str = String(cbuf!!, off, len)
        appendable.append(str)
    }

    override fun write(str: String?) {
        appendable.append(str)
    }

    override fun write(str: String?, off: Int, len: Int) {
        val substr = str?.substring(off, off + len)
        appendable.append(substr)
    }

    override fun flush() {
    }

    override fun close() {
    }
}

private val styledTextOutputFactoryClass: Class<*> by lazy {
    getClassWithFallback(
        "org.gradle.internal.logging.text.StyledTextOutputFactory",
        "org.gradle.logging.StyledTextOutputFactory"
    )
}

private val styledTextOutputClass: Class<*> by lazy {
    getClassWithFallback(
        "org.gradle.internal.logging.text.StyledTextOutput",
        "org.gradle.logging.StyledTextOutput"
    )
}

@Suppress("UNCHECKED_CAST")
private val styleClass: Class<Enum<*>> by lazy {
    getClassWithFallback(
        "org.gradle.internal.logging.text.StyledTextOutput\$Style",
        "org.gradle.logging.StyledTextOutput\$Style"
    ) as Class<Enum<*>>
}

private val serviceRegistryClass: Class<*> by lazy {
    getClassWithFallback(
        "org.gradle.internal.service.ServiceRegistry",
        "org.gradle.api.internal.project.ServiceRegistry"
    )
}

private val DefaultTask_getServices: Method by lazy {
    var clazz: Class<*>? = DefaultTask::class.java
    var method: Method? = null
    while (clazz != Any::class.java && clazz != null) {
        try {
            method = clazz.getDeclaredMethod("getServices")
            method.isAccessible = true
            break
        } catch (e: NoSuchMethodException) {
            clazz = clazz.superclass
        }
    }
    method!!
}

private val ServiceRegistry_get: Method by lazy {
    serviceRegistryClass.method("get", Class::class.java)
}

private val StyledTextOutputFactory_create: Method by lazy {
    styledTextOutputFactoryClass.method("create", String::class.java)
}

private val StyledTextOutputFactory_createWithLevel: Method by lazy {
    styledTextOutputFactoryClass.method("create", String::class.java, LogLevel::class.java)
}

private val StyledTextOutput_withStyle: Method by lazy {
    styledTextOutputClass.method("withStyle", styleClass)
}

private fun Class<*>.method(name: String, vararg paramTypes: Class<*>): Method {
    return getMethod(name, *paramTypes).apply {
        isAccessible = true
    }
}

private fun getClassWithFallback(name: String, fallbackName: String): Class<*> {
    return try {
        Class.forName(name)
    } catch (ignored: ClassNotFoundException) {
        // We're probably on Gradle 2.X; if not, oh well - another bug for
        // us to fix!
        Class.forName(fallbackName)
    }
}

