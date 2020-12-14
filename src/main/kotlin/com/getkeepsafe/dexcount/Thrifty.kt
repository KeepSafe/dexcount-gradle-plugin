package com.getkeepsafe.dexcount

import com.microsoft.thrifty.protocol.CompactProtocol
import com.microsoft.thrifty.transport.Transport
import okio.BufferedSink

// Write support

private inline val <T> T.ignore: Unit
    get() {}

fun <S : BufferedSink> S.transport(): Transport = object : Transport() {
    val self = this@transport

    override fun close() = self.close()
    override fun flush() = self.flush()

    override fun write(data: ByteArray) = self.write(data).ignore
    override fun write(data: ByteArray, offset: Int, count: Int) = self.write(data, offset, count).ignore

    override fun read(buffer: ByteArray, offset: Int, count: Int) = error("write-only")
}

// Read support

fun <S : okio.BufferedSource> S.transport(): Transport = object : Transport() {
    private val self = this@transport

    override fun close() = self.close()
    override fun flush() {}

    override fun read(buffer: ByteArray, offset: Int, count: Int) = self.read(buffer, offset, count)

    override fun write(data: ByteArray) = error("read-only")
    override fun write(buffer: ByteArray, offset: Int, count: Int) = error("read-only")
}

// Protocol support

fun <T : Transport> T.compactProtocol() = CompactProtocol(this)

